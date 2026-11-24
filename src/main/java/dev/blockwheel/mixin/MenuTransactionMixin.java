package dev.blockwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.blockwheel.Blockwheel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class MenuTransactionMixin {
    @WrapMethod(method = "clicked")
    private void afterCompletedInput(int slot, int button, ContainerInput input, Player player, Operation<Void> original) {
        var menu = (AbstractContainerMenu) (Object) this;
        var before = player instanceof ServerPlayer server ? Blockwheel.BUILDERS.beforeInput(server, menu) : null;
        original.call(slot, button, input, player);
        if (player instanceof ServerPlayer server) Blockwheel.BUILDERS.afterInput(server, menu, before, input);
    }
    @WrapMethod(method = "removed")
    private void afterCursorReturned(Player player, Operation<Void> original) {
        var menu = (AbstractContainerMenu) (Object) this;
        var before = player instanceof ServerPlayer server ? Blockwheel.BUILDERS.beforeInput(server, menu) : null;
        original.call(player);
        if (player instanceof ServerPlayer server) Blockwheel.BUILDERS.afterInput(server, menu, before);
    }
}
