package com.lia.mediaplayer.playlist;

import com.lia.mediaplayer.source.Urls;

import java.util.ArrayList;
import java.util.List;

/**
 * A named, ordered list of media URLs the user has saved. Entries are plain links
 * (direct audio files or YouTube videos); the audio player resolves and plays them as
 * sound. Persisted to disk by {@link PlaylistStore} (serialized by its field names, so
 * keep them stable).
 */
public final class Playlist {

    private String name;
    private List<String> urls = new ArrayList<>();

    public Playlist(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The live, mutable list of URLs in play order.
     */
    public List<String> urls() {
        if (urls == null) { // a hand-edited / older JSON file may omit the array
            urls = new ArrayList<>();
        }
        return urls;
    }

    public int size() {
        return urls().size();
    }

    public boolean isEmpty() {
        return urls().isEmpty();
    }

    /**
     * Appends {@code url}, ignoring anything that is not a real {@code http(s)} link.
     *
     * <p>This is the choke point every entry passes through — the screens, the clipboard
     * import and the public API all end up here — so the same rule
     * {@link Urls#isHttp(String)} states for chat links holds for a stored playlist: what
     * comes back out of {@code playlists.json} is handed to {@code ffmpeg} and
     * {@code yt-dlp}, and neither may be given a {@code file:} or {@code concat:} URL.</p>
     */
    public void add(String url) {
        if (Urls.isHttp(url)) {
            urls().add(url);
        }
    }

    public void removeAt(int index) {
        List<String> list = urls();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }

    /**
     * Moves the entry at {@code from} so that it sits before what is currently at
     * {@code insertBefore}, the two indices being read against the list <em>as it is
     * now</em>.
     *
     * <p>That is the shape a drop needs: the user points at a gap between two rows, and
     * which gap they meant does not change because the row being moved is about to leave
     * its own place. {@link #swap} cannot express it — dragging a track from the twelfth
     * position to the second is one move, not ten swaps.</p>
     *
     * @param insertBefore {@code 0..size}, where {@code size} means "past the last entry"
     */
    public void move(int from, int insertBefore) {
        List<String> list = urls();
        if (from < 0 || from >= list.size()) {
            return;
        }
        int target = Math.max(0, Math.min(list.size(), insertBefore));
        if (target == from || target == from + 1) {
            return; // dropped back into the gap it came from
        }
        String url = list.remove(from);
        // Removing the entry shifted everything after it down by one, so a gap that was
        // past the old position is now one index lower.
        list.add(target > from ? target - 1 : target, url);
    }

    public void swap(int indexA, int indexB) {
        List<String> list = urls();
        if (indexA >= 0 && indexA < list.size() && indexB >= 0 && indexB < list.size()) {
            String temp = list.get(indexA);
            list.set(indexA, list.get(indexB));
            list.set(indexB, temp);
        }
    }
}
