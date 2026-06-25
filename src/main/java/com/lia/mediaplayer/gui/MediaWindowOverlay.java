package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.audio.AudioPlayer;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.source.MediaSources;
import com.lia.mediaplayer.video.VideoPlayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single coordinator that renders and drives <em>all</em> media windows — the
 * pinned {@link ImageWindow}s and the {@link VideoWindow}s — as one stack.
 *
 * <p>Previously each kind drew and handled input in its own event subscriber, so
 * the two stacks were independent: an image and a video could overlap with no
 * defined order, a click could "fall through" to a window drawn underneath, and
 * a video's batched text (the timestamp / volume pop-up) floated above windows
 * sitting on top of it because all text is flushed in one late pass.</p>
 *
 * <p>Here everything shares the {@link MediaWindow#zOrder() z-order}: windows are
 * drawn back-to-front, each in its own depth band, and the text buffer is
 * {@linkplain GuiGraphics#flush() flushed} after every window so a front window
 * fully occludes the one behind it — content <em>and</em> text. Clicking a window
 * raises it ({@link MediaWindow#bringToFront()}), and input is tested top-first so
 * only the front-most window under the cursor reacts.</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class MediaWindowOverlay {
    /** Base depth for the lowest window; above the chat, below the hover tooltip (400). */
    private static final int BASE_Z = 300;
    /** Depth separation between stacked windows, so a front one occludes the text of one behind. */
    private static final int Z_STEP = 5;

    /** Last-laid-out geometry of the "reveal hidden videos" chat button (for hit-testing). */
    private static boolean revealVisible;
    private static int revealX, revealY, revealW, revealH;

    /** Last-laid-out geometry of the always-on "Playlists" chat button (for hit-testing). */
    private static int plBtnX, plBtnY, plBtnW, plBtnH;

    private MediaWindowOverlay() {
    }

    // ------------------------------------------------------------------
    // Shared stack
    // ------------------------------------------------------------------

    /** Every media window (image + video), sorted bottom-to-top by z-order. */
    private static List<MediaWindow> orderedWindows() {
        List<MediaWindow> all = new ArrayList<>();
        all.addAll(ImageWindowManager.windows());
        all.addAll(VideoPlayerManager.windows());
        all.addAll(AudioPlayerManager.windows());
        all.sort(Comparator.comparingLong(MediaWindow::zOrder));
        return all;
    }

    private static boolean noWindows() {
        return ImageWindowManager.isEmpty() && VideoPlayerManager.isEmpty() && AudioPlayerManager.isEmpty();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Draws the whole stack back-to-front. Each window gets its own depth band and
     * its text is flushed before the next window draws, so the front window always
     * covers the one behind it — including the seek time and volume pop-up.
     */
    private static void renderAll(GuiGraphics g, int screenWidth, int screenHeight,
                                  int mouseX, int mouseY, boolean withControls) {
        List<MediaWindow> all = orderedWindows();
        if (all.isEmpty()) {
            return;
        }
        // Each anchor group (images vs videos) cascades independently, so same-kind
        // windows fan out without landing on top of each other.
        Map<Integer, Integer> slotByGroup = new HashMap<>();
        int depth = 0;
        for (MediaWindow window : all) {
            if (!window.isVisible()) {
                continue;
            }
            int slot = slotByGroup.merge(window.anchorGroup(), 1, Integer::sum) - 1;
            g.pose().pushPose();
            g.pose().translate(0, 0, BASE_Z + depth * Z_STEP);
            window.layout(screenWidth, screenHeight, slot);
            window.render(g, mouseX, mouseY, withControls);
            g.pose().popPose();
            // Force the batched text to draw now, before the next (higher) window's
            // fills, so a window in front occludes the text of the one behind it.
            g.flush();
            depth++;
        }
    }

    /** On the chat screen: full windows with controls, then the hover preview on top. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) {
            return;
        }
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        int screenWidth = event.getScreen().width;
        int screenHeight = event.getScreen().height;

        renderAll(event.getGuiGraphics(), screenWidth, screenHeight, mouseX, mouseY, true);
        renderPlaylistsButton(event.getGuiGraphics(), mouseX, mouseY);
        renderRevealButton(event.getGuiGraphics(), mouseX, mouseY);
        ImageHoverPreview.render(event.getGuiGraphics(), mouseX, mouseY,
                screenWidth, screenHeight);
    }

    /** A small always-present chat button (top-left) that opens the playlist manager. */
    private static void renderPlaylistsButton(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable("gui.liasmediaplayer.playlists");
        int noteW = 11;
        plBtnW = noteW + font.width(label) + 8;
        plBtnH = 14;
        plBtnX = 4;
        plBtnY = 4;

        boolean over = MediaWindow.inRect(mouseX, mouseY, plBtnX, plBtnY, plBtnW, plBtnH);
        int fg = over ? 0xFFFFD23F : 0xFFFFFFFF;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500); // above the windows and their batched text
        g.fill(plBtnX, plBtnY, plBtnX + plBtnW, plBtnY + plBtnH, over ? 0xF0303030 : 0xD0181818);
        Glyphs.note(g, plBtnX + 2, plBtnY + 2, fg);
        g.drawString(font, label, plBtnX + 2 + noteW, plBtnY + 3, fg);
        g.pose().popPose();
        g.flush();
    }

    /**
     * Draws a small button in the top-left of the chat screen that brings any hidden
     * (minimised-but-still-playing) video windows back on screen. Shown only while at
     * least one video is hidden.
     */
    private static void renderRevealButton(GuiGraphics g, int mouseX, int mouseY) {
        int hidden = VideoPlayerManager.hiddenCount() + AudioPlayerManager.hiddenCount();
        revealVisible = hidden > 0;
        if (!revealVisible) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component label = Component.translatable(hidden > 1 ? "gui.liasmediaplayer.hidden_players.plural" : "gui.liasmediaplayer.hidden_players.singular", hidden);
        int triW = 8; // room for the little play triangle on the left
        revealW = triW + font.width(label) + 10;
        revealH = 14;
        revealX = 4;
        revealY = 22; // sit just below the always-present "Playlists" button

        boolean over = MediaWindow.inRect(mouseX, mouseY, revealX, revealY, revealW, revealH);
        int fg = over ? 0xFFFFD23F : 0xFFFFFFFF;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500); // above the windows and their batched text
        g.fill(revealX, revealY, revealX + revealW, revealY + revealH, over ? 0xF0303030 : 0xD0181818);
        // A small play triangle, vertically centred.
        int tx = revealX + 5;
        int ty = revealY + 3;
        for (int i = 0; i < 8; i++) {
            int half = Math.min(i, 7 - i);
            g.fill(tx, ty + i, tx + 1 + half, ty + i + 1, fg);
        }
        g.drawString(font, label, revealX + 5 + triW, revealY + 3, fg);
        g.pose().popPose();
        g.flush();
    }



    /** While no screen is open, keep the windows on the HUD (picture only, no controls). */
    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || noWindows()) {
            return;
        }
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        renderAll(event.getGuiGraphics(), screenWidth, screenHeight, -1, -1, false);
    }

    // ------------------------------------------------------------------
    // Mouse input (chat screen only)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) {
            return;
        }
        // The always-present "Playlists" button opens the playlist manager.
        if (event.getButton() == 0
                && MediaWindow.inRect(event.getMouseX(), event.getMouseY(), plBtnX, plBtnY, plBtnW, plBtnH)) {
            Minecraft.getInstance().setScreen(new PlaylistScreen());
            event.setCanceled(true);
            return;
        }
        // The "reveal hidden players" button takes priority over the windows.
        if (event.getButton() == 0 && revealVisible
                && MediaWindow.inRect(event.getMouseX(), event.getMouseY(), revealX, revealY, revealW, revealH)) {
            VideoPlayerManager.revealAll();
            AudioPlayerManager.revealAll();
            event.setCanceled(true);
            return;
        }
        List<MediaWindow> all = orderedWindows();
        // Front-most (last drawn) window first, so a click can't reach one behind it.
        for (int i = all.size() - 1; i >= 0; i--) {
            MediaWindow window = all.get(i);
            if (!window.isVisible()) {
                continue;
            }
            MediaWindow.ClickResult result =
                    window.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            if (result == MediaWindow.ClickResult.CLOSE) {
                window.close();
                event.setCanceled(true);
                return;
            }
            if (result == MediaWindow.ClickResult.HANDLED) {
                window.bringToFront(); // focus: raise the clicked window above the rest
                event.setCanceled(true);
                return;
            }
        }
        // Nothing consumed it: a click on a media link spawns / focuses its window.
        if (event.getButton() == 0) {
            String url = hoveredUrl(event.getMouseX(), event.getMouseY());
            if (url == null) {
                return;
            }
            MediaKind kind = MediaSources.kindOf(url);
            if (kind == MediaKind.VIDEO) {
                // Default: queue the link into the current player. Shift-click opens a
                // separate, independent window instead. Alt-click forces it to play as
                // audio only.
                if (Screen.hasAltDown()) {
                    if (Screen.hasShiftDown()) {
                        AudioPlayerManager.open(url).bringToFront();
                    } else {
                        AudioPlayerManager.enqueue(url);
                    }
                } else if (Screen.hasShiftDown()) {
                    VideoPlayerManager.open(url).bringToFront();
                } else {
                    VideoPlayerManager.enqueue(url);
                }
                event.setCanceled(true);
            } else if (kind == MediaKind.AUDIO) {
                // Default: queue into the current bar. Shift-click opens a separate bar.
                if (Screen.hasShiftDown()) {
                    AudioPlayerManager.open(url).bringToFront();
                } else {
                    AudioPlayerManager.enqueue(url);
                }
                event.setCanceled(true);
            } else if (kind == MediaKind.IMAGE) {
                ImageWindowManager.show(url).bringToFront();
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || noWindows()) {
            return;
        }
        List<MediaWindow> ordered = orderedWindows();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MediaWindow window = ordered.get(i);
            if (window.isVisible() && window.mouseDragged(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || noWindows()) {
            return;
        }
        List<MediaWindow> ordered = orderedWindows();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MediaWindow window = ordered.get(i);
            if (window.isVisible() && window.mouseReleased()) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || noWindows()) {
            return;
        }
        List<MediaWindow> all = orderedWindows();
        for (int i = all.size() - 1; i >= 0; i--) {
            MediaWindow window = all.get(i);
            if (window.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaY())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle: close finished videos
    // ------------------------------------------------------------------

    /**
     * Once a video has played through to the end, advance to the next queued URL in
     * the same window; if nothing is queued, dispose the window automatically.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!VideoPlayerManager.isEmpty()) {
            for (VideoWindow window : VideoPlayerManager.windows()) {
                if (window.player().state() == VideoPlayer.State.ENDED && !window.advance()) {
                    VideoPlayerManager.close(window);
                }
            }
        }
        if (!AudioPlayerManager.isEmpty()) {
            for (AudioWindow window : AudioPlayerManager.windows()) {
                AudioPlayer ap = window.player();
                if (ap.state() == AudioPlayer.State.ENDED && !ap.isPaused() && !window.advance()) {
                    AudioPlayerManager.close(window);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    @Nullable
    private static String hoveredUrl(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Style style = mc.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY);
        if (style == null) {
            return null;
        }
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.OPEN_URL) {
            return null;
        }
        return clickEvent.getValue();
    }
}
