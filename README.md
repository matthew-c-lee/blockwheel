# Blockwheel

**1.0.1 — hotfix.**

A Fabric mod for **Minecraft Java Edition 26.2**. Hold **R** while holding a building block, point the mouse near another shape in its family, then release **R** to convert the stack. The floating cube-shaped slots combine Minecraft's gray hotbar styling with the supplied radial reference's geometry.

<img width="550" height="309" alt="wheel demonstration" src="https://github.com/user-attachments/assets/8e9a6eaf-b487-4091-b435-3812c669987b" />

## Install

1. Install Fabric Loader **0.19.5 or newer** for Minecraft **26.2**, with **Java 25**.
2. Put `blockwheel-1.0.1+mc26.2.jar` and **Fabric API 0.160.0+26.2 or newer for 26.2** in your instance's `mods` folder.
3. For multiplayer, install both mods on the server as well. Singleplayer includes its own server automatically.

Use a Fabric API build intended for **26.2**. This release does not target Bedrock, Forge, NeoForge, 26.1 or 26.3.

## Builder Mode

Use the small frameless switch inside the inventory's top-right corner, above the crafting result. **Left-click** toggles Builder Mode; **right-click** opens settings. Off has its thumb on the left; on moves it right and turns the track green. There is no surrounding button box. The settings page uses the same switch design with an explicit ON/OFF label. Hover for its state and controls. New players start with Builder Mode off; preferences are saved per player in that world's player data. Existing feature choices survive upgrades. Wood trapdoors and experimental log-based families are independent, off-by-default options.

Settings open on **Block wheel**, with the title **Blockwheel Settings**. The Builder Mode master switch is on **Builder**; the other pages show its status where it matters. **Restock** controls when and how much to refill, **Supply** chooses item sources (including the non-block shulker toggle), and **Pick block** groups enhanced picking with fallback rules. Each setting has help describing its effect.

With optional **Mod Menu**, select **Blockwheel → Configure** to open the same settings, including **Block wheel**, **Builder**, **Restock**, **Supply**, **Pick block** and **Interface** tabs. Join a world running Blockwheel to change server-owned preferences. Pick fallback rules and interface preferences are stored for the instance and can also be edited from the title screen. Mod Menu is not required or bundled.

An acknowledged Builder toggle plays the enchanting-table sound when enabled and the grindstone sound when disabled, with **Builder Mode: ON/OFF** above the hotbar. Joining a world or changing unrelated preferences does not replay it. A successful named-shulker deposit plays the ender-eye insertion sound without a message. Both buttons and keybinds share this feedback. A short dump press acts on release and uses main inventory only; a 0.6-second hold includes the hotbar and fires once at the threshold. Releasing a completed hold does nothing. The deposit button shows a filling progress strip while held.

The **Interface** tab can completely hide either inventory control with **Show Builder Mode Button** and **Show Shulker Refill Controls**, both ON by default. Hidden controls are removed, including their click targets. Visibility does not change gameplay settings. Bind **Open Blockwheel Settings** under Controls to keep settings accessible without Mod Menu or visible inventory buttons. The **Block wheel** page has an opt-in **Use only the block wheel** action. It turns Builder Mode off and hides both controls while preserving individual feature choices and manual-radial options.

The Interface tab also has separate **Hide button tooltips** and **Hide Builder status text** toggles, both **OFF by default**. The first hides hover help on the inventory Builder switch and named-shulker fill control; item tooltips, settings help and keyboard narration stay available. The second hides only Builder Mode's ON/OFF overlay, retaining its sounds. These local preferences are saved in `config/blockwheel-client.json`.

Builder Mode fills the shapes already in your hotbar, stores surplus pickups as canonical base blocks, normalizes variants moved into main storage, restocks the shape you are placing from available material, and extends pick block to compatible materials. Explicit pick block can also transform compatible hotbar stacks, including the selected stack when the inventory is full. Automatic actions preserve physical fractional items and accept only output that fits. The manual radial remains available in either mode.

Direct placement into main storage keeps converted blocks in the slot you choose. Shift-click instead merges converted base blocks into existing matching storage stacks before using empty slots. Stack limits and fractional leftovers still apply.

<img width="550" height="309" alt="builder mode compaction" src="https://github.com/user-attachments/assets/b63f7fdb-dca6-4364-b4db-db6b6c8dcd47" />

