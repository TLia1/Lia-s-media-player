package com.lia.mediaplayer.gui;

import net.minecraft.util.Mth;

/**
 * Where a media window is and how big it is — the arithmetic, and the state the
 * arithmetic runs on.
 *
 * <p>Three things decide a window's size, and they interact: the scale it is at (its
 * auto-fit, or whatever a drag or a wheel-zoom left it at), the aspect ratio of what it
 * is showing, and the room the screen has left once the title bar, the control bar and
 * the padding are taken out. {@link #solve} is where they meet. It is the piece of
 * {@code MediaWindow} most worth having on its own: every bug it can have is a window
 * that ends up somewhere it cannot be dragged back from — a grip pushed off the bottom
 * of the screen, a box narrower than its own buttons, a restored size several times too
 * large — and none of those can be reproduced without the arithmetic being reachable
 * from a test.</p>
 *
 * <p>It holds no rectangle of its own. {@code MediaWindow} keeps the box and content
 * rects, because those are the result of the last frame's layout and are what the rest
 * of the window (and its subclasses) draw and hit-test against; this holds the
 * <em>intent</em> those are computed from, which outlives any one frame: has the player
 * moved this window, has the player resized it, and is it in theatre mode.</p>
 */
final class WindowPlacement {

    /** How far a window may be zoomed past its source size. */
    private static final double MAX_SCALE = 6.0;

    // Manual placement / sizing: once the user drags or resizes the window it stops
    // auto-anchoring and uses these values instead.
    private boolean userPlaced;
    private int userX, userY;
    private boolean userSized;
    private double userScale;
    /** Effective scale used by the last layout. */
    private double lastScale = 1.0;
    private boolean initialPositionApplied;

    // Theatre mode: the window fills the screen and the geometry it had before is put
    // aside so leaving puts it back exactly where it was.
    private boolean theater;
    private boolean savedPlaced, savedSized;
    private int savedX, savedY;
    private double savedScale;

    /**
     * A restored content width still waiting for a real source size to be turned into
     * a scale; {@code 0} when there is nothing pending. See {@link #applyPendingWidth}.
     */
    private int pendingWidth;

    /**
     * The sizes one layout pass produced.
     *
     * <p>{@code settled*} is the size the window is heading for; {@code content*} /
     * {@code box*} are what to draw this frame, which differ only while the opening
     * animation is still running. The initial placement uses the settled figures: the
     * corner positions are pinned once and never recomputed, so anchoring to the
     * momentary size would park the window a few pixels short of the corner it was
     * asked for.</p>
     */
    record Size(int contentW, int contentH, int boxW, int boxH, int settledBoxW, int settledBoxH) {
    }

    /**
     * Works out how big the window is this frame.
     *
     * <p>The content is capped so the <em>whole</em> box — title bar, control bar and
     * padding included — fits on screen. Without that, a tall image or an over-eager
     * resize pushes the bottom-right grip off the bottom, where it cannot be grabbed to
     * undo what just happened.</p>
     *
     * <p>Theatre mode is exactly that cap: it already knows about the chrome, the aspect
     * ratio and the screen, so "as big as fits" needs no arithmetic of its own — and,
     * unlike going through {@link #MAX_SCALE}, it fills the screen for a small source too.</p>
     *
     * @param autoScale  what the window would pick for itself; ignored once the player
     *                   has sized it by hand
     * @param openScale  the opening animation's factor, 1.0 once it has finished
     */
    Size solve(int srcW, int srcH, int screenW, int screenH, double autoScale,
               int titleBarH, int controlBarH, int minContentW, int maxContentW, double openScale) {
        srcW = Math.max(1, srcW);
        srcH = Math.max(1, srcH);
        double scale = userSized ? userScale : autoScale;

        int chromeH = titleBarH + controlBarH + MediaWindow.PADDING * 2;
        int capW = Math.max(minContentW, maxContentW);
        int capH = Math.max(MediaWindow.MIN_CONTENT, screenH - chromeH - 2);
        // Width that keeps the (aspect-locked) height within capH.
        int widthCapFromHeight = Math.max(minContentW, (int) Math.floor(capH * (double) srcW / srcH));
        int widthCap = Math.min(capW, widthCapFromHeight);

        int settledW = theater ? widthCap
                : Mth.clamp((int) Math.round(srcW * scale), minContentW, widthCap);
        // The scale the window *is* at, recorded before the opening animation scales it
        // down: a wheel-zoom in the first frames must start from the real size, not from
        // the momentary one.
        lastScale = settledW / (double) srcW;

        int contentW = Math.max(MediaWindow.MIN_CONTENT / 2, (int) Math.round(settledW * openScale));
        int contentH = Math.max(1, (int) Math.round(contentW * (double) srcH / srcW));
        int settledH = Math.max(1, (int) Math.round(settledW * (double) srcH / srcW));

        return new Size(contentW, contentH,
                contentW + MediaWindow.PADDING * 2, contentH + chromeH,
                settledW + MediaWindow.PADDING * 2, settledH + chromeH);
    }

    // ------------------------------------------------------------------
    // Position
    // ------------------------------------------------------------------

    boolean isPlaced() {
        return userPlaced;
    }

    int x() {
        return userX;
    }

    int y() {
        return userY;
    }

    void moveTo(int x, int y) {
        userX = x;
        userY = y;
    }

