package dev.blockwheel;

import dev.blockwheel.network.Payloads;
import dev.blockwheel.server.ConversionController;
import dev.blockwheel.server.BuilderController;
import dev.blockwheel.server.BuilderServerConfig;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Blockwheel implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blockwheel");
    public static final ConversionController CONVERSIONS = new ConversionController();
    public static final BuilderController BUILDERS = new BuilderController();
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("blockwheel", path); }

    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(BuilderPayloads.Update.TYPE, BuilderPayloads.Update.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BuilderPayloads.Status.TYPE, BuilderPayloads.Status.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BuilderPayloads.Feedback.TYPE, BuilderPayloads.Feedback.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuilderPayloads.PickPreferences.TYPE, BuilderPayloads.PickPreferences.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.PickPreferences.TYPE, (payload, context) -> BUILDERS.pickRules(context.player(), payload.rules()));
        PayloadTypeRegistry.clientboundPlay().register(BuilderPayloads.Catalog.TYPE, BuilderPayloads.Catalog.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuilderPayloads.SupplyQuery.TYPE, BuilderPayloads.SupplyQuery.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BuilderPayloads.SupplyCounts.TYPE, BuilderPayloads.SupplyCounts.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BuilderPayloads.SupplyInvalidated.TYPE, BuilderPayloads.SupplyInvalidated.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.SupplyQuery.TYPE, (payload, context) -> BUILDERS.querySupply(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.Update.TYPE, (payload, context) -> BUILDERS.configure(context.player(), payload.settings()));
        PayloadTypeRegistry.serverboundPlay().register(BuilderPayloads.FillTemplates.TYPE, BuilderPayloads.FillTemplates.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BuilderPayloads.FillTemplates.TYPE, (payload, context) -> BUILDERS.fillTemplates(context.player(), payload.containerId(), payload.includeHotbar()));
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Open.TYPE, Payloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Convert.TYPE, Payloads.Convert.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Wheel.TYPE, Payloads.Wheel.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Rejected.TYPE, Payloads.Rejected.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Open.TYPE, (payload, context) -> CONVERSIONS.open(context.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Convert.TYPE, (payload, context) -> CONVERSIONS.convert(context.player(), payload));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BUILDERS.setPolicy(BuilderServerConfig.load()); CONVERSIONS.reload(server); BUILDERS.reload(server, CONVERSIONS.catalog(), CONVERSIONS.experimentalCatalog());
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                BUILDERS.setPolicy(BuilderServerConfig.load()); CONVERSIONS.reload(server); BUILDERS.reload(server, CONVERSIONS.catalog(), CONVERSIONS.experimentalCatalog());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CONVERSIONS.clear(); BUILDERS.clear(); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> BUILDERS.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { CONVERSIONS.forget(handler.player); BUILDERS.forget(handler.player); });
    }
}
