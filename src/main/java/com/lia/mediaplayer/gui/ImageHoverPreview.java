package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.image.ImagePreviewCache;
import com.lia.mediaplayer.source.MediaSources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
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

    /** Draws the preview for whatever image label is under the cursor (if any). */
    static void render(GuiGraphics guiGraphics, int mouseX, int mouseY,
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
        if (!MediaSources.isImage(url)) {
            return;
        }

        // If it is already pinned and showing, the window is the preview — don't also
        // draw the floating one on top of it.
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
                    Component.translatable("gui.liasmediaplayer.image.load_failed"), mouseX, mouseY);
            default -> renderStatus(guiGraphics, mc,
                    Component.translatable("gui.liasmediaplayer.image.loading"), mouseX, mouseY);
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

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, PREVIEW_Z);
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, BACKGROUND_COLOR);
        guiGraphics.blit(frame, x, y, width, height,
                0.0f, 0.0f, entry.width, entry.height, entry.width, entry.height);
        guiGraphics.pose().popPose();
    }
}
