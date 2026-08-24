package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
 *
 * <p>The chrome around that content — the softened box, the title bar carrying the
 * media's name and the corner buttons, the 1 px edge that marks the front window, the
 * control bar strip and the open animation — is drawn here and only here, so a new
 * window type inherits the whole look by implementing {@link #drawContent}.</p>
 */
abstract class MediaWindow {
    protected static final int PADDING = 3;
    protected static final int BUTTON = 11;
    /**
     * Height of the title bar above the content, for the windows that have one.
     */
    protected static final int TITLE_BAR = 12;
    /**
     * How long a window takes to settle into place when it appears.
     */
    private static final int OPEN_MS = 160;
    /**
     * How long the mark left by a click stays on screen.
     */
    private static final int FLASH_MS = 220;
    /**
     * How much smaller a window starts before it grows into its real size.
     */
    private static final double OPEN_SCALE = 0.92;
    /**
     * Size of the square resize grip in the bottom-right corner.
     */
    protected static final int GRIP = 8;
    /**
     * Smallest the scaled content is allowed to get, in pixels.
     */
    protected static final int MIN_CONTENT = 48;
    private static final double MAX_SCALE = 6.0;

    enum ClickResult {NONE, HANDLED, CLOSE}

    /**
     * Monotonic counter handing out unique IDs to every window.
     */
    private static long idSeq;

    /**
     * This window's unique ID for API control.
     */
    private final long id = ++idSeq;

    public final long getId() {
        return id;
    }

    /**
     * Monotonic counter handing out stacking order to every window (image or video).
     */
    private static long zSeq;

    /**
     * This window's place in the global stacking order; higher draws on top.
     */
    private long zOrder;

    protected boolean visible = true;

    /**
     * When this window appeared, driving the open animation (see {@link Anim}).
     */
    private final long openedAt = Anim.now();

    // Where the last click landed and when, for the flash that reports it.
    private int flashX, flashY;
    private long flashAt;

    MediaWindow() {
        bringToFront();
    }

    /**
     * Raises this window above all others in the shared image/video stack.
     */
    final void bringToFront() {
        zOrder = ++zSeq;
    }

    /**
     * Stacking order; windows are drawn from lowest to highest, so highest is on top.
     */
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
    /**
     * {@link #TITLE_BAR} or {@code 0}, resolved once per layout so every derived
     * coordinate agrees with what {@link #render} draws.
     */
    private int titleBarH;
    /**
     * Right edge available to the title text: the leftmost corner button.
     */
    private int titleTextRight;

    // Manual placement / sizing: once the user drags or resizes the window it
    // stops auto-anchoring and uses these values instead.
    private boolean userPlaced;
    private int userX, userY;
    private boolean userSized;
    private double userScale;
    private double lastScale = 1.0; // effective scale used by the last layout
    private boolean initialPositionApplied;

    // Active drag gestures.
    private boolean draggingMove;
    private boolean draggingResize;
    private int grabDX, grabDY;

    // ------------------------------------------------------------------
    // Subclass contract
    // ------------------------------------------------------------------

    /**
     * Intrinsic content width in pixels (e.g. the decoded video/image width).
     */
    protected abstract int sourceWidth();

    /**
     * Intrinsic content height in pixels.
     */
    protected abstract int sourceHeight();

    /**
     * Default (un-resized) scale that fits the content nicely on screen.
     */
    protected abstract double computeAutoScale(int srcW, int srcH, int screenWidth, int screenHeight);

    /**
     * The source URL, opened in the browser by the link button.
     */
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

    /**
     * Sets {@link #boxX}/{@link #boxY} for the default (un-moved) placement.
     */
    protected abstract void computeAnchor(int screenWidth, int screenHeight, int slot);

    /**
     * Draws the picture itself into the content rect.
     */
    protected abstract void drawContent(GuiGraphics g, Font font);

    /**
     * Extra vertical space reserved below the content for a control bar.
     */
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

    /**
     * Whether a "hide this window" button is shown next to the close button.
     */
    protected boolean hasHideButton() {
        return false;
    }

    /**
     * Whether the window carries a title bar above its content.
     *
     * <p>On by default: the corner buttons used to float <em>over</em> the top-right of
     * the picture, which put three opaque squares on the part of an image or a video
     * most likely to matter. A strip of its own gives them somewhere to live and gives
     * the window room to say what it is playing.</p>
     *
     * <p>A window whose content already <em>is</em> a title row — the audio bar — turns
     * it off rather than showing the same name twice.</p>
     */
    protected boolean hasTitleBar() {
        return true;
    }

    /**
     * The name shown in the title bar. Defaults to the media's resolved title, which
     * {@link com.lia.mediaplayer.media.MediaTitleCache} already keeps for the queue
     * panels — so a YouTube window is named by its video, and a direct link by its file.
     */
    protected String windowTitle() {
        return com.lia.mediaplayer.media.MediaTitleCache.getOrLoad(mediaUrl());
    }

    /**
     * Lays out the subclass' control bar using the current content rect.
     */
    protected void layoutControls(Font font) {
    }

    /**
     * Renders the subclass' control bar.
     */
    protected void renderControls(GuiGraphics g, Font font, int mouseX, int mouseY) {
    }

    /**
     * Lets the subclass consume a click on its controls before move/resize.
     */
    protected ClickResult onControlClick(double mouseX, double mouseY) {
        return ClickResult.NONE;
    }

    /**
     * Lets the subclass consume a drag on its controls (seek / volume).
     */
    protected boolean onControlDrag(double mouseX, double mouseY) {
        return false;
    }

    /**
     * Lets the subclass finish a control drag on mouse-up.
     */
    protected boolean onControlRelease() {
        return false;
    }

    /**
     * Plain (no modifier) wheel over the window; subclass decides what it does.
     */
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

    /**
     * Extra hover area outside the box that still counts as "ours" (e.g. a popup).
     */
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

    /**
     * Computes the window geometry for this frame and stores the hit regions.
     */
    final void layout(int screenWidth, int screenHeight, int slot) {
        int srcW = Math.max(1, sourceWidth());
        int srcH = Math.max(1, sourceHeight());

        double scale = userSized ? userScale : computeAutoScale(srcW, srcH, screenWidth, screenHeight);
        titleBarH = hasTitleBar() ? TITLE_BAR : 0;

        // Cap the content size so the whole box (with its title bar, control bar and
        // padding) always fits on screen — otherwise a tall image or an over-sized
        // resize pushes the bottom-right grip off-screen where it can't be grabbed
        // again.
        int minContentW = minContentWidth();
        int chromeH = titleBarH + controlBarHeight() + PADDING * 2;
        int maxContentW = Math.max(minContentW, maxContentWidth(screenWidth));
        int maxContentH = Math.max(MIN_CONTENT, screenHeight - chromeH - 2);
        // Width that keeps the (aspect-locked) height within maxContentH.
        int widthCapFromHeight = Math.max(minContentW, (int) Math.floor(maxContentH * (double) srcW / srcH));
        int widthCap = Math.min(maxContentW, widthCapFromHeight);

        int settledW = Mth.clamp((int) Math.round(srcW * scale), minContentW, widthCap);
        // The scale the window *is* at, recorded before the opening animation scales it
        // down: a wheel-zoom in the first frames must start from the real size, not from
        // the momentary one.
        lastScale = settledW / (double) srcW;

        contentW = Math.max(MIN_CONTENT / 2, (int) Math.round(settledW * openScale()));
        contentH = Math.max(1, (int) Math.round(contentW * (double) srcH / srcW));

        boxW = contentW + PADDING * 2;
        boxH = contentH + titleBarH + controlBarHeight() + PADDING * 2;

        if (!userPlaced && !initialPositionApplied) {
            // Placed from the size the window is *settling* into, not the smaller one it
            // is drawn at on this first frame: the corner positions pin userX/userY once
            // and never recompute them, so animating them would leave the window parked
            // a few pixels short of the corner it was asked for.
            int settledBoxH = Math.max(1, (int) Math.round(settledW * (double) srcH / srcW))
                    + titleBarH + controlBarHeight() + PADDING * 2;
            applyInitialPosition(screenWidth, screenHeight, settledW + PADDING * 2, settledBoxH);
            initialPositionApplied = true;
        }

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
        contentY = boxY + titleBarH + PADDING;

        // Buttons at the right end of the title bar (right to left: close, then hide
        // if present, then the open-in-browser link). A window without a title bar
        // keeps them where they have always been, over the top-right of the content.
        if (titleBarH > 0) {
            closeBtnX = boxX + boxW - PADDING - BUTTON;
            closeBtnY = boxY + (titleBarH - BUTTON) / 2;
        } else {
            closeBtnX = contentX + contentW - BUTTON - 1;
            closeBtnY = contentY + 1;
        }
        int next = closeBtnX;
        if (hasHideButton()) {
            hideBtnX = next - BUTTON - 2;
            hideBtnY = closeBtnY;
            next = hideBtnX;
        }
        linkBtnX = next - BUTTON - 2;
        linkBtnY = closeBtnY;
        titleTextRight = linkBtnX - 3;

        gripX = boxX + boxW - GRIP;
        gripY = boxY + boxH - GRIP;

        layoutControls(Minecraft.getInstance().font);
    }

    private void applyInitialPosition(int screenWidth, int screenHeight, int settledBoxW, int settledBoxH) {
        WindowPosition position = ((MediaPlayerContext) LiasMediaPlayerApi.getInstance()).getConfigStore().defaultWindowPosition();
        if (position == WindowPosition.CENTER) {
            // Leave userPlaced as false to allow default cascading behavior
            return;
        }
        userPlaced = true;
        switch (position) {
            case TOP_LEFT -> {
                userX = PADDING;
                userY = PADDING;
            }
            case TOP_RIGHT -> {
                userX = screenWidth - settledBoxW - PADDING;
                userY = PADDING;
            }
            case BOTTOM_LEFT -> {
                userX = PADDING;
                userY = screenHeight - settledBoxH - PADDING;
            }
            case BOTTOM_RIGHT -> {
                userX = screenWidth - settledBoxW - PADDING;
                userY = screenHeight - settledBoxH - PADDING;
            }
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * How far through its opening animation this window is, eased.
     */
    private double openEase() {
        return Anim.easeOut(Anim.progress(openedAt, OPEN_MS));
    }

    /**
     * The factor the content is scaled by right now: the window grows from
     * {@link #OPEN_SCALE} to its real size as it opens.
     */
    private double openScale() {
        return OPEN_SCALE + (1.0 - OPEN_SCALE) * openEase();
    }

    /**
     * Draws the window.
     *
     * @param withControls {@code false} for the in-world HUD overlay, which has no
     *                     cursor and shows just the picture
     * @param focused      whether this is the front window of the stack; only the front
     *                     one gets the bright edge
     */
    final void render(GuiGraphics g, int mouseX, int mouseY, boolean withControls, boolean focused) {
        Font font = Minecraft.getInstance().font;
        double fade = openEase();
        boolean controls = withControls || alwaysShowControls();

        Panels.fill(g, boxX, boxY, boxX + boxW, boxY + boxH, Theme.withAlpha(Theme.WINDOW_BG, fade));
        if (titleBarH > 0) {
            renderTitleBar(g, font, fade);
        }
        if (controls && controlBarHeight() > 0) {
            int barTop = contentY + contentH;
            Panels.fillBottom(g, boxX, barTop, boxX + boxW, boxY + boxH,
                    Theme.withAlpha(Theme.CONTROL_BAR_BG, fade));
        }

        drawContent(g, font);

        // The edge goes over the content, so a picture that fills its rect still ends
        // on a clean outline rather than bleeding into the window behind it. Only a
        // screen that routes clicks to the stack gets the bright "this one is in front"
        // edge — on the bare HUD nothing can be clicked, so there is no front to mark.
        boolean marked = focused && withControls;
        Panels.border(g, boxX, boxY, boxX + boxW, boxY + boxH,
                Theme.withAlpha(marked ? Theme.BORDER_FOCUSED : Theme.BORDER, fade));

        if (!controls) {
            return; // HUD overlay: no cursor, so nothing below would be readable.
        }

        renderControls(g, font, mouseX, mouseY);
        renderCornerButtons(g, mouseX, mouseY);
        renderGrip(g, mouseX, mouseY);
        renderClickFlash(g);
    }

    /**
     * The strip above the content: the media's name on the left, the corner buttons on
     * the right (drawn by {@link #renderCornerButtons}).
     */
    private void renderTitleBar(GuiGraphics g, Font font, double fade) {
        Panels.fillTop(g, boxX, boxY, boxX + boxW, boxY + titleBarH,
                Theme.withAlpha(Theme.TITLE_BAR_BG, fade));
        int textX = boxX + 4;
        int maxW = titleTextRight - textX;
        if (maxW < 8) {
            return; // too narrow to say anything; the buttons win
        }
        // Vanilla's font renderer reads a near-zero alpha as "fully opaque", so the
        // first frame of the fade would show the title at full strength. See
        // NowPlayingBanner, which has the same floor for the same reason.
        if (fade * 255 < 8) {
            return;
        }
        g.drawString(font, Component.literal(Glyphs.fit(font, windowTitle(), maxW)),
                textX, boxY + (titleBarH - font.lineHeight) / 2 + 1,
                Theme.withAlpha(Theme.TEXT_SUBTLE, fade));
    }

    private void renderCornerButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean overLink = inRect(mouseX, mouseY, linkBtnX, linkBtnY, BUTTON, BUTTON);
        drawButtonBackdrop(g, linkBtnX, linkBtnY);
        Glyphs.externalLink(g, linkBtnX, linkBtnY, overLink ? Theme.ICON_HOVER : Theme.ICON);
        if (overLink) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.open_browser"));
        }

        boolean overClose = inRect(mouseX, mouseY, closeBtnX, closeBtnY, BUTTON, BUTTON);
        drawButtonBackdrop(g, closeBtnX, closeBtnY);
        Glyphs.close(g, closeBtnX, closeBtnY, overClose ? Theme.DANGER : Theme.ICON);
        if (overClose) {
            Tooltips.request(Component.translatable("gui.liasmediaplayer.control.close"));
        }

        if (hasHideButton()) {
            boolean overHide = inRect(mouseX, mouseY, hideBtnX, hideBtnY, BUTTON, BUTTON);
            drawButtonBackdrop(g, hideBtnX, hideBtnY);
            Glyphs.minimize(g, hideBtnX, hideBtnY, overHide ? Theme.ICON_HOVER : Theme.ICON);
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
    private void drawButtonBackdrop(GuiGraphics g, int x, int y) {
        if (titleBarH == 0) {
            g.fill(x, y, x + BUTTON, y + BUTTON, Theme.CORNER_BUTTON_BG);
        }
    }

    /**
     * A small diagonal grip in the bottom-right corner, highlighted on hover.
     */
    private void renderGrip(GuiGraphics g, int mouseX, int mouseY) {
        boolean active = inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP) || draggingResize;
        // The cursor cannot be swapped for a resize arrow — that is a GLFW window-level
        // call with no vanilla seam behind it, and a stuck cursor outlives the window —
        // so the affordance is drawn instead: the grip lights up and gains a backdrop.
        if (active) {
            g.fill(gripX, gripY, gripX + GRIP, gripY + GRIP, Theme.CORNER_BUTTON_BG);
        }
        int color = active ? Theme.ICON_HOVER : Theme.ICON;
        for (int i = 1; i <= 3; i++) {
            int o = i * 2;
            g.fill(gripX + GRIP - o, gripY + GRIP - 1, gripX + GRIP, gripY + GRIP, color);
            g.fill(gripX + GRIP - 1, gripY + GRIP - o, gripX + GRIP, gripY + GRIP, color);
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
     */
    private void renderClickFlash(GuiGraphics g) {
        double t = Anim.progress(flashAt, FLASH_MS);
        if (t >= 1.0) {
            return;
        }
        double eased = Anim.easeOut(t);
        int half = (int) Math.round(3 + 6 * eased);
        Panels.fill(g, flashX - half, flashY - half, flashX + half, flashY + half,
                Theme.withAlpha(Theme.PRESS_FLASH, 1.0 - eased));
    }

    // ------------------------------------------------------------------
    // Input (return value tells the caller whether the event was consumed)
    // ------------------------------------------------------------------

    final ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        ClickResult result = routeClick(mouseX, mouseY, button);
        if (result != ClickResult.NONE) {
            flashX = (int) Math.round(mouseX);
            flashY = (int) Math.round(mouseY);
            flashAt = Anim.now();
        }
        return result;
    }

    private ClickResult routeClick(double mouseX, double mouseY, int button) {
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
            // Same outline a close leaves: from the screen's point of view the window
            // went away, and it should go away the same way either time.
            MediaWindowOverlay.noteClosed(boxX, boxY, boxW, boxH);
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
        if (Keys.controlDown()) {
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

    /**
     * Wheel zoom around the current size ({@code steps} = wheel notches).
     */
    protected final void zoom(double steps) {
        pinPosition();
        userSized = true;
        double minScale = minContentWidth() / (double) Math.max(1, sourceWidth());
        userScale = Mth.clamp(lastScale * (1.0 + 0.1 * steps), minScale, MAX_SCALE);
    }

    /**
     * Raises the "now playing" banner for {@code url} when this window is hidden.
     *
     * <p>A hidden player keeps playing — that is what the hide button means — so a
     * queue moving on to its next track is otherwise completely silent about what
     * started. A visible window needs no banner: its title bar (or, for the audio bar,
     * its content row) already carries the name.</p>
     *
     * <p>The URL is handed over as-is: the banner resolves the title itself, so a name
     * still being fetched when playback starts lands on the banner instead of leaving
     * it announcing the placeholder.</p>
     */
    protected final void announceIfHidden(String url) {
        if (!visible) {
            NowPlayingBanner.show(url);
        }
    }

    /**
     * Closes this window and leaves a fading outline of it behind.
     *
     * <p>The window itself cannot fade out: closing disposes its player and its
     * texture, and keeping either alive to be looked at for another fifth of a second
     * would mean a window that has been closed still decoding and still holding an
     * audio line. What is left instead is the shape it occupied — enough for the eye to
     * follow what disappeared, with nothing behind it.</p>
     */
    final void closeWithFade() {
        MediaWindowOverlay.noteClosed(boxX, boxY, boxW, boxH);
        close();
    }

    /**
     * Opens the media's source URL in the system browser.
     */
    private void openLink() {
        String url = mediaUrl();
        // openUri hands the string to the OS handler (xdg-open / FileProtocolHandler /
        // open), which happily launches whatever protocol is registered for it. The URL
        // originates from a chat component, so only ever pass on a real http(s) link.
        if (com.lia.mediaplayer.source.Urls.isHttp(url)) {
            Util.getPlatform().openUri(url);
        }
    }

    /**
     * Freezes the current auto-anchored position so move/resize don't make it jump.
     */
    private void pinPosition() {
        if (!userPlaced) {
            userPlaced = true;
            userX = boxX;
            userY = boxY;
        }
    }

    /**
     * The tooltips for the controls both player windows share, so the two never drift
     * apart on what a button claims to do. Each is looked up fresh from the current
     * state: a tooltip that named the button rather than its effect ("loop") would say
     * nothing the glyph does not already say.
     */
    protected static Component playTooltip(boolean playing) {
        return Component.translatable(playing
                ? "gui.liasmediaplayer.control.pause"
                : "gui.liasmediaplayer.control.play");
    }

    protected static Component loopTooltip(RepeatMode mode) {
        return Component.translatable(switch (mode) {
            case OFF -> "gui.liasmediaplayer.control.loop.off";
            case ALL -> "gui.liasmediaplayer.control.loop.all";
            case ONE -> "gui.liasmediaplayer.control.loop.one";
        });
    }

    protected static Component shuffleTooltip(boolean on) {
        return Component.translatable(on
                ? "gui.liasmediaplayer.control.shuffle.on"
                : "gui.liasmediaplayer.control.shuffle.off");
    }

    protected static Component volumeTooltip(boolean muted) {
        return Component.translatable(muted
                ? "gui.liasmediaplayer.control.unmute"
                : "gui.liasmediaplayer.control.mute");
    }

    /**
     * Colour for a toggle button (loop, shuffle) in each of its four states, so both
     * player windows draw their toggles the same way.
     */
    protected static int toggleColor(boolean active, boolean hovered) {
        if (active) {
            return hovered ? Theme.ICON_HOVER : Theme.ICON_ACTIVE;
        }
        return hovered ? Theme.ICON_HOVER : Theme.ICON_INACTIVE;
    }

    static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
