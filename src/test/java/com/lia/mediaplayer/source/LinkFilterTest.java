package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matching rules behind the link filters. Both of them are the kind of thing that
 * looks obviously right and is not: a plain {@code endsWith} on hosts lets
 * {@code evil-discordapp.com} through a {@code discordapp.com} entry, and an exact match
 * on names never fires on a server that prefixes ranks.
 */
class LinkFilterTest {

    @Test
    void hostMatches_TheHostItself() {
        assertTrue(LinkFilter.hostMatches("https://example.com/a.mp4", List.of("example.com")));
    }

    @Test
    void hostMatches_ASubDomain() {
        assertTrue(LinkFilter.hostMatches("https://cdn.discordapp.com/x/y.mp4", List.of("discordapp.com")));
    }

    @Test
    void hostMatches_IgnoresWww_OnEitherSide() {
        assertTrue(LinkFilter.hostMatches("https://www.example.com/a.mp4", List.of("example.com")));
        assertTrue(LinkFilter.hostMatches("https://example.com/a.mp4", List.of("www.example.com")));
    }

    @Test
    void hostMatches_RejectsAHostThatMerelyEndsWithTheEntry() {
        assertFalse(LinkFilter.hostMatches("https://evil-discordapp.com/x.mp4", List.of("discordapp.com")));
    }

    @Test
    void hostMatches_RejectsADifferentHost() {
        assertFalse(LinkFilter.hostMatches("https://example.org/a.mp4", List.of("example.com")));
    }

    @Test
    void hostMatches_IsFalseForAnEmptyListAndForAnUnparseableUrl() {
        assertFalse(LinkFilter.hostMatches("https://example.com/a.mp4", List.of()));
        assertFalse(LinkFilter.hostMatches("not a url", List.of("example.com")));
    }

    @Test
    void nameMatches_TheNameItself_CaseInsensitively() {
        assertTrue(LinkFilter.nameMatches("Steve", List.of("steve")));
    }

    @Test
    void nameMatches_ThroughAServerPrefix() {
        assertTrue(LinkFilter.nameMatches("[VIP] Steve", List.of("steve")));
    }

    @Test
    void nameMatches_RejectsAnotherPlayer() {
        assertFalse(LinkFilter.nameMatches("Alex", List.of("steve")));
    }

    @Test
    void nameMatches_IsFalseForNothingToMatchOn() {
        assertFalse(LinkFilter.nameMatches(null, List.of("steve")));
        assertFalse(LinkFilter.nameMatches("  ", List.of("steve")));
        assertFalse(LinkFilter.nameMatches("Steve", List.of()));
    }
}
