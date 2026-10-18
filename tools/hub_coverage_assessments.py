"""Human-reviewed capability findings, separate from popularity metadata.

Each label paraphrases the pinned upstream README/source. A similarly named
Runewarden catalogue is never sufficient evidence of an interactive helper.
"""
ASSESSMENTS = {}


def add(slug, domain, implemented=(), partial=(), missing=(), unknown=(), note=''):
    ASSESSMENTS[slug] = {'domain': domain, 'note': note, 'features': [
        {'capability': label, 'status': status}
        for status, labels in [('implemented', implemented), ('partial', partial), ('missing', missing), ('unknown', unknown)]
        for label in labels]}


add('quest-helper', 'quests',
    implemented=('Quest search and completed-quest filtering', 'Quest requirement metadata and optimal-order planning'),
    missing=('Own quest NPC and object interaction targets', 'Own automatic quest-stage and conditional branch following', 'Own puzzle-specific answers and visual solutions', 'Own quest dialogue assistance', 'Own quest stop routing and minimap/world-map guidance', 'Own dedicated adaptations of every upstream quest puzzle and stateful helper'),
    note='Runewarden intentionally removed its quest walkthrough runtime in 0.9.3. Quest walkthroughs require the separately installed external Quest Helper. This plugin-only export contains no companion source, binary or development-launcher handoff bridge; automatic quest handoff is not provided here. The recorded Quest Helper 4.18.0 source pin remains reference provenance, not a bundled helper or an executed handoff claim. Companion functionality is not counted as an independently implemented Runewarden helper. Account quest state, requirements and optimal-order planning remain.')
add('117hd', 'graphics', missing=('Enhanced 3D renderer with dynamic lighting and shadows', 'Material, terrain and water texture enhancements', 'GPU antialiasing, draw-distance and renderer performance controls', 'Renderer-level colour accessibility and seasonal environments'),
    note='Own HUD branding does not implement a graphics engine. HdPluginConfig at the Hub-pinned commit was inspected, beyond the brief README.')
add('wikisync', 'network', missing=('Opt-in public Wiki account-state uploads', 'Wiki-server-selected variable synchronization'),
    note='The plugin reads local account facts, but does not publish a WikiSync account profile. The abandoned Runewarden website is a separate architectural decision.')
add('party-panel', 'network', missing=('Live party-member inventory and worn-equipment panels', 'Party-member active-prayer and skill displays', 'Party transport and peer synchronization'),
    note='A self equipment HUD and shared local activity session do not implement multiplayer party transport.')
add('banked-experience', 'bank', implemented=('Banked-resource XP allocation and level requirements', 'Secondary-material limits without counting ingredients twice', 'Optional production-output chains'),
    partial=('Per-method overrides across all bankable training materials', 'Seed-vault, inventory and looting-bag inclusion'),
    note='The XP calculator consumes its bank snapshot; the ownership ledger supports more storage types, but that alone does not prove all containers feed the XP calculation.')
add('bank-tag-layouts', 'bank', partial=('Save and compare a target inventory slot layout',),
    missing=('Drag-and-drop virtual bank-tag layouts', 'Layout gaps, duplicate items and missing-item placeholders', 'Bank-tag-layout import/export compatibility', 'Generate and persist a bank-tab layout from worn gear and inventory'),
    note='bankSearch.layoutBank refreshes a filter. It does not implement draggable Bank Tag Layouts.')
add('skills-tab-progress-bars', 'xp',
    implemented=('Progress strips embedded in all native skill tiles including Sailing', 'Level and own custom-XP or virtual-level goal progress', 'Shared HUD colour customization and native goal tooltips'),
    missing=('Configurable skill-tile bar height and corner indentation', 'Read goals set through the native in-game XP menu', 'Goal-only and hover-only skill-bar modes', 'Completion dimming at level 99, 200 million XP or a custom threshold'),
    note='SkillProgressTool supplies its own saved goals, measured rates and native Stats widget strips. It does not replace the source plugin’s widget implementation or claim its native XP-goal compatibility.')

add('inventory-setups', 'bank', implemented=('Capture worn equipment, inventory and spellbook', 'Preserve or ignore inventory positions', 'Explicit interchangeable items and pooled potion doses', 'Rune-pouch and quiver requirements', 'Share Runewarden-format loadouts'),
    partial=('Bank filtering and missing-item readiness', 'Visual target-slot and quantity differences'),
    missing=('Saved attack-option and weapon-style requirements', 'Bolt-pouch capture and comparison', 'Inventory Setups JSON and plugin-message interoperability', 'Setup folders, favourites, custom icons and rich notes', 'Per-slot fuzzy and unordered comparison controls', 'In-bank draggable layouts with repeated slots', 'Bulk item replacement across saved setups', 'Ground-item setup menu actions for UIMs'),
    note='The loadout model has a 30-plan limit. Native layout compatibility and the complete upstream editor are separate gaps.')
