package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's single point of contact with {@link GuiGraphics}'s textured blit.
 *
 * <p>{@code GuiGraphics.blit} is one of the least stable methods in the client
 * API: 1.21.2 moved the destination and source sizes around and prepended a
 * {@link RenderType} factory, and 1.21.6 moves it again onto the blaze3d render
 * pipeline. Every call site in the mod draws the same thing — a whole texture,
 * scaled into a rectangle — so they all go through this one method and only this
 * file needs a version guard.
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
        //?} else {
        /*// 1.21.2 reordered the arguments: the destination size moved ahead of the
        // source region size, and a RenderType factory became the first argument.
        g.blit(RenderType::guiTextured, texture, x, y, 0.0f, 0.0f,
                width, height, textureWidth, textureHeight, textureWidth, textureHeight);
        *///?}
    }
}
