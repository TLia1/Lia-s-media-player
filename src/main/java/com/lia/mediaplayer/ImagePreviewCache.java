package com.lia.mediaplayer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
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
 */
final class ImagePreviewCache {
    /** Mirrors ChatComponent.MAX_CHAT_HISTORY. */
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            if (size() > MAX_ENTRIES) {
                eldest.getValue().releaseTexture();
                return true;
            }
            return false;
        }
    };

    private ImagePreviewCache() {
    }

    /** Registers a URL seen in chat so its preview can be loaded lazily later. */
    static void track(String url) {
        CACHE.computeIfAbsent(url, u -> new Entry());
    }

    /**
     * Returns the entry for the URL, starting an asynchronous download the
     * first time it is requested. Check {@link Entry#state} to know whether
     * the texture is ready.
     */
    static Entry getOrLoad(String url) {
        Entry entry = CACHE.computeIfAbsent(url, u -> new Entry());
        if (entry.state == State.IDLE) {
            startLoading(url, entry);
        }
        return entry;
    }

    /** Drops every cached preview (e.g. when leaving a server). */
    static void clear() {
        CACHE.values().forEach(Entry::releaseTexture);
        CACHE.clear();
    }

    private static void startLoading(String url, Entry entry) {
        entry.state = State.LOADING;
        LiasMediaPlayer.LOGGER.info("Loading image preview from {}", url);

        CompletableFuture
                .supplyAsync(() -> download(url), Util.ioPool())
                .whenCompleteAsync((decoded, error) -> onDownloadComplete(url, entry, decoded, error),
                        Minecraft.getInstance());
    }

    /** Runs on the IO pool — never touch GL or the cache from here. */
    private static GifDecoder.Result download(String url) {
        try {
            // Tenor share links are HTML pages; resolve them to the real GIF first.
            String mediaUrl = TenorResolver.isTenorPageUrl(url) ? TenorResolver.resolve(url) : url;
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
        if (isGif(data)) {
            return GifDecoder.decode(data);
        }

        NativeImage single;
        if (isPng(data)) {
            single = NativeImage.read(new ByteArrayInputStream(data));
        } else {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
            if (decoded == null) {
                throw new IOException("Unsupported image format");
            }
            single = GifDecoder.toNativeImage(toArgb(decoded));
        }
        return new GifDecoder.Result(new NativeImage[]{single}, new int[]{0});
    }

    /** Ensures the image is TYPE_INT_ARGB so getRGB yields packed ARGB ints. */
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

    /** Runs on the main thread — safe to create GL textures and mutate the cache. */
    private static void onDownloadComplete(String url, Entry entry, @Nullable GifDecoder.Result decoded,
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
            if (CACHE.get(url) != entry) {
                closeFrames(decoded);
                return;
            }

            NativeImage[] images = decoded.frames();
            ResourceLocation[] locations = new ResourceLocation[images.length];
            for (int i = 0; i < images.length; i++) {
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        LiasMediaPlayer.MODID, "preview/" + TEXTURE_ID.getAndIncrement());
                Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(images[i]));
                locations[i] = location;
            }

            int total = 0;
            for (int delay : decoded.delaysMs()) {
                total += delay;
            }

            entry.frames = locations;
            entry.frameDelaysMs = decoded.delaysMs();
            entry.totalDurationMs = total;
            entry.animationStartMs = 0L;
            entry.width = images[0].getWidth();
            entry.height = images[0].getHeight();
            entry.state = State.LOADED;
            LiasMediaPlayer.LOGGER.info("Loaded image preview {}x{} ({} frame(s)) from {}",
                    entry.width, entry.height, images.length, url);
        } catch (Exception e) {
            entry.state = State.FAILED;
            closeFrames(decoded);
            LiasMediaPlayer.LOGGER.warn("Failed to create preview texture for {}", url, e);
        }
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

    enum State {
        IDLE,
        LOADING,
        LOADED,
        FAILED
    }

    static final class Entry {
        State state = State.IDLE;
        @Nullable
        ResourceLocation[] frames;
        int @Nullable [] frameDelaysMs;
        int totalDurationMs;
        long animationStartMs;
        int width;
        int height;

        /**
         * The texture to draw right now. For a static image this is always the
         * single frame; for an animated GIF it is selected from the wall clock
         * so the animation plays at its intended speed and loops seamlessly.
         */
        @Nullable
        ResourceLocation currentFrame() {
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

        void releaseTexture() {
            if (frames != null) {
                for (ResourceLocation location : frames) {
                    if (location != null) {
                        Minecraft.getInstance().getTextureManager().release(location);
                    }
                }
                frames = null;
            }
            frameDelaysMs = null;
            totalDurationMs = 0;
            animationStartMs = 0L;
            state = State.IDLE;
        }
    }
}
