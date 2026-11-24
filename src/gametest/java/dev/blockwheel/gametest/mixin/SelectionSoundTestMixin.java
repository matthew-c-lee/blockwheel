package dev.blockwheel.gametest.mixin;

import dev.blockwheel.gametest.SelectionSoundProbe;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe real UI playback requests without replacing Minecraft's sound implementation. */
@Mixin(SoundManager.class)
public abstract class SelectionSoundTestMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void recordSelectionSound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        SelectionSoundProbe.record(sound);
        dev.blockwheel.gametest.FeedbackProbe.sound(sound);
    }
}
