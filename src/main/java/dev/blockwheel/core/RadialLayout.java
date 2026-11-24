package dev.blockwheel.core;

import java.util.function.IntPredicate;

/** Geometry only: no rendering backend assumptions, and no fixed number of forms. */
public record RadialLayout(double centerX, double centerY, double radius, int nodeSize, int count) {
    /** A neutral center lets opening/releasing or returning to the middle cancel. */
    public static final double CENTER_DEAD_ZONE = 8;

    public RadialLayout {
        if (count < 1 || count > 8) throw new IllegalArgumentException("A wheel page needs 1..8 entries");
    }

    public record Point(double x, double y) {}

    public static RadialLayout fit(int width, int height, int count) {
        // All sizes are GUI pixels, just like the hotbar's 16-pixel item icons.
        // Alternates must not move or resize the four muscle-memory positions.
        double radius = Math.min(64, Math.min((width - 60) / 2.0, (height - 72) / 2.0));
        radius = Math.max(36, radius);
        double available = height / 2.0 - 25 - radius;
        int nodeSize = (int) Math.max(14, Math.min(available, Math.min(22, radius * Math.sin(Math.PI / 4) * 0.72)));
        return new RadialLayout(width / 2.0, height / 2.0 - 3, radius, nodeSize, count);
    }

    /** 24 GUI pixels at normal sizes; shrink further only on a constrained viewport. */
    public float itemScale() { return Math.min(1.5f, (nodeSize - 8) / 8f); }

    public Point node(int index) {
        double angle = -Math.PI / 2 + index * 2 * Math.PI / count;
        return new Point(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius);
    }

    /** Closest visible node anywhere outside the neutral center; no icon hitbox required. */
    public int closestNode(double mouseX, double mouseY) {
        return closestNode(mouseX, mouseY, slot -> true);
    }

    /** Empty reserved positions do not draw icons or participate in nearest-icon selection. */
    public int closestNode(double mouseX, double mouseY, IntPredicate visible) {
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                || Math.hypot(mouseX - centerX, mouseY - centerY) <= CENTER_DEAD_ZONE) return -1;
        int closest = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if (!visible.test(i)) continue;
            Point point = node(i);
            // Match the integer centers used to draw the icons.
            double distance = Math.hypot(mouseX - Math.round(point.x()), mouseY - Math.round(point.y()));
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = i;
            }
        }
        return closest;
    }
}
