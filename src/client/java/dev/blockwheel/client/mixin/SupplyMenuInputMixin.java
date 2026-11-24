package dev.blockwheel.client.mixin;

import dev.blockwheel.client.BuilderClient;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla can accept predicted clicks without resending changed slots. */
@Mixin(AbstractContainerMenu.class)
public abstract class SupplyMenuInputMixin {
    @Inject(method = "clicked", at = @At("TAIL"))
    private void localMenuPredictionChanged(CallbackInfo callback) { BuilderClient.invalidateSupply(); }
}
