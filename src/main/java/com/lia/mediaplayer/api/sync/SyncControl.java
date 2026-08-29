/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.sync;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The receiving half of watch-together: apply what arrived on your channel, hold the
 * local user off a host-controlled session, and converge on a position without the
 * lurch a seek gives.
 *
 * <p>Reach it through {@link MediaSync#control()}. <b>Render thread only.</b></p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.3.0
 */
public interface SyncControl {

    /**
     * Applies a remote action to the local player with {@code action.handleId()}.
     *
     * <p>Nothing is broadcast back — see {@link PlaybackSyncAdapter}. An action naming a
     * handle that is not alive is dropped; a session that has fallen behind should be
     * repaired by playing the media again, not by this.</p>
     *
     * <p>{@link SyncAction.Type#PLAY} resumes at
     * {@link SyncAction#projectedPositionMicros()} rather than at the position on the
     * wire, so the time the packet spent in flight is not lost. It gets there through
     * {@link #driftCorrect} with the default tolerance, so a small delay is absorbed
     * smoothly and a large one is a seek.</p>
     */
    void apply(SyncAction action);

    /**
     * Stops the local user from driving this player: the transport bar, the seek bar, the
     * keyboard shortcuts and the key bindings all decline, and the window says why.
     *
     * <p>Two audiences. A host-controlled watch party, where only the host's transport
     * counts; and a map or adventure mod running a cutscene the player must not pause.</p>
     *
     * <p>The lock is <b>not</b> a lock on the API: {@link #apply}, and an addon holding a
     * {@code MediaHandle}, both still work. It governs the user's hands, which is what
     * "the host controls this" actually means. Closing the window is also still allowed
     * — a player who cannot get out of a video would file that as a crash.</p>
     *
     * @param reason shown in the window; {@code null} for a plain "controlled by
     *               someone else" message. Translated, from your own lang files.
     */
    void setLocked(long handleId, boolean locked, @Nullable Component reason);

    /** Whether {@link #setLocked} is currently holding this player. */
    boolean isLocked(long handleId);

    /**
     * Converges on {@code targetMicros} instead of jumping to it.
     *
     * <p>This is the part worth having in the mod rather than in every addon. A video's
     * audio line is the master clock and the decoder is back-pressured by a bounded frame
     * queue, so a small correction can be made <em>inside</em> that clock — the picture
     * slides into place over a few ticks and nothing stalls. Repeated seeks from outside
     * cannot do that: each one tears down the pipeline, and a party that corrects twice a
     * second never plays anything at all.</p>
     *
     * <p>Three outcomes, and the caller does not have to tell them apart:</p>
     * <ul>
     *   <li>within {@code toleranceMicros} — nothing happens;</li>
     *   <li>a small distance out — the clock is nudged, by a bounded step per call, so
     *       calling this once a tick converges;</li>
     *   <li>far out (more than about two seconds, or the player has no clock to nudge —
     *       an audio track's line <em>is</em> its position and cannot be skewed) — a real
     *       seek.</li>
     * </ul>
     *
     * @param toleranceMicros how close is close enough; {@code <= 0} means the mod's own
     *                        default, which is a frame or two
     * @return whether anything was done — {@code false} means the player was already
     *         within tolerance, or the handle is not alive
     */
    boolean driftCorrect(long handleId, long targetMicros, long toleranceMicros);
}
