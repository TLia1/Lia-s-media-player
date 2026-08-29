package com.lia.mediaplayer.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link FFmpegSession} the way {@code VideoPlayer} does, against a real local
 * file, to check that one ffmpeg really does deliver both outputs.
 *
 * <p>Opt-in: it needs an ffmpeg on the machine and a sample file, so it runs only when
 * {@code -Dliasmediaplayer.test.sample=/path/to.mp4} is given. Without that it is
 * skipped, which is what keeps {@code testAll} hermetic.</p>
 */
class FFmpegSessionMuxedTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "LMP_TEST_SAMPLE", matches = ".+")
    void oneProcessDeliversBothOutputs() throws Exception {
        String sample = System.getenv("LMP_TEST_SAMPLE");
        int width = 426;
        int height = 240;
        long frameBytes = (long) width * height * 4;

        FFmpegSession session = new FFmpegSession();
        AtomicLong audioBytes = new AtomicLong();
        CountDownLatch audioStarted = new CountDownLatch(1);

        session.start(sample, width, height, 0.0, true, true, 44100, 2, 1, in -> {
            audioStarted.countDown();
            try (InputStream stream = in) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) >= 0) {
                    audioBytes.addAndGet(read);
                }
            } catch (IOException ignored) {
                // the session was torn down
            }
        });

        ReadableByteChannel channel = session.getVideoChannel();
        assertNotNull(channel, "the muxed session must expose a video channel");

        long videoBytes = 0;
        ByteBuffer buffer = ByteBuffer.allocateDirect((int) frameBytes);
        long deadline = System.currentTimeMillis() + 15_000;
        while (videoBytes < frameBytes * 20 && System.currentTimeMillis() < deadline) {
            buffer.clear();
            int read = channel.read(buffer);
            if (read < 0) {
                break;
            }
            videoBytes += read;
        }
        assertTrue(audioStarted.await(10, TimeUnit.SECONDS), "ffmpeg never connected the audio output");
        Thread.sleep(500);
        session.kill();

        assertTrue(videoBytes >= frameBytes * 20,
                "expected at least 20 frames, got " + (videoBytes / frameBytes));
        assertTrue(audioBytes.get() > 0, "no PCM arrived over the loopback connection");
    }
}