**Shulker Supply starts OFF**, including for existing players. Enable it in the settings to restock and pick from carried shulkers and, optionally, shulkers in your personal ender chest without opening either container. Unnamed boxes may empty completely; named boxes always retain one item in each occupied source slot, including duplicate item types. Their layout, color, name and other components are preserved. There are no automatic deposits.

Supply also restocks items without a conversion family, including netherrack and consumables. Those items are matched exactly, including components, and moved unchanged. Family building materials retain their existing shape conversions. Refills follow successful use, placement or completed eating in **either hand**, including consumption of the last item. The refill returns to the hand that used it; intentional drops and inventory moves remain deliberate.

<img width="550" height="309" alt="shulker supply example" src="https://github.com/user-attachments/assets/96b81ff1-3e3c-4089-a375-38a7d99dda3d" />

Hand restocking defaults to **fewer than 6 items remaining**, adding **up to 32 additional items** per refill. Both values are configurable from 1 to 99 in **Restock**; actual stack capacity and available supplies still limit the result. Five held blocks therefore become 37, rather than being topped up to 64. The **Non-blocks from shulkers** switch can exclude pearls, rockets and food from carried/ender shulker refills while retaining loose-inventory refills and building-block supplies. It starts on, preserving the existing behavior when Shulker Supply is enabled. See [hand restock settings](docs/RESTOCK.md).

Blockwheel performs its own server-side restocking and does not read Tweakeroo's item filters, threshold or quantity. Tweakeroo is optional; its compatibility guard prevents competing restocks while Builder Mode owns the affected main hand or offhand.

Pick block also fetches non-transformable items by exact match. Existing hotbar and main-inventory matches keep vanilla behavior; enabled supplies add carried shulkers, loose personal ender chest items and shulkers inside that ender chest. Remote access uses the existing player and server gates, and named boxes retain their protected slots.

Optional **smart pick fallbacks** try the requested block first, then additional blocks in your chosen order. Independent default groups cover logs/wood, anvil damage states and compatible copper oxidation/waxing states. Log defaults include same-species stripped requests, nether stems/hyphae and stripped bamboo. For example, missing stripped wood tries a stripped log, then ordinary wood, then an ordinary log. It hands you the selected alternative rather than stripping it. Use **Pick block → Edit fallback rules** to choose targets and alternatives from a searchable picture grid, reorder them, remove them or restore defaults. Rules are backed by readable [JSON configuration](docs/PICK-RULES.md) in `config/blockwheel-pick-rules.json`. Anvils prefer nearest damage, with more damaged winning ties. Copper tries opposite wax at the same age, then nearest age with the same wax, then nearest age with opposite wax; less oxidation wins ties. Fallbacks deliver the actual substitute, never repair anvils or change copper finish. Exact requested-state acquisition always comes first. See [state priorities and safeguards](docs/STATE-PICK.md).

Material tooltips retain their last received breakdown during refresh, then update when the new server counts arrive. The checking message appears only before a first result for the current family, rather than on each refresh.

Remote access is performed by the server and can be disabled in `config/blockwheel-server.json`, independently of player preferences. The same release must be installed on both sides in multiplayer.

See the [Builder Mode guide](docs/BUILDER-MODE.md), [Shulker Supply guide and server configuration](docs/SHULKER-SUPPLY.md), [architecture](docs/ARCHITECTURE.md), [Tweakeroo compatibility](docs/TWEAKEROO.md) and [release verification](docs/VERIFICATION.md).

## Fill named ender shulkers

The small deposit icon beside an open ender chest fills **existing matching stacks in named shulkers** from main inventory on a short release. Hold for **0.6 seconds** to also include hotbar items. It ignores empty shulker slots and unnamed boxes. Normally exact matches fill first; with Builder Mode on, compatible family forms can also convert into existing templates. Experimental wood gives existing log templates priority across eligible boxes, accepting only complete lossless batches and leaving original remainders safe. Builder Mode off keeps exact matching. Stack limits, physical change and box metadata/layout are preserved, and excess input remains in inventory. Equipment and cursor-held items are excluded.

