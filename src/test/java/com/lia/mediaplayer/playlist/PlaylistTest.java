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
}
