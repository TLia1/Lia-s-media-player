package com.lia.mediaplayer.video;

import javax.sound.sampled.SourceDataLine;
import org.jetbrains.annotations.Nullable;

/**
 * Handles synchronization between video frames and the audio line or wall clock.
 */
public class PlaybackClock {
    private final Object clockLock = new Object();

    private long clockOffsetMicros;   // playback time represented by lineBase / wall baseline
    private long lineBaseMicros;      // audio line position captured at the last (re)baseline
    private long wallAccumMicros;     // accumulated time while paused (no-audio clock)
    private long wallResumeNanos;     // nanoTime when playback last (re)started (no-audio clock)

    public PlaybackClock() {
        this.clockOffsetMicros = 0;
        this.lineBaseMicros = 0;
        this.wallAccumMicros = 0;
        this.wallResumeNanos = System.nanoTime();
    }

    public void start(long offsetMicros, @Nullable SourceDataLine audioLine) {
        synchronized (clockLock) {
            this.clockOffsetMicros = offsetMicros;
            this.lineBaseMicros = audioLine != null ? audioLine.getMicrosecondPosition() : 0;
            this.wallAccumMicros = offsetMicros;
            this.wallResumeNanos = System.nanoTime();
        }
    }

    public void pause(boolean hasAudio, @Nullable SourceDataLine audioLine) {
        synchronized (clockLock) {
            this.wallAccumMicros = currentPositionMicrosLocked(hasAudio, audioLine, true);
        }
    }

    public void resume(@Nullable SourceDataLine audioLine) {
        synchronized (clockLock) {
            this.wallResumeNanos = System.nanoTime();
            if (audioLine != null) {
                this.lineBaseMicros = audioLine.getMicrosecondPosition();
                this.clockOffsetMicros = this.wallAccumMicros;
            }
        }
    }

    public void seekTo(long targetMicros, @Nullable SourceDataLine audioLine) {
        synchronized (clockLock) {
            this.clockOffsetMicros = targetMicros;
            this.lineBaseMicros = audioLine != null ? audioLine.getMicrosecondPosition() : 0;
            this.wallAccumMicros = targetMicros;
            this.wallResumeNanos = System.nanoTime();
        }
    }

    public long currentPositionMicros(boolean hasAudio, @Nullable SourceDataLine audioLine, boolean isPlaying) {
        synchronized (clockLock) {
            return currentPositionMicrosLocked(hasAudio, audioLine, isPlaying);
        }
    }

    private long currentPositionMicrosLocked(boolean hasAudio, @Nullable SourceDataLine audioLine, boolean isPlaying) {
        if (hasAudio && audioLine != null) {
            return clockOffsetMicros + (audioLine.getMicrosecondPosition() - lineBaseMicros);
        }
        long base = wallAccumMicros;
        if (isPlaying) {
            base += (System.nanoTime() - wallResumeNanos) / 1000L;
        }
        return base;
    }
}
