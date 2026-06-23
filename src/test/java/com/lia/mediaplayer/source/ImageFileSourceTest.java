package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageFileSourceTest {
    private final ImageFileSource source = new ImageFileSource();

    @Test
    void matches_ValidImages_ReturnsTrue() {
        assertTrue(source.matches("http://example.com/image.png"));
        assertTrue(source.matches("https://example.com/image.jpg"));
        assertTrue(source.matches("https://example.com/image.jpeg"));
        assertTrue(source.matches("https://example.com/image.gif"));
        assertTrue(source.matches("https://example.com/image.bmp"));
        
        // With query params or fragments
        assertTrue(source.matches("https://example.com/image.png?width=200&height=100"));
        assertTrue(source.matches("https://example.com/image.png#fragment"));
    }

    @Test
    void matches_InvalidImages_ReturnsFalse() {
        assertFalse(source.matches("https://example.com/video.mp4"));
        assertFalse(source.matches("https://example.com/"));
        assertFalse(source.matches("https://example.com/image"));
        assertFalse(source.matches("https://example.com/image.png.txt"));
    }

    @Test
    void kind_ReturnsImage() {
        assertEquals(MediaKind.IMAGE, source.kind());
    }
}
