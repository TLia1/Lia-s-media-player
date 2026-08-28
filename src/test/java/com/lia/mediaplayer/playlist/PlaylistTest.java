package com.lia.mediaplayer.playlist;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaylistTest {

    /**
     * {@link Playlist#add} only stores real {@code http(s)} links, so the one-letter names
     * the reorder cases read best with are turned into URLs on the way in and back into
     * names on the way out. {@link #u} and {@link #urls} are the two halves of that.
     */
    private static String u(String name) {
        return "https://example.com/" + name + ".mp3";
    }

    private static List<String> urls(String... names) {
        return Arrays.stream(names).map(PlaylistTest::u).toList();
    }

    private static Playlist of(String... names) {
        Playlist playlist = new Playlist("Test");
        for (String name : names) {
            playlist.add(u(name));
        }
        return playlist;
    }

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

        playlist.add(u("a"));
        assertFalse(playlist.isEmpty());
        assertEquals(1, playlist.size());
        assertEquals(u("a"), playlist.urls().get(0));
    }

    /**
     * A playlist entry is handed to ffmpeg/yt-dlp on a later session, so it gets the same
     * gate a chat link gets — anything that is not an absolute http(s) URL is dropped.
     */
    @Test
    void add_IgnoresAnythingThatIsNotAnHttpLink() {
        Playlist playlist = new Playlist("Test");
        playlist.add("file:///etc/passwd");
        playlist.add("concat:a|b");
        playlist.add("-i");
        playlist.add("not a url");
        playlist.add("");
        playlist.add(null);
        assertTrue(playlist.isEmpty());

        playlist.add(u("a"));
        assertEquals(List.of(u("a")), playlist.urls());
    }

    @Test
    void removeAt_RemovesElementIfInBounds() {
        Playlist playlist = of("a", "b");

        playlist.removeAt(0);
        assertEquals(1, playlist.size());
        assertEquals(u("b"), playlist.urls().get(0));
    }

    @Test
    void removeAt_OutOfBounds_DoesNothing() {
        Playlist playlist = of("a");

        playlist.removeAt(-1);
        playlist.removeAt(5);
        assertEquals(1, playlist.size());
        assertEquals(u("a"), playlist.urls().get(0));
    }

    @Test
    void swap_SwapsElementsIfInBounds() {
        Playlist playlist = of("a", "b", "c");

        playlist.swap(0, 2);
        assertEquals(urls("c", "b", "a"), playlist.urls());
    }

    @Test
    void swap_OutOfBounds_DoesNothing() {
        Playlist playlist = of("a", "b");

        playlist.swap(-1, 1);
        playlist.swap(0, 5);

        assertEquals(urls("a", "b"), playlist.urls());
    }

    // ------------------------------------------------------------------
    // move: what a drag-and-drop reorder does
    // ------------------------------------------------------------------

    @Test
    void move_DownwardsAccountsForTheGapTheEntryLeaves() {
        Playlist playlist = of("a", "b", "c", "d");
        // "put a in the gap before d", read against the list as it is now.
        playlist.move(0, 3);
        assertEquals(urls("b", "c", "a", "d"), playlist.urls());
    }

    @Test
    void move_UpwardsInsertsAtTheGapItself() {
        Playlist playlist = of("a", "b", "c", "d");
        playlist.move(3, 1);
        assertEquals(urls("a", "d", "b", "c"), playlist.urls());
    }

    @Test
    void move_PastTheEndPutsTheEntryLast() {
        Playlist playlist = of("a", "b", "c");
        playlist.move(0, 3);
        assertEquals(urls("b", "c", "a"), playlist.urls());
    }

    @Test
    void move_IntoItsOwnGapChangesNothing() {
        Playlist playlist = of("a", "b", "c");
        playlist.move(1, 1);
        playlist.move(1, 2);
        assertEquals(urls("a", "b", "c"), playlist.urls());
    }

    @Test
    void move_FromOutOfBoundsDoesNothing() {
        Playlist playlist = of("a", "b");
        playlist.move(-1, 0);
        playlist.move(2, 0);
        assertEquals(urls("a", "b"), playlist.urls());
    }

    @Test
    void move_ADropPastTheBottomOfTheListLandsAtTheEnd() {
        // The drop gap is clamped, not refused: dragging below the last row means "last".
        Playlist playlist = of("a", "b", "c");
        playlist.move(0, 99);
        assertEquals(urls("b", "c", "a"), playlist.urls());
    }
}
