package dev.blockwheel.client.compat;

import dev.blockwheel.Blockwheel;
import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;

/** Optional Easy Place bootstrap. Never predicts item stacks or reads remote container contents. */
public final class LitematicaCompatibility {
    private static Method emptyPickSlot, pickSlot;
    private static Method legacyPlace, rewrittenPlace, booleanValue, toolMode;
    private static Object easyPlaceMode, rewriteMode;
    private static boolean resolved;
    private static long lastRequest;
    private static Pending pending, replaying;
    private record Pending(Player player, Level level, Level schematic, BlockPos position, BlockState state,
                           BlockState schematicState, ItemStack target, ItemStack previous, BuilderSettings settings,
                           int slot, boolean rewritten, long time) {
        boolean matches(ItemStack stack, BlockPos pos, Level world, boolean rewrite) {
            return position.equals(pos) && schematic == world && rewritten == rewrite
                    && ItemStack.isSameItemSameComponents(target, stack);
        }
    }
    private LitematicaCompatibility() {}

    public static void reset() { lastRequest = 0; pending = replaying = null; }

    /** Resume one interrupted click once the server's actual hand stack arrives. */
    public static void tick(Minecraft client) {
        var request = pending;
        if (request == null) return;
        var player = client.player;
        if (System.nanoTime() - request.time() > 2_000_000_000L || player != request.player() || client.level != request.level()
                || client.gameMode == null || client.getConnection() == null || client.gui.screen() != null || !client.isWindowActive()
                || !player.isAlive() || player.isSpectator() || player.hasInfiniteMaterials() || player.isUsingItem()
                || !BuilderClient.settings().equals(request.settings()) || player.getInventory().getSelectedSlot() != request.slot()
                || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
                || client.level.getBlockState(request.position()) != request.state()
                || request.schematic().getBlockState(request.position()) != request.schematicState()) {
            pending = null; return;
        }
        var held = player.getMainHandItem();
        if (!ItemStack.isSameItemSameComponents(held, request.target())) {
            if (!ItemStack.matches(held, request.previous())) pending = null;
            return;
        }
        pending = null;
        try {
            if (!(boolean) booleanValue.invoke(easyPlaceMode) || (boolean) booleanValue.invoke(rewriteMode) != request.rewritten()
                    || ((Enum<?>) toolMode.invoke(null)).name().equals("REBUILD")) return;
            replaying = request;
            // Native Easy Place traces again and owns orientation, reach, placement and its position cache.
            if (request.rewritten()) rewrittenPlace.invoke(null);
            else legacyPlace.invoke(null, client);
        } catch (ReflectiveOperationException | RuntimeException error) {
            disable(error);
        } finally { replaying = null; }
    }

    public static boolean needsSupply(Minecraft client, ItemStack target) {
        var player = client.player;
        if (player == null || client.level == null || client.gameMode == null || client.getConnection() == null || client.gui.screen() != null
                || !client.isWindowActive() || player.hasInfiniteMaterials() || !BuilderClient.settings().active(Feature.RESTOCK)
                || !ClientPlayNetworking.canSend(BuilderPayloads.SupplyHand.TYPE)
                || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
                || target.isEmpty() || !(target.getItem() instanceof BlockItem) || !target.getComponentsPatch().isEmpty()
                || BuilderClient.material(target) == null && !BuilderClient.settings().shulkerSupply()) return false;
        // Keep Litematica's normal picking and existing offhand matches in charge of local exact items.
        return player.getInventory().findSlotMatchingItem(target) == -1
                && !ItemStack.isSameItemSameComponents(player.getOffhandItem(), target);
    }

    /** True means this attempt waits for authoritative supply instead of running a competing local swap. */
    public static boolean supplyForEasyPlace(Minecraft client, ItemStack target, BlockPos position, Level world, boolean rewritten) {
        // A continuation must never place a different schematic block, even if it needs the same item.
        if (replaying != null) return !replaying.matches(target, position, world, rewritten);
        if (pending != null && (!pending.matches(target, position, world, rewritten) || !needsSupply(client, target))) pending = null;
        if (!needsSupply(client, target)) return false;
        long now = System.nanoTime();
        if (lastRequest != 0 && now - lastRequest < 250_000_000L) return true;
        int slot = preferredSlot(client.player);
        if (slot < 0 || slot > 8) return false;
        var held = client.player.getInventory().getItem(slot);
        // Selection is ordinary vanilla state; the server alone replaces or converts inventory stacks.
        client.player.getInventory().setSelectedSlot(slot);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        ClientPlayNetworking.send(new BuilderPayloads.SupplyHand(slot, BuiltInRegistries.ITEM.getKey(target.getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString(), held.getCount()));
        lastRequest = now;
        pending = new Pending(client.player, client.level, world, position.immutable(), client.level.getBlockState(position),
                world.getBlockState(position), target.copyWithCount(1), held.copy(), BuilderClient.settings(), slot, rewritten, now);
        return true;
    }

    private static int preferredSlot(Player player) {
        try {
            if (!resolved) {
                resolved = true;
                var inventoryUtils = Class.forName("fi.dy.masa.litematica.util.InventoryUtils");
                emptyPickSlot = inventoryUtils.getDeclaredMethod("getEmptyPickBlockableHotbarSlot", Inventory.class);
                pickSlot = inventoryUtils.getDeclaredMethod("getPickBlockTargetSlot", Player.class);
                emptyPickSlot.setAccessible(true); pickSlot.setAccessible(true);
                legacyPlace = Class.forName("fi.dy.masa.litematica.util.WorldUtils").getMethod("handleEasyPlace", Minecraft.class);
                rewrittenPlace = Class.forName("fi.dy.masa.litematica.util.EasyPlaceUtils").getMethod("handleEasyPlaceWithMessage");
                var config = Class.forName("fi.dy.masa.litematica.config.Configs$Generic");
                easyPlaceMode = config.getField("EASY_PLACE_MODE").get(null); rewriteMode = config.getField("EASY_PLACE_POST_REWRITE").get(null);
                booleanValue = easyPlaceMode.getClass().getMethod("getBooleanValue");
                toolMode = Class.forName("fi.dy.masa.litematica.data.DataManager").getMethod("getToolMode");
            }
            if (emptyPickSlot == null || pickSlot == null) return -1;
            int slot = (int) emptyPickSlot.invoke(null, player.getInventory());
            return slot >= 0 ? slot : (int) pickSlot.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException error) {
            disable(error);
            return -1;
        }
    }

    private static void disable(Exception error) {
        emptyPickSlot = null; pickSlot = null; pending = null;
        Blockwheel.LOGGER.warn("Litematica API unavailable; Easy Place supply bridge disabled", error);
    }
}
