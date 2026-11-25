package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RestockSettings;
import dev.blockwheel.network.BuilderPayloads;
import dev.blockwheel.server.BuilderServerConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Schematic requests use restock policy, bounded quantities and atomic displacement, never schematic authority. */
public final class SchematicSupplyGameTests {
    private ServerPlayer player(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel(); player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(player, BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false).withFeature(Feature.PICK_BLOCK, false)
                .withRestock(RestockSettings.DEFAULT.withAmount(7)));
        return player;
    }
    private BuilderPayloads.SupplyHand request(ServerPlayer player, Item target) {
        var held = player.getMainHandItem();
        return new BuilderPayloads.SupplyHand(player.getInventory().getSelectedSlot(), BuiltInRegistries.ITEM.getKey(target).toString(),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount());
    }
    @GameTest public void schematicSupplyConvertsMainMaterialAndPreservesTheDisplacedTool(GameTestHelper test) {
        var p = player(test); var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me")); p.getInventory().setItem(0, tool.copy());
        p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
        test.assertTrue(Blockwheel.BUILDERS.supplyHand(p, request(p, Items.COBBLESTONE_STAIRS))
                && p.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && p.getMainHandItem().getCount() == 7
                && p.getInventory().getItem(9).getCount() == 25 && ItemStack.matches(tool, p.getInventory().getItem(10)),
                "Schematic supply ignored restock quantity, required pick block or lost the displaced tool"); test.succeed();
    }
    @GameTest public void schematicSupplyKeepsNamedReservesAndDoesNotSubstituteARequestedBlock(GameTestHelper test) {
        var p = player(test); p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.NETHERRACK, 5)));
        test.assertTrue(Blockwheel.BUILDERS.supplyHand(p, request(p, Items.NETHERRACK)) && p.getMainHandItem().is(Items.NETHERRACK)
                && p.getMainHandItem().getCount() == 4 && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 1,
                "Exact schematic supply spent a named reserve or invented material");
        var missing = player(test); missing.getInventory().setItem(9, new ItemStack(Items.OAK_LOG, 20));
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(missing, request(missing, Items.STRIPPED_OAK_LOG))
                && missing.getMainHandItem().isEmpty() && missing.getInventory().getItem(9).getCount() == 20,
                "Schematic supply applied pick fallbacks or stripped a log"); test.succeed();
    }
    @GameTest public void schematicSupplyRespectsRestockAndRemoteAccessGates(GameTestHelper test) {
        for (int gate = 0; gate < 5; gate++) {
            var p = player(test); var settings = Blockwheel.BUILDERS.settings(p);
            settings = switch (gate) {
                case 0 -> settings.withEnabled(false);
                case 1 -> settings.withFeature(Feature.RESTOCK, false);
                case 2 -> settings.withFeature(Feature.SHULKER_SUPPLY, false);
                default -> settings.withFeature(Feature.ENDER_ACCESS, false);
            };
            Blockwheel.BUILDERS.configure(p, settings);
            var box = ShulkerSupplyGameTests.box(true, new ItemStack(Items.NETHERRACK, 20)); p.getEnderChestInventory().setItem(0, box.copy());
            boolean allowed = Blockwheel.BUILDERS.enderAllowed();
            try {
                if (gate == 4) {
                    Blockwheel.BUILDERS.configure(p, Blockwheel.BUILDERS.settings(p).withFeature(Feature.ENDER_ACCESS, true));
                    Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(false));
                }
                test.assertTrue(!Blockwheel.BUILDERS.supplyHand(p, request(p, Items.NETHERRACK)) && p.getMainHandItem().isEmpty()
                        && ItemStack.matches(box, p.getEnderChestInventory().getItem(0)), "Schematic supply bypassed a saved/server gate");
            } finally { Blockwheel.BUILDERS.setPolicy(new BuilderServerConfig(allowed)); }
        }
        test.succeed();
    }
    @GameTest public void schematicSupplyRejectsStaleHandsAndRapidRepeatedRequests(GameTestHelper test) {
        var p = player(test); p.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
        var stale = request(p, Items.COBBLESTONE_STAIRS); p.getInventory().setItem(0, new ItemStack(Items.DIRT));
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(p, stale) && p.getMainHandItem().is(Items.DIRT)
                && p.getInventory().getItem(9).getCount() == 32, "Stale schematic request spent material");
        var fresh = player(test); fresh.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
        test.assertTrue(Blockwheel.BUILDERS.supplyHand(fresh, request(fresh, Items.COBBLESTONE_STAIRS)), "Fresh schematic supply failed");
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(fresh, request(fresh, Items.COBBLESTONE_WALL))
                && fresh.getMainHandItem().is(Items.COBBLESTONE_STAIRS) && fresh.getInventory().getItem(9).getCount() == 25,
                "Burst schematic requests bypassed the server cooldown"); test.succeed();
    }
    @GameTest public void schematicSupplyDoesNotSpendHeldOrLooseEnderMaterial(GameTestHelper test) {
        var p = player(test); p.getInventory().setItem(0, new ItemStack(Items.DIRT));
        p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 64));
        p.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.COBBLESTONE, 64));
        p.getEnderChestInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(p, request(p, Items.COBBLESTONE_STAIRS)) && p.getMainHandItem().is(Items.DIRT)
                && p.getInventory().getItem(5).getCount() == 64 && p.getOffhandItem().getCount() == 64
                && p.getEnderChestInventory().getItem(0).getCount() == 64, "Schematic supply broadened automatic donor scope"); test.succeed();
    }
    @GameTest public void schematicSupplyRequiresSpaceForTheDisplacedHandAndRejectsNonBlocks(GameTestHelper test) {
        var p = player(test); p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = 9; slot < 36; slot++) p.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        var box = ShulkerSupplyGameTests.box(false, new ItemStack(Items.COBBLESTONE, 20)); p.getEnderChestInventory().setItem(0, box.copy());
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(p, request(p, Items.COBBLESTONE_STAIRS)) && p.getMainHandItem().is(Items.DIAMOND_PICKAXE)
                && ItemStack.matches(box, p.getEnderChestInventory().getItem(0)), "Full inventory caused partial supply or an overwritten tool");
        var nonBlock = player(test); nonBlock.getInventory().setItem(9, new ItemStack(Items.ENDER_PEARL, 16));
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(nonBlock, request(nonBlock, Items.ENDER_PEARL)) && nonBlock.getMainHandItem().isEmpty(),
                "Schematic supply accepted a non-block target");
        var malformed = player(test);
        test.assertTrue(!Blockwheel.BUILDERS.supplyHand(malformed, new BuilderPayloads.SupplyHand(0, "Not An ID", "minecraft:air", 0))
                && malformed.getMainHandItem().isEmpty(), "Malformed schematic item ID was accepted"); test.succeed();
    }
}
