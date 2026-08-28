package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A plain on/off option, rendered as a button that reads {@code Label: ON} /
 * {@code Label: OFF} and flips on click — the shape vanilla uses for every one of its
 * own boolean settings.
 *
 * <p>The two states are labelled with vanilla's own {@code options.on} /
 * {@code options.off} keys, so they follow the player's language without this mod (or an
 * addon using this option) having to translate them.</p>
 */
public class BooleanOption extends ConfigOption<Boolean> {

    public BooleanOption(String id, String group, String translationKey, boolean defaultValue) {
        super(id, group, translationKey, defaultValue);
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            setValue(element.getAsBoolean());
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new Button.Builder(getButtonMessage(), button -> {
            setValue(!getValue());
            saveCallback.run();
            button.setMessage(getButtonMessage());
        }).bounds(x, y, width, 20).build();
    }

    private Component getButtonMessage() {
        return Component.translatable(getTranslationKey())
                .append(Component.literal(": "))
                .append(Component.translatable(getValue() ? "options.on" : "options.off"));
    }

    // ---- Covariant builder overrides (see ConfigOption's javadoc) ------------

    @Override
    public BooleanOption withDescription(String descriptionKey) {
        super.withDescription(descriptionKey);
        return this;
    }

    @Override
    public BooleanOption withWarning(String warningKey) {
        super.withWarning(warningKey);
        return this;
    }

    @Override
    public BooleanOption withWidth(OptionWidth width) {
        super.withWidth(width);
        return this;
    }
}