add('shortest-path', 'travel', partial=('Send guide, clue and saved destinations to Shortest Path', 'Carry item requirements into owned route requests'),
    missing=('Own graph, transport database and pathfinding engine', 'Own world-map destination routing without Shortest Path installed'),
    note='GuideNavigation uses public plugin messages for routing and confirms receiver acceptance. The production plugin uses an optional separately installed/enabled Shortest Path; this export bundles no companion source or binary. Default tests use an identity-only discovery fixture. The two unchanged real-companion contracts are retained under integration-tests/java, require an externally supplied Shortest Path 1.21.3 JAR at the recorded pin and are not part of default test coverage. The excluded development-launcher startup tests establish no coverage here. Completed advice reads fixed public getters, copies bounded summary facts and launches no query search. Guide preparation has 94 sourced arrival points for 112 training/minigame guide IDs; method/combat positions and unsourced destinations remain excluded. Farm preparation compares eligible patch destinations using native current-position/account/transport costs; custom runs keep their next stop and order. My POH stores character declarations, scans only verified own building-mode houses and requires explicit application; a cape never implies furniture. Inventory-mode teleports use carried/equipped rather than banked-only items. Owned refresh is coalesced and manual routes are preserved. Catalog membership is not destination coverage, and the native engine is not a Runewarden implementation.')
add('equipment-inspector', 'social',
    implemented=('Inspect another player through a native context-menu action', 'Display visible worn-equipment names and actual item icons'),
    note='EquipmentInspectorTool/Overlay reads public PlayerComposition for the nine visible worn slots, with optional live refresh, GE estimates and item-database links. Ring/ammo are explicitly unavailable; no hidden inventory or stats are inferred.')

add('customizable-xp-drops', 'xp', missing=('Replace and animate native XP drops with custom typography and positioning', 'Group, split, filter and recolour XP drops', 'Predict hits from XP drops including party members', 'Share XP-drop display profiles'), note='Training/session XP totals are a different feature from rendering individual XP drops.')
add('hotkeyable-menu-swaps', 'menus', missing=('Text-rule menu reordering and hiding', 'Context hotkeys for bank amounts and transport menus', 'Custom ground-item menu ordering'), note='No generic menu-rule editor exists. Existing Hub menu features require a counterpart-specific API review; this audit does not declare them prohibited.')
add('kitten-tracker', 'pets', missing=('Kitten growth and hunger/attention timers', 'Feed and play reminders for kitten care'))
add('loot-lookup', 'items',
    missing=('NPC-name search and context-menu drop-table lookup', 'Complete per-NPC tables with rarity, quantity and value columns', 'Multiple monster variants, pinned drop sections and grid/list views'),
    note='The item database answers where an item comes from. It is not a full monster-to-drops browser.')
add('ping-grapher', 'diagnostics', missing=('Live world-ping history graph', 'Server-tick interval history and configurable lag thresholds'), note='Bounded internal plugin timing diagnostics do not measure network ping.')
add('resource-packs', 'graphics', missing=('Load and switch base-client UI resource packs', 'Custom resource-pack asset overrides'), note='Runewarden styles its own overlays and sidebar; base-client reskinning is not supplied.')
add('osrs-tcg', 'social', missing=('In-client trading-card collection and card-pack experience',), note='This is a separate community game, not an existing command-centre feature.')
add('wasted-bank-space', 'bank', missing=('Recognize bank items eligible for alternate-storage relocation', 'Per-storage cleanup recommendations and BIS exclusions'), note='Ownership search does not perform a bank-space audit.')
add('easy-empty', 'menus', missing=('Rune-pouch fill/empty menu changes by altar or bank context', 'Bank-menu necklace and potion convenience swaps'))
add('c-engineer-completed', 'rewards', implemented=('Collection-log and combat-achievement popup sounds',), partial=('Configurable event soundboard and sound profiles',),
    missing=('Level-up, quest, diary-tier and death announcements', 'Compatibility with the upstream voice-pack file layout'), note='RewardNotification.Kind currently covers collection, rare drops, music, combat achievements and demo; general level/quest/death audio is not present.')
add('large-logout', 'ui', missing=('Resize native logout buttons, including the world-switcher logout',), note='No extra logout control has been added; earlier duplicate-close UX problems are not a replacement.')
add('better-teleport-menu', 'travel', missing=('Custom hotkeys inside native teleport-choice menus', 'Filter inaccessible teleport destinations in those menus', 'Enlarged special-event teleport menus'), note='Saved route destinations do not alter teleport-menu keybindings.')
add('plank-sack', 'containers',
    implemented=('Observe all seven native plank-type counts including Sailing planks', 'Per-plank-type count breakdown with actual item icons', 'Carried and currently visible bank-container location with validated combined capacity'),
    partial=('Configurable plank overlay colours and arrangement',),
    missing=('Original inventory hover tooltip and alternate source layout styles',),
    note='ContainerHudTool reads the seven public native varbits and rejects invalid or over-capacity totals. The shared movable HUD has common style controls, but not every upstream per-icon/label/layout option or ownership-ledger integration.')

