package dev.blockwheel.core;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterfacePreferencesTest {
    @Test void absentPreferencesKeepBothKindsOfTextVisibleAndMigratePartialFiles() {
        assertEquals(InterfacePreferences.DEFAULT, InterfacePreferences.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow());
        var partial = InterfacePreferences.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"hide_button_tooltips\":true}")).getOrThrow();
        assertTrue(partial.hideButtonTooltips()); assertFalse(partial.hideBuilderMessages());
        assertFalse(InterfacePreferences.DEFAULT.hideButtonTooltips()); assertFalse(InterfacePreferences.DEFAULT.hideBuilderMessages());
        assertTrue(partial.showBuilderButton()); assertTrue(partial.showShulkerRefillControls());
    }
    @Test void visibilitySwitchesRoundTripWithoutChangingTheExistingTextPreferences() {
        var original = InterfacePreferences.DEFAULT.withButtonTooltipsHidden(true).withBuilderMessagesHidden(true);
        var hidden = original.withBuilderButtonShown(false).withShulkerControlsShown(false);
        assertTrue(hidden.hideButtonTooltips()); assertTrue(hidden.hideBuilderMessages());
        assertFalse(hidden.showBuilderButton()); assertFalse(hidden.showShulkerRefillControls());
        assertEquals(hidden, InterfacePreferences.CODEC.parse(JsonOps.INSTANCE, InterfacePreferences.CODEC.encodeStart(JsonOps.INSTANCE, hidden).getOrThrow()).getOrThrow());
        assertEquals(original, hidden.withBuilderButtonShown(true).withShulkerControlsShown(true));
    }
    @Test void independentChoicesRoundTripAndUnsupportedVersionsAreRejected() {
        for (boolean tooltips : new boolean[]{false, true}) for (boolean messages : new boolean[]{false, true}) {
            var value = InterfacePreferences.DEFAULT.withButtonTooltipsHidden(tooltips).withBuilderMessagesHidden(messages);
            assertEquals(value, InterfacePreferences.CODEC.parse(JsonOps.INSTANCE,
                    InterfacePreferences.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).getOrThrow());
        }
        assertTrue(InterfacePreferences.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"version\":2}")).isError());
    }
}
