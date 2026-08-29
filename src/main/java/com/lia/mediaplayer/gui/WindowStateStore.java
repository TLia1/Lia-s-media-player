package com.lia.mediaplayer.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.api.RepeatMode;
import com.lia.mediaplayer.playlist.PlaylistStore;
import com.lia.mediaplayer.storage.JsonFileStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the windows were left, persisted to {@code <gamedir>/liasmediaplayer/windows.json}.
 *
 * <p>Everything else the mod owns dies with the session: {@code onDisconnect} disposes
 * every window, so the size you dragged a player to and the loop mode you set were
 * re-chosen from scratch on every join. This remembers them.</p>
 *
 * <p>State is kept <strong>per kind</strong> ({@code image} / {@code video} /
 * {@code audio}), not per URL: what is worth restoring is "my video player lives
 * bottom-left at this size", which is a property of how someone arranges their screen,
 * not of the clip that happened to be playing. One entry per kind also bounds the file
 * at three objects however long the mod is used.</p>
 *
 * <p>Same shape as {@link PlaylistStore}: loaded lazily,
 * written through a {@link JsonFileStore} (temp file plus atomic move), and never thrown
 * from — a media player that refuses to start because a preferences file is unreadable
 * would be a worse failure than forgetting where the window was.</p>
 *
 * <p>The JSON is built field by field rather than by reflection, because the Gson that
 * ships with Minecraft ranges from 2.8 to 2.14 across the fourteen targets and record
 * support only arrived in 2.10.</p>
 */
public final class WindowStateStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Kind keys — also the JSON object's field names. */
    public static final String IMAGE = "image";
    public static final String VIDEO = "video";
    public static final String AUDIO = "audio";

    /**
     * One kind's remembered arrangement.
     *
     * <p>{@code placed} and {@code sized} carry the same meaning as
     * {@link MediaWindow}'s {@code userPlaced} / {@code userSized}: false means "never
     * touched", and the window should fall back to the configured default position and
     * its auto-fit scale rather than to a stored zero.</p>
     *
     * <p>The size is kept as the content's <strong>width in pixels</strong>, not as the
     * scale factor the window works in. The scale is relative to the source's own
     * resolution, so restoring 0.5 from a 1080p video onto a 360p one would give a
     * quarter of the box that was arranged; a width is the thing the user actually
     * chose, and means the same for every clip.</p>
     */
    public record State(boolean placed, int x, int y,
                        boolean sized, int width,
                        boolean queuePanel, RepeatMode repeat, boolean shuffle) {

        /** The state of a window nobody has arranged yet. */
        public static final State DEFAULT =
                new State(false, 0, 0, false, 0, false, RepeatMode.OFF, false);

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("placed", placed);
            json.addProperty("x", x);
            json.addProperty("y", y);
            json.addProperty("sized", sized);
            json.addProperty("width", width);
            json.addProperty("queuePanel", queuePanel);
            json.addProperty("repeat", repeat.name());
            json.addProperty("shuffle", shuffle);
            return json;
        }

        /**
         * Reads one entry back, falling back to {@link #DEFAULT}'s value for anything
         * missing or malformed. The file may have been written by an older version of
         * the mod, or hand-edited, so no field is trusted to be there or to parse.
         */
        static State fromJson(JsonObject json) {
            return new State(
                    bool(json, "placed", DEFAULT.placed()),
                    integer(json, "x", DEFAULT.x()),
                    integer(json, "y", DEFAULT.y()),
                    bool(json, "sized", DEFAULT.sized()),
                    integer(json, "width", DEFAULT.width()),
                    bool(json, "queuePanel", DEFAULT.queuePanel()),
                    repeatMode(json, DEFAULT.repeat()),
                    bool(json, "shuffle", DEFAULT.shuffle()));
        }

        private static boolean bool(JsonObject json, String key, boolean fallback) {
            try {
                JsonElement element = json.get(key);
                return element == null ? fallback : element.getAsBoolean();
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        private static int integer(JsonObject json, String key, int fallback) {
            try {
                JsonElement element = json.get(key);
                return element == null ? fallback : element.getAsInt();
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        private static RepeatMode repeatMode(JsonObject json, RepeatMode fallback) {
            try {
                JsonElement element = json.get("repeat");
                return element == null ? fallback : RepeatMode.valueOf(element.getAsString());
            } catch (RuntimeException e) {
                return fallback;
            }
        }
    }

    private final JsonFileStore file = new JsonFileStore("windows.json");
    private final Map<String, State> states = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    public WindowStateStore() {
    }

    /**
     * The remembered state for a kind, or {@link State#DEFAULT} if there is none.
     */
    public synchronized State get(String kind) {
        ensureLoaded();
        return states.getOrDefault(kind, State.DEFAULT);
    }

    /**
     * Records a kind's state, marking the file for rewriting only when something
     * actually changed.
     *
     * <p>Windows offer their state every client tick, which is 20 candidate writes a
     * second; the record's own equality is what turns that into one write per real
     * change. {@link #flush()} does the file access.</p>
     */
    public synchronized void put(String kind, State state) {
        ensureLoaded();
        if (state.equals(states.get(kind))) {
            return;
        }
        states.put(kind, state);
        dirty = true;
    }

    /**
     * Writes the file if anything changed since the last flush.
     */
    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        save();
    }

    // ------------------------------------------------------------------
    // Serialization (no Minecraft types — the unit tests drive these directly)
    // ------------------------------------------------------------------

    static String toJson(Map<String, State> states) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            json.add(entry.getKey(), entry.getValue().toJson());
        }
        return GSON.toJson(json);
    }

    static Map<String, State> fromJson(String text) {
        Map<String, State> parsed = new LinkedHashMap<>();
        JsonObject json;
        try {
            json = GSON.fromJson(text, JsonObject.class);
        } catch (RuntimeException e) {
            return parsed;
        }
        if (json == null) {
            return parsed;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                parsed.put(entry.getKey(), State.fromJson(entry.getValue().getAsJsonObject()));
            }
        }
        return parsed;
    }

    // ------------------------------------------------------------------
    // File access
    // ------------------------------------------------------------------

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        String text = file.read();
        if (text != null) {
            states.putAll(fromJson(text));
        }
    }

    private void save() {
        file.write(toJson(states));
    }
}
