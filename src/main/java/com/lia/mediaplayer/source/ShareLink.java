package com.lia.mediaplayer.source;

import java.net.URI;
import java.util.Locale;

/**
 * Turns a media URL into the link you would send someone so that it opens
 * <em>where you are in it</em> — {@code ...&t=137s} rather than "watch the first four
 * minutes again, it's around there somewhere".
 *
 * <p>Purely textual, and deliberately so: the position comes from the player, and
 * everything here is a function of a string and a number of seconds. That is what makes
 * it testable without a game, and it is why it lives beside {@link Urls} — this is the
 * same kind of knowledge the sources hold (what a link of this host looks like), not
 * anything about playing one.</p>
 *
 * <p>Each site spells the position differently, and a site that has no spelling for it
 * is left alone rather than being given a parameter it would ignore: a link that carries
 * a stray {@code t=} is worse than one that starts from the beginning, because it looks
 * like it should have worked.</p>
 */
public final class ShareLink {

    private ShareLink() {
    }

    /**
     * Whether a position can be written into {@code url} at all.
     *
     * <p>Asked by the UI before it offers the choice: the copy button says "copy the
     * link" for a direct {@code .mp4}, and "copy the link at 2:17" only where that means
     * something.</p>
     */
    public static boolean supportsTimestamp(String url) {
        return YouTubeSource.isYouTube(url)
                || TwitchSource.isTwitch(url)
                || VimeoSource.isVimeo(url)
                || SoundCloudSource.isSoundCloud(url);
    }

    /**
     * {@code url} with the playback position written into it, or {@code url} unchanged
     * when the site has no way to say it (or the position is at the very start, where
     * there is nothing to say).
     *
     * @param seconds the position in whole seconds
     */
    public static String atSeconds(String url, long seconds) {
        if (seconds <= 0 || !supportsTimestamp(url)) {
            return url;
        }
        if (YouTubeSource.isYouTube(url)) {
            // /embed/ is the one YouTube form that ignores t= and wants start=; every
            // other one (watch, youtu.be, shorts, live) takes t=<n>s.
            String path = Urls.pathLower(url);
            boolean embed = path != null && path.startsWith("/embed/");
            return embed
                    ? withQueryParam(url, "start", Long.toString(seconds))
                    : withQueryParam(url, "t", seconds + "s");
        }
        if (TwitchSource.isTwitch(url)) {
            return withQueryParam(url, "t", twitchTime(seconds));
        }
        if (VimeoSource.isVimeo(url)) {
            return withFragment(url, "t=" + seconds + "s");
        }
        // SoundCloud reads the fragment as a clock reading, not as a count of seconds.
        return withFragment(url, "t=" + clockTime(seconds));
    }

    /**
     * Twitch's own spelling of a duration: {@code 1h02m03s}, with the leading units
     * dropped when they are zero.
     */
    static String twitchTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh%02dm%02ds", hours, minutes, secs);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm%02ds", minutes, secs);
        }
        return secs + "s";
    }

    /**
     * {@code m:ss}, or {@code h:mm:ss} once there is an hour of it.
     *
     * <p>Public because the UI shows the same reading before it copies anything: the
     * copy button's tooltip names the moment it would write into the link, and it should
     * be the one the link ends up carrying.</p>
     */
    public static String clockTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /**
     * {@code url} with {@code key=value} in its query string, replacing any parameter of
     * that name it already had — a second {@code t=} would be read by nobody in
     * particular, and the one that matters is the one being written now.
     *
     * <p>Hand-rolled rather than routed through {@link URI}: rebuilding a URI
     * from its parts re-encodes the path and drops what it considers redundant, and this
     * string is going onto someone's clipboard to be pasted back into chat. It should
     * come out as the link they were given, plus one parameter.</p>
     */
    static String withQueryParam(String url, String key, String value) {
        String fragment = "";
        String rest = url;
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash);
            rest = rest.substring(0, hash);
        }
        String base = rest;
        String query = "";
        int question = rest.indexOf('?');
        if (question >= 0) {
            base = rest.substring(0, question);
            query = rest.substring(question + 1);
        }
        StringBuilder kept = new StringBuilder();
        for (String param : query.split("&")) {
            if (param.isEmpty() || param.equals(key) || param.startsWith(key + "=")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(param);
        }
        if (kept.length() > 0) {
            kept.append('&');
        }
        kept.append(key).append('=').append(value);
        return base + "?" + kept + fragment;
    }

    /** {@code url} with its fragment replaced by {@code fragment} (given without the {@code #}). */
    static String withFragment(String url, String fragment) {
        int hash = url.indexOf('#');
        String base = hash >= 0 ? url.substring(0, hash) : url;
        return base + "#" + fragment;
    }
}
