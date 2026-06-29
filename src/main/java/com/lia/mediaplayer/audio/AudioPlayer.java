package com.lia.mediaplayer.audio;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.tools.FFmpegCli;

import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One playing (or paused) audio track — the sound-only counterpart of
 * {@link com.lia.mediaplayer.video.VideoPlayer}. It reuses the same external tooling and
 * shared helpers (so there is no second copy of the heavy machinery):
 *
 * <ul>
 *   <li>{@link MediaUrlResolver} turns a chat link into a directly-openable URL
 *       (YouTube links resolve to a stream that ffmpeg opens with {@code -vn}, so only
 *       the sound is played);</li>
 *   <li>{@link FFmpegCli} probes the stream and pipes {@code s16le} PCM out of ffmpeg;</li>
 *   <li>{@link Volume} holds the shared level and the dB-gain math.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * <ul>
 *   <li><b>Control thread</b> — resolves/probes the URL, opens the line, launches the
 *       ffmpeg session, and then parks until a seek, end-of-stream or dispose needs
 *       handling. It relaunches the session on seek (the proven recovery path).</li>
 *   <li><b>Pump thread</b> (one per session) — reads PCM and blocking-writes it to the
 *       {@link SourceDataLine}, which paces playback. A stopped line back-pressures the
 *       write into a clean pause.</li>
 * </ul>
 *
 * <h2>Clock</h2>
 * The line's {@code getMicrosecondPosition()} is the master clock; a stopped line
 * freezes it, so pausing needs no extra wall-clock bookkeeping.
 */
public final class AudioPlayer {

    /** Never seek into the very last slice (ffmpeg can EOF there with nothing to play). */
    private static final long SEEK_END_MARGIN_MICROS = 500_000L; // 0.5s
    /** A pause longer than this may have let a network stream go stale; relaunch on resume. */
    private static final long STALE_PAUSE_NANOS = 3_000_000_000L; // 3s
    private static final int MAX_AUDIO_CHANNELS = 2;
    private static final AtomicInteger TRACK_ID = new AtomicInteger();

    public enum State {LOADING, PLAYING, PAUSED, ENDED, FAILED}

    private final String url;

    private volatile Thread controlThread;
    private volatile boolean running = true;
    private volatile State state = State.LOADING;
    @Nullable
    private volatile String errorMessage;

    private volatile long durationMicros;

    // Resolved (direct) URL and audio format, set once on startup.
    private String mediaUrl;
    private int audioSampleRate;
    private int audioChannels;

    // --- Current ffmpeg session (swapped on seek) ---------------------------
    private volatile int sessionGen;
    @Nullable
    private volatile Process audioProcess;
    @Nullable
    private volatile Thread audioThread;
    /** Set by a pump thread when it reaches end-of-stream, so the control thread can react. */
    private volatile boolean sessionEnded;

    // --- Pause / seek gate (control thread waits here) ----------------------
    private final ReentrantLock gate = new ReentrantLock();
    private final Condition gateSignal = gate.newCondition();
    private volatile boolean seekRequested;
    private volatile long seekTargetMicros;
    private volatile long pausedAtNanos;

    // --- Pump pause gate (pump thread waits here while paused) ---------------
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();

    // --- Clock / line -------------------------------------------------------
    private final Object clockLock = new Object();
    @Nullable
    private volatile SourceDataLine audioLine;
    private volatile float lastAppliedGain = -1f;
    private long clockOffsetMicros;  // playback time represented by lineBase
    private long lineBaseMicros;     // line position captured at the last (re)baseline

    public AudioPlayer(String url) {
        this.url = url;
    }

    public String url() {
        return url;
    }

