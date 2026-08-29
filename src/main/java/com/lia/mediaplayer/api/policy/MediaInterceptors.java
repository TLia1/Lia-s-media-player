/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.policy;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaRequest;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

/**
 * Asks every registered {@link MediaInterceptor}, in order, and reduces their answers to
 * one — the counterpart of {@code PlaybackEvents.post} for the questions that are asked
 * before something happens.
 *
 * <p>It lives here, in {@code api}, for the same reason the event dispatcher does: the
 * registry is static (that is the one discovery story that works on both loaders), so
 * the reduction belongs beside it rather than being written out again at each of the
 * three places in the mod that ask. Addons have no reason to call any of this — they
 * implement the interface and register it.</p>
 *
 * <p><b>The first veto wins</b> and a rewrite is threaded through the interceptors that
 * follow, so two of them compose. An interceptor that throws is logged and treated as
 * having abstained.</p>
 *
 * @since API 3.2.0
 */
public final class MediaInterceptors {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MediaInterceptors() {
    }

    /** Whether anything is registered — the cheap check before building a request to ask about. */
    public static boolean any() {
        return !LiasMediaPlayerApi.interceptors().isEmpty();
    }

    /**
     * The request as the interceptors left it, or {@code null} if one of them cancelled.
     *
     * @param request never {@code null}; the caller's own object, handed on unchanged
     *                when nothing rewrites it
     */
    @Nullable
    public static MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
        MediaRequest current = request;
        for (MediaInterceptor interceptor : LiasMediaPlayerApi.interceptors()) {
            try {
                current = interceptor.beforePlay(current, origin);
            } catch (RuntimeException e) {
                LOGGER.error("A media interceptor threw on beforePlay", e);
                continue;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** Whether every interceptor allows {@code url} to become a playable chat label. */
    public static boolean allowChatLink(String url, @Nullable String sender, MediaKind kind) {
        for (MediaInterceptor interceptor : LiasMediaPlayerApi.interceptors()) {
            try {
                if (!interceptor.allowChatLink(url, sender, kind)) {
                    return false;
                }
            } catch (RuntimeException e) {
                LOGGER.error("A media interceptor threw on allowChatLink", e);
            }
        }
        return true;
    }

    /**
     * The label after every interceptor has had a turn at it. Never {@code null}: an
     * interceptor answering {@code null} means "I have nothing to say", so what was
     * passed in survives.
     */
    public static Component decorateLabel(String url, MediaKind kind, Component label) {
        Component current = label;
        List<MediaInterceptor> interceptors = LiasMediaPlayerApi.interceptors();
        for (MediaInterceptor interceptor : interceptors) {
            try {
                Component next = interceptor.decorateLabel(url, kind, current);
                if (next != null) {
                    current = next;
                }
            } catch (RuntimeException e) {
                LOGGER.error("A media interceptor threw on decorateLabel", e);
            }
        }
        return current;
    }
}
