# Bundled guide sources

Runewarden retains account-independent guide references, metadata and marker packs as checked-in resources. Original guide arrangement, UI and navigation code are GPL-3.0-only. Data retain their individual notices in `src/main/resources/com/runewarden/licenses/`; those terms are not replaced by the code license.

The catalogue contains **545 non-quest guide entries** and **238 quest metadata rows**. Quest metadata supports requirements, account status, ordering and planning links. It contains no bundled quest walkthrough steps, step-specific supplies or quest marker packs. Quest walkthroughs belong to the separately installed Quest Helper; this plugin-only repository does not include the former development adapter.

## Source provenance

| Content | Source snapshot | Terms and scope |
| --- | --- | --- |
| Quest names, requirements and metadata | [Quest Helper 5f791937](https://github.com/Zoinkwiz/quest-helper/tree/5f791937a99a676f23c02aa48c4e4d5b50e7edad), 14 October 2026, and individually attributed supplements | BSD-2-Clause; Wiki supplements CC BY-NC-SA 3.0. Walkthrough payloads were removed. |
| Training and introductory minigame guides | Curated Runewarden reference snapshots, with source links in individual entries | Original GPL-3.0-only checklists; linked reference material retains its own terms. Guide entries and XP-calculator methods are separate catalogues. |
| Community tile-pack references | [RuneMarkers 063b553b](https://github.com/jamiegyoung/runemarkers/tree/063b553bce36522fb2af54555c5afa2737a1e55f), 15 October 2026 | MIT, Jamie Young; upstream attribution is retained. |
| Hueycoatl positions and timing cues | [Huey Helper 2920826a](https://github.com/Jufinda/Huey-Helper/blob/2920826a73fde71efda4814625225a92be6aa06c/src/main/java/com/hueyhelper/HueyHelperPlugin.java#L114-L126), 29 September 2026 | BSD-2-Clause, Jufinda. |
| Inferno wave reference facts | [Inferno LoS 2b9b162b](https://github.com/ifreedive-osrs/ifreedive-osrs.github.io/blob/2b9b162bdafdb07507831f9dba026172a9d33ea4/index.html) and [We Do Raids d3145cac](https://github.com/perrylzx/perrylzx.github.io/blob/d3145caced686d84cf3ad8bfcd4ac663ceda6707/index.html) | Original summaries and factual coordinate derivation; source attribution retained. |
| Fight Caves waves and rotation references | [Spawn Predictor 8c5a34d4](https://github.com/damencs/spawn-predictor/tree/8c5a34d4c88407cda7a6c60d8014018163d97974) | BSD-2-Clause, Damen. SPAWN markers identify enemy origins, not player standing tiles. |
| Colosseum composition and timing facts | [OSRS Wiki](https://oldschool.runescape.wiki/w/Fortis_Colosseum) | Original factual summaries with source links. |
| Colosseum pillar position references | [Tile Packs 8091ee84](https://github.com/TrevorMDev/tile-packs/tree/8091ee842642c34fb88bb79c61e104500e91bd96), We Do Raids pack entry | BSD-2-Clause; manually selected standing references. |
| Encounter preparation and practice lessons | Curated Runewarden resource entries with individual primary references | Original GPL-3.0-only standing, movement and practice checklists. These are not universal live encounter solvers. |
| Guide and farming arrival coordinates | Pinned [Shortest Path 37abff3a](https://github.com/Skretzo/shortest-path/tree/37abff3a3345185fc11fccecae04d8170de25a11), Quest Helper source above and RuneLite 1.13.2 world-map facts | BSD notices and retained coordinate evidence accompany `guide-travel-*.json` and `docs/guide-travel-source/`. |

Tile numbers refer to positions within the selected source pack. Existing source labels, including unsafe tiles, are preserved. Switching a selected pack changes Runewarden's own overlay; it does not delete personal Ground Markers or rewrite another plugin's marker configuration. Static wave briefings do not resolve every random spawn arrangement. Player standing and boss-phase lessons remain manually selected.

## Guide progress and navigation

Native minigame guides open through an explicit player action and use supported current-state observations while open. A score or phase does not establish every objective or terminal result; unsupported and open-ended work retains manual completion controls.

Guide preparation travel uses the separately installed and enabled Shortest Path plugin's public local message contract. Selecting a supported guide or reaching its travel step can request a route automatically; there is no separate route button. The arrival catalogue contains **94 sourced points covering 112 training/minigame guide IDs**, not a destination for every guide. Farm runs use their own patch resolver.

Runewarden validates current position, profile and route ownership, and preserves a route the player replaces manually. Encounter standing instructions do not become automatic movement routes. Most instanced origins remain unavailable; a verified own-house template in building mode supports the documented virtual POH origin. Displaying a route never performs walking or teleport actions.

Quest Helper at the pinned revision has no incoming public `PluginMessage` API for starting a helper. This repository neither invents that API nor bundles substitute quest walkthroughs. Quest planning remains available even when the external helper is absent.

## Resource maintenance

The build consumes checked-in resources directly. `docs/data-manifest.json` records dataset fingerprints; `tools/validate_bundle.py` checks them. Preserve each dataset's source revision, license and reproducible preparation notes when changing it. Historical export-script paths in retained provenance are not build commands for this repository. Removed quest-stage generators do not form part of the current build.

No executable guide code is scraped or downloaded inside RuneLite. Optional item-source articles use the separately documented, explicitly consented Wiki lookup in `docs/item-database.md`.
