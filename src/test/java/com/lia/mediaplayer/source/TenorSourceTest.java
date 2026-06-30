package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenorSourceTest {
    private final TenorSource source = new TenorSource();

    @Test
    void matches_ValidTenor_ReturnsTrue() {
        assertTrue(source.matches("https://tenor.com/view/some-gif-12345"));
        assertTrue(source.matches("https://tenor.com/fr/view/some-gif-12345"));
        assertTrue(source.matches("http://tenor.com/view/test"));
    }

    @Test
    void matches_InvalidTenor_ReturnsFalse() {
        assertFalse(source.matches("https://tenor.com/"));
        assertFalse(source.matches("https://tenor.com/search/test"));
        assertFalse(source.matches("https://example.com/view/123"));
    }

    @Test
    void kind_ReturnsImage() {
        assertEquals(MediaKind.IMAGE, source.kind());
    }
}
