package com.lia.mediaplayer.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenorResolverTest {

    @Test
    void extractMediaUrl_WithContentUrlMeta_ReturnsUrl() {
        String html = "<html><head><meta itemprop=\"contentUrl\" content=\"https://media.tenor.com/m/ABCDEFGH123/tenor.gif\"></head><body></body></html>";
        String extracted = TenorResolver.extractMediaUrl(html);
        assertEquals("https://c.tenor.com/ABCDEFGH123/tenor.gif", extracted);
    }

    @Test
    void extractMediaUrl_WithOgImage_ReturnsUrl() {
        String html = "<html><head><meta property=\"og:image\" content=\"https://c.tenor.com/456/tenor.gif\"></head><body></body></html>";
        String extracted = TenorResolver.extractMediaUrl(html);
        assertEquals("https://c.tenor.com/456/tenor.gif", extracted);
    }

    @Test
    void extractMediaUrl_NoUrl_ReturnsNull() {
        String html = "<html><head><title>Test</title></head><body>No media here</body></html>";
        String extracted = TenorResolver.extractMediaUrl(html);
        assertNull(extracted);
    }
}
