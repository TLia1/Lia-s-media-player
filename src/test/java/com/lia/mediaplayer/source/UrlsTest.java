package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlsTest {

    @ParameterizedTest
    @CsvSource(value = {
        "http://example.com/PATH, /path",
        "https://example.com/Video.mp4, /video.mp4",
        "https://example.com/foo/BAR/baz, /foo/bar/baz",
        "https://example.com/, /"
    }, nullValues = {"null"})
    void pathLower_ValidUrls_ReturnsLowerCasedPath(String url, String expected) {
        assertEquals(expected, Urls.pathLower(url));
    }

    @Test
    void pathLower_EmptyPath_ReturnsEmptyString() {
        assertEquals("", Urls.pathLower("https://example.com"));
    }

    @Test
    void pathLower_WithQueryParamsAndFragments_ExtractsOnlyPath() {
        assertEquals("/video.mp4", Urls.pathLower("https://example.com/video.mp4?test=1&A=B#frag"));
    }

    @Test
    void pathLower_InvalidUrls_ReturnsNull() {
        assertNull(Urls.pathLower("not a url"));
        assertNull(Urls.pathLower(null));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "http://example.com/path, example.com",
        "https://WWW.EXAMPLE.COM/path, example.com",
        "https://www.youtube.com/watch, youtube.com",
        "https://m.youtube.com/watch, m.youtube.com",
        "https://example.com:8080/path, example.com"
    }, nullValues = {"null"})
    void hostLower_ValidUrls_ReturnsLowerCasedHostWithoutWww(String url, String expected) {
        assertEquals(expected, Urls.hostLower(url));
    }

    @Test
    void hostLower_InvalidUrls_ReturnsNull() {
        assertNull(Urls.hostLower("not a url"));
        assertNull(Urls.hostLower(null));
        assertNull(Urls.hostLower("file:///C:/test.mp4")); // no host
    }
}
