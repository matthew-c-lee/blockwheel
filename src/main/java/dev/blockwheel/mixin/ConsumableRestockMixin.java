package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.BuilderSettings.Feature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Food finishes after the initial use request; refill only after vanilla consumes the item. */
@Mixin(LivingEntity.class)
public abstract class ConsumableRestockMixin {
    @WrapMethod(method = "completeUsingItem")
    private void restockAfterConsumption(Operation<Void> original) {
        if (!((Object) this instanceof ServerPlayer player) || player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || !Blockwheel.BUILDERS.active(player, Feature.RESTOCK)) {
            original.call(); return;
        }
        int slot = player.getInventory().getSelectedSlot();
        var before = player.getMainHandItem().copy();
        original.call();
        Blockwheel.BUILDERS.afterUse(player, slot, before);
    }
}
