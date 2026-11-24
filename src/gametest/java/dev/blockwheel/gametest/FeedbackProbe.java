package dev.blockwheel.gametest;

import dev.blockwheel.gametest.mixin.SoundVolumeAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import java.util.ArrayList;
import java.util.List;

/** Observe the actual sound manager and local-player overlay path, with no production test hooks. */
public final class FeedbackProbe {
    public record Played(String id, float volume, boolean relative) {}
    private static final List<Played> sounds = new ArrayList<>();
    private static final List<String> messages = new ArrayList<>();
    private FeedbackProbe() {}
    public static void reset() { sounds.clear(); messages.clear(); }
    public static List<Played> sounds() { return List.copyOf(sounds); }
    public static List<String> messages() { return List.copyOf(messages); }
    public static void message(String value) { messages.add(value); }
    public static void sound(SoundInstance sound) {
        var id = sound.getIdentifier();
        if (id.equals(SoundEvents.ENCHANTMENT_TABLE_USE.location()) || id.equals(SoundEvents.GRINDSTONE_USE.location())
                || id.equals(SoundEvents.END_PORTAL_FRAME_FILL.location())) {
            sounds.add(new Played(id.toString(), ((SoundVolumeAccessor) sound).blockwheel$getRequestedVolume(), sound.isRelative()));
        }
    }
}
