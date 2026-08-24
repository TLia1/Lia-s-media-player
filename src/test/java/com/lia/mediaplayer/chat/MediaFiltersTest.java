package com.lia.mediaplayer.chat;

import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.source.FilterMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The policy the three modes describe, with the lists driven straight through the config
 * options — which is also a check that {@code StringOption} splits what is typed into it
 * the way the matchers expect.
 */
class MediaFiltersTest {

    @AfterEach
    void tearDown() {
        // The options are static; a mode left set here would decide the next test.
        ConfigStore.LINK_FILTER_MODE.resetToDefault();
        ConfigStore.BLOCKED_DOMAINS.resetToDefault();
        ConfigStore.ALLOWED_DOMAINS.resetToDefault();
        ConfigStore.BLOCKED_SENDERS.resetToDefault();
    }

    @Test
    void offAllowsEverything_EvenWithListsFilledIn() {
        ConfigStore.BLOCKED_DOMAINS.setValue("example.com");

        assertTrue(MediaFilters.allowsUrl("https://example.com/a.mp4"));
    }

    @Test
    void blocklistRefusesOnlyWhatIsListed() {
        ConfigStore.LINK_FILTER_MODE.setValue(FilterMode.BLOCKLIST);
        ConfigStore.BLOCKED_DOMAINS.setValue(" Example.COM , other.net ");

        assertFalse(MediaFilters.allowsUrl("https://example.com/a.mp4"));
        assertFalse(MediaFilters.allowsUrl("https://cdn.example.com/a.mp4"));
        assertTrue(MediaFilters.allowsUrl("https://elsewhere.org/a.mp4"));
    }

    @Test
    void allowlistAcceptsOnlyWhatIsListed_AndAnEmptyOneAcceptsNothing() {
        ConfigStore.LINK_FILTER_MODE.setValue(FilterMode.ALLOWLIST);
        ConfigStore.ALLOWED_DOMAINS.setValue("example.com");

        assertTrue(MediaFilters.allowsUrl("https://example.com/a.mp4"));
        assertFalse(MediaFilters.allowsUrl("https://elsewhere.org/a.mp4"));

        ConfigStore.ALLOWED_DOMAINS.setValue("");
        assertFalse(MediaFilters.allowsUrl("https://example.com/a.mp4"));
    }

    @Test
    void theTwoHostListsAreIndependent() {
        // Switching mode must not read the other mode's list, which is why both are kept.
        ConfigStore.BLOCKED_DOMAINS.setValue("example.com");
        ConfigStore.LINK_FILTER_MODE.setValue(FilterMode.ALLOWLIST);

        assertFalse(MediaFilters.allowsUrl("https://example.com/a.mp4"));
    }

    @Test
    void senderFilteringIsIndependentOfTheHostMode() {
        // The mode governs the host lists; a blocked sender is blocked whatever it says.
        ConfigStore.BLOCKED_SENDERS.setValue("griefer");

        assertFalse(MediaFilters.allowsSender("Griefer"));
        assertFalse(MediaFilters.allowsSender("[VIP] griefer"));
        assertTrue(MediaFilters.allowsSender("Steve"));
    }

    @Test
    void aMessageWithNoSenderIsAlwaysAllowed() {
        ConfigStore.BLOCKED_SENDERS.setValue("griefer");

        assertTrue(MediaFilters.allowsSender(null));
    }
}
