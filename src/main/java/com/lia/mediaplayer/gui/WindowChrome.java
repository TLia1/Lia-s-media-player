package com.lia.mediaplayer.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Everything a media window draws that is not its picture: the title strip, the corner
 * buttons, the resize grip, the mark a click leaves behind — and the vocabulary the two
 * player windows share for their control bars, which is the tooltips and the colours of
 * a toggle.
 *
 * <p>It is all static and all handed its coordinates, because none of it is a decision:
 * {@code MediaWindow} works out where the chrome goes and whether it is shown at all,
 * and this draws what it is told. Keeping it apart is what stops the window class from
 * being the place both the layout and the pixels live, and it means the two subclasses
 * reach the shared look through a name that says what it is rather than by inheriting
 * two dozen {@code protected static} helpers.</p>
 *
 * @see WindowButtons for where the corner buttons sit
 */
final class WindowChrome {

    private WindowChrome() {
    }

    /**
     * The strip above the content: the media's name on the left, the corner buttons on
     * the right (drawn by {@link #cornerButtons}).
     *
     * @param textRight where the title has to stop — {@link WindowButtons#leftEdge()}
     */
    static void titleBar(GuiGraphics g, Font font, int boxX, int boxY, int boxW,
                         int titleBarH, int textRight, String title, double fade) {
        Panels.fillTop(g, boxX, boxY, boxX + boxW, boxY + titleBarH,
                Theme.withAlpha(Theme.TITLE_BAR_BG, fade));
        int textX = boxX + 4;
        int maxW = textRight - textX;
        if (maxW < 8) {
            return; // too narrow to say anything; the buttons win
        }
        // Vanilla's font renderer reads a near-zero alpha as "fully opaque", so the
        // first frame of the fade would show the title at full strength. See
        // NowPlayingBanner, which has the same floor for the same reason.
        if (fade * 255 < 8) {
            return;
        }
        g.drawString(font, Component.literal(Glyphs.fit(font, title, maxW)),
                textX, boxY + (titleBarH - font.lineHeight) / 2 + 1,
                Theme.withAlpha(Theme.TEXT_SUBTLE, fade));
    }

    /**
     * The heart, the copy button, the browser link, the optional hide button and the
     * close button, each hovered-coloured and each asking for its tooltip.
     *
     * @param inTitleBar whether the row sits in a title strip; if not, each button needs
     *                   a backdrop of its own — see {@link #buttonBackdrop}
     * @param favorite   whether the media is already kept, which decides between a
     *                   filled and a hollow heart
     * @param copyTip    what the copy button says right now — it changes with the
     *                   playback position and just after a copy, so the window supplies
     *                   it, and only when the cursor is actually on the button
     */
    static void cornerButtons(GuiGraphics g, int mouseX, int mouseY, WindowButtons buttons,
                              boolean inTitleBar, boolean favorite, Supplier<Component> copyTip) {
        // The heart: what turns "this played once" into something the library keeps.
        // It is a window button rather than a history-screen one because the moment you
        // know you want to keep a track is while it is playing.
        boolean overFav = buttons.overFavorite(mouseX, mouseY);
        buttonBackdrop(g, buttons.favX(), buttons.y(), inTitleBar);
        // Filled once it is kept, hollow while it is not — the same pair the history
        // screen draws, so the button means one thing in both places.
        if (favorite) {
            Glyphs.heart(g, buttons.favX(), buttons.y(), overFav ? Theme.ICON_HOVER : Theme.DANGER);
        } else {
            Glyphs.heartOutline(g, buttons.favX(), buttons.y(), overFav ? Theme.ICON_HOVER : Theme.ICON);
        }
        if (overFav) {
            Tooltips.request(Component.translatable(favorite
                    ? "gui.liasmediaplayer.control.unfavorite"
                    : "gui.liasmediaplayer.control.favorite"));
        }

        boolean overLink = buttons.overLink(mouseX, mouseY);
        buttonBackdrop(g, buttons.linkX(), buttons.y(), inTitleBar);
        Glyphs.externalLink(g, buttons.linkX(), buttons.y(), overLink ? Theme.ICON_HOVER : Theme.ICON);
        if (overLink) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.open_browser"));
        }

        boolean overCopy = buttons.overCopy(mouseX, mouseY);
        buttonBackdrop(g, buttons.copyX(), buttons.y(), inTitleBar);
        Glyphs.copy(g, buttons.copyX(), buttons.y(), overCopy ? Theme.ICON_HOVER : Theme.ICON);
        if (overCopy) {
            Tooltips.request(copyTip.get());
        }

