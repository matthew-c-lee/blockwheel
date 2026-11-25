# Blockwheel architecture

## Discovery and authoritative views

`FamilyData` builds the immutable ordinary `FamilyCatalog` from server registries, shape definitions, recipes and explicit overrides. `FamilySafety` excludes native/conventionally named stripped variants, anvils and shelves, and rejects mixed copper oxidation/waxing families. `BlockMaterials` reapplies this safety projection so programmatically constructed catalogs cannot bypass it. Customized stacks never gain material equivalence. Optional shape `base_tags` scope inference to valid base categories without hardcoded wood species. The ordinary sign definition uses `standing_signs` and requires a `planks` base; its shaped recipe identifies the species and its four-unit value matches two planks. Hanging signs are not members.

`WoodFamilyData` reads `blockwheel/wood_families` resources. Same-namespace log/stem candidates need unambiguous recipes proving plank and wood relationships; explicit definitions can assert irregular valid names. Unsupported or unsafe candidates are omitted. Experimental-data failures preserve ordinary families. `WoodFamilies` extends an existing plank family immutably using integer gcd/lcm scaling: default slab 1, plank/stair/fence 2, sign 4, log/wood 8, and separately enabled trapdoor 6. The log becomes the storage base while planks keep their radial shape.

`FamilyViews` caches four trapdoor/wood combinations. Saved per-player opt-ins select the same catalog, material projection and planner for every operation. The Builder master separately authorizes automatic actions. Manual conversions remain available with it off. Catalog reloads and material-option changes invalidate wheel sessions; the server sends the allowed view rather than accepting a client exchange rate.

## Shared integer mathematics

| Component | Responsibility |
| --- | --- |
| `ConversionPlan` | Exact whole source/target batches |
| `ManualConversionPlan` | One-stack radial caps, exact family consolidation withdrawals and physical change |
| `MaterialComposition` | Exact physical decomposition with primary and optional secondary storage forms |
| `MaterialWithdrawal` | Bounded exact-sum withdrawal from reserve-subtracted quantities |
| `InventoryTransaction` | Capacity routing, complete snapshots, nested staging and commit |
| `MaterialSupply` | Shared source traversal, ordering, access and named-slot reserves |

These existing engines serve distinct policies. There is no separate wood arithmetic in radial, restock or shulker handlers. Units are bounded integers; stack eligibility uses exact rational comparisons. No fractional inventory balance is stored. Experimental wood compositions prefer logs, then planks, before other necessary physical forms. This shared secondary preference applies to manual change and server material routing as well as normalization; adding a sign shape cannot replace three remainder planks with a sign and a plank. Existing three-argument composition callers retain their original policy.

Manual conversion first applies held-source batching and its existing one-stack cap. With Builder Mode off, only the minimal matching source copies may finish its last batch; target merging and other family withdrawals are disabled. With Builder Mode on it then uses `MaterialWithdrawal` to plan the largest exact additional output that fits the hand. The pool contains the held physical remainder and ordinary same-family main-storage items. Exact target stacks have priority, then the held remainder, then other forms in stable inventory order. `ManualConversionPlan` carries immutable form/count withdrawals as well as source batching and physical change; legacy constructors retain their routing contract. `BlockMaterials.mainSupply` exposes the same eligible loose sources to server planning and client narration/release validation. No exchange rates or amounts come from the client.

`InventoryConversion` validates and stages every main withdrawal before replacing the selected hand slot. The authoritative player path opens the existing inventory transaction with an ender snapshot only when the current Builder/Supply/Ender/server gates allow it. `TemplateDeposit.fillRemainder` reuses the explicit refill action's named-template destination discovery and `Box.fillExisting`, routing only conversion change into identical occupied remote templates. It never sweeps inventory, converts that change or creates template slots. Unaccepted change uses main storage, then the existing manual drop path after commit. Hand output, donors, remote container components and remaining physical items share one validation/commit. No additional target stacks are produced. Result counts include the accepted main-source consolidation.

Restock and family-aware pick request the active building shape. Shared acquisition withdraws permitted material, creates the requested form and stores exact physical change. Bounded exact withdrawal avoids overpayment when change cannot fit. A smaller fully validated output may succeed. Explicit pick permits hotbar material, including the selected source; automatic restock excludes other loose hotbar stacks.

Restock accepts only the selected hotbar slot or `Inventory.SLOT_OFFHAND`. Placement/use hooks capture the actual hand slot and pre-use stack; completed consumption captures its hand before vanilla resets the use state. Both destinations share quantity, supply and capacity policies. `MaterialSupply.totalUnits` includes the offhand for conservation while source traversal continues to exclude it. The optional Tweakeroo guard owns supported items in either hand.

