package com.lia.mediaplayer.playlist;

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

    /** The live, mutable list of URLs in play order. */
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

    public void add(String url) {
        urls().add(url);
    }

    public void removeAt(int index) {
        List<String> list = urls();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
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
