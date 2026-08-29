/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.theme;

/**
 * One colour of the mod's palette, named by what it <em>is for</em> rather than by what
 * it looks like.
 *
 * <p>This is the public face of {@code gui.Theme}, and it is the reason an addon can
 * supply a palette at all: every pixel the mod draws already comes from one of these
 * roles, so a theme is a map and not a rewrite. The names match the fields in
 * {@code Theme} one for one.</p>
 *
 * <p>Constants are only ever added, never removed or renamed. A role a
 * {@link MediaTheme} does not mention keeps the built-in dark palette's value, so a
 * theme written against an older version still draws every part of a newer UI.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public enum ThemeRole {

    // Surfaces
    /** A media window's body, behind the picture. */
    WINDOW_BG,
    /** The strip a window's title and corner buttons sit in. */
    TITLE_BAR_BG,
    /** The transport bar under a player. */
    CONTROL_BAR_BG,
    /** The pad behind a corner button on a window with no title strip. */
    CORNER_BUTTON_BG,
    /** What is drawn where a picture has not arrived yet. */
    PLACEHOLDER,
    /** A docked panel — the queue panel is the one everyone sees. */
    PANEL_BG,
    /** A panel's own header strip. */
    PANEL_HEADER_BG,
    /** The body of a scrolling list. */
    LIST_BG,
    /** A popup or dropdown over everything else. */
    POPUP_BG,
    /** The backdrop of a hover preview. */
    PREVIEW_BG,
    /** A small rounded label. */
    CHIP_BG,
    /** The same, under the cursor. */
    CHIP_HOVER_BG,
    /** The "now playing" banner. */
    BANNER_BG,

    // Edges
    /** The ordinary border of a window or panel. */
    BORDER,
    /** The border of whatever has the focus. */
    BORDER_FOCUSED,
    /** A divider inside a panel. */
    BORDER_SUBTLE,

    // Rows
    /** A list row. */
    ROW_BG,
    /** A list row under the cursor. */
    ROW_HOVER_BG,
    /** The selected list row. */
    ROW_SELECTED_BG,

    // Sliders and bars
    /** The unfilled part of a seek or volume bar. */
    TRACK,
    /** The filled part. */
    FILL,
    /** The draggable knob. */
    KNOB,
    /** A scrollbar's groove. */
    SCROLL_TRACK,
    /** A scrollbar's thumb. */
    SCROLL_THUMB,

    // Text
    /** Ordinary text. */
    TEXT,
    /** Secondary text — a window title, a caption. */
    TEXT_SUBTLE,
    /** Text that is present but not the point. */
    TEXT_DIM,

    // Icons
    /** An icon at rest. */
    ICON,
    /** An icon under the cursor. */
    ICON_HOVER,
    /** An icon whose toggle is on. */
    ICON_ACTIVE,
    /** An icon whose toggle is off. */
    ICON_INACTIVE,
    /** An icon that cannot be clicked right now. */
    ICON_DISABLED,

    // Feedback
    /** The mark a click leaves behind. */
    PRESS_FLASH,
    /** An error, or a destructive control under the cursor. */
    DANGER,
    /** The cross over a muted speaker. */
    MUTED
}
