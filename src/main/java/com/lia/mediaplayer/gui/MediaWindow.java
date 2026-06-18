package com.lia.mediaplayer.gui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Base class for the on-screen media windows (a {@link VideoWindow} or an
 * {@link ImageWindow}). It owns everything that is common to both: the box
 * geometry, the close/hide corner buttons, and — the reason this class exists —
 * the ability to <em>move</em> the window (drag its body) and <em>resize</em> it
 * (drag the bottom-right grip, or {@code Ctrl}+mouse-wheel to zoom).
 *
 * <p>Subclasses describe their intrinsic content (its source size, how to draw
 * it, its default placement and auto-fit scale) and may add their own control
 * bar below the content. Layout is recomputed every frame and the hit regions
 * are cached so the mouse handlers (which fire between renders) can test against
 * the last drawn position.</p>
 */
abstract class MediaWindow {
    protected static final int PADDING = 3;
    protected static final int BUTTON = 11;
    /** Size of the square resize grip in the bottom-right corner. */
    protected static final int GRIP = 8;
    /** Smallest the scaled content is allowed to get, in pixels. */
    protected static final int MIN_CONTENT = 48;
    private static final double MAX_SCALE = 6.0;

    protected static final int BG_COLOR = 0xD0101010;
    protected static final int BAR_COLOR = 0xF0181818;
    protected static final int TRACK_COLOR = 0xFF4A4A4A;
    protected static final int FILL_COLOR = 0xFF4CA6FF;
    protected static final int KNOB_COLOR = 0xFFFFFFFF;
    protected static final int TEXT_COLOR = 0xFFFFFFFF;
    protected static final int BTN_COLOR = 0xFFE0E0E0;
    protected static final int BTN_HOVER = 0xFFFFD23F;
    protected static final int PLACEHOLDER = 0xFF000000;

    enum ClickResult {NONE, HANDLED, CLOSE}

    /** Monotonic counter handing out stacking order to every window (image or video). */
    private static long zSeq;

    /** This window's place in the global stacking order; higher draws on top. */
    private long zOrder;

    protected boolean visible = true;

    MediaWindow() {
        bringToFront();
    }

    /** Raises this window above all others in the shared image/video stack. */
    final void bringToFront() {
        zOrder = ++zSeq;
    }

    /** Stacking order; windows are drawn from lowest to highest, so highest is on top. */
    final long zOrder() {
        return zOrder;
    }

    // Cached geometry from the last layout (full-screen coordinates).
    protected int boxX, boxY, boxW, boxH;
    protected int contentX, contentY, contentW, contentH;
    protected int closeBtnX, closeBtnY;
    protected int hideBtnX, hideBtnY;
    private int linkBtnX, linkBtnY;
    private int gripX, gripY;

    // Manual placement / sizing: once the user drags or resizes the window it
    // stops auto-anchoring and uses these values instead.
    private boolean userPlaced;
    private int userX, userY;
    private boolean userSized;
    private double userScale;
    private double lastScale = 1.0; // effective scale used by the last layout

    // Active drag gestures.
    private boolean draggingMove;
    private boolean draggingResize;
    private int grabDX, grabDY;

    // ------------------------------------------------------------------
    // Subclass contract
    // ------------------------------------------------------------------

    /** Intrinsic content width in pixels (e.g. the decoded video/image width). */
    protected abstract int sourceWidth();

    /** Intrinsic content height in pixels. */
    protected abstract int sourceHeight();

    /** Default (un-resized) scale that fits the content nicely on screen. */
    protected abstract double computeAutoScale(int srcW, int srcH, int screenWidth, int screenHeight);

    /** The source URL, opened in the browser by the link button. */
    protected abstract String mediaUrl();

    /**
     * Removes this window from whatever registry owns it and releases its resources.
     * Called when the close button is clicked. Each subclass forwards to its own
     * manager, so {@link MediaWindowOverlay} never needs to know the concrete window
     * type to close it.
     */
    protected abstract void close();

    /**
     * Cascade group for the default (un-moved) placement: windows sharing a group fan
     * out so they don't land exactly on top of each other. Images and videos use
     * different groups so each kind cascades independently.
     */
    protected abstract int anchorGroup();

