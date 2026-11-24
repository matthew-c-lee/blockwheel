package dev.blockwheel.server;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.HashMap;
import java.util.Map;

/** Copy, plan, validate and commit. No live inventory is touched while calculating capacity. */
public final class InventoryTransaction {
    private final Inventory inventory;
    private final ItemStack[] before;
    private final ItemStack[] stacks;
    private final int selected;
    private final Container ender;
    private final ItemStack[] beforeEnder, enderStacks;
    private record BoxKey(boolean ender, int slot) {}
    private final Map<BoxKey, Box> boxes = new HashMap<>();

    public InventoryTransaction(Inventory inventory) {
        this(inventory, null);
    }
    public InventoryTransaction(Inventory inventory, Container ender) {
        this.inventory = inventory;
        this.ender = ender;
        selected = inventory.getSelectedSlot();
        before = new ItemStack[inventory.getContainerSize()];
        stacks = new ItemStack[before.length];
        for (int i = 0; i < before.length; i++) {
            before[i] = inventory.getItem(i).copy();
            stacks[i] = before[i].copy();
        }
        beforeEnder = new ItemStack[ender == null ? 0 : ender.getContainerSize()];
        enderStacks = new ItemStack[beforeEnder.length];
        for (int i = 0; i < beforeEnder.length; i++) {
            beforeEnder[i] = ender.getItem(i).copy(); enderStacks[i] = beforeEnder[i].copy();
        }
    }
    private InventoryTransaction(InventoryTransaction other) {
        inventory = other.inventory;
        before = other.before;
        selected = other.selected;
        ender = other.ender; beforeEnder = other.beforeEnder;
        stacks = new ItemStack[other.stacks.length];
        for (int i = 0; i < stacks.length; i++) stacks[i] = other.stacks[i].copy();
        enderStacks = new ItemStack[other.enderStacks.length];
        for (int i = 0; i < enderStacks.length; i++) enderStacks[i] = other.enderStacks[i].copy();
        other.boxes.forEach((key, box) -> boxes.put(key, new Box(box)));
    }
    public InventoryTransaction copy() { return new InventoryTransaction(this); }
    public ItemStack get(int slot) { return stacks[slot]; }
    public ItemStack before(int slot) { return before[slot].copy(); }
    public int enderSize() { return enderStacks.length; }
    public ItemStack getEnder(int slot) { return enderStacks[slot]; }
    public ItemStack beforeEnder(int slot) { return beforeEnder[slot].copy(); }
    public void set(int slot, ItemStack stack) { stacks[slot] = stack.copy(); }
    public void remove(int slot, int count) {
        if (count < 0 || count > stacks[slot].getCount()) throw new IllegalArgumentException("Invalid source amount");
        stacks[slot].shrink(count);
    }
    public void removeEnder(int slot, int count) {
        if (count < 0 || count > enderStacks[slot].getCount()) throw new IllegalArgumentException("Invalid ender source amount");
        enderStacks[slot].shrink(count);
    }
    public int limit(ItemStack stack) { return Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize(stack)); }

    public static boolean supportedShulker(ItemStack stack) {
        return stack.getCount() == 1 && stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof ShulkerBoxBlock && !stack.has(DataComponents.LOCK)
                && !stack.has(DataComponents.CONTAINER_LOOT)
                && stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).allItemsCopyStream().limit(28).count() <= 27;
    }

    /** One shadow copy per box. Only source-slot counts may be changed. */
    public static final class Box {
        private final NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        private final int reserve;
        private boolean dirty;
        private Box(ItemStack stack) {
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
            reserve = stack.has(DataComponents.CUSTOM_NAME) ? 1 : 0;
        }
        private Box(Box other) {
            reserve = other.reserve; dirty = other.dirty;
            for (int i = 0; i < 27; i++) items.set(i, other.items.get(i).copy());
        }
        public ItemStack get(int slot) { return items.get(slot); }
        public int reserve() { return reserve; }
        public int space(int slot) {
            var stack = items.get(slot);
            return stack.isEmpty() || !stack.getItem().canFitInsideContainerItems() ? 0
                    : Math.max(0, Math.min(stack.getMaxStackSize(), net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE) - stack.getCount());
        }
        public void remove(int slot, int count) {
            var stack = items.get(slot);
            if (count < 0 || count > Math.max(0, stack.getCount() - reserve)) throw new IllegalArgumentException("Protected shulker reserve");
            if (count > 0) { stack.shrink(count); dirty = true; }
        }
        /** Deposit only into an existing identical stack; never create or move a template. */
        public int fillExisting(int slot, ItemStack input) {
            var stack = items.get(slot);
            if (stack.isEmpty() || input.isEmpty() || !input.getItem().canFitInsideContainerItems()
                    || !ItemStack.isSameItemSameComponents(stack, input)) return 0;
            int count = Math.min(input.getCount(), space(slot));
            if (count > 0) { stack.grow(count); dirty = true; }
            return count;
        }
    }

    public Box box(boolean remote, int slot) {
        var key = new BoxKey(remote, slot);
        var existing = boxes.get(key);
        if (existing != null) return existing;
        var stack = remote ? getEnder(slot) : get(slot);
        if (!supportedShulker(stack)) return null;
        var box = new Box(stack); boxes.put(key, box); return box;
    }

    /** Seal edits before a pick-block operation moves an outer shulker to another slot. */
    public void flushContainers() {
        boxes.forEach((key, box) -> {
            if (!box.dirty) return;
            ItemStack outer = (key.ender() ? getEnder(key.slot()) : get(key.slot())).copy();
            outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(box.items));
            if (key.ender()) enderStacks[key.slot()] = outer; else stacks[key.slot()] = outer;
        });
        boxes.clear();
    }

    /** Merge first, then use empty slots. The caller explicitly chooses the inventory domain. */
    public ItemStack insert(ItemStack input, int first, int end, boolean useEmpty) {
        ItemStack output = input.copy();
        for (int slot = first; slot < end && !output.isEmpty(); slot++) {
            ItemStack existing = stacks[slot];
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, output)) continue;
            int count = Math.min(output.getCount(), Math.max(0, limit(existing) - existing.getCount()));
            existing.grow(count);
            output.shrink(count);
        }
        if (useEmpty) for (int slot = first; slot < end && !output.isEmpty(); slot++) {
            if (!stacks[slot].isEmpty()) continue;
            int count = Math.min(output.getCount(), limit(output));
            stacks[slot] = output.copyWithCount(count);
            output.shrink(count);
        }
        return output;
    }

    /** Honor a completed inventory move's destination before ordinary merge/empty-slot routing. */
    public ItemStack insertPreferred(ItemStack input, java.util.List<Integer> preferred, int first, int end) {
        var output = input.copy();
        for (int slot : preferred) {
            if (slot < first || slot >= end || output.isEmpty()) continue;
            var existing = stacks[slot];
            if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, output)) continue;
            int count = Math.min(output.getCount(), Math.max(0, limit(output) - existing.getCount()));
            if (existing.isEmpty()) stacks[slot] = output.copyWithCount(count); else existing.grow(count);
            output.shrink(count);
        }
        return insert(output, first, end, true);
    }

    public boolean changed() {
        flushContainers();
        for (int i = 0; i < stacks.length; i++) if (!ItemStack.matches(before[i], stacks[i])) return true;
        for (int i = 0; i < enderStacks.length; i++) if (!ItemStack.matches(beforeEnder[i], enderStacks[i])) return true;
        return false;
    }
    public boolean commit() {
        flushContainers();
        if (inventory.getSelectedSlot() != selected) return false;
        for (int i = 0; i < before.length; i++) if (!ItemStack.matches(before[i], inventory.getItem(i))) return false;
        for (int i = 0; i < beforeEnder.length; i++) if (!ItemStack.matches(beforeEnder[i], ender.getItem(i))) return false;
        for (int i = 0; i < stacks.length; i++) if (!ItemStack.matches(before[i], stacks[i])) inventory.setItem(i, stacks[i].copy());
        for (int i = 0; i < enderStacks.length; i++) if (!ItemStack.matches(beforeEnder[i], enderStacks[i])) ender.setItem(i, enderStacks[i].copy());
        if (ender != null && changed()) ender.setChanged();
        if (changed()) inventory.setChanged();
        return true;
    }
}
