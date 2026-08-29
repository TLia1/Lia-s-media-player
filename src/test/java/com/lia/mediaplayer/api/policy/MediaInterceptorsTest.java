package com.lia.mediaplayer.api.policy;

import com.lia.mediaplayer.api.LiasMediaPlayerApi;
import com.lia.mediaplayer.api.MediaKind;
import com.lia.mediaplayer.api.MediaRequest;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How several interceptors are reduced to one answer.
 *
 * <p>Three rules are pinned here because all three are promises the API makes in writing:
 * the first veto wins, a rewrite is threaded through the interceptors that follow (so two
 * of them compose rather than the last one deciding), and one that throws is treated as
 * having abstained — a bug in one addon must not take the mod, or the other addons, with
 * it.</p>
 */
class MediaInterceptorsTest {

    private static final String URL = "https://example.com/clip.mp4";

    private final List<MediaInterceptor> registered = new ArrayList<>();

    private void register(MediaInterceptor interceptor) {
        registered.add(interceptor);
        LiasMediaPlayerApi.registerInterceptor(interceptor);
    }

    @AfterEach
    void unregisterEverything() {
        registered.forEach(LiasMediaPlayerApi::unregisterInterceptor);
        registered.clear();
    }

    // ------------------------------------------------------------------
    // beforePlay
    // ------------------------------------------------------------------

    @Test
    void nothingRegisteredMeansTheRequestPassesThroughUntouched() {
        assertFalse(MediaInterceptors.any());
        MediaRequest request = MediaRequest.of(URL);
        assertSame(request, MediaInterceptors.beforePlay(request, PlayOrigin.API));
    }

    @Test
    void anAbstainingInterceptorHandsTheSameObjectBack() {
        register(new MediaInterceptor() {
        });
        MediaRequest request = MediaRequest.of(URL);
        assertTrue(MediaInterceptors.any());
        assertSame(request, MediaInterceptors.beforePlay(request, PlayOrigin.API));
    }

    @Test
    void theFirstVetoWinsAndTheRestAreNotAsked() {
        List<String> asked = new ArrayList<>();
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                asked.add("first");
                return null;
            }
        });
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                asked.add("second");
                return request;
            }
        });
        assertNull(MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.CHAT_CLICK));
        assertEquals(List.of("first"), asked);
    }

    @Test
    void aRewriteIsThreadedThroughTheOnesAfterIt() {
        String other = "https://example.com/other.mp4";
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                return MediaRequest.of(other);
            }
        });
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                // Sees the first one's answer, not the caller's, which is what lets two
                // interceptors compose.
                assertEquals(other, request.url());
                return request.as(MediaKind.AUDIO);
            }
        });
        MediaRequest result = MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.API);
        assertEquals(other, result.url());
        assertEquals(MediaKind.AUDIO, result.kind());
    }

    @Test
    void theOriginIsPassedThroughUnchanged() {
        List<PlayOrigin> seen = new ArrayList<>();
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                seen.add(origin);
                return request;
            }
        });
        MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.KEYBIND);
        MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.RESTORE);
        assertEquals(List.of(PlayOrigin.KEYBIND, PlayOrigin.RESTORE), seen);
    }

    @Test
    void oneThatThrowsAbstainsAndTheRestStillDecide() {
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                throw new IllegalStateException("broken addon");
            }
        });
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                return null;
            }
        });
        assertNull(MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.API),
                "the second interceptor must still have been asked");
    }

    @Test
    void aThrowingInterceptorDoesNotDiscardAnEarlierRewrite() {
        String other = "https://example.com/other.mp4";
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                return MediaRequest.of(other);
            }
        });
        register(new MediaInterceptor() {
            @Override
            public MediaRequest beforePlay(MediaRequest request, PlayOrigin origin) {
                throw new IllegalStateException("broken addon");
            }
        });
        assertEquals(other,
                MediaInterceptors.beforePlay(MediaRequest.of(URL), PlayOrigin.API).url());
    }

    // ------------------------------------------------------------------
    // allowChatLink
    // ------------------------------------------------------------------

    @Test
    void oneVetoIsEnoughToKeepALinkAsPlainText() {
        register(new MediaInterceptor() {
            @Override
            public boolean allowChatLink(String url, @Nullable String sender, MediaKind kind) {
                return !"griefer".equals(sender);
            }
        });
        assertFalse(MediaInterceptors.allowChatLink(URL, "griefer", MediaKind.VIDEO));
        assertTrue(MediaInterceptors.allowChatLink(URL, "someone", MediaKind.VIDEO));
        assertTrue(MediaInterceptors.allowChatLink(URL, null, MediaKind.VIDEO),
                "a system message has no sender to have blocked");
    }

    @Test
    void aThrowingChatVetoIsIgnoredRatherThanTakenAsANo() {
        register(new MediaInterceptor() {
            @Override
            public boolean allowChatLink(String url, @Nullable String sender, MediaKind kind) {
                throw new IllegalStateException("broken addon");
            }
        });
        assertTrue(MediaInterceptors.allowChatLink(URL, "someone", MediaKind.IMAGE));
    }

    // ------------------------------------------------------------------
    // decorateLabel
    // ------------------------------------------------------------------

    @Test
    void labelsAreDecoratedInOrderAndNullMeansNothingToSay() {
        register(new MediaInterceptor() {
            @Override
            public Component decorateLabel(String url, MediaKind kind, Component label) {
                return Component.literal(label.getString() + "!");
            }
        });
        register(new MediaInterceptor() {
            @Override
            public Component decorateLabel(String url, MediaKind kind, Component label) {
                return null; // abstains
            }
        });
        register(new MediaInterceptor() {
            @Override
            public Component decorateLabel(String url, MediaKind kind, Component label) {
                return Component.literal("[" + label.getString() + "]");
            }
        });
        Component result = MediaInterceptors.decorateLabel(
                URL, MediaKind.VIDEO, Component.literal("video"));
        assertEquals("[video!]", result.getString());
    }

    @Test
    void aThrowingDecoratorLeavesTheLabelAsItWas() {
        Component label = Component.literal("video");
        register(new MediaInterceptor() {
            @Override
            public Component decorateLabel(String url, MediaKind kind, Component in) {
                throw new IllegalStateException("broken addon");
            }
        });
        assertSame(label, MediaInterceptors.decorateLabel(URL, MediaKind.VIDEO, label));
    }
}
