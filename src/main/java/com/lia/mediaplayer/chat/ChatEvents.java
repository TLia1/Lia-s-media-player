package com.lia.mediaplayer.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

//? if >=1.21.5 {
/*import java.net.URI;
import java.net.URISyntaxException;
*///?}

/**
 * The mod's single point of contact with {@link ClickEvent} and {@link HoverEvent}.
 *
 * <p>1.21.5 replaced both of them: what used to be one class carrying an
 * {@code Action} enum and a {@code String} payload became a sealed interface with
 * one record per action, so {@code new ClickEvent(Action.OPEN_URL, url)} is now
 * {@code new ClickEvent.OpenUrl(uri)} and the read side pattern-matches instead of
 * comparing actions. The mod only ever builds an open-url click and a show-text
 * hover, and only ever reads an open-url click back out, so all three operations
 * live here and only this file needs a version guard.
 *
 * <p>The read side is used from the {@code gui} package, which inspects the style
 * under the cursor in the chat overlay; it is chat semantics regardless of who
 * asks, so it lives here with the write side rather than being guarded twice.
 */
public final class ChatEvents {

    private ChatEvents() {
    }

    /**
     * The click event that opens {@code url}, or {@code null} if this Minecraft
     * version cannot represent it — in which case the label is still drawn and
     * styled, it just is not clickable.
     */
    @Nullable
    public static ClickEvent openUrl(String url) {
        //? if <1.21.5 {
        return new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        //?} else {
        /*// OpenUrl holds a parsed URI rather than the raw string, so a link the
        // rewriter matched can still fail here. This runs while chat is being
        // rendered, where an exception would take the whole message down, so a
        // malformed URL degrades to no click event instead.
        try {
            return new ClickEvent.OpenUrl(new URI(url));
        } catch (URISyntaxException e) {
            return null;
        }
        *///?}
    }

    /** The hover event that shows {@code text} as a tooltip. */
    public static HoverEvent showText(Component text) {
        //? if <1.21.5 {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //?} else {
        /*return new HoverEvent.ShowText(text);
        *///?}
    }

    /**
     * The URL {@code style} opens when clicked, or {@code null} if clicking it does
     * something else or nothing at all.
     */
    @Nullable
    public static String clickedUrl(Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        //? if <1.21.5 {
        if (clickEvent == null || clickEvent.getAction() != ClickEvent.Action.OPEN_URL) {
            return null;
        }
        return clickEvent.getValue();
        //?} else {
        /*// The round trip through URI normalises the string a little (an empty path
        // gains its slash, for one), which is harmless: every consumer feeds it to
        // MediaSources, which parses rather than compares.
        return clickEvent instanceof ClickEvent.OpenUrl openUrl ? openUrl.uri().toString() : null;
        *///?}
    }
}
