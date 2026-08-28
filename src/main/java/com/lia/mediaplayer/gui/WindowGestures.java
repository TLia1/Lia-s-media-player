package com.lia.mediaplayer.gui;

/**
 * The mouse state a media window keeps between events: which drag is in progress, where
 * it was grabbed, whether the last two clicks were a double-click, where the cursor was
 * and when it last moved, and the click that is still flashing.
 *
 * <p>None of it needs Minecraft, and all of it is the kind of thing that goes subtly
 * wrong — a double-click that also counts as the first click of the next pair, a drag
 * that survives the window it was started on, a theatre-mode timeout that never fires
 * because the cursor position is read from a frame that had no cursor. Kept apart from
 * the window it belongs to, it can be tested by feeding it clicks; inside the window it
 * could not be tested at all.</p>
 *
 * <p>The drag flags are set by the window (which decides <em>whether</em> a press starts
 * a drag — a press on a button does not) and cleared here on release.</p>
 */
final class WindowGestures {

    /**
     * How long two clicks may be apart and still count as a double-click.
     */
    private static final int DOUBLE_CLICK_MS = 300;
    /**
     * How far the second click of a double-click may land from the first.
     */
    private static final int DOUBLE_CLICK_SLOP = 4;
    /**
     * How long the mark left by a click stays on screen.
     */
    private static final int FLASH_MS = 220;

    // Active drag gestures.
    private boolean draggingMove;
    private boolean draggingResize;
    private int grabDX, grabDY;

    // The previous click, for spotting a double-click on the picture.
    private long lastClickAt;
    private int lastClickX, lastClickY;

    // Where the last click landed and when, for the flash that reports it.
    private int flashX, flashY;
    private long flashAt;

    // Where the cursor last was and when it last moved, which is what tells theatre
    // mode whether anyone is still looking for the controls.
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private long lastMouseMoveAt = Anim.now();

    // ------------------------------------------------------------------
    // Cursor
    // ------------------------------------------------------------------

    /**
     * Records where the cursor is, which is the whole of theatre mode's idle detection.
     *
     * <p>Done from {@code render} rather than from a move event because there is no
     * mouse-move hook: {@code ClientHooks} carries press, drag, release and scroll, and
     * the render hook is the one place the cursor position is reported on every version
     * and on both loaders. It fires once a frame, which is exactly the resolution
     * vanilla's own drag dispatch has.</p>
     */
    void noteCursor(int mouseX, int mouseY) {
        if (mouseX < 0 && mouseY < 0) {
            return; // the HUD overlay draws with no cursor at all, not with a still one
        }
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            lastMouseMoveAt = Anim.now();
        }
    }

    int cursorX() {
        return lastMouseX;
    }

    int cursorY() {
        return lastMouseY;
    }

    /**
     * How long the cursor has been still, in milliseconds.
     */
    long idleMillis() {
        return Anim.now() - lastMouseMoveAt;
    }

    /**
     * Treats the cursor as having just moved, without moving it — what a mode change
     * does, so the controls that were just rearranged are visible where they landed
     * rather than already timed out.
     */
    void wake() {
        lastMouseMoveAt = Anim.now();
    }

    // ------------------------------------------------------------------
    // Clicks
    // ------------------------------------------------------------------

    /**
     * Whether this click closes a double-click with the one before it: soon enough, and
     * near enough that it was aimed at the same thing rather than being two separate
     * clicks that happened to be quick.
     */
    boolean isDoubleClick(double mouseX, double mouseY) {
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        long at = Anim.now();
        boolean paired = at - lastClickAt <= DOUBLE_CLICK_MS
                && Math.abs(x - lastClickX) <= DOUBLE_CLICK_SLOP
                && Math.abs(y - lastClickY) <= DOUBLE_CLICK_SLOP;
        // Reset rather than extend, so three fast clicks are one pair and a stray
        // click, not two overlapping pairs.
        lastClickAt = paired ? 0 : at;
        lastClickX = x;
        lastClickY = y;
        return paired;
    }

    /**
     * Starts the mark that reports a click was taken.
     */
    void flash(double mouseX, double mouseY) {
        flashX = (int) Math.round(mouseX);
        flashY = (int) Math.round(mouseY);
        flashAt = Anim.now();
    }

    int flashX() {
        return flashX;
    }

    int flashY() {
        return flashY;
    }

    /**
     * How far through the click flash, 0..1; at or past 1 there is nothing to draw.
     */
    double flashProgress() {
        return Anim.progress(flashAt, FLASH_MS);
    }

    // ------------------------------------------------------------------
    // Drags
    // ------------------------------------------------------------------

    /**
     * Begins a move drag, remembering where inside the box it was grabbed so the window
     * does not jump to put its corner under the cursor.
     */
    void beginMove(double mouseX, double mouseY, int boxX, int boxY) {
        draggingMove = true;
        grabDX = (int) Math.round(mouseX) - boxX;
        grabDY = (int) Math.round(mouseY) - boxY;
    }

    void beginResize() {
        draggingResize = true;
    }

    boolean isMoving() {
        return draggingMove;
    }

    boolean isResizing() {
        return draggingResize;
    }

    /**
     * Whether a gesture is in progress at all — what stops the window arrangement being
     * written to disk mid-drag.
     */
    boolean isDragging() {
        return draggingMove || draggingResize;
    }

    /**
     * Where the box's top-left should go for a cursor at {@code mouseX}, honouring the
     * grab offset.
     */
    int moveToX(double mouseX) {
        return (int) Math.round(mouseX) - grabDX;
    }

    int moveToY(double mouseY) {
        return (int) Math.round(mouseY) - grabDY;
    }

    /**
     * Ends whatever drag was in progress.
     *
     * @return whether there was one, which is also the answer to "was the release ours?"
     */
    boolean release() {
        boolean handled = draggingMove || draggingResize;
        draggingMove = false;
        draggingResize = false;
        return handled;
    }
}
