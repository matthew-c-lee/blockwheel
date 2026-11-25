package dev.blockwheel.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blockwheel.core.RadialLayout;
import dev.blockwheel.core.RadialPages;
import dev.blockwheel.core.ManualConversionPlan;
import dev.blockwheel.server.BlockMaterials;
import dev.blockwheel.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Backend-independent GUI extraction: works through Minecraft's OpenGL/Vulkan pipelines. */
public final class RadialScreen extends Screen {
    private static final float SELECTION_VOLUME = 0.5f;
    private final Payloads.Wheel wheel;
    private final RadialPages arrangement;
    private final int[] variants;
    private int page;
    private int selected = -1;
    private boolean finished;
    private int age;
    private boolean managingMouse;
    private int previousCursorMode;

    public RadialScreen(Payloads.Wheel wheel) {
        super(Component.translatable("ui.blockwheel.title"));
        this.wheel = wheel;
        this.arrangement = new RadialPages(wheel.options().size(), index -> wheel.options().get(index).shapeId(), RadialPages.STANDARD_POSITIONS);
        this.variants = new int[wheel.options().size()];
        if (wheel.options().stream().anyMatch(option -> option.itemId().equals(wheel.sourceItem()) && option.shapeId().equals(RadialPages.FENCE_GATE))) {
            for (int index = 0; index < wheel.options().size(); index++) {
                if (wheel.options().get(index).shapeId().equals(RadialPages.FENCE)) variants[index] = 1;
            }
        }
    }

    @Override protected void init() {
        if (!managingMouse) {
            var window = minecraft.getWindow();
            previousCursorMode = GLFW.glfwGetInputMode(window.handle(), GLFW.GLFW_CURSOR);
            // Show the native pointer while keeping Minecraft's mouse-look disabled.
            InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL,
                    window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
            managingMouse = true;
        }
        selected = -1;
    }

