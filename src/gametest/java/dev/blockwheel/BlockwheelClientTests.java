package dev.blockwheel;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.client.BlockwheelClient;
import dev.blockwheel.client.RadialScreen;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.gametest.SelectionSoundProbe;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Exercises nearest-pointer selection, audible feedback requests and actual release conversions. */
public final class BlockwheelClientTests implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            boolean originalHudHidden = context.computeOnClient(client -> client.gui.hud.isHidden());
            var originalKey = context.computeOnClient(client -> KeyMappingHelper.getBoundKeyOf(BlockwheelClient.OPEN));
            try {
                seed(context, world, Items.COBBLESTONE, 64);
                open(context);
                release(context);
                assertClicks(context, 0);
                assertInventory(world, 64, 0, 0, 0);
                assertRestored(context, originalHudHidden);

                open(context);
                for (int i = 0; i < 4; i++) { move(context, 1, 0); move(context, -1, 0); }
                assertClicks(context, 0);
                release(context);
                assertInventory(world, 64, 0, 0, 0);

                open(context);
                move(context, 0, 24);
                assertSelection(context, "Wall");
                assertClicks(context, 1);
                move(context, 2, 0);
                assertSelection(context, "Wall");
                assertClicks(context, 1);
                release(context);
                assertClicks(context, 1);
                assertInventory(world, 0, 64, 0, 0);

                seed(context, world, Items.COBBLESTONE, 64);
                float[] camera = context.computeOnClient(client -> new float[]{client.player.getYRot(), client.player.getXRot()});
                open(context);
                point(context, 0, 100);
                assertSelection(context, "Wall");
                point(context, 0, 76); // Moving up still selects Wall while the pointer stays below the center.
                assertSelection(context, "Wall");
                assertClicks(context, 1);
                point(context, 24, 0); // Cross the center to select Stairs, without reaching its icon.
                assertSelection(context, "Stairs");
                assertOutsideIcons(context);
                move(context, -2, 0);
                assertSelection(context, "Stairs");
                context.waitTicks(8);
                assertClicks(context, 2); // Resting and tiny movements must not repeat the click.
                assertCamera(context, camera);
                context.takeScreenshot("blockwheel-position-stairs");
                release(context);
                assertInventory(world, 0, 0, 64, 0);
                assertCamera(context, camera);
                assertRestored(context, originalHudHidden);

                seed(context, world, Items.COBBLESTONE, 64);
                open(context);
                point(context, 0, 24);
                assertSelection(context, "Wall");
                point(context, 0, 0); // Returning to the neutral center clears the selection and cancels on release.
                if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().equals("Reshape"))) {
                    throw new AssertionError("Returning to the center did not clear the selection");
                }
                assertClicks(context, 1);
                release(context);
                assertInventory(world, 64, 0, 0, 0);

                open(context);
                move(context, 0, -24); // Current full-block form.
                release(context);
                assertInventory(world, 64, 0, 0, 0);

                open(context);
                move(context, -24, 0);
                assertSelection(context, "Slab");
                if (!context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString().endsWith("64 -> 64"))) {
                    throw new AssertionError("Slab preview did not match the capped server output");
                }
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
                context.waitTicks(2);
                assertInventory(world, 64, 0, 0, 0);
                context.takeScreenshot("blockwheel-position-slabs");
                release(context);
                assertInventory(world, 32, 0, 0, 64);
                world.getServer().runOnServer(server -> {
                    var inv = world.getConnection().getServerPlayer().getInventory();
                    if (!inv.getItem(0).is(Items.COBBLESTONE_SLAB) || inv.getItem(0).getCount() != 64
                            || !inv.getItem(9).is(Items.COBBLESTONE) || inv.getItem(9).getCount() != 32) {
                        throw new AssertionError("Capped slabs or base change went to the wrong inventory domain");
                    }
                    for (int slot = 1; slot < 9; slot++) if (!inv.getItem(slot).isEmpty()) throw new AssertionError("Capped change filled another hotbar slot");
                });

                open(context);
                move(context, 0, -24);
                String preview = context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString());
                context.getInput().holdShift();
                context.waitTicks(2);
                String withShift = context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString());
                if (!preview.equals(withShift)) throw new AssertionError("Shift changed the conversion preview");
                release(context);
                context.getInput().releaseShift();
                assertInventory(world, 64, 0, 0, 0);

                seed(context, world, Items.COBBLESTONE, 64);
                context.getInput().holdShift();
                open(context);
                move(context, -24, 0);
                release(context);
                context.getInput().releaseShift();
                assertInventory(world, 32, 0, 0, 64);

                seed(context, world, Items.COBBLESTONE_SLAB, 1);
                open(context);
                move(context, 0, -24);
                release(context);
                assertInventory(world, 0, 0, 0, 1);

                seed(context, world, Items.COBBLESTONE, 64);
                open(context);
                move(context, 0, 24);
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.gui.screen() == null);
                context.waitTicks(3);
                if (context.computeOnClient(client -> client.gui.screen() != null)) throw new AssertionError("Cancelled wheel reopened while held");
                context.getInput().releaseKey(BlockwheelClient.OPEN);
                context.waitTicks(2);
                assertInventory(world, 64, 0, 0, 0);
                assertRestored(context, originalHudHidden);

                context.getInput().pressKey(BlockwheelClient.OPEN);
                context.waitTicks(8);
                if (context.computeOnClient(client -> client.gui.screen() != null)) throw new AssertionError("Quick tap left a wheel open");
                assertInventory(world, 64, 0, 0, 0);

                verifyWallFenceAlternates(context, world);
                verifySparseFamilies(context, world);
                seed(context, world, Items.COBBLESTONE, 64);
                context.runOnClient(client -> {
                    BlockwheelClient.OPEN.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));
                    KeyMapping.resetMapping();
                });
                open(context);
                move(context, 24, 0);
                release(context);
                assertInventory(world, 0, 0, 64, 0);
                assertRestored(context, originalHudHidden);
            } finally {
                context.getInput().releaseKey(GLFW.GLFW_KEY_R);
                context.getInput().releaseKey(GLFW.GLFW_KEY_G);
                context.getInput().releaseShift();
                context.runOnClient(client -> {
                    if (client.gui.screen() instanceof RadialScreen screen) screen.onClose();
                    BlockwheelClient.OPEN.setKey(originalKey);
                    KeyMapping.resetMapping();
                });
            }
        }
    }

    private void seed(ClientGameTestContext context, TestSingleplayerContext world, Item item, int count) {
        world.getServer().runOnServer(server -> {
            var player = world.getConnection().getServerPlayer();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(item, count));
            player.getInventory().setSelectedSlot(0);
            player.inventoryMenu.broadcastChanges();
        });
        context.waitFor(client -> client.gui.screen() == null && client.player.getMainHandItem().is(item)
                && client.player.getMainHandItem().getCount() == count);
        context.waitTicks(2);
    }

    private void open(ClientGameTestContext context) {
        boolean hudWasHidden = context.computeOnClient(client -> client.gui.hud.isHidden());
        context.runOnClient(client -> SelectionSoundProbe.reset());
        context.getInput().holdKey(BlockwheelClient.OPEN);
        context.waitForScreen(RadialScreen.class);
        context.waitTicks(3);
        if (!context.computeOnClient(client -> client.gui.screen() instanceof RadialScreen && client.gui.hud.isHidden() == hudWasHidden
                && !client.mouseHandler.isMouseGrabbed()
                && GLFW.glfwGetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL)) {
            throw new AssertionError(context.computeOnClient(client -> "Pointer state: screen=" + (client.gui.screen() == null ? "null" : client.gui.screen().getClass().getSimpleName()) + ", hudHidden=" + client.gui.hud.isHidden() + ", mouseLook=" + client.mouseHandler.isMouseGrabbed() + ", cursorMode=" + GLFW.glfwGetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR)));
        }
        assertClicks(context, 0);
    }

    private void verifyWallFenceAlternates(ClientGameTestContext context, TestSingleplayerContext world) {
        seed(context, world, Items.NETHER_BRICKS, 64);
        open(context);
        point(context, 0, 24);
        assertSelection(context, "Wall");
        assertOutsideIcons(context);
        assertClicks(context, 1);
        context.takeScreenshot("blockwheel-fixed-wall");
        scroll(context, 1);
        assertSelection(context, "Wall");
        assertClicks(context, 1);
        point(context, -24, 24);
        assertSelection(context, "Fence");
        assertClicks(context, 2);
        scroll(context, -1);
        assertSelection(context, "Fence"); assertClicks(context, 2);
        context.takeScreenshot("blockwheel-additional-fence");
        assertNetherInventory(world, 64, 0, 0);
        point(context, 24, 0);
        assertSelection(context, "Stairs");
        scroll(context, 1);
        assertSelection(context, "Stairs");
        point(context, -24, 24);
        assertSelection(context, "Fence");
        release(context);
        assertNetherInventory(world, 0, 0, 64);

        seed(context, world, Items.NETHER_BRICKS, 64);
        open(context);
        point(context, 0, 24);
        assertSelection(context, "Wall");
        release(context);
        assertNetherInventory(world, 0, 64, 0);

        seed(context, world, Items.NETHER_BRICK_FENCE, 64);
        open(context);
        point(context, -24, 24);
        assertSelection(context, "Fence");
        scroll(context, -1);
        assertSelection(context, "Fence"); // The source form is current: releasing must be a no-op.
        release(context);
        assertNetherInventory(world, 0, 0, 64);

        seed(context, world, Items.NETHER_BRICK_WALL, 64);
        open(context);
        point(context, -24, 24);
        assertSelection(context, "Fence");
        release(context);
        assertNetherInventory(world, 0, 0, 64);

        open(context);
        point(context, 0, 24);
        assertSelection(context, "Wall");
        release(context);
        assertNetherInventory(world, 0, 64, 0);

        seed(context, world, Items.OAK_PLANKS, 64);
        open(context);
        point(context, 0, 24);
        assertSelection(context, "Fence"); // Fence-only families retain their usual down position.
        scroll(context, 1);
        assertSelection(context, "Fence Gate");
        assertClicks(context, 2);
        scroll(context, -1);
        assertSelection(context, "Fence");
        assertClicks(context, 3);
        release(context);
        world.getConnection().waitForServerboundPackets();
        world.getServer().runOnServer(server -> {
            var inv = world.getConnection().getServerPlayer().getInventory();
            if (inv.countItem(Items.OAK_FENCE) != 64 || inv.countItem(Items.OAK_PLANKS) != 0) {
                throw new AssertionError("Fence-only family conversion changed");
            }
        });
    }

    private void verifySparseFamilies(ClientGameTestContext context, TestSingleplayerContext world) {
        for (Item base : new Item[]{Items.SMOOTH_STONE, Items.POLISHED_GRANITE}) {
            seed(context, world, base, 8);
            open(context);
            point(context, -24, 0);
            assertSelection(context, "Slab");
            context.takeScreenshot(base == Items.SMOOTH_STONE ? "blockwheel-two-options" : "blockwheel-three-options");
            release(context);
            Item slabs = base == Items.SMOOTH_STONE ? Items.SMOOTH_STONE_SLAB : Items.POLISHED_GRANITE_SLAB;
            context.waitFor(client -> client.player.getMainHandItem().is(slabs) && client.player.getMainHandItem().getCount() == 16);
        }
        seed(context, world, Items.POLISHED_GRANITE, 8);
        open(context);
        point(context, 24, 0);
        assertSelection(context, "Stairs");
        release(context);
        context.waitFor(client -> client.player.getMainHandItem().is(Items.POLISHED_GRANITE_STAIRS)
                && client.player.getMainHandItem().getCount() == 8);
    }

    private void scroll(ClientGameTestContext context, double amount) {
        context.getInput().scroll(amount);
        context.waitTicks(1);
    }

    private void assertNetherInventory(TestSingleplayerContext world, int blocks, int walls, int fences) {
        world.getConnection().waitForServerboundPackets();
        int[] actual = world.getServer().computeOnServer(server -> {
            var inv = world.getConnection().getServerPlayer().getInventory();
            return new int[]{inv.countItem(Items.NETHER_BRICKS), inv.countItem(Items.NETHER_BRICK_WALL),
                    inv.countItem(Items.NETHER_BRICK_FENCE)};
        });
        if (!java.util.Arrays.equals(actual, new int[]{blocks, walls, fences})) {
            throw new AssertionError("Unexpected nether brick block/wall/fence counts: " + java.util.Arrays.toString(actual));
        }
    }

    private void move(ClientGameTestContext context, double dx, double dy) {
        double[] delta = context.computeOnClient(client -> new double[]{dx * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                dy * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()});
        context.getInput().moveCursor(delta[0], delta[1]);
        context.waitTicks(1);
    }

    /** Place the actual cursor relative to the wheel, in GUI pixels. */
    private void point(ClientGameTestContext context, double dx, double dy) {
        double[] position = context.computeOnClient(client -> {
            var window = client.getWindow();
            var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            return new double[]{(layout.centerX() + dx) * window.getScreenWidth() / window.getGuiScaledWidth(),
                    (layout.centerY() + dy) * window.getScreenHeight() / window.getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(position[0], position[1]);
        context.waitTicks(1);
    }

    private void assertOutsideIcons(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var window = client.getWindow();
            var layout = RadialLayout.fit(window.getGuiScaledWidth(), window.getGuiScaledHeight(), 4);
            double x = client.mouseHandler.getScaledXPos(window), y = client.mouseHandler.getScaledYPos(window);
            for (int i = 0; i < 4; i++) {
                var node = layout.node(i);
                if (Math.hypot(x - node.x(), y - node.y()) <= layout.nodeSize() + 3) {
                    throw new AssertionError("The non-hover selection case put the cursor on an icon");
                }
            }
        });
    }

    private void assertSelection(ClientGameTestContext context, String label) {
        String actual = context.computeOnClient(client -> client.gui.screen().getNarrationMessage().getString());
        if (!actual.startsWith(label + ".")) throw new AssertionError("Expected " + label + ", got " + actual);
    }

    private void assertClicks(ClientGameTestContext context, int expected) {
        var clicks = context.computeOnClient(client -> SelectionSoundProbe.clicks());
        if (clicks.size() != expected) throw new AssertionError("Expected " + expected + " selection clicks, got " + clicks.size());
        for (var click : clicks) {
            if (!(click.volume() >= 0.4f && click.volume() < 1) || click.source() != SoundSource.UI || !click.relative()) {
                throw new AssertionError("Selection feedback must have the increased volume and remain a non-positional UI sound: " + click);
            }
        }
    }

    private void release(ClientGameTestContext context) {
        context.getInput().releaseKey(BlockwheelClient.OPEN);
        context.waitFor(client -> client.gui.screen() == null);
        context.waitTicks(3);
    }

    private void assertInventory(TestSingleplayerContext world, int blocks, int walls, int stairs, int slabs) {
        world.getConnection().waitForServerboundPackets();
        int[] actual = world.getServer().computeOnServer(server -> {
            var inv = world.getConnection().getServerPlayer().getInventory();
            return new int[]{inv.countItem(Items.COBBLESTONE), inv.countItem(Items.COBBLESTONE_WALL),
                    inv.countItem(Items.COBBLESTONE_STAIRS), inv.countItem(Items.COBBLESTONE_SLAB)};
        });
        if (!java.util.Arrays.equals(actual, new int[]{blocks, walls, stairs, slabs})) {
            throw new AssertionError("Unexpected block/wall/stair/slab counts: " + java.util.Arrays.toString(actual));
        }
    }

    private void assertCamera(ClientGameTestContext context, float[] expected) {
        float[] actual = context.computeOnClient(client -> new float[]{client.player.getYRot(), client.player.getXRot()});
        if (Math.abs(actual[0] - expected[0]) > 0.01 || Math.abs(actual[1] - expected[1]) > 0.01) {
            throw new AssertionError("Radial mouse motion changed the camera");
        }
    }

    private void assertRestored(ClientGameTestContext context, boolean expectedHudHidden) {
        if (!context.computeOnClient(client -> client.gui.hud.isHidden() == expectedHudHidden && client.mouseHandler.isMouseGrabbed()
                && GLFW.glfwGetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED)) {
            throw new AssertionError("HUD or gameplay mouse capture was not restored");
        }
    }
}
