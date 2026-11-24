package dev.blockwheel.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-player refill policy; amounts are additional items, not a target stack count. */
public record RestockSettings(int below, int amount, boolean nonBlockShulkers) {
    public static final int MAXIMUM = 99;
    public static final RestockSettings DEFAULT = new RestockSettings(6, 32, true);
    public static final Codec<RestockSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, MAXIMUM).optionalFieldOf("below", 6).forGetter(RestockSettings::below),
            Codec.intRange(1, MAXIMUM).optionalFieldOf("amount", 32).forGetter(RestockSettings::amount),
            Codec.BOOL.optionalFieldOf("non_block_shulkers", true).forGetter(RestockSettings::nonBlockShulkers)
    ).apply(instance, RestockSettings::new));

    public RestockSettings {
        if (below < 1 || below > MAXIMUM || amount < 1 || amount > MAXIMUM) {
            throw new IllegalArgumentException("Restock settings must be between 1 and " + MAXIMUM);
        }
    }
    public RestockSettings withBelow(int value) { return new RestockSettings(value, amount, nonBlockShulkers); }
    public RestockSettings withAmount(int value) { return new RestockSettings(below, value, nonBlockShulkers); }
    public RestockSettings withNonBlockShulkers(boolean value) { return new RestockSettings(below, amount, value); }
}
