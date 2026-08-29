package com.lia.mediaplayer.image;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.image.DecodedImage;
import com.lia.mediaplayer.api.image.ImageDecoder;
import com.lia.mediaplayer.api.render.SurfacePixels;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.gui.TextureBridge;
import com.lia.mediaplayer.media.MediaCache;
import com.lia.mediaplayer.source.GiphySource;
import com.lia.mediaplayer.source.TenorSource;
import com.lia.mediaplayer.source.Urls;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps downloaded image previews alive for roughly as long as the message that
 * references them can still be displayed in the chat (the vanilla chat keeps at
 * most 100 messages, so the cache is bounded the same way and evicts the oldest
 * entry first).
 *
 * <p>Animated GIFs are decoded once into a sequence of fully composited frames
 * (see {@link GifDecoder}); each frame is uploaded as its own texture a single
 * time, and the render code just blits whichever frame matches the wall clock.
 * No re-decoding or texture re-upload happens while the preview is on screen.</p>
 *
 * <p>All public methods must be called from the render/main thread. Downloads
 * happen on a background IO pool and are published back on the main thread.</p>
 *
 * <p>One instance, owned by {@code MediaPlayerContext} and reached through
 * {@code MediaPlayerContext.get().getImagePreviewCache()}. Both of its bounds — the
 * entry count and the memory budget — are settings a player can move while the game is
 * running, so {@link MediaCache} re-reads them rather than capturing them, and carries
 * the eviction that releases each entry's textures on the way out.</p>
 */
public final class ImagePreviewCache {
    /**
     * Mirrors ChatComponent.MAX_CHAT_HISTORY.
     */
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    /**
     * Largest decoded still we accept. The byte cap above bounds the download, not the
     * decoded bitmap: a few kilobytes of PNG can expand to gigabytes of ARGB, and the URL
     * comes from chat, so the pixel count needs its own limit.
     */
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

    private static long getMaxCacheBytes() {
        return (long) ConfigStore.MAX_IMAGE_CACHE_MEGABYTES.getValue() * 1024 * 1024;
    }

    private final MediaCache<Entry> cache = new MediaCache<>(
            ConfigStore.MAX_IMAGE_CACHE_ENTRIES::getValue,
            ImagePreviewCache::getMaxCacheBytes,
            entry -> entry.estimatedSizeBytes,
            Entry::releaseTexture);

    /**
     * Registers a URL seen in chat so its preview can be loaded lazily later.
     */
    public void track(String url) {
        Minecraft.getInstance().execute(() -> cache.computeIfAbsent(url, u -> new Entry()));
    }

    /**
     * Returns the entry for the URL, starting an asynchronous download the
     * first time it is requested. Check {@link Entry#state} to know whether
     * the texture is ready.
     */
    public Entry getOrLoad(String url) {
        return getOrLoad(url, false);
    }

    /**
     * The same, optionally keeping the first frame's pixels readable — what a
     * {@code MediaSurface} asked for with {@code keepPixels} needs, and what backs
     * {@code MediaSurface.pixels()}.
     *
     * <p>Retention is opt-in, and per entry rather than global, because it is heap the
     * cache's VRAM budget does not account for: keeping every chat preview readable would
     * roughly double what the picture cache costs, to answer a question almost no caller
     * asks. Only the first frame is kept — an addon sampling a colour means the picture,
     * not the animation.</p>
     *
     * <p>An entry already loaded <em>without</em> the pixels is dropped and loaded again,
     * because the copy can only be taken while the frames are still decoded. That is a
     * re-download, so it happens at most once per URL per session and only when something
     * actually asks.</p>
     */
    public Entry getOrLoad(String url, boolean keepPixels) {
        Entry entry = cache.computeIfAbsent(url, u -> new Entry());
        if (keepPixels && !entry.keepPixels) {
            entry.keepPixels = true;
            if (entry.state == State.LOADED && entry.pixels == null) {
                entry.releaseTexture();
            }
        }
        if (entry.state == State.IDLE) {
            startLoading(url, entry);
        }
        return entry;
    }

