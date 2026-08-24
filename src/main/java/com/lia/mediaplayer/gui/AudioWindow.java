package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.media.MediaTitleCache;
import com.lia.mediaplayer.media.PlaybackError;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Collection;

import static com.lia.mediaplayer.gui.MediaControls.timeText;

/**
 * The on-screen window for a single {@link AudioPlayer}: a compact bar showing the
 * track name plus a control row (play/pause, previous, next, loop, shuffle, a speaker
 * toggle and a seek bar with elapsed/total time). It is deliberately small — no video picture — so
 * it sits unobtrusively while you listen.
 *
 * <p>Movement, resizing, the corner buttons and the shared z-order all come from
 * {@link MediaWindow}; the play queue is the shared {@link PlayQueue} (same model the
 * video player uses), which also holds the history behind the "previous" control and
 * the loop / shuffle modes the two right-hand toggles drive.</p>
 *
 * <p>The queue is also <em>shown</em> the same way: the {@link QueuePanel} that docks
 * beside the video player docks beside this bar too, in its text layout. Before that
 * the bar could only say "+49" in its time read-out, which was the whole of what a
 * fifty-track playlist looked like from here.</p>
 */
final class AudioWindow extends MediaWindow {
    private static final int CONTROL_BAR_HEIGHT = 16;
    private static final int MIN_SEEK_W = 20;
    /**
     * Intrinsic bar size; the title fills the content row, the controls sit below.
     */
    private static final int BASE_W = 254;
    private static final int BASE_H = 14;
    /**
     * How many entries of a bulk enqueue get their title fetched up front.
     */
    private static final int WARM_AHEAD = 10;

    private AudioPlayer player;
    private final PlayQueue queue = new PlayQueue();
    /**
     * What plays next, in the text layout: an audio bar has no thumbnails to show, so
     * its rows are the names alone (see {@link QueuePanel.Mode#TEXT}).
     */
    private final QueuePanel panel = new QueuePanel(queue, this::jumpTo);

    private boolean draggingSeek;
    private boolean draggingVolume;
    private double scrubFraction;

    // Control-bar hit regions cached from the last layout.
    private int playBtnX, playBtnY;
    private int prevBtnX, prevBtnY;
    private int backBtnX, backBtnY;
    private int fwdBtnX, fwdBtnY;
    private int nextBtnX, nextBtnY;
    private int loopBtnX, loopBtnY;
    private int shuffleBtnX, shuffleBtnY;
    private boolean showQueueBtn;
    private int queueBtnX, queueBtnY;
    private int volBtnX, volBtnY;
    private boolean showVolumePopup;
    private int volBarX, volBarY;
    private int seekX, seekY, seekW, seekH;
    private int timeTextX;

    AudioWindow(AudioPlayer player) {
        this.player = player;
    }

