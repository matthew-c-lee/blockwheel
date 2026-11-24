# Shulker Supply

Shulker Supply extends Builder Mode's existing restocker and pick block. It withdraws physical items and puts the requested supply in your hand. Family shapes use the same live catalog and material units as the radial; items without a family are restocked or picked by exact item-and-component match with no transformation. You do not need to place or open either the shulker or an ender chest.

## Enable it

1. Install Blockwheel 1.0.1 on both client and server. The dependency minimums remain Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 and Java 25.
2. Open your inventory and left-click the Builder icon to enable **Builder Mode**.
3. Right-click the Builder switch, select **Supply**, and enable **Shulker Supply**.
4. Choose whether to enable remote ender chest access, **Pick from stored supplies**, **Show stored material totals** and **Non-blocks from shulkers**.

The supply master defaults OFF, including after upgrading an existing world. The three suboptions default ON, ready for opt-in, and are saved independently. Original Builder feature preferences survive migration. All player preferences belong to the current world's player data and survive respawn, world restart and reconnect.

| Setting | Required gates and effect |
| --- | --- |
| Shulker Supply | Builder Mode must be ON; enables carried-box hand supply, including exact refills for items without families |
| Ender chest access | Requires Supply and server permission; adds remote shulkers, plus loose ender items for pick block |
| Pick from stored supplies | Requires Supply and the Pick block feature; adds exact or convertible supply to explicit pick block |
| Show stored material totals | Requires Supply and the original Material tooltips feature; adds server-calculated supply breakdowns |

Turning off a parent feature stops its actions while keeping the saved suboptions. The ender chest control displays **Server disabled** when administrator policy forbids access.

## Radial leftovers

With Builder Mode, Shulker Supply and permitted Ender chest access enabled, a manual radial transaction returns leftover physical material to identical occupied slots inside named shulkers in your ender chest before using main inventory. For example, 64 stone made into 64 held slabs leave 32 stone; a ten-stone template becomes 42 stone. Multiple templates may accept the change up to their actual capacities. Anything unaccepted remains physical and follows the usual main-inventory/drop path. Empty slots, unnamed boxes, carried boxes and loose ender items are not destinations. Existing unrelated inventory is not swept, and no shape conversion is done while routing this change.

The radial's conversion donors remain loose main-inventory family items. If that plan cannot produce even one target, Builder Mode and Shulker Supply permit a separate exact-item retrieval from carried and permitted ender shulkers. Two held planks can retrieve existing logs, preserving both planks in main storage outside the hotbar. This fallback follows the supply tiers and named reserves, never transforms box contents, never uses loose other-hotbar/ender items, and aborts atomically if the displaced stack cannot fit main storage. Successful conversions do not use this fallback or top up from boxes.

## Which materials are used first

| Priority | Source |
| --- | --- |
| 1 | Loose materials in main-storage slots 9–35 |
| Pick block only, before boxes | Loose compatible materials in hotbar slots 0–8 |
| 2 | Unnamed carried shulkers |
| Pick block only, before remote boxes | Loose items in your personal ender chest |
| 3 | Unnamed shulkers in your personal ender chest |
| 4 | Named carried shulkers |
| 5 | Named shulkers in your personal ender chest |

Within each tier, exact requested shapes come first, then canonical base blocks, then other compatible forms. Ties use stable outer and inner slot order. Sources can combine: a refill needing 63 stairs can consume 20 loose blocks, 30 blocks from an unnamed carried box and 13 available blocks from a named box. An unnamed remote box takes priority over a named carried box.

Carried shulkers may occupy any ordinary inventory or hotbar slot. Automatic restocking never spends other loose hotbar building stacks. Explicit pick block may use those stacks because the player requested a new shape. Neither operation uses offhand or armor sources. Restocking and material tooltips use only shulkers inside the ender chest. Explicit pick block can additionally withdraw loose ender chest items, under the same remote policy.

Priorities yield to material conservation, named reserves and capacity. If preferred material would require physical change that cannot fit, an exact combination from lower-priority sources may be used. A smaller safe output is also possible. If no complete physical result fits, all original items remain.

## Named boxes preserve the layout

A box is protected when its outer item has the native `CUSTOM_NAME` component. Display text and translated default names are not used to guess protection.

Every occupied source slot retains at least one item. Two slots holding the same block each keep their own item. A named box with stacks of 64, 32 and 1 can contribute at most 63, 31 and 0 from those particular slots. Unrelated items and empty slots remain untouched. Unnamed boxes can empty completely.

Only counts in eligible source slots change. Automatic supply never moves, consolidates, sorts, normalizes, deposits or refills items inside a box. The separate explicit [deposit button](NAMED-SHULKERS.md) can grow existing named template stacks when you request it. The outer box is copied and only its native container component is replaced; its name, color and other components remain intact. Reserve protection also applies inside the ender chest and cannot be disabled.

Ratios come from the existing data definitions. With ordinary Minecraft families, a full block, stair, wall or fence is two units and a slab is one. Three slabs in a named slot can provide two slabs for one stair, leaving one protected. Two slabs in a named slot provide only one available slab: that can combine with another eligible unit, but cannot create a stair by itself. Fractions stay physical items; no invisible balances are stored.

## Remote ender chest policy

