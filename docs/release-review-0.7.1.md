# RuneLite release review — 0.7.1

> Historical version record. See [the 0.7.2 release review](release-review-0.7.2.md) for the current active release boundary.

Reviewed 17 October 2026 against the current public sources below. This is a
source assessment, not approval from RuneLite or Jagex. The development client
is not ready for an unchanged Plugin Hub submission. Publishing source on GitHub
and having a plugin accepted into the normal client are separate steps.

## Findings requiring action or a specific ruling

| Finding | Assessment | Source location |
| --- | --- | --- |
| Live protection-prayer recommendation | Unresolved gameplay review: a fixed monster-family reference names a protection prayer during possible combat. It does not predict attacks, select switch timing or change prayers. The public wording is broad, but no specific ruling on this static presentation was found; request review rather than claim either approval or a confirmed violation. | `MobKnowledgeCatalog`, `RunewardenSightTool`, `RunewardenSightOverlay`; also the selected Slayer reference |
| Diagnostics use `java.lang.Runtime` | Concrete Hub API restriction. Heap measurement does not make the API exempt. Replace this implementation before submission. | `ModuleRuntime.report()` |
| Item-source HTTP disclosure | Selecting an item can request its article from the Wiki. Add the required visible disclosure and appropriate consent/activation. README text alone is insufficient. | `ItemKnowledgeTool`, `ItemSourceService` |
| Other-plugin lifecycle control | Explicitly enabling native Time Tracking is player-triggered, but still needs review against the restriction on changing another plugin's configuration. | `HarvestClockTool` |
| Owned overlay layout persistence | Writes only Runewarden-owned overlay positions into RuneLite's configuration. This is narrower than altering another HUD, but should be reviewed against the core-UI restrictions. | `HudLayoutPositions`, `HudSettingsTool` |
| Boss/minigame current-state guidance | Wintertodt and Tempoross select next interactions and update highlights from observed state. This is more than a static guide; have those exact behaviors reviewed. | `MinigameActivityCatalog`, `MinigameNativeObjectives`, `MinigameHudOverlay`, `MinigameSceneOverlay` |
| External wave-planner export | Explicit browser export of current NPC positions; Colosseum also includes the player's tile. Clarify that disclosure and request review of the exact integration. It does not solve waves inside the plugin. | `WaveExportTool` |

The public rules prohibit combat prayer recommendations, future-attack guidance
and automatically recommended boss standing locations. RuneLite also lists new
high-end PvM boss plugins among features it is not considering. We therefore
cannot assume that an existing similar plugin creates permission for a new one.

## Features which must be assessed accurately

- CLICK is a generic flash after every received game tick. It does not inspect
  an enemy's attack, choose an overhead or identify an actual flick opportunity.
  Core Metronome provides a relevant comparison; this is not automatically a
  prayer-switching indicator.
- LOS measures current footprint visibility and distance through public APIs.
  It does not search standing tiles or guarantee a safe spot. The prayer advice
  in the same card is a separate finding.
- Entrance packs are one-shot static references. No reactive Inferno/Colosseum
  wave solver or upcoming-attack predictor is implemented. Automatic loading
  still needs the exact behavior reviewed.
- Resource bars, XP/banked XP, ordinary farming timers, ownership, item sources,
  loadouts, plans and run journals have no identified gameplay-automation issue
  in this source pass. That is not blanket approval for every subfeature.

Production scanning found no private-field reflection, runtime code loading,
process launch, Java object serialization, Robot/input dispatch or packet
sending. Gson and OkHttp are injected. Development-launcher code and test-only
reflection are separate from the plugin's production source set.

Hub review concerns security and game rules; it does not certify accuracy,
performance or usefulness. Live compatibility and long-session testing remain
separate from this release assessment. No repository has been published and no
submission has been sent as part of this review.

## Public sources checked

- [Jagex: Third Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [RuneLite: Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [RuneLite: Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [RuneLite: Information about the Plugin Hub](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub)
- [RuneLite: Metronome](https://github.com/runelite/runelite/wiki/Metronome)
