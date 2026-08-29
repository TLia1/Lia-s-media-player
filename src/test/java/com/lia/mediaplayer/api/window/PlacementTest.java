package com.lia.mediaplayer.api.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a {@link Placement} resolves to. Pure arithmetic, and the failure mode of every
 * bug in it is a window somewhere it cannot be reached — the same reason
 * {@code WindowPlacement} is tested next door.
 */
class PlacementTest {

    private static final int SCREEN_W = 800;
    private static final int SCREEN_H = 400;
    private static final int BOX_W = 200;
    private static final int BOX_H = 100;

    private static int x(Placement placement) {
        return placement.resolveX(SCREEN_W, BOX_W);
    }

    private static int y(Placement placement) {
        return placement.resolveY(SCREEN_H, BOX_H);
    }

    @Test
    void anchoredOffsetsMoveInwardsFromTheAnchoredEdge() {
        // The whole point: anchored(TOP_RIGHT, 4, 4) means "four pixels from the
        // top-right corner", not four pixels off the side of the screen.
        Placement topRight = Placement.anchored(Anchor.TOP_RIGHT, 4, 4);
        assertEquals(SCREEN_W - BOX_W - 4, x(topRight));
        assertEquals(4, y(topRight));

        Placement bottomLeft = Placement.anchored(Anchor.BOTTOM_LEFT, 4, 4);
        assertEquals(4, x(bottomLeft));
        assertEquals(SCREEN_H - BOX_H - 4, y(bottomLeft));
    }

    @Test
    void aCentredAxisTakesTheOffsetAsAPlainNudge() {
        assertEquals((SCREEN_W - BOX_W) / 2, x(Placement.anchored(Anchor.CENTER, 0, 0)));
        assertEquals((SCREEN_H - BOX_H) / 2, y(Placement.anchored(Anchor.CENTER, 0, 0)));
        assertEquals((SCREEN_W - BOX_W) / 2 + 10, x(Placement.anchored(Anchor.CENTER, 10, 0)));
        assertEquals((SCREEN_H - BOX_H) / 2 - 10, y(Placement.anchored(Anchor.CENTER, 0, -10)));
    }

    @Test
    void everyAnchorLandsInsideTheScreenAtZeroOffset() {
        for (Anchor anchor : Anchor.values()) {
            Placement placement = Placement.anchored(anchor, 0, 0);
            int left = x(placement);
            int top = y(placement);
            assertTrue(left >= 0 && left + BOX_W <= SCREEN_W, anchor + " x=" + left);
            assertTrue(top >= 0 && top + BOX_H <= SCREEN_H, anchor + " y=" + top);
        }
    }

    @Test
    void anchorEdgeFlagsAreConsistent() {
        for (Anchor anchor : Anchor.values()) {
            assertFalse(anchor.isLeft() && anchor.isRight(), anchor.name());
            assertFalse(anchor.isTop() && anchor.isBottom(), anchor.name());
        }
        assertTrue(Anchor.CENTER_RIGHT.isRight());
        assertFalse(Anchor.CENTER_RIGHT.isTop());
        assertFalse(Anchor.CENTER_RIGHT.isBottom());
    }

    @Test
    void absoluteIsTakenAsGiven() {
        assertEquals(37, x(Placement.at(37, 12)));
        assertEquals(12, y(Placement.at(37, 12)));
        // Unclamped here on purpose: the mod applies the on-screen clamp afterwards, so
        // this layer stays a pure function of what the caller asked for.
        assertEquals(-500, x(Placement.at(-500, 0)));
    }

    @Test
    void relativeIsAFractionOfTheFreeSpaceSoOneIsFlushRight() {
        assertEquals(0, x(Placement.relative(0.0, 0.0)));
        assertEquals(SCREEN_W - BOX_W, x(Placement.relative(1.0, 0.0)));
        assertEquals(SCREEN_H - BOX_H, y(Placement.relative(0.0, 1.0)));
        assertEquals((SCREEN_W - BOX_W) / 2, x(Placement.relative(0.5, 0.0)));
    }

    @Test
    void relativeSurvivesAScreenSizeChangeWithTheSameMeaning() {
        Placement placement = Placement.relative(1.0, 1.0);
        assertEquals(1920 - BOX_W, placement.resolveX(1920, BOX_W));
        assertEquals(1080 - BOX_H, placement.resolveY(1080, BOX_H));
    }

    @Test
    void aWindowBiggerThanTheScreenStillResolvesToZeroRatherThanNegativeSpace() {
        assertEquals(0, Placement.relative(1.0, 0.0).resolveX(100, 400));
    }

    @Test
    void rememberedIsTheOnlyOneWithNoOpinion() {
        assertTrue(Placement.remembered().isRemembered());
        assertFalse(Placement.at(0, 0).isRemembered());
        assertFalse(Placement.relative(0.5, 0.5).isRemembered());
        assertFalse(Placement.anchored(Anchor.CENTER, 0, 0).isRemembered());
    }

    @Test
    void outOfRangeFractionsAndANullAnchorAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Placement.relative(-0.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Placement.relative(0.0, 1.5));
        assertThrows(IllegalArgumentException.class, () -> Placement.relative(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Placement.anchored(null, 0, 0));
    }
}
