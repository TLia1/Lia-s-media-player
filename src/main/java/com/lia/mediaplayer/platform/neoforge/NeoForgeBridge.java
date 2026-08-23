package com.lia.mediaplayer.platform.neoforge;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.command.ShowCommand;
import com.lia.mediaplayer.input.ModKeybinds;
import com.lia.mediaplayer.platform.ClientHooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The NeoForge half of the loader bridge: every {@code @SubscribeEvent} the mod has, each
 * one unwrapping an event object and calling the matching {@link ClientHooks} method.
 *
 * <p>Deliberately dumb. Anything that looks like a decision belongs one level down, in
 * {@code ClientHooks} or below, where the Fabric bridge gets it for free.</p>
 *
 * <p>{@code @EventBusSubscriber} without an explicit bus is correct here: NeoForge routes
 * each listener by its event type, so the mod-bus events ({@code RegisterKeyMappingsEvent})
 * and the game-bus ones (everything else) can share one class.</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class NeoForgeBridge {

    private NeoForgeBridge() {
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onSystemChatReceived(ClientChatReceivedEvent.System event) {
        event.setMessage(ClientHooks.onChatReceived(event.getMessage()));
    }

    @SubscribeEvent
    public static void onPlayerChatReceived(ClientChatReceivedEvent.Player event) {
        event.setMessage(ClientHooks.onChatReceived(event.getMessage()));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientHooks.onDisconnect();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientHooks.onClientTick();
    }

    // ------------------------------------------------------------------
    // Screens and HUD
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        ClientHooks.onScreenInit(event.getScreen(), event::addListener);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        ClientHooks.onScreenRender(event.getScreen(), event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        ClientHooks.onHudRender(event.getGuiGraphics());
    }

    // ------------------------------------------------------------------
    // Mouse
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ClientHooks.onMousePressed(event.getScreen(), event.getMouseX(), event.getMouseY(),
                event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (ClientHooks.onMouseDragged(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (ClientHooks.onMouseReleased(event.getScreen())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (ClientHooks.onMouseScrolled(event.getScreen(), event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // Categories are now registered objects rather than free-form strings,
        // and NeoForge wants modded ones declared before the mappings using them.
        //? if >=1.21.11
        /*event.registerCategory(ModKeybinds.CATEGORY);*/
        for (KeyMapping mapping : ModKeybinds.all()) {
            event.register(mapping);
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(ShowCommand.<CommandSourceStack>tree(
                (context, message) -> context.getSource().sendFailure(message)));
    }
}
