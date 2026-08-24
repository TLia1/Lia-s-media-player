package com.lia.mediaplayer.gui;

import net.minecraft.client.Minecraft;
//? if <1.21.11
import net.minecraft.client.gui.screens.Screen;

/**
 * The mod's single point of contact with the "is a modifier held?" query.
 *
 * <p>Up to 1.21.10 these were three statics on {@link Screen}, which every mod
 * called from anywhere. 1.21.11 removed them and moved the same three checks
 * onto the {@link Minecraft} instance, so the call is now an instance call on
 * the client singleton.</p>
 *
 * <p>Both forms read the same GLFW key state; nothing about the semantics
 * changed. Like {@link Blit}, {@link TextureBridge} and {@link GuiLayer}, this
 * is a one-file, one-guard seam rather than five guarded call sites.</p>
 *
 * <p>Public, like {@link Screens} and unlike the other seams here, because the
 * callers are spread across {@code gui} and {@code input}: the "play from
 * clipboard" key binding reads the same alt/shift modifiers a click on a chat
 * link does, and the two must agree on what they mean.</p>
 */
public final class Keys {
    private Keys() {
    }

    /** True while either shift key is held. */
    public static boolean shiftDown() {
        //? if <1.21.11 {
        return Screen.hasShiftDown();
        //?} else
        /*return Minecraft.getInstance().hasShiftDown();*/
    }

    /** True while either control key is held (command on macOS). */
    public static boolean controlDown() {
        //? if <1.21.11 {
        return Screen.hasControlDown();
        //?} else
        /*return Minecraft.getInstance().hasControlDown();*/
    }

    /** True while either alt key is held. */
    public static boolean altDown() {
        //? if <1.21.11 {
        return Screen.hasAltDown();
        //?} else
        /*return Minecraft.getInstance().hasAltDown();*/
    }
}
