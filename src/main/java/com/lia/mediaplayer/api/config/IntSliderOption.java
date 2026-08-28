package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class IntSliderOption extends ConfigOption<Integer> {
    private final int min;
    private final int max;

    public IntSliderOption(String id, String group, String translationKey, int defaultValue, int min, int max) {
        super(id, group, translationKey, defaultValue);
        this.min = min;
        this.max = max;
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int val = element.getAsInt();
            setValue(Math.max(min, Math.min(max, val)));
        }
    }

    @Override
    public AbstractWidget createWidget(int x, int y, int width, Runnable saveCallback) {
        return new AbstractSliderButton(x, y, width, 20, Component.empty(), getSliderValue()) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.translatable(getTranslationKey()).append(Component.literal(": " + getIntValue())));
            }

            @Override
            protected void applyValue() {
                // Qualified deliberately, and it must stay that way. From 1.21.11 on,
                // AbstractSliderButton made its own setValue(double) protected instead of
                // private, so inside this anonymous subclass the bare name `setValue`
                // resolves to the *inherited* slider method: an int widens to a double with
                // no boxing, and an inherited member shadows the enclosing class's method
                // outright rather than overloading with it. The option's value was then
                // never written — nothing saved, and the reset button stayed greyed out —
                // while the slider fed its own raw integer back in as a 0..1 fraction and
                // clamped the handle to one end. Naming the outer instance is what keeps
                // the two apart, on every version.
                IntSliderOption.this.setValue(getIntValue());
                saveCallback.run();
            }

            private int getIntValue() {
                return Math.max(min, Math.min(max, (int) Math.round(this.value * (max - min) + min)));
            }
        };
    }

    private double getSliderValue() {
        int range = max - min;
        return range <= 0 ? 0.0 : (double) (getValue() - min) / range;
    }

    // ---- Covariant builder overrides (see ConfigOption's javadoc) ------------

    @Override
    public IntSliderOption withDescription(String descriptionKey) {
        super.withDescription(descriptionKey);
        return this;
    }

    @Override
    public IntSliderOption withWarning(String warningKey) {
        super.withWarning(warningKey);
        return this;
    }

    @Override
    public IntSliderOption withWidth(OptionWidth width) {
        super.withWidth(width);
        return this;
    }
}

