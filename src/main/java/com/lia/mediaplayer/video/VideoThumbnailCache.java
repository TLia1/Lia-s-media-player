package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.TextureBridge;
import com.lia.mediaplayer.image.GifDecoder;
import com.lia.mediaplayer.media.MediaCache;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.source.Urls;
import com.lia.mediaplayer.source.YouTubeSource;
import com.lia.mediaplayer.tools.FFmpegCli;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds and caches a small still image for each video in a player's queue, so the
 * queue panel can show what each entry is before it plays:
 *
 * <ul>
 *   <li><b>YouTube links</b> use the video's official thumbnail image (no yt-dlp
 *       needed — just the predictable {@code i.ytimg.com} URL).</li>
 *   <li><b>Direct video files / streams</b> are opened with FFmpeg just long enough
 *       to grab their first decoded frame.</li>
 * </ul>
 *
 * <p>Loading happens on the IO pool; the texture is created back on
 * the render/main thread. All public methods must be called from the main thread.</p>
 *
 * <p>One instance, owned by {@code MediaPlayerContext} and reached through
 * {@code MediaPlayerContext.get().getThumbnailCache()}. Every entry holds a GPU texture,
 * which is why the eviction policy lives in {@link MediaCache} and is tested there: an
 * entry that leaves the map without {@link Thumb#release()} running is a texture the
 * game never gets back, and nothing in the game says so.</p>
 */
public final class VideoThumbnailCache {
    /**
     * Thumbnails are scaled to fit this box (16:9-ish), never upscaled.
     */
    private static final int MAX_W = 160;
    private static final int MAX_H = 90;
    private static final int MAX_ENTRIES = 64;
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

    /**
     * How many thumbnails may be extracted with ffmpeg at the same time, across every
     * panel and screen in the game.
     *
     * <p>Two, because the work behind one is not one process but up to three — a yt-dlp
     * resolve for a page link, an {@code ffprobe}, and an {@code ffmpeg} that decodes to
     * a single frame. Nothing bounded them: a queue panel draws every visible row at
     * once and each row asks for its picture, so opening a playlist of direct links used
     * to fire a dozen of those at the same instant, on a machine already running
     * Minecraft. The cache is what makes that a one-off cost, and this is what stops the
     * one-off cost landing all at once.</p>
     */
    private static final int MAX_CONCURRENT_EXTRACTIONS = 2;

    /**
     * The threads ffmpeg extractions run on.
     *
     * <p>Deliberately <em>not</em> {@link Util#ioPool()}: that pool is vanilla's, shared
     * with chunk and texture loading, and an extraction holds its thread for as long as
     * ffprobe and ffmpeg take (seconds, on a slow link). Two threads of our own that
     * retire when idle keep that off vanilla's back and make the work legible under its
     * own name in a profiler.</p>
     *
     * <p>Static rather than per-instance: the bound has to mean "two at a time in the
     * game", not "two per cache".</p>
     */
    private static final ExecutorService EXTRACTOR = newExtractorPool();

    private static ExecutorService newExtractorPool() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                MAX_CONCURRENT_EXTRACTIONS, MAX_CONCURRENT_EXTRACTIONS,
                30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "liasmediaplayer-thumbnail");
                    thread.setDaemon(true);
                    return thread;
                });
        // Nothing plays for most of a session; the threads should not outlive the burst
        // that needed them.
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private final MediaCache<Thumb> cache = new MediaCache<>(() -> MAX_ENTRIES, Thumb::release);

    /**
     * Returns the thumbnail for a URL, starting a one-off background load the first time.
     */
    public Thumb getOrLoad(String url) {
        return getOrLoad(url, url, -1.0);
    }

    /**
     * The frame at {@code atSeconds}, rather than the one this cache would pick for a
     * queue row — what the API's {@code MediaSurfaces.thumbnail(url, atSeconds)} needs.
     *
     * <p>Cached under a key of its own, so asking for two moments of the same video gives
     * two pictures rather than whichever was asked for first. A YouTube link goes through
     * ffmpeg here instead of taking its published poster: a timestamp was asked for, and
     * the poster is not it.</p>
     */
    public Thumb getOrLoadAt(String url, double atSeconds) {
        return getOrLoad(url + "#t=" + atSeconds, url, Math.max(0, atSeconds));
    }

    private Thumb getOrLoad(String key, String url, double atSeconds) {
        Thumb thumb = cache.computeIfAbsent(key, u -> new Thumb());
        if (thumb.state == State.IDLE) {
            startLoading(key, url, thumb, atSeconds);
        }
        return thumb;
    }

    /**
     * Whether a thumbnail for {@code url} has already been decoded.
     *
     * <p>A peek, deliberately: unlike {@link #getOrLoad} it starts nothing. The API's
     * {@code QueueEntry.hasThumbnail} is built on it, and reading a queue must not turn
     * into a hundred ffmpeg launches for rows nobody has looked at.</p>
     */
    public boolean hasThumbnail(String url) {
        Thumb thumb = cache.get(url);
        return thumb != null && thumb.isLoaded();
    }

    /**
     * Drops every cached thumbnail (e.g. when leaving a server).
     */
    public void clear() {
        cache.clear();
    }

    /** How many thumbnails are held — see {@code api.diag.MediaPlayerStats}. */
    public int size() {
        return cache.size();
    }

    private void startLoading(String key, String url, Thumb thumb, double atSeconds) {
        thumb.state = State.LOADING;
        // A YouTube thumbnail is one small HTTP GET and belongs on the shared IO pool
        // with the other downloads; everything else spawns ffmpeg and goes through the
        // bounded extractor instead. Deciding here rather than inside build() is what
        // keeps a picture that needs no process from queueing behind two that do.
        boolean poster = atSeconds < 0 && YouTubeSource.isYouTube(url);
        Executor executor = poster ? Util.ioPool() : EXTRACTOR;
        CompletableFuture
                .supplyAsync(() -> build(url, atSeconds), executor)
                .whenCompleteAsync((image, error) -> onComplete(key, thumb, image, error),
                        Minecraft.getInstance());
    }

    // ------------------------------------------------------------------
    // Background work (IO pool) — never touch GL or the cache from here
    // ------------------------------------------------------------------

    private static BufferedImage build(String url, double atSeconds) {
        try {
            boolean poster = atSeconds < 0 && YouTubeSource.isYouTube(url);
            BufferedImage raw = poster ? downloadYouTubeThumb(url) : grabFrame(url, atSeconds);
            if (raw == null) {
                throw new IOException("no thumbnail");
            }
            return scaleToArgb(raw);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Nullable
    private static BufferedImage downloadYouTubeThumb(String url) throws IOException {
        String id = youTubeId(url);
        if (id == null) {
            return null;
        }
        // Try the high-quality thumbnail first, then fall back to the always-present default.
        String[] candidates = {
                "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg",
                "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg",
                "https://i.ytimg.com/vi/" + id + "/default.jpg"
        };
        IOException last = null;
        for (String candidate : candidates) {
            try {
                return downloadImage(candidate);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no thumbnail for " + id);
    }

    private static BufferedImage downloadImage(String imageUrl) throws IOException {
        if (!Urls.isHttp(imageUrl)) {
            throw new IOException("Refusing to fetch a non-http(s) thumbnail: " + imageUrl);
        }
        HttpURLConnection connection = (HttpURLConnection) URI.create(imageUrl).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 liasmediaplayer thumbnail");
        connection.setRequestProperty("Accept", "image/*");
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + imageUrl);
            }
            try (InputStream in = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    throw new IOException("Unsupported image at " + imageUrl);
                }
                return image;
            }
        } finally {
            connection.disconnect();
        }
    }

    @Nullable
    private static BufferedImage grabFrame(String url, double atSeconds) throws IOException {
        String mediaUrl = MediaUrlResolver.resolve(url);
        FFmpegCli.MediaInfo info = FFmpegCli.probe(mediaUrl);
        if (!info.hasVideo()) {
            return null;
        }
        // Decode at a small size that fits the thumbnail box (saves work over a full frame).
        int[] target = FFmpegCli.fitWithin(info.width(), info.height(), MAX_W, MAX_H);
        int w = target[0];
        int h = target[1];

        double at;
        if (atSeconds >= 0) {
            // A caller asked for a moment; keep it inside the clip so the grab has
            // something to land on.
            at = atSeconds;
            if (info.durationMicros() > 0) {
                at = Math.min(at, Math.max(0, info.durationMicros() / 1_000_000.0 - 0.1));
            }
        } else {
            // Skip a touch into the clip so we don't land on a black intro frame, while
            // staying safely before the end of short clips.
            at = 1.0;
            if (info.durationMicros() > 0) {
                at = Math.min(1.0, (info.durationMicros() / 1_000_000.0) * 0.5);
            }
        }

        byte[] rgba = FFmpegCli.grabRawFrame(mediaUrl, w, h, at);
        if (rgba == null) {
            // Fall back to the very first frame if the seek produced nothing.
            rgba = FFmpegCli.grabRawFrame(mediaUrl, w, h, 0);
        }
        if (rgba == null) {
            return null;
        }
        return toArgbImage(rgba, w, h);
    }

    /**
     * Builds a TYPE_INT_ARGB image from a packed {@code rgba} frame.
     */
    private static BufferedImage toArgbImage(byte[] rgba, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[width * height];
        for (int i = 0, p = 0; i < argb.length; i++, p += 4) {
            int r = rgba[p] & 0xFF;
            int g = rgba[p + 1] & 0xFF;
            int b = rgba[p + 2] & 0xFF;
            int a = rgba[p + 3] & 0xFF;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, argb, 0, width);
        return image;
    }


    /**
     * Scales the source to fit the thumbnail box and forces TYPE_INT_ARGB.
     */
    private static BufferedImage scaleToArgb(BufferedImage source) {
        int sw = Math.max(1, source.getWidth());
        int sh = Math.max(1, source.getHeight());
        double scale = Math.min(1.0, Math.min(MAX_W / (double) sw, MAX_H / (double) sh));
        int w = Math.max(1, (int) Math.round(sw * scale));
        int h = Math.max(1, (int) Math.round(sh * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------
    // Main thread — create the texture and publish it
    // ------------------------------------------------------------------

    private void onComplete(String key, Thumb thumb, @Nullable BufferedImage image, @Nullable Throwable error) {
        if (error != null || image == null) {
            thumb.state = State.FAILED;
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            LiasMediaPlayer.LOGGER.debug("No thumbnail for {}: {}", key, cause == null ? "?" : cause.toString());
            return;
        }
        // The entry may have been evicted while the load was in flight.
        if (thumb.disposed || cache.get(key) != thumb) {
            return;
        }
        try {
            NativeImage native_ = GifDecoder.toNativeImage(image);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    LiasMediaPlayer.MODID, "videothumb/" + TEXTURE_ID.getAndIncrement());
            TextureBridge.register(location, native_);
            thumb.texture = location;
            thumb.width = image.getWidth();
            thumb.height = image.getHeight();
            thumb.state = State.LOADED;
        } catch (Exception e) {
            thumb.state = State.FAILED;
            LiasMediaPlayer.LOGGER.debug("Failed to upload thumbnail for {}", key, e);
        }
    }

    // ------------------------------------------------------------------
    // YouTube id parsing
    // ------------------------------------------------------------------

    @Nullable
    private static String youTubeId(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (host.equals("youtu.be")) {
                return path != null && path.length() > 1 ? sanitizeId(firstSegment(path.substring(1))) : null;
            }
            if (host.equals("youtube.com") || host.equals("m.youtube.com") || host.equals("music.youtube.com")) {
                if (path != null) {
                    String lower = path.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("/shorts/")) {
                        return sanitizeId(firstSegment(path.substring("/shorts/".length())));
                    }
                    if (lower.startsWith("/embed/")) {
                        return sanitizeId(firstSegment(path.substring("/embed/".length())));
                    }
                    if (lower.startsWith("/live/")) {
                        return sanitizeId(firstSegment(path.substring("/live/".length())));
                    }
                }
                String query = uri.getQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("v=")) {
                            return sanitizeId(part.substring(2));
                        }
                    }
                }
            }
            return null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * Accepts only the shape of a real YouTube id. The result is pasted straight into the
     * {@code i.ytimg.com} thumbnail URL, so anything else (path separators, an encoded
     * query, ".." and friends) would let a chat link steer that request elsewhere.
     */
    @Nullable
    private static String sanitizeId(@Nullable String id) {
        if (id == null || id.isEmpty() || id.length() > 32) {
            return null;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                return null;
            }
        }
        return id;
    }

    private static String firstSegment(String s) {
        int slash = s.indexOf('/');
        String seg = slash >= 0 ? s.substring(0, slash) : s;
        int q = seg.indexOf('?');
        return q >= 0 ? seg.substring(0, q) : seg;
    }

    public enum State {IDLE, LOADING, LOADED, FAILED}

    /**
     * A single queue thumbnail.
     */
    public static final class Thumb {
        public State state = State.IDLE;
        public boolean disposed = false;
        @Nullable
        public ResourceLocation texture;
        public int width;
        public int height;

        public boolean isLoaded() {
            return state == State.LOADED && texture != null;
        }

        public void release() {
            disposed = true;
            if (texture != null) {
                TextureBridge.release(texture);
                texture = null;
            }
            state = State.IDLE;
        }
    }
}
