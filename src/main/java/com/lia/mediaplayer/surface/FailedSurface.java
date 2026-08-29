package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.MediaSurface;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The answer to a request that was refused — a link the mod cannot play, or one surface
 * too many.
 *
 * <p>{@code MediaSurfaces} promises never to throw and never to return {@code null}, and
 * this is how the mod side keeps that promise. A refusal is logged once where it happens;
 * what comes back is simply a surface that never becomes ready, so a block-entity renderer
 * drawing a wall of them is already handling it with the branch it needed anyway.</p>
 */
final class FailedSurface implements MediaSurface {

    static final MediaSurface INSTANCE = new FailedSurface();

    private FailedSurface() {
    }

    @Override
    @Nullable
    public ResourceLocation texture() {
        return null;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public PlaybackState state() {
        return PlaybackState.FAILED;
    }

    @Override
    public int sourceWidth() {
        return 0;
    }

    @Override
    public int sourceHeight() {
        return 0;
    }

    @Override
    public float aspectRatio() {
        return 0f;
    }

    @Override
    public void markWanted() {
    }

    @Override
    public Optional<MediaHandle> playback() {
        return Optional.empty();
    }

    @Override
    public void close() {
    }
}
