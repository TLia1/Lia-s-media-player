package com.lia.mediaplayer.source;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourcesTest {

    private MediaSources mediaSources;

    @BeforeEach
    void setUp() {
        mediaSources = new MediaSources();
    }

    @Test
    void kindOf_ReturnsCorrectKind() {
        assertEquals(MediaKind.IMAGE, mediaSources.kindOf("https://example.com/image.png"));
        assertEquals(MediaKind.VIDEO, mediaSources.kindOf("https://example.com/video.mp4"));
        assertEquals(MediaKind.AUDIO, mediaSources.kindOf("https://example.com/audio.mp3"));
        assertEquals(MediaKind.VIDEO, mediaSources.kindOf("https://youtube.com/watch?v=123"));
        assertEquals(MediaKind.VIDEO, mediaSources.kindOf("https://example.com/stream.m3u8"));
        assertEquals(MediaKind.IMAGE, mediaSources.kindOf("https://tenor.com/view/123"));
        assertNull(mediaSources.kindOf("https://example.com/unknown.txt"));
        assertNull(mediaSources.kindOf(null));

        assertEquals(MediaKind.IMAGE, mediaSources.apiKindOf("https://example.com/image.png"));
    }

    @Test
    void isMethods_WorkCorrectly() {
        assertTrue(mediaSources.isImage("https://example.com/image.png"));
        assertFalse(mediaSources.isVideo("https://example.com/image.png"));

        assertTrue(mediaSources.isVideo("https://example.com/video.mp4"));
        assertFalse(mediaSources.isAudio("https://example.com/video.mp4"));

        assertTrue(mediaSources.isAudio("https://example.com/audio.mp3"));
        assertFalse(mediaSources.isImage("https://example.com/audio.mp3"));

        assertTrue(mediaSources.isSupported("https://example.com/image.png"));
        assertFalse(mediaSources.isSupported("https://example.com/unknown.txt"));
    }

    @Test
    void find_ReturnsCorrectSource() {
        Optional<MediaSource> source = mediaSources.find("https://youtube.com/watch?v=123");
        assertTrue(source.isPresent());
        assertTrue(source.get() instanceof YouTubeSource);

        assertFalse(mediaSources.find("https://example.com/unknown.txt").isPresent());
    }

    @Test
    void labelFor_ReturnsSourceLabelOrRawText() {
        assertEquals("[youtube]", mediaSources.labelFor("https://youtube.com/watch?v=123").getString());
        assertEquals("[picture]", mediaSources.labelFor("https://example.com/image.png").getString());
        assertEquals("https://example.com/unknown.txt", mediaSources.labelFor("https://example.com/unknown.txt").getString());
    }

    @Test
    void register_AddsCustomSource() {
        MediaSource customSource = new MediaSource() {
            @Override
            public boolean matches(String url) {
                return "custom".equals(url);
            }

            @Override
            public MediaKind kind() {
                return MediaKind.AUDIO;
            }

            @Override
            public Component label(String url) {
                return Component.literal("[custom]");
            }
        };

        mediaSources.register(customSource);

        assertTrue(mediaSources.isSupported("custom"));
        assertEquals(MediaKind.AUDIO, mediaSources.kindOf("custom"));
        assertEquals("[custom]", mediaSources.labelFor("custom").getString());
    }
}
