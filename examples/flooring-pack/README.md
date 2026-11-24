Copy this directory into a Minecraft 26.2 world's `datapacks` directory, then run `/reload`.

This adds the **Flooring** shape to Blockwheel. It discovers already-registered blocks ending in `_flooring`, or blocks tagged `my_mod:flooring` with a suitable crafting recipe or explicit family mapping. It does not add new blocks. It is harmless when no matching flooring blocks are installed.

Flooring has value 1, like slabs, so one full block converts to two flooring pieces. Edit `units` to 2 for a one-for-one conversion. No Java changes are required.
