# Changes

## 0.9.6

- Complete the Runewarden rename across Java packages and class names, the plugin entrypoint, configuration groups, resources, datasets, tools and documentation.
- Use `com.runewarden.RunewardenPlugin` and the `runewarden` primary configuration namespace. Previous test settings are not automatically migrated.
- Refresh data fingerprints, source evidence and the plugin-only GitHub upload package.

## 0.9.5

- Add an integrated World hopper with country/US-coast flags, world/activity search, membership/region/type filters, character favourites and six reversible sorts. Provide explicit row/previous/next/return actions, configurable quick-hop keys, `::rwhop` and friends/clan Hop-to entries, with fresh entry checks and special-world warnings before native confirmations.
- Add bounded TCP world-latency measurements and an optional movable current-world ping/retransmission HUD. Keep manual hopping separate from background list and connection updates.
- Skip unavailable or stale transformed NPC definitions in Runewarden's Varlamore scene overlay.
- Read the native completed Shortest Path result directly instead of sending a second query request. Keep one pending summary and coalesce capability-driven replans, retaining navigation and HUD functionality.
- Plan farm preparation against eligible native patch destinations through Shortest Path's actual transport costs and current account state. Preserve an active custom run's next stop and order.
- Refresh route inputs from the current position and native travel capabilities; replan owned routes after long position changes or relevant capability changes. Farm travel advice now displays the accepted native transport chain instead of fixed regional teleport text.
- Add character-specific My POH declarations and an explicit Use my POH for routing control. Scan portal evidence only in a verified own house in building mode; visited nexus menus remain suggestions. Carried/worn teleport access and confirmed house choices can produce the native Construction cape → POH → Catherby route.
- Capture action titles, icons and skills together from native operations or matching inventory/XP transformations. A selected training/calculator method and an ambiguous gem animation no longer label unrelated work.
- Hide confirmed finished batches and their matching action cycles, reset counts for fresh materials and suppress generic bars for unrecognized non-production animations.

## 0.9.4

- Open native minigame guides only through an explicit player action, immediately at the current native objective, including work completed before opening. Follow confirmed objective changes while the guide remains open.
- Dismiss the native minigame guide on activity exit, confirmed completion or Close guide; clear its owned guidance and prevent automatic reopening.
- Keep native progress for the current activity cycle separate from saved manual checklists, so an old completed checklist does not hide an active new cycle.
- Connect the selected guide to current minigame objectives, farm-run progress and attributed training progress while preserving profile, selection and dismissal guards.
- Make Giants' Foundry recognize loaded alloy, selected moulds and carried/stored preforms when opening its guide. Follow the current heat/tool objective; finished progress leads to hand-in, and only confirmed reward acceptance completes the guide.
- Keep open-ended training and subjective boss lessons manual where no explicit goal or supported native completion observation establishes a final step. Quest walkthroughs remain with Quest Helper.

## 0.9.3

- Show native item, spell or skill icons beside action progress, with confirmed recipe outputs and resource-specific action references where available.
- Render the crop's actual icon above farming patches, including separate Palm and Curry saplings. Align remaining growth estimates with native Time Tracking cycle offsets and retain earlier same-stage observations; display hours and minutes rather than an unexplained whole-stage minute range.
- Resize Runewarden HUD cards by holding Ctrl and dragging an edge or corner. Retain separate Alt-drag positioning, saved per-character sizes, lock controls and reset.
- Remove Runewarden's quest walkthrough, branch following, quest interaction highlights and quest-owned routes. Keep account quest state, requirements, optimal ordering and planning links; open Quest Helper for the selected quest instead.
- Include the unchanged Quest Helper 4.18.0 development-client companion at official Hub source commit `5f791937a99a676f23c02aa48c4e4d5b50e7edad`. The main Plugin Hub JAR has no typed Quest Helper dependency.
- Dismiss an active boss lesson and its owned HUD/markers/route after leaving its observed arena or local-player death. Keep pre-entry lessons and between-kill guidance open; unsupported arena boundaries remain manual.
- Start the bundled Shortest Path companion automatically with Runewarden in the development client. Route supported guides without a separate route button, retry while the client or receiver is not ready, and supply 94 sourced arrival points covering 112 training/minigame guide IDs instead of relying on test-only destinations.
- Route active farm runs to their first and next unfinished stop automatically. Use saved pins, observed patch anchors or sourced native farm-area markers; pause, End run, profile changes and completion clear the owned route. Preserve manually replaced routes.

## 0.9.2

