package com.lia.mediaplayer.platform.neoforge;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.gui.ConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.List;

/**
 * The NeoForge entry point. Owns everything FML needs — the {@code @Mod} annotation, the
 * mod container, the config screen extension point — and does nothing else itself: the
 * actual startup is {@link LiasMediaPlayer#init()}, which knows no loader.
 */
@Mod(value = LiasMediaPlayer.MODID, dist = Dist.CLIENT)
public final class NeoForgeMod {

    public NeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new ConfigScreen(parent));

        LiasMediaPlayer.init();

        // Collect addon media sources during client setup, once every mod has been
        // constructed and had the chance to register a provider.
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // NeoForge has no entrypoint discovery, so everything comes through the
        // loader-neutral registry in LiasMediaPlayerApi.
        LiasMediaPlayer.registerExternalSources(List.of());
    }
}
