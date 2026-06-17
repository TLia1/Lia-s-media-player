package com.lia.mediaplayer.image;

import com.lia.mediaplayer.LiasMediaPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a Tenor share page into the direct, downloadable GIF behind it, so
 * {@link ImagePreviewCache} can fetch the actual image.
 *
 * <p>Recognizing a Tenor link is the job of
 * {@link com.lia.mediaplayer.source.TenorSource}; this class only handles the
 * resolution step. A Tenor "view" link is an HTML page, not an image file, but it
 * is server-rendered with the media URLs in its markup, for example an
 * {@code <img>} pointing at {@code https://media1.tenor.com/m/<id>/name.gif} and
 * a {@code <meta itemprop="contentUrl" content="...media.../m/<id>/name.gif">}.</p>
 *
 * <p>The {@code media*.tenor.com/m/<id>/...} URLs are hot-link protected, so we
 * extract the media id and rebuild the canonical direct-download endpoint
 * {@code https://c.tenor.com/<id>/tenor.gif}, which serves the raw GIF.</p>
 */
final class TenorResolver {
    private static final int MAX_HTML_BYTES = 512 * 1024;

    // The <img> that displays the GIF: the high-resolution variant (id ...AAAAd).
    private static final Pattern IMG_MEDIA = Pattern.compile(
            "<img[^>]+src\\s*=\\s*[\"']https?://(?:media\\d*|c)\\.tenor\\.com/m/([A-Za-z0-9_-]{8,})/[^\"']+\\.gif[\"']",
            Pattern.CASE_INSENSITIVE);
    // <meta itemprop="contentUrl" content="...media.../m/<id>/...">, either order.
    private static final Pattern CONTENT_URL_PROP_FIRST = Pattern.compile(
            "itemprop\\s*=\\s*[\"']contentUrl[\"'][^>]*content\\s*=\\s*[\"']https?://(?:media\\d*|c)\\.tenor\\.com/m/([A-Za-z0-9_-]{8,})/[^\"']+[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_URL_CONTENT_FIRST = Pattern.compile(
            "content\\s*=\\s*[\"']https?://(?:media\\d*|c)\\.tenor\\.com/m/([A-Za-z0-9_-]{8,})/[^\"']+[\"'][^>]*itemprop\\s*=\\s*[\"']contentUrl[\"']",
            Pattern.CASE_INSENSITIVE);
    // Last resort: any media id appearing in a /m/<id>/ media URL on the page.
    private static final Pattern ANY_MEDIA_ID = Pattern.compile(
            "https?://(?:media\\d*|c)\\.tenor\\.com/m/([A-Za-z0-9_-]{8,})/[^\"'\\s]+",
            Pattern.CASE_INSENSITIVE);
    // Older pages: a plain Open Graph gif URL with no /m/<id>/ segment.
    private static final Pattern OG_IMAGE = Pattern.compile(
            "property\\s*=\\s*[\"']og:image[\"'][^>]*content\\s*=\\s*[\"']([^\"']+\\.gif)[\"']"
                    + "|content\\s*=\\s*[\"']([^\"']+\\.gif)[\"'][^>]*property\\s*=\\s*[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE);

    private TenorResolver() {
    }

    /**
     * Fetches the Tenor page and returns a direct GIF media URL. Runs on the IO
     * pool. Throws if the page can't be fetched or no media URL is found.
     */
    static String resolve(String pageUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(pageUrl).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (compatible; liasmediaplayer/1.0; +https://tenor.com)");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " resolving Tenor link " + pageUrl);
            }
            String html;
            try (InputStream in = connection.getInputStream()) {
                byte[] data = in.readNBytes(MAX_HTML_BYTES);
                html = new String(data, StandardCharsets.UTF_8);
            }

            String media = extractMediaUrl(html);
            if (media == null) {
                throw new IOException("No Tenor media URL found on " + pageUrl);
            }
            LiasMediaPlayer.LOGGER.info("Resolved Tenor link {} -> {}", pageUrl, media);
            return media;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Pulls a downloadable GIF URL out of the page HTML. Prefers the displayed
     * img (highest resolution), then the contentUrl meta, then any media id on
     * the page, rebuilding the direct c.tenor.com endpoint. Falls back to a
     * plain og:image gif for older layouts. Package-private for testing.
     */
    static String extractMediaUrl(String html) {
        if (html == null) {
            return null;
        }

        String id = firstGroup(IMG_MEDIA, html);
        if (id == null) {
            id = firstGroup(CONTENT_URL_PROP_FIRST, html);
        }
        if (id == null) {
            id = firstGroup(CONTENT_URL_CONTENT_FIRST, html);
        }
        if (id == null) {
            id = firstGroup(ANY_MEDIA_ID, html);
        }
        if (id != null) {
            // The canonical direct-download endpoint for the raw GIF.
            return "https://c.tenor.com/" + id + "/tenor.gif";
        }

        // Older page layout: a direct og:image gif with no /m/<id>/ segment.
        Matcher og = OG_IMAGE.matcher(html);
        if (og.find()) {
            String url = og.group(1) != null ? og.group(1) : og.group(2);
            return url == null ? null : unescape(url);
        }
        return null;
    }

    private static String firstGroup(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static String unescape(String url) {
        return url.replace("&amp;", "&").replace("&#x2F;", "/").replace("&#47;", "/");
    }
}
