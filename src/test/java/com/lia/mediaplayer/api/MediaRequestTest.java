package com.lia.mediaplayer.api;

import com.lia.mediaplayer.api.window.Anchor;
import com.lia.mediaplayer.api.window.Placement;
import com.lia.mediaplayer.api.window.Sizing;
import com.lia.mediaplayer.api.window.WindowChromeOptions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builder's defaults and its gate. Both matter more than they look: the defaults are
 * the promise that {@code play(MediaRequest.of(url))} behaves like a chat click, and the
 * gate is the API layer's share of keeping a {@code file:} URL away from ffmpeg.
 */
class MediaRequestTest {

    private static final String URL = "https://example.com/clip.mp4";

    @Test
    void theDefaultsReproduceAChatClick() {
        MediaRequest request = MediaRequest.of(URL);
        assertEquals(URL, request.url());
        assertEquals(List.of(URL), request.urls());
        assertNull(request.kind(), "the registered sources decide unless told otherwise");
        assertFalse(request.isNewWindow(), "a link queues into the front-most player");
        assertTrue(request.placement().isRemembered());
        assertEquals(Sizing.auto(), request.sizing());
        assertEquals(0L, request.startMicros());
        assertTrue(request.isAutoplay());
        assertFalse(request.isShuffle());
        assertNull(request.title());
        assertEquals(WindowChromeOptions.full(), request.chrome());
        assertTrue(request.isCloseWhenEnded());
    }

    @Test
    void geometryPersistenceIsOffByDefaultForAnApiOpenedWindow() {
        // windows.json is keyed by window *kind*, so an addon that parks a player
        // somewhere would otherwise overwrite where the user likes their own to open.
        assertFalse(MediaRequest.of(URL).isPersistGeometry());
        assertTrue(MediaRequest.of(URL).persistGeometry(true).isPersistGeometry());
    }

    @Test
    void ofRejectsAnythingThatIsNotAnHttpUrlWithAHost() {
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of(null));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of(""));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("   "));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("concat:a|b"));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("https:///clip.mp4"));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("example.com/clip.mp4"));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of("-i"));
    }

    @Test
    void ofAcceptsBothSchemesWhateverTheCase() {
        assertEquals("HTTP://example.com/a.mp4", MediaRequest.of("HTTP://example.com/a.mp4").url());
        assertEquals("http://example.com/a.mp4", MediaRequest.of("http://example.com/a.mp4").url());
    }

    @Test
    void ofAllDropsWhatItCannotUseAndRefusesAnEmptyResult() {
        MediaRequest request = MediaRequest.ofAll(List.of("file:///x.mp4", URL, "nonsense"));
        assertEquals(List.of(URL), request.urls());

        assertThrows(IllegalArgumentException.class, () -> MediaRequest.ofAll(List.of()));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.ofAll(null));
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.ofAll(List.of("file:///x.mp4")));
    }

    @Test
    void negativeStartIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MediaRequest.of(URL).startAt(-1));
    }

    @Test
    void nullPlacementSizingAndChromeFallBackToTheDefaultsRatherThanThrowing() {
        MediaRequest request = MediaRequest.of(URL)
                .placement(null).sizing(null).chrome(null);
        assertTrue(request.placement().isRemembered());
        assertEquals(Sizing.auto(), request.sizing());
        assertEquals(WindowChromeOptions.full(), request.chrome());
    }

    @Test
    void theSettersChainOnOneObject() {
        MediaRequest request = MediaRequest.of(URL);
        assertSame(request, request.newWindow(true).shuffle(true).autoplay(false));
        assertTrue(request.isNewWindow());
        assertTrue(request.isShuffle());
        assertFalse(request.isAutoplay());
    }

    @Test
    void copyIsIndependentOfItsTemplate() {
        MediaRequest template = MediaRequest.of(URL)
                .newWindow(true)
                .placement(Placement.anchored(Anchor.TOP_RIGHT, 4, 4))
                .sizing(Sizing.contentWidth(320))
                .as(MediaKind.AUDIO);
        MediaRequest copy = template.copy();

        assertEquals(template.placement(), copy.placement());
        assertEquals(template.sizing(), copy.sizing());
        assertEquals(MediaKind.AUDIO, copy.kind());

        copy.newWindow(false).as(null).sizing(Sizing.auto());
        assertTrue(template.isNewWindow(), "the template must not have moved");
        assertEquals(MediaKind.AUDIO, template.kind());
        assertEquals(Sizing.contentWidth(320), template.sizing());
    }

    @Test
    void urlsIsASnapshotTheCallerCannotEdit() {
        MediaRequest request = MediaRequest.of(URL);
        assertThrows(UnsupportedOperationException.class, () -> request.urls().add(URL));
    }
}
