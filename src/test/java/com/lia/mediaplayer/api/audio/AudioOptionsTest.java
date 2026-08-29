package com.lia.mediaplayer.api.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an addon is allowed to hand {@code MediaAudio.play}. The compact constructor
 * canonicalises rather than validating, and that choice is the thing worth pinning: a
 * block-entity tick computing a gain from a distance it got slightly wrong should get a
 * quiet sound, not an exception thrown out of somebody else's renderer.
 */
class AudioOptionsTest {

    @Test
    void theDefaultsArePlayItOnceAtFullVolume() {
        AudioOptions options = AudioOptions.defaults();

        assertEquals(1.0f, options.gain());
        assertFalse(options.loop());
        assertEquals(0L, options.startMicros());
        assertEquals(AudioChannel.MEDIA, options.channel());
        assertNull(options.placement());
        assertEquals(0, options.fadeInMillis());
        assertEquals(0, options.fadeOutMillis());
        assertTrue(options.pauseWithGame());
    }

    @Test
    void defaultsAreShared() {
        assertSame(AudioOptions.defaults(), AudioOptions.defaults(),
                "a value with no state to vary should not allocate per call");
    }

    @Test
    void anOutOfRangeGainIsClampedRatherThanRejected() {
        assertEquals(1.0f, AudioOptions.defaults().withGain(9f).gain());
        assertEquals(0.0f, AudioOptions.defaults().withGain(-9f).gain());
    }

    @Test
    void negativeTimesAreZeroed() {
        AudioOptions options = AudioOptions.defaults().withStart(-5L).withFade(-1, -2);

        assertEquals(0L, options.startMicros());
        assertEquals(0, options.fadeInMillis());
        assertEquals(0, options.fadeOutMillis());
    }

    @Test
    void aNullChannelFallsBackToMedia() {
        assertEquals(AudioChannel.MEDIA, AudioOptions.defaults().withChannel(null).channel());
    }

    @Test
    void chainingKeepsEverythingElse() {
        AudioOptions options = AudioOptions.defaults()
                .withLoop(true)
                .withChannel(AudioChannel.AMBIENT)
                .withFade(500, 250)
                .withPauseWithGame(false)
                .withStart(3_000_000L)
                .withGain(0.4f);

        assertTrue(options.loop());
        assertEquals(AudioChannel.AMBIENT, options.channel());
        assertEquals(500, options.fadeInMillis());
        assertEquals(250, options.fadeOutMillis());
        assertFalse(options.pauseWithGame());
        assertEquals(3_000_000L, options.startMicros());
        assertEquals(0.4f, options.gain());
    }

    @Test
    void aPlacementCanBeTakenBackOff() {
        AudioOptions placed = AudioOptions.defaults().withPlacement(AudioPlacement.screen());
        assertSame(AudioPlacement.screen(), placed.placement());

        assertNull(placed.withPlacement(null).placement(), "null is the 2D default");
    }
}
