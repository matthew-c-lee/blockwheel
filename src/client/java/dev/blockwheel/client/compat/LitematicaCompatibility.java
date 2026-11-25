package dev.blockwheel.client.compat;

import dev.blockwheel.Blockwheel;
import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Optional Easy Place bootstrap. Never predicts item stacks or reads remote container contents. */
public final class LitematicaCompatibility {
    private static Method emptyPickSlot, pickSlot;
    private static boolean resolved;
    private static long lastRequest;
    private LitematicaCompatibility() {}

    public static void reset() { lastRequest = 0; }

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
    public static boolean supplyForEasyPlace(Minecraft client, ItemStack target) {
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
            }
            if (emptyPickSlot == null || pickSlot == null) return -1;
            int slot = (int) emptyPickSlot.invoke(null, player.getInventory());
            return slot >= 0 ? slot : (int) pickSlot.invoke(null, player);
        } catch (ReflectiveOperationException | RuntimeException error) {
            emptyPickSlot = null; pickSlot = null;
            Blockwheel.LOGGER.warn("Litematica pick-slot API unavailable; Easy Place supply bridge disabled", error);
            return -1;
        }
    }
}
