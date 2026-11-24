package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.ConversionPlan;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.server.BlockMaterials;
import dev.blockwheel.server.InventoryConversion;
import dev.blockwheel.server.InventoryTransaction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native pickup/menu/placement hooks plus planner conservation and capacity invariants. */
public class BuilderGameTests {
    private ServerPlayer player(GameTestHelper test, Feature... features) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        var pos = test.absolutePos(BlockPos.ZERO);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        int mask = 0; for (var feature : features) mask |= feature.bit();
        Blockwheel.BUILDERS.configure(player, new BuilderSettings(true, mask));
        return player;
    }
    private void stack(ServerPlayer player, int slot, net.minecraft.world.item.Item item, int count) {
        player.getInventory().setItem(slot, new ItemStack(item, count));
    }
    private int count(ServerPlayer player, net.minecraft.world.item.Item item) { return player.getInventory().countItem(item); }
    private long units(ServerPlayer player) {
        long value = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            var material = Blockwheel.BUILDERS.materials().resolve(stack);
            if (material != null) value += (long) stack.getCount() * material.form().units();
        }
        return value;
    }
    private ItemEntity ground(GameTestHelper test, ServerPlayer player, ItemStack stack) {
        var item = new ItemEntity(test.getLevel(), player.getX(), player.getY(), player.getZ(), stack);
        item.setNoPickUpDelay(); test.getLevel().addFreshEntity(item); return item;
    }
    private void fill(ServerPlayer player) { for (int i = 0; i < 36; i++) stack(player, i, Items.DIRT, 64); }
    private void click(ServerPlayer player, int slot, int button, ContainerInput input) {
        player.containerMenu.clicked(slot, button, input, player);
    }

    @GameTest public void everyFamilyShapePairConservesManualMaterial(GameTestHelper test) {
        var player = player(test);
        int checked = 0;
        for (var family : Blockwheel.BUILDERS.materials().families()) for (var source : family.forms()) for (var target : family.forms()) {
            for (int amount : new int[]{1, 3, new ItemStack(source.item()).getMaxStackSize()}) {
                player.getInventory().clearContent();
                stack(player, 0, source.item(), amount);
                var dropped = new ArrayList<ItemStack>();
                var catalogFamily = Blockwheel.CONVERSIONS.catalog().family(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(source.item()).toString()).orElseThrow();
                InventoryConversion.apply(player.getInventory(), dropped::add, 0, target.item(), InventoryConversion.plan(amount, catalogFamily,
                        catalogFamily.shape(source.shapeId()).orElseThrow(), catalogFamily.shape(target.shapeId()).orElseThrow()));
                long output = units(player);
                for (var drop : dropped) output += (long) drop.getCount() * Blockwheel.BUILDERS.materials().resolve(drop).form().units();
                test.assertTrue(output == (long) amount * source.units(), "Manual material mismatch for " + family.id());
                checked++;
            }
        }
        test.assertTrue(checked > 2000, "Shape-pair matrix unexpectedly small: " + checked);
        Blockwheel.LOGGER.info("Builder matrix verified {} actual shape/count conversions", checked);
        test.succeed();
    }

    @GameTest public void everyFamilyPickupShapePairConservesMaterial(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        int checked = 0;
        for (var family : Blockwheel.BUILDERS.materials().families()) for (var source : family.forms()) for (var target : family.forms()) {
            player.getInventory().clearContent();
            int capacity = new ItemStack(target.item()).getMaxStackSize();
            stack(player, 0, target.item(), capacity - 1);
            int amount = Math.min(33, new ItemStack(source.item()).getMaxStackSize());
            var incoming = new ItemStack(source.item(), amount);
            long before = units(player) + (long) amount * source.units();
            test.assertTrue(Boolean.TRUE.equals(Blockwheel.BUILDERS.pickup(player, incoming)), "Pickup rejected with free storage");
            test.assertTrue(incoming.isEmpty(), "Pickup was not fully accommodated");
            test.assertTrue(units(player) == before, "Pickup matrix lost material for " + family.id());
            for (int i = 1; i < 9; i++) test.assertTrue(player.getInventory().getItem(i).isEmpty(), "Pickup populated an empty hotbar slot");
            checked++;
        }
        test.assertTrue(checked > 700, "Pickup matrix unexpectedly small");
        test.succeed();
    }

    @GameTest public void pickupPriorityIsSelectedThenExactThenLeftToRight(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        stack(player, 0, Items.COBBLESTONE_SLAB, 60);
        stack(player, 1, Items.COBBLESTONE_WALL, 62);
        stack(player, 2, Items.COBBLESTONE_STAIRS, 63);
        player.getInventory().setSelectedSlot(2);
        var incoming = new ItemStack(Items.COBBLESTONE_WALL, 3);
        long before = units(player) + 6;
        Blockwheel.BUILDERS.pickup(player, incoming);
        test.assertTrue(player.getInventory().getItem(2).getCount() == 64, "Selected shape was not first");
        test.assertTrue(player.getInventory().getItem(1).getCount() == 64, "Exact picked shape was not second");
        test.assertTrue(player.getInventory().getItem(0).getCount() == 60, "Earlier other-shape slot stole priority");
        test.assertTrue(units(player) == before, "Priority routing lost material");
        var more = new ItemStack(Items.COBBLESTONE_WALL, 4);
        Blockwheel.BUILDERS.pickup(player, more);
        test.assertTrue(player.getInventory().getItem(0).getCount() == 64 && count(player, Items.COBBLESTONE) == 2, "Other shape/base routing failed");
        for (int i = 3; i < 9; i++) test.assertTrue(player.getInventory().getItem(i).isEmpty(), "Empty hotbar filled");
        test.succeed();
    }

    @GameTest public void oddSlabAndOneSpacePickupExamplesAreExact(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        var odd = new ItemStack(Items.COBBLESTONE_SLAB, 33);
        Blockwheel.BUILDERS.pickup(player, odd);
        test.assertTrue(count(player, Items.COBBLESTONE) == 16 && count(player, Items.COBBLESTONE_SLAB) == 1, "Odd slabs were rounded");
        for (int i = 0; i < 9; i++) test.assertTrue(player.getInventory().getItem(i).isEmpty(), "No-template pickup filled hotbar");
        player.getInventory().clearContent(); stack(player, 0, Items.COBBLESTONE_SLAB, 63);
        var walls = new ItemStack(Items.COBBLESTONE_WALL, 10);
        Blockwheel.BUILDERS.pickup(player, walls);
        test.assertTrue(count(player, Items.COBBLESTONE) == 9 && count(player, Items.COBBLESTONE_SLAB) == 65, "One-space slab routing was not 64 + 9 base + 1 slab");
        test.assertTrue(player.getInventory().getItem(0).getCount() == 64, "Slab destination overfilled");
        player.getInventory().clearContent(); stack(player, 0, Items.COBBLESTONE_SLAB, 32);
        Blockwheel.BUILDERS.pickup(player, new ItemStack(Items.COBBLESTONE_STAIRS, 32));
        test.assertTrue(count(player, Items.COBBLESTONE_SLAB) == 64 && count(player, Items.COBBLESTONE) == 16, "32-space slab example failed");
        test.succeed();
    }

    @GameTest public void fractionalPickupCombinesAnExistingPhysicalRemainder(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        stack(player, 0, Items.COBBLESTONE_SLAB, 63); stack(player, 9, Items.COBBLESTONE_SLAB, 1);
        long before = units(player) + 20;
        Blockwheel.BUILDERS.pickup(player, new ItemStack(Items.COBBLESTONE_WALL, 10));
        test.assertTrue(count(player, Items.COBBLESTONE) == 10 && count(player, Items.COBBLESTONE_SLAB) == 64, "Compatible odd slabs were not combined");
        test.assertTrue(units(player) == before, "Combining remainders lost material"); test.succeed();
    }

    @GameTest public void nativePartialPickupLeavesOriginalGroundItemsWithoutLoops(GameTestHelper test) {
        var player = player(test, Feature.PICKUP); fill(player);
        stack(player, 0, Items.COBBLESTONE_STAIRS, 63); stack(player, 9, Items.COBBLESTONE, 63);
        var entity = ground(test, player, new ItemStack(Items.COBBLESTONE_WALL, 10));
        long before = units(player) + 20;
        entity.playerTouch(player);
        test.assertTrue(entity.getItem().is(Items.COBBLESTONE_WALL) && entity.getItem().getCount() == 8 && !entity.isRemoved(), "Ground remainder changed shape or count");
        test.assertTrue(units(player) + entity.getItem().getCount() * 2L == before, "Partial pickup lost material");
        entity.playerTouch(player);
        test.assertTrue(entity.getItem().getCount() == 8, "Full inventory repeated pickup/drop");
        test.succeed();
    }

    @GameTest public void insufficientFractionalCapacityDoesNotPartiallyMutateHotbar(GameTestHelper test) {
        var player = player(test, Feature.PICKUP); fill(player); stack(player, 0, Items.COBBLESTONE_SLAB, 63);
        var input = new ItemStack(Items.COBBLESTONE_WALL, 1);
        test.assertTrue(Boolean.FALSE.equals(Blockwheel.BUILDERS.pickup(player, input)), "Unstorable half was accepted");
        test.assertTrue(input.getCount() == 1 && player.getInventory().getItem(0).getCount() == 63, "Rejected pickup changed inventory");
        stack(player, 9, Items.COBBLESTONE, 63);
        var slabs = new ItemStack(Items.COBBLESTONE_SLAB, 4);
        Blockwheel.BUILDERS.pickup(player, slabs);
        test.assertTrue(slabs.getCount() == 1 && player.getInventory().getItem(0).getCount() == 64
                && player.getInventory().getItem(9).getCount() == 64, "Partial original-item pickup was incorrect");
        test.succeed();
    }

    @GameTest public void vanillaPickupDelayAndOwnershipRemainEnforced(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        var entity = ground(test, player, new ItemStack(Items.COBBLESTONE_WALL, 5));
        entity.setPickUpDelay(20); entity.playerTouch(player);
        test.assertTrue(units(player) == 0 && !entity.isRemoved(), "Pickup delay bypassed");
        entity.setNoPickUpDelay(); entity.setTarget(UUID.randomUUID()); entity.playerTouch(player);
        test.assertTrue(units(player) == 0 && !entity.isRemoved(), "Owner protection bypassed");
        entity.setTarget(player.getUUID()); entity.playerTouch(player);
        test.assertTrue(entity.isRemoved() && count(player, Items.COBBLESTONE) == 5, "Eligible native pickup did not coerce"); test.succeed();
    }

    @GameTest public void enableNormalizationPreservesOddMaterialAndFullStorage(GameTestHelper test) {
        var player = player(test);
        stack(player, 9, Items.COBBLESTONE_SLAB, 33); stack(player, 0, Items.COBBLESTONE_STAIRS, 7);
        long before = units(player);
        Blockwheel.BUILDERS.configure(player, new BuilderSettings(true, Feature.NORMALIZE.bit()));
        test.assertTrue(count(player, Items.COBBLESTONE) == 16 && count(player, Items.COBBLESTONE_SLAB) == 1, "Enable did not normalize");
        test.assertTrue(player.getInventory().getItem(0).is(Items.COBBLESTONE_STAIRS) && units(player) == before, "Hotbar normalized or material lost");
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults()); fill(player); stack(player, 9, Items.COBBLESTONE_SLAB, 33);
        Blockwheel.BUILDERS.configure(player, new BuilderSettings(true, Feature.NORMALIZE.bit()));
        test.assertTrue(player.getInventory().getItem(9).is(Items.COBBLESTONE_SLAB) && player.getInventory().getItem(9).getCount() == 33, "Full storage discarded an odd remainder");
        test.succeed();
    }

    @GameTest public void normalCursorMovesNormalizeOnlyAtTheMainInventoryDestination(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_STAIRS, 64);
        click(player, 36, 0, ContainerInput.PICKUP);
        test.assertTrue(player.containerMenu.getCarried().is(Items.COBBLESTONE_STAIRS) && player.containerMenu.getCarried().getCount() == 64, "Cursor item normalized");
        click(player, 9, 0, ContainerInput.PICKUP);
        test.assertTrue(count(player, Items.COBBLESTONE) == 64 && player.containerMenu.getCarried().isEmpty(), "Cursor-to-main failed"); test.succeed();
    }

    @GameTest public void normalizationKeepsTheClickedSlotInsteadOfEarlierEmptyOrMergeSlots(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_STAIRS, 17); stack(player, 10, Items.COBBLESTONE, 5);
        long before = units(player); click(player, 36, 0, ContainerInput.PICKUP); click(player, 23, 0, ContainerInput.PICKUP);
        test.assertTrue(player.getInventory().getItem(23).is(Items.COBBLESTONE) && player.getInventory().getItem(23).getCount() == 17
                && player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(10).getCount() == 5
                && units(player) == before, "Normalized blocks moved away from the actual destination"); test.succeed();
    }

    @GameTest public void normalizationKeepsNumberKeySwapDestinationAndDisplacedHotbarItem(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 2, Items.COBBLESTONE_WALL, 12); stack(player, 26, Items.DIRT, 1);
        click(player, 26, 2, ContainerInput.SWAP);
        test.assertTrue(player.getInventory().getItem(26).is(Items.COBBLESTONE) && player.getInventory().getItem(26).getCount() == 12
                && player.getInventory().getItem(2).is(Items.DIRT) && player.getInventory().getItem(9).isEmpty(), "Swap normalization ignored its destination"); test.succeed();
    }

    @GameTest public void normalizationCombinesStoredSlabsAtTheNewDestination(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_SLAB, 31); stack(player, 9, Items.COBBLESTONE_SLAB, 1);
        click(player, 36, 0, ContainerInput.PICKUP); click(player, 23, 0, ContainerInput.PICKUP);
        test.assertTrue(player.getInventory().getItem(23).is(Items.COBBLESTONE) && player.getInventory().getItem(23).getCount() == 16
                && player.getInventory().getItem(9).isEmpty() && units(player) == 32, "Fractional donors changed the output slot or lost material"); test.succeed();
    }

    @GameTest public void normalizationRetainsBothDragDestinations(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_STAIRS, 2);
        click(player, 36, 0, ContainerInput.PICKUP);
        click(player, -999, AbstractContainerMenu.getQuickcraftMask(0, 1), ContainerInput.QUICK_CRAFT);
        click(player, 20, AbstractContainerMenu.getQuickcraftMask(1, 1), ContainerInput.QUICK_CRAFT);
        click(player, 23, AbstractContainerMenu.getQuickcraftMask(1, 1), ContainerInput.QUICK_CRAFT);
        click(player, -999, AbstractContainerMenu.getQuickcraftMask(2, 1), ContainerInput.QUICK_CRAFT);
        test.assertTrue(player.getInventory().getItem(20).is(Items.COBBLESTONE) && player.getInventory().getItem(20).getCount() == 1
                && player.getInventory().getItem(23).is(Items.COBBLESTONE) && player.getInventory().getItem(23).getCount() == 1
                && player.containerMenu.getCarried().isEmpty(), "Drag normalization consolidated away a placed destination"); test.succeed();
    }

    @GameTest public void shiftAndNumberKeyMovesNormalizeAtTransactionBoundaries(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_STAIRS, 64);
        click(player, 36, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(count(player, Items.COBBLESTONE) == 64 && count(player, Items.COBBLESTONE_STAIRS) == 0, "Shift move failed");
        player.getInventory().clearContent(); stack(player, 0, Items.COBBLESTONE_WALL, 17); stack(player, 9, Items.DIRT, 1);
        click(player, 9, 0, ContainerInput.SWAP);
        test.assertTrue(count(player, Items.COBBLESTONE) == 17 && player.getInventory().getItem(0).is(Items.DIRT), "Number-key swap failed"); test.succeed();
    }

    @GameTest public void shiftClickMergesConvertedStairsIntoExistingBaseBeforeEmptySlots(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE);
        stack(player, 0, Items.STONE_BRICK_STAIRS, 17); stack(player, 14, Items.STONE_BRICKS, 10);
        long before = units(player); click(player, 36, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(player.getInventory().getItem(14).is(Items.STONE_BRICKS) && player.getInventory().getItem(14).getCount() == 27
                && player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(0).isEmpty()
                && units(player) == before, "Shift-click did not merge converted stone brick stairs into the existing block stack");
        test.succeed();
    }

    @GameTest public void shiftClickFillsAllExistingBaseStacksBeforeRoutingPhysicalRemainder(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE);
        stack(player, 0, Items.STONE_BRICK_SLAB, 33); stack(player, 12, Items.STONE_BRICKS, 60); stack(player, 20, Items.STONE_BRICKS, 62);
        long before = units(player); click(player, 36, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(player.getInventory().getItem(12).getCount() == 64 && player.getInventory().getItem(20).getCount() == 64
                && player.getInventory().getItem(9).is(Items.STONE_BRICKS) && player.getInventory().getItem(9).getCount() == 10
                && count(player, Items.STONE_BRICK_SLAB) == 1 && player.getInventory().getItem(0).isEmpty()
                && units(player) == before, "Shift-click failed to merge first and preserve fractional material");
        test.succeed();
    }

    @GameTest public void dragDoesNotNormalizeBeforeReleaseOrAlterCarriedRemainder(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 0, Items.COBBLESTONE_STAIRS, 4);
        click(player, 36, 0, ContainerInput.PICKUP);
        click(player, -999, AbstractContainerMenu.getQuickcraftMask(0, 1), ContainerInput.QUICK_CRAFT);
        click(player, 9, AbstractContainerMenu.getQuickcraftMask(1, 1), ContainerInput.QUICK_CRAFT);
        click(player, 10, AbstractContainerMenu.getQuickcraftMask(1, 1), ContainerInput.QUICK_CRAFT);
        test.assertTrue(count(player, Items.COBBLESTONE) == 0 && player.containerMenu.getCarried().getCount() == 4, "Partial drag mutated inventory");
        click(player, -999, AbstractContainerMenu.getQuickcraftMask(2, 1), ContainerInput.QUICK_CRAFT);
        test.assertTrue(count(player, Items.COBBLESTONE) == 2 && player.containerMenu.getCarried().is(Items.COBBLESTONE_STAIRS)
                && player.containerMenu.getCarried().getCount() == 2, "Completed drag lost or normalized cursor material"); test.succeed();
    }

    @GameTest public void hotbarCraftingOffhandAndExternalTransfersKeepTheirShapes(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE);
        for (int destination : new int[]{37, 45, 1}) {
            player.getInventory().clearContent(); player.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);
            stack(player, 0, Items.COBBLESTONE_STAIRS, 3);
            click(player, 36, 0, ContainerInput.PICKUP); click(player, destination, 0, ContainerInput.PICKUP);
            test.assertTrue(player.inventoryMenu.getSlot(destination).getItem().is(Items.COBBLESTONE_STAIRS), "Non-storage destination normalized: " + destination);
        }
        player.getInventory().clearContent(); player.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);
        player.containerMenu = ChestMenu.oneRow(7, player.getInventory());
        stack(player, 0, Items.COBBLESTONE_WALL, 5);
        click(player, 36, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(player.containerMenu.getSlot(0).getItem().is(Items.COBBLESTONE_WALL) && count(player, Items.COBBLESTONE) == 0, "External transfer normalized");
        player.containerMenu = player.inventoryMenu; test.succeed();
    }

    @GameTest public void restockPrefersExactThenBaseAndNeverConsumesOtherDomains(GameTestHelper test) {
        var player = player(test, Feature.RESTOCK);
        stack(player, 1, Items.COBBLESTONE_STAIRS, 17); stack(player, Inventory.SLOT_OFFHAND, Items.COBBLESTONE_STAIRS, 19);
        stack(player, 9, Items.COBBLESTONE, 64); stack(player, 10, Items.COBBLESTONE_STAIRS, 5); stack(player, 11, Items.COBBLESTONE_WALL, 64);
        long before = units(player);
        var plan = Blockwheel.BUILDERS.planner().restock(player.getInventory(), 0, Items.COBBLESTONE_STAIRS);
        test.assertTrue(plan != null && plan.transaction().commit(), "Restock failed");
        test.assertTrue(player.getInventory().getItem(0).getCount() == 64 && player.getInventory().getItem(9).getCount() == 5
                && player.getInventory().getItem(10).isEmpty() && player.getInventory().getItem(11).getCount() == 64, "Donor preference incorrect");
        test.assertTrue(player.getInventory().getItem(1).getCount() == 17 && player.getOffhandItem().getCount() == 19 && units(player) == before, "Restock consumed hotbar/offhand or lost material"); test.succeed();
    }

    @GameTest public void nativeLastItemPlacementRestocksTheOriginalShape(GameTestHelper test) {
        var player = player(test, Feature.RESTOCK);
        stack(player, 0, Items.COBBLESTONE_STAIRS, 1); stack(player, 9, Items.COBBLESTONE, 10);
        var floor = test.absolutePos(new BlockPos(2, 0, 2)); test.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        player.setPos(floor.getX() - 1.5, floor.getY() + 1, floor.getZ() + .5);
        var result = player.gameMode.useItemOn(player, test.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false));
        test.assertTrue(result.consumesAction() && test.getLevel().getBlockState(floor.above()).is(Blocks.COBBLESTONE_STAIRS), "Native placement failed");
        test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && player.getMainHandItem().getCount() == 10
                && count(player, Items.COBBLESTONE) == 0, "Last-item restock failed"); test.succeed();
    }

    @GameTest public void intentionalSlotChangesAndDisabledFeaturesDoNotRestock(GameTestHelper test) {
        var player = player(test, Feature.RESTOCK); stack(player, 9, Items.COBBLESTONE, 64);
        player.getInventory().setSelectedSlot(1);
        Blockwheel.BUILDERS.afterPlacement(player, 0, new ItemStack(Items.COBBLESTONE_STAIRS, 1));
        test.assertTrue(player.getInventory().getItem(0).isEmpty() && count(player, Items.COBBLESTONE) == 64, "Old slot restocked after changing selection");
        player.getInventory().setSelectedSlot(0); Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults());
        Blockwheel.BUILDERS.afterPlacement(player, 0, new ItemStack(Items.COBBLESTONE_STAIRS, 1));
        var incoming = new ItemStack(Items.COBBLESTONE_WALL, 3);
        test.assertTrue(Blockwheel.BUILDERS.pickup(player, incoming) == null && incoming.getCount() == 3
                && player.getInventory().getItem(0).isEmpty(), "Disabled mode performed an automatic action"); test.succeed();
    }

    @GameTest public void familyPickPreservesDisplacedItemsAndRejectsUnsafeReplacement(GameTestHelper test) {
        var player = player(test, Feature.PICK_BLOCK); fill(player); stack(player, 9, Items.COBBLESTONE, 64);
        long before = units(player); int dirt = count(player, Items.DIRT);
        test.assertTrue(Blockwheel.BUILDERS.pick(player, new ItemStack(Items.COBBLESTONE_WALL)), "Family pick failed");
        test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE_WALL) && player.getMainHandItem().getCount() == 64
                && units(player) == before && count(player, Items.DIRT) == dirt, "Pick overwrote old material");
        fill(player); stack(player, 9, Items.COBBLESTONE, 64);
        var snapshot = new InventoryTransaction(player.getInventory());
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, new ItemStack(Items.COBBLESTONE_SLAB)), "Unsafe full-inventory replacement succeeded");
        for (int i = 0; i < 36; i++) test.assertTrue(ItemStack.matches(snapshot.before(i), player.getInventory().getItem(i)), "Failed pick changed slot " + i);
        test.succeed();
    }

    @GameTest public void customizedItemsAreNotConvertedOrConsumed(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE, Feature.PICKUP, Feature.RESTOCK, Feature.PICK_BLOCK);
        var custom = new ItemStack(Items.COBBLESTONE, 64); custom.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this"));
        player.getInventory().setItem(9, custom.copy());
        test.assertTrue(Blockwheel.BUILDERS.pickup(player, custom.copy()) == null, "Customized pickup was coerced");
        test.assertTrue(Blockwheel.BUILDERS.planner().restock(player.getInventory(), 0, Items.COBBLESTONE_STAIRS) == null, "Customized donor consumed");
        test.assertTrue(!Blockwheel.BUILDERS.pick(player, new ItemStack(Items.COBBLESTONE_WALL)), "Pick consumed customized donor");
        var stairs = new ItemStack(Items.COBBLESTONE_STAIRS, 3); stairs.set(DataComponents.CUSTOM_NAME, Component.literal("Shaped gift"));
        player.getInventory().setItem(0, stairs.copy()); click(player, 36, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(player.getInventory().contains(stack -> ItemStack.matches(stack, stairs)) && ItemStack.matches(player.getInventory().getItem(9), custom), "Custom data was normalized or altered"); test.succeed();
    }

    @GameTest public void moddedShapesUseDataUnitsAndActualStackLimits(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        var material = Blockwheel.BUILDERS.materials().resolve(BuilderTestContent.FLOOR);
        test.assertTrue(material != null && material.form().units() == 3 && material.family().base().item() == BuilderTestContent.BASE, "New modded family or shape not discovered");
        stack(player, 0, BuilderTestContent.SLAB, 12);
        Blockwheel.BUILDERS.pickup(player, new ItemStack(BuilderTestContent.BASE, 10));
        test.assertTrue(player.getMainHandItem().getCount() == 16 && count(player, BuilderTestContent.BASE) == 8, "Modded stack limit/ratio ignored");
        player.getInventory().clearContent(); stack(player, 9, BuilderTestContent.FLOOR, 3);
        Blockwheel.BUILDERS.configure(player, new BuilderSettings(true, Feature.NORMALIZE.bit()));
        test.assertTrue(count(player, BuilderTestContent.BASE) == 4 && count(player, BuilderTestContent.SLAB) == 1, "Three-unit flooring lost physical change");
        player.getInventory().clearContent(); stack(player, 9, BuilderTestContent.BASE, 32);
        var plan = Blockwheel.BUILDERS.planner().restock(player.getInventory(), 0, BuilderTestContent.STAIRS);
        test.assertTrue(plan != null && plan.transaction().commit() && player.getMainHandItem().getCount() == 16
                && count(player, BuilderTestContent.BASE) == 16, "Modded restock used a hardcoded stack size"); test.succeed();
    }

    @GameTest public void stalePlansAreRejectedWithoutPartialChanges(GameTestHelper test) {
        var player = player(test, Feature.PICKUP); stack(player, 9, Items.COBBLESTONE, 10);
        var incoming = new ItemStack(Items.COBBLESTONE_WALL, 4);
        var plan = Blockwheel.BUILDERS.planner().pickup(player.getInventory(), incoming);
        stack(player, 9, Items.DIRT, 1);
        test.assertTrue(!plan.transaction().commit() && incoming.getCount() == 4 && player.getInventory().getItem(9).is(Items.DIRT), "Stale plan committed"); test.succeed();
    }

    @GameTest public void familiesWithoutReliableBasesStayOutsideAutomaticConversion(GameTestHelper test) {
        var existing = Blockwheel.CONVERSIONS.catalog().family("minecraft:cobblestone").orElseThrow();
        var definitions = existing.members().stream().map(FamilyCatalog.Member::shape).toList();
        var unbased = FamilyCatalog.discover(List.of(new FamilyCatalog.BlockSpec("minecraft:cobblestone_stairs", Set.of(), false),
                        new FamilyCatalog.BlockSpec("minecraft:cobblestone_wall", Set.of(), false)), definitions, Map.of(), List.of());
        test.assertTrue(unbased.family("minecraft:cobblestone_stairs").isPresent(), "Unbased family vanished from manual discovery");
        var automatic = new BlockMaterials(unbased);
        test.assertTrue(automatic.resolve(Items.COBBLESTONE_STAIRS) == null, "Automatic conversion guessed a missing base"); test.succeed();
    }

    @GameTest public void mainToHotbarTransfersRemainTheChosenVariant(GameTestHelper test) {
        var player = player(test, Feature.NORMALIZE); stack(player, 9, Items.COBBLESTONE_STAIRS, 5);
        click(player, 9, 0, ContainerInput.QUICK_MOVE);
        test.assertTrue(count(player, Items.COBBLESTONE_STAIRS) == 5 && count(player, Items.COBBLESTONE) == 0, "Main-to-hotbar transfer normalized"); test.succeed();
    }

    @GameTest public void creativePickupAlsoPreservesUnacceptedGroundMaterial(GameTestHelper test) {
        var player = player(test, Feature.PICKUP); player.setGameMode(GameType.CREATIVE); fill(player);
        stack(player, 0, Items.COBBLESTONE_STAIRS, 63); stack(player, 9, Items.COBBLESTONE, 63);
        var entity = ground(test, player, new ItemStack(Items.COBBLESTONE_WALL, 9)); entity.playerTouch(player);
        test.assertTrue(!entity.isRemoved() && entity.getItem().getCount() == 7
                && player.getMainHandItem().getCount() == 64 && count(player, Items.COBBLESTONE) == 64, "Creative pickup discarded overflow"); test.succeed();
    }

    @GameTest public void fractionalStorageCanCompleteThePreferredHotbarShape(GameTestHelper test) {
        var player = player(test, Feature.PICKUP);
        stack(player, 0, Items.COBBLESTONE_STAIRS, 63); stack(player, 9, Items.COBBLESTONE_SLAB, 1);
        var input = new ItemStack(Items.COBBLESTONE_SLAB, 1);
        long before = units(player) + 1;
        Blockwheel.BUILDERS.pickup(player, input);
        test.assertTrue(input.isEmpty() && player.getMainHandItem().getCount() == 64
                && count(player, Items.COBBLESTONE_SLAB) == 0 && units(player) == before, "Fractional input was stored instead of completing the active shape");
        player.getInventory().clearContent(); stack(player, 0, BuilderTestContent.FLOOR, 15); stack(player, 9, BuilderTestContent.SLAB, 1);
        var base = new ItemStack(BuilderTestContent.BASE, 1);
        before = units(player) + 2;
        Blockwheel.BUILDERS.pickup(player, base);
        test.assertTrue(base.isEmpty() && player.getMainHandItem().getCount() == 16
                && count(player, BuilderTestContent.SLAB) == 0 && units(player) == before, "Data-defined three-unit shape could not combine physical change"); test.succeed();
    }
}
