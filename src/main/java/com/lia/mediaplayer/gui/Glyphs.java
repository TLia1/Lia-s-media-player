package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Every icon the mod draws — transport controls, window controls, list controls — plus a
 * text-fitting helper. All of them are plain filled rectangles, so they need no textures,
 * scale with the GUI, and have nothing that can change shape between Minecraft versions.
 * They live together so a control is drawn the same way everywhere rather than being
 * re-implemented per window, and so the colours ({@link Theme}) are the only thing a call
 * site chooses.
 *
 * <p>Each glyph is drawn inside an {@code 11×11} button box whose top-left is
 * {@code (x, y)} (the {@link MediaWindow#BUTTON} size).</p>
 */
final class Glyphs {

    private static final int BUTTON = MediaWindow.BUTTON;

    private Glyphs() {
    }

    /**
     * A play triangle (paused) or two pause bars (playing).
     */
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

    /**
     * A "skip to next" glyph: a right-pointing triangle followed by a vertical bar.
     */
    static void next(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < BUTTON; i++) {
            int half = Math.min(i, BUTTON - 1 - i);
            int len = 1 + half;
            g.fill(x + 1, y + i, x + 1 + len, y + i + 1, color);
        }
        g.fill(x + BUTTON - 3, y, x + BUTTON - 1, y + BUTTON, color);
    }

    /**
     * A "skip to previous" glyph: a vertical bar followed by a left-pointing triangle.
     */
    static void previous(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y, x + 4, y + BUTTON, color);
        for (int i = 0; i < BUTTON; i++) {
            int half = Math.min(i, BUTTON - 1 - i);
            int len = 1 + half;
            g.fill(x + BUTTON - 1 - len, y + i, x + BUTTON - 1, y + i + 1, color);
        }
    }

    /**
     * A tiny speaker glyph; crossed out when muted.
     */
    static void speaker(GuiGraphics g, int x, int y, boolean muted, int color) {
        int midY = y + BUTTON / 2;
        g.fill(x + 1, midY - 2, x + 3, midY + 2, color);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 3, midY - 1 - i, x + 4 + i, midY + 1 + i, color);
        }
        if (muted) {
            g.fill(x + 7, y + 2, x + 8, y + 3, Theme.MUTED);
            g.fill(x + 9, y + 2, x + 10, y + 3, Theme.MUTED);
            g.fill(x + 8, midY, x + 9, midY + 1, Theme.MUTED);
            g.fill(x + 7, y + BUTTON - 3, x + 8, y + BUTTON - 2, Theme.MUTED);
            g.fill(x + 9, y + BUTTON - 3, x + 10, y + BUTTON - 2, Theme.MUTED);
        } else {
            g.fill(x + 7, midY - 2, x + 8, midY + 2, color);
            g.fill(x + 9, midY - 3, x + 10, midY + 3, color);
        }
    }

    /**
     * A "repeat" glyph: two bars running in opposite directions, each turning at one
     * end and tipped with an arrow head at the other, plus a "1" between them when only
     * the current track repeats ({@link RepeatMode#ONE}).
     *
     * <p>The art is 10×10 and is inset one pixel from the left, so it lines up with the
     * neighbouring glyphs (which run from {@code x + 1} to {@code x + 10}).</p>
     */
    static void loop(GuiGraphics g, int x, int y, boolean single, int color) {
        // Top bar, travelling right and turning down at its end...
        g.fill(x + 1, y + 1, x + 11, y + 2, color);
        g.fill(x + 10, y + 2, x + 11, y + 3, color);
        // ...with the arrow head it points at on the left.
        g.fill(x + 2, y, x + 3, y + 1, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);

        // Bottom bar, the same the other way round.
        g.fill(x + 1, y + 8, x + 11, y + 9, color);
        g.fill(x + 1, y + 7, x + 2, y + 8, color);
        g.fill(x + 9, y + 7, x + 10, y + 8, color);
        g.fill(x + 9, y + 9, x + 10, y + 10, color);

        if (single) {
            // A "1" in the middle: the stroke plus its flag.
            g.fill(x + 6, y + 3, x + 7, y + 7, color);
            g.fill(x + 5, y + 4, x + 6, y + 5, color);
        }
    }

    /**
     * A "shuffle" glyph: two crossing paths, each ending in an arrow head.
     */
    static void shuffle(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            g.fill(x + 1 + i, y + 2 + i, x + 2 + i, y + 3 + i, color); // top-left → bottom-right
            g.fill(x + 1 + i, y + 8 - i, x + 2 + i, y + 9 - i, color); // bottom-left → top-right
        }
        // Arrow heads on the two right-hand ends.
        g.fill(x + 6, y + 1, x + 10, y + 2, color);
        g.fill(x + 9, y + 1, x + 10, y + 4, color);
        g.fill(x + 6, y + 9, x + 10, y + 10, color);
        g.fill(x + 9, y + 7, x + 10, y + 10, color);
    }

    /**
     * A small music-note glyph (used for the audio bar / playlists button).
     */
    static void note(GuiGraphics g, int x, int y, int color) {
        // Stem + a filled note head at the bottom-left.
        g.fill(x + 6, y + 1, x + 7, y + 8, color);
        g.fill(x + 7, y + 1, x + 9, y + 3, color);     // flag
        g.fill(x + 3, y + 6, x + 7, y + 9, color);     // head
    }

    /**
     * A cross, for anything that closes or removes ("×").
     */
    static void close(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, color); // ↘
            g.fill(x + 8 - i, y + 2 + i, x + 9 - i, y + 3 + i, color); // ↙
        }
    }

    /**
     * A single low bar, for "hide this window" ("_").
     */
    static void minimize(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 7, x + 9, y + 9, color);
    }

    /**
     * An "open in the browser" arrow: a diagonal shaft pointing up and to the right.
     */
    static void externalLink(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + 2 + i, y + 8 - i, x + 3 + i, y + 9 - i, color);
        }
        // Arrow head at the top-right corner.
        g.fill(x + 5, y + 2, x + 9, y + 3, color);
        g.fill(x + 8, y + 2, x + 9, y + 6, color);
    }

    /**
     * A "playlist" glyph: three stacked lines, each with a bullet on its left.
     */
    static void queue(GuiGraphics g, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            int ry = y + 1 + row * 4;
            g.fill(x + 1, ry, x + 3, ry + 2, color);          // bullet
            g.fill(x + 4, ry, x + BUTTON - 1, ry + 1, color); // line
        }
    }

    /**
     * A filled square: stop.
     */
    static void stop(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 2, x + 9, y + 9, color);
    }

    /**
     * A narrow speaker with a {@code +} or a {@code −} beside it: raise / lower volume.
     */
    static void volume(GuiGraphics g, int x, int y, boolean up, int color) {
        int midY = y + BUTTON / 2;
        // A half-width speaker, so the sign fits in the same box.
        g.fill(x, midY - 2, x + 2, midY + 2, color);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 2, midY - 1 - i, x + 3 + i, midY + 1 + i, color);
        }
        g.fill(x + 7, midY - 1, x + 11, midY + 1, color);
        if (up) {
            g.fill(x + 8, midY - 3, x + 10, midY + 3, color);
        }
    }

    /**
     * Two triangles pointing right: playback speed.
     */
    static void speed(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < BUTTON; i++) {
            int len = 1 + Math.min(i, BUTTON - 1 - i);
            g.fill(x, y + i, x + len, y + i + 1, color);
            g.fill(x + 5, y + i, x + 5 + len, y + i + 1, color);
        }
    }

    /**
     * A push-pin: "keep this window where it is".
     */
    static void pin(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 1, x + 8, y + 3, color);  // head
        g.fill(x + 4, y + 3, x + 7, y + 7, color);  // body
        g.fill(x + 2, y + 7, x + 9, y + 8, color);  // shoulder
        g.fill(x + 5, y + 8, x + 6, y + 11, color); // needle
    }

    /**
     * Four corner brackets: enter (or leave) full screen.
     */
    static void fullscreen(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 5, y + 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 5, color);
        g.fill(x + 6, y + 1, x + 10, y + 2, color);
        g.fill(x + 9, y + 1, x + 10, y + 5, color);
        g.fill(x + 1, y + 9, x + 5, y + 10, color);
        g.fill(x + 1, y + 6, x + 2, y + 10, color);
        g.fill(x + 6, y + 9, x + 10, y + 10, color);
        g.fill(x + 9, y + 6, x + 10, y + 10, color);
    }

    /**
     * A magnifying glass: search / filter. At this size a hollow square reads as the
     * lens, so the ring is drawn as four sides rather than a circle.
     */
    static void search(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 8, y + 2, color);
        g.fill(x + 1, y + 7, x + 8, y + 8, color);
        g.fill(x + 1, y + 2, x + 2, y + 7, color);
        g.fill(x + 7, y + 2, x + 8, y + 7, color);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 7 + i, y + 7 + i, x + 9 + i, y + 9 + i, color); // handle
        }
    }

    /**
     * A waste bin: delete for good (as opposed to {@link #close}, which dismisses).
     */
    static void trash(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 4, y + 1, x + 7, y + 2, color); // handle
        g.fill(x + 2, y + 2, x + 9, y + 3, color); // lid
        g.fill(x + 3, y + 4, x + 4, y + 10, color);
        g.fill(x + 7, y + 4, x + 8, y + 10, color);
        g.fill(x + 5, y + 5, x + 6, y + 9, color); // middle slat
        g.fill(x + 3, y + 9, x + 8, y + 10, color);
    }

    /**
     * Two columns of dots: the part of a row you grab to drag it elsewhere.
     */
    static void dragHandle(GuiGraphics g, int x, int y, int color) {
        for (int row = 0; row < 3; row++) {
            int ry = y + 2 + row * 3;
            g.fill(x + 3, ry, x + 5, ry + 2, color);
            g.fill(x + 6, ry, x + 8, ry + 2, color);
        }
    }

    /**
     * A solid heart: favourites. Like the loop and shuffle toggles it has one shape and
     * two colours ({@link Theme#ICON_ACTIVE} / {@link Theme#ICON_INACTIVE}) rather than
     * a filled and a hollow variant, which do not read apart at eleven pixels.
     */
    static void heart(GuiGraphics g, int x, int y, int color) {
        // Two lobes...
        g.fill(x + 2, y + 2, x + 4, y + 3, color);
        g.fill(x + 7, y + 2, x + 9, y + 3, color);
        g.fill(x + 1, y + 3, x + 10, y + 5, color);
        // ...narrowing to the point.
        for (int i = 0; i < 4; i++) {
            g.fill(x + 1 + i, y + 5 + i, x + 10 - i, y + 6 + i, color);
        }
    }

    /**
     * A small arrow pointing up or down.
     */
    static void arrow(GuiGraphics g, int x, int y, boolean up, int color) {
        int tx = x + 3;
        int ty = y + 4;
        for (int i = 0; i < 3; i++) {
            int w = 1 + i * 2;
            int px = tx + 2 - i;
            int py = up ? ty + i : ty + 2 - i;
            g.fill(px, py, px + w, py + 1, color);
        }
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