The fill button sits to the right of the ender chest/panel; an inventory shortcut follows exposed native ender slots and otherwise sits beside the main panel. That remote shortcut requires Ender Chest Access and server permission. The button on an already open personal ender chest works independently of Builder Mode and remote access. With experimental wood enabled, this same explicit action can also fill carried named shulkers without granting remote access; carried destinations precede permitted ender destinations. Nothing is deposited automatically. See [named-shulker deposits](docs/NAMED-SHULKERS.md) and [interface details](docs/INTERFACE.md).

## Controls and behavior

| Input | Action |
| --- | --- |
| Hold R | Open the wheel for the stack in your main hand |
| Point near an option | Select the closest icon; the cursor stays visible |
| Release R after selecting another shape | Convert the held stack in complete batches, then close |
| Release R in the neutral center, or with the current shape selected | Close without changing anything |
| Escape | Cancel while still holding R |
| Scroll / Page Up / Page Down | Change page if a pack supplies additional pages; never switch wall/fence variants |
| Open Blockwheel Settings key, unbound by default | Open settings even with inventory controls hidden |
| Toggle Builder Mode key, unbound by default | Enable or disable Builder Mode |
| Fill named ender shulkers key, unbound by default | Run the same manual deposit as the fill button, under the existing ender-access policy |

R is rebindable under **Options → Controls → Key Binds → Blockwheel**. The hold-and-release gesture follows the rebound key. Clicking, Enter and number keys do not confirm conversions. A quick tap without a deliberate movement does nothing.

The settings and two gameplay keybinds start **Unbound** in that same category. Each activates once per press; holding a key does not repeat it, and typing or navigating a menu does not run either action. Minecraft saves the bindings with its normal controls settings. The dump key uses exact matching with Builder Mode off and the existing family conversion when it is on.

Selection follows the cursor's current position: the closest icon is highlighted even when the pointer is between icons, inside the ring or beyond it. Moving right while the cursor is still on the left keeps the left option selected; cross toward the right option to switch. The center has an 8-GUI-pixel neutral radius, so opening and releasing without moving does nothing, and returning to the middle clears the selection. The camera stays still until the wheel closes, then normal mouse-look resumes.

Each newly highlighted option plays a wooden pressure-plate click at 0.5 volume through Minecraft's UI sound channel. Staying within the same option's region, returning to the neutral center and waiting without moving produce no additional clicks.

The compact wheel uses GUI pixels, so it follows the same GUI scale as the hotbar. Item icons are up to 24 GUI pixels, compared with the hotbar's 16; the standard four-option wheel is about one third smaller than the maximum size before 1.3. Adding alternates keeps the existing icon coordinates, radius and sizes fixed.

Only forms that actually exist are shown. Wood usually has planks, stairs, slabs and a fence; stone families often have a wall instead of a fence. When both exist, such as nether brick, walls remain down and fences get a separate bottom-left icon. A fence-only family retains its usual down position. Scrolling does not change the selected form.

**Block wheel → Wooden trapdoor shapes** starts **OFF**, including for existing saves. When enabled, wooden trapdoors appear at **bottom-right** in their plank family. One trapdoor has the value of **three planks** (six wooden slabs), matching the vanilla six-planks-for-two-trapdoors recipe. Conversion works in both directions, uses complete batches and keeps physical remainders. Metal trapdoors are excluded. With Builder Mode enabled, the same opt-in and ratio also apply to pickup, normalization, restocking, pick and template coercion. When the alternate is off, trapdoors remain exact items. The radial itself still works while Builder Mode is off.

The four standard directions are fixed, including for two- and three-option families. Missing forms leave gaps instead of moving other options. Additional data-defined forms use diagonal positions, then further pages, while the standard forms retain their directions on the first page.

| Direction | Shape |
| --- | --- |
| Up | Full block / base material |
| Right | Stairs |
| Down | Wall; fence if the family has no wall |
| Left | Slab |
| Bottom-left | Wooden sign; fence when a wall is also available |
| Bottom-right | Wooden trapdoor, when enabled |
| Top-left | Unstripped log / stem, with experimental wood enabled |
| Top-right | Unstripped wood / hyphae, with experimental wood enabled |

