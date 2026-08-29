package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.media.AudioGain;
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
    /**
     * This video's own share of the mix — the window's or the surface's, handed down by
     * {@code VideoPlayer.setAudioGain}. {@link AudioGain#detached()} until then.
     */
    private volatile AudioGain audioGain = AudioGain.detached();
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
            audioGain.onLineOpened();
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

    /** The gain this output multiplies in — see {@link AudioGain}. */
    public AudioGain gain() {
        return audioGain;
    }

    /** Hands this output the gain to apply. Safe to call while the line is open. */
    public void setGain(AudioGain gain) {
        if (gain != null) {
            audioGain = gain;
            gain.onLineOpened(); // a different gain has never written to this line
        }
    }

    private void applyGain(SourceDataLine line) {
        MediaPlayerContext ctx = MediaPlayerContext.get();
        audioGain.apply(line, ctx.getVolumeManager());
    }

    public void close() {
        SourceDataLine line = audioLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
            }
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

    /**
     * Reads PCM from one session and writes it to the line until the stream ends, the
     * player stops, or a newer session supersedes this one.
     *
     * @param onEndOfStream run when the stream ended of its own accord, rather than
     *                      because the session was replaced or the player stopped. It is
     *                      how a window with no picture learns its track is over — see
     *                      {@code VideoPlayer.onAudioStreamEnded}.
     */
    public void pumpAudio(int expectedGen, IntSupplier currentGenSupplier, BooleanSupplier isRunningSupplier,
                          InputStream in, Runnable onEndOfStream) {
        byte[] buffer = new byte[8192];
        boolean drained = false;
        try {
            int read;
            SourceDataLine line = audioLine;
            while (isRunningSupplier.getAsBoolean() && currentGenSupplier.getAsInt() == expectedGen) {
                read = in.read(buffer);
                if (read < 0) {
                    drained = true;
                    break;
                }
                if (currentGenSupplier.getAsInt() != expectedGen) {
                    break;
                }
                if (line != null) {
                    applyGain(line);
                    line.write(buffer, 0, read);
                }
            }
        } catch (Exception ignored) {
            // Process killed, pipe or socket closed (typically a seek or a dispose).
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
        if (drained && isRunningSupplier.getAsBoolean() && currentGenSupplier.getAsInt() == expectedGen) {
            onEndOfStream.run();
        }
    }
}
