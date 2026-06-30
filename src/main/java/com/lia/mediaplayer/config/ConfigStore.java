package com.lia.mediaplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.api.config.IntSliderOption;
import com.lia.mediaplayer.api.config.StepSliderOption;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public static final Integer[] RESOLUTION_HEIGHTS = {144, 240, 360, 480, 720};
    public static final Integer[] RESOLUTION_WIDTHS = {256, 426, 640, 854, 1280};

    static {
        VIDEO_RESOLUTION = new StepSliderOption<>(
                "liasmediaplayer:video_resolution",
                "config.liasmediaplayer.video_resolution",
                3, // default 480p
                RESOLUTION_HEIGHTS,
                height -> height + "p"
        );
        MAX_PINNED_IMAGES = new IntSliderOption("liasmediaplayer:max_pinned_images", "config.liasmediaplayer.max_pinned_images", 6, 1, 20);
        MAX_VIDEO_WINDOWS = new IntSliderOption("liasmediaplayer:max_video_windows", "config.liasmediaplayer.max_video_windows", 4, 1, 10);
        MAX_AUDIO_WINDOWS = new IntSliderOption("liasmediaplayer:max_audio_windows", "config.liasmediaplayer.max_audio_windows", 4, 1, 10);
        MAX_GIF_FRAMES = new IntSliderOption("liasmediaplayer:max_gif_frames", "config.liasmediaplayer.max_gif_frames", 256, 10, 1000);
        MAX_IMAGE_CACHE_ENTRIES = new IntSliderOption("liasmediaplayer:max_image_cache_entries", "config.liasmediaplayer.max_image_cache_entries", 30, 5, 100);
    }

    public ConfigStore() {
        register(VIDEO_RESOLUTION);
        register(MAX_PINNED_IMAGES);
        register(MAX_VIDEO_WINDOWS);
        register(MAX_AUDIO_WINDOWS);
        register(MAX_GIF_FRAMES);
        register(MAX_IMAGE_CACHE_ENTRIES);
    }

    public synchronized void register(ConfigOption<?> option) {
        registeredOptions.put(option.getId(), option);
        if (loaded) {
            // Re-load to apply any saved values for newly registered options
            load();
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> ConfigOption<T> getOption(String id) {
        return (ConfigOption<T>) registeredOptions.get(id);
    }

    public synchronized Collection<ConfigOption<?>> getAllOptions() {
        return registeredOptions.values();
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
        return RESOLUTION_WIDTHS[VIDEO_RESOLUTION.getValue()];
    }

    public int videoMaxHeight() {
        ensureLoaded();
        return RESOLUTION_HEIGHTS[VIDEO_RESOLUTION.getValue()];
    }
}