add('runeprofile', 'network', missing=('Opt-in public achievement-profile publishing', 'RuneProfile account synchronization'))
add('detached-camera', 'camera', missing=('Hotkey-controlled free-roaming detached camera',))
add('optimal-quest-guide', 'quests', implemented=('Wiki optimal-order quest sorting', 'Hide completed quests and refresh after native completion'), partial=('Account prerequisite metadata and Quest Helper planning links', 'Recommended next quest from fully checked live prerequisites'), note='Unverified requirements retain an unknown state rather than being silently treated as satisfied. Native quest progress, prerequisite metadata and ordering remain in Runewarden. Quest Helper planning controls are retained references, but the plugin-only export has no automatic handoff bridge; walkthroughs must be opened separately in Quest Helper.')
add('clue-scroll-juggling', 'clues',
    implemented=('Separate native dropped-scroll deadlines for all six clue tiers and elite challenge scrolls', 'Configurable tier filters, expiry thresholds and once-per-drop alerts', 'Earliest-only or separate countdown HUD rows with tick/second and ring options', 'Extra native item timers from IDs, names and star wildcards', 'Manual removal and profile-isolated private-clue checkpoints'),
    partial=('Cross-scene and cross-session clue timer continuity',),
    missing=('Upstream infobox-manager placement and independent notification-channel editor', 'Historical arbitrary timer-reduction multiplier'),
    note='The pinned README is a template; actual source/config were inspected. Native getDespawnTime replaces a fixed hour assumption. Clean checkpoints remain marked awaiting sighting; dirty checkpoints show unknown. Public/extra/instance timers are never restored as paused private loot. Live transition acceptance remains open.')

add('collection-log-luck', 'collection', partial=('Display known item rarity and collection ownership reference',), missing=('Per-item received-versus-expected luck calculations', 'Collection-log context-menu probability details', 'Public collection-log account imports and luck sharing'), note='A rarity-coloured reward popup is not a probability or dryness model.')
add('wom-utils', 'network', missing=('Wise Old Man gain updates and player lookups', 'Competition countdowns and notifications', 'Clan/group synchronization and membership management'))
add('runedoku-helper', 'puzzles', missing=('Runedoku board solver and selected-rune hints', 'Candidate pencil marks and conflicting-entry detection', 'Numeric board display and configurable puzzle styling'))
add('object-hider', 'graphics', missing=('Select and hide arbitrary ground-object models', 'Persist editable object-hiding selections'))
add('flipping-utilities', 'economy', missing=('Grand Exchange trade history and realized-profit accounting', 'Margin, buy-limit and active-offer timers', 'Offer editing aids, recipe flips and transaction statistics'))
add('looting-bag-value', 'containers', implemented=('Observe looting-bag contents in the storage ledger',), partial=('Look up the observed contents while preparing a loadout',), missing=('Looting-bag inventory value and free-slot overlay', 'Incremental pickup, dispenser and telegrab tracking'), note='A last-observed container snapshot does not prove every live bag change is tracked.')
add('emote-clue-items', 'clues', implemented=('Current emote-clue equipment requirements and supply focus', 'Observed built/filled STASH state with unknown-state handling', 'Search STASH units and accepted item examples'),
    partial=('Persist an emote-item collection checklist',), missing=('Global item highlighting for future emote clues', 'Persistent world-map markers for all relevant STASH locations', 'Right-click lookup of every item’s future clue requirements'))
add('dink', 'network', missing=('Configurable Discord/webhook event notifications', 'Screenshots, rich embeds and per-event routing', 'Quest, clue, trade, GE and group-storage webhook coverage'), note='Local notification popups do not send external webhooks. External transmission would need explicit opt-in and a disclosed destination.')
add('zom-leftclick-dropper', 'menus', missing=('Editable item-name rules for left-click Drop menus',))
add('spirit-tree-map', 'travel', missing=('Interactive map replacements for native teleport-choice interfaces', 'Spirit tree, mushtree, obelisk and regional transport map selection'))
add('raid-reloader', 'raids', missing=('Raid scouting reload convenience control',), note='The source-pinned control must be reviewed separately before adding a counterpart; ordinary route clearing is unrelated.')
add('loot-logger', 'loot', implemented=('Run-scoped loot totals and saved activity histories',), partial=('Searchable persistent history for every native Loot Tracker source',), missing=('Upstream JSON-lines loot-history compatibility and per-source file import/export',), note='Shared activity/run tracking is useful, but not the complete Loot Logger history browser.')
add('watchdog', 'alerts', partial=('Built-in low-supply, training-idle and harvest alerts', 'Styled local popup, sound and desktop notifications'),
    missing=('User-defined alert rules with regex and capture groups', 'General message, spawn, stat, inventory and location trigger editor', 'Composable per-rule counters and notification actions', 'Shared alert-pack import and subscription hub'), note='Fixed purpose-built reminders are not a general event-rule engine. The source documents its own activity restrictions; a counterpart needs feature-specific review.')
add('runewatch', 'social', missing=('RuneWatch and WDR watchlist synchronization', 'Player lookup, nearby-party and trade-window watchlist warnings'))
add('menu-swapper-extended', 'menus', missing=('Extended NPC, object, minigame and transport menu swaps', 'Per-interaction convenience settings'), note='Source Config inspected. Menu-entry changes are distinct from automatically performing game actions.')
add('not-empty', 'menus', missing=('Suppress accidental potion Empty menu choices',))
add('bank-memory', 'bank',
    implemented=('Named bank snapshots with persistent per-character history', 'Search and page saved item contents', 'Compare snapshot item quantities and historical GE values', 'Export bank snapshot item data as JSON, CSV and TSV'),
    partial=('Automatic current-bank observation and retention',),
    missing=('Browse all saved accounts while offline without changing the active character', 'World-mode-separated current bank histories', 'High Alchemy valuation and inventory bank-count hover tooltips'),
    note='BankMemoryTool adds eight manual named snapshots per profile, explicit open-bank comparison, repricing-separated diffs and exports. OwnershipLedger supplies last-observed ownership, but there is no full automatic upstream-style multi-account bank archive.')

