package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.MemoryReleasable;
import com.lia.mediaplayer.media.MemoryMonitor;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.source.YouTubeSource;
import com.lia.mediaplayer.image.GifDecoder;
import com.lia.mediaplayer.tools.FFmpegCli;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * <p>Loading happens on the IO pool; the {@link DynamicTexture} is created back on
 * the render/main thread. All public methods must be called from the main thread.</p>
 */
public final class VideoThumbnailCache {
    /** Thumbnails are scaled to fit this box (16:9-ish), never upscaled. */
    private static final int MAX_W = 160;
    private static final int MAX_H = 90;
    private static final int MAX_ENTRIES = 64;
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

    private static final LinkedHashMap<String, Thumb> CACHE = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Thumb> eldest) {
            if (size() > MAX_ENTRIES) {
                eldest.getValue().release();
                return true;
            }
            return false;
        }
    };

    static {
        MemoryMonitor.register(new MemoryReleasable() {
            @Override
            public int getReleasePriority() {
                return 10;
            }

            @Override
            public boolean releaseMemory(boolean isCritical) {
                if (CACHE.isEmpty()) {
                    return false;
                }
                Minecraft.getInstance().execute(VideoThumbnailCache::clear);
                return true;
            }
        });
    }

    private VideoThumbnailCache() {
    }

    /** Returns the thumbnail for a URL, starting a one-off background load the first time. */
    public static Thumb getOrLoad(String url) {
        Thumb thumb = CACHE.computeIfAbsent(url, u -> new Thumb());
        if (thumb.state == State.IDLE) {
            startLoading(url, thumb);
        }
        return thumb;
    }

    /** Drops every cached thumbnail (e.g. when leaving a server). */
    public static void clear() {
        CACHE.values().forEach(Thumb::release);
        CACHE.clear();
    }

    private static void startLoading(String url, Thumb thumb) {
        thumb.state = State.LOADING;
        CompletableFuture
                .supplyAsync(() -> build(url), Util.ioPool())
                .whenCompleteAsync((image, error) -> onComplete(url, thumb, image, error),
                        Minecraft.getInstance());
    }

    // ------------------------------------------------------------------
    // Background work (IO pool) — never touch GL or the cache from here
    // ------------------------------------------------------------------

    private static BufferedImage build(String url) {
        try {
            BufferedImage raw = YouTubeSource.isYouTube(url) ? downloadYouTubeThumb(url) : grabFirstFrame(url);
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
    private static BufferedImage grabFirstFrame(String url) throws IOException {
        String mediaUrl = MediaUrlResolver.resolve(url);
        FFmpegCli.MediaInfo info = FFmpegCli.probe(mediaUrl);
        if (!info.hasVideo()) {
            return null;
        }
        // Decode at a small size that fits the thumbnail box (saves work over a full frame).
        int[] target = FFmpegCli.fitWithin(info.width(), info.height(), MAX_W, MAX_H);
        int w = target[0];
        int h = target[1];

        // Skip a touch into the clip so we don't land on a black intro frame, while
        // staying safely before the end of short clips.
        double at = 1.0;
        if (info.durationMicros() > 0) {
            at = Math.min(1.0, (info.durationMicros() / 1_000_000.0) * 0.5);
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

    /** Builds a TYPE_INT_ARGB image from a packed {@code rgba} frame. */
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



    /** Scales the source to fit the thumbnail box and forces TYPE_INT_ARGB. */
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

    private static void onComplete(String url, Thumb thumb, @Nullable BufferedImage image, @Nullable Throwable error) {
        if (error != null || image == null) {
            thumb.state = State.FAILED;
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            LiasMediaPlayer.LOGGER.debug("No thumbnail for {}: {}", url, cause == null ? "?" : cause.toString());
            return;
        }
        // The entry may have been evicted while the load was in flight.
        if (thumb.disposed || CACHE.get(url) != thumb) {
            return;
        }
        try {
            NativeImage native_ = GifDecoder.toNativeImage(image);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    LiasMediaPlayer.MODID, "videothumb/" + TEXTURE_ID.getAndIncrement());
            Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(native_));
            thumb.texture = location;
            thumb.width = image.getWidth();
            thumb.height = image.getHeight();
            thumb.state = State.LOADED;
        } catch (Exception e) {
            thumb.state = State.FAILED;
            LiasMediaPlayer.LOGGER.debug("Failed to upload thumbnail for {}", url, e);
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
                return path != null && path.length() > 1 ? firstSegment(path.substring(1)) : null;
            }
            if (host.endsWith("youtube.com")) {
                if (path != null) {
                    String lower = path.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("/shorts/")) {
                        return firstSegment(path.substring("/shorts/".length()));
                    }
                    if (lower.startsWith("/embed/")) {
                        return firstSegment(path.substring("/embed/".length()));
                    }
                    if (lower.startsWith("/live/")) {
                        return firstSegment(path.substring("/live/".length()));
                    }
                }
                String query = uri.getQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("v=")) {
                            return part.substring(2);
                        }
                    }
                }
            }
            return null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String firstSegment(String s) {
        int slash = s.indexOf('/');
        String seg = slash >= 0 ? s.substring(0, slash) : s;
        int q = seg.indexOf('?');
        return q >= 0 ? seg.substring(0, q) : seg;
    }

    public enum State {IDLE, LOADING, LOADED, FAILED}

    /** A single queue thumbnail. */
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

        void release() {
            disposed = true;
            if (texture != null) {
                Minecraft.getInstance().getTextureManager().release(texture);
                texture = null;
            }
            state = State.IDLE;
        }
    }
}
