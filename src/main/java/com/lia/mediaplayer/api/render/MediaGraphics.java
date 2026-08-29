/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing a {@link MediaSurface} into a rectangle, without re-deriving the version guards
 * that go with it.
 *
 * <p>{@code GuiGraphics.blit} is one of the least stable methods in the client API — the
 * argument order moved in 1.21.2, and 1.21.6 replaced its render-type factory with a
 * blaze3d pipeline. The mod keeps exactly one guarded call site for it and this is how an
 * addon reaches that one, instead of carrying a copy of the same three-way guard.</p>
 *
 * <p>Both draw methods {@linkplain MediaSurface#markWanted() mark the surface wanted},
 * because drawing it is the definition of wanting it. A caller that draws its surfaces
 * through these therefore never has to think about back-pressure at all.</p>
 *
 * <p>A surface that is not ready draws nothing — no placeholder, no outline. What belongs
 * in a loading rectangle is the caller's decision, not this method's.</p>
 *
 * <p><b>Render thread only.</b></p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.0.0
 */
public final class MediaGraphics {

    private MediaGraphics() {
    }

    /**
     * Draws the surface centred in the rectangle, as large as fits, aspect ratio
     * preserved — letterboxed. What a video player does.
     */
    public static void draw(GuiGraphics graphics, MediaSurface surface,
                            int x, int y, int width, int height) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        if (api != null) {
            api.drawSurface(graphics, surface, x, y, width, height, false);
        }
    }

    /** Draws the surface filling the rectangle exactly, distorting it if it has to. */
    public static void drawStretched(GuiGraphics graphics, MediaSurface surface,
                                     int x, int y, int width, int height) {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        if (api != null) {
            api.drawSurface(graphics, surface, x, y, width, height, true);
        }
    }

    /**
     * The rectangle {@link #draw} would use inside a box, as
     * {@code {x, y, width, height}} — for laying other things out around it.
     *
     * <p>Falls back to the whole box for a surface whose size is not known yet, so a
     * layout built on it does not jump when the first frame arrives at a different aspect
     * ratio than nothing.</p>
     */
    public static int[] fit(MediaSurface surface, int boxX, int boxY, int boxW, int boxH) {
        float aspect = surface == null ? 0f : surface.aspectRatio();
        if (aspect <= 0f || boxW <= 0 || boxH <= 0) {
            return new int[]{boxX, boxY, Math.max(0, boxW), Math.max(0, boxH)};
        }
        int width = boxW;
        int height = Math.round(width / aspect);
        if (height > boxH) {
            height = boxH;
            width = Math.round(height * aspect);
        }
        return new int[]{boxX + (boxW - width) / 2, boxY + (boxH - height) / 2, width, height};
    }
}
