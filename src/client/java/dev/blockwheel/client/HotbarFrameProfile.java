package dev.blockwheel.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.blockwheel.client.mixin.SpriteContentsAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;

import java.util.Map;
import java.util.WeakHashMap;

/** Separates a pack's outer outline/padding from the strips used for the fixed-width bevel. */
final class HotbarFrameProfile {
    // Sprite identities change on resource reload. No image or atlas is retained by the cached value.
    private static final Map<SpriteContents, HotbarFrameProfile> CACHE = new WeakHashMap<>();
    private final Strip[] strips;
    private final int border;
    private final float shellDepth;
    private record Strip(float start, float width) {}

    private HotbarFrameProfile(Strip[] strips, int border, float shellDepth) {
        this.strips = strips; this.border = border; this.shellDepth = shellDepth;
    }

    static HotbarFrameProfile of(SpriteContents contents, boolean selected) {
        return CACHE.computeIfAbsent(contents, sprite -> analyze(
                ((SpriteContentsAccessor) sprite).blockwheel$getOriginalImage(), sprite.width(), sprite.height(), selected));
    }

    static HotbarFrameProfile analyze(NativeImage image, int width, int height, boolean selected) {
        int border = selected ? 3 : 2;
        int logicalWidth = selected ? 24 : 182, logicalHeight = selected ? 23 : 22;
        int shell = 0;
        float shellDepth = .5f;
        if (!image.isClosed()) {
            int middle = Math.min(height - 1, (int) (11.5f * height / logicalHeight));
            int maximum = Math.max(1, Math.round(6f * width / logicalWidth));
            int pixel = 0;
            while (pixel < maximum && image.getPixel(pixel, middle) >>> 24 == 0) pixel++;
            if (pixel == maximum) pixel = 0;
            shell = image.getPixel(pixel, middle);
            // Use the center of a real source pixel, never an HD pixel boundary next to padding.
            shellDepth = (pixel + .5f) * logicalWidth / width;
        }
        Strip[] strips = new Strip[4]; // Top, right, bottom, left, measured inward from the image edge.
        for (int edge = 0; edge < strips.length; edge++) {
            // Preserve the known vanilla layout if a strip cannot be identified safely.
            strips[edge] = new Strip(selected && edge == 2 ? 0 : 1, border);
            if (image.isClosed()) continue;
            float scale = (float) (edge % 2 == 0 ? height : width) / (edge % 2 == 0 ? logicalHeight : logicalWidth);
            int floor = image.getPixel((int) ((edge == 1 ? logicalWidth - 11.5f : 11.5f) * width / logicalWidth),
                    (int) ((edge == 2 ? logicalHeight - 11.5f : 11.5f) * height / logicalHeight));
            int limit = Math.max(1, Math.round(6 * scale));
            int start = 0;
            while (start < limit && matches(image, width, height, logicalWidth, logicalHeight, edge, start, shell, true)) start++;
            if (start == limit) continue;
            int end = start;
            int maximum = Math.min(limit, start + Math.max(1, Math.round(border * scale)));
            while (end < maximum && !matches(image, width, height, logicalWidth, logicalHeight, edge, end, floor, false)) end++;
            // A flat/transparent texture without a distinguishable bevel is ambiguous; keep the safe layout.
            if (end > start) strips[edge] = new Strip(start / scale, (end - start) / scale);
        }
        return new HotbarFrameProfile(strips, border, shellDepth);
    }

    float depth(int edge, int sourceLayer) {
        Strip strip = strips[edge];
        return strip.start + (sourceLayer - .5f) * strip.width / border;
    }

    float shellDepth() { return shellDepth; }

    private static boolean matches(NativeImage image, int width, int height, int logicalWidth, int logicalHeight,
                                   int edge, int depth, int reference, boolean shell) {
        int count = 0;
        // Use a majority across the straight strip, avoiding corners and individual texture flecks.
        for (float along : new float[]{6.5f, 9, 11.5f, 14, 16.5f}) {
            int x = edge % 2 == 0 ? (int) (along * width / logicalWidth) : edge == 1 ? width - depth - 1 : depth;
            int y = edge % 2 == 1 ? (int) (along * height / logicalHeight) : edge == 2 ? height - depth - 1 : depth;
            if (similar(image.getPixel(x, y), reference, shell)) count++;
        }
        return count >= 3;
    }

    private static boolean similar(int color, int reference, boolean shell) {
        int alpha = color >>> 24, referenceAlpha = reference >>> 24;
        if (alpha == 0) return shell || referenceAlpha == 0;
        if (referenceAlpha == 0) return false;
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        int otherRed = reference >> 16 & 255, otherGreen = reference >> 8 & 255, otherBlue = reference & 255;
        // Vanilla's top outline is opaque black; its selected side outline is translucent near-black.
        if (shell && Math.max(Math.max(red, green), blue) <= 12
                && Math.max(Math.max(otherRed, otherGreen), otherBlue) <= 12) return true;
        int tolerance = shell ? 8 : 12;
        return Math.abs(alpha - referenceAlpha) <= tolerance && Math.abs(red - otherRed) <= tolerance
                && Math.abs(green - otherGreen) <= tolerance && Math.abs(blue - otherBlue) <= tolerance;
    }
}
