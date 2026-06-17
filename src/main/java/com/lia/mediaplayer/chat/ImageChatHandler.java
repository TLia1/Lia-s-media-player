package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.ImageWindowManager;
import com.lia.mediaplayer.image.ImagePreviewCache;
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
 * Rewrites incoming chat so image and Tenor links become a gold, clickable
 * {@code [picture]} / {@code [gif]} label, and registers each one with
 * {@link ImagePreviewCache} so its preview can be loaded lazily on hover.
 *
 * <p>The component-walking is delegated to {@link ChatLinkRewriter}; this class only
 * supplies the image-specific rule (which links to claim, the gold style, and the
 * preview-cache warm-up). Drawing the hover preview and the pinned windows is the
 * job of the {@code gui} package.</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class ImageChatHandler {

    /** Image/GIF links → gold {@code [picture]}/{@code [gif]} label; warms the preview cache. */
    private static final ChatLinkRewriter.LinkRewrite IMAGE_LINKS = new ChatLinkRewriter.LinkRewrite() {
        @Override
        public boolean matches(String url) {
            return MediaSources.isImage(url);
        }

        @Override
        public Component label(String url) {
            return MediaSources.labelFor(url);
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited
                    .withColor(ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        }

        @Override
        public void onMatch(String url) {
            ImagePreviewCache.track(url);
        }
    };

    private ImageChatHandler() {
    }

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), IMAGE_LINKS));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(ChatLinkRewriter.rewrite(event.getMessage(), IMAGE_LINKS));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ImageWindowManager.disposeAll();
        ImagePreviewCache.clear();
    }
}
