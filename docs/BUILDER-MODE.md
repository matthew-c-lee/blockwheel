# Builder Mode

Builder Mode uses physical items in your existing inventory and optional shulker supplies. It adds no storage slots, containers, virtual balances or special block items.

Install the same Blockwheel release on the client and server, with Minecraft 26.2, Java 25, Fabric Loader 0.19.5 or newer and Fabric API 0.160.0+26.2 or newer for 26.2.

## Enable and configure

The Block wheel page offers a radial-only action. The Interface tab can independently hide the Builder switch and named-shulker controls; both visibility settings start ON. Hidden widgets have no click targets. Bind **Open Blockwheel Settings**, or use optional Mod Menu, to retain settings access. The opt-in **Use only the block wheel** disables the Builder master and hides both controls without resetting individual feature choices.

Open your inventory and use the small frameless switch in its top-right corner, above the crafting result slot. Its surrounding button box has been removed. Left-click toggles Builder Mode. OFF puts the thumb on the left of a gray track; ON moves it right on a muted green track. The thumb's mark also changes. Right-click the same control to open Blockwheel Settings, then select Builder for the master switch and compaction options. The first page is Block wheel; Restock, Supply and Pick block group the optional retrieval settings. Its standard tooltip explains the current state and both clicks. Confirmed toggles play the enchanting-table sound for ON or the grindstone sound for OFF, with **Builder Mode: ON/OFF** above the hotbar. The unset-by-default Builder key uses the same feedback. The Interface tab can hide the inventory-control tooltips or just the ON/OFF overlay; both hide options start OFF, and sounds/narration remain available.

Builder Mode defaults to disabled for new players. The five original feature switches default to enabled, ready for when the master switch is turned on. Preferences are saved in each world's player data, follow respawns, and survive logout and server restarts. Different servers and worlds retain their own preferences. Existing saved switches are preserved during upgrade.

| Setting | Behavior while Builder Mode is on |
| --- | --- |
| Pickup routing | Replenish matching hotbar shapes, then store canonical base material |
| Storage normalization | Normalize existing main-storage variants on activation and variants moved from hotbar to main storage |
| Hand restock | Refill the shape you are placing; with Supply enabled, also refill other items by exact match |
| Pick block | Obtain a missing item from eligible supplies; convert compatible family shapes or move exact matches unchanged |
| Material tooltips | Show exact available base-block and slab equivalents |

The **Supply** tab adds a master switch, ender chest access, shulker pick block and shulker material tooltips. The supply master defaults **OFF** for new and existing players. Its three suboptions default ON, ready for opt-in; their parent features and the server's remote-access policy still apply. Turning Supply off preserves your suboptions. See [SHULKER-SUPPLY.md](SHULKER-SUPPLY.md) for priorities, protected slots, settings and server configuration.

Turn Builder Mode off when crafting or sorting specific variants. Disabling an individual feature does not disable the other switches. The manual hold-R radial works in both modes. Its fixed directions are block up, stairs right, wall/fence down and slab left. Every manual radial conversion produces one held target stack at most, using its actual stack limit; slabs retain their additional 64-item ceiling. Excess base material uses eligible named ender-shulker templates first when supply is enabled, then main storage, then drops if full. The selected target does not fill additional inventory stacks. For example, 63 experimental logs become 64 stairs in hand and 47 stored logs.

With Builder Mode on, the manual wheel consolidates compatible ordinary family material from main storage into one selected hand stack. With it off, the wheel converts the held stack, retains its one-stack cap and base leftovers, and does not merge stored target items or convert other stored family shapes. Minimal matching copies may still complete the held stack’s last batch. One held slab plus one stored slab becomes one full block in either mode. With Builder Mode on, one held plank plus three stored stairs can become one log. With experimental wood enabled, 64 held planks and ten stored logs become 26 held wood. Exact target items join first before additional donor forms are converted. Unaccepted material stays physical. Other hotbar slots, offhand, shulkers and customized stacks are not manual sources. With Builder Mode, Shulker Supply and permitted ender access on, conversion leftovers first refill matching named ender-shulker templates, then use main inventory and normal overflow handling.

The radial shows grayscale cube-shaped item slots around an empty center. Walls stay down; if a family also has fences, they get a separate bottom-left icon. Fence-only families keep down. Scrolling only changes pages. Standard coordinates and item sizes stay fixed when alternates appear. See [INTERFACE.md](INTERFACE.md).

**Wooden trapdoor shapes**, in the Block wheel tab, starts OFF for new and existing players. Enabling it adds bottom-right wooden trapdoors without moving other forms. A trapdoor is worth three planks or six wooden slabs, matching the native crafting ratio in both directions. The server applies the opt-in consistently to the radial and Builder's material operations. With the alternate off, trapdoors are exact items and cannot be converted into or out of their plank family. With it on, manual conversion still works while the Builder master is off; automatic conversion still requires Builder Mode and the corresponding feature. Only registered wooden trapdoors with an unambiguous plank family participate; metal trapdoors are excluded.

