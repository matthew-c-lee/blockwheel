# Experimental wood families

Enable **Settings → Block wheel → Logs and wood (experimental)** in a compatible world. It starts OFF and is saved per player. The manual wheel works with Builder Mode off; automatic storage requires Builder Mode and its relevant feature switches.

Logs/stems stay top-left and all-bark wood/hyphae stay top-right. Planks stay up, stairs right, slabs left and fence down when no wall exists. Ordinary signs use bottom-left, at two planks per sign. Optional wooden trapdoors retain bottom-right. Input, selection, appearance and other positions are unchanged.

## Material values and vanilla crafting

The default relationships are **one log = one wood = four planks**. Typical integer values are:

| Shape | Default units |
| --- | ---: |
| Slab | 1 |
| Plank, stair, fence | 2 |
| Ordinary wooden sign | 4 |
| Optional wooden trapdoor | 6 |
| Unstripped log / stem | 8 |
| Unstripped wood / hyphae | 8 |

Both oak logs and oak wood yield four planks in Minecraft 26.2's normal crafting recipe. The vanilla four-logs-to-three-wood recipe loses material. Blockwheel's 1:1 exchange removes that loss without creating a plank-gain loop: one log → one wood → four vanilla planks → one Blockwheel log returns the original quantity. The native integration test repeats that cycle ten times using Minecraft's real recipe manager. It also checks that the vanilla four-log recipe followed by conversion returns only three logs.

The same default is inferred for other supported species only with matching recipe evidence. Stripped variants stay outside all transformation families. Bamboo's different relationship is not forced into this model. Explicit server data packs may retain other exact ratios; those use their own integer denominator. There are no hidden fractional balances.

## Manual conversion

| Input and requested target, with no other eligible family supply | Result |
| --- | --- |
| One log → wood | One wood |
| One log → signs | Two signs |
| Six planks → signs | Three signs |
| One sign → planks | Two planks |
| One wood → log | One log |
| Four planks / stairs / fences → log | One log |
| Five stairs → log | One held log and one original stair in main storage |
| 63 logs → stairs | 64 held stairs and 47 stored logs |
| 63 wood → logs | 63 held logs |

Signs are present independently of this experimental toggle and use the vanilla 16-item stack limit. Six planks plus one stick craft into three signs; Blockwheel ignores the stick. Hanging signs remain excluded.

Every target is capped at one actual stack in hand; slabs also retain their 64-item ceiling. With Builder Mode on, the manual planner consolidates compatible ordinary shapes from main storage into remaining hand capacity. Exact target items are preferred before other donor shapes. For example, 64 stairs with 44 stored logs give 60 held logs, and 64 planks with ten stored logs give 26 held wood. One plank with three stored stairs can complete a log. This explicit consolidation requires Builder Mode and is independent of conservative automatic compaction. With Builder Mode off, 64 planks become sixteen wood and ten stored logs remain untouched. Existing target logs do not join the result. The wheel still caps output to one stack and preserves raw base leftovers in both modes.

Builder Mode’s additional family withdrawals use the shared bounded exact-batch solver. With the mode off, only minimal matching source copies may finish the held stack’s last batch; extra stored sources are not swept. Only material that fits the selected stack is spent. Other hotbar slots, offhand, shulker contents, customized items, stripped variants and different species are not sources. Unusable donor fractions stay intact. Capped excess remains exact base-first physical change; incomplete held source items are preserved. When Builder Mode, Shulker Supply, Ender chest access and server permission allow it, this change first refills matching occupied named-shulker slots in the ender chest. Anything unaccepted goes to main storage, then drops if full. This routing does not convert the change or sweep existing inventory. Explicit custom ratios retain their own lossless batch sizes.

## Primary logs and secondary planks

Logs are the primary storage form. Planks are the secondary form both when log compaction is unsuitable and when complete logs leave a remainder. Signs are usable family shapes, not a preferred form for storing leftover wood. Thus 67 stairs still compact to sixteen logs and three planks. Log eligibility is checked independently from ordinary plank normalization: a small fence stack can become planks even when it cannot or should not become logs.

The log gate still requires an exact log-equivalent batch or more than one actual input stack. The over-stack check uses exact fractions and real item limits. Every candidate must fit and must not increase occupied main slots. Fewer slots win; logs win storage-form ties when eligible. Existing canonical log stacks remain destinations rather than automatic source material to unpack.

