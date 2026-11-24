package dev.blockwheel.network;

import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.RestockSettings;
import dev.blockwheel.core.PickRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Preferences carry no inventory quantities. Catalog packets are bounded chunks of server data. */
public final class BuilderPayloads {
    private BuilderPayloads() {}
    private static void writeSettings(RegistryFriendlyByteBuf buf, BuilderSettings settings) {
        buf.writeBoolean(settings.enabled()); buf.writeVarInt(settings.features());
        buf.writeVarInt(settings.restock().below()); buf.writeVarInt(settings.restock().amount());
        buf.writeBoolean(settings.restock().nonBlockShulkers());
    }
    private static BuilderSettings readSettings(RegistryFriendlyByteBuf buf) {
        return new BuilderSettings(buf.readBoolean(), buf.readVarInt(),
                new RestockSettings(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }
    public record Update(BuilderSettings settings) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Blockwheel.id("builder_update_v4"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buf, value) -> writeSettings(buf, value.settings), buf -> new Update(readSettings(buf)));
        @Override public Type<Update> type() { return TYPE; }
    }
    public record Status(BuilderSettings settings, boolean enderAllowed) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(Blockwheel.id("builder_status_v4"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.of(
                (buf, value) -> { writeSettings(buf, value.settings); buf.writeBoolean(value.enderAllowed); },
                buf -> new Status(readSettings(buf), buf.readBoolean()));
        @Override public Type<Status> type() { return TYPE; }
    }
    public record SupplyQuery(long request, String itemId) implements CustomPacketPayload {
        public static final Type<SupplyQuery> TYPE = new Type<>(Blockwheel.id("supply_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SupplyQuery> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarLong(value.request); buf.writeUtf(value.itemId, 256); },
                buf -> new SupplyQuery(buf.readVarLong(), buf.readUtf(256)));
        @Override public Type<SupplyQuery> type() { return TYPE; }
    }
    public record SupplyInvalidated() implements CustomPacketPayload {
        public static final Type<SupplyInvalidated> TYPE = new Type<>(Blockwheel.id("supply_invalidated"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SupplyInvalidated> CODEC = StreamCodec.unit(new SupplyInvalidated());
        @Override public Type<SupplyInvalidated> type() { return TYPE; }
    }
    public record FillTemplates(int containerId, boolean includeHotbar) implements CustomPacketPayload {
        public FillTemplates(int containerId) { this(containerId, false); }
        public static final Type<FillTemplates> TYPE = new Type<>(Blockwheel.id("fill_templates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FillTemplates> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarInt(value.containerId); buf.writeBoolean(value.includeHotbar); },
                buf -> new FillTemplates(buf.readVarInt(), buf.readBoolean()));
        @Override public Type<FillTemplates> type() { return TYPE; }
    }
    public record Feedback(int kind) implements CustomPacketPayload {
        public static final int MODE_ON = 0, MODE_OFF = 1, DEPOSIT = 2;
        public Feedback { if (kind < 0 || kind > 2) throw new IllegalArgumentException("Invalid action feedback"); }
        public static final Type<Feedback> TYPE = new Type<>(Blockwheel.id("action_feedback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Feedback> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.kind), buf -> new Feedback(buf.readVarInt()));
        @Override public Type<Feedback> type() { return TYPE; }
    }
    public record PickPreferences(PickRules rules) implements CustomPacketPayload {
        public static final Type<PickPreferences> TYPE = new Type<>(Blockwheel.id("pick_preferences_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PickPreferences> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeBoolean(value.rules.enabled()); buf.writeBoolean(value.rules.includeLogDefaults());
            buf.writeBoolean(value.rules.includeAnvilDefaults()); buf.writeBoolean(value.rules.includeCopperDefaults()); buf.writeVarInt(value.rules.rules().size());
            for (var rule : value.rules.rules()) {
                buf.writeUtf(rule.target(), PickRules.MAX_ID_LENGTH); buf.writeVarInt(rule.fallbacks().size());
                for (var fallback : rule.fallbacks()) buf.writeUtf(fallback, PickRules.MAX_ID_LENGTH);
            }
        }, buf -> {
            boolean enabled = buf.readBoolean(), defaults = buf.readBoolean();
            boolean anvils = buf.readBoolean(), copper = buf.readBoolean();
            int count = bounded(buf.readVarInt(), 0, PickRules.MAX_RULES); List<PickRules.Rule> rules = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String target = buf.readUtf(PickRules.MAX_ID_LENGTH); int size = bounded(buf.readVarInt(), 0, PickRules.MAX_FALLBACKS);
                List<String> fallbacks = new ArrayList<>(size);
                for (int item = 0; item < size; item++) fallbacks.add(buf.readUtf(PickRules.MAX_ID_LENGTH));
                rules.add(new PickRules.Rule(target, fallbacks));
            }
            return new PickPreferences(new PickRules(enabled, defaults, anvils, copper, rules));
        });
        @Override public Type<PickPreferences> type() { return TYPE; }
    }
    public record SupplyCounts(long request, String familyId, long loose, long carried, long ender,
                               long protectedUnits) implements CustomPacketPayload {
        public SupplyCounts {
            for (long value : new long[]{loose, carried, ender, protectedUnits}) {
                if (value < 0 || value > (1L << 50)) throw new IllegalArgumentException("Invalid supply amount");
            }
        }
        public long total() { return loose + carried + ender; }
        public static final Type<SupplyCounts> TYPE = new Type<>(Blockwheel.id("supply_counts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SupplyCounts> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarLong(value.request); buf.writeUtf(value.familyId, 256);
            buf.writeVarLong(value.loose); buf.writeVarLong(value.carried); buf.writeVarLong(value.ender); buf.writeVarLong(value.protectedUnits);
        }, buf -> new SupplyCounts(buf.readVarLong(), buf.readUtf(256), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        @Override public Type<SupplyCounts> type() { return TYPE; }
    }
    public record Form(String shapeId, String itemId, int units) {
        public Form { if (units < 1 || units > 64) throw new IllegalArgumentException("Invalid material units"); }
    }
    public record Family(String id, String baseItem, int baseUnits, List<Form> forms) {
        public Family {
            forms = List.copyOf(forms);
            if (forms.size() < 2 || forms.size() > 32 || baseUnits < 1 || baseUnits > 64) throw new IllegalArgumentException("Invalid material family");
        }
    }
    public record Catalog(long revision, int part, boolean last, List<Family> families) implements CustomPacketPayload {
        public Catalog {
            families = List.copyOf(families);
            if (part < 0 || part > 32768 || families.size() > 128) throw new IllegalArgumentException("Invalid catalog chunk");
        }
        public static final Type<Catalog> TYPE = new Type<>(Blockwheel.id("builder_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeLong(value.revision); buf.writeVarInt(value.part); buf.writeBoolean(value.last);
            buf.writeVarInt(value.families.size());
            for (Family family : value.families) {
                buf.writeUtf(family.id, 256); buf.writeUtf(family.baseItem, 256); buf.writeVarInt(family.baseUnits);
                buf.writeVarInt(family.forms.size());
                for (Form form : family.forms) {
                    buf.writeUtf(form.shapeId, 256); buf.writeUtf(form.itemId, 256); buf.writeVarInt(form.units);
                }
            }
        }, buf -> {
            long revision = buf.readLong(); int part = buf.readVarInt(); boolean last = buf.readBoolean();
            int count = bounded(buf.readVarInt(), 0, 128);
            List<Family> families = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(256), base = buf.readUtf(256); int units = buf.readVarInt();
                int size = bounded(buf.readVarInt(), 2, 32);
                List<Form> forms = new ArrayList<>(size);
                for (int j = 0; j < size; j++) forms.add(new Form(buf.readUtf(256), buf.readUtf(256), buf.readVarInt()));
                families.add(new Family(id, base, units, forms));
            }
            return new Catalog(revision, part, last, families);
        });
        @Override public Type<Catalog> type() { return TYPE; }
    }
    private static int bounded(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Invalid catalog size");
        return value;
    }
}
