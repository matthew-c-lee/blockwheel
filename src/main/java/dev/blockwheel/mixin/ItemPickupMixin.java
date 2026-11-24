package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blockwheel.Blockwheel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve vanilla ownership, pickup delay, statistics and entity lifecycle checks. */
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {
    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean routeMaterial(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        if (inventory.player instanceof ServerPlayer player) {
            Boolean result = Blockwheel.BUILDERS.pickup(player, stack);
            if (result != null) return result;
        }
        return original.call(inventory, stack);
    }
}
