package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.video.VideoPlayer;
import com.lia.mediaplayer.video.VideoThumbnailCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

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
 * <p>A window also owns a small <em>queue</em> of URLs. Rather than spawning a new
 * window for every link, additional videos are appended here; when the current
 * one ends (or the user presses "next") the window swaps its {@link VideoPlayer}
 * for the next queued URL in place.</p>
 */
final class VideoWindow extends MediaWindow {
    private static final int CONTROL_BAR_HEIGHT = 18;
    /**
     * Smallest seek bar we keep when computing the minimum window width.
     */
    private static final int MIN_SEEK_W = 20;
    /**
     * How many entries of a bulk enqueue get their thumbnail/title fetched up front.
     */
    private static final int WARM_AHEAD = 10;

    /**
     * A player box this wide (or narrower) gets the compact {@link QueuePanel.Mode#MINI}
     * queue panel: beside a small player, a full-width one would be wider than the
     * video it belongs to.
     */
    private static final int MINI_PANEL_MAX_BOX_W = 200;

    private VideoPlayer player;
    /**
     * URLs waiting to play in this same window, in play order.
     */
    private final PlayQueue queue = new PlayQueue();
    private boolean draggingSeek;
    private boolean draggingVolume;
    private double scrubFraction;

    // Control-bar hit regions cached from the last layout.
    private int playBtnX, playBtnY;
    private int backBtnX, backBtnY;
    private int fwdBtnX, fwdBtnY;
    private boolean showNext;
    private int nextBtnX, nextBtnY;
    private int loopBtnX, loopBtnY;
    private boolean showShuffle;
    private int shuffleBtnX, shuffleBtnY;
    private boolean showVolume;
    private boolean showVolumePopup;
    private int volBtnX, volBtnY;
    private int volBarX, volBarY;
    private int seekX, seekY, seekW, seekH;
    private int timeTextX;
    private boolean showQueueBtn;
    private int queueBtnX, queueBtnY;

    /**
     * The list of what plays next, docked beside the player. Shared with the audio bar
     * (see {@link QueuePanel}); this window only says which layout it has room for and
     * what "play this one" means.
     */
    private final QueuePanel panel = new QueuePanel(queue, this::jumpTo);

    /** Drawn over the content area once playback has failed; see {@link ErrorPanel}. */
    private final ErrorPanel errorPanel = new ErrorPanel();

    VideoWindow(VideoPlayer player) {
        this.player = player;
    }

    VideoPlayer player() {
        return player;
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    /**
     * Appends a URL to this window's play queue (it plays after the current ones).
     */
    void enqueue(String url) {
        queue.add(url);
        // Warm the thumbnail and title so the panel can show them without a click.
        VideoThumbnailCache.getOrLoad(url);
        MediaTitleCache.getOrLoad(url);
    }

    /**
     * Appends several URLs in order — an expanded YouTube playlist, typically.
     *
     * <p>Only the first few are warmed: both caches are small LRUs backed by a network
     * (or ffmpeg) call per entry, so eagerly warming hundreds of them would thrash the
     * cache and fire hundreds of requests for rows nobody has scrolled to. The panel
     * loads the rest as it draws them.</p>
     */
    void enqueueAll(java.util.Collection<String> urls) {
        int warmed = 0;
        for (String url : urls) {
            if (warmed++ < WARM_AHEAD) {
                enqueue(url);
            } else {
                queue.add(url);
            }
        }
    }

    /**
     * Number of URLs still waiting to play after the current one.
     */
    int queueSize() {
        return queue.size();
    }

    /**
     * A snapshot of the queued URLs, in play order, for rendering.
     */
    List<String> queuedUrls() {
        return queue.snapshot();
    }

    /**
     * Disposes the current player and starts the next video in the same window — the
     * head of the queue, the current video again under {@link RepeatMode#ONE}, or the
     * start of a fresh round under {@link RepeatMode#ALL}. Returns {@code false} (and
     * leaves the current player untouched) when there is nothing left to play, so
     * callers can close the window instead.
     */
    boolean advance() {
        String next = queue.next(player.url());
        if (next == null) {
            return false;
        }
        playUrl(next);
        return true;
    }

    /**
     * Sets how this window loops (see {@link RepeatMode}).
     */
    void setRepeat(RepeatMode mode) {
        queue.setRepeat(mode);
    }

    /**
     * Keeps shuffle on for this window, so every looped round is reshuffled.
     */
    void setShuffle(boolean value) {
        queue.setShuffle(value);
    }

    /**
     * Plays a specific queued entry now (the others keep their order).
     */
    void jumpTo(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playUrl(queue.remove(index));
    }

    /**
     * Removes a queued entry without playing it.
     */
    void removeAt(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
        }
    }

    /**
     * Moves a queued entry one place earlier in the queue.
     */
    void moveUp(int index) {
        queue.moveUp(index);
    }

    /**
     * Moves a queued entry one place later in the queue.
     */
    void moveDown(int index) {
        queue.moveDown(index);
    }

