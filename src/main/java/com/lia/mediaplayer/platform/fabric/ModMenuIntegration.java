package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.gui.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Puts the mod's config screen behind the wrench button in ModMenu's mod list — Fabric's
 * counterpart to NeoForge's {@code IConfigScreenFactory}, which is a loader feature there
 * and a third-party mod here.
 *
 * <p>ModMenu is an <b>optional</b> dependency: it is compiled against but never required
 * at runtime. Fabric only loads a {@code modmenu} entrypoint when ModMenu itself asks for
 * it, so this class is simply never touched when it is absent. Nothing is lost either
 * way — the same screen is reachable from the pause menu button and from a key binding.</p>
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
