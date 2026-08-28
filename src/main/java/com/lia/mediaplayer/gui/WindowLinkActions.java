package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.source.ShareLink;
import com.lia.mediaplayer.source.Urls;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The three things a window's corner buttons do with the link behind it: keep it, open
 * it in a browser, or put it on the clipboard.
 *
 * <p>They have nothing to do with drawing a window or laying one out — each is a short
 * conversation with something outside the mod (the history store, the OS handler, the
 * clipboard) about a URL that came out of chat, which is why they are here rather than
 * on {@code MediaWindow}. The URL gate is the reason they are worth grouping: every one
 * of these hands the string to something that would happily act on a {@code file:} or a
 * {@code javascript:} link, and the answer in all three cases is the same
 * {@link Urls#isHttp} check.</p>
 *
 * <p>One instance per window, holding only the moment of the last copy — the clipboard
 * is somewhere else, so the button has to say for itself that it did something.</p>
 */
final class WindowLinkActions {

    /**
     * How long the copy button keeps saying it copied something.
     */
    private static final int COPIED_MS = 1600;

    /** When the copy button last put something on the clipboard. */
    private long copiedAt;

    // ------------------------------------------------------------------
    // Favourites
    // ------------------------------------------------------------------

    boolean isFavorite(String url) {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        return context != null && context.getHistoryStore().isFavorite(url);
    }

    void toggleFavorite(String url) {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context != null) {
            context.getHistoryStore().toggleFavorite(url, kindOf(url));
        }
    }

    /**
     * Which player this URL belongs to, for the history entry the heart creates. Asked
     * of the source registry rather than hard-coded per window type, so an addon's own
     * source lands in the library under its own kind.
     */
    private static MediaKind kindOf(String url) {
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        return context == null ? null : context.getMediaSources().kindOf(url);
    }

    // ------------------------------------------------------------------
    // The browser
    // ------------------------------------------------------------------

    /**
     * Opens the media's source URL in the system browser.
     */
    void openInBrowser(String url) {
        // openUri hands the string to the OS handler (xdg-open / FileProtocolHandler /
        // open), which happily launches whatever protocol is registered for it. The URL
        // originates from a chat component, so only ever pass on a real http(s) link.
        if (Urls.isHttp(url)) {
            Util.getPlatform().openUri(url);
        }
    }

    // ------------------------------------------------------------------
    // The clipboard
    // ------------------------------------------------------------------

    /**
     * Puts the media's link on the clipboard — plainly, or with the moment currently
     * playing written into it.
     *
     * <p>The timestamp is the whole point of the button existing next to the browser
     * one: "have a look at this video" is a link anybody can already copy out of chat,
     * and "have a look at <em>this bit</em>" is not. It is behind {@code Shift} rather
     * than being a button of its own because it is the same action on the same link:
     * a second glyph to aim at would say there were two things to copy.</p>
     *
     * @param positionMicros where playback has got to, or {@code -1} for a window with
     *                       no clock behind it
     * @param atPosition     write that position into the link, where the site has a way
     *                       of saying it
     */
    void copyLink(String url, long positionMicros, boolean atPosition) {
        if (!Urls.isHttp(url)) {
            return; // the same gate openInBrowser applies, for the same reason
        }
        String copied = atPosition ? timestamped(url, positionMicros) : url;
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(copied);
        } catch (RuntimeException e) {
            return; // no clipboard on this platform; saying "copied" would be a lie
        }
        copiedAt = Anim.now();
    }

    /**
     * {@code url} with the playback position in it, or {@code url} itself when there is
     * no position to write (a pinned image, a live stream at the start) or no way to
     * write it for this site.
     */
    private static String timestamped(String url, long positionMicros) {
        if (positionMicros <= 0) {
            return url;
        }
        return ShareLink.atSeconds(url, positionMicros / 1_000_000L);
    }

    /**
     * What the copy button says: that it just copied something, what {@code Shift} would
     * add, or — where the site cannot express a position — plainly what it does.
     */
    Component copyTooltip(String url, long positionMicros) {
        if (Anim.progress(copiedAt, COPIED_MS) < 1.0) {
            return Component.translatable("gui.liasmediaplayer.control.copy_link.done");
        }
        if (positionMicros <= 0 || !ShareLink.supportsTimestamp(url)) {
            return Component.translatable("gui.liasmediaplayer.control.copy_link");
        }
        String at = ShareLink.clockTime(positionMicros / 1_000_000L);
        return Component.translatable(Keys.shiftDown()
                ? "gui.liasmediaplayer.control.copy_link.at"
                : "gui.liasmediaplayer.control.copy_link.shift", at);
    }
}
