package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

//? if <1.21.5 {
import java.lang.reflect.Field;
import org.jetbrains.annotations.Nullable;
//?}

/**
 * The mod's single point of contact with Minecraft's texture upload path, and the
 * companion to {@link Blit}: {@code Blit} draws a texture, this creates one and
 * pushes pixels into it.
 *
 * <p>Two things move underneath it. 1.21.5 gave {@link DynamicTexture} a mandatory
 * debug label and rebuilt {@code upload()} on top of the blaze3d {@code GpuDevice},
 * and it also made the address of a {@link NativeImage}'s pixel block reachable
 * through the public {@code getPointer()} instead of only by reflecting on a
 * private field. Everything the mod uploads — video frames, video thumbnails and
 * image previews — goes through this class, so those breaks are guarded once.
 *
 * <p>There is deliberately no interface with one implementation per version here.
 * Stonecutter resolves the version at generation time, so exactly one variant is
 * ever compiled and runtime dispatch would buy nothing.
 */
public final class TextureBridge {

    //? if <1.21.5 {
    /**
     * {@code NativeImage.pixels}, the address of the native pixel block.
     *
     * <p>This is the least defensible thing in the mod: a private field that is
     * part of no API contract, reached by name. 1.21.5 and later use the public
     * {@code getPointer()} and do not compile this at all. Where it is still
     * needed, a failure to reach it leaves the field null and every upload turns
     * into a no-op against a zero-filled texture, rather than throwing from the
     * middle of the render loop.
     */
    @Nullable
    private static final Field NATIVE_IMAGE_PIXELS = findPixelsField();

    @Nullable
    private static Field findPixelsField() {
        try {
            Field field = NativeImage.class.getDeclaredField("pixels");
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            LiasMediaPlayer.LOGGER.error(
                    "NativeImage.pixels is unreachable on this Minecraft build; video will render blank.", e);
            return null;
        }
    }
    //?}

    /**
     * Textures asked to go away, waiting for a moment when nothing is looking at them.
     * See {@link #release}.
     */
    private static final List<ResourceLocation> PENDING_RELEASE = new ArrayList<>();

    private TextureBridge() {
    }

    /**
     * Registers {@code image} under {@code location} as a texture that is uploaded
     * once and never rewritten. The texture manager takes ownership: releasing the
     * location closes both the texture and the image.
     */
    public static void register(ResourceLocation location, NativeImage image) {
        Minecraft.getInstance().getTextureManager().register(location, newTexture(location, image));
    }

    /**
     * Drops the texture registered under {@code location} — <em>not now</em>, but at the
     * next {@link #flushReleases()}, which is once a client tick.
     *
     * <h4>Why it cannot be now</h4>
     *
     * <p>From 26.1 the GUI is no longer drawn as it is walked: a screen (and every
     * mod hook that draws over one) <em>extracts</em> its draw commands, and
     * {@code GuiRenderer} executes the whole frame's worth afterwards. A command that
     * names a texture holds a view of it, so closing that texture between the extraction
     * and the execution kills the frame with
     * {@code IllegalStateException: Texture view Sampler0 (…) has been closed!} — from
     * inside vanilla's renderer, with nothing of the mod on the stack to say who did it.
     *
     * <p>And the mod does close textures from inside a draw. Three places, all of them
     * legitimate: {@code VideoRenderer.uploadFrame} reallocates when the frame size
     * changes and the frames are pulled off the queue by the window's own draw;
     * {@code VideoThumbnailCache} evicts its eldest entry when a list asks for a
     * thumbnail it does not hold; and {@code ImagePreviewCache} trims itself to its
     * memory limit when a preview finishes loading. Each of those can free a texture
     * that an earlier draw in the same frame is still pointing at.
     *
     * <p>Deferring by one tick makes the whole class of failure impossible instead of
     * auditing the call sites one by one, and it is safe because every location the mod
     * registers comes from a monotonic counter: a released location is never registered
     * again, so a pending release can never take a live texture with it. The cost is
     * that a dropped texture outlives its owner by up to 50 ms — which nothing draws,
     * because the caller clears its reference the moment it asks for the release.
     */
    public static void release(ResourceLocation location) {
        synchronized (PENDING_RELEASE) {
            PENDING_RELEASE.add(location);
        }
    }

    /**
     * Frees everything {@link #release} has queued. Called once a client tick, which is
     * between two frames — the only moment at which no draw command can still be holding
     * one of these textures.
     */
    public static void flushReleases() {
        List<ResourceLocation> due;
        synchronized (PENDING_RELEASE) {
            if (PENDING_RELEASE.isEmpty()) {
                return;
            }
            due = new ArrayList<>(PENDING_RELEASE);
            PENDING_RELEASE.clear();
        }
        for (ResourceLocation location : due) {
            try {
                Minecraft.getInstance().getTextureManager().release(location);
            } catch (RuntimeException e) {
                // Freeing a texture must never be the thing that takes the game down.
                LiasMediaPlayer.LOGGER.debug("Could not release texture {}", location, e);
            }
        }
    }

