/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.input;

import com.lia.mediaplayer.api.MediaHandle;

import org.jetbrains.annotations.Nullable;

/**
 * What an addon's window shortcut does when its key is pressed over a screen that hosts
 * the media windows.
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
@FunctionalInterface
public interface WindowShortcutAction {

    /**
     * Runs the shortcut.
     *
     * @param frontMost the front-most player that has a transport — the same window the
     *                  mod's own shortcuts act on — or {@code null} when nothing is
     *                  playing
     * @return {@code true} if the key was used, which stops the screen from seeing it.
     *         Answer {@code false} when there was nothing to do: swallowing a key on a
     *         screen with a text field in it is how a shortcut becomes a bug report.
     */
    boolean onPress(@Nullable MediaHandle frontMost);
}
