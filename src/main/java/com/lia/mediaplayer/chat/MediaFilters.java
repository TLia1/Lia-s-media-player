package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.source.FilterMode;
import com.lia.mediaplayer.source.LinkFilter;
import org.jetbrains.annotations.Nullable;

/**
 * The client-side policy on <em>whose</em> links get rewritten and <em>which</em> ones.
 *
 * <p>Until now the mod rewrote every media link from everyone: on a public server that
 * is the one thing a client-side media player can be used to annoy people with. These
 * two questions are asked before a link becomes clickable:</p>
 *
 * <ul>
 *   <li>{@link #allowsSender} — the whole message is left alone when it comes from a
 *       listed player, so nothing they post is offered at all.</li>
 *   <li>{@link #allowsUrl} — a single link is left as plain text when its host fails
 *       the host lists.</li>
 * </ul>
 *
 * <p>Everything here is <strong>purely local</strong>: a filtered link is still in the
 * message and still says what it says, it just is not turned into a label the mod will
 * play. Nothing is hidden from the player and no message is cancelled — that would be
 * a chat filter, which is a different thing and not this mod's business.</p>
 *
 * <p>The filters apply to <em>chat</em> only. Playing a link the player put on their own
 * clipboard, or typed into a playlist, is deliberately not filtered: the lists exist to
 * govern what other people can put in front of you.</p>
 */
public final class MediaFilters {

    private MediaFilters() {
    }

    /**
     * Whether messages from {@code senderName} should be scanned for links at all.
     *
     * <p>{@code null} — a system message, or a loader that could not say who sent it —
     * is always allowed: there is no sender to have blocked.</p>
     */
    public static boolean allowsSender(@Nullable String senderName) {
        if (senderName == null) {
            return true;
        }
        return !LinkFilter.nameMatches(senderName, ConfigStore.BLOCKED_SENDERS.entries());
    }

    /**
     * Whether a link from {@code url}'s host may be turned into a playable label.
     */
    public static boolean allowsUrl(String url) {
        return switch (ConfigStore.LINK_FILTER_MODE.getValue()) {
            case OFF -> true;
            case BLOCKLIST -> !LinkFilter.hostMatches(url, ConfigStore.BLOCKED_DOMAINS.entries());
            // An empty allow-list under ALLOWLIST really does mean "nothing", which is
            // the honest reading of the mode and visible the moment it is switched on.
            case ALLOWLIST -> LinkFilter.hostMatches(url, ConfigStore.ALLOWED_DOMAINS.entries());
        };
    }

    /**
     * The mode as configured, exposed so a caller can tell "no filtering at all" from
     * "filtering that happens to allow this one".
     */
    public static FilterMode mode() {
        return ConfigStore.LINK_FILTER_MODE.getValue();
    }
}
