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
     * How long two clicks may be apart and still count as a double-click.
     */
    private static final int DOUBLE_CLICK_MS = 300;
    /**
     * How far the second click of a double-click may land from the first.
     */
    private static final int DOUBLE_CLICK_SLOP = 4;
    /**
     * How long the cursor has to sit still before theatre mode drops its chrome.
     */
    private static final int THEATER_IDLE_MS = 2000;
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
    private int favBtnX, favBtnY;
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

    // Theatre mode: the window fills the screen and the geometry it had before is put
    // aside so leaving puts it back exactly where it was.
    private boolean theater;
    private boolean savedPlaced, savedSized;
    private int savedX, savedY;
    private double savedScale;

    // Where the cursor last was and when it last moved, which is what tells theatre
    // mode whether anyone is still looking for the controls.
    private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;
    private long lastMouseMoveAt = Anim.now();

    /** Set every frame from {@code render}'s {@code withControls}; see {@link #isInteractive()}. */
    private boolean interactive;

    // The previous click, for spotting a double-click on the picture.
    private long lastClickAt;
    private int lastClickX, lastClickY;

    /**
     * Whether the state loaded from {@code windows.json} has been applied yet. Read on
     * the first layout rather than in the constructor, so a window built before the
     * mod's context exists still finds the store.
     */
    private boolean restoredState;

    /**
     * A restored content width still waiting for a real source size to be turned into
     * a scale; {@code 0} when there is nothing pending. See {@link #applyPendingWidth}.
     */
    private int pendingWidth;

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
     * Whether {@link #sourceWidth()} / {@link #sourceHeight()} report the content's real
     * size yet, rather than the placeholder a window shows before its media has loaded.
     * Only {@link #applyPendingWidth} cares, and only on the first frames of a window
     * whose size is being restored.
     */
    protected boolean sourceSizeKnown() {
        return true;
    }

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
     * {@link #MIN_CONTENT} or the room the corner buttons need, whichever is larger;
     * subclasses with a fixed-width control bar (e.g. the video player) raise this
     * further so the window can't shrink small enough for its controls to spill outside
     * the box — and should keep {@code super}'s figure as their own floor.
     */
    protected int minContentWidth() {
        return Math.max(MIN_CONTENT, cornerButtonsWidth());
    }

    /**
     * How wide the row of corner buttons is: close, the browser link and the favourite
     * heart, plus the hide button on the windows that have one.
     *
     * <p>It is part of the minimum because they are laid out right-to-left from the
     * window's right edge and nothing stops them running past its left one: a window
     * narrower than its own buttons draws them over each other and over whatever is to
     * its left.</p>
     */
    private int cornerButtonsWidth() {
        int buttons = 3 + (hasHideButton() ? 1 : 0);
        return buttons * (BUTTON + 2) + 4;
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
     * Whether this frame is being drawn on a screen that routes clicks to the window
     * stack, rather than on the bare HUD.
     *
     * <p>Only one thing needs it, and only because {@link #alwaysShowControls()} exists:
     * the audio bar keeps its transport row on the HUD, but a queue panel is a
     * two-hundred-pixel list of rows nobody out there can click, and it has no business
     * being drawn over the world.</p>
     */
    protected final boolean isInteractive() {
        return interactive;
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
    // Transport contract
    //
    // What a keyboard shortcut (see WindowShortcuts) can ask of a window. Every method
    // returns whether it did something, which is also the answer to "was the key
    // consumed?" — a pinned image answers no to all of them and the key falls through.
    // Both player windows already own these actions for their control bars; this is
    // the same set, reachable without the mouse.
    // ------------------------------------------------------------------

    /**
     * Whether this window has a player behind it, and so anything to play, pause or
     * seek. {@link WindowShortcuts} uses it to pick which window a transport key means.
     */
    boolean hasTransport() {
        return false;
    }

    boolean togglePlayPause() {
        return false;
    }

    /**
     * Seeks {@code deltaMicros} from the current position, clamped into the track.
     */
    boolean seekBy(long deltaMicros) {
        return false;
    }

    boolean playNext() {
        return false;
    }

    boolean playPrevious() {
        return false;
    }

    boolean cycleRepeat() {
        return false;
    }

    boolean toggleShuffle() {
        return false;
    }

    /**
     * Whether this window has a picture worth filling the screen with. The audio bar
     * says no: it is already exactly as big as its content needs to be.
     */
    boolean supportsTheater() {
        return true;
    }

    /**
     * Called as theatre mode is entered, for a subclass to fold away anything docked
     * beside the window that would fight with it for the screen.
     */
    protected void onEnterTheater() {
    }

    // ------------------------------------------------------------------
    // Persistence contract
    // ------------------------------------------------------------------

    /**
     * Which entry of {@code windows.json} this window reads and writes — one of
     * {@link WindowStateStore#IMAGE}, {@link WindowStateStore#VIDEO} or
     * {@link WindowStateStore#AUDIO}. State is shared by every window of a kind, so a
     * second video player opens where the first one was left.
     */
    protected abstract String stateKey();

    /**
     * Adds this window's own state to the geometry {@link #captureState} collects.
     * Only the player windows have anything to add (their queue panel and loop modes).
     */
    protected WindowStateStore.State decorateState(WindowStateStore.State geometry) {
        return geometry;
    }

    /**
     * Applies the parts of a restored state this window understands. Called once, on
     * the first layout, after the geometry has been put back.
     */
    protected void applyRestoredState(WindowStateStore.State state) {
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

        restoreStateOnce();
        applyPendingWidth();

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

        // Theatre mode is exactly the cap that was just computed: `widthCap` already
        // knows about the chrome, the aspect ratio and the screen, so "as big as fits"
        // needs no arithmetic of its own — and unlike going through MAX_SCALE, it fills
        // the screen for a small source too.
        int settledW = theater ? widthCap
                : Mth.clamp((int) Math.round(srcW * scale), minContentW, widthCap);
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

        if (theater) {
            boxX = Math.max(0, (screenWidth - boxW) / 2);
            boxY = Math.max(0, (screenHeight - boxH) / 2);
        } else if (userPlaced) {
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
        favBtnX = linkBtnX - BUTTON - 2;
        favBtnY = closeBtnY;
        titleTextRight = favBtnX - 3;

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
    // Persistence
    // ------------------------------------------------------------------

    private static WindowStateStore stateStore() {
        MediaPlayerContext context = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        return context == null ? null : context.getWindowStateStore();
    }

    /**
     * Puts back where this kind of window was left, on the first layout only.
     *
     * <p>Position, loop mode and the queue panel are put back straight away; the size
     * waits for {@link #applyPendingWidth}, which needs a real source size to work
     * against.</p>
     */
    private void restoreStateOnce() {
        if (restoredState) {
            return;
        }
        restoredState = true;
        WindowStateStore store = stateStore();
        if (store == null) {
            return;
        }
        WindowStateStore.State state = store.get(stateKey());
        // The remembered spot belongs to one window. A second player of the same kind
        // takes it only if the first is not already sitting there — otherwise the two
        // would land exactly on top of each other, and the cascade in computeAnchor
        // exists precisely to stop that.
        if (state.placed() && MediaWindowOverlay.isSoleWindowOfKind(this)) {
            userPlaced = true;
            userX = state.x();
            userY = state.y();
            // The configured default position must not overwrite what was restored.
            initialPositionApplied = true;
        }
        if (state.sized() && state.width() > 0) {
            pendingWidth = state.width();
        }
        applyRestoredState(state);
    }

    /**
     * Turns a restored content width into the scale the window actually works in, once
     * there is a real source size to divide it by.
     *
     * <p>A video window exists before its player has decoded a single frame, and
     * {@link #sourceWidth()} reports a 320x180 placeholder until then. Converting the
     * width against that placeholder would restore a box several times too large the
     * moment the real resolution arrived, so the width waits here instead. A source
     * whose size never resolves — a video that fails to open — simply keeps its
     * auto-fit scale, which is the right answer for a window with nothing in it.</p>
     */
    private void applyPendingWidth() {
        if (pendingWidth <= 0 || !sourceSizeKnown()) {
            return;
        }
        userSized = true;
        userScale = pendingWidth / (double) Math.max(1, sourceWidth());
        pendingWidth = 0;
    }

    /**
     * This window's arrangement as the store would record it, or {@code null} when it
     * is in no state to be recorded. {@link MediaWindowOverlay#clientTick()} collects
     * these once a tick.
     *
     * <p>Nothing is offered mid-gesture: a drag would produce a new position every
     * tick, turning one placement into twenty file writes. The tick after the mouse
     * comes up records where it landed. Theatre mode is skipped for the reason it is a
     * mode at all — it is somewhere a window goes, not somewhere it lives, and the
     * geometry worth remembering is the one waiting to be restored.</p>
     *
     * <p>Nor before the window has been laid out once, which is where the stored state
     * is read back: a window that has never been positioned would otherwise report
     * "never placed, never sized" and overwrite the very entry it is about to restore
     * from. That is not a race worth losing — a window opened hidden is never laid out
     * at all, so it would wipe the saved position every time.</p>
     */
    final WindowStateStore.State captureState() {
        if (!restoredState || draggingMove || draggingResize || theater) {
            return null;
        }
        // The width the window settles at, not the animated `contentW` of this frame:
        // recording the opening animation's momentary size would save a box a little
        // smaller than the one on screen.
        int width = userSized ? (int) Math.round(sourceWidth() * userScale) : 0;
        return decorateState(new WindowStateStore.State(
                userPlaced, userX, userY, userSized, width,
                false, RepeatMode.OFF, false));
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
        noteCursor(mouseX, mouseY);
        boolean controls = (withControls || alwaysShowControls()) && chromeShown();

        Panels.fill(g, boxX, boxY, boxX + boxW, boxY + boxH, Theme.withAlpha(Theme.WINDOW_BG, fade));
        if (titleBarH > 0 && chromeShown()) {
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

        interactive = withControls;
        renderControls(g, font, mouseX, mouseY);
        renderCornerButtons(g, mouseX, mouseY);
        if (!theater) {
            renderGrip(g, mouseX, mouseY); // nothing to resize while the screen is the size
        }
        renderClickFlash(g);
    }

    /**
     * Records where the cursor is, which is the whole of theatre mode's idle detection.
     *
     * <p>Done from {@code render} rather than from a move event because there is no
     * mouse-move hook: {@code ClientHooks} carries press, drag, release and scroll, and
     * the render hook is the one place the cursor position is reported on every version
     * and on both loaders. It fires once a frame, which is exactly the resolution
     * vanilla's own drag dispatch has.</p>
     */
    private void noteCursor(int mouseX, int mouseY) {
        if (mouseX < 0 && mouseY < 0) {
            return; // the HUD overlay draws with no cursor at all, not with a still one
        }
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            lastMouseMoveAt = Anim.now();
        }
    }

    /**
     * Whether the title bar, corner buttons, grip and control bar are drawn.
     *
     * <p>Always true outside theatre mode. In theatre they go after
     * {@link #THEATER_IDLE_MS} of a still cursor and come back the instant it moves —
     * the behaviour of every full-screen video player, and the reason theatre mode is
     * worth having at all.</p>
     *
     * <p>It is a clean cut rather than a fade on purpose. The chrome is drawn by a
     * dozen {@link Glyphs} calls spread over two subclasses, none of which take an
     * alpha; fading only the strips behind them would leave the glyphs floating at full
     * strength over a vanishing backdrop, which reads worse than either end state. The
     * layout is unchanged either way, so nothing moves when they return.</p>
     */
    private boolean chromeShown() {
        return !theater || Anim.now() - lastMouseMoveAt < THEATER_IDLE_MS;
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
        // The heart: what turns "this played once" into something the library keeps.
        // It is a window button rather than a history-screen one because the moment you
        // know you want to keep a track is while it is playing.
        boolean favorite = isFavorite();
        boolean overFav = inRect(mouseX, mouseY, favBtnX, favBtnY, BUTTON, BUTTON);
        drawButtonBackdrop(g, favBtnX, favBtnY);
        // Filled once it is kept, hollow while it is not — the same pair the history
        // screen draws, so the button means one thing in both places.
        if (favorite) {
            Glyphs.heart(g, favBtnX, favBtnY, overFav ? Theme.ICON_HOVER : Theme.DANGER);
        } else {
            Glyphs.heartOutline(g, favBtnX, favBtnY, overFav ? Theme.ICON_HOVER : Theme.ICON);
        }
        if (overFav) {
            Tooltips.request(Component.translatable(favorite
                    ? "gui.liasmediaplayer.control.unfavorite"
                    : "gui.liasmediaplayer.control.favorite"));
        }

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
        // Read before the cursor note below, because pressing a button is itself what
        // wakes hidden theatre chrome: a click aimed at a control nobody can see must
        // bring the controls back rather than press the control blindly.
        boolean chromeWasShown = chromeShown();
        noteCursor((int) Math.round(mouseX), (int) Math.round(mouseY));
        ClickResult result = routeClick(mouseX, mouseY, button, chromeWasShown);
        if (result != ClickResult.NONE) {
            flashX = (int) Math.round(mouseX);
            flashY = (int) Math.round(mouseY);
            flashAt = Anim.now();
        }
        return result;
    }

    private ClickResult routeClick(double mouseX, double mouseY, int button, boolean chromeWasShown) {
        if (button != 0 || !visible) {
            return ClickResult.NONE;
        }
        if (chromeWasShown) {
            if (inRect(mouseX, mouseY, closeBtnX, closeBtnY, BUTTON, BUTTON)) {
                return ClickResult.CLOSE;
            }
            if (inRect(mouseX, mouseY, linkBtnX, linkBtnY, BUTTON, BUTTON)) {
                openLink();
                return ClickResult.HANDLED;
            }
            if (inRect(mouseX, mouseY, favBtnX, favBtnY, BUTTON, BUTTON)) {
                toggleFavorite();
                return ClickResult.HANDLED;
            }
            if (hasHideButton() && inRect(mouseX, mouseY, hideBtnX, hideBtnY, BUTTON, BUTTON)) {
                visible = false;
                // Same outline a close leaves: from the screen's point of view the window
                // went away, and it should go away the same way either time.
                MediaWindowOverlay.noteClosed(boxX, boxY, boxW, boxH);
                return ClickResult.HANDLED;
            }
            if (!theater && inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP)) {
                beginResize();
                return ClickResult.HANDLED;
            }
            ClickResult sub = onControlClick(mouseX, mouseY);
            if (sub != ClickResult.NONE) {
                return sub;
            }
        }
        // A second click on the picture enlarges it to fill the screen, and a third
        // puts it back — checked before the move grab below, which the first click of
        // the pair has already harmlessly started and released.
        if (isDoubleClick(mouseX, mouseY) && supportsTheater()
                && inRect(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            toggleTheater();
            return ClickResult.HANDLED;
        }
        // Anywhere else inside the window grabs it for moving (and, either way,
        // swallows the click so it does not fall through to the chat behind it).
        if (containsMouse(mouseX, mouseY)) {
            beginMove(mouseX, mouseY);
            return ClickResult.HANDLED;
        }
        return ClickResult.NONE;
    }

    /**
     * Whether this click closes a double-click with the one before it: soon enough, and
     * near enough that it was aimed at the same thing rather than being two separate
     * clicks that happened to be quick.
     */
    private boolean isDoubleClick(double mouseX, double mouseY) {
        int x = (int) Math.round(mouseX);
        int y = (int) Math.round(mouseY);
        long at = Anim.now();
        boolean paired = at - lastClickAt <= DOUBLE_CLICK_MS
                && Math.abs(x - lastClickX) <= DOUBLE_CLICK_SLOP
                && Math.abs(y - lastClickY) <= DOUBLE_CLICK_SLOP;
        // Reset rather than extend, so three fast clicks are one pair and a stray
        // click, not two overlapping pairs.
        lastClickAt = paired ? 0 : at;
        lastClickX = x;
        lastClickY = y;
        return paired;
    }

    final boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingResize) {
            applyResize(mouseX);
            return true;
        }
        if (draggingMove) {
            int x = (int) Math.round(mouseX) - grabDX;
            int y = (int) Math.round(mouseY) - grabDY;
            // Shift is the "no, I meant exactly there" modifier, the same escape hatch
            // every drawing program gives its grid.
            if (!Keys.shiftDown()) {
                x = Snap.axis(x, boxW, MediaWindowOverlay.snapGuidesX(this), Snap.THRESHOLD);
                y = Snap.axis(y, boxH, MediaWindowOverlay.snapGuidesY(this), Snap.THRESHOLD);
            }
            userX = x;
            userY = y;
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
        if (theater) {
            return; // the window is the screen; there is nowhere to move it to
        }
        pinPosition();
        draggingMove = true;
        grabDX = (int) Math.round(mouseX) - boxX;
        grabDY = (int) Math.round(mouseY) - boxY;
    }

    // ------------------------------------------------------------------
    // Theatre mode
    // ------------------------------------------------------------------

    /**
     * Whether this window currently fills the screen.
     */
    final boolean isTheater() {
        return theater;
    }

    /**
     * Swaps between the window's own size and filling the screen, putting the exact
     * geometry back on the way out.
     *
     * <p>Nothing about the layout is recomputed here: {@link #layout} already branches
     * on the flag, so a toggle is this bookkeeping plus one frame.</p>
     *
     * @return {@code true} when the window has a picture to enlarge and the mode changed
     */
    final boolean toggleTheater() {
        if (!supportsTheater()) {
            return false;
        }
        if (theater) {
            theater = false;
            userPlaced = savedPlaced;
            userX = savedX;
            userY = savedY;
            userSized = savedSized;
            userScale = savedScale;
        } else {
            savedPlaced = userPlaced;
            savedX = userX;
            savedY = userY;
            savedSized = userSized;
            savedScale = userScale;
            theater = true;
            onEnterTheater();
        }
        // The chrome is on when the mode changes either way, so the controls that just
        // moved are visible where they landed rather than already timed out.
        lastMouseMoveAt = Anim.now();
        return true;
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
        if (theater) {
            return; // the size is the screen's; a zoom here would only be felt on the way out
        }
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
     * Right edge available to the title, i.e. the left edge of the corner buttons.
     * A window that draws its own name in the content row (the audio bar) stops it here
     * rather than counting the buttons itself.
     */
    protected final int titleTextRight() {
        return titleTextRight;
    }

    /**
     * Which player this window's media belongs to, for the history entry the heart
     * creates. Asked of the source registry rather than hard-coded per subclass, so an
     * addon's own source lands in the library under its own kind.
     */
    private com.lia.mediaplayer.api.MediaKind mediaKind() {
        MediaPlayerContext context = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        return context == null ? null : context.getMediaSources().kindOf(mediaUrl());
    }

    private boolean isFavorite() {
        MediaPlayerContext context = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        return context != null && context.getHistoryStore().isFavorite(mediaUrl());
    }

    private void toggleFavorite() {
        MediaPlayerContext context = (MediaPlayerContext) LiasMediaPlayerApi.getInstanceOrNull();
        if (context != null) {
            context.getHistoryStore().toggleFavorite(mediaUrl(), mediaKind());
        }
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

    /**
     * The two "jump {@value MediaControls#SKIP_MICROS} micros" buttons, drawn the same
     * way by both player windows: greyed out (and inert) when there is no duration to
     * jump within, which is what a live stream has.
     */
    protected final void renderSkipButton(GuiGraphics g, int x, int y, boolean forward,
                                          boolean seekable, int mouseX, int mouseY) {
        boolean over = inRect(mouseX, mouseY, x, y, BUTTON, BUTTON);
        Glyphs.seekStep(g, x, y, forward,
                seekable ? (over ? Theme.ICON_HOVER : Theme.ICON) : Theme.ICON_DISABLED);
        if (over && seekable) {
            Tooltips.request(skipTooltip(forward));
        }
    }

    protected static Component skipTooltip(boolean forward) {
        return Component.translatable(forward
                        ? "gui.liasmediaplayer.control.skip_forward"
                        : "gui.liasmediaplayer.control.skip_back",
                MediaControls.SKIP_MICROS / 1_000_000L);
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