The wheel leaves the world and HUD at their normal brightness, with no full-screen dimming. Its cube-derived slots use straight polygon edges, rather than rows snapped to GUI pixels. Border bands are two GUI pixels wide normally and three on selection, with a one-pixel dark outer shell that moves with them. Their colors, bevel shading, texture and transparency come directly from Minecraft's current hotbar and hotbar-selection sprites, including resource-pack replacements. The interior repeats the actual translucent hotbar floor at its native GUI-pixel texture scale, without stretching it to fill the hexagon. Item positions and sizes remain fixed. There is no current-shape marker, central panel, crosshair, text, conversion information or page counter in the radial. The normal hotbar and remaining HUD stay visible, respecting the existing HUD setting. Selecting the held shape still cancels. This mod reshapes inventory items; it does not edit placed blocks.

| Conversion | Result |
| --- | --- |
| 1 full block / stair / wall / fence → slab | 2 slabs |
| 2 slabs → full block / stair / wall / fence | 1 piece |
| Any two of full block, stair, wall and fence in the same family | 1 for 1 |
| 2 planks / stairs / fences → wooden sign | 1 sign; sticks are ignored |
| 1 wooden sign → planks / stairs / fences | 2 pieces |
| 64 planks → signs, experimental wood off | 16 signs in hand, 32 planks in main inventory |
| 3 planks / stairs / fences → wooden trapdoor | 1 trapdoor |
| 1 wooden trapdoor → planks / stairs / fences | 3 pieces |
| 6 wooden slabs → wooden trapdoor | 1 trapdoor |
| 1 held slab + 1 matching slab in main inventory → full blocks | 1 full block in hand |
| 31 held slabs + 1 matching slab in main inventory → full blocks | 16 full blocks in hand |
| 31 held slabs, no matching slab in main inventory → full blocks | 15 full blocks in hand, 1 slab in main inventory |
| 64 full blocks → slabs | 64 slabs in hand, 32 base blocks in main inventory |
| 33 stairs → slabs | 64 slabs in hand, 1 base block in main inventory |
| 64 stairs → logs, with 44 stored logs, Builder Mode and experimental wood enabled | 60 logs in hand, stored stack emptied |
| 63 logs → stairs, experimental wood enabled | 64 stairs in hand, 47 logs in main inventory |
| 1 log ↔ wood, experimental wood enabled | 1 for 1 |
| 63 wood → logs, experimental wood enabled | 63 logs in hand |
| 64 full blocks → slabs, main inventory full | 64 slabs in hand, 32 base blocks dropped on the ground |

Ordinary wooden signs are available by default at bottom-left. They cost **two planks each**, matching six planks for three vanilla signs while ignoring the stick. Their native stack limit is **16**. All twelve vanilla wood types are supported, including bamboo, crimson and warped. Hanging signs use stripped materials and remain outside shape conversion. See the [sign guide](docs/SIGNS.md) for ratios and examples. All eight normal wooden options fit on one wheel when logs, wood and trapdoors are enabled. Existing directions stay fixed.

Every radial conversion fills at most one target stack in hand, using its actual stack limit; slabs also retain their 64-item ceiling. The selected shape always occupies the selected hand slot.

With **Builder Mode on**, the wheel consolidates compatible ordinary family material from main-inventory slots 9–35 into that stack. It first completes the held conversion, then merges existing target items and converts additional compatible shapes in complete lossless batches. With experimental wood enabled, 64 held planks and ten stored logs become 26 held wood; 64 stairs and 44 stored logs become 60 held logs. One held plank and three stored stairs can complete one log. Other hotbar slots, offhand, shulkers, customized items and different families are not consolidation sources. Unaccepted donors remain in their original form.

With **Builder Mode off**, the wheel converts the held stack without merging stored target items or sweeping other family shapes. It may borrow only the matching source items needed to complete its last batch: one held slab plus one stored slab can still become one full block. Other stored source material stays put. The one-stack limit and physical base leftovers apply in both modes.

If a selected shape cannot make even one complete item, **Builder Mode and Shulker Supply** allow the wheel to retrieve existing exact target items from enabled carried/ender shulkers. Two held oak planks can therefore become a hand of stored oak logs while both planks move into main inventory, outside the hotbar. Named slots each retain one item, ender access still needs player/server permission, and retrieval stops at one target stack. It does not convert other shapes inside boxes. If the displaced planks cannot fit main inventory, everything stays unchanged.

