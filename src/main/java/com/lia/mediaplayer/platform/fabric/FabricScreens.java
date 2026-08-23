package com.lia.mediaplayer.platform.fabric;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Fabric bridge's single point of contact with "add a widget to a screen that has
 * already been initialised".
 *
 * <p>Fabric exposes the screen's own widget list rather than an add method, and 26.1
 * renamed the accessor from {@code getButtons} to {@code getWidgets} — the list had held
 * more than buttons for a long time by then.</p>
 */
final class FabricScreens {
    private FabricScreens() {
    }

    /** Appends {@code widget} to {@code screen}'s widget list. */
    static void addWidget(Screen screen, AbstractWidget widget) {
        //? if <26.1 {
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(widget);
        //?} else
        /*net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(widget);*/
    }
}
