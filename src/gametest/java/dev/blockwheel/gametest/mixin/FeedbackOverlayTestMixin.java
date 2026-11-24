package dev.blockwheel.gametest.mixin;

import dev.blockwheel.gametest.FeedbackProbe;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class FeedbackOverlayTestMixin {
    @Inject(method = "sendOverlayMessage", at = @At("HEAD"))
    private void recordFeedback(Component message, CallbackInfo callback) { FeedbackProbe.message(message.getString()); }
}
