package com.lia.mediaplayer.api.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that keeps a window shortcut from eating a keystroke, and the modifier
 * matching under it.
 *
 * <p>These keys are pressed over the chat screen, where the text field has the focus, so
 * an unmodified letter is not a shortcut — it is a character somebody was typing. The API
 * throws rather than documenting it, because an addon author who gets this wrong finds
 * out from a player who could not say "next" in chat.
 */
class MediaKeybindsTest {

    private static final WindowShortcutAction NOTHING = handle -> false;

    // ------------------------------------------------------------------
    // The bare-letter rule
    // ------------------------------------------------------------------

    @Test
    void refusesAnUnmodifiedLetter() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaKeybinds.registerWindowShortcut(InputConstants.KEY_N, 0, NOTHING));
        assertThrows(IllegalArgumentException.class,
                () -> MediaKeybinds.registerWindowShortcut(InputConstants.KEY_A, 0, NOTHING));
        assertThrows(IllegalArgumentException.class,
                () -> MediaKeybinds.registerWindowShortcut(InputConstants.KEY_Z, 0, NOTHING));
    }

    @Test
    void refusesAnUnmodifiedDigit() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaKeybinds.registerWindowShortcut(InputConstants.KEY_0, 0, NOTHING));
        assertThrows(IllegalArgumentException.class,
                () -> MediaKeybinds.registerWindowShortcut(InputConstants.KEY_9, 0, NOTHING));
    }

    @Test
    void allowsALetterOnceItCarriesAModifier() {
        assertDoesNotThrow(() -> MediaKeybinds.registerWindowShortcut(
                InputConstants.KEY_J, MediaKeybinds.MOD_CONTROL, NOTHING));
    }

    @Test
    void allowsAnUnmodifiedKeyThatIsNotATypingKey() {
        // F-keys and the like are not what a chat field is collecting.
        assertDoesNotThrow(() -> MediaKeybinds.registerWindowShortcut(
                InputConstants.KEY_F9, 0, NOTHING));
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    @Test
    void matchesOnlyTheExactModifierCombination() {
        MediaKeybinds.Shortcut shortcut = new MediaKeybinds.Shortcut(
                InputConstants.KEY_J, MediaKeybinds.MOD_CONTROL, NOTHING);
        assertTrue(shortcut.matches(InputConstants.KEY_J, true, false, false));
        assertFalse(shortcut.matches(InputConstants.KEY_J, false, false, false),
                "the modifier is part of the shortcut, not an optional extra");
        assertFalse(shortcut.matches(InputConstants.KEY_J, true, true, false),
                "an extra modifier is a different chord");
        assertFalse(shortcut.matches(InputConstants.KEY_K, true, false, false));
    }

    @Test
    void combinesModifiers() {
        MediaKeybinds.Shortcut shortcut = new MediaKeybinds.Shortcut(InputConstants.KEY_J,
                MediaKeybinds.MOD_CONTROL | MediaKeybinds.MOD_SHIFT, NOTHING);
        assertTrue(shortcut.matches(InputConstants.KEY_J, true, true, false));
        assertFalse(shortcut.matches(InputConstants.KEY_J, true, false, false));
    }

    @Test
    void anUnmodifiedShortcutMatchesOnlyWithNoModifiersHeld() {
        MediaKeybinds.Shortcut shortcut =
                new MediaKeybinds.Shortcut(InputConstants.KEY_F9, 0, NOTHING);
        assertTrue(shortcut.matches(InputConstants.KEY_F9, false, false, false));
        assertFalse(shortcut.matches(InputConstants.KEY_F9, true, false, false));
        assertFalse(shortcut.matches(InputConstants.KEY_F9, false, false, true));
    }

    @Test
    void registeringNothingIsHarmless() {
        assertDoesNotThrow(() -> MediaKeybinds.register(null, minecraft -> {
        }));
        assertDoesNotThrow(() -> MediaKeybinds.registerWindowShortcut(
                InputConstants.KEY_F10, 0, null));
    }
}
