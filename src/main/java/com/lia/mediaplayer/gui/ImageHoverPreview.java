package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.chat.ChatEvents;
import com.lia.mediaplayer.image.ImagePreviewCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.6 {
/*import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws the floating image/GIF preview shown when the mouse hovers an image label
 * in the chat. The download is started lazily on first hover (through
 * {@link ImagePreviewCache}); while it loads, a small tooltip stands in.
 *
 * <p>This is purely a render concern, so it lives with the other on-screen drawing
 * in the {@code gui} package and is invoked by {@link MediaWindowOverlay} after the
 * pinned windows, so the preview always sits on top of them. The chat handler that
 * rewrites links no longer needs to know how previews are drawn.</p>
 */
final class ImageHoverPreview {
    private static final int PREVIEW_Z = 400; // above the chat, same layer as tooltips
    private static final int CURSOR_OFFSET = 8;
    private static final int BACKGROUND_COLOR = 0xF0100010;

    private ImageHoverPreview() {
    }

    /**
     * Draws the preview for whatever image label is under the cursor (if any).
     */
    static void render(GuiGraphics guiGraphics, int mouseX, int mouseY,
                       int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();

        Style style = ChatHitTest.hoveredStyle(mouseX, mouseY);
        if (style == null) {
            return;
        }

        String url = ChatEvents.clickedUrl(style);
        if (url == null) {
            return;
        }

        com.lia.mediaplayer.MediaPlayerContext ctx = (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
        if (ctx == null) return;

        if (!ctx.getMediaSources().isImage(url)) {
            return;
        }

        // If it is already pinned and showing, the window is the preview — don't also
        // draw the floating one on top of it.
        ImageWindow pinned = ctx.getImageManager().get(url);
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
                    Component.translatable("gui.liasmediaplayer.image.load_failed"), mouseX, mouseY);
            default -> renderStatus(guiGraphics, mc,
                    Component.translatable("gui.liasmediaplayer.image.loading"), mouseX, mouseY);
        }
    }

    private static void renderStatus(GuiGraphics guiGraphics, Minecraft mc, Component text,
                                     int mouseX, int mouseY) {
        GuiLayer.push(guiGraphics, PREVIEW_Z);
        //? if <1.21.6 {
        guiGraphics.renderTooltip(mc.font, text, mouseX, mouseY);
        //?} elif <26.1 {
        /*// 1.21.6 renamed the convenience overload to setTooltipForNextFrame, which
        // *defers* the tooltip until Screen.renderWithTooltip draws it. That is no
        // use here: this runs from ScreenEvent.Render.Post, which fires after the
        // deferred tooltip has already been rendered, so the tooltip would appear a
        // frame late and linger a frame after the cursor left the link. Building the
        // component list by hand and calling renderTooltip keeps it immediate, which
        // is what every version before 1.21.6 did.
        guiGraphics.renderTooltip(mc.font,
                java.util.List.of(ClientTooltipComponent.create(text.getVisualOrderText())),
                mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        *///?} else {
        /*// 26.1 dropped the immediate renderTooltip overloads entirely, but kept the
        // same escape hatch under a new name: setTooltipForNextFrame only stores a
        // closure that Screen.extractRenderStateWithTooltipAndSubtitles later runs
        // through extractDeferredElements, and that closure calls this public
        // `tooltip` method. ScreenEvent.Render.Post fires after that has already
        // happened (ClientHooks.drawScreenInternal), so the deferred path would be
        // a frame late here exactly as it was on 1.21.6 — calling `tooltip`
        // directly keeps it immediate.
        guiGraphics.tooltip(mc.font,
                java.util.List.of(ClientTooltipComponent.create(text.getVisualOrderText())),
                mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        *///?}
        GuiLayer.pop(guiGraphics);
    }

    private static void renderImagePreview(GuiGraphics guiGraphics, ImagePreviewCache.Entry entry,
                                           int mouseX, int mouseY, int screenWidth, int screenHeight) {
        ResourceLocation frame = entry.currentFrame();
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

        GuiLayer.push(guiGraphics, PREVIEW_Z);
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, BACKGROUND_COLOR);
        Blit.textured(guiGraphics, frame, x, y, width, height, entry.width, entry.height);
        GuiLayer.pop(guiGraphics);
    }
}
