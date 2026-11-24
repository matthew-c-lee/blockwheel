package dev.blockwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native-hotbar radial frames and original 16-pixel inventory control glyphs. */
public final class BlockwheelStyle {
    private BlockwheelStyle() {}
    public static final int BUTTON_SIZE = 20;
    private static final int EDGE = 0xff242424, SELECTED = 0xffdedede;
    private static final int HIGHLIGHT = 0xffbdbdbd, SHADE = 0xff454545;
    private static final String[] DEPOSIT = {
            "................", ".......a........", ".......a........", ".....aaaaa......",
            "......aaa.......", ".......a........", "................", "..oooooooooooo..",
            "..otttttttttto..", "..offffffffsso..", "..offffffffsso..", "..offffffffsso..",
            "..offffffffsso..", "..osssssssssso..", "..oooooooooooo..", "................"};

    public static void cubeSlot(GuiGraphicsExtractor graphics, int x, int y, int radius, boolean selected) {
        RadialSlotRenderState.draw(graphics, x, y, Math.max(1, (int) Math.round((radius - 2) * .8)), selected);
    }

    public static void control(GuiGraphicsExtractor graphics, int x, int y, boolean enabled,
                               boolean active, boolean hovered, boolean deposit) {
        if (!deposit) {
            switchTrack(graphics, x + 2, y + 5, 16, 10, enabled, active);
            if (active && hovered) graphics.outline(x + 2, y + 5, 16, 10, SELECTED);
            return;
        }
        int edge = hovered && active ? SELECTED : EDGE;
        graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, edge);
        graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, active ? 0xff747474 : 0xff555555);
        graphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + 2, enabled ? SHADE : HIGHLIGHT);
        graphics.fill(x + 1, y + 2, x + 2, y + BUTTON_SIZE - 1, enabled ? SHADE : HIGHLIGHT);
        graphics.fill(x + 2, y + BUTTON_SIZE - 2, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, enabled ? HIGHLIGHT : SHADE);
        graphics.fill(x + BUTTON_SIZE - 2, y + 2, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 2, enabled ? HIGHLIGHT : SHADE);
        String[] glyph = DEPOSIT;
        for (int row = 0; row < glyph.length; row++) for (int column = 0; column < glyph[row].length(); column++) {
            int color = switch (glyph[row].charAt(column)) {
                case 'o' -> EDGE;
                case 't' -> active ? 0xffdddddd : 0xff929292;
                case 'f' -> active ? 0xffbcbcbc : 0xff858585;
                case 's' -> active ? 0xff8c8c8c : 0xff6e6e6e;
                case 'a' -> enabled ? 0xffa6c59d : active ? 0xffd2d2d2 : 0xff8c8c8c;
                default -> 0;
            };
            if (color != 0) graphics.fill(x + 2 + column, y + 2 + row, x + 3 + column, y + 3 + row, color);
        }
    }

    public static void switchTrack(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean enabled, boolean active) {
        graphics.fill(x, y, x + width, y + height, EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, !active ? 0xff464646 : enabled ? 0xff526f47 : 0xff545454);
        int knob = height - 2, left = enabled ? x + width - knob - 1 : x + 1;
        graphics.fill(left, y + 1, left + knob, y + height - 1, active ? 0xffc8c8c8 : 0xff858585);
        graphics.fill(left, y + 1, left + knob, y + 2, active ? 0xfff1f1f1 : 0xffa2a2a2);
        graphics.fill(left + knob - 1, y + 2, left + knob, y + height - 1, 0xff767676);
        int center = left + knob / 2;
        if (enabled) graphics.fill(center, y + 3, center + 1, y + height - 3, active ? 0xff36512c : SHADE);
        else {
            graphics.fill(center - 1, y + 3, center + 2, y + height - 3, SHADE);
            graphics.fill(center, y + 4, center + 1, y + height - 4, active ? 0xffc8c8c8 : 0xff858585);
        }
    }
}
