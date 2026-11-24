"""Check native synthetic resource-pack captures; reads PNGs without editing them."""
import argparse
import math
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("reference")
parser.add_argument("--scale", required=True, choices=(2, 3, 4), type=int)
parser.add_argument("--source-scale", choices=(1, 2), default=1, type=int)
args = parser.parse_args()
image = Image.open(args.reference).convert("RGB")
scale = args.scale
shell_depth = .5 if args.source_scale == 1 else .75


def at(x, y):
    return image.getpixel((math.floor(x * scale), math.floor(y * scale)))


radius = 16
half_height = (math.sqrt(3) / 2 + math.sqrt(2) / 2) / 2
width = radius * math.sqrt(2) / 2 / half_height
shoulder = radius * math.sqrt(3) / 4 / half_height
points = [(0, -radius), (width, -shoulder), (width, shoulder), (0, radius), (-width, shoulder), (-width, -shoulder)]
normals = []
for a, b in zip(points, points[1:] + points[:1]):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    normals.append((dy / length, -dx / length))
offsets = []
for i in range(6):
    a, b = normals[(i - 1) % 6], normals[i]
    divisor = 1 + a[0] * b[0] + a[1] * b[1]
    offsets.append(((a[0] + b[0]) / divisor, (a[1] + b[1]) / divisor))


def ring(index, distance):
    return (points[index][0] + offsets[index][0] * distance, points[index][1] + offsets[index][1] * distance)


def barycentric(point, a, b, c):
    denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    first = ((b[1] - c[1]) * (point[0] - c[0]) + (c[0] - b[0]) * (point[1] - c[1])) / denominator
    second = ((c[1] - a[1]) * (point[0] - c[0]) + (a[0] - c[0]) * (point[1] - c[1])) / denominator
    return first, second, 1 - first - second


samples = 0
assert at(35.5, 141.5) == at(24 + shell_depth, 151.5), "Fixture must reproduce the old top-strip/black-outline collision"
for center, border, reference_x in ((80, 2, 24), (160, 3, 240)):
    selected = border == 3
    # This fixture is deliberately asymmetric: top outline=3 texels, selected bottom=2.
    # The normal top/bottom bevels contain just one source texel; selection has three/two.
    strip_starts = (3, 1, 2 if selected else 1, 1)
    strip_widths = (3 if selected else 1, border, 2 if selected else 1, border)
    for layer in range(border):
        for edge in range(6):
            source_edge = 1 if edge == 1 else 2 if edge in (2, 3) else 3 if edge == 4 else 0
            depth = strip_starts[source_edge] + (border - layer - .5) * strip_widths[source_edge] / border
            next_edge = (edge + 1) % 6
            a, b = ring(edge, layer), ring(next_edge, layer)
            c, d = ring(next_edge, layer + 1), ring(edge, layer + 1)
            for fraction in (.12, .25, .5, .75, .88):
                first, second = ring(edge, layer + .5), ring(next_edge, layer + .5)
                px = math.floor((center + first[0] + (second[0] - first[0]) * fraction) * scale)
                py = math.floor((80 + first[1] + (second[1] - first[1]) * fraction) * scale)
                point = ((px + .5) / scale - center, (py + .5) / scale - 80)
                weights = barycentric(point, a, b, c)
                if min(weights) >= -1e-6:
                    along = weights[1] + weights[2]
                else:
                    weights = barycentric(point, a, c, d)
                    assert min(weights) >= -1e-6, "Frame probe fell outside its quad"
                    along = weights[1]
                tangent = 4.5 + 14 * along
                w, h = (24, 23) if selected else (182, 22)
                x, y = (w - depth, tangent) if source_edge == 1 else (tangent, h - depth) if source_edge == 2 else (depth, tangent) if source_edge == 3 else (tangent, depth)
                expected = at(reference_x + x, 140 + y)
                actual = image.getpixel((px, py))
                assert actual == expected, ("Pack frame sampled outline/padding instead of bevel", border, layer, edge, fraction, actual, expected)
                assert expected != at(reference_x + shell_depth, 151.5), "Fixture bevel must differ from shell"
                samples += 1
    for row in range(15 * scale):
        for column in range(15 * scale):
            assert image.getpixel(((center - 7) * scale + column, 73 * scale + row)) == image.getpixel((28 * scale + column, 144 * scale + row)), "Pack floor density/alpha changed"
    for edge in range(6):
        next_edge = (edge + 1) % 6
        first, second = ring(edge, border + .5), ring(next_edge, border + .5)
        x, y = center + (first[0] + second[0]) / 2, 80 + (first[1] + second[1]) / 2
        assert at(x, y) == at(reference_x + shell_depth, 151.5), ("Shell thickness or native alpha changed", border, edge, x, y, at(x, y), at(reference_x + shell_depth, 151.5))
print(f"GUI scale {scale}: {samples} pack bevel probes across all six edges; no extra black band; fixed 2px/3px bevels, shell, floor density and alpha")
