package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.image.ImagePreviewCache;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A pinned image / GIF preview: the same picture the chat hover shows, but kept
 * on screen as a window you can drag around and resize. The pixels live in
 * {@link ImagePreviewCache} (shared with the hover preview), so this window only
 * tracks placement and draws whichever frame is current.
 */
final class ImageWindow extends MediaWindow {
    private final String url;

    ImageWindow(String url) {
        this.url = url;
    }

    String url() {
        return url;
    }

    private ImagePreviewCache.Entry entry() {
        return ImagePreviewCache.getOrLoad(url);
    }

    @Override
    protected String mediaUrl() {
        return url;
    }

    @Override
    protected void close() {
        ((com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance()).getImageManager().close(this);
    }

    @Override
    protected int anchorGroup() {
        return 0;
    }

    @Override
    protected int sourceWidth() {
        int w = entry().width;
        return w > 0 ? w : 320;
    }

    @Override
    protected int sourceHeight() {
        int h = entry().height;
        return h > 0 ? h : 180;
    }

    @Override
    protected double computeAutoScale(int srcW, int srcH, int screenWidth, int screenHeight) {
        int maxW = Math.max(64, screenWidth / 2);
        int maxH = Math.max(64, screenHeight / 2);
        return Math.min(1.0, Math.min(maxW / (double) srcW, maxH / (double) srcH));
    }

    @Override
    protected void computeAnchor(int screenWidth, int screenHeight, int slot) {
        // Cascade from a little above-left of centre so stacked images don't
        // land exactly on top of each other.
        int x = (screenWidth - boxW) / 2 + slot * 24;
        int y = screenHeight / 4 + slot * 24;
        boxX = clamp(x, 2, Math.max(2, screenWidth - boxW - 2));
        boxY = clamp(y, 2, Math.max(2, screenHeight - boxH - 2));
    }

    @Override
    protected void drawContent(GuiGraphics g, Font font) {
        ImagePreviewCache.Entry e = entry();
        ResourceLocation frame = e.state == ImagePreviewCache.State.LOADED ? e.currentFrame() : null;
        if (frame != null) {
            g.blit(frame, contentX, contentY, contentW, contentH,
                    0.0f, 0.0f, e.width, e.height, e.width, e.height);
            return;
        }
        g.fill(contentX, contentY, contentX + contentW, contentY + contentH, PLACEHOLDER);
        Component status = switch (e.state) {
            case FAILED -> Component.translatable("gui.liasmediaplayer.image.load_failed");
            default -> Component.translatable("gui.liasmediaplayer.image.loading");
        };
        int tx = contentX + (contentW - font.width(status)) / 2;
        int ty = contentY + (contentH - font.lineHeight) / 2;
        g.drawString(font, status, tx, ty, TEXT_COLOR);
    }

    /** No control bar, so a plain wheel is free to zoom. */
    @Override
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        zoom(scrollY);
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
