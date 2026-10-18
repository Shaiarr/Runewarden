# Equipment inspector

Runewarden can inspect a loaded player's visible equipment from its public native
`PlayerComposition`. The sidebar shows the player's public name/combat level, an actual
item-icon worn grid, full wrapping item names, slot/name hover labels and manual links
to the existing Item database. Inspect yourself uses the same observation path.

The standard grid shows head, cape, neck, weapon, body, shield, legs, hands and feet.
Ring and ammunition slots are marked **Not visible** because another player's
composition does not transmit those items. Ordinary clothing kits are shown as
appearance rather than invented item IDs. Additional native arms/hair/jaw appearance
item IDs, when present, are available in the visible item list. NPC-transformed players
show equipment as unavailable instead of treating the transformation as worn items.

The GE estimate sums only observed pieces in the nine visible standard equipment
slots, using RuneLite's canonical prices. Unpriced pieces remain explicitly unpriced;
rings/ammunition, hidden inventory, private stats and unknown charges are not inferred.
No hiscores, third-party request, web scraping or player-name upload is involved.
Client-side appearance modifications can affect the visible composition, so this is
an appearance inspector, not a server-side gear verification service.

## Display and lifecycle

**Inspector options** controls live gear updates, visible value and the optional movable
HUD. The HUD is off by default and uses the same immutable icon snapshot, the shared
`equipment-inspector` Runewarden HUD style and the normal HUD lock. Its display is
read-only; sidebar item buttons open the database after profile/selection validation.
Live updates do not reconstruct snapshots or query item definitions/icons when the
selected player's equipment IDs, public name and combat level are unchanged. Pausing
updates retains the local snapshot while the selected native player remains loaded;
Refresh obtains a new observation.

Every actor, composition and item-definition read occurs on the client thread. Context
selection and manual refresh dispatch there with lifecycle/profile guards. The player
identity must still occupy the exact native player index in the same loaded world view.
Actor index reuse, world-view changes, disappearance, logout, profile changes and stop
clear the selection. Delayed open-module and item-button callbacks reject stale snapshots.
No player names or equipment observations are persisted. The only saved data is the
three presentation booleans in the owned preference group.

Item definitions/icons use a 128-ID access-order cache and one selected player's finite
12-kit composition. The HUD paints cached snapshots without actor/item-definition reads.
There is no full nearby-player scan, scene scan or network polling.

## Root integration

Type: `EquipmentInspectorTool`.

Injection: `(Client, ClientThread, ConfigManager, ItemManager, Gson,
RunewardenDataStore, RunewardenConfig)`.

- Lifecycle: `start()`, `stop()`, `clear()`, `onGameTick()`.
- Native context action: `select(Player)`; the shared context-menu router owns menu creation.
- Manual self selection: `inspectSelf()`; refresh: `refreshSelected()`.
- Sidebar: `createPanel()`; overlay: `overlay()`; global lock: `setHudLocked(boolean)`.
- Item links: `setOpenItemAction(IntConsumer)`.
- Context selection navigation: `setOpenModuleAction(Runnable)`.
- Owned preferences: `onConfigChanged(ConfigChanged)`, group `runewardenequipmentinspector`,
  key `preferencesV1`, schema 1. Shared HUD style key: `equipment-inspector`.

## Primary precedent and verification

The current Hub Equipment Inspector source is pinned to
<https://github.com/botanicvelious/Equipment-Inspector/tree/19f1b077a3c1304754580f071316b908366edc78>.
Its published feature is seeing player equipment names/icons through a context action;
its implementation reads `PlayerComposition.getEquipmentId(KitType)` and native item
prices. Runewarden independently implements that public observation through its shared
context router, item browser and HUD, with no foreign plugin dependency/configuration.
No source implementation was copied into this module.

`EquipmentInspectorTest` exercises dispatch, exact visible item IDs/icons, unknown
ring/ammunition, ordinary appearance kits, native identity/world-view reuse, live/manual
snapshots, NPC transforms, stale callbacks, canonical known/unknown values, snapshot-only
HUD painting, lock behaviour, presentation-only persistence and the actual 206-pixel
sidebar with expanded controls and item names.