    /**
     * Swaps in a new player for the given URL, disposing the current one.
     */
    private void playUrl(String url) {
        com.lia.mediaplayer.history.HistoryStore.record(url, com.lia.mediaplayer.api.MediaKind.VIDEO);
        player.dispose();
        draggingSeek = false;
        draggingVolume = false;
        player = new VideoPlayer(url);
        player.start();
        announceIfHidden(url);
    }

    /**
     * Starts the current video over from scratch — a fresh player, a fresh resolve, a
     * fresh ffmpeg. What the retry button on a failed player does, and the only sensible
     * answer to most of the causes {@link com.lia.mediaplayer.media.PlaybackError} names:
     * an expired stream URL, a timeout, a network blip.
     */
    void retry() {
        playUrl(player.url());
    }

    /**
     * Disposes the current player and discards anything still queued.
     */
    void disposeAll() {
        queue.clear();
        player.dispose();
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
    protected String mediaUrl() {
        return player.url();
    }

    @Override
    protected void close() {
        ((com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance()).getVideoManager().close(this);
    }

    @Override
    protected int anchorGroup() {
        return 1;
    }

    @Override
    protected String stateKey() {
        return WindowStateStore.VIDEO;
    }

    @Override
    protected WindowStateStore.State decorateState(WindowStateStore.State geometry) {
        return new WindowStateStore.State(geometry.placed(), geometry.x(), geometry.y(),
                geometry.sized(), geometry.width(),
                panel.isOpen(), queue.repeat(), queue.shuffle());
    }

    @Override
    protected void applyRestoredState(WindowStateStore.State state) {
        panel.setOpen(state.queuePanel());
        queue.setRepeat(state.repeat());
        queue.setShuffle(state.shuffle());
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

    // ------------------------------------------------------------------
    // Transport (keyboard shortcuts; the control bar reaches the same actions)
    // ------------------------------------------------------------------

    @Override
    boolean hasTransport() {
        return true;
    }

    @Override
    boolean togglePlayPause() {
        player.togglePause();
        return true;
    }

    @Override
    boolean seekBy(long deltaMicros) {
        long duration = player.durationMicros();
        if (duration <= 0) {
            return false; // a live stream has no position to seek within
        }
        player.seekTo(Mth.clamp(player.positionMicros() + deltaMicros, 0, duration));
        return true;
    }

    @Override
    long positionMicros() {
        return player.positionMicros();
    }

    @Override
    boolean playNext() {
        return advance();
    }

    @Override
    boolean cycleRepeat() {
        queue.cycleRepeat();
        return true;
    }

    @Override
    boolean toggleShuffle() {
        queue.toggleShuffle();
        return true;
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
            Tooltips.request(playTooltip(player.isPlaying()));
        }

        // The two fixed-step skips.
        boolean seekable = player.durationMicros() > 0;
        renderSkipButton(g, backBtnX, backBtnY, false, seekable, mouseX, mouseY);
        renderSkipButton(g, fwdBtnX, fwdBtnY, true, seekable, mouseX, mouseY);

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
        Glyphs.loop(g, loopBtnX, loopBtnY, repeat == RepeatMode.ONE, toggleColor(!repeat.isOff(), overLoop));
        if (overLoop) {
            Tooltips.request(loopTooltip(repeat));
        }
        if (showShuffle) {
            boolean overShuffle = inRect(mouseX, mouseY, shuffleBtnX, shuffleBtnY, BUTTON, BUTTON);
            Glyphs.shuffle(g, shuffleBtnX, shuffleBtnY, toggleColor(queue.shuffle(), overShuffle));
            if (overShuffle) {
                Tooltips.request(shuffleTooltip(queue.shuffle()));
            }
        }

        // Volume: a speaker/mute button with a pop-up vertical slider on hover.
        if (showVolume) {
            boolean overVol = inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON);
            Glyphs.speaker(g, volBtnX, volBtnY, player.isMuted(), overVol ? Theme.ICON_HOVER : Theme.ICON);
            if (overVol) {
                Tooltips.request(volumeTooltip(player.isMuted()));
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
                    com.lia.mediaplayer.tools.MediaBinaries.updateToolsAsync();
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
        if (showVolume && showVolumePopup && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, MediaControls.VOL_BAR_W + 6, MediaControls.VOL_BAR_H + 6)) {
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

    @Override
    protected boolean onControlDrag(double mouseX, double mouseY) {
        if (draggingVolume) {
            player.setVolume((float) MediaControls.volumeFractionAt(mouseY, volBarY));
            return true;
        }
        if (draggingSeek) {
            scrubFraction = MediaControls.fractionAt(mouseX, seekX, seekW);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onControlRelease() {
        if (draggingVolume) {
            draggingVolume = false;
            return true;
        }
        if (draggingSeek) {
            draggingSeek = false;
            player.seekToFraction(scrubFraction);
            return true;
        }
        return false;
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

    @Override
    protected boolean overExtraRegion(double mouseX, double mouseY) {
        return panel.contains(mouseX, mouseY);
    }

}
