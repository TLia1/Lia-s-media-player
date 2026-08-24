package com.lia.mediaplayer.platform;

import com.lia.mediaplayer.chat.AudioChatHandler;
import com.lia.mediaplayer.chat.ImageChatHandler;
import com.lia.mediaplayer.chat.VideoChatHandler;
import com.lia.mediaplayer.gui.MediaWindowOverlay;
import com.lia.mediaplayer.input.KeybindHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * The mod's single catalogue of client hooks — the complete list of moments the mod
 * needs the game to call it, expressed only in vanilla types.
 *
 * <p>This is the same idea as the seams in {@code gui} ("one point of contact with X"),
 * applied to the loader itself. A bridge in {@code platform.neoforge} or
 * {@code platform.fabric} subscribes to that loader's events and forwards them here;
 * nothing below this package ever sees a {@code ScreenEvent}, a {@code Event<...>} or an
 * {@code @SubscribeEvent}. Twelve hooks are the mod's entire loader surface, which is
 * why two thin bridges beat pulling in a cross-loader abstraction layer.</p>
 *
 * <p>The boolean-returning mouse hooks answer "did the mod consume this?". NeoForge wants
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
     * @return the message to display, which is {@code message} itself when it holds no
     *         media link
     */
    public static Component onChatReceived(Component message) {
        Component result = ImageChatHandler.rewrite(message);
        result = VideoChatHandler.rewrite(result);
        return AudioChatHandler.rewrite(result);
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
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Polls the key bindings and retires players whose queue ran out. */
    public static void onClientTick() {
        KeybindHandler.onClientTick();
        MediaWindowOverlay.clientTick();
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
}
