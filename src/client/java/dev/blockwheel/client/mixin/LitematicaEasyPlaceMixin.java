package dev.blockwheel.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.blockwheel.client.compat.LitematicaCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional call site verified against Litematica 0.28.8 for Minecraft 26.2. */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils", remap = false)
public abstract class LitematicaEasyPlaceMixin {
    @Inject(method = "handleEasyPlace", at = @At(value = "INVOKE", target =
            "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"),
            cancellable = true, require = 0, remap = false)
    private static void supplyBeforePick(CallbackInfoReturnable<InteractionResult> result, @Local ItemStack target,
                                         @Local BlockPos position, @Local Level world) {
        if (LitematicaCompatibility.supplyForEasyPlace(Minecraft.getInstance(), target, position, world, true))
            result.setReturnValue(InteractionResult.FAIL);
    }
}