    AudioPlayer player() {
        return player;
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    /**
     * Appends a URL to this window's play queue.
     */
    void enqueue(String url) {
        queue.add(url);
        MediaTitleCache.getOrLoad(url); // warm the name so the bar can show it instantly
    }

    /**
     * Appends several URLs (e.g. a whole playlist) in order.
     *
     * <p>Only the first few names are warmed: the title cache is a small LRU backed by
     * a network call per entry, so eagerly resolving a few hundred of them would thrash
     * it for names the bar will not show for the next hour.</p>
     */
    void enqueueAll(Collection<String> urls) {
        int warmed = 0;
        for (String url : urls) {
            if (warmed++ < WARM_AHEAD) {
                enqueue(url);
            } else {
                queue.add(url);
            }
        }
    }

    int queueSize() {
        return queue.size();
    }

    /**
     * Sets how this bar loops (see {@link RepeatMode}); used when a saved playlist is
     * started with looping already on.
     */
    void setRepeat(RepeatMode mode) {
        queue.setRepeat(mode);
    }

    /**
     * Keeps shuffle on for this bar, so every looped round is reshuffled rather than
     * replaying the order the first round happened to get.
     */
    void setShuffle(boolean value) {
        queue.setShuffle(value);
    }

    /**
     * Disposes the current player and starts the next track in the same window — the
     * head of the queue, the current track again under {@link RepeatMode#ONE}, or the
     * start of a fresh round under {@link RepeatMode#ALL}. Returns {@code false}
     * (leaving the current player untouched) when there is nothing left to play, so
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
     * Plays a specific queued entry now (the others keep their order) — what a click on
     * a row of the queue panel means.
     */
    void jumpTo(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playUrl(queue.remove(index));
    }

    /**
     * Goes back to the previously played track, re-queuing the current one at the front
     * so "next" returns to it. Returns {@code false} when there is no history.
     */
    boolean previous() {
        String prev = queue.previous(player.url());
        if (prev == null) {
            return false;
        }
        playUrl(prev);
        return true;
    }

    /**
     * Swaps in a new player for the given URL, disposing the current one.
     */
    private void playUrl(String url) {
        com.lia.mediaplayer.history.HistoryStore.record(url, com.lia.mediaplayer.api.MediaKind.AUDIO);
        player.dispose();
        draggingSeek = false;
        player = new AudioPlayer(url);
        player.start();
        announceIfHidden(url);
    }

    /**
     * Starts the current track over from scratch — a fresh player, a fresh resolve, a
     * fresh ffmpeg. What the retry button on a failed bar does.
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
        return BASE_W;
    }

    @Override
    protected int sourceHeight() {
        return BASE_H;
    }

    @Override
    protected String mediaUrl() {
        return player.url();
    }

    @Override
    protected void close() {
        ((com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance()).getAudioManager().close(this);
    }

    @Override
    protected int anchorGroup() {
        return 2; // images=0, videos=1, audio bars=2 — each cascades independently
    }

    @Override
    protected String stateKey() {
        return WindowStateStore.AUDIO;
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
     * A bar of buttons has no picture to enlarge; filling the screen with one would be
     * a 14 px strip stretched over a monitor.
     */
    @Override
    boolean supportsTheater() {
        return false;
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
    boolean playPrevious() {
        return previous();
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
        return 1.0; // the bar is already a sensible size; the user can resize/zoom
    }

    @Override
    protected void computeAnchor(int screenWidth, int screenHeight, int slot) {
        // Bottom-right, stacked upward so several bars don't land on top of each other
        // and so they clear the left-aligned chat link.
        int x = screenWidth - boxW - PADDING;
        boxX = Mth.clamp(x, 2, Math.max(2, screenWidth - boxW - 2));
        int bottom = screenHeight - 36;
        int y = bottom - boxH - slot * (boxH + 4);
        boxY = Mth.clamp(y, 2, Math.max(2, screenHeight - boxH - 2));
    }

    @Override
    protected int controlBarHeight() {
        return CONTROL_BAR_HEIGHT;
    }

    @Override
    protected int minContentWidth() {
        Font font = Minecraft.getInstance().font;
        int buttons = 8; // play, prev, -10s, +10s, next, loop, shuffle, speaker
        if (!queue.isEmpty()) {
            buttons += 1; // the queue panel toggle, shown only when there is a queue
        }
        int buttonsW = buttons * (BUTTON + 4);
        int timeW = font.width(timeText(player.positionMicros(), player.durationMicros(), queueSize()));
        int needed = buttonsW + MIN_SEEK_W + 6 + timeW + GRIP + 2;
        return Math.max(super.minContentWidth(), needed);
    }

    @Override
    protected boolean hasHideButton() {
        return true;
    }

    /**
     * The bar's content row already <em>is</em> a title row — a note glyph and the
     * track name — so a title bar above it would print the same string twice and
     * double the height of a window whose whole point is to be small.
     */
    @Override
    protected boolean hasTitleBar() {
        return false;
    }

    @Override
    protected boolean alwaysShowControls() {
        return true; // the audio bar's controls should stay visible on the HUD
    }

    @Override
    protected void layoutControls(Font font) {
        int barTop = contentY + contentH;
        playBtnY = barTop + (CONTROL_BAR_HEIGHT - BUTTON) / 2;
        prevBtnY = playBtnY;
        backBtnY = playBtnY;
        fwdBtnY = playBtnY;
        nextBtnY = playBtnY;
        loopBtnY = playBtnY;
        shuffleBtnY = playBtnY;
        volBtnY = playBtnY;

        // Track controls on the outside, within-the-track skips on the inside, so the
        // row reads prev · -10s · +10s · next around the pair they belong to.
        playBtnX = contentX;
        prevBtnX = playBtnX + BUTTON + 4;
        backBtnX = prevBtnX + BUTTON + 4;
        fwdBtnX = backBtnX + BUTTON + 4;
        nextBtnX = fwdBtnX + BUTTON + 4;

        int cursor = nextBtnX + BUTTON + 4;
        showQueueBtn = !queue.isEmpty();
        if (showQueueBtn) {
            queueBtnX = cursor;
            queueBtnY = playBtnY;
            cursor = queueBtnX + BUTTON + 4;
        } else {
            panel.closeIfEmpty();
        }
        loopBtnX = cursor;
        shuffleBtnX = loopBtnX + BUTTON + 4;
        volBtnX = shuffleBtnX + BUTTON + 4;

        // The slider pops up vertically above the speaker button.
        volBarX = volBtnX + (BUTTON - MediaControls.VOL_BAR_W) / 2;
        volBarY = volBtnY - 4 - MediaControls.VOL_BAR_H;

        seekX = volBtnX + BUTTON + 4;
        seekH = MediaControls.SEEK_H;
        seekY = barTop + (CONTROL_BAR_HEIGHT - seekH) / 2;

        int timeWidth = font.width(timeText(player.positionMicros(), player.durationMicros(), queueSize()));
        int rightLimit = contentX + contentW - GRIP - 2;
        seekW = Math.max(10, rightLimit - timeWidth - 6 - seekX);
        timeTextX = seekX + seekW + 4;
    }

    @Override
    protected void drawContent(GuiGraphics g, Font font) {
        // A music note, then the track name (or a status), centred in the content row.
        // While the track is being fetched — including a seek or a resume, both of which
        // relaunch ffmpeg — the note gives way to a turning spinner, so a bar that has
        // gone quiet for a second is visibly working rather than visibly stuck.
        int ty = contentY + (contentH - font.lineHeight) / 2;
        boolean working = player.isSeeking() || player.state() == AudioPlayer.State.LOADING;
        if (working) {
            Glyphs.spinner(g, contentX, ty - 1, Theme.ICON_ACTIVE, Anim.now());
        } else {
            Glyphs.note(g, contentX, ty - 1, Theme.ICON);
        }
        int textX = contentX + 12;
        // Stop the title before the corner buttons (heart, link, hide, close), whose
        // left edge the base class already knows.
        int titleRight = titleTextRight();
        int maxW = Math.max(10, titleRight - textX);

        // The bar is one line of plain text that has to be measured and ellipsised to
        // fit, so the translated strings are resolved here rather than composed as
        // components.
        String text;
        int color = Theme.TEXT;
        if (player.state() == AudioPlayer.State.FAILED) {
            // The bar has one line, so it says the cause rather than "playback failed",
            // and hovering it gives the advice the video window has room to print.
            text = PlaybackError.message(player.errorMessage()).getString();
            color = Theme.DANGER;
            if (inRect(cursorX(), cursorY(), textX, ty, maxW, font.lineHeight)) {
                Tooltips.request(PlaybackError.hint(player.errorMessage()));
            }
        } else if (working) {
            text = Component.translatable("gui.liasmediaplayer.audio.loading",
                    MediaTitleCache.getOrLoad(player.url())).getString();
        } else {
            text = MediaTitleCache.getOrLoad(player.url());
        }
        g.drawString(font, Component.literal(Glyphs.fit(font, text, maxW)), textX, ty, color);
    }

    @Override
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int barTop = contentY + contentH;

        boolean overPlay = inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON);
        boolean failed = player.state() == AudioPlayer.State.FAILED;
        // There is nothing to pause on a failed track, and the one thing worth offering
        // in that spot is another go at it — the bar has no room for a panel of buttons
        // the way the video window does.
        if (failed) {
            Glyphs.refresh(g, playBtnX, playBtnY, overPlay ? Theme.ICON_HOVER : Theme.ICON);
        } else {
            Glyphs.playPause(g, playBtnX, playBtnY, player.isPlaying(), overPlay ? Theme.ICON_HOVER : Theme.ICON);
        }
        if (overPlay) {
            Tooltips.request(failed
                    ? Component.translatable("gui.liasmediaplayer.error.retry")
                    : playTooltip(player.isPlaying()));
        }

        boolean canPrev = queue.hasPrevious();
        boolean overPrev = inRect(mouseX, mouseY, prevBtnX, prevBtnY, BUTTON, BUTTON);
        Glyphs.previous(g, prevBtnX, prevBtnY, canPrev ? (overPrev ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overPrev && canPrev) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.previous"));
        }

        boolean seekable = player.durationMicros() > 0;
        renderSkipButton(g, backBtnX, backBtnY, false, seekable, mouseX, mouseY);
        renderSkipButton(g, fwdBtnX, fwdBtnY, true, seekable, mouseX, mouseY);

        boolean canNext = queue.hasNext();
        boolean overNext = inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON);
        Glyphs.next(g, nextBtnX, nextBtnY, canNext ? (overNext ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overNext && canNext) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.next"));
        }

