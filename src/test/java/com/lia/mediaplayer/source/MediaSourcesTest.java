package com.lia.mediaplayer.source;

import org.junit.jupiter.api.Test;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaSource;
import net.minecraft.network.chat.Component;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MediaSourcesTest {

    @Test
    void kindOf_ReturnsCorrectKind() {
        assertEquals(MediaKind.IMAGE, MediaSources.kindOf("https://example.com/image.png"));
        assertEquals(MediaKind.VIDEO, MediaSources.kindOf("https://example.com/video.mp4"));
        assertEquals(MediaKind.AUDIO, MediaSources.kindOf("https://example.com/audio.mp3"));
        assertEquals(MediaKind.VIDEO, MediaSources.kindOf("https://youtube.com/watch?v=123"));
        assertEquals(MediaKind.VIDEO, MediaSources.kindOf("https://example.com/stream.m3u8"));
        assertEquals(MediaKind.IMAGE, MediaSources.kindOf("https://tenor.com/view/123"));
        assertNull(MediaSources.kindOf("https://example.com/unknown.txt"));
        assertNull(MediaSources.kindOf(null));
        
        assertEquals(MediaKind.IMAGE, MediaSources.apiKindOf("https://example.com/image.png"));
    }

    @Test
    void isMethods_WorkCorrectly() {
        assertTrue(MediaSources.isImage("https://example.com/image.png"));
        assertFalse(MediaSources.isVideo("https://example.com/image.png"));
        
        assertTrue(MediaSources.isVideo("https://example.com/video.mp4"));
        assertFalse(MediaSources.isAudio("https://example.com/video.mp4"));
        
        assertTrue(MediaSources.isAudio("https://example.com/audio.mp3"));
        assertFalse(MediaSources.isImage("https://example.com/audio.mp3"));
        
        assertTrue(MediaSources.isSupported("https://example.com/image.png"));
        assertFalse(MediaSources.isSupported("https://example.com/unknown.txt"));
    }

    @Test
    void find_ReturnsCorrectSource() {
        Optional<MediaSource> source = MediaSources.find("https://youtube.com/watch?v=123");
        assertTrue(source.isPresent());
        assertTrue(source.get() instanceof YouTubeSource);
        
        assertFalse(MediaSources.find("https://example.com/unknown.txt").isPresent());
    }

    @Test
    void labelFor_ReturnsSourceLabelOrRawText() {
        assertEquals("[youtube]", MediaSources.labelFor("https://youtube.com/watch?v=123").getString());
        assertEquals("[picture]", MediaSources.labelFor("https://example.com/image.png").getString());
        assertEquals("https://example.com/unknown.txt", MediaSources.labelFor("https://example.com/unknown.txt").getString());
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
        
        MediaSources.register(customSource);
        
        assertTrue(MediaSources.isSupported("custom"));
        assertEquals(MediaKind.AUDIO, MediaSources.kindOf("custom"));
        assertEquals("[custom]", MediaSources.labelFor("custom").getString());
    }
}
