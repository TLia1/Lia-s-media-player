package com.lia.mediaplayer.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The strip that announces a track nothing on screen is showing.
 *
 * <p>A player window keeps playing while hidden — that is the point of the hide button
 * — so when its queue moves on to the next track there is nothing anywhere that says
 * what started. This fills that gap and only that gap: it is raised from
 * {@code playUrl} when the window doing the playing is not visible, and never when the
 * window is right there with the title in its bar.</p>
 *
 * <p>One banner at a time, deliberately: a playlist skipped through quickly should
 * leave the newest title on screen, not a queue of stale ones. It is drawn over the
 * chat screen and over the bare HUD alike, so the announcement does not depend on
 * whether the chat happens to be open.</p>
 */
final class NowPlayingBanner {

    /** How long the banner is on screen, fade included. */
    private static final int SHOW_MS = 2800;
    /** The share of that time spent fully opaque; the rest is the fade in and out. */
    private static final double HOLD = 0.82;

    private static final int HEIGHT = 16;
    private static final int GLYPH_W = 11;
    /** Below the top edge, clear of the vanilla "hidden players" chip on the left. */
    private static final int TOP = 6;
    /** Above the windows and their tooltips would be wrong; this sits with the chips. */
    private static final int BANNER_Z = 500;

    @Nullable
    private static Component text;
    private static long shownAt;

    private NowPlayingBanner() {
    }

    /**
     * Announces {@code title}, replacing whatever the banner was showing.
     */
    static void show(String title) {
        text = Component.translatable("gui.liasmediaplayer.now_playing", title);
        shownAt = Anim.now();
    }

    /**
     * Drops the banner immediately (e.g. on disconnect, with the windows it described).
     */
    static void clear() {
        text = null;
        shownAt = 0;
    }

    /**
     * Draws the banner if one is still running. Safe — and free — to call every frame.
     */
    static void render(GuiGraphics g, int screenWidth) {
        Component message = text;
        if (message == null) {
            return;
        }
        double t = Anim.progress(shownAt, SHOW_MS);
        if (t >= 1.0) {
            text = null;
            return;
        }
        double alpha = Anim.inOut(t, HOLD);
        // Vanilla's font renderer treats a colour whose alpha byte is (near) zero as
        // fully opaque, so the very start and end of the fade would flash at full
        // strength. Below that threshold there is nothing worth drawing anyway.
        if (alpha * 255 < 8) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int width = GLYPH_W + font.width(message) + 12;
        int x = (screenWidth - width) / 2;

        GuiLayer.push(g, BANNER_Z);
        Panels.fill(g, x, TOP, x + width, TOP + HEIGHT, Theme.withAlpha(Theme.BANNER_BG, alpha));
        Panels.border(g, x, TOP, x + width, TOP + HEIGHT, Theme.withAlpha(Theme.BORDER_SUBTLE, alpha));
        Glyphs.note(g, x + 4, TOP + (HEIGHT - GLYPH_W) / 2, Theme.withAlpha(Theme.ICON_ACTIVE, alpha));
        g.drawString(font, message, x + 4 + GLYPH_W, TOP + (HEIGHT - font.lineHeight) / 2 + 1,
                Theme.withAlpha(Theme.TEXT, alpha));
        GuiLayer.popAndFlush(g);
    }
}