- Bundle/register the actual Shortest Path development-client companion, pinned to the recorded official Hub source commit, with full resources and BSD notice. Production Hub JAR integration remains through public plugin messages.
- Automatically route selected sourced guide steps to their location, matching loaded NPC or object. Confirm receiver acceptance; preserve manual replacement routes and clear guide-owned routes on dismissal.
- Preserve sourced action cooldowns across native idle events, support cached Maya frame durations and detect short repeated animations on client ticks.
- Add a visible guide step-progress bar and connect native task changes to the selected guide instruction. Exact linked training guides enter their method on attributed work.
- Add a global End run button and visible build version. Cancel pending guide opens/updates on dismissal; retain same-activity dismissal across bank gaps and client restart.
- Use explicit release classpaths in launch scripts so leftover old JARs cannot override the new build.
- Replace the fake same-name Shortest Path test class with a separately compiled upstream plugin and a real EventBus/pathfinder integration test.

## 0.9.1

- Draw the missing training XP progress bar toward the explicit run target or next level.
- Show native-duration action bars for animations outside the old short whitelist; start fresh events at the current time and recognize native herb-cleaning menu events.
- Advance restored guides past completed steps, wrap to unfinished steps and close guides after their final step or observed quest completion.
- Show current native minigame tasks in the selected matching guide without checking off unobserved static instructions.
- End runs when their explicit XP target is reached. Every End run path dismisses the selected guide, owned guide overlays, automatic minigame coaching and guide bank focus, including idle/already-finished sessions.
- Guard queued clears/updates from reopening a dismissed guide or blanking a newly selected guide; restart completed repeatable training guides for a new run.

## 0.9.0

- Added observed farming patch growth and compost overlays, shared radial node/harvest/effect/activity timers, and native-model loot beams with rarity controls.
- Added live Varlamore thieving and agility coaches, action-cycle and confirmed batch progress, equipment inspection, Hunter rumour tracking, Sailing and port-task coaching, skill goals, dropped-clue timers, bank snapshot comparisons and container displays.
- Expanded Guardians of the Rift and Tempoross with witnessed field timers, active rune/altar references, actual rune icons, fish-spot and tether information. Expired estimates clear without requiring a chat message.
- Added Shift context actions for local references and coaching panels, with shared HUD styling, movement and module suspension.
- Added a signed-manifest inventory and a capability comparison covering every available Hub plugin with at least 50,000 active installs. Remaining partial, missing and unreviewed features remain explicit.
- This is a development release; real-client acceptance and Plugin Hub review remain pending.

## 0.8.1

- Fix Foundry's numbered `null` labels by resolving native object variants and supplying valid station names.
- Prevent queued display-label caret work and unfocused input updates from scrolling the sidebar to the bottom.
- Preserve editable-field selection, typing and focused scrolling, plus per-module reading positions.

## 0.8.0

- Add conditional native quest branch focus, step interaction references, quest items and bounded scene hints while preserving conservative completion.
- Expand training to 340 explicit method plans across all 24 skills, 115 reference-rate plans across 17 skills, 118 exact XP/action models, account-aware unlocks, level-band projections and saved personal pace.
- Add ten sourced training guides, retaining separate Rubium deposits and rocks.
- Add Foundry mould/heat planning, loaded Telekinetic maze solving, confirmed Mahogany Homes contracts/routes and configurable minigame methods/roles.
- Add 25 direct-damage special profiles, exact manual target variants, manual defence/HP scenarios and connected Boss Academy preparation/attempt/review.
- Expand Slayer to 32 sourced methods with corrected master costs, task-storage facts, acquisition scenarios, personal samples and counter-family guards.
- Reconcile farm travel supplies, equipment, fees, access checks and explicit shared charge budgets.
- Add 4,538-item offline acquisition coverage through 64 bounded lazy shards, structural Wiki source parsing, dose/variant loadouts, inventory positioning and read-only potion storage.
- Add bounded Back/Home/Forward navigation, scroll restoration and command-dashboard activity shortcuts.
- Add Linux/Windows verification, production-JAR inspection, explicit live-release gates, local process measurement and a clean-commit Hub submission preparer.
- Preserve default-off Wiki consent, profile/callback guards, Shift hover, native minigame coaching and the documented Fight Caves NPC-origin precedent.

## 0.7.3

