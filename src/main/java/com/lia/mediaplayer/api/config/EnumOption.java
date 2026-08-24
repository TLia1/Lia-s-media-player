package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class EnumOption<E extends Enum<E>> extends ConfigOption<E> {
    private final List<E> values;

    public EnumOption(String id, String group, String translationKey, E defaultValue) {
        super(id, group, translationKey, defaultValue);
        this.values = Arrays.asList(defaultValue.getDeclaringClass().getEnumConstants());
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue().name());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String name = element.getAsString();
            for (E val : values) {
                if (val.name().equals(name)) {
                    setValue(val);
                    break;
                }
            }
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new Button.Builder(getButtonMessage(), (button) -> {
            cycleValue();
            saveCallback.run();
            button.setMessage(getButtonMessage());
        }).bounds(x, y, width, 20).build();
    }

    private void cycleValue() {
        int currentIndex = values.indexOf(getValue());
        int nextIndex = (currentIndex + 1) % values.size();
        setValue(values.get(nextIndex));
    }

    private Component getButtonMessage() {
        return Component.translatable(getTranslationKey())
                .append(Component.literal(": "))
                .append(valueLabel(getValue()));
    }

    /**
     * The label for one value: {@code <translationKey>.<constant in lower case>} when
     * the language files carry that key, and the bare constant name when they do not.
     *
     * <p>The fallback is what keeps this safe for an addon's own enum option: it gets a
     * translated value label by adding one key per constant, and the same raw
     * {@code SCREAMING_CASE} it has always had until it does.</p>
     *
     * <p>The check is made by resolving the key rather than through {@code I18n}: a
     * translatable component resolves to its own key when nothing translates it, which
     * is the same answer with one class fewer between here and the language file.</p>
     */
    private Component valueLabel(E value) {
        String key = getTranslationKey() + "." + value.name().toLowerCase(Locale.ROOT);
        Component translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(value.name()) : translated;
    }
}
