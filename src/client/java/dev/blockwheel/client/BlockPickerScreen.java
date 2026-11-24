package dev.blockwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import dev.blockwheel.core.PickRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Searchable, paginated picture grid of placeable block items. */
public final class BlockPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> select;
    private final Predicate<String> allowed;
    private final List<AbstractWidget> cells = new ArrayList<>();
    private EditBox search;
    private Button previous, next;
    private String query = "";
    private int page, pages, count, columns, rows, left;
    public BlockPickerScreen(Screen parent, Consumer<String> select, Predicate<String> allowed) {
        super(Component.translatable("ui.blockwheel.pick.choose_block")); this.parent = parent; this.select = select; this.allowed = allowed;
    }
    @Override protected void init() {
        cells.clear(); columns = Math.max(1, Math.min(16, (width - 32) / 26)); rows = Math.max(1, (height - 122) / 26);
        left = (width - columns * 26 + 2) / 2;
        search = addRenderableWidget(new EditBox(font, Math.max(16, width / 2 - 180), 38, Math.min(360, width - 32), 20,
                Component.translatable("ui.blockwheel.pick.search_blocks")));
        search.setMaxLength(128); search.setHint(Component.translatable("ui.blockwheel.pick.search_blocks")); search.setValue(query);
        search.setResponder(text -> { query = text; page = 0; refresh(); });
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; refresh(); }).bounds(width / 2 - 88, height - 54, 32, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; refresh(); }).bounds(width / 2 + 56, height - 54, 32, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose()).bounds(width / 2 - 60, height - 28, 120, 20).build());
        refresh(); setInitialFocus(search);
    }
    private void refresh() {
        cells.forEach(this::removeWidget); cells.clear();
        var filter = query.strip().toLowerCase(Locale.ROOT);
        var blocks = BuiltInRegistries.BLOCK.stream().filter(block -> block.asItem() instanceof BlockItem item && item.getBlock() == block)
                .map(block -> BuiltInRegistries.BLOCK.getKey(block).toString()).filter(id -> id.length() <= PickRules.MAX_ID_LENGTH).filter(allowed)
                .filter(id -> filter.isEmpty() || id.contains(filter) || BlockIconButton.name(id).getString().toLowerCase(Locale.ROOT).contains(filter))
                .sorted(Comparator.comparing((String id) -> BlockIconButton.name(id).getString().toLowerCase(Locale.ROOT)).thenComparing(id -> id)).toList();
        count = blocks.size(); int size = columns * rows; pages = Math.max(1, (count + size - 1) / size); page = Math.clamp(page, 0, pages - 1);
        for (int index = page * size; index < Math.min(count, (page + 1) * size); index++) {
            var id = blocks.get(index); int local = index - page * size;
            cells.add(addRenderableWidget(new BlockIconButton(left + local % columns * 26, 70 + local / columns * 26, id, () -> {
                select.accept(id); minecraft.gui.setScreen(parent);
            })));
        }
        previous.active = page > 0; next.active = page + 1 < pages;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) { page += vertical < 0 ? 1 : -1; refresh(); return true; } return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta); graphics.centeredText(font, title, width / 2, 15, 0xffffffff);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.pages", page + 1, pages, count), width / 2, height - 48, 0xffc8c8c8);
        if (count == 0) graphics.centeredText(font, Component.translatable("ui.blockwheel.pick.no_blocks"), width / 2, 85, 0xffc8c8c8);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
