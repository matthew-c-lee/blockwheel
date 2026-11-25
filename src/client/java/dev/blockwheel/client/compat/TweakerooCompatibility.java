package dev.blockwheel.client.compat;

import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.core.BuilderSettings.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Supported items in either hand are owned by the server restocker. */
public final class TweakerooCompatibility {
    private TweakerooCompatibility() {}
    public static boolean ownsRestock(Player player, InteractionHand hand, ItemStack reference) {
        return (hand == InteractionHand.MAIN_HAND || hand == InteractionHand.OFF_HAND) && player == Minecraft.getInstance().player
                && BuilderClient.settings().active(Feature.RESTOCK) && !reference.isEmpty()
                && (BuilderClient.material(reference) != null
                    || BuilderClient.settings().shulkerSupply() && !reference.isDamageableItem())
                && player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty();
    }
}
