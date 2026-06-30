package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Represents a single configuration option that can be registered in the
 * Media Player's configuration menu.
 *
 * @param <T> the type of value this option stores
 */
public abstract class ConfigOption<T> {
    private final String id;
    private final String translationKey;
    private final T defaultValue;
    private T currentValue;
    private String warningKey;

    public ConfigOption(String id, String translationKey, T defaultValue) {
        this.id = id;
        this.translationKey = translationKey;
        this.defaultValue = defaultValue;
        this.currentValue = defaultValue;
    }

    /**
     * Sets a warning translation key to be displayed as a tooltip when configuring this option.
     * Useful for warning users about performance or memory implications.
     */
    public ConfigOption<T> withWarning(String warningKey) {
        this.warningKey = warningKey;
        return this;
    }

    public String getWarningKey() {
        return warningKey;
    }

    /**
     * The unique identifier for this option (e.g. "liasmediaplayer:max_video_windows").
     */
    public String getId() {
        return id;
    }

    /**
     * The localization key used to display the label in the GUI.
     */
    public String getTranslationKey() {
        return translationKey;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue() {
        return currentValue;
    }

    public void setValue(T value) {
        this.currentValue = value;
        // Optionally trigger a save, but currently ConfigStore handles save when UI closes or updates
    }

    /**
     * Serializes the current value to a JSON element for storage.
     */
    public abstract JsonElement serialize();

    /**
     * Deserializes the value from a JSON element loaded from storage.
     */
    public abstract void deserialize(JsonElement element);

    /**
     * Creates the GUI widget that will be rendered in the configuration screen.
     *
     * @param x            the starting X coordinate
     * @param y            the starting Y coordinate
     * @param width        the width of the widget
     * @param saveCallback a callback that should be invoked when the value changes, to persist the settings
     */
    public abstract AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback);
}
