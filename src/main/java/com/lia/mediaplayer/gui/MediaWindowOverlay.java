package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.chat.ChatEvents;
import com.lia.mediaplayer.media.YouTubePlaylistResolver;
import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.source.YouTubePlaylistSource;
import com.lia.mediaplayer.video.VideoPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single coordinator that renders and drives <em>all</em> media windows — the
 * pinned {@link ImageWindow}s and the {@link VideoWindow}s — as one stack.
 *
 * <p>Every method here is a plain function on vanilla types: no loader event object
 * ever reaches this class. {@code platform.ClientHooks} is what the two loader bridges
 * call, and it forwards to these. That split is what lets one window stack serve both
 * NeoForge and Fabric, whose screen/HUD events have neither a common type nor a common
 * shape — and it makes the window logic testable without booting the game.</p>
 */
public final class MediaWindowOverlay {
    private static final int BASE_Z = 300;
    private static final int Z_STEP = 5;
    /** How long the outline of a closed (or hidden) window lingers. */
    private static final int GHOST_MS = 180;
    /** A stuck ghost is invisible after GHOST_MS anyway; this only bounds the list. */
    private static final int MAX_GHOSTS = 8;

    /**
     * The outline left behind by a window that was just closed or hidden — see
     * {@link MediaWindow#closeWithFade()} for why the window itself cannot be the thing
     * that fades.
     */
    private record Ghost(int x, int y, int w, int h, long startedAt) {
    }

    private static final List<Ghost> ghosts = new ArrayList<>();

    private static boolean revealVisible;
    private static int revealX, revealY, revealW, revealH;

    private static int plBtnX, plBtnY, plBtnW, plBtnH;

    private MediaWindowOverlay() {
    }

