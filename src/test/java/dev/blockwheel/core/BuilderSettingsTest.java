package dev.blockwheel.core;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuilderSettingsTest {
    @Test void defaultsAreOffWithEveryFeatureReadyToEnable() {
        var settings = BuilderSettings.defaults();
        assertFalse(settings.enabled());
        for (var feature : BuilderSettings.Feature.values()) {
            assertEquals(feature != BuilderSettings.Feature.SHULKER_SUPPLY && feature != BuilderSettings.Feature.WOOD_TRAPDOORS && feature != BuilderSettings.Feature.EXPERIMENTAL_WOOD,
                    settings.feature(feature)); assertFalse(settings.active(feature));
        }
    }
    @Test void independentFeaturesAndMasterToggleRoundTripThroughPersistenceCodec() {
        var settings = BuilderSettings.defaults().withEnabled(true).withFeature(BuilderSettings.Feature.NORMALIZE, false);
        assertTrue(settings.active(BuilderSettings.Feature.RESTOCK));
        assertFalse(settings.active(BuilderSettings.Feature.NORMALIZE));
        var encoded = BuilderSettings.CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        assertEquals(settings, BuilderSettings.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var disabled = settings.withEnabled(false);
        for (var feature : BuilderSettings.Feature.values()) assertFalse(disabled.active(feature));
        assertEquals(settings.features(), disabled.features());
        assertThrows(IllegalArgumentException.class, () -> new BuilderSettings(true, 1 << BuilderSettings.Feature.values().length));
    }
    @Test void existingPreferencesMigrateWithoutOptingIntoShulkerSupply() {
        var json = com.google.gson.JsonParser.parseString("{\"enabled\":true,\"features\":29}");
        var migrated = BuilderSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(migrated.enabled()); assertFalse(migrated.feature(BuilderSettings.Feature.NORMALIZE));
        assertFalse(migrated.feature(BuilderSettings.Feature.WOOD_TRAPDOORS));
        assertFalse(migrated.feature(BuilderSettings.Feature.EXPERIMENTAL_WOOD));
        assertFalse(migrated.shulkerSupply()); assertTrue(migrated.feature(BuilderSettings.Feature.ENDER_ACCESS));
        assertTrue(migrated.feature(BuilderSettings.Feature.SHULKER_PICK_BLOCK)); assertTrue(migrated.feature(BuilderSettings.Feature.SHULKER_TOOLTIPS));
        var optedIn = migrated.withFeature(BuilderSettings.Feature.SHULKER_SUPPLY, true)
                .withFeature(BuilderSettings.Feature.ENDER_ACCESS, false);
        assertEquals(optedIn, BuilderSettings.CODEC.parse(JsonOps.INSTANCE, BuilderSettings.CODEC.encodeStart(JsonOps.INSTANCE, optedIn).getOrThrow()).getOrThrow());
    }
    @Test void experimentalWoodOptInPreservesOtherSavedFeaturesAndRestockPreferences() {
        var original = BuilderSettings.defaults().withEnabled(true).withFeature(BuilderSettings.Feature.NORMALIZE, false)
                .withRestock(new RestockSettings(9, 17, false));
        var changed = original.withFeature(BuilderSettings.Feature.EXPERIMENTAL_WOOD, true);
        assertEquals(2, changed.catalogView()); assertFalse(original.shapeEnabled(WoodFamilies.LOG)); assertTrue(changed.shapeEnabled(WoodFamilies.LOG));
        assertEquals(changed, BuilderSettings.CODEC.parse(JsonOps.INSTANCE, BuilderSettings.CODEC.encodeStart(JsonOps.INSTANCE, changed).getOrThrow()).getOrThrow());
        assertEquals(original, changed.withFeature(BuilderSettings.Feature.EXPERIMENTAL_WOOD, false));
    }

    @Test void optionalTrapdoorsRoundTripWithoutChangingLegacyOrSupplyBitsAndDoNotRequireMaster() {
        var original = BuilderSettings.defaults().withFeature(BuilderSettings.Feature.ENDER_ACCESS, false);
        var optedIn = original.withFeature(BuilderSettings.Feature.WOOD_TRAPDOORS, true);
        var json = BuilderSettings.CODEC.encodeStart(JsonOps.INSTANCE, optedIn).getOrThrow();
        assertEquals(optedIn, BuilderSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertFalse(optedIn.enabled()); assertTrue(optedIn.shapeEnabled("blockwheel:trapdoor"));
        assertFalse(original.shapeEnabled("blockwheel:trapdoor")); assertTrue(original.shapeEnabled("example:flooring"));
        assertEquals(original, optedIn.withFeature(BuilderSettings.Feature.WOOD_TRAPDOORS, false));
    }
}
