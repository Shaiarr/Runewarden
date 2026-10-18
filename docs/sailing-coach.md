# Native Sailing coach

The Sailing module now has a live, movable Runewarden HUD, scene highlights and current courier-task planning. It reads native boat/world-entity state. A child world view alone does not activate the Sailing HUD: its current entity must match the player's actual world view and be one of the three verified boat types.

## Implemented coverage

| Area | Current behavior |
| --- | --- |
| Navigation | Sixteen native headings; observed displacement each game tick, rounded to quarter tiles; world-coordinate normalization across scene-base changes. First-sample speed remains unknown. |
| Boat status | Native current/max hull HP and wind charges; configurable hull percentage warning; repair highlight on the current boat's observed hull. Impossible native counts remain unknown. |
| Sails | Thirty observed sail patterns across raft, skiff and sloop. A sail highlights only while its actual native trim action is available. |
| Rapids | Seventy-four native rapid object forms in the current top-level scene. |
| Cargo hold | Sixty-three hold forms covering seven tiers and three sizes, including Ultimate Ironman capacities. Counts use the visible native hold container and item stackability. Current courier crates are excluded from consumed slots. Missing, closed, conflicting or unsupported data remains unknown. |
| Courier tasks | All five native current-task slots; native DB rows for collection/delivery ports, cargo item and required count; collected/delivered counters; pickup → delivery → ledger objective. Thirty independently identified ports, including distinct Sarim and Khazard row IDs. |
| Courier navigation | Own world-map marker for the selected task's next port; current scene's matching ledger highlight; explicit button hands its destination to Runewarden Travel. It does not compute a sea collision route. |
| Barracuda trials | Exact native active difficulty and unambiguous master-state identity for Tempor Tantrum, Jubbly Jive and Gwenith Glide. Current native progress; observed-start elapsed time and progress splits; completion requires an observed native completion-count increase. Enabling mid-run never invents a start timestamp. |
| Trial objectives | Tempor rum pickup/delivery from its actual HUD sprite; Jubbly empty outcrops from actual child sprites, with final-Jubbly position filtering; visible native lost-supply morphs for the three trials. |
| Customization | Independent HUD, scene-label, trim, repair, rapid, trial-target, map-destination and cargo toggles; compact mode; 70–150% own scale; 5–75% repair threshold; selected task; common HUD editor style and lock. |

Snapshots and map markers clear on logout, world or account change and module shutdown. Render and route handoff recheck account/world identity. Settings are validated by `RunewardenDataStore` and belong only to `runewardensailing`. Scene observations are identity-based, bounded to 384 recognized objects, scoped to the current top-level/player world views, and invalidated for scene, base or plane changes. Native task DB lookups are cached and unresolved rows retry at most every ten ticks.

## Explicit limits

This does not claim full parity with any of the three upstream plugins. Missing areas include Sailing crew-overhead filtering, visibility modes, helm/menu convenience controls, remembered cargo while its interface is closed, offered/bounty-task boards, task-reward optimization, an in-world navigable sea route, and Barracuda A* hazard avoidance, static full-course waypoints, lightning/fetid-pool projections or trial personal-best history. Gwenith support currently covers native progress, timing and visible trial supplies. No game action, steering input or other plugin's settings are automated.

The tests exercise real native identity boundaries, DB task transitions/cache/retry, malformed and ambiguous data, cargo capacity/count rules, current-boat-only highlights, trial start/split/completion evidence, final-Jubbly target selection, profile/world isolation, destination lifecycle and canvas fit. A logged-in Sailing run on the dev client remains a separate manual validation step.

## Primary sources and attribution

Retrieved 17 October 2026 from the official Plugin Hub pinned repositories:

- [Sailing](https://github.com/LlemonDuck/sailing/tree/af987f3be93bf0ca666af3d1f111cbf91e6a4d0d): `SailingUtil`, `BoatTracker`, `Boat`, `HullTier`, `CargoHoldTier`, `NavigationOverlay`, `LuffOverlay`, `CargoHoldTracker`, `CourierTaskTracker`, `BarracudaDifficulty`, `BarracudaSplitsTracker`, `TemporTantrumHelper`, `JubblyJiveHelper`, `LostCargoHighlighter`, `RapidsOverlay`.
- [Port Tasks](https://github.com/coopermor/port-tasks/tree/0e13530c77b8c599db9d880330022920759d74c1): `PortLocation` native port row IDs, ledger IDs and map locations. This mapping avoids the separate Sailing source's Sarim/Khazard row alias.
- [Barracuda Trials Pathfinder](https://github.com/NathanVegetable/barracuda-trial/tree/b05a9f833d59e6b60db0ed3cc1a9265e6d5573cf): `ProgressTracker` active master-state mapping. Its pathfinding algorithm is not included.
- RuneLite native gameval definitions: hull max/current varbits 19177/19181; wind current/max 19178/11629; current task triples 19574–19588; trial difficulty 18410 and master states 18427/18432/18437; trial start varp 4987; completion counters 4998–5000; visible cargo inventory varp 5204.

BSD notices are shipped in `com/runewarden/licenses/sailing-coach-BSD.txt`.
