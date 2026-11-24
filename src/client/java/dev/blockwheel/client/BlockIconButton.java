package dev.blockwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.RenderPipelines;

/** Native block-model preview with ordinary button focus, tooltip and narration. */
final class BlockIconButton extends Button {
    private final ItemStack stack;
    BlockIconButton(int x, int y, String id, Runnable press) {
        super(x, y, 24, 24, name(id), button -> press.run(), DEFAULT_NARRATION);
        stack = stack(id); setTooltip(Tooltip.create(name(id).copy().append(Component.literal("\n" + (id == null ? "" : id)))));
    }
    static ItemStack stack(String id) {
        if (id == null) return new ItemStack(Items.BARRIER);
        var item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).asItem();
        return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
    }
    static Component name(String id) {
        if (id == null) return Component.translatable("ui.blockwheel.pick.choose_target");
        var item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).asItem();
        return item == Items.AIR ? Component.literal(id) : new ItemStack(item).getHoverName();
    }
    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(!active ? "widget/button_disabled"
                : isHoveredOrFocused() ? "widget/button_highlighted" : "widget/button"), getX(), getY(), getWidth(), getHeight());
        graphics.fakeItem(stack, getX() + 4, getY() + 4);
    }
}
