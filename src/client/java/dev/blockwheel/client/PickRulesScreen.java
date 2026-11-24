package dev.blockwheel.client;

import dev.blockwheel.core.PickRules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** A staged editor: rule changes are saved together, while Cancel leaves the JSON untouched. */
public final class PickRulesScreen extends Screen {
    private final Screen parent;
    private PickRules draft = PickRulesClient.rules();
    private final List<AbstractWidget> rows = new ArrayList<>();
    private String query = "", error;
    private int page, pages, left, contentWidth, pageSize;
    private Button previous, next;
    public PickRulesScreen(Screen parent) { super(Component.translatable("ui.blockwheel.pick.rules_title")); this.parent = parent; }
    @Override protected void init() {
        rows.clear(); contentWidth = Math.min(480, width - 32); left = (width - contentWidth) / 2; pageSize = Math.max(1, (height - 160) / 28);
        var search = addRenderableWidget(new EditBox(font, left, 44, contentWidth - 108, 20, Component.translatable("ui.blockwheel.pick.search_rules")));
        search.setMaxLength(128); search.setHint(Component.translatable("ui.blockwheel.pick.search_rules")); search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.add_rule"), button -> minecraft.gui.setScreen(new PickRuleEditorScreen(this, null)))
                .bounds(left + contentWidth - 100, 44, 100, 20).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; refresh(); }).bounds(width / 2 - 86, height - 64, 28, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; refresh(); }).bounds(width / 2 + 58, height - 64, 28, 20).build());
        int segment = (contentWidth - 4) / 3;
        addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.save"), button -> {
            if (PickRulesClient.save(draft)) onClose(); else error = PickRulesClient.error();
        }).bounds(left, height - 32, segment, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(left + segment + 2, height - 32, segment, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.reload"), button -> {
            if (PickRulesClient.reload()) { draft = PickRulesClient.rules(); error = null; page = 0; refresh(); }
            else error = PickRulesClient.error();
        }).bounds(left + (segment + 2) * 2, height - 32, segment, 20).build());
        refresh(); setInitialFocus(search);
    }
    PickRules draft() { return draft; }
    boolean accept(String previousTarget, PickRules.Rule rule) {
        try {
            var next = draft;
            if (previousTarget != null && !previousTarget.equals(rule.target())) next = remove(next, previousTarget);
            draft = next.put(rule); error = null; return true;
        } catch (IllegalArgumentException exception) { error = "ui.blockwheel.pick.limit"; return false; }
    }
    void restore(String target) { draft = draft.withoutOverride(target); error = null; }
    private PickRules remove(PickRules value, String target) {
        return value.hasDefault(target, PickRulesClient.defaults())
                ? value.put(new PickRules.Rule(target, List.of())) : value.withoutOverride(target);
    }
    private void refresh() {
        rows.forEach(this::removeWidget); rows.clear();
        var filter = query.strip().toLowerCase(Locale.ROOT);
        var rules = draft.effective(PickRulesClient.defaults()).entrySet().stream()
                .filter(entry -> filter.isEmpty() || entry.getKey().contains(filter) || BlockIconButton.name(entry.getKey()).getString().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing(entry -> entry.getKey())).toList();
        pages = Math.max(1, (rules.size() + pageSize - 1) / pageSize); page = Math.clamp(page, 0, pages - 1);
        for (int index = page * pageSize; index < Math.min(rules.size(), (page + 1) * pageSize); index++) {
            var entry = rules.get(index); var rule = new PickRules.Rule(entry.getKey(), entry.getValue());
            int y = 78 + (index - page * pageSize) * 28;
            Runnable edit = () -> minecraft.gui.setScreen(new PickRuleEditorScreen(this, rule));
            rows.add(addRenderableWidget(new BlockIconButton(left, y, rule.target(), edit)));
            var label = Component.translatable("ui.blockwheel.pick.rule_row", BlockIconButton.name(rule.target()), rule.fallbacks().size());
            rows.add(addRenderableWidget(Button.builder(label, button -> edit.run()).bounds(left + 28, y + 2, contentWidth - 84, 20).build()));
            rows.add(addRenderableWidget(Button.builder(Component.translatable("ui.blockwheel.pick.remove"), button -> {
                try { draft = remove(draft, rule.target()); error = null; refresh(); }
                catch (IllegalArgumentException exception) { error = "ui.blockwheel.pick.limit"; }
            }).bounds(left + contentWidth - 52, y + 2, 52, 20).build()));
        }
        previous.active = page > 0; next.active = page + 1 < pages;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) { page += vertical < 0 ? 1 : -1; refresh(); return true; } return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta); graphics.centeredText(font, title, width / 2, 12, 0xffffffff);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.order_hint"), width / 2, 27, 0xffb8b8b8);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.page", page + 1, pages), width / 2, height - 58, 0xffc8c8c8);
        if (error != null) graphics.centeredText(font, Component.translatable(error), width / 2, height - 82, 0xffff8c8c);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
