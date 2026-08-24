package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShareLink}: writing the current position into a link so it can be pasted back
 * into chat. Every rule here is one site's spelling of a timestamp, and getting one
 * wrong produces a link that looks right and silently starts from the beginning — which
 * is exactly the kind of thing a test catches and a play-through does not.
 */
class ShareLinkTest {

    @Test
    void aWatchLinkGainsATimeParameter() {
        assertEquals("https://www.youtube.com/watch?v=abc&t=137s",
                ShareLink.atSeconds("https://www.youtube.com/watch?v=abc", 137));
    }

    @Test
    void aShortLinkGetsItsFirstQueryParameter() {
        assertEquals("https://youtu.be/abc?t=90s",
                ShareLink.atSeconds("https://youtu.be/abc", 90));
    }

    @Test
    void anExistingTimeIsReplacedRatherThanRepeated() {
        assertEquals("https://www.youtube.com/watch?v=abc&t=200s",
                ShareLink.atSeconds("https://www.youtube.com/watch?v=abc&t=5s", 200));
    }

    @Test
    void theOtherParametersAreKeptInOrder() {
        assertEquals("https://www.youtube.com/watch?v=abc&list=PL1&t=12s",
                ShareLink.atSeconds("https://www.youtube.com/watch?v=abc&list=PL1", 12));
    }

    @Test
    void aFragmentStaysAtTheEnd() {
        assertEquals("https://www.youtube.com/watch?v=abc&t=30s#anchor",
                ShareLink.atSeconds("https://www.youtube.com/watch?v=abc#anchor", 30));
    }

    @Test
    void anEmbedLinkUsesStartInsteadOfT() {
        // t= is ignored by the embedded player; start= is the parameter it reads.
        assertEquals("https://www.youtube.com/embed/abc?start=45",
                ShareLink.atSeconds("https://www.youtube.com/embed/abc", 45));
    }

    @Test
    void twitchWantsItsDurationSpelledOut() {
        assertEquals("https://www.twitch.tv/videos/123?t=1h02m03s",
                ShareLink.atSeconds("https://www.twitch.tv/videos/123", 3723));
        assertEquals("2m05s", ShareLink.twitchTime(125));
        assertEquals("9s", ShareLink.twitchTime(9));
    }

    @Test
    void vimeoAndSoundCloudUseTheFragment() {
        assertEquals("https://vimeo.com/12345#t=60s",
                ShareLink.atSeconds("https://vimeo.com/12345", 60));
        assertEquals("https://soundcloud.com/artist/track#t=1:05",
                ShareLink.atSeconds("https://soundcloud.com/artist/track", 65));
    }

    @Test
    void aSiteWithNoTimestampFormIsLeftExactlyAsItWas() {
        String url = "https://example.com/clip.mp4";
        assertFalse(ShareLink.supportsTimestamp(url));
        assertEquals(url, ShareLink.atSeconds(url, 42));
    }

    @Test
    void theStartOfATrackIsNotWorthSaying() {
        String url = "https://www.youtube.com/watch?v=abc";
        assertEquals(url, ShareLink.atSeconds(url, 0));
        assertEquals(url, ShareLink.atSeconds(url, -1));
    }

    @Test
    void theTimestampedSitesAreTheOnesWithASpelling() {
        assertTrue(ShareLink.supportsTimestamp("https://youtu.be/abc"));
        assertTrue(ShareLink.supportsTimestamp("https://www.twitch.tv/videos/1"));
        assertTrue(ShareLink.supportsTimestamp("https://vimeo.com/12345"));
        assertTrue(ShareLink.supportsTimestamp("https://soundcloud.com/artist/track"));
        assertFalse(ShareLink.supportsTimestamp("https://i.imgur.com/a.png"));
        assertFalse(ShareLink.supportsTimestamp("not a url"));
    }

    @Test
    void aClockReadingGrowsAnHoursFieldOnlyWhenItNeedsOne() {
        assertEquals("0:07", ShareLink.clockTime(7));
        assertEquals("2:05", ShareLink.clockTime(125));
        assertEquals("1:02:03", ShareLink.clockTime(3723));
    }
}
