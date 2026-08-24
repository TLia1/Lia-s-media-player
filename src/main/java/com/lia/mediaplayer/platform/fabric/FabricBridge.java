package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.MediaSourceProvider;
import com.lia.mediaplayer.command.ShowCommand;
import com.lia.mediaplayer.platform.ClientHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Fabric half of the loader bridge: subscribes every Fabric API event the mod needs
 * and forwards it to the matching {@link ClientHooks} method.
 *
 * <p>The counterpart of {@code platform.neoforge.NeoForgeBridge}, and just as dumb —
 * except for two places where Fabric offers no equivalent and the bridge has to do a
 * little work of its own: the player-chat rewrite (see {@link FabricChatSink}) and the
 * drag synthesis below.</p>
 *
 * <p>Fabric's screen events are per-screen rather than global, so everything screen-shaped
 * is subscribed from {@code AFTER_INIT} for the screen that was just built.</p>
 */
public final class FabricBridge implements ClientModInitializer {

    /**
     * Whether a mouse button is currently held down over the open screen.
     *
     * <p>This is how window dragging works on Fabric. {@code ScreenMouseEvents} only grew
     * {@code allowMouseDrag} in 1.21.8, and the mod targets 1.21.1 upwards; rather than
     * ship a mixin for the three versions below that — the mod bundles none, and one
     * loader needing one for a feature the other gets for free is a poor trade — the drag
     * is reconstructed from the press/release events (which every version has) plus the
     * cursor position handed to the render hook.</p>
     *
     * <p>This loses nothing. Vanilla dispatches a drag from the GLFW cursor callback,
     * which the game polls once per frame, so a real drag event can fire at most once per
     * frame either way — the reconstruction has exactly the same resolution, unlike the
     * once-per-tick polling that a client-tick fallback would give.</p>
     */
    private static boolean mouseHeld;

    @Override
    public void onInitializeClient() {
        LiasMediaPlayer.init();

        registerChat();
        registerLifecycle();
        registerScreens();
        FabricHud.register();
        FabricKeyMappings.register();
        registerCommands();
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    private static void registerChat() {
        // System messages have a modify event, so they are a straight swap.
        ClientReceiveMessageEvents.MODIFY_GAME.register(
                (message, overlay) -> ClientHooks.onChatReceived(message));

        // Player messages do not: modifying a signed message would break its signature
        // chain, so Fabric only offers allow/cancel. Messages that hold no media link —
        // the overwhelming majority — are left to vanilla untouched, so the hand-off only
        // ever applies to a message the mod actually rewrote.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) -> {
            Component rewritten = ClientHooks.onChatReceived(message);
            if (rewritten == message) {
                return true;
            }
            FabricChatSink.addPlayerMessage(rewritten, signed, message, timestamp);
            return false;
        });
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    private static void registerLifecycle() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientHooks.onDisconnect());

        // Collected once the client is up rather than from onInitializeClient, because
        // Fabric gives no ordering between mod initializers: an addon that registers its
        // provider from its own initializer may not have run yet at this point.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            List<MediaSourceProvider> discovered = FabricLoader.getInstance()
                    .getEntrypoints("liasmediaplayer:sources", MediaSourceProvider.class);
            LiasMediaPlayer.registerExternalSources(discovered);
        });
    }

    // ------------------------------------------------------------------
    // Screens, HUD, mouse and keyboard
    // ------------------------------------------------------------------

    private static void registerScreens() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // A screen that has just been (re)built cannot have a button held down on it.
            mouseHeld = false;
            ClientHooks.onMouseReleased(screen);

            ClientHooks.onScreenInit(screen, widget -> FabricScreens.addWidget(screen, widget));

            // 26.1 stopped drawing the GUI and started extracting it into a render state;
            // the event was renamed to match.
            //? if <26.1 {
            ScreenEvents.afterRender(screen).register(
                    (s, graphics, mouseX, mouseY, tickDelta) -> render(s, graphics, mouseX, mouseY));
            //?} else {
            /*ScreenEvents.afterExtract(screen).register(
                    (s, graphics, mouseX, mouseY, tickProgress) -> render(s, graphics, mouseX, mouseY));
            *///?}

            // 1.21.11 folded the cursor position and the button number into a single
            // MouseButtonEvent record. The scroll events kept their loose parameters.
            //? if <1.21.11 {
            ScreenMouseEvents.allowMouseClick(screen).register(
                    (s, mouseX, mouseY, button) -> !press(s, mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen).register(
                    (s, mouseX, mouseY, button) -> !release(s));
            //?} else {
            /*ScreenMouseEvents.allowMouseClick(screen).register(
                    (s, event) -> !press(s, event.x(), event.y(), event.button()));
            ScreenMouseEvents.allowMouseRelease(screen).register(
                    (s, event) -> !release(s));
            *///?}

            ScreenMouseEvents.allowMouseScroll(screen).register(
                    (s, mouseX, mouseY, horizontal, vertical) ->
                            !ClientHooks.onMouseScrolled(s, mouseX, mouseY, vertical));

            // The keyboard callback moved to a KeyEvent record at the same threshold the
            // mouse one did, and for the same reason.
            //? if <1.21.11 {
            ScreenKeyboardEvents.allowKeyPress(screen).register(
                    (s, key, scancode, modifiers) -> !ClientHooks.onKeyPressed(s, key));
            //?} else {
            /*ScreenKeyboardEvents.allowKeyPress(screen).register(
                    (s, event) -> !ClientHooks.onKeyPressed(s, event.key()));
            *///?}
        });
    }

    /**
     * Feeds the reconstructed drag, then draws. Both happen in the render hook because
     * that is the only place Fabric reports the cursor position on every version.
     */
    private static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseHeld) {
            ClientHooks.onMouseDragged(screen, mouseX, mouseY);
        }
        ClientHooks.onScreenRender(screen, graphics, mouseX, mouseY);
    }

    /** @return {@code true} when the mod consumed the press */
    private static boolean press(Screen screen, double mouseX, double mouseY, int button) {
        mouseHeld = true;
        return ClientHooks.onMousePressed(screen, mouseX, mouseY, button);
    }

    /** @return {@code true} when the mod consumed the release */
    private static boolean release(Screen screen) {
        mouseHeld = false;
        return ClientHooks.onMouseReleased(screen);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
                dispatcher.register(ShowCommand.<FabricClientCommandSource>tree(
                        (ctx, message) -> ctx.getSource().sendError(message))));
    }
}
