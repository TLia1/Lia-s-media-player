package com.lia.mediaplayer.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The component walk that turns a link in someone's chat message into a label.
 *
 * <p>This is the mod's headline feature and the code path every incoming message goes
 * through, on both loaders, whether or not it contains a link. Two things about it are
 * worth pinning. The first is that it must leave a message it does not claim
 * <em>exactly</em> alone — the same instance, not an equal copy: on Fabric that identity
 * is what tells {@code FabricBridge} to let vanilla deliver the message with its
 * signature intact (see {@code ClientHooksTest}). The second is that the text around a
 * link — its wording, its styling, and the punctuation that ends the sentence — has to
 * come out the other side unchanged, which is easy to break in a loop that is juggling
 * two different end offsets for every match.</p>
 */
class ChatLinkRewriterTest {

    /**
     * A rule that claims {@code .png} links, labels them {@code [picture]}, and records
     * what it was asked about — a stand-in for {@code ImageChatHandler}'s.
     */
    private static final class PngRule implements ChatLinkRewriter.LinkRewrite {
        final List<String> offered = new ArrayList<>();
        final List<String> matched = new ArrayList<>();

        @Override
        public boolean matches(String url) {
            offered.add(url);
            return url.endsWith(".png");
        }

        @Override
        public Component label(String url) {
            return Component.translatable("chat.liasmediaplayer.label.picture");
        }

        @Override
        public Style style(Style inherited, String url) {
            return inherited.withUnderlined(true);
        }

        @Override
        public void onMatch(String url) {
            matched.add(url);
        }
    }

    private static final String LABEL = "chat.liasmediaplayer.label.picture";

    // ------------------------------------------------------------------
    // Leaving a message alone
    // ------------------------------------------------------------------

    @Test
    void returnsTheVerySameMessageWhenNothingMatched() {
        Component message = Component.literal("hello, no links here");
        assertSame(message, ChatLinkRewriter.rewrite(message, new PngRule()));
    }

    @Test
    void returnsTheVerySameMessageWhenTheRuleDeclinesTheLink() {
        Component message = Component.literal("look: https://example.com/clip.mp4");
        PngRule rule = new PngRule();

        assertSame(message, ChatLinkRewriter.rewrite(message, rule));
        assertEquals(List.of("https://example.com/clip.mp4"), rule.offered);
        assertTrue(rule.matched.isEmpty());
    }

    @Test
    void leavesADeclinedLinkInTheTextItCameFrom() {
        Component message = Component.literal("a https://example.com/clip.mp4 b");
        assertEquals("a https://example.com/clip.mp4 b",
                ChatLinkRewriter.rewrite(message, new PngRule()).getString());
    }

    @Test
    void ignoresSomethingThatIsNotAnHttpUrl() {
        Component message = Component.literal("try file:///etc/passwd or ftp://host/a.png");
        PngRule rule = new PngRule();

        assertSame(message, ChatLinkRewriter.rewrite(message, rule));
        assertTrue(rule.offered.isEmpty());
    }

    // ------------------------------------------------------------------
    // Replacing a link
    // ------------------------------------------------------------------

    @Test
    void replacesTheLinkAndKeepsTheTextAroundIt() {
        Component message = Component.literal("look at https://example.com/cat.png please");
        PngRule rule = new PngRule();

        Component out = ChatLinkRewriter.rewrite(message, rule);

        assertEquals("look at " + LABEL + " please", out.getString());
        assertEquals(List.of("https://example.com/cat.png"), rule.matched);
    }

    @Test
    void replacesEveryLinkInOneMessage() {
        Component message = Component.literal(
                "https://example.com/a.png and https://example.com/b.png");

        assertEquals(LABEL + " and " + LABEL,
                ChatLinkRewriter.rewrite(message, new PngRule()).getString());
    }

    @Test
    void replacesOnlyTheLinksTheRuleClaims() {
        Component message = Component.literal(
                "https://example.com/a.png then https://example.com/b.mp4");

        assertEquals(LABEL + " then https://example.com/b.mp4",
                ChatLinkRewriter.rewrite(message, new PngRule()).getString());
    }

    @Test
    void findsALinkInsideASiblingComponent() {
        Component message = Component.literal("<Lia> ")
                .append(Component.literal("https://example.com/cat.png"));

        assertEquals("<Lia> " + LABEL,
                ChatLinkRewriter.rewrite(message, new PngRule()).getString());
    }

    // ------------------------------------------------------------------
    // Punctuation
    //
    // The URL pattern is greedy to the next space, so a link at the end of a sentence
    // arrives with the full stop attached. The rule must be asked about the link
    // without it, and the punctuation must survive into the output.
    // ------------------------------------------------------------------

    @Test
    void stripsTrailingPunctuationBeforeAskingTheRule() {
        PngRule rule = new PngRule();
        ChatLinkRewriter.rewrite(Component.literal("see https://example.com/cat.png."), rule);
        assertEquals(List.of("https://example.com/cat.png"), rule.offered);
    }

    @Test
    void keepsTheTrailingPunctuationInTheMessage() {
        assertEquals("see " + LABEL + ".", ChatLinkRewriter
                .rewrite(Component.literal("see https://example.com/cat.png."), new PngRule())
                .getString());
    }

    @Test
    void handlesALinkInBrackets() {
        assertEquals("(" + LABEL + ") ok", ChatLinkRewriter
                .rewrite(Component.literal("(https://example.com/cat.png) ok"), new PngRule())
                .getString());
    }

    @Test
    void stripsARunOfPunctuation() {
        PngRule rule = new PngRule();
        Component out = ChatLinkRewriter.rewrite(
                Component.literal("wow https://example.com/cat.png!?"), rule);

        assertEquals(List.of("https://example.com/cat.png"), rule.offered);
        assertEquals("wow " + LABEL + "!?", out.getString());
    }

    @Test
    void doesNotStripPunctuationThatIsPartOfThePath() {
        // Only *trailing* punctuation goes; a dot inside the path is the file extension.
        PngRule rule = new PngRule();
        ChatLinkRewriter.rewrite(
                Component.literal("https://example.com/a.b.c/cat.png"), rule);
        assertEquals(List.of("https://example.com/a.b.c/cat.png"), rule.offered);
    }

    // ------------------------------------------------------------------
    // Styling
    // ------------------------------------------------------------------

    @Test
    void keepsTheStylingOfTheTextAroundTheLink() {
        Component message = Component.literal("bold https://example.com/cat.png bold")
                .setStyle(Style.EMPTY.withBold(true));

        Component out = ChatLinkRewriter.rewrite(message, new PngRule());

        assertEquals(3, out.getSiblings().size());
        assertTrue(out.getSiblings().get(0).getStyle().isBold(), "text before the link");
        assertTrue(out.getSiblings().get(2).getStyle().isBold(), "text after the link");
    }

    @Test
    void derivesTheLabelStyleFromTheStyleItReplaced() {
        Component message = Component.literal("https://example.com/cat.png")
                .setStyle(Style.EMPTY.withBold(true));

        Component label = ChatLinkRewriter.rewrite(message, new PngRule()).getSiblings().get(0);

        assertTrue(label.getStyle().isBold(), "inherited from the surrounding text");
        assertTrue(label.getStyle().isUnderlined(), "added by the rule");
    }

    @Test
    void doesNotStyleTheTextAroundTheLinkLikeTheLabel() {
        Component message = Component.literal("before https://example.com/cat.png");

        Component out = ChatLinkRewriter.rewrite(message, new PngRule());

        assertFalse(out.getSiblings().get(0).getStyle().isUnderlined());
    }
}
