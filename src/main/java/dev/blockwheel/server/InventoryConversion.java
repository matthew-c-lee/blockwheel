package dev.blockwheel.server;

import dev.blockwheel.core.ConversionPlan;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.ManualConversionPlan;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.List;

/** Called only on the server thread, after validating the complete source snapshot. */
public final class InventoryConversion {
    private InventoryConversion() {}
    public record Result(int consumed, int produced, int dropped) {}

    public static ManualConversionPlan<FamilyCatalog.Member> plan(int count, FamilyCatalog.Family family,
            FamilyCatalog.Member source, FamilyCatalog.Member target) {
        return plan(count, family, source, target, 0);
    }

    public static ManualConversionPlan<FamilyCatalog.Member> plan(Inventory inventory, int slot,
            FamilyCatalog.Family family, FamilyCatalog.Member source, FamilyCatalog.Member target) {
        return plan(inventory, slot, family, source, target, true);
    }

    public static ManualConversionPlan<FamilyCatalog.Member> plan(Inventory inventory, int slot,
            FamilyCatalog.Family family, FamilyCatalog.Member source, FamilyCatalog.Member target, boolean consolidate) {
        if (!consolidate) return plan(inventory.getItem(slot).getCount(), family, source, target,
                BlockMaterials.matchingMainCount(inventory, inventory.getItem(slot)));
        var base = family.members().stream().filter(form -> form.shape().base()).findFirst().orElse(source);
        return ManualConversionPlan.calculate(inventory.getItem(slot).getCount(), source, target, base, family.members(), form -> form.shape().units(),
                ManualConversionPlan.outputLimit(target.shape().id(), new ItemStack(item(target)).getMaxStackSize()),
                BlockMaterials.mainSupply(inventory, family.members(), InventoryConversion::item, form -> form.shape().units()), secondary(family));
    }

    private static ManualConversionPlan<FamilyCatalog.Member> plan(int count, FamilyCatalog.Family family,
            FamilyCatalog.Member source, FamilyCatalog.Member target, int available) {
        var base = family.members().stream().filter(form -> form.shape().base()).findFirst().orElse(source);
        return ManualConversionPlan.calculate(count, source, target, base, family.members(), form -> form.shape().units(),
                ManualConversionPlan.outputLimit(target.shape().id(), new ItemStack(item(target)).getMaxStackSize()), available, secondary(family));
    }

    private static FamilyCatalog.Member secondary(FamilyCatalog.Family family) {
        return family.shape(dev.blockwheel.core.WoodFamilies.LOG).isPresent()
                ? family.shape(dev.blockwheel.core.WoodFamilies.PLANKS).orElse(null) : null;
    }

    private static Item item(FamilyCatalog.Member member) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(member.blockId())).asItem();
    }

    public static Result apply(ServerPlayer player, int slot, Item target, ManualConversionPlan<FamilyCatalog.Member> plan) {
        var policy = dev.blockwheel.Blockwheel.BUILDERS.supplyPolicy(player, false, false);
        var transaction = new InventoryTransaction(player.getInventory(), policy.shulkers() && policy.ender() ? player.getEnderChestInventory() : null);
        var result = apply(transaction, stack -> player.drop(stack, false, true), slot, target, plan, InventoryConversion::item);
        player.inventoryMenu.broadcastChanges();
        return result;
    }

    public static Result apply(Inventory inventory, Consumer<ItemStack> drop, int slot, Item target,
            ManualConversionPlan<FamilyCatalog.Member> plan) {
        return apply(inventory, drop, slot, target, plan, InventoryConversion::item);
    }

    public static Result apply(ServerPlayer player, int slot, Item target, ConversionPlan plan) {
        Inventory inventory = player.getInventory();
        Result result = apply(inventory, stack -> player.drop(stack, false, true), slot, target, plan);
        player.inventoryMenu.broadcastChanges();
        return result;
    }

    public static Result apply(Inventory inventory, Consumer<ItemStack> drop, int slot, Item target, ConversionPlan plan) {
        return apply(inventory, drop, slot, target,
                new ManualConversionPlan<Item>(plan.consumed(), plan.produced(), plan.remainder(), List.of()), Function.identity());
    }

    private static <T> Result apply(Inventory inventory, Consumer<ItemStack> drop, int slot, Item target,
            ManualConversionPlan<T> plan, Function<T, Item> item) {
        return apply(new InventoryTransaction(inventory), drop, slot, target, plan, item);
    }

    private static <T> Result apply(InventoryTransaction transaction, Consumer<ItemStack> drop, int slot, Item target,
            ManualConversionPlan<T> plan, Function<T, Item> item) {
        if (plan.produced() < 1) return new Result(0, 0, 0);
        ItemStack source = transaction.get(slot).copy();
        if (Math.addExact(source.getCount(), plan.borrowed()) != plan.consumed() + plan.remainder()) throw new IllegalArgumentException("Source changed");
        withdraw(transaction, source, plan.borrowed(), slot);
        for (var part : plan.withdrawals()) withdraw(transaction, new ItemStack(item.apply(part.form())), part.count(), slot);
        int remaining = plan.produced();
        int limit = Math.min(transaction.limit(new ItemStack(target)), plan.handLimit());
        // The selected shape always takes the hand; physical change belongs in main storage.
        int placed = Math.min(remaining, limit);
        transaction.set(slot, new ItemStack(target, placed));
        remaining -= placed;
        int dropped = 0;
        List<ItemStack> overflow = new ArrayList<>();
        while (remaining > 0) {
            int size = Math.min(remaining, limit);
            ItemStack output = new ItemStack(target, size);
            output = transaction.insert(output, 0, 36, false);
            output = transaction.insert(output, Inventory.SLOT_OFFHAND, Inventory.SLOT_OFFHAND + 1, false);
            output = transaction.insert(output, 0, 36, true);
            if (!output.isEmpty()) {
                dropped += output.getCount();
                overflow.add(output);
            }
            remaining -= size;
        }
        if (plan.remainder() > 0) dropped += storeChange(transaction, source.copyWithCount(plan.remainder()), overflow);
        // Capped radial conversions return physical base-first change to permitted storage only.
        for (var change : plan.stored()) {
            dropped += storeChange(transaction, new ItemStack(item.apply(change.form()), change.count()), overflow);
        }
        if (!transaction.commit()) throw new IllegalStateException("Inventory changed during conversion");
        overflow.forEach(drop);
        return new Result(plan.inputCount(), plan.produced(), dropped);
    }

    private static void withdraw(InventoryTransaction transaction, ItemStack source, int needed, int heldSlot) {
        for (int donor = 9; donor < 36 && needed > 0; donor++) {
            if (donor == heldSlot) continue;
            var stack = transaction.get(donor);
            if (!BlockMaterials.matchingSource(source, stack)) continue;
            int count = Math.min(needed, stack.getCount());
            transaction.remove(donor, count);
            needed -= count;
        }
        if (needed > 0) throw new IllegalArgumentException("Main-inventory source changed");
    }

    private static int storeChange(InventoryTransaction transaction, ItemStack input, List<ItemStack> overflow) {
        var output = transaction.insert(TemplateDeposit.fillRemainder(transaction, input), 9, 36, true);
        int dropped = output.getCount(), limit = transaction.limit(input);
        while (!output.isEmpty()) {
            int size = Math.min(output.getCount(), limit);
            overflow.add(output.copyWithCount(size));
            output.shrink(size);
        }
        return dropped;
    }
}
