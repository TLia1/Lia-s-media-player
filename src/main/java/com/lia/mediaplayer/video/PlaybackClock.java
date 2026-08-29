package com.lia.mediaplayer.video;

import com.lia.mediaplayer.media.MediaPlayback;

import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.SourceDataLine;

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

    /**
     * How far out of place a drift correction is willing to slide the clock before it
     * gives up and asks for a real seek. Two seconds is about the largest skew a viewer
     * reads as "it caught up" rather than as a fault.
     */
    public static final long MAX_SKEW_MICROS = 2_000_000L;

    /** The most one call moves the clock, so a correction converges over ticks rather than jumping. */
    private static final long STEP_MICROS = 50_000L;

    /** What {@link #driftCorrect} did. */
    public enum Drift {
        /** Already close enough; nothing moved. */
        WITHIN_TOLERANCE,
        /** The clock was nudged. Call again next tick to keep converging. */
        NUDGED,
        /** Too far out to slide; the caller should seek. */
        SEEK
    }

    /**
     * Slides the clock towards {@code targetMicros} instead of jumping to it.
     *
     * <p>This is the piece {@code SyncControl.driftCorrect} exists for. The audio line is
     * the master clock and the decoder is back-pressured by a bounded frame queue, so a
     * small correction can be made <em>here</em>, by moving the offset the line's position
     * is measured from: the presentation times the frames are compared against shift, the
     * picture slides into place over a few ticks and nothing is torn down. Repeated seeks
     * from outside cannot do that — each one restarts the pipeline, and a party correcting
     * twice a second never plays anything at all.</p>
     *
     * <p>The skew is bounded twice over: {@link #STEP_MICROS} per call, so a correction is
     * gradual, and {@link #MAX_SKEW_MICROS} in total, past which this refuses and answers
     * {@link Drift#SEEK} — beyond a couple of seconds a slide is no longer a correction,
     * it is a wrong position held for a long time.</p>
     *
     * <p>Pure arithmetic over the clock's own fields, so it is unit-tested with no audio
     * line at all.</p>
     */
    public Drift driftCorrect(long targetMicros, long toleranceMicros,
                              boolean hasAudio, @Nullable SourceDataLine audioLine, boolean isPlaying) {
        long tolerance = toleranceMicros > 0
                ? toleranceMicros : MediaPlayback.DEFAULT_DRIFT_TOLERANCE_MICROS;
        synchronized (clockLock) {
            long current = currentPositionMicrosLocked(hasAudio, audioLine, isPlaying);
            long delta = targetMicros - current;
            if (Math.abs(delta) <= tolerance) {
                return Drift.WITHIN_TOLERANCE;
            }
            if (Math.abs(delta) > MAX_SKEW_MICROS) {
                return Drift.SEEK;
            }
            long step = Math.max(-STEP_MICROS, Math.min(STEP_MICROS, delta));
            // Both baselines move, so the answer is the same whichever of the two clocks
            // is currently authoritative — a video whose audio line dies mid-correction
            // must not jump when it falls back to the wall clock.
            clockOffsetMicros += step;
            wallAccumMicros += step;
            return Drift.NUDGED;
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
