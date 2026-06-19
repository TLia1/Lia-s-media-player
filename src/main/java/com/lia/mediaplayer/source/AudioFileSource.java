package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A direct audio file: a URL whose path ends in a sound-only container extension
 * ffmpeg can open straight away (a Discord {@code .mp3}/{@code .ogg}/{@code .wav}
 * attachment and friends). Shown as an {@code [audio]} label and played by the
 * in-game audio player.
 *
 * <p>The recognized extensions are intentionally disjoint from the video ones
 * ({@link DirectVideoSource}): {@code .webm}/{@code .ogv}/{@code .m4v} stay video,
 * while their audio-only siblings {@code .weba}/{@code .oga}/{@code .m4a} are audio.
 * That keeps every link claimed by exactly one feature.</p>
 */
public final class AudioFileSource implements MediaSource {

    private static final Component LABEL = Component.literal("[audio]");

    @Override
    public boolean matches(String url) {
        return isAudioFile(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        return com.lia.mediaplayer.api.MediaKind.AUDIO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    /** Whether {@code url}'s path ends in a known audio-only extension. */
    public static boolean isAudioFile(String url) {
        String path = Urls.pathLower(url);
        if (path == null) {
            return false;
        }
        return path.endsWith(".mp3")
                || path.endsWith(".wav")
                || path.endsWith(".ogg")
                || path.endsWith(".oga")
                || path.endsWith(".flac")
                || path.endsWith(".m4a")
                || path.endsWith(".aac")
                || path.endsWith(".opus")
                || path.endsWith(".weba")
                || path.endsWith(".wma")
                || path.endsWith(".aiff")
                || path.endsWith(".aif");
    }
}
