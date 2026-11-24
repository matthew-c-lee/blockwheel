# Interface design

The radial consists only of native item renders inside dark cube-shaped slots. It has no central panel, label, preview, number, current-item marker, page label or background disc. The crosshair is hidden while the wheel is open; the rest of the HUD and hotbar retain their normal visibility and brightness.

The silhouette is computed from the standard block model's orthographic GUI projection, using the actual Minecraft `assets/minecraft/models/block/block.json` rotation `[30,225,0]`. A transformed unit cube gives a projected half-width of `sqrt(2)/2` and half-height of `(cos(30°) + sqrt(2) sin(30°))/2`, with vertical side edges. Six floating-point vertices preserve those proportions. Straight GPU edges replace the previous one-GUI-pixel rows, so diagonals do not develop large steps when GUI scale increases.

All shapes use the same silhouette, with its inner radius retained at 80% of the 2.4.0 size. Parallel-edge polygon offsets make the border two GUI pixels thick normally and three on selection, measured perpendicular to each edge. Both have a one-GUI-pixel dark outer shell; it moves outward with selection. Its outside coverage fades over one physical display pixel. The interior, item center and item scale remain fixed.

The frame samples the live `hud/hotbar` and `hud/hotbar_selection` GUI sprites. Individual inner/outer gray bands retain the native texture variation and directional bevel shading; the selected shell uses the selected sprite's own shade and alpha instead of leaving the old dark frame underneath. The interior repeats a 15×15 native hotbar floor patch, excluding its slot-edge shadow, at one logical texel per GUI pixel. Polygon clipping fits tiles inside the hexagon without stretching them or scaling the noise. Native transparency is retained. Vanilla's selected bottom bands sample its last three colored rows; its transparent interior is never used as a border strip. No approximate radial palette, opaque replacement background, copied Minecraft PNG or generated noise texture is bundled. Items continue to use Minecraft's normal renderer and models.

`HotbarFrameProfile` reads the already-loaded sprite pixels once per sprite identity. It distinguishes contiguous outline/padding from the colored bevel independently on the top, right, bottom and left. A majority across each straight strip avoids treating individual texture flecks as a new border. Shorter bevels are fitted to the existing two/three GUI-pixel bands; an oversized pack outline cannot become additional black bevel bands. Source coordinates use the actual texture resolution, and the shell samples a source-pixel center after any transparent padding. Texture variation along the edges remains live. Reloading packs creates new sprites and profiles; weak cache keys do not retain old atlases or images. No per-frame image reads or disk scans are needed.

This adapts ordinary rectangular hotbar borders, rather than trying to infer arbitrary artwork. If an outline or bevel cannot be distinguished, the known vanilla layout remains the fallback. The interior still uses the native floor-patch layout, so packs with radically rearranged artwork can need explicit future support. Vanilla's complete reference images remain pixel-identical to the preceding frame build, including the bottom highlight correction.

`RadialSlotRenderState` submits the textured polygon mesh through the native GUI pipeline. `BlockwheelStyle` delegates radial drawing, renders the frameless Builder track/thumb and retains the deposit control's square shading and original glyph. No Allumeria textures or per-family icon images are redistributed.

## Builder icon

The Builder control keeps its 20×20 input target inside the player inventory's top-right corner, above the crafting result. Only its 16×10 track and thumb are drawn; there is no outer button box. OFF places the thumb left on a gray track; ON moves it right on a muted green track. The thumb also changes its mark, so state is not conveyed by color alone. Hover/focus changes the track's own edge. The native tooltip reports state, left-click toggle and right-click settings. Keyboard activation retains native button input, focus and narration. The settings master uses a larger labeled switch with the same positions and no surrounding box. Confirmed ON/OFF changes play enchanting/grindstone UI sounds and normally set the hotbar overlay text. Default toggle/deposit clicks are suppressed to avoid duplicate sound layers; right-click settings retains its ordinary UI click.

The icon also appears on Creative's player-inventory tab. It is hidden on other Creative tabs and when a narrow recipe-book view covers the inventory. Positions follow the actual panel origin and GUI scale, not fixed display coordinates.

## Mod Menu

