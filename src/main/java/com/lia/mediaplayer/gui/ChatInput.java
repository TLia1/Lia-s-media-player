package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * The mod's single point of contact with "is the player part-way through typing a chat
 * message?".
 *
 * <p>{@link WindowShortcuts} needs the answer because the media windows are driven from
 * over the chat screen, where the text field has the focus and every key the mod might
 * claim is also a key someone could be typing. {@code ChatScreen.input} is
 * {@code protected}, so this reads it the way any screen exposes its widgets — through
 * {@link Screen#children()}, which the field is registered into on every version
 * ({@code addWidget} up to 1.21.11, {@code addRenderableWidget} after; both land in the
 * same list).</p>
 *
 * <p>An empty field means the player is not typing yet, which is what makes it safe for
 * a bare {@code Space} or arrow key to mean "pause" or "seek": neither does anything to
 * an empty {@link EditBox}, and the first character of a real message is never one of
 * them.</p>
 */
final class ChatInput {

    private ChatInput() {
    }

    /**
     * Whether {@code screen}'s text field holds nothing — including when it has no text
     * field at all, which is the honest answer for a screen where nothing is being
     * typed.
     */
    static boolean isEmpty(Screen screen) {
        if (screen == null) {
            return true;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box) {
                return box.getValue().isEmpty();
            }
        }
        return true;
    }
}
