/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.audio;

/**
 * The mixer of a mod that is not up yet — every level reads as unity and every setter
 * does nothing.
 *
 * <p>The counterpart of {@code DeadSurface}, and there for the same reason: an addon
 * whose {@code onInitializeClient} runs before this mod's should be able to write
 * {@code MediaAudio.mixer().setChannelGain(...)} without a null check, and should not be
 * able to crash someone else's startup with an {@link NullPointerException} if the order
 * ever changes.</p>
 */
final class DeadMixer implements MediaMixer {

    static final MediaMixer INSTANCE = new DeadMixer();

    private DeadMixer() {
    }

    @Override
    public float masterVolume() {
        return 1.0f;
    }

    @Override
    public void setMasterVolume(float volume) {
    }

    @Override
    public boolean isMuted() {
        return false;
    }

    @Override
    public float channelGain(AudioChannel channel) {
        return 1.0f;
    }

    @Override
    public void setChannelGain(AudioChannel channel, float gain) {
    }

    @Override
    public void resetChannelGains() {
    }
}
