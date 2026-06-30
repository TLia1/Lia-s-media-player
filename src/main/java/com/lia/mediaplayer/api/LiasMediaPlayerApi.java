/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The API mod entry point. This class exists solely so that NeoForge shows
 * "Lia's Media Player API" as a separate entry in the Mods menu — it carries
 * no logic of its own. All public API surfaces live in this package
 * ({@code com.lia.mediaplayer.api}).
 *
 * <p>Other mods should <b>only depend on classes in this package</b> and never
 * import anything from {@code com.lia.mediaplayer} directly.</p>
 */
@Mod(value = LiasMediaPlayerApi.API_ID, dist = Dist.CLIENT)
public class LiasMediaPlayerApi {
    /**
     * The mod ID for the API entry in neoforge.mods.toml.
     */
    public static final String API_ID = "liasmediaplayerapi";

    private static IMediaPlayerAPI instance;

    public LiasMediaPlayerApi(IEventBus modEventBus) {
        // The API mod has no initialization logic of its own.
    }

    /**
     * Retrieves the active Media Player API instance.
     *
     * @return the API instance
     * @throws IllegalStateException if called before the mod is fully initialized
     */
    public static IMediaPlayerAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Lia's Media Player API is not initialized yet.");
        }
        return instance;
    }

    /**
     * Internal method used by the main mod to inject the API implementation.
     */
    public static void setInstance(IMediaPlayerAPI api) {
        instance = api;
    }
}