Capped excess becomes exact base-first physical change; incomplete source items remain physical too. With Builder Mode, Shulker Supply and permitted ender chest access enabled, this transaction's leftover items first refill identical occupied slots inside named ender-chest shulkers. Empty slots, unnamed boxes and unrelated inventory items are left alone. Any material that cannot be stored there merges into main-storage stacks, then empty main slots, then drops at your feet if full. No remainder fills other hotbar slots or offhand, and no additional target stacks are produced. Selecting the current shape still cancels. These rules apply in Creative mode too.

Conversions intentionally use the values above, rather than vanilla crafting yields. They are reversible. Only ordinary block stacks without custom item-component changes are accepted; named items, container contents and other custom data cannot be laundered through conversion. Changing your held item, count or selected slot invalidates an open wheel.

## Experimental log-based wood families

**Block wheel → Logs and wood (experimental)** starts **OFF**. Verified same-species logs and wood join existing plank families. One log equals one wood block and four planks. Typical integer values are slab **1**, plank/stair/fence **2**, log/wood **8**. Trapdoors retain their three-plank cost. Bamboo is not guessed into this relationship.

A single log can become a single wood and back. Both yield four vanilla planks, so this exchange adds no vanilla crafting gain loop; four-log-to-three-wood crafting still loses material. Automatic storage uses logs as primary and planks as secondary: one fence becomes one plank, four become one log, 25 stairs become 25 planks, and 65 become sixteen logs plus one plank. Mixed main-inventory material aggregates immediately after native menu changes: one plank plus three stairs becomes a log without toggling Builder Mode. The log gate requires an exact batch or more than one actual stack; plank normalization is independent of that gate. Neither can increase occupied slots. Fewer slots take priority over logs. Restocking and pick block instead request the shape being built.

Explicit named-shulker filling is opportunistic: an existing twelve-log template can accept six logs from 25 stairs, preserving the remaining stair. Only accepted exact batches are consumed. Named withdrawals still retain one item per originally occupied slot.

**Stripped logs, wood, stems and hyphae never have a transformation family**, regardless of the toggle. They cannot supply family conversion, normalize, appear in the radial or be generated by conversion. Ordinary exact-item handling and optional pick-substitute selection remain available. Shelves are excluded. See the [wood accounting, policies and data-pack guide](docs/WOOD-FAMILIES.md).

## Build and test

Install JDK 25 and set `JAVA_HOME` to its installation directory, or put its `bin` directory on PATH. A fresh checkout needs only Git, that JDK and internet access for the first dependency download; the checked-in wrapper supplies Gradle 9.5.1. Run:

```sh
./gradlew build
./gradlew runGameTest
./gradlew releaseArtifacts
./gradlew runClientGameTest
# Optional compatibility profile with the real Tweakeroo/MaLiLib 0.29.5 releases:
./gradlew runClientGameTest -PtestTweakeroo=true
# Optional Mod Menu 20.0.3 integration profile:
./gradlew runClientGameTest -PtestModMenu=true
```

On Windows use `.\gradlew.bat`. `build` runs JUnit and Fabric server GameTests and writes production and sources JARs to `build/libs/`; `runGameTest` also runs the server integration suite on its own. `releaseArtifacts` builds and stages only those two JARs and `SHA256SUMS.txt` in `build/release/`. The installable file is `blockwheel-1.0.1+mc26.2.jar`; its internal mod version is `1.0.1`. No local tool paths or previous caches are required.

The client test needs an OpenGL-capable display and a **1600×1000** viewport. Tests include a button form added entirely through data and a registered modded family with smaller stack limits and a three-unit flooring shape. Client scenarios cover inventory controls, native gameplay packets, saved preferences, dedicated-server reconnection and the radial. Test entrypoints, test data and optional compatibility dependencies are excluded from the release JAR.

GitHub Actions runs the build, unit tests and server GameTests on every push/PR and manual dispatch. In **Actions → Build and test → successful run → Artifacts**, download **blockwheel-1.0.1+mc26.2**, extract it, and install the JAR without `-sources`. Client/render tests are opt-in under **Run workflow → client_tests**, with baseline, Mod Menu or Tweakeroo profiles; CI uses Xvfb/Mesa and checks captured frames. See [development and CI](docs/DEVELOPMENT.md) for setup, artifact contents and current validation limits.

