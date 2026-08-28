package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mouse state a window keeps between events.
 *
 * <p>Written against wall-clock time, so the cases here are the ones that do not need to
 * wait: what happens when clicks arrive back to back, which is exactly where the
 * double-click rule is subtle. Three fast clicks must be one pair and a stray, not two
 * overlapping pairs — otherwise a quick triple-click on a video enters theatre mode and
 * leaves it again, and the player sees nothing happen.</p>
 */
class WindowGesturesTest {

    // ------------------------------------------------------------------
    // Double clicks
    // ------------------------------------------------------------------

    @Test
    void oneClickIsNotADoubleClick() {
        assertFalse(new WindowGestures().isDoubleClick(100, 100));
    }

    @Test
    void twoClicksInTheSamePlacePair() {
        WindowGestures gestures = new WindowGestures();
        assertFalse(gestures.isDoubleClick(100, 100));
        assertTrue(gestures.isDoubleClick(100, 100));
    }

    @Test
    void aSecondClickJustBesideTheFirstStillPairs() {
        // A hand moves a pixel or two between clicks; insisting on the exact pixel would
        // make double-clicking a video mostly not work.
        WindowGestures gestures = new WindowGestures();
        gestures.isDoubleClick(100, 100);
        assertTrue(gestures.isDoubleClick(103, 102));
    }

    @Test
    void aSecondClickSomewhereElseDoesNotPair() {
        WindowGestures gestures = new WindowGestures();
        gestures.isDoubleClick(100, 100);
        assertFalse(gestures.isDoubleClick(140, 100));
    }

    @Test
    void threeFastClicksAreOnePairAndAStray() {
        WindowGestures gestures = new WindowGestures();
        assertFalse(gestures.isDoubleClick(100, 100));
        assertTrue(gestures.isDoubleClick(100, 100));
        assertFalse(gestures.isDoubleClick(100, 100), "the pair is spent, not extended");
    }

    @Test
    void afterAStrayThirdClickTheNextOnePairsAgain() {
        WindowGestures gestures = new WindowGestures();
        gestures.isDoubleClick(100, 100);
        gestures.isDoubleClick(100, 100);
        gestures.isDoubleClick(100, 100);
        assertTrue(gestures.isDoubleClick(100, 100));
    }

    @Test
    void aMissedPairStartsANewOneWhereItLanded() {
        WindowGestures gestures = new WindowGestures();
        gestures.isDoubleClick(100, 100);
        assertFalse(gestures.isDoubleClick(300, 300));
        assertTrue(gestures.isDoubleClick(300, 300));
    }

    // ------------------------------------------------------------------
    // Drags
    // ------------------------------------------------------------------

    @Test
    void holdsNoDragToStartWith() {
        WindowGestures gestures = new WindowGestures();
        assertFalse(gestures.isMoving());
        assertFalse(gestures.isResizing());
        assertFalse(gestures.isDragging());
    }

    @Test
    void remembersWhereInsideTheBoxItWasGrabbed() {
        // Without the offset the window would jump so its corner sits under the cursor.
        WindowGestures gestures = new WindowGestures();
        gestures.beginMove(150, 130, 100, 100);

        assertEquals(100, gestures.moveToX(150));
        assertEquals(100, gestures.moveToY(130));
        assertEquals(140, gestures.moveToX(190));
        assertEquals(170, gestures.moveToY(200));
    }

    @Test
    void aMoveAndAResizeAreTellingApart() {
        WindowGestures gestures = new WindowGestures();
        gestures.beginResize();

        assertTrue(gestures.isResizing());
        assertFalse(gestures.isMoving());
        assertTrue(gestures.isDragging());
    }

    @Test
    void releaseEndsWhateverWasRunningAndSaysItWasOurs() {
        WindowGestures gestures = new WindowGestures();
        gestures.beginMove(150, 130, 100, 100);

        assertTrue(gestures.release());
        assertFalse(gestures.isDragging());
    }

    @Test
    void releaseWithNothingHeldIsNotOurs() {
        // The window uses this answer to decide whether to swallow the mouse-up; a drag
        // that claimed one it never started would eat a click meant for something else.
        assertFalse(new WindowGestures().release());
    }

    // ------------------------------------------------------------------
    // The cursor, which is what theatre mode's idle timeout watches
    // ------------------------------------------------------------------

    @Test
    void startsWithNoCursorAtAll() {
        WindowGestures gestures = new WindowGestures();
        assertEquals(Integer.MIN_VALUE, gestures.cursorX());
        assertEquals(Integer.MIN_VALUE, gestures.cursorY());
    }

    @Test
    void remembersWhereTheCursorIs() {
        WindowGestures gestures = new WindowGestures();
        gestures.noteCursor(40, 60);

        assertEquals(40, gestures.cursorX());
        assertEquals(60, gestures.cursorY());
    }

    @Test
    void ignoresAFrameDrawnWithNoCursor() {
        // The HUD overlay renders with no cursor at all rather than with a still one, so
        // it must neither move the cursor nor count as the cursor holding still.
        WindowGestures gestures = new WindowGestures();
        gestures.noteCursor(40, 60);
        gestures.noteCursor(-1, -1);

        assertEquals(40, gestures.cursorX());
        assertEquals(60, gestures.cursorY());
    }

    @Test
    void reportsTheCursorAsRecentlyMovedAfterItMoves() {
        WindowGestures gestures = new WindowGestures();
        gestures.noteCursor(40, 60);
        assertTrue(gestures.idleMillis() < 2000, "was " + gestures.idleMillis());
    }

    @Test
    void wakingCountsAsAMoveWithoutMovingTheCursor() {
        WindowGestures gestures = new WindowGestures();
        gestures.noteCursor(40, 60);
        gestures.wake();

        assertTrue(gestures.idleMillis() < 2000);
        assertEquals(40, gestures.cursorX());
    }

    // ------------------------------------------------------------------
    // The click flash
    // ------------------------------------------------------------------

    @Test
    void hasNothingToDrawBeforeAnyClick() {
        assertEquals(1.0, new WindowGestures().flashProgress());
    }

    @Test
    void marksWhereTheClickLanded() {
        WindowGestures gestures = new WindowGestures();
        gestures.flash(123.4, 88.6);

        assertEquals(123, gestures.flashX());
        assertEquals(89, gestures.flashY());
        assertTrue(gestures.flashProgress() < 1.0, "the flash has just started");
    }
}
