package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.SurfaceOptions;
import com.lia.mediaplayer.media.PlayerHandle;
import com.lia.mediaplayer.video.VideoPlayer;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A video decoded off-screen, for someone else to draw.
 *
 * <p>Almost none of this is new. {@link VideoPlayer} never cared who blitted its frames:
 * {@code prepareFrame()} hands out a texture, {@code setPictureWanted} is exactly the
 * back-pressure switch a surface needs, and the ffmpeg session already runs headless.
 * What was missing was a <em>lifetime owner other than a window</em> — this is it.</p>
 *
 * <p>The one thing that has to be got right is the back-pressure. A surface nobody marked
 * this tick stops producing pictures and its already-decoded frames are dropped, exactly
 * as {@code MediaWindowOverlay.clientTick} does for a hidden window; without it the frame
 * queue fills, the decode thread jams against it, and ffmpeg blocks behind that — which,
 * since one process carries the sound too, means the audio stops as well.</p>
 */
final class VideoSurfaceEntry extends SurfaceEntry {

    private final VideoPlayer player;
    private final SurfaceOptions options;
    private final PlayerHandle handle;

    /** Whether {@code setPictureWanted} currently says yes, so it is only called on a change. */
    private boolean pictureWanted = true;

    VideoSurfaceEntry(String key, String url, SurfaceOptions options) {
        super(key);
        this.options = options;
        this.player = new VideoPlayer(url);
        if (options.maxWidth() > 0 || options.maxHeight() > 0) {
            player.setResolutionCap(options.maxWidth(), options.maxHeight());
        }
        player.setAudioGain(MediaPlayerContext.get().getMixer().newGain());
        this.handle = new PlayerHandle(player, MediaKind.VIDEO, this::disposeOnce);
        player.start();
        if (!options.autoplay()) {
            // The player is opening a stream, not playing yet, so it cannot be paused
            // here — onTick does it on the first frame there is something to pause.
            handle.requestPauseOnStart();
        }
    }

    @Override
    @Nullable
    ResourceLocation texture() {
        return isDisposed() ? null : player.prepareFrame();
    }

    @Override
    PlaybackState state() {
        return isDisposed() ? PlaybackState.ENDED : player.playbackState();
    }

    @Override
    int sourceWidth() {
        return isDisposed() ? 0 : player.videoWidth();
    }

    @Override
    int sourceHeight() {
        return isDisposed() ? 0 : player.videoHeight();
    }

    @Override
    boolean isDecodingVideo() {
        return !isDisposed();
    }

    @Override
    Optional<MediaHandle> playback() {
        return Optional.of(handle);
    }

    @Override
    void onTick() {
        if (isDisposed()) {
            return;
        }
        handle.applyPendingPause();
        handle.pollPlaybackEvents();
        player.audioGain().clientTick();
        boolean wanted = wasWanted();
        if (wanted != pictureWanted) {
            pictureWanted = wanted;
            player.setPictureWanted(wanted);
        }
        if (!wanted) {
            // Nothing is drawing this one, and drawing is what empties the frame queue.
            player.discardDueFrames();
        }
        if (options.loop() && player.state() == VideoPlayer.State.ENDED) {
            player.seekTo(0);
            player.resume();
        }
    }

    @Override
    void dispose() {
        player.dispose();
        // The addon may still be holding the handle we handed out; from here on it is
        // dead, like a window's handle after its window goes.
        handle.markDead();
    }
}
