package dev.blockwheel.gametest;

import dev.blockwheel.gametest.mixin.SoundVolumeAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/** Client-thread observations at the sound manager, independent of an audio device. */
public final class SelectionSoundProbe {
    public record Click(float volume, SoundSource source, boolean relative) {}
    private static final List<Click> clicks = new ArrayList<>();

    private SelectionSoundProbe() {}
    public static void reset() { clicks.clear(); }
    public static List<Click> clicks() { return List.copyOf(clicks); }
    public static void record(SoundInstance sound) {
        if (sound.getIdentifier().equals(SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON.location())) {
            clicks.add(new Click(((SoundVolumeAccessor) sound).blockwheel$getRequestedVolume(), sound.getSource(), sound.isRelative()));
        }
    }
}
