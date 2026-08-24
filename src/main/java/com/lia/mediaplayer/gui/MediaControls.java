package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class MediaControls {
    static final int VOL_BAR_W = 6;
    static final int VOL_BAR_H = 40;
    /** Height of a seek bar at rest. */
    static final int SEEK_H = 4;
    /** Height of a seek bar under the cursor, or while being scrubbed. */
    static final int SEEK_H_ACTIVE = 6;
    /**
     * How far above and below the bar still counts as pointing at it. The bar is four
     * pixels tall; hitting it exactly would be a test of aim, so the reactive band is
     * the same one the click handlers already accept.
     */
    static final int SEEK_GRAB = 3;

    private MediaControls() {
    }

    /**
     * Whether the cursor is close enough to the seek bar for it to react.
     */
    static boolean overSeek(double mouseX, double mouseY, int seekX, int seekY, int seekW) {
        return mouseX >= seekX && mouseX <= seekX + seekW
                && mouseY >= seekY - SEEK_GRAB && mouseY <= seekY + SEEK_H + SEEK_GRAB;
    }

    /**
     * Draws a seek bar, growing and revealing its handle when it is being pointed at.
     *
     * <p>Both players draw the same bar, and used to draw it twice — once each, with
     * the handle permanently visible. Showing the handle only while the bar is live
     * keeps a paused window's bar as a plain progress read-out, and the two extra
     * pixels of height are what say "this one is draggable" before the drag starts.</p>
     *
     * @param active  the cursor is on the bar, or a scrub is in progress
     * @param seekable there is a duration to seek within (a live stream has none)
     */
    static void drawSeekBar(GuiGraphics g, int seekX, int seekY, int seekW,
                            double fraction, boolean seekable, boolean active) {
        int height = active ? SEEK_H_ACTIVE : SEEK_H;
        // Grow about the bar's centre line, so the row's vertical rhythm does not shift.
        int top = seekY - (height - SEEK_H) / 2;
        g.fill(seekX, top, seekX + seekW, top + height, Theme.TRACK);
        if (!seekable) {
            return;
        }
        int fill = Mth.clamp((int) Math.round(seekW * fraction), 0, seekW);
        g.fill(seekX, top, seekX + fill, top + height, Theme.FILL);
        if (active) {
            int knobX = seekX + fill;
            g.fill(knobX - 1, top - 2, knobX + 2, top + height + 2, Theme.KNOB);
        }
    }

    static String timeText(long positionMicros, long durationMicros, int queuedSize) {
        String suffix = queuedSize > 0 ? "  +" + queuedSize : "";
        if (durationMicros <= 0) {
            return Component.translatable("gui.liasmediaplayer.time.live").getString() + suffix;
        }
        return format(positionMicros) + " / " + format(durationMicros) + suffix;
    }

    private static String format(long micros) {
        long totalSeconds = Math.max(0, micros / 1_000_000L);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    static double fractionAt(double mouseX, int seekX, int seekW) {
        if (seekW <= 0) {
            return 0;
        }
        return Mth.clamp((mouseX - seekX) / seekW, 0.0, 1.0);
    }

    static double volumeFractionAt(double mouseY, int volBarY) {
        return Mth.clamp((volBarY + VOL_BAR_H - mouseY) / (double) VOL_BAR_H, 0.0, 1.0);
    }

    static void drawVolumePopup(GuiGraphics g, int volBarX, int volBarY, float volume, int trackColor, int fillColor, int knobColor) {
        Panels.fill(g, volBarX - 3, volBarY - 3, volBarX + VOL_BAR_W + 3, volBarY + VOL_BAR_H + 3, Theme.POPUP_BG);
        g.fill(volBarX, volBarY, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, trackColor);
        int fillH = Math.round(VOL_BAR_H * volume);
        g.fill(volBarX, volBarY + VOL_BAR_H - fillH, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, fillColor);
        int knobY = volBarY + VOL_BAR_H - Mth.clamp(fillH, 0, VOL_BAR_H);
        g.fill(volBarX - 2, knobY - 1, volBarX + VOL_BAR_W + 2, knobY + 1, knobColor);
    }
}
