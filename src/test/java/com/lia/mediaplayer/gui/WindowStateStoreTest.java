package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.gui.WindowStateStore.State;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code windows.json} format.
 *
 * <p>This file outlives the version of the mod that wrote it, so the reader has to
 * survive whatever it is handed — a field that was not there yet, a value of the wrong
 * type, a hand-edit that broke the syntax. Every one of those has to end in the default
 * state rather than an exception, because the alternative is a mod that will not start
 * until a preferences file is deleted.</p>
 */
class WindowStateStoreTest {

    private static final State ARRANGED =
            new State(true, 120, 64, true, 480, true, RepeatMode.ALL, true);

    @Test
    void roundTripsAnArrangedWindow() {
        Map<String, State> in = new LinkedHashMap<>();
        in.put(WindowStateStore.VIDEO, ARRANGED);

        Map<String, State> out = WindowStateStore.fromJson(WindowStateStore.toJson(in));

        assertEquals(ARRANGED, out.get(WindowStateStore.VIDEO));
    }

    @Test
    void roundTripsEveryKindAtOnce() {
        Map<String, State> in = new LinkedHashMap<>();
        in.put(WindowStateStore.IMAGE, new State(true, 1, 2, false, 0, false, RepeatMode.OFF, false));
        in.put(WindowStateStore.VIDEO, ARRANGED);
        in.put(WindowStateStore.AUDIO, new State(false, 0, 0, true, 254, false, RepeatMode.ONE, false));

        Map<String, State> out = WindowStateStore.fromJson(WindowStateStore.toJson(in));

        assertEquals(in, out);
    }

    @Test
    void readsBackAnEmptyDocument() {
        assertTrue(WindowStateStore.fromJson("{}").isEmpty());
    }

    @Test
    void survivesMalformedJson() {
        assertTrue(WindowStateStore.fromJson("{not json at all").isEmpty());
        assertTrue(WindowStateStore.fromJson("").isEmpty());
    }

    @Test
    void ignoresEntriesThatAreNotObjects() {
        Map<String, State> out = WindowStateStore.fromJson("{\"video\": 7, \"audio\": null}");
        assertTrue(out.isEmpty());
    }

    @Test
    void fillsInFieldsAnOlderVersionDidNotWrite() {
        // A file from before the queue panel and loop mode were remembered.
        Map<String, State> out = WindowStateStore.fromJson(
                "{\"video\": {\"placed\": true, \"x\": 10, \"y\": 20}}");

        State video = out.get(WindowStateStore.VIDEO);
        assertEquals(new State(true, 10, 20, false, 0, false, RepeatMode.OFF, false), video);
    }

    @Test
    void fallsBackOnAValueOfTheWrongType() {
        Map<String, State> out = WindowStateStore.fromJson(
                "{\"video\": {\"x\": \"left\", \"repeat\": \"SIDEWAYS\", \"placed\": true}}");

        State video = out.get(WindowStateStore.VIDEO);
        assertEquals(State.DEFAULT.x(), video.x());
        assertEquals(State.DEFAULT.repeat(), video.repeat());
        assertTrue(video.placed(), "the fields that did parse are still kept");
    }

    @Test
    void aDefaultStateSaysTheWindowWasNeverArranged() {
        // What the rest of the mod keys off: false here means "use the configured
        // default position and the auto-fit size", not "use 0, 0".
        assertEquals(false, State.DEFAULT.placed());
        assertEquals(false, State.DEFAULT.sized());
    }
}
