package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Only referenced by the 1.21.2-1.21.5 branch below. 1.21.11 moved the class to
// net.minecraft.client.renderer.rendertype, so importing it unconditionally
// would break a version that never uses it.
//? if >=1.21.2 && <1.21.6
/*import net.minecraft.client.renderer.RenderType;*/

//? if >=1.21.6
/*import net.minecraft.client.renderer.RenderPipelines;*/

/**
 * The mod's single point of contact with {@link GuiGraphics}'s textured blit.
 *
 * <p>{@code GuiGraphics.blit} is one of the least stable methods in the client
 * API: 1.21.2 moved the destination and source sizes around and prepended a
 * {@code RenderType} factory, and 1.21.6 swapped that factory for a blaze3d
 * {@code RenderPipeline}. Every call site in the mod draws the same thing — a
 * whole texture, scaled into a rectangle — so they all go through this one
 * method and only this file needs a version guard.
 */
final class Blit {
    private Blit() {
    }

    /**
     * Draws the whole of {@code texture} (its natural size being
     * {@code textureWidth} x {@code textureHeight}) scaled into the rectangle at
     * {@code (x, y)} of size {@code width} x {@code height}.
     */
    static void textured(GuiGraphics g, ResourceLocation texture,
                         int x, int y, int width, int height,
                         int textureWidth, int textureHeight) {
        //? if <1.21.2 {
        g.blit(texture, x, y, width, height, 0.0f, 0.0f,
                textureWidth, textureHeight, textureWidth, textureHeight);
        //?} elif <1.21.6 {
        /*// 1.21.2 reordered the arguments: the destination size moved ahead of the
        // source region size, and a RenderType factory became the first argument.
        g.blit(RenderType::guiTextured, texture, x, y, 0.0f, 0.0f,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
        *///?} else {
        /*// 1.21.6 kept that argument order but replaced the RenderType factory with
        // a blaze3d RenderPipeline: GUI drawing no longer goes through the
        // immediate-mode buffer source, so there is no render type to pick.
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
        *///?}
    }
}
