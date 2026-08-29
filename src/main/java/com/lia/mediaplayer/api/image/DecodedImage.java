/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.image;

import java.util.List;

/**
 * What an {@link ImageDecoder} hands back: one or more fully composited frames, as
 * packed {@code 0xAARRGGBB} pixels in row-major order.
 *
 * <p><b>Composited, not deltas.</b> Every frame must be a complete picture the mod can
 * upload as its own texture; that is how the built-in GIF decoder works and it is what
 * lets the renderer blit a frame without knowing anything about the format. Disposal
 * methods, transparency indices and frame offsets are the decoder's problem, not the
 * mod's.</p>
 *
 * <p>Plain {@code int[]}s rather than a {@code NativeImage} on purpose: a decoder runs on
 * the IO pool, where allocating native memory the render thread will have to free is a
 * leak waiting for an exception on the way. The mod copies these into its own textures on
 * the render thread and the arrays are then free to be collected.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param width    pixels across; every frame has this width
 * @param height   pixels down
 * @param frames   one {@code width * height} array per frame, in play order; at least one
 * @param delaysMs how long each frame is shown, same length as {@code frames}. Ignored
 *                 for a single frame; a still image passes {@code {0}}.
 * @since API 3.4.0
 */
public record DecodedImage(int width, int height, List<int[]> frames, int[] delaysMs) {

    /**
     * @throws IllegalArgumentException if the dimensions are not positive, if there is no
     *                                  frame, if the two lists disagree in length, or if
     *                                  a frame is not {@code width * height} pixels. The
     *                                  mod checks rather than trusts: these arrays become
     *                                  the length of a native write.
     */
    public DecodedImage {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("A decoded image must have a positive size, got "
                    + width + "x" + height);
        }
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("A decoded image must have at least one frame");
        }
        if (delaysMs == null || delaysMs.length != frames.size()) {
            throw new IllegalArgumentException("Expected one delay per frame, got "
                    + (delaysMs == null ? "none" : String.valueOf(delaysMs.length))
                    + " for " + frames.size() + " frame(s)");
        }
        int expected = width * height;
        for (int[] frame : frames) {
            if (frame == null || frame.length != expected) {
                throw new IllegalArgumentException("Every frame must be " + expected
                        + " pixels for a " + width + "x" + height + " image");
            }
        }
        frames = List.copyOf(frames);
        delaysMs = delaysMs.clone();
    }

    /** A single still picture. */
    public static DecodedImage still(int width, int height, int[] argb) {
        return new DecodedImage(width, height, List.of(argb), new int[]{0});
    }

    /** How many frames there are; {@code 1} for a still. */
    public int frameCount() {
        return frames.size();
    }
}
