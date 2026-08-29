package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.audio.AudioChannel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * The channel half of the gain chain. Nothing here touches an audio line or the game —
 * the mixer is five numbers and a factory — which is what makes the one property worth
 * protecting testable: a write publishes a whole new array, so a reader on an audio
 * thread can never see a half-updated set.
 */
class AudioMixerTest {

    private static final float EPSILON = 1e-4f;

    private static AudioMixer mixer() {
        return new AudioMixer(new Volume());
    }

    @Test
    void everyChannelStartsAtUnity() {
        AudioMixer mixer = mixer();

        for (AudioChannel channel : AudioChannel.values()) {
            assertEquals(1.0f, mixer.channelGain(channel), EPSILON, channel.name());
        }
    }

    @Test
    void aChannelGainIsClampedAndOnlyAffectsItsOwnChannel() {
        AudioMixer mixer = mixer();

        mixer.setChannelGain(AudioChannel.VOICE, 0.25f);
        mixer.setChannelGain(AudioChannel.UI, 5.0f);
        mixer.setChannelGain(AudioChannel.AMBIENT, -1.0f);

        assertEquals(0.25f, mixer.channelGain(AudioChannel.VOICE), EPSILON);
        assertEquals(1.0f, mixer.channelGain(AudioChannel.UI), EPSILON);
        assertEquals(0.0f, mixer.channelGain(AudioChannel.AMBIENT), EPSILON);
        assertEquals(1.0f, mixer.channelGain(AudioChannel.MEDIA), EPSILON,
                "ducking one channel must not touch the others");
    }

    @Test
    void anUnknownChannelIsAnsweredRatherThanThrownAt() {
        AudioMixer mixer = mixer();

        // An addon built against a newer API can hand over a constant this version has
        // never heard of; the same rule ApiVersion.supports(null) follows applies here.
        assertEquals(1.0f, mixer.channelGain(null), EPSILON);
        mixer.setChannelGain(null, 0.0f);
        assertEquals(1.0f, mixer.channelGain(AudioChannel.MEDIA), EPSILON);
    }

    @Test
    void resettingPutsEveryChannelBack() {
        AudioMixer mixer = mixer();
        mixer.setChannelGain(AudioChannel.MEDIA, 0.1f);
        mixer.setChannelGain(AudioChannel.VOICE, 0.2f);

        mixer.resetChannelGains();

        for (AudioChannel channel : AudioChannel.values()) {
            assertEquals(1.0f, mixer.channelGain(channel), EPSILON, channel.name());
        }
    }

    @Test
    void everyGainItHandsOutIsItsOwn() {
        AudioMixer mixer = mixer();

        assertNotSame(mixer.newGain(), mixer.newGain(),
                "two sounds must not share one fade");
    }

    @Test
    void aGainItMadeIsMultipliedByItsChannel() {
        AudioMixer mixer = mixer();
        AudioGain gain = mixer.newGain();
        gain.setChannel(AudioChannel.AMBIENT);
        gain.setGain(0.5f);
        mixer.setChannelGain(AudioChannel.AMBIENT, 0.5f);

        gain.clientTick();

        // 0.5 (the sound's own) x 0.5 (its channel) x 1 (the master, which is where a
        // fresh Volume sits and stays without a game to read a slider from).
        assertEquals(0.25f, gain.effectiveGain(), EPSILON);
    }

    @Test
    void duckingOneChannelLeavesAnotherAlone() {
        AudioMixer mixer = mixer();
        AudioGain music = mixer.newGain();
        AudioGain voice = mixer.newGain();
        voice.setChannel(AudioChannel.VOICE);

        mixer.setChannelGain(AudioChannel.MEDIA, 0.2f);
        music.clientTick();
        voice.clientTick();

        assertEquals(0.2f, music.effectiveGain(), EPSILON);
        assertEquals(1.0f, voice.effectiveGain(), EPSILON,
                "the whole point of channels: duck the radio without touching the narrator");
    }
}
