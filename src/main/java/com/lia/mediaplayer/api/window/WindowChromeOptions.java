/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * Which parts of the window furniture exist.
 *
 * <p>The mod's own windows are {@link #full()} and always will be; this is for the cases
 * a chat mod never had — a cutscene that must not be paused, a backdrop that must not eat
 * clicks, a picture with no title bar over it.</p>
 *
 * <p>Turning a part off removes both the thing drawn and the input that went with it, so
 * a window with {@code resizable = false} has no grip <em>and</em> ignores a
 * {@code Ctrl}+wheel. Anything the mod itself does — the queue advancing, a track ending,
 * the API's own transport calls — is unaffected: this hides controls, it does not lock
 * playback. {@code MediaHandle} still drives a window with no chrome at all, which is the
 * point of {@link #display()}.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @param titleBar    the strip above the picture carrying its name
 * @param controls    the transport row below it (play, seek, volume, loop)
 * @param closeButton the corner button that closes the window
 * @param hideButton  the corner button that hides it while it keeps playing
 * @param resizable   the bottom-right grip and {@code Ctrl}+wheel zoom
 * @param movable     dragging the body of the window
 * @param queuePanel  the list of what plays next, docked beside the window
 * @param interactive whether clicks reach this window at all; {@code false} lets them
 *                    pass through to whatever is behind it
 * @since API 2.2.0
 */
public record WindowChromeOptions(boolean titleBar, boolean controls, boolean closeButton,
                                  boolean hideButton, boolean resizable, boolean movable,
                                  boolean queuePanel, boolean interactive) {

    private static final WindowChromeOptions FULL =
            new WindowChromeOptions(true, true, true, true, true, true, true, true);
    private static final WindowChromeOptions BARE =
            new WindowChromeOptions(false, false, false, false, true, true, false, true);
    private static final WindowChromeOptions DISPLAY =
            new WindowChromeOptions(false, false, false, false, false, false, false, false);

    /** Everything, which is what the mod's own windows use. */
    public static WindowChromeOptions full() {
        return FULL;
    }

    /**
     * The picture alone — no title bar, no controls, no corner buttons — but still
     * movable and resizable by the user. For a cutscene or a backdrop that the player
     * is nonetheless allowed to get out of the way.
     */
    public static WindowChromeOptions bare() {
        return BARE;
    }

    /**
     * The picture, and no input of any kind: clicks pass straight through to whatever is
     * behind it. A decorative display an addon drives entirely through its
     * {@code MediaHandle} — and the one thing here a caller has to think about, because a
     * window like this can only be closed by the addon that opened it.
     */
    public static WindowChromeOptions display() {
        return DISPLAY;
    }

    /** This, with the title bar turned on or off. */
    public WindowChromeOptions withTitleBar(boolean value) {
        return new WindowChromeOptions(value, controls, closeButton, hideButton,
                resizable, movable, queuePanel, interactive);
    }

    /** This, with the control bar turned on or off. */
    public WindowChromeOptions withControls(boolean value) {
        return new WindowChromeOptions(titleBar, value, closeButton, hideButton,
                resizable, movable, queuePanel, interactive);
    }

    /** This, with the queue panel allowed or forbidden. */
    public WindowChromeOptions withQueuePanel(boolean value) {
        return new WindowChromeOptions(titleBar, controls, closeButton, hideButton,
                resizable, movable, value, interactive);
    }

    /** This, taking clicks or letting them through. */
    public WindowChromeOptions withInteractive(boolean value) {
        return new WindowChromeOptions(titleBar, controls, closeButton, hideButton,
                resizable, movable, queuePanel, value);
    }
}
