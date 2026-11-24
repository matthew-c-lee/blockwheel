package dev.blockwheel.client;

import dev.blockwheel.client.mixin.ContainerScreenAccessor;
import dev.blockwheel.client.mixin.RecipeBookScreenAccessor;
import dev.blockwheel.core.BuilderSettings.Feature;
import dev.blockwheel.network.BuilderPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** GUI-relative controls; no dependencies on a particular ender chest side-panel mod. */
public final class InventoryControls {
    private static final java.util.Map<Screen, Object> generations = new java.util.WeakHashMap<>();
    private InventoryControls() {}
    public static void install(Screen screen) {
        boolean inventory = screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
        boolean ender = screen instanceof ContainerScreen && screen.getTitle().equals(Component.translatable("container.enderchest"));
        if (!inventory && !ender) return;
        var container = (AbstractContainerScreen<?>) screen;
        Object generation = new Object(); generations.put(screen, generation);
        var client = Minecraft.getInstance();
        InventoryIconButton mode = inventory ? new InventoryIconButton(InventoryIconButton.Kind.BUILDER, BuilderClient.modeLabel(),
                () -> BuilderClient.request(BuilderClient.settings().withEnabled(!BuilderClient.settings().enabled())),
                () -> client.gui.setScreen(new BuilderSettingsScreen(screen)), () -> BuilderClient.settings().enabled()) : null;
        var dump = new InventoryIconButton(InventoryIconButton.Kind.DEPOSIT, Component.translatable("ui.blockwheel.deposit"),
                BuilderClient::fillNamedShulkers, null, () -> false).holdAction(BuilderClient::fillNamedShulkers);
        if (mode != null) Screens.getWidgets(screen).add(mode);
        Screens.getWidgets(screen).add(dump);
        Runnable update = () -> {
            if (generations.get(screen) == generation) { update(container, mode, dump, ender); dump.tickHold(); }
            else dump.cancelHold();
        };
        update.run();
        ScreenEvents.beforeExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> update.run());
        ScreenEvents.afterTick(screen).register(current -> update.run());
        ScreenEvents.remove(screen).register(current -> dump.cancelHold());
        ScreenMouseEvents.afterMouseRelease(screen).register((current, event, handled) -> {
            if (generations.get(screen) != generation) { dump.cancelHold(); return handled; }
            boolean released = dump.releaseHold(event);
            return handled || released;
        });
    }

    private static void update(AbstractContainerScreen<?> screen, InventoryIconButton mode, InventoryIconButton dump, boolean ender) {
        var preferences = InterfacePreferencesClient.settings();
        present(screen, mode, preferences.showBuilderButton()); present(screen, dump, preferences.showShulkerRefillControls());
        var client = Minecraft.getInstance(); var position = (ContainerScreenAccessor) screen;
        int left = position.blockwheel$getLeft(), top = position.blockwheel$getTop();
        boolean covered = screen instanceof InventoryScreen && ((RecipeBookScreenAccessor) screen).blockwheel$isNarrow()
                && ((RecipeBookScreenAccessor) screen).blockwheel$getBook().isVisible();
        boolean visible = !covered && (!(screen instanceof CreativeModeInventoryScreen creative) || creative.isInventoryOpen());
        if (mode != null) {
            mode.setX(left + position.blockwheel$getImageWidth() - 25); mode.setY(top + 4);
            mode.visible = visible && preferences.showBuilderButton(); mode.active = mode.visible && BuilderClient.available(); mode.setMessage(BuilderClient.modeLabel());
            mode.contextTooltip(Component.translatable("ui.blockwheel.builder_icon_tooltip",
                    Component.translatable(BuilderClient.settings().enabled() ? "ui.blockwheel.enabled" : "ui.blockwheel.disabled"),
                    Component.translatable(BuilderClient.settings().enabled() ? "ui.blockwheel.disable" : "ui.blockwheel.enable")));
        }
        int right = left + position.blockwheel$getImageWidth(), header = top + 5;
        // Side panels that expose native ender slots can be located without mod-specific class names.
        if (!ender && client.player != null) {
            var slots = screen.getMenu().slots.stream().filter(slot -> slot.container == client.player.getEnderChestInventory()).toList();
            if (!slots.isEmpty()) {
                right = left + slots.stream().mapToInt(slot -> slot.x).max().orElseThrow() + 24;
                header = top + slots.stream().mapToInt(slot -> slot.y).min().orElseThrow() - 13;
            }
        }
        int x = right + 4, y = header;
        if (x + BlockwheelStyle.BUTTON_SIZE > client.getWindow().getGuiScaledWidth() - 2) {
            x = right - BlockwheelStyle.BUTTON_SIZE - 4; y = header - 8;
        }
        dump.setX(x); dump.setY(y); dump.visible = visible && preferences.showShulkerRefillControls() && (ender || !(screen instanceof CreativeModeInventoryScreen));
        boolean remote = BuilderClient.settings().feature(Feature.ENDER_ACCESS) && BuilderClient.enderAllowed();
        boolean carried = BuilderClient.settings().feature(Feature.EXPERIMENTAL_WOOD);
        dump.setMessage(Component.translatable(carried ? "ui.blockwheel.deposit_all_named" : "ui.blockwheel.deposit"));
        dump.active = dump.visible && ClientPlayNetworking.canSend(BuilderPayloads.FillTemplates.TYPE) && (ender || remote || carried)
                && client.player != null && client.player.containerMenu.getCarried().isEmpty();
        var help = Component.translatable(dump.active ? carried ? BuilderClient.settings().enabled() ? "ui.blockwheel.deposit_wood_tooltip_builder" : "ui.blockwheel.deposit_wood_tooltip"
                : BuilderClient.settings().enabled() ? "ui.blockwheel.deposit_tooltip_builder" : "ui.blockwheel.deposit_tooltip"
                : !ender && !BuilderClient.enderAllowed() ? "ui.blockwheel.deposit_server_disabled" : "ui.blockwheel.deposit_unavailable");
        if (dump.active) help.append("\n").append(Component.translatable("ui.blockwheel.deposit_hold_hint"));
        dump.contextTooltip(help);
    }

    private static void present(AbstractContainerScreen<?> screen, InventoryIconButton button, boolean shown) {
        if (button == null) return;
        var widgets = Screens.getWidgets(screen);
        if (shown) { if (!widgets.contains(button)) widgets.add(button); }
        else {
            button.visible = false; button.active = false;
            if (screen.getFocused() == button) screen.setFocused(null);
            button.setFocused(false); widgets.remove(button);
        }
    }

}
