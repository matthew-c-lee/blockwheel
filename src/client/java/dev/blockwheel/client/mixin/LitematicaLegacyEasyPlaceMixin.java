package dev.blockwheel.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blockwheel.client.compat.LitematicaCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Litematica 0.28.8 defaults to this legacy Easy Place path. */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.WorldUtils", remap = false)
public abstract class LitematicaLegacyEasyPlaceMixin {
    @WrapOperation(method = "doEasyPlaceAction", at = @At(value = "INVOKE", target =
            "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"),
            require = 0, remap = false)
    private static void supplyBeforePick(ItemStack target, BlockPos position, Level world, Minecraft client, Operation<Void> original) {
        if (!LitematicaCompatibility.supplyForEasyPlace(client, target)) original.call(target, position, world, client);
    }
}
