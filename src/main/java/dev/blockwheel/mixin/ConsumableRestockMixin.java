package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.BuilderSettings.Feature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;

/** Food finishes after the initial use request; refill only after vanilla consumes the item. */
@Mixin(LivingEntity.class)
public abstract class ConsumableRestockMixin {
    @WrapMethod(method = "completeUsingItem")
    private void restockAfterConsumption(Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player)
                || !Blockwheel.BUILDERS.active(player, Feature.RESTOCK)) {
            original.call(); return;
        }
        var hand = player.getUsedItemHand();
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        var before = player.getItemInHand(hand).copy();
        original.call();
        Blockwheel.BUILDERS.afterUse(player, slot, before);
    }
}