    /**
     * Drops every cached preview (e.g. when leaving a server).
     */
    public void clear() {
        cache.clear();
    }

    /** How many pictures are held — see {@code api.diag.MediaPlayerStats}. */
    public int size() {
        return cache.size();
    }

    /** What those pictures are estimated to cost, in bytes. */
    public long estimatedBytes() {
        return cache.estimatedBytes();
    }

    private void startLoading(String url, Entry entry) {
        entry.state = State.LOADING;
        LiasMediaPlayer.LOGGER.info("Loading image preview from {}", url);

        CompletableFuture
                .supplyAsync(() -> download(url), Util.ioPool())
                .whenCompleteAsync((decoded, error) -> onDownloadComplete(url, entry, decoded, error),
                        Minecraft.getInstance());
    }

    /**
     * Runs on the IO pool — never touch GL or the cache from here.
     */
    private static GifDecoder.Result download(String url) {
        try {
            // Share links are HTML pages; turn them into the real GIF first. Tenor needs
            // the page fetched to find its media id, Giphy carries the id in the link.
            String mediaUrl = url;
            if (TenorSource.isTenorPage(url)) {
                mediaUrl = TenorResolver.resolve(url);
            } else {
                String giphy = GiphySource.directGif(url);
                if (giphy != null) {
                    mediaUrl = giphy;
                }
            }
            if (!Urls.isHttp(mediaUrl)) {
                throw new IOException("Refusing to fetch a non-http(s) image: " + mediaUrl);
            }
            HttpURLConnection connection = (HttpURLConnection) URI.create(mediaUrl).toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 liasmediaplayer image preview");
            connection.setRequestProperty("Accept", "image/*");
            try {
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + responseCode + " for " + mediaUrl);
                }
                byte[] data;
                try (InputStream in = connection.getInputStream()) {
                    data = in.readNBytes(MAX_IMAGE_BYTES + 1);
                }
                if (data.length > MAX_IMAGE_BYTES) {
                    throw new IOException("Image too large (> " + MAX_IMAGE_BYTES + " bytes)");
                }
                return decode(data);
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    /**
     * Decodes the downloaded bytes into one or more frames. Animated GIFs become
     * a full frame sequence; everything else (png, jpg, single-frame gif, bmp)
     * becomes a single frame. Runs on the IO pool.
     */
    private static GifDecoder.Result decode(byte[] data) throws IOException {
        GifDecoder.Result external = decodeExternally(data);
        if (external != null) {
            return external;
        }
        if (isGif(data)) {
            return GifDecoder.decode(data);
        }

        NativeImage single;
        if (isPng(data)) {
            single = NativeImage.read(new ByteArrayInputStream(data));
            if ((long) single.getWidth() * single.getHeight() > MAX_IMAGE_PIXELS) {
                single.close();
                throw new IOException("Image is implausibly large");
            }
        } else {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
            if (decoded == null) {
                throw new IOException("Unsupported image format");
            }
            if ((long) decoded.getWidth() * decoded.getHeight() > MAX_IMAGE_PIXELS) {
                throw new IOException("Image is implausibly large");
            }
            single = GifDecoder.toNativeImage(toArgb(decoded));
        }
        return new GifDecoder.Result(new NativeImage[]{single}, new int[]{0});
    }

    /**
     * Offers the bytes to every registered {@link ImageDecoder}, in order, and turns
     * whatever claims them into the frame sequence the rest of this class works in.
     *
     * <p>Registered decoders are asked <em>before</em> the built-ins, which is what lets
     * an addon supply WebP or APNG — formats the mod has no reader for — and, if it
     * really wants to, a better GIF decoder than this one. A decoder that claims a
     * picture and then throws does not fall through: it said the picture was its, and
     * quietly producing a different one would hide the bug. It answering {@code null}
     * does fall through, because that is it saying the bytes were not its after all.</p>
     *
     * <p>Runs on the IO pool, like the rest of {@link #decode}. The pixel cap is applied
     * to what comes back for the same reason it is applied to the built-in path — the URL
     * came from chat.</p>
     */
    @Nullable
    private static GifDecoder.Result decodeExternally(byte[] data) throws IOException {
        List<ImageDecoder> decoders = LiasMediaPlayerApi.imageDecoders();
        if (decoders.isEmpty()) {
            return null;
        }
        byte[] header = Arrays.copyOf(data, Math.min(data.length, ImageDecoder.HEADER_BYTES));
        for (ImageDecoder decoder : decoders) {
            boolean claims;
            try {
                claims = decoder.supports(header);
            } catch (RuntimeException e) {
                LiasMediaPlayer.LOGGER.warn("Image decoder {} threw from supports",
                        decoder.getClass().getName(), e);
                continue;
            }
            if (!claims) {
                continue;
            }
            DecodedImage decoded = decoder.decode(data);
            if (decoded == null) {
                continue;
            }
            if ((long) decoded.width() * decoded.height() > MAX_IMAGE_PIXELS) {
                throw new IOException("Image is implausibly large");
            }
            return toResult(decoded);
        }
        return null;
    }

    /** An addon's frames, uploaded into the {@link NativeImage}s the cache holds. */
    private static GifDecoder.Result toResult(DecodedImage decoded) {
        NativeImage[] images = new NativeImage[decoded.frameCount()];
        for (int i = 0; i < images.length; i++) {
            images[i] = GifDecoder.toNativeImage(
                    decoded.frames().get(i), decoded.width(), decoded.height());
        }
        return new GifDecoder.Result(images, decoded.delaysMs());
    }

    /**
     * Ensures the image is TYPE_INT_ARGB so getRGB yields packed ARGB ints.
     */
    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage argb = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        argb.createGraphics().drawImage(source, 0, 0, null);
        return argb;
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
    }

