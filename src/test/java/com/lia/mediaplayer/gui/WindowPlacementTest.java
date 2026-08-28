package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.gui.WindowPlacement.Size;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic that decides how big a media window is and where it sits.
 *
 * <p>Every way this can go wrong ends the same way for a player: a window they cannot
 * get back. A box taller than the screen puts the resize grip below the bottom edge,
 * where nothing can grab it; a box narrower than its own button row draws five buttons
 * on top of each other; a size restored against a placeholder resolution comes back
 * several times too large the moment the real video arrives. None of those could be
 * reproduced while this lived inside {@code MediaWindow}, which needs a client and a GL
 * context to exist at all.</p>
 *
 * <p>The numbers below use a 1920x1080 screen and a 16:9 source unless the case is
 * about something else.</p>
 */
class WindowPlacementTest {

    private static final int SCREEN_W = 1920;
    private static final int SCREEN_H = 1080;
    private static final int TITLE_BAR = MediaWindow.TITLE_BAR;
    private static final int PAD = MediaWindow.PADDING;
    private static final int MIN = 64;

    /** A layout pass with the chrome a video window has: a title bar and a control bar. */
    private static Size layout(WindowPlacement placement, int srcW, int srcH, double autoScale) {
        return placement.solve(srcW, srcH, SCREEN_W, SCREEN_H, autoScale,
                TITLE_BAR, 20, MIN, SCREEN_W - PAD * 2 - 2, 1.0);
    }

    // ------------------------------------------------------------------
    // Size
    // ------------------------------------------------------------------

    @Test
    void takesTheAutoScaleWhileNobodyHasResizedIt() {
        Size size = layout(new WindowPlacement(), 1280, 720, 0.5);
        assertEquals(640, size.contentW());
        assertEquals(360, size.contentH());
    }

    @Test
    void keepsTheSourceAspectRatio() {
        Size size = layout(new WindowPlacement(), 1000, 400, 0.5);
        assertEquals(500, size.contentW());
        assertEquals(200, size.contentH());
    }

    @Test
    void wrapsTheContentInItsChrome() {
        Size size = layout(new WindowPlacement(), 1280, 720, 0.5);
        assertEquals(640 + PAD * 2, size.boxW());
        assertEquals(360 + TITLE_BAR + 20 + PAD * 2, size.boxH());
    }

    @Test
    void prefersWhatTheUserSizedItToOverTheAutoScale() {
        WindowPlacement placement = new WindowPlacement();
        layout(placement, 1280, 720, 0.5);
        placement.resizeTo(320, MIN, 1280);

        assertEquals(320, layout(placement, 1280, 720, 0.5).contentW());
    }

    // ------------------------------------------------------------------
    // The caps — a window that cannot be dragged back is the failure here
    // ------------------------------------------------------------------

    @Test
    void neverGoesNarrowerThanItsOwnButtonRow() {
        Size size = layout(new WindowPlacement(), 1280, 720, 0.001);
        assertEquals(MIN, size.contentW());
    }

    @Test
    void neverGrowsTallerThanTheScreenHasRoomFor() {
        // A phone-shaped source at a scale that would be four screens tall.
        Size size = layout(new WindowPlacement(), 400, 1600, 4.0);
        assertTrue(size.boxH() <= SCREEN_H, "box was " + size.boxH() + " tall");
    }

    @Test
    void countsTheChromeAgainstTheAvailableHeight() {
        // The cap is on the whole box, not on the picture: a taller control bar has to
        // leave the picture less room, or the grip ends up under the bottom edge.
        WindowPlacement placement = new WindowPlacement();
        int slim = placement.solve(400, 1600, SCREEN_W, SCREEN_H, 4.0, 0, 0, MIN, SCREEN_W, 1.0).boxH();
        int fat = placement.solve(400, 1600, SCREEN_W, SCREEN_H, 4.0, TITLE_BAR, 80, MIN, SCREEN_W, 1.0).boxH();

        assertTrue(slim <= SCREEN_H);
        assertTrue(fat <= SCREEN_H);
    }

    @Test
    void neverGrowsWiderThanTheCallerAllows() {
        // The video window shrinks this to leave room for its docked queue panel.
        WindowPlacement placement = new WindowPlacement();
        Size size = placement.solve(1280, 720, SCREEN_W, SCREEN_H, 4.0, TITLE_BAR, 20, MIN, 500, 1.0);
        assertEquals(500, size.contentW());
    }

    @Test
    void survivesASourceWithNoSizeYet() {
        Size size = layout(new WindowPlacement(), 0, 0, 1.0);
        assertTrue(size.contentW() > 0);
        assertTrue(size.contentH() > 0);
    }

    // ------------------------------------------------------------------
    // Theatre mode
    // ------------------------------------------------------------------

