package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YouTubePlaylistSourceTest {

    private final YouTubePlaylistSource source = new YouTubePlaylistSource();

    @Test
    void matches_PlaylistPages() {
        assertTrue(source.matches("https://www.youtube.com/playlist?list=PL1234567890"));
        assertTrue(source.matches("https://youtube.com/playlist?list=PL1234567890"));
        assertTrue(source.matches("https://m.youtube.com/playlist?list=PL1234567890"));
        assertTrue(source.matches("https://music.youtube.com/playlist?list=OLAK5uy_1234"));
        assertTrue(source.matches("https://www.youtube.com/playlist?app=desktop&list=PL123"));
    }

    @Test
    void matches_RejectsPlaylistPagesWithoutAList() {
        assertFalse(source.matches("https://www.youtube.com/playlist"));
        assertFalse(source.matches("https://www.youtube.com/playlist?list="));
        assertFalse(source.matches("https://www.youtube.com/playlist?v=abc"));
    }

    @Test
    void matches_LeavesSingleVideosToYouTubeSource() {
        // A watch link inside a playlist still opens that one video on YouTube, so it
        // stays a single video here too — the two sources must not both claim it.
        String watchInPlaylist = "https://www.youtube.com/watch?v=abc123&list=PL1234567890";
        assertFalse(source.matches(watchInPlaylist));
        assertTrue(YouTubeSource.isYouTube(watchInPlaylist));

        assertFalse(source.matches("https://youtu.be/abc123"));
        assertFalse(source.matches("https://www.youtube.com/watch?v=abc123"));
    }

    @Test
    void matches_RejectsOtherHostsAndSchemes() {
        assertFalse(source.matches("https://example.com/playlist?list=PL123"));
        assertFalse(source.matches("https://notyoutube.com/playlist?list=PL123"));
        // Never let anything that isn't a real http(s) link reach yt-dlp.
        assertFalse(source.matches("file:///playlist?list=PL123"));
        assertFalse(source.matches("--config-location=/tmp/evil"));
        assertFalse(source.matches(null));
        assertFalse(source.matches(""));
    }

    @Test
    void kindAndLabel() {
        assertEquals(MediaKind.VIDEO, source.kind());
        assertEquals("chat.liasmediaplayer.label.youtube_playlist",
                source.label("https://www.youtube.com/playlist?list=PL123").getString());
    }

    @Test
    void registry_RoutesPlaylistPagesToThisSource() {
        MediaSources sources = new MediaSources();
        String playlist = "https://www.youtube.com/playlist?list=PL1234567890";

        assertEquals(MediaKind.VIDEO, sources.kindOf(playlist));
        assertInstanceOf(YouTubePlaylistSource.class, sources.find(playlist).orElseThrow());
        assertInstanceOf(YouTubeSource.class,
                sources.find("https://www.youtube.com/watch?v=abc123&list=PL123").orElseThrow());
    }
}
