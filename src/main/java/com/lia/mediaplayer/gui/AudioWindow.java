package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.media.MediaTitleCache;

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

    private boolean draggingSeek;
    private boolean draggingVolume;
    private double scrubFraction;

    // Control-bar hit regions cached from the last layout.
    private int playBtnX, playBtnY;
    private int prevBtnX, prevBtnY;
    private int nextBtnX, nextBtnY;
    private int loopBtnX, loopBtnY;
    private int shuffleBtnX, shuffleBtnY;
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
        player.dispose();
        draggingSeek = false;
        player = new AudioPlayer(url);
        player.start();
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
        int buttons = 6; // play, prev, next, loop, shuffle, speaker
        int buttonsW = buttons * (BUTTON + 4);
        int timeW = font.width(timeText(player.positionMicros(), player.durationMicros(), queueSize()));
        int needed = buttonsW + MIN_SEEK_W + 6 + timeW + GRIP + 2;
        return Math.max(MIN_CONTENT, needed);
    }

    @Override
    protected boolean hasHideButton() {
        return true;
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
        nextBtnY = playBtnY;
        loopBtnY = playBtnY;
        shuffleBtnY = playBtnY;
        volBtnY = playBtnY;

        playBtnX = contentX;
        prevBtnX = playBtnX + BUTTON + 4;
        nextBtnX = prevBtnX + BUTTON + 4;
        loopBtnX = nextBtnX + BUTTON + 4;
        shuffleBtnX = loopBtnX + BUTTON + 4;
        volBtnX = shuffleBtnX + BUTTON + 4;

        // The slider pops up vertically above the speaker button.
        volBarX = volBtnX + (BUTTON - MediaControls.VOL_BAR_W) / 2;
        volBarY = volBtnY - 4 - MediaControls.VOL_BAR_H;

        seekX = volBtnX + BUTTON + 4;
        seekH = 4;
        seekY = barTop + (CONTROL_BAR_HEIGHT - seekH) / 2;

        int timeWidth = font.width(timeText(player.positionMicros(), player.durationMicros(), queueSize()));
        int rightLimit = contentX + contentW - GRIP - 2;
        seekW = Math.max(10, rightLimit - timeWidth - 6 - seekX);
        timeTextX = seekX + seekW + 4;
    }

    @Override
    protected void drawContent(GuiGraphics g, Font font) {
        // A music note, then the track name (or a status), centred in the content row.
        int ty = contentY + (contentH - font.lineHeight) / 2;
        Glyphs.note(g, contentX, ty - 1, Theme.ICON);
        int textX = contentX + 12;
        // Stop the title before the three corner buttons (link, hide, close), which are
        // laid out right-to-left from closeBtnX.
        int titleRight = closeBtnX - 2 * (BUTTON + 2) - 2;
        int maxW = Math.max(10, titleRight - textX);

        // The bar is one line of plain text that has to be measured and ellipsised to
        // fit, so the translated strings are resolved here rather than composed as
        // components.
        String text;
        int color = Theme.TEXT;
        switch (player.state()) {
            case FAILED -> {
                text = Component.translatable("gui.liasmediaplayer.audio.playback_failed").getString();
                color = Theme.DANGER;
            }
            case LOADING -> text = Component.translatable("gui.liasmediaplayer.audio.loading",
                    MediaTitleCache.getOrLoad(player.url())).getString();
            default -> text = MediaTitleCache.getOrLoad(player.url());
        }
        g.drawString(font, Component.literal(Glyphs.fit(font, text, maxW)), textX, ty, color);
    }

    @Override
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int barTop = contentY + contentH;
        g.fill(boxX, barTop, boxX + boxW, boxY + boxH, Theme.CONTROL_BAR_BG);

        boolean overPlay = inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON);
        Glyphs.playPause(g, playBtnX, playBtnY, player.isPlaying(), overPlay ? Theme.ICON_HOVER : Theme.ICON);
        if (overPlay) {
            Tooltips.request(playTooltip(player.isPlaying()));
        }

        boolean canPrev = queue.hasPrevious();
        boolean overPrev = inRect(mouseX, mouseY, prevBtnX, prevBtnY, BUTTON, BUTTON);
        Glyphs.previous(g, prevBtnX, prevBtnY, canPrev ? (overPrev ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overPrev && canPrev) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.previous"));
        }

        boolean canNext = queue.hasNext();
        boolean overNext = inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON);
        Glyphs.next(g, nextBtnX, nextBtnY, canNext ? (overNext ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (overNext && canNext) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.next"));
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

        // Seek bar.
        double fraction = draggingSeek ? scrubFraction : player.progress();
        g.fill(seekX, seekY, seekX + seekW, seekY + seekH, Theme.TRACK);
        if (player.durationMicros() > 0) {
            int fill = (int) Math.round(seekW * fraction);
            g.fill(seekX, seekY, seekX + fill, seekY + seekH, Theme.FILL);
            int knobX = seekX + Mth.clamp(fill, 0, seekW);
            g.fill(knobX - 1, seekY - 2, knobX + 1, seekY + seekH + 2, Theme.KNOB);
        }

        g.drawString(font, Component.literal(timeText(player.positionMicros(), player.durationMicros(), queue.size())),
                timeTextX, barTop + (CONTROL_BAR_HEIGHT - font.lineHeight) / 2, Theme.TEXT);
    }

    // ------------------------------------------------------------------
    // Control input
    // ------------------------------------------------------------------

    @Override
    protected ClickResult onControlClick(double mouseX, double mouseY) {
        if (inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON)) {
            player.togglePause();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, prevBtnX, prevBtnY, BUTTON, BUTTON)) {
            previous();
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON)) {
            advance();
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
        if (player.durationMicros() > 0 && inRect(mouseX, mouseY, seekX, seekY - 3, seekW, seekH + 6)) {
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
     * Plain mouse wheel over the bar changes the volume in 10% steps.
     */
    @Override
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        player.changeVolume((float) (scrollY * 0.1));
        return true;
    }

}
