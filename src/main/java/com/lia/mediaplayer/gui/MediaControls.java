package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

final class MediaControls {
    static final int VOL_BAR_W = 6;
    static final int VOL_BAR_H = 40;

    private MediaControls() {}

    static String timeText(long positionMicros, long durationMicros, int queuedSize) {
        String suffix = queuedSize > 0 ? "  +" + queuedSize : "";
        if (durationMicros <= 0) {
            return "LIVE" + suffix;
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
        g.fill(volBarX - 3, volBarY - 3, volBarX + VOL_BAR_W + 3, volBarY + VOL_BAR_H + 3, 0xE0101010);
        g.fill(volBarX, volBarY, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, trackColor);
        int fillH = Math.round(VOL_BAR_H * volume);
        g.fill(volBarX, volBarY + VOL_BAR_H - fillH, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, fillColor);
        int knobY = volBarY + VOL_BAR_H - Mth.clamp(fillH, 0, VOL_BAR_H);
        g.fill(volBarX - 2, knobY - 1, volBarX + VOL_BAR_W + 2, knobY + 1, knobColor);
    }
}
