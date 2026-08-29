package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.gui.WindowShortcuts.Action;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The shortcut table.
 *
 * <p>Most of what matters about it is what it <em>refuses</em>: these keys are pressed
 * over the chat screen, where a table that claimed one key too many would eat a letter
 * out of someone's message. Half the cases below are that rule.</p>
 */
class WindowShortcutsTest {

    // (key, control, shift, typing)

    @Test
    void spaceTogglesPlaybackWhileNothingIsBeingTyped() {
        assertEquals(Action.PLAY_PAUSE,
                WindowShortcuts.actionFor(InputConstants.KEY_SPACE, false, false, false));
    }

    @Test
    void spaceIsLeftToTheChatBoxOnceAMessageIsStarted() {
        assertEquals(Action.NONE,
                WindowShortcuts.actionFor(InputConstants.KEY_SPACE, false, false, true));
    }

    @Test
    void arrowsSeek() {
        assertEquals(Action.SEEK_BACK,
                WindowShortcuts.actionFor(InputConstants.KEY_LEFT, false, false, false));
        assertEquals(Action.SEEK_FORWARD,
                WindowShortcuts.actionFor(InputConstants.KEY_RIGHT, false, false, false));
    }

    @Test
    void shiftSeeksFurther() {
        assertEquals(Action.SEEK_BACK_FAR,
                WindowShortcuts.actionFor(InputConstants.KEY_LEFT, false, true, false));
        assertEquals(Action.SEEK_FORWARD_FAR,
                WindowShortcuts.actionFor(InputConstants.KEY_RIGHT, false, true, false));
    }

    @Test
    void controlVerticalArrowsMoveTheVolume() {
        assertEquals(Action.VOLUME_UP,
                WindowShortcuts.actionFor(InputConstants.KEY_UP, true, false, false));
        assertEquals(Action.VOLUME_DOWN,
                WindowShortcuts.actionFor(InputConstants.KEY_DOWN, true, false, false));
    }

    @Test
    void bareVerticalArrowsAreLeftToChatHistoryRecall() {
        // Vanilla uses bare Up/Down on an empty field to recall chat history; the mod
        // must not shadow that, so volume moved behind Ctrl.
        assertEquals(Action.NONE,
                WindowShortcuts.actionFor(InputConstants.KEY_UP, false, false, false));
        assertEquals(Action.NONE,
                WindowShortcuts.actionFor(InputConstants.KEY_DOWN, false, false, false));
    }

    @Test
    void controlVerticalArrowsKeepWorkingMidMessage() {
        assertEquals(Action.VOLUME_UP,
                WindowShortcuts.actionFor(InputConstants.KEY_UP, true, false, true));
        assertEquals(Action.VOLUME_DOWN,
                WindowShortcuts.actionFor(InputConstants.KEY_DOWN, true, false, true));
    }

    @Test
    void arrowsAreLeftToTheChatBoxOnceAMessageIsStarted() {
        for (int key : new int[] {InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT,
                InputConstants.KEY_UP, InputConstants.KEY_DOWN}) {
            assertEquals(Action.NONE, WindowShortcuts.actionFor(key, false, false, true),
                    "arrow key " + key + " must not be claimed mid-message");
        }
    }

    @Test
    void controlLettersKeepWorkingMidMessage() {
        // The whole point of putting these behind control: an unfinished message is the
        // normal state of the chat screen, and these still have to work in it.
        assertEquals(Action.MUTE, WindowShortcuts.actionFor(InputConstants.KEY_M, true, false, true));
        assertEquals(Action.LOOP, WindowShortcuts.actionFor(InputConstants.KEY_L, true, false, true));
        assertEquals(Action.SHUFFLE, WindowShortcuts.actionFor(InputConstants.KEY_S, true, false, true));
        assertEquals(Action.NEXT, WindowShortcuts.actionFor(InputConstants.KEY_N, true, false, true));
        assertEquals(Action.PREVIOUS, WindowShortcuts.actionFor(InputConstants.KEY_P, true, false, true));
        assertEquals(Action.THEATER, WindowShortcuts.actionFor(InputConstants.KEY_F, true, false, true));
    }

    @Test
    void bareLettersAreNeverClaimed() {
        // Even with an empty field: the first letter of a message would be eaten.
        for (int key : new int[] {InputConstants.KEY_M, InputConstants.KEY_L, InputConstants.KEY_S,
                InputConstants.KEY_N, InputConstants.KEY_P, InputConstants.KEY_F}) {
            assertEquals(Action.NONE, WindowShortcuts.actionFor(key, false, false, false),
                    "letter " + key + " must not be claimed without a modifier");
        }
    }

    @Test
    void leavesTheChatBoxItsOwnControlShortcuts() {
        // Select-all, copy, cut and paste belong to the text field.
        for (int key : new int[] {InputConstants.KEY_A, InputConstants.KEY_C,
                InputConstants.KEY_X, InputConstants.KEY_V}) {
            assertEquals(Action.NONE, WindowShortcuts.actionFor(key, true, false, false),
                    "Ctrl+" + key + " belongs to the text field");
        }
    }

    @Test
    void leavesEscapeAlone() {
        assertEquals(Action.NONE,
                WindowShortcuts.actionFor(InputConstants.KEY_ESCAPE, false, false, false));
        assertEquals(Action.NONE,
                WindowShortcuts.actionFor(InputConstants.KEY_ESCAPE, true, false, false));
    }

    @Test
    void aModifierSuppressesTheUnmodifiedMeaning() {
        // Ctrl+Space is not "pause": the control branch answers first, and it has no
        // entry for space.
        assertNotEquals(Action.PLAY_PAUSE,
                WindowShortcuts.actionFor(InputConstants.KEY_SPACE, true, false, false));
    }
}
