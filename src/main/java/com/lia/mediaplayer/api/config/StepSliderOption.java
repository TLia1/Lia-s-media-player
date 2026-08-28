package com.lia.mediaplayer.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

public class StepSliderOption<T> extends ConfigOption<Integer> {
    private final T[] steps;
    private final Function<T, String> displayFormatter;

    public StepSliderOption(String id, String group, String translationKey, int defaultIndex,
                            T[] steps, Function<T, String> displayFormatter) {
        super(id, group, translationKey, defaultIndex);
        this.steps = steps;
        this.displayFormatter = displayFormatter;
    }

    /**
     * The value the slider currently points at. The index is clamped because the stored
     * value comes from a JSON file a user (or an older version of the mod) may have
     * written with a different set of steps.
     */
    public T getSelectedStep() {
        return steps[clampIndex(getValue())];
    }

    private int clampIndex(int index) {
        return Math.max(0, Math.min(steps.length - 1, index));
    }

    @Override
    public JsonElement serialize() {
        return new JsonPrimitive(getValue());
    }

    @Override
    public void deserialize(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            int val = element.getAsInt();
            setValue(Math.max(0, Math.min(steps.length - 1, val)));
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
                this.setMessage(Component.translatable(getTranslationKey())
                        .append(Component.literal(": " + displayFormatter.apply(steps[getIntValue()]))));
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
                StepSliderOption.this.setValue(getIntValue());
                saveCallback.run();
            }

            private int getIntValue() {
                return clampIndex((int) Math.round(this.value * (steps.length - 1)));
            }
        };
    }

    private double getSliderValue() {
        int lastIndex = steps.length - 1;
        return lastIndex <= 0 ? 0.0 : (double) clampIndex(getValue()) / lastIndex;
    }

    // ---- Covariant builder overrides (see ConfigOption's javadoc) ------------

    @Override
    public StepSliderOption<T> withDescription(String descriptionKey) {
        super.withDescription(descriptionKey);
        return this;
    }

    @Override
    public StepSliderOption<T> withWarning(String warningKey) {
        super.withWarning(warningKey);
        return this;
    }

    @Override
    public StepSliderOption<T> withWidth(OptionWidth width) {
        super.withWidth(width);
        return this;
    }
}

