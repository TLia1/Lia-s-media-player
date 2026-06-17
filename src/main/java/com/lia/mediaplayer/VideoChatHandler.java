package com.lia.mediaplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites video links relayed by the Discord chat bridge into a compact,
 * hoverable label and drives the in-game {@link VideoWindow}s:
 *
 * <ul>
 *   <li>Clicking the label spawns/shows the player and starts playback.</li>
 *   <li>The control bar (play/pause, volume, seek), the move/resize gestures and
 *       the corner buttons (open-link, hide, close) are interactive while the
 *       chat screen is open.</li>
 *   <li>A player stays on screen until closed with the × button; a hidden player
 *       keeps playing and re-clicking the link shows it again.</li>
 *   <li>While no screen is open, visible players are drawn on the HUD (picture
 *       only) so a video keeps showing during normal gameplay.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class VideoChatHandler {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private VideoChatHandler() {
    }

    // ------------------------------------------------------------------
    // 1) Rewrite incoming chat: video url -> hoverable label
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(rewriteVideoLinks(event.getMessage()));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(rewriteVideoLinks(event.getMessage()));
    }

    private static Component rewriteVideoLinks(Component message) {
        MutableComponent rebuilt = Component.empty();
        boolean[] changed = {false};

        FormattedText.StyledContentConsumer<Object> consumer = (style, text) -> {
            int last = 0;
            Matcher matcher = URL_PATTERN.matcher(text);
            while (matcher.find()) {
                String url = matcher.group();
                if (!VideoSupport.isVideoUrl(url)) {
                    continue;
                }
                if (matcher.start() > last) {
                    rebuilt.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
                }
                Style videoStyle = style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                rebuilt.append(VideoSupport.labelFor(url).copy().setStyle(videoStyle));
                last = matcher.end();
                changed[0] = true;
            }
            if (last < text.length()) {
                rebuilt.append(Component.literal(text.substring(last)).setStyle(style));
            }
            return Optional.empty();
        };
        message.visit(consumer, Style.EMPTY);

        return changed[0] ? rebuilt : message;
    }

    // ------------------------------------------------------------------
    // 2) Cleanup
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VideoPlayerManager.disposeAll();
        VideoThumbnailCache.clear();
        VideoTitleCache.clear();
    }
}
