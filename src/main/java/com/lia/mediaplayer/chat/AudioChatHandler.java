package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.AudioPlayerManager;
import com.lia.mediaplayer.source.MediaSources;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Rewrites incoming chat so direct audio links become a green, underlined
 * {@code [audio]} label. Clicking the label is handled by
 * {@link com.lia.mediaplayer.gui.MediaWindowOverlay}, which queues / opens the in-game
 * audio bar; this class only does the chat rewrite and the disconnect cleanup.
 *
 * <p>The component-walking is delegated to {@link ChatLinkRewriter}; this class only
 * supplies the audio-specific rule (which links to claim and the green underlined
 * style). Audio and video sources are disjoint, so this composes on the same message as
 * {@link VideoChatHandler} and {@link ImageChatHandler} without fighting over a link.</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class AudioChatHandler {

    /** Direct audio links → green underlined {@code [audio]} label. */
    private static final ChatLinkRewriter.LinkRewrite AUDIO_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            return MediaSources.isAudio(url);
        }

        @Override
        public Component label(String url) {
            return MediaSources.labelFor(url);
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        }
    };

    private AudioChatHandler() {
    }

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), AUDIO_LINKS));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), AUDIO_LINKS));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AudioPlayerManager.disposeAll();
    }
}
