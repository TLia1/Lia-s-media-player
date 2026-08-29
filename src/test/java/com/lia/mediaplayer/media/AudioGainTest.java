package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.audio.AudioChannel;
import com.lia.mediaplayer.api.audio.AudioPlacement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-sound half of the gain chain, on a {@linkplain AudioGain#detached() detached}
 * gain — which needs neither a mixer, a game nor an audio line, and is exactly the shape
 * a player constructed outside a running game holds.
 *
 * <p>What is worth pinning here is the fade, because it is the one piece of state in the
 * class that moves on its own: an addon that asks for a fade and then sets a gain must
 * not find the fade still creeping over the top of it afterwards.</p>
 */
class AudioGainTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void aFreshGainIsUnityAndTwoDimensional() {
        AudioGain gain = AudioGain.detached();

        assertEquals(1.0f, gain.gain(), EPSILON);
        assertEquals(AudioChannel.MEDIA, gain.channel());
        assertTrue(gain.placement().isEmpty());
        assertEquals(0.0f, gain.pan(), EPSILON);
        assertFalse(gain.isFading());
    }

    @Test
    void theGainIsClamped() {
        AudioGain gain = AudioGain.detached();

        gain.setGain(4.0f);
        assertEquals(1.0f, gain.gain(), EPSILON);
        gain.setGain(-1.0f);
        assertEquals(0.0f, gain.gain(), EPSILON);
    }

    @Test
    void aNullChannelIsIgnoredRatherThanStored() {
        AudioGain gain = AudioGain.detached();
        gain.setChannel(AudioChannel.AMBIENT);
        gain.setChannel(null);

        assertEquals(AudioChannel.AMBIENT, gain.channel(),
                "channel() promises never to be null");
    }

    @Test
    void aFadeOfNoLengthLandsImmediately() {
        AudioGain gain = AudioGain.detached();

        gain.fadeTo(0.25f, 0);

        assertEquals(0.25f, gain.gain(), EPSILON);
        assertFalse(gain.isFading());
    }

    @Test
    void aFadeIsRunningUntilItsTimeIsUp() throws InterruptedException {
        AudioGain gain = AudioGain.detached();

        gain.fadeTo(0.0f, 1);
        assertTrue(gain.isFading());

        Thread.sleep(20);
        gain.clientTick();

        assertEquals(0.0f, gain.gain(), EPSILON);
        assertFalse(gain.isFading(), "a fade past its duration is over");
    }

    @Test
    void settingTheGainCancelsAFadeInFlight() {
        AudioGain gain = AudioGain.detached();

        gain.fadeTo(0.0f, 10_000);
        gain.setGain(0.8f);
        gain.clientTick();

        assertFalse(gain.isFading());
        assertEquals(0.8f, gain.gain(), EPSILON, "the fade must not creep over a direct set");
    }

    @Test
    void aScreenPlacementNeitherAttenuatesNorPans() {
        AudioGain gain = AudioGain.detached();

        gain.setPlacement(AudioPlacement.screen());
        gain.clientTick();

        assertEquals(1.0f, gain.effectiveGain(), EPSILON);
        assertEquals(0.0f, gain.pan(), EPSILON);
        assertTrue(gain.placement().isPresent());
    }

    @Test
    void takingASoundBackOutOfTheWorldRecentresItAtOnce() {
        AudioGain gain = AudioGain.detached();
        gain.setPlacement(AudioPlacement.screen());

        gain.setPlacement(null);

        assertTrue(gain.placement().isEmpty());
        assertEquals(0.0f, gain.pan(), EPSILON);
    }

    @Test
    void theSoundsOwnGainIsWhatTheChainMultipliesOut() {
        AudioGain gain = AudioGain.detached();

        gain.setGain(0.5f);
        gain.clientTick();

        // A detached gain has no master and no channel to scale by, so the chain is the
        // handle gain alone — which is what makes this the one factor worth asserting.
        assertEquals(0.5f, gain.effectiveGain(), EPSILON);
    }
}
