package com.lia.mediaplayer.gui;

import net.minecraft.util.Mth;

/**
 * The mod's whole palette, in one place — the colour equivalent of {@link Glyphs}.
 *
 * <p>Every window, panel, list and overlay the mod draws picks its colours from here
 * rather than declaring its own constants. Before this class the same nine values were
 * spelled out in {@code MediaWindow}, re-declared under different names in
 * {@code VideoWindow} and {@code PlaylistScreen}, and inlined as raw literals in
 * {@code MediaWindowOverlay} — so "the hovered colour" was four independent
 * definitions that only happened to agree.</p>
 *
 * <p>The names are <em>roles</em>, not shades ({@link #ICON_HOVER}, not "yellow"): a
 * call site says what the pixel means, which is what makes swapping the whole palette
 * a change to this file alone.</p>
 */
final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------
    // Surfaces
    // ------------------------------------------------------------------

    /** The body of a media window, behind the picture. */
    static final int WINDOW_BG = 0xD0101010;
    /** The strip above a window's content, carrying its title and corner buttons. */
    static final int TITLE_BAR_BG = 0xF01C1C1C;
    /** The control bar strip below a player's content. */
    static final int CONTROL_BAR_BG = 0xF0181818;
    /** Backdrop behind a corner button (link / hide / close), over the picture. */
    static final int CORNER_BUTTON_BG = 0x80000000;
    /** Fills the content rect while there is no frame to show yet. */
    static final int PLACEHOLDER = 0xFF000000;
    /** The video player's docked queue panel. */
    static final int PANEL_BG = 0xF0141414;
    /** The queue panel's header strip. */
    static final int PANEL_HEADER_BG = 0xFF1E1E1E;
    /** A full-screen list background (the playlist screen's two columns). */
    static final int LIST_BG = 0xC0101010;
    /** The volume slider's pop-up backdrop. */
    static final int POPUP_BG = 0xE0101010;
    /** The floating chat image preview's backdrop. */
    static final int PREVIEW_BG = 0xF0100010;
    /** A small overlay button over the chat ("Playlists", "N players"). */
    static final int CHIP_BG = 0xD0181818;
    /** The same chip under the cursor. */
    static final int CHIP_HOVER_BG = 0xF0303030;
    /** The "now playing" banner that announces a track no window is showing. */
    static final int BANNER_BG = 0xE0141414;

    // ------------------------------------------------------------------
    // Outlines
    // ------------------------------------------------------------------

    /** The 1 px edge of a window that is not the front one. */
    static final int BORDER = 0x60FFFFFF;
    /**
     * The same edge on the front window. The z-stack has always existed; this is the
     * only thing that makes it visible, so the two values have to read apart at a
     * glance rather than being two shades of the same grey.
     */
    static final int BORDER_FOCUSED = 0xFF4CA6FF;
    /** The edge of a panel that is not a window (the banner, the queue panel). */
    static final int BORDER_SUBTLE = 0x40FFFFFF;

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    static final int ROW_BG = 0xFF202020;
    static final int ROW_HOVER_BG = 0xFF2E2E38;
    static final int ROW_SELECTED_BG = 0xFF394A6B;

    // ------------------------------------------------------------------
    // Bars
    // ------------------------------------------------------------------

    /** The unfilled part of a seek or volume bar. */
    static final int TRACK = 0xFF4A4A4A;
    /** The elapsed part of a seek or volume bar. */
    static final int FILL = 0xFF4CA6FF;
    /** The handle riding on a seek or volume bar. */
    static final int KNOB = 0xFFFFFFFF;
    static final int SCROLL_TRACK = 0xFF333333;
    static final int SCROLL_THUMB = 0xFF6A6A6A;

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    static final int TEXT = 0xFFFFFFFF;
    /** Secondary text: counts, hints, list captions. */
    static final int TEXT_SUBTLE = 0xFFAAAAAA;
    /** Text that is barely there: a placeholder inside a thumbnail. */
    static final int TEXT_DIM = 0xFF888888;

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    /** A control glyph at rest. */
    static final int ICON = 0xFFE0E0E0;
    /** A control glyph under the cursor. */
    static final int ICON_HOVER = 0xFFFFD23F;
    /**
     * A toggle whose mode is on (loop, shuffle) — distinct from {@link #ICON_HOVER} so
     * "active" and "hovered" never read as the same state.
     */
    static final int ICON_ACTIVE = 0xFF4CA6FF;
    /** A toggle whose mode is off. */
    static final int ICON_INACTIVE = 0xFF8A8A8A;
    /** A control that cannot be used right now (no previous track, first row, …). */
    static final int ICON_DISABLED = 0xFF555555;
    /**
     * The flash left behind by a click. Windows are not screen widgets, so a button has
     * no held state to draw from; the flash is drawn at the point that was clicked and
     * fades out, which reports the press wherever it landed — a button, the seek bar or
     * a queue row alike.
     */
    static final int PRESS_FLASH = 0x90FFFFFF;

    // ------------------------------------------------------------------
    // Accents
    // ------------------------------------------------------------------

    /** An error message, or a destructive control under the cursor. */
    static final int DANGER = 0xFFFF6B6B;
    /** The cross drawn over a muted speaker; brighter, because it is tiny. */
    static final int MUTED = 0xFFFF5555;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * The same colour at a fraction of its opacity, for anything that fades. Only the
     * alpha channel moves, so a colour keeps its identity all the way down to
     * invisible — fading towards the background instead would tie every animation to
     * whatever happens to be behind it.
     */
    static int withAlpha(int argb, double factor) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(factor, 0.0, 1.0));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
