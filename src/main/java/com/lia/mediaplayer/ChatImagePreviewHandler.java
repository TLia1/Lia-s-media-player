package com.lia.mediaplayer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites chat messages so image links (e.g. Discord attachments relayed by
 * the chat bridge) are displayed as a colored [picture] entry instead of the
 * raw URL, and renders a preview of the image when the mouse hovers over it
 * in the chat screen.
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class ChatImagePreviewHandler {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Component PICTURE_LABEL = Component.literal("[picture]");
    private static final int PREVIEW_Z = 400; // above the chat, same layer as tooltips
    private static final int CURSOR_OFFSET = 8;
    private static final int BACKGROUND_COLOR = 0xF0100010;

    private ChatImagePreviewHandler() {
    }

    // ------------------------------------------------------------------
    // 1) Rewrite incoming chat messages: image url -> [picture]
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(rewriteImageLinks(event.getMessage()));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(rewriteImageLinks(event.getMessage()));
    }

    /**
     * Walks the component tree (with inherited styles) and replaces every image
     * URL with a [picture] component carrying an OPEN_URL click event, so the
     * link still works on click and can be found again under the mouse cursor.
     */
    private static Component rewriteImageLinks(Component message) {
        MutableComponent rebuilt = Component.empty();
        boolean[] changed = {false};

        FormattedText.StyledContentConsumer<Object> consumer = (style, text) -> {
            int last = 0;
            Matcher matcher = URL_PATTERN.matcher(text);
            while (matcher.find()) {
                String url = matcher.group();
                if (!isSupportedUrl(url)) {
                    continue;
                }

                if (matcher.start() > last) {
                    rebuilt.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
                }

                Component name = labelFor(url);

                Style pictureStyle = style
                        .withColor(ChatFormatting.GOLD)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
                rebuilt.append(name.copy().setStyle(pictureStyle));

                ImagePreviewCache.track(url);
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

    /** Any URL the preview can display: a direct image file or a Tenor share page. */
    static boolean isSupportedUrl(String url) {
        return isImageUrl(url) || TenorResolver.isTenorPageUrl(url);
    }

    /** Builds the clickable chat label, using the file name or Tenor slug. */
    private static Component labelFor(String url) {
        if (TenorResolver.isTenorPageUrl(url)) {
            return Component.literal("[gif]");
        }
        return PICTURE_LABEL;
    }

    static boolean isImageUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null) {
                return false;
            }
            String lowerPath = path.toLowerCase(Locale.ROOT);
            return lowerPath.endsWith(".png")
                    || lowerPath.endsWith(".jpg")
                    || lowerPath.endsWith(".jpeg")
                    || lowerPath.endsWith(".gif")
                    || lowerPath.endsWith(".bmp");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 2) Render the preview when hovering a [picture] entry in the chat
    // ------------------------------------------------------------------

    /**
     * Draws the floating image preview for whatever [picture] link is under the
     * cursor. Called by {@link MediaWindowOverlay} after the pinned windows so
     * the preview always sits on top of them.
     */
    static void renderHoverPreview(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                   int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();

        Style style = mc.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
        if (style == null) {
            return;
        }

        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.OPEN_URL) {
            return;
        }

        String url = clickEvent.getValue();
        if (!isSupportedUrl(url)) {
            return;
        }

        // If it is already pinned and showing, the window is the preview — don't
        // also draw the floating one on top of it.
        ImageWindow pinned = ImageWindowManager.get(url);
        if (pinned != null && pinned.isVisible()) {
            return;
        }

        // Starts the async download on first hover.
        ImagePreviewCache.Entry entry = ImagePreviewCache.getOrLoad(url);
        switch (entry.state) {
            case LOADED -> {
                if (entry.currentFrame() != null) {
                    renderImagePreview(guiGraphics, entry, mouseX, mouseY, screenWidth, screenHeight);
                }
            }
            case FAILED -> renderStatus(guiGraphics, mc,
                    Component.literal("Couldn't load image"), mouseX, mouseY);
            default -> renderStatus(guiGraphics, mc,
                    Component.literal("Loading image..."), mouseX, mouseY);
        }
    }

    private static void renderStatus(GuiGraphics guiGraphics, Minecraft mc, Component text,
                                     int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, PREVIEW_Z);
        guiGraphics.renderTooltip(mc.font, text, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    private static void renderImagePreview(GuiGraphics guiGraphics, ImagePreviewCache.Entry entry,
                                           int mouseX, int mouseY, int screenWidth, int screenHeight) {
        net.minecraft.resources.ResourceLocation frame = entry.currentFrame();
        if (frame == null) {
            return;
        }

        // Scale the image down (never up) so it fits in roughly half the screen.
        int maxWidth = Math.max(32, screenWidth / 2);
        int maxHeight = Math.max(32, screenHeight / 2);
        float scale = Math.min(1.0f,
                Math.min(maxWidth / (float) entry.width, maxHeight / (float) entry.height));
        int width = Math.max(1, Math.round(entry.width * scale));
        int height = Math.max(1, Math.round(entry.height * scale));

        // Place the preview above the cursor and keep it on screen.
        int x = Mth.clamp(mouseX + CURSOR_OFFSET, 2, screenWidth - width - 2);
        int y = Mth.clamp(mouseY - height - CURSOR_OFFSET, 2, screenHeight - height - 2);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, PREVIEW_Z);
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, BACKGROUND_COLOR);
        guiGraphics.blit(frame, x, y, width, height,
                0.0f, 0.0f, entry.width, entry.height, entry.width, entry.height);
        guiGraphics.pose().popPose();
    }

    // ------------------------------------------------------------------
    // 3) Cleanup
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ImageWindowManager.disposeAll();
        ImagePreviewCache.clear();
    }
}
