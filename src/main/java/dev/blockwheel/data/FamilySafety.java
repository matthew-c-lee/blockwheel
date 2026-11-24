package dev.blockwheel.data;

import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.WoodFamilies;
import dev.blockwheel.mixin.AxeItemAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AnvilBlock;
import java.util.HashSet;

/** State-selection fallbacks never grant permission to repair, strip, wax or oxidize materials. */
public final class FamilySafety {
    private FamilySafety() {}
    public static boolean excluded(String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        String path = Identifier.parse(id).getPath();
        return WoodFamilies.stripped(id) || block instanceof AnvilBlock || path.endsWith("_shelf")
                || AxeItemAccessor.blockwheel$strippables().containsValue(block);
    }
    public static FamilyCatalog apply(FamilyCatalog catalog) {
        var copper = RegistryPickDefaults.copperStates();
        return catalog.filterMembers(member -> !excluded(member.blockId())).filterFamilies(family -> {
            var finishes = new HashSet<String>(); boolean anyCopper = false, other = false;
            for (var member : family.members()) {
                var state = copper.get(member.blockId());
                if (state == null) other = true;
                else { anyCopper = true; finishes.add(state.oxidation() + ":" + state.waxed()); }
            }
            return !anyCopper || !other && finishes.size() == 1;
        });
    }
}