If the manual plan produces zero, `ConversionController` may use `BuilderInventory.retrieveExact` under the Builder/Supply/Ender/server gates. It reuses exact acquisition and pick's hand-displacement finalizer: withdraw existing targets up to the radial cap, store the original held stack in slots 9–35, then commit both changes atomically. No box-family conversion, loose other-hotbar/ender withdrawal, remote deposit or overflow drop occurs. `RadialScreen` submits zero-preview selections when Supply is enabled because remote contents are server-owned. Ordinary successful conversions retain their donor policy.

## Conservative main storage

`StorageCompaction` permits an aggregate main-storage pool when its value is an exact number of logs, or its quantities exceed one actual stack. The latter adds exact source-count/stack-limit fractions rather than assuming 64. Default wood is 1:1 with logs; slabs need eight and plank-equivalent shapes need four. This gate applies only to log compaction; otherwise the original plank form is the secondary storage candidate, still subject to capacity and occupied-slot checks. Explicit custom ratios retain their own batch sizes.

`BuilderInventory` stages log-first composition, compatible alternative storage forms and ordinary merging. Actual occupied main slots determine the winner. More slots are rejected; fewer slots outrank logs; logs win ties between storage forms. Direct destinations break ties without overriding slot savings. Pools exclude other hotbar slots, equipment, customized/stripped blocks and other species. Ordinary-family normalization retains its old behavior with experimental wood off.

Pickup first replenishes eligible held shapes. Whole remaining input is stored unchanged, then considered for conservative compaction. Virtual source staging can fill existing log capacity without needing a temporary slot. Already split input requires exact physical change before acceptance. Automatic plans never drop converted overflow. Planning is bounded and family-local, not a global inventory sorter.

## Sources and atomic commit

`MaterialSupply.Source` describes a loose or supported shulker inner slot, tier, form value and reserve. Matching selects family material or component-identical exact items. Exact handling shares traversal and permissions without inventing families for netherrack, food or stripped blocks. Source ordering and player/server gates remain as documented in [SHULKER-SUPPLY.md](SHULKER-SUPPLY.md); loose ender material is a pick-only expansion.

`InventoryTransaction` snapshots the selected slot, complete inventory and included ender chest. Lazy `Box` shadows copy 27 inner slots and reserve one item per occupied named withdrawal slot. Unsupported locked, loot-sealed, expanded or stacked boxes remain excluded; contents are not recursively searched. Nested edits flush before an outer box moves, preventing a stale copy restoring spent contents.

Insertion uses real item/container limits and explicit slot ranges. Commit revalidates relevant source and destination snapshots on the server thread, then applies staged writes, rebuilds immutable container contents, retains outer metadata and marks storage dirty. Conflicts abort the plan. Conservation counts physical material including protected reserves and moved boxes.

## Dump gestures

`PressHoldAction` is a shared client state machine for the gameplay key and deposit widget. It emits a short action on release before 600 ms, or a bulk action once at/after the threshold. The fired state remains armed until release, so repeated key events and the final release cannot dispatch again. Cancelling emits nothing. The key drains repeated clicks, respects gameplay/focus context and resets on disconnect. The widget uses native mouse/key events, a screen-level release fallback, screen-generation guards and cancellation on removal/visibility/focus changes. A thin native-GUI progress strip displays the hold time without changing button geometry. A quick release and completed hold produce the same existing success sound after the server accepts material.

## Explicit template packing

`TemplateDeposit` is the existing manual button/key action. Named destinations accept additions only into originally occupied matching slots. Empty slots, unnamed destinations, equipment and cursor sources remain excluded. Short gestures use loose main inventory only; completed holds add hotbar sources after it; another shulker's contents are never deposit inputs.

The authoritative `FillTemplates.includeHotbar` flag selects the same source scope for the exact loop and both ordinary/experimental conversion planners. No default coercion path can borrow hotbar material. Normally exact templates fill first, then compatible conversion with Builder Mode active. Experimental wood gives existing log templates priority across boxes. `BuilderInventory.acquireBatches` uses the shared exact-withdrawal solver, bounded by accepted template capacity. Only complete accepted batches are spent; original unaccepted items remain intact. Secondary exact/compatible templates can accept leftovers without creating new templates.

