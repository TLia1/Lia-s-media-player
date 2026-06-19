package com.lia.mediaplayer.source;

/**
 * Re-export of {@link com.lia.mediaplayer.api.MediaKind} for backward compatibility.
 *
 * <p>The enum values {@code IMAGE}, {@code VIDEO} and {@code AUDIO} are the same
 * constants from the public API. Existing code that references
 * {@code MediaKind.IMAGE} etc. continues to compile because these constants are of
 * type {@code com.lia.mediaplayer.api.MediaKind}, which is also the return type of
 * {@link com.lia.mediaplayer.api.MediaSource#kind()}.</p>
 *
 * @deprecated Import {@link com.lia.mediaplayer.api.MediaKind} directly instead.
 */
@Deprecated
public final class MediaKind {
    private MediaKind() {}

    /** @see com.lia.mediaplayer.api.MediaKind#IMAGE */
    public static final com.lia.mediaplayer.api.MediaKind IMAGE = com.lia.mediaplayer.api.MediaKind.IMAGE;
    /** @see com.lia.mediaplayer.api.MediaKind#VIDEO */
    public static final com.lia.mediaplayer.api.MediaKind VIDEO = com.lia.mediaplayer.api.MediaKind.VIDEO;
    /** @see com.lia.mediaplayer.api.MediaKind#AUDIO */
    public static final com.lia.mediaplayer.api.MediaKind AUDIO = com.lia.mediaplayer.api.MediaKind.AUDIO;
}