add('clue-steps', 'clues', missing=('Per-clue completed-step counts across clue tiers', 'Configurable certainty colours for clue-step estimates'), note='A current clue-part instruction is not the number of scroll steps already completed.')
add('tasks-tracker', 'tasks', missing=('Mirror, search, filter and pin the native Tasks interface outside the game UI', 'Generic native-task export for external services'), note='Diary and CA planners are distinct datasets from the upstream generic Tasks interface.')
add('fish-barrel', 'containers',
    implemented=('Observe barrel raw-fish counts from native checks and confirmed changes', 'Dedicated carried/equipped barrel-count HUD with item icons and display toggles'),
    partial=('Complete catch, fill and inventory reconciliation for every fish source',),
    missing=('Original inventory-slot overlay placement and alternate source display styles',),
    note='ContainerHudTool/FishBarrelReading keeps unknown starting or ambiguous counts unknown. A Check requires a newly loaded response; a Fill click alone never proves contents. Banked barrels remain unknown, and per-fish historical composition is not supplied.')

add('odablock', 'rewards', partial=('Configurable local event sounds',), missing=('Upstream voice-pack coverage and achievement triggers', 'Novelty player-kill and snowball announcement systems'))
add('instant-inventory', 'ui', missing=('Immediate local feedback for inventory equip and item interactions', 'Immediate bank, prayer and special-bar interaction presentation'), note='The upstream feature is about local interface responsiveness. Its name alone is not evidence of automation, and no prohibition is asserted here.')
add('fashionscape', 'cosmetics', missing=('Local-only equipment and base-model appearance overrides', 'Appearance search, filters and colour matching', 'Appearance loadouts, locking and randomized outfits'))
add('camera-smoothing', 'camera', missing=('Smoothed yaw, pitch and zoom motion', 'Adjustable camera smoothing strengths'))
add('companion-pet', 'cosmetics', missing=('Local-only companion pet and transmogrification previews', 'Pet favourites, sizing and thrall appearance replacements'))
add('ticktracker', 'diagnostics', partial=('Observe generic server-tick cadence for the training HUD',), missing=('Good-tick percentage and configurable lag thresholds', 'Login warm-up exclusion and tick-lag warning history'), note='The CLICK/dot metronome is not a network-health calculation.')

# Activity and combat entries remain in the same complete popularity tier. Their
# purpose-built mechanisms are compared rather than treating written guides as parity.
add('sailing', 'sailing',
    implemented=('Highlight nearby native rapids and the own-boat trim action', 'Display observed boat speed, heading, wind and hull facts'),
    partial=('Barracuda lost-supply and current trial objective highlights', 'Native courier cargo counts and destination guidance'),
    missing=('Crew overhead-text filtering and boat-visibility modes', 'Helm-only sail and cargo-hold priority menu conveniences', 'Sea-charting location requirements and charted-state highlights', 'Weather station and current-duck puzzle solvers', 'Hide Gwenith portal transitions', 'Destination labels directly on native cargo item widgets'),
    note='SailingCoach implements bounded live navigation, trimming, cargo and trial facts. It does not claim the upstream charting solvers, menu changes or closed-hold memory.')

add('tile-packs', 'markers', implemented=('Search category-based authored marker packs', 'Manually load and clear numbered tile markers'), partial=('Persistent pack visibility and imported custom pack editing',), missing=('Full upstream pack submission, favourites and content-management workflow',))
add('guardians-of-the-rift-helper', 'minigames',
    implemented=('Outline the currently active elemental and catalytic guardians', 'Native rune icons and requirement colours above active guardians', 'Supply-conditioned Great Guardian, cell-table and free-slot essence-pile highlights'),
    partial=('Witnessed-start altar and portal expiry estimates', 'Current contribution and resource activity panel'),
    missing=('Complete time-since-portal and outside-round next-game or reward-point panel parity', 'All pre-start, fragment-threshold, first-altar and portal desktop notifications', 'Hide impossible left-click menu choices'),
    note='MinigameFieldEvents maps all twelve native runes and requires the actual active animation. Its 33/43-tick estimates start only from witnessed transitions; initial and expired ages stay unknown. Talisman bypass, complete cell placement and quest-unlock coverage remain partial; no tick-perfect timer claim is made.')

