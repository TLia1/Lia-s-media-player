package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.media.AudioGain;
import com.lia.mediaplayer.media.MediaPlayback;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.tools.FFmpegCli;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
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
public final class VideoPlayer implements MediaPlayback {
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
    /**
     * Bytes in one decoded frame, known once the stream has been probed.
     */
    private int frameBytes;
    /**
     * How many frame buffers have been handed out to {@link #freeBuffers} so far.
     *
     * <p>The pool grows on demand up to {@link #bufferPoolLimit()} rather than being
     * filled up front. A buffer is {@code width * height * 4} bytes of <em>off-heap</em>
     * memory — 1.5 MiB at 480p, 3.5 MiB at 720p — so pre-allocating the whole pool
     * reserved around a hundred megabytes per player, and close to a gigabyte across
     * four 720p windows, before a single frame had been decoded. A stream that keeps up
     * with the clock never needs more than three or four.</p>
     *
     * <p>Decode thread only, except for the reset in {@link #decodeLoop}.</p>
     */
    private int buffersAllocated;
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
     * Whether anything is going to look at the picture.
     *
     * <p>False while the window is hidden, and then ffmpeg is started without a video
     * stream at all. Hiding a player is how the mod is used as a music player, and a
     * hidden window used to pay the full price of a video it was throwing away frame by
     * frame: the decode, the scale, and fifty megabytes a second down a pipe, all so
     * that {@link #discardDueFrames} could drop the result. {@code -vn} is the honest
     * version of that, and it leaves only the sound — which is the part someone hiding
     * the window still wants.</p>
     *
     * <p>Only meaningful for a stream that has sound. A silent video with no picture has
     * no output at all, so one keeps decoding as before rather than becoming a session
     * that produces nothing and can never reach its end.</p>
     */
    private volatile boolean pictureWanted = true;
    /**
     * Set by the audio pump when its stream ends, so the decode thread can notice the
     * track is over while there is no video stream to reach EOF instead.
     *
     * <p>With a picture, end-of-track is a null frame out of {@link #readVideoFrame};
     * without one, nothing else would ever say so and a hidden player would sit on a
     * finished track forever instead of advancing its queue.</p>
     */
    private volatile boolean audioStreamEnded;
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

    /**
     * A caller-supplied ceiling on the decoded picture, or {@code 0} for none — see
     * {@link #setResolutionCap}.
     */
    private int maxWidth;
    private int maxHeight;

    /**
     * Caps the decoded picture below the user's video-quality setting.
     *
     * <p>For an off-screen surface drawn small: a television the size of a block has no
     * use for a 480p decode, and the memory a frame buffer costs is width x height x 4
     * bytes per queued frame. Must be called before {@link #start()}; the size is chosen
     * once, when the stream is probed.</p>
     */
    public void setResolutionCap(int width, int height) {
        this.maxWidth = Math.max(0, width);
        this.maxHeight = Math.max(0, height);
    }

    public VideoPlayer(String url) {
        this.url = url;
        this.frameQueueCapacity = ConfigStore.FRAME_QUEUE_CAPACITY.getValue();
        this.frameQueue = new ArrayBlockingQueue<>(this.frameQueueCapacity);
        this.freeBuffers = new ArrayBlockingQueue<>(this.frameQueueCapacity + 4);
        this.renderer = new VideoRenderer();
        this.audioOutput = new AudioOutput(url);
        this.clock = new PlaybackClock();
        this.session = new FFmpegSession();
    }

    private static MediaPlayerContext getContext() {
        return MediaPlayerContext.get();
    }

    public String url() {
        return url;
    }

    public State state() {
        return state;
    }

    /**
     * The engine's own {@link State}, in the vocabulary everything outside the engines
     * speaks. The two enums have the same five names today; this method is what keeps
     * that a coincidence rather than a contract.
     */
    @Override
    public PlaybackState playbackState() {
        return switch (state) {
            case LOADING -> PlaybackState.LOADING;
            case PLAYING -> PlaybackState.PLAYING;
            case PAUSED -> PlaybackState.PAUSED;
            case ENDED -> PlaybackState.ENDED;
            case FAILED -> PlaybackState.FAILED;
        };
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

    @Override
    public AudioGain audioGain() {
        return audioOutput.gain();
    }

    @Override
    public void setAudioGain(AudioGain gain) {
        audioOutput.setGain(gain);
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
        List<VideoFrame> drained = new ArrayList<>();
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

    /**
     * Says whether the picture is still being looked at.
     *
     * <p>Relaunching through {@link #seekTo} rather than by a path of its own: swapping
     * a session for one with a different set of streams is exactly what a seek already
     * does, at exactly the position this needs, and it is the one relaunch path that has
     * been proven against real streams. The only cost is that turning the picture back
     * on shows the window's "seeking" notice for the second ffmpeg takes to produce a
     * frame — which is the truth about what is happening.</p>
     *
     * <p>Before playback has started there is no session to swap: the flag is recorded
     * and {@link #startSession} reads it when it opens the first one.</p>
     */
    public void setPictureWanted(boolean wanted) {
        if (pictureWanted == wanted) {
            return;
        }
        pictureWanted = wanted;
        // A silent stream produces video either way (see sessionHasVideo), so there is
        // nothing to relaunch for. Nor is there before the stream has been probed: the
        // wish is recorded above and startSession reads it when it opens the first
        // session — which is the case a hidden window advancing its queue lands in,
        // since the player it hands this to has not started yet.
        if (hasAudio && (state == State.PLAYING || state == State.PAUSED)) {
            seekTo(positionMicros());
        }
    }

    /**
     * Whether the running session carries a picture.
     *
     * <p>Not the same question as {@link #pictureWanted}: a stream with no sound has
     * nothing else to produce, so it keeps its video stream even while hidden rather
     * than becoming a session with no output at all — one that could never reach its own
     * end, and would leave a queue stuck on it forever.</p>
     */
    private boolean sessionHasVideo() {
        return pictureWanted || !hasAudio;
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

    /**
     * {@inheritDoc}
     *
     * <p>Handed to {@link PlaybackClock#driftCorrect}, which is where a small correction
     * belongs: sliding the offset the frames' presentation times are compared against
     * moves the picture into place over a few ticks without restarting the pipeline. Only
     * a correction too large for that becomes a seek. A seek that is already in flight is
     * left alone — correcting towards a position while another one is landing would fight
     * with it.</p>
     */
    @Override
    public boolean driftCorrect(long targetMicros, long toleranceMicros) {
        if (isSeeking()) {
            return false;
        }
        PlaybackClock.Drift drift = clock.driftCorrect(targetMicros, toleranceMicros,
                hasAudio, audioOutput.getLine(), state == State.PLAYING);
        if (drift == PlaybackClock.Drift.SEEK) {
            seekTo(targetMicros);
            return true;
        }
        return drift == PlaybackClock.Drift.NUDGED;
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
            // The user's video-quality setting is a budget on their machine, so a
            // caller-supplied cap may only lower it, never raise it.
            int capW = getContext().getConfigStore().videoMaxWidth();
            int capH = getContext().getConfigStore().videoMaxHeight();
            if (maxWidth > 0) {
                capW = Math.min(capW, maxWidth);
            }
            if (maxHeight > 0) {
                capH = Math.min(capH, maxHeight);
            }
            int[] target = FFmpegCli.fitWithin(info.width(), info.height(), capW, capH);
            videoWidth = target[0];
            videoHeight = target[1];
            durationMicros = Math.max(0, info.durationMicros());
            frameDurationMicros = info.frameDurationMicros();

            hasAudio = info.hasAudio() && audioOutput.open(info);

            freeBuffers.clear();
            frameBytes = videoWidth * videoHeight * 4;
            buffersAllocated = 0;

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

                if (!sessionHasVideo()) {
                    if (awaitPictureOrEnd()) {
                        continue; // the picture is wanted again, or a seek/stop landed
                    }
                    onEndOfStream(); // the sound ran out, and there is no frame to say so
                    continue;
                }

                VideoFrame decoded = readVideoFrame();
                if (decoded == null) {
                    if (seekRequested || !running) {
                        continue;
                    }
                    reportStreamEnd();
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
            // A session that was asked for a picture and has no channel to read it from
            // is a bug, not a finished track — and the caller cannot tell the two apart,
            // because both arrive here as a null frame. Say so rather than let it be
            // mistaken for the end of the video and close the window in silence.
            if (sessionHasVideo()) {
                LiasMediaPlayer.LOGGER.warn(
                        "No video channel for {} despite the session being asked for one", url);
            }
            return null;
        }

        ByteBuffer buffer = takeBuffer();
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

    /**
     * A buffer to decode the next frame into: one the render thread has given back, or
     * a freshly allocated one while the pool is still under its limit.
     *
     * <p>Growing on demand is what keeps the memory bill proportional to how far behind
     * the pipeline actually runs. A stream that keeps up with the clock cycles three or
     * four buffers forever; only one that has to buffer ahead through a stall pays for
     * the rest, and only for as long as the player lives.</p>
     *
     * <p>Buffers are never freed by hand, and deliberately: {@link #dispose} interrupts
     * the decode thread rather than joining it, so a {@code memFree} here could land
     * while that thread is still reading ffmpeg's pipe into the very buffer being
     * released. A use-after-free on the render path takes the game down with no Java
     * stack to explain it; letting the collector reclaim them costs a GC cycle of
     * latency and cannot.</p>
     *
     * @return a buffer, or {@code null} when the player is stopping or a seek has landed
     */
    @Nullable
    private ByteBuffer takeBuffer() throws InterruptedException {
        while (running && !seekRequested) {
            ByteBuffer pooled = freeBuffers.poll();
            if (pooled != null) {
                return pooled;
            }
            if (buffersAllocated < bufferPoolLimit()) {
                buffersAllocated++;
                return ByteBuffer.allocateDirect(frameBytes);
            }
            // The pool is at its limit and every buffer is either queued or on screen:
            // wait for the render thread to hand one back. This is the back-pressure
            // that paces ffmpeg — see the "no -re" note in FFmpegCli.openVideo.
            ByteBuffer waited = freeBuffers.poll(50, TimeUnit.MILLISECONDS);
            if (waited != null) {
                return waited;
            }
        }
        return null;
    }

    /**
     * The most frame buffers this player will ever hold: everything the queue can take,
     * plus the one being decoded into, the one on screen, and a little slack.
     */
    private int bufferPoolLimit() {
        return frameQueueCapacity + 4;
    }

    /**
     * Parks the decode thread while the session is running without a video stream.
     *
     * <p>There is nothing to read in that state — {@link #readVideoFrame} would answer
     * null and be taken for the end of the track — so the thread waits here instead,
     * and the sound plays on its own thread meanwhile.</p>
     *
     * @return {@code true} when something other than the end of the sound woke it
     */
    private boolean awaitPictureOrEnd() throws InterruptedException {
        gate.lock();
        try {
            while (running && !sessionHasVideo() && !seekRequested && !audioStreamEnded) {
                gateSignal.await();
            }
            return !running || sessionHasVideo() || seekRequested;
        } finally {
            gate.unlock();
        }
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
        List<VideoFrame> drained = new ArrayList<>();
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
        if (!sessionHasVideo()) {
            // Same reasoning as the failed-launch branch above: a session with no video
            // stream will never produce the frame that ends the seek. Leaving it in
            // flight would freeze positionMicros() on the target, and a hidden player
            // whose clock never advances is one whose queue never moves on.
            clearPendingSeek();
        }
        // Note what is *not* here: the seek is not over because ffmpeg has been
        // launched. It is over when the first frame it produces reaches the screen,
        // which prepareFrame decides.
        if (state == State.ENDED) {
            state = State.PLAYING;
        }
    }

    /**
     * Says in the log why the video stream stopped, when ffmpeg has something to say
     * about it.
     *
     * <p>A stream that simply ran out is the normal case and stays quiet at DEBUG. A
     * process that quit with a status, or wrote to stderr, is a failure wearing the same
     * clothes — the decode thread sees an EOF either way — and that one gets a warning
     * with ffmpeg's own words in it. Without this a player that closes itself a second
     * after opening leaves nothing in the log but its own silence.</p>
     */
    private void reportStreamEnd() {
        String detail = session.describeEnd();
        if (!detail.isEmpty()) {
            LiasMediaPlayer.LOGGER.warn("Video stream for {} ended early — {}", url, detail);
        } else {
            LiasMediaPlayer.LOGGER.debug("Video stream for {} reached its end", url);
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
        audioStreamEnded = false;

        session.start(mediaUrl, videoWidth, videoHeight, startSeconds, sessionHasVideo(), hasAudio,
                audioOutput.getSampleRate(), audioOutput.getChannels(), gen, (in) -> {
            audioOutput.pumpAudio(gen, () -> sessionGen, () -> running, in,
                    () -> onAudioStreamEnded(gen));
        });
    }

    /**
     * The audio pump reporting that its stream reached its end.
     *
     * <p>Ignored while there is a picture: the null frame out of the video stream is the
     * authority there, and it arrives with the frames still queued ahead of it, whereas
     * the sound is written to the line before the last frames have been shown.</p>
     */
    private void onAudioStreamEnded(int gen) {
        if (gen != sessionGen || sessionHasVideo()) {
            return;
        }
        audioStreamEnded = true;
        signalGate();
    }
}
