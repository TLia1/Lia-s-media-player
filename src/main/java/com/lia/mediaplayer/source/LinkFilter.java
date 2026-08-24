package com.lia.mediaplayer.source;

import java.util.List;
import java.util.Locale;

/**
 * The matching rules behind the client-side link filters: does this URL come from a
 * listed host, and was this message written by a listed player.
 *
 * <p>Pure functions over strings — the lists are passed in rather than read from the
 * config — so the rules can be unit-tested without a game, and so the one place that
 * decides what "listed" means is not spread over the chat handlers. The policy built on
 * top of them (which list applies, and in which mode) lives in
 * {@link com.lia.mediaplayer.chat.MediaFilters}.</p>
 *
 * <p>Entries are expected to be lower-cased already, which
 * {@link com.lia.mediaplayer.api.config.StringOption#entries()} guarantees.</p>
 */
public final class LinkFilter {

    private LinkFilter() {
    }

    /**
     * Whether {@code url}'s host is one of {@code hosts}, or a sub-domain of one.
     *
     * <p>Sub-domains count so that one entry covers a site: {@code discordapp.com}
     * matches {@code cdn.discordapp.com}, and someone blocking a domain does not have
     * to guess which of its CDN hosts a link will arrive from. The match is on whole
     * labels, so {@code evil-discordapp.com} is <em>not</em> a sub-domain of
     * {@code discordapp.com} — a plain {@code endsWith} would say it was.</p>
     *
     * <p>A leading {@code www.} is stripped from both sides, so a list entry written
     * the way the site is spoken about still matches the link as posted.</p>
     */
    public static boolean hostMatches(String url, List<String> hosts) {
        if (hosts.isEmpty()) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null || host.isEmpty()) {
            return false;
        }
        for (String entry : hosts) {
            String listed = entry.startsWith("www.") ? entry.substring(4) : entry;
            if (listed.isEmpty()) {
                continue;
            }
            if (host.equals(listed) || host.endsWith("." + listed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code name} is one of {@code names}.
     *
     * <p>Containment rather than equality, because the name a message arrives with is
     * the sender's <em>display</em> name: on a server that prefixes ranks or teams,
     * "Steve" arrives as "[VIP] Steve" and an exact match would never fire. The cost is
     * that a short entry can catch a longer name that happens to contain it, which is
     * the right way round for a filter someone reached for on purpose.</p>
     */
    public static boolean nameMatches(String name, List<String> names) {
        if (name == null || name.isBlank() || names.isEmpty()) {
            return false;
        }
        String needle = name.toLowerCase(Locale.ROOT);
        for (String entry : names) {
            if (!entry.isEmpty() && needle.contains(entry)) {
                return true;
            }
        }
        return false;
    }
}