- Restore Fight Caves entrance rotation/target timing and confirmed-entry NPC spawn origins, matching the existing Hub Spawn Predictor; keep player-standing packs manual.
- Remove the overbroad eight-activity hold; retain native activity detection, inventory/role objectives, object/NPC references and optional guide/lesson following across all twenty supported contexts.
- Restore personal warmth and Volcanic Mine exit reminder controls.
- Preserve the 0.7.2 public HUD APIs, default-off Wiki consent, cancellation guards and read-only native plugin integration.
- Record the concrete current Hub/core feature comparisons.

## 0.7.2

- Remove forbidden Runtime diagnostics and direct native overlay-config access; keep tick timings and use public overlay persistence APIs.
- Require explicit, default-off Wiki item-source consent with IP/article-title disclosure, request cancellation and stale-response guards.
- Remove programmatic enabling of Time Tracking; native trackers remain read-only.
- Remove entrance prediction/countdowns/automatic opening markers and live arena export; retain written guides and manual marker selection.
- Bound eight combat/boss/Volcanic Mine activity contexts to native status and manual lessons, including generic guide-following and stale callback protection; twelve ordinary skilling contexts retain coaching.
- Add mechanical release API/boundary checks to CI. Official Hub approval remains outstanding.

## 0.7.1

- Require Shift for NPC hover sight and combat cards by default; preserve always-on hover as an explicit setting in HUD settings and RuneLite configuration.
- Clear hover rendering immediately when Shift is released, skip hover/LOS work while inactive, and retain explicit manual sight targets.
- Record a source-based feature-completeness review and refresh Combat Achievement coverage documentation.

## 0.7.0

- One command dashboard for current runs, preparation, next milestones, farming, claimables and unlock opportunities.
- Account-aware plan discovery and editable cross-module milestone plans, progress-free sharing, reviewed import, undo/redo and typed navigation.
- Universal activity hub with multi-skill XP, loot/costs, observed outcomes, milestones, opt-in detection and a bounded shared run history.
- Twenty minigame contexts with 122 learning milestones, richer native role/objective coaching and numbered object/NPC references.
- Searchable DPS targets, ranked saved/sandbox setups, per-set combat choices, target-stat scenarios and ownership/purchase budgets.
- Deeper Slayer eligibility, weighting, personal method/rate plans, skip/block/point simulation and exact assignment activity evidence.
- Per-patch farming choices, supply reconciliation, active route progress, observed replants, contract information and explicit XP/yield/price scenarios.
- Mandatory charge/effect/manual loadout checks; consolidated ammunition, rune and charge awareness using 647 native ID-encoded references.
- Native daily claimables, account opportunities, expanded CA access checks and native account-specific reward thresholds.
- Clue-driven bank preparation, saved travel/bank stops, supply-aware guide routes and per-death return kits linked to saved loadouts.
- Activity-aware AFK alerts, exact recipe action evidence, measured method reuse and Sailing rank/courier reward scenarios.
- Global paged/scoped search across modules, guides, items/storage, monsters, goals, plans, music, combat achievements and account opportunities.
- Expanded profile/lifecycle, native-width layout, integration and local catalog/cache verification.


## 0.6.1

- Honor disabled Bank & XP and Skilling coach modules during delayed startup; reject stale module-enable actions after restart.
- Prevent stopped bank filters from reactivating through preparation links and preserve each guide's saved item choices during external focus.
- Apply shared activity changes before recording loot and keep suspended/offline time out of saved run duration.
- Retain locked HUD positions through plugin restart and harvest module toggles.

## 0.6.0

- Shared activity identity, training/loot checkpoints, observed training HUD and optional rhythm-independent training hotkeys.
- Saved exact loadouts and timestamped account ownership, with missing-supply bank focus.
- Effect/protection HUD, food/potion restoration previews and combined readable hover cards.
- All 48 diary tiers with 492 native task observations; extended personal progression goals and reviewed plan sharing with undo/redo.
- Native clue/STASH preparation, public Shortest Path preview and advertised activity-world discovery.
- Targeted ground loot with actual ownership/deadlines; separate observed death/retrieval records.
- Deterministic recipe chains, patch preparation, deeper Slayer planning and expanded native minigame contexts.
- Supported accuracy/DPS/TTK and owned selected-setup comparisons, with unsupported contexts excluded.
- Global search, optional personal routines, recoverable saves, reviewed backup restore/undo, real module suspension and redacted diagnostics.
- Wider native sidebar/contrast/accessibility checks, dependency locking, data fingerprints and CI verification.

## 0.5.0

Music and Combat Achievement tracking, rarity celebrations/soundboard, sixteen
native minigame contexts, top-layer above-cursor cards and the standalone 773-guide
RuneLite development client.
