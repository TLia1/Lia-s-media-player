package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.image.ImagePreviewCache;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

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
            MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
            return ctx != null && ctx.getMediaSources().isImage(url) && MediaFilters.allowsUrl(url);
        }

        @Override
        public MediaKind kind() {
            return MediaKind.IMAGE;
        }

        @Override
        public Component label(String url) {
            MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
            return ctx != null ? ctx.getMediaSources().labelFor(url) : Component.translatable("chat.liasmediaplayer.label.picture");
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.GOLD)
                    .withClickEvent(ChatEvents.openUrl(url));
        }

        @Override
        public void onMatch(String url) {
            MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
            if (ctx != null) {
                ctx.getImagePreviewCache().track(url);
            }
        }
    };

    private ImageChatHandler() {
    }

    /**
     * Rewrites one incoming chat message. Loader-neutral: the bridge that owns the
     * loader's chat event calls this and puts the result back.
     */
    public static Component rewrite(Component message) {
        return rewrite(message, null);
    }

    /**
     * The same, told who sent the message — which is what a registered
     * {@code MediaInterceptor} is asked about each link with.
     */
    public static Component rewrite(Component message, @Nullable String sender) {
        return ChatLinkRewriter.rewrite(message, IMAGE_LINKS, sender);
    }

    /** Drops every pinned image and the preview cache when leaving a world. */
    public static void onDisconnect() {
        MediaPlayerContext ctx = MediaPlayerContext.getOrNull();
        if (ctx != null) {
            ctx.getImageManager().disposeAll();
            ctx.getImagePreviewCache().clear();
        }
    }
}
