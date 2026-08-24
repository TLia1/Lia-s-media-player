package com.lia.mediaplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.EnumOption;
import com.lia.mediaplayer.api.config.IntSliderOption;
import com.lia.mediaplayer.api.config.OptionWidth;
import com.lia.mediaplayer.api.config.StepSliderOption;
import com.lia.mediaplayer.gui.WindowPosition;
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
        DEFAULT_WINDOW_POSITION = new EnumOption<>(
                "liasmediaplayer:default_window_position",
                "liasmediaplayer",
                "config.liasmediaplayer.default_window_position",
                WindowPosition.CENTER
        );
        DEFAULT_WINDOW_POSITION.withDescription("config.liasmediaplayer.default_window_position.description");
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
        register(DEFAULT_WINDOW_POSITION);
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
}
