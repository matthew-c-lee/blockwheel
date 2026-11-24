package dev.blockwheel.server;

import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.MaterialComposition;
import dev.blockwheel.core.MaterialWithdrawal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** An item projection of the existing authoritative catalog, restricted to reliable base families. */
public final class BlockMaterials {
    public record Form(String shapeId, Item item, int units, boolean base) {}
    public record Family(String id, Form base, List<Form> forms, MaterialComposition<Form> composition) {
        public boolean logBased() { return base.shapeId().equals(dev.blockwheel.core.WoodFamilies.LOG); }
        /** The original full-block form remains the secondary storage form when logs are enabled. */
        public Form plankBase() {
            return logBased() ? forms.stream().filter(form -> form.shapeId().equals(dev.blockwheel.core.WoodFamilies.PLANKS))
                    .findFirst().orElse(null) : null;
        }
    }
    public record Material(Family family, Form form) {}
    private final Map<Item, Material> byItem = new HashMap<>();
    private final List<Family> families;

    public BlockMaterials(FamilyCatalog catalog) {
        List<Family> result = new ArrayList<>();
        for (var family : dev.blockwheel.data.FamilySafety.apply(catalog).families()) {
            List<Form> forms = family.members().stream().map(member -> new Form(member.shape().id(),
                    BuiltInRegistries.BLOCK.getValue(Identifier.parse(member.blockId())).asItem(),
                    member.shape().units(), member.shape().base())).toList();
            var bases = forms.stream().filter(Form::base).toList();
            if (bases.size() != 1 || forms.stream().anyMatch(form -> form.item() == Items.AIR)) continue;
            Form base = bases.getFirst();
            var secondary = base.shapeId().equals(dev.blockwheel.core.WoodFamilies.LOG) ? forms.stream()
                    .filter(form -> form.shapeId().equals(dev.blockwheel.core.WoodFamilies.PLANKS)).findFirst().orElse(null) : null;
            Family material = new Family(family.id(), base, forms, new MaterialComposition<>(base, secondary, forms, Form::units));
            result.add(material);
            for (Form form : forms) byItem.put(form.item(), new Material(material, form));
        }
        families = List.copyOf(result);
    }

    public List<Family> families() { return families; }
    public Material resolve(ItemStack stack) { return ordinary(stack) ? byItem.get(stack.getItem()) : null; }
    public Material resolve(Item item) { return byItem.get(item); }
    public static boolean ordinary(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getComponentsPatch().isEmpty();
    }

    public static boolean matchingSource(ItemStack source, ItemStack candidate) {
        return ordinary(source) && ordinary(candidate) && ItemStack.isSameItemSameComponents(source, candidate);
    }

    /** Shared read-only source count for radial previews and authoritative conversion planning. */
    public static int matchingMainCount(Inventory inventory, ItemStack source) {
        int count = 0;
        for (int slot = 9; slot < 36; slot++) {
            var stack = inventory.getItem(slot);
            if (matchingSource(source, stack)) count = Math.addExact(count, stack.getCount());
        }
        return count;
    }

    /** The client preview and server planner see the same ordinary, loose main-storage family sources. */
    public static <T> List<MaterialWithdrawal.Supply<T>> mainSupply(Inventory inventory, List<T> forms,
            Function<T, Item> item, ToIntFunction<T> units) {
        List<MaterialWithdrawal.Supply<T>> result = new ArrayList<>();
        for (int slot = 9; slot < 36; slot++) {
            var stack = inventory.getItem(slot);
            if (!ordinary(stack)) continue;
            for (var form : forms) if (stack.is(item.apply(form))) {
                result.add(new MaterialWithdrawal.Supply<>(form, stack.getCount(), units.applyAsInt(form)));
                break;
            }
        }
        return List.copyOf(result);
    }
}
