package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.window.ActionIcon;
import com.lia.mediaplayer.api.window.WindowAction;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The addon half of a window's corner button row — which registered
 * {@link WindowAction}s apply to a window right now, what each one's icon looks like,
 * and the guard rail around calling into somebody else's code from inside a draw.
 *
 * <p>It is a class of its own rather than three helpers on {@link WindowChrome} because
 * every method here is about the <em>addon</em> boundary: each one either caps a list,
 * translates a name into one of {@link Glyphs}' drawings, or swallows an exception that
 * must not reach the render loop. {@code WindowChrome} draws what it is told and should
 * not also be the place that decides what an addon is allowed to do.</p>
 *
 * <p>Render thread only.</p>
 */
final class WindowActions {

    private WindowActions() {
    }

    /**
     * The actions to show on {@code handle}'s window: those that claim it, in
     * registration order, at most {@link WindowAction#MAX_VISIBLE}.
     *
     * <p>The cap is the window's, not the registry's: the button row is packed
     * right-to-left from the window's edge and its width is part of a window's
     * <em>minimum</em> width, so an unbounded row would push a small player's title out
     * entirely and then draw over whatever was beside it. Registrations past the cap keep
     * their place in the list and appear if an earlier one stops applying.</p>
     *
     * <p>An action that throws from {@code appliesTo} is treated as not applying, and
     * logged: this is called every frame, from inside a draw, so it is not somewhere an
     * addon's exception may propagate.</p>
     */
    static List<WindowAction> applicable(MediaHandle handle) {
        List<WindowAction> registered = LiasMediaPlayerApi.windowActions();
        if (registered.isEmpty() || handle == null) {
            return Collections.emptyList();
        }
        List<WindowAction> shown = new ArrayList<>(WindowAction.MAX_VISIBLE);
        for (WindowAction action : registered) {
            if (shown.size() >= WindowAction.MAX_VISIBLE) {
                break;
            }
            try {
                if (action.appliesTo(handle)) {
                    shown.add(action);
                }
            } catch (RuntimeException e) {
                LiasMediaPlayer.LOGGER.error("Window action {} threw from appliesTo", safeId(action), e);
            }
        }
        return shown;
    }

    /** Runs an action's click, with the same guard. */
    static void click(WindowAction action, MediaHandle handle) {
        try {
            action.onClick(handle);
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.error("Window action {} threw from onClick", safeId(action), e);
        }
    }

    /** An action's icon, or {@code null} if asking for it threw. */
    static ActionIcon icon(WindowAction action) {
        try {
            return action.icon();
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.error("Window action {} threw from icon", safeId(action), e);
            return null;
        }
    }

    /** An action's tooltip, or {@code null} if asking for it threw. */
    static Component tooltip(WindowAction action) {
        try {
            return action.tooltip();
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.error("Window action {} threw from tooltip", safeId(action), e);
            return null;
        }
    }

    /**
     * Draws one action's icon at the size and in the colour the rest of the row uses.
     *
     * <p>The switch is the whole reason {@link ActionIcon} is a closed enum: the mod's
     * icons are drawn from primitives rather than blitted from an atlas, so there is no
     * sprite id an addon could have handed over instead — and a name from a fixed list is
     * what keeps an addon's button looking like the mod's own in every theme.</p>
     */
    static void draw(GuiGraphics g, ActionIcon icon, int x, int y, int color) {
        if (icon == null) {
            return;
        }
        switch (icon) {
            case HEART -> Glyphs.heart(g, x, y, color);
            case COPY -> Glyphs.copy(g, x, y, color);
            case EXTERNAL_LINK -> Glyphs.externalLink(g, x, y, color);
            case ADD_TO_PLAYLIST -> Glyphs.addToPlaylist(g, x, y, color);
            case QUEUE -> Glyphs.queue(g, x, y, color);
            case SEARCH -> Glyphs.search(g, x, y, color);
            case REFRESH -> Glyphs.refresh(g, x, y, color);
            case TRASH -> Glyphs.trash(g, x, y, color);
            case PIN -> Glyphs.pin(g, x, y, color);
            case NOTE -> Glyphs.note(g, x, y, color);
            case STOP -> Glyphs.stop(g, x, y, color);
            case FULLSCREEN -> Glyphs.fullscreen(g, x, y, color);
            case SHUFFLE -> Glyphs.shuffle(g, x, y, color);
            case SPEED -> Glyphs.speed(g, x, y, color);
            case ARROW_UP -> Glyphs.arrow(g, x, y, true, color);
            case ARROW_DOWN -> Glyphs.arrow(g, x, y, false, color);
        }
    }

    /** An id to name in a log line, even from an action whose {@code id()} is what threw. */
    private static String safeId(WindowAction action) {
        try {
            return action.id();
        } catch (RuntimeException ignored) {
            return action.getClass().getName();
        }
    }
}
