package com.lia.mediaplayer.input;

import com.lia.mediaplayer.LiasMediaPlayer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
//? if >=1.21.11
/*import net.minecraft.resources.ResourceLocation;*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * The mod's configurable key bindings. They appear in the vanilla
 * <em>Options → Controls</em> screen under a "Lia's Media Player" category and are
 * <strong>unbound by default</strong> (so they can never clash with a vanilla or other
 * mod key out of the box — the player assigns whatever keys they like).
 *
 * <p>The bindings act on the <em>active</em> audio bar (see
 * {@link com.lia.mediaplayer.gui.AudioPlayerManager}); the actual reaction to a press
 * lives in {@link KeybindHandler}. Registration happens on the mod event bus via
 * {@link RegisterKeyMappingsEvent}.</p>
 */
@EventBusSubscriber(modid = LiasMediaPlayer.MODID, value = Dist.CLIENT)
public final class ModKeybinds {

    // A key mapping category used to be a bare translation key; 1.21.11 made it a
    // registered object identified by a ResourceLocation, whose label is derived
    // from that id (`key.category.<namespace>.<path>`). Both label keys ship in
    // the lang files, so whichever version is built finds its own. Threshold
    // unbisected: 1.21.9 and 1.21.10 are not targets.
    //? if <1.21.11 {
    public static final String CATEGORY = "key.categories.liasmediaplayer";
    //?} else {
    /*public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath(LiasMediaPlayer.MODID, "main"));
    *///?}

    public static final KeyMapping PLAY_PAUSE = unbound("playpause");
    public static final KeyMapping NEXT = unbound("next");
    public static final KeyMapping PREVIOUS = unbound("previous");
    public static final KeyMapping OPEN_PLAYLISTS = unbound("playlists");

    private ModKeybinds() {
    }

    private static KeyMapping unbound(String id) {
        return new KeyMapping("key.liasmediaplayer." + id,
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    }

    @SubscribeEvent
    static void onRegister(RegisterKeyMappingsEvent event) {
        // Categories are now registered objects rather than free-form strings,
        // and NeoForge wants modded ones declared before the mappings using them.
        //? if >=1.21.11
        /*event.registerCategory(CATEGORY);*/
        event.register(PLAY_PAUSE);
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(OPEN_PLAYLISTS);
    }
}