    @Test
    void theatreFillsWhatTheScreenAllows() {
        WindowPlacement placement = new WindowPlacement();
        Size windowed = layout(placement, 1280, 720, 0.25);
        placement.toggleTheater();
        Size theatre = layout(placement, 1280, 720, 0.25);

        assertTrue(theatre.contentW() > windowed.contentW());
        assertTrue(theatre.boxH() <= SCREEN_H);
        assertTrue(theatre.boxW() <= SCREEN_W);
    }

    @Test
    void theatreEnlargesASourceSmallerThanTheScreenToo() {
        // Going through the zoom ceiling would cap a 160x90 clip at six times its size;
        // theatre mode is the cap itself, so it fills the screen whatever the source is.
        WindowPlacement placement = new WindowPlacement();
        placement.toggleTheater();
        Size size = layout(placement, 160, 90, 1.0);
        assertTrue(size.contentW() > 160 * 6, "only reached " + size.contentW());
    }

    @Test
    void leavingTheatrePutsTheExactGeometryBack() {
        WindowPlacement placement = new WindowPlacement();
        layout(placement, 1280, 720, 0.5);
        placement.pin(120, 64);
        placement.resizeTo(400, MIN, 1280);
        int widthBefore = placement.storedWidth(1280);

        placement.toggleTheater();
        placement.toggleTheater();

        assertFalse(placement.isTheater());
        assertTrue(placement.isPlaced());
        assertEquals(120, placement.x());
        assertEquals(64, placement.y());
        assertEquals(widthBefore, placement.storedWidth(1280));
    }

    @Test
    void leavingTheatreLeavesAnUnplacedWindowUnplaced() {
        WindowPlacement placement = new WindowPlacement();
        placement.toggleTheater();
        placement.toggleTheater();

        assertFalse(placement.isPlaced(), "theatre must not count as having been moved");
        assertFalse(placement.isSized());
    }

    // ------------------------------------------------------------------
    // The opening animation
    // ------------------------------------------------------------------

    @Test
    void drawsSmallerWhileOpeningButAnchorsToTheSizeItIsHeadingFor() {
        WindowPlacement placement = new WindowPlacement();
        Size size = placement.solve(1280, 720, SCREEN_W, SCREEN_H, 0.5,
                TITLE_BAR, 20, MIN, SCREEN_W, 0.92);

        assertTrue(size.contentW() < 640, "the frame drawn is scaled down");
        assertEquals(640 + PAD * 2, size.settledBoxW(), "the corner it is placed in is not");
    }

    @Test
    void aZoomInTheFirstFramesStartsFromTheRealSizeNotTheAnimatedOne() {
        WindowPlacement placement = new WindowPlacement();
        placement.solve(1280, 720, SCREEN_W, SCREEN_H, 0.5, TITLE_BAR, 20, MIN, SCREEN_W, 0.92);
        placement.zoom(1, MIN, 1280);

        // One notch is 10%: 640 -> 704, not 589 (which is 0.92 x 640) -> 648.
        assertEquals(704, layout(placement, 1280, 720, 0.5).contentW());
    }

    // ------------------------------------------------------------------
    // Zoom and resize
    // ------------------------------------------------------------------

    @Test
    void zoomStopsAtTheMinimumWidth() {
        WindowPlacement placement = new WindowPlacement();
        layout(placement, 1280, 720, 0.5);
        for (int notch = 0; notch < 200; notch++) {
            placement.zoom(-1, MIN, 1280);
            layout(placement, 1280, 720, 0.5);
        }
        assertEquals(MIN, layout(placement, 1280, 720, 0.5).contentW());
    }

    @Test
    void zoomStopsAtTheMaximumScale() {
        WindowPlacement placement = new WindowPlacement();
        placement.solve(100, 100, SCREEN_W, SCREEN_H, 1.0, 0, 0, MIN, SCREEN_W, 1.0);
        for (int notch = 0; notch < 200; notch++) {
            placement.zoom(1, MIN, 100);
            placement.solve(100, 100, SCREEN_W, SCREEN_H, 1.0, 0, 0, MIN, SCREEN_W, 1.0);
        }
        assertEquals(600, placement.storedWidth(100), "six times the source, and no further");
    }

    @Test
    void aGripPressThatNeverMovesLeavesTheSizeAlone() {
        // beginResize turns "sized by hand" on. If it did not also take the current
        // scale, the window would snap to its minimum width on a bare click of the grip.
        WindowPlacement placement = new WindowPlacement();
        layout(placement, 1280, 720, 0.5);

        placement.beginResize();

        assertEquals(640, layout(placement, 1280, 720, 0.5).contentW());
    }

    // ------------------------------------------------------------------
    // Position
    // ------------------------------------------------------------------

    @Test
    void pinningFreezesWhereTheWindowWasAutoAnchored() {
        WindowPlacement placement = new WindowPlacement();
        assertFalse(placement.isPlaced());

        placement.pin(300, 200);

        assertTrue(placement.isPlaced());
        assertEquals(300, placement.x());
        assertEquals(200, placement.y());
    }

