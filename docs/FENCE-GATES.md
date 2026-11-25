# Fence gates on the wheel

Hold R and point at the fence option. Scroll either direction to alternate between **Fence** and **Fence Gate**, then release R to convert to the displayed form. Scrolling changes only the choice; the inventory changes on release. The neutral center and selecting the current form still cancel.

Both forms use the same position and icon size. Fences remain down in ordinary wood families and bottom-left when a wall also exists. The gate adds no extra node or page. Holding a gate starts that shared node on Fence Gate, so scroll once to choose Fence. A family without a gate keeps its previous fence option. A gate-only custom family still has a reachable node.

One gate is worth **two planks**, matching the wooden portion of the native recipe; the four sticks are ignored and never generated. Therefore one gate converts to two fences or four slabs under Blockwheel's existing family values, and four planks make two gates. Incomplete source items remain physical, and manual output is capped at one actual stack. Builder Mode off/on source policies are unchanged.

The ordinary data definition uses `minecraft:fence_gates`, the `_fence_gate` suffix and a `minecraft:planks` base scope. Gates participate in shared Builder storage, restocking, supply and named-template conversion without separate arithmetic. Experimental wood gives two gates per default log. Stripped blocks and unrelated species remain excluded by existing safety rules.

Scroll elsewhere retains page navigation for packs with extra pages; Page Up/Down always change pages. Scrolling over the fence/gate node cycles that pair before considering page navigation.