The hotbar fixture also reloads synthetic standard/HD resource packs, checks their border profiles and restores vanilla. Screenshot checks read native captures without editing them:

```bash
python3 src/gametest/python/verify_radial_frames.py <vanilla-capture.png> --scale 4
python3 src/gametest/python/verify_resource_pack_frames.py <pack-capture.png> --scale 4 --source-scale 1
```

Use `--source-scale 2` for the HD pack fixture and match `--scale` to the capture's GUI scale. See [release verification](docs/VERIFICATION.md) for the checks actually executed and the pack layouts supported by the sampler.

Pinned toolchain: Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.17.21, Gradle 9.5.1, Java 25. No mappings or remapping step are needed for this unobfuscated Minecraft version.

The included [verification report](docs/VERIFICATION.md) records which checks were actually run for this release.

## Extending it

See [data-pack documentation](docs/EXTENDING.md) and the ready-to-copy [flooring shape example](examples/flooring-pack/). Adding a new form does not require editing Java, packets or the radial layout.

## Implementation map

| Location | Responsibility |
| --- | --- |
| `core/Shape.java` | Form definitions, ordering, tags and suffixes |
| `core/FamilyCatalog.java` | Deterministic, immutable family discovery and explicit overrides |
| `core/ConversionPlan.java` | Exact integer batching and material conservation |
| `data/FamilyData.java` / `data/FamilySafety.java` | Server discovery and stripped/anvil/shelf/copper-state safety |
| `data/WoodFamilyData.java` / `core/WoodFamilies.java` | Recipe-proven or explicit optional wood relations and exact integer scaling |
| `core/FamilyViews.java` | Four immutable per-player trapdoor/wood catalog views |
| `server/ConversionController.java` | Server authorization, single-use snapshots and stale-request checks |
| `server/InventoryConversion.java` | Held output, family consolidation, ender-template change routing and overflow drops |
| `network/Payloads.java` | Bounded request and authoritative wheel-snapshot codecs |
| `client/RadialScreen.java` | Rendering and controls, driven by the server's option list |
| `client/RadialSlotRenderState.java` | Straight polygon bands and current native hotbar textures/colors/alpha |
| `client/compat/BlockwheelModMenu.java` | Optional entrypoint for the shared settings screen |
| `core/RadialLayout.java` | GUI-scaled geometry, closest-icon selection and the neutral center |
| `core/MaterialComposition.java` | Exact primary/secondary storage change and fractional amount formatting |
| `server/InventoryTransaction.java` | Shared capacity routing, snapshots, validation and atomic inventory commit |
| `server/BuilderInventory.java` / `core/StorageCompaction.java` | Shared acquisition and conservative, capacity-validated storage policies |
| `server/TemplateDeposit.java` | Explicit exact/family deposits and opportunistic log-template packing |
| `server/MaterialSupply.java` | Shared source tiers, per-slot reserves and authoritative material totals |
| `core/MaterialWithdrawal.java` | Bounded exact withdrawal when preferred sources would require unstorable change |
| `core/RestockSettings.java` | Validated per-player trigger, additional quantity and non-block shulker preference |
| `core/PickRules.java` / `core/PickDefaults.java` / `core/StatePickDefaults.java` | Bounded overrides and deterministic grouped fallbacks |
| `data/RegistryPickDefaults.java` | Native weathering/waxing graph discovery and log defaults |
| `client/PickRulesClient.java` | Atomic instance-wide JSON persistence and rule synchronization |
| `client/PickRulesScreen.java` / `client/BlockPickerScreen.java` | Visual rule editing and searchable native block previews |
| `server/BuilderServerConfig.java` | Server-owned remote ender chest policy |
| `server/BuilderController.java` | Persistent settings, event boundaries and automatic transaction authorization |
| `core/InterfacePreferences.java` | Independent local control, tooltip and Builder overlay visibility switches |
| `core/RadialPages.java` | Fixed standard directions, reserved gaps and extra-form pagination |
| `core/ManualConversionPlan.java` | Shared client/server one-stack target cap, exact family withdrawals and physical change |

The client requests an operation; the server derives the actual inputs, targets and quantities. It never accepts a client-supplied item count, family, output item or exchange rate. A snapshot can be spent once and expires after two minutes. Data reloads and disconnects discard sessions.