    /**
     * Both {@link DynamicTexture} constructors upload the image as they build it,
     * so a texture is complete and drawable the moment this returns.
     */
    private static DynamicTexture newTexture(ResourceLocation location, NativeImage image) {
        //? if <1.21.5 {
        return new DynamicTexture(image);
        //?} else {
        /*// 1.21.5 requires a label, used to name the underlying GpuTexture in
        // graphics debuggers and in the "trying to upload disposed texture"
        // warning. The location is the most useful thing to see there.
        return new DynamicTexture(location::toString, image);
        *///?}
    }

    /**
     * The address of {@code image}'s pixel block, or {@code 0} if it cannot be
     * obtained — which only happens on the legacy path, when the reflection on
     * {@code NativeImage.pixels} failed.
     */
    private static long pixelAddress(NativeImage image) {
        //? if <1.21.5 {
        if (NATIVE_IMAGE_PIXELS == null) {
            return 0L;
        }
        try {
            return NATIVE_IMAGE_PIXELS.getLong(image);
        } catch (IllegalAccessException e) {
            return 0L;
        }
        //?} else {
        /*return image.getPointer();
        *///?}
    }

    /**
     * Writes a whole {@code TYPE_INT_ARGB} pixel array into {@code image}'s native pixel
     * block in one pass.
     *
     * <p>This exists because the obvious way to fill a {@link NativeImage} — a nested
     * {@code x}/{@code y} loop calling {@code setPixel} — costs a bounds-checked
     * multiply and a method call for every pixel, and the mod does it for every frame of
     * every animated GIF. A 500x500 GIF at the default frame limit is sixty-four million
     * of those. A flat loop writing through the pixel block's own address is the same
     * work with none of the overhead, and it is a shape the JIT can vectorise.</p>
     *
     * <p>The channel shuffle is unconditional, unlike the per-pixel calls it replaces:
     * {@code Format.RGBA} lays bytes out as R,G,B,A, which read back as {@code 0xAABBGGRR}
     * on a little-endian machine, so a bulk write always produces ABGR whatever version
     * of {@code setPixel} would have been used. (Minecraft has no big-endian target;
     * {@code NativeImage} itself assumes the same thing.)</p>
     *
     * @return whether the write happened. {@code false} only on the legacy path when the
     *         reflection on {@code NativeImage.pixels} failed, and the caller should
     *         then fall back to per-pixel writes rather than ship a blank image.
     */
    public static boolean writeArgb(NativeImage image, int[] argb) {
        long destination = pixelAddress(image);
        if (destination == 0L) {
            return false;
        }
        IntBuffer pixels = MemoryUtil.memIntBuffer(destination, argb.length);
        for (int i = 0; i < argb.length; i++) {
            int pixel = argb[i];
            pixels.put(i, (pixel & 0xFF00FF00)
                    | ((pixel & 0x00FF0000) >>> 16)
                    | ((pixel & 0x000000FF) << 16));
        }
        return true;
    }

    /**
     * A texture whose contents are replaced wholesale, frame after frame, from a
     * native RGBA buffer produced outside Minecraft.
     *
     * <p>This exists to keep the decoder's buffer from being copied twice. Video
     * playback uploads at frame rate, so {@link #upload} writes the ffmpeg buffer
     * straight into the texture's pixel block and then hands it to the GPU.
     */
    public static final class Frame implements AutoCloseable {
        private final ResourceLocation location;
        private final NativeImage image;
        private final DynamicTexture texture;
        private final int width;
        private final int height;

        private Frame(ResourceLocation location, NativeImage image, DynamicTexture texture, int width, int height) {
            this.location = location;
            this.image = image;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }

        /**
         * Allocates a {@code width} x {@code height} RGBA texture and registers it
         * under {@code location}. The pixel block is zeroed, so the texture reads
         * as fully transparent until the first successful {@link #upload}.
         */
        public static Frame allocate(ResourceLocation location, int width, int height) {
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, true);
            DynamicTexture texture = newTexture(location, image);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            return new Frame(location, image, texture, width, height);
        }

        public ResourceLocation location() {
            return location;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /**
         * Replaces the whole texture with {@code rgba}, which must be a direct
         * buffer of exactly {@code width * height * 4} bytes.
         *
         * @return whether the frame reached the GPU; a rejected frame leaves the
         *         previous one on screen rather than tearing or crashing.
         */
        public boolean upload(ByteBuffer rgba) {
            // The length is taken from the destination, not from the source: this
            // writes into a native pixel block, so trusting the incoming capacity
            // would turn any mismatch between frame size and texture size into an
            // out-of-bounds write rather than a visible glitch.
            long expectedBytes = (long) width * height * 4L;
            if (rgba.capacity() != expectedBytes) {
                LiasMediaPlayer.LOGGER.warn("Skipping frame: {} bytes for a {}x{} texture",
                        rgba.capacity(), width, height);
                return false;
            }
            long destination = pixelAddress(image);
            if (destination == 0L) {
                return false;
            }
            MemoryUtil.memCopy(MemoryUtil.memAddress(rgba), destination, expectedBytes);
            texture.upload();
            return true;
        }

        /** Unregisters the texture and frees both it and its pixel block. */
        @Override
        public void close() {
            // Releasing is enough: TextureManager.release closes what it drops, and
            // DynamicTexture.close closes the NativeImage with it. Closing the
            // texture again here would be a no-op, not a second free.
            release(location);
        }
    }
}
