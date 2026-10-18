# Skills progress

`SkillProgressTool` reads all native skills, including Sailing, from the installed RuneLite **1.13.2** client. It provides a separate movable `skill-progress` HUD, native Skills-tab progress strips and tooltips, and a RuneLite-only “Runewarden goal” context action alongside the existing skill guide.

| Feature | Behavior |
| --- | --- |
| Actual skill state | Native current XP, real level and boosted level. Initial login values, boosts and offline catch-up values are not counted as newly earned XP. |
| Level goals | Per-profile targets from level 2 through virtual level 126. The actual skill XP cap remains 200,000,000. |
| Custom XP goals | Explicit targets from 1–200,000,000 XP, with comma input accepted and validation at the form. Goals retain their starting XP so their progress measures the selected task. |
| Clearing goals | Returns the skill to its native next-level goal; skills already at real level 99 show that cap as complete until an explicit virtual/XP goal is selected. |
| Measured rates | Positive observed XP differences divided by observed game ticks. Rate/ETA wait for ten observed ticks (six seconds) of samples. There are no catalog, hiscore or assumed hourly rates. |
| Idle handling | Configurable idle pause from 0–600 seconds (0 disables idle pause), default 60 seconds. Each skill has its own active clock; manual pause still updates actual XP while excluding gains/time from the measured session. |
| Interrupted sessions | Hopping, logout and connection loss suspend baselines. Offline XP becomes the new baseline on return. A backwards XP change resets that skill's session rather than generating negative rates. Switching profiles clears session metrics and loads that profile's goals. |
| HUD cards | One to four skill cards, actual native icons from `SkillIconManager`, goal progress, remaining XP, measured session/rate and ETA. Each metric can be hidden; the shared HUD settings provide position lock, scale, type, frame, colors and visibility. |
| Follow latest | The HUD can follow the latest skill that gained XP. This does not change the sidebar's edited skill or overwrite a draft XP target. Manual selection/context actions select a skill until the next gain. |
| Native Skills tab | Three-pixel progress strips use the real `InterfaceID.Stats` widget bounds and respect hidden widgets/parents. Optional standard RuneLite hover tooltips show XP, goal, remaining XP and measured rate/ETA. Native widget actions and appearance stay intact. |
| Native context action | Adds a public `MenuAction.RUNELITE` menu entry only to a recognized skill's native “View … guide” menu in `InterfaceID.STATS`. It selects/opens the corresponding goal panel; it does not invoke a game action. Stale character/lifecycle callbacks are discarded. |
| Saved data | Only per-profile goals are persisted, through validated/recoverable `RunewardenDataStore`. Unsupported future payloads remain intact. Session counters are local to the enabled plugin session, retained across ordinary hops and reset on account switches/plugin stop. |

The ETA is a projection at the measured session rate, not a guaranteed completion time. A paused card retains its measured rate and shows a pause label. The denominator includes the configured idle allowance; it does not measure “perfect” action speed. The native tab strips render after the actual Stats interface and never replace the minimap, skills screen or native guide.

## Wiring

Inject `SkillProgressTool`. Register `createPanel()` under “Skills progress”, `hud()` and `skillTab()` with the overlay manager, and HUD widget key `skill-progress`. Propagate `setHudLocked(boolean)`, `start()`, `stop()`, `clear()`, `onGameTick()`, `onStatChanged(StatChanged)`, `onGameStateChanged(GameStateChanged)` and `onMenuEntryAdded(MenuEntryAdded)`. Set `setOpenAction(Runnable)` to select/open this module from its native skill-tab menu action. The scene/model event methods run on the client thread; the open action runs on Swing's event thread.

## Primary API references and checks

- [Native XP Tracker skill-tab menu integration](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/xptracker/XpTrackerPlugin.java): Stats interface filtering, public menu entry creation and `MenuAction.RUNELITE` client callbacks.
- [Native `InterfaceID.Stats` widget IDs](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/gameval/InterfaceID.java), [`Widget` visibility/bounds](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/widgets/Widget.java), and [`Overlay` manual interface hooks](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/ui/overlay/Overlay.java).
- [Native XP/level limits and lookup](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/Experience.java) and [`SkillIconManager`](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/game/SkillIconManager.java).

The pinned source-JAR hash is recorded in `agility-native-coach.md`. Tests verify actual tick arithmetic, warmup/ETA, offline and negative-XP rebasing, pause behavior, all native skills/widget bindings, profile goal persistence/clearing, XP-cap validation, stale native-menu callbacks, future-data retention, native-icon HUD drawing, hidden-widget decoration/tooltip gating, and preservation of drafts in the 206-pixel sidebar. Live client acceptance remains necessary for visual placement across UI layouts.