    @Override public void removed() {
        finished = true;
        if (managingMouse) {
            // Consume pending movement while mouse-look is still disabled, so
            // closing the wheel cannot apply that movement to the camera.
            minecraft.mouseHandler.handleAccumulatedMovement();
            var window = minecraft.getWindow();
            InputConstants.grabOrReleaseMouse(window, previousCursorMode,
                    window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
            managingMouse = false;
        }
        super.removed();
    }

    private int optionCount() { return arrangement.slots(page); }
    private Payloads.Option option(int index) {
        int choice = arrangement.choice(page, index);
        return choice < 0 ? null : wheel.options().get(arrangement.choice(page, index, variants[choice]));
    }
    private int pages() { return arrangement.size(); }
    private RadialLayout layout() { return RadialLayout.fit(width, height, optionCount()); }
    public boolean isHoldingOpenKey() { return !finished; }
    private void updateSelection() {
        if (finished) return;
        double x = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        double y = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        int next = layout().closestNode(x, y, slot -> option(slot) != null);
        if (next != selected && next >= 0) playSelectionSound();
        selected = next;
    }
    private void playSelectionSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, 1.1f, SELECTION_VOLUME));
    }
    private boolean current(Payloads.Option option) { return option.itemId().equals(wheel.sourceItem()); }
    private ManualConversionPlan<Payloads.Option> plan(Payloads.Option option) {
        var source = wheel.options().stream().filter(form -> form.itemId().equals(wheel.sourceItem())).findFirst().orElseThrow();
        var base = wheel.options().stream().filter(form -> form.itemId().equals(wheel.familyItem())).findFirst().orElse(source);
        var secondary = base.shapeId().equals(dev.blockwheel.core.WoodFamilies.LOG) ? wheel.options().stream()
                .filter(form -> form.shapeId().equals(dev.blockwheel.core.WoodFamilies.PLANKS)).findFirst().orElse(null) : null;
        if (!BuilderClient.settings().enabled()) return ManualConversionPlan.calculate(wheel.count(), source, option, base,
                wheel.options(), Payloads.Option::units, ManualConversionPlan.outputLimit(option.shapeId(), item(option.itemId()).getMaxStackSize()),
                minecraft.player == null ? 0 : BlockMaterials.matchingMainCount(minecraft.player.getInventory(), item(wheel.sourceItem())), secondary);
        return ManualConversionPlan.calculate(wheel.count(), source, option, base, wheel.options(), Payloads.Option::units,
                ManualConversionPlan.outputLimit(option.shapeId(), item(option.itemId()).getMaxStackSize()),
                minecraft.player == null ? java.util.List.of() : BlockMaterials.mainSupply(minecraft.player.getInventory(),
                        wheel.options(), form -> item(form.itemId()).getItem(), Payloads.Option::units), secondary);
    }

    @Override public void tick() {
        age++;
        if (minecraft.player == null || age > 2400 || !minecraft.isWindowActive()) { onClose(); return; }
        var inventory = minecraft.player.getInventory();
        var stack = inventory.getItem(wheel.slot());
        if (inventory.getSelectedSlot() != wheel.slot() || stack.getCount() != wheel.count()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wheel.sourceItem())) onClose();
        updateSelection();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Keep the world and HUD unobscured; also skip Screen's default background.
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        RadialLayout layout = layout();
        updateSelection();
        for (int i = 0; i < optionCount(); i++) {
            var option = option(i);
            if (option == null) continue;
            var point = layout.node(i);
            int x = (int) Math.round(point.x()), y = (int) Math.round(point.y());
            int size = layout.nodeSize();
            BlockwheelStyle.cubeSlot(graphics, x, y, size, i == selected);
            drawItem(graphics, item(option.itemId()), x, y, layout.itemScale());
        }

    }

    public void releaseSelection() {
        updateSelection();
        release();
    }

    private void release() {
        if (finished || minecraft.gui.screen() != this) return;
        finished = true;
        if (minecraft.isWindowActive() && selected >= 0 && selected < optionCount()) {
            var option = option(selected);
            // Stored supplies are server-owned; a zero local preview cannot rule out exact retrieval.
            boolean supply = BuilderClient.settings().shulkerSupply();
            if (!current(option) && (plan(option).produced() > 0 || supply) && ClientPlayNetworking.canSend(Payloads.Convert.TYPE)) {
                ClientPlayNetworking.send(new Payloads.Convert(wheel.token(), option.shapeId()));
            }
        }
        onClose();
    }

    @Override public void mouseMoved(double x, double y) { updateSelection(); }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (BlockwheelClient.OPEN.matchesMouse(event)) {
            updateSelection();
            release();
        }
        return true;
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (BlockwheelClient.OPEN.matches(event)) {
            updateSelection();
            release();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (BlockwheelClient.OPEN.matches(event)) return true; // Holding/repeating the open key keeps the wheel open.
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            changePage(key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1); return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!Double.isFinite(vertical) || vertical == 0) return true;
        updateSelection();
        if (selected >= 0 && arrangement.variants(page, selected) > 1) {
            int choice = arrangement.choice(page, selected);
            variants[choice] = Math.floorMod(variants[choice] + (vertical < 0 ? 1 : -1), arrangement.variants(page, selected));
            playSelectionSound();
            return true;
        }
        if (pages() > 1) changePage(vertical < 0 ? 1 : -1);
        return true;
    }

    private void changePage(int direction) {
        int nextPage = Math.floorMod(page + direction, pages());
        if (nextPage == page) return;
        page = nextPage;
        selected = -1;
        updateSelection();
    }
    @Override public Component getNarrationMessage() {
        if (selected < 0 || selected >= optionCount()) return title;
        var preview = plan(option(selected));
        return label(option(selected)).copy().append(". ").append(Component.translatable("ui.blockwheel.preview",
                preview.inputCount(), preview.produced()));
    }
    @Override public void onClose() {
        finished = true;
        if (minecraft.gui.screen() == this) minecraft.gui.setScreen(null);
    }

    private static Component label(Payloads.Option option) {
        String path = option.shapeId().substring(option.shapeId().indexOf(':') + 1).replace('_', ' ').replace('/', ' ');
        return Component.translatableWithFallback(option.translationKey(), Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }
    private static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))); }
    private static void drawItem(GuiGraphicsExtractor graphics, ItemStack item, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x - 8 * scale, y - 8 * scale);
        graphics.pose().scale(scale, scale);
        graphics.item(item, 0, 0);
        graphics.pose().popMatrix();
    }
}
