package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.media.MediaTitleCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.lia.mediaplayer.gui.MediaControls.timeText;

/**
 * The on-screen window for a single {@link AudioPlayer}: a compact bar showing the
 * track name plus a control row (play/pause, previous, next, a speaker toggle and a
 * seek bar with elapsed/total time). It is deliberately small — no video picture — so
 * it sits unobtrusively while you listen.
 *
 * <p>Movement, resizing, the corner buttons and the shared z-order all come from
 * {@link MediaWindow}; the play queue is the shared {@link PlayQueue} (same model the
 * video player uses), and a short history list backs the "previous" control.</p>
 */
final class AudioWindow extends MediaWindow {
    private static final int CONTROL_BAR_HEIGHT = 16;
    private static final int MIN_SEEK_W = 20;
    /** Intrinsic bar size; the title fills the content row, the controls sit below. */
    private static final int BASE_W = 220;
    private static final int BASE_H = 14;
    /** How many played tracks to remember for the "previous" control. */
    private static final int MAX_HISTORY = 64;

    /** Pop-up volume slider geometry (mirrors VideoWindow). */
    private static final int VOL_BAR_W = 6;
    private static final int VOL_BAR_H = 40;

    private AudioPlayer player;
    private final PlayQueue queue = new PlayQueue();
    /** Previously-played URLs, most recent last (backs the "previous" button). */
    private final List<String> history = new ArrayList<>();

    private boolean draggingSeek;
    private boolean draggingVolume;
    private double scrubFraction;

    // Control-bar hit regions cached from the last layout.
    private int playBtnX, playBtnY;
    private int prevBtnX, prevBtnY;
    private int nextBtnX, nextBtnY;
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

    /** Appends a URL to this window's play queue. */
    void enqueue(String url) {
        queue.add(url);
        MediaTitleCache.getOrLoad(url); // warm the name so the bar can show it instantly
    }

    /** Appends several URLs (e.g. a whole playlist) in order. */
    void enqueueAll(Collection<String> urls) {
        for (String url : urls) {
            enqueue(url);
        }
    }

    int queueSize() {
        return queue.size();
    }

    /**
     * Disposes the current player and starts the next queued URL in the same window.
     * Returns {@code false} (leaving the current player untouched) when the queue is
     * empty, so callers can close the window instead.
     */
    boolean advance() {
        if (queue.isEmpty()) {
            return false;
        }
        pushHistory(player.url());
        playUrl(queue.removeFirst());
        return true;
    }

    /**
     * Goes back to the previously played track, re-queuing the current one at the front
     * so "next" returns to it. Returns {@code false} when there is no history.
     */
    boolean previous() {
        if (history.isEmpty()) {
            return false;
        }
        queue.addFirst(player.url());
        String prev = history.remove(history.size() - 1);
        playUrl(prev);
        return true;
    }

    private void pushHistory(String url) {
        history.add(url);
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    /** Swaps in a new player for the given URL, disposing the current one. */
    private void playUrl(String url) {
        player.dispose();
        draggingSeek = false;
        player = new AudioPlayer(url);
        player.start();
    }

    /** Disposes the current player and discards anything still queued. */
    void disposeAll() {
        queue.clear();
        history.clear();
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
        AudioPlayerManager.close(this);
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
        int buttons = 4; // play, prev, next, speaker
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
        volBtnY = playBtnY;

        playBtnX = contentX;
        prevBtnX = playBtnX + BUTTON + 4;
        nextBtnX = prevBtnX + BUTTON + 4;
        volBtnX = nextBtnX + BUTTON + 4;

        // The slider pops up vertically above the speaker button.
        volBarX = volBtnX + (BUTTON - VOL_BAR_W) / 2;
        volBarY = volBtnY - 4 - VOL_BAR_H;

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
        Glyphs.note(g, contentX, ty - 1, BTN_COLOR);
        int textX = contentX + 12;
        // Stop the title before the three corner buttons (link, hide, close), which are
        // laid out right-to-left from closeBtnX.
        int titleRight = closeBtnX - 2 * (BUTTON + 2) - 2;
        int maxW = Math.max(10, titleRight - textX);

        String text;
        int color = TEXT_COLOR;
        switch (player.state()) {
            case FAILED -> {
                text = "playback failed";
                color = 0xFFFF6B6B;
            }
            case LOADING -> text = MediaTitleCache.getOrLoad(player.url()) + "  (loading…)";
            default -> text = MediaTitleCache.getOrLoad(player.url());
        }
        g.drawString(font, Component.literal(Glyphs.fit(font, text, maxW)), textX, ty, color);
    }

    @Override
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int barTop = contentY + contentH;
        g.fill(boxX, barTop, boxX + boxW, boxY + boxH, BAR_COLOR);

        boolean overPlay = inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON);
        Glyphs.playPause(g, playBtnX, playBtnY, player.isPlaying(), overPlay ? BTN_HOVER : BTN_COLOR);

        boolean canPrev = !history.isEmpty();
        boolean overPrev = inRect(mouseX, mouseY, prevBtnX, prevBtnY, BUTTON, BUTTON);
        Glyphs.previous(g, prevBtnX, prevBtnY, canPrev ? (overPrev ? BTN_HOVER : BTN_COLOR) : 0xFF555555);

        boolean canNext = !queue.isEmpty();
        boolean overNext = inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON);
        Glyphs.next(g, nextBtnX, nextBtnY, canNext ? (overNext ? BTN_HOVER : BTN_COLOR) : 0xFF555555);

        boolean overVol = inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON);
        Glyphs.speaker(g, volBtnX, volBtnY, player.isMuted(), overVol ? BTN_HOVER : BTN_COLOR);
        showVolumePopup = overVol || overPopup(mouseX, mouseY) || draggingVolume;
        if (showVolumePopup) {
            MediaControls.drawVolumePopup(g, volBarX, volBarY, player.volume(), TRACK_COLOR, FILL_COLOR, KNOB_COLOR);
        }

        // Seek bar.
        double fraction = draggingSeek ? scrubFraction : player.progress();
        g.fill(seekX, seekY, seekX + seekW, seekY + seekH, TRACK_COLOR);
        if (player.durationMicros() > 0) {
            int fill = (int) Math.round(seekW * fraction);
            g.fill(seekX, seekY, seekX + fill, seekY + seekH, FILL_COLOR);
            int knobX = seekX + Mth.clamp(fill, 0, seekW);
            g.fill(knobX - 1, seekY - 2, knobX + 1, seekY + seekH + 2, KNOB_COLOR);
        }

        g.drawString(font, Component.literal(timeText(player.positionMicros(), player.durationMicros(), queue.size())),
                timeTextX, barTop + (CONTROL_BAR_HEIGHT - font.lineHeight) / 2, TEXT_COLOR);
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
        if (inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON)) {
            player.toggleMute();
            return ClickResult.HANDLED;
        }
        if (showVolumePopup && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, VOL_BAR_W + 6, VOL_BAR_H + 6)) {
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
                && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, VOL_BAR_W + 6, VOL_BAR_H + 6);
    }

    /** Plain mouse wheel over the bar changes the volume in 10% steps. */
    @Override
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        player.changeVolume((float) (scrollY * 0.1));
        return true;
    }

}
