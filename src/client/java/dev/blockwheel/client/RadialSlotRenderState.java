package dev.blockwheel.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.blockwheel.client.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Straight polygon bands sampling the actual, resource-pack-aware hotbar sprites. */
final class RadialSlotRenderState implements GuiElementRenderState {
    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final Map<Integer, Hexagon> SHAPES = new HashMap<>();
    private record Point(float x, float y) {}
    private record Uv(float u, float v) {}
    private record Patch(float left, float top, List<Point> points) {}
    private final Matrix3x2f pose;
    private final Hexagon shape;
    private final TextureAtlasSprite hotbar, frame;
    private final HotbarFrameProfile frameProfile;
    private final TextureSetup textureSetup;
    private final ScreenRectangle bounds, scissorArea;
    private final int border;
    private final float feather;

    private RadialSlotRenderState(GuiGraphicsExtractor graphics, int x, int y, int radius, boolean selected) {
        var client = Minecraft.getInstance();
        pose = new Matrix3x2f(graphics.pose()).translate(x, y);
        shape = SHAPES.computeIfAbsent(radius, Hexagon::new);
        var atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        hotbar = atlas.getSprite(HOTBAR);
        frame = selected ? atlas.getSprite(SELECTION) : hotbar;
        frameProfile = HotbarFrameProfile.of(frame.contents(), selected);
        textureSetup = TextureSetup.singleTexture(atlas.getTextureView(), atlas.getSampler());
        border = selected ? 3 : 2;
        // Soften only the outside silhouette over one physical display pixel, independent of GUI scale.
        feather = 1f / (float) client.getWindow().getGuiScale();
        int extent = (int) Math.ceil(radius + border + 3);
        bounds = new ScreenRectangle(-extent, -extent, extent * 2, extent * 2).transformMaxBounds(pose);
        // This state is used only by the full-screen radial, outside any nested inventory scissors.
        scissorArea = new ScreenRectangle(0, 0, graphics.guiWidth(), graphics.guiHeight());
    }

    static void draw(GuiGraphicsExtractor graphics, int x, int y, int radius, boolean selected) {
        ((GuiGraphicsAccessor) graphics).blockwheel$getRenderState()
                .addGuiElement(new RadialSlotRenderState(graphics, x, y, radius, selected));
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }
    @Override public TextureSetup textureSetup() { return textureSetup; }
    @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Override public ScreenRectangle bounds() { return bounds; }

    @Override public void buildVertices(VertexConsumer vertices) {
        for (var patch : shape.patches) {
            var points = patch.points(); var first = points.getFirst();
            for (int index = 1; index + 1 < points.size(); index++) {
                var next = points.get(index + 1); var current = points.get(index);
                vertex(vertices, first, interior(first, patch), 255);
                vertex(vertices, next, interior(next, patch), 255);
                vertex(vertices, current, interior(current, patch), 255);
                vertex(vertices, current, interior(current, patch), 255);
            }
        }
        for (int layer = 0; layer < border; layer++) band(vertices, layer, layer + 1, border - layer, false, 255, 255);
        float solidEdge = border + 1 - feather / 2;
        band(vertices, border, solidEdge, 0, true, 255, 255);
        band(vertices, solidEdge, border + 1 + feather / 2, 0, true, 255, 0);
    }

    private void band(VertexConsumer vertices, float inside, float outside, int sourceLayer, boolean shell,
                      int innerAlpha, int outerAlpha) {
        var in = shape.at(inside); var out = shape.at(outside);
        for (int edge = 0; edge < 6; edge++) {
            int next = (edge + 1) % 6;
            var first = frame(edge, sourceLayer, shell, false);
            var second = frame(edge, sourceLayer, shell, true);
            vertex(vertices, in[edge], first, innerAlpha);
            vertex(vertices, in[next], second, innerAlpha);
            vertex(vertices, out[next], second, outerAlpha);
            vertex(vertices, out[edge], first, outerAlpha);
        }
    }

    private Uv interior(Point point, Patch patch) {
        // Repeat the actual floor patch at one logical texel per GUI pixel. Exclude its slot-edge shadow.
        float u = (4 + Math.clamp(point.x() - patch.left(), 0, 15)) / 182;
        float v = (4 + Math.clamp(point.y() - patch.top(), 0, 15)) / 22;
        return new Uv(hotbar.getU(u), hotbar.getV(v));
    }

