# Release boundary and remediation — 0.7.2

**Historical 0.7.2 record. The broad activity hold and Fight Caves entrance removal were corrected in [0.7.3](release-review-0.7.3.md).**

Reviewed 17 October 2026. This records concrete fixes and the active release
surface. It is not RuneLite/Jagex approval or a guarantee against account action.
The intended live distribution is the normal RuneLite Plugin Hub after review;
the bundled client remains a development candidate.

## Concrete implementation fixes

| Area | Current implementation |
| --- | --- |
| Diagnostics | No `java.lang.Runtime`. Bounded tick-duration measurements remain; in-client JVM heap reporting is removed. |
| HUD layout | Only Runewarden-owned overlays use public preferred-position/location setters and `OverlayManager.resetOverlay/saveOverlay`. No direct native configuration access or private-origin access. |
| Time Tracking | Observations are read-only. Players enable the native plugin in RuneLite's own settings; Runewarden no longer enables or starts it. |
| Wiki sources | Default-off native consent, exact external-server warning and visible article-title/IP disclosure. Disabled consent prevents requests and cached external responses; revocation cancels active work, drops queued work and rejects stale callbacks. Bundled sources remain available. |
| Build maintenance | A mechanical production API/release-surface scan runs in CI alongside source-data and Java checks. It does not replace external policy review. |

Legacy free HUD layouts using private right/centre/widget origins cannot be
recreated through public APIs. They require repositioning once; snapped positions
are retained. New free layouts store absolute public canvas positions. The
configuration keys and RWP1 import format remain compatible.

## Retained written/manual functionality

Written boss and wave guides, player-selected phases, manual numbered marker
packs, attempt journals, static monster-family references and the generic native
tick metronome remain. None chooses a protection prayer from the current enemy
attack or changes a prayer. The monster reference is fixed family knowledge;
our earlier general characterization of it as reactive guidance was incorrect.

The hover still requires Shift by default, with optional constant display. LOS
measures current footprint visibility and distance; it does not search a safe
standing tile or guarantee safety. Ordinary account planning, loadouts, bank
focus, XP/material calculations, farming, ownership and item browsing remain.

## Held automatic functionality

Entrance clock/rotation prediction, entry countdowns and automatic opening-pack
selection are removed from executable code. The retired service/overlay types
retain inert compatibility constructors, but are not registered by the plugin.
The former live wave export is now three fixed, manually opened Wiki links;
no NPC/player positions, arena state or timing flags are captured or exported.

Eight contexts use native status and manual authored lessons only: Wintertodt,
Tempoross, Pest Control, Barbarian Assault, Nightmare Zone, Soul Wars, Castle Wars
and Volcanic Mine. Automatic next-action text, scene/NPC targets and phase/guide
following are blocked independently of saved preferences and at producer,
consumer and delayed-callback boundaries. The generic guide-completion path also
obeys the same restriction. Twelve ordinary skilling contexts retain coaching.

This conservative hold is not a finding that every contextual guide or current
state highlight is prohibited. Exact gameplay features need their own review;
manual guides are not removed because they discuss bosses. Public rules restrict
certain automatic combat indications even when no game input is performed.

## Submission and validation

Static family-reference presentation, passive LOS, supported native readback
scripts and the precise retained activity overlays should be described openly in
the submission. No blanket approval is inferred from an existing similar plugin.
Do not describe this release as approved, ban-proof or a certification that all
rules have been exhausted. No repository publication or Hub submission has
been performed by this remediation.

All 136 production sources compile for Java 11 against the pinned RuneLite
1.13.2 runtime. The available combined JUnit suite passes 205 tests in 31
classes, including consent revocation, public HUD layout, held activity
boundaries and inert wave services. The source-data checks validate 28 datasets;
all seven Python parser tests pass. The full Gradle/Mockito suite could not be
rerun because its pinned test dependencies were unavailable after workspace
maintenance. The earlier 0.7.0 full run passed 953 Java tests. These checks do
not establish in-game correctness or Windows execution.

Primary sources checked:

- [Jagex third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [RuneLite rejected/restricted features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [RuneLite Plugin Hub review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [External-server warnings](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub)
