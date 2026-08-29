/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.window;

import com.lia.mediaplayer.api.MediaHandle;

import net.minecraft.network.chat.Component;

/**
 * An addon's own button in a media window's corner row — beside the heart, the copy
 * button and the browser link.
 *
 * <p>This is how "share to my party", "save to my gallery" or "report this link" gets
 * onto the window without forking the UI. Register with
 * {@link com.lia.mediaplayer.api.LiasMediaPlayerApi#registerWindowAction}.</p>
 *
 * <h2>The cap</h2>
 *
 * <p><b>At most {@value #MAX_VISIBLE} addon buttons are drawn</b>, in registration
 * order, and only on windows whose chrome has buttons at all (a bare or decorative
 * window opened through {@code WindowChromeOptions} has none). The row is packed
 * right-to-left from the window's edge and is part of a window's <em>minimum width</em>,
 * so an unbounded row would push a small player's title out entirely. Registrations past
 * the cap are kept and logged, not silently dropped — they appear if an earlier one
 * stops applying.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #appliesTo} is asked while the window lays its buttons out, which is every
 * frame: keep it a field read, not a lookup. Both it and {@link #onClick} run on the
 * render thread. An action that throws is logged and skipped for that frame.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public interface WindowAction {

    /** How many addon buttons a window draws at most. */
    int MAX_VISIBLE = 3;

    /**
     * A stable id, namespaced with your mod id ({@code "myaddon:share"}). Used in logs
     * and to keep a second registration of the same action from doubling the button.
     */
    String id();

    /** What the button says under the cursor. Translated — see the i18n rule in the docs. */
    Component tooltip();

    /** Which of the mod's icons to draw. */
    ActionIcon icon();

    /**
     * Whether this action means anything for {@code handle} right now. Asked per window
     * and per frame; {@code false} takes the button out of the row entirely rather than
     * greying it out, and the rest of the row slides over.
     */
    default boolean appliesTo(MediaHandle handle) {
        return true;
    }

    /** The button was clicked. Render thread. */
    void onClick(MediaHandle handle);
}
