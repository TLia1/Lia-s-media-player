package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectVideoSourceTest {
    private final DirectVideoSource source = new DirectVideoSource();

    @Test
    void matches_ValidVideos_ReturnsTrue() {
        assertTrue(source.matches("http://example.com/video.mp4"));
        assertTrue(source.matches("https://example.com/video.webm"));
        assertTrue(source.matches("https://example.com/video.mov"));
        assertTrue(source.matches("https://example.com/video.mkv"));
        assertTrue(source.matches("https://example.com/video.m4v"));
        assertTrue(source.matches("https://example.com/video.avi"));
        assertTrue(source.matches("https://example.com/video.flv"));
        assertTrue(source.matches("https://example.com/video.ogv"));
        assertTrue(source.matches("https://example.com/video.ts"));

        // With query params
        assertTrue(source.matches("https://example.com/video.mp4?hd=1"));
    }

    @Test
    void matches_InvalidVideos_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/image.png"));
        assertFalse(source.matches("https://example.com/video.mp4.zip"));
        assertFalse(source.matches("https://example.com/"));
    }

    @Test
    void kind_ReturnsVideo() {
        assertEquals(MediaKind.VIDEO, source.kind());
    }
}
