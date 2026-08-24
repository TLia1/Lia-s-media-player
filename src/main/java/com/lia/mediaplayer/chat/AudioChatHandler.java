package com.lia.mediaplayer.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

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
public final class AudioChatHandler {

    /**
     * Direct audio links → green underlined {@code [audio]} label.
     */
    private static final ChatLinkRewriter.LinkRewrite AUDIO_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
            return ctx != null && ctx.getMediaSources().isAudio(url) && MediaFilters.allowsUrl(url);
        }

        @Override
        public Component label(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
            return ctx != null ? ctx.getMediaSources().labelFor(url) : Component.literal("[audio]");
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.GREEN)
                    .withUnderlined(true)
                    .withHoverEvent(ChatEvents.showText(Component.translatable("gui.liasmediaplayer.tooltip.audio")))
                    .withClickEvent(ChatEvents.openUrl(url));
        }
    };

    private AudioChatHandler() {
    }

    /**
     * Rewrites one incoming chat message. Loader-neutral: the bridge that owns the
     * loader's chat event calls this and puts the result back.
     */
    public static Component rewrite(Component message) {
        return ChatLinkRewriter.rewrite(message, AUDIO_LINKS);
    }

    /** Drops every open audio bar when leaving a world. */
    public static void onDisconnect() {
        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx != null) {
            ctx.getAudioManager().disposeAll();
        }
    }
}
