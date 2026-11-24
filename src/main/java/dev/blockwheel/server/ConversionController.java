package dev.blockwheel.server;

import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.ConversionPlan;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.FamilyViews;
import dev.blockwheel.data.WoodFamilyData;
import dev.blockwheel.data.FamilyData;
import dev.blockwheel.network.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConversionController {
    private record Session(long token, int slot, ItemStack source, FamilyCatalog.Family family,
                           FamilyCatalog.Member member, int openedAt) {}
    private FamilyCatalog catalog = FamilyCatalog.empty();
    private FamilyCatalog experimentalCatalog = FamilyCatalog.empty();
    private FamilyViews views = new FamilyViews(catalog, experimentalCatalog);
    private final Map<UUID, Session> sessions = new HashMap<>();
    private long nextToken;

    public FamilyCatalog catalog() { return catalog; }
    public FamilyCatalog experimentalCatalog() { return experimentalCatalog; }

    public void reload(MinecraftServer server) {
        sessions.clear();
        try {
            var loaded = FamilyData.load(server.getResourceManager());
            FamilyCatalog expanded;
            try { expanded = WoodFamilyData.expand(server.getResourceManager(), loaded); }
            catch (Exception error) { Blockwheel.LOGGER.error("Experimental wood data rejected; using ordinary families", error); expanded = loaded; }
            var prepared = new FamilyViews(loaded, expanded);
            catalog = loaded; experimentalCatalog = expanded; views = prepared;
            Blockwheel.LOGGER.info("Blockwheel discovered {} block families", catalog.families().size());
        } catch (Exception error) {
            Blockwheel.LOGGER.error("Blockwheel data rejected; keeping the last valid catalog", error);
        }
    }

    public void clear() { sessions.clear(); catalog = FamilyCatalog.empty(); experimentalCatalog = catalog; views = new FamilyViews(catalog, catalog); }
    public void forget(ServerPlayer player) { sessions.remove(player.getUUID()); }

    public void open(ServerPlayer player, int slot) {
        if (!ServerPlayNetworking.canSend(player, Payloads.Wheel.TYPE)) return;
        Payloads.Wheel wheel = prepare(player, slot);
        if (wheel != null) ServerPlayNetworking.send(player, wheel);
        else if (ServerPlayNetworking.canSend(player, Payloads.Rejected.TYPE)) ServerPlayNetworking.send(player, new Payloads.Rejected());
    }

    /** Server-side preparation is separate from transport, so tests exercise the same authorization path. */
    public Payloads.Wheel prepare(ServerPlayer player, int slot) {
        forget(player);
        if (!usable(player) || slot < 0 || slot > 8 || slot != player.getInventory().getSelectedSlot()) return null;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) { message(player, "message.blockwheel.hold_block"); return null; }
        if (!stack.getComponentsPatch().isEmpty()) { message(player, "message.blockwheel.custom_data"); return null; }
        String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
        var settings = Blockwheel.BUILDERS.settings(player);
        var family = views.get(settings).family(blockId).orElse(null);
        if (family == null) { message(player, "message.blockwheel.no_family"); return null; }
        var member = family.member(blockId).orElseThrow();
        Session session = new Session(++nextToken, slot, stack.copy(), family, member, player.level().getServer().getTickCount());
        sessions.put(player.getUUID(), session);
        List<Payloads.Option> options = family.members().stream().map(form -> new Payloads.Option(
                form.shape().id(), form.shape().translationKey(), itemId(form.blockId()), form.shape().units())).toList();
        String familyItem = family.members().stream().filter(form -> form.shape().base()).findFirst()
                .map(form -> itemId(form.blockId())).orElse(itemId(blockId));
        return new Payloads.Wheel(session.token(), slot,
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), member.shape().units(), familyItem, options);
    }

    public void convert(ServerPlayer player, Payloads.Convert request) {
        Session session = sessions.get(player.getUUID());
        // A late packet must not invalidate a newer wheel session.
        if (session == null || session.token() != request.token()) return;
        sessions.remove(player.getUUID()); // Every snapshot can be spent only once.
        if (!usable(player) || player.level().getServer().getTickCount() - session.openedAt() > 2400
                || player.getInventory().getSelectedSlot() != session.slot()
                || !ItemStack.matches(session.source(), player.getInventory().getItem(session.slot()))) {
            message(player, "message.blockwheel.changed"); return;
        }
        var target = session.family().shape(request.shapeId()).orElse(null);
        if (target == null || target.blockId().equals(session.member().blockId())
                || !Blockwheel.BUILDERS.settings(player).shapeEnabled(target.shape().id())
                || !Blockwheel.BUILDERS.settings(player).shapeEnabled(session.member().shape().id())) return;
        Item item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(target.blockId())).asItem();
        if (item == Items.AIR) return;
        var plan = InventoryConversion.plan(player.getInventory(), session.slot(), session.family(), session.member(), target,
                Blockwheel.BUILDERS.settings(player).enabled());
        if (plan.produced() == 0) {
            var policy = Blockwheel.BUILDERS.supplyPolicy(player, false, false);
            if (Blockwheel.BUILDERS.settings(player).enabled() && policy.shulkers()) {
                var retrieval = Blockwheel.BUILDERS.planner(player).retrieveExact(player.getInventory(),
                        policy.ender() ? player.getEnderChestInventory() : null, policy, session.slot(), new ItemStack(item), plan.handLimit());
                if (retrieval != null && retrieval.transaction().commit()) {
                    player.inventoryMenu.broadcastChanges();
                    player.sendOverlayMessage(Component.translatable("message.blockwheel.retrieved", retrieval.count(), new ItemStack(item).getHoverName()));
                    return;
                }
            }
            player.sendOverlayMessage(Component.translatable("message.blockwheel.need_batch",
                    ConversionPlan.minimumInput(session.member().shape().units(), target.shape().units())));
            return;
        }
        var result = InventoryConversion.apply(player, session.slot(), item, plan);
        player.sendOverlayMessage(Component.translatable("message.blockwheel.converted", result.consumed(), result.produced(), new ItemStack(item).getHoverName()));
    }

    private static boolean usable(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && player.containerMenu == player.inventoryMenu;
    }
    private static String itemId(String blockId) {
        return BuiltInRegistries.ITEM.getKey(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)).asItem()).toString();
    }
    private static void message(ServerPlayer player, String key) { player.sendOverlayMessage(Component.translatable(key)); }
}