        if (showQueueBtn) {
            boolean overQueue = inRect(mouseX, mouseY, queueBtnX, queueBtnY, BUTTON, BUTTON);
            Glyphs.queue(g, queueBtnX, queueBtnY, (overQueue || panel.isOpen()) ? Theme.ICON_HOVER : Theme.ICON);
            if (overQueue) {
                Tooltips.request(Component.translatable(panel.isOpen()
                        ? "gui.liasmediaplayer.control.queue.hide"
                        : "gui.liasmediaplayer.control.queue.show"));
            }
        }

        RepeatMode repeat = queue.repeat();
        boolean overLoop = inRect(mouseX, mouseY, loopBtnX, loopBtnY, BUTTON, BUTTON);
        Glyphs.loop(g, loopBtnX, loopBtnY, repeat == RepeatMode.ONE,
                toggleColor(!repeat.isOff(), overLoop));
        if (overLoop) {
            Tooltips.request(loopTooltip(repeat));
        }

        boolean overShuffle = inRect(mouseX, mouseY, shuffleBtnX, shuffleBtnY, BUTTON, BUTTON);
        Glyphs.shuffle(g, shuffleBtnX, shuffleBtnY, toggleColor(queue.shuffle(), overShuffle));
        if (overShuffle) {
            Tooltips.request(shuffleTooltip(queue.shuffle()));
        }

