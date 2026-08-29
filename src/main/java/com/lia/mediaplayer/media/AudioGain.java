package com.lia.mediaplayer.media;

import com.lia.mediaplayer.api.audio.AudioChannel;
import com.lia.mediaplayer.api.audio.AudioControls;
import com.lia.mediaplayer.api.audio.AudioPlacement;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.util.Optional;

/**
 * One sound's own share of the mix — the implementation of {@link AudioControls}, and the
 * thing an engine multiplies into {@link Volume} before it touches a line.
 *
 * <p>One of these belongs to each thing that makes a noise: a player window, an off-screen
 * video surface, a headless track. It outlives the player it is attached to — a window
 * swapping to the next queued track hands the same gain to the fresh player, so a fade or
 * a placement an addon set survives the swap.</p>
 *
 * <h2>Threading — the whole reason this class exists</h2>
 *
 * <p>The chain has four factors and two of them are expensive to work out: reading the
 * camera and measuring a distance is client-thread work, and the audio thread writes a
 * line's gain every 8 KB of PCM. Doing the arithmetic where the sound is written would
 * mean the audio pump reading the world, which is neither safe nor fast.</p>
 *
 * <p>So it is split. {@link #clientTick()} runs once a tick on the client thread, does all
 * the work — the fade step, the camera, the distance, the pan — and publishes two plain
 * {@code volatile float}s. {@link #apply(SourceDataLine, Volume)} runs on the audio thread
 * and does nothing but read those two and write the line, skipping the write entirely when
 * neither has meaningfully changed. There is no lock anywhere in this class, and the audio
 * thread never touches a {@link Vec3}.</p>
 *
 * <p>The cost of that is resolution: gains move in tick steps, so a fade is a 20 Hz ramp
 * and a sound moving past you pans in 50 ms increments. That is the same resolution
 * vanilla updates its own sound positions at, and it is documented on
 * {@link AudioControls}.</p>
 */
public final class AudioGain implements AudioControls {

    /**
     * A gain difference too small to be worth a hardware-control write. The same
     * threshold {@link Volume#apply} uses, for the same reason.
     */
    private static final float GAIN_EPSILON = 0.001f;

    /** The same, for the pan control. Coarser: a pan step this small is inaudible. */
    private static final float PAN_EPSILON = 0.01f;

    /**
     * The mixer whose channel gains and master level this multiplies into, or
     * {@code null} for a {@linkplain #detached() detached} gain.
     */
    @Nullable
    private final AudioMixer mixer;

    // --- Set by the addon, read by clientTick (client thread, but written from the
    // --- render thread by an API call, so volatile) -----------------------------
    private volatile float gain = 1.0f;
    private volatile AudioChannel channel = AudioChannel.MEDIA;
    @Nullable
    private volatile AudioPlacement placement;

    // --- The fade, client thread only ------------------------------------------
    private float fadeFrom;
    private float fadeTarget;
    private long fadeStartNanos;
    private long fadeDurationNanos;
    private boolean fading;

    // --- Published to the audio thread ------------------------------------------
    /** channel x handle x attenuation. Everything but the master level. */
    private volatile float multiplier = 1.0f;
    private volatile float pan;
    /** The whole chain including the master, for {@link #effectiveGain()} to read back. */
    private volatile float effective = 1.0f;
    /**
     * Cleared by the audio thread the first time a line turns out to have no pan control
     * at all. Read by {@link #clientTick()} so it stops computing a pan nothing can
     * apply, and so {@link #pan()} reports the centre it is actually being played at.
     */
    private volatile boolean panSupported = true;

    // --- Audio thread only -------------------------------------------------------
    private float lastAppliedGain = -1f;
    private float lastAppliedPan = Float.NaN;

    AudioGain(@Nullable AudioMixer mixer) {
        this.mixer = mixer;
    }

    /**
     * A gain attached to no mixer: unity, 2D, and never ticked by anyone.
     *
     * <p>What a player starts life with, so that constructing one outside a running game
     * — which is exactly what the engines' unit tests do — needs neither a context nor a
     * mixer. Whatever owns the player replaces it with a real one from
     * {@link AudioMixer#newGain()} before playback begins; a player nobody attaches keeps
     * this and behaves precisely as it did before any of this existed.</p>
     */
    public static AudioGain detached() {
        return new AudioGain(null);
    }

    // ------------------------------------------------------------------
    // Per-tick recompute — client thread
    // ------------------------------------------------------------------

