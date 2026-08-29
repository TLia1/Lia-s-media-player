package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.tools.MediaBinaries;
import com.lia.mediaplayer.video.VideoPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;


/**
 * The on-screen window for a single {@link VideoPlayer}: the video image plus a
 * control bar (play/pause, an optional "next" button, the loop and shuffle
 * toggles, a speaker toggle and a pop-up volume slider, and a seek bar with
 * elapsed/total time) and two corner buttons (hide, close).
 *
 * <p>Movement and resizing are inherited from {@link MediaWindow}. Visibility
 * and playback are independent: a hidden window keeps its player decoding and
 * playing audio.</p>
 *
 * <p>The <em>queue</em> — additional videos appended rather than given a window each,
 * and the swap of the {@link VideoPlayer} in place when the current one ends — comes
 * from {@link QueuedMediaWindow}, shared with the audio bar. What is left here is the
 * picture and the control bar under it.</p>
 */
final class VideoWindow extends QueuedMediaWindow<VideoPlayer> {
    private static final int CONTROL_BAR_HEIGHT = 18;
    /**
     * Smallest seek bar we keep when computing the minimum window width.
     */
    private static final int MIN_SEEK_W = 20;
    /**
     * A player box this wide (or narrower) gets the compact {@link QueuePanel.Mode#MINI}
     * queue panel: beside a small player, a full-width one would be wider than the
     * video it belongs to.
     */
    private static final int MINI_PANEL_MAX_BOX_W = 200;

    // Video-only parts of the control bar; the rest of the hit regions are shared
    // (see QueuedMediaWindow).
    private boolean showNext;
    private boolean showShuffle;
    private boolean showVolume;

    /** Drawn over the content area once playback has failed; see {@link ErrorPanel}. */
    private final ErrorPanel errorPanel = new ErrorPanel();

    VideoWindow(VideoPlayer player) {
        super(player);
    }

    // ------------------------------------------------------------------
    // Queue (the model, the panel and the transport live in QueuedMediaWindow)
    // ------------------------------------------------------------------

    @Override
    protected VideoPlayer createPlayer(String url) {
        return new VideoPlayer(url);
    }

    @Override
    protected MediaKind playbackKind() {
        return MediaKind.VIDEO;
    }

    /**
     * Tells the player to stop producing a picture nobody is going to look at.
     *
     * <p>Hiding a player is how someone uses the mod as a music player, and until this
     * the sound was the only part of a hidden window that was not wasted: the video was
     * still decoded, still scaled, and still pushed down a pipe for
     * {@code VideoPlayer.discardDueFrames} to throw away. The player relaunches ffmpeg
     * without a video stream instead — and relaunches it with one when the window comes
     * back, which shows the usual "seeking" notice for the second that takes.</p>
     */
    @Override
    void setVisible(boolean visible) {
        super.setVisible(visible);
        player.setPictureWanted(visible);
    }

    /**
     * A hidden window that advances to the next track keeps its next player picture-free
     * too, rather than quietly going back to decoding one.
     */
    @Override
    protected void onPlayerSwapped(VideoPlayer freshPlayer) {
        freshPlayer.setPictureWanted(isVisible());
    }

    /**
     * The panel shows a thumbnail beside each row, so the picture is warmed as well as
     * the name.
     */
    @Override
    protected void warmCaches(String url) {
        MediaPlayerContext.get().getThumbnailCache().getOrLoad(url);
        MediaPlayerContext.get().getTitleCache().getOrLoad(url);
    }

    // ------------------------------------------------------------------
    // MediaWindow contract
    // ------------------------------------------------------------------

    @Override
    protected int sourceWidth() {
        return player.videoWidth() > 0 ? player.videoWidth() : 320;
    }

    @Override
    protected int sourceHeight() {
        return player.videoHeight() > 0 ? player.videoHeight() : 180;
    }

    /**
     * False until the first frame has been decoded — up to then {@link #sourceWidth()}
     * is the 320x180 stand-in the window is drawn at while it loads.
     */
    @Override
    protected boolean sourceSizeKnown() {
        return player.videoWidth() > 0 && player.videoHeight() > 0;
    }

    @Override
    protected void close() {
        MediaPlayerContext.get().getVideoManager().close(this);
    }

    @Override
    protected int anchorGroup() {
        return 1;
    }

    @Override
    protected String stateKey() {
        return WindowStateStore.VIDEO;
    }

