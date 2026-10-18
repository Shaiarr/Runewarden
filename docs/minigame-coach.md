# Minigame coach

The Minigames sidebar shares an immutable, account-bound reading with the
movable black, white and gold HUD. Twenty contexts are detected from public
interfaces, actual instances or unique activity objects. Every supported context
retains its native objectives, supported object/NPC highlights and optional
matching-guide and lesson following. The broad eight-activity hold in 0.7.2 has
been removed; the concrete comparison is in
[release-review-0.7.3.md](release-review-0.7.3.md).

Automatic activity detection, current inventory/role steps, resource highlights
and supply reminders are ordinary helper functions. They do not perform game
input. An activity's label as combat or a minigame does not by itself disable
these functions. Future attack prediction, reactive prayer instructions and
automatic player-standing solutions are outside the implemented activity loops.

## Controls

Automatic HUD, matching-guide selection, resource highlighting, NPC references,
numbered labels and compact density have independent switches. HUD scale is
70–150% and respects the global lock. Activities can be disabled individually.
Guardian fragment batch, Wintertodt warmth and Volcanic Mine exit reminder
thresholds are configurable. Volcanic Mine adds a manually selected vent/miner role. Wintertodt root fletching and Tempoross fish batch/cooking are independent method controls. The HUD displays six wrapped fact lines, or two in
compact mode; the sidebar shows all available facts.

Matching guides are optional and off by default. Unresolved contract tiers or
training variants preserve the selected guide. Manual choices remain selected;
leaving an activity restores the previous guide only while the coach owns the
automatic selection. Logout/account reset discards ownership without restoring
another account's guide. Queued changes check lifecycle epoch and profile.

The 122 authored milestones are manually browsable across all twenty contexts.
Complete & next advances immediately; Back, Next, Undo completion and Restart
operate the checklist. Optional native-objective following positions the
learning plan without completing unseen preparation or claiming a win.

## Native observations and rendering

Ordinary resource scene entry is scanned once per native world view, scene, base and plane. The active Telekinetic room additionally reads a bounded nearby wall/finish window and caches its collision solution.
Relevant object events maintain bounded caches afterward. Rendering reads the
published targets rather than scanning scenes or resolving object definitions.
Detection and seeding stop when all observing options are off and the sidebar
is closed. Stale activity/profile snapshots cannot restore old targets.

Numbered target labels identify resources or NPCs. Fixed Mixology station
references retain verified template coordinates. These are not boss safe-tile
solutions. The current support matrix and reader provenance are in
[minigame-native-state.md](minigame-native-state.md),
[minigame-extra-activities.md](minigame-extra-activities.md) and
[minigame-learning-plans.md](minigame-learning-plans.md).

## Specialist helpers

- Foundry: best available owned mould combination for the native commission, alloy score, remaining section actions with full/partial uniform support, heat capacity, initial heat-adjustment estimates and native bonus reclick cue. The HUD prioritises current action counts; the sidebar retains the complete detail.
- Telekinetic Theatre: actual loaded-wall/collision shortest-pull solution and a numbered standing-side marker. Guidance pauses while the guardian moves or when native evidence is ambiguous.
- Mahogany Homes: native confirmed assignment, address and contract-tier material ranges persist separately per RS profile. Route to customer hands the confirmed address to Travel/Shortest Path. Finishing the contract clears the saved assignment.

Detailed coverage and limits: [minigame-specialists.md](minigame-specialists.md).
