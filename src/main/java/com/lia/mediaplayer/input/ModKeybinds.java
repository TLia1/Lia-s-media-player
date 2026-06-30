package com.lia.mediaplayer.input;

import com.lia.mediaplayer.LiasMediaPlayer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
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

    public static final String CATEGORY = "key.categories.liasmediaplayer";

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
        event.register(PLAY_PAUSE);
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(OPEN_PLAYLISTS);
    }
}
