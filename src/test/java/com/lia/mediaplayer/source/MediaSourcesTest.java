package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import com.lia.mediaplayer.api.MediaKind;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourcesTest {

    @Test
    void kindOf_ReturnsCorrectKind() {
        assertEquals(MediaKind.IMAGE, MediaSources.kindOf("https://example.com/image.png"));
        assertEquals(MediaKind.VIDEO, MediaSources.kindOf("https://example.com/video.mp4"));
        assertEquals(MediaKind.AUDIO, MediaSources.kindOf("https://example.com/audio.mp3"));
        assertEquals(MediaKind.VIDEO, MediaSources.kindOf("https://youtube.com/watch?v=123"));
        assertEquals(MediaKind.IMAGE, MediaSources.kindOf("https://tenor.com/view/123"));
        assertNull(MediaSources.kindOf("https://example.com/unknown.txt"));
    }

    @Test
    void isMethods_WorkCorrectly() {
        assertTrue(MediaSources.isImage("https://example.com/image.png"));
        assertFalse(MediaSources.isVideo("https://example.com/image.png"));
        
        assertTrue(MediaSources.isVideo("https://example.com/video.mp4"));
        assertFalse(MediaSources.isAudio("https://example.com/video.mp4"));
        
        assertTrue(MediaSources.isAudio("https://example.com/audio.mp3"));
        assertFalse(MediaSources.isImage("https://example.com/audio.mp3"));
        
        assertTrue(MediaSources.isSupported("https://example.com/image.png"));
        assertFalse(MediaSources.isSupported("https://example.com/unknown.txt"));
    }
}
