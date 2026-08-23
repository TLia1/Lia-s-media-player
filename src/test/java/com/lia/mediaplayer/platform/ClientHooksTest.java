package com.lia.mediaplayer.platform;

import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers the contract the Fabric bridge leans on: {@link ClientHooks#onChatReceived}
 * returns the <em>same</em> component when it changed nothing.
 *
 * <p>This assertion only became possible when the chat handlers stopped taking a NeoForge
 * event object — what used to need a running game and a posted
 * {@code ClientChatReceivedEvent} is now a function from {@link Component} to
 * {@link Component}.</p>
 *
 * <p>It matters more on Fabric than it reads. Fabric has no modify-chat event, so a
 * rewritten player message has to be cancelled and re-injected by hand
 * ({@code FabricChatSink}); identity is how the bridge tells "nothing to do, leave it to
 * vanilla" from "rewritten, take it over". A defensive copy here would push every
 * ordinary chat line down the re-injection path, losing vanilla's own handling of signed
 * messages for all of them.</p>
 *
 * <p>The rewritten cases are deliberately not asserted here: an actual media label pulls
 * in either the hover-event machinery (which drags {@code ItemStack} and the built-in
 * registries behind it) or the preview cache (which posts to the render thread), and
 * neither exists without booting the game. What the labels themselves say is covered by
 * {@code MediaSourcesTest}.</p>
 */
class ClientHooksTest {

    @BeforeEach
    void setUp() {
        // A real context: every field it builds has an empty constructor, and the parts
        // that need a running game are reached lazily from methods chat rewriting never
        // calls. So there is nothing here worth faking.
        LiasMediaPlayerApi.setInstance(new MediaPlayerContext());
    }

    @AfterEach
    void tearDown() {
        LiasMediaPlayerApi.setInstance(null);
    }

    @Test
    void onChatReceived_LeavesAMessageWithoutLinksAlone() {
        Component message = Component.literal("hello there");

        assertSame(message, ClientHooks.onChatReceived(message));
    }

    @Test
    void onChatReceived_LeavesALinkNoMediaSourceClaimsAlone() {
        Component message = Component.literal("see https://example.com/page.html for details");

        assertSame(message, ClientHooks.onChatReceived(message));
    }

    @Test
    void onChatReceived_DoesNothingBeforeTheModIsInitialised() {
        // Chat can arrive while the mod is still starting up; every rule has to answer
        // "not mine" rather than throw out of someone else's callback.
        LiasMediaPlayerApi.setInstance(null);
        Component message = Component.literal("watch https://example.com/video.mp4 now");

        assertSame(message, ClientHooks.onChatReceived(message));
    }
}
