package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @ParameterizedTest
    @CsvSource({
            "http://example.com/video.mp4",
            "https://example.com/video.mp4",
            "HTTPS://EXAMPLE.COM/video.mp4",
            "https://example.com:8080/video.mp4",
            "https://my_cdn.example.com/video.mp4"
    })
    void isHttp_HttpUrls_ReturnsTrue(String url) {
        assertTrue(Urls.isHttp(url));
    }

    @ParameterizedTest
    @CsvSource({
            "file:///C:/secret.mp4",
            "ftp://example.com/video.mp4",
            "concat:/etc/passwd",
            "'--config-location=/tmp/evil.mp4'",
            "'-i /etc/passwd'",
            "/local/path/video.mp4",
            "example.com/video.mp4",
            "'https:///video.mp4'",
            "'not a url'",
            "''"
    })
    void isHttp_EverythingElse_ReturnsFalse(String url) {
        assertFalse(Urls.isHttp(url));
    }

    @Test
    void isHttp_Null_ReturnsFalse() {
        assertFalse(Urls.isHttp(null));
    }

    @Test
    void hostLower_InvalidUrls_ReturnsNull() {
        assertNull(Urls.hostLower("not a url"));
        assertNull(Urls.hostLower(null));
        assertNull(Urls.hostLower("file:///C:/test.mp4")); // no host
    }
}
