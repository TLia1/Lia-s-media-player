package com.lia.mediaplayer.media;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Turns the raw diagnostic a failed playback leaves behind — yt-dlp's stderr, ffmpeg's
 * or ffprobe's, or one of our own {@code IOException} messages — into something a player
 * can act on.
 *
 * <p>Both engines fail the same way (they shell out to the same two tools), and both
 * windows have to say so, which is why this sits in {@code media} rather than in
 * {@code video} or {@code audio}.</p>
 *
 * <p>{@link #classify} is deliberately pure and free of Minecraft types: it is the part
 * worth unit-testing, and {@code PlaybackErrorTest} pins the patterns that matter
 * against the real wording of the tools. The raw text is never thrown away — the window
 * still shows it under the readable line, because it is what a bug report needs.</p>
 */
public final class PlaybackError {

    /**
     * What went wrong, in the terms a viewer can do something about.
     *
     * <p>{@code suggestsToolUpdate} marks the causes where the fix is "get a newer
     * yt-dlp/ffmpeg" rather than "try again": those are the ones that put the
     * <em>update the tools</em> button next to <em>retry</em>. YouTube breaking its
     * extractors is by far the most common failure this mod sees, and it is the one a
     * player has no way of guessing at from ffmpeg's output.</p>
     */
    public enum Cause {
        TOOL_MISSING("tool_missing", true),
        EXTRACTOR_OUTDATED("extractor_outdated", true),
        AGE_RESTRICTED("age_restricted", false),
        SIGN_IN_REQUIRED("sign_in", false),
        PRIVATE("private", false),
        GEO_BLOCKED("geo_blocked", false),
        LIVE_NOT_STARTED("live_not_started", false),
        UNAVAILABLE("unavailable", false),
        TIMEOUT("timeout", false),
        NOT_FOUND("not_found", false),
        LINK_EXPIRED("link_expired", false),
        NETWORK("network", false),
        UNSUPPORTED_FORMAT("unsupported_format", false),
        DECODE_FAILED("decode_failed", false),
        UNKNOWN("unknown", false);

        private final String id;
        private final boolean suggestsToolUpdate;

        Cause(String id, boolean suggestsToolUpdate) {
            this.id = id;
            this.suggestsToolUpdate = suggestsToolUpdate;
        }

        /**
         * The stable id this cause is known by — {@code "extractor_outdated"},
         * {@code "geo_blocked"} and the rest. It is what the message and hint keys are
         * built from, and what {@code api.diag.MediaLogEntry} carries so an addon can
         * match on the cause rather than on the wording.
         */
        public String id() {
            return id;
        }

        public String messageKey() {
            return "error.liasmediaplayer.cause." + id;
        }

        public String hintKey() {
            return "error.liasmediaplayer.cause." + id + ".hint";
        }

        /**
         * Whether the failure is one a newer yt-dlp/ffmpeg would plausibly fix.
         */
        public boolean suggestsToolUpdate() {
            return suggestsToolUpdate;
        }
    }

    /**
     * A cause and the lowercase fragments that identify it. Matching is by substring
     * because none of these tools have stable error codes — the wording is the only
     * thing there is, and it is stable enough that yt-dlp's own issue templates quote it.
     */
    private record Rule(Cause cause, String... needles) {
    }

    /**
     * Checked in order, so the narrower reading of an overlapping message wins:
     * "blocked it in your country" is also a "Video unavailable", and "Sign in to
     * confirm your age" is also a "Sign in to confirm".
     */
    private static final List<Rule> RULES = List.of(
            new Rule(Cause.TOOL_MISSING,
                    "is required to play", "failed to run yt-dlp", "cannot run program",
                    "error=2, no such file"),
            new Rule(Cause.EXTRACTOR_OUTDATED,
                    "unable to extract", "nsig extraction failed", "signature extraction failed",
                    "please report this issue", "update to the latest version", "yt-dlp is out of date",
                    "unable to download api page", "failed to extract any player response",
                    "unsupported client", "unable to recognize playlist"),
            new Rule(Cause.AGE_RESTRICTED,
                    "confirm your age", "age-restricted", "age restricted",
                    "inappropriate for some users"),
            new Rule(Cause.SIGN_IN_REQUIRED,
                    "sign in to confirm", "not a bot", "login required", "requires authentication",
                    "members-only", "join this channel", "use --cookies"),
            new Rule(Cause.PRIVATE,
                    "private video", "video is private", "this playlist is private"),
            new Rule(Cause.GEO_BLOCKED,
                    "in your country", "geo restrict", "geo-restricted", "not available in your",
                    "not available from your location", "blocked it in your"),
            new Rule(Cause.LIVE_NOT_STARTED,
                    "live event will begin", "premieres in", "this live event", "not currently live"),
            // yt-dlp's "Requested format is not available" is a format-selector miss,
            // not a missing video, but it contains UNAVAILABLE's "is not available" and
            // would otherwise be read as one. Listed here rather than folded into the
            // UNSUPPORTED_FORMAT rule below, which has to stay under UNAVAILABLE: its
            // other needles are broader than this one.
            new Rule(Cause.UNSUPPORTED_FORMAT,
                    "requested format is not available"),
            new Rule(Cause.UNAVAILABLE,
                    "video unavailable", "video is unavailable", "is not available",
                    "has been removed", "removed by the uploader", "has been terminated",
                    "no longer available", "isn't available", "found no playable media"),
            new Rule(Cause.TIMEOUT,
                    "timed out", "timeout"),
            new Rule(Cause.NOT_FOUND,
                    "404", "not found"),
            new Rule(Cause.LINK_EXPIRED,
                    "403", "forbidden"),
            new Rule(Cause.NETWORK,
                    "connection refused", "connection reset", "network is unreachable",
                    "no route to host", "failed to resolve", "getaddrinfo", "unknownhostexception",
                    "name resolution", "ssl", "tls handshake", "unable to download webpage"),
            new Rule(Cause.UNSUPPORTED_FORMAT,
                    "invalid data found", "protocol not found", "unknown protocol",
                    "decoder not found", "does not contain any stream", "no streams"),
            new Rule(Cause.DECODE_FAILED,
                    "ffprobe could not read", "could not parse ffprobe", "ffmpeg exited",
                    "failed to read frame")
    );

    private PlaybackError() {
    }

    /**
     * Reads {@code raw} — a tool's stderr, an exception message, anything — and answers
     * what it means. Never throws and never returns {@code null}: an unrecognised
     * message is {@link Cause#UNKNOWN}, which still gets a readable line and a retry.
     */
    public static Cause classify(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Cause.UNKNOWN;
        }
        String text = raw.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String needle : rule.needles()) {
                if (text.contains(needle)) {
                    return rule.cause();
                }
            }
        }
        return Cause.UNKNOWN;
    }

    /**
     * The one-line reason, translated.
     */
    public static Component message(@Nullable String raw) {
        return Component.translatable(classify(raw).messageKey());
    }

    /**
     * The line under it: what to do about it.
     */
    public static Component hint(@Nullable String raw) {
        return Component.translatable(classify(raw).hintKey());
    }
}