    /**
     * The queue panel is docked <em>beside</em> the player and caps its width to leave
     * room ({@link #maxContentWidth}), so leaving it open would stop theatre mode ever
     * filling the screen. It comes back when the window does.
     */
    @Override
    protected void onEnterTheater() {
        panel.setOpen(false);
    }

    @Override
    protected double computeAutoScale(int srcW, int srcH, int screenWidth, int screenHeight) {
        int maxW = Math.max(160, screenWidth / 3);
        int maxH = Math.max(90, screenHeight / 3);
        return Math.min(1.0, Math.min(maxW / (double) srcW, maxH / (double) srcH));
    }

    @Override
    protected void computeAnchor(int screenWidth, int screenHeight, int slot) {
        // Bottom-right, stacked leftwards, so it never covers the left-aligned
        // chat text / link you are hovering. When the queue panel is open we leave
        // room for it on the right so the player slides left instead of being
        // covered by the panel.
        int rightReserve = panel.isOpen() ? panelMode(boxW).width + QueuePanel.GAP : 0;
        int x = screenWidth - boxW - PADDING - rightReserve - slot * (boxW + 6);
        boxX = Mth.clamp(x, 2, Math.max(2, screenWidth - boxW - 2));
        // Sit above the chat input line at the bottom of the screen.
        int bottom = screenHeight - 36;
        boxY = Mth.clamp(bottom - boxH, 2, Math.max(2, screenHeight - boxH - 2));
    }

    @Override
    protected int controlBarHeight() {
        return CONTROL_BAR_HEIGHT;
    }

    /**
     * The control bar is laid out left-to-right at a fixed pixel size (buttons, then
     * a seek bar, then the time read-out), so it has a hard minimum width. Stop the
     * window shrinking below it — otherwise the seek bar and time spill past the right
     * edge of the box. The figure mirrors {@link #layoutControls}: the visible buttons,
     * a minimal seek bar, the time text, and the room reserved for the resize grip.
     */
    @Override
    protected int minContentWidth() {
        Font font = Minecraft.getInstance().font;
        int buttons = 4;                       // play/pause, the two skips and loop are always shown
        if (queue.hasNext()) {
            buttons += 1;                      // next
        }
        if (!queue.isEmpty()) {
            buttons += 2;                      // queue panel + shuffle
        }
        if (player.hasAudio()) {
            buttons += 1;                      // speaker
        }
        int buttonsW = buttons * (BUTTON + 4);
        int timeW = font.width(MediaControls.timeText(player.positionMicros(), player.durationMicros(), queue.size()));
        // buttons + minimal seek + gap + time + grip margin (matches layoutControls).
        int needed = buttonsW + MIN_SEEK_W + 6 + timeW + GRIP + 2;
        return Math.max(super.minContentWidth(), needed);
    }

    /**
     * While the queue panel is open it is docked to the right of the player, so cap
     * the player's width to leave room for the panel (plus the gap). This stops the
     * player from growing wide enough to sit underneath the panel.
     */
    @Override
    protected int maxContentWidth(int screenWidth) {
        int base = screenWidth - PADDING * 2 - 2;
        if (!panel.isOpen()) {
            return base;
        }
        // The panel beside the player is full-size for a large player and compact for
        // a small one, so the room to reserve depends on which the player will use.
        // If the screen is wide enough to fit the full panel beside a player larger
        // than the mini threshold, allow that; otherwise keep the player within the
        // mini range so only the compact panel needs reserving (giving it more room).
        int largeBoxCap = base + PADDING * 2 - QueuePanel.reserveFor(QueuePanel.Mode.FULL);
        int boxCap;
        if (largeBoxCap > MINI_PANEL_MAX_BOX_W) {
            boxCap = largeBoxCap;
        } else {
            int miniBoxCap = base + PADDING * 2 - QueuePanel.reserveFor(QueuePanel.Mode.MINI);
            boxCap = Math.min(MINI_PANEL_MAX_BOX_W, miniBoxCap);
        }
        return Math.max(minContentWidth(), boxCap - PADDING * 2);
    }

