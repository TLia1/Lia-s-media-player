package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaQueue;
import com.lia.mediaplayer.api.MediaRequest;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.event.PlaybackEvents;
import com.lia.mediaplayer.api.window.WindowAction;
import com.lia.mediaplayer.api.window.WindowChromeOptions;
import com.lia.mediaplayer.media.AudioGain;
import com.lia.mediaplayer.media.MediaTitleCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
 * control bar strip and the open animation — is the same for every window type, so a new
 * one inherits the whole look by implementing {@link #drawContent}.</p>
 *
 * <p>What this class does itself is <b>coordinate</b>: it asks the subclass what it is
 * showing, asks each of four collaborators to do their part with it, and publishes the
 * resulting rectangles for the subclass to draw into. The four are worth knowing by
 * name, because a change usually belongs in one of them rather than here:</p>
 * <ul>
 *   <li>{@link WindowPlacement} — where the window is and how big, and the arithmetic
 *       that decides it;</li>
 *   <li>{@link WindowGestures} — the mouse state between events: which drag is running,
 *       whether two clicks were a double-click, how long the cursor has been still;</li>
 *   <li>{@link WindowChrome} (with {@link WindowButtons}) — everything drawn that is not
 *       the picture;</li>
 *   <li>{@link WindowLinkActions} — what the corner buttons do with the link: keep it,
 *       open it, copy it.</li>
 * </ul>
 *
 * <p>Three of the four need no Minecraft to run, which is the point: the layout maths,
 * the double-click rule and the button row are now reachable from a unit test, and each
 * of them is the sort of thing whose failure mode is a window nobody can drag back.</p>
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

    /** Where this window is and how big — see {@link WindowPlacement}. */
    private final WindowPlacement placement = new WindowPlacement();

    /** What the mouse is doing to it — see {@link WindowGestures}. */
    private final WindowGestures gestures = new WindowGestures();

    /** What its corner buttons do with the link — see {@link WindowLinkActions}. */
    private final WindowLinkActions actions = new WindowLinkActions();

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

    // ------------------------------------------------------------------
    // The last layout's result (full-screen coordinates)
    //
    // These are what the subclass draws into and what the mouse handlers test against,
    // so they are the window's own rather than the placement's: they describe the frame
    // that was drawn, while WindowPlacement holds the intent they were computed from.
    // ------------------------------------------------------------------

    protected int boxX, boxY, boxW, boxH;
    protected int contentX, contentY, contentW, contentH;
    /** The corner button row; see {@link WindowButtons}. */
    private WindowButtons buttons = WindowButtons.layout(0, 0, false);
    /**
     * The addon buttons the row was laid out for, resolved once per layout — see
     * {@code api.window.WindowAction}. Held rather than re-asked at draw and at click,
     * because those two have to agree about what is under the cursor, and an action's
     * {@code appliesTo} may answer differently between them.
     */
    private List<WindowAction> windowActions = List.of();

    /**
     * Whether the local user has been held off this window's transport — see
     * {@code api.sync.SyncControl.setLocked}.
     *
     * <p>It governs <em>hands</em>, not the player: the control bar, the seek bar and
     * the keyboard shortcuts decline, while {@code SyncControl.apply} and an addon
     * holding a {@code MediaHandle} still drive it. That is what "the host controls this"
     * actually means, and it is why the check is here and in the two input paths rather
     * than inside the transport methods, which the API goes through too.</p>
     *
     * <p>Closing and hiding stay allowed whatever this says. A viewer who cannot get out
     * of a video files that as a crash.</p>
     */
    private boolean locked;

    /** What to say while {@link #locked}; {@code null} for the generic message. */
    @Nullable
    private Component lockReason;
    private int gripX, gripY;
    /**
     * {@link #TITLE_BAR} or {@code 0}, resolved once per layout so every derived
     * coordinate agrees with what {@link #render} draws.
     */
    private int titleBarH;

    /** Set every frame from {@code render}'s {@code withControls}; see {@link #isInteractive()}. */
    private boolean interactive;

    /**
     * Whether the state loaded from {@code windows.json} has been applied yet. Read on
     * the first layout rather than in the constructor, so a window built before the
     * mod's context exists still finds the store.
     */
    private boolean restoredState;

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
     * What this window is showing, for the history entry every play records, for the
     * API's handles and for the playback events.
     */
    protected abstract MediaKind mediaKind();

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
        return Math.max(MIN_CONTENT, WindowButtons.width(showsHideButton(), chrome.closeButton(),
                windowActions.size()));
    }

    /** {@link #controlBarHeight()}, or nothing at all when the chrome has no controls. */
    private int effectiveControlBarHeight() {
        return chrome.controls() ? controlBarHeight() : 0;
    }

    /** Whether this window both wants a hide button and is allowed one. */
    protected final boolean showsHideButton() {
        return hasHideButton() && chrome.hideButton();
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
     * {@link MediaTitleCache} already keeps for the queue
     * panels — so a YouTube window is named by its video, and a direct link by its file.
     */
    protected String windowTitle() {
        if (titleOverride != null) {
            return titleOverride.getString();
        }
        return MediaPlayerContext.get().getTitleCache().getOrLoad(mediaUrl());
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

    /** Whether the user's own transport is being held off — see {@link #locked}. */
    final boolean isLocked() {
        return locked;
    }

    /** Called by {@code MediaSyncControl}; nothing else has any business locking a window. */
    final void setLocked(boolean value, @Nullable Component reason) {
        this.locked = value;
        this.lockReason = value ? reason : null;
    }

    /** What the window says while it is locked. */
    private Component lockMessage() {
        return lockReason != null
                ? lockReason
                : Component.translatable("gui.liasmediaplayer.locked");
    }

    boolean togglePlayPause() {
        return false;
    }

    /** Resumes, if paused. Part of the transport contract for the API's handles. */
    boolean play() {
        return false;
    }

    boolean pause() {
        return false;
    }

    boolean seekTo(long micros) {
        return false;
    }

    boolean seekToFraction(double fraction) {
        return false;
    }

    /**
     * Seeks {@code deltaMicros} from the current position, clamped into the track.
     */
    boolean seekBy(long deltaMicros) {
        return false;
    }

    /**
     * Converges on {@code targetMicros} rather than jumping to it — the window half of
     * {@code api.sync.SyncControl.driftCorrect}. A window with no player answers
     * {@code false}, like the rest of the transport contract.
     */
    boolean driftCorrect(long targetMicros, long toleranceMicros) {
        return false;
    }

    /**
     * Where playback has got to, in microseconds, or {@code -1} for a window with no
     * clock behind it (a pinned image).
     *
     * <p>Part of the transport contract because it is asked for the same reason the rest
     * of it is: something outside the window — here the copy button — needs one answer
     * that both players give in the same terms.</p>
     */
    long positionMicros() {
        return -1;
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
     * Total length of what is playing, or {@code -1} for a window with no clock behind
     * it. The companion of {@link #positionMicros()}, and asked for the same reason.
     */
    long durationMicros() {
        return -1;
    }

    /**
     * Where playback stands, in the API's terms. A pinned image has no player, and is
     * simply {@link PlaybackState#PLAYING} for as long as it is on screen.
     */
    PlaybackState playbackState() {
        return PlaybackState.PLAYING;
    }

    /**
     * This window's queue as the API sees it, or {@code null} for a window that has
     * none. Only the player windows do; a pinned image is one picture.
     */
    @Nullable
    MediaQueue queueHandle() {
        return null;
    }

    /**
     * This window's own share of the mix, or {@code null} for a window that makes no
     * sound. Only the player windows do; a pinned image is one picture.
     *
     * <p>Held by the window rather than by the player because it has to outlive one: a
     * queue advancing swaps the player out, and a gain, a channel or a placement an addon
     * set must survive that.</p>
     */
    @Nullable
    AudioGain audioControls() {
        return null;
    }

    // ------------------------------------------------------------------
    // The API's view of this window
    //
    // A window is what a MediaHandle points at, so the handle is created here rather
    // than by whoever asks for one: the events posted below carry it, and two callers
    // asking twice must get the same object back or a listener added to one of them
    // would never fire.
    // ------------------------------------------------------------------

    /** Set to false exactly once, by {@link #markDisposed()}. */
    private boolean alive = true;

    // ------------------------------------------------------------------
    // What a MediaRequest asked for
    //
    // All four default to what the mod's own windows do, so a window nobody made a
    // request for behaves exactly as it always has.
    // ------------------------------------------------------------------

    /** Which parts of the furniture this window has — see {@link WindowChromeOptions}. */
    private WindowChromeOptions chrome = WindowChromeOptions.full();

    /** Whether this window retires itself once its queue has run out. */
    private boolean closeWhenEnded = true;

    /**
     * Whether this window's geometry is written back to {@code windows.json}.
     *
     * <p>Off for a window the API opened, and that default is the important half of the
     * feature rather than a detail: the store is keyed by window <em>kind</em>, so an
     * addon parking a player in a corner would otherwise overwrite where the user likes
     * their own video window to open — every session, invisibly.</p>
     */
    private boolean persistGeometry = true;

    /** A title the caller supplied, in place of the one the mod resolves. */
    private Component titleOverride;

    /**
     * Applies everything in {@code request} that is the window's business. Called by the
     * manager between building the window and its first layout, so nothing here is ever
     * seen changing.
     */
    final void applyRequest(MediaRequest request) {
        chrome = request.chrome();
        closeWhenEnded = request.isCloseWhenEnded();
        persistGeometry = request.isPersistGeometry();
        titleOverride = request.title();
        placement.request(request.placement(), request.sizing());
    }

    final WindowChromeOptions chrome() {
        return chrome;
    }

    final void setChrome(WindowChromeOptions value) {
        chrome = value == null ? WindowChromeOptions.full() : value;
    }

    /**
     * Whether this window retires itself once there is nothing left to play — see
     * {@code MediaRequest.closeWhenEnded}.
     */
    final boolean closesWhenEnded() {
        return closeWhenEnded;
    }

    final boolean persistsGeometry() {
        return persistGeometry;
    }

    final void setPersistGeometry(boolean value) {
        persistGeometry = value;
    }

    /** The window's geometry, for {@code MediaWindowHandle}. */
    final WindowPlacement placement() {
        return placement;
    }

    /** Whether the control bar is drawn and laid out at all. */
    protected final boolean controlsEnabled() {
        return chrome.controls();
    }

    private WindowHandle handle;

    /**
     * This window as the public API sees it. Created on first use — most windows are
     * opened from chat and nobody ever asks — and then kept, because it carries the
     * per-handle listeners.
     */
    final WindowHandle handle() {
        if (handle == null) {
            handle = new WindowHandle(this);
        }
        return handle;
    }

    /**
     * Whether this window still exists. False once its manager has let go of it —
     * closed, evicted past the window cap, or dropped on disconnect.
     */
    final boolean isAlive() {
        return alive;
    }

    /**
     * Announces that this window has gone for good, and posts {@link PlaybackEvent.Type#STOPPED}.
     *
     * <p>Called by the managers, from all three ways a window can go away, rather than
     * from {@link #close()}: two of the three (the eviction past the cap, the sweep on
     * disconnect) never go through {@code close()} at all, and those are exactly the
     * cases where an addon holding a handle is otherwise left calling into nothing.</p>
     */
    final void markDisposed() {
        if (!alive) {
            return;
        }
        postPlaybackEvent(PlaybackEvent.Type.STOPPED);
        alive = false;
        // Nothing else will ever fire on this handle, and an addon that forgot to
        // unregister must not keep the window's listeners alive for the session.
        handle().clearListeners();
    }

    /**
     * Builds a {@link PlaybackEvent} describing this window right now and dispatches it
     * to this handle's own listeners and to the global {@link PlaybackEvents}.
     */
    final void postPlaybackEvent(PlaybackEvent.Type type) {
        WindowHandle target = handle();
        PlaybackEvent event = new PlaybackEvent(type, playerKind(), mediaUrl(),
                playbackState(), Math.max(0, positionMicros()), Math.max(0, durationMicros()),
                target);
        target.dispatch(event);
        PlaybackEvents.post(event);
        // The two things derived from the same transitions, and derived here for the same
        // reason: pause, resume and seek are reachable from four places and this is the
        // one they all end up at.
        MediaSyncControl.broadcast(getId(), event.getUrl(), type, event.getPositionMicros());
        if (type == PlaybackEvent.Type.FAILED) {
            PlaybackFailures.report(mediaUrl(), mediaKind(), errorText());
        }
    }

    /**
     * The raw diagnostic behind a {@link PlaybackEvent.Type#FAILED}, or {@code null} for
     * a window with no player to have failed. Overridden by the player windows, which are
     * the only ones that can fail.
     */
    @Nullable
    String errorText() {
        return null;
    }

    private PlaybackEvent.PlayerKind playerKind() {
        return switch (mediaKind()) {
            case VIDEO -> PlaybackEvent.PlayerKind.VIDEO;
            case AUDIO -> PlaybackEvent.PlayerKind.AUDIO;
            case IMAGE -> PlaybackEvent.PlayerKind.IMAGE;
        };
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
        placement.applyPendingWidth(srcW, sourceSizeKnown());

        titleBarH = hasTitleBar() && chrome.titleBar() ? TITLE_BAR : 0;
        WindowPlacement.Size size = placement.solve(
                srcW, srcH, screenWidth, screenHeight,
                computeAutoScale(srcW, srcH, screenWidth, screenHeight),
                titleBarH, effectiveControlBarHeight(),
                minContentWidth(), maxContentWidth(screenWidth),
                openScale());
        contentW = size.contentW();
        contentH = size.contentH();
        boxW = size.boxW();
        boxH = size.boxH();

        if (placement.needsInitialPosition()) {
            placement.applyInitialPosition(
                    MediaPlayerContext.get().getConfigStore().defaultWindowPosition(),
                    screenWidth, screenHeight, size.settledBoxW(), size.settledBoxH());
        }

        if (placement.isTheater()) {
            boxX = Math.max(0, (screenWidth - boxW) / 2);
            boxY = Math.max(0, (screenHeight - boxH) / 2);
        } else if (placement.hasRequestedPosition()) {
            // Re-resolved every pass rather than pinned once: that is what lets a
            // relative or anchored placement mean the same thing after the window is
            // resized or the GUI scale changes.
            boxX = placement.requestedX(screenWidth, boxW);
            boxY = placement.requestedY(screenHeight, boxH);
        } else if (placement.isPlaced()) {
            boxX = placement.clampedX(screenWidth, boxW);
            boxY = placement.clampedY(screenHeight, boxH);
        } else {
            computeAnchor(screenWidth, screenHeight, slot);
        }

        // Let a subclass tighten the position after placement (e.g. keep room beside
        // the player for an attached panel so it can't be covered).
        constrainPosition(screenWidth, screenHeight);

        contentX = boxX + PADDING;
        contentY = boxY + titleBarH + PADDING;

        // The corner buttons live at the right end of the title bar. A window without
        // one keeps them where they have always been, over the top-right of the content.
        windowActions = chrome.controls() ? WindowActions.applicable(handle()) : List.of();
        buttons = titleBarH > 0
                ? WindowButtons.layout(boxX + boxW - PADDING, boxY + (titleBarH - BUTTON) / 2,
                        showsHideButton(), chrome.closeButton(), windowActions.size())
                : WindowButtons.layout(contentX + contentW - 1, contentY + 1,
                        showsHideButton(), chrome.closeButton(), windowActions.size());

        gripX = boxX + boxW - GRIP;
        gripY = boxY + boxH - GRIP;

        layoutControls(Minecraft.getInstance().font);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static WindowStateStore stateStore() {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
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
        // The remembered spot belongs to one window. A second player of the same kind
        // takes it only if the first is not already sitting there — otherwise the two
        // would land exactly on top of each other, and the cascade in computeAnchor
        // exists precisely to stop that.
        placement.restore(store.get(stateKey()), MediaWindowOverlay.isSoleWindowOfKind(this));
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
        if (!restoredState || gestures.isDragging() || placement.isTheater() || !persistGeometry) {
            return null;
        }
        return decorateState(new WindowStateStore.State(
                placement.isPlaced(), placement.x(), placement.y(),
                // The width the window settles at, not the animated `contentW` of this
                // frame: recording the opening animation's momentary size would save a
                // box a little smaller than the one on screen.
                placement.isSized(), placement.storedWidth(sourceWidth()),
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
        gestures.noteCursor(mouseX, mouseY);
        boolean controls = (withControls || alwaysShowControls()) && chromeShown() && chrome.controls();

        Panels.fill(g, boxX, boxY, boxX + boxW, boxY + boxH, Theme.withAlpha(Theme.WINDOW_BG, fade));
        if (titleBarH > 0 && chromeShown()) {
            WindowChrome.titleBar(g, font, boxX, boxY, boxW, titleBarH,
                    titleTextRight(), windowTitle(), fade);
        }
        if (controls && effectiveControlBarHeight() > 0) {
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
        if (locked && inRect(mouseX, mouseY, boxX, boxY, boxW, boxH)) {
            // First, so a hovered button's own tooltip still wins — Tooltips keeps the
            // last request of the frame. This is the fallback the rest of the window says.
            Tooltips.request(lockMessage());
        }
        renderControls(g, font, mouseX, mouseY);
        String url = mediaUrl();
        WindowChrome.cornerButtons(g, mouseX, mouseY, buttons, titleBarH > 0,
                actions.isFavorite(url), () -> actions.copyTooltip(url, positionMicros()),
                windowActions);
        if (!placement.isTheater() && chrome.resizable()) {
            // Nothing to resize while the screen is the size.
            WindowChrome.grip(g, gripX, gripY,
                    inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP) || gestures.isResizing());
        }
        WindowChrome.clickFlash(g, gestures.flashX(), gestures.flashY(), gestures.flashProgress());
    }

    /**
     * Where the cursor was when this frame started, for the parts of a window that are
     * drawn by {@link #drawContent} — which is handed no mouse position, having had
     * nothing to hover over until the failure panel arrived. {@link Integer#MIN_VALUE}
     * on the HUD, where there is no cursor at all.
     */
    protected final int cursorX() {
        return gestures.cursorX();
    }

    protected final int cursorY() {
        return gestures.cursorY();
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
        return !placement.isTheater() || gestures.idleMillis() < THEATER_IDLE_MS;
    }

    // ------------------------------------------------------------------
    // Input (return value tells the caller whether the event was consumed)
    // ------------------------------------------------------------------

    final ClickResult mouseClicked(double mouseX, double mouseY, int button) {
        // Read before the cursor note below, because pressing a button is itself what
        // wakes hidden theatre chrome: a click aimed at a control nobody can see must
        // bring the controls back rather than press the control blindly.
        boolean chromeWasShown = chromeShown();
        gestures.noteCursor((int) Math.round(mouseX), (int) Math.round(mouseY));
        ClickResult result = routeClick(mouseX, mouseY, button, chromeWasShown);
        if (result != ClickResult.NONE) {
            gestures.flash(mouseX, mouseY);
        }
        return result;
    }

    private ClickResult routeClick(double mouseX, double mouseY, int button, boolean chromeWasShown) {
        if (button != 0 || !visible) {
            return ClickResult.NONE;
        }
        if (chromeWasShown) {
            if (buttons.overClose(mouseX, mouseY)) {
                return ClickResult.CLOSE;
            }
            if (buttons.overLink(mouseX, mouseY)) {
                actions.openInBrowser(mediaUrl());
                return ClickResult.HANDLED;
            }
            if (buttons.overCopy(mouseX, mouseY)) {
                actions.copyLink(mediaUrl(), positionMicros(), Keys.shiftDown());
                return ClickResult.HANDLED;
            }
            if (buttons.overFavorite(mouseX, mouseY)) {
                actions.toggleFavorite(mediaUrl());
                return ClickResult.HANDLED;
            }
            int actionIndex = buttons.actionAt(mouseX, mouseY);
            if (actionIndex >= 0 && actionIndex < windowActions.size()) {
                WindowActions.click(windowActions.get(actionIndex), handle());
                return ClickResult.HANDLED;
            }
            if (buttons.overHide(mouseX, mouseY)) {
                visible = false;
                // Same outline a close leaves: from the screen's point of view the window
                // went away, and it should go away the same way either time.
                MediaWindowOverlay.noteClosed(boxX, boxY, boxW, boxH);
                return ClickResult.HANDLED;
            }
            if (!placement.isTheater() && chrome.resizable()
                    && inRect(mouseX, mouseY, gripX, gripY, GRIP, GRIP)) {
                beginResize();
                return ClickResult.HANDLED;
            }
            // Everything above this line is still allowed while locked — closing, hiding,
            // copying the link, moving and resizing the window. Only the transport below
            // is held off.
            if (!locked) {
                ClickResult sub = onControlClick(mouseX, mouseY);
                if (sub != ClickResult.NONE) {
                    return sub;
                }
            }
        }
        // A second click on the picture enlarges it to fill the screen, and a third
        // puts it back — checked before the move grab below, which the first click of
        // the pair has already harmlessly started and released.
        if (gestures.isDoubleClick(mouseX, mouseY) && supportsTheater()
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

    final boolean mouseDragged(double mouseX, double mouseY) {
        if (gestures.isResizing()) {
            // The cursor sets the right edge of the content.
            placement.resizeTo((int) Math.round(mouseX) - boxX - PADDING,
                    minContentWidth(), sourceWidth());
            return true;
        }
        if (gestures.isMoving()) {
            int x = gestures.moveToX(mouseX);
            int y = gestures.moveToY(mouseY);
            // Shift is the "no, I meant exactly there" modifier, the same escape hatch
            // every drawing program gives its grid.
            if (!Keys.shiftDown()) {
                x = Snap.axis(x, boxW, MediaWindowOverlay.snapGuidesX(this), Snap.THRESHOLD);
                y = Snap.axis(y, boxH, MediaWindowOverlay.snapGuidesY(this), Snap.THRESHOLD);
            }
            placement.moveTo(x, y);
            return true;
        }
        return onControlDrag(mouseX, mouseY);
    }

    final boolean mouseReleased() {
        boolean handled = gestures.release();
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
    // Move / resize
    // ------------------------------------------------------------------

    private void beginMove(double mouseX, double mouseY) {
        if (placement.isTheater() || !chrome.movable()) {
            return; // the window is the screen; there is nowhere to move it to
        }
        placement.pin(boxX, boxY);
        gestures.beginMove(mouseX, mouseY, boxX, boxY);
    }

    private void beginResize() {
        placement.pin(boxX, boxY);
        placement.beginResize();
        gestures.beginResize();
    }

    /**
     * Wheel zoom around the current size ({@code steps} = wheel notches).
     */
    protected final void zoom(double steps) {
        if (placement.isTheater() || !chrome.resizable()) {
            return; // the size is the screen's; a zoom here would only be felt on the way out
        }
        placement.pin(boxX, boxY);
        placement.zoom(steps, minContentWidth(), sourceWidth());
    }

    // ------------------------------------------------------------------
    // Theatre mode
    // ------------------------------------------------------------------

    /**
     * Whether this window currently fills the screen.
     */
    final boolean isTheater() {
        return placement.isTheater();
    }

    /**
     * Swaps between the window's own size and filling the screen, putting the exact
     * geometry back on the way out.
     *
     * <p>Nothing about the layout is recomputed here: {@link WindowPlacement#solve}
     * already branches on the flag, so a toggle is this bookkeeping plus one frame.</p>
     *
     * @return {@code true} when the window has a picture to enlarge and the mode changed
     */
    final boolean toggleTheater() {
        if (!supportsTheater()) {
            return false;
        }
        boolean entering = !placement.isTheater();
        placement.toggleTheater();
        if (entering) {
            onEnterTheater();
        }
        // The chrome is on when the mode changes either way, so the controls that just
        // moved are visible where they landed rather than already timed out.
        gestures.wake();
        return true;
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
        return buttons.leftEdge() - 3;
    }

    static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
