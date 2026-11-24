# Hand restock settings

Open **Mods → Blockwheel → Configure → Restock**, or right-click the Builder icon in inventory and select **Restock**. Join a world running Blockwheel to edit its saved per-player settings.

| Setting | Default | Behavior |
| --- | --- | --- |
| Hand restock | On when Builder Mode is enabled | Refill after successful main-hand use, placement or completed consumption |
| Restock below | 6 | Refill when fewer than this many items remain; 1 means only when empty |
| Add up to | 32 | Maximum additional items per refill, rather than the final stack size |
| Non-blocks from shulkers (Supply page) | On | Permit carried and ender shulkers to refill non-block items when Shulker Supply is enabled |

Both numbers range from 1 to 99. The sliders also support left/right arrow keys in one-item steps. Requests are validated and saved by the server; changes survive world reopen, death and reconnect alongside the existing feature preferences. Older player data receives the new numeric defaults without changing its feature switches.

At the defaults, six remaining items do not trigger a refill. Five remaining items plus sufficient supply become 37. An empty hand receives up to 32 items. If only nine compatible items are available, five become 14. A sixteen-item stack such as snowballs is still capped at sixteen, regardless of the configured refill amount.

Refills use the existing ordered source planner, family ratios, named-slot reserves and physical change. Unsafe or unavailable refills leave the inventory unchanged. Drops, intentional slot changes and inventory moves do not trigger them. The quantity setting applies to hand restocking; pickup routing, pick block and manual radial conversions retain their own existing limits.

## Non-block supplies

The **Supply** page contains **Non-blocks from shulkers**, alongside the other source choices. With it off, pearls, rockets and ordinary food cannot be withdrawn automatically from carried or ender shulkers. Eligible copies in ordinary main inventory can still refill the hand. Placeable block items retain shulker supplies, including netherrack and other blocks without transformation families. Items that place blocks are treated as block items even if they have another use.

The switch applies only to automatic hand restocking. Explicit pick block and manual named-shulker deposits retain their normal rules. It does not enable Shulker Supply or remote ender access; their existing player/server gates remain required.

## Tweakeroo

Tweakeroo is optional. Blockwheel obtains and converts material on the server itself; it does not call Tweakeroo to fetch stacks or read Tweakeroo's restock item filters, threshold or amount. These Blockwheel settings control the main hand while Builder Mode and Hand restock are active.

If Tweakeroo is installed, a narrow compatibility guard prevents both mods from restocking the same controlled hand. Turning off Builder Mode or Hand restock returns that behavior to Tweakeroo. Its unrelated features and offhand/tool handling remain its own. See [Tweakeroo coordination](TWEAKEROO.md).

## Hotkeys

**Toggle Builder Mode** and **Fill named ender shulkers** are both unbound by default under **Options → Controls → Key Binds → Blockwheel**. They are normal gameplay keys and do not execute while typing or navigating screens. Holding either key does not repeat it. For fill, a short release deposits only main-inventory items; holding for 0.6 seconds fires once and includes the hotbar. Releasing a completed hold does nothing.

The toggle uses the same saved, server-confirmed setting as the Builder button. The fill key uses the same validated manual deposit request as the ender button, including the saved Ender Chest Access preference, server permission, empty cursor and named/occupied template rules. Builder Mode controls whether family forms can convert during that deposit; automatic Shulker Supply does not have to be on.
