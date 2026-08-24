package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sources added for the page-based sites, and the two things that decide whether one
 * of them is really "free": that it claims the links it should and none it should not,
 * and that it declares whether the extractor has to see the link first.
 */
class NewSourcesTest {

    private MediaSources sources;

    @BeforeEach
    void setUp() {
        sources = new MediaSources();
    }

    // ------------------------------------------------------------------
    // Registration: what the registry as a whole answers
    // ------------------------------------------------------------------

    @Test
    void theRegistryClaimsTheNewSites_WithTheRightKind() {
        assertEquals(MediaKind.AUDIO, sources.kindOf("https://soundcloud.com/artist/track"));
        assertEquals(MediaKind.AUDIO, sources.kindOf("https://artist.bandcamp.com/track/song"));
        assertEquals(MediaKind.VIDEO, sources.kindOf("https://vimeo.com/123456789"));
        assertEquals(MediaKind.VIDEO, sources.kindOf("https://streamable.com/abc123"));
        assertEquals(MediaKind.VIDEO, sources.kindOf("https://v.redd.it/abcdef"));
        assertEquals(MediaKind.IMAGE, sources.kindOf("https://giphy.com/gifs/funny-cat-l0HlKrB02QY0f1mbm"));
    }

    @Test
    void everyNewPageSourceAsksForTheExtractor_AndTheFileSourcesDoNot() {
        assertTrue(sources.requiresExtractor("https://soundcloud.com/artist/track"));
        assertTrue(sources.requiresExtractor("https://artist.bandcamp.com/album/record"));
        assertTrue(sources.requiresExtractor("https://vimeo.com/123456789"));
        assertTrue(sources.requiresExtractor("https://streamable.com/abc123"));
        assertTrue(sources.requiresExtractor("https://v.redd.it/abcdef"));
        // The ones that were already there keep their answers.
        assertTrue(sources.requiresExtractor("https://youtube.com/watch?v=123"));
        assertTrue(sources.requiresExtractor("https://twitch.tv/somebody"));
        // A file ffmpeg can open, and a link nothing claims at all.
        assertFalse(sources.requiresExtractor("https://example.com/video.mp4"));
        assertFalse(sources.requiresExtractor("https://example.com/page.html"));
    }

    @Test
    void theNewSourcesStillRefuseNonHttpLinks() {
        assertNull(sources.kindOf("file://soundcloud.com/artist/track"));
        assertNull(sources.kindOf("ftp://vimeo.com/123456789"));
        assertNull(sources.kindOf("--config-location=https://streamable.com/abc123"));
    }

    // ------------------------------------------------------------------
    // The individual rules, where the interesting rejections live
    // ------------------------------------------------------------------

    @Test
    void soundCloud_NeedsSomethingToPlay() {
        assertTrue(SoundCloudSource.isSoundCloud("https://m.soundcloud.com/artist/track"));
        assertTrue(SoundCloudSource.isSoundCloud("https://on.soundcloud.com/abcdef"));
        assertFalse(SoundCloudSource.isSoundCloud("https://soundcloud.com/"));
        assertFalse(SoundCloudSource.isSoundCloud("https://soundcloudx.com/artist/track"));
    }

    @Test
    void bandcamp_ClaimsTracksAndAlbums_ButNotAnArtistsLandingPage() {
        assertTrue(BandcampSource.isBandcamp("https://artist.bandcamp.com/track/song"));
        assertTrue(BandcampSource.isBandcamp("https://artist.bandcamp.com/album/record"));
        assertFalse(BandcampSource.isBandcamp("https://artist.bandcamp.com/"));
        assertFalse(BandcampSource.isBandcamp("https://artist.bandcamp.com/merch"));
    }

    @Test
    void vimeo_NeedsANumericVideoId() {
        assertTrue(VimeoSource.isVimeo("https://vimeo.com/123456789"));
        assertTrue(VimeoSource.isVimeo("https://vimeo.com/123456789/abcdef0123"));
        assertTrue(VimeoSource.isVimeo("https://player.vimeo.com/video/123456789"));
        assertFalse(VimeoSource.isVimeo("https://vimeo.com/channels/staffpicks"));
        assertFalse(VimeoSource.isVimeo("https://vimeo.com/upgrade"));
        assertFalse(VimeoSource.isVimeo("https://player.vimeo.com/123456789"));
    }

    @Test
    void streamable_ClaimsAShortIdAndNotTheSitesOwnPages() {
        assertTrue(StreamableSource.isStreamable("https://streamable.com/abc123"));
        assertTrue(StreamableSource.isStreamable("https://streamable.com/e/abc123"));
        assertFalse(StreamableSource.isStreamable("https://streamable.com/"));
        assertFalse(StreamableSource.isStreamable("https://streamable.com/a-very-long-path-here"));
    }

    @Test
    void reddit_ClaimsVideosAndCommentPages_ButNotListings() {
        assertTrue(RedditVideoSource.isRedditVideo("https://v.redd.it/abcdef"));
        assertTrue(RedditVideoSource.isRedditVideo("https://www.reddit.com/r/aww/comments/abc123/a_cat/"));
        assertTrue(RedditVideoSource.isRedditVideo("https://old.reddit.com/r/aww/comments/abc123/a_cat/"));
        assertFalse(RedditVideoSource.isRedditVideo("https://www.reddit.com/r/aww/"));
        assertFalse(RedditVideoSource.isRedditVideo("https://www.reddit.com/user/somebody"));
    }

    @Test
    void giphy_RewritesAPageToTheGifBehindIt() {
        assertEquals("https://i.giphy.com/media/l0hlkrb02qy0f1mbm/giphy.gif",
                GiphySource.directGif("https://giphy.com/gifs/funny-cat-l0HlKrB02QY0f1mbm"));
        assertEquals("https://i.giphy.com/media/l0hlkrb02qy0f1mbm/giphy.gif",
                GiphySource.directGif("https://giphy.com/stickers/l0HlKrB02QY0f1mbm"));
    }

    @Test
    void giphy_LeavesAlonePagesWithNoMediaIdInThem() {
        assertNull(GiphySource.directGif("https://giphy.com/gifs/cats"));
        assertNull(GiphySource.directGif("https://giphy.com/explore/cat"));
        assertNull(GiphySource.directGif("https://giphy.com/"));
        // A direct GIF is ImageFileSource's, not this one's.
        assertNull(GiphySource.directGif("https://media.giphy.com/media/abc123/giphy.gif"));
    }
}
