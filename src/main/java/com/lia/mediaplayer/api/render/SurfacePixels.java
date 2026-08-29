/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

/**
 * A copy of a surface's decoded pixels, for an addon that wants to <em>read</em> a
 * picture rather than draw it — the usual reason being to pull a colour out of it and
 * theme something else to match.
 *
 * <p><b>A copy, and yours.</b> {@link MediaSurface#pixels()} allocates a fresh array
 * every call rather than handing out the buffer the texture was uploaded from. That
 * buffer is native memory the texture manager owns; lending it out would make every
 * addon a potential use-after-free, and the alternative — a shared read-only view — is
 * only safe until the cache evicts the entry underneath it. Call it once and keep what
 * you get, not the surface.</p>
 *
 * <p>Packed {@code 0xAARRGGBB}, row-major, {@code width * height} long.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.4.0
 */
public record SurfacePixels(int width, int height, int[] argb) {

    /** The pixel at {@code (x, y)}, or {@code 0} outside the picture. */
    public int at(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return argb[y * width + x];
    }

    /**
     * The average colour, ignoring fully transparent pixels — the one-line answer to
     * "what colour is this picture?", which is what most callers of this actually want.
     *
     * <p>{@code 0} for a picture that is entirely transparent. The alpha of the result is
     * the average alpha of what was counted, so a mostly-transparent logo does not come
     * back as an opaque grey.</p>
     */
    public int averageColor() {
        long a = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        long counted = 0;
        for (int pixel : argb) {
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha == 0) {
                continue;
            }
            a += alpha;
            r += (pixel >>> 16) & 0xFF;
            g += (pixel >>> 8) & 0xFF;
            b += pixel & 0xFF;
            counted++;
        }
        if (counted == 0) {
            return 0;
        }
        return (int) ((a / counted) << 24 | (r / counted) << 16 | (g / counted) << 8 | (b / counted));
    }
}
