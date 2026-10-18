# Activity guidance correction — 0.7.3

Reviewed 17 October 2026. Version 0.7.2 suspended eight complete activity
contexts because their guidance was selected automatically. That boundary was
too broad. Current RuneLite helpers establish that automatic
activation, inventory-based progression, current-state highlights and timers
are not, by themselves, prohibited gameplay automation.

## Restored functionality

All 20 supported activities again use their native objective loops, contextual
guide selection and relevant object/NPC highlights. This restores Wintertodt,
Tempoross, Pest Control, Barbarian Assault, Nightmare Zone, Soul Wars, Castle
Wars and Volcanic Mine alongside the twelve contexts retained in 0.7.2.
The player still performs every game action.

Fight Caves server-clock rotation prediction and the entrance countdown are
restored. Entering the cave can load the predicted NPC spawn-origin reference
markers. These indicate the rotation's spawn locations, rather than computing a
player standing position from live enemies. Inferno and Colosseum player
standing packs remain manually selected. The retained functionality does not
predict an incoming attack, select a prayer or calculate a dynamic standing tile.

## Comparisons checked

| Feature | Established comparison and scope |
| --- | --- |
| Wintertodt fuel loop | [Wintertodt Solo Helper](https://github.com/aquaosrs/wintertodt-solo-helper) describes an overlay appearing on region entry, with relight, chop, heal-pyromancer and fletch highlights. The core [Wintertodt plugin](https://github.com/runelite/runelite/wiki/Wintertodt) also follows current actions and inventory fuel. |
| Tempoross fish/cook/load/pool loop | [Tempoross Solo Helper](https://github.com/RyanTamulevicz/tempoross-solo-helper) describes current-action text, relevant target highlights, progression from inventory/HUD/NPC/object observations and automatic activation when the encounter HUD appears. |
| Pest Control | The core [Pest Control plugin](https://github.com/runelite/runelite/wiki/Pest-Control) provides portal order, participation status and spinner highlights. Runewarden uses observed open portals and nearby spinners. |
| Barbarian Assault | The core [Barbarian Assault plugin](https://github.com/runelite/runelite/wiki/Barbarian-Assault) supplies call timers and teammate health. The current Hub also lists BA Call Highlight and Ba Minigame. Runewarden follows received calls, role supplies and observed trap/item states; it does not make a call or change equipment. |
| Nightmare Zone | The core [Nightmare Zone plugin](https://github.com/runelite/runelite/wiki/Nightmare-Zone) displays points/absorption and warns about expiring potions. Runewarden's supplies reminder reads current absorption. |
| Volcanic Mine | The current Hub lists Volcanic Mine notifications, Ultimate Volcanic Mine and [Volcanic Mine Stability Tracker](https://github.com/hex-agon/volcanic-mine-stability-tracker). Runewarden shows native readings, unknown-vent reminders, boulder highlights and a player-configured exit reminder; it does not infer an optimal vent solution. |
| Soul Wars / Castle Wars | The current Hub lists Soul Wars, Soul Wars Status and Castle Wars Indicators. Runewarden reads displayed match information and the player's fragments, bandages or carried standard, with ordinary objective reminders. Those listings establish accepted tracking examples; they do not prove every Runewarden instruction has received an identical review. |

The [current official Plugin Hub listing](https://runelite.net/plugin-hub/)
was checked directly for these entries, including Wintertodt Solo Helper,
Tempoross Solo Helper, Fight Caves Spawn Predictor and Fight Cave Waves.
Repository descriptions explain behavior; a repository's current branch is not
proof that every line is the exact revision presently distributed by the Hub.

For Fight Caves, the [current Hub descriptor](https://api.github.com/repos/runelite/plugin-hub/contents/plugins/spawnpredictor)
was also checked: it names `damencs/spawn-predictor` at
[`8c5a34d4c88407cda7a6c60d8014018163d97974`](https://github.com/damencs/spawn-predictor/tree/8c5a34d4c88407cda7a6c60d8014018163d97974).
This is stronger evidence than a similar plugin name. Runewarden's attributed
rotation data already records that source revision; the restored feature uses
the same distinction between rotation/spawn references and live player-movement
advice.

## Fixes retained from 0.7.2

The correction does not undo the concrete implementation fixes: no
`java.lang.Runtime`, public API storage for owned HUD overlays, no enabling or
starting another plugin, and default-off Wiki item-source requests with visible
consent and revocation handling. Static monster references, Shift hover,
written guides, manual marker packs and the generic CLICK metronome remain.

[RuneLite's review rules](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
still apply to each implementation, including their note about grandfathered
plugins. These comparisons correct our feature assessment; they are not an
approval of Runewarden. Official Hub review remains the release gate.

## Validation

All 136 production sources compile for Java 11 against the pinned RuneLite
1.13.2 runtime. The combined available suite passes 219 JUnit tests in 31
classes, including fourteen Fight Caves entrance/spawn-boundary tests and twelve
activity-presentation/ownership regressions. All seven Python parser tests pass;
28 bundled datasets and their fingerprints validate.

The full Gradle/Mockito suite could not be rerun because its pinned test
dependencies are unavailable in this workspace. These checks do not establish
Windows execution or live in-game correctness.
