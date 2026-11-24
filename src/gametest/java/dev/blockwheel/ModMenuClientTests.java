package dev.blockwheel;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import dev.blockwheel.client.BuilderClient;
import dev.blockwheel.client.BuilderSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The optional classes are isolated so the normal test profile also verifies their absence is safe. */
public final class ModMenuClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        if (FabricLoader.getInstance().isModLoaded("modmenu")) Integration.verify(context);
    }

    private static final class Integration {
        private static void open(ClientGameTestContext context) {
            context.runOnClient(client -> {
                if (!ModMenu.hasConfigScreen("blockwheel")) throw new AssertionError("Mod Menu did not register Blockwheel's configuration entry");
                var menu = (ModsScreen) ModMenuApi.createModsScreen(client.gui.screen()); client.gui.setScreen(menu);
                var list = menu.children().stream().filter(child -> child instanceof ModListWidget)
                        .map(child -> (ModListWidget) child).findFirst().orElseThrow();
                list.setSelected(list.children().stream().filter(entry -> entry.getMod().getId().equals("blockwheel")).findFirst().orElseThrow());
            });
            context.waitTicks(2);
            context.clickScreenButton(Component.translatable("modmenu.configure").getString());
            context.waitForScreen(BuilderSettingsScreen.class);
        }

        private static void verify(ClientGameTestContext context) {
            int scale = context.computeOnClient(client -> client.options.guiScale().get());
            var parent = context.computeOnClient(client -> client.gui.screen());
            try {
                context.runOnClient(client -> { client.options.guiScale().set(3); client.resizeGui(); });
                open(context);
                context.runOnClient(client -> {
                    if (!client.gui.screen().getTitle().getString().equals("Blockwheel Settings")) throw new AssertionError("Settings still have a Builder-only title");
                    if (client.gui.screen().children().stream().anyMatch(child -> child instanceof Button button
                            && button.getMessage().getString().startsWith("Builder Mode:"))) throw new AssertionError("Block wheel page shows an unrelated Builder master switch");
                    if (BuilderClient.available()) throw new AssertionError("Title-screen settings unexpectedly had a server connection");
                    long inactive = client.gui.screen().children().stream().filter(child -> child instanceof Button button && !button.active).count();
                    if (inactive < 4) throw new AssertionError("Server-owned options were editable outside a world");
                });
                layout(context);
                context.takeScreenshot("blockwheel-modmenu-outside-world");
                context.clickScreenButton("Pick block"); context.clickScreenButton("Edit fallback rules...");
                context.waitForScreen(dev.blockwheel.client.PickRulesScreen.class);
                context.clickScreenButton("Cancel"); context.waitForScreen(BuilderSettingsScreen.class);
                context.clickScreenButton("Interface");
                context.runOnClient(client -> {
                    long editable = client.gui.screen().children().stream().filter(child -> child instanceof Button button
                            && button.active && button.getMessage().getString().startsWith("Hide ")).count();
                    if (editable != 2) throw new AssertionError("Local interface preferences were not editable outside a world");
                });
                context.clickScreenButton("Done"); context.waitForScreen(ModsScreen.class);
                context.runOnClient(client -> client.gui.setScreen(parent));
                try (var world = context.worldBuilder().create()) {
                    world.getConnection().waitForChunksRender(); context.waitFor(client -> BuilderClient.available());
                    open(context);
                    context.clickScreenButton("Builder"); context.clickScreenButton(BuilderClient.modeLabel().getString());
                    context.waitFor(client -> BuilderClient.settings().enabled());
                    if (!world.getServer().computeOnServer(server -> Blockwheel.BUILDERS.settings(world.getConnection().getServerPlayer()).enabled())) {
                        throw new AssertionError("Mod Menu settings did not update the authoritative player preferences");
                    }
                    context.takeScreenshot("blockwheel-modmenu-general-options");
                    context.clickScreenButton(Component.translatable("ui.blockwheel.restock_tab").getString());
                    context.runOnClient(client -> {
                        long sliders = client.gui.screen().children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractSliderButton slider
                                && slider.active).count();
                        if (sliders != 2 || BuilderClient.settings().restock().below() != 6 || BuilderClient.settings().restock().amount() != 32) {
                            throw new AssertionError("Mod Menu did not expose the two restock controls with their defaults");
                        }
                    });
                    context.waitTicks(2); context.takeScreenshot("blockwheel-modmenu-restock-options");
                    context.clickScreenButton(Component.translatable("ui.blockwheel.shulker_tab").getString());
                    context.waitTicks(2); context.takeScreenshot("blockwheel-modmenu-supply-options");
                    context.clickScreenButton("Pick block"); context.waitTicks(2); context.takeScreenshot("blockwheel-modmenu-pick-options");
                    context.clickScreenButton("Block wheel"); context.waitTicks(2); context.takeScreenshot("blockwheel-modmenu-radial-options");
                    context.clickScreenButton("Interface"); context.waitTicks(2); context.takeScreenshot("blockwheel-modmenu-interface-options");
                    for (int scaleValue = 2; scaleValue <= 4; scaleValue++) {
                        int chosenScale = scaleValue;
                        context.runOnClient(client -> { client.options.guiScale().set(chosenScale); client.resizeGui(); });
                        for (String tab : new String[]{"Block wheel", "Builder", "Restock", "Supply", "Pick block", "Interface"}) {
                            if (context.computeOnClient(client -> client.gui.screen().children().stream().anyMatch(child -> child instanceof Button button
                                    && button.getMessage().getString().equals(tab) && button.active))) context.clickScreenButton(tab);
                            layout(context);
                        }
                    }
                    context.clickScreenButton("Done"); context.waitForScreen(ModsScreen.class);
                    context.runOnClient(client -> client.gui.setScreen(null));
                }
                Blockwheel.LOGGER.info("Mod Menu 20.0.3 client checks passed: native Configure button, world preferences, all six tabs, local pick/interface preferences outside a world, restock defaults, master switch and parent return");
            } finally {
                context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); client.gui.setScreen(parent); });
            }
        }

        private static void layout(ClientGameTestContext context) {
            context.runOnClient(client -> {
                var screen = client.gui.screen();
                var widgets = screen.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible)
                        .map(child -> (net.minecraft.client.gui.components.AbstractWidget) child).toList();
                for (int i = 0; i < widgets.size(); i++) {
                    var a = widgets.get(i);
                    if (a.getX() < 0 || a.getY() < 0 || a.getX() + a.getWidth() > screen.width || a.getY() + a.getHeight() > screen.height)
                        throw new AssertionError("Settings control outside screen: " + a.getMessage().getString());
                    for (int j = i + 1; j < widgets.size(); j++) {
                        var b = widgets.get(j);
                        if (a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                                && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY())
                            throw new AssertionError("Settings controls overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
                    }
                }
            });
        }
    }
}
