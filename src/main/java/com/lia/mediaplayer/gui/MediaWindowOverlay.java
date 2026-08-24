package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.chat.ChatEvents;
import com.lia.mediaplayer.media.YouTubePlaylistResolver;
import com.lia.mediaplayer.audio.AudioPlayer;
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

import java.util.*;
import java.util.function.Consumer;

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
        return (MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstanceOrNull();
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
        if (!(screen instanceof ChatScreen)) {
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
        Component label = Component.translatable(hidden > 1 ? "gui.liasmediaplayer.hidden_players.plural" : "gui.liasmediaplayer.hidden_players.singular", hidden);
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
        if (!(screen instanceof ChatScreen)) {
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
        // A playlist page is not a media item: expand it first (a yt-dlp round-trip
        // on a background thread), then queue everything it contains.
        if (com.lia.mediaplayer.source.YouTubePlaylistSource.isPlaylist(url)) {
            playYouTubePlaylist(url, Keys.altDown());
            return true;
        }
        MediaKind kind = ctx.getMediaSources().kindOf(url);
        if (kind == MediaKind.VIDEO) {
            if (Keys.altDown()) {
                if (Keys.shiftDown()) {
                    ctx.getAudioManager().open(url).bringToFront();
                } else {
                    ctx.getAudioManager().enqueue(url);
                }
            } else if (Keys.shiftDown()) {
                ctx.getVideoManager().open(url).bringToFront();
            } else {
                ctx.getVideoManager().enqueue(url);
            }
            return true;
        }
        if (kind == MediaKind.AUDIO) {
            if (Keys.shiftDown()) {
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
        if (!(screen instanceof ChatScreen) || noWindows()) {
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
        if (!(screen instanceof ChatScreen) || noWindows()) {
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
        if (!(screen instanceof ChatScreen) || noWindows()) {
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
    // Lifecycle: close finished videos
    // ------------------------------------------------------------------

    /** Closes players whose track ended and could not advance to a next one. */
    public static void clientTick() {
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;

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
