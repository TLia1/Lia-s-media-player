package com.lia.mediaplayer.source;

import net.minecraft.network.chat.Component;

/**
 * A Reddit-hosted video: the {@code v.redd.it/<id>} share link, or the comment page a
 * video post lives on ({@code reddit.com/r/<sub>/comments/<id>/...}).
 *
 * <p>Neither is a media file — {@code v.redd.it} serves a DASH manifest whose audio is a
 * separate track — so both go through yt-dlp, which muxes the two back together.</p>
 */
public final class RedditVideoSource implements com.lia.mediaplayer.api.MediaSource {

    private static final Component LABEL = Component.literal("[reddit]");

    @Override
    public boolean matches(String url) {
        return isRedditVideo(url);
    }

    @Override
    public com.lia.mediaplayer.api.MediaKind kind() {
        return com.lia.mediaplayer.api.MediaKind.VIDEO;
    }

    @Override
    public Component label(String url) {
        return LABEL;
    }

    @Override
    public boolean requiresExtractor() {
        return true;
    }

    /**
     * Whether {@code url} is a Reddit video link.
     *
     * <p>Only comment pages are claimed on {@code reddit.com}, never a subreddit or a
     * user page: those are listings, and most of what is on them is not a video at all.
     * A comment page that turns out to hold a link post rather than a video fails at
     * playback time, which is the same thing that happens to a deleted YouTube link.</p>
     */
    public static boolean isRedditVideo(String url) {
        if (!Urls.isHttp(url)) {
            return false;
        }
        String host = Urls.hostLower(url);
        if (host == null) {
            return false;
        }
        String path = Urls.pathLower(url);
        if (host.equals("v.redd.it")) {
            return path != null && path.length() > 1;
        }
        if (!host.equals("reddit.com") && !host.equals("old.reddit.com")
                && !host.equals("new.reddit.com") && !host.equals("m.reddit.com")) {
            return false;
        }
        return path != null && path.contains("/comments/");
    }
}
