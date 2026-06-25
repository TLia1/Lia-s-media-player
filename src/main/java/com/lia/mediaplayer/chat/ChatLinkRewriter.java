package com.lia.mediaplayer.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared machinery for rewriting media links in an incoming chat message into a
 * compact, clickable label, preserving the surrounding text and its styling.
 *
 * <p>The image and video chat handlers used to carry near-identical copies of this
 * component-walking loop; the only differences were which URLs they matched, which
 * label and colour they applied, and what they did on a match. Those differences
 * are captured by a {@link LinkRewrite} strategy, so both handlers share one tested
 * implementation and a new media feature only has to describe its own rule.</p>
 */
public final class ChatLinkRewriter {

    /** Any {@code http(s)} URL; the {@link LinkRewrite} decides which ones it claims. */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private ChatLinkRewriter() {
    }

    /**
     * Describes how one feature turns a matching URL into a chat label. Stateless and
     * reusable: a handler can keep a single instance and pass it on every message.
     */
    public interface LinkRewrite {

        /** Whether this rule claims {@code url}. */
        boolean matches(String url);

        /** The clickable label to show in place of {@code url}. */
        Component label(String url);

        /**
         * The style for the label, derived from the {@code inherited} style of the
         * surrounding text. Implementations typically add a colour and an
         * {@code OPEN_URL} click event pointing back at {@code url}.
         */
        Style style(Style inherited, String url);

        /** Optional side effect when a URL matches (e.g. warming a preview cache). */
        default void onMatch(String url) {
        }
    }

    /**
     * Returns a copy of {@code message} with every URL claimed by {@code rule}
     * replaced by its label, or the original message unchanged when nothing matched.
     * The component tree is walked with its inherited styles so non-link text keeps
     * its original formatting.
     */
    public static Component rewrite(Component message, LinkRewrite rule) {
        MutableComponent rebuilt = Component.empty();
        boolean[] changed = {false};

        FormattedText.StyledContentConsumer<Object> consumer = (style, text) -> {
            int last = 0;
            Matcher matcher = URL_PATTERN.matcher(text);
            while (matcher.find()) {
                String url = matcher.group();
                if (!rule.matches(url)) {
                    continue;
                }
                if (matcher.start() > last) {
                    rebuilt.append(Component.literal(text.substring(last, matcher.start())).setStyle(style));
                }
                rebuilt.append(rule.label(url).copy().setStyle(rule.style(style, url)));
                rule.onMatch(url);
                last = matcher.end();
                changed[0] = true;
            }
            if (last < text.length()) {
                rebuilt.append(Component.literal(text.substring(last)).setStyle(style));
            }
            return Optional.empty();
        };
        message.visit(consumer, message.getStyle());

        return changed[0] ? rebuilt : message;
    }
}
