package dev.blockwheel.client;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Entirely synthetic test artwork; no Minecraft or third-party texture is redistributed. */
public final class HotbarFrameProfileChecks {
    private HotbarFrameProfileChecks() {}

    public static void run() {
        int checks = 0;
        for (int scale : new int[]{1, 2, 4}) {
            for (boolean selected : new boolean[]{false, true}) {
                int width = (selected ? 24 : 182) * scale, height = (selected ? 23 : 22) * scale;
                try (var image = texture(scale, selected, scale > 1)) {
                    var profile = HotbarFrameProfile.analyze(image, width, height, selected);
                    float shell = (scale > 1 ? 1.5f : .5f) / scale;
                    if (Math.abs(profile.shellDepth() - shell) > .0001f) throw new AssertionError("Shell must sample an opaque source-pixel center");
                    checks++;
                    int border = selected ? 3 : 2;
                    for (int edge = 0; edge < 4; edge++) {
                        for (int layer = 1; layer <= border; layer++) {
                            float depth = profile.depth(edge, layer);
                            float start = edge == 0 ? 3 : edge == 2 && selected ? 2 : 1;
                            float span = edge == 0 && !selected || edge == 2 ? (selected ? 2 : 1) : border;
                            float expected = start + (layer - .5f) * span / border;
                            if (Math.abs(depth - expected) > .0001f) {
                                throw new AssertionError("Pack strip mismatch: scale=" + scale + ", selected=" + selected
                                        + ", edge=" + edge + ", layer=" + layer + ", depth=" + depth + ", expected=" + expected);
                            }
                            checks++;
                        }
                    }
                }
                try (var blank = new NativeImage(width, height, true)) {
                    var profile = HotbarFrameProfile.analyze(blank, width, height, selected);
                    for (int edge = 0; edge < 4; edge++) {
                        float expected = selected && edge == 2 ? .5f : 1.5f;
                        if (Math.abs(profile.depth(edge, 1) - expected) > .0001f) throw new AssertionError("Ambiguous texture fallback");
                        checks++;
                    }
                }
            }
        }
        dev.blockwheel.Blockwheel.LOGGER.info("Hotbar pack strip checks passed: {} (1x/2x/4x, normal/selected, asymmetric borders, padding, ambiguous fallback)", checks);
    }

    public static void writePack(Path directory, int scale, boolean padding) throws IOException {
        Path sprites = directory.resolve("assets/minecraft/textures/gui/sprites/hud");
        Files.createDirectories(sprites);
        Files.writeString(directory.resolve("pack.mcmeta"), """
                {"pack":{"description":"Blockwheel synthetic hotbar regression fixture",
                "min_format":[84,0],"max_format":[1000,0]}}
                """);
        for (boolean selected : new boolean[]{false, true}) {
            try (var image = texture(scale, selected, padding)) {
                image.writeToFile(sprites.resolve(selected ? "hotbar_selection.png" : "hotbar.png"));
            }
        }
    }

    private static NativeImage texture(int scale, boolean selected, boolean padding) {
        int w = (selected ? 24 : 182) * scale, h = (selected ? 23 : 22) * scale;
        var image = new NativeImage(w, h, true);
        image.fillRect(0, 0, w, h, selected ? 0 : 0xd0282828);
        int border = selected ? 3 : 2;
        // The top has a deliberately oversized black outline and the normal top bevel is only one texel thick.
        image.fillRect(0, 0, w, 3 * scale, 0xff080808);
        for (int i = 0; i < (selected ? 3 : 1); i++) image.fillRect(0, (3 + i) * scale, w, scale,
                selected ? new int[]{0xff399ba9, 0xff5bc3d1, 0xff8be3f1}[i] : 0xff666666);
        image.fillRect(0, h - (selected ? 2 : 1) * scale, w, (selected ? 2 : 1) * scale, 0xff080808);
        for (int i = 0; i < (selected ? 2 : 1); i++) image.fillRect(0, h - ((selected ? 3 : 2) + i) * scale, w, scale,
                selected ? new int[]{0xff389aa8, 0xff77d7e5}[i] : 0xff707070);
        image.fillRect(0, 0, scale, h, selected ? 0xbe080808 : 0xff080808);
        image.fillRect(w - scale, 0, scale, h, selected ? 0xbe080808 : 0xff080808);
        for (int i = 0; i < border; i++) {
            image.fillRect((1 + i) * scale, 0, scale, h, selected ? 0xff45adbb + i * 0x00101010 : 0xff585858 + i * 0x00101010);
            image.fillRect(w - (2 + i) * scale, 0, scale, h, selected ? 0xff3fa7b5 + i * 0x00101010 : 0xff505050 + i * 0x00101010);
        }
        if (padding) {
            // Sub-GUI-texel transparent padding tests physical source-pixel detection for HD textures.
            image.fillRect(0, 0, w, 1, 0);
            image.fillRect(0, 0, 1, h, 0);
            image.fillRect(w - 1, 0, 1, h, 0);
            image.fillRect(0, h - 1, w, 1, 0);
        }
        return image;
    }
}
