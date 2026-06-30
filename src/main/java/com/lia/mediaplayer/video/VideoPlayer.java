package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.tools.FFmpegCli;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One playing (or paused) video. Orchestrates playback state, decodes video frames
 * and audio on background threads, and provides the current frame to the render thread.
 */
public final class VideoPlayer {
    private static final int FRAME_QUEUE_CAPACITY = 64;
    private static final long SEEK_END_MARGIN_MICROS = 500_000L; // 0.5s
    private static final long STALE_PAUSE_NANOS = 3_000_000_000L; // 3s
    private static final AtomicInteger PLAYER_ID = new AtomicInteger(0);

    public enum State {LOADING, PLAYING, PAUSED, ENDED, FAILED}

    private final String url;
    private final VideoRenderer renderer;
    private final AudioOutput audioOutput;
    private final PlaybackClock clock;
    private final FFmpegSession session;

    // --- Decode side (written by decode thread, read by render thread) ------
    private final BlockingQueue<VideoFrame> frameQueue = new ArrayBlockingQueue<>(FRAME_QUEUE_CAPACITY);
    private final BlockingQueue<ByteBuffer> freeBuffers = new ArrayBlockingQueue<>(FRAME_QUEUE_CAPACITY + 4);
    private volatile Thread decodeThread;
    private volatile boolean running = true;
    private volatile State state = State.LOADING;
    @Nullable
    private volatile String errorMessage;

    // Media properties, set once after probing.
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile long durationMicros;
    private volatile boolean hasAudio;

    // Resolved (direct) media URL and decode geometry, set once on startup.
    private String mediaUrl;
    private long frameDurationMicros = 33_333L; // ~30fps default until probed

    // --- Current ffmpeg session ---
    private volatile int sessionGen;
    private volatile long sessionBaseMicros;
    private long frameIndex;

    // --- Pause / seek gate (decode thread waits here) ---
    private final ReentrantLock gate = new ReentrantLock();
    private final Condition gateSignal = gate.newCondition();
    private volatile boolean seekRequested;
    private volatile long seekTargetMicros;

    private volatile long pausedAtNanos; // used to detect stale sessions

    public VideoPlayer(String url) {
        this.url = url;
        this.renderer = new VideoRenderer();
        this.audioOutput = new AudioOutput(url);
        this.clock = new PlaybackClock();
        this.session = new FFmpegSession();
    }

    private static com.lia.mediaplayer.MediaPlayerContext getContext() {
        return (com.lia.mediaplayer.MediaPlayerContext) com.lia.mediaplayer.api.LiasMediaPlayerApi.getInstance();
    }

