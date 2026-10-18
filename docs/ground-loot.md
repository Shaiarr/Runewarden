# Contextual ground loot

Runewarden highlights floor items required by the selected guide or a player's manual target list, regardless of their price. Other floor stacks qualify by configurable total GE value (default 25,000 GP). Unknown prices never qualify as valuable by guesswork; they can still qualify as targets. Noted items match their canonical unnoted target.

The scene overlay uses the black/white/gold HUD type and gold target tiles, with a red urgency accent during the final 50 observed server ticks. Separate title and ownership/deadline lines avoid one long unreadable label. Display options independently control labels, target tiles and timers. The sidebar lists nearby matches, item-database links, individually removable targets, and an optional own/group-only filter. Manual target presentation preferences are shared; scene observations are cleared between characters.

## Native observations and limits

- Native `TileItem` instances remain separate, including identical IDs on the same tile. Mixed ownership, quantities and deadlines are not merged into a fabricated timer.
- Despawn and visibility deadlines come directly from `TileItem.getDespawnTime()` and `getVisibleTime()` relative to `Client.getTickCount()`. Missing/nonpositive deadlines produce no timer. Permanently private native items never receive a public countdown.
- Takeability follows native ownership and account type, using RuneLite 1.13.2 Ground Items' rules: self/group/none are shown; other-player items are shown for non-iron accounts. Unknown ownership is excluded. This describes ownership eligibility, not path reachability.
- `ItemSpawned`, `ItemQuantityChanged` and `ItemDespawned` maintain a bounded identity map. There is one initial snapshot per top-level world view, scene, base coordinate and plane, including enabling while already logged in. No scene scan, price lookup or JSON decode occurs in rendering.
- Loading/logout/hops, account changes and world-view unloads discard stale observations. Floor items do not add profit or earned loot; Run Loot remains separate.
- Bounds: 4,096 observed native instances, 160 prioritized display entries, 512 cached definitions and 128 manual targets. Crowded scenes are marked in the sidebar; at most four labels are drawn on a single tile.
- This module does not change the native Ground Items plugin's settings, replace game menus, pick up items, or assume that visibility means reachability. Both overlays can be enabled independently.

## Integration contract

`GroundLootTool` injection: `(Client, ConfigManager, ItemManager, Gson)`.

Lifecycle: `start()`, `stop()`, `clear()`, `clearScene()`, `onGameTick()`; sidebar: `createPanel()`; scene renderer: `overlay()`.

Forward these native events: `onItemSpawned(ItemSpawned)`, `onItemDespawned(ItemDespawned)`, `onItemQuantityChanged(ItemQuantityChanged)`, `onGameStateChanged(GameStateChanged)`, `onWorldViewUnloaded(WorldViewUnloaded)`. Forward owned preferences through `onConfigChanged(ConfigChanged)`; group `runewardengroundloot`, key `preferencesV1`.

`setTargetItems(Supplier<Set<Integer>>)` supplies a cached active guide/supply target set; sampled once per game tick, bounded to 1,024 IDs. `setOpenItemAction(IntConsumer)` opens the existing item database on a manual sidebar click. Item detail callbacks reject an old account/profile session.

`GroundLootToolTest` covers initial snapshots/replayed events, target relevance versus price, noted targets, mixed native ownership/deadlines, quantity changes, native timer changes, permanent privacy, ironman filters, scene/account cleanup, disabled-state reseeding, display bounds, stale sidebar actions, malformed settings and actual 206-pixel layout.

## Primary reference

Implementation was checked against pinned RuneLite **1.13.2** API/client source jars: `net/runelite/api/TileItem.java`, `Tile.java`, item events, `plugins/grounditems/GroundItemsPlugin.java` (ownership filtering) and `GroundItemsOverlay.java` (private versus public visibility semantics). Public upstream: <https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/grounditems>.

## 0.8.1 native loot beams and radial deadlines

The owned Ground loot module now renders animated native RuneLite objects at matching
floor tiles. It adapts RuneLite 1.13.2's Ground Items beam models, animation IDs and
recolouring technique, rather than calling its package-private `Lootbeam` class or
changing Ground Items settings. Both **Modern** and **Light** styles are selectable.
These are cosmetic scene objects; floor items and game menus remain unchanged.

| Value tier | Default minimum total stack GE value |
| --- | ---: |
| White | 25,000 GP |
| Green | 100,000 GP |
| Blue | 1,000,000 GP |
| Purple | 10,000,000 GP |
| Gold | 100,000,000 GP |

All five thresholds and the minimum beam tier are editable under **Loot beams**. They
are GP bands, not inferred item rarity. The guide/manual target beam switches and
selectable target colour allow required supplies to beam even when unpriced or below
the value threshold. A target's selected colour is its minimum tier; a more valuable
item keeps its higher value tier. The label price floor is independent of the beam
floor. One beam represents the strongest qualifying item at a native tile/world view.
The existing account ownership and ironman filters apply before beam selection.

The default limit is 32 beams, configurable from 1 to a hard cap of 64. Candidate
selection uses all eligible observed items, independently of the 160-entry label
snapshot, then retains only the selected tile spots. At most ten recoloured models
(five tiers × two styles) and two animation references are cached. Unchanged beams
reuse their native objects and model references. Missing models retry no more often
than every five game ticks. No model loading or object creation runs in overlay paint.

**Display options** independently controls countdown text and radial timers. Native
remaining time appears inside the rings: **D** for despawn and **P** for the transition
to public loot. Public rings use blue; despawn rings use the item's value/target colour
and inherited urgency accent. The ring baseline is the item's first local observation,
not an invented original spawn time. Both deadlines remain exactly the native
`TileItem` values. Unknown/nonpositive deadlines and permanently private public
transitions produce no guessed timer. The shared `RadialTimer` painter preserves the
outer label font and graphics state.

Beams clear on despawn reconciliation, scene/base/plane transitions, world-view unload,
account changes, disabling and shutdown. Cleanup captures only old owned native
objects before dispatching through `ClientThread`; delayed cleanup cannot remove a
new session's objects. Public `RuneLiteObject` location/world-view APIs preserve the
observed instance and plane. Neither native Ground Items configuration nor its own
beam objects are accessed.

The Guice constructor is now `(Client, ConfigManager, ItemManager, Gson,
RunewardenDataStore, ClientThread)`. Existing four/five-argument fixture constructors
remain. Root event, lifecycle, sidebar and overlay wiring are unchanged.

The native model reference is pinned to
<https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/grounditems/Lootbeam.java>:
Light model `5809` / `ENAKH_LIGHT_STREAMING`; Modern model `43330` /
`FX_BEAM_IDLE`. The complete upstream BSD-2-Clause notice is bundled as
`licenses/ground-loot-beams-BSD-2-Clause.txt`. This is source/API parity evidence,
not a claim that the Runewarden release has already received Plugin Hub review.
