package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YouTubeSourceTest {
    private final YouTubeSource source = new YouTubeSource();

    @Test
    void matches_ValidYouTube_ReturnsTrue() {
        assertTrue(source.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(source.matches("https://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(source.matches("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(source.matches("https://music.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(source.matches("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(source.matches("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
        assertTrue(source.matches("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertTrue(source.matches("https://www.youtube.com/live/dQw4w9WgXcQ"));
    }

    @Test
    void matches_InvalidYouTube_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/video.mp4"));
        assertFalse(source.matches("https://youtube.com/"));
        assertFalse(source.matches("https://youtu.be/"));
    }

    @Test
    void kind_ReturnsVideo() {
        assertEquals(MediaKind.VIDEO, source.kind());
    }
}
