package dev.blockwheel;

import dev.blockwheel.client.BlockwheelStyle;
import dev.blockwheel.client.HotbarFrameProfileChecks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Native reference sprites beside blank production slots for exact color/alpha screenshot checks. */
public final class HotbarStyleClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        int scale = context.computeOnClient(client -> client.options.guiScale().get());
        var parent = context.computeOnClient(client -> client.gui.screen());
        List<String> packs = context.computeOnClient(client -> List.copyOf(client.getResourcePackRepository().getSelectedIds()));
        try {
            context.runOnClient(client -> HotbarFrameProfileChecks.run());
            capture(context, "blockwheel-hotbar-reference");
            for (int resolution : new int[]{1, 2}) {
                String name = "blockwheel-test-hotbar-" + resolution;
                context.runOnClient(client -> {
                    try { HotbarFrameProfileChecks.writePack(client.getResourcePackDirectory().resolve(name), resolution, resolution > 1); }
                    catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                });
                var selected = new ArrayList<>(packs);
                selected.add("file/" + name);
                reload(context, selected);
                capture(context, "blockwheel-hotbar-pack-" + resolution);
            }
            reload(context, packs);
            capture(context, "blockwheel-hotbar-restored");
            Blockwheel.LOGGER.info("Hotbar style reference and synthetic resource pack captures completed at GUI scales 2/3/4; vanilla reload restored");
        } finally {
            if (!context.computeOnClient(client -> List.copyOf(client.getResourcePackRepository().getSelectedIds())).equals(packs)) reload(context, packs);
            context.runOnClient(client -> { client.options.guiScale().set(scale); client.resizeGui(); client.gui.setScreen(parent); });
        }
    }

    private static void reload(ClientGameTestContext context, List<String> packs) {
        var future = context.computeOnClient(client -> {
            client.getResourcePackRepository().reload();
            if (!client.getResourcePackRepository().getAvailableIds().containsAll(packs)) throw new AssertionError("Test resource pack not discovered");
            client.getResourcePackRepository().setSelected(packs);
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> future.isDone(), 1200);
        future.join();
        context.waitFor(client -> client.gui.overlay() == null, 1200);
        if (!context.computeOnClient(client -> List.copyOf(client.getResourcePackRepository().getSelectedIds())).equals(packs)) {
            throw new AssertionError("Resource pack reload recovered instead of accepting the test pack");
        }
    }

    private static void capture(ClientGameTestContext context, String prefix) {
        for (int guiScale = 2; guiScale <= 4; guiScale++) {
            int value = guiScale;
            context.runOnClient(client -> {
                client.options.guiScale().set(value); client.resizeGui(); client.gui.setScreen(new ReferenceScreen());
                if (client.getWindow().getGuiScale() != value) throw new AssertionError("Viewport cannot provide GUI scale " + value);
            });
            context.waitTicks(3); context.takeScreenshot(prefix + "-scale-" + guiScale);
        }
    }

    private static final class ReferenceScreen extends Screen {
        private ReferenceScreen() { super(Component.literal("Hotbar rendering test fixture")); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xff6a8bad);
            BlockwheelStyle.cubeSlot(graphics, 80, 80, 22, false);
            BlockwheelStyle.cubeSlot(graphics, 160, 80, 22, true);
            BlockwheelStyle.control(graphics, 300, 80, false, true, false, false);
            BlockwheelStyle.control(graphics, 330, 80, true, true, false, false);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/hotbar"), 24, 140, 182, 22);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/hotbar_selection"), 240, 140, 24, 23);
        }
    }
}
