package com.lia.mediaplayer.gui;

/**
 * Where the row of buttons in a window's top-right corner sits: the heart, the copy
 * button, the browser link, an optional hide button, and the close button.
 *
 * <p>They are laid out right-to-left from the window's right edge, and both the drawing
 * ({@link WindowChrome#cornerButtons}) and the click routing ({@code MediaWindow}) have
 * to agree on every one of the five positions. Keeping that in one immutable value
 * rather than in ten fields on the window is what makes agreement automatic — and makes
 * it testable, which matters because {@link #width} is part of a window's
 * <em>minimum</em> size: the buttons do not stop at the left edge, so a window narrower
 * than its own button row draws them over each other and over whatever is beside it.</p>
 *
 * @param y      the top of every button in the row; they share it
 * @param closeX the rightmost button
 * @param hideX  only meaningful when {@link #hasHide()}
 */
record WindowButtons(int y, int closeX, int hideX, int linkX, int copyX, int favX, boolean hasHide) {

    /** Side of one square button. */
    static final int SIZE = MediaWindow.BUTTON;

    /** Gap between two buttons. */
    private static final int GAP = 2;

    /**
     * The row, packed right-to-left from {@code rightEdge} (the x of the close button's
     * right side).
     */
    static WindowButtons layout(int rightEdge, int y, boolean hasHide) {
        int closeX = rightEdge - SIZE;
        int next = hasHide ? closeX - SIZE - GAP : closeX;
        int hideX = hasHide ? next : closeX;
        int linkX = next - SIZE - GAP;
        // Beside the browser link rather than beside the heart: the two of them are the
        // same gesture ("take this link somewhere else"), one to a window and one to the
        // clipboard, and they are told apart by their glyphs, not by hunting for them.
        int copyX = linkX - SIZE - GAP;
        int favX = copyX - SIZE - GAP;
        return new WindowButtons(y, closeX, hideX, linkX, copyX, favX, hasHide);
    }

    /**
     * How much horizontal room the row needs, including a little slack at its left.
     *
     * <p>Part of a window's minimum content width — see the class note.</p>
     */
    static int width(boolean hasHide) {
        int buttons = 4 + (hasHide ? 1 : 0);
        return buttons * (SIZE + GAP) + 4;
    }

    /**
     * The left edge of the row, i.e. where a title drawn to its left has to stop.
     */
    int leftEdge() {
        return favX;
    }

    boolean overClose(double mouseX, double mouseY) {
        return over(closeX, mouseX, mouseY);
    }

    boolean overHide(double mouseX, double mouseY) {
        return hasHide && over(hideX, mouseX, mouseY);
    }

    boolean overLink(double mouseX, double mouseY) {
        return over(linkX, mouseX, mouseY);
    }

    boolean overCopy(double mouseX, double mouseY) {
        return over(copyX, mouseX, mouseY);
    }

    boolean overFavorite(double mouseX, double mouseY) {
        return over(favX, mouseX, mouseY);
    }

    private boolean over(int x, double mouseX, double mouseY) {
        return MediaWindow.inRect(mouseX, mouseY, x, y, SIZE, SIZE);
    }
}
