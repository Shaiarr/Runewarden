# Runewarden 0.9.0

This development release adds live helpers and an evidence-backed Hub baseline. It is not a claim of full Plugin Hub parity or a public 1.0 release.

## Implemented in this round

- Farming patch overlays show observed crop state, native growth information and confirmed compost separately from planned supplies. Unobserved compost remains unknown.
- Gathering, harvest, active effects and native activity clocks have selectable radial/text displays. Rings use an observed remaining window when the game does not expose the original start time.
- Ground loot has rarity tiers, native animated beam models, bounded rendering and native item visibility/despawn countdowns.
- Varlamore thieving and agility have dedicated native-state coaches, scene highlights, progress tracking and configurable HUDs.
- Action progress offers sourced action cycles, an explicitly labelled animation fallback, and inventory-plus-XP-confirmed batches. ETA requires measured cadence.
- Guardians of the Rift and Tempoross add current rune/altar information, native rune icons, supply highlights, witnessed field timers and tether state. Missed starts and expired estimates remain unknown.
- Equipment inspection reads visible native equipment, provides item links and an optional movable HUD. Untransmitted slots are not invented.
- Hunter rumours connect observed assignments, catches, rare pieces, preparation, bank focus, routes, guides and item references.
- Sailing connects native boat state, courier port tasks, map destinations and Barracuda trial information. It does not reproduce every feature of the specialist pathfinder.
- Skills Progress includes native skill-tab strips and goal menus, removable per-profile goals, actual XP, measured session rates and ETA, including Sailing.
- Bank memory provides bounded named bank snapshots, quantity/value comparisons and manual exports. Historical observations are identified as historical.
- Clue timers track observed dropped scrolls and actual native despawn clocks, with account/world/instance guards.
- Container displays use verified plank-sack varbits and observed fish-barrel evidence; unknown contents remain unknown.
- Shift context menus open local references and helper panels without sending game actions.

## Coverage evidence

`hub-utility-audit.md` and its JSON companion compare all available plugins with at least 50,000 active installs in the recorded signed manifest/count snapshot. `hub-inventory.json` retains the complete inventory, including unavailable and unreviewed records. The source snapshot and fetch tool make that comparison reproducible.

The comparison retains partial and missing features. Large gaps include high-detail rendering, multiplayer party transport, optional external sharing, virtual bank layouts, general menu-rule editing and specialist raid/puzzle engines. A similarly named guide does not count as a completed specialist helper.

## Verification and remaining acceptance

`automated-evidence-0.9.0.json` records the final full-suite and package checks. The code targets Java 11 and RuneLite 1.13.2. Existing consent, profile isolation, manual boss lesson/standing-pack selection and native-action boundaries remain in place.

Real Windows execution, in-game visual/long-session compatibility and actual RuneLite Plugin Hub review remain unverified in `live-acceptance.json`. Automated checks cannot establish those results.
