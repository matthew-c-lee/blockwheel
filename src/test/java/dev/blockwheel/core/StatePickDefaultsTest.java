package dev.blockwheel.core;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StatePickDefaultsTest {
    @Test void anvilStatesPreferNearestThenMoreDamagedAndNeverIncludeTheRequestedState() {
        var ids = List.of("minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil");
        var rules = StatePickDefaults.anvils(ids);
        assertEquals(List.of(ids.get(2), ids.get(0)), rules.get(ids.get(1)));
        assertEquals(List.of(ids.get(1), ids.get(2)), rules.get(ids.get(0)));
        assertEquals(List.of(ids.get(1), ids.get(0)), rules.get(ids.get(2)));
        assertTrue(StatePickDefaults.anvils(List.of(ids.get(0))).isEmpty());
    }
    private List<StatePickDefaults.Copper> states(String subtype) {
        var result = new ArrayList<StatePickDefaults.Copper>();
        for (boolean wax : new boolean[]{false, true}) for (int age = 0; age < 4; age++)
            result.add(new StatePickDefaults.Copper(subtype + "_" + age + "_" + wax, subtype, age, wax));
        return result;
    }
    @Test void copperOrdersWaxSwapThenSameWaxNearestThenOtherWaxWithLessOxidizedTies() {
        var definitions = states("test:cut_stairs"); var rules = StatePickDefaults.copper(definitions);
        assertEquals(List.of("test:cut_stairs_1_true", "test:cut_stairs_0_false", "test:cut_stairs_2_false", "test:cut_stairs_3_false",
                "test:cut_stairs_0_true", "test:cut_stairs_2_true", "test:cut_stairs_3_true"), rules.get("test:cut_stairs_1_false"));
        assertEquals("test:cut_stairs_2_false", rules.get("test:cut_stairs_2_true").getFirst());
    }
    @Test void everyCopperStateStaysInItsSubtypeAndHasUniqueNonselfAlternatives() {
        var all = new ArrayList<>(states("test:grate")); all.addAll(states("test:full"));
        var rules = StatePickDefaults.copper(all);
        for (var requested : all) {
            var choices = rules.get(requested.id()); assertEquals(7, choices.size()); assertEquals(7, java.util.Set.copyOf(choices).size());
            assertFalse(choices.contains(requested.id())); assertTrue(choices.stream().allMatch(id -> id.startsWith(requested.subtype() + "_")));
        }
    }
    @Test void oldJsonKeepsExistingChoicesAndNewDefaultGroupsCanBeDisabledIndependently() {
        var migrated = PickRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"enabled\":true,\"include_log_defaults\":false,\"rules\":[]}")).getOrThrow();
        assertFalse(migrated.includeLogDefaults()); assertTrue(migrated.includeAnvilDefaults()); assertTrue(migrated.includeCopperDefaults());
        var defaults = new PickDefaults(Map.of("test:log", List.of("test:wood")), Map.of("test:anvil", List.of("test:chipped")), Map.of("test:copper", List.of("test:waxed")));
        assertEquals(2, migrated.effective(defaults).size());
        var rules = migrated.withAnvilDefaults(false).withCopperDefaults(false).put(new PickRules.Rule("test:anvil", List.of("test:custom")));
        assertEquals(Map.of("test:anvil", List.of("test:custom")), rules.effective(defaults));
        assertEquals(rules, PickRules.CODEC.parse(JsonOps.INSTANCE, PickRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow()).getOrThrow());
    }
}
