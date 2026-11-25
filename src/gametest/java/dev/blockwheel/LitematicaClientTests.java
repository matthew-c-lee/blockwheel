package dev.blockwheel;

import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.compat.LitematicaCompatibility;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.RestockSettings;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional real-mod profile: original and rewritten Easy Place fetch material, then place and restock. */
public final class LitematicaClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        Map<Object, Boolean> saved = new LinkedHashMap<>();
        Object slots = config("Generic", "PICK_BLOCKABLE_SLOTS"); String oldSlots = (String) call(slots, "getStringValue");
        try {
            context.runOnClient(client -> {
                for (String name : new String[]{"EASY_PLACE_MODE", "EASY_PLACE_FIRST"}) {
                    var option = config("Generic", name); saved.put(option, (boolean) call(option, "getBooleanValue")); set(option, true);
                }
                var adjacent = config("Generic", "EASY_PLACE_CLICK_ADJACENT");
                saved.put(adjacent, (boolean) call(adjacent, "getBooleanValue")); set(adjacent, false);
                var rewrite = config("Generic", "EASY_PLACE_POST_REWRITE"); saved.put(rewrite, (boolean) call(rewrite, "getBooleanValue"));
                for (String name : new String[]{"ENABLE_RENDERING", "ENABLE_SCHEMATIC_RENDERING"}) {
                    var option = config("Visuals", name); saved.put(option, (boolean) call(option, "getBooleanValue")); set(option, true);
                }
                pickSlots("2");
            });
            for (boolean rewritten : new boolean[]{false, true}) {
                context.runOnClient(client -> { set(config("Generic", "EASY_PLACE_POST_REWRITE"), rewritten); LitematicaCompatibility.reset(); });
                try (var world = context.worldBuilder().create()) {
                    var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
                    // Litematica reloads its file on world join, replacing the fixture's in-memory options.
                    context.runOnClient(client -> {
                        for (String name : new String[]{"EASY_PLACE_MODE", "EASY_PLACE_FIRST"}) set(config("Generic", name), true);
                        // Match the default direct-click policy; adjacent mode also needs a real ray-trace hit behind the ghost.
                        set(config("Generic", "EASY_PLACE_CLICK_ADJACENT"), false);
                        set(config("Generic", "EASY_PLACE_POST_REWRITE"), rewritten);
                        for (String name : new String[]{"ENABLE_RENDERING", "ENABLE_SCHEMATIC_RENDERING"}) set(config("Visuals", name), true);
                        pickSlots("2");
                    });
                    var settings = BuilderSettings.defaults().withEnabled(true).withFeature(Feature.SHULKER_SUPPLY, true)
                            .withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false).withFeature(Feature.PICK_BLOCK, false)
                            .withRestock(RestockSettings.DEFAULT.withAmount(7));
                    context.runOnClient(client -> BuilderClient.request(settings)); context.waitFor(client -> BuilderClient.settings().equals(settings));
                    BlockPos first = server.computeOnServer(value -> {
                        var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL);
                        player.getInventory().clearContent(); player.getEnderChestInventory().clearContent(); player.getInventory().setSelectedSlot(0);
                        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
                        player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE_STAIRS, 20)));
                        var position = player.blockPosition().offset(rewritten ? -2 : 2, 0, 0);
                        for (var target : new BlockPos[]{position, position.offset(0, 0, 1)}) {
                            value.overworld().setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
                            value.overworld().setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                        }
                        BuilderController.sync(player); return position;
                    });
                    connection.waitForClientboundPackets();
                    for (int placement = 0; placement < 2; placement++) {
                        BlockPos position = first.offset(0, 0, placement);
                        context.waitFor(client -> client.level.getBlockState(position.below()).is(Blocks.STONE));
                        context.runOnClient(client -> {
                            var schematic = (Level) invoke("fi.dy.masa.litematica.world.SchematicWorldHandler", "getSchematicWorld", new Class<?>[0]);
                            try { schematic.getChunkSource().getClass().getMethod("loadChunk", int.class, int.class)
                                    .invoke(schematic.getChunkSource(), position.getX() >> 4, position.getZ() >> 4); }
                            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                            schematic.setBlock(position, Blocks.COBBLESTONE.defaultBlockState(), 3);
                        });
                        var angles = context.computeOnClient(client -> {
                            var delta = Vec3.atCenterOf(position).subtract(client.player.getEyePosition());
                            return new float[]{(float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                                    (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)))};
                        });
                        context.getInput().lookAt(angles[0], angles[1]); context.waitTicks(2);
                        int expected = placement == 0 ? 6 : 12;
                        try {
                            context.waitFor(client -> {
                                if (rewritten) invoke("fi.dy.masa.litematica.util.EasyPlaceUtils", "handleEasyPlaceWithMessage", new Class<?>[0]);
                                else invoke("fi.dy.masa.litematica.util.WorldUtils", "handleEasyPlace", new Class<?>[]{Minecraft.class}, client);
                                return client.level.getBlockState(position).is(Blocks.COBBLESTONE)
                                        && client.player.getMainHandItem().is(Items.COBBLESTONE) && client.player.getMainHandItem().getCount() == expected;
                            });
                        } catch (AssertionError error) {
                            context.takeScreenshot("blockwheel-easyplace-timeout-" + rewritten);
                            String diagnostic = context.computeOnClient(client -> {
                                var schematic = (Level) invoke("fi.dy.masa.litematica.world.SchematicWorldHandler", "getSchematicWorld", new Class<?>[0]);
                                return "rewritten=" + rewritten + ", target=" + position + ", real=" + client.level.getBlockState(position)
                                        + ", schematic=" + schematic.getBlockState(position) + ", selected=" + client.player.getInventory().getSelectedSlot()
                                        + ", held=" + client.player.getMainHandItem() + ", mode=" + call(config("Generic", "EASY_PLACE_MODE"), "getBooleanValue");
                            });
                            throw new AssertionError("Easy Place did not place/refill: " + diagnostic, error);
                        }
                        int remaining = placement == 0 ? 13 : 6;
                        if (!server.computeOnServer(value -> connection.getServerPlayer().getInventory().getSelectedSlot() == 1
                                && connection.getServerPlayer().getInventory().getItem(0).is(Items.DIAMOND_PICKAXE)
                                && ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == remaining)) {
                            throw new AssertionError("Easy Place ignored its pick slots, restock quantity, remote material or the held tool");
                        }
                    }
                    Blockwheel.LOGGER.info("Litematica {} Easy Place scenario passed: configured pick slot, remote family supply, placement and hand restock",
                            rewritten ? "rewritten" : "default");
                }
            }
        } finally {
            context.runOnClient(client -> { saved.forEach(LitematicaClientTests::set); pickSlots(oldSlots); LitematicaCompatibility.reset(); });
        }
    }
    private static Object config(String group, String name) {
        try { return Class.forName("fi.dy.masa.litematica.config.Configs$" + group).getField(name).get(null); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object call(Object value, String method) {
        try { return value.getClass().getMethod(method).invoke(value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(Object option, boolean value) {
        try { option.getClass().getMethod("setBooleanValue", boolean.class).invoke(option, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void pickSlots(String slots) {
        invoke("fi.dy.masa.litematica.util.InventoryUtils", "setPickBlockableSlots", new Class<?>[]{String.class}, slots);
    }
    private static Object invoke(String type, String method, Class<?>[] parameters, Object... args) {
        try { return Class.forName(type).getMethod(method, parameters).invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
