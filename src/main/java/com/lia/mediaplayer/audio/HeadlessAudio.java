package com.lia.mediaplayer.audio;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.audio.AudioOptions;
import com.lia.mediaplayer.config.ConfigStore;
import com.lia.mediaplayer.media.AudioGain;
import com.lia.mediaplayer.media.AudioMixer;
import com.lia.mediaplayer.media.PlayerHandle;
import com.lia.mediaplayer.source.Urls;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every sound an addon asked for that has no window, and the budget they share.
 *
 * <p>The audio counterpart of {@code surface.SurfaceRegistry}, and owned by
 * {@code MediaPlayerContext} for the same reason: a lifecycle (emptied on disconnect) and
 * a budget over something that costs a process and an audio line. Reached from the API
 * through {@code api.audio.MediaAudio}.</p>
 *
 * <h2>Why a cap, and why it is small</h2>
 *
 * <p>A headless sound looks free from the addon's side — one call, no window, nothing on
 * screen — and it is not: it is an ffmpeg process, a {@code SourceDataLine} and two
 * threads, exactly like a track in the audio bar. An addon looping over the speaker
 * blocks it can see would start dozens without meaning to, and unlike a window there is
 * nothing on screen to make that visible. Past the cap a request is refused and logged,
 * which an addon author can find, rather than queued or silently dropped.</p>
 *
 * <h2>Who ends a sound</h2>
 *
 * <p>Nothing on screen can, so this does: a non-looping track that reaches its end and a
 * track that fails both retire themselves, and everything goes on disconnect. Anything
 * else is the addon's {@code MediaHandle.close()}. A sound closed with an
 * {@link AudioOptions#fadeOutMillis()} fades first and is disposed when the fade lands,
 * so an addon does not have to schedule that itself.</p>
 *
 * <p>Client thread only.</p>
 */
public final class HeadlessAudio {

    private final AudioMixer mixer;

    /** Live entries, in the order they were asked for. */
    private final List<Entry> entries = new ArrayList<>();

    public HeadlessAudio(AudioMixer mixer) {
        this.mixer = mixer;
    }

    // ------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------

    /**
     * Starts {@code url} with no window at all, or answers {@code null} if it will not:
     * a link that is not http(s), or a request past the cap.
     */
    @Nullable
    public MediaHandle play(String url, AudioOptions options) {
        if (!Urls.isHttp(url)) {
            LiasMediaPlayer.LOGGER.warn("Refusing headless audio for {}: not an http(s) URL", url);
            return null;
        }
        int cap = ConfigStore.MAX_HEADLESS_AUDIO.getValue();
        if (entries.size() >= cap) {
            LiasMediaPlayer.LOGGER.warn("Refusing headless audio for {}: the cap ({}) is already reached", url, cap);
            return null;
        }
        Entry entry = new Entry(url, options, mixer.newGain());
        entries.add(entry);
        return entry.handle;
    }

    // ------------------------------------------------------------------
    // Per-tick
    // ------------------------------------------------------------------

    /** Advances every live sound: fades, looping, the game pause, and retirement. */
    public void clientTick() {
        if (entries.isEmpty()) {
            return;
        }
        boolean gamePaused = isGamePaused();
        for (Entry entry : new ArrayList<>(entries)) {
            entry.tick(gamePaused);
            if (entry.disposed) {
                entries.remove(entry);
            }
        }
    }

    private static boolean isGamePaused() {
        try {
            return Minecraft.getInstance().isPaused();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Stops every headless sound, whoever is still holding a handle.
     *
     * <p>Called from the disconnect sweep, with the surfaces and the caches. A sound
     * played into a world does not outlive it: what the addon is left holding answers
     * {@code isAlive() == false}, which is the same thing it already handles when a track
     * ends of its own accord.</p>
     */
    public void disposeAll() {
        List<Entry> live = new ArrayList<>(entries);
        entries.clear();
        for (Entry entry : live) {
            entry.dispose();
        }
    }

    /** How many headless sounds exist — for diagnostics and for the cap. */
    public int size() {
        return entries.size();
    }

    // ------------------------------------------------------------------
    // One sound
    // ------------------------------------------------------------------

    private static final class Entry {

        private final AudioPlayer player;
        private final AudioGain gain;
        private final AudioOptions options;
        private final PlayerHandle handle;

        /** Set once the track has actually begun, which is when the fade-in starts. */
        private boolean started;
        /** Whether the entry has asked for the fade that precedes the end of the track. */
        private boolean fadingOut;
        /** Whether {@code close()} was called and the closing fade is running. */
        private boolean closing;
        /** Whether this entry paused the player because the game was paused. */
        private boolean pausedByGame;
        private boolean disposed;

        Entry(String url, AudioOptions options, AudioGain gain) {
            this.options = options;
            this.gain = gain;
            gain.setChannel(options.channel());
            gain.setPlacement(options.placement());
            // Silent until the track is actually running when a fade-in was asked for,
            // so the fade is the first thing heard rather than elapsing during the
            // second ffmpeg spends opening the stream.
            gain.setGain(options.fadeInMillis() > 0 ? 0f : options.gain());
            this.player = new AudioPlayer(url);
            player.setAudioGain(gain);
            this.handle = new PlayerHandle(player, MediaKind.AUDIO, this::requestClose);
            player.start();
        }

        void tick(boolean gamePaused) {
            if (disposed) {
                return;
            }
            gain.clientTick();
            handle.pollPlaybackEvents();
            PlaybackState state = player.playbackState();

            if (!started && state == PlaybackState.PLAYING) {
                started = true;
                if (options.startMicros() > 0) {
                    player.seekTo(options.startMicros());
                }
                if (options.fadeInMillis() > 0) {
                    gain.fadeTo(options.gain(), options.fadeInMillis());
                }
            }
            if (closing) {
                if (!gain.isFading()) {
                    dispose();
                }
                return;
            }
            if (state == PlaybackState.FAILED) {
                // The FAILED event has just been posted by pollPlaybackEvents; there is
                // nothing left to play and nothing to come back to.
                dispose();
                return;
            }
            if (state == PlaybackState.ENDED) {
                if (options.loop()) {
                    fadingOut = false;
                    gain.setGain(options.gain());
                    // A seek out of ENDED is what restarts the engine — see
                    // AudioPlayer.performSeek, which puts the state back to PLAYING.
                    player.seekTo(0);
                } else {
                    dispose();
                }
                return;
            }
            applyGamePause(gamePaused, state);
            applyEndFade(state);
        }

        /**
         * Pauses and resumes with the game, for a sound that asked for it. Only ever
         * undoes its <em>own</em> pause: an addon that paused the handle itself must not
         * find it resumed by the pause menu closing.
         */
        private void applyGamePause(boolean gamePaused, PlaybackState state) {
            if (!options.pauseWithGame()) {
                return;
            }
            if (gamePaused && state == PlaybackState.PLAYING) {
                pausedByGame = true;
                player.pause();
            } else if (!gamePaused && pausedByGame && state == PlaybackState.PAUSED) {
                pausedByGame = false;
                player.resume();
            }
        }

        /** Rides the gain down over the last {@code fadeOutMillis} of a track that ends. */
        private void applyEndFade(PlaybackState state) {
            if (fadingOut || options.loop() || options.fadeOutMillis() <= 0
                    || state != PlaybackState.PLAYING) {
                return;
            }
            long duration = player.durationMicros();
            if (duration <= 0) {
                return; // a live stream has no end to fade before
            }
            long remainingMillis = (duration - player.positionMicros()) / 1000L;
            if (remainingMillis <= options.fadeOutMillis()) {
                fadingOut = true;
                gain.fadeTo(0f, (int) Math.max(1, remainingMillis));
            }
        }

        /**
         * What {@code MediaHandle.close()} runs. With a fade-out asked for, the sound
         * rides down first and {@link #tick} disposes it when the fade lands.
         */
        private void requestClose() {
            if (disposed || closing) {
                return;
            }
            if (options.fadeOutMillis() <= 0) {
                dispose();
                return;
            }
            closing = true;
            gain.fadeTo(0f, options.fadeOutMillis());
        }

        void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            player.dispose();
            handle.markDead();
        }
    }
}
