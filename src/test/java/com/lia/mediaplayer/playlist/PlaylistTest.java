package com.lia.mediaplayer.playlist;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistTest {

    @Test
    void createPlaylist_HasCorrectValues() {
        Playlist playlist = new Playlist("My Playlist");
        assertEquals("My Playlist", playlist.name());
        assertTrue(playlist.urls().isEmpty());

        playlist.urls().add("http://example.com/audio.mp3");
        assertEquals(1, playlist.urls().size());
        assertEquals("http://example.com/audio.mp3", playlist.urls().get(0));
    }

    @Test
    void setName_UpdatesName() {
        Playlist playlist = new Playlist("Old Name");
        playlist.setName("New Name");
        assertEquals("New Name", playlist.name());
    }

    @Test
    void add_AndSizeAndIsEmpty_WorkCorrectly() {
        Playlist playlist = new Playlist("Test");
        assertTrue(playlist.isEmpty());
        assertEquals(0, playlist.size());

        playlist.add("url1");
        assertFalse(playlist.isEmpty());
        assertEquals(1, playlist.size());
        assertEquals("url1", playlist.urls().get(0));
    }

    @Test
    void removeAt_RemovesElementIfInBounds() {
        Playlist playlist = new Playlist("Test");
        playlist.add("url1");
        playlist.add("url2");

        playlist.removeAt(0);
        assertEquals(1, playlist.size());
        assertEquals("url2", playlist.urls().get(0));
    }

    @Test
    void removeAt_OutOfBounds_DoesNothing() {
        Playlist playlist = new Playlist("Test");
        playlist.add("url1");

        playlist.removeAt(-1);
        playlist.removeAt(5);
        assertEquals(1, playlist.size());
        assertEquals("url1", playlist.urls().get(0));
    }

    @Test
    void swap_SwapsElementsIfInBounds() {
        Playlist playlist = new Playlist("Test");
        playlist.add("url1");
        playlist.add("url2");
        playlist.add("url3");

        playlist.swap(0, 2);
        assertEquals("url3", playlist.urls().get(0));
        assertEquals("url2", playlist.urls().get(1));
        assertEquals("url1", playlist.urls().get(2));
    }

    @Test
    void swap_OutOfBounds_DoesNothing() {
        Playlist playlist = new Playlist("Test");
        playlist.add("url1");
        playlist.add("url2");

        playlist.swap(-1, 1);
        playlist.swap(0, 5);

        assertEquals("url1", playlist.urls().get(0));
        assertEquals("url2", playlist.urls().get(1));
    }

    // ------------------------------------------------------------------
    // move: what a drag-and-drop reorder does
    // ------------------------------------------------------------------

    private static Playlist of(String... urls) {
        Playlist playlist = new Playlist("Test");
        for (String url : urls) {
            playlist.add(url);
        }
        return playlist;
    }

    @Test
    void move_DownwardsAccountsForTheGapTheEntryLeaves() {
        Playlist playlist = of("a", "b", "c", "d");
        // "put a in the gap before d", read against the list as it is now.
        playlist.move(0, 3);
        assertEquals(List.of("b", "c", "a", "d"), playlist.urls());
    }

    @Test
    void move_UpwardsInsertsAtTheGapItself() {
        Playlist playlist = of("a", "b", "c", "d");
        playlist.move(3, 1);
        assertEquals(List.of("a", "d", "b", "c"), playlist.urls());
    }

    @Test
    void move_PastTheEndPutsTheEntryLast() {
        Playlist playlist = of("a", "b", "c");
        playlist.move(0, 3);
        assertEquals(List.of("b", "c", "a"), playlist.urls());
    }

    @Test
    void move_IntoItsOwnGapChangesNothing() {
        Playlist playlist = of("a", "b", "c");
        playlist.move(1, 1);
        playlist.move(1, 2);
        assertEquals(List.of("a", "b", "c"), playlist.urls());
    }

    @Test
    void move_FromOutOfBoundsDoesNothing() {
        Playlist playlist = of("a", "b");
        playlist.move(-1, 0);
        playlist.move(2, 0);
        assertEquals(List.of("a", "b"), playlist.urls());
    }

    @Test
    void move_ADropPastTheBottomOfTheListLandsAtTheEnd() {
        // The drop gap is clamped, not refused: dragging below the last row means "last".
        Playlist playlist = of("a", "b", "c");
        playlist.move(0, 99);
        assertEquals(List.of("b", "c", "a"), playlist.urls());
    }
}
