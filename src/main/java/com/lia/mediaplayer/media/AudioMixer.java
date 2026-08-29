package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.audio.AudioChannel;
import com.lia.mediaplayer.api.audio.MediaMixer;

import java.util.Arrays;

/**
 * The channel half of the gain chain, and the factory for the per-sound half.
 *
 * <p>{@link Volume} is still the mod's one master level and nothing here replaces it —
 * this holds the five channel gains that sit between it and a single sound, and hands out
 * the {@link AudioGain} each player multiplies in. Owned by {@code MediaPlayerContext}
 * beside {@link Volume} itself.</p>
 *
 * <pre>{@code
 * effective = volume.effective() x channelGain(channel) x gain.gain() x attenuation
 * }</pre>
 *
 * <h2>No registry</h2>
 *
 * <p>A mixer that kept a list of every live {@link AudioGain} so it could recompute them
 * would need every one of them unregistered on exactly the right path — a window closing,
 * a surface's last reference going, a headless track ending, a disconnect — and the cost
 * of missing one is a leak nobody notices. Each gain is instead ticked by whatever owns
 * it (the window, the surface entry, the headless entry), which are the objects that
 * already have a working disposal path, so a gain whose owner is gone is simply garbage.
 * This class holds no references to anything it makes.</p>
 *
 * <h2>Persistence</h2>
 *
 * <p>Channel gains are deliberately <b>not</b> saved. They are a runtime mix an addon
 * sets and puts back; persisting them would mean an addon that crashed mid-fade could
 * leave someone's game quiet across restarts with nothing in any menu to explain it. The
 * master level, which is the user's own setting, is saved by {@link Volume} as it always
 * was.</p>
 *
 * <p>Thread-safe: the gains are published as a whole array on every write, so a reader on
 * an audio thread sees one consistent set of five values and never a torn one.</p>
 */
public final class AudioMixer implements MediaMixer {

    private final Volume volume;

    /**
     * One gain per {@link AudioChannel}, indexed by ordinal. Replaced wholesale rather
     * than written in place: array element writes are not volatile, and copying five
     * floats costs nothing next to being right about it.
     */
    private volatile float[] channelGains = unity();

    public AudioMixer(Volume volume) {
        this.volume = volume;
    }

    private static float[] unity() {
        float[] gains = new float[AudioChannel.values().length];
        Arrays.fill(gains, 1.0f);
        return gains;
    }

    /** The one master level, shared with every player in the mod. */
    public Volume volume() {
        return volume;
    }

    /**
     * A fresh per-sound gain, at unity, 2D, on {@link AudioChannel#MEDIA}. The caller
     * owns it and ticks it — see the class note.
     */
    public AudioGain newGain() {
        return new AudioGain(this);
    }

    // ------------------------------------------------------------------
    // MediaMixer
    // ------------------------------------------------------------------

    @Override
    public float masterVolume() {
        return volume.level();
    }

    @Override
    public void setMasterVolume(float value) {
        volume.set(value);
    }

    @Override
    public boolean isMuted() {
        return volume.isMuted();
    }

    @Override
    public float channelGain(AudioChannel channel) {
        return channel == null ? 1.0f : channelGains[channel.ordinal()];
    }

    @Override
    public void setChannelGain(AudioChannel channel, float gain) {
        if (channel == null) {
            return;
        }
        float[] updated = channelGains.clone();
        updated[channel.ordinal()] = Math.max(0.0f, Math.min(1.0f, gain));
        channelGains = updated;
    }

    @Override
    public void resetChannelGains() {
        channelGains = unity();
    }
}
