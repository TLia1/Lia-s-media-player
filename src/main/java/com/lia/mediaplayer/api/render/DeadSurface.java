/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.render;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.PlaybackState;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A surface that never had anything in it, shared by everything that can refuse a
 * request: the mod is not up yet, the link is not media, the cap is full.
 *
 * <p>It exists so {@link MediaSurfaces} can promise never to answer {@code null} and
 * never to throw. A block-entity renderer has nowhere sensible to catch an exception, and
 * a caller that has to tell four kinds of "no" apart before it can decide not to draw is
 * a caller that will get it wrong.</p>
 */
final class DeadSurface implements MediaSurface {

    static final MediaSurface INSTANCE = new DeadSurface();

    private DeadSurface() {
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
