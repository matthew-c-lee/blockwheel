package dev.blockwheel.client;

import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.MaterialComposition;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.Map;

/** Server-confirmed settings and a read-only catalog projection; never mutates inventory. */
public final class BuilderClient {
    public record Material(BuilderPayloads.Family family, BuilderPayloads.Form form) {}
    private static BuilderSettings settings = BuilderSettings.defaults();
    private static Map<Item, Material> catalog = Map.of();
    private static Map<Item, Material> incoming = new HashMap<>();
    private static long revision;
    private static int nextPart;
    private static boolean receiving;
    private static boolean enderAllowed;
    private static BuilderPayloads.SupplyCounts supply;
    private static long supplyTime, requestSequence, pendingRequest = -1, pendingTime, lastQuery;
    private static String pendingFamily;

    private BuilderClient() {}
    public static BuilderSettings settings() { return settings; }
    public static boolean enderAllowed() { return enderAllowed; }
    /** Keep displayed counts while refreshing; inventory operations never use this snapshot. */
    public static void invalidateSupply() { supplyTime = System.nanoTime() - 1_000_000_000L; pendingRequest = -1; pendingFamily = null; }
    private static void clearSupply() { supply = null; invalidateSupply(); }
    public static boolean available() { return ClientPlayNetworking.canSend(BuilderPayloads.Update.TYPE); }
    public static void request(BuilderSettings value) {
        if (available()) ClientPlayNetworking.send(new BuilderPayloads.Update(value));
    }
    public static void fillNamedShulkers() {
        fillNamedShulkers(false);
    }
    public static void fillNamedShulkers(boolean includeHotbar) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu.getCarried().isEmpty() && ClientPlayNetworking.canSend(BuilderPayloads.FillTemplates.TYPE)) {
            ClientPlayNetworking.send(new BuilderPayloads.FillTemplates(player.containerMenu.containerId, includeHotbar));
        }
    }
    public static Material material(ItemStack stack) {
        return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() ? catalog.get(stack.getItem()) : null;
    }
    public static long availableUnits(ItemStack target) {
        var family = material(target);
        var player = Minecraft.getInstance().player;
        if (family == null || player == null) return 0;
        long units = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = player.getInventory().getItem(slot);
            var source = material(stack);
            if (source != null && source.family().id().equals(family.family().id())) units += (long) stack.getCount() * source.form().units();
        }
        return units;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Feedback.TYPE, (payload, context) -> {
            var client = context.client();
            var sound = switch (payload.kind()) {
                case BuilderPayloads.Feedback.MODE_ON -> SoundEvents.ENCHANTMENT_TABLE_USE;
                case BuilderPayloads.Feedback.MODE_OFF -> SoundEvents.GRINDSTONE_USE;
                default -> SoundEvents.END_PORTAL_FRAME_FILL;
            };
            client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1f, .65f));
            if (payload.kind() != BuilderPayloads.Feedback.DEPOSIT && client.player != null
                    && !InterfacePreferencesClient.settings().hideBuilderMessages()) {
                client.player.sendOverlayMessage(Component.translatable(payload.kind() == BuilderPayloads.Feedback.MODE_ON
                        ? "ui.blockwheel.builder_on" : "ui.blockwheel.builder_off"));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Status.TYPE, (payload, context) -> {
            settings = payload.settings(); enderAllowed = payload.enderAllowed(); clearSupply();
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.SupplyInvalidated.TYPE, (payload, context) -> invalidateSupply());
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.SupplyCounts.TYPE, (payload, context) -> {
            if (payload.request() != pendingRequest || !payload.familyId().equals(pendingFamily)) return;
            supply = payload; supplyTime = System.nanoTime(); pendingRequest = -1;
        });
        ClientPlayNetworking.registerGlobalReceiver(BuilderPayloads.Catalog.TYPE, (payload, context) -> accept(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            settings = BuilderSettings.defaults(); catalog = Map.of(); incoming = new HashMap<>(); nextPart = 0; receiving = false;
            enderAllowed = false; clearSupply(); lastQuery = 0;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> InventoryControls.install(screen));
        ItemTooltipCallback.EVENT.register((stack, context, flags, lines) -> {
            if (!settings.active(Feature.TOOLTIPS)) return;
            var material = material(stack);
            if (material == null) return;
            boolean include = settings.shulkerSupply() && settings.feature(Feature.SHULKER_TOOLTIPS);
            var snapshot = include ? supplyFor(stack, material) : null;
            long units = snapshot == null ? availableUnits(stack) : snapshot.total();
            lines.add(Component.translatable("tooltip.blockwheel.material", MaterialComposition.format(units, material.family().baseUnits()))
                    .withStyle(ChatFormatting.DARK_AQUA));
            material.family().forms().stream().filter(form -> form.shapeId().equals("blockwheel:slab")).findFirst().ifPresent(slab ->
                    lines.add(Component.translatable("tooltip.blockwheel.slabs", MaterialComposition.format(units, slab.units())).withStyle(ChatFormatting.GRAY)));
            if (include && snapshot == null) lines.add(Component.translatable("tooltip.blockwheel.supply_loading").withStyle(ChatFormatting.GRAY));
            if (snapshot != null) {
                int base = material.family().baseUnits();
                lines.add(Component.translatable("tooltip.blockwheel.inventory_supply", MaterialComposition.format(snapshot.loose(), base)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.blockwheel.carried_supply", MaterialComposition.format(snapshot.carried(), base)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.blockwheel.ender_supply", MaterialComposition.format(snapshot.ender(), base)).withStyle(ChatFormatting.GRAY));
                if (snapshot.protectedUnits() > 0) lines.add(Component.translatable("tooltip.blockwheel.protected_supply", MaterialComposition.format(snapshot.protectedUnits(), base)).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.blockwheel.shape_equivalents", snapshot.total() / material.form().units()).withStyle(ChatFormatting.DARK_AQUA));
            }
        });
    }

    private static BuilderPayloads.SupplyCounts supplyFor(ItemStack stack, Material material) {
        long now = System.nanoTime();
        var previous = supply != null && supply.familyId().equals(material.family().id()) ? supply : null;
        if (previous != null && now - supplyTime < 1_000_000_000L) return previous;
        if (pendingRequest >= 0 && material.family().id().equals(pendingFamily) && now - pendingTime < 1_000_000_000L) return previous;
        if (now - lastQuery >= 300_000_000L && ClientPlayNetworking.canSend(BuilderPayloads.SupplyQuery.TYPE)) {
            pendingRequest = ++requestSequence; pendingFamily = material.family().id(); pendingTime = lastQuery = now;
            ClientPlayNetworking.send(new BuilderPayloads.SupplyQuery(pendingRequest, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        }
        return previous;
    }

    public static Component modeLabel() {
        return Component.translatable(settings.enabled() ? "ui.blockwheel.builder_on" : "ui.blockwheel.builder_off")
                .withStyle(settings.enabled() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
    private static void accept(BuilderPayloads.Catalog packet) {
        if (packet.part() == 0) { incoming = new HashMap<>(); revision = packet.revision(); nextPart = 0; receiving = true; clearSupply(); }
        if (!receiving || packet.revision() != revision || packet.part() != nextPart++) { incoming.clear(); receiving = false; return; }
        for (var family : packet.families()) for (var form : family.forms()) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(form.itemId()));
            if (item == Items.AIR || incoming.size() >= 32768) { incoming.clear(); receiving = false; return; }
            var previous = incoming.putIfAbsent(item, new Material(family, form));
            if (previous != null) { incoming.clear(); receiving = false; return; }
        }
        if (packet.last()) { catalog = Map.copyOf(incoming); incoming = new HashMap<>(); receiving = false; }
    }
}
