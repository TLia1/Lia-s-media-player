/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * An addon's own page, reachable from the mod's library screens.
 *
 * <p>The playlist and history screens are where a player goes to look at their media, and
 * an addon that keeps media of its own — a shared queue, a server's featured list, a
 * gallery — wants to be reachable from there rather than from a key binding nobody
 * remembers. Register with
 * {@link com.lia.mediaplayer.api.LiasMediaPlayerApi#registerScreenTab} and the mod adds a
 * button that opens {@link #open}.</p>
 *
 * <h2>What "tab" means here</h2>
 *
 * <p>A button in the library screens' footer row, not a tab strip: the two screens are
 * not tabbed and turning them into a tabbed pair to host this would be a redesign of the
 * mod's own UI for a feature that is asking for a way <em>in</em>. What an addon gets is
 * what a tab would have given it — a labelled way from the library to its own screen, and
 * the screen it came from to go back to. Up to {@value #MAX_VISIBLE} are shown, in
 * registration order.</p>
 *
 * <p>Render thread only.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.4.0
 */
public interface MediaScreenTab {

    /** How many addon buttons the library screens show. */
    int MAX_VISIBLE = 3;

    /** A stable, namespaced id ({@code "myaddon:featured"}). Registering it twice replaces the first. */
    String id();

    /** The button's label. Translated, and short — the footer row is not wide. */
    Component title();

    /** The button's tooltip, or {@code null} for none. */
    @Nullable
    default Component tooltip() {
        return null;
    }

    /**
     * The screen to open.
     *
     * @param parent the library screen the player came from. Send them back to it when
     *               yours closes ({@code minecraft.setScreen(parent)}), or the Escape key
     *               drops them into the world from the middle of the library.
     * @return the screen, or {@code null} to do nothing (a tab that is not ready yet)
     */
    @Nullable
    Screen open(Screen parent);
}