Experimental filling supports carried named destinations before eligible ender destinations. Carried-only access grants no remote permission. Ender writes still require the native personal ender menu open or the existing player/server remote policy. With experimental wood off, destinations remain ender-only. Builder Mode off permits exact deposits only. Withdrawal reserves remain independent of deposit planning.

`FillTemplates` sends only a menu ID. The server validates menu identity, ownership, empty cursor, permissions and fresh contents. Template types, positions, components and real limits remain unchanged. The shared transaction prevents partial spending on conflicts.

## Pick selection and state safety

`RegistryPickDefaults` discovers log counterparts and native copper weathering/waxing graph roots. `StatePickDefaults` generates deterministic anvil/copper lists; `PickDefaults` groups them with log defaults. Names alone do not establish copper subtype compatibility.

The native reach-checked pick supplies the target. Exact loose-item and Creative behavior remain vanilla, then requested-item acquisition runs before ordered alternatives. A fallback may use only its own permitted family and never grants conversion to the original request. Copper families preserve wax/age; anvils have no transformation family.

`PickRules` stores independent group flags and bounded one-hop custom overrides, including empty suppression. `PickRulesClient` atomically persists version-1 JSON, preserves malformed files and retains the last valid state on failed reload. Instance rules synchronize to per-player server memory on join/save/reload and clear on disconnect. IDs, lists and bytes are bounded. The existing picture/search/priority editor uses this same model.

## Events, settings and synchronization

| Native boundary | Purpose |
| --- | --- |
| Item pickup after vanilla delay/owner checks | Pickup routing |
| Completed menu click/removal | Proven hotbar-to-main normalization and cursor return |
| Successful block/item use in either hand | Quantity restock into that hand, including last-item consumption |
| Completed eating | Delayed consumable restock |
| Server pick item | Reach-checked requested block and exact vanilla behavior |

`BuilderController` authorizes actions and prevents reentrancy. No every-tick inventory scan is added. Legacy non-wood provenance excludes ambiguous cursor origins. Expanded wood families are re-evaluated for changed main slots at the same completed menu boundaries, including inbound transfers and removals; cursor and external-container contents are not conversion sources. Unrelated direct writes can still bypass those boundaries. Intentional drops, moves, durability changes and returned replacement items do not trigger restock.

The persistent player attachment preserves feature/supply bits and nested `RestockSettings`, with optional `experimental_wood:false`. Restock remains below six/add thirty-two by default. Expanded Builder update/status payloads are v4 and pick preferences v2; both sides should install this release. A local settings draft composes edits while confirmation is in flight, but operations read only server-confirmed state.

`InterfacePreferencesClient` saves four local flags: hide tooltips, hide Builder text, show Builder button and show refill controls. Missing visibility fields default true. No presentation flags go to the server. `InventoryControls` physically removes hidden widgets and clears focus; a screen-generation guard prevents obsolete listeners restoring stale buttons. Visibility does not change gameplay or template protection.

The unbound settings key and optional compile-only Mod Menu entry point open the same six tabs, starting with Block wheel. Builder owns the master switch, Restock owns quantities, Supply owns source choices, and Pick block owns enhanced retrieval and fallback rules. Server-owned writes are disabled outside a compatible world; local pick/interface settings remain accessible. The opt-in Radial Only preset disables the master and hides optional controls while preserving individual choices.

## Radial and feedback

`RadialPages` reserves block up, stairs right, slab left, wall/fence down, alternate fence bottom-left, trapdoor bottom-right, log top-left and wood top-right. Missing forms remain gaps; extras paginate. `RadialLayout` retains nearest-icon selection, neutral center and fixed geometry. Visible cursor and hold/release input remain unchanged.

`RadialSlotRenderState` samples current native hotbar sprites through GUI polygon meshes. Borders remain two normal/three selected GUI pixels plus a moving one-pixel outer shell. Floor texels repeat at native scale with transparency. No copied Minecraft assets are bundled. The crosshair alone is hidden; HUD and world brightness remain unchanged.

Server acknowledgements trigger enchanting/grindstone Builder sounds with optional ON/OFF text, or the ender-eye fill sound without text. Buttons and keys share these actions. Failed restocks remain silent. Local text preferences retain sound and narration.

The wood resource separates conversion `log_input` / `wood_output` from optional `recipe_log_input` / `recipe_wood_output` evidence. Defaults use 1:1 exchange while proving the native 4:3 recipe and equal plank yields. Old files default evidence counts to their existing conversion counts. Proof rejects a recipe with greater output material value than its input. `BlockMaterials.Family.plankBase()` retains the original full-block role as secondary storage; generic integer composition and transactions supply its normalization.
