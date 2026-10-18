# Changes

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
