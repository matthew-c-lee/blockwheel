# Smart pick fallbacks

Open **Mods → Blockwheel → Configure → Pick block**, or right-click the inventory Builder switch and select **Pick block**. **Smart pick fallbacks** toggles the new alternatives; turning it off retains the original exact/family pick behavior.

The requested block always comes first. If the normal pick operation cannot obtain it, alternatives are tried from top to bottom. Each candidate considers the existing permitted inventory/supply sources before the next candidate. For example, an exact stripped-wood stack in an enabled ender shulker wins over an ordinary log already in the hotbar.

Picking an alternative puts that block in your hand. It does not strip a log or convert the alternative into the original target. The normal family-acquisition rules may still obtain the chosen alternative from its own compatible materials. There are no new recipes, exchange rates or free items.

Builder Mode and its Pick block feature must be on. Carried/ender supplies still require the existing player options and server permission. Named shulkers retain one item per occupied slot. Creative pick remains vanilla. Custom-component target stacks do not use an ID-only fallback rule.

## Defaults

**Logs** starts on and derives rules from registered, same-namespace blocks. Missing forms are omitted. New conventionally named wood species can participate without Java edits, including modded species with matching forms.

| Requested block | Alternatives, highest priority first |
| --- | --- |
| Stripped log | Ordinary log of the same species |
| Wood, bark on all sides | Ordinary log of the same species |
| Stripped wood | Stripped log, ordinary wood, ordinary log of the same species |
| Stripped stem | Ordinary stem of the same type |
| Hyphae | Ordinary stem of the same type |
| Stripped hyphae | Stripped stem, ordinary hyphae, ordinary stem |
| Stripped bamboo block | Bamboo block |

Ordinary wood/log requests do not use stripped forms that cannot be unstripped. Defaults never cross species. Custom rules can choose any supported block items, so their priority is your decision.

**Anvils** and **Copper** are independent ON-by-default groups. They use closest damage (more damaged ties) and native copper-state graphs (less oxidized ties), after exact requested-item acquisition. They select actual available substitutes without changing damage, waxing or oxidation. See [STATE-PICK.md](STATE-PICK.md) for complete priorities. Stripped blocks remain excluded from transformation families even when a pick rule selects an actual stripped or unstripped substitute.

## Visual editor

1. Select **Edit fallback rules**. Search the target list or use **Add rule**.
2. Choose a target from the picture grid. Search accepts localized block names or identifiers, such as `oak_log` or `minecraft:stone`.
3. Use **Add fallback** to choose alternatives from that same grid. Clicking an existing alternative replaces it.
4. **Up** and **Dn** change the priority. **Remove** removes an alternative; **Restore default** removes a custom override for a generated rule.
5. **Apply rule** returns to the draft list. **Save** writes all edits together; **Cancel** leaves the saved configuration untouched. Lists and grids support paging and mouse-wheel navigation.

Removing a generated default creates an empty override so it stays disabled while its default group is on. Such a target displays zero fallbacks and can be edited or restored. A rule never lists its own target or repeats an alternative. Rules are one-hop lists: a rule for an alternative is not followed recursively.

Pick rules are saved for the Minecraft instance, independently of world-specific Builder/restock switches. They can be edited before joining a world. Each connection receives your saved rules, and saves/reloads synchronize them again. The server derives the actual pick target from vanilla's reach-checked request and uses only your own permitted supplies.

## JSON file

The file is `config/blockwheel-pick-rules.json`. It is created on first client startup. Use **Reload file** in the editor after a manual edit; restarting also loads it. Saves use a temporary file and atomic replacement where supported. Malformed data is reported in the log and retained on disk, rather than overwritten. A failed reload keeps the last valid preferences; a malformed file on startup leaves smart fallbacks disabled until corrected.

```json
{
  "version": 1,
  "enabled": true,
  "include_log_defaults": true,
  "include_anvil_defaults": true,
  "include_copper_defaults": true,
  "rules": [
    {
      "target": "minecraft:stripped_oak_wood",
      "fallbacks": [
        "minecraft:stripped_oak_log",
        "minecraft:oak_wood",
        "minecraft:oak_log"
      ]
    }
  ]
}
```

`rules` contains overrides/additions to the generated defaults. An empty `fallbacks` list disables that target's generated rule. Each `include_*_defaults` flag controls its generated group. Disable all three to use only explicit rules. Missing new flags in an old version-1 file default to true without resetting existing preferences. Unknown block IDs are retained for portability; missing or unavailable alternatives are skipped at pick time.

Limits are 256 custom targets, 16 alternatives per target, 256 characters per identifier and 28,000 encoded bytes of rule data. These keep the synchronized configuration inside the native client-to-server packet budget. The editor prevents duplicate/self alternatives and reports capacity failures without replacing the current file. The ready-to-copy [example](../examples/pick-rules.json) uses the same format.
