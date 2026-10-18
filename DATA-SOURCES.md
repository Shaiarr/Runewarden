# HUD data sources

Runewarden HUD 0.10.0 contains four account-independent JSON reference resources. It reads supported live observations through RuneLite's public APIs. The original Java/UI implementation is BSD-2-Clause; third-party source and data keep their own notices and terms. See [NOTICE](NOTICE) and [`src/main/resources/com/runewarden/licenses/`](src/main/resources/com/runewarden/licenses/).

| Retained material | Primary source | Attribution and scope |
| --- | --- | --- |
| `action-recipes.json` | RuneLite 1.13.2 skill-calculator action enums; individually identified Wiki potion facts | Exact item transformations, levels and XP for action detection and hovers. Native BSD notices are in `runelite-hud-reference-BSD-2-Clause.txt`; Wiki portions retain `wiki-reference-CC-BY-NC-SA.txt`. This is not a banked-XP planner. |
| `skilling-resource-details.json` | Per-entry OSRS Wiki pages/revisions and native RuneLite gathering/cooking facts | Resource uses and farming-stage/payment facts. Wiki-derived portions retain CC BY-NC-SA 3.0 attribution; native definitions retain BSD notices. |
| `farming-native-snapshot.json` | RuneLite 1.13.2 `FarmingWorld`, `PatchImplementation` and `Produce` | Native patch/crop/state facts, covered by `runelite-farming-data-BSD-2-Clause.txt`. Observed timer estimates are separate from these static references. |
| `poh-travel-portals.json` | [Shortest Path 1.21.3 at 37abff3a](https://github.com/Skretzo/shortest-path/tree/37abff3a3345185fc11fccecae04d8170de25a11) | Portal names/object IDs and relevant transport-item IDs. Exact source file SHA256 pins remain in the JSON; full BSD terms remain in `harvest-travel-BSD-2-Clause.txt`. The companion pathfinder is not embedded in the Hub plugin. |

Retained source-level facts also have explicit notices:

- Farm destinations use RuneLite 1.13.2 `FarmingPatchLocation` map markers. `FarmPatchDestinations.java` preserves its full source header; the binary notice is repeated in `harvest-travel-BSD-2-Clause.txt`.
- Farming clock alignment, native birdhouse observations, compost correlation, gathering-node timing, effect durations, consumable projections and daily claims retain the applicable native BSD notices. These records do not imply every crop, resource or activity is supported.
- Varlamore observations retain [Benjamin Zeigler's pinned MIT source](https://github.com/sololegends/runelite-varlamore-house-thieving/tree/ed8c58e1e9dbc73508187c1d88000c61115aee6e) plus separately attributed Wiki timing facts.
- `CombatSpellCatalog` holds 42 numeric autocast spell facts, with the exact [Wiki DPS source commit](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/cdn/json/spells.json) in `combat-spells-attribution.txt`. The upstream GPL-3.0 license document is preserved as `mob-knowledge-upstream-GPL-3.0.txt` for provenance. No upstream calculator implementation is included. Supported normal base-max-hit arithmetic and its reference scope are described in `max-hit-reference-attribution.txt`.
- Action timing sources and revisions remain in `action-progress-attribution.txt`. Generic Wiki attribution does not replace individual per-entry source links or third-party licenses.

The broader prototype's guide, quest, encounter, Slayer, DPS/monster/equipment, minigame, world-hopper and reward datasets/assets are absent. No executable reference code is downloaded by the running plugin.

When changing data, retain source revisions, license terms and preparation notes. Inspect the source and data changes before regenerating `docs/data-manifest.json` with `python tools/validate_bundle.py --write`; validation consumes the checked-in resources directly. Unknown native observations must remain unknown rather than becoming invented completion or timing facts.
