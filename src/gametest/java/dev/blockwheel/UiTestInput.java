package dev.blockwheel;

import dev.blockwheel.client.InventoryIconButton;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.lwjgl.glfw.GLFW;

/** Native mouse input for compact controls; no production-only test hooks. */
public final class UiTestInput {
    private UiTestInput() {}
    public static void settings(ClientGameTestContext context) { click(context, InventoryIconButton.Kind.BUILDER, GLFW.GLFW_MOUSE_BUTTON_RIGHT); }
    public static void click(ClientGameTestContext context, InventoryIconButton.Kind kind, int button) {
        point(context, kind); context.getInput().pressMouse(button);
    }
    public static void hold(ClientGameTestContext context, InventoryIconButton.Kind kind) {
        point(context, kind); context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void point(ClientGameTestContext context, InventoryIconButton.Kind kind) {
        var point = context.computeOnClient(client -> {
            var widget = client.gui.screen().children().stream().filter(child -> child instanceof InventoryIconButton icon
                    && icon.kind() == kind && icon.visible && icon.active).map(child -> (InventoryIconButton) child).findFirst().orElseThrow();
            var window = client.getWindow();
            return new double[]{(widget.getX() + widget.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (widget.getY() + widget.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]);
    }
    public static void creativeInventory(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                var method = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class); method.setAccessible(true);
                method.invoke(client.gui.screen(), CreativeModeTabs.allTabs().stream().filter(tab -> tab.getType() == CreativeModeTab.Type.INVENTORY).findFirst().orElseThrow());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        context.waitTicks(2);
    }
}
