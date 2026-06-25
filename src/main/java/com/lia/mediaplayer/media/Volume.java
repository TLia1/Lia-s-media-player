package com.lia.mediaplayer.media;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The single, shared playback volume used by <em>every</em> media player — the
 * {@link com.lia.mediaplayer.media} layer that both the video engine
 * ({@code video.VideoPlayer}) and the audio engine ({@code audio.AudioPlayer}) build
 * on. Keeping one value here means:
 *
 * <ul>
 *   <li>the level stays in sync across players and carries over when a window swaps
 *       to the next queued track, and</li>
 *   <li>the dB-gain math that pushes the level onto a {@link SourceDataLine} lives in
 *       exactly one place instead of being copied into each engine.</li>
 * </ul>
 *
 * <p>The stored level (0..1) is the user-controlled setting; {@link #effective()}
 * additionally scales it by Minecraft's live master-volume slider so the in-game
 * sound options still apply. All methods are safe to call from any thread.</p>
 */
public final class Volume {

    private static final float MUTE_THRESHOLD = 0.0001f;

    /** User-controlled level in 0..1, shared by audio and video. */
    private static volatile float level = 1.0f;
    /** The level to restore when un-muting. */
    private static volatile float beforeMute = 1.0f;

    private Volume() {
    }

    /** The current user-set level in 0..1. */
    public static float level() {
        return level;
    }

    public static boolean isMuted() {
        return level <= MUTE_THRESHOLD;
    }

    /** Sets the level, clamped to 0..1. */
    public static void set(float value) {
        level = Math.max(0.0f, Math.min(1.0f, value));
        save();
    }

    public static void change(float delta) {
        set(level + delta);
    }

    public static synchronized void toggleMute() {
        if (level > MUTE_THRESHOLD) {
            beforeMute = level;
            set(0.0f);
        } else {
            set(beforeMute > MUTE_THRESHOLD ? beforeMute : 1.0f);
        }
    }

    /** The level actually sent to a line: the user setting scaled by the master slider. */
    public static float effective() {
        return Math.max(0.0f, Math.min(1.0f, level * master()));
    }

    /** The current Minecraft master-volume slider value (0..1), read live. */
    public static float master() {
        try {
            return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        } catch (Exception e) {
            return 1.0f;
        }
    }

    /**
     * Applies {@link #effective()} to {@code line}, preferring dB gain and falling back
     * to a linear control. Cheap to call often: it skips the hardware-control write when
     * the level has not meaningfully changed since {@code lastApplied}, so an audio loop
     * can re-apply it every buffer to follow live master-volume changes. Returns the
     * value the caller should remember as the new {@code lastApplied}.
     */
    public static float apply(SourceDataLine line, float lastApplied) {
        try {
            float v = effective();
            if (Math.abs(v - lastApplied) < 0.001f) {
                return lastApplied; // no audible change since the last write
            }
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float value = v <= 0.0001f
                        ? gain.getMinimum()
                        : (float) (20.0 * Math.log10(v)); // linear volume -> dB
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), value)));
            } else if (line.isControlSupported(FloatControl.Type.VOLUME)) {
                FloatControl vol = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
                float value = vol.getMinimum() + v * (vol.getMaximum() - vol.getMinimum());
                vol.setValue(Math.max(vol.getMinimum(), Math.min(vol.getMaximum(), value)));
            }
            return v;
        } catch (Exception ignored) {
            // Volume control is best-effort.
            return lastApplied;
        }
    }

    private static Path getConfigPath() {
        return Paths.get(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "config", "liasmediaplayer_volume.txt");
    }

    public static void load() {
        try {
            Path path = getConfigPath();
            if (Files.exists(path)) {
                String str = Files.readString(path).strip();
                level = Math.max(0.0f, Math.min(1.0f, Float.parseFloat(str)));
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static void save() {
        try {
            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, String.valueOf(level));
        } catch (Exception e) {
            // ignore
        }
    }
}