    /**
     * Advances the fade and recomputes what the audio thread should be applying. Called
     * once a client tick by whatever owns this gain.
     */
    public void clientTick() {
        stepFade();
        AudioPlacement where = placement;
        float attenuation = 1.0f;
        float panning = 0.0f;
        if (where != null && where.isPositional()) {
            Vec3 source = where.position();
            Vec3 listener = source == null ? null : AudioListener.position();
            if (source == null) {
                // The entity being followed is gone or unloaded: it is not there to be
                // heard. See AudioPlacement.position().
                attenuation = 0.0f;
            } else if (listener != null) {
                attenuation = PositionalAudio.attenuation(listener.distanceTo(source), where.radius());
                panning = PositionalAudio.pan(source.x - listener.x, source.z - listener.z,
                        AudioListener.yawDegrees());
            }
            // listener == null (no world) leaves both alone: the sound keeps the gain it
            // had rather than stuttering across a loading screen.
        }
        AudioMixer owner = mixer;
        float channelGain = owner == null ? 1.0f : owner.channelGain(channel);
        float computed = clamp01(gain) * clamp01(channelGain) * attenuation;
        multiplier = computed;
        pan = panSupported ? panning : 0f;
        effective = owner == null ? computed : clamp01(owner.volume().effective() * computed);
    }

    private void stepFade() {
        if (!fading) {
            return;
        }
        long elapsed = System.nanoTime() - fadeStartNanos;
        if (elapsed >= fadeDurationNanos) {
            gain = fadeTarget;
            fading = false;
            return;
        }
        float progress = (float) (elapsed / (double) fadeDurationNanos);
        gain = fadeFrom + (fadeTarget - fadeFrom) * progress;
    }

    // ------------------------------------------------------------------
    // Applying — audio thread
    // ------------------------------------------------------------------

    /**
     * Writes this gain (and its pan) onto {@code line}. Called from an engine's pump
     * loop, as often as it likes: both writes are skipped when nothing audible has
     * changed since the last one.
     *
     * <p>Reads two volatile floats and nothing else. In particular it does not read the
     * world, does not allocate, and does not lock.</p>
     */
    public void apply(SourceDataLine line, Volume volume) {
        lastAppliedGain = volume.apply(line, lastAppliedGain, multiplier);
        applyPan(line);
    }

    private void applyPan(SourceDataLine line) {
        float wanted = pan;
        if (!Float.isNaN(lastAppliedPan) && Math.abs(wanted - lastAppliedPan) < PAN_EPSILON) {
            return;
        }
        try {
            // PAN is the mono control and BALANCE the stereo one, and which of the two a
            // line offers is the audio backend's business, not ours — several offer
            // neither, which is why a 2.5D sound is documented as possibly-centred.
            FloatControl control = null;
            if (line.isControlSupported(FloatControl.Type.PAN)) {
                control = (FloatControl) line.getControl(FloatControl.Type.PAN);
            } else if (line.isControlSupported(FloatControl.Type.BALANCE)) {
                control = (FloatControl) line.getControl(FloatControl.Type.BALANCE);
            }
            if (control == null) {
                // Nothing to write to. Say so once, so the lookup is not repeated for
                // every buffer and the next tick stops computing a pan for it.
                panSupported = false;
                lastAppliedPan = 0f;
                return;
            }
            control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), wanted)));
            lastAppliedPan = wanted;
        } catch (Exception ignored) {
            // Panning is best-effort, exactly like the gain write it sits beside.
            lastAppliedPan = wanted;
        }
    }

    /**
     * Forgets what was last written, so the next {@link #apply} writes unconditionally.
     * Called when a line is opened or swapped: a fresh line starts at its own defaults,
     * not at whatever the previous one was left on.
     */
    public void onLineOpened() {
        lastAppliedGain = -1f;
        lastAppliedPan = Float.NaN;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    // ------------------------------------------------------------------
    // AudioControls — render thread
    // ------------------------------------------------------------------

    @Override
    public float gain() {
        return gain;
    }

    @Override
    public void setGain(float value) {
        fading = false;
        gain = clamp01(value);
    }

    @Override
    public void fadeTo(float target, int millis) {
        float clamped = clamp01(target);
        if (millis <= 0) {
            setGain(clamped);
            return;
        }
        // From wherever the running fade had got to, not from where it started, so two
        // fades in quick succession do not jump.
        fadeFrom = gain;
        fadeTarget = clamped;
        fadeStartNanos = System.nanoTime();
        fadeDurationNanos = millis * 1_000_000L;
        fading = true;
    }

    @Override
    public boolean isFading() {
        return fading;
    }

    @Override
    public AudioChannel channel() {
        return channel;
    }

    @Override
    public void setChannel(AudioChannel value) {
        if (value != null) {
            channel = value;
        }
    }

    @Override
    public Optional<AudioPlacement> placement() {
        return Optional.ofNullable(placement);
    }

    @Override
    public void setPlacement(@Nullable AudioPlacement value) {
        placement = value;
        if (value == null || !value.isPositional()) {
            // Back to 2D immediately rather than at the next tick, so a sound taken out
            // of the world cannot be left attenuated by wherever it used to be.
            pan = 0f;
        }
    }

    @Override
    public float effectiveGain() {
        return effective;
    }

    @Override
    public float pan() {
        return pan;
    }
}
