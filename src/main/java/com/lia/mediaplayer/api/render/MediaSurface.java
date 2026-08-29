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
 * A decoded media surface the caller draws itself — the mod as a decoder rather than as
 * a window.
 *
 * <p>Everything else in this API puts media in the mod's own floating window. This puts
 * it wherever you want it: a cinema screen in the world, a TV on a block, an animated
 * background in your own {@code Screen}, a trailer in a menu, album art beside a
 * jukebox.</p>
 *
 * <h2>Lifetime</h2>
 *
 * <p><b>Reference-counted.</b> Two callers asking for the same media share one decode,
 * and each gets its own {@code MediaSurface} to {@link #close()}; the decode is disposed
 * when the last one lets go. Closing twice is harmless, and so is never closing anything
 * at all when the world unloads — every surface is dropped then regardless of who is
 * still holding one, because a texture outliving the world it was decoded for is a leak
 * for the rest of the session. After that {@link #isReady()} is {@code false} and
 * {@link #texture()} is {@code null}: <b>do not cache the id you got from
 * {@link #texture()}</b>, ask again each frame.</p>
 *
 * <h2>Back-pressure</h2>
 *
 * <p>{@link #markWanted()} once per frame while the surface is on screen, and not
 * otherwise. A video surface nobody marks stops decoding pictures — its sound, if any,
 * keeps playing — which is the whole of what makes a world full of screens affordable.
 * Call it for the ones actually in view, not for every block entity you own.</p>
 *
 * <h2>Threading</h2>
 *
 * <p><b>Render thread only</b>, all of it. {@link #texture()} touches the texture manager
 * and {@link #markWanted()} drives a decode thread.</p>
 *
 * <h2>A note on the signature</h2>
 *
 * <p>{@link #texture()} returns a Minecraft type, and Minecraft renamed that type
 * ({@code ResourceLocation} became {@code ResourceLocation} in 1.21.11). An addon compiles
 * against one Minecraft version, so this is not a problem in practice — but it does mean
 * this method's signature differs between the mod's build targets, and it is why the
 * texture-shaped part of this API is deliberately concentrated in this one package.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.0.0
 */
public interface MediaSurface extends AutoCloseable {

    /**
     * The texture to draw, or {@code null} while it is loading, once it has failed, and
     * after the world unloaded. Ask every frame; never keep the value.
     */
    @Nullable
    ResourceLocation texture();

    /** Whether {@link #texture()} has something to give right now. */
    boolean isReady();

    PlaybackState state();

    /** The decoded width, or {@code 0} before that is known. */
    int sourceWidth();

    int sourceHeight();

    /** Width over height, or {@code 0} before the size is known. */
    float aspectRatio();

    /**
     * Call once per frame while this surface is on screen — see the class note on
     * back-pressure. Harmless on an image surface, which decodes once.
     */
    void markWanted();

    /**
     * The transport, for a surface with a player behind it (a video). Empty for a still
     * image and for a thumbnail, which have nothing to play.
     */
    Optional<MediaHandle> playback();

    /**
     * The decoded pixels, copied out, for an addon that wants to sample the picture
     * rather than draw it.
     *
     * <p>Empty unless the surface was asked for with
     * {@link MediaSurfaces#image(String, boolean) keepPixels}, and empty for a video or a
     * thumbnail: keeping every frame of a video readable would double what a decode
     * costs, for a question nobody has asked. Also empty while it is still loading, and
     * once the world has unloaded.</p>
     *
     * <p>A fresh array each call — see {@link SurfacePixels}. For an animated picture it
     * is the first frame, which is the one an addon sampling a colour means.</p>
     *
     * @since API 3.4.0
     */
    default Optional<SurfacePixels> pixels() {
        return Optional.empty();
    }

    /**
     * Releases <em>this</em> acquisition. The shared decode goes when the last one does.
     * Idempotent.
     */
    @Override
    void close();
}