    /** Sets {@link #boxX}/{@link #boxY} for the default (un-moved) placement. */
    protected abstract void computeAnchor(int screenWidth, int screenHeight, int slot);

    /** Draws the picture itself into the content rect. */
    protected abstract void drawContent(GuiGraphics g, Font font);

    /** Extra vertical space reserved below the content for a control bar. */
    protected int controlBarHeight() {
        return 0;
    }

    /**
     * Smallest the scaled content is allowed to get, in pixels of width. Defaults to
     * {@link #MIN_CONTENT}; subclasses with a fixed-width control bar (e.g. the video
     * player) raise this so the window can't shrink small enough for its controls to
     * spill outside the box.
     */
    protected int minContentWidth() {
        return MIN_CONTENT;
    }

    /**
     * Largest the scaled content is allowed to get, in pixels of width. Defaults to
     * "as wide as the screen allows"; subclasses can shrink this — e.g. the video
     * player reserves room for its side queue panel so the two never overlap.
     */
    protected int maxContentWidth(int screenWidth) {
        return screenWidth - PADDING * 2 - 2;
    }

    /**
     * Hook to adjust {@link #boxX}/{@link #boxY} after the default placement and the
     * on-screen clamp, but before the content/button geometry is derived. The default
     * does nothing.
     */
    protected void constrainPosition(int screenWidth, int screenHeight) {
    }

    /** Whether a hide ("_") button is shown next to the close button. */
    protected boolean hasHideButton() {
        return false;
    }

    /** Lays out the subclass' control bar using the current content rect. */
    protected void layoutControls(Font font) {
    }

