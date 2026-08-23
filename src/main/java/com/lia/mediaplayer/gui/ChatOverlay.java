package com.lia.mediaplayer.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

/**
 * The mod's single point of contact with the chat overlay object itself — "where does
 * the chat hud live on the client, and what tick count is it fading against?".
 *
 * <p>26.2 split the in-game HUD out of {@code Gui}: {@code Gui} kept the screen stack
 * and gained the {@code Hud} it now owns, and the chat overlay moved onto that
 * {@code Hud}. Both accessors keep their names, only their owner changed.</p>
 *
 * <p>Two callers need it and they sit in different packages: {@link ChatHitTest}, which
 * asks the overlay what is under the cursor, and the Fabric bridge, which has to put a
 * rewritten player message into it by hand (Fabric has no modify-chat event). Hence one
 * public seam rather than the same guard written twice.</p>
 */
public final class ChatOverlay {
    private ChatOverlay() {
    }

    /** The chat overlay of the running client. */
    public static ChatComponent chat(Minecraft mc) {
        //? if <26.2 {
        return mc.gui.getChat();
        //?} else
        /*return mc.gui.hud.getChat();*/
    }

    /** The gui tick count that drives the unfocused chat fade-out. */
    public static int guiTicks(Minecraft mc) {
        //? if <26.2 {
        return mc.gui.getGuiTicks();
        //?} else
        /*return mc.gui.hud.getGuiTicks();*/
    }
}
