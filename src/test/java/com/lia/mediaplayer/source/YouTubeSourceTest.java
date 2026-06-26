package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeSourceTest {
    private final YouTubeSource source = new YouTubeSource();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ", // http instead of https
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/live/dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120s" // with extra query params
    })
    void matches_ValidYouTube_ReturnsTrue(String url) {
        assertTrue(source.matches(url));
        assertTrue(YouTubeSource.isYouTube(url)); // Also test the static method
    }

    @Test
    void matches_InvalidYouTube_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/video.mp4"));
        assertFalse(source.matches("https://youtube.com/"));
        assertFalse(source.matches("https://youtu.be/"));
        assertFalse(source.matches("https://www.youtube.com/playlist?list=PL1234567890"));
        assertFalse(source.matches("https://music.youtube.com/playlist?list=PL1234567890"));
        assertFalse(source.matches(null));
        assertFalse(source.matches(""));
    }

    @Test
    void kind_ReturnsVideo() {
        assertEquals(MediaKind.VIDEO, source.kind());
    }

    @Test
    void label_ReturnsYoutubeLabel() {
        assertEquals("[youtube]", source.label("https://www.youtube.com/watch?v=dQw4w9WgXcQ").getString());
    }
}
