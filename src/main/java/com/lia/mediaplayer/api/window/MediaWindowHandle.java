/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * The window half of a {@link com.lia.mediaplayer.api.MediaHandle} — where it is, how big
 * it is, and what it lets the user do to it.
 *
 * <p>Reached through {@code handle.window()}, which is empty for a handle that has no
 * window (a headless player, an off-screen surface).</p>
 *
 * <p><b>Render thread only</b>, all of it: these read and write the geometry the layout
 * pass works on.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.2.0
 */
public interface MediaWindowHandle {

    /** The box's left edge, in GUI pixels, as of the last frame drawn. */
    int x();

    int y();

    /** The whole box including the title bar and control bar, not just the picture. */
    int width();

    int height();

    /**
     * Moves the window. A placement that resolves against the screen ({@code relative},
     * {@code anchored}) keeps doing so on every later frame, so it survives a resize.
     */
    void setPlacement(Placement placement);

    /** Resizes the window, aspect ratio preserved. */
    void setSizing(Sizing sizing);

    /** Whether the window is on screen; a hidden player keeps playing. */
    boolean isVisible();

    void setVisible(boolean visible);

    /** Raises this window above the rest of the stack. */
    void bringToFront();

    /** Whether the window currently fills the screen. */
    boolean isTheater();

    void setTheater(boolean theater);

    /** Which parts of the furniture this window has — see {@link WindowChromeOptions}. */
    WindowChromeOptions chrome();

    void setChrome(WindowChromeOptions chrome);

    /** Shorthand for {@code setChrome(chrome().withInteractive(interactive))}. */
    void setInteractive(boolean interactive);

    /**
     * Whether this window's geometry is written back to {@code windows.json}.
     *
     * <p>Off by default for a window the API opened, and that default is not an
     * oversight. The store is keyed by window <em>kind</em>, not by window: one entry for
     * "the video window", shared by every video window there will ever be. An addon that
     * parks a player in a corner would otherwise overwrite where the user likes their own
     * video window to open, and would go on doing it every session.</p>
     */
    boolean persistsGeometry();

    void setPersistGeometry(boolean persist);
}
