package com.lia.mediaplayer.surface;

import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.PlaybackState;
import com.lia.mediaplayer.api.render.SurfacePixels;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One shared decode behind any number of {@code MediaSurface}s.
 *
 * <p>The split is the whole design of this package: an <em>entry</em> is the decoding —
 * one ffmpeg process, one set of textures — and a {@link SurfaceAcquisition} is one
 * caller's view of it. Two addons asking for the same video get two acquisitions over one
 * entry, and the entry goes when the second of them lets go. Without that, a cinema hall
 * and a lobby TV showing the same film would be two processes, and the second one would
 * be nobody's fault and nobody's to fix.</p>
 *
 * <p>Render thread only.</p>
 */
abstract class SurfaceEntry {

    /** What {@link SurfaceRegistry} keys this by: the media, plus anything that changes the decode. */
    final String key;

    private int refCount;
    private boolean disposed;

    /**
     * Whether anything asked to see a picture since the last tick.
     *
     * <p>This is the back-pressure switch, and it is a per-tick flag rather than a
     * count on purpose: a caller drawing a surface in twelve places should not have to
     * balance twelve "no longer wanted" calls, and the only question the decoder has is
     * whether <em>anyone</em> is looking.</p>
     */
    private boolean wantedThisTick;

    /** Whether anything wanted it during the tick that just ended. */
    private boolean wantedLastTick = true;

    SurfaceEntry(String key) {
        this.key = key;
    }

    // ------------------------------------------------------------------
    // Reference counting
    // ------------------------------------------------------------------

    final void acquire() {
        refCount++;
    }

    /** @return whether that was the last acquisition */
    final boolean release() {
        refCount--;
        return refCount <= 0;
    }

    final boolean isDisposed() {
        return disposed;
    }

    final void disposeOnce() {
        if (!disposed) {
            disposed = true;
            dispose();
        }
    }

    // ------------------------------------------------------------------
    // Per-tick
    // ------------------------------------------------------------------

    final void markWanted() {
        wantedThisTick = true;
    }

    /** Whether anything asked to see this during the tick that just ended. */
    final boolean wasWanted() {
        return wantedLastTick;
    }

    /**
     * Rolls the "wanted" flag over and lets the entry act on it. Called once a tick by
     * the registry, for every live entry.
     */
    final void tick() {
        wantedLastTick = wantedThisTick;
        wantedThisTick = false;
        onTick();
    }

    // ------------------------------------------------------------------
    // Per-kind
    // ------------------------------------------------------------------

    @Nullable
    abstract ResourceLocation texture();

    abstract PlaybackState state();

    abstract int sourceWidth();

    abstract int sourceHeight();

    final boolean isReady() {
        return !disposed && texture() != null;
    }

    /** Whether this entry holds a running video decode, which is what the tighter cap counts. */
    boolean isDecodingVideo() {
        return false;
    }

    Optional<MediaHandle> playback() {
        return Optional.empty();
    }

    /**
     * The decoded pixels, for the one kind of entry that can keep them — see
     * {@code api.render.MediaSurface.pixels()}. Empty everywhere else, which is every
     * entry that is not an image asked for with {@code keepPixels}.
     */
    Optional<SurfacePixels> pixels() {
        return Optional.empty();
    }

    void onTick() {
    }

    void dispose() {
    }
}
