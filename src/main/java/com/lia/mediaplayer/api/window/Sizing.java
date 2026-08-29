/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * How big a window is.
 *
 * <p>Everything here resolves to a <b>content width</b> — the picture's width, not the
 * box's, so the title bar and control bar are not part of the number. That is deliberate
 * and is not an implementation detail leaking out: the windows are aspect-locked, so a
 * width is the whole of the size, and the content width is exactly what the mod already
 * persists in {@code windows.json}, which makes "restore it to the size it was" and "open
 * it at this size" the same operation.</p>
 *
 * <p>The mod still applies its own floor and ceiling afterwards — a window may not be
 * narrower than its own button row, nor taller than the screen. A sizing is a request,
 * and an impossible one is clamped rather than refused.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.2.0
 */
public sealed interface Sizing {

    /**
     * The content width asked for, or {@code -1} for "whatever the window would pick for
     * itself" — which is what {@link #auto()} and {@link #theater()} both answer, the
     * latter because filling the screen is not a width the caller can compute.
     */
    int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight);

    /** Whether the window should open filling the screen. */
    default boolean isTheater() {
        return false;
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /** An exact content width in GUI pixels. */
    static Sizing contentWidth(int pixels) {
        requirePositive(pixels, "pixels");
        return new ContentWidth(pixels);
    }

    /** A multiple of the media's own resolution: {@code 1.0} is one screen pixel per source pixel. */
    static Sizing scale(double factor) {
        if (!(factor > 0.0) || Double.isInfinite(factor)) {
            throw new IllegalArgumentException("factor must be a positive, finite number, was " + factor);
        }
        return new Scale(factor);
    }

    /** The largest aspect-preserving size that fits in {@code maxW} x {@code maxH}. */
    static Sizing fitWithin(int maxW, int maxH) {
        requirePositive(maxW, "maxW");
        requirePositive(maxH, "maxH");
        return new FitWithin(maxW, maxH);
    }

    /**
     * A fraction of the screen's width.
     *
     * @throws IllegalArgumentException if {@code fraction} is outside {@code 0..1}
     */
    static Sizing fractionOfScreen(double fraction) {
        if (!(fraction > 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("fraction must be in (0, 1], was " + fraction);
        }
        return new FractionOfScreen(fraction);
    }

    /** The window's own auto-fit — the mod's behaviour when nobody says otherwise. */
    static Sizing auto() {
        return Auto.INSTANCE;
    }

    /** Fills the screen, the way a double-click on the picture does. */
    static Sizing theater() {
        return Theater.INSTANCE;
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive, was " + value);
        }
    }

    // ------------------------------------------------------------------
    // The cases
    // ------------------------------------------------------------------

    /** @see #contentWidth(int) */
    record ContentWidth(int pixels) implements Sizing {

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            return pixels;
        }
    }

    /** @see #scale(double) */
    record Scale(double factor) implements Sizing {

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            return Math.max(1, (int) Math.round(Math.max(1, sourceWidth) * factor));
        }
    }

    /** @see #fitWithin(int, int) */
    record FitWithin(int maxW, int maxH) implements Sizing {

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            int srcW = Math.max(1, sourceWidth);
            int srcH = Math.max(1, sourceHeight);
            // The width that makes the aspect-locked height exactly maxH, or maxW —
            // whichever runs out first.
            return Math.max(1, Math.min(maxW, (int) Math.floor(maxH * (double) srcW / srcH)));
        }
    }

    /** @see #fractionOfScreen(double) */
    record FractionOfScreen(double fraction) implements Sizing {

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            return Math.max(1, (int) Math.round(screenWidth * fraction));
        }
    }

    /** @see #auto() */
    record Auto() implements Sizing {

        private static final Auto INSTANCE = new Auto();

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            return -1;
        }
    }

    /** @see #theater() */
    record Theater() implements Sizing {

        private static final Theater INSTANCE = new Theater();

        @Override
        public int resolveContentWidth(int sourceWidth, int sourceHeight, int screenWidth, int screenHeight) {
            return -1; // the theatre branch of the layout already knows how to fill a screen
        }

        @Override
        public boolean isTheater() {
            return true;
        }
    }
}
