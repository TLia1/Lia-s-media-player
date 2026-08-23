package com.lia.mediaplayer.command;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@code /show} through a dispatcher whose source type is neither loader's.
 *
 * <p>That is the point of the test as much as the coverage: the command tree was pinned
 * to {@code CommandSourceStack} and is now generic, because NeoForge and Fabric hand out
 * command sources with no common ancestor. If the tree ever stops being source-agnostic,
 * this stops compiling.</p>
 */
class ShowCommandTest {

    /** Stands in for a loader's command source; the tree must not care what this is. */
    private record TestSource(String name) {
    }

    /**
     * Records what the command asked the API to play.
     *
     * <p>A real {@link MediaPlayerContext} with the playback methods overridden, rather
     * than a mock of the 32-method interface: every field the context builds has an empty
     * constructor, so the parts left un-overridden — {@code kindOf} above all — run for
     * real and the test exercises the actual URL classification rather than a stubbed
     * answer. Overriding is also what keeps this off Mockito, whose pinned version cannot
     * generate classes under the Java 25 the 26.x targets compile against.</p>
     */
    private static final class RecordingApi extends MediaPlayerContext {
        final List<String> played = new ArrayList<>();

        @Override
        public long playVideo(String url) {
            played.add("playVideo:" + url);
            return 1;
        }

        @Override
        public long playVideoNewWindow(String url) {
            played.add("playVideoNewWindow:" + url);
            return 1;
        }

        @Override
        public long playAudio(String url) {
            played.add("playAudio:" + url);
            return 1;
        }

        @Override
        public long playAudioNewWindow(String url) {
            played.add("playAudioNewWindow:" + url);
            return 1;
        }

        @Override
        public long showImage(String url) {
            played.add("showImage:" + url);
            return 1;
        }
    }

    private RecordingApi api;
    private List<Component> failures;
    private CommandDispatcher<TestSource> dispatcher;

    @BeforeEach
    void setUp() {
        api = new RecordingApi();
        LiasMediaPlayerApi.setInstance(api);

        failures = new ArrayList<>();
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(ShowCommand.<TestSource>tree((context, message) -> failures.add(message)));
    }

    @AfterEach
    void tearDown() {
        LiasMediaPlayerApi.setInstance(null);
    }

    private int run(String command) throws CommandSyntaxException {
        return dispatcher.execute(command, new TestSource("test"));
    }

    private static String keyOf(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents()).getKey();
    }

    @Test
    void playsAVideo() throws CommandSyntaxException {
        assertEquals(1, run("show video \"https://example.com/video.mp4\""));
        assertEquals(List.of("playVideo:https://example.com/video.mp4"), api.played);
        assertTrue(failures.isEmpty());
    }

    @Test
    void opensAVideoInItsOwnWindowWhenAsked() throws CommandSyntaxException {
        assertEquals(1, run("show video \"https://example.com/video.mp4\" true"));
        assertEquals(List.of("playVideoNewWindow:https://example.com/video.mp4"), api.played);
    }

    @Test
    void playsAYouTubeLinkAsSoundOnly() throws CommandSyntaxException {
        // Asking for a video as audio is the one kind mismatch that is allowed.
        assertEquals(1, run("show audio \"https://youtube.com/watch?v=123\""));
        assertEquals(List.of("playAudio:https://youtube.com/watch?v=123"), api.played);
    }

    @Test
    void pinsAnImage() throws CommandSyntaxException {
        assertEquals(1, run("show image \"https://example.com/image.png\""));
        assertEquals(List.of("showImage:https://example.com/image.png"), api.played);
    }

    @Test
    void reportsAnUnknownMediaType() throws CommandSyntaxException {
        assertEquals(0, run("show hologram \"https://example.com/video.mp4\""));
        assertEquals(1, failures.size());
        assertEquals("command.liasmediaplayer.invalid_type", keyOf(failures.getFirst()));
        assertTrue(api.played.isEmpty());
    }

    @Test
    void reportsAnUnsupportedUrl() throws CommandSyntaxException {
        assertEquals(0, run("show video \"https://example.com/page.html\""));
        assertEquals("command.liasmediaplayer.unsupported_url", keyOf(failures.getFirst()));
        assertTrue(api.played.isEmpty());
    }

    @Test
    void refusesToPlayAnImageAsAudio() throws CommandSyntaxException {
        assertEquals(0, run("show audio \"https://example.com/image.png\""));
        assertEquals("command.liasmediaplayer.kind_mismatch", keyOf(failures.getFirst()));
        assertTrue(api.played.isEmpty());
    }
}
