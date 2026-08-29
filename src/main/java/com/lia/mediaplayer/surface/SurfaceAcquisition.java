package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.MediaSurface;
import com.lia.mediaplayer.api.render.SurfacePixels;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One caller's view of a {@link SurfaceEntry} — the object an addon actually holds.
 *
 * <p>Everything here is a forward to the entry, with two jobs of its own: it is what
 * {@code close()} closes (so one caller letting go does not disturb another's view of the
 * same video), and it is idempotent about it. Closing twice, or closing after the world
 * unloaded, is deliberately not an error: an addon that closes in a destructor it also
 * calls by hand is doing the safe thing, and it should not be punished for it.</p>
 *
 * <p>Render thread only.</p>
 */
final class SurfaceAcquisition implements MediaSurface {

    private final SurfaceRegistry registry;
    private final SurfaceEntry entry;
    private boolean closed;

    SurfaceAcquisition(SurfaceRegistry registry, SurfaceEntry entry) {
        this.registry = registry;
        this.entry = entry;
    }

    @Override
    @Nullable
    public ResourceLocation texture() {
        return closed ? null : entry.texture();
    }

    @Override
    public boolean isReady() {
        return !closed && entry.isReady();
    }

    @Override
    public PlaybackState state() {
        return closed ? PlaybackState.ENDED : entry.state();
    }

    @Override
    public int sourceWidth() {
        return closed ? 0 : entry.sourceWidth();
    }

    @Override
    public int sourceHeight() {
        return closed ? 0 : entry.sourceHeight();
    }

    @Override
    public float aspectRatio() {
        int width = sourceWidth();
        int height = sourceHeight();
        return height > 0 ? (float) width / height : 0f;
    }

    @Override
    public void markWanted() {
        if (!closed) {
            entry.markWanted();
        }
    }

    @Override
    public Optional<MediaHandle> playback() {
        return closed ? Optional.empty() : entry.playback();
    }

    @Override
    public Optional<SurfacePixels> pixels() {
        return closed ? Optional.empty() : entry.pixels();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            registry.release(entry);
        }
    }
}
