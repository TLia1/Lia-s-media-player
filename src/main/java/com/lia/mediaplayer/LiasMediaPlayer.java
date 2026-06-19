package com.lia.mediaplayer;

import com.lia.mediaplayer.api.event.MediaSourceRegistrationEvent;
import com.lia.mediaplayer.source.MediaSources;
import com.lia.mediaplayer.tools.MediaBinaries;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * Client-side companion mod for Lia's Minecraft <-> Discord bridge.
 * Replaces image links coming from the chat bridge with a hoverable
 * [picture] entry that renders an image preview over the chat, and turns
 * video/YouTube links into an in-game player.
 *
 * <p>The player shells out to two external command-line tools — yt-dlp (to
 * resolve YouTube links) and ffmpeg (to decode video/audio). Rather than
 * bundling them in the jar, we download the official builds into the game
 * folder on first launch; see {@link MediaBinaries}. Kicking that off from the
 * mod constructor means the binaries are usually ready before the first link is
 * clicked, instead of being fetched lazily mid-feature.</p>
 */
@Mod(value = LiasMediaPlayer.MODID, dist = Dist.CLIENT)
public class LiasMediaPlayer {
    // The value here should match an entry in the META-INF/neoforge.mods.toml file
    public static final String MODID = "liasmediaplayer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LiasMediaPlayer(IEventBus modEventBus) {
        // Install yt-dlp and ffmpeg in the background so they are ready when needed.
        MediaBinaries.installAllAsync();

        // Fire the source registration event during client setup so addons can
        // register their custom MediaSources.
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        MediaSourceRegistrationEvent registrationEvent = new MediaSourceRegistrationEvent();
        // Post to the mod event bus; addons that depend on the API listen for this.
        net.neoforged.fml.ModLoader.postEventWrapContainerInModOrder(registrationEvent);
        // Apply the registered sources.
        registrationEvent.getRegistered().forEach(MediaSources::register);
        LOGGER.info("Registered {} external media source(s) via API",
                registrationEvent.getRegistered().size());
    }
}
