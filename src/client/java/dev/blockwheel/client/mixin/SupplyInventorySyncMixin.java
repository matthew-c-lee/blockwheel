package dev.blockwheel.client.mixin;

import dev.blockwheel.client.BuilderClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Expire informational supply snapshots whenever vanilla synchronizes inventory contents. */
@Mixin(ClientPacketListener.class)
public abstract class SupplyInventorySyncMixin {
    @Inject(method = {"handleContainerSetSlot", "handleContainerContent", "handleSetPlayerInventory"}, at = @At("TAIL"))
    private void inventoryChanged(CallbackInfo callback) { BuilderClient.invalidateSupply(); }
}