add('tombs-of-amascut', 'raids', missing=('Room-specific puzzle solvers and raid overlays', 'Native salt cooldown, raid HP and deposit-pot interface aids', 'Room mechanics, encounter counters and detailed raid progress'), note='Written raid guides and manual markers are available, but do not implement these upstream mechanisms.')
add('better-npc-highlight', 'npc', partial=('Context-bound minigame NPC highlights',), missing=('General NPC name/ID rule editor with nine drawing styles', 'Per-rule widths, colours and death exclusions'))
add('the-gauntlet', 'bosses', missing=('Resource-node outlines, icons and gathered-resource counters', 'Preparation and full-run timers', 'Dedicated Gauntlet NPC and preparation overlays'), note='Written Gauntlet lessons and manual marker packs are available, but do not implement a live prep helper.')
add('zulrah-helper', 'bosses', partial=('Manual boss phase and marker selection',), missing=('Full manual Zulrah rotation diagram and rotation-elimination interface',), note='The upstream explicitly requires manual input rather than detecting rotations from game state.')
add('mahogany-homes', 'minigames', implemented=('Track the assigned client, location and furniture stage', 'Activity supplies and route destination'), partial=('Complete per-contract statistics and every upstream visual setting',))
add('tempoross', 'minigames',
    implemented=('Highlight the native double fishing spot', 'Distinct fire, forming-cloud and spreading-shadow highlights', 'Current tethered and broken-mast colours during an incoming-wave window', 'Independent off, radial, seconds and tick display choices for event timers', 'Calculate carried-fish damage with long arithmetic'),
    partial=('Witnessed-start double-spot, fire-stage and wave countdowns', 'Current native phase, fish preparation and spirit-pool guidance'),
    missing=('Complete configurable double-spot, wave, fire, storm-threshold and vulnerability desktop notifications', 'Recurring fire-cycle coverage and full phase history', 'Reward-point infobox parity outside the instance'),
    note='MinigameFieldEvents uses native identities/messages, explicitly estimated random double-spot lifetime and a bounded 7.8-second wave window that expires even if impact chat is absent. Unknown initial ages are never filled with full timers.')

add('easy-giantsfoundry', 'minigames', implemented=('Choose source-derived optimal moulds for unlocked pieces', 'Display crucible alloy composition and score'), partial=('Temperature and next-tool guidance',), missing=('All per-tool actions remaining and bonus-progress visual cues', 'Complete specialist colour and notification controls'), note='Guide selection reconciles loaded alloy, native mould state and carried/stored preforms. Native heat/tool objectives advance the selected guide; full sword progress remains hand-in until witnessed reward acceptance confirms completion. These guide lifecycle improvements do not establish complete specialist display parity or complete subjective learning milestones.')
add('mastering-mixology', 'minigames', partial=('Current potion recipe, order and station guidance',), missing=('Full configurable order-selection strategies', 'Digweed spawn highlighting and notifications', 'Quick-action timing and widget augmentation'))
add('rogues-den', 'minigames', missing=('State-aware maze obstacle and optimal-tile highlights',), note='Written training material does not supply a complete live maze traversal helper.')
add('port-tasks', 'sailing',
    implemented=('Track native active courier tasks with collection and delivery counters', 'Highlight the relevant native task ledger'),
    partial=('Destination markers on the world map with explicit travel handoff',),
    missing=('Actual in-world sea routes between task ports',),
    note='SailingCoachTasks reads five current delivery DB slots and 30 native ports. Offered tasks, bounty tasks and rewards are not implemented; bounty support is also advertised as future work by the pinned upstream README.')

add('wilderness-player-alarm', 'pvp', missing=('Nearby-player wilderness screen alarm', 'Threat-display and alert configuration'), note='A generic low-health alert is not another-player detection.')
add('hunter-rumours', 'hunter',
    implemented=('Track the native current Hunter Guild rumour', 'Persist the observed saved rumour for each of the six guild masters'),
    partial=('Use the saved master list to prepare and compare a manual block list',),
    note='HunterRumourCoach reads native whistle/guild dialogue and rare-part state, with profile-safe HUD, confirmed NPC highlights, supplies and manual travel links. Unknown assignments stay unknown; block optimization, native teleport-widget rewriting and hidden pity counters are not claimed.')

add('fight-cave-waves', 'waves', partial=('Manual wave library and Fight Caves opening spawn markers',), missing=('Current and next wave NPC roster display throughout Fight Caves and Inferno',))
add('totem-fletching', 'skilling', missing=('Optimal totem carving actions and decoration progress', 'Ent-trail locations and expiry timers', 'Per-totem live points display'))
add('spawnpredictor', 'waves', implemented=('Fight Caves entrance rotation estimate and countdown', 'Selected-rotation entry guidance and opening NPC-spawn markers'), partial=('Every upstream calibration and uncertainty control',))
add('radius-markers', 'npc', missing=('Persistent spawn, wander and retreat radius regions', 'General per-NPC aggression, hunting and attack-range markers'), note='Current line of sight and footprint reference are separate features from these radius regions.')
add('barrows-door-highlighter', 'bosses', missing=('Mark locked versus unlocked Barrows tunnel doors',))
add('varlamore-house-thieving', 'skilling',
    implemented=('Identify actual distracted citizens, locked doors, owners and escape context', 'Follow the native hint arrow to bonus furniture', 'Display observed inventory keys and valuables with their trade-in value'),
    partial=('Configurable target highlights across all source target types',),
    missing=('Native inventory hover tooltip for each valuable item', 'Every distraction, empty-chest and elapsed-threshold notification option', 'Independent bonus-item sound suppression'),
    note='VarlamoreThievingCoach also supplies observed-start estimated distraction/container/return timers. An unwitnessed mid-cycle start remains unknown; those bounded estimates are not an assertion of universal upstream timer parity.')

