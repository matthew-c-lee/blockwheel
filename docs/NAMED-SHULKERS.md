# Fill named ender shulkers

Press and release the small deposit icon beside an open personal ender chest to fill from main inventory only. Hold it for **0.6 seconds** to include the hotbar and offhand. The same gestures apply to the unset-by-default fill key while playing. It moves matching items from your inventory into existing stacks inside named shulkers stored in that ender chest. No box needs to be opened or placed.

A successful deposit plays the ender-eye insertion sound, without a status message. A quick gesture sends its request only on release. A completed hold sends one request at the threshold and does nothing on release, even if new material arrives while still held. The button shows a filling two-pixel progress strip until release. Moving away, hiding the control, closing the screen or losing window focus cancels an unfinished button gesture. An empty or rejected deposit stays silent.

**Settings → Interface → Hide button tooltips** can hide the fill button's hover help, together with the Builder inventory switch's help. It starts OFF and preserves narration. Wooden-trapdoor coercion participates only when **Wooden trapdoor shapes** is enabled; each trapdoor is worth three planks.

**Show Shulker Refill Controls**, ON by default in Interface, removes these controls and their input targets when disabled. It does not disable supply or template protection; the deposit key remains available. Experimental wood permits carried-only filling without remote access, while ender destinations still require their existing authorization.

The inventory also has a deposit shortcut to the right of the panel, following the right edge of exposed ender chest slots when a side panel provides them. It can be used while the ender chest is closed, provided Ender Chest Access is enabled and the server permits remote access. It does not require Builder Mode or automatic Shulker Supply. The button on an already open personal ender chest does not require those remote settings or Builder Mode.

## Template rules

- Only supported shulker items with a native custom name are destinations. Unnamed shulkers and loose ender chest items are ignored. With experimental wood off, destinations are ender-only. With it on, the same explicit action also supports carried named templates, before permitted ender destinations.
- Only occupied inner slots can accept items. Empty slots remain empty, even if another slot in the box contains that item.
- Normally exact item-and-component matches fill first across all eligible templates. With Builder Mode and experimental wood on, occupied compatible log templates get priority across boxes, then exact secondary templates and remaining compatible forms. With Builder Mode off, only these exact matches are moved.
- With Builder Mode on, compatible ordinary family variants may then convert into remaining template capacity. For example, a completed hold can use hotbar cobblestone stairs to fill a cobblestone template; a short release cannot. This uses the existing family ratios, actual stack limits and safe physical change. Customized inputs or templates are not stripped or coerced.
- Duplicate template slots fill independently in their original positions. Boxes use carried-then-ender order when opted in, and native slot order within each tier; templates use their existing inner slot order.
- A short release uses only main storage slots 9–35. A completed hold adds hotbar slots 0–8 after main storage, including the selected stack, then the offhand. This scope applies to exact matching and family coercion, including experimental log packing. Armor, crafting slots and cursor-held items are not sources. Offhand material is spent only by this explicit bulk action; automatic restock and pick supply still exclude it.
- Actual stack limits apply, including smaller modded limits. Unspent input stays in inventory. Odd slabs and custom ratios remain physical items; conversion change goes into main storage. If the required change cannot fit, that conversion is skipped. There are no overflow drops or virtual balances.
- Box name, color and other components are preserved. The action only grows matching inner stack counts; it never sorts, consolidates, creates templates or moves items between slots.
- Nested shulker items and other items that cannot legally fit inside container items are not deposited.

For example, a named box contains 60 cobblestone in slot 1, an empty slot 2, and one cobblestone in slot 3. Depositing 20 cobblestone leaves 64 in slot 1, slot 2 empty, and 17 in slot 3. A separate unnamed box remains untouched.

The button/key is an explicit inventory-wide action. Separately, a manual radial conversion can return only its own leftover material to identical occupied named ender-shulker templates when Builder Mode, Shulker Supply and permitted Ender chest access are on. It does not sweep the inventory, create slots or convert the leftover item. Capacity that is unavailable falls back to main inventory and normal manual overflow drops. Pickups, restocking, normalization and screen closing do not trigger a deposit. Named-box withdrawal protection remains one item per occupied slot, so filling a template prepares it for later withdrawals without changing its layout.

## Experimental log packing

Existing log slots accept compatible complete batches even when conservative main-inventory normalization would leave the source alone. A twelve-log template accepts six logs from 25 stairs and leaves one original stair. An existing stair template may then accept that remainder; no new template is created. Default wood fills log templates 1:1; three wood fit three log spaces. Explicit custom 4:3 ratios still need complete lossless batches. The shared exact-withdrawal planner confirms capacity before spending sources, preserves rejected input in its original slots, and never changes template types. See [WOOD-FAMILIES.md](WOOD-FAMILIES.md).

## Server validation

The request contains only the current menu ID. The server verifies the player's current screen, ownership, cursor state and access policy, then calculates the complete move from fresh inventories. Experimental carried-only filling needs no ender permission; remote destinations still require the saved Ender Chest Access preference and server policy unless the native personal ender chest is open. Inventory and ender chest stacks are revalidated before any writes. A conflicting input or destination rejects the whole plan; no source is partially spent.

An open ender chest is recognized by the server menu's actual backing container identity, not its display title. Other chests cannot grant access by being named “Ender Chest.” The remote inventory shortcut uses the same `config/blockwheel-server.json` policy as remote supply. If the server disables remote access, the open-container action still uses the access the server has already granted.

## Button placement

The fill button is to the right of the native ender chest panel. In player inventory it also follows a side panel's right edge when that panel exposes native personal-ender slots; otherwise it uses the current panel's right edge. On a viewport without enough space to the right, it moves to the upper-right header. It no longer occupies the recipe-control area. It is separate from the Builder icon above the crafting result.

The user's separate ender chest side-panel mod was not identified or installed in the test environment. The implementation avoids guessing its private classes and uses exposed native slot bounds. That detection was tested with a simulated native ender-slot panel. A mod that draws its panel entirely through private UI objects may require a dedicated adapter; its name or source is needed to verify that layout. The native ender chest screen and inventory fallback remain available.
