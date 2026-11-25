package dev.blockwheel.server;

import dev.blockwheel.Blockwheel;
import dev.blockwheel.core.BuilderSettings;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.FamilyCatalog;
import dev.blockwheel.core.PickRules;
import dev.blockwheel.core.PickDefaults;
import dev.blockwheel.core.FamilyViews;
import dev.blockwheel.data.RegistryPickDefaults;
import dev.blockwheel.data.WoodFamilyData;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.IdentityHashMap;

/** Event boundaries and authorization; all inventory calculation lives in the shared planner. */
public final class BuilderController {
    public record InputSnapshot(ItemStack[] inventory, ItemStack carried, boolean fromHotbar) {}
    private record CursorOrigin(AbstractContainerMenu menu, ItemStack carried) {}
    private final AttachmentType<BuilderSettings> settingsType = AttachmentRegistry.create(Blockwheel.id("builder_settings"),
            builder -> builder.initializer(BuilderSettings::defaults).persistent(BuilderSettings.CODEC).copyOnDeath());
    private BlockMaterials materials = new BlockMaterials(FamilyCatalog.empty());
    private BuilderInventory inventory = new BuilderInventory(materials);
    private BlockMaterials[] materialViews = {materials, materials, materials, materials};
    private BuilderInventory[] planners = {inventory, inventory, inventory, inventory};
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, CursorOrigin> cursors = new HashMap<>();
    private long revision;
    private volatile MinecraftServer server;
    private BuilderServerConfig policy = new BuilderServerConfig(true);
    private final Map<Container, ServerPlayer> enderOwners = new IdentityHashMap<>();
    private final Map<UUID, Integer> queries = new HashMap<>();
    private final Map<UUID, PickRules> pickRules = new HashMap<>();
    private PickDefaults pickDefaults = PickDefaults.EMPTY;
    public void pickRules(ServerPlayer player, PickRules rules) { pickRules.put(player.getUUID(), rules); }

    public BuilderSettings settings(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrCreate(settingsType);
    }
    public AttachmentType<BuilderSettings> settingsType() { return settingsType; }
    public BlockMaterials materials() { return materials; }
    public BuilderInventory planner() { return inventory; }
    public BlockMaterials materials(ServerPlayer player) { return materialViews[settings(player).catalogView()]; }
    public BuilderInventory planner(ServerPlayer player) { return planners[settings(player).catalogView()]; }
    public boolean active(ServerPlayer player, Feature feature) { return settings(player).active(feature); }
    public boolean enderAllowed() { return policy.allowRemoteEnderChestSupply(); }
    public void setPolicy(BuilderServerConfig policy) { this.policy = policy; }
    public MaterialSupply.Policy supplyPolicy(ServerPlayer player, boolean pick, boolean tooltips) {
        var settings = settings(player);
        boolean shulkers = settings.shulkerSupply() && (!pick || settings.feature(Feature.SHULKER_PICK_BLOCK))
                && (!tooltips || settings.feature(Feature.SHULKER_TOOLTIPS));
        boolean remote = shulkers && settings.feature(Feature.ENDER_ACCESS) && enderAllowed();
        return new MaterialSupply.Policy(shulkers, remote, pick || tooltips, pick && remote);
    }
    private boolean usable(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && !busy.contains(player.getUUID()); }

    public void configure(ServerPlayer player, BuilderSettings requested) {
        BuilderSettings previous = settings(player);
        ((AttachmentTarget) player).setAttached(settingsType, requested);
        cursors.remove(player.getUUID());
        boolean alternatesChanged = requested.catalogView() != previous.catalogView();
        if (alternatesChanged) Blockwheel.CONVERSIONS.forget(player);
        if (requested.active(Feature.NORMALIZE) && (!previous.active(Feature.NORMALIZE) || alternatesChanged) && usable(player)) {
            normalize(player, planner(player).storageFamilies(player.getInventory()), true);
        }
        if (alternatesChanged) sendCatalog(player);
        sendStatus(player);
        if (previous.enabled() != requested.enabled()) feedback(player, requested.enabled() ? BuilderPayloads.Feedback.MODE_ON : BuilderPayloads.Feedback.MODE_OFF);
    }
    private static void feedback(ServerPlayer player, int kind) {
        if (ServerPlayNetworking.canSend(player, BuilderPayloads.Feedback.TYPE)) ServerPlayNetworking.send(player, new BuilderPayloads.Feedback(kind));
    }