        boolean overClose = buttons.overClose(mouseX, mouseY);
        buttonBackdrop(g, buttons.closeX(), buttons.y(), inTitleBar);
        Glyphs.close(g, buttons.closeX(), buttons.y(), overClose ? Theme.DANGER : Theme.ICON);
        if (overClose) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.close"));
        }

        if (buttons.hasHide()) {
            boolean overHide = buttons.overHide(mouseX, mouseY);
            buttonBackdrop(g, buttons.hideX(), buttons.y(), inTitleBar);
            Glyphs.minimize(g, buttons.hideX(), buttons.y(), overHide ? Theme.ICON_HOVER : Theme.ICON);
            if (overHide) {
                Tooltips.request(Component.translatable("gui.liasmediaplayer.control.hide"));
            }
        }
    }

    /**
     * The dark square behind a corner button — needed only when the button sits over
     * the picture. In a title bar the strip is already the backdrop, and painting a
     * second one there just puts three darker squares on it.
     */
    private static void buttonBackdrop(GuiGraphics g, int x, int y, boolean inTitleBar) {
        if (!inTitleBar) {
            g.fill(x, y, x + WindowButtons.SIZE, y + WindowButtons.SIZE, Theme.CORNER_BUTTON_BG);
        }
    }

    /**
     * A small diagonal grip in the bottom-right corner, highlighted when hovered or held.
     */
    static void grip(GuiGraphics g, int gripX, int gripY, boolean active) {
        // The cursor cannot be swapped for a resize arrow — that is a GLFW window-level
        // call with no vanilla seam behind it, and a stuck cursor outlives the window —
        // so the affordance is drawn instead: the grip lights up and gains a backdrop.
        int size = MediaWindow.GRIP;
        if (active) {
            g.fill(gripX, gripY, gripX + size, gripY + size, Theme.CORNER_BUTTON_BG);
        }
        int color = active ? Theme.ICON_HOVER : Theme.ICON;
        for (int i = 1; i <= 3; i++) {
            int o = i * 2;
            g.fill(gripX + size - o, gripY + size - 1, gripX + size, gripY + size, color);
            g.fill(gripX + size - 1, gripY + size - o, gripX + size, gripY + size, color);
        }
    }

    /**
     * The mark a click leaves behind: a small square that expands from where the cursor
     * was and fades out.
     *
     * <p>This is the window equivalent of a button's pressed state. A window is not a
     * screen widget, so its controls are hit-tested rectangles rather than widgets with
     * a held state to draw from — a real "pressed" look would mean every one of the
     * dozen control glyphs in the two player windows tracking the mouse button itself.
     * Marking the point that was clicked instead reports the press from one place, and
     * covers the controls that are not buttons at all (the seek bar, a queue row).</p>
     *
     * @param progress how far through the flash animation, 0..1; at or past 1 nothing
     *                 is drawn
     */
    static void clickFlash(GuiGraphics g, int x, int y, double progress) {
        if (progress >= 1.0) {
            return;
        }
        double eased = Anim.easeOut(progress);
        int half = (int) Math.round(3 + 6 * eased);
        Panels.fill(g, x - half, y - half, x + half, y + half,
                Theme.withAlpha(Theme.PRESS_FLASH, 1.0 - eased));
    }

    // ------------------------------------------------------------------
    // The control-bar vocabulary both players share
    //
    // Each tooltip is looked up fresh from the current state: one that named the button
    // rather than its effect ("loop") would say nothing the glyph does not already say.
    // ------------------------------------------------------------------

    static Component playTooltip(boolean playing) {
        return Component.translatable(playing
                ? "gui.liasmediaplayer.control.pause"
                : "gui.liasmediaplayer.control.play");
    }

    static Component loopTooltip(RepeatMode mode) {
        return Component.translatable(switch (mode) {
            case OFF -> "gui.liasmediaplayer.control.loop.off";
            case ALL -> "gui.liasmediaplayer.control.loop.all";
            case ONE -> "gui.liasmediaplayer.control.loop.one";
        });
    }

    static Component shuffleTooltip(boolean on) {
        return Component.translatable(on
                ? "gui.liasmediaplayer.control.shuffle.on"
                : "gui.liasmediaplayer.control.shuffle.off");
    }

    static Component volumeTooltip(boolean muted) {
        return Component.translatable(muted
                ? "gui.liasmediaplayer.control.unmute"
                : "gui.liasmediaplayer.control.mute");
    }

    static Component skipTooltip(boolean forward) {
        return Component.translatable(forward
                        ? "gui.liasmediaplayer.control.skip_forward"
                        : "gui.liasmediaplayer.control.skip_back",
                MediaControls.SKIP_MICROS / 1_000_000L);
    }

    /**
     * The two "jump {@value MediaControls#SKIP_MICROS} micros" buttons, drawn the same
     * way by both player windows: greyed out (and inert) when there is no duration to
     * jump within, which is what a live stream has.
     */
    static void skipButton(GuiGraphics g, int x, int y, boolean forward,
                           boolean seekable, int mouseX, int mouseY) {
        boolean over = MediaWindow.inRect(mouseX, mouseY, x, y, MediaWindow.BUTTON, MediaWindow.BUTTON);
        Glyphs.seekStep(g, x, y, forward,
                seekable ? (over ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (over && seekable) {
            Tooltips.request(skipTooltip(forward));
        }
    }

    /**
     * Colour for a toggle button (loop, shuffle) in each of its four states, so both
     * player windows draw their toggles the same way.
     */
    static int toggleColor(boolean active, boolean hovered) {
        if (active) {
            return hovered ? Theme.ICON_HOVER : Theme.ICON_ACTIVE;
        }
        return hovered ? Theme.ICON_HOVER : Theme.ICON_INACTIVE;
    }
}
