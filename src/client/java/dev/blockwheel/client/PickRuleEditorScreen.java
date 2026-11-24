package dev.blockwheel.client;

import dev.blockwheel.core.PickRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Select a target and edit its ordered alternatives using native block previews. */
public final class PickRuleEditorScreen extends Screen {
    private final PickRulesScreen parent;
    private final String original;
    private String target, error;
    private final List<String> alternatives = new ArrayList<>();
    private int page, pages, left, contentWidth, pageSize;
    public PickRuleEditorScreen(PickRulesScreen parent, PickRules.Rule rule) {
        super(Component.translatable("ui.blockwheel.pick.edit_rule")); this.parent = parent;
        original = rule == null ? null : rule.target(); target = original;
        if (rule != null) alternatives.addAll(rule.fallbacks());
    }
    @Override protected void init() {
        contentWidth = Math.min(480, width - 32); left = (width - contentWidth) / 2; pageSize = Math.max(1, (height - 178) / 28);
        pages = Math.max(1, (alternatives.size() + pageSize - 1) / pageSize); page = Math.clamp(page, 0, pages - 1);
        Runnable chooseTarget = () -> minecraft.gui.setScreen(new BlockPickerScreen(this, id -> target = id,
                id -> (id.equals(target) || !parent.draft().effective(PickRulesClient.defaults()).containsKey(id)) && !alternatives.contains(id)));
        addRenderableWidget(new BlockIconButton(left, 48, target, chooseTarget));
        addRenderableWidget(Button.builder(BlockIconButton.name(target), button -> chooseTarget.run()).bounds(left + 28, 50, contentWidth - 28, 20).build());
        var add = addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.add_fallback"), button ->
                minecraft.gui.setScreen(new BlockPickerScreen(this, alternatives::add, id -> !id.equals(target) && !alternatives.contains(id))))
                .bounds(left, 80, 130, 20).build());
        add.active = target != null && alternatives.size() < PickRules.MAX_FALLBACKS;
        if (original != null && PickRulesClient.defaults().containsKey(original)) {
            addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.restore_default"), button -> {
                parent.restore(original); minecraft.gui.setScreen(parent);
            }).bounds(left + contentWidth - 130, 80, 130, 20).build());
        }
        for (int index = page * pageSize; index < Math.min(alternatives.size(), (page + 1) * pageSize); index++) {
            int item = index, y = 110 + (index - page * pageSize) * 28; var id = alternatives.get(index);
            Runnable replace = () -> minecraft.gui.setScreen(new BlockPickerScreen(this, value -> alternatives.set(item, value),
                    value -> !value.equals(target) && (!alternatives.contains(value) || value.equals(id))));
            addRenderableWidget(Button.builder(Component.literal(Integer.toString(index + 1)), button -> replace.run()).bounds(left, y + 2, 20, 20).build());
            addRenderableWidget(new BlockIconButton(left + 24, y, id, replace));
            addRenderableWidget(Button.builder(BlockIconButton.name(id), button -> replace.run()).bounds(left + 52, y + 2, contentWidth - 156, 20).build());
            var up = addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.up"), button -> {
                Collections.swap(alternatives, item, item - 1); rebuildWidgets();
            }).bounds(left + contentWidth - 100, y + 2, 25, 20).build()); up.active = index > 0;
            var down = addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.down"), button -> {
                Collections.swap(alternatives, item, item + 1); rebuildWidgets();
            }).bounds(left + contentWidth - 73, y + 2, 25, 20).build()); down.active = index + 1 < alternatives.size();
            addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.remove"), button -> {
                alternatives.remove(item); rebuildWidgets();
            }).bounds(left + contentWidth - 46, y + 2, 46, 20).build());
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; rebuildWidgets(); }).bounds(width / 2 - 86, height - 60, 28, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; rebuildWidgets(); }).bounds(width / 2 + 58, height - 60, 28, 20).build()); next.active = page + 1 < pages;
        var apply = addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.apply_rule"), button -> {
            if (parent.accept(original, new PickRules.Rule(target, alternatives))) minecraft.gui.setScreen(parent);
            else error = "ui.blockwheel.pick.limit";
        }).bounds(width / 2 - 132, height - 32, 130, 20).build()); apply.active = target != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(width / 2 + 2, height - 32, 130, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta); graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.order_hint"), width / 2, 27, 0xffb8b8b8);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.page", page + 1, pages), width / 2, height - 54, 0xffc8c8c8);
        if (error != null) graphics.centeredText(font, Component.translatable(error), width / 2, height - 80, 0xffff8c8c);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
