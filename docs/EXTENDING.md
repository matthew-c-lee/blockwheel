# Data-defined forms and families

Blockwheel reads server data packs on startup and successful `/reload`. The server sends each wheel's authoritative forms to the client. Clients do not need the same data pack. A resource pack is optional for translated labels; an unknown translation key falls back to a readable form name.

## How automatic discovery works

1. Read the live registry, including modded blocks. Only blocks with their own canonical block item participate.
2. Classify non-base forms by suffix or block tag. Built-in definitions cover stairs, slabs, walls, fences and optional wooden trapdoors.
3. Use unambiguous shaped crafting recipes to identify a form's full-block material. A recipe must use one unique full-cube block material. Vanilla sticks and nether-brick items are allowed as fence auxiliaries. Tags, ingredient alternatives, custom output components and ambiguous materials are not treated as proof of equivalence.
4. Otherwise, try same-namespace full-cube base IDs in the data-defined priority order: `<stem>_planks`, `<stem>`, `<stem>_block`, `<stem>s`. This handles wood, quartz, purpur, bricks and tiles. Blocks with block entities are not inferred as full-cube bases.
5. Group forms around that base. Two or more same-stem forms may also form a family without a full block. Omit ambiguous duplicate forms instead of selecting one arbitrarily.
6. Apply explicit family definitions, which detach their members from inferred families. A final safety filter excludes anvils, shelves and stripped blocks, and rejects mixed copper oxidation/waxing families, including explicit definitions.

Stonecutting is deliberately excluded as an equivalence source: one stonecutting input can produce multiple finishes, which would merge distinct families. Ordinary logs/planks and distinct stone finishes remain separate unless safely defined otherwise. Copper waxing/oxidation, stripped exclusions and anvil protection cannot be bypassed by an explicit family. The optional wood extension uses separately verified relationships; namespaces and species are never inferred as interchangeable.

New conventionally named blocks normally require no change. Arbitrary mod naming or unusual ingredients can require an explicit family file. Discovery does not claim to infer every possible relationship from an unrestricted recipe graph.

## Add a shape, such as flooring

Copy the supplied `examples/flooring-pack/` directory into your world's `datapacks` directory, or include its contents in a mod's resources. Run `/reload`.

The file `data/my_pack/blockwheel/shapes/flooring.json` defines the shape ID `my_pack:flooring`:

```json
{
  "translation_key": "shape.my_pack.flooring",
  "units": 1,
  "order": 50,
  "suffixes": ["_flooring"],
  "block_tags": ["my_mod:flooring"]
}
```

- `units`: material value, integer 1–64. Built-in slabs have 1, ordinary full blocks/stairs/walls/fences have 2, wooden signs have 4, and wooden trapdoors have 6. Flooring with 1 yields two pieces per full block. Set it to 2 for a one-for-one conversion.
- `order`: wheel order; ties are sorted by shape ID.
- `suffixes`: identify a form and derive its material stem. The longest matching suffix wins.
- `block_tags`: an additional way to identify the form. Tags can handle irregular names when an unambiguous recipe or explicit family supplies the relationship. A tag alone does not guess a base.
- `base_tags`: optional scope for inferred family bases; at least one listed tag must match. For example, `minecraft:planks` keeps a sign definition in wooden plank families, even if a nonwood block is tagged as a standing sign. It also applies to naming fallback. Explicit family overrides remain authoritative.
- `translation_key`: optional; defaults from the shape ID. A client resource pack can translate it. The UI otherwise humanizes the ID.
- `disabled`: optional; set to `true` in a higher-priority copy of the same resource to disable that form definition.

For example, registered blocks `my_mod:limestone`, `my_mod:limestone_slab` and `my_mod:limestone_flooring` will discover one family. A data pack does not register the flooring block itself; that block must already be supplied by Minecraft or another mod.

There is exactly one base form. Its built-in resource is `data/blockwheel/blockwheel/shapes/block.json`. Its `base_suffixes` list controls fallback base lookup, so packs can add a naming convention without changing Java.

