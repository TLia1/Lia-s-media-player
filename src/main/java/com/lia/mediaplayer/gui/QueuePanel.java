package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.media.MediaTitleCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/**
 * The list of what plays next, docked beside a player window: one row per queued URL,
 * click a row to jump to it, arrows to reorder, a cross to drop it.
 *
 * <p>It used to live inside {@link VideoWindow} — three hundred lines of geometry,
 * rendering and hit-testing that the audio bar could not reach, so a bar playing a
 * fifty-track playlist could say only "+49" and offer no way to see or reorder them,
 * despite driving the very same {@link PlayQueue}. Everything specific to a window is
 * now the {@link Mode} it asks for and the "play this one" callback it hands over.</p>
 *
 * <p>The panel owns its open/closed state and its scroll position, and caches the
 * geometry of the last layout so the mouse handlers — which fire between frames — test
 * against what was actually drawn. That is the same contract the window controls follow;
 * this is one more control that happens to be bigger than a button.</p>
 */
final class QueuePanel {

    /**
     * How a panel is laid out beside its window.
     *
     * <p>Not a style choice: each one is the most a panel can show in the room it has.
     * A video player wide enough to sit next to a 200 px panel gets thumbnails
     * <em>and</em> titles; a small one gets a narrow panel where a title would be a
     * dozen characters, so it shows thumbnails alone; and the audio bar has no
     * thumbnails to show at all, so its rows are half as tall and hold the names.</p>
     */
    enum Mode {
        /** Thumbnail plus title. */
        FULL(200, 30),
        /** Thumbnail alone, in a narrow panel. */
        MINI(104, 30),
        /** Title alone, in short rows. */
        TEXT(200, 14);

        final int width;
        final int rowHeight;

        Mode(int width, int rowHeight) {
            this.width = width;
            this.rowHeight = rowHeight;
        }

        boolean hasThumbnails() {
            return this != TEXT;
        }

        boolean hasTitles() {
            return this != MINI;
        }
    }

    private static final int THUMB_W = Thumbnail.W;
    private static final int THUMB_H = Thumbnail.H;
    private static final int HEADER_H = 12;
    private static final int PAD = 3;
    /** Gap between the window and the panel docked beside it. */
    static final int GAP = 4;
    /** Width of the scrollbar drawn when the queue overflows. */
    private static final int SCROLLBAR_W = 3;
    /**
     * How far a panel may grow past its window's own height to fit more rows. Without
     * a cap, a queue of two hundred would ask for a panel taller than any screen; with
     * one, a short window still gets a panel worth reading rather than a one-row slot.
     */
    private static final int MAX_NATURAL_ROWS = 8;

    private static final int BUTTON = MediaWindow.BUTTON;

    private final PlayQueue queue;
    /** What the window does when a row is clicked: stop what is playing and play that. */
    private final IntConsumer onJump;

    private boolean open;
    private int scroll;

    // Geometry from the last layout (screen coordinates).
    private Mode mode = Mode.FULL;
    private int x, y, width, height;
    private int rowsTop;
    private int visibleRows;
    private boolean scrollable;

    QueuePanel(PlayQueue queue, IntConsumer onJump) {
        this.queue = queue;
        this.onJump = onJump;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    boolean isOpen() {
        return open && !queue.isEmpty();
    }

    void setOpen(boolean value) {
        open = value;
        scroll = 0;
    }

    void toggle() {
        setOpen(!open);
    }

    /**
     * Forgets an open panel with nothing left in it, so a queue that has just run dry
     * does not leave the window sized for a panel that will not be drawn.
     */
    void closeIfEmpty() {
        if (queue.isEmpty()) {
            open = false;
        }
    }

    /**
     * The horizontal room a window has to keep free on its right for this panel:
     * its width, the gap, and a margin off the screen edge.
     */
    static int reserveFor(Mode mode) {
        return mode.width + GAP + 2;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Places the panel beside the window box, for this frame.
     *
     * <p>To the right of the window; if the screen has no room there, to its left; and
     * failing both, clamped onto the screen. The height matches the window, except that
     * a window shorter than its queue grows the panel up to {@link #MAX_NATURAL_ROWS} —
     * an audio bar is 40 px tall and would otherwise get a panel with one row in it.</p>
     */
    void layout(int boxX, int boxY, int boxW, int boxH, Mode mode) {
        this.mode = mode;
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int rows = queue.size();

        width = mode.width;
        x = boxX + boxW + GAP;
        if (x + width > screenW - 2) {
            int leftX = boxX - GAP - width;
            x = leftX >= 2 ? leftX : Math.max(2, screenW - width - 2);
        }

        int chrome = HEADER_H + PAD * 2;
        int natural = chrome + Math.min(rows, MAX_NATURAL_ROWS) * mode.rowHeight;
        height = Mth.clamp(Math.max(boxH, natural), chrome + mode.rowHeight, screenH - 4);

        int rowsRoom = Math.max(1, (height - chrome) / mode.rowHeight);
        visibleRows = Mth.clamp(rows, 1, rowsRoom);
        scrollable = rows > visibleRows;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - visibleRows));

        y = Mth.clamp(boxY, 2, Math.max(2, screenH - height - 2));
        rowsTop = y + HEADER_H + PAD;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int rows = queue.size();

        Panels.fill(g, x, y, x + width, y + height, Theme.PANEL_BG);
        Panels.fillTop(g, x, y, x + width, y + HEADER_H, Theme.PANEL_HEADER_BG);
        Panels.border(g, x, y, x + width, y + height, Theme.BORDER_SUBTLE);

        Component header = mode.hasTitles()
                ? Component.translatable("gui.liasmediaplayer.queue.header", rows)
                : Component.translatable("gui.liasmediaplayer.queue.header_mini", rows);
        if (mode.hasTitles() && scrollable) {
            header = header.copy().append(Component.translatable("gui.liasmediaplayer.queue.range",
                    scroll + 1, scroll + visibleRows));
        }
        g.drawString(font, header, x + 4, y + 2, Theme.TEXT);

        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= rows) {
                break;
            }
            renderRow(g, font, index, rowTop(i), mouseX, mouseY);
        }