        boolean overVol = inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON);
        Glyphs.speaker(g, volBtnX, volBtnY, player.isMuted(), overVol ? Theme.ICON_HOVER : Theme.ICON);
        if (overVol) {
            Tooltips.request(volumeTooltip(player.isMuted()));
        }
        showVolumePopup = overVol || overPopup(mouseX, mouseY) || draggingVolume;
        if (showVolumePopup) {
            MediaControls.drawVolumePopup(g, volBarX, volBarY, player.volume(), Theme.TRACK, Theme.FILL, Theme.KNOB);
        }

        // Seek bar: taller, with its handle showing, while it is live.
        double fraction = draggingSeek ? scrubFraction : player.progress();
        boolean overSeek = draggingSeek || MediaControls.overSeek(mouseX, mouseY, seekX, seekY, seekW);
        MediaControls.drawSeekBar(g, seekX, seekY, seekW, fraction, player.durationMicros() > 0, overSeek);

        g.drawString(font, Component.literal(timeText(player.positionMicros(), player.durationMicros(), queue.size())),
                timeTextX, barTop + (CONTROL_BAR_HEIGHT - font.lineHeight) / 2, Theme.TEXT);

        // The bar keeps its controls on the HUD; the panel is for the screen that can
        // actually click it.
        if (panel.isOpen() && isInteractive()) {
            panel.layout(boxX, boxY, boxW, boxH, QueuePanel.Mode.TEXT);
            panel.render(g, font, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Control input
    // ------------------------------------------------------------------

    @Override
    protected ClickResult onControlClick(double mouseX, double mouseY) {
        if (inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON)) {
            if (player.state() == AudioPlayer.State.FAILED) {
                retry();
            } else {
                player.togglePause();
            }
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, prevBtnX, prevBtnY, BUTTON, BUTTON)) {
            previous();
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
        if (inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON)) {
            advance();
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
        if (inRect(mouseX, mouseY, loopBtnX, loopBtnY, BUTTON, BUTTON)) {
            queue.cycleRepeat();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, shuffleBtnX, shuffleBtnY, BUTTON, BUTTON)) {
            queue.toggleShuffle();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON)) {
            player.toggleMute();
            return ClickResult.HANDLED;
        }
        if (showVolumePopup && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, MediaControls.VOL_BAR_W + 6, MediaControls.VOL_BAR_H + 6)) {
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

    @Override
    protected boolean overPopup(double mouseX, double mouseY) {
        return showVolumePopup
                && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, MediaControls.VOL_BAR_W + 6, MediaControls.VOL_BAR_H + 6);
    }

    /**
     * Mouse-wheel: scrolls the queue panel when the cursor is over it, otherwise adjusts
     * the volume in 10% steps.
     */
    @Override
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        if (panel.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        player.changeVolume((float) (scrollY * 0.1));
        return true;
    }

    @Override
    protected boolean overExtraRegion(double mouseX, double mouseY) {
        return panel.contains(mouseX, mouseY);
    }

    /**
     * Keeps the bar far enough from the right edge for the docked panel to fit beside
     * it — the same clamp the video player applies, and for the same reason: without it
     * a bar dragged into the corner would have the panel drawn on top of it.
     */
    @Override
    protected void constrainPosition(int screenWidth, int screenHeight) {
        if (!panel.isOpen()) {
            return;
        }
        int maxX = screenWidth - boxW - QueuePanel.reserveFor(QueuePanel.Mode.TEXT);
        if (maxX >= 2) {
            boxX = Mth.clamp(boxX, 2, maxX);
        }
    }

    /**
     * Caps the bar's width so the panel still fits beside it on a narrow screen.
     */
    @Override
    protected int maxContentWidth(int screenWidth) {
        int base = screenWidth - PADDING * 2 - 2;
        if (!panel.isOpen()) {
            return base;
        }
        return Math.max(minContentWidth(),
                base - QueuePanel.reserveFor(QueuePanel.Mode.TEXT));
    }

}
