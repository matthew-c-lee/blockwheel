# Changelog

## 1.0.3 — Easy Place single-click hotfix

- Finish the original Easy Place click after a missing item arrives from supply. Resume Litematica once with the server-confirmed hand stack, including when the activation button has already been released and hold-to-place is disabled.
- Cancel delayed placement if the original target, selected slot, world, settings or inventory context changes. Keep native placement checks, orientation and duplicate-placement protection.
- Replace repeated Easy Place calls in the integration fixture with single native right clicks, and cover cancellation after target or slot changes.

## 1.0.2 — Easy Place, bulk deposits and fence gates

- Let Litematica Easy Place request missing building items through Hand restock, using the configured refill amount, compatible family material and enabled carried/ender shulker supplies. Litematica keeps its own pick slots and placement behavior; all inventory changes remain server-authoritative.
- Include the offhand after main inventory and hotbar in a completed 0.6-second dump hold. Short presses remain main-inventory only. Exact matching, Builder family conversion and experimental log packing share this scope.
- Scroll over the fence option to switch between fence and fence gate in the same wheel position. One gate is worth two planks, ignoring sticks; gates join their plank families and share ordinary conservation and stack limits.
- Add an optional Litematica 0.28.8 / MaLiLib 0.29.5 client profile without bundling or requiring either mod.

## 1.0.1 — Hotfix

- Fix automatic restocking after offhand placement, item use and completed eating, including the last item. Both hands use the same refill settings, supply permissions and named reserves without spending the other hand's loose items.
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
