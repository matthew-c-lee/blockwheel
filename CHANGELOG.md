# Changelog

## 1.0.1 — Hotfix

- When a radial conversion cannot make one complete target, enabled Shulker Supply can retrieve existing exact targets from eligible carried or ender-chest shulkers. Two held planks can retrieve stored logs while both planks move into main inventory outside the hotbar.
- Preserve named-box reserves and metadata, respect supply/ender permissions, and leave everything unchanged if the displaced stack cannot fit. Successful conversions retain their existing behavior.
- Include the Minecraft version in build filenames and provide production/sources JARs with SHA-256 checksums through GitHub Actions.

## 1.0.0 — Initial public release

- Hold R, move toward a shape and release to transform compatible building blocks. Fixed directions, a neutral cancel area and Minecraft hotbar styling keep the wheel compact and predictable. Highlighted borders retain all three colored bands through the bottom corners. Resource-pack frame sampling separates outline/padding from the bevel while retaining vanilla's appearance.
- Convert between full blocks, stairs, slabs, walls and fences. Ordinary wooden signs cost two planks each, ignoring sticks, and occupy bottom-left. Optional wooden trapdoors retain their vanilla plank ratio.
- Keep output to one native stack in hand, preserve exact physical leftovers, and safely route inventory overflow. Signs use their native 16-item limit.
- Enable optional Builder Mode for pickup routing, storage normalization, hand restock, family-aware pick block and radial material consolidation. The wheel remains usable by itself.
- Optionally use carried and personal ender-chest shulkers, with one item protected in every occupied named template slot. Short-release refill uses main inventory; a 600 ms hold includes the hotbar exactly once, with button progress feedback.
- Configure pick fallbacks through a searchable visual editor, including log/wood, anvil and copper defaults. Configure gameplay and interface features through the built-in screen or optional Mod Menu.
- Optionally extend wood families with unstripped logs and wood. Exact integer accounting and slot-aware compaction prefer logs and secondary planks without increasing clutter. Stripped variants, shelves and hanging signs remain outside this family system.
- Discover compatible modded forms from data, tags and recipes. Shape definitions can scope inferred bases through `base_tags`; overrides support unusual relationships.

Targets Minecraft Java Edition 26.2, Fabric Loader 0.19.5+, Fabric API 0.160.0+26.2 and Java 25. Mod Menu and Tweakeroo are optional and are not bundled. See [release verification](docs/VERIFICATION.md) for executed checks and practical limits.
