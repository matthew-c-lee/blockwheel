package dev.blockwheel.core;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestockSettingsTest {
    @Test void defaultsAreBelowSixAndThirtyTwoAdditionalWithExistingNonBlockSupplyAllowed() {
        assertEquals(new RestockSettings(6, 32, true), BuilderSettings.defaults().restock());
    }
    @Test void existingWorldPreferencesKeepTheirFeatureBitsAndReceiveTheNewDefaults() {
        var old = JsonParser.parseString("{\"enabled\":true,\"features\":29,\"supply_features\":11}");
        var settings = BuilderSettings.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(29 | 11 << 5, settings.features()); assertTrue(settings.enabled());
        assertEquals(RestockSettings.DEFAULT, settings.restock());
    }
    @Test void customRefillPreferencesRoundTripAndSurviveIndependentFeatureAndMasterChanges() {
        var policy = new RestockSettings(1, 7, false);
        var settings = BuilderSettings.defaults().withRestock(policy).withEnabled(true)
                .withFeature(BuilderSettings.Feature.NORMALIZE, false);
        var json = BuilderSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, BuilderSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(policy, settings.withEnabled(false).restock());
        assertEquals(policy, settings.withFeature(BuilderSettings.Feature.SHULKER_SUPPLY, true).restock());
    }
    @Test void invalidSavedAndRequestedBoundsCannotEnterThePlanner() {
        for (int value : new int[]{-1, 0, 100, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RestockSettings(value, 32, true));
            assertThrows(IllegalArgumentException.class, () -> new RestockSettings(6, value, true));
            assertTrue(RestockSettings.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"below\":" + value + "}")).error().isPresent());
        }
        assertEquals(99, new RestockSettings(99, 99, false).amount());
    }
}