The optional `modmenu` entrypoint opens **Blockwheel Settings**, implemented by the existing `BuilderSettingsScreen`, with Block wheel, Builder, Restock, Supply, Pick block and Interface tabs and an ordinary Done/back path. The screen starts on Block wheel. The master switch appears only on Builder; it is no longer a global header for unrelated pages. Gameplay controls use server-confirmed preferences. Restock exposes the hand-refill toggle and two native bounded sliders; the non-block shulker option is on Supply. Enhanced pick block is on Pick block alongside the instance-wide fallback rules; left/right arrows change the sliders one item at a time. A local edit draft composes rapid changes before the server echoes them, without granting inventory authority. Outside a compatible world the screen disables server-owned preference writes. Instance-wide pick rules and interface settings remain editable, with dedicated subtitles explaining their scope. The rule editor uses native block models, button/keyboard focus, a searchable picture grid and explicit ordering controls. Mod Menu is neither a required dependency nor bundled.

## Optional controls and radial-only use

**Show Builder Mode Button** and **Show Shulker Refill Controls** start ON. Turning either off removes its native widgets, click target and focus target; it leaves no placeholder. These switches are local visibility preferences, independent of Builder Mode, supply permissions and named-template protection. Screen reinitialization cannot restore an obsolete hidden widget.

The unbound **Open Blockwheel Settings** key opens the same screen during gameplay. Mod Menu remains an optional title-screen entry point. On the Block wheel page, **Use only the block wheel** switches the Builder master off and hides both optional controls; it preserves individual feature settings, pick rules and manual-radial options. The preset requires a compatible world because it changes the server-owned master.

The Block wheel tab contains the independent off-by-default wooden-trapdoor and experimental wood-family options. Gameplay settings remain per-player and server-confirmed; the four Interface preferences are local, persistent and editable outside a world.

## Optional text visibility

The Interface tab has **Hide button tooltips** and **Hide Builder status text**, both OFF by default. The first clears native tooltip holders on the Builder inventory switch and named-shulker fill control, without changing their messages used for narration. It does not hide item tooltips or settings help. The second skips only the Builder ON/OFF overlay; enchanting/grindstone sounds still play. Deposits already have no text. These settings are local to the instance, saved in `config/blockwheel-client.json`, and can be edited outside a world.

## Fixed alternates

Base up, stairs right, slab left and wall down retain their coordinates and sizes when diagonal options appear. Wooden signs use bottom-left. A fence-only family uses down; when walls coexist, fences get their own bottom-left icon. Opted-in wooden trapdoors use bottom-right. Experimental logs/stems use top-left, and wood/hyphae use top-right; planks retain up. A custom wall/fence pair keeps its existing bottom-left fence, with a conflicting sign using a remaining gap or page. All eight ordinary wood shapes otherwise fit together. Other data-defined forms fill remaining diagonals, then further pages. Scroll only changes pages and never swaps a form inside an icon. The neutral center, nearest-node selection, visible pointer and hold/release conversion remain unchanged. Selecting the held shape still cancels. Accessibility narration retains the selected shape and exact conversion calculation without putting that text on screen.

The deposit button fills only from main inventory on short release. Holding for 0.6 seconds includes the hotbar and shows a two-pixel progress strip within its existing bounds. It dispatches once at the hold threshold; release cannot send a second short action. The existing empty-cursor, visibility, window-focus and named-template rules still apply.

The explicit deposit button and its rules are covered in [NAMED-SHULKERS.md](NAMED-SHULKERS.md). Actual-client captures and executed checks are listed in [VERIFICATION.md](VERIFICATION.md).

The readable [client configuration example](../examples/blockwheel-client.json) shows all four presentation defaults. The experimental material choice is server-saved per player, rather than a local visibility field.

## Page organization

| Page | Options |
| --- | --- |
| Block wheel (first) | Wooden trapdoors, experimental logs/wood, radial-only action |
| Builder | Builder Mode master, picked-up block conversion, main-inventory compaction, material totals |
| Restock | Hand restock, trigger count, additional refill amount |
| Supply | Shulker Supply, ender access, pick sources, stored totals, non-block shulker restocking |
| Pick block | Enhanced pick, fallback switch, Logs/Anvils/Copper default groups, visual rule editor |
| Interface | Inventory control visibility, control hover help, Builder status text |

Pages explain their purpose in plain language. Source/world scope and Builder Mode status appear below that description. The three built-in fallback groups share one row so all controls fit without overlapping Done at GUI scales 2/3/4. The help explains what changes in play rather than internal validation or implementation requirements. Existing settings, file keys and defaults are retained.
