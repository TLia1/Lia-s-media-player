package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.gui.MediaWindowOverlay;
import com.lia.mediaplayer.source.YouTubePlaylistSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Rewrites incoming chat so video, stream and YouTube links (including a YouTube
 * playlist page) become an aqua, underlined {@code [video]} / {@code [youtube]} label. Clicking the label is handled
 * by {@link MediaWindowOverlay}, which spawns or queues the
 * in-game player; this class only does the chat rewrite and the disconnect cleanup.
 *
 * <p>The component-walking is delegated to {@link ChatLinkRewriter}; this class only
 * supplies the video-specific rule (which links to claim and the aqua underlined
 * style).</p>
 */
public final class VideoChatHandler {

    /**
     * Video/stream/YouTube links → aqua underlined {@code [video]}/{@code [youtube]} label.
     */
    private static final ChatLinkRewriter.LinkRewrite VIDEO_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
            return ctx != null && ctx.getMediaSources().isVideo(url) && MediaFilters.allowsUrl(url);
        }

        @Override
        public Component label(String url) {
            MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
            return ctx != null ? ctx.getMediaSources().labelFor(url) : Component.translatable("chat.liasmediaplayer.label.video");
        }

        @Override
        public Style style(Style inherited, String url) {
            // A playlist page clicks through to the whole list rather than one video,
            // so it gets its own tooltip.
            String tooltip = YouTubePlaylistSource.isPlaylist(url)
                    ? "gui.liasmediaplayer.tooltip.youtube_playlist"
                    : "gui.liasmediaplayer.tooltip.video";
            return inherited
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withHoverEvent(ChatEvents.showText(Component.translatable(tooltip)))
                    .withClickEvent(ChatEvents.openUrl(url));
        }
    };

    private VideoChatHandler() {
    }

    /**
     * Rewrites one incoming chat message. Loader-neutral: the bridge that owns the
     * loader's chat event calls this and puts the result back.
     */
    public static Component rewrite(Component message) {
        return ChatLinkRewriter.rewrite(message, VIDEO_LINKS);
    }

    /** Drops every open video window and the thumbnail/title caches when leaving a world. */
    public static void onDisconnect() {
        MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
        if (ctx != null) {
            ctx.getVideoManager().disposeAll();
            ctx.getThumbnailCache().clear();
            ctx.getTitleCache().clear();
        }
    }
}
