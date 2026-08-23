package com.lia.mediaplayer.platform.fabric;

import com.lia.mediaplayer.input.ModKeybinds;
import net.minecraft.client.KeyMapping;

/**
 * The Fabric bridge's single point of contact with key-binding registration.
 *
 * <p>26.1 moved the helper along with the rest of the official-names rename:
 * {@code fabric-key-binding-api-v1}'s {@code KeyBindingHelper.registerKeyBinding} became
 * {@code fabric-key-mapping-api-v1}'s {@code KeyMappingHelper.registerKeyMapping}. Same
 * call, new module and new name.</p>
 *
 * <p>Unlike NeoForge, Fabric has nothing to register the 1.21.11+ {@code KeyMapping.Category}
 * with: the category is a plain record carried by the mapping, and the controls screen
 * groups by whatever it finds there. Declaring the mappings is enough.</p>
 */
final class FabricKeyMappings {
    private FabricKeyMappings() {
    }

    /** Registers every mapping {@link ModKeybinds} declares. */
    static void register() {
        for (KeyMapping mapping : ModKeybinds.all()) {
            //? if <26.1 {
            net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
            //?} else
            /*net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);*/
        }
    }
}
