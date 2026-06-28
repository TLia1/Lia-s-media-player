package com.lia.mediaplayer.video;

import com.lia.mediaplayer.tools.FFmpegCli;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

public class FFmpegSession {
    @Nullable
    private Process videoProcess;
    @Nullable
    private InputStream videoIn;
    @Nullable
    private ReadableByteChannel videoChannel;
    @Nullable
    private Process audioProcess;
    @Nullable
    private Thread audioThread;

    public void start(String mediaUrl, int videoWidth, int videoHeight, double startSeconds, boolean hasAudio, int audioSampleRate, int audioChannels, int sessionGen, Consumer<InputStream> audioLoop) throws IOException {
        kill();

        Process video = FFmpegCli.openVideo(mediaUrl, videoWidth, videoHeight, startSeconds);
        this.videoProcess = video;
        this.videoIn = video.getInputStream();
        this.videoChannel = Channels.newChannel(this.videoIn);

        if (hasAudio) {
            Process audio = FFmpegCli.openAudio(mediaUrl, audioSampleRate, audioChannels, startSeconds);
            this.audioProcess = audio;
            InputStream audioStream = audio.getInputStream();
            Thread thread = new Thread(() -> audioLoop.accept(audioStream), "liasmediaplayer-audio-" + sessionGen);
            thread.setDaemon(true);
            this.audioThread = thread;
            thread.start();
        }
    }

    @Nullable
    public ReadableByteChannel getVideoChannel() {
        return videoChannel;
    }

    public void kill() {
        Process audio = audioProcess;
        if (audio != null) {
            audio.destroyForcibly();
            audioProcess = null;
        }
        Process video = videoProcess;
        if (video != null) {
            video.destroyForcibly();
            videoProcess = null;
        }
        if (videoChannel != null) {
            try {
                videoChannel.close();
            } catch (IOException ignored) {}
            videoChannel = null;
        }
        videoIn = null;
        Thread thread = audioThread;
        if (thread != null) {
            thread.interrupt();
            audioThread = null;
        }
    }
}