add('wilderness-multi-lines', 'pvp', missing=('Multi-combat and spear-range boundary overlays', 'Wilderness level-20 and level-30 dividing lines'))
add('visual-metronome', 'ticks', implemented=('Generic native-tick visual indicator with CLICK/dot modes',), partial=('Movable, recolourable and resizable cadence display',), missing=('Party-synchronized metronome sequence state',))
add('cox-additions', 'raids', partial=('Manual raid attempts and aggregate activity timing',), missing=('Automatic CoX room, floor and Olm phase splits', 'Thieving grub estimates and Vanguards-specific overlays'))
add('thrall-helper', 'effects', partial=('Known active-effect and departure reminders',), missing=('Complete supported-spell expiration/recast reminder matrix', 'All native cooldown, duration and spell-icon display options'))
add('barracuda-trials-pathfinder', 'sailing',
    partial=('Native trial identity and current objective highlighting across three trials',),
    missing=('Real-time hazard-aware A-star routes between moving waypoints', 'Full Wiki static waypoint order and path projection', 'Efficient and relaxed route modes with speed-boost optimization'),
    note='The live Sailing coach reads difficulty/progress, witnessed-start timing, splits and KC-confirmed finishes. It supplies selected native objectives, not the upstream pathfinding engine or complete route projections.')

