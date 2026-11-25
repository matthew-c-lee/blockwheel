package dev.blockwheel;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RadialPages;
import dev.blockwheel.network.Payloads;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;

import java.util.List;

public final class FenceGateGameTests {
    private ServerPlayer player(GameTestHelper test, boolean builder) {
        var p = test.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getEnderChestInventory().clearContent(); p.getInventory().setSelectedSlot(0);
        Blockwheel.BUILDERS.configure(p, BuilderSettings.defaults().withEnabled(builder)
                .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false)); return p;
    }
    private void convert(ServerPlayer p, String shape) {
        var wheel = Blockwheel.CONVERSIONS.prepare(p, 0); if (wheel == null) throw new AssertionError("No fence gate wheel");
        Blockwheel.CONVERSIONS.convert(p, new Payloads.Convert(wheel.token(), shape));
    }
    @GameTest public void gateDiscoveryMatchesNativeTwoPlankRecipes(GameTestHelper test) {
        int found = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (block.builtInRegistryHolder().tags().noneMatch(tag -> tag.location().toString().equals("minecraft:fence_gates"))) continue;
            var family = Blockwheel.CONVERSIONS.catalog().family(BuiltInRegistries.BLOCK.getKey(block).toString()).orElseThrow();
            var gate = family.shape(RadialPages.FENCE_GATE).orElseThrow(); var plank = family.shape("blockwheel:block").orElseThrow();
            test.assertTrue(gate.shape().units() == 4 && plank.shape().units() == 2, "Fence gate material value drifted");
            var material = new ItemStack(BuiltInRegistries.BLOCK.getValue(Identifier.parse(plank.blockId())).asItem());
            var stick = new ItemStack(Items.STICK);
            var input = CraftingInput.of(3, 2, List.of(stick.copy(), material.copy(), stick.copy(), stick.copy(), material.copy(), stick.copy()));
            var output = test.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, test.getLevel()).orElseThrow().value().assemble(input);
            test.assertTrue(output.is(block.asItem()) && output.getCount() == 1, "Fence gate no longer matches two planks and four sticks"); found++;
        }
        test.assertTrue(found >= 12, "Missing native fence gate families"); test.succeed();
    }
    @GameTest public void manualGateConversionPreservesOddPlanksAndReversesWithoutInventingSticks(GameTestHelper test) {
        var p = player(test, false); p.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 5)); convert(p, RadialPages.FENCE_GATE);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_FENCE_GATE) && p.getMainHandItem().getCount() == 2
                && p.getInventory().getItem(9).is(Items.OAK_PLANKS) && p.getInventory().getItem(9).getCount() == 1, "Gate conversion lost the odd plank");
        convert(p, RadialPages.FENCE);
        test.assertTrue(p.getMainHandItem().is(Items.OAK_FENCE) && p.getMainHandItem().getCount() == 4
                && p.getInventory().countItem(Items.OAK_PLANKS) == 1 && p.getInventory().countItem(Items.STICK) == 0,
                "Gate-to-fence conversion changed material or generated sticks"); test.succeed();
    }
    @GameTest public void gateMaterialUsesExistingOffhandRestockAndBulkDepositEngines(GameTestHelper test) {
        var p = player(test, true); p.getInventory().setItem(9, new ItemStack(Items.OAK_FENCE_GATE, 20));
        p.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_FENCE, 4));
        Blockwheel.BUILDERS.afterUse(p, Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_FENCE, 5));
        test.assertTrue(p.getOffhandItem().getCount() == 36 && p.getInventory().getItem(9).getCount() == 4,
                "Fence gate material did not share ordinary family restock accounting");
        p.getInventory().clearContent(); p.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.OAK_FENCE, 10));
        p.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.OAK_FENCE_GATE, 1)));
        test.assertTrue(Blockwheel.BUILDERS.fillTemplates(p, p.containerMenu.containerId, true) && p.getOffhandItem().isEmpty()
                && ShulkerSupplyGameTests.contents(p.getEnderChestInventory().getItem(0)).get(0).getCount() == 6,
                "Bulk fence-gate templates did not consume complete offhand material batches"); test.succeed();
    }
}
