package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.video.VideoThumbnailCache;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * One small picture of a media URL, drawn the same size and the same way everywhere the
 * mod lists tracks — the player's queue panel and the playlist screen.
 *
 * <p>Both show a list of links, and a list of links is far quicker to read with the
 * pictures in it; the fitting, the placeholder and the "still loading" mark are the same
 * problem in both, so they are solved here rather than twice.
 * {@link VideoThumbnailCache} does the fetching and holds the texture — this is only how
 * one is put on screen.</p>
 */
final class Thumbnail {

    /** The box a thumbnail occupies. 16:9, small enough for a 14 px row of text beside it. */
    static final int W = 48;
    static final int H = 27;

    private Thumbnail() {
    }

    /**
     * Draws the thumbnail for {@code url} in the {@link #W}×{@link #H} box at
     * {@code (x, y)}: the picture fitted inside it, or a mark saying it is on its way
     * (or did not come).
     */
    static void draw(GuiGraphics g, Font font, String url, int x, int y) {
        g.fill(x, y, x + W, y + H, Theme.PLACEHOLDER);
        VideoThumbnailCache.Thumb thumb = MediaPlayerContext.get().getThumbnailCache().getOrLoad(url);
        if (thumb.isLoaded()) {
            // Fit the (already-small) thumbnail inside the box, preserving aspect.
            int tw = Math.max(1, thumb.width);
            int th = Math.max(1, thumb.height);
            double scale = Math.min(W / (double) tw, H / (double) th);
            int w = Math.max(1, (int) Math.round(tw * scale));
            int h = Math.max(1, (int) Math.round(th * scale));
            Blit.textured(g, thumb.texture, x + (W - w) / 2, y + (H - h) / 2, w, h, tw, th);
            return;
        }
        String mark = thumb.state == VideoThumbnailCache.State.FAILED ? "?" : "...";
        g.drawString(font, Component.literal(mark),
                x + (W - font.width(mark)) / 2, y + (H - font.lineHeight) / 2, Theme.TEXT_DIM);
    }
}