    public void reload(MinecraftServer server, FamilyCatalog catalog) {
        FamilyCatalog expanded;
        try { expanded = WoodFamilyData.expand(server.getResourceManager(), catalog); }
        catch (Exception error) { Blockwheel.LOGGER.error("Experimental wood data rejected; using ordinary families", error); expanded = catalog; }
        reload(server, catalog, expanded);
    }
    public void reload(MinecraftServer server, FamilyCatalog catalog, FamilyCatalog expanded) {
        this.server = server;
        var views = new FamilyViews(catalog, expanded);
        var prepared = new BlockMaterials[4]; var preparedPlanners = new BuilderInventory[4];
        for (int index = 0; index < 4; index++) { prepared[index] = new BlockMaterials(views.get(index)); preparedPlanners[index] = new BuilderInventory(prepared[index]); }
        materialViews = prepared; planners = preparedPlanners; materials = prepared[0]; inventory = preparedPlanners[0];
        revision++;
        pickDefaults = RegistryPickDefaults.create();
        cursors.clear();
        for (var player : server.getPlayerList().getPlayers()) { sendCatalog(player); sendStatus(player); }
    }
    public void forget(ServerPlayer player) {
        busy.remove(player.getUUID()); cursors.remove(player.getUUID()); queries.remove(player.getUUID());
        pickRules.remove(player.getUUID());
        enderOwners.entrySet().removeIf(entry -> entry.getValue().getUUID().equals(player.getUUID()));
    }
    public void clear() {
        server = null;
        busy.clear(); cursors.clear(); enderOwners.clear(); queries.clear();
        pickRules.clear(); pickDefaults = PickDefaults.EMPTY;
        materials = new BlockMaterials(FamilyCatalog.empty()); inventory = new BuilderInventory(materials);
        materialViews = new BlockMaterials[]{materials, materials, materials, materials}; planners = new BuilderInventory[]{inventory, inventory, inventory, inventory};
    }
    private void trackEnder(ServerPlayer player) {
        enderOwners.entrySet().removeIf(entry -> entry.getValue().getUUID().equals(player.getUUID()) && entry.getKey() != player.getEnderChestInventory());
        enderOwners.put(player.getEnderChestInventory(), player);
    }
    public void enderChanged(Container container) {
        var current = server;
        if (current == null || !current.isSameThread()) return;
        var player = enderOwners.get(container);
        if (player != null) invalidateSupply(player);
    }
    public void join(ServerPlayer player) { trackEnder(player); sendCatalog(player); sendStatus(player); }
    private void sendStatus(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, BuilderPayloads.Status.TYPE)) ServerPlayNetworking.send(player, new BuilderPayloads.Status(settings(player), enderAllowed()));
        invalidateSupply(player);
    }
    public static void invalidateSupply(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, BuilderPayloads.SupplyInvalidated.TYPE)) ServerPlayNetworking.send(player, new BuilderPayloads.SupplyInvalidated());
    }
    public MaterialSupply.Amounts supplyAmounts(ServerPlayer player, Item item) {
        var materials = materials(player);
        var target = materials.resolve(item);
        if (target == null) return new MaterialSupply.Amounts(0, 0, 0, 0);
        var sources = supplyPolicy(player, false, true);
        var transaction = new InventoryTransaction(player.getInventory(), sources.ender() ? player.getEnderChestInventory() : null);
        return MaterialSupply.amounts(transaction, MaterialSupply.collect(transaction, materials, target, sources));
    }
    public void querySupply(ServerPlayer player, BuilderPayloads.SupplyQuery query) {
        if (!active(player, Feature.TOOLTIPS) || !settings(player).shulkerSupply() || !settings(player).feature(Feature.SHULKER_TOOLTIPS)
                || query.request() < 0 || !ServerPlayNetworking.canSend(player, BuilderPayloads.SupplyCounts.TYPE)) return;
        int now = player.level().getServer().getTickCount();
        if (now - queries.getOrDefault(player.getUUID(), now - 5) < 5) return;
        queries.put(player.getUUID(), now);
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(query.itemId()));
            var material = materials(player).resolve(item);
            if (material == null) return;
            trackEnder(player);
            var amounts = supplyAmounts(player, item);
            ServerPlayNetworking.send(player, new BuilderPayloads.SupplyCounts(query.request(), material.family().id(),
                    amounts.loose(), amounts.carried(), amounts.ender(), amounts.protectedUnits()));
        } catch (IllegalArgumentException ignored) { /* Invalid item identifiers grant no storage access. */ }
    }
    private void sendCatalog(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, BuilderPayloads.Catalog.TYPE)) return;
        List<BuilderPayloads.Family> chunk = new ArrayList<>();
        int bytes = 0, part = 0;
        for (var family : materials(player).families()) {
            String base = BuiltInRegistries.ITEM.getKey(family.base().item()).toString();
            var forms = family.forms().stream().map(form -> new BuilderPayloads.Form(form.shapeId(),
                    BuiltInRegistries.ITEM.getKey(form.item()).toString(), form.units())).toList();
            int estimate = family.id().length() + base.length() + 32;
            for (var form : forms) estimate += form.shapeId().length() + form.itemId().length() + 16;
            if (!chunk.isEmpty() && (bytes + estimate > 48_000 || chunk.size() == 128)) {
                ServerPlayNetworking.send(player, new BuilderPayloads.Catalog(revision, part++, false, chunk));
                chunk = new ArrayList<>(); bytes = 0;
            }
            chunk.add(new BuilderPayloads.Family(family.id(), base, family.base().units(), forms));
            bytes += estimate;
        }
        ServerPlayNetworking.send(player, new BuilderPayloads.Catalog(revision, part, true, chunk));
    }

    /** Null means vanilla owns this pickup; false means leave it on the ground unchanged. */
    public Boolean pickup(ServerPlayer player, ItemStack incoming) {
        if (!active(player, Feature.PICKUP) || !player.isAlive() || player.isSpectator() || materials(player).resolve(incoming) == null) return null;
        if (busy.contains(player.getUUID())) return false;
        busy.add(player.getUUID());
        try {
            var original = incoming.copy();
            var plan = planner(player).pickup(player.getInventory(), incoming);
            if (plan == null || plan.count() == 0 || !ItemStack.matches(original, incoming) || !plan.transaction().commit()) return false;
            incoming.shrink(plan.count());
            sync(player);
            return true;
        } finally { busy.remove(player.getUUID()); }
    }

    public void afterPlacement(ServerPlayer player, int slot, ItemStack previous) {
        afterUse(player, slot, previous);
    }

    /** Called only after a successful use or completed consumption, never for inventory moves/drops. */
    public void afterUse(ServerPlayer player, int slot, ItemStack previous) {
        if (!active(player, Feature.RESTOCK) || !usable(player) || player.containerMenu != player.inventoryMenu
                || !player.containerMenu.getCarried().isEmpty() || !BuilderInventory.isRestockSlot(player.getInventory(), slot)
                || previous.isEmpty() || player.hasInfiniteMaterials()
                || materials(player).resolve(previous) == null && !settings(player).shulkerSupply()) return;
        var current = player.getInventory().getItem(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(previous, current)) return;
        var restock = settings(player).restock();
        if (current.getCount() >= previous.getCount() || current.getCount() >= restock.below()) return;
        busy.add(player.getUUID());
        try {
            var sources = supplyPolicy(player, false, false);
            if (!restock.nonBlockShulkers() && !(previous.getItem() instanceof BlockItem)) sources = MaterialSupply.Policy.mainOnly();
            var plan = planner(player).restock(player.getInventory(), sources.ender() ? player.getEnderChestInventory() : null,
                    sources, slot, previous, restock.amount());
            if (plan != null && plan.transaction().commit()) sync(player);
        } finally { busy.remove(player.getUUID()); }
    }

    public boolean fillTemplates(ServerPlayer player, int containerId) {
        return fillTemplates(player, containerId, false);
    }
    public boolean fillTemplates(ServerPlayer player, int containerId, boolean includeHotbar) {
        var menu = player.containerMenu;
        if (!usable(player) || menu.containerId != containerId || !menu.getCarried().isEmpty()) return false;
        boolean openEnder = menu instanceof ChestMenu chest && chest.getContainer() == player.getEnderChestInventory();
        boolean carried = settings(player).feature(Feature.EXPERIMENTAL_WOOD);
        boolean remote = settings(player).feature(Feature.ENDER_ACCESS) && enderAllowed();
        if (!openEnder && (menu != player.inventoryMenu || !remote && !carried)) return false;
        busy.add(player.getUUID());
        try {
            var plan = TemplateDeposit.plan(player.getInventory(), openEnder || remote ? player.getEnderChestInventory() : null,
                    planner(player), settings(player).enabled(), carried, includeHotbar);
            if (plan.count() == 0 || player.containerMenu != menu || !plan.transaction().commit()) return false;
            cursors.remove(player.getUUID()); sync(player); feedback(player, BuilderPayloads.Feedback.DEPOSIT); return true;
        } finally { busy.remove(player.getUUID()); }
    }

    public boolean pick(ServerPlayer player, ItemStack requested) {
        if (!active(player, Feature.PICK_BLOCK) || !usable(player) || player.hasInfiniteMaterials()
                || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
                || requested.isEmpty() || !requested.isItemEnabled(player.level().enabledFeatures())
                || player.getInventory().findSlotMatchingItem(requested) != -1) return false;
        var sources = supplyPolicy(player, true, false);
        busy.add(player.getUUID());
        try {
            if (pickSupplied(player, requested, sources)) return true;
            var rules = pickRules.get(player.getUUID());
            if (rules == null || !rules.enabled() || !(requested.getItem() instanceof BlockItem requestedBlock)
                    || !requested.getComponentsPatch().isEmpty()) return false;
            var targetId = BuiltInRegistries.BLOCK.getKey(requestedBlock.getBlock()).toString();
            for (var id : rules.effective(pickDefaults).getOrDefault(targetId, List.of())) {
                var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
                var item = block.asItem();
                if (!(item instanceof BlockItem) || !item.isEnabled(player.level().enabledFeatures())) continue;
                var alternative = new ItemStack(item);
                int existing = player.getInventory().findSlotMatchingItem(alternative);
                if (existing >= 0 && existing < 36) {
                    if (existing < 9) player.getInventory().setSelectedSlot(existing);
                    else player.getInventory().pickSlot(existing);
                    picked(player); return true;
                }
                if (pickSupplied(player, alternative, sources)) return true;
            }
            return false;
        } finally { busy.remove(player.getUUID()); }
    }

    private boolean pickSupplied(ServerPlayer player, ItemStack requested, MaterialSupply.Policy sources) {
        var materials = materials(player);
        var target = materials.resolve(requested);
        if (target == null && !sources.shulkers()) return false;
        List<Integer> destinations = new ArrayList<>(); destinations.add(player.getInventory().getSuitableHotbarSlot());
        for (int slot = 0; slot < 9; slot++) {
            var member = materials.resolve(player.getInventory().getItem(slot));
            if (target != null && member != null && member.family() == target.family() && !destinations.contains(slot)) destinations.add(slot);
        }
        for (int slot : destinations) {
            var plan = planner(player).pick(player.getInventory(), sources.ender() ? player.getEnderChestInventory() : null, sources, slot, requested);
            if (plan == null) continue;
            if (!plan.transaction().commit()) return false;
            player.getInventory().setSelectedSlot(slot); picked(player); return true;
        }
        return false;
    }
    private static void picked(ServerPlayer player) {
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot())); sync(player);
    }

    public InputSnapshot beforeInput(ServerPlayer player, AbstractContainerMenu menu) {
        if (!active(player, Feature.NORMALIZE) || !usable(player)) return null;
        ItemStack[] before = new ItemStack[36];
        for (int i = 0; i < before.length; i++) before[i] = player.getInventory().getItem(i).copy();
        var cursor = cursors.get(player.getUUID());
        var carried = menu.getCarried().copy();
        boolean fromHotbar = cursor != null && cursor.menu() == menu && ItemStack.matches(cursor.carried(), carried);
        return new InputSnapshot(before, carried, fromHotbar);
    }

    public void afterInput(ServerPlayer player, AbstractContainerMenu menu, InputSnapshot before) {
        afterInput(player, menu, before, ContainerInput.PICKUP);
    }

    public void afterInput(ServerPlayer player, AbstractContainerMenu menu, InputSnapshot before, ContainerInput input) {
        if (before == null || !active(player, Feature.NORMALIZE) || !usable(player)) return;
        var current = player.getInventory();
        Map<BlockMaterials.Family, List<Integer>> destinations = new LinkedHashMap<>();
        for (int i = 9; i < 36; i++) {
            var stack = current.getItem(i);
            var previous = before.inventory()[i];
            if (ItemStack.matches(previous, stack)) continue;
            // Experimental wood has primary/secondary storage forms. Re-evaluate its main pool
            // after any native storage change, including rearrangements and container arrivals.
            // Cursor, hotbar, equipment and external-container contents remain outside that pool.
            var oldMaterial = materials(player).resolve(previous);
            if (oldMaterial != null && oldMaterial.family().logBased())
                destinations.computeIfAbsent(oldMaterial.family(), family -> new ArrayList<>());
            var material = materials(player).resolve(stack);
            if (material != null && material.family().logBased()) {
                var slots = destinations.computeIfAbsent(material.family(), family -> new ArrayList<>());
                if (increased(previous, stack)) slots.add(i);
                continue;
            }
            if (material == null || material.form().base() || !increased(previous, stack)) continue;
            if (before.fromHotbar() && ItemStack.isSameItemSameComponents(before.carried(), stack)
                    || removedFromHotbar(before.inventory(), current, stack)) {
                destinations.computeIfAbsent(material.family(), family -> new ArrayList<>()).add(i);
            }
        }
        var carried = menu.getCarried();
        boolean fromHotbar = !carried.isEmpty() && (before.fromHotbar()
                && ItemStack.isSameItemSameComponents(before.carried(), carried)
                || removedFromHotbar(before.inventory(), current, carried));
        if (fromHotbar) cursors.put(player.getUUID(), new CursorOrigin(menu, carried.copy()));
        else cursors.remove(player.getUUID());
        // Vanilla's menu-input handler owns synchronization after this completed click/drag.
        // Quick move chooses storage automatically, so merge converted output before using empty slots.
        // Direct clicks, swaps and drags keep the destination the player explicitly chose.
        normalize(player, destinations.keySet(), false, input == ContainerInput.QUICK_MOVE ? Map.of() : destinations);
    }

    private static boolean increased(ItemStack before, ItemStack after) {
        return !after.isEmpty() && (!ItemStack.isSameItemSameComponents(before, after) || after.getCount() > before.getCount());
    }
    private static boolean removedFromHotbar(ItemStack[] before, net.minecraft.world.entity.player.Inventory after, ItemStack item) {
        if (item.isEmpty()) return false;
        for (int i = 0; i < 9; i++) {
            if (!ItemStack.isSameItemSameComponents(before[i], item)) continue;
            var current = after.getItem(i);
            if (!ItemStack.isSameItemSameComponents(current, before[i]) || current.getCount() < before[i].getCount()) return true;
        }
        return false;
    }
    private void normalize(ServerPlayer player, Set<BlockMaterials.Family> families, boolean synchronize) {
        normalize(player, families, synchronize, Map.of());
    }
    private void normalize(ServerPlayer player, Set<BlockMaterials.Family> families, boolean synchronize,
                           Map<BlockMaterials.Family, List<Integer>> destinations) {
        if (families.isEmpty() || busy.contains(player.getUUID())) return;
        busy.add(player.getUUID());
        try {
            var plan = planner(player).normalize(player.getInventory(), families, destinations);
            if (plan.transaction().commit() && synchronize) sync(player);
        } finally { busy.remove(player.getUUID()); }
    }
    public static void sync(ServerPlayer player) {
        player.containerMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.broadcastChanges();
        invalidateSupply(player);
    }
}
