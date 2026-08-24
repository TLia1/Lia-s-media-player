package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.media.PlaybackError;
import com.lia.mediaplayer.tools.MediaBinaries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a player window shows once playback has failed: the reason in words, what to do
 * about it, and the one or two buttons that do it.
 *
 * <p>It used to be ffmpeg's or yt-dlp's raw output and nothing else — accurate, and of no
 * use to anyone who did not already know what {@code Unable to extract player response}
 * meant. {@link PlaybackError} turns that line into a cause; this draws the cause, keeps
 * the raw text underneath (dimmed) because a bug report still needs it, and offers
 * <em>retry</em> — plus <em>update the tools</em> for the causes a newer yt-dlp fixes,
 * which is most of the ones YouTube produces.</p>
 *
 * <p>A window is not a screen, so there are no {@code Button} widgets here: the chips are
 * drawn by hand and hit-tested from the window's {@code onControlClick}, the same way
 * every other control in a media window works.</p>
 */
final class ErrorPanel {

    /** What a click on the panel asked for. */
    enum Action {NONE, RETRY, UPDATE_TOOLS}

    private static final int CHIP_H = 14;
    private static final int CHIP_PAD = 6;
    private static final int CHIP_GAP = 6;
    /** Enough to identify the failure; the log has the rest. */
    private static final int MAX_DETAIL_LINES = 3;

    private int retryX;
    private int retryY;
    private int retryW;
    private int updateX;
    private int updateW;
    private boolean showUpdate;
    /** False until a frame has been drawn, so a click cannot hit a stale rectangle. */
    private boolean laidOut;

    /**
     * Draws the whole failure notice inside the given rect.
     *
     * @param raw the player's error message, as the tool produced it
     */
    void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                @Nullable String raw, int mouseX, int mouseY) {
        PlaybackError.Cause cause = PlaybackError.classify(raw);
        boolean updating = MediaBinaries.isUpdating();
        showUpdate = cause.suggestsToolUpdate();

        int textWidth = Math.max(40, w - 20);
        Component title = Component.translatable(cause.messageKey());
        List<FormattedCharSequence> hint = font.split(Component.translatable(cause.hintKey()), textWidth);

        // The block is centred as a whole: title, hint, buttons. The raw detail hangs
        // below it and is allowed to run out of the window rather than push the buttons
        // off-centre — it is the least important line here.
        int blockH = font.lineHeight + 3 + hint.size() * font.lineHeight + 5 + CHIP_H;
        int top = y + Math.max(4, (h - blockH) / 2 - font.lineHeight);

        int ty = top;
        g.drawString(font, title, x + (w - font.width(title)) / 2, ty, Theme.DANGER);
        ty += font.lineHeight + 3;
        for (FormattedCharSequence line : hint) {
            g.drawString(font, line, x + (w - textWidth) / 2, ty, Theme.TEXT_SUBTLE);
            ty += font.lineHeight;
        }
        ty += 5;

        Component retryLabel = Component.translatable("gui.liasmediaplayer.error.retry");
        Component updateLabel = Component.translatable(updating
                ? "gui.liasmediaplayer.error.updating"
                : "gui.liasmediaplayer.error.update_tools");
        retryW = font.width(retryLabel) + CHIP_PAD * 2 + 10;
        updateW = showUpdate ? font.width(updateLabel) + CHIP_PAD * 2 : 0;
        int rowW = retryW + (showUpdate ? CHIP_GAP + updateW : 0);
        retryX = x + (w - rowW) / 2;
        retryY = ty;
        updateX = retryX + retryW + CHIP_GAP;
        laidOut = true;

        drawChip(g, font, retryX, retryY, retryW, retryLabel, true,
                inChip(mouseX, mouseY, retryX, retryW), true);
        if (showUpdate) {
            drawChip(g, font, updateX, retryY, updateW, updateLabel, false,
                    inChip(mouseX, mouseY, updateX, updateW), !updating);
        }

        // The tool's own words, kept but demoted.
        if (raw != null && !raw.isBlank()) {
            int detailY = retryY + CHIP_H + 6;
            List<FormattedCharSequence> detail = font.split(Component.literal(raw.strip()), textWidth);
            for (int i = 0; i < Math.min(detail.size(), MAX_DETAIL_LINES); i++) {
                if (detailY > y + h - font.lineHeight) {
                    break;
                }
                g.drawString(font, detail.get(i), x + (w - textWidth) / 2, detailY, Theme.TEXT_DIM);
                detailY += font.lineHeight;
            }
        }
    }

    /**
     * Draws one chip: a rounded panel, an optional leading glyph, and the label.
     */
    private void drawChip(GuiGraphics g, Font font, int x, int y, int w, Component label,
                          boolean withGlyph, boolean hovered, boolean enabled) {
        Panels.fill(g, x, y, x + w, y + CHIP_H, hovered && enabled ? Theme.CHIP_HOVER_BG : Theme.CHIP_BG);
        Panels.border(g, x, y, x + w, y + CHIP_H, Theme.BORDER_SUBTLE);
        int textX = x + CHIP_PAD;
        if (withGlyph) {
            Glyphs.refresh(g, x + 2, y + (CHIP_H - MediaWindow.BUTTON) / 2,
                    enabled ? (hovered ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
            textX = x + 2 + MediaWindow.BUTTON + 1;
        }
        int color = enabled ? (hovered ? Theme.ICON_HOVER : Theme.TEXT) : Theme.TEXT_DIM;
        g.drawString(font, label, textX, y + (CHIP_H - font.lineHeight) / 2 + 1, color);
    }

    private boolean inChip(double mouseX, double mouseY, int x, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= retryY && mouseY < retryY + CHIP_H;
    }

    /**
     * Routes a click on the panel. Returns {@link Action#NONE} when it hit neither chip,
     * so the window can go on to its own handling (moving the window, say).
     */
    Action click(double mouseX, double mouseY) {
        if (!laidOut) {
            return Action.NONE;
        }
        if (inChip(mouseX, mouseY, retryX, retryW)) {
            return Action.RETRY;
        }
        if (showUpdate && !MediaBinaries.isUpdating() && inChip(mouseX, mouseY, updateX, updateW)) {
            return Action.UPDATE_TOOLS;
        }
        return Action.NONE;
    }
}
