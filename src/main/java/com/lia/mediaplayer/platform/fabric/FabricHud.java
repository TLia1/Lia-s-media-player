package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.platform.ClientHooks;
import net.minecraft.resources.ResourceLocation;

/**
 * The Fabric bridge's single point of contact with the in-world HUD.
 *
 * <p>This is not a rename but a change of shape, so it gets a seam rather than a token
 * replacement. Up to 1.21.11 a mod drew over the HUD by registering a callback that ran
 * after vanilla's own drawing ({@code HudRenderCallback}). 26.1 turned the HUD into an
 * ordered list of named elements and deleted the callback: a mod now contributes an
 * element and says where in that list it goes.</p>
 *
 * <p>{@code HudElementRegistry} does exist from 1.21.8 on, but {@code HudRenderCallback}
 * is used for everything below 26.1 anyway — one branch per shape, switching where the
 * old shape actually disappears, rather than a third case in the middle.</p>
 */
final class FabricHud {
    private FabricHud() {
    }

    /** Draws the mod's windows immediately after vanilla chat. */
    static void register() {
        //? if <26.1 {
        // Deprecated from 1.21.4 on in favour of HudElementRegistry, and still the only
        // way to draw over the HUD on 1.21.1 and 1.21.4 — the deprecation warning on
        // those builds is expected.
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, tickCounter) -> ClientHooks.onHudRender(graphics));
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementAfter(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                ResourceLocation.fromNamespaceAndPath(LiasMediaPlayer.MODID, "media_windows"),
                (graphics, deltaTracker) -> ClientHooks.onHudRender(graphics));
        *///?}
    }
}
