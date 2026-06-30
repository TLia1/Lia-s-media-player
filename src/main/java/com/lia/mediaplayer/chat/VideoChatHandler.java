package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.video.VideoThumbnailCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Rewrites incoming chat so video, stream and YouTube links become an aqua,
 * underlined {@code [video]} / {@code [youtube]} label. Clicking the label is handled
 * by {@link com.lia.mediaplayer.gui.MediaWindowOverlay}, which spawns or queues the
 * in-game player; this class only does the chat rewrite and the disconnect cleanup.
 *
 * <p>The component-walking is delegated to {@link ChatLinkRewriter}; this class only
 * supplies the video-specific rule (which links to claim and the aqua underlined
 * style).</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class VideoChatHandler {

    /**
     * Video/stream/YouTube links → aqua underlined {@code [video]}/{@code [youtube]} label.
     */
    private static final ChatLinkRewriter.LinkRewrite VIDEO_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
            return ctx != null && ctx.getMediaSources().isVideo(url);
        }

        @Override
        public Component label(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
            return ctx != null ? ctx.getMediaSources().labelFor(url) : Component.literal("[video]");
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("gui.liasmediaplayer.tooltip.video")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        }
    };

    private VideoChatHandler() {
    }

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), VIDEO_LINKS));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), VIDEO_LINKS));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
        if (ctx != null) {
            ctx.getVideoManager().disposeAll();
        }
        VideoThumbnailCache.clear();
        MediaTitleCache.clear();
    }
}
