package com.lia.mediaplayer.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lia.mediaplayer.media.PlaybackError.Cause;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stderr-to-cause table, pinned against the real wording of the tools.
 *
 * <p>Substring matching on someone else's error messages is the sort of thing that
 * quietly stops working: yt-dlp rephrases a line, a rule added later shadows an older
 * one because it is listed above it, and the player goes back to showing a wall of
 * ffmpeg output. The strings below are the ones the tools actually print — the value of
 * this file is that they are written down somewhere a change has to walk past.</p>
 *
 * <p>The last test is the other half: fifteen causes times two keys each, all of which
 * have to exist in the language files, because an unmatched key is what a player sees
 * <em>instead</em> of the reason their video did not play.</p>
 */
class PlaybackErrorTest {

    // ------------------------------------------------------------------
    // Real messages
    // ------------------------------------------------------------------

    @Test
    void recognisesAnOutdatedExtractor() {
        assertEquals(Cause.EXTRACTOR_OUTDATED, PlaybackError.classify(
                "ERROR: [youtube] dQw4w9WgXcQ: Unable to extract player response; "
                        + "please report this issue on https://github.com/yt-dlp/yt-dlp/issues"));
        assertEquals(Cause.EXTRACTOR_OUTDATED, PlaybackError.classify(
                "WARNING: [youtube] nsig extraction failed: Some formats may be missing"));
    }

    @Test
    void recognisesAMissingTool() {
        assertEquals(Cause.TOOL_MISSING, PlaybackError.classify(
                "Cannot run program \"yt-dlp\": error=2, No such file or directory"));
    }

    @Test
    void recognisesTheSignInWall() {
        assertEquals(Cause.SIGN_IN_REQUIRED, PlaybackError.classify(
                "ERROR: [youtube] Sign in to confirm you're not a bot. Use --cookies-from-browser"));
    }

    @Test
    void recognisesAPrivateVideo() {
        assertEquals(Cause.PRIVATE, PlaybackError.classify(
                "ERROR: [youtube] abc: Private video. Sign in if you've been granted access"));
    }

    @Test
    void recognisesALiveStreamThatHasNotStarted() {
        assertEquals(Cause.LIVE_NOT_STARTED, PlaybackError.classify(
                "ERROR: [youtube] abc: This live event will begin in 3 hours."));
    }

    @Test
    void recognisesAnExpiredStreamUrl() {
        // A googlevideo URL resolved minutes ago and handed to ffmpeg too late.
        assertEquals(Cause.LINK_EXPIRED, PlaybackError.classify(
                "https://rr3---sn-xxx.googlevideo.com/videoplayback: Server returned 403 Forbidden"));
    }

    @Test
    void recognisesTheNetworkBeingDown() {
        assertEquals(Cause.NETWORK, PlaybackError.classify(
                "java.net.UnknownHostException: www.youtube.com"));
        assertEquals(Cause.NETWORK, PlaybackError.classify(
                "[tcp @ 0x55f] Connection refused"));
    }

    @Test
    void recognisesSomethingFfmpegCannotDecode() {
        assertEquals(Cause.UNSUPPORTED_FORMAT, PlaybackError.classify(
                "[matroska @ 0x1] Invalid data found when processing input"));
        assertEquals(Cause.UNSUPPORTED_FORMAT, PlaybackError.classify(
                "ERROR: [youtube] abc: Requested format is not available"));
    }

    @Test
    void recognisesOurOwnFailures() {
        assertEquals(Cause.DECODE_FAILED, PlaybackError.classify("ffprobe could not read the stream"));
        assertEquals(Cause.DECODE_FAILED, PlaybackError.classify("ffmpeg exited with code 1"));
        assertEquals(Cause.TIMEOUT, PlaybackError.classify("yt-dlp timed out after 25s"));
    }

    // ------------------------------------------------------------------
    // Order: the narrower reading of an overlapping message has to win
    // ------------------------------------------------------------------

    @Test
    void prefersGeoBlockingOverPlainUnavailability() {
        // Both readings match this line; only one of them tells the player anything.
        assertEquals(Cause.GEO_BLOCKED, PlaybackError.classify(
                "ERROR: [youtube] abc: Video unavailable. "
                        + "The uploader has not made this video available in your country"));
    }

    @Test
    void prefersAgeRestrictionOverThePlainSignInWall() {
        assertEquals(Cause.AGE_RESTRICTED, PlaybackError.classify(
                "ERROR: [youtube] abc: Sign in to confirm your age. "
                        + "This video may be inappropriate for some users."));
    }

    @Test
    void prefersAnOutdatedExtractorOverTheNetwork() {
        // "Unable to download webpage" is a network rule, but yt-dlp's own advice to
        // update is the actionable half of this message.
        assertEquals(Cause.EXTRACTOR_OUTDATED, PlaybackError.classify(
                "ERROR: unable to download API page; please update to the latest version"));
    }

    // ------------------------------------------------------------------
    // Contract
    // ------------------------------------------------------------------

    @Test
    void answersUnknownRatherThanFailing() {
        assertEquals(Cause.UNKNOWN, PlaybackError.classify(null));
        assertEquals(Cause.UNKNOWN, PlaybackError.classify(""));
        assertEquals(Cause.UNKNOWN, PlaybackError.classify("   \n\t "));
        assertEquals(Cause.UNKNOWN, PlaybackError.classify("something nobody has ever printed"));
    }

    @Test
    void matchesRegardlessOfCase() {
        assertEquals(Cause.PRIVATE, PlaybackError.classify("PRIVATE VIDEO"));
        assertEquals(Cause.PRIVATE, PlaybackError.classify("Private Video"));
    }

    @Test
    void offersTheUpdateButtonOnlyWhereANewerToolWouldHelp() {
        assertTrue(Cause.TOOL_MISSING.suggestsToolUpdate());
        assertTrue(Cause.EXTRACTOR_OUTDATED.suggestsToolUpdate());
        // A private video stays private however new yt-dlp is.
        assertFalse(Cause.PRIVATE.suggestsToolUpdate());
        assertFalse(Cause.GEO_BLOCKED.suggestsToolUpdate());
        assertFalse(Cause.UNKNOWN.suggestsToolUpdate());
    }

    // ------------------------------------------------------------------
    // The language keys behind the causes
    // ------------------------------------------------------------------

    @Test
    void everyCauseHasBothOfItsKeysInBothLanguages() {
        for (String language : new String[]{"en_us", "fr_fr"}) {
            JsonObject lang = loadLanguage(language);
            for (Cause cause : Cause.values()) {
                assertTrue(lang.has(cause.messageKey()),
                        language + " is missing " + cause.messageKey());
                assertTrue(lang.has(cause.hintKey()),
                        language + " is missing " + cause.hintKey());
            }
        }
    }

    private static JsonObject loadLanguage(String language) {
        String path = "assets/liasmediaplayer/lang/" + language + ".json";
        try (InputStream in = PlaybackErrorTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "not on the test classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