    /**
     * Keeps the player far enough from the right edge that the docked panel fits to
     * its right. Without this, dragging the player into the right edge (or centering
     * it on a narrow screen) would leave no room on either side and the panel would
     * be drawn over the player. Reserves only the compact panel's width for a mini
     * player. The width cap above guarantees this clamp range is non-empty, so the
     * panel always fits on the right and never overlaps.
     */
    @Override
    protected void constrainPosition(int screenWidth, int screenHeight) {
        if (!panel.isOpen()) {
            return;
        }
        int reserve = QueuePanel.reserveFor(panelMode(boxW));
        int maxX = screenWidth - boxW - reserve;
        if (maxX >= 2) {
            boxX = Mth.clamp(boxX, 2, maxX);
        }
    }

    /**
     * The panel layout that pairs with a player of the given box width.
     */
    private static QueuePanel.Mode panelMode(int playerBoxW) {
        return playerBoxW <= MINI_PANEL_MAX_BOX_W ? QueuePanel.Mode.MINI : QueuePanel.Mode.FULL;
    }

    @Override
    protected boolean hasHideButton() {
        return true;
    }

    @Override
    protected void layoutControls(Font font) {
        int barTop = contentY + contentH;
        playBtnX = contentX;
        playBtnY = barTop + (CONTROL_BAR_HEIGHT - BUTTON) / 2;

        int cursor = playBtnX + BUTTON + 4;

        // The two fixed-step skips, next to play: they act inside the current video, so
        // they belong on its side of the "next track" control rather than past it.
        backBtnX = cursor;
        backBtnY = playBtnY;
        cursor = backBtnX + BUTTON + 4;
        fwdBtnX = cursor;
        fwdBtnY = playBtnY;
        cursor = fwdBtnX + BUTTON + 4;

        // "Next" and "queue" buttons: only while something is queued (or a loop mode
        // will bring the round back around).
        showNext = queue.hasNext();
        if (showNext) {
            nextBtnX = cursor;
            nextBtnY = playBtnY;
            cursor = nextBtnX + BUTTON + 4;
        }
        showQueueBtn = !queue.isEmpty();
        if (showQueueBtn) {
            queueBtnX = cursor;
            queueBtnY = playBtnY;
            cursor = queueBtnX + BUTTON + 4;
        } else {
            panel.closeIfEmpty(); // nothing left to show
        }

        // Loop applies to a lone video too; shuffle only means something with a queue.
        loopBtnX = cursor;
        loopBtnY = playBtnY;
        cursor = loopBtnX + BUTTON + 4;
        showShuffle = !queue.isEmpty();
        if (showShuffle) {
            shuffleBtnX = cursor;
            shuffleBtnY = playBtnY;
            cursor = shuffleBtnX + BUTTON + 4;
        }

        showVolume = player.hasAudio();
        if (showVolume) {
            volBtnX = cursor;
            volBtnY = playBtnY;
            cursor = volBtnX + BUTTON + 4;
            // The slider pops up vertically above the speaker button.
            volBarX = volBtnX + (BUTTON - MediaControls.VOL_BAR_W) / 2;
            volBarY = volBtnY - 4 - MediaControls.VOL_BAR_H;
        }

        seekX = cursor;
        seekH = MediaControls.SEEK_H;
        seekY = barTop + (CONTROL_BAR_HEIGHT - seekH) / 2;

        // Reserve room on the right of the bar for the time read-out and the
        // resize grip in the corner.
        int timeWidth = font.width(MediaControls.timeText(player.positionMicros(), player.durationMicros(), queue.size()));
        int rightLimit = contentX + contentW - GRIP - 2;
        seekW = Math.max(10, rightLimit - timeWidth - 6 - seekX);
        timeTextX = seekX + seekW + 4;
    }

    @Override
    protected void drawContent(GuiGraphics g, Font font) {
        ResourceLocation frame = player.prepareFrame();
        if (frame != null) {
            Blit.textured(g, frame, contentX, contentY, contentW, contentH,
                    player.videoWidth(), player.videoHeight());
            if (player.isSeeking()) {
                // A seek — and a resume, which relaunches the same way — holds this frame
                // on screen for about a second. Without something moving over it, a held
                // frame and a dead player look exactly alike.
                drawLoadingNotice(g, font, Component.translatable("gui.liasmediaplayer.video.seeking"));
            }
        } else {
            g.fill(contentX, contentY, contentX + contentW, contentY + contentH, Theme.PLACEHOLDER);
            if (player.state() != VideoPlayer.State.FAILED) {
                // Loading, buffering, or waiting on a seek that has nothing to hold over:
                // all of them are "working", and all of them get the spinner that says so.
                drawLoadingNotice(g, font, Component.translatable(
                        player.state() == VideoPlayer.State.LOADING
                                ? "gui.liasmediaplayer.video.loading"
                                : "gui.liasmediaplayer.video.buffering"));
                return;
            }

            errorPanel.render(g, font, contentX, contentY, contentW, contentH,
                    player.errorMessage(), cursorX(), cursorY());
        }
    }

