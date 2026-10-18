# RuneLite Hub capability baseline

The signed RuneLite 1.13.2 Hub manifest was verified against the certificate bundled in the verified native client. Its metadata, the current official active-install endpoint and the pinned official marker tree are retained separately. The website’s older rendered counts are not mixed into this ranking.

The inventory contains **2,681 listed records**, **2,655 with an available JAR** and **26 without one**. The comparison includes **every available plugin with at least 50,000 active installs: 151 plugins**. The remaining 2,530 inventory records are explicitly unreviewed.

Availability means that the signed manifest has a matching JAR entry; it is not inferred from repository presence, warnings or install counts. The manifest schema contains no hidden/disabled flags. Warning text, build-failure timestamps and unavailability metadata remain visible. The install endpoint also contains legacy IDs, which are excluded unless present in this manifest.

The source review records **447 bounded capability findings**: 79 implemented, 290 missing, 76 partial, 2 unknown. These are findings, not a percentage of all features in existence.

## Limits

- This is a source/documentation comparison, not in-game validation or RuneLite approval.
- Source/test paths and hashes are indexed for Runewarden 0.9.6 under com.runewarden; this namespace reindex is not new test-execution evidence or Hub approval.
- The 151-plugin popularity tier is complete for the recorded threshold and snapshot; every other inventory record remains explicitly unreviewed.
- A README capability baseline does not certify parity with every upstream source option or undocumented edge case.
- A guide, item catalogue, static reference, generic tick, self HUD or native-plugin adapter is not counted as a full specialist implementation.
- Referenced tests are evidence pointers; the audit generator does not execute those tests.
- Real-companion contracts are optional integration evidence, not default plugin-only test coverage; development-launcher tests are excluded.
- Current and future changes require re-review of capability statuses; source hashes identify the inspected snapshot.
- The GitHub marker source pin is independently recorded. The manifest does not expose a source commit that cryptographically binds a README to its built JAR.
- A Hub-listed feature is precedent for investigation, not an automatic approval of a new implementation. No unsupported blanket prohibition is inferred.

## Plugin-only test evidence

This is a plugin-only source export. No development launcher, native client or companion source/binary is included. Normal tests run without a real Shortest Path implementation. The identity-only fixture covers plugin discovery and message ownership, not pathfinding or transport behavior.

The two unchanged real-companion suites under `integration-tests/java` contain 11 tests and require an externally supplied Shortest Path 1.21.3 JAR at the recorded upstream pin. They are optional and are not run by the default test task. Their earlier integration results do not establish a pass for this exported checkout. See `integration-tests/README.md`.

The six tests from `RoutingCompanionStartupTest` and `QuestHelperLauncherBridgeTest` belonged to the separate development launcher. Those suites are absent and are not claimed as plugin-only coverage. The retained popularity snapshot and total of 447 capability findings are unchanged. The combined quest-metadata/Quest Helper planning-link finding is partial because this export supplies no handoff bridge. Neither a retained hash nor this generator establishes a test pass.

## Architecture gaps that must remain visible

- Independent high-detail renderer and base-client resource-pack reskinning
- Multiplayer party transport and synchronized peer-state panels
- Optional WikiSync, public profile, clan/competition and webhook transports
- Native virtual bank layouts and full Inventory Setups interoperability
- Dedicated raid, Gauntlet and specialized puzzle engines; remaining full clue-helper breadth
- General menu-rule editing, custom alert-rule engine, network history/lag analytics and broader container observation

## Complete high-usage tier

