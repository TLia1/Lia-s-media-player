package com.lia.mediaplayer.platform.neoforge;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * The API mod entry point. This class exists solely so that NeoForge shows
 * "Lia's Media Player API" as a separate entry in the Mods menu — it carries no logic of
 * its own.
 *
 * <p>It lives here rather than on {@link LiasMediaPlayerApi} so that the {@code api}
 * package, which is what addons compile against, stays free of loader imports. Fabric has
 * no equivalent: {@code fabric.mod.json} declares {@code "provides"} instead, which makes
 * the id resolvable for dependants without a second entry in the mod list.</p>
 */
@Mod(value = LiasMediaPlayerApi.API_ID, dist = Dist.CLIENT)
public final class NeoForgeApiMod {

    public NeoForgeApiMod(IEventBus modEventBus) {
        // The API mod has no initialization logic of its own.
    }
}
