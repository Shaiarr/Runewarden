# Runewarden 0.8.0 readiness

This is an integrated development candidate toward 1.0. It substantially deepens
the existing command centre; it does not claim complete specialist parity with
every Hub plugin or approval from RuneLite/Jagex.

| Area | Implemented in this candidate | Remaining precise boundary |
| --- | --- | --- |
| Quests | Conditional source branches, current-step NPC/object references, item cards/bank focus, native stage following and manual review | Unknown conditions block automatic selection; unsupported specialised puzzle classes remain manual. See generated quest-assistance coverage. |
| Training | 340 explicit plans, 24 skills, 115 reference-rate plans in 17 skills, 118 XP/action models, level-band estimates, unlock checks and personal pace | Attack/Strength/Defence/Hitpoints/Ranged, Slayer and Sailing use personal pace/exact rewards; no universal invented rate. |
| Minigames | Twenty contexts plus Foundry mould/heat workbench, loaded Telekinetic shortest-pull solving, twelve confirmed Homes customers/routes and chosen roles/methods | Foundry heat estimates exclude travel; Homes materials are contract ranges. Other contexts retain their documented native objectives and written lessons. |
| Combat and practice | Twenty-five direct-damage special profiles, explicit target variants, manual reductions/HP snapshots, preparation to attempt/review/recovery | Raid scaling, poison/venom, thralls, enchanted bolts, delayed/healing special effects and BGS overflow remain unsupported. Snapshot/special comparisons do not claim sustained full-fight TTK. |
| Slayer | Thirty-two sourced methods, master/task filters, corrected block/reward costs, native storage observations, skip/reset/boost and cape scenarios, personal samples | Individual boss-sublist odds and Mortimer acquisition rules stay unknown without verified rules. |
| Farming | Crop/patch planning with per-stop travel, reusable/consumed supplies, shared charges, fees, access and bank focus | Hidden charge amounts are explicit player budgets; no inference from an unreadable container. |
| Items and loadouts | 4,538 offline item IDs and 22,926 additional cards through 64 lazy shards; source parsing; variants, doses and optional positions; native visible potion storage | Snapshot coverage is direct acquisition/requirements, not a universal recursive crafting graph. Potion-store doses only use recognised exact native rows. |
| Connected journeys | Dashboard activity launch, Back/Home/Forward with scroll restoration, guide/item/bank links, matching combat target and saved practice context | A complete logged-in end-to-end acceptance run remains outstanding. |
| Engineering | Full Gradle/JUnit, parser regressions, data fingerprints, production JAR inspection, Linux/Windows CI, bounded cache probe, process measurement and release gates | Local headless checks cannot certify actual Windows/gameplay stability, conflicts, FPS or memory savings against other plugins. |
| Publication | Git-ready source, standard Hub build metadata, exact clean-commit marker/review generator and matching source package | A dedicated public repository, actual Hub review/merge and live acceptance evidence are still required. |

The machine-readable `live-acceptance.json` keeps ten live/review gates explicitly
unverified until real evidence is recorded. `tools/release_gate.py` reports actual
JUnit results and blocks public 1.x readiness if mandatory gates are missing,
unverified or unsupported by evidence. An empty file or wholly skipped test run
cannot manufacture a pass.

Network item lookups remain default-off, with the Wiki IP/article-title disclosure
and revocation guards. Account information stays local. No website integration,
game input automation, automatic player-standing solver or live arena export is
added. The existing documented Fight Caves first-wave NPC spawn origins, manual
boss packs, static combat references and all twenty native activity helpers stay.

The current automated evidence is produced in `build/reports/` by the release
procedure. Historical 0.7.x audits remain as records of those versions; use this
document and the current module documents to assess 0.8.0.

The recorded local run passed **1,130 Java tests across 148 suites**, **26 Python
checks**, the production-JAR inspection and **94 dataset fingerprints**. Native
sidebar fixtures rendered 37 images. The 316-file Windows runtime matched every
pinned hash. See `automated-evidence-0.8.0.json`; Windows CI and real gameplay were
not executed by that local headless run.