    private Uv frame(int edge, int layer, boolean shell, boolean end) {
        boolean selected = border == 3;
        float w = selected ? 24 : 182, h = selected ? 23 : 22;
        float along = end ? 18.5f : 4.5f;
        float x, y;
        if (shell) {
            // Vanilla's selection shell has its own translucent shade; do not leave the old dark frame beneath it.
            x = frameProfile.shellDepth(); y = along;
        } else if (edge == 1) {
            x = w - frameProfile.depth(1, layer); y = along;
        } else if (edge == 2 || edge == 3) {
            x = along; y = h - frameProfile.depth(2, layer);
        } else if (edge == 4) {
            x = frameProfile.depth(3, layer); y = along;
        } else {
            x = along; y = frameProfile.depth(0, layer);
        }
        return new Uv(frame.getU(x / w), frame.getV(y / h));
    }

    private void vertex(VertexConsumer vertices, Point point, Uv uv, int alpha) {
        vertices.addVertexWith2DPose(pose, point.x(), point.y()).setUv(uv.u(), uv.v())
                .setColor(255, 255, 255, alpha);
    }

    /** Parallel-edge offsets keep every band a constant perpendicular width, even at the miters. */
    private static final class Hexagon {
        private final float radius, width;
        private final Point[] points, offsets;
        private final List<Patch> patches = new ArrayList<>();
        private final Map<Float, Point[]> rings = new HashMap<>();
        private Hexagon(int radius) {
            this.radius = radius;
            double halfHeight = (Math.sqrt(3) / 2 + Math.sqrt(2) / 2) / 2;
            width = (float) (radius * Math.sqrt(2) / 2 / halfHeight);
            float shoulder = (float) (radius * Math.sqrt(3) / 4 / halfHeight);
            points = new Point[]{new Point(0, -radius), new Point(width, -shoulder), new Point(width, shoulder),
                    new Point(0, radius), new Point(-width, shoulder), new Point(-width, -shoulder)};
            var normals = new Point[6];
            for (int i = 0; i < 6; i++) {
                var a = points[i]; var b = points[(i + 1) % 6];
                float dx = b.x() - a.x(), dy = b.y() - a.y(), length = (float) Math.hypot(dx, dy);
                normals[i] = new Point(dy / length, -dx / length);
            }
            offsets = new Point[6];
            for (int i = 0; i < 6; i++) {
                var previous = normals[(i + 5) % 6]; var next = normals[i];
                float scale = 1 / (1 + previous.x() * next.x() + previous.y() * next.y());
                offsets[i] = new Point((previous.x() + next.x()) * scale, (previous.y() + next.y()) * scale);
            }
            rings.put(0f, points);
            for (int row = (int) Math.floor((-radius + 7) / 15.0); row <= (int) Math.floor((radius + 7) / 15.0); row++) {
                for (int column = (int) Math.floor((-width + 7) / 15.0); column <= (int) Math.floor((width + 7) / 15.0); column++) {
                    float left = column * 15 - 7, top = row * 15 - 7;
                    List<Point> clipped = new ArrayList<>(Arrays.asList(points));
                    clipped = clip(clipped, true, left, true); clipped = clip(clipped, true, left + 15, false);
                    clipped = clip(clipped, false, top, true); clipped = clip(clipped, false, top + 15, false);
                    if (clipped.size() >= 3) patches.add(new Patch(left, top, List.copyOf(clipped)));
                }
            }
        }
        private static List<Point> clip(List<Point> source, boolean horizontal, float boundary, boolean greater) {
            List<Point> result = new ArrayList<>();
            if (source.isEmpty()) return result;
            var previous = source.getLast();
            float previousValue = horizontal ? previous.x() : previous.y();
            boolean previousInside = greater ? previousValue >= boundary : previousValue <= boundary;
            for (var current : source) {
                float value = horizontal ? current.x() : current.y();
                boolean inside = greater ? value >= boundary : value <= boundary;
                if (inside != previousInside) {
                    float ratio = (boundary - previousValue) / (value - previousValue);
                    result.add(new Point(previous.x() + (current.x() - previous.x()) * ratio,
                            previous.y() + (current.y() - previous.y()) * ratio));
                }
                if (inside) result.add(current);
                previous = current; previousValue = value; previousInside = inside;
            }
            return result;
        }
        private Point[] at(float distance) {
            return rings.computeIfAbsent(distance, value -> {
                var result = new Point[6];
                for (int i = 0; i < 6; i++) result[i] = new Point(points[i].x() + offsets[i].x() * value,
                        points[i].y() + offsets[i].y() * value);
                return result;
            });
        }
    }
}
