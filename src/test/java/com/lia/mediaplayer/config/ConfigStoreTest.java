package com.lia.mediaplayer.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lia.mediaplayer.api.config.ConfigOption;
import com.lia.mediaplayer.gui.ThemeName;
import com.lia.mediaplayer.source.FilterMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code config.json} format.
 *
 * <p>This file is the one a player is most likely to open in an editor, it outlives the
 * version of the mod that wrote it, and a single bad value in it reaches every setting
 * at once. So the contract worth pinning is not "a good file loads" but "nothing in a
 * file can stop the mod starting or leave an option outside its own range": an entry of
 * the wrong type, a slider index from a build with a different set of steps, a key for
 * an addon that is not installed, or a file that is not JSON at all.</p>
 *
 * <p>The built-in options are {@code static final} singletons — one set for the whole
 * process — so every test starts by putting them back to their defaults rather than
 * inheriting whatever the previous one left behind.</p>
 */
class ConfigStoreTest {

    private ConfigStore store;

    @BeforeEach
    void reset() {
        store = new ConfigStore();
        for (ConfigOption<?> option : store.getAllOptions()) {
            option.resetToDefault();
        }
    }

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    void roundTripsEveryRegisteredOption() {
        ConfigStore.MAX_VIDEO_WINDOWS.setValue(7);
        ConfigStore.MAX_IMAGE_CACHE_MEGABYTES.setValue(512);
        ConfigStore.THEME.setValue(ThemeName.CONTRAST);
        ConfigStore.LINK_FILTER_MODE.setValue(FilterMode.BLOCKLIST);
        ConfigStore.BLOCKED_DOMAINS.setValue("example.com, tracker.test");
        ConfigStore.AUTO_UPDATE_TOOLS.setValue(false);

        JsonObject saved = store.toJson();
        for (ConfigOption<?> option : store.getAllOptions()) {
            option.resetToDefault();
        }
        store.applyJson(saved);

        assertEquals(7, ConfigStore.MAX_VIDEO_WINDOWS.getValue());
        assertEquals(512, ConfigStore.MAX_IMAGE_CACHE_MEGABYTES.getValue());
        assertEquals(ThemeName.CONTRAST, ConfigStore.THEME.getValue());
        assertEquals(FilterMode.BLOCKLIST, ConfigStore.LINK_FILTER_MODE.getValue());
        assertEquals("example.com, tracker.test", ConfigStore.BLOCKED_DOMAINS.getValue());
        assertEquals(false, ConfigStore.AUTO_UPDATE_TOOLS.getValue());
    }

    @Test
    void writesEveryRegisteredOptionUnderItsOwnId() {
        JsonObject saved = store.toJson();
        for (ConfigOption<?> option : store.getAllOptions()) {
            assertTrue(saved.has(option.getId()), "missing from config.json: " + option.getId());
        }
    }

    // ------------------------------------------------------------------
    // Values a hand-edited file can carry
    // ------------------------------------------------------------------

    @Test
    void clampsASliderValueFromOutsideItsRange() {
        JsonObject json = new JsonObject();
        json.add(ConfigStore.MAX_VIDEO_WINDOWS.getId(), new JsonPrimitive(9999));
        store.applyJson(json);
        assertEquals(10, ConfigStore.MAX_VIDEO_WINDOWS.getValue());

        json.add(ConfigStore.MAX_VIDEO_WINDOWS.getId(), new JsonPrimitive(-4));
        store.applyJson(json);
        assertEquals(1, ConfigStore.MAX_VIDEO_WINDOWS.getValue());
    }

    @Test
    void clampsAResolutionStepFromABuildWithMoreSteps() {
        // The stored value is an index into RESOLUTION_HEIGHTS, so a file written by a
        // version with a longer list must not index off the end of this one's.
        JsonObject json = new JsonObject();
        json.add(ConfigStore.VIDEO_RESOLUTION.getId(), new JsonPrimitive(42));
        store.applyJson(json);

        assertEquals(ConfigStore.RESOLUTION_HEIGHTS.length - 1, ConfigStore.VIDEO_RESOLUTION.getValue());
        assertEquals(1280, store.videoMaxWidth());
        assertEquals(720, store.videoMaxHeight());
    }

    @Test
    void keepsTheDefaultWhenAValueHasTheWrongType() {
        JsonObject json = new JsonObject();
        json.add(ConfigStore.MAX_VIDEO_WINDOWS.getId(), new JsonPrimitive("four"));
        json.add(ConfigStore.AUTO_UPDATE_TOOLS.getId(), new JsonPrimitive(3));
        json.add(ConfigStore.THEME.getId(), new JsonPrimitive(true));

        store.applyJson(json);

        assertEquals(ConfigStore.MAX_VIDEO_WINDOWS.getDefaultValue(), ConfigStore.MAX_VIDEO_WINDOWS.getValue());
        assertEquals(ConfigStore.AUTO_UPDATE_TOOLS.getDefaultValue(), ConfigStore.AUTO_UPDATE_TOOLS.getValue());
        assertEquals(ConfigStore.THEME.getDefaultValue(), ConfigStore.THEME.getValue());
    }

    @Test
    void keepsTheDefaultForAnEnumConstantThatNoLongerExists() {
        JsonObject json = new JsonObject();
        json.add(ConfigStore.THEME.getId(), new JsonPrimitive("SEPIA"));
        store.applyJson(json);
        assertEquals(ConfigStore.THEME.getDefaultValue(), ConfigStore.THEME.getValue());
    }

    @Test
    void ignoresAnOptionNobodyRegistered() {
        // What an addon's setting looks like when the addon is not installed.
        JsonObject json = new JsonObject();
        json.add("someaddon:its_own_option", new JsonPrimitive(1));
        json.add(ConfigStore.MAX_GIF_FRAMES.getId(), new JsonPrimitive(64));

        store.applyJson(json);

        assertEquals(64, ConfigStore.MAX_GIF_FRAMES.getValue());
    }

    // ------------------------------------------------------------------
    // Files that are not documents
    // ------------------------------------------------------------------

    @Test
    void survivesAFileThatIsNotJson() {
        assertNull(ConfigStore.parse("{not json at all", null));
        assertNull(ConfigStore.parse("", null));
        assertNull(ConfigStore.parse(null, null));
    }

    @Test
    void survivesJsonThatIsNotAnObject() {
        assertNull(ConfigStore.parse("[1, 2, 3]", null));
        assertNull(ConfigStore.parse("\"a string\"", null));
        assertNull(ConfigStore.parse("null", null));
    }

    @Test
    void readsBackAnEmptyDocument() {
        JsonObject empty = ConfigStore.parse("{}", null);
        assertNotNull(empty);
        store.applyJson(empty);
        assertTrue(ConfigStore.MAX_VIDEO_WINDOWS.isDefault());
    }

    @Test
    void applyingNothingChangesNothing() {
        ConfigStore.MAX_GIF_FRAMES.setValue(500);
        store.applyJson(null);
        assertEquals(500, ConfigStore.MAX_GIF_FRAMES.getValue());
    }
}