add('tictac7x-charges', 'charges', implemented=('Known ID-encoded jewellery and consumable charge counts', 'Configurable low-ammo, rune and dose thresholds'), partial=('All armour and weapon charge families',), missing=('Full hidden-charge observation through native messages and checks',), note='SupplyAwarenessTool explicitly leaves unsupported hidden weapon charges unknown.')
add('chompy-hunter', 'hunter', missing=('Chompy spawn lifetime and changing urgency colours', 'Session and lifetime chompy kill-rate displays'))
add('hunllef-helper', 'bosses', missing=('Manually started Hunllef prayer-cycle audio metronome', 'Encounter-specific cycle adjustment controls'), note='A generic tick pulse and written boss guide do not implement the manual Hunllef helper.')
add('ba-minigame', 'minigames', partial=('Role-specific native facts and minigame guidance',), missing=('Complete wave/call timers and teammate-role presentation', 'Lobby convenience swaps and role chat styling'))
add('fortis-colosseum', 'waves', partial=('Manual Colosseum wave guides and attempt loot totals',), missing=('Native reward-interface bank-all and next-wave presentation options',))
add('monster-hp-percentage', 'npc', missing=('General per-name NPC HP percentage and numeric overlays', 'Per-NPC low-health styles and wildcard thresholds'))
add('crab-stun-timers', 'raids', missing=('Chambers crab unstun countdowns',))
add('tree-despawn-timer', 'gathering', implemented=('Observed tree remaining-time estimates and respawn display',), partial=('Pie, tick and seconds visualization options',), missing=('Special popular-tree visual treatment based on nearby choppers',))
add('cox-qol', 'raids', missing=('Prayer-enhance detail and party grub counters', 'CoX preparation and clan-party entrance warnings', 'Olm head and CoX-specific true-tile highlight settings', 'CoX-specific menu and hotkey conveniences'))
add('improved-tile-indicators', 'markers', missing=('Native player/destination tile styling with behind-actor modes',), note='Authored static marker packs do not replace native movement/destination tile indicators.')
add('tobqol', 'raids', unknown=('Exact room-specific Theatre quality-of-life option coverage',), note='The pinned README describes scope but not the actual options. A written Theatre guide cannot be counted as a replacement; configuration/source review remains open.')
add('kph-tracker', 'loot', implemented=('Run loot, elapsed time and activity session summaries',), partial=('All boss recognition, pause/resume and kill-rate statistics',), missing=('Upstream chat commands and every boss-specific rule',))
add('crab-solver', 'raids', missing=('Chambers crystal-to-crab colour solution overlay',))
add('one-click-summer-garden', 'skilling', missing=('Summer Garden elemental cycle and reset analysis', 'Start-gate timing and cycle-alignment visualization'))
add('attacktimer', 'ticks', missing=('Own-weapon next-attack cooldown timer', 'Weapon and encounter-specific attack-speed corrections'), note='The existing generic tick display does not calculate the player attack cooldown.')
add('stealing-artefacts', 'skilling', missing=('Assigned artefact route and house/ladder highlights', 'Lured-guard indicators and artefact session overlays'))
add('dt2-mistake-tracker', 'bosses', missing=('Source-supported automatic DT2 mistake-event detection', 'Per-boss native event summaries'), note='Manual post-attempt coaching and mistake journals are separate from automatic mistake-event detection.')
add('maxhitplugin', 'combat', implemented=('Automatic current-gear normal-attack max-hit calculation',), partial=('Current selected spell, prayers and boosted-level modifiers', 'Complete special-attack max-hit coverage'))
add('demonic-larva-tracker', 'bosses', missing=('Larva-specific visibility and highlight rules', 'Supported death prediction and absorbed-larva counters'))
add('star-calling-assist', 'network', missing=('Star discovery uploads and community star-location list', 'Manual or configured automatic star reports'), note='A local mining guide does not supply a scouting network.')
add('zom-con-qol', 'menus', missing=('Construction build-menu key remapping',))
add('zalcano', 'bosses', missing=('Zalcano phase health and participant counts', 'Personal contribution thresholds and reward-eligibility estimates', 'Scouting and Crystal Tool Seed probability displays'))
add('fight-cave-helper', 'waves', partial=('Manual Fight Caves wave references and opening setup',), missing=('Dedicated per-wave spawn image interface for every wave',))
add('gauntlet-minimap', 'bosses', missing=('Gauntlet resource and demi-boss minimap icons', 'Hide resources after gathering the configured amount'))
add('tictac7x-motherlode', 'gathering', partial=('Mining node timers and training progression',), missing=('Upper-floor vein-section logic and vein suitability highlights', 'Combined hopper/sack capacity and nugget session widget', 'Rockfall indicators and stop-mining alerts'))
add('elysiumevents-plugin', 'network', missing=('Clan-event calendar and external spreadsheet synchronization', 'Wise Old Man event integration'))
add('improved-tears-of-guthix-interface', 'minigames', missing=('Native Tears of Guthix tick countdown and current-stream indicator',))
add('ba-call-highlight', 'minigames', partial=('Read native BA role and call facts',), missing=('Highlight the matching call-horn context-menu entry',))
add('projectile-override', 'accessibility', missing=('Configurable visual projectile substitutions by boss', 'Projectile colour/shape contrast accessibility profiles'))
add('target-true-tile', 'npc', partial=('Shift-hover current NPC footprint reference',), missing=('Persistent interacting-target true tile with memory', 'Friendly exclusions and NPC-tag integration'))
add('annoyance-mute', 'audio', missing=('Per-sound and per-entity nuisance-audio suppression',))
add('one-tick-flick', 'ticks', implemented=('Generic tick cadence with optional CLICK text',), missing=('Latency-adjusted prayer-click training window', 'Flick combo counter and prayer-bar overlays'), note='The present display is a generic metronome, not an enemy-dependent prayer indicator.')
add('easy-blastfurnace', 'minigames', partial=('Native furnace facts, supplies and load/collect guidance',), missing=('Every optimized tick method and next-click target sequence', 'Coal-bag live contents and precise stamina prompts'))
add('party-defence-tracker', 'combat', partial=('Manual defence-reduction scenarios in DPS comparison',), missing=('Party special-attack outcome synchronization', 'Live target-defence and vulnerability infobox'))
add('time-tracking-reminder', 'farming', implemented=('Ready farming/birdhouse selection in a movable HUD', 'Read native Time Tracking observations'), partial=('All miscellaneous routine and patch reminder categories',))
add('vardorvis-projectiles', 'accessibility', missing=('Visual substitution of the Vardorvis prayer-disable projectile',))
add('herbi-afk', 'hunter', missing=('Live Herbiboar trail-to-next-spot lines and arrows', 'Harvest target and nearest new trail-start guidance'), note='Authored training plans do not adapt the native Herbiboar tracker.')
add('menuhp', 'npc', missing=('Current NPC HP or percentage inside the context menu',))
add('razor-kebbit-tracking', 'hunter', missing=('Native razor-backed kebbit trail tracking and search-target highlights',))
add('slayer-assistant', 'slayer', implemented=('Current task location, required equipment and Wiki links', 'Static monster attack-style and attribute references'), partial=('Search all assignment variants and every assigning master',))
add('action-progress', 'xp',
    implemented=('Movable bar, countdown ring and seconds display choices', 'Confirmed ingredient-to-product batch progress with matching XP', 'Common potion, gem, battlestaff, glass, jewellery, bow and ordinary fish cycle indicators'),
    partial=('Animation progress for supported smithing, furnace, grinding and construction animations', 'Action-specific coverage across the complete documented production list'),
    missing=('Complete smithing, smelting and cannonball action-completion cycles', 'All enchantment, orb, plank, tablet and jewellery-spell cycles', 'Action-specific dairy, pastry, wine, fruit and pizza cycles', 'Campfire, ultracompost, Sailing sorting, Camdozaal and reward-search cycles'),
    note='ActionProgressTool confirms 102 modelled production recipes through ingredient/output/XP evidence, supplies nine standard cycle categories and explicitly labels native-animation fallback. Ordinary selected cooking has cycle support without claiming unverified product-based batch completion. Animation end is not claimed to be universal action completion; unsupported specialist cycles remain visible gaps.')