    private static com.lia.mediaplayer.MediaPlayerContext getContext() {
        return (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
    }

    public State state() {
        return state;
    }

    @Nullable
    public String errorMessage() {
        return errorMessage;
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    /** Total length in microseconds, or 0 if unknown (some live streams). */
    public long durationMicros() {
        return durationMicros;
    }

    // ------------------------------------------------------------------
    // Volume (delegates to the shared, video+audio level)
    // ------------------------------------------------------------------

    public float volume() {
        return getContext().getVolumeManager().level();
    }

    public boolean isMuted() {
        return getContext().getVolumeManager().isMuted();
    }

    public void setVolume(float value) {
        getContext().getVolumeManager().set(value);
        applyGainIfOpen();
    }

    public void changeVolume(float delta) {
        getContext().getVolumeManager().change(delta);
        applyGainIfOpen();
    }

    public void toggleMute() {
        getContext().getVolumeManager().toggleMute();
        applyGainIfOpen();
    }

    private void applyGainIfOpen() {
        SourceDataLine line = audioLine;
        if (line != null) {
            lastAppliedGain = getContext().getVolumeManager().apply(line, lastAppliedGain);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void start() {
        if (controlThread != null) {
            return;
        }
        Thread thread = new Thread(this::controlLoop, "liasmediaplayer-audio-" + TRACK_ID.getAndIncrement());
        thread.setDaemon(true);
        controlThread = thread;
        thread.start();
    }

    public void dispose() {
        running = false;
        signalGate();
        signalPause();     // unblock a pump thread waiting on the pause gate
        closeAudioLine();  // unblock a paused pump stuck in line.write
        killSession();
        Thread thread = controlThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Playback control (called from the render/main thread)
    // ------------------------------------------------------------------

    public void togglePause() {
        if (state == State.PLAYING) {
            pause();
        } else if (state == State.PAUSED) {
            resume();
        } else if (state == State.ENDED) {
            seekTo(0); // replay from the start
        }
    }

    public void pause() {
        if (state != State.PLAYING) {
            return;
        }
        pausedAtNanos = System.nanoTime();
        state = State.PAUSED;
        SourceDataLine line = audioLine;
        if (line != null) {
            // Stopping the line freezes the master clock (a stopped line's position
            // stops advancing).  The pump thread is gated by the pauseLock so it
            // won't drain the ffmpeg stream while paused.
            line.stop();
        }
    }

    public void resume() {
        if (state == State.ENDED) {
            seekTo(0);
            return;
        }
        if (state != State.PAUSED) {
            return;
        }
        long pausedAt = pausedAtNanos;
        pausedAtNanos = 0;
        boolean stale = pausedAt != 0 && (System.nanoTime() - pausedAt) > STALE_PAUSE_NANOS;
        long resumePos = positionMicros();
        state = State.PLAYING;
        // Wake the pump thread so it can continue writing PCM to the line.
        signalPause();
        if (stale) {
            // The session may have gone stale while paused (an idle network stream can be
            // dropped). Relaunch from the paused position via the seek path, which is
            // known to recover cleanly, instead of un-pausing a possibly-dead process.
            seekTo(resumePos);
        } else {
            SourceDataLine line = audioLine;
            if (line != null) {
                line.start();
            }
        }
    }

    public void seekToFraction(double fraction) {
        if (durationMicros <= 0) {
            return;
        }
        seekTo((long) (Math.max(0.0, Math.min(1.0, fraction)) * durationMicros));
    }

    public void seekTo(long targetMicros) {
        long target = Math.max(0, targetMicros);
        long duration = durationMicros;
        if (duration > 0) {
            target = Math.min(target, Math.max(0, duration - SEEK_END_MARGIN_MICROS));
        }
        seekTargetMicros = target;
        seekRequested = true;
        signalGate();
    }

    public long positionMicros() {
        synchronized (clockLock) {
            SourceDataLine line = audioLine;
            if (line != null) {
                return clockOffsetMicros + (line.getMicrosecondPosition() - lineBaseMicros);
            }
            return clockOffsetMicros;
        }
    }

    public double progress() {
        long duration = durationMicros;
        if (duration <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, positionMicros() / (double) duration));
    }

    private void signalGate() {
        gate.lock();
        try {
            gateSignal.signalAll();
        } finally {
            gate.unlock();
        }
    }

    /** Wake the pump thread if it is waiting on the pause gate. */
    private void signalPause() {
        pauseLock.lock();
        try {
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Control thread (orchestrator)
    // ------------------------------------------------------------------

    private void controlLoop() {
        try {
            int retries = 1;
            FFmpegCli.MediaInfo info = null;
            while (true) {
                try {
                    mediaUrl = MediaUrlResolver.resolve(url);
                    info = FFmpegCli.probe(mediaUrl);
                    break;
                } catch (IOException e) {
                    if (retries > 0) {
                        retries--;
                        LiasMediaPlayer.LOGGER.warn("Media resolution failed for {}, retrying... ({})", url, e.getMessage());
                        continue;
                    }
                    throw e;
                }
            }
            if (!info.hasAudio()) {
                throw new IllegalStateException("This file has no audio track");
            }
            if (!openAudioLine(info)) {
                throw new IllegalStateException("Could not open an audio output");
            }
            durationMicros = Math.max(0, info.durationMicros());

            startSession(0);
            synchronized (clockLock) {
                clockOffsetMicros = 0;
                SourceDataLine line = audioLine;
                lineBaseMicros = line != null ? line.getMicrosecondPosition() : 0;
            }
            state = State.PLAYING;

            while (running) {
                gate.lock();
                try {
                    while (running && !seekRequested && !sessionEnded) {
                        gateSignal.await();
                    }
                } finally {
                    gate.unlock();
                }
                if (!running) {
                    break;
                }
                if (seekRequested) {
                    performSeek();
                } else if (sessionEnded) {
                    onEndOfStream();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            errorMessage = t.getMessage() != null ? t.getMessage() : t.toString();
            state = State.FAILED;
            LiasMediaPlayer.LOGGER.warn("Audio playback failed for {}: {}", url, errorMessage);
        } finally {
            closeAudioLine();
            killSession();
        }
    }

    private void performSeek() {
        long target = seekTargetMicros;
        seekRequested = false;
        sessionEnded = false;

        SourceDataLine line = audioLine;
        if (line != null) {
            // Drop pre-seek audio and release a paused pump blocked in line.write.
            line.flush();
        }
        try {
            startSession(target / 1_000_000.0);
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.warn("Audio seek failed for {}: {}", url, e.toString());
        }
        synchronized (clockLock) {
            SourceDataLine l = audioLine;
            clockOffsetMicros = target;
            lineBaseMicros = l != null ? l.getMicrosecondPosition() : 0;
        }
        if (state == State.ENDED) {
            state = State.PLAYING;
        }
        // Honour the current pause state across the relaunch.
        SourceDataLine l = audioLine;
        if (l != null) {
            if (state == State.PAUSED) {
                l.stop();
            } else {
                l.start();
            }
        }
    }

    private void onEndOfStream() {
        sessionEnded = false;
        SourceDataLine line = audioLine;
        if (line != null) {
            line.drain();
        }
        state = State.ENDED;
    }

    // ------------------------------------------------------------------
    // ffmpeg session management (control thread)
    // ------------------------------------------------------------------

    private void startSession(double startSeconds) throws IOException {
        killSession();
        int gen = ++sessionGen;

        Process audio = FFmpegCli.openAudio(mediaUrl, audioSampleRate, audioChannels, startSeconds);
        audioProcess = audio;
        InputStream in = audio.getInputStream();
        SourceDataLine line = audioLine;
        Thread thread = new Thread(() -> pumpLoop(gen, in, line), "liasmediaplayer-audiopump-" + gen);
        thread.setDaemon(true);
        audioThread = thread;
        thread.start();
    }

    private void killSession() {
        Process audio = audioProcess;
        if (audio != null) {
            audio.destroyForcibly();
            audioProcess = null;
        }
        Thread thread = audioThread;
        if (thread != null) {
            thread.interrupt();
            audioThread = null;
        }
    }

    /**
     * Pumps PCM from one audio ffmpeg process into the line. Exits when the player
     * stops, the session is superseded (a seek launched a newer one), or the stream
     * ends — signalling the control thread in the last case.
     *
     * <p>While the player is paused the pump waits on {@link #pauseCondition} instead
     * of continuing to read/write. Without this gate, {@code line.write()} could
     * succeed without blocking (the stopped line still accepts data into its internal
     * buffer), draining the entire ffmpeg stream and reaching EOF — which the control
     * thread would interpret as "track finished" and auto-advance/close the window.</p>
     */
    private void pumpLoop(int gen, InputStream in, SourceDataLine line) {
        byte[] buffer = new byte[8192];
        try {
            int read;
            while (running && gen == sessionGen && (read = in.read(buffer)) >= 0) {
                if (gen != sessionGen) {
                    return;
                }
                // Wait here while paused, so we don't drain the ffmpeg stream into the
                // line buffer and accidentally reach EOF.
                waitWhilePaused(gen);
                if (!running || gen != sessionGen) {
                    return;
                }
                lastAppliedGain = getContext().getVolumeManager().apply(line, lastAppliedGain);
                line.write(buffer, 0, read);
            }
            // Clean end-of-stream for the current session: let the control thread react.
            if (running && gen == sessionGen) {
                sessionEnded = true;
                signalGate();
            }
        } catch (Exception ignored) {
            // Process killed, pipe closed, or line closed (typically a seek/dispose) — just exit.
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    /**
     * Blocks the pump thread while the player is paused and this session is still
     * current. Returns immediately if the player is not paused, or if the player
     * has been disposed or a new session has started.
     */
    private void waitWhilePaused(int gen) {
        pauseLock.lock();
        try {
            while (state == State.PAUSED && running && gen == sessionGen) {
                pauseCondition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pauseLock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Audio line setup
    // ------------------------------------------------------------------

    private boolean openAudioLine(FFmpegCli.MediaInfo info) {
        int channels = Math.min(Math.max(info.channels(), 1), MAX_AUDIO_CHANNELS);
        int sampleRate = info.sampleRate();
        if (sampleRate <= 0) {
            return false;
        }
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(lineInfo)) {
                LiasMediaPlayer.LOGGER.info("No audio line for {} ch @ {} Hz", channels, sampleRate);
                return false;
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(format);
            line.start();
            audioLine = line;
            lastAppliedGain = getContext().getVolumeManager().apply(line, lastAppliedGain);
            audioSampleRate = sampleRate;
            audioChannels = channels;
            return true;
        } catch (Exception e) {
            LiasMediaPlayer.LOGGER.info("Could not open audio for {}: {}", url, e.toString());
            audioLine = null;
            return false;
        }
    }

    private void closeAudioLine() {
        SourceDataLine line = audioLine;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {
                // ignore
            }
            audioLine = null;
        }
    }
}
