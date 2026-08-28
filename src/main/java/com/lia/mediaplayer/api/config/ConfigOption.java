package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Objects;

/**
 * Represents a single configuration option that can be registered in the
 * Media Player's configuration menu.
 *
 * <p><b>Chaining.</b> {@link #withDescription}, {@link #withWarning} and
 * {@link #withWidth} are declared here and so answer {@code ConfigOption<T>}, which is
 * not the type a declaration wants back: {@code IntSliderOption x = new
 * IntSliderOption(...).withDescription(...)} does not compile, and every built-in option
 * used to carry a downcast to say so. Each subclass therefore <b>overrides all three
 * covariantly</b>, returning its own type — the whole body being {@code super}, a cast,
 * and {@code this}, once, in the one place that can prove the cast is sound.</p>
 *
 * <p>The alternative was the self-typed shape, {@code ConfigOption<T, SELF extends
 * ConfigOption<T, SELF>>}, which gets the same result with no per-subclass code and
 * gives it to an addon's own option type for free. It was not taken because this class
 * is in {@code api}: adding a type parameter breaks every {@code ConfigOption<?>} an
 * addon has already written, and three overrides per subclass is a cheaper price than a
 * breaking change to the surface other mods compile against. A subclass that skips them
 * loses nothing it had before.</p>
 *
 * @param <T> the type of value this option stores
 */
public abstract class ConfigOption<T> {
    private final String id;
    private final String group;
    private final String translationKey;
    private final T defaultValue;
    private T currentValue;
    private String warningKey;
    private String descriptionKey;
    private OptionWidth width = OptionWidth.FULL;

    public ConfigOption(String id, String group, String translationKey, T defaultValue) {
        this.id = id;
        this.group = group;
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
     * Sets a translation key describing what this option does, shown as a tooltip in
     * the configuration menu.
     *
     * <p>Distinct from {@link #withWarning}: a description explains the setting, a
     * warning cautions about a particular value. An option may carry both, and the
     * config screen shows the description first with the warning under it.</p>
     */
    public ConfigOption<T> withDescription(String descriptionKey) {
        this.descriptionKey = descriptionKey;
        return this;
    }

    /**
     * The description translation key, or {@code null} if this option has none.
     */
    public String getDescriptionKey() {
        return descriptionKey;
    }

    /**
     * Sets the width of the option in the config screen.
     */
    public ConfigOption<T> withWidth(OptionWidth width) {
        this.width = width;
        return this;
    }

    public OptionWidth getWidth() {
        return width;
    }

    /**
     * The unique identifier for this option (e.g. "liasmediaplayer:max_video_windows").
     */
    public String getId() {
        return id;
    }

    /**
     * The group this option belongs to, used for creating sub-menus in the config screen.
     */
    public String getGroup() {
        return group;
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
     * Restores the value this option was declared with.
     *
     * <p>It lives here rather than at the call site because {@code setValue} is typed
     * on {@code T}: a screen holding a {@code ConfigOption<?>} cannot hand its own
     * default back to it without an unchecked cast. The option can, so it does.</p>
     */
    public void resetToDefault() {
        this.currentValue = defaultValue;
    }

    /**
     * Whether this option is still at its default value — what the config screen uses
     * to decide if there is anything for its reset button to undo.
     */
    public boolean isDefault() {
        return Objects.equals(currentValue, defaultValue);
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
