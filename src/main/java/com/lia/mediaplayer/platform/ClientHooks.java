package com.lia.mediaplayer.platform;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.diag.MediaPlayerLog;
import com.lia.mediaplayer.api.event.PlaybackEvent;
import com.lia.mediaplayer.api.event.PlaybackEvents;
import com.lia.mediaplayer.chat.AudioChatHandler;
import com.lia.mediaplayer.chat.ImageChatHandler;
import com.lia.mediaplayer.chat.MediaFilters;
import com.lia.mediaplayer.chat.VideoChatHandler;
import com.lia.mediaplayer.gui.MediaWindowOverlay;
import com.lia.mediaplayer.input.KeybindHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The mod's single catalogue of client hooks — the complete list of moments the mod
 * needs the game to call it, expressed only in vanilla types.
 *
 * <p>This is the same idea as the seams in {@code gui} ("one point of contact with X"),
 * applied to the loader itself. A bridge in {@code platform.neoforge} or
 * {@code platform.fabric} subscribes to that loader's events and forwards them here;
 * nothing below this package ever sees a {@code ScreenEvent}, a {@code Event<...>} or an
 * {@code @SubscribeEvent}. Thirteen hooks are the mod's entire loader surface, which is
 * why two thin bridges beat pulling in a cross-loader abstraction layer.</p>
 *
 * <p>The boolean-returning mouse and keyboard hooks answer "did the mod consume this?". NeoForge wants
 * that as {@code event.setCanceled(true)} and Fabric as {@code return false} from an
 * {@code allow*} callback; both bridges translate, this class does not care.</p>
 *
 * <p>Nothing depends on this package — it sits above everything, so the package graph
 * stays acyclic.</p>
 */
public final class ClientHooks {

    private ClientHooks() {
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    /**
     * Rewrites one incoming chat message, running all three link rules over it.
     *
     * <p>Order does not matter: the image, video and audio sources are disjoint, so each
     * rule only claims links the other two ignore, and they compose on one message.</p>
     *
     * @param sender the display name the message arrived with, or {@code null} for a
     *               system message (and for any message whose sender the loader could
     *               not name). Only the sender filter reads it — see
     *               {@link MediaFilters}. It is a name rather
     *               than a UUID because that is what both loaders can produce from the
     *               same field ({@code ChatType.Bound#name()}) on every target version,
     *               and because a name is what someone writing a filter list has.
     * @return the message to display, which is {@code message} itself when it holds no
     *         media link
     */
    public static Component onChatReceived(Component message, @Nullable String sender) {
        // A blocked sender's message is returned untouched — the same instance, so the
        // Fabric bridge leaves it to vanilla exactly as it does an ordinary line.
        if (!MediaFilters.allowsSender(sender)) {
            return message;
        }
        Component result = ImageChatHandler.rewrite(message, sender);
        result = VideoChatHandler.rewrite(result, sender);
        return AudioChatHandler.rewrite(result, sender);
    }

    /** Tears down every window, player and cache when leaving a world. */
    public static void onDisconnect() {
        ImageChatHandler.onDisconnect();
        VideoChatHandler.onDisconnect();
        AudioChatHandler.onDisconnect();
        // The windows those handlers just disposed leave a fading outline behind, and a
        // "now playing" banner may still be counting down. Neither should survive into
        // the next world.
        MediaWindowOverlay.clearGhosts();
        // And the surfaces addons decoded, whoever is still holding one: a texture that
        // outlives the world it was decoded for is a leak for the rest of the session,
        // and the ffmpeg process behind it would keep running for a screen that is gone.
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context != null) {
            context.getSurfaceRegistry().disposeAll();
            // And the sounds addons were playing with no window: nothing on screen can
            // stop one, so a track played into a world would otherwise keep an ffmpeg
            // process and an audio line running into the next.
            context.getHeadlessAudio().disposeAll();
        }
        // The failure backlog is per-session state like the caches are: a "why did that
        // not play?" screen opened in the next world should not be showing the last
        // world's errors.
        MediaPlayerLog.clear();
        // Posted last, so a listener woken by it finds the stack already empty and every
        // handle it may still be holding already dead.
        PlaybackEvents.post(PlaybackEvent.lifecycle(PlaybackEvent.Type.WORLD_LEFT));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Polls the key bindings and retires players whose queue ran out. */
    public static void onClientTick() {
        KeybindHandler.onClientTick();
        MediaWindowOverlay.clientTick();
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context != null) {
            // Rolls each surface's "wanted" flag over; see SurfaceRegistry.clientTick.
            context.getSurfaceRegistry().clientTick();
            // Fades, looping and the game pause for windowless sounds, and the once-a-tick
            // recompute of their placement — see HeadlessAudio.clientTick.
            context.getHeadlessAudio().clientTick();
        }
    }

    // ------------------------------------------------------------------
    // Screens
    // ------------------------------------------------------------------

    /** Adds the config button to the pause menu, once that screen has been laid out. */
    public static void onScreenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
        MediaWindowOverlay.screenInit(screen, addWidget);
    }

    /** Draws the window stack over an open chat screen. */
    public static void onScreenRender(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        MediaWindowOverlay.screenRender(screen, graphics, mouseX, mouseY);
    }

    /** Draws the window stack over the in-world HUD. */
    public static void onHudRender(GuiGraphics graphics) {
        MediaWindowOverlay.hudRender(graphics);
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    /** @return {@code true} when the mod took the press and the screen must not see it */
    public static boolean onMousePressed(Screen screen, double mouseX, double mouseY, int button) {
        return MediaWindowOverlay.mousePressed(screen, mouseX, mouseY, button);
    }

    /** @return {@code true} when a window is being moved or resized by this drag */
    public static boolean onMouseDragged(Screen screen, double mouseX, double mouseY) {
        return MediaWindowOverlay.mouseDragged(screen, mouseX, mouseY);
    }

    /** @return {@code true} when a window was being dragged and this ends it */
    public static boolean onMouseReleased(Screen screen) {
        return MediaWindowOverlay.mouseReleased(screen);
    }

    /** @return {@code true} when a window under the cursor took the scroll */
    public static boolean onMouseScrolled(Screen screen, double mouseX, double mouseY, double deltaY) {
        return MediaWindowOverlay.mouseScrolled(screen, mouseX, mouseY, deltaY);
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    /**
     * Offers a key press over an open screen to the window stack — pause, seek, volume
     * and the rest, without reaching for the mouse.
     *
     * <p>Only the key code is passed on. The modifiers travel with the event on both
     * loaders, but which of them the mod cares about is a question with a different
     * answer on macOS ({@code Cmd} standing in for {@code Ctrl}), and
     * {@code gui.Keys} is where that is already known — so the shortcut table asks it
     * rather than reading raw GLFW bits that would be wrong on one platform.</p>
     *
     * @return {@code true} when the mod took the key and the screen must not see it
     */
    public static boolean onKeyPressed(Screen screen, int key) {
        return MediaWindowOverlay.keyPressed(screen, key);
    }
}
