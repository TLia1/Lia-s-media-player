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
 * control bar (play/pause, an optional "next" button, a speaker toggle and a
 * pop-up volume slider, and a seek bar with elapsed/total time) and two corner
 * buttons (hide, close).
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
    /** Smallest seek bar we keep when computing the minimum window width. */
    private static final int MIN_SEEK_W = 20;

    /** Pop-up volume slider geometry. */
    private static final int VOL_BAR_W = 6;
    private static final int VOL_BAR_H = 40;

    /** Queue panel geometry. */
    private static final int ROW_H = 30;
    private static final int THUMB_W = 48;
    private static final int THUMB_H = 27;
    private static final int HEADER_H = 12;
    private static final int PANEL_PAD = 3;
    /** Fixed width of the side panel and the gap between it and the player. */
    private static final int PANEL_W = 200;
    /** Compact panel width used next to a small player (thumbnails only, no titles). */
    private static final int PANEL_W_MINI = 104;
    /** A player box this wide (or narrower) gets the compact "mini" queue panel. */
    private static final int MINI_PANEL_MAX_BOX_W = 200;
    private static final int PANEL_GAP = 4;
    /**
     * Horizontal room kept to the right of the player for the open panel (panel width
     * + gap + a 2px margin), for the full and the compact ("mini") panel respectively.
     * Used both to cap the player's width and to clamp its position, so the docked
     * panel always fits on the right and never overlaps it.
     */
    private static final int PANEL_RESERVE = PANEL_W + PANEL_GAP + 2;
    private static final int PANEL_RESERVE_MINI = PANEL_W_MINI + PANEL_GAP + 2;
    /** Width of the scrollbar drawn when the queue overflows. */
    private static final int SCROLLBAR_W = 3;
    private static final int PANEL_BG = 0xF0141414;
    private static final int PANEL_HEADER_BG = 0xFF1E1E1E;
    private static final int ROW_BG = 0xFF202020;
    private static final int ROW_HOVER_BG = 0xFF2E2E38;
    private static final int SCROLL_TRACK_BG = 0xFF333333;
    private static final int SCROLL_THUMB_COLOR = 0xFF6A6A6A;

    private VideoPlayer player;
    /** URLs waiting to play in this same window, in play order. */
    private final PlayQueue queue = new PlayQueue();
    private boolean draggingSeek;
    private boolean draggingVolume;
    private double scrubFraction;

    // Control-bar hit regions cached from the last layout.
    private int playBtnX, playBtnY;
    private boolean showNext;
    private int nextBtnX, nextBtnY;
    private boolean showVolume;
    private boolean showVolumePopup;
    private int volBtnX, volBtnY;
    private int volBarX, volBarY;
    private int seekX, seekY, seekW, seekH;
    private int timeTextX;
    private boolean showQueueBtn;
    private int queueBtnX, queueBtnY;

    // Queue panel state + last-laid-out geometry (for input hit-testing).
    private boolean queueOpen;
    private int queueScroll;        // first visible row index
    private int panelX, panelY, panelW, panelH;
    private int panelRowsTop;       // y of the first row
    private int panelVisibleRows;   // rows that fit in the panel
    private boolean panelScrollable; // true when there are more rows than fit
    private boolean panelMini;       // compact layout (no titles) for a small player

    VideoWindow(VideoPlayer player) {
        this.player = player;
    }

    VideoPlayer player() {
        return player;
    }

    // ------------------------------------------------------------------
    // Queue
    // ------------------------------------------------------------------

    /** Appends a URL to this window's play queue (it plays after the current ones). */
    void enqueue(String url) {
        queue.add(url);
        // Warm the thumbnail and title so the panel can show them without a click.
        VideoThumbnailCache.getOrLoad(url);
        MediaTitleCache.getOrLoad(url);
    }

    /** Number of URLs still waiting to play after the current one. */
    int queueSize() {
        return queue.size();
    }

    /** A snapshot of the queued URLs, in play order, for rendering. */
    List<String> queuedUrls() {
        return queue.snapshot();
    }

    /**
     * Disposes the current player and starts the next queued URL in the same
     * window. Returns {@code false} (and leaves the current player untouched) when
     * the queue is empty, so callers can close the window instead.
     */
    boolean advance() {
        if (queue.isEmpty()) {
            return false;
        }
        playUrl(queue.removeFirst());
        return true;
    }

    /** Plays a specific queued entry now (the others keep their order). */
    void jumpTo(int index) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        playUrl(queue.remove(index));
    }

    /** Removes a queued entry without playing it. */
    void removeAt(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
        }
    }

    /** Moves a queued entry one place earlier in the queue. */
    void moveUp(int index) {
        queue.moveUp(index);
    }

    /** Moves a queued entry one place later in the queue. */
    void moveDown(int index) {
        queue.moveDown(index);
    }

    /** Swaps in a new player for the given URL, disposing the current one. */
    private void playUrl(String url) {
        player.dispose();
        draggingSeek = false;
        draggingVolume = false;
        player = new VideoPlayer(url);
        player.start();
    }

    /** Disposes the current player and discards anything still queued. */
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

    @Override
    protected String mediaUrl() {
        return player.url();
    }

    @Override
    protected void close() {
        VideoPlayerManager.close(this);
    }

    @Override
    protected int anchorGroup() {
        return 1;
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
        int rightReserve = (queueOpen && !queue.isEmpty()) ? panelWidthFor(boxW) + PANEL_GAP : 0;
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
        int buttons = 1;                       // play/pause is always shown
        if (!queue.isEmpty()) {
            buttons += 2;                      // next + queue
        }
        if (player.hasAudio()) {
            buttons += 1;                      // speaker
        }
        int buttonsW = buttons * (BUTTON + 4);
        int timeW = font.width(timeText());
        // buttons + minimal seek + gap + time + grip margin (matches layoutControls).
        int needed = buttonsW + MIN_SEEK_W + 6 + timeW + GRIP + 2;
        return Math.max(MIN_CONTENT, needed);
    }

    /**
     * While the queue panel is open it is docked to the right of the player, so cap
     * the player's width to leave room for the panel (plus the gap). This stops the
     * player from growing wide enough to sit underneath the panel.
     */
    @Override
    protected int maxContentWidth(int screenWidth) {
        int base = screenWidth - PADDING * 2 - 2;
        if (!queueOpen || queue.isEmpty()) {
            return base;
        }
        // The panel beside the player is full-size for a large player and compact for
        // a small one, so the room to reserve depends on which the player will use.
        // If the screen is wide enough to fit the full panel beside a player larger
        // than the mini threshold, allow that; otherwise keep the player within the
        // mini range so only the compact panel needs reserving (giving it more room).
        int largeBoxCap = base + PADDING * 2 - PANEL_RESERVE;
        int boxCap;
        if (largeBoxCap > MINI_PANEL_MAX_BOX_W) {
            boxCap = largeBoxCap;
        } else {
            int miniBoxCap = base + PADDING * 2 - PANEL_RESERVE_MINI;
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
        if (!queueOpen || queue.isEmpty()) {
            return;
        }
        int reserve = boxW <= MINI_PANEL_MAX_BOX_W ? PANEL_RESERVE_MINI : PANEL_RESERVE;
        int maxX = screenWidth - boxW - reserve;
        if (maxX >= 2) {
            boxX = Mth.clamp(boxX, 2, maxX);
        }
    }

    /** The panel width that pairs with a player of the given box width. */
    private static int panelWidthFor(int playerBoxW) {
        return playerBoxW <= MINI_PANEL_MAX_BOX_W ? PANEL_W_MINI : PANEL_W;
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

        // "Next" and "queue" buttons: only while something is queued.
        showNext = !queue.isEmpty();
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
            queueOpen = false; // nothing left to show
        }

        showVolume = player.hasAudio();
        if (showVolume) {
            volBtnX = cursor;
            volBtnY = playBtnY;
            cursor = volBtnX + BUTTON + 4;
            // The slider pops up vertically above the speaker button.
            volBarX = volBtnX + (BUTTON - VOL_BAR_W) / 2;
            volBarY = volBtnY - 4 - VOL_BAR_H;
        }

        seekX = cursor;
        seekH = 4;
        seekY = barTop + (CONTROL_BAR_HEIGHT - seekH) / 2;

        // Reserve room on the right of the bar for the time read-out and the
        // resize grip in the corner.
        int timeWidth = font.width(timeText());
        int rightLimit = contentX + contentW - GRIP - 2;
        seekW = Math.max(10, rightLimit - timeWidth - 6 - seekX);
        timeTextX = seekX + seekW + 4;
    }

    @Override
    protected void drawContent(GuiGraphics g, Font font) {
        ResourceLocation frame = player.prepareFrame();
        if (frame != null) {
            g.blit(frame, contentX, contentY, contentW, contentH,
                    0.0f, 0.0f, player.videoWidth(), player.videoHeight(),
                    player.videoWidth(), player.videoHeight());
        } else {
            g.fill(contentX, contentY, contentX + contentW, contentY + contentH, PLACEHOLDER);
            Component status = switch (player.state()) {
                case FAILED -> Component.translatable("gui.liasmediaplayer.video.playback_failed");
                case LOADING -> Component.translatable("gui.liasmediaplayer.video.loading");
                default -> Component.translatable("gui.liasmediaplayer.video.buffering");
            };
            int tx = contentX + (contentW - font.width(status)) / 2;
            int ty = contentY + (contentH - font.lineHeight) / 2;
            g.drawString(font, status, tx, ty, TEXT_COLOR);
        }
    }

    @Override
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int barTop = contentY + contentH;
        g.fill(boxX, barTop, boxX + boxW, boxY + boxH, BAR_COLOR);

        if (player.state() == VideoPlayer.State.FAILED) {
            String msg = player.errorMessage();
            if (msg != null) {
                g.drawString(font, Component.literal(trim(msg, 60)), contentX, barTop + 4, 0xFFFF6B6B);
            }
        }

        // Play / pause button.
        boolean overPlay = inRect(mouseX, mouseY, playBtnX, playBtnY, BUTTON, BUTTON);
        drawPlayPause(g, overPlay ? BTN_HOVER : BTN_COLOR);

        // "Next" (skip to the next queued video) button.
        if (showNext) {
            boolean overNext = inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON);
            drawNext(g, overNext ? BTN_HOVER : BTN_COLOR);
        }

        // "Queue" (show/hide the playlist panel) button.
        if (showQueueBtn) {
            boolean overQueue = inRect(mouseX, mouseY, queueBtnX, queueBtnY, BUTTON, BUTTON);
            drawQueueIcon(g, (overQueue || queueOpen) ? BTN_HOVER : BTN_COLOR);
        }

        // Volume: a speaker/mute button with a pop-up vertical slider on hover.
        if (showVolume) {
            boolean overVol = inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON);
            drawSpeaker(g, overVol ? BTN_HOVER : BTN_COLOR);
            showVolumePopup = overVol || overPopup(mouseX, mouseY) || draggingVolume;
            if (showVolumePopup) {
                drawVolumePopup(g);
            }
        } else {
            showVolumePopup = false;
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

        // Time read-out.
        g.drawString(font, Component.literal(timeText()),
                timeTextX, barTop + (CONTROL_BAR_HEIGHT - font.lineHeight) / 2, TEXT_COLOR);

        // The playlist panel floats above the window when open.
        if (queueOpen && !queue.isEmpty()) {
            renderQueuePanel(g, font, mouseX, mouseY);
        }
    }

    private void drawVolumePopup(GuiGraphics g) {
        g.fill(volBarX - 3, volBarY - 3, volBarX + VOL_BAR_W + 3, volBarY + VOL_BAR_H + 3, 0xE0101010);
        g.fill(volBarX, volBarY, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, TRACK_COLOR);
        float v = player.volume();
        int fillH = Math.round(VOL_BAR_H * v);
        g.fill(volBarX, volBarY + VOL_BAR_H - fillH, volBarX + VOL_BAR_W, volBarY + VOL_BAR_H, FILL_COLOR);
        int knobY = volBarY + VOL_BAR_H - Mth.clamp(fillH, 0, VOL_BAR_H);
        g.fill(volBarX - 2, knobY - 1, volBarX + VOL_BAR_W + 2, knobY + 1, KNOB_COLOR);
    }

    private void drawPlayPause(GuiGraphics g, int color) {
        int cx = playBtnX;
        int cy = playBtnY;
        if (player.isPlaying()) {
            // Two vertical bars.
            g.fill(cx + 1, cy, cx + 4, cy + BUTTON, color);
            g.fill(cx + 7, cy, cx + 10, cy + BUTTON, color);
        } else {
            // A right-pointing triangle approximated with stacked rows.
            for (int i = 0; i < BUTTON; i++) {
                int half = Math.min(i, BUTTON - 1 - i);
                int len = 2 + half;
                g.fill(cx + 2, cy + i, cx + 2 + len, cy + i + 1, color);
            }
        }
    }

    /** A "skip to next" glyph: a right-pointing triangle followed by a vertical bar. */
    private void drawNext(GuiGraphics g, int color) {
        int cx = nextBtnX;
        int cy = nextBtnY;
        for (int i = 0; i < BUTTON; i++) {
            int half = Math.min(i, BUTTON - 1 - i);
            int len = 1 + half;
            g.fill(cx + 1, cy + i, cx + 1 + len, cy + i + 1, color);
        }
        g.fill(cx + BUTTON - 3, cy, cx + BUTTON - 1, cy + BUTTON, color);
    }

    /** A tiny speaker glyph; crossed out when muted. */
    private void drawSpeaker(GuiGraphics g, int color) {
        int x = volBtnX;
        int y = volBtnY;
        int midY = y + BUTTON / 2;
        // Speaker box.
        g.fill(x + 1, midY - 2, x + 3, midY + 2, color);
        // Cone (widening towards the right).
        for (int i = 0; i < 3; i++) {
            g.fill(x + 3, midY - 1 - i, x + 4 + i, midY + 1 + i, color);
        }
        if (player.isMuted()) {
            // A small red "x" past the cone.
            g.fill(x + 7, y + 2, x + 8, y + 3, 0xFFFF5555);
            g.fill(x + 9, y + 2, x + 10, y + 3, 0xFFFF5555);
            g.fill(x + 8, midY, x + 9, midY + 1, 0xFFFF5555);
            g.fill(x + 7, y + BUTTON - 3, x + 8, y + BUTTON - 2, 0xFFFF5555);
            g.fill(x + 9, y + BUTTON - 3, x + 10, y + BUTTON - 2, 0xFFFF5555);
        } else {
            // Two sound "waves".
            g.fill(x + 7, midY - 2, x + 8, midY + 2, color);
            g.fill(x + 9, midY - 3, x + 10, midY + 3, color);
        }
    }

    /** A "playlist" glyph: three stacked lines with a small bar on the left of each. */
    private void drawQueueIcon(GuiGraphics g, int color) {
        int x = queueBtnX;
        int y = queueBtnY;
        for (int row = 0; row < 3; row++) {
            int ry = y + 1 + row * 4;
            g.fill(x + 1, ry, x + 3, ry + 2, color);          // bullet
            g.fill(x + 4, ry, x + BUTTON - 1, ry + 1, color); // line
        }
    }

    // ------------------------------------------------------------------
    // Queue panel
    // ------------------------------------------------------------------

    /** Recomputes the panel geometry (cached for input hit-testing). */
    private void computePanelLayout() {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int rows = queue.size();

        // A small player gets the compact panel (thumbnails only, no titles).
        panelMini = boxW <= MINI_PANEL_MAX_BOX_W;
        panelW = panelWidthFor(boxW);
        // Sit to the right of the player; if there is no room there, fall back to
        // the left of it, and as a last resort clamp onto the screen.
        panelX = boxX + boxW + PANEL_GAP;
        if (panelX + panelW > screenW - 2) {
            int leftX = boxX - PANEL_GAP - panelW;
            panelX = leftX >= 2 ? leftX : Math.max(2, screenW - panelW - 2);
        }

        // Match the player's height; when there are more rows than fit, the extra
        // ones scroll. The panel is never taller than the screen.
        panelH = Mth.clamp(boxH, HEADER_H + PANEL_PAD * 2 + ROW_H, screenH - 4);
        int rowsRoom = Math.max(1, (panelH - HEADER_H - PANEL_PAD * 2) / ROW_H);
        panelVisibleRows = Mth.clamp(rows, 1, rowsRoom);
        panelScrollable = rows > panelVisibleRows;

        int maxScroll = Math.max(0, rows - panelVisibleRows);
        queueScroll = Mth.clamp(queueScroll, 0, maxScroll);

        panelY = Mth.clamp(boxY, 2, Math.max(2, screenH - panelH - 2));
        panelRowsTop = panelY + HEADER_H + PANEL_PAD;
    }

    /** Right edge available for row content (excludes the scrollbar gutter). */
    private int panelContentRight() {
        return panelX + panelW - (panelScrollable ? SCROLLBAR_W + 2 : 0);
    }

    private void renderQueuePanel(GuiGraphics g, Font font, int mouseX, int mouseY) {
        computePanelLayout();
        int rows = queue.size();

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        g.fill(panelX, panelY, panelX + panelW, panelY + HEADER_H, PANEL_HEADER_BG);

        String header = panelMini ? ("(" + rows + ")") : ("Queue (" + rows + ")");
        if (!panelMini && rows > panelVisibleRows) {
            header += "  " + (queueScroll + 1) + "-" + (queueScroll + panelVisibleRows);
        }
        g.drawString(font, Component.literal(header), panelX + 4, panelY + 2, TEXT_COLOR);

        for (int i = 0; i < panelVisibleRows; i++) {
            int index = queueScroll + i;
            if (index >= rows) {
                break;
            }
            renderRow(g, font, index, rowTop(i), mouseX, mouseY);
        }

        if (panelScrollable) {
            renderScrollbar(g, rows);
        }
    }

    /** A thin scrollbar on the right gutter showing the scroll position. */
    private void renderScrollbar(GuiGraphics g, int rows) {
        int sbX = panelX + panelW - SCROLLBAR_W - 1;
        int trackTop = panelRowsTop;
        int trackBot = panelY + panelH - PANEL_PAD;
        int trackH = Math.max(1, trackBot - trackTop);
        g.fill(sbX, trackTop, sbX + SCROLLBAR_W, trackBot, SCROLL_TRACK_BG);

        int thumbH = Math.max(8, trackH * panelVisibleRows / rows);
        int maxScroll = Math.max(1, rows - panelVisibleRows);
        int thumbY = trackTop + (trackH - thumbH) * queueScroll / maxScroll;
        g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, SCROLL_THUMB_COLOR);
    }

    private void renderRow(GuiGraphics g, Font font, int index, int rowY, int mouseX, int mouseY) {
        int rows = queue.size();
        String url = queue.get(index);
        int rowX = panelX + PANEL_PAD;
        int rowW = panelContentRight() - PANEL_PAD - rowX;

        int upX = upBtnX();
        int btnY = rowY + (ROW_H - BUTTON) / 2;
        boolean overButtons = mouseX >= upX - 2 && inRect(mouseX, mouseY, upX, btnY, BUTTON * 3 + 4, BUTTON);
        boolean overRow = inRect(mouseX, mouseY, rowX, rowY, rowW, ROW_H - 1);

        g.fill(rowX, rowY, rowX + rowW, rowY + ROW_H - 1, (overRow && !overButtons) ? ROW_HOVER_BG : ROW_BG);

        // Thumbnail.
        int tx = rowX + 2;
        int ty = rowY + (ROW_H - THUMB_H) / 2;
        drawThumbnail(g, font, url, tx, ty);

        // Position number + label. The compact panel shows neither (the thumbnail
        // and play order are enough), leaving room for a narrower panel.
        if (!panelMini) {
            int labelX = tx + THUMB_W + 4;
            int labelMaxW = upX - 4 - labelX;
            String label = (index + 1) + ". " + MediaTitleCache.getOrLoad(url);
            g.drawString(font, Component.literal(fit(font, label, labelMaxW)),
                    labelX, rowY + (ROW_H - font.lineHeight) / 2, TEXT_COLOR);
        }

        // Reorder + remove buttons.
        boolean canUp = index > 0;
        boolean canDown = index < rows - 1;
        boolean overUp = inRect(mouseX, mouseY, upX, btnY, BUTTON, BUTTON);
        boolean overDown = inRect(mouseX, mouseY, downBtnX(), btnY, BUTTON, BUTTON);
        boolean overRemove = inRect(mouseX, mouseY, removeBtnX(), btnY, BUTTON, BUTTON);
        drawArrow(g, upX, btnY, true, canUp ? (overUp ? BTN_HOVER : BTN_COLOR) : 0xFF555555);
        drawArrow(g, downBtnX(), btnY, false, canDown ? (overDown ? BTN_HOVER : BTN_COLOR) : 0xFF555555);
        g.drawString(font, Component.literal("x"), removeBtnX() + 3, btnY + 2,
                overRemove ? 0xFFFF6B6B : BTN_COLOR);
    }

    private void drawThumbnail(GuiGraphics g, Font font, String url, int tx, int ty) {
        g.fill(tx, ty, tx + THUMB_W, ty + THUMB_H, 0xFF000000);
        VideoThumbnailCache.Thumb thumb = VideoThumbnailCache.getOrLoad(url);
        if (thumb.isLoaded()) {
            // Fit the (already-small) thumbnail inside the box, preserving aspect.
            int tw = Math.max(1, thumb.width);
            int th = Math.max(1, thumb.height);
            double scale = Math.min(THUMB_W / (double) tw, THUMB_H / (double) th);
            int w = Math.max(1, (int) Math.round(tw * scale));
            int h = Math.max(1, (int) Math.round(th * scale));
            int ox = tx + (THUMB_W - w) / 2;
            int oy = ty + (THUMB_H - h) / 2;
            g.blit(thumb.texture, ox, oy, w, h, 0.0f, 0.0f, tw, th, tw, th);
        } else {
            String dots = thumb.state == VideoThumbnailCache.State.FAILED ? "?" : "...";
            g.drawString(font, Component.literal(dots),
                    tx + (THUMB_W - font.width(dots)) / 2, ty + (THUMB_H - font.lineHeight) / 2, 0xFF888888);
        }
    }

    /** Up/down triangle inside an 11px button box. */
    private void drawArrow(GuiGraphics g, int x, int y, boolean up, int color) {
        for (int i = 0; i < 4; i++) {
            int row = up ? i : 3 - i;
            int half = i;
            g.fill(x + 5 - half, y + 3 + row, x + 6 + half, y + 4 + row, color);
        }
    }

    private void handlePanelClick(double mouseX, double mouseY) {
        for (int i = 0; i < panelVisibleRows; i++) {
            int index = queueScroll + i;
            if (index >= queue.size()) {
                break;
            }
            int rowY = rowTop(i);
            if (mouseY < rowY || mouseY >= rowY + ROW_H) {
                continue;
            }
            int btnY = rowY + (ROW_H - BUTTON) / 2;
            if (inRect(mouseX, mouseY, removeBtnX(), btnY, BUTTON, BUTTON)) {
                removeAt(index);
            } else if (inRect(mouseX, mouseY, downBtnX(), btnY, BUTTON, BUTTON)) {
                moveDown(index);
            } else if (inRect(mouseX, mouseY, upBtnX(), btnY, BUTTON, BUTTON)) {
                moveUp(index);
            } else {
                jumpTo(index);
            }
            return;
        }
    }

    private int rowTop(int visibleRow) {
        return panelRowsTop + visibleRow * ROW_H;
    }

    private int removeBtnX() {
        return panelContentRight() - PANEL_PAD - BUTTON;
    }

    private int downBtnX() {
        return removeBtnX() - BUTTON - 2;
    }

    private int upBtnX() {
        return downBtnX() - BUTTON - 2;
    }

    /** Truncates a string with an ellipsis so it fits within {@code maxWidth} pixels. */
    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(sb.toString() + text.charAt(i)) > limit) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + ellipsis;
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
        if (showNext && inRect(mouseX, mouseY, nextBtnX, nextBtnY, BUTTON, BUTTON)) {
            advance();
            return ClickResult.HANDLED;
        }
        if (showQueueBtn && inRect(mouseX, mouseY, queueBtnX, queueBtnY, BUTTON, BUTTON)) {
            queueOpen = !queueOpen;
            queueScroll = 0;
            return ClickResult.HANDLED;
        }
        if (queueOpen && inRect(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            handlePanelClick(mouseX, mouseY);
            return ClickResult.HANDLED;
        }
        if (showVolume && inRect(mouseX, mouseY, volBtnX, volBtnY, BUTTON, BUTTON)) {
            player.toggleMute();
            return ClickResult.HANDLED;
        }
        if (showVolume && showVolumePopup && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, VOL_BAR_W + 6, VOL_BAR_H + 6)) {
            draggingVolume = true;
            player.setVolume((float) volumeFractionAt(mouseY));
            return ClickResult.HANDLED;
        }
        if (player.durationMicros() > 0 && inRect(mouseX, mouseY, seekX, seekY - 3, seekW, seekH + 6)) {
            draggingSeek = true;
            scrubFraction = fractionAt(mouseX);
            return ClickResult.HANDLED;
        }
        return ClickResult.NONE;
    }

    @Override
    protected boolean onControlDrag(double mouseX, double mouseY) {
        if (draggingVolume) {
            player.setVolume((float) volumeFractionAt(mouseY));
            return true;
        }
        if (draggingSeek) {
            scrubFraction = fractionAt(mouseX);
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
        if (queueOpen && inRect(mouseX, mouseY, panelX, panelY, panelW, panelH)) {
            int maxScroll = Math.max(0, queue.size() - panelVisibleRows);
            queueScroll = Mth.clamp(queueScroll - (int) Math.signum(scrollY), 0, maxScroll);
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
                && inRect(mouseX, mouseY, volBarX - 3, volBarY - 3, VOL_BAR_W + 6, VOL_BAR_H + 6);
    }

    @Override
    protected boolean overExtraRegion(double mouseX, double mouseY) {
        return queueOpen && inRect(mouseX, mouseY, panelX, panelY, panelW, panelH);
    }

    private double fractionAt(double mouseX) {
        if (seekW <= 0) {
            return 0;
        }
        return Mth.clamp((mouseX - seekX) / seekW, 0.0, 1.0);
    }

    /** Vertical slider: bottom is 0%, top is 100%. */
    private double volumeFractionAt(double mouseY) {
        return Mth.clamp((volBarY + VOL_BAR_H - mouseY) / (double) VOL_BAR_H, 0.0, 1.0);
    }

    private String timeText() {
        int queued = queue.size();
        String suffix = queued > 0 ? "  +" + queued : "";
        long duration = player.durationMicros();
        if (duration <= 0) {
            return "LIVE" + suffix;
        }
        return format(player.positionMicros()) + " / " + format(duration) + suffix;
    }

    private static String format(long micros) {
        long totalSeconds = Math.max(0, micros / 1_000_000L);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