The separate deposit icon fills occupied named-shulker templates from main inventory, then the hotbar. Normally exact matches fill first; experimental wood instead prioritizes existing log templates. While Builder Mode is active, compatible ordinary family variants may convert into remaining capacity. It is an explicit action, independently described in [NAMED-SHULKERS.md](NAMED-SHULKERS.md), and does not change automatic pickup or normalization behavior. An already open personal ender chest can use it with Builder Mode disabled; the inventory shortcut requires the saved Ender Chest Access preference and server permission, independently of automatic Builder/Supply mode.

## Pickups

Supported pickups fill existing, ordinary stacks from the same family in this order:

1. The selected hotbar slot, if it contains a matching family member with room.
2. Slots containing the exact picked-up shape.
3. Other matching hotbar slots, from left to right.
4. Main inventory storage, in canonical base form with physical fractional change.

A minimal compatible physical remainder in main storage can complete a hotbar shape too: one incoming slab plus one stored slab can refill one stair. If that preferred plan cannot fit, routing also tries without borrowing storage material.

Empty hotbar slots are never automatically populated by pickup routing. Actual item stack limits are used, including modded limits. Offhand, armor, crafting slots and external containers are outside automatic pickup routing.

Canonical bases come from the existing family catalog. Stone bricks stay stone bricks; oak planks stay planks with experimental wood off. With it on, compatible main-inventory wood uses the conservative compaction policy below. A family with no reliable base does not participate in Builder Mode conversion.

| Incoming material and capacity | Result |
| --- | --- |
| 33 slabs, no matching hotbar template | 16 base blocks and one physical slab in main storage |
| 32 stairs, hotbar slabs with 32 spaces | 32 slabs added to the hotbar, 16 base blocks stored |
| 10 walls, hotbar slabs with one space | One slab added, nine base blocks and one remaining slab stored |
| Same case with an existing compatible one-slab remainder | Combine the two fractional remainders into a base block where it fits |
| Inventory can accommodate only part of the pickup | Accept a safe number of original items; leave the rest on the ground in their original shape |

Pickup delay and ownership checks remain vanilla. Automatic operations never drop converted overflow, preventing pickup/conversion/drop loops. If a whole incoming item cannot be accommodated without losing its fractional material, it stays on the ground.

## Inventory moves

Main storage means inventory slots 9 through 35. The hotbar is slots 0 through 8.

| Move | Normalize |
| --- | --- |
| Hotbar to main storage | Yes, after the inventory action completes |
| Hotbar to hotbar | No |
| Hotbar to cursor | No |
| Hotbar to crafting, offhand or external container | No |
| Main storage to hotbar | No |

Normal clicks, shift-clicks, number-key swaps and completed drag operations use the same server transaction boundary. Items attached to the cursor are left intact. Affected families can be consolidated in main storage to combine odd leftovers. If normalization does not fit, only safe complete batches are applied, or the original variant remains unchanged.

For a direct click, number-key swap or completed drag, normalized base blocks prioritize the actual destination you chose, before other empty slots or merge targets. Completed drags retain each destination's whole-block share. Shift-click chooses storage automatically, so it fills existing matching base stacks before creating a stack in an empty main slot. Existing fractional material can still combine, with physical change stored safely in main inventory.

There is no inventory scan every tick. Direct slot writes by other mods, Creative palette item creation, and operations that bypass vanilla menu transactions are conservatively left alone. A cursor stack whose origin predates enabling Builder Mode is also left untouched until a new traceable transfer occurs.

## Experimental wood storage

**Block wheel → Logs and wood (experimental)** starts OFF and is independent of the Builder master. Its default values are slab 1, plank/stair/fence 2, log/wood 8 and optional trapdoor 6: **one log = one wood = four planks**. Both log and wood already yield four vanilla planks; the native four-log-to-three-wood recipe loses material, so the 1:1 exchange adds no vanilla crafting gain loop.

Logs are the primary storage form; planks are the fallback. One fence becomes one plank, four fences become a log, 25 stairs become 25 planks, and 65 stairs can become sixteen logs plus one plank. The exact-batch/over-one-stack rule gates logs, not ordinary plank normalization. Every candidate must fit without increasing occupied slots. Existing log stacks remain destinations. Explicit custom ratios retain their own exact units and incomplete-batch protection.

Compatible main-storage forms aggregate after completed native menu changes, including main-to-main rearrangements, removals and inbound container transfers. One plank plus three stairs becomes a log without an off/on cycle. Cursor-held items, other hotbar stacks, equipment, customized and stripped blocks stay outside the pool. Plank fallback prefers existing plank stacks or original source positions; explicit destinations and real slot savings retain their normal priority. Pickup replenishes held shapes first and uses the same storage policy.

Stripped logs/wood/stems/hyphae have no family under either setting, including through explicit family files. They cannot be converted, normalized, coerced or used as family resources; exact ordinary handling remains available. Shelves are excluded. Detailed examples, discovery constraints and the different opportunistic template policy are in [WOOD-FAMILIES.md](WOOD-FAMILIES.md).