add('tictac7x-tithe', 'minigames', partial=('Tithe activity facts and next-object guidance',), missing=('Every plant’s dry, watered, grown and blighted tick state', 'Complete seed and watering-container inventory highlights', 'Dedicated Tithe points widget parity'))
add('mastering-mixology-strategy', 'minigames', partial=('Current-order recipe selection',), missing=('All upstream optimized brewing strategies and configurable priorities',))
add('visual-ticks', 'ticks', implemented=('Native game-tick pulse display',), partial=('Configurable visual cadence presentation',), missing=('Three simultaneous tab-specific tick configurations', 'Global and individual cycle-length adjustment hotkeys'))
add('consumable-cooldowns', 'combat', partial=('Preview food healing and prayer restoration',), missing=('Live regular-food, combo-food and potion cooldown overlays', 'Variable-delay sliced-food timing'), note='ConsumablePreviewOverlay is an effect preview, not a live cooldown tracker.')
add('nyloer', 'raids', missing=('Nylocas type symbols and wave numbering',), note='Source README also lists unsupported/restricted features separately; they are not included as implementation requirements here.')
add('pvp-performance-tracker', 'pvp', missing=('PvP fight accuracy, damage and performance analysis', 'Optional opponent synchronization and post-fight replay analysis'))
add('barrows-potential', 'bosses', missing=('Current Barrows reward-potential target and creature-combination calculation',))
add('pyramid-plunder-counter', 'minigames', partial=('Current Pyramid Plunder room and activity facts',), missing=('Persistent chest/sarcophagus counts since the last sceptre', 'Cumulative sceptre and pet probability calculations'))
add('weapon-charges-2', 'charges', partial=('Known ID-encoded charges and departure checks',), missing=('All supported hidden charged-weapon reservoirs', 'Weapon-use decrement and native check-message reconciliation'))
add('gauntlet-chest-popup', 'rewards', implemented=('Rarity-coloured loot popup and rare-drop audio',), partial=('Gauntlet-source reward recognition',), missing=('Dedicated Gauntlet chest sprite, title and normal/corrupted themes',))
add('forestrycc', 'network', missing=('Community Forestry event reports and concurrent location timers', 'Event confirmation, expiry and accessible-location filters'))
add('door-kicker', 'cosmetics', unknown=('Exact novelty door-interaction behaviour',), note='The pinned README is a slogan rather than a feature specification; no animation behaviour is invented from the name.')
add('gemstone-crab-timer', 'skilling', partial=('Training-session idle reminders and method plans',), missing=('Gemstone Crab HP-threshold notifications and death pulse', 'Post-death tunnel target highlight'))
add('guardians-of-the-rift-optimizer', 'minigames', partial=('Activity facts, resource guidance and current altar support',), missing=('Optimal/secondary obelisk choice across optimization modes', 'Arrival-aware tick-perfect portal and altar timers', 'Potential-point optimizer and full menu conveniences'))
add('gauntlet-map', 'bosses', missing=('Discovered Gauntlet-room map with player, resources and demi-bosses', 'Room/node map style controls'))
add('treecount', 'gathering', missing=('Per-tree active-chopper counts with animation-stall reconciliation',))
add('cox-light-colors', 'rewards', missing=('Chambers reward-chest light recolouring',))
add('corner-tile-indicators', 'markers', missing=('Corner-only player and destination tile rendering',))
add('player-outline', 'accessibility', missing=('Local player model outline with width and feather controls',))
add('pickpocket-helper', 'skilling', partial=('Training method and generic idle guidance',), missing=('Pickpocket-specific stun, failure and success audio/overlays', 'Pickpocket-focused item and interaction indicators'))
add('delve-calculator', 'collection', missing=('Depth-completion-based Delve expected-drop calculations', 'Observed versus expected Delve luck analysis'))
add('did-i-compost', 'farming',
    implemented=('Overlay the exact native farming patch after confirmed compost application',),
    partial=('Persist observed treatment across every crop reset and patch lifecycle',),
    note='FarmCompostEvidence requires an exact selected native patch and matching server confirmation. Planned/carried compost does not count. Old-session evidence remains last seen until revisited rather than proving treatment of a new crop.')

add('raid-data-tracker', 'raids', partial=('Shared local activity/run history and loot totals',), missing=('Per-raid personal/team points and split-aware histories', 'Raid-specific loot, luck and dry-streak accounting'))
add('startierindicator', 'gathering', partial=('Authored star-mining training guidance',), missing=('Native star tier, remaining-percent and active-miner displays', 'Stardust-rate, visibility alerts and location-sharing text'))
add('tzhaar-hp-tracker', 'npc', missing=('Wave-NPC health overlays across Caves, Inferno and Colosseum', 'Per-NPC alive/dead menu and health-style options'))
add('wintertodt-notifications', 'minigames', partial=('Wintertodt native facts and activity guidance', 'Training-idle reminders'), missing=('Full interruption and completed-action visual notification rules',))
add('vineyard-helper', 'skilling', missing=('Pickable grapevine highlights based on barrel capacity',))
add('deepseatrawling', 'sailing', missing=('Moving shoal paths, stops and depth-change countdowns', 'Net-depth mismatch and full-net indicators', 'Special shoal colour and catch-quantity overlays'))
add('fishing-notifier', 'alerts', partial=('Supported fishing training-session idle alert',), missing=('Fishing-specific interruption coverage independent of a selected plan',))
add('damage-counter', 'combat', missing=('Observed per-target damage and elapsed-fight DPS', 'Party damage sorting and boss-death damage summary'), note='Simulated loadout DPS is not an observed damage counter.')
add('gnome-restaurant', 'minigames', missing=('Live recipe ingredient overlay for an assigned delivery', 'Order and assignment-delay timers', 'Recipient arrows, map markers and route integration'))
