package com.lia.mediaplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.config.BooleanOption;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.EnumOption;
import com.lia.mediaplayer.api.config.IntSliderOption;
import com.lia.mediaplayer.api.config.OptionWidth;
import com.lia.mediaplayer.api.config.StepSliderOption;
import com.lia.mediaplayer.api.config.StringOption;
import com.lia.mediaplayer.gui.ThemeName;
import com.lia.mediaplayer.gui.WindowPosition;
import com.lia.mediaplayer.source.FilterMode;
import com.lia.mediaplayer.storage.JsonFileStore;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The user's saved configuration, persisted to {@code <gamedir>/liasmediaplayer/config.json}.
 * Dynamically registers and stores ConfigOptions.
 */
public class ConfigStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final JsonFileStore file = new JsonFileStore("config.json");
    private boolean loaded;

    private final Map<String, ConfigOption<?>> registeredOptions = new LinkedHashMap<>();

    // Built-in options
    public static final StepSliderOption<Integer> VIDEO_RESOLUTION;
    public static final IntSliderOption MAX_PINNED_IMAGES;
    public static final IntSliderOption MAX_VIDEO_WINDOWS;
    public static final IntSliderOption MAX_AUDIO_WINDOWS;
    public static final IntSliderOption MAX_GIF_FRAMES;
    public static final IntSliderOption MAX_IMAGE_CACHE_ENTRIES;
    public static final IntSliderOption FRAME_QUEUE_CAPACITY;
    public static final IntSliderOption MAX_IMAGE_CACHE_MEGABYTES;
    public static final IntSliderOption YT_DLP_TIMEOUT_SECONDS;
    public static final EnumOption<WindowPosition> DEFAULT_WINDOW_POSITION;
    /** Which palette every window, panel and list draws with — see {@code gui.Theme}. */
    public static final EnumOption<ThemeName> THEME;
    // The client-side link filters (see chat.MediaFilters). Both
    // host lists are kept, not one list whose meaning depends on the mode: switching
    // the mode to look at the other list should not throw away what was typed into
    // this one.
    public static final EnumOption<FilterMode> LINK_FILTER_MODE;
    public static final StringOption BLOCKED_DOMAINS;
    public static final StringOption ALLOWED_DOMAINS;
    public static final StringOption BLOCKED_SENDERS;
    /** Whether an outdated yt-dlp is replaced at launch instead of merely reported. */
    public static final BooleanOption AUTO_UPDATE_TOOLS;

    public static final Integer[] RESOLUTION_HEIGHTS = {144, 240, 360, 480, 720};
    public static final Integer[] RESOLUTION_WIDTHS = {256, 426, 640, 854, 1280};

    // ------------------------------------------------------------------
    // Declaring an option
    //
    // Every built-in option repeats the same four strings — the namespaced id, the
    // group, the label key and the description key — all four derived from one name,
    // and each declaration used to spell them out and carry a downcast on top. The
    // factories below take the name and nothing else; only what is genuinely per-option
    // (the range, the default, a width, a warning) is still written out.
    //
    // The convention they encode is that an option's name is the same in all four:
    // `max_gif_frames` is `liasmediaplayer:max_gif_frames`, labelled by
    // `config.liasmediaplayer.max_gif_frames` and described by that key plus
    // `.description`. A new option that follows it needs two language keys and one line
    // here; one that does not cannot use these and should say why.
    // ------------------------------------------------------------------

    /**
     * The group every built-in option belongs to — the mod's own id, which is what puts
     * them all on one page of the config screen. An addon passes its own.
     */
    private static final String GROUP = LiasMediaPlayer.MODID;

    private static String id(String name) {
        return GROUP + ":" + name;
    }

    private static String key(String name) {
        return "config." + GROUP + "." + name;
    }

    private static String descriptionKey(String name) {
        return key(name) + ".description";
    }

    private static IntSliderOption slider(String name, int defaultValue, int min, int max) {
        return new IntSliderOption(id(name), GROUP, key(name), defaultValue, min, max)
                .withDescription(descriptionKey(name));
    }

    private static <E extends Enum<E>> EnumOption<E> choice(String name, E defaultValue) {
        return new EnumOption<>(id(name), GROUP, key(name), defaultValue)
                .withDescription(descriptionKey(name));
    }

    private static StringOption text(String name) {
        return new StringOption(id(name), GROUP, key(name), "")
                .withDescription(descriptionKey(name));
    }

    private static BooleanOption toggle(String name, boolean defaultValue) {
        return new BooleanOption(id(name), GROUP, key(name), defaultValue)
                .withDescription(descriptionKey(name));
    }

    static {
        VIDEO_RESOLUTION = new StepSliderOption<>(
                id("video_resolution"), GROUP, key("video_resolution"),
                3, // default 480p
                RESOLUTION_HEIGHTS,
                height -> height + "p")
                .withDescription(descriptionKey("video_resolution"));
        MAX_PINNED_IMAGES = slider("max_pinned_images", 6, 1, 20);
        MAX_VIDEO_WINDOWS = slider("max_video_windows", 4, 1, 10).withWidth(OptionWidth.HALF);
        MAX_AUDIO_WINDOWS = slider("max_audio_windows", 4, 1, 10).withWidth(OptionWidth.HALF);
        MAX_GIF_FRAMES = slider("max_gif_frames", 256, 10, 1000);
        MAX_IMAGE_CACHE_ENTRIES = slider("max_image_cache_entries", 30, 5, 100).withWidth(OptionWidth.HALF);
        FRAME_QUEUE_CAPACITY = slider("frame_queue_capacity", 64, 16, 256)
                .withWarning(key("frame_queue_capacity") + ".warning");
        MAX_IMAGE_CACHE_MEGABYTES = slider("max_image_cache_mb", 256, 64, 1024).withWidth(OptionWidth.HALF);
        YT_DLP_TIMEOUT_SECONDS = slider("yt_dlp_timeout", 25, 5, 60);
        THEME = choice("theme", ThemeName.DARK);
        DEFAULT_WINDOW_POSITION = choice("default_window_position", WindowPosition.CENTER);
        LINK_FILTER_MODE = choice("link_filter_mode", FilterMode.OFF);
        BLOCKED_DOMAINS = text("blocked_domains");
        ALLOWED_DOMAINS = text("allowed_domains");
        BLOCKED_SENDERS = text("blocked_senders");
        AUTO_UPDATE_TOOLS = toggle("auto_update_tools", true);
    }

    public ConfigStore() {
        register(VIDEO_RESOLUTION);
        register(MAX_VIDEO_WINDOWS);
        register(MAX_AUDIO_WINDOWS);
        register(MAX_IMAGE_CACHE_ENTRIES);
        register(MAX_IMAGE_CACHE_MEGABYTES);
        register(MAX_GIF_FRAMES);
        register(MAX_PINNED_IMAGES);
        register(FRAME_QUEUE_CAPACITY);
        register(YT_DLP_TIMEOUT_SECONDS);
        register(THEME);
        register(DEFAULT_WINDOW_POSITION);
        register(LINK_FILTER_MODE);
        register(BLOCKED_DOMAINS);
        register(ALLOWED_DOMAINS);
        register(BLOCKED_SENDERS);
        register(AUTO_UPDATE_TOOLS);
    }

    public synchronized void register(ConfigOption<?> option) {
        registeredOptions.put(option.getId(), option);
        if (loaded) {
            // Apply the saved value for this option alone. Re-running the full load()
            // would also re-apply the on-disk value of every other option, discarding
            // any change made since the file was last written.
            applySavedValue(option);
        }
    }

    /**
     * Reads the config file and applies the stored value of a single option, if present.
     * Used when an addon registers an option after the initial load.
     */
    private void applySavedValue(ConfigOption<?> option) {
        JsonObject json = readJson();
        if (json != null && json.has(option.getId())) {
            option.deserialize(json.get(option.getId()));
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> ConfigOption<T> getOption(String id) {
        return (ConfigOption<T>) registeredOptions.get(id);
    }

    public synchronized Collection<ConfigOption<?>> getAllOptions() {
        return registeredOptions.values();
    }

    public synchronized List<ConfigOption<?>> getOptionsByGroup(String group) {
        return registeredOptions.values().stream()
                .filter(o -> o.getGroup().equals(group))
                .collect(Collectors.toList());
    }

    public synchronized List<String> getGroups() {
        return registeredOptions.values().stream()
                .map(ConfigOption::getGroup)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    public synchronized void save() {
        file.write(GSON.toJson(toJson()));
    }

    private void load() {
        applyJson(readJson());
    }

    /**
     * Every registered option's current value, as the document {@link #save} writes.
     *
     * <p>Separated from the file I/O so the format has a seam a unit test can drive:
     * this file is edited by hand, survives an upgrade of the mod, and is the one place
     * a bad value can reach every setting at once. Same shape as
     * {@code WindowStateStore.toJson}/{@code fromJson}, for the same reason.</p>
     */
    JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (ConfigOption<?> option : registeredOptions.values()) {
            json.add(option.getId(), option.serialize());
        }
        return json;
    }

    /**
     * Applies a saved document to the registered options.
     *
     * <p>An entry naming an option nobody registered is skipped rather than an error —
     * that is what an addon's setting looks like when the addon is not installed, and
     * dropping it would erase the addon's configuration the next time this file is
     * written. What each option makes of a value of the wrong shape is the option's own
     * business: they all leave themselves alone rather than throw.</p>
     */
    void applyJson(@Nullable JsonObject json) {
        if (json == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            ConfigOption<?> option = registeredOptions.get(entry.getKey());
            if (option != null) {
                option.deserialize(entry.getValue());
            }
        }
    }

    /**
     * The config file parsed, or {@code null} if it is absent, unreadable or not JSON.
     * An unparseable file leaves every option on its default rather than failing the
     * launch — the same best-effort contract {@link JsonFileStore} states for the I/O.
     */
    @Nullable
    private JsonObject readJson() {
        return parse(file.read(), file.path());
    }

    /**
     * A config document parsed defensively: {@code null} for anything that is not a JSON
     * object, including {@code null} itself.
     *
     * @param source only named in the warning, so a test can pass {@code null}
     */
    @Nullable
    static JsonObject parse(@Nullable String text, @Nullable Object source) {
        if (text == null) {
            return null;
        }
        try {
            JsonElement parsed = GSON.fromJson(text, JsonElement.class);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not parse {}: {}", source, e.toString());
            return null;
        }
    }

    // Convenience delegates for the core built-in options
    public int videoMaxWidth() {
        ensureLoaded();
        return RESOLUTION_WIDTHS[resolutionIndex()];
    }

    public int videoMaxHeight() {
        ensureLoaded();
        return RESOLUTION_HEIGHTS[resolutionIndex()];
    }

    /**
     * The selected resolution step, clamped to the shorter of the two parallel arrays.
     * The stored value comes from a JSON file that may have been written by a different
     * version of the mod, so it is not trusted to be a valid index.
     */
    private int resolutionIndex() {
        int steps = Math.min(RESOLUTION_WIDTHS.length, RESOLUTION_HEIGHTS.length);
        return Math.max(0, Math.min(steps - 1, VIDEO_RESOLUTION.getValue()));
    }

    public WindowPosition defaultWindowPosition() {
        ensureLoaded();
        return DEFAULT_WINDOW_POSITION.getValue();
    }

    /**
     * The selected palette. Read once a client tick by {@code gui.Theme}, which is why
     * this one does not {@code ensureLoaded()}: the settings are loaded during startup
     * (see {@code LiasMediaPlayer.init}), and a tick poll has no business doing file
     * I/O on the off-chance they are not.
     */
    public ThemeName theme() {
        return THEME.getValue();
    }
}
