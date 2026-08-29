package com.lia.mediaplayer.video;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.tools.FFmpegCli;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Consumer;

/**
 * The ffmpeg process (or processes) behind one stretch of video playback, from a
 * {@code startSession} to the next seek, pause or dispose.
 *
 * <h2>One process, two outputs</h2>
 *
 * <p>A video with sound is decoded by a <em>single</em> ffmpeg: the picture comes out of
 * its stdout and the PCM over a loopback TCP connection it makes back to this class —
 * see {@link FFmpegCli#openVideoWithAudio}, which carries the reasoning. The previous
 * shape ran two processes over the same URL, which downloaded and demuxed the stream
 * twice and doubled the cost of every seek.
 *
 * <p>The two-process path is still here and still works. It is what a stream with no
 * audio track uses (there is no second output to open), what the mod falls back to when
 * the loopback listener cannot be opened, and what
 * {@code -Dliasmediaplayer.singleprocess=false} selects for someone whose network stack
 * or security software objects to a process connecting to a listening socket on their
 * own machine.
 */
public class FFmpegSession {

    /**
     * Whether a video with sound is played by one process rather than two.
     *
     * <p>An escape hatch rather than a setting, in the same shape as
     * {@code liasmediaplayer.ytdlp.clients}: the thing a bug report can ask someone to
     * try when the sound is missing and the log shows the handshake failing.
     */
    private static final boolean SINGLE_PROCESS =
            !"false".equalsIgnoreCase(System.getProperty("liasmediaplayer.singleprocess", "true"));

    /**
     * How long the audio side waits for ffmpeg to connect back before giving up and
     * letting the video play silently.
     *
     * <p>ffmpeg opens its outputs once it has opened its input, so the connection lands
     * within a second or two of the stream being reachable at all. This is generous
     * enough to survive a slow network and a loaded machine, and short enough that a
     * session which will never connect does not hold a thread for the length of the
     * video.
     */
    private static final int AUDIO_ACCEPT_TIMEOUT_MS = 30_000;

    @Nullable
    private Process videoProcess;
    @Nullable
    private InputStream videoIn;
    @Nullable
    private ReadableByteChannel videoChannel;
    /**
     * The second process, on the two-process path only. Null when one process serves
     * both outputs.
     */
    @Nullable
    private Process audioProcess;
    @Nullable
    private Thread audioThread;
    /**
     * The loopback listener and the connection ffmpeg made to it, on the single-process
     * path only. Held so {@link #kill()} can close them: closing the listener is also
     * what releases an audio thread still blocked in {@code accept}.
     */
    @Nullable
    private volatile ServerSocket audioListener;
    @Nullable
    private volatile Socket audioSocket;

    /**
     * @param wantVideo whether this session should produce a picture at all. False for a
     *                  hidden window, which then runs a sound-only ffmpeg and leaves
     *                  {@link #getVideoChannel()} null — see {@code
     *                  VideoPlayer.setPictureWanted}. Only ever false alongside
     *                  {@code hasAudio}, because a session with neither stream would
     *                  produce nothing.
     */
    public void start(String mediaUrl, int videoWidth, int videoHeight, double startSeconds,
                      boolean wantVideo, boolean hasAudio, int audioSampleRate, int audioChannels,
                      int sessionGen, Consumer<InputStream> audioLoop) throws IOException {
        kill();

        if (!wantVideo && hasAudio) {
            // Sound alone: the existing audio-only process, with no video channel for
            // the decode thread to read. Nothing to mux, so nothing to hand over a
            // socket either.
            startAudioOnly(mediaUrl, startSeconds, audioSampleRate, audioChannels, sessionGen, audioLoop);
            return;
        }

        if (hasAudio && SINGLE_PROCESS) {
            if (startMuxed(mediaUrl, videoWidth, videoHeight, startSeconds,
                    audioSampleRate, audioChannels, sessionGen, audioLoop)) {
                return;
            }
            // The listener could not be opened at all. Fall through to two processes
            // rather than playing without sound.
        }
        startSeparate(mediaUrl, videoWidth, videoHeight, startSeconds,
                hasAudio, audioSampleRate, audioChannels, sessionGen, audioLoop);
    }

    /**
     * One ffmpeg, video on stdout and audio over loopback.
     *
     * <p>The {@code accept} happens on the audio thread, not here: this runs on the
     * decode thread, which should be reading frames rather than waiting on a handshake.
     * A connection that never arrives therefore costs a silent video, not a stalled one.
     *
     * @return whether the session was started this way
     */
    private boolean startMuxed(String mediaUrl, int videoWidth, int videoHeight, double startSeconds,
                               int audioSampleRate, int audioChannels, int sessionGen,
                               Consumer<InputStream> audioLoop) throws IOException {
        ServerSocket listener;
        try {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
            listener.setSoTimeout(AUDIO_ACCEPT_TIMEOUT_MS);
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.warn(
                    "Could not open a loopback port for audio ({}); falling back to a second ffmpeg", e.toString());
            return false;
        }
        this.audioListener = listener;

        Process video = FFmpegCli.openVideoWithAudio(mediaUrl, videoWidth, videoHeight, startSeconds,
                audioSampleRate, audioChannels,
                urlHost(listener.getInetAddress()), listener.getLocalPort());
        adoptVideo(video);

        Thread thread = new Thread(() -> acceptThenPump(listener, audioLoop),
                "liasmediaplayer-audio-" + sessionGen);
        thread.setDaemon(true);
        this.audioThread = thread;
        thread.start();
        return true;
    }

