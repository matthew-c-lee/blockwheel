package dev.blockwheel.gametest.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The public volume getter needs a resolved audio asset; observe the requested gain instead. */
@Mixin(AbstractSoundInstance.class)
public interface SoundVolumeAccessor {
    @Accessor("volume") float blockwheel$getRequestedVolume();
}
