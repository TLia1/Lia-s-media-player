package com.lia.mediaplayer.input;

import com.lia.mediaplayer.LiasMediaPlayer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
//? if >=1.21.11
/*import net.minecraft.resources.ResourceLocation;*/

/**
 * The mod's configurable key bindings. They appear in the vanilla
 * <em>Options → Controls</em> screen under a "Lia's Media Player" category and are
 * <strong>unbound by default</strong> (so they can never clash with a vanilla or other
 * mod key out of the box — the player assigns whatever keys they like).
 *
 * <p>The bindings act on the <em>active</em> audio bar (see
 * {@link com.lia.mediaplayer.gui.AudioPlayerManager}); the actual reaction to a press
 * lives in {@link KeybindHandler}. Registering the mappings with the game is the job
 * of the per-loader bridge in {@code platform}, which is the only part of this that
 * differs between NeoForge and Fabric; this class just declares them.</p>
 */
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

    /**
     * Every mapping this mod declares, in the order they should appear in the controls
     * screen. The loader bridges iterate this rather than naming each field, so adding a
     * binding here is enough for both loaders to pick it up.
     */
    public static KeyMapping[] all() {
        return new KeyMapping[] {PLAY_PAUSE, NEXT, PREVIOUS, OPEN_PLAYLISTS};
    }
}
