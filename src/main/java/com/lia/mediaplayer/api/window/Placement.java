/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * Where a window sits.
 *
 * <p>Coordinates are GUI-scaled pixels, like every vanilla screen — the same numbers a
 * {@code Screen} works in, not framebuffer pixels. The mod resolves a placement on
 * <em>every</em> layout pass rather than once, so {@link #relative} keeps its meaning
 * across a resolution change or a GUI-scale change instead of leaving the window wherever
 * the old screen size put it.</p>
 *
 * <p><b>The result is always clamped on screen.</b> Whatever a placement resolves to, the
 * mod keeps at least a couple of pixels of the window reachable on every side; a window
 * that cannot be dragged back is the one failure mode this whole area exists to prevent.
 * So {@link #at(int, int)} with a wild coordinate is not an error, it is simply pushed
 * back to the edge.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.2.0
 */
public sealed interface Placement {

    /**
     * Where the window's left edge goes, given the screen and the window's own width.
     * Unclamped — the mod applies the on-screen clamp afterwards.
     */
    int resolveX(int screenWidth, int boxWidth);

    /** The vertical counterpart of {@link #resolveX}. */
    int resolveY(int screenHeight, int boxHeight);

    /**
     * Whether this placement declines to have an opinion, leaving the window wherever
     * the user last dragged it (or at the configured default). Only {@link #remembered()}
     * says yes.
     */
    default boolean isRemembered() {
        return false;
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /**
     * An inset from one of the nine anchor points.
     *
     * <p>The offsets move <em>inwards</em> from the anchored edge, which is what makes
     * {@code anchored(TOP_RIGHT, 4, 4)} read as "four pixels from the top-right corner"
     * rather than four pixels off the side of the screen. On a centred axis they are a
     * plain offset, positive being right and down.</p>
     */
    static Placement anchored(Anchor anchor, int dx, int dy) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        return new Anchored(anchor, dx, dy);
    }

    /** An absolute top-left corner, in GUI pixels. */
    static Placement at(int x, int y) {
        return new Absolute(x, y);
    }

    /**
     * A fraction of the space the window leaves free: {@code 0} is flush left/top,
     * {@code 1} flush right/bottom, {@code 0.5} centred. Expressed this way so a
     * placement survives a resolution or GUI-scale change with the same meaning.
     *
     * @throws IllegalArgumentException if either fraction is outside {@code 0..1}
     */
    static Placement relative(double fx, double fy) {
        requireFraction(fx, "fx");
        requireFraction(fy, "fy");
        return new Relative(fx, fy);
    }

    /** Wherever the user left it, else the configured default. The mod's own behaviour. */
    static Placement remembered() {
        return Remembered.INSTANCE;
    }

    private static void requireFraction(double value, String name) {
        if (!(value >= 0.0 && value <= 1.0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }

    // ------------------------------------------------------------------
    // The cases
    // ------------------------------------------------------------------

    /** @see #anchored(Anchor, int, int) */
    record Anchored(Anchor anchor, int dx, int dy) implements Placement {

        @Override
        public int resolveX(int screenWidth, int boxWidth) {
            int free = screenWidth - boxWidth;
            if (anchor.isLeft()) {
                return dx;
            }
            if (anchor.isRight()) {
                return free - dx;
            }
            return free / 2 + dx;
        }

        @Override
        public int resolveY(int screenHeight, int boxHeight) {
            int free = screenHeight - boxHeight;
            if (anchor.isTop()) {
                return dy;
            }
            if (anchor.isBottom()) {
                return free - dy;
            }
            return free / 2 + dy;
        }
    }

    /** @see #at(int, int) */
    record Absolute(int x, int y) implements Placement {

        @Override
        public int resolveX(int screenWidth, int boxWidth) {
            return x;
        }

        @Override
        public int resolveY(int screenHeight, int boxHeight) {
            return y;
        }
    }

    /** @see #relative(double, double) */
    record Relative(double fx, double fy) implements Placement {

        @Override
        public int resolveX(int screenWidth, int boxWidth) {
            return (int) Math.round(fx * Math.max(0, screenWidth - boxWidth));
        }

        @Override
        public int resolveY(int screenHeight, int boxHeight) {
            return (int) Math.round(fy * Math.max(0, screenHeight - boxHeight));
        }
    }

    /** @see #remembered() */
    record Remembered() implements Placement {

        private static final Remembered INSTANCE = new Remembered();

        @Override
        public int resolveX(int screenWidth, int boxWidth) {
            return 0;
        }

        @Override
        public int resolveY(int screenHeight, int boxHeight) {
            return 0;
        }

        @Override
        public boolean isRemembered() {
            return true;
        }
    }
}
