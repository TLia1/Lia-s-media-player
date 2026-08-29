package com.lia.mediaplayer.gui;

import com.lia.mediaplayer.LiasMediaPlayer;
import com.lia.mediaplayer.MediaPlayerContext;
import com.lia.mediaplayer.api.MediaHandle;
import com.lia.mediaplayer.api.input.MediaKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The keyboard half of driving the media windows: what a key press means while a screen
 * that hosts the window stack is open, and who it is aimed at.
 *
 * <p>Split in two on purpose. {@link #actionFor} is a pure lookup — key plus modifiers
 * plus "is a message being typed?" in, an {@link Action} out — so the whole table can be
 * unit-tested without a game; {@link #handle} is the part that needs a live window to
 * act on.</p>
 *
 * <h2>Why two families of shortcut</h2>
 *
 * <p>These keys are pressed over the chat screen, where the text field has the focus.
 * That rules out claiming a bare letter: someone opening chat to say "next" would have
 * the {@code n} eaten before they got started. So the table has two halves.</p>
 *
 * <ul>
 *   <li><strong>Bare {@code Space} and the arrow keys</strong>, but only while the chat
 *       field is <em>empty</em>. None of them does anything to an empty text field, and
 *       an empty field is exactly the state "not typing yet" — so transport and seeking,
 *       the things reached most often, stay one keystroke away.</li>
 *   <li><strong>{@code Ctrl} plus a letter, or {@code Ctrl} plus an arrow</strong>, always.
 *       The chat field binds {@code Ctrl+A/C/V/X} and nothing else, so the keys below are
 *       free whether or not a message is half-written. {@link Keys#controlDown()} is what
 *       decides, not the event's modifier bits, so this is {@code Cmd} on macOS like every
 *       other control-key check in the mod. Volume lives here rather than on the bare
 *       arrow: an empty chat field is also the state vanilla uses bare Up/Down to recall
 *       chat history, and the mod must not shadow that.</li>
 * </ul>
 *
 * <p>{@code Escape} is deliberately left alone: it closes the chat, which is what
 * someone pressing it wants, and a media window intercepting it would be a surprise
 * with no way out.</p>
 */
final class WindowShortcuts {

    /** How far a plain arrow key seeks. */
    private static final long SEEK_MICROS = 5_000_000L;
    /** How far {@code Shift} + an arrow key seeks. */
    private static final long SEEK_FAR_MICROS = 30_000_000L;
    /** How much one press of an up/down arrow moves the volume. */
    private static final float VOLUME_STEP = 0.05f;

    /**
     * What a key press means. {@link #NONE} is "not ours" — the screen must see the key.
     */
    enum Action {
        NONE,
        PLAY_PAUSE,
        SEEK_BACK,
        SEEK_FORWARD,
        SEEK_BACK_FAR,
        SEEK_FORWARD_FAR,
        VOLUME_UP,
        VOLUME_DOWN,
        MUTE,
        LOOP,
        SHUFFLE,
        NEXT,
        PREVIOUS,
        THEATER
    }

    private WindowShortcuts() {
    }

    /**
     * The table itself.
     *
     * @param key       a GLFW key code, as {@link InputConstants} names them
     * @param control   whether a control key (command on macOS) is held
     * @param shift     whether a shift key is held
     * @param typing    whether a chat message is part-written, which withdraws every
     *                  un-modified shortcut
     */
    static Action actionFor(int key, boolean control, boolean shift, boolean typing) {
        if (control) {
            return switch (key) {
                case InputConstants.KEY_M -> Action.MUTE;
                case InputConstants.KEY_L -> Action.LOOP;
                case InputConstants.KEY_S -> Action.SHUFFLE;
                case InputConstants.KEY_N -> Action.NEXT;
                case InputConstants.KEY_P -> Action.PREVIOUS;
                case InputConstants.KEY_F -> Action.THEATER;
                case InputConstants.KEY_UP -> Action.VOLUME_UP;
                case InputConstants.KEY_DOWN -> Action.VOLUME_DOWN;
                default -> Action.NONE;
            };
        }
        if (typing) {
            return Action.NONE;
        }
        return switch (key) {
            case InputConstants.KEY_SPACE -> Action.PLAY_PAUSE;
            case InputConstants.KEY_LEFT -> shift ? Action.SEEK_BACK_FAR : Action.SEEK_BACK;
            case InputConstants.KEY_RIGHT -> shift ? Action.SEEK_FORWARD_FAR : Action.SEEK_FORWARD;
            default -> Action.NONE;
        };
    }

    /**
     * Offers {@code key} to whatever addons registered a window shortcut for it.
     *
     * <p>Every registration is checked, not just the first match, and the first one that
     * answers {@code true} takes the key — an addon that finds nothing to do says so and
     * the key goes on to the screen, which is what keeps a shortcut from quietly eating
     * a keystroke over a text field.</p>
     */
    private static boolean handleAddonShortcut(int key) {
        List<MediaKeybinds.Shortcut> shortcuts = MediaKeybinds.windowShortcuts();
        if (shortcuts.isEmpty()) {
            return false;
        }
        boolean control = Keys.controlDown();
        boolean shift = Keys.shiftDown();
        boolean alt = Keys.altDown();
        MediaHandle target = frontMostHandle();
        for (MediaKeybinds.Shortcut shortcut : shortcuts) {
            if (!shortcut.matches(key, control, shift, alt)) {
                continue;
            }
            try {
                if (shortcut.action().onPress(target)) {
                    return true;
                }
            } catch (RuntimeException e) {
                LiasMediaPlayer.LOGGER.error("An addon window shortcut threw on key {}", key, e);
            }
        }
        return false;
    }

    /** The window an addon shortcut acts on: the same one the mod's own keys pick. */
    @Nullable
    private static MediaHandle frontMostHandle() {
        MediaWindow target = MediaWindowOverlay.frontMost(MediaWindow::hasTransport);
        return target == null ? null : target.handle();
    }

    /**
     * Runs whatever {@code key} means on the front-most window that can do it.
     *
     * <p>"Front-most that can do it" rather than plainly "front-most": a pinned image on
     * top of the stack has no transport, and it would be a poor answer to swallow
     * {@code Space} on its behalf while the video behind it keeps playing. The theatre
     * toggle picks its target the same way, from the windows that have a picture to
     * enlarge.</p>
     *
     * @return {@code true} when the mod took the key and the screen must not see it
     */
    static boolean handle(Screen screen, int key) {
        Action action = actionFor(key, Keys.controlDown(), Keys.shiftDown(), !ChatInput.isEmpty(screen));
        if (action == Action.NONE) {
            // Addon shortcuts are tried only once the mod's own table has declined, so
            // an addon can never shadow a built-in key — see api.input.MediaKeybinds.
            return handleAddonShortcut(key);
        }
        // Volume is the one shared level (see media.Volume), so these three are answered
        // by the mod as a whole rather than by whichever window happens to be in front.
        MediaPlayerContext context = MediaPlayerContext.getOrNull();
        if (context == null) {
            return false;
        }
        switch (action) {
            case VOLUME_UP -> {
                context.getVolumeManager().change(VOLUME_STEP);
                return true;
            }
            case VOLUME_DOWN -> {
                context.getVolumeManager().change(-VOLUME_STEP);
                return true;
            }
            case MUTE -> {
                context.getVolumeManager().toggleMute();
                return true;
            }
            default -> {
                // handled below, against a window
            }
        }
        if (action == Action.THEATER) {
            // Not a transport action: how big the picture is drawn is the viewer's own
            // business even in a host-controlled session.
            MediaWindow target = MediaWindowOverlay.frontMost(MediaWindow::supportsTheater);
            return target != null && target.toggleTheater();
        }
        MediaWindow target = MediaWindowOverlay.frontMost(MediaWindow::hasTransport);
        if (target == null) {
            return false;
        }
        if (target.isLocked()) {
            // Held off by SyncControl.setLocked. The key is still consumed: it was aimed
            // at the player, and letting a space fall through into the chat field instead
            // would be a worse answer than nothing happening.
            return true;
        }
        return switch (action) {
            case PLAY_PAUSE -> target.togglePlayPause();
            case SEEK_BACK -> target.seekBy(-SEEK_MICROS);
            case SEEK_FORWARD -> target.seekBy(SEEK_MICROS);
            case SEEK_BACK_FAR -> target.seekBy(-SEEK_FAR_MICROS);
            case SEEK_FORWARD_FAR -> target.seekBy(SEEK_FAR_MICROS);
            case LOOP -> target.cycleRepeat();
            case SHUFFLE -> target.toggleShuffle();
            case NEXT -> target.playNext();
            case PREVIOUS -> target.playPrevious();
            default -> false;
        };
    }
}
