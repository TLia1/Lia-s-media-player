package com.lia.mediaplayer.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version gate addons are meant to adopt before any of the features it gates.
 */
class ApiVersionTest {

    @Test
    void asStringIsTheThreeNumbers() {
        assertEquals(ApiVersion.MAJOR + "." + ApiVersion.MINOR + "." + ApiVersion.PATCH,
                ApiVersion.asString());
    }

    @Test
    void atLeastComparesMajorFirst() {
        assertTrue(ApiVersion.atLeast(ApiVersion.MAJOR, ApiVersion.MINOR));
        assertTrue(ApiVersion.atLeast(ApiVersion.MAJOR - 1, Integer.MAX_VALUE),
                "a lower major wins however high the minor");
        assertFalse(ApiVersion.atLeast(ApiVersion.MAJOR + 1, 0));
        assertFalse(ApiVersion.atLeast(ApiVersion.MAJOR, ApiVersion.MINOR + 1));
    }

    @Test
    void everyCapabilityAnswersAgainstItsOwnSinceVersion() {
        for (Capability capability : Capability.values()) {
            assertEquals(ApiVersion.atLeast(capability.sinceMajor(), capability.sinceMinor()),
                    ApiVersion.supports(capability), capability.name());
        }
    }

    @Test
    void anUnknownCapabilityIsNotSupportedRatherThanAThrow() {
        assertFalse(ApiVersion.supports(null));
    }

    @Test
    void theCapabilitiesThisReleaseShipsAreOn() {
        assertTrue(ApiVersion.supports(Capability.HANDLES));
        assertTrue(ApiVersion.supports(Capability.HISTORY_ACCESS));
        assertTrue(ApiVersion.supports(Capability.PLAYLIST_EDITING));
        assertTrue(ApiVersion.supports(Capability.TOOLS));
        assertTrue(ApiVersion.supports(Capability.PLACEMENT));
        assertTrue(ApiVersion.supports(Capability.QUEUE_ACCESS));
        assertTrue(ApiVersion.supports(Capability.METADATA_PROVIDERS));
        assertTrue(ApiVersion.supports(Capability.RESOLVERS));
        assertTrue(ApiVersion.supports(Capability.SURFACES));
        assertTrue(ApiVersion.supports(Capability.HEADLESS_AUDIO));
        assertTrue(ApiVersion.supports(Capability.POSITIONAL_AUDIO));
        assertTrue(ApiVersion.supports(Capability.MIXER));
        assertTrue(ApiVersion.supports(Capability.INTERCEPTORS));
        assertTrue(ApiVersion.supports(Capability.THEMES));
        assertTrue(ApiVersion.supports(Capability.WINDOW_ACTIONS));
        assertTrue(ApiVersion.supports(Capability.KEYBINDS));
        assertTrue(ApiVersion.supports(Capability.SYNC));
        assertTrue(ApiVersion.supports(Capability.IMAGE_DECODERS));
        assertTrue(ApiVersion.supports(Capability.SURFACE_PIXELS));
        assertTrue(ApiVersion.supports(Capability.SCREEN_TABS));
        assertTrue(ApiVersion.supports(Capability.PLAYLIST_IMPORT_EXPORT));
        assertTrue(ApiVersion.supports(Capability.DIAGNOSTICS));
    }

    /**
     * As of 3.4 the roadmap's declared capabilities have all landed, so there is nothing
     * left for this to list. It is kept as a loop rather than deleted: the moment a
     * capability is declared ahead of its release again — which is what the enum is for —
     * the assertion above starts failing and this is where the pending one is named.
     */
    @Test
    void everyDeclaredCapabilityIsAccountedFor() {
        for (Capability capability : Capability.values()) {
            assertTrue(ApiVersion.supports(capability),
                    capability + " is declared but not shipped; list it here and say so in the roadmap");
        }
    }

    @Test
    void sinceReadsAsAVersionAddonsCanPrint() {
        assertEquals("3.1", Capability.HEADLESS_AUDIO.since());
        assertEquals("3.2", Capability.INTERCEPTORS.since());
    }
}
