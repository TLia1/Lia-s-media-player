package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tiny pixel-art control glyphs (play/pause, skip, speaker, …) and a text-fitting
 * helper, drawn with plain filled rectangles so they need no textures and scale with
 * the GUI. Shared by the player windows so each control is drawn the same way in one
 * place rather than re-implemented per window.
 *
 * <p>Each glyph is drawn inside an {@code 11×11} button box whose top-left is
 * {@code (x, y)} (the {@link MediaWindow#BUTTON} size).</p>
 */
final class Glyphs {

    private static final int BUTTON = MediaWindow.BUTTON;

    private Glyphs() {
    }

    /** A play triangle (paused) or two pause bars (playing). */
    static void playPause(GuiGraphics g, int x, int y, boolean playing, int color) {
        if (playing) {
            g.fill(x + 1, y, x + 4, y + BUTTON, color);
            g.fill(x + 7, y, x + 10, y + BUTTON, color);
        } else {
            for (int i = 0; i < BUTTON; i++) {
                int half = Math.min(i, BUTTON - 1 - i);
                int len = 2 + half;
                g.fill(x + 2, y + i, x + 2 + len, y + i + 1, color);
            }
        }
    }

    /** A "skip to next" glyph: a right-pointing triangle followed by a vertical bar. */
    static void next(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < BUTTON; i++) {
            int half = Math.min(i, BUTTON - 1 - i);
            int len = 1 + half;
            g.fill(x + 1, y + i, x + 1 + len, y + i + 1, color);
        }
        g.fill(x + BUTTON - 3, y, x + BUTTON - 1, y + BUTTON, color);
    }

    /** A "skip to previous" glyph: a vertical bar followed by a left-pointing triangle. */
    static void previous(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 4, y + BUTTON, color);
        for (int i = 0; i < BUTTON; i++) {
            int half = Math.min(i, BUTTON - 1 - i);
            int len = 1 + half;
            g.fill(x + BUTTON - 1 - len, y + i, x + BUTTON - 1, y + i + 1, color);
        }
    }

    /** A tiny speaker glyph; crossed out when muted. */
    static void speaker(GuiGraphics g, int x, int y, boolean muted, int color) {
        int midY = y + BUTTON / 2;
        g.fill(x + 1, midY - 2, x + 3, midY + 2, color);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 3, midY - 1 - i, x + 4 + i, midY + 1 + i, color);
        }
        if (muted) {
            g.fill(x + 7, y + 2, x + 8, y + 3, 0xFFFF5555);
            g.fill(x + 9, y + 2, x + 10, y + 3, 0xFFFF5555);
            g.fill(x + 8, midY, x + 9, midY + 1, 0xFFFF5555);
            g.fill(x + 7, y + BUTTON - 3, x + 8, y + BUTTON - 2, 0xFFFF5555);
            g.fill(x + 9, y + BUTTON - 3, x + 10, y + BUTTON - 2, 0xFFFF5555);
        } else {
            g.fill(x + 7, midY - 2, x + 8, midY + 2, color);
            g.fill(x + 9, midY - 3, x + 10, midY + 3, color);
        }
    }

    /** A small music-note glyph (used for the audio bar / playlists button). */
    static void note(GuiGraphics g, int x, int y, int color) {
        // Stem + a filled note head at the bottom-left.
        g.fill(x + 6, y + 1, x + 7, y + 8, color);
        g.fill(x + 7, y + 1, x + 9, y + 3, color);     // flag
        g.fill(x + 3, y + 6, x + 7, y + 9, color);     // head
    }

    /**
     * Truncates {@code text} with an ellipsis so it fits within {@code maxWidth} pixels.
     */
    static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(text, limit) + ellipsis;
    }
}
