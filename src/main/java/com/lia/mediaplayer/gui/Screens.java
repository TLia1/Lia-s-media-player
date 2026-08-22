package com.lia.mediaplayer.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's single point of contact with "which screen is open, and open this
 * one instead".
 *
 * <p>Up to 26.1 the open screen belonged to {@link Minecraft}: a public
 * {@code screen} field and a {@code setScreen} method. 26.2 moved both onto
 * {@code Minecraft.gui}, which became the owner of the screen stack —
 * {@code gui.screen()} and {@code gui.setScreen(...)}.</p>
 *
 * <p>{@code Minecraft.setScreenAndShow} also exists on 26.2 and looks like the
 * closer match by name, but it is not: it forces a frame to be rendered
 * immediately, which is what the loading sequence needs and not what opening a
 * config screen means. {@code gui.setScreen} is the direct replacement.</p>
 *
 * <p>Public, unlike the other seams in this package, because the callers are
 * spread across {@code gui} and {@code input}.</p>
 */
public final class Screens {
    private Screens() {
    }

    /** The screen currently open, or {@code null} if the game is in-world. */
    @Nullable
    public static Screen current() {
        Minecraft mc = Minecraft.getInstance();
        //? if <26.2 {
        return mc.screen;
        //?} else
        /*return mc.gui.screen();*/
    }

    /** Opens {@code screen}, or closes the current one when given {@code null}. */
    public static void open(@Nullable Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        //? if <26.2 {
        mc.setScreen(screen);
        //?} else
        /*mc.gui.setScreen(screen);*/
    }
}
