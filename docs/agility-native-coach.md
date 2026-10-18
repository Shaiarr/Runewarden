# Native agility coach

Implemented against the RuneLite **1.13.2** client dependency, with no external network requests or game actions. The native agility datasets retain their BSD notices in source and `com/runewarden/licenses/agility-attribution.txt` in the plugin JAR.

| Capability | Implemented behavior |
| --- | --- |
| Activity HUD | Independently movable, lockable and styled through the `agility` HUD preference; automatic course recognition, XP gained/hour, laps, rolling last/best lap and laps/hour, marks collected/on ground, and laps to the selected level. |
| Native courses | All 21 native variants: Gnome Stronghold, Shayzien basic/advanced, Draynor, Al Kharid, Agility Pyramid, Varrock, Penguin, Barbarian Outpost, Canifis, Ape Atoll, Falador, Colossal Wyrm basic/advanced, Wilderness, Werewolf, Seers' Village, Pollnivneach, Rellekka, Prifddinas and Ardougne. Nineteen use native region detection; Wyrm uses observed course objects/progress. |
| Lap completion | A positive native Agility XP change at an exact native end tile/plane. Wyrm uses a new progress transition to 6 while Wyrm objects are observed. Same-tick/repeated completion events and login state do not create additional laps. |
| Goal estimates | Native per-course XP, including Pyramid level scaling and the Pollnivneach/Rellekka hard-diary totals. The selected goal is level 2–99. |
| Scene assistance | All 269 ordinary obstacle IDs, six portal IDs, three trap IDs with the native Pyramid-region restriction, 22 Sepulchre floor links and six Sepulchre skilling IDs. Current clickboxes are outlined and filled; trap tiles have their own color. |
| Shortcuts | The installed client's full public `AgilityShortcut` catalog, native multiloc/location matching, required level, native hover tooltip and a nearby-shortcut list. Orange means the current boosted Agility level is below the shortcut's level; meeting that level does not assert that every quest/item requirement has been met. |
| Ground objectives | Marks of grace and the Werewolf-course stick are highlighted. Collected marks require both a nearby mark despawn and an inventory quantity increase within two game ticks; a ground expiry or ordinary bank withdrawal alone does not count. |
| Brimhaven arena | Activity XP tracking and the native one-minute target interval, displayed as a radial countdown after an observed hint-arrow target movement. Initial login/entry and a plane-only arrow change do not invent a fresh interval. |
| Persistence | Per RuneScape profile and course; validated/recoverable saves through `RunewardenDataStore`. Totals survive restarting; an interrupted/offline interval cannot become a lap time. Samples are bounded to 30; scene objects to 2,048; marks to 256. Rendering reuses scene collection views. |
| Sidebar | Four narrow sections for activity totals, a level goal, display controls and nearby shortcuts. Tick refreshes update labels rather than recreating inputs. |

Course recognition follows the native region table, which is broader than an exact obstacle boundary. Ordinary obstacle highlights show the currently loaded native geometry; they do not claim an inferred next obstacle or a universal optimal route. The XP/hour denominator is observed game ticks in the recognized activity after the first XP/lap, including idle ticks there; it excludes time outside the activity and offline time. The first completion after entry/re-entry establishes a lap-time baseline. Best/laps-hour use the last 30 complete uninterrupted lap intervals.

This does not implement Brimhaven's weighted path solver, Sepulchre trap timing/runner predictions/floor PBs, Dorgesh-Kaan task routing, Underwater Agility routing, mark-spawn predictions, or per-obstacle action-duration countdowns. Native clickbox coverage for an object is separate from complete activity-specific coaching. No duration has been fabricated for an ordinary obstacle.

## Wiring

Inject `AgilityCoach`, expose `createPanel()` under “Agility coach”, add `hud()` and `scene()` to the overlay manager, register HUD widget `agility`, and propagate `setHudLocked(boolean)`. Call `start()`, `stop()`, `clear()`, `onGameTick()`, `onStatChanged`, `onVarbitChanged`, `onGameStateChanged`, `onItemSpawned` and `onItemDespawned`. Forward every Game/Ground/Wall/Decorative object spawn/despawn through `onTileObject(tile, oldObject, newObject)`. Root plugin integration owns the event subscriptions. Scene/native model methods run on the client thread; sidebar actions are queued with epoch/profile guards.

## Pinned primary sources

Source directory: [RuneLite agility at runelite-parent-1.13.2](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/agility). Shortcut data uses the public [AgilityShortcut class](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/game/AgilityShortcut.java) from that dependency directly.

| Artifact | SHA-256 |
| --- | --- |
| `client-1.13.2-sources.jar` | `86d51d6cb3428af7e9bae3c85b0d82f80b1360609f2bc2cacc37b45cd93d6b47` |
| `Courses.java` | `0000ce6139c8674c7520e07e091f73a6dde511eae5fdadbc13bc2d00e641096c` |
| `Obstacles.java` | `bcba4e88cd3819ec8e26b64567eb478c827342e1bfa2831ab326dcae6b3d320c` |
| `AgilityPlugin.java` | `60755711fa4ea017eca2ed9c87603e7dfa2860c2f65628f019b48152a7267a7b` |
| `AgilityArenaTimer.java` | `34497590e8342f24ee2548df686408c643fcce042fe5c256c91de9a6a092db2b` |

`AgilityCatalogTest` compares every adapted course region/end point/XP and every obstacle set with the actual pinned native classes. Focused tests also cover positive-XP/plane gates, Wyrm event deduplication, account isolation/recovery, future-save preservation, scene seeding/despawns/world views, marks pickup evidence, arena countdown, same-tick refreshes, shortcut requirements and HUD/sidebar rendering. They are deterministic native-event tests; live course sessions remain part of the client acceptance pass.
