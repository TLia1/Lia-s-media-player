package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioFileSourceTest {
    private final AudioFileSource source = new AudioFileSource();

    @Test
    void matches_ValidAudio_ReturnsTrue() {
        assertTrue(source.matches("http://example.com/audio.mp3"));
        assertTrue(source.matches("https://example.com/audio.wav"));
        assertTrue(source.matches("https://example.com/audio.ogg"));
        assertTrue(source.matches("https://example.com/audio.oga"));
        assertTrue(source.matches("https://example.com/audio.flac"));
        assertTrue(source.matches("https://example.com/audio.m4a"));
        assertTrue(source.matches("https://example.com/audio.aac"));
        assertTrue(source.matches("https://example.com/audio.opus"));
        assertTrue(source.matches("https://example.com/audio.weba"));
        assertTrue(source.matches("https://example.com/audio.wma"));
        assertTrue(source.matches("https://example.com/audio.aiff"));
        assertTrue(source.matches("https://example.com/audio.aif"));
        
        // With query params
        assertTrue(source.matches("https://example.com/audio.mp3?bitrate=320"));
    }

    @Test
    void matches_InvalidAudio_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/video.mp4"));
        assertFalse(source.matches("https://example.com/audio.mp3.exe"));
        assertFalse(source.matches("https://example.com/"));
    }

    @Test
    void kind_ReturnsAudio() {
        assertEquals(MediaKind.AUDIO, source.kind());
    }
}