    /**
     * Waits for ffmpeg's audio connection, then hands its stream to the pump.
     */
    private void acceptThenPump(ServerSocket listener, Consumer<InputStream> audioLoop) {
        Socket socket;
        try {
            socket = listener.accept();
            this.audioSocket = socket;
        } catch (IOException e) {
            // A timeout, or kill() closing the listener under us. Either way there is no
            // sound for this session; the picture is unaffected.
            LiasMediaPlayer.LOGGER.debug("No audio connection from ffmpeg: {}", e.toString());
            return;
        } finally {
            closeQuietly(listener);
            if (this.audioListener == listener) {
                this.audioListener = null;
            }
        }
        try (Socket open = socket) {
            audioLoop.accept(open.getInputStream());
        } catch (IOException e) {
            LiasMediaPlayer.LOGGER.debug("Audio connection ended: {}", e.toString());
        }
    }

    /**
     * The original shape: one ffmpeg for the picture and, when there is sound, a second
     * one for it. Kept for a stream with no audio track and as the fallback path.
     */
    private void startSeparate(String mediaUrl, int videoWidth, int videoHeight, double startSeconds,
                               boolean hasAudio, int audioSampleRate, int audioChannels, int sessionGen,
                               Consumer<InputStream> audioLoop) throws IOException {
        adoptVideo(FFmpegCli.openVideo(mediaUrl, videoWidth, videoHeight, startSeconds));

        if (hasAudio) {
            startAudioOnly(mediaUrl, startSeconds, audioSampleRate, audioChannels, sessionGen, audioLoop);
        }
    }

    /**
     * One ffmpeg producing sound and nothing else — the second process of the two-process
     * path, and the whole of a session for a window nobody is looking at.
     */
    private void startAudioOnly(String mediaUrl, double startSeconds, int audioSampleRate,
                                int audioChannels, int sessionGen,
                                Consumer<InputStream> audioLoop) throws IOException {
        Process audio = FFmpegCli.openAudio(mediaUrl, audioSampleRate, audioChannels, startSeconds);
        this.audioProcess = audio;
        InputStream audioStream = audio.getInputStream();
        Thread thread = new Thread(() -> audioLoop.accept(audioStream), "liasmediaplayer-audio-" + sessionGen);
        thread.setDaemon(true);
        this.audioThread = thread;
        thread.start();
    }

    private void adoptVideo(Process video) {
        this.videoProcess = video;
        this.videoIn = video.getInputStream();
        this.videoChannel = Channels.newChannel(this.videoIn);
    }

    @Nullable
    public ReadableByteChannel getVideoChannel() {
        return videoChannel;
    }

    /**
     * Why the video stream ended, as far as the process can say — its exit status and
     * whatever it wrote to stderr.
     *
     * <p>The end of the stream is the only signal the decode thread gets, and on its own
     * it cannot tell "the video finished" from "ffmpeg refused the command and quit".
     * Those two look identical from the reading end and have to be told apart from
     * outside; this is what tells them apart.</p>
     *
     * @return a description, or an empty string when the process ended normally with
     *         nothing to report
     */
    public String describeEnd() {
        Process video = videoProcess;
        if (video == null) {
            return "";
        }
        String stderr = FFmpegCli.stderrOf(video);
        if (video.isAlive()) {
            return stderr.isEmpty() ? "" : "ffmpeg is still running and said: " + stderr;
        }
        int exit = video.exitValue();
        if (exit == 0 && stderr.isEmpty()) {
            return "";
        }
        return "ffmpeg exited with " + exit + (stderr.isEmpty() ? "" : ": " + stderr);
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
            } catch (IOException ignored) {
            }
            videoChannel = null;
        }
        videoIn = null;
        // Closed before the interrupt: an audio thread parked in accept() or in a
        // socket read does not answer to interruption, but it does to its socket
        // being closed under it.
        ServerSocket listener = audioListener;
        if (listener != null) {
            closeQuietly(listener);
            audioListener = null;
        }
        Socket socket = audioSocket;
        if (socket != null) {
            closeQuietly(socket);
            audioSocket = null;
        }
        Thread thread = audioThread;
        if (thread != null) {
            thread.interrupt();
            audioThread = null;
        }
    }

    /**
     * {@code address} as a URL authority: bare for IPv4, bracketed for IPv6.
     *
     * <p>Exists because the two halves of the handshake have to name the same address,
     * and they are produced in different places — Java binds the listener, ffmpeg is
     * handed a URL. Writing {@code 127.0.0.1} into that URL looked obviously right and
     * was not: {@link InetAddress#getLoopbackAddress()} answers {@code ::1} on any JVM
     * that prefers IPv6, so the listener sat on {@code [::1]} while ffmpeg knocked on
     * {@code 127.0.0.1} and was refused — a port that was open, and a connection that
     * could never arrive. Deriving the URL from the socket is what keeps the two from
     * disagreeing again.</p>
     */
    static String urlHost(InetAddress address) {
        String literal = address.getHostAddress();
        // getHostAddress can carry a scope suffix for a link-local v6 address
        // ("fe80::1%eth0"), which is meaningless to another process — and never appears
        // for a loopback address, which is the only thing bound here.
        int scope = literal.indexOf('%');
        if (scope >= 0) {
            literal = literal.substring(0, scope);
        }
        return address instanceof Inet6Address ? "[" + literal + "]" : literal;
    }

    private static void closeQuietly(@Nullable java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Tearing a session down must never be the thing that throws.
        }
    }
}
