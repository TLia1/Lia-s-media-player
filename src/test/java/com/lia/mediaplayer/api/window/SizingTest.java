package com.lia.mediaplayer.api.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The content width each {@link Sizing} asks for. Everything resolves to a width because
 * the windows are aspect-locked — see the interface's own note.
 */
class SizingTest {

    private static final int SRC_W = 1920;
    private static final int SRC_H = 1080;
    private static final int SCREEN_W = 800;
    private static final int SCREEN_H = 450;

    private static int resolve(Sizing sizing) {
        return sizing.resolveContentWidth(SRC_W, SRC_H, SCREEN_W, SCREEN_H);
    }

    @Test
    void contentWidthIsTakenAsGiven() {
        assertEquals(320, resolve(Sizing.contentWidth(320)));
    }

    @Test
    void scaleIsAMultipleOfTheSourceResolution() {
        assertEquals(SRC_W, resolve(Sizing.scale(1.0)));
        assertEquals(SRC_W / 2, resolve(Sizing.scale(0.5)));
    }

    @Test
    void fitWithinRunsOutOnWhicheverAxisIsTighter() {
        // 16:9 into a 400x100 box: the height runs out, so the width is 100/9*16.
        assertEquals(177, resolve(Sizing.fitWithin(400, 100)));
        // Into a 200x400 box the width runs out first and is used as-is.
        assertEquals(200, resolve(Sizing.fitWithin(200, 400)));
        // And into a square box a landscape source is width-limited, not height-limited.
        assertEquals(400, resolve(Sizing.fitWithin(400, 400)));
    }

    @Test
    void fitWithinRespectsATallSource() {
        Sizing sizing = Sizing.fitWithin(400, 400);
        assertEquals(225, sizing.resolveContentWidth(1080, 1920, SCREEN_W, SCREEN_H));
    }

    @Test
    void fractionOfScreenIsAFractionOfTheScreenNotOfTheSource() {
        assertEquals(SCREEN_W / 4, resolve(Sizing.fractionOfScreen(0.25)));
        assertEquals(SCREEN_W, resolve(Sizing.fractionOfScreen(1.0)));
    }

    @Test
    void autoAndTheaterDeclineToNameAWidth() {
        assertEquals(-1, resolve(Sizing.auto()));
        assertEquals(-1, resolve(Sizing.theater()));
        assertFalse(Sizing.auto().isTheater());
        assertTrue(Sizing.theater().isTheater());
        assertFalse(Sizing.contentWidth(100).isTheater());
    }

    @Test
    void nothingEverResolvesToZeroOrLess() {
        assertTrue(Sizing.scale(0.0001).resolveContentWidth(4, 3, SCREEN_W, SCREEN_H) >= 1);
        assertTrue(Sizing.fitWithin(1, 1).resolveContentWidth(SRC_W, SRC_H, SCREEN_W, SCREEN_H) >= 1);
        assertTrue(Sizing.fractionOfScreen(0.001).resolveContentWidth(SRC_W, SRC_H, 10, 10) >= 1);
    }

    @Test
    void aSourceSizeOfZeroDoesNotDivideByZero() {
        assertEquals(1, Sizing.scale(1.0).resolveContentWidth(0, 0, SCREEN_W, SCREEN_H));
        assertTrue(Sizing.fitWithin(400, 400).resolveContentWidth(0, 0, SCREEN_W, SCREEN_H) >= 1);
    }

    @Test
    void negativeSizesAndOutOfRangeFractionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Sizing.contentWidth(0));
        assertThrows(IllegalArgumentException.class, () -> Sizing.contentWidth(-10));
        assertThrows(IllegalArgumentException.class, () -> Sizing.scale(0.0));
        assertThrows(IllegalArgumentException.class, () -> Sizing.scale(-1.0));
        assertThrows(IllegalArgumentException.class, () -> Sizing.scale(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Sizing.fitWithin(0, 100));
        assertThrows(IllegalArgumentException.class, () -> Sizing.fitWithin(100, -1));
        assertThrows(IllegalArgumentException.class, () -> Sizing.fractionOfScreen(0.0));
        assertThrows(IllegalArgumentException.class, () -> Sizing.fractionOfScreen(1.5));
    }
}
