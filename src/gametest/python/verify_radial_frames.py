"""Check a native HotbarStyleClientTests reference PNG; requires Pillow, never edits images."""
import argparse
import math
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("reference", help="blockwheel-hotbar-reference-scale-N.png")
parser.add_argument("--scale", required=True, choices=(2, 3, 4), type=int)
args = parser.parse_args()
image = Image.open(args.reference).convert("RGB")
scale = args.scale
assert image.width >= 400 * scale and image.height >= 240 * scale, "Capture is too small for the requested GUI scale"
radius = 16
half_height = (math.sqrt(3) / 2 + math.sqrt(2) / 2) / 2
half_width = radius * math.sqrt(2) / 2 / half_height


def at(x, y):
    return image.getpixel((math.floor(x * scale), math.floor(y * scale)))


def run_length(x, y, color):
    left = right = x
    while left > 0 and image.getpixel((left - 1, y)) == color:
        left -= 1
    while right + 1 < image.width and image.getpixel((right + 1, y)) == color:
        right += 1
    return right - left + 1


# These are live native reference sprites rendered on the same background, not a guessed palette.
background = at(1, 1)
interior = at(24 + 11, 140 + 11)
assert interior != background, "Reference hotbar background was not drawn"
normal_layers, selected_layers = [], []
for center, border, reference_x, colors in ((80, 2, 24, normal_layers), (160, 3, 240, selected_layers)):
    assert at(center, 80) == interior, "Slot interior did not retain the native texture/alpha"
    # The entire floor patch has the same density as the hotbar: one logical texture texel per GUI pixel.
    for row in range(15 * scale):
        for column in range(15 * scale):
            actual = image.getpixel(((center - 7) * scale + column, 73 * scale + row))
            expected = image.getpixel((28 * scale + column, 144 * scale + row))
            assert actual == expected, "Background was stretched instead of retaining native hotbar texture scale"
    assert at(center + 9, 80) == at(29, 151), "Background did not repeat the native patch without stretching"
    for layer in range(border):
        expected = at(reference_x + border - layer + .5, 151.5)
        x = math.floor((center - half_width - layer - .5) * scale)
        assert image.getpixel((x, 80 * scale)) == expected, ("Native frame shade mismatch", center, layer)
        assert run_length(x, 80 * scale, expected) == scale, ("Band is not one GUI pixel wide", center, layer)
        colors.append(expected)
    shell = at(reference_x + .5, 151.5)
    assert at(center - half_width - border - .5, 80) == shell, "Outer shell does not use its own native shade/alpha"
    # Every state keeps exactly the same textured interior, without an old frame beneath selection.
    for x in range(-10 * scale, 10 * scale):
        for y in range(-8 * scale, 8 * scale):
            assert image.getpixel((80 * scale + x, 80 * scale + y)) == image.getpixel((160 * scale + x, 80 * scale + y)), "Selection changed the interior"

assert normal_layers != selected_layers[:2], "Selection did not change the native frame shades"

# The selected sprite's colored bottom border reaches its final row. Sampling one row
# too high makes its innermost band transparent, exposing the brown/world background.
# Check actual bottom-band pixels against the live source sprite beside the hexagons.
shoulder = radius * math.sqrt(3) / 4 / half_height
points = [(0, -radius), (half_width, -shoulder), (half_width, shoulder),
          (0, radius), (-half_width, shoulder), (-half_width, -shoulder)]
normals = []
for first, second in zip(points, points[1:] + points[:1]):
    dx, dy = second[0] - first[0], second[1] - first[1]
    length = math.hypot(dx, dy)
    normals.append((dy / length, -dx / length))
offsets = []
for edge in range(6):
    previous, following = normals[(edge - 1) % 6], normals[edge]
    divisor = 1 + previous[0] * following[0] + previous[1] * following[1]
    offsets.append(((previous[0] + following[0]) / divisor, (previous[1] + following[1]) / divisor))


def ring_point(index, distance):
    return (points[index][0] + offsets[index][0] * distance,
            points[index][1] + offsets[index][1] * distance)


def barycentric(point, a, b, c):
    denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
    first = ((b[1] - c[1]) * (point[0] - c[0]) + (c[0] - b[0]) * (point[1] - c[1])) / denominator
    second = ((c[1] - a[1]) * (point[0] - c[0]) + (a[0] - c[0]) * (point[1] - c[1])) / denominator
    return first, second, 1 - first - second


bottom_samples = 0
for center, border, reference_x, bottom_row in ((80, 2, 24, 20), (160, 3, 240, 22)):
    for layer in range(border):
        source_row = bottom_row - (border - layer) + 1
        for edge in (2, 3):
            next_edge = (edge + 1) % 6
            a, b = ring_point(edge, layer), ring_point(next_edge, layer)
            c, d = ring_point(next_edge, layer + 1), ring_point(edge, layer + 1)
            for fraction in (.12, .25, .5, .75, .88):
                first, second = ring_point(edge, layer + .5), ring_point(next_edge, layer + .5)
                ideal = (center + first[0] + (second[0] - first[0]) * fraction,
                         80 + first[1] + (second[1] - first[1]) * fraction)
                px, py = math.floor(ideal[0] * scale), math.floor(ideal[1] * scale)
                local = ((px + .5) / scale - center, (py + .5) / scale - 80)
                weights = barycentric(local, a, b, c)
                if min(weights) >= -1e-6:
                    along = weights[1] + weights[2]
                else:
                    weights = barycentric(local, a, c, d)
                    assert min(weights) >= -1e-6, "Bottom-band probe fell outside its quad"
                    along = weights[1]
                expected = at(reference_x + 4.5 + 14 * along, 140 + source_row + .5)
                actual = image.getpixel((px, py))
                assert actual == expected, ("Bottom border sampled the wrong row or leaked background", border, layer, edge, fraction, actual, expected)
                bottom_samples += 1
for left in (300, 330):
    changed = 0
    for row in range(20 * scale):
        for column in range(20 * scale):
            pixel = image.getpixel((left * scale + column, 80 * scale + row))
            if 2 * scale <= column < 18 * scale and 5 * scale <= row < 15 * scale:
                changed += pixel != background
            else:
                assert pixel == background, "Builder switch still draws an outer button box"
    assert changed > 0, "Frameless switch was not rendered"
# The model-projected upper-left edge has a 2:1 slope. Each physical row must advance two
# display pixels, rather than staying put and then jumping by 2 * GUI scale as the old mask did.
edges = []
for y in range(64 * scale, 69 * scale):
    edges.append(next(x for x in range(55 * scale, 80 * scale) if image.getpixel((x, y)) != background))
assert all(second - first == -2 for first, second in zip(edges, edges[1:])), "Diagonal was quantized to GUI-sized steps"
print(f"GUI scale {scale}: native texture density/repeat, normal/selected shades and alpha, 2px/3px bands, {bottom_samples} opaque bottom-band probes, straight edges and frameless switches")