    /**
     * Adds the mod's config button to the pause menu.
     *
     * <p>{@code addWidget} is how the caller's loader attaches a widget to an
     * already-initialised screen — a NeoForge event method on one side, a mutable widget
     * list on the other — which is the only part of this that is not vanilla.</p>
     */
    public static void screenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        if (!(screen instanceof PauseScreen)) {
            return;
        }
        addWidget.accept(Button.builder(Component.translatable("gui.liasmediaplayer.config_button"), (button) -> {
            Screens.open(new ConfigScreen(screen));
        }).bounds(screen.width - 10 - 112, 10, 112, 20).build());
    }

    private static MediaPlayerContext getContext() {
        return MediaPlayerContext.getOrNull();
    }

    // ------------------------------------------------------------------
    // Shared stack
    // ------------------------------------------------------------------

    private static List<MediaWindow> orderedWindows() {
        MediaPlayerContext ctx = getContext();
        List<MediaWindow> all = new ArrayList<>();
        if (ctx != null) {
            all.addAll(ctx.getImageManager().getWindows());
            all.addAll(ctx.getVideoManager().getWindows());
            all.addAll(ctx.getAudioManager().getWindows());
        }
        all.sort(Comparator.comparingLong(MediaWindow::zOrder));
        return all;
    }

    private static boolean noWindows() {
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return true;
        return ctx.getImageManager().isEmpty() && ctx.getVideoManager().isEmpty() && ctx.getAudioManager().isEmpty();
    }

    /**
     * Whether there is anything in the stack at all — asked by
     * {@link MediaControlScreen}, which is a screen for driving windows and should say
     * so when there are none.
     */
    static boolean hasWindows() {
        return !noWindows();
    }

    /**
     * Whether {@code screen} is one the window stack lives on — drawn over it, and
     * driven by its mouse and keyboard input.
     *
     * <p>It is asked in six places (render, the four mouse hooks and the keyboard one),
     * and every one of them has to agree: a screen that renders the windows but refuses
     * their clicks would show buttons that do not work. One predicate is what keeps them
     * in step — and what made {@link MediaControlScreen} a one-line addition rather than
     * six.</p>
     *
     * <p>The chat screen is here because that is where a media link is clicked in the
     * first place; the control screen because reaching a pause button should not require
     * opening a text field.</p>
     */
    private static boolean acceptsWindows(Screen screen) {
        return screen instanceof ChatScreen || screen instanceof MediaControlScreen;
    }

    /**
     * The top-most visible window matching {@code filter}, or {@code null}.
     *
     * <p>The filter is the point: "the window in front" is rarely the right target on
     * its own. A transport key means the front-most window that <em>has</em> a
     * transport, so a pinned image sitting over a playing video does not silently
     * swallow the space bar.</p>
     */
    @Nullable
    static MediaWindow frontMost(Predicate<MediaWindow> filter) {
        MediaWindow best = null;
        for (MediaWindow window : orderedWindows()) {
            if (window.isVisible() && filter.test(window)) {
                best = window; // orderedWindows() is sorted by z, so the last match wins
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Magnetism
    // ------------------------------------------------------------------

    /**
     * The vertical lines a dragged window snaps to: the screen's two edges and its
     * centre, plus the edges and centre of every other visible window.
     *
     * @param self the window being dragged, which must not be attracted to itself
     */
    static int[] snapGuidesX(MediaWindow self) {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        List<MediaWindow> others = otherVisibleWindows(self);
        int[] guides = new int[3 + others.size() * 3];
        guides[0] = 2;
        guides[1] = screenW - 2;
        guides[2] = screenW / 2;
        int i = 3;
        for (MediaWindow other : others) {
            guides[i++] = other.boxX;
            guides[i++] = other.boxX + other.boxW;
            guides[i++] = other.boxX + other.boxW / 2;
        }
        return guides;
    }

    /** The horizontal counterpart of {@link #snapGuidesX}. */
    static int[] snapGuidesY(MediaWindow self) {
        var window = Minecraft.getInstance().getWindow();
        int screenH = window.getGuiScaledHeight();
        List<MediaWindow> others = otherVisibleWindows(self);
        int[] guides = new int[3 + others.size() * 3];
        guides[0] = 2;
        guides[1] = screenH - 2;
        guides[2] = screenH / 2;
        int i = 3;
        for (MediaWindow other : others) {
            guides[i++] = other.boxY;
            guides[i++] = other.boxY + other.boxH;
            guides[i++] = other.boxY + other.boxH / 2;
        }
        return guides;
    }

    /**
     * Whether {@code window} is the only one of its kind currently open.
     *
     * <p>Asked when a window is restoring where it was left: several windows share one
     * {@code windows.json} entry, so only a lone one should take the remembered spot.</p>
     */
    static boolean isSoleWindowOfKind(MediaWindow window) {
        for (MediaWindow other : orderedWindows()) {
            if (other != window && other.stateKey().equals(window.stateKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Collects where the windows are and how they are set up, once a tick.
     *
     * <p>Windows are gathered into one entry per kind before anything reaches the
     * store, because several of them share that entry: putting each in turn would have
     * two video players overwrite each other every tick, and the store — which writes
     * whenever the value it holds changes — would rewrite the file twenty times a
     * second. The stack is sorted by z, so the front-most window of each kind is the
     * one whose arrangement is kept, which is also the one the user last touched.</p>
     */
    private static void saveWindowState(MediaPlayerContext ctx) {
        Map<String, WindowStateStore.State> latest = new LinkedHashMap<>();
        for (MediaWindow window : orderedWindows()) {
            WindowStateStore.State state = window.captureState();
            if (state != null) {
                latest.put(window.stateKey(), state);
            }
        }
        WindowStateStore store = ctx.getWindowStateStore();
        latest.forEach(store::put);
        store.flush();
    }

    private static List<MediaWindow> otherVisibleWindows(MediaWindow self) {
        List<MediaWindow> others = new ArrayList<>();
        for (MediaWindow window : orderedWindows()) {
            if (window != self && window.isVisible()) {
                others.add(window);
            }
        }
        return others;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private static void renderAll(GuiGraphics g, int screenWidth, int screenHeight,
                                  int mouseX, int mouseY, boolean withControls) {
        renderGhosts(g);
        // Checked before the snapshot below allocates: the HUD path calls this every
        // frame, windows or not, so that a closing outline still has somewhere to draw.
        if (noWindows()) {
            return;
        }
        List<MediaWindow> all = orderedWindows();
        // The last visible window in z-order is the one a click would reach, and the
        // only one that gets the bright edge. Found up front because the windows are
        // drawn bottom-up, so "the one drawn last" is not known until it is too late.
        MediaWindow front = null;
        for (MediaWindow window : all) {
            if (window.isVisible()) {
                front = window;
            }
        }
        Map<Integer, Integer> slotByGroup = new HashMap<>();
        int depth = 0;
        for (MediaWindow window : all) {
            if (!window.isVisible()) {
                continue;
            }
            int slot = slotByGroup.merge(window.anchorGroup(), 1, Integer::sum) - 1;
            GuiLayer.push(g, BASE_Z + depth * Z_STEP);
            window.layout(screenWidth, screenHeight, slot);
            window.render(g, mouseX, mouseY, withControls, window == front);
            GuiLayer.popAndFlush(g);
            depth++;
        }
    }

    /**
     * Records the outline of a window that is going away, so
     * {@link #renderGhosts} can fade it out after the window itself is gone.
     */
    static void noteClosed(int x, int y, int w, int h) {
        if (ghosts.size() >= MAX_GHOSTS) {
            ghosts.removeFirst();
        }
        ghosts.add(new Ghost(x, y, w, h, Anim.now()));
    }

    /**
     * Drops every pending outline (leaving a server takes the windows with it).
     */
    public static void clearGhosts() {
        ghosts.clear();
        NowPlayingBanner.clear();
    }

    private static void renderGhosts(GuiGraphics g) {
        if (ghosts.isEmpty()) {
            return;
        }
        GuiLayer.push(g, BASE_Z - Z_STEP);
        ghosts.removeIf(ghost -> {
            double t = Anim.progress(ghost.startedAt(), GHOST_MS);
            if (t >= 1.0) {
                return true;
            }
            double fade = 1.0 - Anim.easeOut(t);
            // Shrinking as it fades reads as "it went away" rather than "it turned
            // transparent" — the reverse of how a window arrives.
            int inset = (int) Math.round(4 * (1.0 - fade));
            Panels.border(g, ghost.x() + inset, ghost.y() + inset,
                    ghost.x() + ghost.w() - inset, ghost.y() + ghost.h() - inset,
                    Theme.withAlpha(Theme.BORDER, fade));
            return false;
        });
        GuiLayer.popAndFlush(g);
    }

    /** Draws the window stack and its two overlay buttons over an open chat screen. */
    public static void screenRender(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
        if (!acceptsWindows(screen)) {
            return;
        }
        renderAll(g, screen.width, screen.height, mouseX, mouseY, true);
        renderPlaylistsButton(g, mouseX, mouseY);
        renderRevealButton(g, mouseX, mouseY);
        NowPlayingBanner.render(g, screen.width);
        ImageHoverPreview.render(g, mouseX, mouseY, screen.width, screen.height);
        // Last, so a window control's tooltip lands on top of every window and of the
        // chat preview — and so the request the topmost window made is the one drawn.
        Tooltips.renderPending(g, mouseX, mouseY);
    }

    private static void renderPlaylistsButton(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable("gui.liasmediaplayer.playlists");
        int noteW = 11;
        plBtnW = noteW + font.width(label) + 8;
        plBtnH = 14;
        plBtnX = 4;
        plBtnY = 4;

        boolean over = MediaWindow.inRect(mouseX, mouseY, plBtnX, plBtnY, plBtnW, plBtnH);
        int fg = over ? Theme.ICON_HOVER : Theme.TEXT;
        GuiLayer.push(g, 500);
        Panels.fill(g, plBtnX, plBtnY, plBtnX + plBtnW, plBtnY + plBtnH, over ? Theme.CHIP_HOVER_BG : Theme.CHIP_BG);
        Glyphs.note(g, plBtnX + 2, plBtnY + 2, fg);
        g.drawString(font, label, plBtnX + 2 + noteW, plBtnY + 3, fg);
        GuiLayer.popAndFlush(g);
    }

    private static void renderRevealButton(GuiGraphics g, int mouseX, int mouseY) {
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;
        int hidden = ctx.getVideoManager().hiddenCount() + ctx.getAudioManager().hiddenCount();
        revealVisible = hidden > 0;
        if (!revealVisible) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable(hidden > 1
                ? "gui.liasmediaplayer.hidden_players.plural"
                : "gui.liasmediaplayer.hidden_players.singular", hidden);
        int triW = 8;
        revealW = triW + font.width(label) + 10;
        revealH = 14;
        revealX = 4;
        revealY = 22;

        boolean over = MediaWindow.inRect(mouseX, mouseY, revealX, revealY, revealW, revealH);
        int fg = over ? Theme.ICON_HOVER : Theme.TEXT;
        GuiLayer.push(g, 500);
        Panels.fill(g, revealX, revealY, revealX + revealW, revealY + revealH, over ? Theme.CHIP_HOVER_BG : Theme.CHIP_BG);
        int tx = revealX + 5;
        int ty = revealY + 3;
        for (int i = 0; i < 8; i++) {
            int half = Math.min(i, 7 - i);
            g.fill(tx, ty + i, tx + 1 + half, ty + i + 1, fg);
        }
        g.drawString(font, label, revealX + 5 + triW, revealY + 3, fg);
        GuiLayer.popAndFlush(g);
    }

    /** Draws the window stack over the in-world HUD, without controls and without a cursor. */
    public static void hudRender(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (Screens.current() != null) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        // Not gated on there being windows: the closing outline and the "now playing"
        // banner both exist precisely when nothing is on screen to show them.
        renderAll(g, width, mc.getWindow().getGuiScaledHeight(), -1, -1, false);
        NowPlayingBanner.render(g, width);
    }

    // ------------------------------------------------------------------
    // Mouse input (chat screen only)
    // ------------------------------------------------------------------

    /**
     * Routes a mouse press over the chat screen to the window stack, the two overlay
     * buttons, or the media link under the cursor.
     *
     * @return {@code true} when the mod consumed the press and the screen must not see it
     */
    public static boolean mousePressed(Screen screen, double mouseX, double mouseY, int button) {
        if (!acceptsWindows(screen)) {
            return false;
        }
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return false;

        if (button == 0 && MediaWindow.inRect(mouseX, mouseY, plBtnX, plBtnY, plBtnW, plBtnH)) {
            Screens.open(new PlaylistScreen());
            return true;
        }
        if (button == 0 && revealVisible
                && MediaWindow.inRect(mouseX, mouseY, revealX, revealY, revealW, revealH)) {
            ctx.getVideoManager().revealAll();
            ctx.getAudioManager().revealAll();
            return true;
        }
        List<MediaWindow> all = orderedWindows();
        for (int i = all.size() - 1; i >= 0; i--) {
            MediaWindow window = all.get(i);
            if (!window.isVisible()) {
                continue;
            }
            MediaWindow.ClickResult result = window.mouseClicked(mouseX, mouseY, button);
            if (result == MediaWindow.ClickResult.CLOSE) {
                window.closeWithFade();
                return true;
            }
            if (result == MediaWindow.ClickResult.HANDLED) {
                window.bringToFront();
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        String url = hoveredUrl(mouseX, mouseY);
        if (url == null) {
            return false;
        }
        return play(url, Keys.altDown(), Keys.shiftDown());
    }

    /**
     * Plays one URL the way a click on it would: the mod's single answer to "the user
     * pointed at this link, now what?".
     *
     * <p>Public because a click on the chat is no longer the only way to arrive here —
     * the "play from clipboard" key binding routes through the same decision, and the
     * two must not drift apart on what alt means.</p>
     *
     * @param audioOnly play a video link as sound alone (the alt modifier)
     * @param newWindow open a player of its own rather than queueing into the front one
     *                  (the shift modifier)
     * @return {@code true} when the URL was something the mod can play
     */
    public static boolean play(String url, boolean audioOnly, boolean newWindow) {
        MediaPlayerContext ctx = getContext();
        if (ctx == null || url == null) {
            return false;
        }
        // A playlist page is not a media item: expand it first (a yt-dlp round-trip
        // on a background thread), then queue everything it contains.
        if (YouTubePlaylistSource.isPlaylist(url)) {
            playYouTubePlaylist(url, audioOnly);
            return true;
        }
        MediaKind kind = ctx.getMediaSources().kindOf(url);
        if (kind == MediaKind.VIDEO) {
            if (audioOnly) {
                if (newWindow) {
                    ctx.getAudioManager().open(url).bringToFront();
                } else {
                    ctx.getAudioManager().enqueue(url);
                }
            } else if (newWindow) {
                ctx.getVideoManager().open(url).bringToFront();
            } else {
                ctx.getVideoManager().enqueue(url);
            }
            return true;
        }
        if (kind == MediaKind.AUDIO) {
            if (newWindow) {
                ctx.getAudioManager().open(url).bringToFront();
            } else {
                ctx.getAudioManager().enqueue(url);
            }
            return true;
        }
        if (kind == MediaKind.IMAGE) {
            ctx.getImageManager().show(url).bringToFront();
            return true;
        }
        return false;
    }

    /**
     * Moves or resizes whichever window is currently being dragged.
     *
     * @return {@code true} when a window took the drag
     */
    public static boolean mouseDragged(Screen screen, double mouseX, double mouseY) {
        // The mod's own screens get the drag before the windows do: they are the screen,
        // so nothing of the stack is behind them to compete for it. See DragTarget.
        if (screen instanceof DragTarget target) {
            return target.onDrag(mouseX, mouseY);
        }
        if (!acceptsWindows(screen) || noWindows()) {
            return false;
        }
        List<MediaWindow> ordered = orderedWindows();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MediaWindow window = ordered.get(i);
            if (window.isVisible() && window.mouseDragged(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ends a drag or a resize.
     *
     * @return {@code true} when a window was being dragged
     */
    public static boolean mouseReleased(Screen screen) {
        if (screen instanceof DragTarget target) {
            return target.onRelease();
        }
        if (!acceptsWindows(screen) || noWindows()) {
            return false;
        }
        List<MediaWindow> ordered = orderedWindows();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MediaWindow window = ordered.get(i);
            if (window.isVisible() && window.mouseReleased()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a scroll to the window under the cursor (volume, seek, queue list).
     *
     * @return {@code true} when a window took the scroll
     */
    public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double deltaY) {
        if (!acceptsWindows(screen) || noWindows()) {
            return false;
        }
        List<MediaWindow> all = orderedWindows();
        for (int i = all.size() - 1; i >= 0; i--) {
            MediaWindow window = all.get(i);
            if (window.mouseScrolled(mouseX, mouseY, deltaY)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Keyboard input
    // ------------------------------------------------------------------

    /**
     * Offers a key press to the window stack.
     *
     * <p>Unlike the mouse hooks this one runs even when no window is open, because the
     * table it consults is not only about windows — the volume keys act on the mod's
     * one shared level whether or not anything is on screen.</p>
     *
     * @return {@code true} when the mod took the key and the screen must not see it
     */
    public static boolean keyPressed(Screen screen, int key) {
        if (!acceptsWindows(screen)) {
            return false;
        }
        return WindowShortcuts.handle(screen, key);
    }

    // ------------------------------------------------------------------
    // Bulk actions (the global key bindings)
    // ------------------------------------------------------------------

    /**
     * Hides every visible window, or reveals them all when they are already hidden.
     *
     * <p>One key for both directions rather than two: what someone wants from it is
     * "get the media out of the way" and then "bring it back", and which of those a
     * press means is never in doubt from looking at the screen.</p>
     */
    public static void toggleAllVisible() {
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;
        List<MediaWindow> all = orderedWindows();
        boolean anyVisible = false;
        for (MediaWindow window : all) {
            if (window.isVisible()) {
                anyVisible = true;
                break;
            }
        }
        if (anyVisible) {
            for (MediaWindow window : all) {
                if (window.isVisible()) {
                    window.setVisible(false);
                    noteClosed(window.boxX, window.boxY, window.boxW, window.boxH);
                }
            }
            return;
        }
        // Images have no hide button and so no revealAll of their own; going through
        // the windows covers all three kinds with one loop.
        for (MediaWindow window : all) {
            window.setVisible(true);
        }
    }

    /**
     * Closes every window, disposing the players behind them.
     */
    public static void closeAll() {
        for (MediaWindow window : orderedWindows()) {
            window.closeWithFade();
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle: close finished videos
    // ------------------------------------------------------------------

    /** Closes players whose track ended and could not advance to a next one. */
    public static void clientTick() {
        // Before anything is drawn with it: the palette follows the theme setting, and
        // this is the one place that runs every tick whether or not anything is playing.
        Theme.refresh();
        // Same reason, and the same "whether or not anything is playing": a tick is
        // between two frames, which is the only moment a texture can be freed without
        // the risk of a draw command still pointing at it. See TextureBridge.release.
        TextureBridge.flushReleases();

        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;

        saveWindowState(ctx);

        if (!ctx.getVideoManager().isEmpty()) {
            for (VideoWindow window : ctx.getVideoManager().getWindows()) {
                if (!window.isVisible()) {
                    // Nothing is drawing this one, and drawing is what empties the frame
                    // queue. Without this the decode thread jams against a full queue and
                    // the track never reaches its end, so the loop below never advances a
                    // hidden player to its next video. See VideoPlayer.discardDueFrames.
                    window.player().discardDueFrames();
                }
                if (window.player().state() == VideoPlayer.State.ENDED && !window.advance()) {
                    window.closeWithFade();
                }
            }
        }
        if (!ctx.getAudioManager().isEmpty()) {
            for (AudioWindow window : ctx.getAudioManager().getWindows()) {
                AudioPlayer ap = window.player();
                if (ap.state() == AudioPlayer.State.ENDED && !ap.isPaused() && !window.advance()) {
                    window.closeWithFade();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Expands a YouTube playlist link and plays the whole thing in one fresh window —
     * as video, or as sound only when alt is held (the same modifier a single YouTube
     * link uses). The expansion is a background yt-dlp call, so the player gets a
     * "loading" line first and the queue appears when it comes back.
     */
    private static void playYouTubePlaylist(String url, boolean audioOnly) {
        YouTubePlaylistResolver.tellPlayer(Component.translatable("chat.liasmediaplayer.playlist.loading"));
        YouTubePlaylistResolver.loadAsync(url, result -> {
            MediaPlayerContext ctx = getContext();
            if (result == null || ctx == null) {
                return; // loadAsync has already told the player what went wrong
            }
            if (audioOnly) {
                ctx.getAudioManager().playAll(result.urls(), false, RepeatMode.OFF);
            } else {
                ctx.getVideoManager().playAll(result.urls(), false, RepeatMode.OFF);
            }
            YouTubePlaylistResolver.tellPlayer(Component.translatable(
                    "chat.liasmediaplayer.playlist.loaded", result.urls().size()));
        });
    }

    @Nullable
    private static String hoveredUrl(double mouseX, double mouseY) {
        Style style = ChatHitTest.hoveredStyle(mouseX, mouseY);
        if (style == null) {
            return null;
        }
        return ChatEvents.clickedUrl(style);
    }
}