| Ordinary input | Automatic result |
| --- | --- |
| One fence | One plank |
| One sign | Two planks |
| Two signs | One log |
| Three signs | Six planks |
| Two or three stairs / fences | Two or three planks |
| Four stairs / fences | One log |
| Five, seventeen, twenty-five or sixty-three stairs | Same quantity of planks; no new log/remainder split |
| 24 stairs | Six logs |
| 28 stairs | Seven logs |
| 64 stairs | Sixteen logs |
| 65 stairs | Sixteen logs and one plank |
| 68 stairs | Seventeen logs |
| 128 stairs | Thirty-two logs |

Different compatible forms aggregate. One plank plus three stairs becomes one log, as do two fourteen-stair stacks becoming seven logs. Normalization re-evaluates the affected wooden family after a completed vanilla change to main storage, including rearrangements, removals and inbound container transfers. It no longer needs a hotbar transfer or a Builder Mode off/on cycle. The cursor, other hotbar slots, offhand, equipment, external containers, customized items and stripped materials are not normalization sources.

Direct placements prefer their destination. Plank fallback otherwise merges existing plank stacks first, then uses original storage positions. Shift-click and compaction can favor a real slot reduction. Odd slabs or custom wood ratios remain unchanged when representing their value as planks would need more occupied slots. For example, seven slabs stay seven slabs in one slot rather than becoming planks plus a slab in two slots.

These policies run at existing transaction boundaries, not through every-tick scans. Direct slot writes by unrelated mods can still bypass menu boundaries. Legacy non-wood families keep their existing hotbar-provenance policy.

## Pickup, restock, pick and named templates

Pickup replenishes eligible held shapes first, then uses the same primary/secondary storage policy. It can fill existing capacity without allocating a temporary source slot. Unaccepted input stays on the ground; automatic conversion never drops overflow.

Restock and pick block request the building shape rather than a storage form. They share the same unit values and source traversal. Restock excludes other loose hotbar stacks; explicit pick may use them. Named shulker withdrawals retain one item per originally occupied slot.

Explicit named-template filling is more opportunistic than main-storage normalization. A twelve-log template can accept six logs from twenty-five stairs while leaving one original stair. Only complete accepted batches are consumed; empty templates are not created. Existing log templates across eligible boxes have priority, then exact secondary templates and other compatible destinations. Default wood now fills log templates 1:1; a three-space log destination can accept three wood. Explicit custom 4:3 relationships still require room for complete batches.

Carried named destinations precede permitted ender destinations when experimental wood is enabled. Remote ender access keeps the existing player/server gates; carried-only filling grants no remote access. Box identities, metadata, slot types, capacities and withdrawal reserves remain intact. This adds no automatic deposits.

## Data-driven relationships and recipe evidence

The supplied `data/blockwheel/blockwheel/wood_families/default.json` keeps conversion values separate from the vanilla recipe used to prove log/wood identity:

```json
{
  "plank_suffix": "_planks",
  "pairs": [
    {"log_suffix": "_log", "wood_suffix": "_wood"},
    {"log_suffix": "_stem", "wood_suffix": "_hyphae"}
  ],
  "planks_per_log": 4,
  "log_input": 1,
  "wood_output": 1,
  "recipe_log_input": 4,
  "recipe_wood_output": 3,
  "require_recipes": true
}
```

Each candidate needs its own canonical block item, same namespace and compatible plank family. Recipe proof checks the log's plank yield and exact log-to-wood recipe. A 1:1 exchange additionally requires the same known plank yield for wood. The proof recipe cannot output more configured material value than it spends. Missing or unsupported wood remains outside the extension without removing valid logs.

Place explicit relationships under `data/<namespace>/blockwheel/wood_families/<name>.json`, using `planks`, `log` and optional `wood` IDs instead of suffix pairs. Omitted `recipe_*` fields default to `log_input` / `wood_output`, preserving older data-pack formats. Quantities are bounded to 1–16. `require_recipes:false` is an explicit server-author assertion and does not bypass stripped, block-entity or namespace exclusions.

To replace a built-in suffix relationship, override its same resource path or disable that definition before adding a conflicting explicit relationship. Existing explicit 4:3 definitions remain supported: they can use `log_input:4`, `wood_output:3` and their original proof settings. Their physical batch accounting is unchanged; the registered custom-family tests retain those cases. Invalid experimental data falls back to ordinary families. Conflicts, unavailable forms and values exceeding the 64-unit wire bound are conservatively omitted. Resource reloads rebuild immutable views and invalidate old wheel sessions.

Stripped logs/wood/stems/hyphae and shelves have no transformation family under either toggle value. Exact ordinary handling remains available, including unchanged stripped-item supply. Existing optional pick substitutes select actual items and do not grant material equivalence. Compaction remains bounded and family-local, not an exhaustive inventory sorter.
