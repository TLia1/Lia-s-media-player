package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.media.MediaUrlResolver;
import com.lia.mediaplayer.media.Volume;
import com.lia.mediaplayer.tools.FFmpegCli;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One playing (or paused) video. Owns a pair of background ffmpeg processes (one
 * for video, one for audio) driven through {@link FFmpegCli}, a small queue of
 * decoded frames, an optional audio line, and the {@link DynamicTexture} that the
 * GUI blits.
 *
 * <h2>Decoding model</h2>
 * <p>Instead of the in-process JavaCV grabber, we shell out to the standalone
 * ffmpeg binary that {@link com.lia.mediaplayer.tools.MediaBinaries} manages. ffmpeg writes scaled
 * {@code rgba} frames to one pipe and {@code s16le} PCM to another; we read those
 * pipes on background threads. {@code ffprobe} gives us dimensions, frame rate,
 * duration and audio layout up front.</p>
 *
 * <h2>Threading</h2>
 * <ul>
 *   <li><b>Decode thread</b> — orchestrates the session: launches ffmpeg, reads
 *       video frames in order, timestamps them from the frame index and frame
 *       rate, and pushes them onto {@link #frameQueue}, blocking (back-pressure)
 *       while the queue is full so ffmpeg pre-buffers a jitter cushion ahead of the
 *       clock. Also handles pause and seek. Never touches OpenGL.</li>
 *   <li><b>Audio thread</b> — reads PCM from the audio process and blocking-writes
 *       it to the {@link SourceDataLine} (which paces it to real time).</li>
 *   <li><b>Render/main thread</b> — {@link #prepareFrame()} selects the frame
 *       matching the playback clock and uploads it to the texture.</li>
 * </ul>
 *
 * <h2>Clock</h2>
 * When the video has audio, the audio line's {@code getMicrosecondPosition()} is
 * the master clock (so picture follows sound). Otherwise a wall clock that only
 * advances while playing is used. Video frames are shown once their timestamp is
 * &le; the clock; late frames are skipped.
 */
public final class VideoPlayer {
    /** Output frames are scaled to fit this box (never upscaled) to bound CPU/VRAM. */
    private static final int MAX_WIDTH = 854;
    private static final int MAX_HEIGHT = 480;
    /**
     * How many decoded frames to buffer ahead of the playback clock. This is the
     * jitter cushion that absorbs an uneven/slow connection: the decoder keeps it
     * full (see {@link #enqueue}) so a brief network stall drains the buffer instead
     * of freezing the picture. Each frame costs up to {@code MAX_WIDTH*MAX_HEIGHT*4}
     * bytes, so this also bounds memory — raise it for a deeper cushion at the cost
     * of RAM, lower it on memory-constrained machines.
     */
    private static final int FRAME_QUEUE_CAPACITY = 64;
    /** Audio is never more than stereo: most lines don't accept more, and we don't need it. */
    private static final int MAX_AUDIO_CHANNELS = 2;
    /**
     * Never let a seek land in the very last slice of the stream: ffmpeg can return
     * EOF there with no decodable frame after the seek point, which freezes the
     * picture on the old frame and leaves the seek bar stuck. Keep a small margin
     * before the reported end so there is always something left to play.
     */
    private static final long SEEK_END_MARGIN_MICROS = 500_000L; // 0.5s
    /**
     * Past this much wall-clock time paused, we assume the current ffmpeg session
     * may have gone stale — for network inputs (YouTube/Discord/…) the server can
     * drop an idle connection, and our paused ffmpeg processes are blocked on a
     * full output pipe so they aren't reading the socket. Resuming such a session
     * freezes the picture on a dead pipe (or a dead audio process stalls the audio
     * clock). When the pause exceeds this, we relaunch the session at the current
     * position on resume (the proven seek path) instead of un-pausing in place.
     */
    private static final long STALE_PAUSE_NANOS = 3_000_000_000L; // 3s
    private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

    public enum State {LOADING, PLAYING, PAUSED, ENDED, FAILED}

    private final String url;

    // --- Decode side (written by decode thread, read by render thread) ------
    private final BlockingQueue<VideoFrame> frameQueue = new ArrayBlockingQueue<>(FRAME_QUEUE_CAPACITY);
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
    private int audioSampleRate;
    private int audioChannels;

    // --- Current ffmpeg session (swapped on seek) ---------------------------
    /** Bumped every time the processes are (re)launched, so the audio thread can tell sessions apart. */
    private volatile int sessionGen;
    @Nullable
    private volatile Process videoProcess;
    @Nullable
    private volatile InputStream videoIn;
    @Nullable
    private volatile Process audioProcess;
    @Nullable
    private volatile Thread audioThread;
    /** Playback time (micros) the current session started at; video timestamps build on this. */
    private volatile long sessionBaseMicros;
    /** Video frames read in the current session (decode thread only). */
    private long frameIndex;

    // --- Pause / seek gate (decode thread waits here) -----------------------
    private final ReentrantLock gate = new ReentrantLock();
    private final Condition gateSignal = gate.newCondition();
    private volatile boolean seekRequested;
    private volatile long seekTargetMicros;

    // --- Clock --------------------------------------------------------------
    private final Object clockLock = new Object();
    @Nullable
    private volatile SourceDataLine audioLine;
    // Volume is shared by every player (see com.lia.mediaplayer.media.Volume) so the
    // level stays in sync and carries over when switching to another video/track.
    private volatile float lastAppliedGain = -1f; // last effective 0..1 pushed to the line
    private long clockOffsetMicros;   // playback time represented by lineBase / wall baseline
    private long lineBaseMicros;      // audio line position captured at the last (re)baseline
    private long wallAccumMicros;     // accumulated time while paused (no-audio clock)
    private long wallResumeNanos;     // nanoTime when playback last (re)started (no-audio clock)
    private volatile long pausedAtNanos; // nanoTime when the player was last paused (0 if not paused)

    // --- Render side (main thread only) -------------------------------------
    @Nullable
    private ResourceLocation textureLocation;
    @Nullable
    private DynamicTexture texture;
    @Nullable
    private NativeImage nativeImage;
    @Nullable
    private VideoFrame currentFrame;

    public VideoPlayer(String url) {
        this.url = url;
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

    /** True once an audio line was successfully opened (i.e. there is sound to control). */
    public boolean hasAudio() {
        return hasAudio;
    }

    /** Current volume in 0..1 (the shared level). */
    public float volume() {
        return Volume.level();
    }

    public boolean isMuted() {
        return Volume.isMuted();
    }

    /** Sets the (shared) volume (0..1) and applies it to the audio line immediately. */
    public void setVolume(float value) {
        Volume.set(value);
        SourceDataLine line = audioLine;
        if (line != null) {
            applyGain(line);
        }
    }

    public void changeVolume(float delta) {
        setVolume(Volume.level() + delta);
    }

    public void toggleMute() {
        Volume.toggleMute();
        SourceDataLine line = audioLine;
        if (line != null) {
            applyGain(line);
        }
    }

    public int videoWidth() {
        return videoWidth;
    }

    public int videoHeight() {
        return videoHeight;
    }

    /** Total length in microseconds, or 0 if unknown (some live streams). */
    public long durationMicros() {
        return durationMicros;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Starts the background decode thread. Safe to call once. */
    public void start() {
        if (decodeThread != null) {
            return;
        }
        Thread thread = new Thread(this::decodeLoop, "liasmediaplayer-video-" + TEXTURE_ID.get());
        thread.setDaemon(true);
        decodeThread = thread;
        thread.start();
    }

    /** Stops decoding, frees the audio line and texture. Call on the main thread. */
    public void dispose() {
        running = false;
        // Wake the decode thread if it is parked on the pause/seek gate.
        signalGate();
        // Unblock a paused audio thread stuck in line.write, then tear down processes.
        closeAudioLine();
        killSession();
        Thread thread = decodeThread;
        if (thread != null) {
            thread.interrupt();
        }
        releaseTexture();
        frameQueue.clear();
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
            // Restart from the beginning.
            seekTo(0);
            resume();
        }
    }

    public void pause() {
        if (state != State.PLAYING) {
            return;
        }
        synchronized (clockLock) {
            // Freeze the no-audio wall clock at the current position.
            wallAccumMicros = currentPositionMicrosLocked();
        }
        pausedAtNanos = System.nanoTime();
        state = State.PAUSED;
        SourceDataLine line = audioLine;
        if (line != null) {
            // Stopping the line stalls the audio thread inside line.write, which in
            // turn back-pressures the audio ffmpeg process: playback freezes cleanly.
            line.stop();
        }
    }

    public void resume() {
        if (state != State.PAUSED && state != State.ENDED) {
            return;
        }
        // If we were paused long enough that the ffmpeg session may be stale, don't
        // try to un-pause the (possibly dead) processes in place — that's what makes
        // the picture freeze until the user scrubs the seek bar. Instead relaunch a
        // fresh session at the current position via the seek path, which is known to
        // recover cleanly.
        long pausedAt = pausedAtNanos;
        boolean stale = state == State.PAUSED
                && pausedAt != 0
                && (System.nanoTime() - pausedAt) > STALE_PAUSE_NANOS;
        pausedAtNanos = 0;

        long resumePos;
        synchronized (clockLock) {
            resumePos = currentPositionMicrosLocked();
            wallResumeNanos = System.nanoTime();
            SourceDataLine line = audioLine;
            if (line != null) {
                lineBaseMicros = line.getMicrosecondPosition();
                clockOffsetMicros = wallAccumMicros;
                line.start();
            }
        }
        state = State.PLAYING;
        if (stale) {
            // Relaunch the ffmpeg processes (video + audio) from where we paused.
            // performSeek (on the decode thread) flushes the queue/line and re-baselines
            // the clock, so playback continues seamlessly from the paused position.
            seekTo(resumePos);
        } else {
            signalGate();
        }
    }

    /** Seeks to {@code fraction} (0..1) of the total duration. */
    public void seekToFraction(double fraction) {
        if (durationMicros <= 0) {
            return;
        }
        long target = (long) (Math.max(0.0, Math.min(1.0, fraction)) * durationMicros);
        seekTo(target);
    }

    public void seekTo(long targetMicros) {
        long target = Math.max(0, targetMicros);
        // Clamp to just before the end so the user can't scrub past the actual
        // content (which would freeze the picture and bug the bar).
        long duration = durationMicros;
        if (duration > 0) {
            long maxTarget = Math.max(0, duration - SEEK_END_MARGIN_MICROS);
            target = Math.min(target, maxTarget);
        }
        seekTargetMicros = target;
        seekRequested = true;
        signalGate();
    }

    /** Current playback position in microseconds. */
    public long positionMicros() {
        synchronized (clockLock) {
            return currentPositionMicrosLocked();
        }
    }

    /** Playback progress as a 0..1 fraction (0 when the duration is unknown). */
    public double progress() {
        long duration = durationMicros;
        if (duration <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, positionMicros() / (double) duration));
    }

    private long currentPositionMicrosLocked() {
        SourceDataLine line = audioLine;
        if (hasAudio && line != null) {
            return clockOffsetMicros + (line.getMicrosecondPosition() - lineBaseMicros);
        }
        long base = wallAccumMicros;
        if (state == State.PLAYING) {
            base += (System.nanoTime() - wallResumeNanos) / 1000L;
        }
        return base;
    }

    private void signalGate() {
        gate.lock();
        try {
            gateSignal.signalAll();
        } finally {
            gate.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Render side — select and upload the current frame (main thread)
    // ------------------------------------------------------------------

    /**
     * Advances the displayed frame to match the playback clock and uploads it to
     * the GPU. Returns the texture to blit, or {@code null} if nothing is ready
     * yet. Must run on the render thread.
     */
    @Nullable
    public ResourceLocation prepareFrame() {
        long position = positionMicros();

        VideoFrame chosen = currentFrame;
        VideoFrame head;
        while ((head = frameQueue.peek()) != null && head.tsMicros() <= position) {
            chosen = frameQueue.poll();
        }
        // If we have not shown anything yet, show the first available frame even
        // if its timestamp is slightly ahead (avoids a black box on startup).
        if (currentFrame == null && chosen == null) {
            chosen = frameQueue.poll();
        }

        if (chosen != null && chosen != currentFrame) {
            currentFrame = chosen;
            uploadFrame(chosen);
        }
        return textureLocation;
    }

    private void uploadFrame(VideoFrame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (nativeImage == null || nativeImage.getWidth() != frame.width() || nativeImage.getHeight() != frame.height()) {
            releaseTexture();
            nativeImage = new NativeImage(NativeImage.Format.RGBA, frame.width(), frame.height(), false);
            texture = new DynamicTexture(nativeImage);
            textureLocation = ResourceLocation.fromNamespaceAndPath(
                    LiasMediaPlayer.MODID, "video/" + TEXTURE_ID.getAndIncrement());
            mc.getTextureManager().register(textureLocation, texture);
        }

        int[] abgr = frame.abgr();
        int width = frame.width();
        int height = frame.height();
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                nativeImage.setPixelRGBA(x, y, abgr[row + x]);
            }
        }
        if (texture != null) {
            texture.upload();
        }
    }

    private void releaseTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
        }
        // DynamicTexture.close() also closes the backing NativeImage.
        if (texture != null) {
            texture.close();
            texture = null;
        }
        nativeImage = null;
        currentFrame = null;
    }

    // ------------------------------------------------------------------
    // Decode thread (orchestrator)
    // ------------------------------------------------------------------

    private void decodeLoop() {
        try {
            mediaUrl = MediaUrlResolver.resolve(url);
            FFmpegCli.MediaInfo info = FFmpegCli.probe(mediaUrl);
            if (!info.hasVideo()) {
                throw new IllegalStateException("Stream has no video track");
            }
            int[] target = fitWithin(info.width(), info.height(), MAX_WIDTH, MAX_HEIGHT);
            videoWidth = target[0];
            videoHeight = target[1];
            durationMicros = Math.max(0, info.durationMicros());
            frameDurationMicros = info.frameDurationMicros();

            hasAudio = info.hasAudio() && openAudioLine(info);

            // Launch the first session from the start of the stream.
            startSession(0);

            synchronized (clockLock) {
                clockOffsetMicros = 0;
                lineBaseMicros = audioLine != null ? audioLine.getMicrosecondPosition() : 0;
                wallAccumMicros = 0;
                wallResumeNanos = System.nanoTime();
            }
            state = State.PLAYING;

            byte[] frameBytes = new byte[videoWidth * videoHeight * 4];

            while (running) {
                // Honor pause / handle pending seek before reading the next frame.
                if (!awaitResumeOrSeek()) {
                    break; // disposed
                }
                if (seekRequested) {
                    performSeek();
                }

                VideoFrame decoded = readVideoFrame(frameBytes);
                if (decoded == null) {
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
            closeAudioLine();
            killSession();
        }
    }

    /**
     * Reads exactly one scaled frame from the current video pipe and wraps it as a
     * timestamped {@link VideoFrame}. Returns {@code null} at end of stream.
     */
    @Nullable
    private VideoFrame readVideoFrame(byte[] buffer) throws IOException {
        InputStream in = videoIn;
        if (in == null) {
            return null;
        }
        int read = in.readNBytes(buffer, 0, buffer.length);
        if (read < buffer.length) {
            return null; // EOF (or the process was killed for a seek/dispose)
        }
        long ts = sessionBaseMicros + frameIndex * frameDurationMicros;
        frameIndex++;
        return new VideoFrame(ts, videoWidth, videoHeight, toAbgr(buffer, videoWidth, videoHeight));
    }

    /** Converts a packed {@code rgba} frame to the {@code abgr} ints NativeImage expects. */
    private static int[] toAbgr(byte[] rgba, int width, int height) {
        int[] abgr = new int[width * height];
        for (int i = 0, p = 0; i < abgr.length; i++, p += 4) {
            int r = rgba[p] & 0xFF;
            int g = rgba[p + 1] & 0xFF;
            int b = rgba[p + 2] & 0xFF;
            int a = rgba[p + 3] & 0xFF;
            abgr[i] = (a << 24) | (b << 16) | (g << 8) | r;
        }
        return abgr;
    }

    /**
     * Blocks while paused. Returns {@code false} if the player was disposed while
     * waiting. Returns immediately if a seek is pending (so seeks work while paused).
     */
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

        // Drop any buffered audio so the line can't keep playing pre-seek sound,
        // and so a paused audio thread blocked in line.write is released to exit.
        SourceDataLine line = audioLine;
        if (line != null) {
            line.flush();
        }
        frameQueue.clear();

        // Relaunch both processes from the new position. A failure here just leaves
        // us with no video pipe, which the read loop treats as end-of-stream.
        try {
            startSession(target / 1_000_000.0);
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.warn("Seek failed for {}: {}", url, e.toString());
        }

        synchronized (clockLock) {
            SourceDataLine l = audioLine;
            clockOffsetMicros = target;
            lineBaseMicros = l != null ? l.getMicrosecondPosition() : 0;
            wallAccumMicros = target;
            wallResumeNanos = System.nanoTime();
        }
        if (state == State.ENDED) {
            state = State.PLAYING;
        }
    }

    private void onEndOfStream() throws InterruptedException {
        // Let any queued audio finish playing before we mark the video as ended,
        // so the auto-close (driven by the ENDED state) doesn't clip the tail.
        SourceDataLine line = audioLine;
        if (line != null) {
            line.drain();
        }
        state = State.ENDED;
        // Park until the user seeks (replay) or the player is disposed.
        gate.lock();
        try {
            while (running && state == State.ENDED && !seekRequested) {
                gateSignal.await();
            }
        } finally {
            gate.unlock();
        }
        if (seekRequested) {
            // performSeek runs at the top of the loop.
            state = State.PLAYING;
        }
    }

    private void enqueue(VideoFrame frame) {
        // Back-pressure: block until the queue has room, so ffmpeg reads ahead and
        // keeps the buffer full (the jitter cushion that absorbs a slow/uneven
        // connection) instead of racing to the end of the stream. Blocking the decode
        // thread is safe — audio runs on its own thread and process — but we stay
        // responsive to pause/seek/dispose so the decode loop can act on them
        // promptly. A frame dropped on the way out here is one the queue is about to
        // flush on a seek anyway.
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

    // ------------------------------------------------------------------
    // ffmpeg session management (decode thread)
    // ------------------------------------------------------------------

    /**
     * Tears down any running processes and starts a fresh video (and, if present,
     * audio) ffmpeg process seeking to {@code startSeconds}. Resets the per-session
     * frame counter and base timestamp.
     */
    private void startSession(double startSeconds) throws IOException {
        killSession();

        int gen = ++sessionGen;
        frameIndex = 0;
        sessionBaseMicros = Math.round(startSeconds * 1_000_000.0);

        Process video = FFmpegCli.openVideo(mediaUrl, videoWidth, videoHeight, startSeconds);
        videoProcess = video;
        videoIn = video.getInputStream();

        SourceDataLine line = audioLine;
        if (hasAudio && line != null) {
            Process audio = FFmpegCli.openAudio(mediaUrl, audioSampleRate, audioChannels, startSeconds);
            audioProcess = audio;
            InputStream audioStream = audio.getInputStream();
            Thread thread = new Thread(() -> audioLoop(gen, audioStream, line),
                    "liasmediaplayer-audio-" + gen);
            thread.setDaemon(true);
            audioThread = thread;
            thread.start();
        }
    }

    /** Destroys the current session's processes and stops its audio thread. */
    private void killSession() {
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
        videoIn = null;
        Thread thread = audioThread;
        if (thread != null) {
            thread.interrupt();
            audioThread = null;
        }
    }

    /**
     * Pumps PCM from one audio ffmpeg process into the line. Exits when the player
     * stops, the session is superseded (a seek launched a newer one), or the stream
     * ends. Blocking writes pace playback; a stopped line back-pressures into a pause.
     */
    private void audioLoop(int gen, InputStream in, SourceDataLine line) {
        byte[] buffer = new byte[8192];
        try {
            int read;
            while (running && gen == sessionGen && (read = in.read(buffer)) >= 0) {
                if (gen != sessionGen) {
                    break;
                }
                applyGain(line);
                line.write(buffer, 0, read);
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

    // ------------------------------------------------------------------
    // Audio line setup (decode thread)
    // ------------------------------------------------------------------

    private boolean openAudioLine(FFmpegCli.MediaInfo info) {
        int channels = Math.min(Math.max(info.channels(), 1), MAX_AUDIO_CHANNELS);
        int sampleRate = info.sampleRate();
        if (sampleRate <= 0) {
            return false;
        }
        try {
            // s16le, signed, little-endian — matches ffmpeg's pcm_s16le output exactly.
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(lineInfo)) {
                LiasMediaPlayer.LOGGER.info("No audio line for {} ch @ {} Hz; playing video without sound",
                        channels, sampleRate);
                return false;
            }
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
            line.open(format);
            line.start();
            applyGain(line);
            audioLine = line;
            audioSampleRate = sampleRate;
            audioChannels = channels;
            return true;
        } catch (Exception e) {
            LiasMediaPlayer.LOGGER.info("Could not open audio for {}: {}", url, e.toString());
            audioLine = null;
            return false;
        }
    }

    /**
     * Pushes the shared volume onto the line (dB gain, master-scaled). Delegates to
     * {@link Volume#apply}, which skips redundant hardware writes so the audio thread
     * can re-apply it every buffer to follow live master-volume changes.
     */
    private void applyGain(SourceDataLine line) {
        lastAppliedGain = Volume.apply(line, lastAppliedGain);
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

    private static int[] fitWithin(int width, int height, int maxWidth, int maxHeight) {
        double scale = Math.min(1.0, Math.min(maxWidth / (double) width, maxHeight / (double) height));
        int w = Math.max(2, (int) Math.round(width * scale));
        int h = Math.max(2, (int) Math.round(height * scale));
        // Even dimensions keep the scaler/codecs happy.
        if ((w & 1) == 1) {
            w++;
        }
        if ((h & 1) == 1) {
            h++;
        }
        return new int[]{w, h};
    }

    /** A single decoded, display-ready frame in {@code abgr} layout. */
    private record VideoFrame(long tsMicros, int width, int height, int[] abgr) {
    }
}
