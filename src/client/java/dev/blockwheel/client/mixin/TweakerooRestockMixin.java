package dev.blockwheel.client.mixin;

import dev.blockwheel.client.compat.TweakerooCompatibility;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional hooks verified against the actual Tweakeroo 0.29.5 source and release JAR. */
@Pseudo
@Mixin(targets = "fi.dy.masa.tweakeroo.util.InventoryUtils", remap = false)
public abstract class TweakerooRestockMixin {
    @Inject(method = "preRestockHand", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void serverOwnsPreRestock(Player player, InteractionHand hand, boolean allowHotbar, CallbackInfo callback) {
        if (TweakerooCompatibility.ownsRestock(player, hand, player.getItemInHand(hand))) callback.cancel();
    }
    @Inject(method = "restockNewStackToHand", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void serverOwnsPostRestock(Player player, InteractionHand hand, ItemStack reference, boolean allowHotbar, CallbackInfo callback) {
        if (TweakerooCompatibility.ownsRestock(player, hand, reference)) callback.cancel();
    }
}
