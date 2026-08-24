package com.lia.mediaplayer.history;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The library's rules, driven directly.
 *
 * <p>No game is needed: the store's file path comes from {@code Minecraft.getInstance()},
 * which has none here, and every file access is written to give up quietly on that — so
 * the store runs entirely in memory and the list behaviour is exactly what is under
 * test. The serialization is checked through the two static methods, which is why they
 * take and return plain values rather than reading the file themselves.</p>
 */
class HistoryStoreTest {

    private static final String A = "https://example.com/a.mp4";
    private static final String B = "https://example.com/b.mp3";

    @Test
    void add_PutsTheMostRecentFirst() {
        HistoryStore store = new HistoryStore();
        store.add(A, MediaKind.VIDEO);
        store.add(B, MediaKind.AUDIO);

        List<HistoryEntry> all = store.all();
        assertEquals(2, all.size());
        assertEquals(B, all.get(0).url());
        assertEquals(A, all.get(1).url());
    }

    @Test
    void add_MovesAKnownEntryBackToTheTopRatherThanDuplicatingIt() {
        HistoryStore store = new HistoryStore();
        store.add(A, MediaKind.VIDEO);
        store.add(B, MediaKind.AUDIO);
        store.add(A, MediaKind.VIDEO);

        List<HistoryEntry> all = store.all();
        assertEquals(2, all.size());
        assertEquals(A, all.get(0).url());
    }

    @Test
    void add_KeepsTheHeartOnAnEntryPlayedAgain() {
        HistoryStore store = new HistoryStore();
        store.add(A, MediaKind.VIDEO);
        store.toggleFavorite(A, MediaKind.VIDEO);
        store.add(A, MediaKind.VIDEO);

        assertTrue(store.isFavorite(A));
    }

    @Test
    void add_IgnoresWhatCouldNeverHaveBeenPlayed() {
        HistoryStore store = new HistoryStore();
        store.add("file:///etc/passwd.mp3", MediaKind.AUDIO);
        store.add(A, null);

        assertTrue(store.all().isEmpty());
    }

    @Test
    void toggleFavorite_AddsAnEntryThatIsNotThereYet() {
        // Favouriting from a window whose history entry has already scrolled off the end
        // has to have somewhere to put it.
        HistoryStore store = new HistoryStore();

        assertTrue(store.toggleFavorite(A, MediaKind.VIDEO));
        assertEquals(1, store.favorites().size());
        assertFalse(store.toggleFavorite(A, MediaKind.VIDEO));
        assertTrue(store.favorites().isEmpty());
    }

    @Test
    void clear_KeepsTheFavourites() {
        HistoryStore store = new HistoryStore();
        store.add(A, MediaKind.VIDEO);
        store.add(B, MediaKind.AUDIO);
        store.toggleFavorite(A, MediaKind.VIDEO);

        store.clear();

        assertEquals(List.of(A), store.all().stream().map(HistoryEntry::url).toList());
    }

    @Test
    void theBoundAppliesToOrdinaryEntriesOnly() {
        HistoryStore store = new HistoryStore();
        store.add(A, MediaKind.VIDEO);
        store.toggleFavorite(A, MediaKind.VIDEO);
        for (int i = 0; i < HistoryStore.MAX_ENTRIES + 20; i++) {
            store.add("https://example.com/" + i + ".mp4", MediaKind.VIDEO);
        }

        assertEquals(HistoryStore.MAX_ENTRIES + 1, store.all().size());
        assertTrue(store.isFavorite(A));
    }

    @Test
    void json_RoundTrips() {
        List<HistoryEntry> entries = List.of(
                new HistoryEntry(A, MediaKind.VIDEO, 1234L, true),
                new HistoryEntry(B, MediaKind.AUDIO, 5678L, false));

        List<HistoryEntry> parsed = HistoryStore.fromJson(HistoryStore.toJson(entries));

        assertEquals(entries, parsed);
    }

    @Test
    void json_SkipsWhatItCannotTrust() {
        String text = """
                [
                  {"url": "file:///etc/passwd.mp3", "kind": "AUDIO"},
                  {"url": "https://example.com/a.mp4", "kind": "NOT_A_KIND"},
                  {"kind": "VIDEO"},
                  "not an object",
                  {"url": "https://example.com/a.mp4", "kind": "VIDEO"}
                ]
                """;

        List<HistoryEntry> parsed = HistoryStore.fromJson(text);

        assertEquals(1, parsed.size());
        assertEquals(A, parsed.get(0).url());
        // Missing fields fall back rather than dropping the entry.
        assertEquals(0L, parsed.get(0).playedAt());
        assertFalse(parsed.get(0).favorite());
    }

    @Test
    void json_SurvivesAFileThatIsNotJsonAtAll() {
        assertTrue(HistoryStore.fromJson("{ not json").isEmpty());
        assertTrue(HistoryStore.fromJson("").isEmpty());
    }
}
