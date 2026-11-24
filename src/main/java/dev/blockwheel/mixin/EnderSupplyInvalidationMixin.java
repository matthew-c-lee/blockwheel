package dev.blockwheel.mixin;

import dev.blockwheel.Blockwheel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 26.2 SimpleContainer has no listener API; observe its actual dirty boundary. */
@Mixin(SimpleContainer.class)
public abstract class EnderSupplyInvalidationMixin {
    @Inject(method = "setChanged", at = @At("TAIL"))
    private void invalidateEnderSupply(CallbackInfo callback) {
        if ((Object) this instanceof PlayerEnderChestContainer) Blockwheel.BUILDERS.enderChanged((Container) this);
    }
}
