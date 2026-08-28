package com.lia.mediaplayer.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row of buttons in a window's top-right corner.
 *
 * <p>The reason this is worth a test file of its own is that two separate pieces of code
 * have to agree on the same five rectangles — the one that draws them and the one that
 * decides what a click hit — and they used to agree only because they read the same ten
 * fields in the same order. They now share this, and what is checked here is that the
 * row is packed the way the drawing assumes, that each button answers for its own
 * rectangle and nobody else's, and that {@link WindowButtons#width} really is how much
 * room the row needs: that figure is a window's minimum width, and a window narrower
 * than it draws its buttons over each other and over whatever is beside it.</p>
 */
class WindowButtonsTest {

    private static final int SIZE = WindowButtons.SIZE;

    /** A row ending at x=200, in a title bar at y=10. */
    private static WindowButtons row(boolean hasHide) {
        return WindowButtons.layout(200, 10, hasHide);
    }

    @Test
    void packsTheButtonsRightToLeft() {
        WindowButtons buttons = row(false);

        assertEquals(200 - SIZE, buttons.closeX());
        assertTrue(buttons.linkX() < buttons.closeX());
        assertTrue(buttons.copyX() < buttons.linkX());
        assertTrue(buttons.favX() < buttons.copyX());
    }

    @Test
    void spacesThemEvenly() {
        WindowButtons buttons = row(false);
        int step = buttons.closeX() - buttons.linkX();

        assertEquals(step, buttons.linkX() - buttons.copyX());
        assertEquals(step, buttons.copyX() - buttons.favX());
        assertTrue(step > SIZE, "buttons must not touch");
    }

    @Test
    void putsTheHideButtonBetweenCloseAndTheLink() {
        WindowButtons buttons = row(true);

        assertTrue(buttons.hideX() < buttons.closeX());
        assertTrue(buttons.linkX() < buttons.hideX());
    }

    @Test
    void shiftsTheRestAlongToMakeRoomForIt() {
        assertEquals(row(false).favX() - (SIZE + 2), row(true).favX());
    }

    @Test
    void sharesOneRowOfY() {
        WindowButtons buttons = row(true);
        assertEquals(10, buttons.y());
    }

    // ------------------------------------------------------------------
    // Hit testing
    // ------------------------------------------------------------------

    @Test
    void eachButtonAnswersForItsOwnRectangleOnly() {
        WindowButtons buttons = row(true);
        int y = buttons.y() + SIZE / 2;

        assertTrue(buttons.overClose(buttons.closeX() + 1, y));
        assertFalse(buttons.overLink(buttons.closeX() + 1, y));
        assertFalse(buttons.overCopy(buttons.closeX() + 1, y));
        assertFalse(buttons.overFavorite(buttons.closeX() + 1, y));
        assertFalse(buttons.overHide(buttons.closeX() + 1, y));

        assertTrue(buttons.overFavorite(buttons.favX() + 1, y));
        assertFalse(buttons.overClose(buttons.favX() + 1, y));
    }

    @Test
    void missesAClickInTheGapBetweenTwoButtons() {
        WindowButtons buttons = row(false);
        double between = buttons.copyX() + SIZE + 1;
        int y = buttons.y() + SIZE / 2;

        assertFalse(buttons.overCopy(between, y));
        assertFalse(buttons.overLink(between, y));
    }

    @Test
    void missesAClickAboveOrBelowTheRow() {
        WindowButtons buttons = row(false);
        double x = buttons.closeX() + 1;

        assertFalse(buttons.overClose(x, buttons.y() - 2));
        assertFalse(buttons.overClose(x, buttons.y() + SIZE + 2));
    }

    @Test
    void neverReportsAHideButtonThatIsNotThere() {
        WindowButtons buttons = row(false);
        // Without a hide button its x is the close button's, so a click on close would
        // otherwise also read as a click on hide — and hiding a window looks like
        // closing it, so nobody would notice which one ran.
        assertFalse(buttons.overHide(buttons.closeX() + 1, buttons.y() + 1));
        assertTrue(buttons.overClose(buttons.closeX() + 1, buttons.y() + 1));
    }

    // ------------------------------------------------------------------
    // The width that bounds a window's minimum size
    // ------------------------------------------------------------------

    @Test
    void reservesEnoughRoomForEveryButtonInTheRow() {
        for (boolean hasHide : new boolean[]{false, true}) {
            WindowButtons buttons = WindowButtons.layout(200, 10, hasHide);
            int used = 200 - buttons.leftEdge();
            assertTrue(WindowButtons.width(hasHide) >= used,
                    "hasHide=" + hasHide + ": row uses " + used
                            + " but only " + WindowButtons.width(hasHide) + " is reserved");
        }
    }

    @Test
    void reservesMoreRoomWhenThereIsAHideButton() {
        assertTrue(WindowButtons.width(true) > WindowButtons.width(false));
    }

    @Test
    void stopsTheTitleAtTheLeftmostButton() {
        WindowButtons buttons = row(true);
        assertEquals(buttons.favX(), buttons.leftEdge());
    }
}
