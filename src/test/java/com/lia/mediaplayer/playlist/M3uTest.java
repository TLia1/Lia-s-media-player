package com.lia.mediaplayer.playlist;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * m3u in both directions.
 *
 * <p>The parser is the half that matters: an m3u file is a list of <em>paths</em>, and a
 * list of paths is exactly the shape that would hand {@code ffmpeg} a {@code file:} URL
 * if the gate were forgotten. The export is tested for the one thing that can actually
 * break a file — a newline smuggled into a title through the {@code #EXTINF} line.
 */
class M3uTest {

    private static Playlist playlistOf(String... urls) {
        Playlist playlist = new Playlist("test");
        for (String url : urls) {
            playlist.add(url);
        }
        return playlist;
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    void readsTheUrlsOfAnExtendedM3u() {
        String content = """
                #EXTM3U
                #EXTINF:-1,First song
                https://example.com/a.mp3
                #EXTINF:-1,Second song
                https://example.com/b.mp3
                """;
        assertEquals(List.of("https://example.com/a.mp3", "https://example.com/b.mp3"),
                M3u.parse(content));
    }

    @Test
    void readsAPlainM3uWithNoDirectives() {
        assertEquals(List.of("https://example.com/a.mp3"),
                M3u.parse("https://example.com/a.mp3\n"));
    }

    @Test
    void dropsEverythingThatIsNotAnHttpUrl() {
        String content = """
                #EXTM3U
                /home/lia/music/track.mp3
                file:///etc/passwd
                C:\\Users\\lia\\track.mp3
                concat:a.mp3|b.mp3
                ../relative.mp3
                https://example.com/kept.mp3
                """;
        assertEquals(List.of("https://example.com/kept.mp3"), M3u.parse(content),
                "an m3u file is a list of paths; only the http(s) ones may reach ffmpeg");
    }

    @Test
    void keepsDuplicates() {
        String url = "https://example.com/a.mp3";
        assertEquals(List.of(url, url), M3u.parse(url + "\n" + url + "\n"),
                "a playlist may legitimately hold the same track twice");
    }

    @Test
    void toleratesEveryLineEndingAndSurroundingSpace() {
        String content = "#EXTM3U\r\n  https://example.com/a.mp3  \r\n\r\nhttps://example.com/b.mp3\r";
        assertEquals(List.of("https://example.com/a.mp3", "https://example.com/b.mp3"),
                M3u.parse(content));
    }

    @Test
    void answersEmptyForNothingUsable() {
        assertTrue(M3u.parse(null).isEmpty());
        assertTrue(M3u.parse("").isEmpty());
        assertTrue(M3u.parse("   \n\n  ").isEmpty());
        assertTrue(M3u.parse("#EXTM3U\n#EXTINF:-1,only a comment\n").isEmpty());
    }

    // ------------------------------------------------------------------
    // Exporting
    // ------------------------------------------------------------------

    @Test
    void writesTheHeaderAnExtinfAndTheUrl() {
        String out = M3u.export(playlistOf("https://example.com/a.mp3"),
                url -> Component.literal("A song"));
        assertEquals("""
                #EXTM3U
                #EXTINF:-1,A song
                https://example.com/a.mp3
                """, out);
    }

    @Test
    void fallsBackToTheUrlWhenThereIsNoTitle() {
        String out = M3u.export(playlistOf("https://example.com/a.mp3"), url -> null);
        assertTrue(out.contains("#EXTINF:-1,https://example.com/a.mp3"));
    }

    @Test
    void flattensANewlineInATitleSoItCannotEndTheExtinfLineEarly() {
        String out = M3u.export(playlistOf("https://example.com/a.mp3"),
                url -> Component.literal("Evil\nhttps://attacker.example/x.mp3"));
        assertEquals(3, out.lines().count(),
                "a title comes from page metadata; it must not be able to add a line");
        assertEquals(List.of("https://example.com/a.mp3"), M3u.parse(out));
    }

    @Test
    void anEmptyPlaylistExportsToJustAHeaderAndReimportsAsNothing() {
        String out = M3u.export(playlistOf(), url -> null);
        assertEquals("#EXTM3U\n", out);
        assertTrue(M3u.parse(out).isEmpty());
    }

    @Test
    void whatIsExportedIsWhatIsRead() {
        Playlist playlist = playlistOf("https://example.com/a.mp3", "https://example.com/b.mp3");
        String out = M3u.export(playlist, Component::literal);
        assertEquals(playlist.urls(), M3u.parse(out));
        assertTrue(M3u.looksLikeM3u(out));
        assertFalse(M3u.looksLikeM3u("https://example.com/a.mp3"));
    }
}
