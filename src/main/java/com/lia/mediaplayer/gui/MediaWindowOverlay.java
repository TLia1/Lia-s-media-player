package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.audio.AudioPlayer;
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

import java.util.*;

/**
 * Single coordinator that renders and drives <em>all</em> media windows — the
 * pinned {@link ImageWindow}s and the {@link VideoWindow}s — as one stack.
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class MediaWindowOverlay {
    private static final int BASE_Z = 300;
    private static final int Z_STEP = 5;

    private static boolean revealVisible;
    private static int revealX, revealY, revealW, revealH;

    private static int plBtnX, plBtnY, plBtnW, plBtnH;

    private MediaWindowOverlay() {
    }

    private static MediaPlayerContext getContext() {
        return (MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
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
        List<MediaWindow> all = orderedWindows();
        if (all.isEmpty()) {
            return;
        }
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
            g.flush();
            depth++;
        }
    }

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
        g.pose().translate(0, 0, 500);
        g.fill(plBtnX, plBtnY, plBtnX + plBtnW, plBtnY + plBtnH, over ? 0xF0303030 : 0xD0181818);
        Glyphs.note(g, plBtnX + 2, plBtnY + 2, fg);
        g.drawString(font, label, plBtnX + 2 + noteW, plBtnY + 3, fg);
        g.pose().popPose();
        g.flush();
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
        int fg = over ? 0xFFFFD23F : 0xFFFFFFFF;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);
        g.fill(revealX, revealY, revealX + revealW, revealY + revealH, over ? 0xF0303030 : 0xD0181818);
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
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;

        if (event.getButton() == 0
                && MediaWindow.inRect(event.getMouseX(), event.getMouseY(), plBtnX, plBtnY, plBtnW, plBtnH)) {
            Minecraft.getInstance().setScreen(new PlaylistScreen());
            event.setCanceled(true);
            return;
        }
        if (event.getButton() == 0 && revealVisible
                && MediaWindow.inRect(event.getMouseX(), event.getMouseY(), revealX, revealY, revealW, revealH)) {
            ctx.getVideoManager().revealAll();
            ctx.getAudioManager().revealAll();
            event.setCanceled(true);
            return;
        }
        List<MediaWindow> all = orderedWindows();
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
                window.bringToFront();
                event.setCanceled(true);
                return;
            }
        }
        if (event.getButton() == 0) {
            String url = hoveredUrl(event.getMouseX(), event.getMouseY());
            if (url == null) {
                return;
            }
            MediaKind kind = ctx.getMediaSources().kindOf(url);
            if (kind == MediaKind.VIDEO) {
                if (Screen.hasAltDown()) {
                    if (Screen.hasShiftDown()) {
                        ctx.getAudioManager().open(url).bringToFront();
                    } else {
                        ctx.getAudioManager().enqueue(url);
                    }
                } else if (Screen.hasShiftDown()) {
                    ctx.getVideoManager().open(url).bringToFront();
                } else {
                    ctx.getVideoManager().enqueue(url);
                }
                event.setCanceled(true);
            } else if (kind == MediaKind.AUDIO) {
                if (Screen.hasShiftDown()) {
                    ctx.getAudioManager().open(url).bringToFront();
                } else {
                    ctx.getAudioManager().enqueue(url);
                }
                event.setCanceled(true);
            } else if (kind == MediaKind.IMAGE) {
                ctx.getImageManager().show(url).bringToFront();
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MediaPlayerContext ctx = getContext();
        if (ctx == null) return;

        if (!ctx.getVideoManager().isEmpty()) {
            for (VideoWindow window : ctx.getVideoManager().getWindows()) {
                if (window.player().state() == VideoPlayer.State.ENDED && !window.advance()) {
                    ctx.getVideoManager().close(window);
                }
            }
        }
        if (!ctx.getAudioManager().isEmpty()) {
            for (AudioWindow window : ctx.getAudioManager().getWindows()) {
                AudioPlayer ap = window.player();
                if (ap.state() == AudioPlayer.State.ENDED && !ap.isPaused() && !window.advance()) {
                    ctx.getAudioManager().close(window);
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
