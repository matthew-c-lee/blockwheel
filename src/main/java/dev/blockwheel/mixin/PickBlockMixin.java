package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blockwheel.Blockwheel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The requested item has already been derived from the world and reach-checked by vanilla. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PickBlockMixin {
    @Shadow public ServerPlayer player;
    @WrapMethod(method = "tryPickItem")
    private void familyAwarePick(ItemStack requested, Operation<Void> original) {
        var menu = player.containerMenu;
        var before = Blockwheel.BUILDERS.beforeInput(player, menu);
        if (!Blockwheel.BUILDERS.pick(player, requested)) original.call(requested);
        Blockwheel.BUILDERS.afterInput(player, menu, before);
        if (before != null) dev.blockwheel.server.BuilderController.sync(player);
    }
}
