package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.image.ImagePreviewCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Rewrites incoming chat so image and Tenor links become a gold, clickable
 * {@code [picture]} / {@code [gif]} label, and registers each one with
 * {@link ImagePreviewCache} so its preview can be loaded lazily on hover.
 *
 * <p>The component-walking is delegated to {@link ChatLinkRewriter}; this class only
 * supplies the image-specific rule (which links to claim, the gold style, and the
 * preview-cache warm-up). Drawing the hover preview and the pinned windows is the
 * job of the {@code gui} package.</p>
 */
public final class ImageChatHandler {

    /**
     * Image/GIF links → gold {@code [picture]}/{@code [gif]} label; warms the preview cache.
     */
    private static final ChatLinkRewriter.LinkRewrite IMAGE_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
            return ctx != null && ctx.getMediaSources().isImage(url);
        }

        @Override
        public Component label(String url) {
            com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
            return ctx != null ? ctx.getMediaSources().labelFor(url) : Component.literal("[picture]");
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.GOLD)
                    .withClickEvent(ChatEvents.openUrl(url));
        }

        @Override
        public void onMatch(String url) {
            ImagePreviewCache.track(url);
        }
    };

    private ImageChatHandler() {
    }

    /**
     * Rewrites one incoming chat message. Loader-neutral: the bridge that owns the
     * loader's chat event calls this and puts the result back.
     */
    public static Component rewrite(Component message) {
        return ChatLinkRewriter.rewrite(message, IMAGE_LINKS);
    }

    /** Drops every pinned image and the preview cache when leaving a world. */
    public static void onDisconnect() {
        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx != null) {
            ctx.getImageManager().disposeAll();
        }
        ImagePreviewCache.clear();
    }
}
