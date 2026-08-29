package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.gui.Blit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a {@link MediaSurface} into a rectangle — what {@code api.render.MediaGraphics}
 * delegates to.
 *
 * <p>It lives here rather than in {@code api} for the reason everything with behaviour
 * does: the API package imports nothing from the mod, and the blit it needs is
 * {@link Blit}, the mod's single guarded contact point with a method whose signature has
 * moved twice since 1.21.1.</p>
 *
 * <p>Render thread only.</p>
 */
public final class SurfaceRenderer {

    private SurfaceRenderer() {
    }

    /**
     * @param stretch {@code true} to fill the rectangle exactly, {@code false} to
     *                letterbox the picture inside it at its own aspect ratio
     */
    public static void draw(GuiGraphics graphics, MediaSurface surface,
                            int x, int y, int width, int height, boolean stretch) {
        if (surface == null || width <= 0 || height <= 0) {
            return;
        }
        // Drawing it is the definition of wanting it, so a caller that draws through the
        // API never has to think about the back-pressure switch at all.
        surface.markWanted();
        ResourceLocation texture = surface.texture();
        if (texture == null) {
            return; // loading, failed, or the world unloaded: what goes here is the caller's call
        }
        int sourceW = Math.max(1, surface.sourceWidth());
        int sourceH = Math.max(1, surface.sourceHeight());
        int drawX = x;
        int drawY = y;
        int drawW = width;
        int drawH = height;
        if (!stretch) {
            drawH = Math.round(drawW * (float) sourceH / sourceW);
            if (drawH > height) {
                drawH = height;
                drawW = Math.round(drawH * (float) sourceW / sourceH);
            }
            drawX = x + (width - drawW) / 2;
            drawY = y + (height - drawH) / 2;
        }
        Blit.textured(graphics, texture, drawX, drawY, drawW, drawH, sourceW, sourceH);
    }
}
