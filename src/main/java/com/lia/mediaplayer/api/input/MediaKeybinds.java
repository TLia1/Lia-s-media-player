/*
 * This file is part of the Lia's Media Player API.
 * Licensed under the MIT License.
 */
package com.lia.mediaplayer.api.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The two ways an addon claims a key, and the rules that go with each.
 *
 * <h2>A key binding</h2>
 *
 * <p>{@link #register(KeyMapping, Consumer)} hands the mod a vanilla {@link KeyMapping}
 * to register alongside its own. It appears in <em>Options &rarr; Controls</em>, the user
 * assigns whatever key they like, and the mod polls it once a client tick and calls the
 * consumer. Declare it <b>unbound</b> ({@link InputConstants#UNKNOWN}) as the mod's own
 * bindings are, so it can never clash out of the box.</p>
 *
 * <p><b>Timing matters.</b> Both loader bridges register whatever the mod's binding list
 * holds at one fixed moment during startup, so this has to be called from your mod
 * constructor or client initializer — a mapping registered after that tick is never
 * collected by the game and will simply never fire. The call is logged either way.</p>
 *
 * <p>An addon that would rather register its own {@code KeyMapping} through its loader
 * and keep its own tick handler should do exactly that; this exists so a small addon
 * does not have to write the loader half twice.</p>
 *
 * <h2>A window shortcut</h2>
 *
 * <p>{@link #registerWindowShortcut} is the other family: a <em>fixed</em> key that acts
 * on the front-most player while the chat screen (or another screen that hosts the
 * window stack) is open. It is not user-rebindable, which is why the rule below is
 * enforced rather than documented.</p>
 *
 * <p><b>A window shortcut must never claim a bare letter.</b> These keys are pressed
 * over a screen whose text field has the focus: someone opening chat to type "next"
 * would have the {@code n} eaten before they got started. Registering an unmodified
 * letter or digit throws. Use {@link #MOD_CONTROL} — the mod's own table is
 * {@code Ctrl} plus a letter for exactly this reason — and remember that the mod already
 * holds {@code Ctrl} with {@code M, L, S, N, P, F} and the arrows, and that the chat
 * field itself holds {@code Ctrl+A/C/V/X}. Built-in shortcuts are matched first, so an
 * addon cannot shadow one.</p>
 *
 * <p>This is part of the <b>public API</b>.</p>
 *
 * @since API 3.2.0
 */
public final class MediaKeybinds {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** A control key is held ({@code Cmd} on macOS, like every other control check in the mod). */
    public static final int MOD_CONTROL = 1;
    /** A shift key is held. */
    public static final int MOD_SHIFT = 2;
    /** An alt key is held. */
    public static final int MOD_ALT = 4;

    /** Copy-on-write: registered during mod construction, read from the client tick. */
    private static final List<Binding> BINDINGS = new CopyOnWriteArrayList<>();
    private static final List<Shortcut> SHORTCUTS = new CopyOnWriteArrayList<>();

    private MediaKeybinds() {
    }

    /**
     * Registers a key binding for the mod to collect and poll.
     *
     * @param mapping  an unbound {@link KeyMapping}; give it your own category and
     *                 translation key
     * @param onPress  run once per press, on the client thread, with the running
     *                 {@link Minecraft}. A consumer that throws is logged and the
     *                 binding is left registered.
     */
    public static void register(KeyMapping mapping, Consumer<Minecraft> onPress) {
        if (mapping == null || onPress == null) {
            return;
        }
        BINDINGS.add(new Binding(mapping, onPress));
        LOGGER.info("Registered addon key binding {}", mapping.getName());
    }

    /**
     * Registers a fixed shortcut over the screens that host the window stack.
     *
     * @param glfwKey   a GLFW key code, as {@link InputConstants} names them
     * @param modifiers zero or more of {@link #MOD_CONTROL}, {@link #MOD_SHIFT},
     *                  {@link #MOD_ALT}, or'd together
     * @throws IllegalArgumentException if no modifier is given and the key is a letter
     *                                  or a digit — see the class note
     */
    public static void registerWindowShortcut(int glfwKey, int modifiers, WindowShortcutAction action) {
        if (action == null) {
            return;
        }
        if (modifiers == 0 && isBareTypingKey(glfwKey)) {
            throw new IllegalArgumentException(
                    "A window shortcut may not claim an unmodified letter or digit (GLFW key " + glfwKey
                            + "): the chat text field has the focus there. Add MediaKeybinds.MOD_CONTROL.");
        }
        SHORTCUTS.add(new Shortcut(glfwKey, modifiers, action));
    }

    /**
     * GLFW numbers the printable ASCII keys by their character, so a letter or a digit
     * is exactly what a user typing into the chat field would produce.
     */
    private static boolean isBareTypingKey(int glfwKey) {
        return (glfwKey >= InputConstants.KEY_A && glfwKey <= InputConstants.KEY_Z)
                || (glfwKey >= InputConstants.KEY_0 && glfwKey <= InputConstants.KEY_9);
    }

    // ------------------------------------------------------------------
    // Read by the mod
    // ------------------------------------------------------------------

    /** Every registered binding (unmodifiable). Called by the mod; addons have no reason to. */
    public static List<Binding> bindings() {
        return Collections.unmodifiableList(new ArrayList<>(BINDINGS));
    }

    /** Every registered window shortcut (unmodifiable). Called by the mod. */
    public static List<Shortcut> windowShortcuts() {
        return Collections.unmodifiableList(new ArrayList<>(SHORTCUTS));
    }

    /** One registered key binding and what it does. */
    public record Binding(KeyMapping mapping, Consumer<Minecraft> onPress) {
    }

    /** One registered window shortcut. */
    public record Shortcut(int glfwKey, int modifiers, WindowShortcutAction action) {

        /** Whether this shortcut is the one the given key press means. */
        public boolean matches(int key, boolean control, boolean shift, boolean alt) {
            return key == glfwKey
                    && control == ((modifiers & MOD_CONTROL) != 0)
                    && shift == ((modifiers & MOD_SHIFT) != 0)
                    && alt == ((modifiers & MOD_ALT) != 0);
        }
    }
}