    private static boolean isGif(byte[] data) {
        return data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a';
    }

    /**
     * Runs on the main thread — safe to create GL textures and mutate the cache.
     */
    private void onDownloadComplete(String url, Entry entry, @Nullable GifDecoder.Result decoded,
                                    @Nullable Throwable error) {
        if (error != null || decoded == null || decoded.frames().length == 0) {
            entry.state = State.FAILED;
            closeFrames(decoded);
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause() : error;
            LiasMediaPlayer.LOGGER.warn("Failed to load image preview from {}: {}", url,
                    cause == null ? "no image" : cause.toString());
            return;
        }

        try {
            // The entry may have been evicted while the download was in flight.
            if (cache.get(url) != entry) {
                closeFrames(decoded);
                return;
            }

            NativeImage[] images = decoded.frames();
            ResourceLocation[] locations = new ResourceLocation[images.length];
            for (int i = 0; i < images.length; i++) {
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        LiasMediaPlayer.MODID, "preview/" + TEXTURE_ID.getAndIncrement());
                TextureBridge.register(location, images[i]);
                locations[i] = location;
            }

            int total = 0;
            for (int delay : decoded.delaysMs()) {
                total += delay;
            }

            if (entry.keepPixels) {
                // Taken from the first frame before the images are handed to the texture
                // manager, which is the last moment their pixels are ours to read.
                entry.pixels = new SurfacePixels(images[0].getWidth(), images[0].getHeight(),
                        readArgb(images[0]));
            }
            entry.frames = locations;
            entry.frameDelaysMs = decoded.delaysMs();
            entry.totalDurationMs = total;
            entry.animationStartMs = 0L;
            entry.width = images[0].getWidth();
            entry.height = images[0].getHeight();
            entry.estimatedSizeBytes = (long) entry.width * entry.height * 4 * images.length
                    + (entry.pixels == null ? 0L : (long) entry.width * entry.height * 4);
            entry.state = State.LOADED;
            LiasMediaPlayer.LOGGER.info("Loaded image preview {}x{} ({} frame(s)) from {}",
                    entry.width, entry.height, images.length, url);
            cache.enforceByteBudget();
        } catch (Exception e) {
            entry.state = State.FAILED;
            closeFrames(decoded);
            LiasMediaPlayer.LOGGER.warn("Failed to create preview texture for {}", url, e);
        }
    }

    /**
     * A {@link NativeImage}'s pixels as packed {@code 0xAARRGGBB}, copied out.
     *
     * <p>Through the public per-pixel reader rather than through the pixel block's
     * address: this runs once per retained picture, not per frame of a video, so the
     * bulk path {@code TextureBridge} exists for would buy nothing and the version guard
     * it carries would have to be written out a second time.</p>
     */
    private static int[] readArgb(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                //? if <1.21.4 {
                int abgr = image.getPixelRGBA(x, y);
                argb[y * width + x] = (abgr & 0xFF00FF00)
                        | ((abgr & 0x00FF0000) >>> 16)
                        | ((abgr & 0x000000FF) << 16);
                //?} else {
                /*argb[y * width + x] = image.getPixel(x, y);
                *///?}
            }
        }
        return argb;
    }

    private static void closeFrames(@Nullable GifDecoder.Result decoded) {
        if (decoded == null) {
            return;
        }
        for (NativeImage image : decoded.frames()) {
            if (image != null) {
                image.close();
            }
        }
    }

    public enum State {
        IDLE,
        LOADING,
        LOADED,
        FAILED
    }

    public static final class Entry {
        public State state = State.IDLE;
        /** Whether {@link #pixels} should be taken when this loads — see {@link #getOrLoad(String, boolean)}. */
        boolean keepPixels;
        /**
         * The first frame's pixels, kept only when something asked. Immutable from here
         * on; {@link #pixels()} copies again on the way out, so nobody can edit the copy
         * the next caller gets.
         */
        @Nullable
        SurfacePixels pixels;
        @Nullable
        ResourceLocation[] frames;
        int @Nullable [] frameDelaysMs;
        int totalDurationMs;
        long animationStartMs;
        public int width;
        public int height;
        public long estimatedSizeBytes;

        /**
         * The texture to draw right now. For a static image this is always the
         * single frame; for an animated GIF it is selected from the wall clock
         * so the animation plays at its intended speed and loops seamlessly.
         */
        @Nullable
        public ResourceLocation currentFrame() {
            if (frames == null || frames.length == 0) {
                return null;
            }
            if (frames.length == 1 || totalDurationMs <= 0 || frameDelaysMs == null) {
                return frames[0];
            }
            if (animationStartMs == 0L) {
                animationStartMs = System.currentTimeMillis();
            }
            long elapsed = (System.currentTimeMillis() - animationStartMs) % totalDurationMs;
            long accumulated = 0;
            for (int i = 0; i < frames.length; i++) {
                accumulated += frameDelaysMs[i];
                if (elapsed < accumulated) {
                    return frames[i];
                }
            }
            return frames[frames.length - 1];
        }

        /**
         * A fresh copy of the retained pixels, or {@code null} if none were kept or the
         * picture has not loaded. See {@code api.render.SurfacePixels} for why it is a
         * copy every time.
         */
        @Nullable
        public SurfacePixels pixels() {
            SurfacePixels kept = pixels;
            return kept == null ? null : new SurfacePixels(kept.width(), kept.height(),
                    kept.argb().clone());
        }

        public void releaseTexture() {
            if (frames != null) {
                for (ResourceLocation location : frames) {
                    if (location != null) {
                        TextureBridge.release(location);
                    }
                }
                frames = null;
            }
            frameDelaysMs = null;
            pixels = null;
            totalDurationMs = 0;
            animationStartMs = 0L;
            estimatedSizeBytes = 0L;
            state = State.IDLE;
        }
    }
}
