/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.sync;

import com.lia.mediaplayer.api.IMediaPlayerAPI;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The front door for watch-together: install the adapter that broadcasts, reach the
 * control that applies.
 *
 * <pre>{@code
 * MediaSync.setAdapter(action -> myChannel.send(new PlaybackPacket(action)));
 *
 * // ... and when a packet arrives:
 * MediaSync.control().apply(new SyncAction(myLocalHandleId, packet.url(),
 *         packet.type(), packet.positionMicros(), packet.wallClockMillis()));
 * }</pre>
 *
 * <p>{@link #setAdapter} may be called before the mod has finished initializing — the
 * adapter is held here and picked up when the mod starts broadcasting.
 * {@link #control()} always answers; before the mod is up (and after the world unloads)
 * it answers with one that does nothing, so a packet arriving at an awkward moment is not
 * an exception in someone's network handler.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.3.0
 */
public final class MediaSync {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Written from an addon's setup, read from the client tick. */
    private static volatile PlaybackSyncAdapter adapter;

    private MediaSync() {
    }

    /**
     * Installs the adapter the mod broadcasts local actions to, replacing any previous
     * one, or clears it with {@code null}.
     *
     * <p>One adapter, not a list — see {@link PlaybackSyncAdapter}. Replacing an
     * installed one is logged.</p>
     */
    public static void setAdapter(@Nullable PlaybackSyncAdapter value) {
        if (adapter != null && value != null && adapter != value) {
            LOGGER.warn("A playback sync adapter was already installed ({}); replacing it with {}",
                    adapter.getClass().getName(), value.getClass().getName());
        }
        adapter = value;
    }

    /** The installed adapter, or {@code null}. */
    @Nullable
    public static PlaybackSyncAdapter adapter() {
        return adapter;
    }

    /**
     * The control side. Never {@code null}: before the mod is initialized this is an
     * inert one whose {@code apply} does nothing and whose {@code driftCorrect} answers
     * {@code false}.
     */
    public static SyncControl control() {
        IMediaPlayerAPI api = LiasMediaPlayerApi.getInstanceOrNull();
        return api == null ? DEAD : api.getSyncControl();
    }

    /**
     * The mod's own broadcast point. It is here rather than in the mod so that the
     * "do not echo what we applied" rule lives beside the adapter it protects — see
     * {@link PlaybackSyncAdapter}. Not for addons.
     */
    public static void broadcast(SyncAction action) {
        PlaybackSyncAdapter target = adapter;
        if (target == null || action == null || applying) {
            return;
        }
        try {
            target.onLocalAction(action);
        } catch (RuntimeException e) {
            LOGGER.error("A playback sync adapter threw on {}", action.type(), e);
        }
    }

    /**
     * Whether the mod is in the middle of applying a remote action, which is when local
     * transitions must not be broadcast back.
     *
     * <p>A plain field and not a {@code ThreadLocal}: everything that applies an action
     * and everything that derives one runs on the client thread.</p>
     */
    private static boolean applying;

    /** Runs {@code body} with broadcasting suppressed. Not for addons. */
    public static void whileApplying(Runnable body) {
        boolean previous = applying;
        applying = true;
        try {
            body.run();
        } finally {
            applying = previous;
        }
    }

    /** What {@link #control()} answers before the mod is up. */
    private static final SyncControl DEAD = new SyncControl() {
        @Override
        public void apply(SyncAction action) {
        }

        @Override
        public void setLocked(long handleId, boolean locked, @Nullable Component reason) {
        }

        @Override
        public boolean isLocked(long handleId) {
            return false;
        }

        @Override
        public boolean driftCorrect(long handleId, long targetMicros, long toleranceMicros) {
            return false;
        }
    };
}