The renderer supports up to 32 definitions. Standard forms retain fixed directions: base up, stairs right, wall down and slab left. A fence-only family uses down; when wall and fence coexist, fence gets bottom-left. The ordinary `blockwheel:sign` shape is reserved at bottom-left in wooden families. If a custom family already has both wall and fence, their existing positions win and the sign uses another gap or page. The optional `blockwheel:trapdoor` shape is reserved at bottom-right. Experimental `blockwheel:log` occupies top-left and `blockwheel:wood` top-right, while the plank `blockwheel:block` stays up. Missing forms leave gaps, and diagonal alternates do not change cardinal coordinates or icon sizes. Other forms fill free diagonal positions before additional pages with up to eight slots. `core/RadialPages.java` applies this policy by shape ID, independently of material IDs, recipes and conversion values. New data-defined forms receive their own choice without a new packet field or conversion implementation.

The supplied trapdoor definition uses only `minecraft:wooden_trapdoors`, with no broad `_trapdoor` suffix. Its unambiguous plank recipe supplies the family, excluding iron/copper trapdoors. Six material units match three two-unit planks. The per-player opt-in is enforced by both manual conversion and the Builder material projection; it defaults off. Data-pack overrides remain authoritative, so editing built-in exchange values is a deliberate server change.

The built-in `sign.json` identifies `minecraft:standing_signs` and scopes inferred bases to `minecraft:planks`. The shaped recipe identifies the wood species while the stick is ignored. Its four units equal two standard planks, matching vanilla's six-plank/three-sign recipe. This avoids a broad `_sign` suffix that could accidentally include hanging signs. New modded wood types need only reliable matching tags and an unambiguous shaped recipe; unusual material values can be supplied through explicit shape/family data. Shape values come from the data definition, not arbitrary recipe yield inference.

## Override an irregular family

Create `data/my_pack/blockwheel/families/limestone.json`:

```json
{
  "members": {
    "blockwheel:block": "my_mod:limestone_block",
    "blockwheel:stairs": "my_mod:carved_limestone_steps",
    "blockwheel:slab": "my_mod:half_limestone",
    "my_pack:flooring": "my_mod:limestone_flooring"
  }
}
```

The file's ID is `my_pack:limestone`. Shape IDs and block IDs are fully qualified. Include only existing blocks and registered shapes. A block may belong to one family, and a family may have one block per shape. Two usable members are enough; every family does not need every form.

Explicitly assigned blocks are removed from inferred families in both directions. Conflicting explicit assignments or unknown blocks/shapes reject the rebuild, preventing partial or order-dependent catalogs. The server keeps its last valid catalog and logs the problem; initial startup begins with an empty catalog if no valid catalog has loaded.

## Disable a family

Automatic family IDs are the full base-block ID. To disable the oak-plank family, use `data/minecraft/blockwheel/families/oak_planks.json`:

```json
{ "disabled": true }
```

To suppress particular members independently, provide a family file with `disabled: true` and a `members` map listing those blocks. They are detached from automatic groups without creating a replacement group.

## Optional wood relationships

Server resources under `data/<namespace>/blockwheel/wood_families/` extend existing plank families only in the opted-in experimental view. Defaults establish one log to four planks and a 1:1 log/wood exchange, separately proving the native four-log/three-wood recipe and matching plank yields; explicit overrides can assert unusual valid names. Native stripped results and conventional stripped IDs remain excluded, as do shelves. Same-namespace, ownership, component and bounded-unit checks still apply. Bamboo is conservatively omitted from ordinary log inference. See [WOOD-FAMILIES.md](WOOD-FAMILIES.md) for the complete schema and examples.

Wood views rescale an ordinary family to an exact integer denominator, using default values slabs 1, planks/stairs/fences 2, log/wood 8 and enabled trapdoor 6; explicit 4:3 overrides may still rescale to 3/6/24/32. Do not assume the globally loaded shape value is the per-player member value; use authoritative family members. Cached trapdoor/wood views are shared by manual, acquisition, storage and template policies.

## Reload and compatibility

Resources follow normal data-pack priority: the highest-priority file with the same namespace and path wins. Different family files coexist. Invalid JSON rejects a rebuild. Successful reloads discard existing wheel sessions, so an old menu cannot spend a stale conversion rule.

This design makes content additions mostly data work. A future Minecraft API change can still require updating the loader, rendering or resource-access integration. A future shape with placement mechanics beyond ordinary block items may require code for those mechanics, while its inventory exchange and radial entry can remain data-defined.
