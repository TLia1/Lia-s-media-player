package com.lia.mediaplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    // The client-side link filters (see com.lia.mediaplayer.chat.MediaFilters). Both
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

    static {
        VIDEO_RESOLUTION = new StepSliderOption<>(
                "liasmediaplayer:video_resolution",
                "liasmediaplayer",
                "config.liasmediaplayer.video_resolution",
                3, // default 480p
                RESOLUTION_HEIGHTS,
                height -> height + "p"
        );
        // Set separately rather than chained: withDescription is declared on
        // ConfigOption<T> and returns it, so chaining it onto a generic option would
        // need an unchecked cast back to the concrete type. The IntSliderOptions below
        // can chain because IntSliderOption is not generic.
        VIDEO_RESOLUTION.withDescription("config.liasmediaplayer.video_resolution.description");
        MAX_PINNED_IMAGES = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_pinned_images", "liasmediaplayer", "config.liasmediaplayer.max_pinned_images", 6, 1, 20)
                .withDescription("config.liasmediaplayer.max_pinned_images.description");
        MAX_VIDEO_WINDOWS = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_video_windows", "liasmediaplayer", "config.liasmediaplayer.max_video_windows", 4, 1, 10)
                .withDescription("config.liasmediaplayer.max_video_windows.description")
                .withWidth(OptionWidth.HALF);
        MAX_AUDIO_WINDOWS = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_audio_windows", "liasmediaplayer", "config.liasmediaplayer.max_audio_windows", 4, 1, 10)
                .withDescription("config.liasmediaplayer.max_audio_windows.description")
                .withWidth(OptionWidth.HALF);
        MAX_GIF_FRAMES = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_gif_frames", "liasmediaplayer", "config.liasmediaplayer.max_gif_frames", 256, 10, 1000)
                .withDescription("config.liasmediaplayer.max_gif_frames.description");
        MAX_IMAGE_CACHE_ENTRIES = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_image_cache_entries", "liasmediaplayer", "config.liasmediaplayer.max_image_cache_entries", 30, 5, 100)
                .withDescription("config.liasmediaplayer.max_image_cache_entries.description")
                .withWidth(OptionWidth.HALF);
        FRAME_QUEUE_CAPACITY = (IntSliderOption) new IntSliderOption("liasmediaplayer:frame_queue_capacity", "liasmediaplayer", "config.liasmediaplayer.frame_queue_capacity", 64, 16, 256)
                .withDescription("config.liasmediaplayer.frame_queue_capacity.description")
                .withWarning("config.liasmediaplayer.frame_queue_capacity.warning");
        MAX_IMAGE_CACHE_MEGABYTES = (IntSliderOption) new IntSliderOption("liasmediaplayer:max_image_cache_mb", "liasmediaplayer", "config.liasmediaplayer.max_image_cache_mb", 256, 64, 1024)
                .withDescription("config.liasmediaplayer.max_image_cache_mb.description")
                .withWidth(OptionWidth.HALF);
        YT_DLP_TIMEOUT_SECONDS = (IntSliderOption) new IntSliderOption("liasmediaplayer:yt_dlp_timeout", "liasmediaplayer", "config.liasmediaplayer.yt_dlp_timeout", 25, 5, 60)
                .withDescription("config.liasmediaplayer.yt_dlp_timeout.description");
        THEME = new EnumOption<>(
                "liasmediaplayer:theme",
                "liasmediaplayer",
                "config.liasmediaplayer.theme",
                ThemeName.DARK
        );
        THEME.withDescription("config.liasmediaplayer.theme.description");
        DEFAULT_WINDOW_POSITION = new EnumOption<>(
                "liasmediaplayer:default_window_position",
                "liasmediaplayer",
                "config.liasmediaplayer.default_window_position",
                WindowPosition.CENTER
        );
        DEFAULT_WINDOW_POSITION.withDescription("config.liasmediaplayer.default_window_position.description");
        LINK_FILTER_MODE = new EnumOption<>(
                "liasmediaplayer:link_filter_mode",
                "liasmediaplayer",
                "config.liasmediaplayer.link_filter_mode",
                FilterMode.OFF
        );
        LINK_FILTER_MODE.withDescription("config.liasmediaplayer.link_filter_mode.description");
        BLOCKED_DOMAINS = (StringOption) new StringOption("liasmediaplayer:blocked_domains", "liasmediaplayer", "config.liasmediaplayer.blocked_domains", "")
                .withDescription("config.liasmediaplayer.blocked_domains.description");
        ALLOWED_DOMAINS = (StringOption) new StringOption("liasmediaplayer:allowed_domains", "liasmediaplayer", "config.liasmediaplayer.allowed_domains", "")
                .withDescription("config.liasmediaplayer.allowed_domains.description");
        BLOCKED_SENDERS = (StringOption) new StringOption("liasmediaplayer:blocked_senders", "liasmediaplayer", "config.liasmediaplayer.blocked_senders", "")
                .withDescription("config.liasmediaplayer.blocked_senders.description");
        AUTO_UPDATE_TOOLS = (BooleanOption) new BooleanOption("liasmediaplayer:auto_update_tools", "liasmediaplayer", "config.liasmediaplayer.auto_update_tools", true)
                .withDescription("config.liasmediaplayer.auto_update_tools.description");
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
        Path path = file();
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has(option.getId())) {
                option.deserialize(json.get(option.getId()));
            }
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not read config from {}: {}", path, e.toString());
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
        Path path = file();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("config.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                JsonObject json = new JsonObject();
                for (ConfigOption<?> option : registeredOptions.values()) {
                    json.add(option.getId(), option.serialize());
                }
                GSON.toJson(json, writer);
            }
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not save config to {}: {}", path, e.toString());
        }
    }

    private void load() {
        Path path = file();
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                    ConfigOption<?> option = registeredOptions.get(entry.getKey());
                    if (option != null) {
                        option.deserialize(entry.getValue());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LiasMediaPlayer.LOGGER.warn("Could not read config from {}: {}", path, e.toString());
        }
    }

    private Path file() {
        try {
            return Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("liasmediaplayer").resolve("config.json");
        } catch (Exception e) {
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
