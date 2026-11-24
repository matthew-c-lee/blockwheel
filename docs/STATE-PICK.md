# Anvil and copper pick defaults

The **Pick block** settings tab has independent **Logs**, **Anvils** and **Copper** switches in one row below Smart pick fallbacks. The new default groups start ON under the existing Smart Pick feature. Builder Mode, enhanced Pick block and Smart Pick must still be enabled. Existing users retain their custom overrides, global enable state and log-default preference.

The exact requested item remains first, using vanilla loose-inventory handling or the existing permitted supply planner. The requested state's own family can supply its shape before any fallback is considered. Each fallback then considers its eligible inventory and shulker sources in the existing order. Access and protected-template rules remain in force.

## Anvils

| Requested | Alternatives, highest priority first |
| --- | --- |
| Anvil | Chipped, damaged |
| Chipped anvil | Damaged, normal |
| Damaged anvil | Chipped, normal |

This is closest damage first, with the more damaged variant winning equal-distance ties. The actual available variant goes into the hand. Anvils never enter shape families or storage normalization and are never repaired or damaged by conversion.

## Copper

After the exact oxidation/wax state and its permitted shape conversion:

1. Same oxidation with opposite waxing.
2. Other oxidation levels with the requested waxing, nearest first.
3. Other oxidation levels with opposite waxing, nearest first.

Less oxidation wins equal-distance ties. For exposed unwaxed cut copper stairs, generated alternatives therefore are:

1. Waxed exposed cut copper stairs.
2. Unaffected cut copper stairs.
3. Weathered cut copper stairs.
4. Oxidized cut copper stairs.
5. Waxed unaffected cut copper stairs.
6. Waxed weathered cut copper stairs.
7. Waxed oxidized cut copper stairs.

An alternative may be obtained from its own same-finish family shapes. Waxed weathered cut copper slabs can produce waxed weathered cut copper stairs; their wax and oxidation remain waxed/weathered. They cannot produce unwaxed or unaffected stairs. Picking a substitute does not fabricate the requested finish.

Subtype relationships come from Minecraft's native weathering and waxing maps, rather than substring similarity. Cut blocks, full copper, grates, doors and other native subtypes retain their distinct graph roots. Family construction additionally rejects mixed waxing/oxidation finishes. There is no conversion that adds/removes wax or changes oxidation for free.

## Editing defaults

Use **Edit fallback rules** to inspect, replace, reorder or suppress individual generated rules with the existing searchable block-picture editor. Empty overrides disable a generated target, and Restore Default removes its override. Rules remain one-hop alternatives. Explicit user rules may select unrelated actual alternatives, but still do not define conversion rates or permit forbidden family transformations.

The instance file remains `config/blockwheel-pick-rules.json`, version 1, with two additive optional booleans:

```json
{
  "version": 1,
  "enabled": true,
  "include_log_defaults": true,
  "include_anvil_defaults": true,
  "include_copper_defaults": true,
  "rules": []
}
```

Old files migrate without resetting existing fields or overrides. The file's normal Save/Cancel, validation, atomic-write, malformed-file preservation and reload behavior are unchanged. Client and server should both run this release for its versioned preference packets. Modded copper states need the native graph relationships or explicit pick overrides; arbitrary names alone do not establish state compatibility.
