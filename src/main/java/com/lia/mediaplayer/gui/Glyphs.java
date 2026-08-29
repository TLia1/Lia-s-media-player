package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.RepeatMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

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
     * Two stacked sheets: copy this to the clipboard.
     *
     * <p>The back sheet is drawn as an outline and the front one filled, because at
     * eleven pixels two outlines overlap into a grid — the pair only reads as "one thing
     * and its copy" when one of them is solid.</p>
     */
    static void copy(GuiGraphics g, int x, int y, int color) {
        // Back sheet, top-left: three sides of a rectangle (the fourth is behind the
        // front sheet and would only thicken the seam).
        g.fill(x + 1, y + 1, x + 7, y + 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 7, color);
        g.fill(x + 1, y + 6, x + 4, y + 7, color);
        // Front sheet, bottom-right.
        g.fill(x + 4, y + 4, x + 10, y + 10, color);
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
     * "Add this to a playlist": the {@link #queue} list with its bottom line cut short
     * for a plus in the corner.
     *
     * <p>A bare plus was what this used to be, and a bare plus says "add" without saying
     * add to <em>what</em> — beside a heart that also adds the entry to something, that
     * is the whole question. Reusing the list glyph answers it at a glance.</p>
     */
    static void addToPlaylist(GuiGraphics g, int x, int y, int color) {
        // The list, narrowed to the left half to leave the plus a corner of its own —
        // the two shapes have to stay visibly separate at this size or they read as one
        // scribble.
        for (int row = 0; row < 3; row++) {
            int ry = y + 1 + row * 4;
            g.fill(x, ry, x + 2, ry + 2, color);     // bullet
            g.fill(x + 3, ry, x + 6, ry + 1, color); // line
        }
        g.fill(x + 7, y + 4, x + BUTTON, y + 6, color); // plus, horizontal
        g.fill(x + 8, y + 3, x + 10, y + 7, color);     // plus, vertical
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
     * A circular arrow: try that again (a failed playback's retry, a tool re-download).
     *
     * <p>Drawn as an almost-closed ring of pixels with an arrow head on the top-right
     * end, rather than as a second {@link #loop} — the two mean different things and a
     * player has to tell them apart at a glance in the same control bar.</p>
     */
    static void refresh(GuiGraphics g, int x, int y, int color) {
        // The ring: a circle of radius 4 about the middle of the button box, with the
        // top-right eighth left open for the arrow head.
        int cx = x + 5;
        int cy = y + 5;
        for (int i = 0; i < 8; i++) {
            if (i == 7) {
                continue; // the gap the arrow head sits in
            }
            g.fill(cx + SPIN_X[i] - 5, cy + SPIN_Y[i] - 5,
                    cx + SPIN_X[i] - 3, cy + SPIN_Y[i] - 3, color);
        }
        // Arrow head, pointing clockwise into the gap.
        g.fill(x + 7, y, x + 11, y + 1, color);
        g.fill(x + 10, y, x + 11, y + 4, color);
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
     * The heart's shape, as {@code {row, first column, last column}} spans inside the
     * 11x11 button box. Both hearts are drawn from this one description, so the filled
     * and the hollow one cannot drift apart by a pixel.
     */
    private static final int[][] HEART_SPANS = {
            {2, 2, 4}, {2, 6, 8},                 // the two lobes
            {3, 1, 9}, {4, 1, 9}, {5, 1, 9},      // the body
            {6, 2, 8}, {7, 3, 7}, {8, 4, 6}, {9, 5, 5}      // narrowing to the point
    };

    /** The pixels of {@link #HEART_SPANS} that have a side facing outwards. */
    private static final int[][] HEART_OUTLINE = outlineOf(HEART_SPANS);

    /**
     * A solid heart: this one is kept.
     */
    static void heart(GuiGraphics g, int x, int y, int color) {
        for (int[] span : HEART_SPANS) {
            g.fill(x + span[1], y + span[0], x + span[2] + 1, y + span[0] + 1, color);
        }
    }

    /**
     * The same heart hollow: this one is not kept yet.
     *
     * <p>The two states used to be one shape in two colours, which is how the loop and
     * shuffle toggles work — but those sit among other toggles, while the heart sits
     * next to a red "remove" cross, where "solid grey" versus "solid red" reads as two
     * different buttons rather than one button's two states. Filled versus hollow is the
     * distinction every application uses for this, and it survives the colour.</p>
     */
    static void heartOutline(GuiGraphics g, int x, int y, int color) {
        for (int[] pixel : HEART_OUTLINE) {
            g.fill(x + pixel[1], y + pixel[0], x + pixel[1] + 1, y + pixel[0] + 1, color);
        }
    }

    /**
     * Turns a filled shape into its outline: every pixel with at least one of its four
     * sides not covered by the shape. Computed once at class load rather than per frame,
     * because a list draws a glyph per row per frame.
     */
    private static int[][] outlineOf(int[][] spans) {
        boolean[][] solid = new boolean[BUTTON][BUTTON];
        for (int[] span : spans) {
            for (int column = span[1]; column <= span[2]; column++) {
                solid[span[0]][column] = true;
            }
        }
        List<int[]> edge = new ArrayList<>();
        for (int row = 0; row < BUTTON; row++) {
            for (int column = 0; column < BUTTON; column++) {
                if (solid[row][column] && !surrounded(solid, row, column)) {
                    edge.add(new int[] {row, column});
                }
            }
        }
        return edge.toArray(new int[0][]);
    }

    private static boolean surrounded(boolean[][] solid, int row, int column) {
        return covered(solid, row - 1, column) && covered(solid, row + 1, column)
                && covered(solid, row, column - 1) && covered(solid, row, column + 1);
    }

    private static boolean covered(boolean[][] solid, int row, int column) {
        return row >= 0 && row < solid.length && column >= 0 && column < solid[row].length
                && solid[row][column];
    }

    /**
     * A "jump by a fixed step" glyph: two stacked chevrons pointing back or forward.
     *
     * <p>Deliberately not {@link #next}/{@link #previous} with a bar: those already mean
     * "the next track", and a control that skipped ten seconds while looking like the
     * one that skips a whole video would be read wrong every time. Chevrons alone say
     * "within this one".</p>
     */
    static void seekStep(GuiGraphics g, int x, int y, boolean forward, int color) {
        chevron(g, x + (forward ? 1 : 5), y, forward, color);
        chevron(g, x + (forward ? 5 : 1), y, forward, color);
    }

    /**
     * One chevron of {@link #seekStep}: five rows widening to the point and back.
     */
    private static void chevron(GuiGraphics g, int x, int y, boolean forward, int color) {
        int top = y + 3;
        for (int i = 0; i < 5; i++) {
            int depth = Math.min(i, 4 - i);      // 0,1,2,1,0 — the tip is the middle row
            int px = forward ? x + depth : x + 2 - depth;
            g.fill(px, top + i, px + 2, top + i + 1, color);
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
     * The eight dot positions of {@link #spinner}, clockwise from the right, on a circle
     * of radius 4 about the middle of the button box.
     */
    private static final int[] SPIN_X = {9, 8, 5, 2, 1, 2, 5, 8};
    private static final int[] SPIN_Y = {5, 8, 9, 8, 5, 2, 1, 2};
    /** How long each dot takes to hand the lead to the next one. */
    private static final long SPIN_STEP_MILLIS = 90L;

    /**
     * A ring of eight dots with a bright head that walks around it: work in progress.
     *
     * <p>This is the one glyph that has to <em>move</em>. A seek — and a resume, which
     * relaunches the same way — holds the last decoded frame on screen for about a
     * second, and a still picture with a caption over it is indistinguishable from a
     * player that has crashed. Motion is the only thing that says otherwise, so the
     * position is taken from the wall clock rather than from a frame or tick counter:
     * the thing it is reassuring you about is precisely that no frames are arriving.</p>
     *
     * @param millis the current {@link Anim#now()} reading
     */
    static void spinner(GuiGraphics g, int x, int y, int color, long millis) {
        int head = (int) Math.floorMod(millis / SPIN_STEP_MILLIS, 8);
        for (int i = 0; i < 8; i++) {
            // How far behind the head this dot is; the tail fades out but never vanishes,
            // so the ring stays readable as a ring.
            int age = Math.floorMod(head - i, 8);
            double alpha = 1.0 - age * 0.11;
            int dx = x + SPIN_X[i];
            int dy = y + SPIN_Y[i];
            g.fill(dx - 1, dy - 1, dx + 1, dy + 1, Theme.withAlpha(color, alpha));
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
