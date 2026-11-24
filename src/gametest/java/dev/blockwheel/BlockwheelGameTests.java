package dev.blockwheel;

import dev.blockwheel.network.Payloads;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;

import java.util.Set;

public class BlockwheelGameTests {
    @GameTest public void manualWheelCompletesOddSlabsFromMainAndCannotReplayTheDonorSpend(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        for (int count : new int[]{1, 31}) {
            player.getInventory().clearContent(); player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_SLAB, count));
            player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE_SLAB, 1));
            var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
            var request = new Payloads.Convert(wheel.token(), "blockwheel:block");
            Blockwheel.CONVERSIONS.convert(player, request);
            test.assertTrue(player.getMainHandItem().is(Items.COBBLESTONE)
                    && player.getMainHandItem().getCount() == (count + 1) / 2, "Converted blocks did not take the hand");
            test.assertTrue(player.getInventory().countItem(Items.COBBLESTONE_SLAB) == 0, "Extra main-inventory slab was ignored");
            Blockwheel.CONVERSIONS.convert(player, request);
            test.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == (count + 1) / 2, "Replayed donor conversion duplicated material");
            for (int slot = 1; slot < 9; slot++) test.assertTrue(player.getInventory().getItem(slot).isEmpty(), "Conversion filled another hotbar slot");
        }
        test.succeed();
    }

    @GameTest public void legacyModeByteCannotRequestAPartialConversion(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        for (boolean legacyFlag : new boolean[]{false, true}) {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            player.getInventory().setSelectedSlot(0);
            var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess());
            Payloads.Convert request;
            try {
                buffer.writeLong(wheel.token());
                buffer.writeUtf("blockwheel:slab", 256);
                buffer.writeBoolean(legacyFlag);
                request = Payloads.Convert.CODEC.decode(buffer);
            } finally { buffer.release(); }
            Blockwheel.CONVERSIONS.convert(player, request);
            test.assertTrue(player.getInventory().getItem(0).is(Items.STONE_SLAB), "Source stayed in hand");
            test.assertTrue(player.getInventory().countItem(Items.STONE) == 32, "Wrong returned base material");
            test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 64, "Legacy mode byte changed conversion quantity");
        }
        test.succeed();
    }

    @GameTest public void vanillaFamilies(GameTestHelper test) {
        var catalog = Blockwheel.CONVERSIONS.catalog();
        Set<BlockFamily.Variant> forms = Set.of(BlockFamily.Variant.SLAB, BlockFamily.Variant.STAIRS,
                BlockFamily.Variant.WALL, BlockFamily.Variant.FENCE, BlockFamily.Variant.CUSTOM_FENCE);
        int checked = 0;
        for (var family : BlockFamilies.getAllFamilies().toList()) {
            String base = BuiltInRegistries.BLOCK.getKey(family.getBaseBlock()).toString();
            for (var variant : family.getVariants().entrySet()) {
                if (!forms.contains(variant.getKey())) continue;
                String id = BuiltInRegistries.BLOCK.getKey(variant.getValue()).toString();
                var found = catalog.family(id).orElse(null);
                test.assertTrue(found != null, "Missing vanilla form: " + id);
                test.assertTrue(found.member(base).isPresent(), "Wrong base for " + id + ": " + found.id() + " expected " + base);
                checked++;
            }
        }
        test.assertTrue(checked > 100, "Vanilla coverage unexpectedly small: " + checked);
        Blockwheel.LOGGER.info("Verified {} vanilla form relationships in {} families", checked, catalog.families().size());
        test.succeed();
    }

    @GameTest public void newShapeLoadedFromData(GameTestHelper test) {
        var family = Blockwheel.CONVERSIONS.catalog().family("minecraft:oak_button").orElseThrow();
        test.assertTrue(family.shape("blockwheel-test:button").isPresent(), "Data-defined shape missing");
        test.assertTrue(family.member("minecraft:oak_planks").isPresent(), "Data-defined shape has wrong family");
        test.succeed();
    }

    @GameTest public void validConversionAndReplay(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        test.assertTrue(wheel != null, "Wheel did not open");
        var request = new Payloads.Convert(wheel.token(), "blockwheel:slab");
        Blockwheel.CONVERSIONS.convert(player, request);
        test.assertTrue(player.getInventory().getItem(0).is(Items.STONE_SLAB), "Held stack was not converted");
        test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 64, "Incorrect slab total");
        test.assertTrue(player.getInventory().getItem(9).is(Items.STONE) && player.getInventory().getItem(9).getCount() == 32, "Base change did not enter main storage");
        Blockwheel.CONVERSIONS.convert(player, request);
        test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 64 && player.getInventory().countItem(Items.STONE) == 32, "Replay duplicated items");
        test.succeed();
    }

    @GameTest public void staleCountAndComponentsAreRejected(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        player.getInventory().getItem(0).shrink(1);
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:slab"));
        test.assertTrue(player.getInventory().countItem(Items.STONE) == 63, "Changed count was accepted");
        test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 0, "Stale conversion produced output");
        wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        player.getInventory().getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:slab"));
        test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 0, "Changed components were accepted");
        test.assertTrue(Blockwheel.CONVERSIONS.prepare(player, 0) == null, "Custom-data stack was accepted");
        test.succeed();
    }

    @GameTest public void wrongSlotAndForeignTargetsAreRejected(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        player.getInventory().setSelectedSlot(1);
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:slab"));
        test.assertTrue(player.getInventory().countItem(Items.STONE) == 64, "Wrong selected slot was accepted");
        player.getInventory().setSelectedSlot(0);
        wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:fence"));
        test.assertTrue(player.getInventory().countItem(Items.STONE) == 64, "Nonexistent stone fence was accepted");
        test.succeed();
    }

    @GameTest public void oldTokenDoesNotInvalidateNewWheel(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
        var old = Blockwheel.CONVERSIONS.prepare(player, 0);
        var current = Blockwheel.CONVERSIONS.prepare(player, 0);
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(old.token(), "blockwheel:slab"));
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(current.token(), "blockwheel:slab"));
        test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 6, "Late packet invalidated current wheel");
        test.succeed();
    }

    @GameTest public void creativeOverflowCreatesGroundItems(GameTestHelper test) {
        var player = test.makeMockServerPlayerInLevel();
        // Mock players spawn independently of the test structure. Drop inside
        // its loaded chunk and allow entity registration to finish before querying.
        var pos = test.absolutePos(BlockPos.ZERO);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setGameMode(GameType.CREATIVE);
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        var before = test.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                .map(ItemEntity::getId).collect(java.util.stream.Collectors.toSet());
        var wheel = Blockwheel.CONVERSIONS.prepare(player, 0);
        Blockwheel.CONVERSIONS.convert(player, new Payloads.Convert(wheel.token(), "blockwheel:slab"));
        test.runAfterDelay(1, () -> {
            int dropped = test.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                    .filter(entity -> !before.contains(entity.getId()) && entity.getItem().is(Items.STONE))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            test.assertTrue(player.getInventory().countItem(Items.STONE_SLAB) == 64, "Wrong inventory output");
            test.assertTrue(dropped == 32, "Creative base-material overflow vanished instead of dropping: " + dropped);
            test.succeed();
        });
    }
}
