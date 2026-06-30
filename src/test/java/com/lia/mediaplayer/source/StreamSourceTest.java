package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamSourceTest {
    private final StreamSource source = new StreamSource();

    @Test
    void matches_ValidStreams_ReturnsTrue() {
        assertTrue(source.matches("http://example.com/stream.m3u8"));
        assertTrue(source.matches("https://example.com/stream.mpd"));
        assertTrue(source.matches("https://example.com/live.m3u8?token=123"));
        assertTrue(source.matches("https://example.com/live.mpd#frag"));
    }

    @Test
    void matches_InvalidStreams_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/video.mp4"));
        assertFalse(source.matches("https://example.com/"));
        assertFalse(source.matches("https://example.com/stream.m3u8.txt"));
        assertFalse(source.matches(null));
    }

    @Test
    void kind_ReturnsVideo() {
        assertEquals(MediaKind.VIDEO, source.kind());
    }

    @Test
    void label_ReturnsVideoLabel() {
        assertEquals("[video]", source.label("https://example.com/stream.m3u8").getString());
    }

    @Test
    void isStream_StaticMethod_WorksCorrectly() {
        assertTrue(StreamSource.isStream("https://example.com/stream.m3u8"));
        assertTrue(StreamSource.isStream("https://example.com/stream.mpd"));
        assertFalse(StreamSource.isStream("https://example.com/video.mp4"));
        assertFalse(StreamSource.isStream(null));
    }
}