| Rank | Plugin | Active installs | Capability findings |
|---:|---|---:|---|
| 1 | [Quest Helper](https://runelite.net/plugin-hub/show/quest-helper) | 586,609 | 2 implemented, 6 missing |
| 2 | [117 HD](https://runelite.net/plugin-hub/show/117hd) | 404,477 | 4 missing |
| 3 | [Sailing](https://runelite.net/plugin-hub/show/sailing) | 391,526 | 2 implemented, 6 missing, 2 partial |
| 4 | [Tile Packs](https://runelite.net/plugin-hub/show/tile-packs) | 375,859 | 2 implemented, 1 missing, 1 partial |
| 5 | [Guardians of the Rift Helper](https://runelite.net/plugin-hub/show/guardians-of-the-rift-helper) | 356,443 | 3 implemented, 3 missing, 2 partial |
| 6 | [Tombs of Amascut](https://runelite.net/plugin-hub/show/tombs-of-amascut) | 326,234 | 3 missing |
| 7 | [WikiSync](https://runelite.net/plugin-hub/show/wikisync) | 321,448 | 2 missing |
| 8 | [Better NPC Highlight](https://runelite.net/plugin-hub/show/better-npc-highlight) | 309,915 | 2 missing, 1 partial |
| 9 | [The Gauntlet](https://runelite.net/plugin-hub/show/the-gauntlet) | 286,063 | 3 missing |
| 10 | [Zulrah Plugin](https://runelite.net/plugin-hub/show/zulrah-helper) | 281,152 | 1 missing, 1 partial |
| 11 | [Mahogany Homes](https://runelite.net/plugin-hub/show/mahogany-homes) | 269,920 | 2 implemented, 1 partial |
| 12 | [Tempoross](https://runelite.net/plugin-hub/show/tempoross) | 261,707 | 5 implemented, 3 missing, 2 partial |
| 13 | [Hub Party Panel](https://runelite.net/plugin-hub/show/party-panel) | 251,661 | 3 missing |
| 14 | [Easy Giants' Foundry](https://runelite.net/plugin-hub/show/easy-giantsfoundry) | 250,776 | 2 implemented, 2 missing, 1 partial |
| 15 | [Banked Experience](https://runelite.net/plugin-hub/show/banked-experience) | 247,956 | 3 implemented, 2 partial |
| 16 | [Mastering Mixology](https://runelite.net/plugin-hub/show/mastering-mixology) | 244,565 | 3 missing, 1 partial |
| 17 | [Bank Tag Layouts](https://runelite.net/plugin-hub/show/bank-tag-layouts) | 242,476 | 4 missing, 1 partial |
| 18 | [Rogues' Den](https://runelite.net/plugin-hub/show/rogues-den) | 242,047 | 1 missing |
| 19 | [Port Tasks](https://runelite.net/plugin-hub/show/port-tasks) | 239,124 | 2 implemented, 1 missing, 1 partial |
| 20 | [Wilderness Player Alarm](https://runelite.net/plugin-hub/show/wilderness-player-alarm) | 235,198 | 2 missing |
| 21 | [Hunter Rumours](https://runelite.net/plugin-hub/show/hunter-rumours) | 235,018 | 2 implemented, 1 partial |
| 22 | [Skills Progress](https://runelite.net/plugin-hub/show/skills-tab-progress-bars) | 233,844 | 3 implemented, 4 missing |
| 23 | [Fight Cave Waves](https://runelite.net/plugin-hub/show/fight-cave-waves) | 232,014 | 1 missing, 1 partial |
| 24 | [Inventory Setups](https://runelite.net/plugin-hub/show/inventory-setups) | 231,332 | 5 implemented, 8 missing, 2 partial |
| 25 | [Totem Fletching](https://runelite.net/plugin-hub/show/totem-fletching) | 217,068 | 3 missing |
| 26 | [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) | 211,920 | 2 missing, 2 partial |
| 27 | [Fight Caves Spawn Predictor](https://runelite.net/plugin-hub/show/spawnpredictor) | 210,322 | 2 implemented, 1 partial |
| 28 | [Equipment Inspector](https://runelite.net/plugin-hub/show/equipment-inspector) | 210,028 | 2 implemented |
| 29 | [Radius Markers](https://runelite.net/plugin-hub/show/radius-markers) | 199,117 | 2 missing |
| 30 | [Barrows Doors Highlighter](https://runelite.net/plugin-hub/show/barrows-door-highlighter) | 197,836 | 1 missing |
| 31 | [Customizable XP drops](https://runelite.net/plugin-hub/show/customizable-xp-drops) | 195,590 | 4 missing |
| 32 | [House Thieving Varlamore](https://runelite.net/plugin-hub/show/varlamore-house-thieving) | 187,855 | 3 implemented, 3 missing, 1 partial |
| 33 | [Wilderness Lines](https://runelite.net/plugin-hub/show/wilderness-multi-lines) | 185,802 | 2 missing |
| 34 | [Custom Menu Swaps](https://runelite.net/plugin-hub/show/hotkeyable-menu-swaps) | 169,370 | 3 missing |
| 35 | [Visual Metronome](https://runelite.net/plugin-hub/show/visual-metronome) | 168,787 | 1 implemented, 1 missing, 1 partial |
| 36 | [CoX Timers & Additions](https://runelite.net/plugin-hub/show/cox-additions) | 163,216 | 2 missing, 1 partial |
| 37 | [Kitten Tracker](https://runelite.net/plugin-hub/show/kitten-tracker) | 158,353 | 2 missing |
| 38 | [Spell Reminder](https://runelite.net/plugin-hub/show/thrall-helper) | 156,072 | 2 missing, 1 partial |
| 39 | [Barracuda Trials Pathfinder](https://runelite.net/plugin-hub/show/barracuda-trials-pathfinder) | 152,713 | 3 missing, 1 partial |
| 40 | [Loot Lookup](https://runelite.net/plugin-hub/show/loot-lookup) | 149,240 | 3 missing |
| 41 | [Item Charges Improved](https://runelite.net/plugin-hub/show/tictac7x-charges) | 147,542 | 2 implemented, 1 missing, 1 partial |
| 42 | [Chompy Hunter](https://runelite.net/plugin-hub/show/chompy-hunter) | 142,511 | 2 missing |
| 43 | [Ping Graph](https://runelite.net/plugin-hub/show/ping-grapher) | 140,519 | 2 missing |
| 44 | [Resource packs](https://runelite.net/plugin-hub/show/resource-packs) | 140,319 | 2 missing |
| 45 | [Hunllef Helper](https://runelite.net/plugin-hub/show/hunllef-helper) | 136,969 | 2 missing |
| 46 | [Ba Minigame](https://runelite.net/plugin-hub/show/ba-minigame) | 136,113 | 2 missing, 1 partial |
| 47 | [Wasted Bank Space](https://runelite.net/plugin-hub/show/wasted-bank-space) | 135,438 | 2 missing |
| 48 | [Runecrafting Utilities](https://runelite.net/plugin-hub/show/easy-empty) | 131,332 | 2 missing |
| 49 | [OSRS TCG](https://runelite.net/plugin-hub/show/osrs-tcg) | 130,100 | 1 missing |
| 50 | [Fortis Colosseum](https://runelite.net/plugin-hub/show/fortis-colosseum) | 125,904 | 1 missing, 1 partial |
| 51 | [C Engineer: Completed](https://runelite.net/plugin-hub/show/c-engineer-completed) | 123,900 | 1 implemented, 2 missing, 1 partial |
| 52 | [Large Logout](https://runelite.net/plugin-hub/show/large-logout) | 123,644 | 1 missing |
| 53 | [Monster Hp Percentage](https://runelite.net/plugin-hub/show/monster-hp-percentage) | 123,005 | 2 missing |
| 54 | [Better Teleport Menu](https://runelite.net/plugin-hub/show/better-teleport-menu) | 121,973 | 3 missing |
| 55 | [Crab Stun Timers](https://runelite.net/plugin-hub/show/crab-stun-timers) | 121,897 | 1 missing |
| 56 | [Tree Despawn Timer](https://runelite.net/plugin-hub/show/tree-despawn-timer) | 119,518 | 1 implemented, 1 missing, 1 partial |
| 57 | [Cox Additions](https://runelite.net/plugin-hub/show/cox-qol) | 116,872 | 4 missing |
| 58 | [Improved Tile Indicators](https://runelite.net/plugin-hub/show/improved-tile-indicators) | 116,210 | 1 missing |
| 59 | [Plank Sack](https://runelite.net/plugin-hub/show/plank-sack) | 115,810 | 3 implemented, 1 missing, 1 partial |
| 60 | [ToB QoL](https://runelite.net/plugin-hub/show/tobqol) | 115,396 | 1 unknown |
| 61 | [Bossing Info](https://runelite.net/plugin-hub/show/kph-tracker) | 112,052 | 1 implemented, 1 missing, 1 partial |
| 62 | [Crab Solver](https://runelite.net/plugin-hub/show/crab-solver) | 111,441 | 1 missing |
| 63 | [One Click Summer Garden](https://runelite.net/plugin-hub/show/one-click-summer-garden) | 109,567 | 2 missing |
| 64 | [AttackTimer](https://runelite.net/plugin-hub/show/attacktimer) | 109,318 | 2 missing |
| 65 | [Stealing Artefacts](https://runelite.net/plugin-hub/show/stealing-artefacts) | 107,626 | 2 missing |
| 66 | [DT2 Boss Utilities](https://runelite.net/plugin-hub/show/dt2-mistake-tracker) | 107,089 | 2 missing |
| 67 | [RuneProfile](https://runelite.net/plugin-hub/show/runeprofile) | 104,844 | 2 missing |
| 68 | [Max Hit](https://runelite.net/plugin-hub/show/maxhitplugin) | 103,951 | 1 implemented, 2 partial |
| 69 | [Larva Tracker](https://runelite.net/plugin-hub/show/demonic-larva-tracker) | 99,832 | 2 missing |
| 70 | [Star Miners](https://runelite.net/plugin-hub/show/star-calling-assist) | 99,304 | 2 missing |
| 71 | [Construction QOL](https://runelite.net/plugin-hub/show/zom-con-qol) | 98,719 | 1 missing |
| 72 | [Zalcano](https://runelite.net/plugin-hub/show/zalcano) | 95,174 | 3 missing |
| 73 | [Fight Cave Helper](https://runelite.net/plugin-hub/show/fight-cave-helper) | 93,444 | 1 missing, 1 partial |
| 74 | [Gauntlet Minimap](https://runelite.net/plugin-hub/show/gauntlet-minimap) | 93,044 | 2 missing |
| 75 | [Motherlode Mine Improved](https://runelite.net/plugin-hub/show/tictac7x-motherlode) | 91,858 | 3 missing, 1 partial |
| 76 | [Clan Events](https://runelite.net/plugin-hub/show/elysiumevents-plugin) | 91,664 | 2 missing |
| 77 | [Detached Camera](https://runelite.net/plugin-hub/show/detached-camera) | 91,276 | 1 missing |
| 78 | [Improved Tears Interface](https://runelite.net/plugin-hub/show/improved-tears-of-guthix-interface) | 90,821 | 1 missing |
| 79 | [Optimal Quest Guide](https://runelite.net/plugin-hub/show/optimal-quest-guide) | 90,738 | 2 implemented, 2 partial |
| 80 | [BA Call Highlight](https://runelite.net/plugin-hub/show/ba-call-highlight) | 90,661 | 1 missing, 1 partial |
| 81 | [Projectile Override](https://runelite.net/plugin-hub/show/projectile-override) | 86,588 | 2 missing |
| 82 | [Collection Log Luck](https://runelite.net/plugin-hub/show/collection-log-luck) | 86,417 | 3 missing, 1 partial |
| 83 | [Target True Tile](https://runelite.net/plugin-hub/show/target-true-tile) | 86,321 | 2 missing, 1 partial |
| 84 | [Annoyance Mute](https://runelite.net/plugin-hub/show/annoyance-mute) | 86,215 | 1 missing |
| 85 | [Clue Juggling Timer](https://runelite.net/plugin-hub/show/clue-scroll-juggling) | 85,771 | 5 implemented, 2 missing, 1 partial |
| 86 | [Wise Old Man](https://runelite.net/plugin-hub/show/wom-utils) | 85,319 | 3 missing |
| 87 | [Runedoku](https://runelite.net/plugin-hub/show/runedoku-helper) | 84,337 | 3 missing |
| 88 | [Ground Object Hider](https://runelite.net/plugin-hub/show/object-hider) | 84,041 | 2 missing |
| 89 | [1 Tick Flick](https://runelite.net/plugin-hub/show/one-tick-flick) | 82,953 | 1 implemented, 2 missing |
| 90 | [Easy Blast Furnace](https://runelite.net/plugin-hub/show/easy-blastfurnace) | 82,469 | 2 missing, 1 partial |
| 91 | [Party Defence Tracker](https://runelite.net/plugin-hub/show/party-defence-tracker) | 82,429 | 2 missing, 1 partial |
| 92 | [Time Tracking Reminder](https://runelite.net/plugin-hub/show/time-tracking-reminder) | 81,035 | 2 implemented, 1 partial |
| 93 | [Vardorvis Projectiles](https://runelite.net/plugin-hub/show/vardorvis-projectiles) | 78,535 | 1 missing |
| 94 | [Flipping Utilities](https://runelite.net/plugin-hub/show/flipping-utilities) | 77,007 | 3 missing |
| 95 | [Herbi AFK](https://runelite.net/plugin-hub/show/herbi-afk) | 76,495 | 2 missing |
| 96 | [Monster Menu HP](https://runelite.net/plugin-hub/show/menuhp) | 75,623 | 1 missing |
| 97 | [Razor Kebbit Tracking](https://runelite.net/plugin-hub/show/razor-kebbit-tracking) | 75,225 | 1 missing |
| 98 | [Looting Bag](https://runelite.net/plugin-hub/show/looting-bag-value) | 75,198 | 1 implemented, 2 missing, 1 partial |
| 99 | [Emote Clue Items](https://runelite.net/plugin-hub/show/emote-clue-items) | 75,129 | 3 implemented, 3 missing, 1 partial |
| 100 | [Dink for Discord](https://runelite.net/plugin-hub/show/dink) | 74,343 | 3 missing |
| 101 | [Slayer Assistant](https://runelite.net/plugin-hub/show/slayer-assistant) | 73,283 | 2 implemented, 1 partial |
| 102 | [Left Click Dropper](https://runelite.net/plugin-hub/show/zom-leftclick-dropper) | 72,806 | 1 missing |
| 103 | [Action Progress](https://runelite.net/plugin-hub/show/action-progress) | 71,903 | 3 implemented, 4 missing, 2 partial |
| 104 | [Teleport Maps](https://runelite.net/plugin-hub/show/spirit-tree-map) | 71,346 | 2 missing |
| 105 | [Tithe Farm Improved](https://runelite.net/plugin-hub/show/tictac7x-tithe) | 71,253 | 3 missing, 1 partial |
| 106 | [RaidReloader](https://runelite.net/plugin-hub/show/raid-reloader) | 70,858 | 1 missing |
| 107 | [Mastering Mixology Strategy](https://runelite.net/plugin-hub/show/mastering-mixology-strategy) | 70,101 | 1 missing, 1 partial |
| 108 | [Visual Ticks](https://runelite.net/plugin-hub/show/visual-ticks) | 69,885 | 1 implemented, 2 missing, 1 partial |
| 109 | [Consumable Cooldowns](https://runelite.net/plugin-hub/show/consumable-cooldowns) | 69,419 | 2 missing, 1 partial |
| 110 | [Loot Logger](https://runelite.net/plugin-hub/show/loot-logger) | 69,417 | 1 implemented, 1 missing, 1 partial |
| 111 | [Nyloer](https://runelite.net/plugin-hub/show/nyloer) | 69,082 | 1 missing |
| 112 | [PvP Performance Tracker](https://runelite.net/plugin-hub/show/pvp-performance-tracker) | 68,655 | 2 missing |
| 113 | [Barrows Potential](https://runelite.net/plugin-hub/show/barrows-potential) | 68,219 | 1 missing |
| 114 | [Watchdog (Custom Alerts)](https://runelite.net/plugin-hub/show/watchdog) | 67,981 | 4 missing, 2 partial |
| 115 | [Pyramid Plunder Counter](https://runelite.net/plugin-hub/show/pyramid-plunder-counter) | 67,582 | 2 missing, 1 partial |
| 116 | [Weapon Charges (WIP)](https://runelite.net/plugin-hub/show/weapon-charges-2) | 67,122 | 2 missing, 1 partial |
| 117 | [Gauntlet Chest Popup](https://runelite.net/plugin-hub/show/gauntlet-chest-popup) | 66,860 | 1 implemented, 1 missing, 1 partial |
| 118 | [Forestry CC](https://runelite.net/plugin-hub/show/forestrycc) | 66,376 | 2 missing |
| 119 | [Door Kicker](https://runelite.net/plugin-hub/show/door-kicker) | 66,113 | 1 unknown |
| 120 | [RuneWatch](https://runelite.net/plugin-hub/show/runewatch) | 65,825 | 2 missing |
| 121 | [Menu Swapper Extended](https://runelite.net/plugin-hub/show/menu-swapper-extended) | 65,068 | 2 missing |
| 122 | [Not Empty](https://runelite.net/plugin-hub/show/not-empty) | 64,336 | 1 missing |
| 123 | [Gemstone Crab](https://runelite.net/plugin-hub/show/gemstone-crab-timer) | 64,110 | 2 missing, 1 partial |
| 124 | [Guardians of the Rift Optimizer](https://runelite.net/plugin-hub/show/guardians-of-the-rift-optimizer) | 63,515 | 3 missing, 1 partial |
| 125 | [Gauntlet Map](https://runelite.net/plugin-hub/show/gauntlet-map) | 63,304 | 2 missing |
| 126 | [Tree Count](https://runelite.net/plugin-hub/show/treecount) | 62,847 | 1 missing |
| 127 | [Clue steps](https://runelite.net/plugin-hub/show/clue-steps) | 62,475 | 2 missing |
| 128 | [Tasks Tracker](https://runelite.net/plugin-hub/show/tasks-tracker) | 62,417 | 2 missing |
| 129 | [Bank Memory](https://runelite.net/plugin-hub/show/bank-memory) | 61,859 | 4 implemented, 3 missing, 1 partial |
| 130 | [Fish Barrel](https://runelite.net/plugin-hub/show/fish-barrel) | 60,924 | 2 implemented, 1 missing, 1 partial |
| 131 | [CoX Light Colors](https://runelite.net/plugin-hub/show/cox-light-colors) | 59,901 | 1 missing |
| 132 | [Corner Tile Indicators](https://runelite.net/plugin-hub/show/corner-tile-indicators) | 59,544 | 1 missing |
| 133 | [Player Outline](https://runelite.net/plugin-hub/show/player-outline) | 58,772 | 1 missing |
| 134 | [Pickpocket Helper](https://runelite.net/plugin-hub/show/pickpocket-helper) | 57,603 | 2 missing, 1 partial |
| 135 | [Delve Calculator](https://runelite.net/plugin-hub/show/delve-calculator) | 57,459 | 2 missing |
| 136 | [Odablock Sounds](https://runelite.net/plugin-hub/show/odablock) | 57,348 | 2 missing, 1 partial |
| 137 | [Instant Inventory](https://runelite.net/plugin-hub/show/instant-inventory) | 56,975 | 2 missing |
| 138 | [Did I Compost?](https://runelite.net/plugin-hub/show/did-i-compost) | 56,588 | 1 implemented, 1 partial |
| 139 | [Camera Smoothing](https://runelite.net/plugin-hub/show/camera-smoothing) | 56,419 | 2 missing |
| 140 | [Raid Data Tracker](https://runelite.net/plugin-hub/show/raid-data-tracker) | 56,333 | 2 missing, 1 partial |
| 141 | [Shooting Star Info](https://runelite.net/plugin-hub/show/startierindicator) | 56,165 | 2 missing, 1 partial |
| 142 | [Tzhaar/Colo Hp Tracker](https://runelite.net/plugin-hub/show/tzhaar-hp-tracker) | 55,755 | 2 missing |
| 143 | [Fashionscape](https://runelite.net/plugin-hub/show/fashionscape) | 54,491 | 3 missing |
| 144 | [Wintertodt Notifications](https://runelite.net/plugin-hub/show/wintertodt-notifications) | 53,118 | 1 missing, 2 partial |
| 145 | [Vineyard Helper](https://runelite.net/plugin-hub/show/vineyard-helper) | 53,068 | 1 missing |
| 146 | [Deep Sea Trawling](https://runelite.net/plugin-hub/show/deepseatrawling) | 52,616 | 3 missing |
| 147 | [Fishing Notifier](https://runelite.net/plugin-hub/show/fishing-notifier) | 52,194 | 1 missing, 1 partial |
| 148 | [Damage Counter](https://runelite.net/plugin-hub/show/damage-counter) | 51,085 | 2 missing |
| 149 | [Companion Pet Plugin](https://runelite.net/plugin-hub/show/companion-pet) | 50,769 | 2 missing |
| 150 | [Gnome Restaurant](https://runelite.net/plugin-hub/show/gnome-restaurant) | 50,333 | 3 missing |
| 151 | [Tick Tracker](https://runelite.net/plugin-hub/show/ticktracker) | 50,324 | 2 missing, 1 partial |

## Selected owned native extensions

These separate source checks keep the owned World Hopper, Agility, floor-loot and farming/gathering mechanisms visible. They are excluded from Hub feature counts and are not an exhaustive built-in RuneLite audit.

### Integrated World Hopper

[Pinned native source](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/worldhopper).

- Official world list and native population updates; country and US-coast flags, search, membership/region/type filters, character favourites and six reversible sorts.
- Explicit row/return/previous/next actions, configurable quick-hop keys, namespaced command and friends/chat/clan Hop-to entries with fresh entry checks and native confirmations.
- Special-world approval invalidation on changed world types, profile/connection-generation guards and a bounded worker for TCP latency plus optional native retransmission HUD.

Remaining boundaries: Manual hops only; native entry restrictions and confirmations remain authoritative, and headless cases do not establish live world switching; Uses ::rwhop, unbound default keys, all saved quick-hop filters and character favourites instead of identical native preferences; TCP-connect probes differ from native ICMP-first measurements; three-second probe spacing/current priority after six seconds is not a response-time guarantee, OS DNS can block the single worker, and network history/lag analytics remain absent.

Inspected source/tests: `WorldHopperTool.java`, `WorldHopperPanel.java`, `WorldHopperPingOverlay.java`, `RunewardenPlugin.java`, `RunewardenConfig.java`, `HudPreferences.java`, `HudSettingsTool.java`, `WorldHopperToolTest.java`, `WorldHopperPanelTest.java`, `WorldHopperWorkerLifecycleTest.java`, `RunewardenPluginIntegrationTest.java`, `HudCustomizationTest.java`.

### Native Agility coaching

[Pinned native source](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/agility).

- Native course, obstacle and shortcut observations with exact-XP/end-position lap confirmation.
- Measured course sessions, level goals, mark/stick observations and Brimhaven target-change estimates.

Remaining boundaries: No weighted Brimhaven route solver, full Sepulchre trap/runner engine or Dorgesh-Kaan/Underwater task routes; Broad native region recognition is not an exact course-boundary or universal traversal engine.

Inspected source/tests: `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`, `AgilityCatalogTest.java`, `AgilityCoachTest.java`, `AgilityTrainingSessionTest.java`.

### Contextual ground loot and native loot beams

[Pinned native source](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/grounditems).

- Native identity/ownership/deadline-aware floor-item highlights and guide/manual targets.
- Five configurable value-band beams using native models, bounded object caches and actual despawn/public rings.

Remaining boundaries: Floor-item eligibility is not path reachability and does not add earned loot; This is not every native Ground Items menu, rule or display feature.

Inspected source/tests: `GroundLootTool.java`, `GroundLootOverlay.java`, `GroundLootBeams.java`, `GroundLootTier.java`, `GroundLootToolTest.java`, `GroundLootBeamsTest.java`.

### Gathering, harvest and observed farming HUDs

[Pinned native source](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/timetracking).

- Observed respawn windows, supported estimated tree lifetimes and text/radial choices.
- Native Time Tracking harvest summaries, crop-specific icons, cycle-aligned patch growth estimates and confirmed compost evidence.

Remaining boundaries: Unknown/random resource lifetimes and unobserved crop states remain unknown; disease and growth skips can alter estimates; Native Time Tracking integration is retained; full original tracker replacement is not claimed.

Inspected source/tests: `SkillingCoach.java`, `NodeTimerRules.java`, `NodeTimerOverlay.java`, `RadialTimer.java`, `HarvestClockTool.java`, `HarvestClockOverlay.java`, `HarvestTimerWindows.java`, `FarmPatchCatalog.java`, `FarmGrowthTiming.java`, `FarmPatchPlanner.java`, `FarmPatchScene.java`, `FarmCompostEvidence.java`, `FarmPatchOverlay.java`, `GatheringRadialTimerTest.java`, `HarvestRadialHudTest.java`, `FarmPatchHudTest.java`, `FarmPatchPlannerTest.java`, `FarmGrowthTimingTest.java`.


## Feature-by-feature findings

### 1. Quest Helper

Active installs: 586,609. [Pinned upstream documentation](https://raw.githubusercontent.com/Zoinkwiz/quest-helper/5f791937a99a676f23c02aa48c4e4d5b50e7edad/README.md).

- **implemented** — Quest search and completed-quest filtering.
- **implemented** — Quest requirement metadata and optimal-order planning.
- **missing** — Own quest NPC and object interaction targets.
- **missing** — Own automatic quest-stage and conditional branch following.
- **missing** — Own puzzle-specific answers and visual solutions.
- **missing** — Own quest dialogue assistance.
- **missing** — Own quest stop routing and minimap/world-map guidance.
- **missing** — Own dedicated adaptations of every upstream quest puzzle and stateful helper.

Runewarden intentionally removed its quest walkthrough runtime in 0.9.3. Quest walkthroughs require the separately installed external Quest Helper. This plugin-only export contains no companion source, binary or development-launcher handoff bridge; automatic quest handoff is not provided here. The recorded Quest Helper 4.18.0 source pin remains reference provenance, not a bundled helper or an executed handoff claim. Companion functionality is not counted as an independently implemented Runewarden helper. Account quest state, requirements and optimal-order planning remain.

Inspected Runewarden files: `RunewardenGuides.java`, `GuideCatalog.java`, `QuestOrder.java`.

### 2. 117 HD

Active installs: 404,477. [Pinned upstream documentation](https://raw.githubusercontent.com/117HD/RLHD/c8bff44a84ff903721750295674be48976d30308/README.md).

- **missing** — Enhanced 3D renderer with dynamic lighting and shadows.
- **missing** — Material, terrain and water texture enhancements.
- **missing** — GPU antialiasing, draw-distance and renderer performance controls.
- **missing** — Renderer-level colour accessibility and seasonal environments.

Own HUD branding does not implement a graphics engine. HdPluginConfig at the Hub-pinned commit was inspected, beyond the brief README.

Inspected Runewarden files: `HudStyle.java`, `HudSettingsTool.java`, `RunewardenPlugin.java`.

### 3. Sailing

Active installs: 391,526. [Pinned upstream documentation](https://raw.githubusercontent.com/LlemonDuck/sailing/af987f3be93bf0ca666af3d1f111cbf91e6a4d0d/README.md).

- **implemented** — Highlight nearby native rapids and the own-boat trim action.
- **implemented** — Display observed boat speed, heading, wind and hull facts.
- **partial** — Barracuda lost-supply and current trial objective highlights.
- **partial** — Native courier cargo counts and destination guidance.
- **missing** — Crew overhead-text filtering and boat-visibility modes.
- **missing** — Helm-only sail and cargo-hold priority menu conveniences.
- **missing** — Sea-charting location requirements and charted-state highlights.
- **missing** — Weather station and current-duck puzzle solvers.
- **missing** — Hide Gwenith portal transitions.
- **missing** — Destination labels directly on native cargo item widgets.

SailingCoach implements bounded live navigation, trimming, cargo and trial facts. It does not claim the upstream charting solvers, menu changes or closed-hold memory.

Inspected Runewarden files: `SailingCoach.java`, `SailingCoachRules.java`, `SailingCoachTasks.java`.

### 4. Tile Packs

Active installs: 375,859. [Pinned upstream documentation](https://raw.githubusercontent.com/TrevorMDev/tile-packs/8091ee842642c34fb88bb79c61e104500e91bd96/README.md).

- **implemented** — Search category-based authored marker packs.
- **implemented** — Manually load and clear numbered tile markers.
- **partial** — Persistent pack visibility and imported custom pack editing.
- **missing** — Full upstream pack submission, favourites and content-management workflow.

Inspected Runewarden files: `TileMarkersTool.java`, `GuideMarkerOverlay.java`, `RunewardenSightOverlay.java`.

### 5. Guardians of the Rift Helper

Active installs: 356,443. [Pinned upstream documentation](https://raw.githubusercontent.com/DatBear/Guardians-of-the-Rift-Helper/cdee9d0d5cf9aa7782bfe8f5c36c20a2b127d11c/README.md).

- **implemented** — Outline the currently active elemental and catalytic guardians.
- **implemented** — Native rune icons and requirement colours above active guardians.
- **implemented** — Supply-conditioned Great Guardian, cell-table and free-slot essence-pile highlights.
- **partial** — Witnessed-start altar and portal expiry estimates.
- **partial** — Current contribution and resource activity panel.
- **missing** — Complete time-since-portal and outside-round next-game or reward-point panel parity.
- **missing** — All pre-start, fragment-threshold, first-altar and portal desktop notifications.
- **missing** — Hide impossible left-click menu choices.

MinigameFieldEvents maps all twelve native runes and requires the actual active animation. Its 33/43-tick estimates start only from witnessed transitions; initial and expired ages stay unknown. Talisman bypass, complete cell placement and quest-unlock coverage remain partial; no tick-perfect timer claim is made.

Inspected Runewarden files: `MinigameFieldEvents.java`, `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameCoach.java`, `MinigameSceneOverlay.java`, `MinigameHudOverlay.java`.

### 6. Tombs of Amascut

Active installs: 326,234. [Pinned upstream documentation](https://raw.githubusercontent.com/LlemonDuck/tombs-of-amascut/831c39b9d60f40965d221fe5ac6b4d19d61c97f4/README.md).

- **missing** — Room-specific puzzle solvers and raid overlays.
- **missing** — Native salt cooldown, raid HP and deposit-pot interface aids.
- **missing** — Room mechanics, encounter counters and detailed raid progress.

Written raid guides and manual markers are available, but do not implement these upstream mechanisms.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 7. WikiSync

Active installs: 321,448. [Pinned upstream documentation](https://raw.githubusercontent.com/weirdgloop/WikiSync/1ff4ea0653395b552844d658d3668815d82e0a79/README.md).

- **missing** — Opt-in public Wiki account-state uploads.
- **missing** — Wiki-server-selected variable synchronization.

The plugin reads local account facts, but does not publish a WikiSync account profile. The abandoned Runewarden website is a separate architectural decision.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 8. Better NPC Highlight

Active installs: 309,915. [Pinned upstream documentation](https://raw.githubusercontent.com/riktenx/better-npc-highlight/8a2b92b35b34ec71aaad1e4c294033d840924cad/README.md).

- **partial** — Context-bound minigame NPC highlights.
- **missing** — General NPC name/ID rule editor with nine drawing styles.
- **missing** — Per-rule widths, colours and death exclusions.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 9. The Gauntlet

Active installs: 286,063. [Pinned upstream documentation](https://raw.githubusercontent.com/LlemonDuck/the-gauntlet/bf0246abf6dc04ce5541264c10e663536f9864c2/README.md).

- **missing** — Resource-node outlines, icons and gathered-resource counters.
- **missing** — Preparation and full-run timers.
- **missing** — Dedicated Gauntlet NPC and preparation overlays.

Written Gauntlet lessons and manual marker packs are available, but do not implement a live prep helper.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 10. Zulrah Plugin

Active installs: 281,152. [Pinned upstream documentation](https://raw.githubusercontent.com/while-loop/runelite-plugins/d1d24f0a3dbab15a7f0f6fcae1ad9f0c2e6186f4/README.md).

- **partial** — Manual boss phase and marker selection.
- **missing** — Full manual Zulrah rotation diagram and rotation-elimination interface.

The upstream explicitly requires manual input rather than detecting rotations from game state.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 11. Mahogany Homes

Active installs: 269,920. [Pinned upstream documentation](https://raw.githubusercontent.com/TheStonedTurtle/Mahogany-Homes/7ec419eed23dbd8f3970c40bbe0b03d5b951a0dd/README.md).

- **implemented** — Track the assigned client, location and furniture stage.
- **implemented** — Activity supplies and route destination.
- **partial** — Complete per-contract statistics and every upstream visual setting.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 12. Tempoross

Active installs: 261,707. [Pinned upstream documentation](https://raw.githubusercontent.com/nmlynch94/runelite-external-plugins/96ddee1178127c982fd94d59fbc6fdc56ab6a308/README.md).

- **implemented** — Highlight the native double fishing spot.
- **implemented** — Distinct fire, forming-cloud and spreading-shadow highlights.
- **implemented** — Current tethered and broken-mast colours during an incoming-wave window.
- **implemented** — Independent off, radial, seconds and tick display choices for event timers.
- **implemented** — Calculate carried-fish damage with long arithmetic.
- **partial** — Witnessed-start double-spot, fire-stage and wave countdowns.
- **partial** — Current native phase, fish preparation and spirit-pool guidance.
- **missing** — Complete configurable double-spot, wave, fire, storm-threshold and vulnerability desktop notifications.
- **missing** — Recurring fire-cycle coverage and full phase history.
- **missing** — Reward-point infobox parity outside the instance.

MinigameFieldEvents uses native identities/messages, explicitly estimated random double-spot lifetime and a bounded 7.8-second wave window that expires even if impact chat is absent. Unknown initial ages are never filled with full timers.

Inspected Runewarden files: `MinigameFieldEvents.java`, `MinigameActivityCatalog.java`, `MinigameCoach.java`, `MinigameSceneOverlay.java`, `MinigameHudOverlay.java`.

### 13. Hub Party Panel

Active installs: 251,661. [Pinned upstream documentation](https://raw.githubusercontent.com/TheStonedTurtle/party-panel/3dc0ead669562bbbd05a0999f9c8604f2500683f/README.md).

- **missing** — Live party-member inventory and worn-equipment panels.
- **missing** — Party-member active-prayer and skill displays.
- **missing** — Party transport and peer synchronization.

A self equipment HUD and shared local activity session do not implement multiplayer party transport.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 14. Easy Giants' Foundry

Active installs: 250,776. [Pinned upstream documentation](https://raw.githubusercontent.com/Toofifty/easy-giantsfoundry/9324c7f55cba4c0fc5245a7bc85f3db521a8cceb/README.md).

- **implemented** — Choose source-derived optimal moulds for unlocked pieces.
- **implemented** — Display crucible alloy composition and score.
- **partial** — Temperature and next-tool guidance.
- **missing** — All per-tool actions remaining and bonus-progress visual cues.
- **missing** — Complete specialist colour and notification controls.

Guide selection reconciles loaded alloy, native mould state and carried/stored preforms. Native heat/tool objectives advance the selected guide; full sword progress remains hand-in until witnessed reward acceptance confirms completion. These guide lifecycle improvements do not establish complete specialist display parity or complete subjective learning milestones.

Inspected Runewarden files: `MinigameFoundry.java`, `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`.

### 15. Banked Experience

Active installs: 247,956. [Pinned upstream documentation](https://raw.githubusercontent.com/TheStonedTurtle/banked-experience/983d5e04220507f3fb63f76ff0b7f83d0ed32e96/README.md).

- **implemented** — Banked-resource XP allocation and level requirements.
- **implemented** — Secondary-material limits without counting ingredients twice.
- **implemented** — Optional production-output chains.
- **partial** — Per-method overrides across all bankable training materials.
- **partial** — Seed-vault, inventory and looting-bag inclusion.

The XP calculator consumes its bank snapshot; the ownership ledger supports more storage types, but that alone does not prove all containers feed the XP calculation.

Inspected Runewarden files: `LoadoutPlan.java`, `LoadoutReadiness.java`, `LoadoutReadinessTool.java`, `GuideBankFilter.java`, `BankedExperience.java`, `BankedExperienceCalculator.java`, `OwnershipLedger.java`, `BankMemoryTool.java`, `BankMemorySnapshot.java`, `BankMemoryDiff.java`.

### 16. Mastering Mixology

Active installs: 244,565. [Pinned upstream documentation](https://raw.githubusercontent.com/hex-agon/mastering-mixology/10f4ceda7fb044f006d0aecc449afcf2339acfa0/README.md).

- **partial** — Current potion recipe, order and station guidance.
- **missing** — Full configurable order-selection strategies.
- **missing** — Digweed spawn highlighting and notifications.
- **missing** — Quick-action timing and widget augmentation.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 17. Bank Tag Layouts

Active installs: 242,476. [Pinned upstream documentation](https://raw.githubusercontent.com/geheur/bank-tag-custom-layouts/d26417d9c6b47f74cdbb685241987ddfd56a7706/README.md).

- **partial** — Save and compare a target inventory slot layout.
- **missing** — Drag-and-drop virtual bank-tag layouts.
- **missing** — Layout gaps, duplicate items and missing-item placeholders.
- **missing** — Bank-tag-layout import/export compatibility.
- **missing** — Generate and persist a bank-tab layout from worn gear and inventory.

bankSearch.layoutBank refreshes a filter. It does not implement draggable Bank Tag Layouts.

Inspected Runewarden files: `LoadoutPlan.java`, `LoadoutReadiness.java`, `LoadoutReadinessTool.java`, `GuideBankFilter.java`, `BankedExperience.java`, `BankedExperienceCalculator.java`, `OwnershipLedger.java`, `BankMemoryTool.java`, `BankMemorySnapshot.java`, `BankMemoryDiff.java`.

### 18. Rogues' Den

Active installs: 242,047. [Pinned upstream documentation](https://raw.githubusercontent.com/nightfirecat/plugin-hub-plugins/3ece9e0401f5ebac9da2762015449d5e68bb0bfc/README.md).

- **missing** — State-aware maze obstacle and optimal-tile highlights.

Written training material does not supply a complete live maze traversal helper.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 19. Port Tasks

Active installs: 239,124. [Pinned upstream documentation](https://raw.githubusercontent.com/coopermor/port-tasks/0e13530c77b8c599db9d880330022920759d74c1/README.md).

- **implemented** — Track native active courier tasks with collection and delivery counters.
- **implemented** — Highlight the relevant native task ledger.
- **partial** — Destination markers on the world map with explicit travel handoff.
- **missing** — Actual in-world sea routes between task ports.

SailingCoachTasks reads five current delivery DB slots and 30 native ports. Offered tasks, bounty tasks and rewards are not implemented; bounty support is also advertised as future work by the pinned upstream README.

Inspected Runewarden files: `SailingCoach.java`, `SailingCoachRules.java`, `SailingCoachTasks.java`.

### 20. Wilderness Player Alarm

Active installs: 235,198. [Pinned upstream documentation](https://raw.githubusercontent.com/adhansen/plugin-repo/cbddd45029e84197c9d485b140d2a16af77cdef2/README.md).

- **missing** — Nearby-player wilderness screen alarm.
- **missing** — Threat-display and alert configuration.

A generic low-health alert is not another-player detection.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 21. Hunter Rumours

Active installs: 235,018. [Pinned upstream documentation](https://raw.githubusercontent.com/geel9/runelite-hunter-rumours/91b833b05b30bcb18cae812be5f0686b324d41c2/README.md).

- **implemented** — Track the native current Hunter Guild rumour.
- **implemented** — Persist the observed saved rumour for each of the six guild masters.
- **partial** — Use the saved master list to prepare and compare a manual block list.

HunterRumourCoach reads native whistle/guild dialogue and rare-part state, with profile-safe HUD, confirmed NPC highlights, supplies and manual travel links. Unknown assignments stay unknown; block optimization, native teleport-widget rewriting and hidden pity counters are not claimed.

Inspected Runewarden files: `HunterRumourCoach.java`, `HunterRumourCatalog.java`, `HunterRumourOverlay.java`, `HunterRumourSceneOverlay.java`.

### 22. Skills Progress

Active installs: 233,844. [Pinned upstream documentation](https://raw.githubusercontent.com/m0bilebtw/skills-tab-progress-bars/c421ff74691c8ccbe35f99e602a32cd94614afbe/README.md).

- **implemented** — Progress strips embedded in all native skill tiles including Sailing.
- **implemented** — Level and own custom-XP or virtual-level goal progress.
- **implemented** — Shared HUD colour customization and native goal tooltips.
- **missing** — Configurable skill-tile bar height and corner indentation.
- **missing** — Read goals set through the native in-game XP menu.
- **missing** — Goal-only and hover-only skill-bar modes.
- **missing** — Completion dimming at level 99, 200 million XP or a custom threshold.

SkillProgressTool supplies its own saved goals, measured rates and native Stats widget strips. It does not replace the source plugin’s widget implementation or claim its native XP-goal compatibility.

Inspected Runewarden files: `SkillProgressTool.java`, `SkillProgressSession.java`, `SkillProgressOverlay.java`, `SkillTabProgressOverlay.java`.

### 23. Fight Cave Waves

Active installs: 232,014. [Pinned upstream documentation](https://raw.githubusercontent.com/nightfirecat/plugin-hub-plugins/07d00f695ddf26801e7e989471d404c98770c3a2/README.md).

- **partial** — Manual wave library and Fight Caves opening spawn markers.
- **missing** — Current and next wave NPC roster display throughout Fight Caves and Inferno.

Inspected Runewarden files: `WaveEntryCoach.java`, `WaveEntryOverlay.java`, `RunewardenGuides.java`, `WaveExportTool.java`.

### 24. Inventory Setups

Active installs: 231,332. [Pinned upstream documentation](https://raw.githubusercontent.com/dillydill123/inventory-setups/16f2c0a319672c2e19738276a17de63aea7216fa/README.md).

- **implemented** — Capture worn equipment, inventory and spellbook.
- **implemented** — Preserve or ignore inventory positions.
- **implemented** — Explicit interchangeable items and pooled potion doses.
- **implemented** — Rune-pouch and quiver requirements.
- **implemented** — Share Runewarden-format loadouts.
- **partial** — Bank filtering and missing-item readiness.
- **partial** — Visual target-slot and quantity differences.
- **missing** — Saved attack-option and weapon-style requirements.
- **missing** — Bolt-pouch capture and comparison.
- **missing** — Inventory Setups JSON and plugin-message interoperability.
- **missing** — Setup folders, favourites, custom icons and rich notes.
- **missing** — Per-slot fuzzy and unordered comparison controls.
- **missing** — In-bank draggable layouts with repeated slots.
- **missing** — Bulk item replacement across saved setups.
- **missing** — Ground-item setup menu actions for UIMs.

The loadout model has a 30-plan limit. Native layout compatibility and the complete upstream editor are separate gaps.

Inspected Runewarden files: `LoadoutPlan.java`, `LoadoutReadiness.java`, `LoadoutReadinessTool.java`, `GuideBankFilter.java`, `BankedExperience.java`, `BankedExperienceCalculator.java`, `OwnershipLedger.java`, `BankMemoryTool.java`, `BankMemorySnapshot.java`, `BankMemoryDiff.java`.

### 25. Totem Fletching

Active installs: 217,068. [Pinned upstream documentation](https://raw.githubusercontent.com/TheRealGuru/totem-fletching/7c3b416a34bc57ef1d32d737acd0059818c0727a/README.md).

- **missing** — Optimal totem carving actions and decoration progress.
- **missing** — Ent-trail locations and expiry timers.
- **missing** — Per-totem live points display.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 26. Shortest Path

Active installs: 211,920. [Pinned upstream documentation](https://raw.githubusercontent.com/Skretzo/shortest-path/37abff3a3345185fc11fccecae04d8170de25a11/README.md).

- **partial** — Send guide, clue and saved destinations to Shortest Path.
- **partial** — Carry item requirements into owned route requests.
- **missing** — Own graph, transport database and pathfinding engine.
- **missing** — Own world-map destination routing without Shortest Path installed.

GuideNavigation uses public plugin messages for routing and confirms receiver acceptance. The production plugin uses an optional separately installed/enabled Shortest Path; this export bundles no companion source or binary. Default tests use an identity-only discovery fixture. The two unchanged real-companion contracts are retained under integration-tests/java, require an externally supplied Shortest Path 1.21.3 JAR at the recorded pin and are not part of default test coverage. The excluded development-launcher startup tests establish no coverage here. Completed advice reads fixed public getters, copies bounded summary facts and launches no query search. Guide preparation has 94 sourced arrival points for 112 training/minigame guide IDs; method/combat positions and unsourced destinations remain excluded. Farm preparation compares eligible patch destinations using native current-position/account/transport costs; custom runs keep their next stop and order. My POH stores character declarations, scans only verified own building-mode houses and requires explicit application; a cape never implies furniture. Inventory-mode teleports use carried/equipped rather than banked-only items. Owned refresh is coalesced and manual routes are preserved. Catalog membership is not destination coverage, and the native engine is not a Runewarden implementation.

Inspected Runewarden files: `GuideNavigation.java`, `ShortestPathRouteReading.java`, `GuideTravelCatalog.java`, `RunewardenGuides.java`, `PohTravelCapabilities.java`, `TravelNotebook.java`, `FarmTravelPlan.java`, `FarmPatchDestinations.java`, `FarmRunNavigation.java`, `FarmPatchPlanner.java`, `FarmRunsTool.java`.

### 27. Fight Caves Spawn Predictor

Active installs: 210,322. [Pinned upstream documentation](https://raw.githubusercontent.com/damencs/spawn-predictor/8c5a34d4c88407cda7a6c60d8014018163d97974/README.md).

- **implemented** — Fight Caves entrance rotation estimate and countdown.
- **implemented** — Selected-rotation entry guidance and opening NPC-spawn markers.
- **partial** — Every upstream calibration and uncertainty control.

Inspected Runewarden files: `WaveEntryCoach.java`, `WaveEntryOverlay.java`, `RunewardenGuides.java`, `WaveExportTool.java`.

### 28. Equipment Inspector

Active installs: 210,028. [Pinned upstream documentation](https://raw.githubusercontent.com/botanicvelious/Equipment-Inspector/19f1b077a3c1304754580f071316b908366edc78/README.md).

- **implemented** — Inspect another player through a native context-menu action.
- **implemented** — Display visible worn-equipment names and actual item icons.

EquipmentInspectorTool/Overlay reads public PlayerComposition for the nine visible worn slots, with optional live refresh, GE estimates and item-database links. Ring/ammo are explicitly unavailable; no hidden inventory or stats are inferred.

Inspected Runewarden files: `EquipmentInspectorTool.java`, `EquipmentInspectorOverlay.java`.

### 29. Radius Markers

Active installs: 199,117. [Pinned upstream documentation](https://raw.githubusercontent.com/Skretzo/runelite-plugins/02effd4f76fbe8e773da1c604b45bc0be7c9f636/README.md).

- **missing** — Persistent spawn, wander and retreat radius regions.
- **missing** — General per-NPC aggression, hunting and attack-range markers.

Current line of sight and footprint reference are separate features from these radius regions.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 30. Barrows Doors Highlighter

Active installs: 197,836. [Pinned upstream documentation](https://raw.githubusercontent.com/hansjm10/Barrows-Door-Highlighter/a07574bbfc18be74f76d25e5b194048026fd54b2/README.md).

- **missing** — Mark locked versus unlocked Barrows tunnel doors.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 31. Customizable XP drops

Active installs: 195,590. [Pinned upstream documentation](https://raw.githubusercontent.com/l2-/template-plugin/abad441320f001d454ccf67186a0d142c235888a/README.md).

- **missing** — Replace and animate native XP drops with custom typography and positioning.
- **missing** — Group, split, filter and recolour XP drops.
- **missing** — Predict hits from XP drops including party members.
- **missing** — Share XP-drop display profiles.

Training/session XP totals are a different feature from rendering individual XP drops.

Inspected Runewarden files: `GoalPlannerTool.java`, `TrainingProgressTool.java`, `TrainingProgressOverlay.java`, `TrainingGuideProgress.java`, `GuideLiveProgress.java`, `BankedExperience.java`, `ActionProgressTool.java`, `ActionProgressOverlay.java`, `ActionProgressRules.java`, `ActionBatchProgress.java`, `ActionRecipeDetector.java`, `SkillProgressTool.java`, `SkillProgressSession.java`, `SkillProgressOverlay.java`, `SkillTabProgressOverlay.java`.

### 32. House Thieving Varlamore

Active installs: 187,855. [Pinned upstream documentation](https://raw.githubusercontent.com/sololegends/runelite-varlamore-house-thieving/ed8c58e1e9dbc73508187c1d88000c61115aee6e/README.md).

- **implemented** — Identify actual distracted citizens, locked doors, owners and escape context.
- **implemented** — Follow the native hint arrow to bonus furniture.
- **implemented** — Display observed inventory keys and valuables with their trade-in value.
- **partial** — Configurable target highlights across all source target types.
- **missing** — Native inventory hover tooltip for each valuable item.
- **missing** — Every distraction, empty-chest and elapsed-threshold notification option.
- **missing** — Independent bonus-item sound suppression.

VarlamoreThievingCoach also supplies observed-start estimated distraction/container/return timers. An unwitnessed mid-cycle start remains unknown; those bounded estimates are not an assertion of universal upstream timer parity.

Inspected Runewarden files: `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`.

### 33. Wilderness Lines

Active installs: 185,802. [Pinned upstream documentation](https://raw.githubusercontent.com/Nightfirecat/plugin-hub-plugins/d81bb4c444c3772b1563be8a8fcc2252c84a0a4f/README.md).

- **missing** — Multi-combat and spear-range boundary overlays.
- **missing** — Wilderness level-20 and level-30 dividing lines.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 34. Custom Menu Swaps

Active installs: 169,370. [Pinned upstream documentation](https://raw.githubusercontent.com/geheur/More-menu-entry-swaps/720769e96dfcc1d976784ece846a4ab0403ef96d/README.md).

- **missing** — Text-rule menu reordering and hiding.
- **missing** — Context hotkeys for bank amounts and transport menus.
- **missing** — Custom ground-item menu ordering.

No generic menu-rule editor exists. Existing Hub menu features require a counterpart-specific API review; this audit does not declare them prohibited.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 35. Visual Metronome

Active installs: 168,787. [Pinned upstream documentation](https://raw.githubusercontent.com/vincent0955/Visual-metronome/f2226279d210c1765e876b51f5e1159921695a46/README.md).

- **implemented** — Generic native-tick visual indicator with CLICK/dot modes.
- **partial** — Movable, recolourable and resizable cadence display.
- **missing** — Party-synchronized metronome sequence state.

Inspected Runewarden files: `SkillingCoach.java`, `SkillingCoachOverlay.java`, `HudSettingsTool.java`.

### 36. CoX Timers & Additions

Active installs: 163,216. [Pinned upstream documentation](https://raw.githubusercontent.com/dey0/pluginhub-plugins/48a8c0a1ecbc40541e2e83d9f8793bf5640a63f5/README.md).

- **partial** — Manual raid attempts and aggregate activity timing.
- **missing** — Automatic CoX room, floor and Olm phase splits.
- **missing** — Thieving grub estimates and Vanguards-specific overlays.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 37. Kitten Tracker

Active installs: 158,353. [Pinned upstream documentation](https://raw.githubusercontent.com/pieterjanbuntinx/kitten-tracker/6672c661be2aceb051059961a6a1781334250741/README.md).

- **missing** — Kitten growth and hunger/attention timers.
- **missing** — Feed and play reminders for kitten care.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 38. Spell Reminder

Active installs: 156,072. [Pinned upstream documentation](https://raw.githubusercontent.com/PortAGuy/thrall-helper/758de245793d09ea3c765b70a3c3e0dfbe9852e3/README.md).

- **partial** — Known active-effect and departure reminders.
- **missing** — Complete supported-spell expiration/recast reminder matrix.
- **missing** — All native cooldown, duration and spell-icon display options.

Inspected Runewarden files: `FieldCoach.java`, `FieldStatusOverlay.java`, `LoadoutReadiness.java`.

### 39. Barracuda Trials Pathfinder

Active installs: 152,713. [Pinned upstream documentation](https://raw.githubusercontent.com/NathanVegetable/barracuda-trial/b05a9f833d59e6b60db0ed3cc1a9265e6d5573cf/README.md).

- **partial** — Native trial identity and current objective highlighting across three trials.
- **missing** — Real-time hazard-aware A-star routes between moving waypoints.
- **missing** — Full Wiki static waypoint order and path projection.
- **missing** — Efficient and relaxed route modes with speed-boost optimization.

The live Sailing coach reads difficulty/progress, witnessed-start timing, splits and KC-confirmed finishes. It supplies selected native objectives, not the upstream pathfinding engine or complete route projections.

Inspected Runewarden files: `SailingXpScenarios.java`, `TrainingProgressTool.java`, `ActivityXpTool.java`, `SailingCoach.java`, `SailingCoachRules.java`, `SailingCoachTasks.java`.

### 40. Loot Lookup

Active installs: 149,240. [Pinned upstream documentation](https://raw.githubusercontent.com/donth77/loot-lookup-plugin/58812e18d0f955d597c4894ece0ae23aff1a3a74/README.md).

- **missing** — NPC-name search and context-menu drop-table lookup.
- **missing** — Complete per-NPC tables with rarity, quantity and value columns.
- **missing** — Multiple monster variants, pinned drop sections and grid/list views.

The item database answers where an item comes from. It is not a full monster-to-drops browser.

Inspected Runewarden files: `ItemKnowledgeTool.java`, `ItemSourceService.java`, `ItemSourceHtmlParser.java`, `MobKnowledgeCatalog.java`.

### 41. Item Charges Improved

Active installs: 147,542. [Pinned upstream documentation](https://raw.githubusercontent.com/TicTac7x/runelite-plugins/85bc2cef4245e6394a0404d8c2f0efd12ef9faa5/README.md).

- **implemented** — Known ID-encoded jewellery and consumable charge counts.
- **implemented** — Configurable low-ammo, rune and dose thresholds.
- **partial** — All armour and weapon charge families.
- **missing** — Full hidden-charge observation through native messages and checks.

SupplyAwarenessTool explicitly leaves unsupported hidden weapon charges unknown.

Inspected Runewarden files: `SupplyAwarenessTool.java`, `LoadoutReadiness.java`, `LoadoutItemVariants.java`.

### 42. Chompy Hunter

Active installs: 142,511. [Pinned upstream documentation](https://raw.githubusercontent.com/PJGJ210/chompy-hunter/8539e578510cd737460933b6f75f582f1aec3490/README.md).

- **missing** — Chompy spawn lifetime and changing urgency colours.
- **missing** — Session and lifetime chompy kill-rate displays.

Inspected Runewarden files: `TrainingProgressTool.java`, `ActivityXpTool.java`, `HunterRumourCoach.java`, `HunterRumourCatalog.java`, `HunterRumourOverlay.java`, `HunterRumourSceneOverlay.java`.

### 43. Ping Graph

Active installs: 140,519. [Pinned upstream documentation](https://raw.githubusercontent.com/yuh25/Ping-Grapher/b0a202f048ecbf1808bc78dfddf9dfa5caee01a4/README.md).

- **missing** — Live world-ping history graph.
- **missing** — Server-tick interval history and configurable lag thresholds.

Bounded internal plugin timing diagnostics do not measure network ping.

Inspected Runewarden files: `ModuleRuntime.java`, `SkillingCoachOverlay.java`.

### 44. Resource packs

Active installs: 140,319. [Pinned upstream documentation](https://raw.githubusercontent.com/melkypie/resource-packs/bd269eda1d4b6f50eba3b8cd84939a20b3b2bd29/README.md).

- **missing** — Load and switch base-client UI resource packs.
- **missing** — Custom resource-pack asset overrides.

Runewarden styles its own overlays and sidebar; base-client reskinning is not supplied.

Inspected Runewarden files: `HudStyle.java`, `HudSettingsTool.java`, `RunewardenPlugin.java`.

### 45. Hunllef Helper

Active installs: 136,969. [Pinned upstream documentation](https://raw.githubusercontent.com/Loze-Put/hunllef-helper/7e0730190bf30cc5972273339cf9407b46c46fd6/README.md).

- **missing** — Manually started Hunllef prayer-cycle audio metronome.
- **missing** — Encounter-specific cycle adjustment controls.

A generic tick pulse and written boss guide do not implement the manual Hunllef helper.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 46. Ba Minigame

Active installs: 136,113. [Pinned upstream documentation](https://raw.githubusercontent.com/SkylerPIlot/Ba-Minigame/9204c0831b02c76eb50e3e9676306b3c15f3ccf3/README.md).

- **partial** — Role-specific native facts and minigame guidance.
- **missing** — Complete wave/call timers and teammate-role presentation.
- **missing** — Lobby convenience swaps and role chat styling.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 47. Wasted Bank Space

Active installs: 135,438. [Pinned upstream documentation](https://raw.githubusercontent.com/mcgeer/WastedBankSpace/1cb3b285c995fb7f4229a172033fcb6020d445cf/README.md).

- **missing** — Recognize bank items eligible for alternate-storage relocation.
- **missing** — Per-storage cleanup recommendations and BIS exclusions.

Ownership search does not perform a bank-space audit.

Inspected Runewarden files: `LoadoutPlan.java`, `LoadoutReadiness.java`, `LoadoutReadinessTool.java`, `GuideBankFilter.java`, `BankedExperience.java`, `BankedExperienceCalculator.java`, `OwnershipLedger.java`, `BankMemoryTool.java`, `BankMemorySnapshot.java`, `BankMemoryDiff.java`.

### 48. Runecrafting Utilities

Active installs: 131,332. [Pinned upstream documentation](https://raw.githubusercontent.com/jpeter17/easy-empty/3c8a143b79e797f24ff3ca4d01bee4c7fa33bb18/README.md).

- **missing** — Rune-pouch fill/empty menu changes by altar or bank context.
- **missing** — Bank-menu necklace and potion convenience swaps.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 49. OSRS TCG

Active installs: 130,100. [Pinned upstream documentation](https://raw.githubusercontent.com/Azderi/osrs-tcg/4df0a577de077adea47385eaf49ad06bd42d260b/README.md).

- **missing** — In-client trading-card collection and card-pack experience.

This is a separate community game, not an existing command-centre feature.

Inspected Runewarden files: `CommandDashboardTool.java`, `CommandDashboardOverlay.java`, `EquipmentInspectorTool.java`, `EquipmentInspectorOverlay.java`, `RunewardenPlugin.java`.

### 50. Fortis Colosseum

Active installs: 125,904. [Pinned upstream documentation](https://raw.githubusercontent.com/LlemonDuck/fortis-colosseum/b86cb48086c83a224eea5cde410bbe06dd4b6dd2/README.md).

- **partial** — Manual Colosseum wave guides and attempt loot totals.
- **missing** — Native reward-interface bank-all and next-wave presentation options.

Inspected Runewarden files: `WaveEntryCoach.java`, `WaveEntryOverlay.java`, `RunewardenGuides.java`, `WaveExportTool.java`.

### 51. C Engineer: Completed

Active installs: 123,900. [Pinned upstream documentation](https://raw.githubusercontent.com/m0bilebtw/c-engineer-completed/5aba5151ac409e1aabcfaf44f19fc86f7a6ce8be/README.md).

- **implemented** — Collection-log and combat-achievement popup sounds.
- **partial** — Configurable event soundboard and sound profiles.
- **missing** — Level-up, quest, diary-tier and death announcements.
- **missing** — Compatibility with the upstream voice-pack file layout.

RewardNotification.Kind currently covers collection, rare drops, music, combat achievements and demo; general level/quest/death audio is not present.

Inspected Runewarden files: `RewardNotifications.java`, `RewardNotification.java`, `RewardPopupOverlay.java`, `RewardSoundboard.java`.

### 52. Large Logout

Active installs: 123,644. [Pinned upstream documentation](https://raw.githubusercontent.com/dekvall/runelite-external-plugins/edcd51ca9e865576ccb91fc75a685de0fd4a3556/README.md).

- **missing** — Resize native logout buttons, including the world-switcher logout.

No extra logout control has been added; earlier duplicate-close UX problems are not a replacement.

Inspected Runewarden files: `HudSettingsTool.java`, `HudPreferences.java`, `HudStyle.java`, `HudResizeController.java`, `InventoryPreviewOverlay.java`, `GuideCoachOverlay.java`.

### 53. Monster Hp Percentage

Active installs: 123,005. [Pinned upstream documentation](https://raw.githubusercontent.com/lejeffe/MonsterHP/360f0c84d427010578f4e0de51c48366b3a77524/README.md).

- **missing** — General per-name NPC HP percentage and numeric overlays.
- **missing** — Per-NPC low-health styles and wildcard thresholds.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 54. Better Teleport Menu

Active installs: 121,973. [Pinned upstream documentation](https://raw.githubusercontent.com/abextm/better-teleport-menu/f11546c7b0552e3efc4572bef7504ac34c16cfb1/README.md).

- **missing** — Custom hotkeys inside native teleport-choice menus.
- **missing** — Filter inaccessible teleport destinations in those menus.
- **missing** — Enlarged special-event teleport menus.

Saved route destinations do not alter teleport-menu keybindings.

Inspected Runewarden files: `GuideNavigation.java`, `ShortestPathRouteReading.java`, `GuideTravelCatalog.java`, `RunewardenGuides.java`, `PohTravelCapabilities.java`, `TravelNotebook.java`, `FarmTravelPlan.java`, `FarmPatchDestinations.java`, `FarmRunNavigation.java`, `FarmPatchPlanner.java`, `FarmRunsTool.java`.

### 55. Crab Stun Timers

Active installs: 121,897. [Pinned upstream documentation](https://raw.githubusercontent.com/AnkouOSRS/crab-stun-timers/84d0186027384e25c0af53684841d4bf699dc731/README.md).

- **missing** — Chambers crab unstun countdowns.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 56. Tree Despawn Timer

Active installs: 119,518. [Pinned upstream documentation](https://raw.githubusercontent.com/CreativeTechGuy/tree-despawn-timer/9f9eb2b35db64cb6032d9d3e713afb2a3bd89176/README.md).

- **implemented** — Observed tree remaining-time estimates and respawn display.
- **partial** — Pie, tick and seconds visualization options.
- **missing** — Special popular-tree visual treatment based on nearby choppers.

Inspected Runewarden files: `NodeTimerRules.java`, `NodeTimerOverlay.java`, `SkillingCoach.java`, `RadialTimer.java`, `ContextActions.java`.

### 57. Cox Additions

Active installs: 116,872. [Pinned upstream documentation](https://raw.githubusercontent.com/MoreBuchus/buchus-plugins/64ee848dda5e3b443e739ada61af04e3d7d29c51/README.md).

- **missing** — Prayer-enhance detail and party grub counters.
- **missing** — CoX preparation and clan-party entrance warnings.
- **missing** — Olm head and CoX-specific true-tile highlight settings.
- **missing** — CoX-specific menu and hotkey conveniences.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 58. Improved Tile Indicators

Active installs: 116,210. [Pinned upstream documentation](https://raw.githubusercontent.com/LeikvollE/tileindicators/bc2ed6db4d5451b508a47b74976cad4675c097c7/README.md).

- **missing** — Native player/destination tile styling with behind-actor modes.

Authored static marker packs do not replace native movement/destination tile indicators.

Inspected Runewarden files: `TileMarkersTool.java`, `GuideMarkerOverlay.java`, `RunewardenSightOverlay.java`.

### 59. Plank Sack

Active installs: 115,810. [Pinned upstream documentation](https://raw.githubusercontent.com/Talkarcabbage/planksacktracker/e13a56ed6cbba3e54701d43924f12df16556fe2e/README.md).

- **implemented** — Observe all seven native plank-type counts including Sailing planks.
- **implemented** — Per-plank-type count breakdown with actual item icons.
- **implemented** — Carried and currently visible bank-container location with validated combined capacity.
- **partial** — Configurable plank overlay colours and arrangement.
- **missing** — Original inventory hover tooltip and alternate source layout styles.

ContainerHudTool reads the seven public native varbits and rejects invalid or over-capacity totals. The shared movable HUD has common style controls, but not every upstream per-icon/label/layout option or ownership-ledger integration.

Inspected Runewarden files: `ContainerHudTool.java`, `ContainerHudRules.java`, `ContainerHudOverlay.java`.

### 60. ToB QoL

Active installs: 115,396. [Pinned upstream documentation](https://raw.githubusercontent.com/damencs/tob-qol/3715d6ae24547fa87dc7ed82665e37b632407ba7/README.md).

- **unknown** — Exact room-specific Theatre quality-of-life option coverage.

The pinned README describes scope but not the actual options. A written Theatre guide cannot be counted as a replacement; configuration/source review remains open.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 61. Bossing Info

Active installs: 112,052. [Pinned upstream documentation](https://raw.githubusercontent.com/Mrnice98/BossingInfo/c7f4c8ce8b7d36435ae5b5b6e028a8c6ed91bff9/README.md).

- **implemented** — Run loot, elapsed time and activity session summaries.
- **partial** — All boss recognition, pause/resume and kill-rate statistics.
- **missing** — Upstream chat commands and every boss-specific rule.

Inspected Runewarden files: `RunLootTool.java`, `SharedActivitySession.java`, `EncounterPracticeSession.java`.

### 62. Crab Solver

Active installs: 111,441. [Pinned upstream documentation](https://raw.githubusercontent.com/TheStonedTurtle/CrabSolver/503de15321ba896d720352fc77190fc9b17ac7aa/README.md).

- **missing** — Chambers crystal-to-crab colour solution overlay.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 63. One Click Summer Garden

Active installs: 109,567. [Pinned upstream documentation](https://raw.githubusercontent.com/LlemonDuck/one-click-summer-garden/c294707f2c3653236b5882d824574ef8d495d879/README.md).

- **missing** — Summer Garden elemental cycle and reset analysis.
- **missing** — Start-gate timing and cycle-alignment visualization.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 64. AttackTimer

Active installs: 109,318. [Pinned upstream documentation](https://raw.githubusercontent.com/ngraves95/attacktimer/52c321783bc52e5772a51fded4efd7d2523a37ac/README.md).

- **missing** — Own-weapon next-attack cooldown timer.
- **missing** — Weapon and encounter-specific attack-speed corrections.

The existing generic tick display does not calculate the player attack cooldown.

Inspected Runewarden files: `SkillingCoach.java`, `SkillingCoachOverlay.java`, `HudSettingsTool.java`.

### 65. Stealing Artefacts

Active installs: 107,626. [Pinned upstream documentation](https://raw.githubusercontent.com/pajlads/StealingArtefacts/631227f32d7518da6615165c4658bf6e3704dedb/README.md).

- **missing** — Assigned artefact route and house/ladder highlights.
- **missing** — Lured-guard indicators and artefact session overlays.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 66. DT2 Boss Utilities

Active installs: 107,089. [Pinned upstream documentation](https://raw.githubusercontent.com/DominickCobb-rs/DT2-Boss-Failure/e6792347a8f36bc3e70943f8ba10b478232c5172/README.md).

- **missing** — Source-supported automatic DT2 mistake-event detection.
- **missing** — Per-boss native event summaries.

Manual post-attempt coaching and mistake journals are separate from automatic mistake-event detection.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 67. RuneProfile

Active installs: 104,844. [Pinned upstream documentation](https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/966f86fb3f0477ef8888cb84f91957891396b9d9/README.md).

- **missing** — Opt-in public achievement-profile publishing.
- **missing** — RuneProfile account synchronization.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 68. Max Hit

Active installs: 103,951. [Pinned upstream documentation](https://raw.githubusercontent.com/Seebity/max-hit/dce258e2d185887de891eec8961c27b5df92a55c/README.md).

- **implemented** — Automatic current-gear normal-attack max-hit calculation.
- **partial** — Current selected spell, prayers and boosted-level modifiers.
- **partial** — Complete special-attack max-hit coverage.

Inspected Runewarden files: `DpsCalculator.java`, `CombatRollProbability.java`, `DpsSpecialAttack.java`, `DpsTargetScenario.java`, `CurrentMaxHit.java`, `CombatCalculatorTool.java`, `ConsumablePreviewOverlay.java`.

### 69. Larva Tracker

Active installs: 99,832. [Pinned upstream documentation](https://raw.githubusercontent.com/marknewan/newan-plugins/5d25c9dac1ccb80c523ed120fef9b405eb84aa81/README.md).

- **missing** — Larva-specific visibility and highlight rules.
- **missing** — Supported death prediction and absorbed-larva counters.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 70. Star Miners

Active installs: 99,304. [Pinned upstream documentation](https://raw.githubusercontent.com/zodaz/star-calling-assist/dd42ca20d1e0c97495b580439f9ac2b35dfbabd8/README.md).

- **missing** — Star discovery uploads and community star-location list.
- **missing** — Manual or configured automatic star reports.

A local mining guide does not supply a scouting network.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 71. Construction QOL

Active installs: 98,719. [Pinned upstream documentation](https://raw.githubusercontent.com/JZomDev/zom-external-plugins/4b5603d615d88c082520be8ce06b778d2ecb43fa/README.md).

- **missing** — Construction build-menu key remapping.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 72. Zalcano

Active installs: 95,174. [Pinned upstream documentation](https://raw.githubusercontent.com/oceijt/Zalcano/d517f1fa4685031a78981e8fafb91b517b223fa3/README.md).

- **missing** — Zalcano phase health and participant counts.
- **missing** — Personal contribution thresholds and reward-eligibility estimates.
- **missing** — Scouting and Crystal Tool Seed probability displays.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 73. Fight Cave Helper

Active installs: 93,444. [Pinned upstream documentation](https://raw.githubusercontent.com/runkev/fight-cave-helper/c8c277de866c75bf1ed74598d687b5c048a2901a/README.md).

- **partial** — Manual Fight Caves wave references and opening setup.
- **missing** — Dedicated per-wave spawn image interface for every wave.

Inspected Runewarden files: `WaveEntryCoach.java`, `WaveEntryOverlay.java`, `RunewardenGuides.java`, `WaveExportTool.java`.

### 74. Gauntlet Minimap

Active installs: 93,044. [Pinned upstream documentation](https://raw.githubusercontent.com/VicSegers/gauntlet-minimap/4745ea9960dd85ca094e372299ba8025720e7717/README.md).

- **missing** — Gauntlet resource and demi-boss minimap icons.
- **missing** — Hide resources after gathering the configured amount.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 75. Motherlode Mine Improved

Active installs: 91,858. [Pinned upstream documentation](https://raw.githubusercontent.com/TicTac7x/runelite-plugins/2909d5622024a4f011d10eea9c513aa6435e393c/README.md).

- **partial** — Mining node timers and training progression.
- **missing** — Upper-floor vein-section logic and vein suitability highlights.
- **missing** — Combined hopper/sack capacity and nugget session widget.
- **missing** — Rockfall indicators and stop-mining alerts.

Inspected Runewarden files: `NodeTimerRules.java`, `NodeTimerOverlay.java`, `SkillingCoach.java`, `RadialTimer.java`, `ContextActions.java`.

### 76. Clan Events

Active installs: 91,664. [Pinned upstream documentation](https://raw.githubusercontent.com/cmsu224/clan-events/ba691ee1b1d4584e100babf0a45dbc212b3cccae/README.md).

- **missing** — Clan-event calendar and external spreadsheet synchronization.
- **missing** — Wise Old Man event integration.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 77. Detached Camera

Active installs: 91,276. [Pinned upstream documentation](https://raw.githubusercontent.com/Adam-/runelite-plugins/225d9fff38ad0f621754c696ea0e919233458da7/README.md).

- **missing** — Hotkey-controlled free-roaming detached camera.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 78. Improved Tears Interface

Active installs: 90,821. [Pinned upstream documentation](https://raw.githubusercontent.com/Cyborger1/improved-tears-of-guthix-interface/57e41a21ddd773812caf77f4522d8ae848b93e19/README.md).

- **missing** — Native Tears of Guthix tick countdown and current-stream indicator.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 79. Optimal Quest Guide

Active installs: 90,738. [Pinned upstream documentation](https://raw.githubusercontent.com/cesoun/optimal-quest-guide/b801ca19e0398c08ca997ec52e7c3eb5e44a8aa1/README.md).

- **implemented** — Wiki optimal-order quest sorting.
- **implemented** — Hide completed quests and refresh after native completion.
- **partial** — Account prerequisite metadata and Quest Helper planning links.
- **partial** — Recommended next quest from fully checked live prerequisites.

Unverified requirements retain an unknown state rather than being silently treated as satisfied. Native quest progress, prerequisite metadata and ordering remain in Runewarden. Quest Helper planning controls are retained references, but the plugin-only export has no automatic handoff bridge; walkthroughs must be opened separately in Quest Helper.

Inspected Runewarden files: `RunewardenGuides.java`, `GuideCatalog.java`, `QuestOrder.java`.

### 80. BA Call Highlight

Active installs: 90,661. [Pinned upstream documentation](https://raw.githubusercontent.com/rugg0064/ba-call-highlight/e98baa812175a8e9e26bc16bb37502282ba8ea07/README.md).

- **partial** — Read native BA role and call facts.
- **missing** — Highlight the matching call-horn context-menu entry.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 81. Projectile Override

Active installs: 86,588. [Pinned upstream documentation](https://raw.githubusercontent.com/Loze-Put/projectile-override/efafd9406b6fdd523e15502a2a8e4e74633067e8/README.md).

- **missing** — Configurable visual projectile substitutions by boss.
- **missing** — Projectile colour/shape contrast accessibility profiles.

Inspected Runewarden files: `HudSettingsTool.java`, `RunewardenSightOverlay.java`.

### 82. Collection Log Luck

Active installs: 86,417. [Pinned upstream documentation](https://raw.githubusercontent.com/peanubnutter/collection-log-luck/c33916c7216a9c38bc5b2d6580df99630610a319/README.md).

- **partial** — Display known item rarity and collection ownership reference.
- **missing** — Per-item received-versus-expected luck calculations.
- **missing** — Collection-log context-menu probability details.
- **missing** — Public collection-log account imports and luck sharing.

A rarity-coloured reward popup is not a probability or dryness model.

Inspected Runewarden files: `RarityCatalog.java`, `RewardNotifications.java`.

### 83. Target True Tile

Active installs: 86,321. [Pinned upstream documentation](https://raw.githubusercontent.com/Notloc/runelite-target-true-tile/c20c412959e8595d3ec785effb2025b072d59a34/README.md).

- **partial** — Shift-hover current NPC footprint reference.
- **missing** — Persistent interacting-target true tile with memory.
- **missing** — Friendly exclusions and NPC-tag integration.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 84. Annoyance Mute

Active installs: 86,215. [Pinned upstream documentation](https://raw.githubusercontent.com/Broooklyn/runelite-external-plugins/195334815e22ef0dbbc76d74e518c5c0a3ab20cb/README.md).

- **missing** — Per-sound and per-entity nuisance-audio suppression.

Inspected Runewarden files: `RewardSoundboard.java`, `RunewardenConfig.java`.

### 85. Clue Juggling Timer

Active installs: 85,771. [Pinned upstream documentation](https://raw.githubusercontent.com/geheur/clue-juggling-timers/c099981f9f8f5f2adea72d1706949b4f461a5f81/README.md).

- **implemented** — Separate native dropped-scroll deadlines for all six clue tiers and elite challenge scrolls.
- **implemented** — Configurable tier filters, expiry thresholds and once-per-drop alerts.
- **implemented** — Earliest-only or separate countdown HUD rows with tick/second and ring options.
- **implemented** — Extra native item timers from IDs, names and star wildcards.
- **implemented** — Manual removal and profile-isolated private-clue checkpoints.
- **partial** — Cross-scene and cross-session clue timer continuity.
- **missing** — Upstream infobox-manager placement and independent notification-channel editor.
- **missing** — Historical arbitrary timer-reduction multiplier.

The pinned README is a template; actual source/config were inspected. Native getDespawnTime replaces a fixed hour assumption. Clean checkpoints remain marked awaiting sighting; dirty checkpoints show unknown. Public/extra/instance timers are never restored as paused private loot. Live transition acceptance remains open.

Inspected Runewarden files: `ClueJugglingTracker.java`, `ClueJugglingTool.java`, `ClueJugglingOverlay.java`.

### 86. Wise Old Man

Active installs: 85,319. [Pinned upstream documentation](https://raw.githubusercontent.com/wise-old-man/wiseoldman-runelite-plugin/9dc1be0dbfda8a3050a215d6b05dbbcda44e09a2/README.md).

- **missing** — Wise Old Man gain updates and player lookups.
- **missing** — Competition countdowns and notifications.
- **missing** — Clan/group synchronization and membership management.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 87. Runedoku

Active installs: 84,337. [Pinned upstream documentation](https://raw.githubusercontent.com/robmonte/runedoku-plugin/f2d3b899048e45af1dfde328106e431dd47bcda7/README.md).

- **missing** — Runedoku board solver and selected-rune hints.
- **missing** — Candidate pencil marks and conflicting-entry detection.
- **missing** — Numeric board display and configurable puzzle styling.

Inspected Runewarden files: `ClueDataService.java`, `ClueCoachTool.java`.

### 88. Ground Object Hider

Active installs: 84,041. [Pinned upstream documentation](https://raw.githubusercontent.com/dey0/pluginhub-plugins/49ec944000c45bed16f96a6fe0669cf45a924727/README.md).

- **missing** — Select and hide arbitrary ground-object models.
- **missing** — Persist editable object-hiding selections.

Inspected Runewarden files: `HudStyle.java`, `HudSettingsTool.java`, `RunewardenPlugin.java`.

### 89. 1 Tick Flick

Active installs: 82,953. [Pinned upstream documentation](https://raw.githubusercontent.com/SuperNerdEric/1-Tick-Flick/c1c2877560ac2a3c9b0a1a8c3b0ab5655beb0100/README.md).

- **implemented** — Generic tick cadence with optional CLICK text.
- **missing** — Latency-adjusted prayer-click training window.
- **missing** — Flick combo counter and prayer-bar overlays.

The present display is a generic metronome, not an enemy-dependent prayer indicator.

Inspected Runewarden files: `SkillingCoach.java`, `SkillingCoachOverlay.java`, `HudSettingsTool.java`.

### 90. Easy Blast Furnace

Active installs: 82,469. [Pinned upstream documentation](https://raw.githubusercontent.com/Toofifty/easy-blastfurnace/c8eff9d7a4e9b4f73fe7209b936d548e3e043f22/README.md).

- **partial** — Native furnace facts, supplies and load/collect guidance.
- **missing** — Every optimized tick method and next-click target sequence.
- **missing** — Coal-bag live contents and precise stamina prompts.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 91. Party Defence Tracker

Active installs: 82,429. [Pinned upstream documentation](https://raw.githubusercontent.com/Hyftar/party-defence-tracker/c6c4fdcbc128e3580488fb242c60bd84c2eb1ca2/README.md).

- **partial** — Manual defence-reduction scenarios in DPS comparison.
- **missing** — Party special-attack outcome synchronization.
- **missing** — Live target-defence and vulnerability infobox.

Inspected Runewarden files: `DpsCalculator.java`, `CombatRollProbability.java`, `DpsSpecialAttack.java`, `DpsTargetScenario.java`, `CurrentMaxHit.java`, `CombatCalculatorTool.java`, `ConsumablePreviewOverlay.java`.

### 92. Time Tracking Reminder

Active installs: 81,035. [Pinned upstream documentation](https://raw.githubusercontent.com/queicherius/runelite-time-tracking-reminder/f66b6af1c6dfa2a5180fe6710a1126dcf4ce4e7b/README.md).

- **implemented** — Ready farming/birdhouse selection in a movable HUD.
- **implemented** — Read native Time Tracking observations.
- **partial** — All miscellaneous routine and patch reminder categories.

Inspected Runewarden files: `HarvestClockTool.java`, `HarvestClockOverlay.java`, `HarvestTimerWindows.java`, `HarvestSelections.java`, `FarmRunsTool.java`, `FarmTravelPlan.java`, `FarmPatchCatalog.java`, `FarmGrowthTiming.java`, `FarmPatchPlanner.java`, `FarmGuideProgress.java`, `TrainingGuideProgress.java`, `GuideLiveProgress.java`, `FarmPatchDestinations.java`, `FarmRunNavigation.java`, `FarmPatchScene.java`, `FarmCompostEvidence.java`, `FarmPatchOverlay.java`.

### 93. Vardorvis Projectiles

Active installs: 78,535. [Pinned upstream documentation](https://raw.githubusercontent.com/InfernoStats/vardorvis-projectiles/c5571a568c5bce98e9243761ab5df9d89b1473db/README.md).

- **missing** — Visual substitution of the Vardorvis prayer-disable projectile.

Inspected Runewarden files: `HudSettingsTool.java`, `RunewardenSightOverlay.java`.

### 94. Flipping Utilities

Active installs: 77,007. [Pinned upstream documentation](https://raw.githubusercontent.com/Flipping-Utilities/rl-plugin/9ea3eaa781fc6b310037f16ae84e74886106caa8/README.md).

- **missing** — Grand Exchange trade history and realized-profit accounting.
- **missing** — Margin, buy-limit and active-offer timers.
- **missing** — Offer editing aids, recipe flips and transaction statistics.

Inspected Runewarden files: `RunLootTool.java`, `ItemKnowledgeTool.java`.

### 95. Herbi AFK

Active installs: 76,495. [Pinned upstream documentation](https://raw.githubusercontent.com/Maurits825/herbi-afk/a23942dca96df277807bfc82d3a4fb87580741a1/README.md).

- **missing** — Live Herbiboar trail-to-next-spot lines and arrows.
- **missing** — Harvest target and nearest new trail-start guidance.

Authored training plans do not adapt the native Herbiboar tracker.

Inspected Runewarden files: `TrainingProgressTool.java`, `ActivityXpTool.java`, `HunterRumourCoach.java`, `HunterRumourCatalog.java`, `HunterRumourOverlay.java`, `HunterRumourSceneOverlay.java`.

### 96. Monster Menu HP

Active installs: 75,623. [Pinned upstream documentation](https://raw.githubusercontent.com/AnkouOSRS/npc-menu-hp/aa0eeeafecf14bf851921a119ea2dd0dbd88d5ef/README.md).

- **missing** — Current NPC HP or percentage inside the context menu.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 97. Razor Kebbit Tracking

Active installs: 75,225. [Pinned upstream documentation](https://raw.githubusercontent.com/cwjoshuak/razor-backed-kebbits/11eccb3eb9f8c992a278ae24717df29c537de3e5/README.md).

- **missing** — Native razor-backed kebbit trail tracking and search-target highlights.

Inspected Runewarden files: `TrainingProgressTool.java`, `ActivityXpTool.java`, `HunterRumourCoach.java`, `HunterRumourCatalog.java`, `HunterRumourOverlay.java`, `HunterRumourSceneOverlay.java`.

### 98. Looting Bag

Active installs: 75,198. [Pinned upstream documentation](https://raw.githubusercontent.com/pwatts6060/runelite-plugins/b334aa4e3dbce5ebb0036d78b182a1faa5e44a90/README.md).

- **implemented** — Observe looting-bag contents in the storage ledger.
- **partial** — Look up the observed contents while preparing a loadout.
- **missing** — Looting-bag inventory value and free-slot overlay.
- **missing** — Incremental pickup, dispenser and telegrab tracking.

A last-observed container snapshot does not prove every live bag change is tracked.

Inspected Runewarden files: `OwnershipLedger.java`, `SupplyAwarenessTool.java`, `ContainerHudTool.java`, `ContainerHudRules.java`, `ContainerHudOverlay.java`, `FishBarrelReading.java`.

### 99. Emote Clue Items

Active installs: 75,129. [Pinned upstream documentation](https://raw.githubusercontent.com/larsvansoest/emote-clue-items/2ab1220cbccba512c2f6744b217c266524e70be9/README.md).

- **implemented** — Current emote-clue equipment requirements and supply focus.
- **implemented** — Observed built/filled STASH state with unknown-state handling.
- **implemented** — Search STASH units and accepted item examples.
- **partial** — Persist an emote-item collection checklist.
- **missing** — Global item highlighting for future emote clues.
- **missing** — Persistent world-map markers for all relevant STASH locations.
- **missing** — Right-click lookup of every item’s future clue requirements.

Inspected Runewarden files: `ClueDataService.java`, `ClueCoachTool.java`, `ClueAccessIndex.java`, `ClueJugglingTool.java`, `ClueJugglingTracker.java`, `ClueJugglingOverlay.java`.

### 100. Dink for Discord

Active installs: 74,343. [Pinned upstream documentation](https://raw.githubusercontent.com/pajlads/DinkPlugin/7c026d35630e65c3c4e1d25f3ef62f2e6b3c71ac/README.md).

- **missing** — Configurable Discord/webhook event notifications.
- **missing** — Screenshots, rich embeds and per-event routing.
- **missing** — Quest, clue, trade, GE and group-storage webhook coverage.

Local notification popups do not send external webhooks. External transmission would need explicit opt-in and a disclosed destination.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 101. Slayer Assistant

Active installs: 73,283. [Pinned upstream documentation](https://raw.githubusercontent.com/LeeOkeefe/slayer-assistant-plugin/f04cb9a5dc9c60c94d5ad1710eeb2d7f9048ee6f/README.md).

- **implemented** — Current task location, required equipment and Wiki links.
- **implemented** — Static monster attack-style and attribute references.
- **partial** — Search all assignment variants and every assigning master.

Inspected Runewarden files: `SlayerCoachTool.java`, `SlayerPlanningTool.java`, `SlayerTaskCatalog.java`, `MobKnowledgeCatalog.java`.

### 102. Left Click Dropper

Active installs: 72,806. [Pinned upstream documentation](https://raw.githubusercontent.com/JZomDev/zom-external-plugins/4b9cf0c6a0aae967b4580f64ad6e4155e0e12c66/README.md).

- **missing** — Editable item-name rules for left-click Drop menus.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 103. Action Progress

Active installs: 71,903. [Pinned upstream documentation](https://raw.githubusercontent.com/guillaume009/runelite-plugin-action-progress/ec2091d59ec74d9856d2190184dc06f335f87976/README.md).

- **implemented** — Movable bar, countdown ring and seconds display choices.
- **implemented** — Confirmed ingredient-to-product batch progress with matching XP.
- **implemented** — Common potion, gem, battlestaff, glass, jewellery, bow and ordinary fish cycle indicators.
- **partial** — Animation progress for supported smithing, furnace, grinding and construction animations.
- **partial** — Action-specific coverage across the complete documented production list.
- **missing** — Complete smithing, smelting and cannonball action-completion cycles.
- **missing** — All enchantment, orb, plank, tablet and jewellery-spell cycles.
- **missing** — Action-specific dairy, pastry, wine, fruit and pizza cycles.
- **missing** — Campfire, ultracompost, Sailing sorting, Camdozaal and reward-search cycles.

ActionProgressTool confirms 102 modelled production recipes through ingredient/output/XP evidence, supplies nine standard cycle categories and explicitly labels native-animation fallback. Ordinary selected cooking has cycle support without claiming unverified product-based batch completion. Animation end is not claimed to be universal action completion; unsupported specialist cycles remain visible gaps.

Inspected Runewarden files: `ActionProgressTool.java`, `ActionProgressOverlay.java`, `ActionProgressRules.java`, `ActionBatchProgress.java`, `ActionRecipeDetector.java`.

### 104. Teleport Maps

Active installs: 71,346. [Pinned upstream documentation](https://raw.githubusercontent.com/MJHylkema/spirit-tree-map/25382eeebed8d5b88abfb9710d4040f986687caf/README.md).

- **missing** — Interactive map replacements for native teleport-choice interfaces.
- **missing** — Spirit tree, mushtree, obelisk and regional transport map selection.

Inspected Runewarden files: `GuideNavigation.java`, `ShortestPathRouteReading.java`, `GuideTravelCatalog.java`, `RunewardenGuides.java`, `PohTravelCapabilities.java`, `TravelNotebook.java`, `FarmTravelPlan.java`, `FarmPatchDestinations.java`, `FarmRunNavigation.java`, `FarmPatchPlanner.java`, `FarmRunsTool.java`.

### 105. Tithe Farm Improved

Active installs: 71,253. [Pinned upstream documentation](https://raw.githubusercontent.com/TicTac7x/runelite-plugins/4348e193c2ebecad83f9a017a625c6a45eceadba/README.md).

- **partial** — Tithe activity facts and next-object guidance.
- **missing** — Every plant’s dry, watered, grown and blighted tick state.
- **missing** — Complete seed and watering-container inventory highlights.
- **missing** — Dedicated Tithe points widget parity.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 106. RaidReloader

Active installs: 70,858. [Pinned upstream documentation](https://raw.githubusercontent.com/Trevor159/runelite-external-plugins/41ae01879ba4fb3b60ebd7d912d250622c8fe192/README.md).

- **missing** — Raid scouting reload convenience control.

The source-pinned control must be reviewed separately before adding a counterpart; ordinary route clearing is unrelated.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 107. Mastering Mixology Strategy

Active installs: 70,101. [Pinned upstream documentation](https://raw.githubusercontent.com/saladuit/Mastering_Mixology_Strategy/f5bc535fe5209604d1eceb0d3458f3673e6e1cb2/README.md).

- **partial** — Current-order recipe selection.
- **missing** — All upstream optimized brewing strategies and configurable priorities.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 108. Visual Ticks

Active installs: 69,885. [Pinned upstream documentation](https://raw.githubusercontent.com/mcscoobe/visual-ticks/42f274cf807333b457b7c54542ef6ae78931d3bd/README.md).

- **implemented** — Native game-tick pulse display.
- **partial** — Configurable visual cadence presentation.
- **missing** — Three simultaneous tab-specific tick configurations.
- **missing** — Global and individual cycle-length adjustment hotkeys.

Inspected Runewarden files: `SkillingCoach.java`, `SkillingCoachOverlay.java`, `HudSettingsTool.java`.

### 109. Consumable Cooldowns

Active installs: 69,419. [Pinned upstream documentation](https://raw.githubusercontent.com/CopyPastaOSRS/consumable-cooldowns/452d4b496f7157092834a8faeea3fba94e6a3273/README.md).

- **partial** — Preview food healing and prayer restoration.
- **missing** — Live regular-food, combo-food and potion cooldown overlays.
- **missing** — Variable-delay sliced-food timing.

ConsumablePreviewOverlay is an effect preview, not a live cooldown tracker.

Inspected Runewarden files: `DpsCalculator.java`, `CombatRollProbability.java`, `DpsSpecialAttack.java`, `DpsTargetScenario.java`, `CurrentMaxHit.java`, `CombatCalculatorTool.java`, `ConsumablePreviewOverlay.java`.

### 110. Loot Logger

Active installs: 69,417. [Pinned upstream documentation](https://raw.githubusercontent.com/TheStonedTurtle/Loot-Logger/f359a7eea8a0021d06beebce296ed04dbaf1af07/README.md).

- **implemented** — Run-scoped loot totals and saved activity histories.
- **partial** — Searchable persistent history for every native Loot Tracker source.
- **missing** — Upstream JSON-lines loot-history compatibility and per-source file import/export.

Shared activity/run tracking is useful, but not the complete Loot Logger history browser.

Inspected Runewarden files: `RunLootTool.java`, `SharedActivitySession.java`, `EncounterPracticeSession.java`.

### 111. Nyloer

Active installs: 69,082. [Pinned upstream documentation](https://raw.githubusercontent.com/EIKOOT/nyloer/9b400ec733fb7d83c261affee4b8edf4feb3fb5f/README.md).

- **missing** — Nylocas type symbols and wave numbering.

Source README also lists unsupported/restricted features separately; they are not included as implementation requirements here.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 112. PvP Performance Tracker

Active installs: 68,655. [Pinned upstream documentation](https://raw.githubusercontent.com/Matsyir/pvp-performance-tracker/3e8c5bac7f3c6357fe6b9ca79428f2f954465ec7/README.md).

- **missing** — PvP fight accuracy, damage and performance analysis.
- **missing** — Optional opponent synchronization and post-fight replay analysis.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 113. Barrows Potential

Active installs: 68,219. [Pinned upstream documentation](https://raw.githubusercontent.com/Godofdrakes/barrows-potential/a81d09b55b145673487f1507d6d634dc38903997/README.md).

- **missing** — Current Barrows reward-potential target and creature-combination calculation.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 114. Watchdog (Custom Alerts)

Active installs: 67,981. [Pinned upstream documentation](https://raw.githubusercontent.com/adamk33n3r/runelite-watchdog/5bdceff5de4346152c52a6e4e8bf3fba88a2a2a7/README.md).

- **partial** — Built-in low-supply, training-idle and harvest alerts.
- **partial** — Styled local popup, sound and desktop notifications.
- **missing** — User-defined alert rules with regex and capture groups.
- **missing** — General message, spawn, stat, inventory and location trigger editor.
- **missing** — Composable per-rule counters and notification actions.
- **missing** — Shared alert-pack import and subscription hub.

Fixed purpose-built reminders are not a general event-rule engine. The source documents its own activity restrictions; a counterpart needs feature-specific review.

Inspected Runewarden files: `SupplyAwarenessTool.java`, `TrainingProgressTool.java`, `HarvestClockTool.java`, `RewardNotifications.java`.

### 115. Pyramid Plunder Counter

Active installs: 67,582. [Pinned upstream documentation](https://raw.githubusercontent.com/adnapPanda/pyramid-plunder-counter/909f38c13025dc79fda03716834223b0d38d9ceb/README.md).

- **partial** — Current Pyramid Plunder room and activity facts.
- **missing** — Persistent chest/sarcophagus counts since the last sceptre.
- **missing** — Cumulative sceptre and pet probability calculations.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 116. Weapon Charges (WIP)

Active installs: 67,122. [Pinned upstream documentation](https://raw.githubusercontent.com/geheur/weapon-charges/8da860f3628cbd4fc72ba09cfdff4b67ad88bac9/README.md).

- **partial** — Known ID-encoded charges and departure checks.
- **missing** — All supported hidden charged-weapon reservoirs.
- **missing** — Weapon-use decrement and native check-message reconciliation.

Inspected Runewarden files: `SupplyAwarenessTool.java`, `LoadoutReadiness.java`, `LoadoutItemVariants.java`.

### 117. Gauntlet Chest Popup

Active installs: 66,860. [Pinned upstream documentation](https://raw.githubusercontent.com/ldavid432/gauntlet-loot-popup/de9152014e052c7253ce0d43d0fc443db7ae4c26/README.md).

- **implemented** — Rarity-coloured loot popup and rare-drop audio.
- **partial** — Gauntlet-source reward recognition.
- **missing** — Dedicated Gauntlet chest sprite, title and normal/corrupted themes.

Inspected Runewarden files: `RewardNotifications.java`, `RewardNotification.java`, `RewardPopupOverlay.java`, `RewardSoundboard.java`.

### 118. Forestry CC

Active installs: 66,376. [Pinned upstream documentation](https://raw.githubusercontent.com/rangdevelopment/root-tracker/d6dad2532c233fd0a4ad0b4504c7d8e8ecc8d763/README.md).

- **missing** — Community Forestry event reports and concurrent location timers.
- **missing** — Event confirmation, expiry and accessible-location filters.

Inspected Runewarden files: `RunewardenPlugin.java`, `ItemSourceService.java`, `ItemKnowledgeTool.java`.

### 119. Door Kicker

Active installs: 66,113. [Pinned upstream documentation](https://raw.githubusercontent.com/Enriath/external-plugins/9d078ca73f670d14b23bbb776ffd75075bec69f3/README.md).

- **unknown** — Exact novelty door-interaction behaviour.

The pinned README is a slogan rather than a feature specification; no animation behaviour is invented from the name.

Inspected Runewarden files: `RunewardenPlugin.java`, `HudSettingsTool.java`.

### 120. RuneWatch

Active installs: 65,825. [Pinned upstream documentation](https://raw.githubusercontent.com/while-loop/runelite-plugins/5f4061692111af1d0a0d00d784ed285de602253c/README.md).

- **missing** — RuneWatch and WDR watchlist synchronization.
- **missing** — Player lookup, nearby-party and trade-window watchlist warnings.

Inspected Runewarden files: `CommandDashboardTool.java`, `CommandDashboardOverlay.java`, `EquipmentInspectorTool.java`, `EquipmentInspectorOverlay.java`, `RunewardenPlugin.java`.

### 121. Menu Swapper Extended

Active installs: 65,068. [Pinned upstream documentation](https://raw.githubusercontent.com/raiyni/menu-swapper-extended/9a5a9cc3278fd552b948bfc62c2ee1a461f6fde1/README.md).

- **missing** — Extended NPC, object, minigame and transport menu swaps.
- **missing** — Per-interaction convenience settings.

Source Config inspected. Menu-entry changes are distinct from automatically performing game actions.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 122. Not Empty

Active installs: 64,336. [Pinned upstream documentation](https://raw.githubusercontent.com/dekvall/runelite-external-plugins/0c1c5cf2a8588ac3b017e7026c393d2eee4980c3/README.md).

- **missing** — Suppress accidental potion Empty menu choices.

Inspected Runewarden files: `RunewardenPlugin.java`, `GuideBankFilter.java`.

### 123. Gemstone Crab

Active installs: 64,110. [Pinned upstream documentation](https://raw.githubusercontent.com/ShaneeexD/GemstoneCrabTimer/4f6a2da963cc60d4347795fb2ea3833b5d9c176a/README.md).

- **partial** — Training-session idle reminders and method plans.
- **missing** — Gemstone Crab HP-threshold notifications and death pulse.
- **missing** — Post-death tunnel target highlight.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 124. Guardians of the Rift Optimizer

Active installs: 63,515. [Pinned upstream documentation](https://raw.githubusercontent.com/hawolt/guardian-of-the-rift/d804cd5a18246924be74335abe25f7895510608f/README.md).

- **partial** — Activity facts, resource guidance and current altar support.
- **missing** — Optimal/secondary obelisk choice across optimization modes.
- **missing** — Arrival-aware tick-perfect portal and altar timers.
- **missing** — Potential-point optimizer and full menu conveniences.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 125. Gauntlet Map

Active installs: 63,304. [Pinned upstream documentation](https://raw.githubusercontent.com/StickySerum/gauntlet-map/25d36de0cd6a2cd319bd2721885325b81c6756f4/README.md).

- **missing** — Discovered Gauntlet-room map with player, resources and demi-bosses.
- **missing** — Room/node map style controls.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `TileMarkersTool.java`.

### 126. Tree Count

Active installs: 62,847. [Pinned upstream documentation](https://raw.githubusercontent.com/Infinitay/tree-count-plugin/9e4d26f772b82cabab8fc2883392116ba95b22a7/README.md).

- **missing** — Per-tree active-chopper counts with animation-stall reconciliation.

Inspected Runewarden files: `NodeTimerRules.java`, `NodeTimerOverlay.java`, `SkillingCoach.java`, `RadialTimer.java`, `ContextActions.java`.

### 127. Clue steps

Active installs: 62,475. [Pinned upstream documentation](https://raw.githubusercontent.com/SkyBouncer/clue_step_tracker/9b366ffa212721b35462fe13bea8d642e6f4abf3/README.md).

- **missing** — Per-clue completed-step counts across clue tiers.
- **missing** — Configurable certainty colours for clue-step estimates.

A current clue-part instruction is not the number of scroll steps already completed.

Inspected Runewarden files: `ClueDataService.java`, `ClueCoachTool.java`, `ClueAccessIndex.java`, `ClueJugglingTool.java`, `ClueJugglingTracker.java`, `ClueJugglingOverlay.java`.

### 128. Tasks Tracker

Active installs: 62,417. [Pinned upstream documentation](https://raw.githubusercontent.com/osrs-reldo/tasks-tracker-plugin/dbdfdaddbdc09592e54f90056a5397df63f4e88a/README.md).

- **missing** — Mirror, search, filter and pin the native Tasks interface outside the game UI.
- **missing** — Generic native-task export for external services.

Diary and CA planners are distinct datasets from the upstream generic Tasks interface.

Inspected Runewarden files: `DiaryPlannerTool.java`, `CombatAchievementsTool.java`, `GoalPlannerTool.java`.

### 129. Bank Memory

Active installs: 61,859. [Pinned upstream documentation](https://raw.githubusercontent.com/Lazyfaith/runelite-bank-memory-plugin/036fc3b672aeae93d73ec4bbcd713587d86eea32/README.md).

- **implemented** — Named bank snapshots with persistent per-character history.
- **implemented** — Search and page saved item contents.
- **implemented** — Compare snapshot item quantities and historical GE values.
- **implemented** — Export bank snapshot item data as JSON, CSV and TSV.
- **partial** — Automatic current-bank observation and retention.
- **missing** — Browse all saved accounts while offline without changing the active character.
- **missing** — World-mode-separated current bank histories.
- **missing** — High Alchemy valuation and inventory bank-count hover tooltips.

BankMemoryTool adds eight manual named snapshots per profile, explicit open-bank comparison, repricing-separated diffs and exports. OwnershipLedger supplies last-observed ownership, but there is no full automatic upstream-style multi-account bank archive.

Inspected Runewarden files: `BankMemoryTool.java`, `BankMemorySnapshot.java`, `BankMemoryDiff.java`, `OwnershipLedger.java`.

### 130. Fish Barrel

Active installs: 60,924. [Pinned upstream documentation](https://raw.githubusercontent.com/molo-pl/runelite-plugins/6aaa5993260cce2d2f46d74325071050f2382b2b/README.md).

- **implemented** — Observe barrel raw-fish counts from native checks and confirmed changes.
- **implemented** — Dedicated carried/equipped barrel-count HUD with item icons and display toggles.
- **partial** — Complete catch, fill and inventory reconciliation for every fish source.
- **missing** — Original inventory-slot overlay placement and alternate source display styles.

ContainerHudTool/FishBarrelReading keeps unknown starting or ambiguous counts unknown. A Check requires a newly loaded response; a Fill click alone never proves contents. Banked barrels remain unknown, and per-fish historical composition is not supplied.

Inspected Runewarden files: `ContainerHudTool.java`, `ContainerHudRules.java`, `FishBarrelReading.java`, `ContainerHudOverlay.java`.

### 131. CoX Light Colors

Active installs: 59,901. [Pinned upstream documentation](https://raw.githubusercontent.com/AnkouOSRS/cox-light-colors/01d10d1ec55a8b6d379e77085659d5d5f8e49210/README.md).

- **missing** — Chambers reward-chest light recolouring.

Inspected Runewarden files: `RewardNotifications.java`, `RewardNotification.java`, `RewardPopupOverlay.java`, `RewardSoundboard.java`.

### 132. Corner Tile Indicators

Active installs: 59,544. [Pinned upstream documentation](https://raw.githubusercontent.com/geheur/Corner-Tile-Indicators/05b27f1847aeca9ce690675710b4e87710dca447/README.md).

- **missing** — Corner-only player and destination tile rendering.

Inspected Runewarden files: `TileMarkersTool.java`, `GuideMarkerOverlay.java`, `RunewardenSightOverlay.java`.

### 133. Player Outline

Active installs: 58,772. [Pinned upstream documentation](https://raw.githubusercontent.com/Adam-/runelite-plugins/2effa2c2d2900696679488294cb453af94149d22/README.md).

- **missing** — Local player model outline with width and feather controls.

Inspected Runewarden files: `HudSettingsTool.java`, `RunewardenSightOverlay.java`.

### 134. Pickpocket Helper

Active installs: 57,603. [Pinned upstream documentation](https://raw.githubusercontent.com/pajlads/runelite-pickpocket-helper/15840d787ca06b705677465b01f19a8d4b11a8b7/README.md).

- **partial** — Training method and generic idle guidance.
- **missing** — Pickpocket-specific stun, failure and success audio/overlays.
- **missing** — Pickpocket-focused item and interaction indicators.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 135. Delve Calculator

Active installs: 57,459. [Pinned upstream documentation](https://raw.githubusercontent.com/Speaax/Delve-calculator/91b874df7470728e8ae71e7870e90c667e95a66d/README.md).

- **missing** — Depth-completion-based Delve expected-drop calculations.
- **missing** — Observed versus expected Delve luck analysis.

Inspected Runewarden files: `RarityCatalog.java`, `RewardNotifications.java`.

### 136. Odablock Sounds

Active installs: 57,348. [Pinned upstream documentation](https://raw.githubusercontent.com/DapperMickie/odablock-sounds/92023b148ecf7641b475034cf1f0636d60d46737/README.md).

- **partial** — Configurable local event sounds.
- **missing** — Upstream voice-pack coverage and achievement triggers.
- **missing** — Novelty player-kill and snowball announcement systems.

Inspected Runewarden files: `RewardNotifications.java`, `RewardNotification.java`, `RewardPopupOverlay.java`, `RewardSoundboard.java`.

### 137. Instant Inventory

Active installs: 56,975. [Pinned upstream documentation](https://raw.githubusercontent.com/elgbar/instant-inventory/0fa9175dc799fc69f02d8bc35217300968e956e8/README.md).

- **missing** — Immediate local feedback for inventory equip and item interactions.
- **missing** — Immediate bank, prayer and special-bar interaction presentation.

The upstream feature is about local interface responsiveness. Its name alone is not evidence of automation, and no prohibition is asserted here.

Inspected Runewarden files: `HudSettingsTool.java`, `HudPreferences.java`, `HudStyle.java`, `HudResizeController.java`, `InventoryPreviewOverlay.java`, `GuideCoachOverlay.java`.

### 138. Did I Compost?

Active installs: 56,588. [Pinned upstream documentation](https://raw.githubusercontent.com/AbusiveTuna/DidICompost/d88c635e42951be266253079a4ba6df51757a390/README.md).

- **implemented** — Overlay the exact native farming patch after confirmed compost application.
- **partial** — Persist observed treatment across every crop reset and patch lifecycle.

FarmCompostEvidence requires an exact selected native patch and matching server confirmation. Planned/carried compost does not count. Old-session evidence remains last seen until revisited rather than proving treatment of a new crop.

Inspected Runewarden files: `HarvestClockTool.java`, `HarvestClockOverlay.java`, `HarvestTimerWindows.java`, `HarvestSelections.java`, `FarmRunsTool.java`, `FarmTravelPlan.java`, `FarmPatchCatalog.java`, `FarmGrowthTiming.java`, `FarmPatchPlanner.java`, `FarmGuideProgress.java`, `TrainingGuideProgress.java`, `GuideLiveProgress.java`, `FarmPatchDestinations.java`, `FarmRunNavigation.java`, `FarmPatchScene.java`, `FarmCompostEvidence.java`, `FarmPatchOverlay.java`.

### 139. Camera Smoothing

Active installs: 56,419. [Pinned upstream documentation](https://raw.githubusercontent.com/ArtsicleOfficial/camera-smoothing/8212cde2b946665d44582969075f6827c5374c36/README.md).

- **missing** — Smoothed yaw, pitch and zoom motion.
- **missing** — Adjustable camera smoothing strengths.

Inspected Runewarden files: `RunewardenPlugin.java`.

### 140. Raid Data Tracker

Active installs: 56,333. [Pinned upstream documentation](https://raw.githubusercontent.com/Raitab/raid-data-tracker/963f3c4f6ccbc6a8342d51a321165336bc39cd36/README.md).

- **partial** — Shared local activity/run history and loot totals.
- **missing** — Per-raid personal/team points and split-aware histories.
- **missing** — Raid-specific loot, luck and dry-streak accounting.

Inspected Runewarden files: `RunewardenGuides.java`, `BossGuideLifecycle.java`, `EncounterPracticeTool.java`, `SharedActivitySession.java`.

### 141. Shooting Star Info

Active installs: 56,165. [Pinned upstream documentation](https://raw.githubusercontent.com/pwatts6060/runelite-plugins/38fc77770ad0a5c07e74e7491975a90e40e62173/README.md).

- **partial** — Authored star-mining training guidance.
- **missing** — Native star tier, remaining-percent and active-miner displays.
- **missing** — Stardust-rate, visibility alerts and location-sharing text.

Inspected Runewarden files: `NodeTimerRules.java`, `NodeTimerOverlay.java`, `SkillingCoach.java`, `RadialTimer.java`, `ContextActions.java`.

### 142. Tzhaar/Colo Hp Tracker

Active installs: 55,755. [Pinned upstream documentation](https://raw.githubusercontent.com/bopsec/buchus-plugins/464672cf74f767febb8762296fc93cf250d3605a/README.md).

- **missing** — Wave-NPC health overlays across Caves, Inferno and Colosseum.
- **missing** — Per-NPC alive/dead menu and health-style options.

Inspected Runewarden files: `RunewardenSightTool.java`, `RunewardenSightOverlay.java`, `MinigameSceneOverlay.java`.

### 143. Fashionscape

Active installs: 54,491. [Pinned upstream documentation](https://raw.githubusercontent.com/equirs/fashionscape-plugin/6646061a26ac74ede28f353c49a640ecf0fc171b/README.md).

- **missing** — Local-only equipment and base-model appearance overrides.
- **missing** — Appearance search, filters and colour matching.
- **missing** — Appearance loadouts, locking and randomized outfits.

Inspected Runewarden files: `RunewardenPlugin.java`, `HudSettingsTool.java`.

### 144. Wintertodt Notifications

Active installs: 53,118. [Pinned upstream documentation](https://raw.githubusercontent.com/erwinvc/runelite-plugins/7d2add3dd74a6d6f7e9c2883b3049c80443eee8e/README.md).

- **partial** — Wintertodt native facts and activity guidance.
- **partial** — Training-idle reminders.
- **missing** — Full interruption and completed-action visual notification rules.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 145. Vineyard Helper

Active installs: 53,068. [Pinned upstream documentation](https://raw.githubusercontent.com/SurfaceCode/vineyard-helper/4aa71651b13003133ff9792642acf5f2815acc04/README.md).

- **missing** — Pickable grapevine highlights based on barrel capacity.

Inspected Runewarden files: `TrainingProgressTool.java`, `VarlamoreThievingCoach.java`, `VarlamoreThievingState.java`, `VarlamoreThievingHudOverlay.java`, `VarlamoreThievingSceneOverlay.java`, `AgilityCoach.java`, `AgilityCourseCatalog.java`, `AgilityObstacles.java`, `AgilityTrainingSession.java`, `AgilityHudOverlay.java`, `AgilitySceneOverlay.java`.

### 146. Deep Sea Trawling

Active installs: 52,616. [Pinned upstream documentation](https://raw.githubusercontent.com/CarelessEsper/deepSeaTrawling/5841a0abd8629e5237bcf718f7f2501fb3366752/README.md).

- **missing** — Moving shoal paths, stops and depth-change countdowns.
- **missing** — Net-depth mismatch and full-net indicators.
- **missing** — Special shoal colour and catch-quantity overlays.

Inspected Runewarden files: `SailingXpScenarios.java`, `TrainingProgressTool.java`, `ActivityXpTool.java`, `SailingCoach.java`, `SailingCoachRules.java`, `SailingCoachTasks.java`.

### 147. Fishing Notifier

Active installs: 52,194. [Pinned upstream documentation](https://raw.githubusercontent.com/Bram91/fishing-notifier/957a15e80268bbfd249adc987e24108c395ce0cc/README.md).

- **partial** — Supported fishing training-session idle alert.
- **missing** — Fishing-specific interruption coverage independent of a selected plan.

Inspected Runewarden files: `SupplyAwarenessTool.java`, `TrainingProgressTool.java`, `HarvestClockTool.java`, `RewardNotifications.java`.

### 148. Damage Counter

Active installs: 51,085. [Pinned upstream documentation](https://raw.githubusercontent.com/0anth/damage-counter/d69450bf84a738c696fa71591cd3a9700562f236/README.md).

- **missing** — Observed per-target damage and elapsed-fight DPS.
- **missing** — Party damage sorting and boss-death damage summary.

Simulated loadout DPS is not an observed damage counter.

Inspected Runewarden files: `DpsCalculator.java`, `CombatRollProbability.java`, `DpsSpecialAttack.java`, `DpsTargetScenario.java`, `CurrentMaxHit.java`, `CombatCalculatorTool.java`, `ConsumablePreviewOverlay.java`.

### 149. Companion Pet Plugin

Active installs: 50,769. [Pinned upstream documentation](https://raw.githubusercontent.com/Mrnice98/Companion-Pets-Plugin/19abee1616aa78f5c5402f5f9badd06b81cd3c38/README.md).

- **missing** — Local-only companion pet and transmogrification previews.
- **missing** — Pet favourites, sizing and thrall appearance replacements.

Inspected Runewarden files: `RunewardenPlugin.java`, `HudSettingsTool.java`.

### 150. Gnome Restaurant

Active installs: 50,333. [Pinned upstream documentation](https://raw.githubusercontent.com/hex-agon/gnome-restaurant/0f7d86f89bbc9b8790ace110d425c2c043fcfe6a/README.md).

- **missing** — Live recipe ingredient overlay for an assigned delivery.
- **missing** — Order and assignment-delay timers.
- **missing** — Recipient arrows, map markers and route integration.

Inspected Runewarden files: `MinigameActivityCatalog.java`, `MinigameNativeObjectives.java`, `MinigameFoundry.java`, `MinigameHomes.java`, `MinigameTelekinetic.java`, `MinigameCoach.java`, `MinigameRunProgress.java`, `MinigameGuideProgress.java`, `MinigameActivityExit.java`, `MinigameGuideSession.java`, `GuideLiveProgress.java`, `RunewardenGuides.java`, `RunewardenPlugin.java`, `GuideBankFilter.java`, `MinigameFieldEvents.java`, `MinigameCountdowns.java`, `MinigameHudOverlay.java`, `MinigameSceneOverlay.java`.

### 151. Tick Tracker

Active installs: 50,324. [Pinned upstream documentation](https://raw.githubusercontent.com/Tatters654/tick-tracker/a1c5d170529f54e78d52f67874458c27329f88a6/README.MD).

- **partial** — Observe generic server-tick cadence for the training HUD.
- **missing** — Good-tick percentage and configurable lag thresholds.
- **missing** — Login warm-up exclusion and tick-lag warning history.

The CLICK/dot metronome is not a network-health calculation.

Inspected Runewarden files: `ModuleRuntime.java`, `SkillingCoachOverlay.java`.

## Rebuild

```sh
python3 tools/fetch-hub-audit-sources.py --output-dir /tmp/runewarden-hub-evidence
python3 tools/build-hub-coverage-audit.py --source-dir /tmp/runewarden-hub-evidence
```

The repository retains the signed metadata, pinned native certificate and text-source indices in `docs/hub-source-snapshot`. The fetcher reconstructs the metadata and downloads only hash-verified pinned text at no more than eight workers. The generator verifies the signed manifest, pinned README/Java hashes, threshold coverage, source file hashes and evidence references. It writes `hub-inventory.json` and this report together with the machine-readable `hub-utility-audit.json`. Original third-party READMEs are not bundled into the plugin. `python3 tools/build-hub-coverage-audit.py --check-evidence` verifies the retained source/test hashes and complete baseline coverage offline.
