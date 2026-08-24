package com.lia.mediaplayer.gui;

/**
 * A screen of the mod's own that wants the mouse drag the loader bridges already carry.
 *
 * <p>Vanilla has {@code GuiEventListener.mouseDragged} and
 * {@code mouseReleased}, but their signatures moved with the rest of the mouse callbacks
 * at 1.21.11 (loose arguments to a {@code MouseButtonEvent} record), so overriding them
 * in a screen would mean a version guard around each one. The mod already receives both
 * events for <em>every</em> screen, in vanilla types, through
 * {@code platform.ClientHooks} — that is how a media window is dragged — and Fabric's
 * side of it even reconstructs the drag on the versions with no drag event at all. A
 * screen that implements this is routed the same stream by
 * {@link MediaWindowOverlay#mouseDragged} and {@link MediaWindowOverlay#mouseReleased},
 * and needs no guard of its own.</p>
 *
 * <p>There is deliberately no "press" method here: a press arrives as an ordinary
 * {@code mouseClicked}, which every version delivers to the screen and which the screen
 * has to handle anyway.</p>
 */
interface DragTarget {

    /**
     * The cursor moved with a button held.
     *
     * @return {@code true} when this screen is dragging something and the event must go
     *         no further
     */
    boolean onDrag(double mouseX, double mouseY);

    /**
     * The button came back up, wherever the cursor was last seen by {@link #onDrag}.
     *
     * @return {@code true} when this ended a drag
     */
    boolean onRelease();
}
