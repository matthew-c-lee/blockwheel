package dev.blockwheel.server;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Explicit filling of occupied named templates; experimental log destinations precede exact secondary slots. */
public final class TemplateDeposit {
    private TemplateDeposit() {}
    private record Target(boolean remote, int outer, int inner) {}

    public static BuilderInventory.Plan plan(Inventory inventory, Container ender) {
        return plan(inventory, ender, null, false);
    }

    public static BuilderInventory.Plan plan(Inventory inventory, Container ender, BuilderInventory converter, boolean convert) {
        return plan(inventory, ender, converter, convert, false);
    }
    /** Experimental wood refilling also supports carried named templates through the same explicit action. */
    public static BuilderInventory.Plan plan(Inventory inventory, Container ender, BuilderInventory converter, boolean convert, boolean carried) {
        return plan(inventory, ender, converter, convert, carried, false);
    }
    public static BuilderInventory.Plan plan(Inventory inventory, Container ender, BuilderInventory converter, boolean convert, boolean carried, boolean includeHotbar) {
        var transaction = new InventoryTransaction(inventory, ender);
        var targets = targets(transaction, carried);
        int moved = 0;
        // Existing log slots get first refusal, even before a matching stairs/planks secondary template.
        if (convert && converter != null) for (var target : targets) {
            if (!converter.logTemplate(transaction, target.remote(), target.outer(), target.inner())) continue;
            var converted = converter.fillTemplate(transaction, target.remote(), target.outer(), target.inner(), includeHotbar);
            if (converted != null) { transaction = converted.transaction(); moved = Math.addExact(moved, converted.count()); }
        }
        for (var target : targets) {
                var box = transaction.box(target.remote(), target.outer());
                // A short press uses main storage; a completed hold then adds hotbar and offhand.
                for (int index = 0; index < (includeHotbar ? 37 : 27); index++) {
                    int slot = index < 36 ? (index + 9) % 36 : Inventory.SLOT_OFFHAND;
                    int count = box.fillExisting(target.inner(), transaction.get(slot));
                    if (count == 0) continue;
                    transaction.remove(slot, count); moved = Math.addExact(moved, count);
                }
        }
        if (convert && converter != null) {
            for (var target : targets) {
                if (converter.logTemplate(transaction, target.remote(), target.outer(), target.inner())) continue;
                var converted = converter.fillTemplate(transaction, target.remote(), target.outer(), target.inner(), includeHotbar);
                if (converted != null) { transaction = converted.transaction(); moved = Math.addExact(moved, converted.count()); }
            }
        }
        return new BuilderInventory.Plan(transaction, moved);
    }

    private static java.util.List<Target> targets(InventoryTransaction transaction, boolean carried) {
        var targets = new java.util.ArrayList<Target>();
        for (boolean remote : carried ? new boolean[]{false, true} : new boolean[]{true}) {
            for (int outer = 0; outer < (remote ? transaction.enderSize() : 36); outer++) {
                var box = transaction.box(remote, outer);
                if (box == null || box.reserve() != 1) continue;
                for (int inner = 0; inner < 27; inner++) if (!box.get(inner).isEmpty()) targets.add(new Target(remote, outer, inner));
            }
        }
        return java.util.List.copyOf(targets);
    }

    /** Route only this transaction's physical change, without sweeping other inventory or filling empty templates. */
    public static ItemStack fillRemainder(InventoryTransaction transaction, ItemStack input) {
        var remainder = input.copy();
        for (var target : targets(transaction, false)) {
            int accepted = transaction.box(true, target.outer()).fillExisting(target.inner(), remainder);
            remainder.shrink(accepted);
            if (remainder.isEmpty()) break;
        }
        return remainder;
    }
}
