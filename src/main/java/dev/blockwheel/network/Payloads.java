package dev.blockwheel.network;

import dev.blockwheel.Blockwheel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public final class Payloads {
    private Payloads() {}

    public record Rejected() implements CustomPacketPayload {
        public static final Type<Rejected> TYPE = new Type<>(Blockwheel.id("rejected"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rejected> CODEC = StreamCodec.unit(new Rejected());
        @Override public Type<Rejected> type() { return TYPE; }
    }

    public record Open(int slot) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Blockwheel.id("open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.slot), buf -> new Open(buf.readVarInt()));
        @Override public Type<Open> type() { return TYPE; }
    }

    public record Convert(long token, String shapeId) implements CustomPacketPayload {
        public static final Type<Convert> TYPE = new Type<>(Blockwheel.id("convert"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Convert> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.token); buf.writeUtf(value.shapeId, 256); buf.writeBoolean(false); },
                buf -> {
                    long token = buf.readLong();
                    String shape = buf.readUtf(256);
                    buf.readBoolean(); // Consume the legacy wire byte; it never changes conversion quantity.
                    return new Convert(token, shape);
                });
        @Override public Type<Convert> type() { return TYPE; }
    }

    public record Option(String shapeId, String translationKey, String itemId, int units) {}

    public record Wheel(long token, int slot, String sourceItem, int count, int sourceUnits,
                        String familyItem, List<Option> options) implements CustomPacketPayload {
        public Wheel { options = List.copyOf(options); }
        public static final Type<Wheel> TYPE = new Type<>(Blockwheel.id("wheel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Wheel> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeLong(value.token); buf.writeVarInt(value.slot); buf.writeUtf(value.sourceItem, 256);
            buf.writeVarInt(value.count); buf.writeVarInt(value.sourceUnits); buf.writeUtf(value.familyItem, 256);
            buf.writeVarInt(value.options.size());
            for (Option option : value.options) {
                buf.writeUtf(option.shapeId, 256); buf.writeUtf(option.translationKey, 256);
                buf.writeUtf(option.itemId, 256); buf.writeVarInt(option.units);
            }
        }, buf -> {
            long token = buf.readLong(); int slot = buf.readVarInt(); String source = buf.readUtf(256);
            int count = buf.readVarInt(); int units = buf.readVarInt(); String family = buf.readUtf(256);
            int size = buf.readVarInt();
            if (size < 2 || size > 32 || units < 1 || units > 64 || count < 1 || count > 9999 || slot < 0 || slot > 8) {
                throw new IllegalArgumentException("Invalid wheel snapshot");
            }
            List<Option> options = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String shape = buf.readUtf(256), label = buf.readUtf(256), item = buf.readUtf(256);
                int value = buf.readVarInt();
                if (value < 1 || value > 64) throw new IllegalArgumentException("Invalid form value");
                options.add(new Option(shape, label, item, value));
            }
            return new Wheel(token, slot, source, count, units, family, options);
        });
        @Override public Type<Wheel> type() { return TYPE; }
    }
}