    /**
     * A chip carrying a turning spinner and a word, centred over the content — what the
     * player shows whenever it is working and has nothing new to show for it yet.
     */
    private void drawLoadingNotice(GuiGraphics g, Font font, Component status) {
        int spinner = BUTTON;
        int w = spinner + font.width(status) + 14;
        int h = Math.max(spinner, font.lineHeight) + 8;
        int x = contentX + (contentW - w) / 2;
        int y = contentY + (contentH - h) / 2;
        Panels.fill(g, x, y, x + w, y + h, Theme.POPUP_BG);
        Panels.border(g, x, y, x + w, y + h, Theme.BORDER_SUBTLE);
        Glyphs.spinner(g, x + 5, y + (h - spinner) / 2, Theme.ICON_ACTIVE, Anim.now());
        g.drawString(font, status, x + 5 + spinner + 4, y + (h - font.lineHeight) / 2 + 1, Theme.TEXT);
    }

    @Override
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int barTop = contentY + contentH;

        // Play / pause button.
        boolean overPlay = inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON);
        Glyphs.playPause(g, playBtnX, playBtnY, player.isPlaying(), overPlay ? Theme.ICON_HOVER : Theme.ICON);
        if (overPlay) {
            Tooltips.request(WindowChrome.playTooltip(player.isPlaying()));
        }

        // The two fixed-step skips.
        boolean seekable = player.durationMicros() > 0;
        WindowChrome.skipButton(g, backBtnX, backBtnY, false, seekable, mouseX, mouseY);
        WindowChrome.skipButton(g, fwdBtnX, fwdBtnY, true, seekable, mouseX, mouseY);

        // "Next" (skip to the next queued video) button.
        if (showNext) {
            boolean overNext = inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON);
            Glyphs.next(g, nextBtnX, nextBtnY, overNext ? Theme.ICON_HOVER : Theme.ICON);
            if (overNext) {
                Tooltips.request(Component.translatable("gui.liasmediaplayer.control.next"));
            }
        }

        // "Queue" (show/hide the playlist panel) button.
        if (showQueueBtn) {
            boolean overQueue = inRect(mouseX, mouseY, queueBtnX, queueBtnY, BUTTON, BUTTON);
            Glyphs.queue(g, queueBtnX, queueBtnY, (overQueue || panel.isOpen()) ? Theme.ICON_HOVER : Theme.ICON);
            if (overQueue) {
                Tooltips.request(Component.translatable(panel.isOpen()
                        ? "gui.liasmediaplayer.control.queue.hide"
                        : "gui.liasmediaplayer.control.queue.show"));
            }
        }

        // Loop / shuffle toggles.
        RepeatMode repeat = queue.repeat();
        boolean overLoop = inRect(mouseX, mouseY, loopBtnX, loopBtnY, BUTTON, BUTTON);
        Glyphs.loop(g, loopBtnX, loopBtnY, repeat == RepeatMode.ONE, WindowChrome.toggleColor(!repeat.isOff(), overLoop));
        if (overLoop) {
            Tooltips.request(WindowChrome.loopTooltip(repeat));
        }
        if (showShuffle) {
            boolean overShuffle = inRect(mouseX, mouseY, shuffleBtnX, shuffleBtnY, BUTTON, BUTTON);
            Glyphs.shuffle(g, shuffleBtnX, shuffleBtnY, WindowChrome.toggleColor(queue.shuffle(), overShuffle));
            if (overShuffle) {
                Tooltips.request(WindowChrome.shuffleTooltip(queue.shuffle()));
            }
        }

        // Volume: a speaker/mute button with a pop-up vertical slider on hover.
        if (showVolume) {
            boolean overVol = inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON);
            Glyphs.speaker(g, volBtnX, volBtnY, player.isMuted(), overVol ? Theme.ICON_HOVER : Theme.ICON);
            if (overVol) {
                Tooltips.request(WindowChrome.volumeTooltip(player.isMuted()));
            }
            showVolumePopup = overVol || overPopup(mouseX, mouseY) || draggingVolume;
            if (showVolumePopup) {
                MediaControls.drawVolumePopup(g, volBarX, volBarY, player.volume(), Theme.TRACK, Theme.FILL, Theme.KNOB);
            }
        } else {
            showVolumePopup = false;
        }

        // Seek bar: taller, with its handle showing, while it is live.
        double fraction = draggingSeek ? scrubFraction : player.progress();
        boolean overSeek = draggingSeek || MediaControls.overSeek(mouseX, mouseY, seekX, seekY, seekW);
        MediaControls.drawSeekBar(g, seekX, seekY, seekW, fraction, player.durationMicros() > 0, overSeek);

        // Time read-out.
        g.drawString(font, Component.literal(MediaControls.timeText(player.positionMicros(), player.durationMicros(), queue.size())),
                timeTextX, barTop + (CONTROL_BAR_HEIGHT - font.lineHeight) / 2, Theme.TEXT);

        // The playlist panel floats above the window when open.
        if (panel.isOpen()) {
            panel.layout(boxX, boxY, boxW, boxH, panelMode(boxW));
            panel.render(g, font, mouseX, mouseY);
        }
    }


    // ------------------------------------------------------------------
    // Control input
    // ------------------------------------------------------------------

    @Override
    protected ClickResult onControlClick(double mouseX, double mouseY) {
        // Checked first: the failure panel covers the content area, where a click would
        // otherwise start dragging the window.
        if (player.state() == VideoPlayer.State.FAILED) {
            switch (errorPanel.click(mouseX, mouseY)) {
                case RETRY -> {
                    retry();
                    return ClickResult.HANDLED;
                }
                case UPDATE_TOOLS -> {
                    MediaBinaries.updateToolsAsync();
                    return ClickResult.HANDLED;
                }
                case NONE -> {
                }
            }
        }
        if (inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON)) {
            player.togglePause();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, backBtnX, backBtnY, BUTTON, BUTTON)) {
            seekBy(-MediaControls.SKIP_MICROS);
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, fwdBtnX, fwdBtnY, BUTTON, BUTTON)) {
            seekBy(MediaControls.SKIP_MICROS);
            return ClickResult.HANDLED;
        }
        if (showNext && inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON)) {
            advance();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, loopBtnX, loopBtnY, BUTTON, BUTTON)) {
            queue.cycleRepeat();
            return ClickResult.HANDLED;
        }
        if (showShuffle && inRect(mouseX, mouseY, shuffleBtnX, shuffleBtnY, BUTTON, BUTTON)) {
            queue.toggleShuffle();
            return ClickResult.HANDLED;
        }
        if (showQueueBtn && inRect(mouseX, mouseY, queueBtnX, queueBtnY, BUTTON, BUTTON)) {
            panel.toggle();
            return ClickResult.HANDLED;
        }
        if (panel.contains(mouseX, mouseY)) {
            panel.click(mouseX, mouseY);
            return ClickResult.HANDLED;
        }
        if (showVolume && inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON)) {
            player.toggleMute();
            return ClickResult.HANDLED;
        }
        if (showVolume && showVolumePopup && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3,
                MediaControls.VOL_BAR_W + 6, MediaControls.VOL_BAR_H + 6)) {
            draggingVolume = true;
            player.setVolume((float) MediaControls.volumeFractionAt(mouseY, volBarY));
            return ClickResult.HANDLED;
        }
        if (player.durationMicros() > 0 && MediaControls.overSeek(mouseX, mouseY, seekX, seekY, seekW)) {
            draggingSeek = true;
            scrubFraction = MediaControls.fractionAt(mouseX, seekX, seekW);
            return ClickResult.HANDLED;
        }
        return ClickResult.NONE;
    }

    /**
     * Mouse-wheel: scrolls the queue panel when the cursor is over it, otherwise
     * adjusts the volume in 10% steps.
     */
    @Override
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        if (panel.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (!player.hasAudio()) {
            return false;
        }
        player.changeVolume((float) (scrollY * 0.1));
        return true;
    }

    @Override
    protected boolean overPopup(double mouseX, double mouseY) {
        return showVolume && showVolumePopup
                && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, MediaControls.VOL_BAR_W + 6, MediaControls.VOL_BAR_H + 6);
    }

}