The server reads the player's actual `PlayerEnderChestContainer`. It stages changes to stored shulker items and, for explicit pick block, eligible loose ender items. The ordinary ender chest inventory persists those updated stacks. The client never edits remote storage and does not need to open the container.

At startup, Blockwheel creates `config/blockwheel-server.json` if absent:

```json
{
  "allow_remote_ender_chest_supply": true
}
```

Set the value to `false` to forbid remote supply and its tooltip counts for everyone on that server. Run a successful administrator `/reload`, or restart, to apply an edit. The policy applies to both restocking and pick block, independently of player settings. An invalid file or non-boolean field disables remote access and logs the error without rewriting the invalid file. The default permits opt-in; it does not enable a player's Shulker Supply switch.

In singleplayer, the integrated server uses the instance's same config directory. Multiplayer requires the mod on the server as well as the client. Access is to that player's ender chest only, with no shared or arbitrary remote inventories. A ready-to-copy [example config](../examples/blockwheel-server.json) is included.

## Pick block and restocking

Restocking follows confirmed main-hand placement, successful item use or completed food consumption. It defaults to triggering below six remaining items and adding up to thirty-two additional items; both numbers are configurable in Restock. Actual stack capacity and available supplies still cap each refill. Netherrack and other items without transformations use exact matching across the same loose/carried/remote tiers; snowballs and food can also refill. The Non-blocks from shulkers switch can restrict non-block automatic refills to ordinary main inventory while retaining block shulker supplies and explicit pick block. Different components never merge, and exact refills do not change the items being withdrawn. Uses that return a different item in hand retain vanilla's result. An intentional drop, inventory move or selection change does not create a future refill template. Restocking exhaustion is silent; no insufficient-material action-bar message is shown. See [hand restock preferences](RESTOCK.md).

Exact requested items already in ordinary inventory retain vanilla pick behavior, and Creative pick remains vanilla. For a missing survival item, pick block uses main storage, compatible hotbar stacks and enabled supplies, aiming for up to one target stack. Family items may convert; items without a family require an exact item-and-component match and move unchanged. With remote access enabled, pick block considers loose ender items as well as ender shulkers. It first tries vanilla's suitable hotbar slot, then compatible family source slots if displaced items cannot fit. The source stack may itself become the target, including when it is selected and the whole inventory is full. Remaining source material and unrelated displaced items are preserved; unsafe plans do nothing. Displaced items are stored in main inventory, with no automatic deposits into the ender chest or shulkers.

Manual hold-R conversion keeps its existing main-inventory donor rules; only the failed-batch exact retrieval described above withdraws from enabled boxes. Pickup routing and ordinary inventory normalization do not deposit into, sort or normalize boxes.

## Material tooltips

With shulker tooltips enabled, hovering a supported building item requests an authoritative server count. It shows available base-block value, slab equivalents, inventory plus hotbar, carried shulkers, ender chest shulkers and protected template value. Protected value is displayed separately and never added to available material. Whole shape equivalents use complete target pieces: one spare slab is half a block and zero whole stairs.

These totals describe eligible material, not a guarantee that every equivalent fits a particular destination. Automatic restocking excludes loose other-hotbar stacks even though explicit pick and the informational inventory total can include them. Actual operations always recalculate their own sources and capacity.

Counts are requested on demand, rate-limited and briefly cached. Inventory synchronization, predicted inventory actions and ender chest changes mark counts stale; an expiry refresh catches other changes while hovering. The last response for the same family stays visible while replacement counts are pending, so periodic refresh does not flicker or change tooltip height. Settings changes, data reloads and disconnects clear old counts. A short checking message can appear before a first response for a family. There are no ender chest or shulker scans every tick, and transactions never use cached tooltip counts. The conversion tooltip itself remains for supported families; exact-only supplies do not create artificial conversion families.

## Supported containers and limits

- Standard unstacked 27-slot shulker items with native container components are supported, including all vanilla colors and compatible subclasses.
- Locked, loot-sealed, stacked or expanded containers are skipped. Arbitrary storage items and nested containers are not searched recursively.
- Customized building stacks are excluded from conversion. Exact restocking can move component-identical copies unchanged; different or unsupported contents remain as-is, and custom outer-box metadata is retained.
- Withdrawals are atomic server-thread plans with source revalidation. All required physical change must fit main storage; automatic supply never drops converted overflow.
- Tweakeroo 0.29.5 remains optional. Its existing isolated hand-restock guard still coordinates with the server restocker; no third-party configuration is changed.

The source model, transaction stages and exact-withdrawal fallback are documented in [ARCHITECTURE.md](ARCHITECTURE.md). Actual test results and the multiplayer test boundary are recorded in [VERIFICATION.md](VERIFICATION.md).

## Optional wood relationships

Experimental wood, disabled by default under Block wheel, lets verified unstripped logs, wood and plank shapes supply each other through this same acquisition engine. Source priorities, access gates and named one-item reserves are unchanged. Exact integer ratios conserve material; stripped logs/wood/stems/hyphae have no conversion family under either setting and can only move as exact items. See [WOOD-FAMILIES.md](WOOD-FAMILIES.md). Explicit named-template refilling has its own opportunistic packing policy; it does not add automatic deposits to Supply.