    /** Renders the subclass' control bar. */
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
    }

    /** Lets the subclass consume a click on its controls before move/resize. */
    protected ClickResult onControlClick(double mouseX, double mouseY) {
        return ClickResult.NONE;
    }

    /** Lets the subclass consume a drag on its controls (seek / volume). */
    protected boolean onControlDrag(double mouseX, double mouseY) {
        return false;
    }

    /** Lets the subclass finish a control drag on mouse-up. */
    protected boolean onControlRelease() {
        return false;
    }

    /** Plain (no modifier) wheel over the window; subclass decides what it does. */
    protected boolean onControlScroll(double mouseX, double mouseY, double scrollY) {
        return false;
    }

    /**
     * Whether this window's controls should be drawn even on the HUD (when no chat
     * screen is open). Audio bars override this so their seek bar and transport buttons
     * stay visible while playing.
     */
    protected boolean alwaysShowControls() {
        return false;
    }

    /** Extra hover area outside the box that still counts as "ours" (e.g. a popup). */
    protected boolean overPopup(double mouseX, double mouseY) {
        return false;
    }

    /**
     * A further interactive area outside the box (e.g. an attached panel) that should
     * capture scroll input, but — unlike {@link #overPopup} — must not be treated as
     * the volume pop-up region.
     */
    protected boolean overExtraRegion(double mouseX, double mouseY) {
        return false;
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    boolean isVisible() {
        return visible;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
    }

    boolean containsMouse(double mouseX, double mouseY) {
        return mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= boxY && mouseY <= boxY + boxH;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Computes the window geometry for this frame and stores the hit regions. */
    final void layout(int screenWidth, int screenHeight, int slot) {
        int srcW = Math.max(1, sourceWidth());
        int srcH = Math.max(1, sourceHeight());

        double scale = userSized ? userScale : computeAutoScale(srcW, srcH, screenWidth, screenHeight);

        // Cap the content size so the whole box (with its control bar / padding)
        // always fits on screen — otherwise a tall image or an over-sized resize
        // pushes the bottom-right grip off-screen where it can't be grabbed again.
        int minContentW = minContentWidth();
        int chromeH = controlBarHeight() + PADDING * 2;
        int maxContentW = Math.max(minContentW, maxContentWidth(screenWidth));
        int maxContentH = Math.max(MIN_CONTENT, screenHeight - chromeH - 2);
        // Width that keeps the (aspect-locked) height within maxContentH.
        int widthCapFromHeight = Math.max(minContentW, (int) Math.floor(maxContentH * (double) srcW / srcH));
        int widthCap = Math.min(maxContentW, widthCapFromHeight);

        contentW = Mth.clamp((int) Math.round(srcW * scale), minContentW, widthCap);
        contentH = Math.max(1, (int) Math.round(contentW * (double) srcH / srcW));
        lastScale = contentW / (double) srcW;

        boxW = contentW + PADDING * 2;
        boxH = contentH + controlBarHeight() + PADDING * 2;

        if (userPlaced) {
            boxX = Mth.clamp(userX, 2, Math.max(2, screenWidth - boxW - 2));
            boxY = Mth.clamp(userY, 2, Math.max(2, screenHeight - boxH - 2));
        } else {
            computeAnchor(screenWidth, screenHeight, slot);
        }

        // Let a subclass tighten the position after placement (e.g. keep room beside
        // the player for an attached panel so it can't be covered).
        constrainPosition(screenWidth, screenHeight);

        contentX = boxX + PADDING;
        contentY = boxY + PADDING;

        // Corner buttons over the top-right of the content (right to left:
        // close, then hide if present, then the open-in-browser link).
        closeBtnX = contentX + contentW - BUTTON - 1;
        closeBtnY = contentY + 1;
        int next = closeBtnX;
        if (hasHideButton()) {
            hideBtnX = next - BUTTON - 2;
            hideBtnY = closeBtnY;
            next = hideBtnX;
        }
        linkBtnX = next - BUTTON - 2;
        linkBtnY = closeBtnY;

        gripX = boxX + boxW - GRIP;
        gripY = boxY + boxH - GRIP;

        layoutControls(Minecraft.getInstance().font);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Draws the window. {@code withControls} is false for the in-world HUD overlay. */
    final void render(GuiGraphics g, int mouseX, int mouseY, boolean withControls) {
        Font font = Minecraft.getInstance().font;

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, BG_COLOR);
        drawContent(g, font);

        if (!withControls && !alwaysShowControls()) {
            return; // HUD overlay shows just the picture.
        }

        renderControls(g, font, mouseX, mouseY);
        renderCornerButtons(g, font, mouseX, mouseY);
        renderGrip(g, mouseX, mouseY);
    }

    private void renderCornerButtons(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean overLink = inRect(mouseX, mouseY, linkBtnX, linkBtnY, BUTTON, BUTTON);
        g.fill(linkBtnX, linkBtnY, linkBtnX + BUTTON, linkBtnY + BUTTON, 0x80000000);
        drawLinkIcon(g, linkBtnX, linkBtnY, overLink ? BTN_HOVER : BTN_COLOR);

        boolean overClose = inRect(mouseX, mouseY, closeBtnX, closeBtnY, BUTTON, BUTTON);
        g.fill(closeBtnX, closeBtnY, closeBtnX + BUTTON, closeBtnY + BUTTON, 0x80000000);
        g.drawString(font, Component.literal("x"), closeBtnX + 3, closeBtnY + 2, overClose ? BTN_HOVER : BTN_COLOR);
        if (hasHideButton()) {
            boolean overHide = inRect(mouseX, mouseY, hideBtnX, hideBtnY, BUTTON, BUTTON);
            g.fill(hideBtnX, hideBtnY, hideBtnX + BUTTON, hideBtnY + BUTTON, 0x80000000);
            g.drawString(font, Component.literal("_"), hideBtnX + 3, hideBtnY + 1, overHide ? BTN_HOVER : BTN_COLOR);
        }
    }

    /** A small "open in browser" arrow (up-and-to-the-right). */
    private void drawLinkIcon(GuiGraphics g, int x, int y, int color) {
        // Diagonal shaft from the bottom-left to the top-right.
        for (int i = 0; i < 6; i++) {
            g.fill(x + 2 + i, y + 8 - i, x + 3 + i, y + 9 - i, color);
        }
        // Arrow head at the top-right corner.
        g.fill(x + 5, y + 2, x + 9, y + 3, color);
        g.fill(x + 8, y + 2, x + 9, y + 6, color);
    }

    /** A small diagonal grip in the bottom-right corner, highlighted on hover. */
    private void renderGrip(GuiGraphics g, int mouseX, int mouseY) {
        int color = inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP) || draggingResize ? BTN_HOVER : BTN_COLOR;
        for (int i = 1; i <= 3; i++) {
            int o = i * 2;
            g.fill(gripX + GRIP - o, gripY + GRIP - 1, gripX + GRIP, gripY + GRIP, color);
            g.fill(gripX + GRIP - 1, gripY + GRIP - o, gripX + GRIP, gripY + GRIP, color);
        }
    }

    // ------------------------------------------------------------------
    // Input (return value tells the caller whether the event was consumed)
    // ------------------------------------------------------------------

    final ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !visible) {
            return ClickResult.NONE;
        }
        if (inRect(mouseX, mouseY, closeBtnX, closeBtnY, BUTTON, BUTTON)) {
            return ClickResult.CLOSE;
        }
        if (inRect(mouseX, mouseY, linkBtnX, linkBtnY, BUTTON, BUTTON)) {
            openLink();
            return ClickResult.HANDLED;
        }
        if (hasHideButton() && inRect(mouseX, mouseY, hideBtnX, hideBtnY, BUTTON, BUTTON)) {
            visible = false;
            return ClickResult.HANDLED;
        }
        if (inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP)) {
            beginResize();
            return ClickResult.HANDLED;
        }
        ClickResult sub = onControlClick(mouseX, mouseY);
        if (sub != ClickResult.NONE) {
            return sub;
        }
        // Anywhere else inside the window grabs it for moving (and, either way,
        // swallows the click so it does not fall through to the chat behind it).
        if (containsMouse(mouseX, mouseY)) {
            beginMove(mouseX, mouseY);
            return ClickResult.HANDLED;
        }
        return ClickResult.NONE;
    }

    final boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingResize) {
            applyResize(mouseX);
            return true;
        }
        if (draggingMove) {
            userX = (int) Math.round(mouseX) - grabDX;
            userY = (int) Math.round(mouseY) - grabDY;
            return true;
        }
        return onControlDrag(mouseX, mouseY);
    }

    final boolean mouseReleased() {
        boolean handled = false;
        if (draggingResize) {
            draggingResize = false;
            handled = true;
        }
        if (draggingMove) {
            draggingMove = false;
            handled = true;
        }
        if (onControlRelease()) {
            handled = true;
        }
        return handled;
    }

    final boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!visible || scrollY == 0
                || !(containsMouse(mouseX, mouseY) || overPopup(mouseX, mouseY) || overExtraRegion(mouseX, mouseY))) {
            return false;
        }
        if (Screen.hasControlDown()) {
            zoom(scrollY);
            return true;
        }
        return onControlScroll(mouseX, mouseY, scrollY);
    }

    // ------------------------------------------------------------------
    // Move / resize helpers
    // ------------------------------------------------------------------

    private void beginMove(double mouseX, double mouseY) {
        pinPosition();
        draggingMove = true;
        grabDX = (int) Math.round(mouseX) - boxX;
        grabDY = (int) Math.round(mouseY) - boxY;
    }

    private void beginResize() {
        pinPosition();
        draggingResize = true;
        userSized = true;
    }

    private void applyResize(double mouseX) {
        int newW = (int) Math.round(mouseX) - boxX - PADDING;
        double minScale = minContentWidth() / (double) Math.max(1, sourceWidth());
        userScale = Mth.clamp(newW / (double) Math.max(1, sourceWidth()), minScale, MAX_SCALE);
    }

    /** Wheel zoom around the current size ({@code steps} = wheel notches). */
    protected final void zoom(double steps) {
        pinPosition();
        userSized = true;
        double minScale = minContentWidth() / (double) Math.max(1, sourceWidth());
        userScale = Mth.clamp(lastScale * (1.0 + 0.1 * steps), minScale, MAX_SCALE);
    }

    /** Opens the media's source URL in the system browser. */
    private void openLink() {
        String url = mediaUrl();
        if (url != null && !url.isEmpty()) {
            Util.getPlatform().openUri(url);
        }
    }

    /** Freezes the current auto-anchored position so move/resize don't make it jump. */
    private void pinPosition() {
        if (!userPlaced) {
            userPlaced = true;
            userX = boxX;
            userY = boxY;
        }
    }

    protected static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
