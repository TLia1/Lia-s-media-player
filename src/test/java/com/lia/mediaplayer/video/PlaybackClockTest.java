package com.lia.mediaplayer.video;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drift correction, which is the piece of watch-together worth having in the mod
 * rather than in every addon.
 *
 * <p>Tested with no audio line at all — the clock's wall-clock path — because that is the
 * half that is pure arithmetic, and because the skew it applies is the same whichever of
 * the two baselines is authoritative. The window is deliberately not involved: what is
 * being pinned here is that a correction is <em>gradual</em> and <em>bounded</em>, which
 * is the whole reason it is not just a seek.</p>
 */
class PlaybackClockTest {

    /**
     * A clock parked at {@code atMicros}. Every call below reads and corrects it with
     * {@code isPlaying = false}, so the wall clock contributes nothing and the arithmetic
     * is exact — a running clock would make every assertion a range.
     */
    private static PlaybackClock pausedAt(long atMicros) {
        PlaybackClock clock = new PlaybackClock();
        clock.seekTo(atMicros, null);
        return clock;
    }

    private static long positionOf(PlaybackClock clock) {
        return clock.currentPositionMicros(false, null, false);
    }

    @Test
    void doesNothingInsideTheTolerance() {
        PlaybackClock clock = pausedAt(10_000_000L);
        assertEquals(PlaybackClock.Drift.WITHIN_TOLERANCE,
                clock.driftCorrect(10_020_000L, 100_000L, false, null, false));
        assertEquals(10_000_000L, positionOf(clock));
    }

    @Test
    void aZeroToleranceMeansTheDefaultRatherThanNone() {
        PlaybackClock clock = pausedAt(10_000_000L);
        // 10ms out, which is inside the default tolerance of a frame or two.
        assertEquals(PlaybackClock.Drift.WITHIN_TOLERANCE,
                clock.driftCorrect(10_010_000L, 0L, false, null, false));
    }

    @Test
    void nudgesForwardsByAtMostOneStepPerCall() {
        PlaybackClock clock = pausedAt(10_000_000L);
        assertEquals(PlaybackClock.Drift.NUDGED,
                clock.driftCorrect(10_500_000L, 10_000L, false, null, false));
        long moved = positionOf(clock) - 10_000_000L;
        assertTrue(moved > 0 && moved <= 50_000L,
                "a correction is gradual; it moved by " + moved + "µs in one call");
    }

    @Test
    void nudgesBackwardsToo() {
        PlaybackClock clock = pausedAt(10_000_000L);
        assertEquals(PlaybackClock.Drift.NUDGED,
                clock.driftCorrect(9_500_000L, 10_000L, false, null, false));
        assertTrue(positionOf(clock) < 10_000_000L);
    }

    @Test
    void repeatedCallsConverge() {
        PlaybackClock clock = pausedAt(10_000_000L);
        long target = 10_400_000L;
        for (int i = 0; i < 100; i++) {
            if (clock.driftCorrect(target, 10_000L, false, null, false)
                    == PlaybackClock.Drift.WITHIN_TOLERANCE) {
                break;
            }
        }
        assertTrue(Math.abs(positionOf(clock) - target) <= 10_000L,
                "calling once a tick should land inside the tolerance, got " + positionOf(clock));
    }

    @Test
    void neverOvershootsTheTarget() {
        PlaybackClock clock = pausedAt(10_000_000L);
        long target = 10_030_000L; // less than one step away, and outside a tight tolerance
        assertEquals(PlaybackClock.Drift.NUDGED,
                clock.driftCorrect(target, 1_000L, false, null, false));
        assertEquals(target, positionOf(clock), "a step is clamped to the distance left");
    }

    @Test
    void refusesToSlideFurtherThanTheMaximumSkew() {
        PlaybackClock clock = pausedAt(10_000_000L);
        long farAway = 10_000_000L + PlaybackClock.MAX_SKEW_MICROS + 1;
        assertEquals(PlaybackClock.Drift.SEEK,
                clock.driftCorrect(farAway, 10_000L, false, null, false));
        assertEquals(10_000_000L, positionOf(clock), "a refusal moves nothing");
    }

    @Test
    void aSeekResetsTheSkewSoLaterCorrectionsAreMeasuredFromTheNewPosition() {
        PlaybackClock clock = pausedAt(10_000_000L);
        clock.driftCorrect(10_500_000L, 10_000L, false, null, false);
        clock.seekTo(30_000_000L, null);
        assertEquals(30_000_000L, positionOf(clock));
        assertEquals(PlaybackClock.Drift.WITHIN_TOLERANCE,
                clock.driftCorrect(30_000_000L, 10_000L, false, null, false));
    }
}
