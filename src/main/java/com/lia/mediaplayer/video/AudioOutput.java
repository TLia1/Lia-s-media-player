package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.tools.FFmpegCli;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public class AudioOutput {
    private static final int MAX_AUDIO_CHANNELS = 2;

    @Nullable
    private volatile SourceDataLine audioLine;
    private volatile float lastAppliedGain = -1f;
    private int audioSampleRate;
    private int audioChannels;
    private final String url;

    public AudioOutput(String url) {
        this.url = url;
    }

    @Nullable
    public SourceDataLine getLine() {
        return audioLine;
    }

    public int getSampleRate() {
        return audioSampleRate;
    }

    public int getChannels() {
        return audioChannels;
    }

    public boolean open(FFmpegCli.MediaInfo info) {
        int channels = Math.min(Math.max(info.channels(), 1), MAX_AUDIO_CHANNELS);
        int sampleRate = info.sampleRate();
        if (sampleRate <= 0) {
            return false;
        }
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(lineInfo)) {
                LiasMediaPlayer.LOGGER.info("No audio line for {} ch @ {} Hz; playing video without sound", channels, sampleRate);
                return false;
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(format);
            line.start();
            applyGain(line);
            this.audioLine = line;
            this.audioSampleRate = sampleRate;
            this.audioChannels = channels;
            return true;
        } catch (Exception e) {
            LiasMediaPlayer.LOGGER.info("Could not open audio for {}: {}", url, e.toString());
            this.audioLine = null;
            return false;
        }
    }

    public void applyGain() {
        SourceDataLine line = audioLine;
        if (line != null) {
            applyGain(line);
        }
    }

    private void applyGain(SourceDataLine line) {
        lastAppliedGain = Volume.apply(line, lastAppliedGain);
    }

    public void close() {
        SourceDataLine line = audioLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {}
            audioLine = null;
        }
    }

    public void stopLine() {
        SourceDataLine line = audioLine;
        if (line != null) {
            line.stop();
        }
    }

    public void startLine() {
        SourceDataLine line = audioLine;
        if (line != null) {
            line.start();
        }
    }

    public void flushLine() {
        SourceDataLine line = audioLine;
        if (line != null) {
            line.flush();
        }
    }

    public void drainLine() {
        SourceDataLine line = audioLine;
        if (line != null) {
            line.drain();
        }
    }

    public void pumpAudio(int expectedGen, IntSupplier currentGenSupplier, BooleanSupplier isRunningSupplier, InputStream in) {
        byte[] buffer = new byte[8192];
        try {
            int read;
            SourceDataLine line = audioLine;
            while (isRunningSupplier.getAsBoolean() && currentGenSupplier.getAsInt() == expectedGen && (read = in.read(buffer)) >= 0) {
                if (currentGenSupplier.getAsInt() != expectedGen) {
                    break;
                }
                if (line != null) {
                    applyGain(line);
                    line.write(buffer, 0, read);
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }
}
