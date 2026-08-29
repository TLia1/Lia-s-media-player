/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.policy;

import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaRequest;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The right to say "no", and to say "not like that" — the half of the event surface that
 * runs <em>before</em> something happens rather than reporting it afterwards.
 *
 * <p>{@code PlaybackEvent} tells an addon what the mod did. This is what a party,
 * moderation or parental-control addon needs instead: a chance to veto a link before it
 * becomes clickable, to rewrite a request before it opens a window, and to say something
 * of its own in the label the chat shows.</p>
 *
 * <p>Register with {@link com.lia.mediaplayer.api.LiasMediaPlayerApi#registerInterceptor}.
 * Every registered interceptor is asked, in registration order, and <b>the first veto
 * wins</b> — a rewrite is threaded through the rest, so two interceptors compose rather
 * than the last one deciding.</p>
 *
 * <h2>What this is not</h2>
 *
 * <p>Nothing here hides a chat message or edits what someone said. A vetoed link stays
 * in the message, still says what it says, and is still selectable and copyable; it
 * simply is not turned into a label this mod will play. That is the same line the
 * built-in link filters draw, and it is deliberate: a chat filter is a different thing
 * and not this mod's business.</p>
 *
 * <h2>Threading and failure</h2>
 *
 * <p>{@link #beforePlay} is called on the render thread. {@link #allowChatLink} and
 * {@link #decorateLabel} are called while an incoming chat message is being rewritten,
 * which is the client's network-to-render handoff — do no I/O in either, and keep them
 * cheap: they run once per link per message.</p>
 *
 * <p>An interceptor that throws is logged and <b>treated as having abstained</b>, because
 * a bug in one addon must not stop the others and must not stop the mod. An interceptor
 * that wants to fail closed has to say so by returning a veto, not by throwing.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public interface MediaInterceptor {

    /**
     * Veto or rewrite a play request.
     *
     * <p>Return {@code request} to abstain (the default), a different
     * {@link MediaRequest} to replace it, or {@code null} to cancel. A replacement is
     * passed on to the interceptors after this one and is what the mod finally plays, so
     * an interceptor may change the URL, force the kind, move the window or strip its
     * chrome.</p>
     *
     * <p><b>Copy before you keep it.</b> A {@code MediaRequest} is mutable and the
     * caller owns the one handed in; use {@link MediaRequest#copy()} if you intend to
     * modify or store it.</p>
     *
     * @param origin who asked — see {@link PlayOrigin}
     */
    @Nullable
    default MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
        return request;
    }

    /**
     * Veto a chat link before it is rewritten into a playable label.
     *
     * <p>{@code false} leaves the URL as ordinary text. Asked after the user's own link
     * filters have already allowed it, so an interceptor never has to re-implement
     * those.</p>
     *
     * @param sender the player who sent the message, or {@code null} for a system
     *               message and for a loader that could not say who sent it
     */
    default boolean allowChatLink(String url, @Nullable String sender, MediaKind kind) {
        return true;
    }

    /**
     * Replace the chat label a {@link com.lia.mediaplayer.api.MediaSource} produced.
     *
     * <p>Return {@code label} to abstain, another component to replace it, or
     * {@code null} to fall back to the label the source gave. The style — the colour and
     * the click event that makes it playable — is applied by the mod afterwards, so a
     * decorated label stays clickable whatever it says.</p>
     */
    @Nullable
    default Component decorateLabel(String url, MediaKind kind, Component label) {
        return label;
    }
}
