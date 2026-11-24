package dev.blockwheel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.client.*;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.PickRules;
import dev.blockwheel.gametest.FeedbackProbe;
import dev.blockwheel.server.BuilderController;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Actual key/sound feedback, world-derived pick packets and native image/search/priority editor. */
public final class SmartPickClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var oldRules = PickRulesClient.rules();
        var oldToggle = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.TOGGLE_BUILDER));
        var oldDump = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.FILL_NAMED_SHULKERS));
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        try {
            context.runOnClient(client -> {
                PickRulesClient.save(PickRules.DEFAULT); FeedbackProbe.reset();
                BlockwheelClient.TOGGLE_BUILDER.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B));
                BlockwheelClient.FILL_NAMED_SHULKERS.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_N)); KeyMapping.resetMapping();
                client.options.guiScale().set(3); client.resizeGui();
            });
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer(); var connection = world.getConnection(); connection.waitForChunksRender();
                context.waitFor(client -> BuilderClient.available());
                context.runOnClient(client -> { if (!FeedbackProbe.sounds().isEmpty()) throw new AssertionError("Joining played toggle feedback"); });
                toggle(context, true); toggle(context, false); toggle(context, true);
                var settings = BuilderClient.settings().withFeature(Feature.SHULKER_SUPPLY, true).withFeature(Feature.NORMALIZE, false).withFeature(Feature.PICKUP, false);
                context.runOnClient(client -> BuilderClient.request(settings)); context.waitFor(client -> BuilderClient.settings().equals(settings));
                seed(context, server, connection, player -> {
                    player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE_WALL, 7)); player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 10));
                    player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.COBBLESTONE, 1)));
                });
                context.runOnClient(client -> FeedbackProbe.reset()); context.getInput().holdKey(BlockwheelClient.FILL_NAMED_SHULKERS);
                context.waitFor(client -> client.player.getMainHandItem().isEmpty() && FeedbackProbe.sounds().size() == 1);
                context.getInput().releaseKey(BlockwheelClient.FILL_NAMED_SHULKERS); context.waitTicks(2);
                context.runOnClient(client -> {
                    sound(SoundEvents.END_PORTAL_FRAME_FILL.location().toString());
                    if (!FeedbackProbe.messages().isEmpty()) throw new AssertionError("Deposit produced an overlay message");
                });

                var target = target(context, server, connection, Blocks.STRIPPED_OAK_WOOD);
                seed(context, server, connection, player -> set(player, 10, Items.OAK_LOG, 12));
                pick(context, target); held(context, Items.OAK_LOG, 12);
                seed(context, server, connection, player -> { set(player, 10, Items.STRIPPED_OAK_WOOD, 6); set(player, 5, Items.OAK_LOG, 12); });
                pick(context, target); held(context, Items.STRIPPED_OAK_WOOD, 6);
                seed(context, server, connection, player -> {
                    set(player, 10, Items.OAK_LOG, 12); set(player, 5, Items.OAK_WOOD, 7);
                    player.getEnderChestInventory().setItem(0, ShulkerSupplyGameTests.box(true, new ItemStack(Items.STRIPPED_OAK_LOG, 9)));
                });
                pick(context, target); held(context, Items.STRIPPED_OAK_LOG, 8);
                if (!server.computeOnServer(value -> ShulkerSupplyGameTests.contents(connection.getServerPlayer().getEnderChestInventory().getItem(0)).get(0).getCount() == 1)) {
                    throw new AssertionError("Default fallback pick lost a named reserve");
                }

                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class);
                UiTestInput.settings(context); context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Pick block");
                capture(context, "blockwheel-smart-pick-settings"); context.clickScreenButton("Edit fallback rules..."); context.waitForScreen(PickRulesScreen.class);
                capture(context, "blockwheel-pick-rules-defaults"); context.clickScreenButton("Add rule"); context.waitForScreen(PickRuleEditorScreen.class);
                click(context, button -> button.getMessage().getString().equals("Choose target block...") && button.getWidth() > 24);
                context.waitForScreen(BlockPickerScreen.class); search(context, "minecraft:stone"); context.clickScreenButton("Stone");
                context.waitForScreen(PickRuleEditorScreen.class); context.clickScreenButton("Add fallback"); context.waitForScreen(BlockPickerScreen.class);
                search(context, "oak_log"); capture(context, "blockwheel-block-picker-search"); context.clickScreenButton("Oak Log");
                context.waitForScreen(PickRuleEditorScreen.class); context.clickScreenButton("Add fallback"); context.waitForScreen(BlockPickerScreen.class);
                search(context, "birch_log"); context.clickScreenButton("Birch Log"); context.waitForScreen(PickRuleEditorScreen.class);
                click(context, button -> button.active && button.getMessage().getString().equals("Dn"));
                capture(context, "blockwheel-pick-rule-priority"); context.clickScreenButton("Apply rule"); context.waitForScreen(PickRulesScreen.class);
                context.clickScreenButton("Save"); context.waitForScreen(BuilderSettingsScreen.class);
                var expected = List.of("minecraft:birch_log", "minecraft:oak_log");
                context.runOnClient(client -> {
                    if (!PickRulesClient.rules().effective(PickRulesClient.defaults()).get("minecraft:stone").equals(expected)) throw new AssertionError("GUI lost priority order");
                    if (!PickRulesClient.reload() || !PickRulesClient.rules().effective(PickRulesClient.defaults()).get("minecraft:stone").equals(expected)) throw new AssertionError("JSON did not preserve GUI priority");
                });
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class); context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                target = target(context, server, connection, Blocks.STONE);
                seed(context, server, connection, player -> { set(player, 10, Items.OAK_LOG, 9); set(player, 5, Items.BIRCH_LOG, 11); });
                pick(context, target); held(context, Items.BIRCH_LOG, 11);

                context.getInput().pressKey(GLFW.GLFW_KEY_E); context.waitForScreen(InventoryScreen.class); UiTestInput.settings(context);
                context.waitForScreen(BuilderSettingsScreen.class); context.clickScreenButton("Pick block"); context.clickScreenButton("Smart pick fallbacks: ON");
                context.clickScreenButton("Done"); context.waitForScreen(InventoryScreen.class); context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                seed(context, server, connection, player -> set(player, 10, Items.OAK_LOG, 9)); pick(context, target); context.waitTicks(4);
                if (!server.computeOnServer(value -> connection.getServerPlayer().getMainHandItem().is(Items.DIRT))) throw new AssertionError("Disabled custom fallback still picked a block");
                context.runOnClient(client -> {
                    try {
                        var valid = Files.readString(PickRulesClient.path()); var previous = PickRulesClient.rules();
                        Files.writeString(PickRulesClient.path(), "{");
                        if (PickRulesClient.reload() || !Files.readString(PickRulesClient.path()).equals("{") || !previous.equals(PickRulesClient.rules())) {
                            throw new AssertionError("Invalid file was overwritten or replaced the current preferences");
                        }
                        Files.writeString(PickRulesClient.path(), valid); if (!PickRulesClient.reload()) throw new AssertionError("Valid file did not reload");
                    } catch (java.io.IOException exception) { throw new AssertionError(exception); }
                });
            }
            Blockwheel.LOGGER.info("Smart pick client tests passed: exact/default/remote picks, picture search, target/fallback selection, priority editing, JSON reload, disable switch and requested sound/text feedback");
        } finally {
            context.runOnClient(client -> {
                PickRulesClient.save(oldRules); BlockwheelClient.TOGGLE_BUILDER.setKey(oldToggle); BlockwheelClient.FILL_NAMED_SHULKERS.setKey(oldDump); KeyMapping.resetMapping();
                client.options.guiScale().set(scale); client.resizeGui();
            });
        }
    }
    private void toggle(ClientGameTestContext context, boolean enabled) {
        context.runOnClient(client -> FeedbackProbe.reset()); context.getInput().pressKey(GLFW.GLFW_KEY_B);
        context.waitFor(client -> BuilderClient.settings().enabled() == enabled && FeedbackProbe.sounds().size() == 1);
        context.runOnClient(client -> {
            sound((enabled ? SoundEvents.ENCHANTMENT_TABLE_USE : SoundEvents.GRINDSTONE_USE).location().toString());
            if (!FeedbackProbe.messages().equals(List.of(enabled ? "Builder Mode: ON" : "Builder Mode: OFF"))) throw new AssertionError("Missing or duplicate mode status text");
        });
    }
    private static void sound(String id) {
        var played = FeedbackProbe.sounds().getFirst();
        if (!played.id().equals(id) || played.volume() != .65f || !played.relative()) throw new AssertionError("Wrong action sound: " + played);
    }
    private void search(ClientGameTestContext context, String query) {
        context.runOnClient(client -> client.gui.screen().children().stream().filter(child -> child instanceof EditBox).map(child -> (EditBox) child).findFirst().orElseThrow().setValue(query));
        context.waitTicks(2);
    }
    private void click(ClientGameTestContext context, Predicate<Button> predicate) {
        var point = context.computeOnClient(client -> {
            var button = client.gui.screen().children().stream().filter(child -> child instanceof Button value && value.active && predicate.test(value))
                    .map(child -> (Button) child).findFirst().orElseThrow(); var window = client.getWindow();
            return new double[]{(button.getX() + button.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (button.getY() + button.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private void seed(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Consumer<ServerPlayer> action) {
        server.runOnServer(value -> {
            var player = connection.getServerPlayer(); player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.getEnderChestInventory().clearContent();
            player.getInventory().setSelectedSlot(0); set(player, 0, Items.DIRT, 1); action.accept(player);
            player.connection.send(new ClientboundSetHeldSlotPacket(0)); BuilderController.sync(player);
        }); connection.waitForClientboundPackets(); context.waitTicks(2);
    }
    private static void set(ServerPlayer player, int slot, Item item, int count) { player.getInventory().setItem(slot, new ItemStack(item, count)); }
    private BlockPos target(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, Block block) {
        var position = server.computeOnServer(value -> { var pos = connection.getServerPlayer().blockPosition().offset(2, 0, 0); value.overworld().setBlockAndUpdate(pos, block.defaultBlockState()); return pos; });
        context.waitFor(client -> client.level.getBlockState(position).is(block)); return position;
    }
    private void pick(ClientGameTestContext context, BlockPos position) { context.runOnClient(client -> client.getConnection().send(new ServerboundPickItemFromBlockPacket(position, false))); }
    private void held(ClientGameTestContext context, Item item, int count) { context.waitFor(client -> client.player.getMainHandItem().is(item) && client.player.getMainHandItem().getCount() == count); }
    private void capture(ClientGameTestContext context, String name) {
        context.runOnClient(client -> { client.gui.toastManager().clear(); client.gui.hud.getChat().clearMessages(false); }); context.waitTicks(1); context.takeScreenshot(name);
    }
}