        if (scrollable) {
            renderScrollbar(g, rows);
        }
    }

    /**
     * A thin scrollbar on the right gutter showing the scroll position.
     */
    private void renderScrollbar(GuiGraphics g, int rows) {
        int sbX = x + width - SCROLLBAR_W - 1;
        int trackTop = rowsTop;
        int trackBot = y + height - PAD;
        int trackH = Math.max(1, trackBot - trackTop);
        g.fill(sbX, trackTop, sbX + SCROLLBAR_W, trackBot, Theme.SCROLL_TRACK);

        int thumbH = Math.max(8, trackH * visibleRows / rows);
        int maxScroll = Math.max(1, rows - visibleRows);
        int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
        g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, Theme.SCROLL_THUMB);
    }

    private void renderRow(GuiGraphics g, Font font, int index, int rowY, int mouseX, int mouseY) {
        int rows = queue.size();
        String url = queue.get(index);
        int rowH = mode.rowHeight;
        int rowX = x + PAD;
        int rowW = contentRight() - PAD - rowX;

        int upX = upButtonX();
        int btnY = rowY + (rowH - BUTTON) / 2;
        boolean overButtons = mouseX >= upX - 2
                && MediaWindow.inRect(mouseX, mouseY, upX, btnY, BUTTON * 3 + 4, BUTTON);
        boolean overRow = MediaWindow.inRect(mouseX, mouseY, rowX, rowY, rowW, rowH - 1);

        g.fill(rowX, rowY, rowX + rowW, rowY + rowH - 1,
                (overRow && !overButtons) ? Theme.ROW_HOVER_BG : Theme.ROW_BG);

        int labelX = rowX + 2;
        if (mode.hasThumbnails()) {
            int tx = rowX + 2;
            int ty = rowY + (rowH - THUMB_H) / 2;
            Thumbnail.draw(g, font, url, tx, ty);
            labelX = tx + THUMB_W + 4;
        }

        // Position number + name. The compact panel shows neither (the thumbnail and the
        // play order are enough), which is what lets it be half as wide.
        if (mode.hasTitles()) {
            int labelMaxW = upX - 4 - labelX;
            String label = (index + 1) + ". " + MediaTitleCache.getOrLoad(url);
            g.drawString(font, Component.literal(Glyphs.fit(font, label, labelMaxW)),
                    labelX, rowY + (rowH - font.lineHeight) / 2, Theme.TEXT);
        }

        boolean canUp = index > 0;
        boolean canDown = index < rows - 1;
        boolean overUp = MediaWindow.inRect(mouseX, mouseY, upX, btnY, BUTTON, BUTTON);
        boolean overDown = MediaWindow.inRect(mouseX, mouseY, downButtonX(), btnY, BUTTON, BUTTON);
        boolean overRemove = MediaWindow.inRect(mouseX, mouseY, removeButtonX(), btnY, BUTTON, BUTTON);
        Glyphs.arrow(g, upX, btnY, true, canUp ? (overUp ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        Glyphs.arrow(g, downButtonX(), btnY, false, canDown ? (overDown ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        Glyphs.close(g, removeButtonX(), btnY, overRemove ? Theme.DANGER : Theme.ICON);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /**
     * Whether the cursor is over the panel — which is also what tells the window to
     * treat it as one of its own regions rather than as empty screen.
     */
    boolean contains(double mouseX, double mouseY) {
        return isOpen() && MediaWindow.inRect(mouseX, mouseY, x, y, width, height);
    }

    /**
     * Acts on a click inside the panel: the row's buttons first, then the row itself.
     */
    void click(double mouseX, double mouseY) {
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= queue.size()) {
                break;
            }
            int rowY = rowTop(i);
            if (mouseY < rowY || mouseY >= rowY + mode.rowHeight) {
                continue;
            }
            int btnY = rowY + (mode.rowHeight - BUTTON) / 2;
            if (MediaWindow.inRect(mouseX, mouseY, removeButtonX(), btnY, BUTTON, BUTTON)) {
                queue.remove(index);
            } else if (MediaWindow.inRect(mouseX, mouseY, downButtonX(), btnY, BUTTON, BUTTON)) {
                queue.moveDown(index);
            } else if (MediaWindow.inRect(mouseX, mouseY, upButtonX(), btnY, BUTTON, BUTTON)) {
                queue.moveUp(index);
            } else {
                onJump.accept(index);
            }
            return;
        }
    }

    /**
     * Scrolls the list one row per wheel notch. Returns {@code false} when the cursor is
     * elsewhere, so the window can fall back to what a wheel usually means (the volume).
     */
    boolean scroll(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        int maxScroll = Math.max(0, queue.size() - visibleRows);
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll);
        return true;
    }

    // ------------------------------------------------------------------
    // Row geometry
    // ------------------------------------------------------------------

    private int rowTop(int visibleRow) {
        return rowsTop + visibleRow * mode.rowHeight;
    }

    /**
     * Right edge available for row content (excludes the scrollbar gutter).
     */
    private int contentRight() {
        return x + width - (scrollable ? SCROLLBAR_W + 2 : 0);
    }

    private int removeButtonX() {
        return contentRight() - PAD - BUTTON;
    }

    private int downButtonX() {
        return removeButtonX() - BUTTON - 2;
    }

    private int upButtonX() {
        return downButtonX() - BUTTON - 2;
    }
}
