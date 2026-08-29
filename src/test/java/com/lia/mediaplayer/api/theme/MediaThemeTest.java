package com.lia.mediaplayer.api.theme;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A theme's id, which is the part with rules: it is what the user's {@code config.json}
 * stores, so it has to be stable, namespaced (two addons calling their palette
 * {@code sunset} would otherwise fight over the setting) and not one of the mod's own.
 */
class MediaThemeTest {

    private static MediaTheme.Builder builder(String id) {
        return MediaTheme.builder(id, Component.literal("A theme"));
    }

    @Test
    void keepsOnlyTheRolesItWasGiven() {
        MediaTheme theme = builder("addon:sunset")
                .set(ThemeRole.FILL, 0xFFFF8A50)
                .set(ThemeRole.WINDOW_BG, 0xD0221018)
                .build();
        assertEquals(2, theme.colors().size());
        assertEquals(0xFFFF8A50, theme.colors().get(ThemeRole.FILL));
        assertNull(theme.colors().get(ThemeRole.TEXT), "an unnamed role keeps the dark value");
    }

    @Test
    void lowerCasesTheId() {
        assertEquals("addon:sunset", builder("Addon:Sunset").build().id());
    }

    @Test
    void refusesAnIdThatIsNotNamespaced() {
        assertThrows(IllegalArgumentException.class, () -> builder("sunset"));
    }

    @Test
    void refusesTheModsOwnPaletteNames() {
        // They are unnamespaced, so the rule above already covers them — stated as its
        // own case because it is the reason the rule exists.
        assertThrows(IllegalArgumentException.class, () -> builder("dark"));
        assertThrows(IllegalArgumentException.class, () -> builder("contrast"));
        assertThrows(IllegalArgumentException.class, () -> builder("minecraft"));
    }

    @Test
    void refusesABlankOrWhitespacedId() {
        assertThrows(IllegalArgumentException.class, () -> builder(""));
        assertThrows(IllegalArgumentException.class, () -> builder("   "));
        assertThrows(IllegalArgumentException.class, () -> builder(null));
        assertThrows(IllegalArgumentException.class, () -> builder("addon: sunset"));
    }

    @Test
    void setAllAddsSeveralAtOnceAndIgnoresNulls() {
        MediaTheme theme = builder("addon:x")
                .setAll(new java.util.HashMap<>() {{
                        put(ThemeRole.TEXT, 0xFFFFFFFF);
                        put(ThemeRole.ICON, null);
                    }})
                .build();
        assertEquals(1, theme.colors().size());
        assertEquals(0xFFFFFFFF, theme.colors().get(ThemeRole.TEXT));
    }

    @Test
    void aBuiltThemeCannotBeEditedThroughItsMap() {
        MediaTheme theme = builder("addon:x").set(ThemeRole.TEXT, 1).build();
        assertThrows(UnsupportedOperationException.class,
                () -> theme.colors().put(ThemeRole.ICON, 2));
    }

    @Test
    void registeringTheSameIdTwiceReplacesRatherThanDuplicates() {
        MediaThemes.register(builder("themetest:dup").set(ThemeRole.FILL, 1).build());
        MediaThemes.register(builder("themetest:dup").set(ThemeRole.FILL, 2).build());
        assertEquals(1, MediaThemes.all().stream()
                .filter(theme -> theme.id().equals("themetest:dup")).count());
        assertEquals(2, MediaThemes.byId("themetest:dup").colors().get(ThemeRole.FILL));
    }

    @Test
    void byIdIsCaseInsensitiveAndAnswersNullForAnUnknownOne() {
        MediaThemes.register(builder("themetest:known").build());
        assertTrue(MediaThemes.byId("THEMETEST:KNOWN") != null);
        assertNull(MediaThemes.byId("themetest:missing"));
        assertNull(MediaThemes.byId(null));
        assertNull(MediaThemes.byId(""));
        assertFalse(MediaThemes.all().isEmpty());
    }
}
