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
    private static final long SEEK_END_MARGIN_MICROS = 500_000L; // 0.5s
    private static final AtomicInteger PLAYER_ID = new AtomicInteger(0);

    public enum State {LOADING, PLAYING, PAUSED, ENDED, FAILED}

    private final String url;
    private final VideoRenderer renderer;
    private final AudioOutput audioOutput;
    private final PlaybackClock clock;
    private final FFmpegSession session;

    // --- Decode side (written by decode thread, read by render thread) ------
    private final int frameQueueCapacity;
    private final BlockingQueue<VideoFrame> frameQueue;
    private final BlockingQueue<ByteBuffer> freeBuffers;
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
    /**
     * Where playback is <em>going</em> while a seek is in flight, or {@code -1}.
     *
     * <p>A seek is not instant: it hands a target to the decode thread, which kills
     * ffmpeg and starts it again at the new offset — the better part of a second. Until
     * that lands the clock still reports the old position, so a seek bar reading the
     * clock would snap back to where playback was and only jump to the requested spot
     * once ffmpeg came up. Reporting the target for the whole of that gap is what makes
     * the bar stay where it was put.</p>
     */
    private volatile long pendingSeekMicros = -1;
    /**
     * The newest session that existed when the pending seek was asked for. Only frames
     * from a <em>later</em> one may be shown while the seek is in flight — see
     * {@link VideoFrame#gen()}.
     */
    private volatile int seekBarrierGen;

    public VideoPlayer(String url) {
        this.url = url;
        this.frameQueueCapacity = com.lia.mediaplayer.config.ConfigStore.FRAME_QUEUE_CAPACITY.getValue();
        this.frameQueue = new ArrayBlockingQueue<>(this.frameQueueCapacity);
        this.freeBuffers = new ArrayBlockingQueue<>(this.frameQueueCapacity + 4);
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
        state = State.PAUSED;
        audioOutput.stopLine();
    }

    /**
     * Resumes playback, <em>always</em> relaunching the ffmpeg session from the paused
     * position when coming back from a pause.
     *
     * <p>Un-pausing a session in place does not work. While paused nobody reads ffmpeg's
     * pipes, so the process blocks against a full one; letting it continue afterwards
     * leaves the picture frozen for good rather than for a moment. This was written as a
     * staleness check with a half-second threshold, which every real pause exceeded — so
     * it always relaunched and the in-place path was never actually exercised. Raising
     * the threshold to a value that looked more sensible is what brought it to light:
     * pause for a few seconds, resume, and the video never comes back.</p>
     *
     * <p>So the relaunch is unconditional, and named for what it is rather than dressed
     * up as an optimisation with a threshold that must never be raised. It costs about a
     * second, which {@link #isSeeking()} reports so the window can say it is loading
     * instead of looking hung.</p>
     *
     * <p>Resuming from {@link State#ENDED} is the exception: {@link #togglePause} has
     * already asked for a seek back to the start, and relaunching at the <em>end</em>
     * position here would override it.</p>
     */
    public void resume() {
        if (state != State.PAUSED && state != State.ENDED) {
            return;
        }
        boolean fromPause = state == State.PAUSED;
        long resumePos = clock.currentPositionMicros(hasAudio, audioOutput.getLine(), false);

        clock.resume(audioOutput.getLine());
        audioOutput.startLine();

        state = State.PLAYING;
        if (fromPause) {
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
        // Raised before the request so the render thread stops accepting the outgoing
        // session's frames from this moment, not from whenever the decode thread wakes.
        seekBarrierGen = sessionGen;
        pendingSeekMicros = target;
        seekRequested = true;
        signalGate();
    }

    /**
     * Whether a seek has been asked for and the picture has not caught up yet.
     *
     * <p>True until a frame from the <em>new</em> session is on screen — not merely
     * until ffmpeg has been launched. Launching takes a moment; producing the first
     * frame takes about a second, and that second is the whole of what the viewer
     * experiences. Ending the state at the launch left the last frame frozen on screen
     * with nothing to explain it, which is exactly what the state exists to prevent.</p>
     */
    public boolean isSeeking() {
        return pendingSeekMicros >= 0;
    }

    public long positionMicros() {
        long pending = pendingSeekMicros;
        if (pending >= 0) {
            return pending;
        }
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
        if (pendingSeekMicros >= 0) {
            if (renderer.showFirstFrameAfter(seekBarrierGen, frameQueue, freeBuffers)) {
                // The new session is on screen: the clock is authoritative again and the
                // window can stop saying it is loading.
                clearPendingSeek();
            }
            return renderer.getTextureLocation();
        }
        return renderer.prepareFrame(positionMicros(), frameQueue, freeBuffers);
    }

    /**
     * Ends the "seeking" state. Called when the new session's first frame is shown, and
     * as a backstop whenever the session ends or fails — otherwise a seek that produces
     * no frame at all (past the end of the stream, or a session that would not start)
     * would leave the window loading forever and its position frozen at the target.
     */
    private void clearPendingSeek() {
        pendingSeekMicros = -1;
    }

    /**
     * Throws away the frames that are already due, without drawing any of them.
     *
     * <p>{@link #prepareFrame} is the only thing that takes frames off the queue, and it
     * is called from the window's draw. A window that is hidden is never drawn, so its
     * queue filled up, the decode thread blocked in {@code enqueue} waiting for room,
     * ffmpeg blocked behind it, and the video simply stopped — never reaching its end,
     * so a queue of tracks in a hidden player never moved on. Discarding the due frames
     * on each client tick keeps the pipeline running at the clock's own pace, which is
     * what lets the end of the track arrive.</p>
     *
     * <p>Decoding pictures nobody will see is waste, and the honest fix is to restart
     * the session without a video stream while hidden. That is a bigger change than
     * this, and this is what makes a hidden queue work at all.</p>
     *
     * <p>Render thread only, like {@link #prepareFrame} — the two share the queue.</p>
     */
    public void discardDueFrames() {
        if (pendingSeekMicros >= 0) {
            // Mid-seek, and with nothing drawing there is no picture to hold: drop the
            // old session's leftovers and treat the first frame of the new one as having
            // been "shown", so a hidden player gets past its seek like a visible one.
            VideoFrame frame;
            while ((frame = frameQueue.poll()) != null) {
                freeBuffers.offer(frame.rgbaBuffer());
                if (frame.gen() > seekBarrierGen) {
                    clearPendingSeek();
                    return;
                }
            }
            return;
        }
        long position = positionMicros();
        VideoFrame head;
        while ((head = frameQueue.peek()) != null && head.tsMicros() <= position) {
            VideoFrame frame = frameQueue.poll();
            if (frame == null) {
                break;
            }
            freeBuffers.offer(frame.rgbaBuffer());
        }
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
            for (int i = 0; i < frameQueueCapacity + 4; i++) {
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
            clearPendingSeek();
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
        return new VideoFrame(ts, sessionGen, videoWidth, videoHeight, buffer);
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
            // No session means no frame will ever arrive to end the seek.
            clearPendingSeek();
        }

        clock.seekTo(target, audioOutput.getLine());
        // Note what is *not* here: the seek is not over because ffmpeg has been
        // launched. It is over when the first frame it produces reaches the screen,
        // which prepareFrame decides.
        if (state == State.ENDED) {
            state = State.PLAYING;
        }
    }

    private void onEndOfStream() throws InterruptedException {
        audioOutput.drainLine();
        clearPendingSeek(); // nothing more is coming to end a seek that was in flight
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
                break;
            }
        }
        // A seek (or a dispose) landed while we were waiting for room. The frame is stale
        // now, but its buffer came out of the fixed freeBuffers pool — dropping it here
        // would shrink the pool for good, and after enough seeks readVideoFrame would
        // never find a buffer again and playback would stall.
        freeBuffers.offer(frame.rgbaBuffer());
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