    /**
     * The user position clamped so the box stays on screen — with at least a couple of
     * pixels of it reachable on every side.
     */
    int clampedX(int screenW, int boxW) {
        return Mth.clamp(userX, 2, Math.max(2, screenW - boxW - 2));
    }

    int clampedY(int screenH, int boxH) {
        return Mth.clamp(userY, 2, Math.max(2, screenH - boxH - 2));
    }

    /**
     * Freezes the current auto-anchored position so move/resize don't make it jump.
     */
    void pin(int boxX, int boxY) {
        if (!userPlaced) {
            userPlaced = true;
            userX = boxX;
            userY = boxY;
        }
    }

    /**
     * Whether the configured default corner still has to be applied — true only on the
     * very first layout of a window nothing else has placed.
     */
    boolean needsInitialPosition() {
        return !userPlaced && !initialPositionApplied;
    }

    /**
     * Puts the window in the corner the settings ask for. {@link WindowPosition#CENTER}
     * is the absence of a choice: it leaves the window unplaced so the cascade in
     * {@code computeAnchor} fans several windows out instead of stacking them.
     */
    void applyInitialPosition(WindowPosition position, int screenW, int screenH,
                              int settledBoxW, int settledBoxH) {
        initialPositionApplied = true;
        if (position == WindowPosition.CENTER) {
            return;
        }
        userPlaced = true;
        int pad = MediaWindow.PADDING;
        switch (position) {
            case TOP_LEFT -> moveTo(pad, pad);
            case TOP_RIGHT -> moveTo(screenW - settledBoxW - pad, pad);
            case BOTTOM_LEFT -> moveTo(pad, screenH - settledBoxH - pad);
            case BOTTOM_RIGHT -> moveTo(screenW - settledBoxW - pad, screenH - settledBoxH - pad);
        }
    }

    // ------------------------------------------------------------------
    // Size
    // ------------------------------------------------------------------

    boolean isSized() {
        return userSized;
    }

    /**
     * The width to record in {@code windows.json}: a content width, not a scale, so a
     * window restores to the same size whatever it ends up showing. {@code 0} for a
     * window the player has never resized.
     */
    int storedWidth(int srcW) {
        return userSized ? (int) Math.round(Math.max(1, srcW) * userScale) : 0;
    }

    /**
     * Takes hold of the current size so a resize drag starts from where the window
     * actually is.
     *
     * <p>Needed because {@code userScale} is only meaningful once the window has been
     * sized by hand: turning {@code userSized} on while it is still the {@code 0.0} it
     * was born with would make the next layout clamp the window to its minimum width.
     * A press on the grip that never turns into a drag — a click, a mis-aim — has to
     * leave the window exactly as it was.</p>
     */
    void beginResize() {
        userSized = true;
        userScale = lastScale;
    }

    /**
     * Sizes the window from a resize drag: the cursor sets the right edge of the content.
     */
    void resizeTo(int contentW, int minContentW, int srcW) {
        userSized = true;
        userScale = clampScale(contentW / (double) Math.max(1, srcW), minContentW, srcW);
    }

    /**
     * Wheel zoom around the current size ({@code steps} = wheel notches).
     */
    void zoom(double steps, int minContentW, int srcW) {
        userSized = true;
        userScale = clampScale(lastScale * (1.0 + 0.1 * steps), minContentW, srcW);
    }

    private static double clampScale(double scale, int minContentW, int srcW) {
        double minScale = minContentW / (double) Math.max(1, srcW);
        return Mth.clamp(scale, minScale, MAX_SCALE);
    }

    // ------------------------------------------------------------------
    // Theatre mode
    // ------------------------------------------------------------------

    boolean isTheater() {
        return theater;
    }

    /**
     * Swaps between the window's own size and filling the screen, putting the exact
     * geometry back on the way out.
     *
     * <p>Nothing about the layout is recomputed here: {@link #solve} already branches on
     * the flag, so a toggle is this bookkeeping plus one frame.</p>
     */
    void toggleTheater() {
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
        }
    }

    // ------------------------------------------------------------------
    // Restoring
    // ------------------------------------------------------------------

    /**
     * Puts back a saved arrangement.
     *
     * @param takePosition whether this window may claim the remembered spot; a second
     *                     window of the same kind must not, or the two would land
     *                     exactly on top of each other
     */
    void restore(WindowStateStore.State state, boolean takePosition) {
        if (state.placed() && takePosition) {
            userPlaced = true;
            userX = state.x();
            userY = state.y();
            // The configured default position must not overwrite what was restored.
            initialPositionApplied = true;
        }
        if (state.sized() && state.width() > 0) {
            pendingWidth = state.width();
        }
    }

    /**
     * Turns a restored content width into the scale the window actually works in, once
     * there is a real source size to divide it by.
     *
     * <p>A video window exists before its player has decoded a single frame, and reports
     * a 320x180 placeholder until then. Converting the width against that placeholder
     * would restore a box several times too large the moment the real resolution
     * arrived, so the width waits here instead. A source whose size never resolves — a
     * video that fails to open — simply keeps its auto-fit scale, which is the right
     * answer for a window with nothing in it.</p>
     *
     * @param sourceSizeKnown whether {@code srcW} is the real size yet
     */
    void applyPendingWidth(int srcW, boolean sourceSizeKnown) {
        if (pendingWidth <= 0 || !sourceSizeKnown) {
            return;
        }
        userSized = true;
        userScale = pendingWidth / (double) Math.max(1, srcW);
        pendingWidth = 0;
    }
}
