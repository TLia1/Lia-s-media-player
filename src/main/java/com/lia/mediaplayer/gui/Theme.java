package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;

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
 * a change to this file alone — and what makes {@link ThemeName} possible at all.</p>
 *
 * <h2>Why the fields are not final</h2>
 *
 * <p>They are the <em>active</em> palette, not constants: {@link #apply} rewrites all of
 * them when the theme setting changes. Nothing caches them — every call site reads them
 * while it draws — so a switch takes effect on the next frame with no invalidation to
 * do. Each theme is written as what it changes about {@link #installDark()}, which is
 * re-installed in full first: a role a theme does not mention keeps the dark value on
 * purpose, and cannot be left holding the <em>previous</em> theme's.</p>
 */
final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------
    // Surfaces
    // ------------------------------------------------------------------

    /** The body of a media window, behind the picture. */
    static int WINDOW_BG;
    /** The strip above a window's content, carrying its title and corner buttons. */
    static int TITLE_BAR_BG;
    /** The control bar strip below a player's content. */
    static int CONTROL_BAR_BG;
    /** Backdrop behind a corner button (link / hide / close), over the picture. */
    static int CORNER_BUTTON_BG;
    /** Fills the content rect while there is no frame to show yet. */
    static int PLACEHOLDER;
    /** The video player's docked queue panel. */
    static int PANEL_BG;
    /** The queue panel's header strip. */
    static int PANEL_HEADER_BG;
    /** A full-screen list background (the playlist screen's two columns). */
    static int LIST_BG;
    /** The volume slider's pop-up backdrop. */
    static int POPUP_BG;
    /** The floating chat image preview's backdrop. */
    static int PREVIEW_BG;
    /** A small overlay button over the chat ("Playlists", "N players"). */
    static int CHIP_BG;
    /** The same chip under the cursor. */
    static int CHIP_HOVER_BG;
    /** The "now playing" banner that announces a track no window is showing. */
    static int BANNER_BG;

    // ------------------------------------------------------------------
    // Outlines
    // ------------------------------------------------------------------

    /** The 1 px edge of a window that is not the front one. */
    static int BORDER;
    /**
     * The same edge on the front window. The z-stack has always existed; this is the
     * only thing that makes it visible, so the two values have to read apart at a
     * glance rather than being two shades of the same grey.
     */
    static int BORDER_FOCUSED;
    /** The edge of a panel that is not a window (the banner, the queue panel). */
    static int BORDER_SUBTLE;

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    static int ROW_BG;
    static int ROW_HOVER_BG;
    static int ROW_SELECTED_BG;

    // ------------------------------------------------------------------
    // Bars
    // ------------------------------------------------------------------

    /** The unfilled part of a seek or volume bar. */
    static int TRACK;
    /** The elapsed part of a seek or volume bar. */
    static int FILL;
    /** The handle riding on a seek or volume bar. */
    static int KNOB;
    static int SCROLL_TRACK;
    static int SCROLL_THUMB;

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    static int TEXT;
    /** Secondary text: counts, hints, list captions. */
    static int TEXT_SUBTLE;
    /** Text that is barely there: a placeholder inside a thumbnail. */
    static int TEXT_DIM;

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    /** A control glyph at rest. */
    static int ICON;
    /** A control glyph under the cursor. */
    static int ICON_HOVER;
    /**
     * A toggle whose mode is on (loop, shuffle) — distinct from {@link #ICON_HOVER} so
     * "active" and "hovered" never read as the same state.
     */
    static int ICON_ACTIVE;
    /** A toggle whose mode is off. */
    static int ICON_INACTIVE;
    /** A control that cannot be used right now (no previous track, first row, …). */
    static int ICON_DISABLED;
    /**
     * The flash left behind by a click. Windows are not screen widgets, so a button has
     * no held state to draw from; the flash is drawn at the point that was clicked and
     * fades out, which reports the press wherever it landed — a button, the seek bar or
     * a queue row alike.
     */
    static int PRESS_FLASH;

    // ------------------------------------------------------------------
    // Accents
    // ------------------------------------------------------------------

    /** An error message, or a destructive control under the cursor. */
    static int DANGER;
    /** The cross drawn over a muted speaker; brighter, because it is tiny. */
    static int MUTED;

    // ------------------------------------------------------------------
    // The active theme
    // ------------------------------------------------------------------

    /** Which palette is installed. Never null: the dark one is installed on load. */
    private static ThemeName active;

    static {
        apply(ThemeName.DARK);
    }

    /** The theme currently installed. */
    static ThemeName active() {
        return active;
    }

    /**
     * Installs {@code name}'s palette, replacing every role.
     *
     * <p>The dark palette goes down first whatever is asked for, so a theme only has to
     * write the roles it actually changes — see the class note.</p>
     */
    static void apply(ThemeName name) {
        installDark();
        switch (name) {
            case DARK -> {
                // the baseline itself
            }
            case CONTRAST -> installContrast();
            case MINECRAFT -> installMinecraft();
        }
        active = name;
    }

    /**
     * Re-reads the theme setting and installs it if it changed.
     *
     * <p>Polled once a client tick rather than pushed from the settings screen: the
     * value can change from the option's widget, from its reset button, or from the
     * config file being re-read, and a poll covers all three without every one of them
     * having to remember to notify anybody. Comparing two enum references costs
     * nothing, and installing only happens on the tick the value actually moves.</p>
     */
    static void refresh() {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context == null) {
            return;
        }
        ThemeName wanted = context.getConfigStore().theme();
        if (wanted != active) {
            apply(wanted);
        }
    }

    // ------------------------------------------------------------------
    // The palettes
    // ------------------------------------------------------------------

    /** The mod's own look, and the baseline every other theme is written against. */
    private static void installDark() {
        WINDOW_BG = 0xD0101010;
        TITLE_BAR_BG = 0xF01C1C1C;
        CONTROL_BAR_BG = 0xF0181818;
        CORNER_BUTTON_BG = 0x80000000;
        PLACEHOLDER = 0xFF000000;
        PANEL_BG = 0xF0141414;
        PANEL_HEADER_BG = 0xFF1E1E1E;
        LIST_BG = 0xC0101010;
        POPUP_BG = 0xE0101010;
        PREVIEW_BG = 0xF0100010;
        CHIP_BG = 0xD0181818;
        CHIP_HOVER_BG = 0xF0303030;
        BANNER_BG = 0xE0141414;

        BORDER = 0x60FFFFFF;
        BORDER_FOCUSED = 0xFF4CA6FF;
        BORDER_SUBTLE = 0x40FFFFFF;

        ROW_BG = 0xFF202020;
        ROW_HOVER_BG = 0xFF2E2E38;
        ROW_SELECTED_BG = 0xFF394A6B;

        TRACK = 0xFF4A4A4A;
        FILL = 0xFF4CA6FF;
        KNOB = 0xFFFFFFFF;
        SCROLL_TRACK = 0xFF333333;
        SCROLL_THUMB = 0xFF6A6A6A;

        TEXT = 0xFFFFFFFF;
        TEXT_SUBTLE = 0xFFAAAAAA;
        TEXT_DIM = 0xFF888888;

        ICON = 0xFFE0E0E0;
        ICON_HOVER = 0xFFFFD23F;
        ICON_ACTIVE = 0xFF4CA6FF;
        ICON_INACTIVE = 0xFF8A8A8A;
        ICON_DISABLED = 0xFF555555;
        PRESS_FLASH = 0x90FFFFFF;

        DANGER = 0xFFFF6B6B;
        MUTED = 0xFFFF5555;
    }

    /**
     * Everything pushed apart: opaque black surfaces, white edges, text that does not
     * rely on a shade of grey to be told from another. For a small GUI scale, a bright
     * screen, or eyes that would rather not hunt for the seek bar.
     */
    private static void installContrast() {
        WINDOW_BG = 0xFF000000;
        TITLE_BAR_BG = 0xFF000000;
        CONTROL_BAR_BG = 0xFF000000;
        CORNER_BUTTON_BG = 0xC0000000;
        PANEL_BG = 0xFF000000;
        PANEL_HEADER_BG = 0xFF000000;
        LIST_BG = 0xFF000000;
        POPUP_BG = 0xFF000000;
        PREVIEW_BG = 0xFF000000;
        CHIP_BG = 0xFF000000;
        CHIP_HOVER_BG = 0xFF444444;
        BANNER_BG = 0xFF000000;

        BORDER = 0xFFFFFFFF;
        BORDER_FOCUSED = 0xFF00E5FF;
        BORDER_SUBTLE = 0xC0FFFFFF;

        ROW_BG = 0xFF000000;
        ROW_HOVER_BG = 0xFF303030;
        ROW_SELECTED_BG = 0xFF0057B8;

        TRACK = 0xFF666666;
        FILL = 0xFF00E5FF;
        SCROLL_TRACK = 0xFF333333;
        SCROLL_THUMB = 0xFFAAAAAA;

        TEXT_SUBTLE = 0xFFDDDDDD;
        TEXT_DIM = 0xFFBBBBBB;

        ICON = 0xFFFFFFFF;
        ICON_HOVER = 0xFFFFE000;
        ICON_ACTIVE = 0xFF00E5FF;
        ICON_INACTIVE = 0xFF999999;
        ICON_DISABLED = 0xFF666666;
        PRESS_FLASH = 0xC0FFFFFF;

        DANGER = 0xFFFF5555;
        MUTED = 0xFFFF3333;
    }

    /**
     * Vanilla's own tones: the translucent {@code 0xC0101010} a screen dims the world
     * with, its grey widget edges, its {@code §7} secondary text and its {@code §a} /
     * {@code §e} / {@code §c} accents. For anyone who finds the default look too much
     * like a desktop application sitting on top of the game.
     */
    private static void installMinecraft() {
        WINDOW_BG = 0xC0101010;
        TITLE_BAR_BG = 0xE0202020;
        CONTROL_BAR_BG = 0xE0202020;
        CORNER_BUTTON_BG = 0x90000000;
        PANEL_BG = 0xD0101010;
        PANEL_HEADER_BG = 0xE0202020;
        LIST_BG = 0xC0101010;
        POPUP_BG = 0xE0101010;
        PREVIEW_BG = 0xE0101010;
        CHIP_BG = 0xC0101010;
        CHIP_HOVER_BG = 0xE0404040;
        BANNER_BG = 0xD0101010;

        // Vanilla marks the widget you are on with a white outline, not a coloured one.
        BORDER = 0xFF6C6C6C;
        BORDER_FOCUSED = 0xFFFFFFFF;
        BORDER_SUBTLE = 0xFF3C3C3C;

        ROW_BG = 0xFF2B2B2B;
        ROW_HOVER_BG = 0xFF3C3C3C;
        ROW_SELECTED_BG = 0xFF505050;

        TRACK = 0xFF6C6C6C;
        FILL = 0xFF55FF55;
        SCROLL_TRACK = 0xFF000000;
        SCROLL_THUMB = 0xFF8B8B8B;

        TEXT_SUBTLE = 0xFFA0A0A0;
        TEXT_DIM = 0xFF808080;

        ICON_HOVER = 0xFFFFFF55;
        ICON_ACTIVE = 0xFF55FF55;
        ICON_INACTIVE = 0xFFA0A0A0;
        ICON_DISABLED = 0xFF6C6C6C;

        DANGER = 0xFFFF5555;
    }

    /**
     * The inverse: light surfaces, dark text and dark icons.
     *
     * <p>Every role that carries meaning by being <em>bright</em> has to flip, not just
     * the backgrounds — a white press flash is invisible on a white panel, and the
     * hover amber has to darken to stay legible on one.</p>
     */
    private static void installLight() {
        WINDOW_BG = 0xF0F2F2F2;
        TITLE_BAR_BG = 0xFFE2E2E2;
        CONTROL_BAR_BG = 0xFFE8E8E8;
        CORNER_BUTTON_BG = 0x60FFFFFF;
        PLACEHOLDER = 0xFFD8D8D8;
        PANEL_BG = 0xF0EDEDED;
        PANEL_HEADER_BG = 0xFFDCDCDC;
        LIST_BG = 0xE0F2F2F2;
        POPUP_BG = 0xF0F2F2F2;
        PREVIEW_BG = 0xF0F5F0F5;
        CHIP_BG = 0xE0E8E8E8;
        CHIP_HOVER_BG = 0xFFFFFFFF;
        BANNER_BG = 0xF0EDEDED;

        BORDER = 0x60000000;
        BORDER_FOCUSED = 0xFF1B72C4;
        BORDER_SUBTLE = 0x30000000;

        ROW_BG = 0xFFE4E4E4;
        ROW_HOVER_BG = 0xFFD2DAEA;
        ROW_SELECTED_BG = 0xFFAFC6EE;

        TRACK = 0xFFBEBEBE;
        FILL = 0xFF1B72C4;
        KNOB = 0xFF202020;
        SCROLL_TRACK = 0xFFD0D0D0;
        SCROLL_THUMB = 0xFF909090;

        TEXT = 0xFF161616;
        TEXT_SUBTLE = 0xFF4A4A4A;
        TEXT_DIM = 0xFF7A7A7A;

        ICON = 0xFF303030;
        ICON_HOVER = 0xFFB07000;
        ICON_ACTIVE = 0xFF1B72C4;
        ICON_INACTIVE = 0xFF8A8A8A;
        ICON_DISABLED = 0xFFB4B4B4;
        PRESS_FLASH = 0x60000000;

        DANGER = 0xFFC62828;
        MUTED = 0xFFD32F2F;
    }

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
