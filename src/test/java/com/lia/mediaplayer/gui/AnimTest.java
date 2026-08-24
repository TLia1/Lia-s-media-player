package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The easing curves behind the window fades and the "now playing" banner. Only the
 * shape functions are covered: {@link Anim#progress} reads the wall clock, and a test
 * that sleeps to observe it would assert on the scheduler rather than on the code.
 */
class AnimTest {

    @Test
    void easeOutSpansTheWholeRange() {
        assertEquals(0.0, Anim.easeOut(0.0), 1e-9);
        assertEquals(1.0, Anim.easeOut(1.0), 1e-9);
    }

    @Test
    void easeOutIsClampedOutsideTheUnitRange() {
        assertEquals(0.0, Anim.easeOut(-3.0), 1e-9);
        assertEquals(1.0, Anim.easeOut(4.5), 1e-9);
    }

    @Test
    void easeOutFrontLoadsTheMovement() {
        // "Ease out" means most of the distance is covered early; at the halfway point
        // the value must already be past halfway, or the curve is easing the wrong way.
        assertTrue(Anim.easeOut(0.5) > 0.5, "expected the first half to cover more ground");
    }

    @Test
    void easeOutNeverGoesBackwards() {
        double previous = -1;
        for (int i = 0; i <= 100; i++) {
            double value = Anim.easeOut(i / 100.0);
            assertTrue(value >= previous, "eased value dropped at t=" + (i / 100.0));
            previous = value;
        }
    }

    @Test
    void inOutIsSilentAtBothEndsAndFullInTheMiddle() {
        assertEquals(0.0, Anim.inOut(0.0, 0.8), 1e-9);
        assertEquals(0.0, Anim.inOut(1.0, 0.8), 1e-9);
        assertEquals(1.0, Anim.inOut(0.5, 0.8), 1e-9);
    }

    @Test
    void inOutHoldsForTheRequestedShareOfThePass() {
        // hold = 0.8 leaves 0.1 of fade at each end, so anything between 0.1 and 0.9 is
        // fully on and anything outside it is still moving.
        assertEquals(1.0, Anim.inOut(0.12, 0.8), 1e-9);
        assertEquals(1.0, Anim.inOut(0.88, 0.8), 1e-9);
        assertTrue(Anim.inOut(0.05, 0.8) < 1.0);
        assertTrue(Anim.inOut(0.95, 0.8) < 1.0);
    }

    @Test
    void inOutIsSymmetric() {
        for (int i = 0; i <= 50; i++) {
            double t = i / 100.0;
            assertEquals(Anim.inOut(t, 0.6), Anim.inOut(1.0 - t, 0.6), 1e-9,
                    "fade in and fade out differ at t=" + t);
        }
    }

    @Test
    void inOutWithNoHoldStillProducesAFiniteCurve() {
        // A degenerate hold must not divide by zero; the guard in inOut keeps the edge
        // width positive.
        double peak = Anim.inOut(0.5, 1.0);
        assertTrue(peak >= 0.0 && peak <= 1.0, "peak out of range: " + peak);
    }
}
