package com.lia.mediaplayer.gui;

import net.minecraft.Util;
import net.minecraft.util.Mth;

/**
 * The mod's clock for short UI animations — window fades, the click flash, the
 * "now playing" banner.
 *
 * <p>Everything here is driven by {@link Util#getMillis()} rather than by a tick
 * counter, and that is the point: the media windows are drawn on the HUD as well as
 * over a screen, and the HUD keeps rendering while the client ticks at a fixed 20 Hz
 * (and while the game is paused, it does not tick at all). A tick-based fade would
 * therefore run at a different speed depending on where the window happens to be
 * drawn, and stall outright on a paused single-player world. Wall-clock time gives one
 * duration that means the same thing everywhere.</p>
 *
 * <p>Durations are in milliseconds and deliberately short: these are meant to make a
 * state change readable, not to be watched.</p>
 */
final class Anim {

    private Anim() {
    }

    /**
     * The current animation clock reading. Store this when something starts and hand it
     * back to {@link #progress}.
     */
    static long now() {
        return Util.getMillis();
    }

    /**
     * Linear progress from {@code 0} to {@code 1} over {@code durationMillis}, clamped
     * at both ends. A {@code startMillis} of {@code 0} means "never started" and yields
     * {@code 1}, so an un-animated caller reads as "already finished".
     */
    static double progress(long startMillis, int durationMillis) {
        if (startMillis == 0 || durationMillis <= 0) {
            return 1.0;
        }
        return Mth.clamp((now() - startMillis) / (double) durationMillis, 0.0, 1.0);
    }

    /**
     * Cubic ease-out: fast at the start, settling at the end. Used for everything that
     * appears, because arriving gently is what makes a 150 ms move read as a movement
     * rather than a jump.
     */
    static double easeOut(double t) {
        double inv = 1.0 - Mth.clamp(t, 0.0, 1.0);
        return 1.0 - inv * inv * inv;
    }

    /**
     * Eases in and out again over one {@code 0..1} pass — the shape of something that
     * appears, holds, and leaves. {@code holdFraction} is how much of the middle is
     * spent fully on.
     */
    static double inOut(double t, double holdFraction) {
        double edge = Math.max(0.001, (1.0 - holdFraction) / 2.0);
        if (t < edge) {
            return easeOut(t / edge);
        }
        if (t > 1.0 - edge) {
            return easeOut((1.0 - t) / edge);
        }
        return 1.0;
    }
}
