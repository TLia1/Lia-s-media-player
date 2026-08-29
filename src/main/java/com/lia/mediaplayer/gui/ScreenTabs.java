package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.screen.MediaScreenTab;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * The addon buttons in the library screens' footer — the implementation of
 * {@code api.screen.MediaScreenTab}.
 *
 * <p><b>Buttons, not tabs.</b> The playlist and history screens are not a tabbed pair,
 * and turning them into one to host this would be a redesign of the mod's own UI for a
 * feature that is asking for a way <em>in</em>. An addon gets what a tab would have given
 * it: a labelled way from the library to its own screen, with the screen it came from to
 * go back to. That difference is written down in {@code MediaScreenTab}'s javadoc and in
 * the roadmap, rather than left for someone to discover.</p>
 *
 * <p>They sit to the right of "Done", in registration order, at most
 * {@link MediaScreenTab#MAX_VISIBLE}: the footer is one row and an unbounded list would
 * run off the edge of a narrow window.</p>
 */
final class ScreenTabs {

    /** The footer row's button size, matching "Done"'s height. */
    private static final int BUTTON_W = 72;
    private static final int BUTTON_H = 20;
    private static final int GAP = 4;

    private ScreenTabs() {
    }

    /**
     * Adds a button per registered tab to {@code parent}'s footer.
     *
     * @param leftX  where the row starts — just right of the screen's own footer buttons
     * @param y      the row's top, the same as "Done"'s
     * @param add    how the screen adds a widget; the two library screens are ordinary
     *               {@code Screen}s, but {@code addRenderableWidget} is protected there,
     *               so the caller passes it in
     */
    static void addTo(Screen parent, int leftX, int y, Consumer<AbstractWidget> add) {
        List<MediaScreenTab> tabs = LiasMediaPlayerApi.screenTabs();
        int shown = 0;
        for (MediaScreenTab tab : tabs) {
            if (shown >= MediaScreenTab.MAX_VISIBLE) {
                break;
            }
            AbstractWidget widget = build(parent, tab, leftX + shown * (BUTTON_W + GAP), y);
            if (widget != null) {
                add.accept(widget);
                shown++;
            }
        }
    }

    /**
     * One tab's button, or {@code null} if asking the tab for its own label threw —
     * a broken addon loses its button, not the screen.
     */
    private static AbstractWidget build(Screen parent, MediaScreenTab tab, int x, int y) {
        try {
            Component title = tab.title();
            Button.Builder builder = Button.builder(title, b -> open(parent, tab))
                    .bounds(x, y, BUTTON_W, BUTTON_H);
            Component tooltip = tab.tooltip();
            if (tooltip != null) {
                builder.tooltip(Tooltip.create(tooltip));
            }
            return builder.build();
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.error("Media screen tab {} threw while building its button",
                    safeId(tab), e);
            return null;
        }
    }

    private static void open(Screen parent, MediaScreenTab tab) {
        try {
            Screen screen = tab.open(parent);
            if (screen != null) {
                Screens.open(screen);
            }
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.error("Media screen tab {} threw from open", safeId(tab), e);
        }
    }

    private static String safeId(MediaScreenTab tab) {
        try {
            return tab.id();
        } catch (RuntimeException ignored) {
            return tab.getClass().getName();
        }
    }
}
