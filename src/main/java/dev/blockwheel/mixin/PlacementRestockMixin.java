package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.BuilderSettings.Feature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerGameMode.class)
public abstract class PlacementRestockMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult restockAfterPlacement(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (!Blockwheel.BUILDERS.active(player, Feature.RESTOCK)) {
            return original.call(player, level, stack, hand, hit);
        }
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        ItemStack before = stack.copy();
        InteractionResult result = original.call(player, level, stack, hand, hit);
        if (result.consumesAction()) Blockwheel.BUILDERS.afterUse(player, slot, before);
        return result;
    }

    @WrapMethod(method = "useItem")
    private InteractionResult restockAfterUse(ServerPlayer player, Level level, ItemStack stack,
            InteractionHand hand, Operation<InteractionResult> original) {
        if (!Blockwheel.BUILDERS.active(player, Feature.RESTOCK)) {
            return original.call(player, level, stack, hand);
        }
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        ItemStack before = stack.copy();
        InteractionResult result = original.call(player, level, stack, hand);
        if (result.consumesAction()) Blockwheel.BUILDERS.afterUse(player, slot, before);
        return result;
    }
}