    @Test
    void pinningASecondTimeChangesNothing() {
        WindowPlacement placement = new WindowPlacement();
        placement.pin(300, 200);
        placement.pin(999, 999);
        assertEquals(300, placement.x());
    }

    @Test
    void keepsTheWindowReachableOnASmallerScreen() {
        // The player resized the game window; the position saved on a big screen would
        // otherwise put the whole box past the edge.
        WindowPlacement placement = new WindowPlacement();
        placement.pin(1800, 1000);

        assertEquals(98, placement.clampedX(400, 300), "pulled back to the right-most spot that fits");
        assertEquals(98, placement.clampedY(300, 200));
        assertEquals(1800, placement.clampedX(SCREEN_W, 100), "left alone where it does fit");
    }

    @Test
    void keepsACornerReachableEvenWhenTheBoxIsBiggerThanTheScreen() {
        WindowPlacement placement = new WindowPlacement();
        placement.pin(1800, 1000);

        assertEquals(2, placement.clampedX(200, 400));
        assertEquals(2, placement.clampedY(200, 400));
    }

    @Test
    void putsAWindowInTheConfiguredCorner() {
        WindowPlacement placement = new WindowPlacement();
        placement.applyInitialPosition(WindowPosition.BOTTOM_RIGHT, 800, 600, 200, 150);

        assertTrue(placement.isPlaced());
        assertEquals(800 - 200 - PAD, placement.x());
        assertEquals(600 - 150 - PAD, placement.y());
    }

    @Test
    void leavesACentredWindowToCascade() {
        // CENTER is the absence of a choice: it must stay unplaced so several windows of
        // the same kind fan out instead of stacking on one spot.
        WindowPlacement placement = new WindowPlacement();
        placement.applyInitialPosition(WindowPosition.CENTER, 800, 600, 200, 150);

        assertFalse(placement.isPlaced());
        assertFalse(placement.needsInitialPosition(), "but it has had its turn");
    }

    @Test
    void asksForAnInitialPositionOnlyOnce() {
        WindowPlacement placement = new WindowPlacement();
        assertTrue(placement.needsInitialPosition());
        placement.applyInitialPosition(WindowPosition.TOP_LEFT, 800, 600, 200, 150);
        assertFalse(placement.needsInitialPosition());
    }

    // ------------------------------------------------------------------
    // Restoring windows.json
    // ------------------------------------------------------------------

    private static WindowStateStore.State state(boolean placed, int x, int y, boolean sized, int width) {
        return new WindowStateStore.State(placed, x, y, sized, width, false, RepeatMode.OFF, false);
    }

    @Test
    void putsBackWhereTheWindowWasLeft() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(true, 120, 64, false, 0), true);

        assertTrue(placement.isPlaced());
        assertEquals(120, placement.x());
        assertEquals(64, placement.y());
    }

    @Test
    void doesNotGiveASecondWindowTheSameSpot() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(true, 120, 64, true, 480), false);

        assertFalse(placement.isPlaced(), "the second window of a kind cascades instead");
        placement.applyPendingWidth(1280, true);
        assertEquals(480, placement.storedWidth(1280), "but it still restores its size");
    }

    @Test
    void doesNotLetTheDefaultCornerOverwriteARestoredPosition() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(true, 120, 64, false, 0), true);
        assertFalse(placement.needsInitialPosition());
    }

    @Test
    void restoresASizeAsAWidthNotAScale() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(false, 0, 0, true, 480), true);
        placement.applyPendingWidth(1280, true);

        assertTrue(placement.isSized());
        assertEquals(480, layout(placement, 1280, 720, 0.1).contentW());
    }

    @Test
    void waitsForTheRealResolutionBeforeRestoringASize() {
        // A video window reports a 320x180 placeholder until its first frame is decoded.
        // Converting the saved width against that would restore a box four times too
        // large the moment the real 1280x720 arrived.
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(false, 0, 0, true, 480), true);

        placement.applyPendingWidth(320, false);
        assertFalse(placement.isSized(), "nothing happens while the size is a placeholder");

        placement.applyPendingWidth(1280, true);
        assertEquals(480, placement.storedWidth(1280));
    }

    @Test
    void appliesARestoredWidthOnlyOnce() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(false, 0, 0, true, 480), true);
        placement.applyPendingWidth(1280, true);
        placement.resizeTo(200, MIN, 1280);
        placement.applyPendingWidth(1280, true);

        assertEquals(200, placement.storedWidth(1280), "the pending width is spent");
    }

    @Test
    void ignoresAStateThatSaysNothing() {
        WindowPlacement placement = new WindowPlacement();
        placement.restore(state(false, 0, 0, false, 0), true);

        assertFalse(placement.isPlaced());
        assertFalse(placement.isSized());
        assertTrue(placement.needsInitialPosition());
    }

    @Test
    void recordsNoWidthForAWindowNobodyResized() {
        assertEquals(0, new WindowPlacement().storedWidth(1280));
    }
}
