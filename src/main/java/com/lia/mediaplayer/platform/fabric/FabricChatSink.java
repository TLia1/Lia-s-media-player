package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.gui.ChatOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jetbrains.annotations.Nullable;

//? if <26.1 {
import net.minecraft.client.GuiMessageTag;
//?} else
/*import net.minecraft.client.multiplayer.chat.GuiMessageTag;*/

import java.time.Instant;

/**
 * Puts a rewritten <em>player</em> chat message into the chat overlay by hand.
 *
 * <p>Fabric's message API has {@code MODIFY_GAME} for system messages but deliberately no
 * {@code MODIFY_CHAT}: rewriting a signed message would break its signature chain. Its
 * own javadoc names the way round — cancel through {@code ALLOW_CHAT} and add the new
 * message yourself — which is what this does.</p>
 *
 * <p>The one thing the callback does not hand over is the {@code GuiMessageTag} that
 * vanilla's {@code ChatListener} would have computed: the "not secure" / "modified"
 * indicator drawn beside the line. It does not have to be approximated, though —
 * {@code ChatTrustLevel.evaluate} is public and takes exactly what the callback gives us,
 * so the tag is the same one vanilla would have attached. It is evaluated against the
 * <em>original</em> message, as vanilla does, not against the rewrite.</p>
 *
 * <p>26.1 renamed the method ({@code addMessage} → {@code addPlayerMessage}) and moved
 * {@code GuiMessageTag} into {@code client.multiplayer.chat}.</p>
 */
final class FabricChatSink {
    private FabricChatSink() {
    }

    /**
     * Adds {@code rewritten} to the chat overlay as if vanilla had.
     *
     * @param signed    the signed message, or {@code null} for a disguised chat message
     *                  (one the server sent without a signature)
     * @param original  the message as received, used to derive the trust indicator
     * @param timestamp when the message was received
     */
    static void addPlayerMessage(Component rewritten, @Nullable PlayerChatMessage signed,
                                 Component original, Instant timestamp) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = ChatOverlay.chat(mc);
        MessageSignature signature = signed == null ? null : signed.signature();

        //? if <26.1 {
        // A disguised message goes through the one-argument addMessage in vanilla, which
        // picks the single-player variant of the system tag; spell that out here.
        GuiMessageTag tag = signed == null
                ? (mc.isSingleplayer() ? GuiMessageTag.systemSinglePlayer() : GuiMessageTag.system())
                : ChatTrustLevel.evaluate(signed, original, timestamp).createTag(signed);
        chat.addMessage(rewritten, signature, tag);
        //?} else {
        /*GuiMessageTag tag = signed == null
                ? GuiMessageTag.system()
                : ChatTrustLevel.evaluate(signed, original, timestamp).createTag(signed);
        chat.addPlayerMessage(rewritten, signature, tag);
        *///?}
    }
}
