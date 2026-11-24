package dev.blockwheel.client.mixin;

import dev.blockwheel.client.RadialScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The radial's center stays empty while the remaining HUD continues to render. */
@Mixin(Hud.class)
public abstract class RadialCrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void emptyRadialCenter(CallbackInfo callback) {
        if (Minecraft.getInstance().gui.screen() instanceof RadialScreen) callback.cancel();
    }
}
