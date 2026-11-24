package dev.blockwheel;

import dev.blockwheel.server.InventoryConversion;

import dev.blockwheel.core.ConversionPlan;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;


public class InventoryRoutingTests {
    private Inventory inventory() { return new Inventory(null, new EntityEquipment()); }
    private void fill(Inventory inventory) { for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.DIRT, 64)); }

    @GameTest public void fillsHeldSlotAndThenAnotherSlotWithoutOversizedStacks(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.STONE, 64));
        var drops = new ArrayList<ItemStack>();
        var result = InventoryConversion.apply(inv, drops::add, 0, Items.STONE_SLAB, ConversionPlan.calculate(64, 2, 1));
        assertEquals(128, result.produced()); assertEquals(0, result.dropped());
        assertEquals(64, inv.getItem(0).getCount()); assertTrue(inv.getItem(0).is(Items.STONE_SLAB));
        assertEquals(64, inv.getItem(1).getCount()); assertTrue(drops.isEmpty());
        test.succeed();
    }
    @GameTest public void dropsExactlyTheOverflowFromAFullInventory(GameTestHelper test) {
        var inv = inventory(); fill(inv); inv.setItem(0, new ItemStack(Items.STONE, 64));
        var drops = new ArrayList<ItemStack>();
        var result = InventoryConversion.apply(inv, drops::add, 0, Items.STONE_SLAB, ConversionPlan.calculate(64, 2, 1));
        assertEquals(64, result.dropped()); assertEquals(1, drops.size());
        assertEquals(64, drops.getFirst().getCount()); assertTrue(drops.getFirst().is(Items.STONE_SLAB));
        assertEquals(64, inv.getItem(0).getCount()); assertTrue(inv.getItem(1).is(Items.DIRT));
        test.succeed();
    }
    @GameTest public void mergesPartialStacksBeforeDroppingOnlyTheRemainder(GameTestHelper test) {
        var inv = inventory(); fill(inv); inv.setItem(0, new ItemStack(Items.STONE, 64));
        inv.setItem(4, new ItemStack(Items.STONE_SLAB, 60));
        var drops = new ArrayList<ItemStack>();
        var result = InventoryConversion.apply(inv, drops::add, 0, Items.STONE_SLAB, ConversionPlan.calculate(64, 2, 1));
        assertEquals(64, inv.getItem(4).getCount()); assertEquals(60, result.dropped());
        assertEquals(60, drops.getFirst().getCount());
        test.succeed();
    }
    @GameTest public void putsConvertedBlocksInHandAndDropsOnlyTheUnstorableOddSlab(GameTestHelper test) {
        var inv = inventory(); fill(inv); inv.setItem(0, new ItemStack(Items.STONE_SLAB, 63));
        var drops = new ArrayList<ItemStack>();
        var result = InventoryConversion.apply(inv, drops::add, 0, Items.STONE, ConversionPlan.calculate(63, 1, 2));
        assertEquals(31, inv.getItem(0).getCount()); assertTrue(inv.getItem(0).is(Items.STONE));
        assertEquals(1, result.dropped()); assertEquals(1, drops.getFirst().getCount()); assertTrue(drops.getFirst().is(Items.STONE_SLAB));
        test.succeed();
    }
    @GameTest public void wholeStackMergesOffhandBeforeDroppingRemainder(GameTestHelper test) {
        var inv = inventory(); fill(inv); inv.setItem(0, new ItemStack(Items.STONE, 64));
        inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.STONE_SLAB, 62));
        var drops = new ArrayList<ItemStack>();
        InventoryConversion.apply(inv, drops::add, 0, Items.STONE_SLAB, ConversionPlan.calculate(64, 2, 1));
        assertEquals(64, inv.getItem(0).getCount());
        assertTrue(inv.getItem(0).is(Items.STONE_SLAB));
        assertEquals(64, inv.getItem(Inventory.SLOT_OFFHAND).getCount());
        assertEquals(62, drops.getFirst().getCount());
        test.succeed();
    }
    @GameTest public void changedSourceIsRejectedBeforeAnyMutation(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.STONE, 63));
        assertThrows(IllegalArgumentException.class, () -> InventoryConversion.apply(inv, stack -> fail("Unexpected drop"),
                0, Items.STONE_SLAB, ConversionPlan.calculate(64, 2, 1)));
        assertEquals(63, inv.getItem(0).getCount());
        test.succeed();
    }

    private InventoryConversion.Result capped(Inventory inventory, ArrayList<ItemStack> drops) {
        return manual(inventory, drops, 0, "blockwheel:slab");
    }

    private InventoryConversion.Result manual(Inventory inventory, ArrayList<ItemStack> drops, int slot, String shape) {
        var source = inventory.getItem(slot);
        String id = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        var family = Blockwheel.CONVERSIONS.catalog().family(id).orElseThrow();
        var target = family.shape(shape).orElseThrow();
        var plan = InventoryConversion.plan(inventory, slot, family, family.member(id).orElseThrow(), target);
        return InventoryConversion.apply(inventory, drops::add, slot,
                BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse(target.blockId())).asItem(), plan);
    }

    @GameTest public void slabCapLeavesOneStackInHandAndBaseOnlyInMainStorage(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_WALL, 64));
        var drops = new ArrayList<ItemStack>(); var result = capped(inv, drops);
        assertEquals(64, result.produced()); assertEquals(64, inv.getItem(0).getCount()); assertTrue(inv.getItem(0).is(Items.COBBLESTONE_SLAB));
        assertTrue(inv.getItem(9).is(Items.COBBLESTONE)); assertEquals(32, inv.getItem(9).getCount());
        for (int i = 1; i < 9; i++) assertTrue(inv.getItem(i).isEmpty());
        assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void cappedChangeMergesMainStorageWithoutTouchingHotbarOrOffhand(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 64));
        inv.setItem(2, new ItemStack(Items.COBBLESTONE, 27)); inv.setItem(3, new ItemStack(Items.COBBLESTONE_SLAB, 60));
        inv.setItem(9, new ItemStack(Items.COBBLESTONE, 60)); inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_SLAB, 62));
        var drops = new ArrayList<ItemStack>(); capped(inv, drops);
        assertEquals(64, inv.getItem(0).getCount()); assertEquals(27, inv.getItem(2).getCount()); assertEquals(60, inv.getItem(3).getCount());
        assertEquals(64, inv.getItem(9).getCount()); assertEquals(28, inv.getItem(10).getCount());
        assertEquals(62, inv.getItem(Inventory.SLOT_OFFHAND).getCount()); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void cappedBaseOverflowDropsEvenIfOtherHotbarSlotsAreEmpty(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        for (int i = 9; i < 36; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
        var drops = new ArrayList<ItemStack>(); var result = capped(inv, drops);
        assertEquals(64, inv.getItem(0).getCount()); assertEquals(32, result.dropped());
        assertEquals(1, drops.size()); assertTrue(drops.getFirst().is(Items.COBBLESTONE)); assertEquals(32, drops.getFirst().getCount());
        for (int i = 1; i < 9; i++) assertTrue(inv.getItem(i).isEmpty());
        test.succeed();
    }

    @GameTest public void cappedModdedSlabsRespectSmallStackLimitsAndPhysicalFractions(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(BuilderTestContent.FLOOR, 15));
        var drops = new ArrayList<ItemStack>(); var result = capped(inv, drops);
        assertEquals(16, result.produced()); assertTrue(inv.getItem(0).is(BuilderTestContent.SLAB));
        assertEquals(16, inv.countItem(BuilderTestContent.SLAB)); assertEquals(13, inv.countItem(BuilderTestContent.BASE));
        assertEquals(1, inv.countItem(BuilderTestContent.FLOOR)); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void smallerInputsKeepTheirNormalSlabYield(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_STAIRS, 31));
        var drops = new ArrayList<ItemStack>(); capped(inv, drops);
        assertEquals(62, inv.getItem(0).getCount()); assertTrue(inv.getItem(0).is(Items.COBBLESTONE_SLAB));
        assertTrue(inv.getItem(9).isEmpty()); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void mainInventorySlabCompletesHeldOneAndThirtyOneSlabConversions(GameTestHelper test) {
        for (int count : new int[]{1, 31}) {
            var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, count));
            inv.setItem(9, new ItemStack(Items.COBBLESTONE_SLAB, 1));
            var drops = new ArrayList<ItemStack>(); var result = manual(inv, drops, 0, "blockwheel:block");
            assertEquals((count + 1) / 2, result.produced());
            assertTrue(inv.getItem(0).is(Items.COBBLESTONE)); assertEquals((count + 1) / 2, inv.getItem(0).getCount());
            assertTrue(inv.getItem(9).isEmpty()); assertTrue(drops.isEmpty());
            for (int slot = 1; slot < 9; slot++) assertTrue(inv.getItem(slot).isEmpty());
        }
        test.succeed();
    }

    @GameTest public void compatibleMainSlabsConsolidateIntoOneHeldBlockStack(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, 1));
        inv.setItem(35, new ItemStack(Items.COBBLESTONE_SLAB, 63));
        var drops = new ArrayList<ItemStack>(); manual(inv, drops, 0, "blockwheel:block");
        assertTrue(inv.getItem(0).is(Items.COBBLESTONE)); assertEquals(32, inv.getItem(0).getCount());
        assertTrue(inv.getItem(35).isEmpty()); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void oddRemainderGoesToMainWhileTheConvertedShapeTakesTheSelectedSlot(GameTestHelper test) {
        var inv = inventory(); inv.setSelectedSlot(4);
        inv.setItem(4, new ItemStack(Items.COBBLESTONE_SLAB, 31)); inv.setItem(2, new ItemStack(Items.COBBLESTONE_SLAB, 17));
        var drops = new ArrayList<ItemStack>(); manual(inv, drops, 4, "blockwheel:block");
        assertTrue(inv.getItem(4).is(Items.COBBLESTONE)); assertEquals(15, inv.getItem(4).getCount());
        assertTrue(inv.getItem(9).is(Items.COBBLESTONE_SLAB)); assertEquals(1, inv.getItem(9).getCount());
        assertEquals(17, inv.getItem(2).getCount()); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void hotbarOffhandAndCustomizedSlabsAreNotUsedAsManualDonors(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, 1));
        inv.setItem(1, new ItemStack(Items.COBBLESTONE_SLAB, 1));
        inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE_SLAB, 1));
        var named = new ItemStack(Items.COBBLESTONE_SLAB, 1);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Keep me"));
        inv.setItem(9, named); inv.setItem(10, new ItemStack(Items.STONE_SLAB, 1));
        var drops = new ArrayList<ItemStack>(); var result = manual(inv, drops, 0, "blockwheel:block");
        assertEquals(0, result.produced()); assertTrue(inv.getItem(0).is(Items.COBBLESTONE_SLAB));
        assertEquals(1, inv.getItem(1).getCount()); assertEquals(1, inv.getItem(Inventory.SLOT_OFFHAND).getCount());
        assertTrue(ItemStack.matches(named, inv.getItem(9))); assertTrue(drops.isEmpty()); test.succeed();
    }

    @GameTest public void staleManualDonorPlanCannotPartiallyMutateTheInventory(GameTestHelper test) {
        var inv = inventory(); inv.setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, 31));
        inv.setItem(9, new ItemStack(Items.COBBLESTONE_SLAB, 1));
        var family = Blockwheel.CONVERSIONS.catalog().family("minecraft:cobblestone_slab").orElseThrow();
        var plan = InventoryConversion.plan(inv, 0, family, family.shape("blockwheel:slab").orElseThrow(), family.shape("blockwheel:block").orElseThrow());
        assertEquals(1, plan.borrowed()); inv.setItem(9, ItemStack.EMPTY);
        assertThrows(IllegalArgumentException.class, () -> InventoryConversion.apply(inv, stack -> fail("Unexpected drop"), 0, Items.COBBLESTONE, plan));
        assertTrue(inv.getItem(0).is(Items.COBBLESTONE_SLAB)); assertEquals(31, inv.getItem(0).getCount());
        assertTrue(inv.getItem(9).isEmpty()); test.succeed();
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertTrue(boolean condition) { if (!condition) throw new AssertionError("Condition failed"); }
    private static void fail(String message) { throw new AssertionError(message); }
    private static void assertThrows(Class<? extends Throwable> expected, Runnable action) {
        try { action.run(); } catch (Throwable error) {
            if (expected.isInstance(error)) return;
            throw new AssertionError("Unexpected exception", error);
        }
        throw new AssertionError("Expected exception: " + expected.getSimpleName());
    }
}