    public String url() {
        return url;
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

    public boolean hasAudio() {
        return hasAudio;
    }

    public float volume() {
        return getContext().getVolumeManager().level();
    }

    public boolean isMuted() {
        return getContext().getVolumeManager().isMuted();
    }

    public void setVolume(float value) {
        getContext().getVolumeManager().set(value);
        audioOutput.applyGain();
    }

    public void changeVolume(float delta) {
        setVolume(getContext().getVolumeManager().level() + delta);
    }

    public void toggleMute() {
        getContext().getVolumeManager().toggleMute();
        audioOutput.applyGain();
    }

    public int videoWidth() {
        return videoWidth;
    }

    public int videoHeight() {
        return videoHeight;
    }

    public long durationMicros() {
        return durationMicros;
    }

    public void start() {
        if (decodeThread != null) {
            return;
        }
        Thread thread = new Thread(this::decodeLoop, "liasmediaplayer-video-" + PLAYER_ID.getAndIncrement());
        thread.setDaemon(true);
        decodeThread = thread;
        thread.start();
    }

    public void dispose() {
        running = false;
        signalGate();
        audioOutput.close();
        session.kill();
        Thread thread = decodeThread;
        if (thread != null) {
            thread.interrupt();
        }
        renderer.releaseTexture();
        java.util.List<VideoFrame> drained = new java.util.ArrayList<>();
        frameQueue.drainTo(drained);
        drained.forEach(f -> freeBuffers.offer(f.rgbaBuffer()));
    }

    public void togglePause() {
        if (state == State.PLAYING) {
            pause();
        } else if (state == State.PAUSED) {
            resume();
        } else if (state == State.ENDED) {
            seekTo(0);
            resume();
        }
    }

    public void pause() {
        if (state != State.PLAYING) {
            return;
        }
        clock.pause(hasAudio, audioOutput.getLine());
        pausedAtNanos = System.nanoTime();
        state = State.PAUSED;
        audioOutput.stopLine();
    }

    public void resume() {
        if (state != State.PAUSED && state != State.ENDED) {
            return;
        }
        long pausedAt = pausedAtNanos;
        boolean stale = state == State.PAUSED
                && pausedAt != 0
                && (System.nanoTime() - pausedAt) > STALE_PAUSE_NANOS;
        pausedAtNanos = 0;

        long resumePos = clock.currentPositionMicros(hasAudio, audioOutput.getLine(), false);

        clock.resume(audioOutput.getLine());
        audioOutput.startLine();

        state = State.PLAYING;
        if (stale) {
            seekTo(resumePos);
        } else {
            signalGate();
        }
    }

    public void seekToFraction(double fraction) {
        if (durationMicros <= 0) {
            return;
        }
        long target = (long) (Math.max(0.0, Math.min(1.0, fraction)) * durationMicros);
        seekTo(target);
    }

    public void seekTo(long targetMicros) {
        long target = Math.max(0, targetMicros);
        long duration = durationMicros;
        if (duration > 0) {
            long maxTarget = Math.max(0, duration - SEEK_END_MARGIN_MICROS);
            target = Math.min(target, maxTarget);
        }
        seekTargetMicros = target;
        seekRequested = true;
        signalGate();
    }

    public long positionMicros() {
        return clock.currentPositionMicros(hasAudio, audioOutput.getLine(), state == State.PLAYING);
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

    @Nullable
    public ResourceLocation prepareFrame() {
        return renderer.prepareFrame(positionMicros(), frameQueue, freeBuffers);
    }

    private void decodeLoop() {
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
            if (!info.hasVideo()) {
                throw new IllegalStateException("Stream has no video track");
            }
            int[] target = FFmpegCli.fitWithin(info.width(), info.height(), getContext().getConfigStore().videoMaxWidth(), getContext().getConfigStore().videoMaxHeight());
            videoWidth = target[0];
            videoHeight = target[1];
            durationMicros = Math.max(0, info.durationMicros());
            frameDurationMicros = info.frameDurationMicros();

            hasAudio = info.hasAudio() && audioOutput.open(info);

            freeBuffers.clear();
            int frameBytes = videoWidth * videoHeight * 4;
            for (int i = 0; i < FRAME_QUEUE_CAPACITY + 4; i++) {
                freeBuffers.offer(ByteBuffer.allocateDirect(frameBytes));
            }

            startSession(0);
            clock.start(0, audioOutput.getLine());
            state = State.PLAYING;

            while (running) {
                if (!awaitResumeOrSeek()) {
                    break;
                }
                if (seekRequested) {
                    performSeek();
                }

                VideoFrame decoded = readVideoFrame();
                if (decoded == null) {
                    if (seekRequested || !running) {
                        continue;
                    }
                    onEndOfStream();
                    continue;
                }
                enqueue(decoded);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            errorMessage = t.getMessage() != null ? t.getMessage() : t.toString();
            state = State.FAILED;
            LiasMediaPlayer.LOGGER.warn("Video playback failed for {}: {}", url, errorMessage);
        } finally {
            audioOutput.close();
            session.kill();
        }
    }

    @Nullable
    private VideoFrame readVideoFrame() throws IOException, InterruptedException {
        ReadableByteChannel channel = session.getVideoChannel();
        if (channel == null) {
            return null;
        }

        ByteBuffer buffer = null;
        while (running && !seekRequested) {
            buffer = freeBuffers.poll(50, TimeUnit.MILLISECONDS);
            if (buffer != null) {
                break;
            }
        }
        if (buffer == null) {
            return null;
        }

        buffer.clear();
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                freeBuffers.offer(buffer);
                return null;
            }
        }
        buffer.flip();

        long ts = sessionBaseMicros + frameIndex * frameDurationMicros;
        frameIndex++;
        return new VideoFrame(ts, videoWidth, videoHeight, buffer);
    }

    private boolean awaitResumeOrSeek() throws InterruptedException {
        gate.lock();
        try {
            while (running && state == State.PAUSED && !seekRequested) {
                gateSignal.await();
            }
            return running;
        } finally {
            gate.unlock();
        }
    }

    private void performSeek() {
        long target = seekTargetMicros;
        seekRequested = false;

        audioOutput.flushLine();
        java.util.List<VideoFrame> drained = new java.util.ArrayList<>();
        frameQueue.drainTo(drained);
        drained.forEach(f -> freeBuffers.offer(f.rgbaBuffer()));

        try {
            startSession(target / 1_000_000.0);
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.warn("Seek failed for {}: {}", url, e.toString());
        }

        clock.seekTo(target, audioOutput.getLine());
        if (state == State.ENDED) {
            state = State.PLAYING;
        }
    }

    private void onEndOfStream() throws InterruptedException {
        audioOutput.drainLine();
        state = State.ENDED;
        gate.lock();
        try {
            while (running && state == State.ENDED && !seekRequested) {
                gateSignal.await();
            }
        } finally {
            gate.unlock();
        }
        if (seekRequested) {
            state = State.PLAYING;
        }
    }

    private void enqueue(VideoFrame frame) {
        while (running && !seekRequested) {
            try {
                if (frameQueue.offer(frame, 50, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void startSession(double startSeconds) throws IOException {
        int gen = ++sessionGen;
        frameIndex = 0;
        sessionBaseMicros = Math.round(startSeconds * 1_000_000.0);

        session.start(mediaUrl, videoWidth, videoHeight, startSeconds, hasAudio, audioOutput.getSampleRate(), audioOutput.getChannels(), gen, (in) -> {
            audioOutput.pumpAudio(gen, () -> sessionGen, () -> running, in);
        });
    }
}
