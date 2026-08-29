/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

/**
 * One of the nine points of the screen a window can be pinned to — see
 * {@link Placement#anchored(Anchor, int, int)}.
 *
 * <p>The richer sibling of the mod's own five-value window-position setting, which stays
 * as it is: that one is a user preference with a dropdown behind it, this one is what an
 * addon says when it means "the top-right corner, four pixels in".</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 2.2.0
 */
public enum Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    /** Whether this anchor sits against the right edge, where an inset moves left. */
    public boolean isRight() {
        return this == TOP_RIGHT || this == CENTER_RIGHT || this == BOTTOM_RIGHT;
    }

    /** Whether this anchor sits against the left edge, where an inset moves right. */
    public boolean isLeft() {
        return this == TOP_LEFT || this == CENTER_LEFT || this == BOTTOM_LEFT;
    }

    /** Whether this anchor sits against the bottom edge, where an inset moves up. */
    public boolean isBottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_CENTER || this == BOTTOM_RIGHT;
    }

    /** Whether this anchor sits against the top edge, where an inset moves down. */
    public boolean isTop() {
        return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
    }
}
