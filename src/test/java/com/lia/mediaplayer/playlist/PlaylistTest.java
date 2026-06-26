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
}
