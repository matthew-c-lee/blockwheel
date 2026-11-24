package dev.blockwheel.data;

import dev.blockwheel.core.LogPickDefaults;
import dev.blockwheel.core.PickDefaults;
import dev.blockwheel.core.StatePickDefaults;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.WeatheringCopper;
import java.util.Map;
import java.util.TreeMap;

/** Native weathering/wax graphs identify exact copper subtypes, including shapes and new native variants. */
public final class RegistryPickDefaults {
    private RegistryPickDefaults() {}
    public static Map<String, StatePickDefaults.Copper> copperStates() {
        var waxOff = HoneycombItem.WAX_OFF_BY_BLOCK.get();
        Map<String, StatePickDefaults.Copper> result = new TreeMap<>();
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) continue;
            var raw = waxOff.getOrDefault(block, block);
            if (!(raw instanceof WeatheringCopper weathering)) continue;
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (id.length() > dev.blockwheel.core.PickRules.MAX_ID_LENGTH) continue;
            result.put(id, new StatePickDefaults.Copper(id, BuiltInRegistries.BLOCK.getKey(WeatheringCopper.getFirst(raw)).toString(),
                    weathering.getAge().ordinal(), block != raw));
        }
        return Map.copyOf(result);
    }
    public static PickDefaults create() {
        var ids = BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString).toList();
        return new PickDefaults(LogPickDefaults.create(ids), StatePickDefaults.anvils(ids), StatePickDefaults.copper(copperStates().values()));
    }
}
