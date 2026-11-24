# Tweakeroo hand-restock coordination

The implementation was based on the actual **Tweakeroo 0.29.5 for Minecraft 26.2** release and its matching source, rather than an assumed extension API.

- [Official release](https://modrinth.com/mod/tweakeroo/version/tzGdG1Ri)
- [Matching source commit](https://github.com/sakura-ryoko/tweakeroo/tree/ad957dad73554a00a2b948ea88059713a2dada67)
- [InventoryUtils at that commit](https://github.com/sakura-ryoko/tweakeroo/blob/ad957dad73554a00a2b948ea88059713a2dada67/src/main/java/fi/dy/masa/tweakeroo/util/InventoryUtils.java)

The inspected public entrypoints are `preRestockHand(Player, InteractionHand, boolean)` and `restockNewStackToHand(Player, InteractionHand, ItemStack, boolean)`. Tweakeroo performs client-side slot-click operations to find and swap matching stacks. It has no server-side family-conversion interface for Blockwheel to call.

Blockwheel therefore implements family-aware restocking on the server and adds a small optional client guard against competing Tweakeroo restock clicks. The guard applies only when all of these are true:

- Builder Mode and its hand-restock feature are enabled by the server.
- The affected hand is the local player's main hand.
- The reference is an ordinary member of a supported base-bearing family, or Shulker Supply is active and the reference can use exact quantity restocking. Damageable tools retain Tweakeroo's own handling.
- The normal inventory menu is active and its cursor is empty.

In that case, the two Tweakeroo restock entrypoints are cancelled for that operation. Successful placement drives the server restocker. The same shared planner handles exact-shape donors, base material, other family forms, capacity and physical change.

Tweakeroo is optional. Blockwheel does not link its classes from the core engine, require it as a dependency, change its configuration, or read its restock item filters/threshold/amount. The shared **Restock** page controls Blockwheel's trigger and additional quantity, defaulting to fewer than six items and up to thirty-two additional items. Disabling Builder Mode or its restock feature releases ownership back to Tweakeroo. Unsupported items and offhand/tool behavior retain Tweakeroo's own rules.

Shulker Supply extends the same server restocker rather than adding another client restock path. The ownership guard applies while the main hand is controlled by Builder Mode, regardless of which eligible source ultimately supplies it. Since 2.3.0 this includes non-family exact refills, such as netherrack and consumed items, while leaving durability handling to Tweakeroo. It does not need a client-side copy of the ender chest contents. Supply, reserve protection and remote policy remain independent of whether Tweakeroo is installed. The client profiles exercise combined carried/remote/named withdrawals, non-family refills, remote pick, persistence and tooltips both with and without the actual pinned releases.

This avoids speculative client inventory edits and duplicate slot-click sequences while retaining Tweakeroo for its other uses. The tradeoff is that Builder restocking follows server-confirmed placement and inventory updates, so ordinary network latency can be visible when the last held item is used.

The compatibility test profile uses the real Tweakeroo **0.29.5** and MaLiLib **0.29.5** JARs. It enables Tweakeroo hand restock, invokes both actual entrypoints to check suppression while Builder Mode owns the hand, then disables Builder Mode and verifies Tweakeroo can refill normally. See [VERIFICATION.md](VERIFICATION.md) for executed results and scope. Neither third-party mod is bundled in the release or source archive.

To reproduce the optional dependency profile with Gradle, use `./gradlew runClientGameTest -PtestTweakeroo=true`. The default `./gradlew runClientGameTest` profile has no Tweakeroo dependency.
