package dev.blockwheel.client;

import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.core.BuilderSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/** Manual wheel options first; optional inventory and retrieval policies have their own pages. */
public final class BuilderSettingsScreen extends Screen {
    private final Screen parent;
    private enum Page {
        WHEEL("ui.blockwheel.radial_tab"), BUILDER("ui.blockwheel.general_tab"),
        RESTOCK("ui.blockwheel.restock_tab"), SUPPLY("ui.blockwheel.shulker_tab"),
        PICK("ui.blockwheel.pick.tab"), INTERFACE("ui.blockwheel.interface_tab");
        final String label;
        Page(String label) { this.label = label; }
    }
    private final Map<Feature, Button> buttons = new EnumMap<>(Feature.class);
    private Button master, nonBlockShulkers;
    private Page page = Page.WHEEL;
    private BuilderSettings editing = BuilderClient.settings();

    public BuilderSettingsScreen(Screen parent) {
        super(Component.translatable("ui.blockwheel.settings.title")); this.parent = parent;
    }
    @Override protected void init() {
        buttons.clear(); master = null; nonBlockShulkers = null;
        int tabWidth = Math.min(390, width - 8) / Page.values().length;
        for (var value : Page.values()) {
            var tab = addRenderableWidget(Button.builder(Component.translatable(value.label), button -> {
                page = value; rebuildWidgets();
            }).bounds((width - tabWidth * Page.values().length) / 2 + value.ordinal() * tabWidth, 36, tabWidth - 2, 20).build());
            tab.active = page != value;
        }
        int x = width / 2 - 125, y = Math.max(92, Math.min(104, height - 156));
        switch (page) {
            case WHEEL -> {
                feature(Feature.WOOD_TRAPDOORS, y); y += 24;
                feature(Feature.EXPERIMENTAL_WOOD, y); y += 24;
                var preset = local("ui.blockwheel.interface.radial_only", () -> {
                    if (InterfacePreferencesClient.save(InterfacePreferencesClient.settings().withBuilderButtonShown(false).withShulkerControlsShown(false)))
                        edit(settings -> settings.withEnabled(false));
                }, x, y, 250);
                preset.active = BuilderClient.available(); y += 24;
            }
            case BUILDER -> {
                master = addRenderableWidget(new BuilderSwitchButton(x, y, 250, BuilderClient.modeLabel(), () ->
                        edit(settings -> settings.withEnabled(!settings.enabled())), () -> BuilderClient.settings().enabled()));
                master.setTooltip(Tooltip.create(Component.translatable("ui.blockwheel.builder_tooltip")));
                master.active = BuilderClient.available(); y += 24;
                for (var feature : new Feature[]{Feature.PICKUP, Feature.NORMALIZE, Feature.TOOLTIPS}) { feature(feature, y); y += 24; }
            }
            case RESTOCK -> {
                feature(Feature.RESTOCK, y); y += 24;
                var below = addRenderableWidget(new RestockSlider(x, y, "ui.blockwheel.restock_below", editing.restock().below(),
                        value -> edit(settings -> settings.withRestock(settings.restock().withBelow(value)))));
                below.active = BuilderClient.available(); below.setTooltip(help("ui.blockwheel.restock_below")); y += 24;
                var amount = addRenderableWidget(new RestockSlider(x, y, "ui.blockwheel.restock_amount", editing.restock().amount(),
                        value -> edit(settings -> settings.withRestock(settings.restock().withAmount(value)))));
                amount.active = BuilderClient.available(); amount.setTooltip(help("ui.blockwheel.restock_amount")); y += 24;
            }
            case SUPPLY -> {
                for (var feature : new Feature[]{Feature.SHULKER_SUPPLY, Feature.ENDER_ACCESS, Feature.SHULKER_PICK_BLOCK, Feature.SHULKER_TOOLTIPS}) {
                    feature(feature, y); y += 24;
                }
                nonBlockShulkers = addRenderableWidget(Button.builder(nonBlockLabel(), button ->
                        edit(settings -> settings.withRestock(settings.restock().withNonBlockShulkers(!settings.restock().nonBlockShulkers()))))
                        .bounds(x, y, 250, 20).tooltip(help("ui.blockwheel.restock_non_blocks")).build());
                nonBlockShulkers.active = BuilderClient.available(); y += 24;
            }
            case PICK -> {
                feature(Feature.PICK_BLOCK, y); y += 24;
                localToggle("ui.blockwheel.pick.enabled", PickRulesClient.rules().enabled(), () ->
                        PickRulesClient.save(PickRulesClient.rules().withEnabled(!PickRulesClient.rules().enabled())), x, y, 250); y += 24;
                localToggle("ui.blockwheel.pick.log_defaults", PickRulesClient.rules().includeLogDefaults(), () ->
                        PickRulesClient.save(PickRulesClient.rules().withLogDefaults(!PickRulesClient.rules().includeLogDefaults())), x, y, 82);
                localToggle("ui.blockwheel.pick.anvil_defaults", PickRulesClient.rules().includeAnvilDefaults(), () ->
                        PickRulesClient.save(PickRulesClient.rules().withAnvilDefaults(!PickRulesClient.rules().includeAnvilDefaults())), x + 84, y, 82);
                localToggle("ui.blockwheel.pick.copper_defaults", PickRulesClient.rules().includeCopperDefaults(), () ->
                        PickRulesClient.save(PickRulesClient.rules().withCopperDefaults(!PickRulesClient.rules().includeCopperDefaults())), x + 168, y, 82); y += 24;
                local("ui.blockwheel.pick.edit_rules", () -> minecraft.gui.setScreen(new PickRulesScreen(this)), x, y, 250); y += 24;
            }
            case INTERFACE -> {
                localToggle("ui.blockwheel.interface.show_builder", InterfacePreferencesClient.settings().showBuilderButton(), () ->
                        InterfacePreferencesClient.save(InterfacePreferencesClient.settings().withBuilderButtonShown(!InterfacePreferencesClient.settings().showBuilderButton())), x, y, 250); y += 24;
                localToggle("ui.blockwheel.interface.show_refill", InterfacePreferencesClient.settings().showShulkerRefillControls(), () ->
                        InterfacePreferencesClient.save(InterfacePreferencesClient.settings().withShulkerControlsShown(!InterfacePreferencesClient.settings().showShulkerRefillControls())), x, y, 250); y += 24;
                localToggle("ui.blockwheel.interface.hide_tooltips", InterfacePreferencesClient.settings().hideButtonTooltips(), () ->
                        InterfacePreferencesClient.save(InterfacePreferencesClient.settings().withButtonTooltipsHidden(!InterfacePreferencesClient.settings().hideButtonTooltips())), x, y, 250); y += 24;
                localToggle("ui.blockwheel.interface.hide_messages", InterfacePreferencesClient.settings().hideBuilderMessages(), () ->
                        InterfacePreferencesClient.save(InterfacePreferencesClient.settings().withBuilderMessagesHidden(!InterfacePreferencesClient.settings().hideBuilderMessages())), x, y, 250); y += 24;
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 75, Math.min(height - 28, y + 12), 150, 20).build());
    }
    private void feature(Feature feature, int y) {
        var button = addRenderableWidget(Button.builder(label(feature), clicked ->
                edit(settings -> settings.withFeature(feature, !settings.feature(feature))))
                .bounds(width / 2 - 125, y, 250, 20).tooltip(help(key(feature))).build());
        button.active = featureAvailable(feature); buttons.put(feature, button);
    }
    private Button local(String key, Runnable action, int x, int y, int width) {
        return addRenderableWidget(Button.builder(Component.translatable(key), button -> action.run())
                .bounds(x, y, width, 20).tooltip(help(key)).build());
    }
    private void localToggle(String key, boolean value, Runnable change, int x, int y, int width) {
        var button = local(key, () -> { change.run(); rebuildWidgets(); }, x, y, width);
        button.setMessage(interfaceLabel(key, value));
    }
    private static Tooltip help(String key) { return Tooltip.create(Component.translatable(key + ".tooltip")); }
    private static boolean featureAvailable(Feature feature) {
        return BuilderClient.available() && (feature != Feature.ENDER_ACCESS || BuilderClient.enderAllowed());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (master != null) { master.setMessage(BuilderClient.modeLabel()); master.active = BuilderClient.available(); }
        if (nonBlockShulkers != null) { nonBlockShulkers.setMessage(nonBlockLabel()); nonBlockShulkers.active = BuilderClient.available(); }
        buttons.forEach((feature, button) -> { button.setMessage(label(feature)); button.active = featureAvailable(feature); });
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 14, 0xffffffff);
        graphics.centeredText(font, Component.translatable("ui.blockwheel.settings." + page.name().toLowerCase(Locale.ROOT) + ".description"), width / 2, 64, 0xffd0d0d0);
        Component scope;
        if (page == Page.INTERFACE) scope = Component.translatable("ui.blockwheel.interface.instance_preferences");
        else if (page == Page.PICK) scope = Component.translatable("ui.blockwheel.settings.pick_scope", BuilderClient.modeLabel());
        else if (!BuilderClient.available()) scope = Component.translatable("ui.blockwheel.world_required");
        else if (page == Page.WHEEL) scope = Component.translatable("ui.blockwheel.settings.wheel_scope");
        else scope = Component.translatable("ui.blockwheel.settings.automatic_scope", BuilderClient.modeLabel());
        graphics.centeredText(font, scope, width / 2, 78, 0xffaaaaaa);
        if (page == Page.PICK && PickRulesClient.error() != null) graphics.centeredText(font, Component.translatable(PickRulesClient.error()), width / 2, height - 12, 0xffff8c8c);
        if (page == Page.INTERFACE && InterfacePreferencesClient.error() != null) graphics.centeredText(font, Component.translatable(InterfacePreferencesClient.error()), width / 2, height - 12, 0xffff8c8c);
    }
    private void edit(UnaryOperator<BuilderSettings> change) { editing = change.apply(editing); BuilderClient.request(editing); }
    private static Component nonBlockLabel() {
        return interfaceLabel("ui.blockwheel.restock_non_blocks", BuilderClient.settings().restock().nonBlockShulkers());
    }
    private static Component interfaceLabel(String key, boolean value) {
        return Component.translatable("ui.blockwheel.feature_state", Component.translatable(key), Component.translatable(value ? "options.on" : "options.off"));
    }
    private static String key(Feature feature) { return "ui.blockwheel.builder." + feature.name().toLowerCase(Locale.ROOT); }
    private static Component label(Feature feature) {
        return Component.translatable("ui.blockwheel.feature_state", Component.translatable(key(feature)),
                Component.translatable(feature == Feature.ENDER_ACCESS && !BuilderClient.enderAllowed() ? "ui.blockwheel.server_disabled"
                        : BuilderClient.settings().feature(feature) ? "options.on" : "options.off"));
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
