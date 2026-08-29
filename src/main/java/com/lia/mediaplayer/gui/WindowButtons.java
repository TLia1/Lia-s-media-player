package com.lia.mediaplayer.gui;

/**
 * Where the row of buttons in a window's top-right corner sits: the heart, the copy
 * button, the browser link, an optional hide button, and the close button.
 *
 * <p>They are laid out right-to-left from the window's right edge, and both the drawing
 * ({@link WindowChrome#cornerButtons}) and the click routing ({@code MediaWindow}) have
 * to agree on every one of those positions — and on the addon buttons an
 * {@code api.window.WindowAction} adds to their left. Keeping that in one immutable value
 * rather than in ten fields on the window is what makes agreement automatic — and makes
 * it testable, which matters because {@link #width} is part of a window's
 * <em>minimum</em> size: the buttons do not stop at the left edge, so a window narrower
 * than its own button row draws them over each other and over whatever is beside it.</p>
 *
 * @param y       the top of every button in the row; they share it
 * @param closeX  the rightmost button
 * @param hideX   only meaningful when {@link #hasHide()}
 * @param actionX the x of each addon button, left of the heart, in the order they are
 *                drawn — see {@code api.window.WindowAction}. Empty when no addon
 *                registered one, which is the overwhelmingly common case.
 */
record WindowButtons(int y, int closeX, int hideX, int linkX, int copyX, int favX,
                     int[] actionX, boolean hasHide, boolean hasClose) {

    /** Side of one square button. */
    static final int SIZE = MediaWindow.BUTTON;

    /** Gap between two buttons. */
    private static final int GAP = 2;

    /** No addon buttons — what the layout tests and the default construction want. */
    private static final int[] NO_ACTIONS = new int[0];

    /**
     * The row, packed right-to-left from {@code rightEdge} (the x of the close button's
     * right side).
     */
    static WindowButtons layout(int rightEdge, int y, boolean hasHide) {
        return layout(rightEdge, y, hasHide, true, 0);
    }

    /**
     * The row without one or both of its optional buttons — what a window opened with a
     * reduced {@code WindowChromeOptions} gets. A button that is not there takes no room
     * either: the rest of the row slides right into its place, so a bare window's heart
     * still sits against the same edge as a full one's.
     */
    static WindowButtons layout(int rightEdge, int y, boolean hasHide, boolean hasClose) {
        return layout(rightEdge, y, hasHide, hasClose, 0);
    }

    /**
     * The row with {@code actionCount} addon buttons on its left — see
     * {@code api.window.WindowAction}. They go left of the heart, which keeps every
     * built-in button where the user already expects it whatever an addon registers.
     */
    static WindowButtons layout(int rightEdge, int y, boolean hasHide, boolean hasClose, int actionCount) {
        // Packed right-to-left, each present button consuming its width and a gap. The
        // absent ones still get a coordinate — an unreachable one — so the record stays
        // a plain tuple and every `over*` guard is the flag, not a null.
        int cursor = rightEdge;
        int closeX = cursor - SIZE;
        if (hasClose) {
            cursor = closeX - GAP;
        }
        int hideX = cursor - SIZE;
        if (hasHide) {
            cursor = hideX - GAP;
        }
        int linkX = cursor - SIZE;
        // Beside the browser link rather than beside the heart: the two of them are the
        // same gesture ("take this link somewhere else"), one to a window and one to the
        // clipboard, and they are told apart by their glyphs, not by hunting for them.
        int copyX = linkX - SIZE - GAP;
        int favX = copyX - SIZE - GAP;
        int[] actionX = NO_ACTIONS;
        if (actionCount > 0) {
            actionX = new int[actionCount];
            int next = favX;
            for (int i = 0; i < actionCount; i++) {
                // Index 0 is the one nearest the heart, so the first-registered action
                // sits closest to the mod's own buttons and later ones extend leftwards.
                next -= SIZE + GAP;
                actionX[i] = next;
            }
        }
        return new WindowButtons(y, closeX, hideX, linkX, copyX, favX, actionX, hasHide, hasClose);
    }

    /**
     * How much horizontal room the row needs, including a little slack at its left.
     *
     * <p>Part of a window's minimum content width — see the class note.</p>
     */
    static int width(boolean hasHide) {
        return width(hasHide, true, 0);
    }

    static int width(boolean hasHide, boolean hasClose) {
        return width(hasHide, hasClose, 0);
    }

    static int width(boolean hasHide, boolean hasClose, int actionCount) {
        int buttons = 3 + (hasHide ? 1 : 0) + (hasClose ? 1 : 0) + Math.max(0, actionCount);
        return buttons * (SIZE + GAP) + 4;
    }

    /**
     * The left edge of the row, i.e. where a title drawn to its left has to stop.
     */
    int leftEdge() {
        return actionX.length == 0 ? favX : actionX[actionX.length - 1];
    }

    /** How many addon buttons this row carries. */
    int actionCount() {
        return actionX.length;
    }

    /**
     * The index of the addon button under the cursor, or {@code -1}. An index rather than
     * the action itself: this record knows about positions, and the window owns the list
     * those positions were laid out from.
     */
    int actionAt(double mouseX, double mouseY) {
        for (int i = 0; i < actionX.length; i++) {
            if (over(actionX[i], mouseX, mouseY)) {
                return i;
            }
        }
        return -1;
    }

    /** The x of one addon button. */
    int actionX(int index) {
        return actionX[index];
    }

    boolean overClose(double mouseX, double mouseY) {
        return hasClose && over(closeX, mouseX, mouseY);
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
