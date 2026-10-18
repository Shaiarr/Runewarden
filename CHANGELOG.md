# Changes

## 0.10.3 — Separate farming timers

- Group farming reminders by the actual patch type instead of RuneLite's combined Tree, Fruit Tree and Special tabs. Celastrus, Hespori, redwood, hardwood, spirit, calquat and crystal trees have independent timers and selections.
- Preserve account-specific growth observations, native farming-clock alignment and the earliest/latest completion preference. A growing celastrus or redwood cannot replace the normal fruit-tree or tree timer.
- Migrate existing timer selections once to the separate categories, preserving explicit empty selections and account isolation.

## 0.10.2 — Harvest runs and floating scene cards

- Add a floating card and a pointer to the actual accessible Varlamore door.
- Connect patch cards and individual birdhouse cards to their actual scene objects with the same pointer renderer. Birdhouse timers use separate native character records rather than the aggregate harvest clock.
- Separate harvest preparation from the active location, add herb/birdhouse/tree/fruit-tree shortcuts to Overview, and show the current stop in a configurable on-screen run card. Ended and completed runs remove the card.
- Tailor harvesting supplies to the selected run and recognize observed birdhouse refilling without marking already seeded houses complete.
- Add compact native Slayer task reporting, sourced location/equipment references and a contextual task card. Distinguish the current points balance from completion rewards actually observed.
- Keep the local production-code guard at 160,000 tokens, below the Hub reviewer's stated 200,000-token size limit.

## 0.10.1 — Varlamore timing and house routing

- Show distracted elapsed time, bounded remaining time and a live range until the next distraction. Detect observed native transitions even when the chat message was missed.
- Identify the unique observed vacant house, mark its entrance and minimap position, and automatically send its exterior destination to Shortest Path. Respect existing activity routes and manual destinations.
- Keep the Windows rendering compatibility fix in the separate test launcher and enforce it in package verification.

## 0.10.0 — HUD core

- Focus Runewarden on configurable live HUDs, timers and compact session tracking, with a black, white and gold style.
- Retain resources/tick teaching, clock/world/equipment and inventory views, supported automatic base max hit, action progress, buffs/cooldowns and gathering reminders.
- Retain observed farming timers/compost, compact harvest runs and optional Shortest Path routing, Varlamore thieving, current-session loot, routines and native daily reminders.
- Remove the broad guide, minigame/boss, Slayer/planning, DPS/banked-XP, world-hopper, reward and loot-beam workflows from this release.
- Separate the production Hub plugin JAR from a local development test package. The latter uses unchanged RuneLite libraries, optional unchanged Shortest Path and an optionally bundled pinned Temurin 17 runtime with source/legal notices.

This is an experimental release on `hud-core`. Actual live acceptance and Plugin Hub review remain pending. Earlier prototype history and verification counts do not describe this reduced source tree.
