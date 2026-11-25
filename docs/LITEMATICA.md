# Litematica Easy Place

Blockwheel can supply a missing building item before Easy Place begins placing it. The optional bridge targets **Litematica 0.28.8** and **MaLiLib 0.29.5** for Minecraft 26.2. Both the default Easy Place path and its `easyPlacePostRewrite` alternative are covered. Neither mod is required or bundled with Blockwheel.

Enable **Builder Mode → Hand restock**, then use Easy Place normally. Enable **Shulker Supply** to include carried boxes and **Ender chest access** for shulkers in your personal ender chest; the server's remote policy still applies. Install Blockwheel 1.0.2 on client and server. Litematica remains client-side.

When the required item already exists in normal inventory or either hand, Litematica keeps its normal picking behavior. If it is missing, the bridge asks the server for that exact block. The server uses the existing restock planner: compatible ordinary family material may convert, and items without a family require exact copies with Supply enabled. The initial fetch uses **Add up to** (32 by default), capped by stack capacity and available material. Subsequent placements use the normal **Restock below** threshold and additional amount, in either hand.

Litematica's configured pick-block slots and tool-avoidance rules choose the destination. A displaced stack must fit main inventory, unchanged. Requests carry the target block and an expected hand snapshot; the server validates the active slot, menu, cursor, item eligibility, settings and fresh storage. Client and server rate limits bound repeated attempts. The client waits for synchronized items before Easy Place's next attempt; it does not create predicted stacks or read remote contents.

Named source slots each retain one item. Automatic donors remain main storage and enabled carried/ender shulkers, excluding loose hotbar, offhand and loose ender items. Pick-block fallback substitutions are not used because the schematic needs its requested block. Unsupported/custom-component targets retain Litematica's normal path. Unavailable material or unsafe displacement leaves the inventory unchanged. Easy Place still owns placement, orientation, reach and its normal activation controls.

The hook locations and slot selectors were inspected in the [upstream EasyPlaceUtils](https://github.com/sakura-ryoko/litematica/blob/LTS/26.2/src/main/java/fi/dy/masa/litematica/util/EasyPlaceUtils.java), [WorldUtils](https://github.com/sakura-ryoko/litematica/blob/LTS/26.2/src/main/java/fi/dy/masa/litematica/util/WorldUtils.java) and [InventoryUtils](https://github.com/sakura-ryoko/litematica/blob/LTS/26.2/src/main/java/fi/dy/masa/litematica/util/InventoryUtils.java), and checked against the actual [0.28.8 release JAR](https://modrinth.com/mod/litematica/version/CuniXtbo). Optional pseudo-mixins wrap only Easy Place's picking call. The slot-selector reflection is resolved once; an unsupported selector API disables the bridge with a log message. An older Blockwheel server without the new request channel retains normal Litematica behavior.

For the optional integration suite:

```sh
./gradlew runClientGameTest -PtestLitematica=true
```

GitHub Actions also offers `litematica` under its manual client profile choice. The fixture creates real schematic-world targets, invokes both actual Easy Place paths, and checks remote family acquisition followed by placement and normal restocking. On November 25, 2026, both paths passed locally with Litematica 0.28.8, MaLiLib 0.29.5 and Tweakeroo 0.29.5 loaded together. The scenarios use Litematica's default direct-click policy and verify its configured pick slot, preservation of the held tool, named ender-shulker withdrawals, actual placements and subsequent hand restocking.
