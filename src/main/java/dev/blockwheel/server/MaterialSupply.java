package dev.blockwheel.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Storage locations expose the same withdrawable slots to the existing acquisition planner. */
public final class MaterialSupply {
    private MaterialSupply() {}
    public record Policy(boolean shulkers, boolean ender, boolean hotbar, boolean looseEnder) {
        public Policy(boolean shulkers, boolean ender, boolean hotbar) { this(shulkers, ender, hotbar, false); }
        public static Policy mainOnly() { return new Policy(false, false, false); }
        public static Policy pickLoose() { return new Policy(false, false, true); }
    }
    public record Source(boolean remote, int outer, int inner, int reserve, int tier, int order,
                         BlockMaterials.Form form) {
        public ItemStack stack(InventoryTransaction transaction) {
            return inner < 0 ? remote ? transaction.getEnder(outer) : transaction.get(outer) : transaction.box(remote, outer).get(inner);
        }
        public int available(InventoryTransaction transaction) { return Math.max(0, stack(transaction).getCount() - reserve); }
        public void remove(InventoryTransaction transaction, int count) {
            if (count < 0 || count > available(transaction)) throw new IllegalArgumentException("Unavailable material source");
            if (inner >= 0) transaction.box(remote, outer).remove(inner, count);
            else if (remote) transaction.removeEnder(outer, count);
            else transaction.remove(outer, count);
        }
    }
    public record Amounts(long loose, long carried, long ender, long protectedUnits) {
        public long total() { return loose + carried + ender; }
    }

    public static List<Source> collect(InventoryTransaction transaction, BlockMaterials materials,
            BlockMaterials.Material target, Policy policy) {
        return collect(transaction, policy, target.form().item(), stack -> {
            var material = materials.resolve(stack);
            return material != null && material.family() == target.family() ? material.form() : null;
        });
    }

    /** Items without a conversion family are moved unchanged, with exact component matching. */
    public static List<Source> collectExact(InventoryTransaction transaction, ItemStack target, Policy policy) {
        if (target.isEmpty()) return List.of();
        var form = new BlockMaterials.Form("blockwheel:exact", target.getItem(), 1, true);
        return collect(transaction, policy, target.getItem(), stack ->
                ItemStack.isSameItemSameComponents(target, stack) ? form : null);
    }

    private static List<Source> collect(InventoryTransaction transaction, Policy policy,
            net.minecraft.world.item.Item target, Function<ItemStack, BlockMaterials.Form> matching) {
        List<Source> result = new ArrayList<>();
        for (int slot = 9; slot < 36; slot++) addLoose(result, transaction, matching, slot, 0);
        if (policy.hotbar()) for (int slot = 0; slot < 9; slot++) addLoose(result, transaction, matching, slot, 1);
        if (policy.shulkers()) {
            for (int slot = 0; slot < 36; slot++) addBox(result, transaction, matching, false, slot);
            if (policy.ender()) for (int slot = 0; slot < transaction.enderSize(); slot++) {
                if (policy.looseEnder()) {
                    var form = matching.apply(transaction.getEnder(slot));
                    if (form != null) result.add(new Source(true, slot, -1, 0, 3, slot, form));
                }
                addBox(result, transaction, matching, true, slot);
            }
        }
        result.sort(Comparator.comparingInt(Source::tier).thenComparingInt(source ->
                source.form().item() == target ? 0 : source.form().base() ? 1 : 2).thenComparingInt(Source::order));
        return List.copyOf(result);
    }

    private static void addLoose(List<Source> result, InventoryTransaction transaction,
            Function<ItemStack, BlockMaterials.Form> matching, int slot, int tier) {
        var form = matching.apply(transaction.get(slot));
        if (form != null) result.add(new Source(false, slot, -1, 0, tier, slot, form));
    }
    private static void addBox(List<Source> result, InventoryTransaction transaction,
            Function<ItemStack, BlockMaterials.Form> matching, boolean remote, int slot) {
        var box = transaction.box(remote, slot);
        if (box == null) return;
        int tier = box.reserve() == 0 ? remote ? 4 : 2 : remote ? 6 : 5;
        for (int inner = 0; inner < 27; inner++) {
            var form = matching.apply(box.get(inner));
            if (form != null) {
                result.add(new Source(remote, slot, inner, box.reserve(), tier, slot * 27 + inner, form));
            }
        }
    }

    public static Amounts amounts(InventoryTransaction transaction, List<Source> sources) {
        long loose = 0, carried = 0, ender = 0, protectedUnits = 0;
        for (var source : sources) {
            long units = (long) source.available(transaction) * source.form().units();
            if (source.remote()) ender += units; else if (source.inner() < 0) loose += units; else carried += units;
            protectedUnits += (long) Math.min(source.reserve(), source.stack(transaction).getCount()) * source.form().units();
        }
        return new Amounts(loose, carried, ender, protectedUnits);
    }

    /** Conservation counts actual physical items, including protected reserves and moved outer boxes. */
    public static long totalUnits(InventoryTransaction transaction, BlockMaterials materials, BlockMaterials.Family family, boolean original) {
        if (!original) transaction.flushContainers();
        long total = 0;
        for (int slot = 0; slot < 36; slot++) total += stackUnits(original ? transaction.before(slot) : transaction.get(slot), materials, family);
        // The offhand is a restock destination, but remains excluded from supply traversal.
        total += stackUnits(original ? transaction.before(Inventory.SLOT_OFFHAND) : transaction.get(Inventory.SLOT_OFFHAND), materials, family);
        for (int slot = 0; slot < transaction.enderSize(); slot++) total += stackUnits(original ? transaction.beforeEnder(slot) : transaction.getEnder(slot), materials, family);
        return total;
    }
    private static long stackUnits(ItemStack stack, BlockMaterials materials, BlockMaterials.Family family) {
        var material = materials.resolve(stack);
        if (material != null && material.family() == family) return (long) stack.getCount() * material.form().units();
        if (!InventoryTransaction.supportedShulker(stack)) return 0;
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().mapToLong(content -> {
            var inner = materials.resolve(content);
            return inner != null && inner.family() == family ? (long) content.getCount() * inner.form().units() : 0;
        }).sum();
    }
}
