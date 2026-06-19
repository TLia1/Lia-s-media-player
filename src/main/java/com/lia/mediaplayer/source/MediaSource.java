package com.lia.mediaplayer.source;

/**
 * @deprecated Use {@link com.lia.mediaplayer.api.MediaSource} directly.
 * This interface is kept only for source compatibility — it is a plain
 * re-export of the public API interface.
 */
@Deprecated
public interface MediaSource extends com.lia.mediaplayer.api.MediaSource {
    // Intentionally empty — just bridges old internal usages to the API type.
}
