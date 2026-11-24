package dev.blockwheel.core;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PickRulesTest {
    @Test void logDefaultsUseExistingSameSpeciesFormsInDeliberateOrder() {
        var defaults = LogPickDefaults.create(List.of("minecraft:oak_log", "minecraft:oak_wood", "minecraft:stripped_oak_log", "minecraft:stripped_oak_wood",
                "example:cedar_log", "example:stripped_cedar_log", "minecraft:crimson_stem", "minecraft:crimson_hyphae",
                "minecraft:stripped_crimson_stem", "minecraft:stripped_crimson_hyphae", "minecraft:bamboo_block", "minecraft:stripped_bamboo_block"));
        assertEquals(List.of("minecraft:stripped_oak_log", "minecraft:oak_wood", "minecraft:oak_log"), defaults.get("minecraft:stripped_oak_wood"));
        assertEquals(List.of("minecraft:oak_log"), defaults.get("minecraft:oak_wood"));
        assertEquals(List.of("example:cedar_log"), defaults.get("example:stripped_cedar_log"));
        assertEquals(List.of("minecraft:stripped_crimson_stem", "minecraft:crimson_hyphae", "minecraft:crimson_stem"), defaults.get("minecraft:stripped_crimson_hyphae"));
        assertEquals(List.of("minecraft:bamboo_block"), defaults.get("minecraft:stripped_bamboo_block"));
        assertFalse(defaults.containsKey("minecraft:oak_log"));
    }
    @Test void missingFormsAreNotInventedOrBorrowedFromOtherNamespaces() {
        var defaults = LogPickDefaults.create(List.of("minecraft:oak_log", "example:stripped_oak_wood", "example:stripped_oak_log", "minecraft:stripped_birch_log"));
        assertTrue(defaults.isEmpty());
    }
    @Test void orderedOverridesAndAnEmptyOverrideReplaceDefaults() {
        var defaults = Map.of("minecraft:stone", List.of("minecraft:cobblestone"));
        var rules = PickRules.DEFAULT.put(new PickRules.Rule("minecraft:stone", List.of("minecraft:oak_log", "minecraft:birch_log")));
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log"), rules.effective(defaults).get("minecraft:stone"));
        assertEquals(List.of(), rules.put(new PickRules.Rule("minecraft:stone", List.of())).effective(defaults).get("minecraft:stone"));
        assertEquals(defaults, rules.withoutOverride("minecraft:stone").effective(defaults));
        assertTrue(PickRules.DEFAULT.withLogDefaults(false).effective(defaults).isEmpty());
    }
    @Test void fileCodecRoundTripsFlagsAndExplicitPriorityWithoutFollowingChains() {
        var rules = new PickRules(false, true, List.of(new PickRules.Rule("minecraft:stone", List.of("minecraft:cobblestone")),
                new PickRules.Rule("minecraft:cobblestone", List.of("minecraft:oak_log"))));
        var json = PickRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow();
        assertEquals(rules, PickRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(List.of("minecraft:cobblestone"), rules.effective(Map.of()).get("minecraft:stone"));
    }
    @Test void listsAreImmutableAndInvalidRulesCannotBeSynchronized() {
        var values = new ArrayList<>(List.of("minecraft:oak_log")); var rule = new PickRules.Rule("minecraft:stone", values); values.clear();
        assertEquals(1, rule.fallbacks().size()); assertThrows(UnsupportedOperationException.class, () -> rule.fallbacks().clear());
        assertThrows(IllegalArgumentException.class, () -> new PickRules.Rule("minecraft:stone", List.of("minecraft:stone")));
        assertThrows(IllegalArgumentException.class, () -> new PickRules.Rule("minecraft:stone", List.of("minecraft:oak_log", "minecraft:oak_log")));
        assertThrows(IllegalArgumentException.class, () -> new PickRules.Rule("bad id", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PickRules(true, true, List.of(rule, rule)));
        var large = new ArrayList<PickRules.Rule>();
        for (int index = 0; index < 200; index++) large.add(new PickRules.Rule("example:" + "a".repeat(150) + index, List.of("minecraft:oak_log")));
        assertThrows(IllegalArgumentException.class, () -> new PickRules(true, false, large));
    }
}