## Hand restocking

After a successful main-hand placement or item use consumes an item, Blockwheel checks the active stack. Food is checked after consumption completes. The default trigger is fewer than six remaining items, and a refill adds up to thirty-two additional items. Both values are configurable from 1 to 99 in the shared Restock tab. The actual item stack limit and available supplies still cap the result. See [RESTOCK.md](RESTOCK.md).

Loose donors come only from main-storage slots 9 through 35; other loose hotbar stacks remain intact. With Shulker Supply enabled, carried boxes and permitted ender chest boxes extend that supply. Each tier prefers exact-shape items, canonical bases, then other compatible family forms. It consumes only material needed for the refill and any physical change required to complete that transaction. Change is stored as base blocks where possible; unavoidable fractions stay actual family items. A safe smaller refill is allowed when a full refill cannot fit. Automatic operations do not drop change.

With Supply enabled, items outside a reliable conversion family also participate. Netherrack, for example, can refill from exact netherrack stacks in loose storage or enabled shulkers. Non-block consumables such as snowballs and bread use the same source order and named-slot protections. Turning off Non-blocks from shulkers restricts their automatic refills to ordinary main inventory; block items, including netherrack, retain their shulker supplies. These refills compare the complete item and components, keep all metadata intact, and perform no shape conversion. Customized family items can likewise be refilled from identical copies without being converted. Without Supply, the original family-only restock policy remains.

The original item is captured before use, so consuming the last item can still refill the emptied slot. Dropping the held stack, changing slots, moving items around or merely waiting does not trigger restocking. There is no remembered empty-slot template that later repopulates an intentionally empty hotbar slot. Uses that replace the hand with a different item, such as a returned container, retain that vanilla result.

Durability changes are not quantity consumption. Damageable tools retain vanilla or Tweakeroo handling rather than entering the exact stack-refill ownership guard.

Creative placement retains vanilla infinite-material behavior. See [Tweakeroo compatibility](TWEAKEROO.md) for coordinated hand restocking with that mod installed.

## Pick block and tooltips

If the exact item already exists in the hotbar or main inventory, vanilla pick-block behavior is retained. Otherwise survival pick block can convert compatible main-storage and hotbar material into up to one stack of the requested shape. Main-storage donors have priority over compatible hotbar donors. Unlike automatic restocking, this explicit action may transform the selected stack or another hotbar stack. Shulkers are additional sources only while Shulker Supply and Shulker pick block are both enabled; remote access has its own gates.

Items without conversion families, such as netherrack, can also be fetched by exact item-and-component match from those enabled supplies. Pick block additionally permits loose items in your personal ender chest under the same ender-access preference and server permission. Loose ender items come after unnamed carried shulkers and before remote shulkers. Family picks can use compatible loose ender material for conversion too. This pick-only expansion does not add loose ender donors to automatic restocking or material tooltips.

The planner tries vanilla's suitable hotbar slot first and stores any surviving displaced item in main storage. If that is impossible, it can use a compatible hotbar source slot as the output destination. This fixes selected-slot and other-hotbar-only sources even with all ordinary inventory slots occupied. Withdrawals happen before replacement, so a consumed source is never accidentally restored as a displaced stack. If no safe plan fits, everything stays unchanged. Creative pick block remains vanilla.

Optional tooltips total compatible ordinary material across main storage and the hotbar. For example, 33 slab units display **Available material: 16 1/2 blocks** and **Equivalent slabs: 33**. Fractions are exact, including for data-defined material ratios. When Shulker material tooltips are enabled with Supply, server-calculated counts add eligible carried and remote shulkers, split sources into separate lines, and show protected template value separately from available supply. Whole shape equivalents round down, so one available slab is zero whole stairs. These are material totals; destination capacity and the stricter restock donor policy still govern actual operations. Offhand, armor and unsupported containers are excluded.

While the current family's counts refresh, the tooltip keeps the last complete server response. Inventory changes mark those counts stale for a new query without replacing them with loading text. Settings, catalog and connection changes clear the old response. The loading line is reserved for a first result; transactions always use fresh server inventories regardless of what the tooltip is displaying. Material conversion tooltips require a conversion family; exact-only restocking and pick block do not invent one.

## Extensibility and protected items

Builder Mode uses the same live registry, tags, recipes, shape definitions and explicit family overrides as the radial. Adding a conventionally named material requires no Builder-specific code. A new shape needs only the existing data definition, including its integer material value. See [EXTENDING.md](EXTENDING.md).

Named items and stacks with custom component changes are excluded from material conversion. Exact-only restocking can move identical component-bearing items without converting them. Unknown or ambiguous bases remain outside conversion and can still use that exact restock path while Supply is enabled. No assumption about a particular stone or wood family is embedded in the routing engine.

Optional [pick fallback rules](PICK-RULES.md) try ordered additional block targets only after the original request cannot be obtained. Their JSON and visual editor are instance-wide; normal Builder/Pick/Supply permissions still apply.
