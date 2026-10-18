# Unified activity hub, training measurements and AFK supervision

Runewarden 0.7.0 uses one character-bound run identity for training, quests,
minigames, farming, boss practice, Slayer and named custom activities. The Activity
hub is the common place to start a prepared choice, pause/resume, finish, compare
saved runs and reopen the associated guide. Browsing another guide or method
prepares a next choice without changing the active run.

## Shared journal and workflow boundaries

`SharedActivitySession.journalView()` publishes one immutable value containing
`session`, `run` and `history`, so Swing cannot combine the title of a new run with
the measurements of its predecessor. Every run record contains its context and
stable ID, start/end timestamps, active/elapsed/working time, per-skill observed
XP, correlated training XP, confirmed actions, gross earned loot, entered costs,
net loot, unpriced-item count, loot-source counts, observed outcome counters and
reached milestones. The native detector can prepare an activity; automatic starts
require the player's explicit opt-in. An active or paused manual run always keeps
its identity.

Finishing a run archives the final values once. Starting another run archives an
unfinished predecessor before any measurements reset. Loot's own New run and
Save run controls follow the same ordering, including actions between game ticks.
Repeated detection of the same instance cannot restart a run immediately after
Finish: the detector must report an exit or a distinct entry ID first. There is no
automatic pause/resume caused by merely browsing a guide.

History is character-specific, capped at 100 records and additionally bounded by
a conservative 900,000-character estimate. Large records evict older history
before reaching the safe-save payload limit. The browser supports text and
activity-type filtering, recent/XP/net/time sorting, six-run pages, a detail/back
view, guide reopening, preparing a prior activity and removing a saved record.
Manual milestones can be marked from the hub. Native workflow milestones and
new level/25/50/75/100-percent XP-target milestones are deduplicated per run and
link back to their corresponding module. Goals and loot are one click away.

## XP: whole run versus selected training method

The Activity hub consumes every skill's `StatChanged` events. It captures
secondary minigame skills and quest rewards as actual observed run XP. First
samples, XP reductions, paused periods, profile switches and post-hop baseline
catch-up never create new earned XP. Its hourly rate uses the run's active clock
and only appears after one minute.

The Training session panel and movable HUD separately measure the selected
training skill. Observed XP without a recent animation or verified activity-action
window remains **Other observed XP**, outside the correlated training rate. An
XP drop is not assumed to be one action. The training panel shows working and
elapsed rates, remaining XP and corresponding ETAs, and shares pause/resume,
restart and finish controls with the hub. Optional keybinds retain their native
Runewarden settings.

The exact recipe observer counts a batch only when all of the following agree:

- A selected deterministic recipe has known inputs, known output items and a
  positive XP value in the bundled source-backed recipe catalog.
- Native inventory observations show every exact input quantity consumed and
  every exact output quantity produced for the same whole-number batch.
- The selected skill's correlated XP matches that batch within the server's
  fractional-XP rounding allowance.
- Three game ticks have passed to allow a nearby native reward interface to
  suppress ambiguous evidence.

Bank changes, reward windows, profile/run changes and offline transitions clear
pending evidence. XP or animation alone, dropped materials without outputs,
unknown recipes, arbitrary inventory changes, outfit/bonus mismatches and
unsupported transformations do not produce confirmed action counts. The
`observeAction(units)` hook remains available for other verified native producers.

Reward guards use the pinned public native `XPREWARD`, `QUESTSCROLL` and
`QUESTSCROLL_SPEEDRUN` groups. Exact menu interactions and known lamp/book item
operations extend an eight-tick exclusion window. A visible XP-reward choice
keeps it active. Detection conservatively reclassifies attributed XP from the
preceding two ticks, covering XP events arriving before a reward widget. This can
exclude nearby legitimate work; unrecognised reward sources cannot be identified
perfectly from `StatChanged` alone. Whole-run observed XP remains distinct from
correlated training XP.

Working time accrues from game ticks at 600 ms while recent work is observed.
Logged-in explicit pauses enter elapsed time but not active/working time. Offline
periods enter neither clock. Actual target XP is still read after rewards, so a
lamp may reduce remaining XP without inflating the training rate.

## AFK preferences and alerts

Known causes distinguish working, banking, travelling, idle, paused, finished,
offline, full inventory, exact missing recipe materials, low health and combat
attention. Supervision runs for every active activity, including automatically
started farming, boss and minigame contexts that have no selected XP skill. Health
and prayer are observed independently of the XP calculator. Low prayer requires a
positive prayer observation followed by zero in the current online episode;
initial zero prayer and post-hop baseline catch-up stay silent. It does not
require a current NPC interaction.

Visible native banks suppress work-window accrual and attention alerts. Observed
movement and explicit intentional travel suppress ordinary idle/gathering alerts;
critical low-health/prayer observations remain available while travelling. Pause,
finish and offline states suppress alerts, and no active context does not arm
supervision.

A no-skill activity begins with unknown work status. Arbitrary animations, a full
boss backpack, phase changes, score changes, historic restored action counts and
standing in an instance cannot create an idle alert. A verified `observeAction`
event can arm a work episode. For farming, an increase of the coordinator's exact
matching-route **Observed replants** counter also arms work, without converting
that observation into invented action totals. Recent verified work then supports
a stopped-work cue; full-inventory cues additionally require a gathering context
(farming or minigame). Known explicitly mapped material requirements remain
checkable. Bank, travel, pause and offline transitions disarm the no-skill work
episode until fresh verified work occurs. The rate/ETA HUD remains skill-only;
the sidebar shows activity clocks and the actual supervision cause for other
activity types.

The player controls HUD visibility, idle tolerance (2–100 ticks), low-health
threshold (5–50%), desktop notifications, sound, unfocused-only delivery, delay
(0–100 ticks) and each idle/full/missing/health/prayer reason independently.
Notifications and sound default off. A stable cause triggers one alert after its
chosen delay, then stays silent until work resumes or the cause changes. Native
`FocusChanged` events enforce the unfocused preference; RuneLite's `Notifier`
provides desktop notifications. The optional sound uses an existing native sound
ID. Nothing clicks, withdraws, eats, moves or changes prayer.

## Checkpoints, migration and integration

The existing `checkpointV1` session key is preserved. New guarded `runMetricsV1`
and `runHistoryV1` keys in the same `runewardenactivity` group hold metrics and
history. A valid older checkpoint with no matching metrics resumes paused with
zero newly observed metrics and a continued-checkpoint label. Existing loot
history is left intact; its summary data was not sufficient to reconstruct past
multi-skill XP. Unsupported/corrupt/future saves retain their original payload
through `RunewardenDataStore`; automatic writes remain blocked until recovery.
Session/training/loot checkpoints run once per 50 ticks and at lifecycle bounds.
A cold client restores paused; same-profile hops preserve prior running/paused
intent and exclude offline time. Delayed controls require the captured profile
and run ID to remain current.

Root integration uses `ActivityHubTool.start/stop/clear/onGameTick`,
`onStatChanged` and `onGameStateChanged`, plus `createPanel` and guide/module/goal
callbacks. Training additionally receives `onItemContainerChanged` and
`onFocusChanged`. Native workflow producers offer a single highest-priority
`Context` per tick via `offerDetected`, passing null on exit, and record positive
matching-run outcome deltas through `observeOutcome(key,label,delta)`. Quest,
goal and activity completion transitions call
`reachMilestone(id,title,module)` once per logical milestone. Native Slayer
assignment credits are labelled credits, not inferred NPC kills. Farming manual
check-offs and conservative observed replant check-offs stay separate. Numeric
minigame score changes do not claim a win or reward without a verified event.

The Training HUD uses the ordinary **training** widget scale, opacity, colours,
frame, visibility, position and lock controls. Recent reward correlation is capped
at 32 drops. Visible updates are coalesced; hidden panels skip reconstruction.
Swing consumes immutable values and performs no native client reads. Populated
206px layout, profile/hop boundaries, journal persistence, stale controls, exact
recipe production and AFK notification episodes have headless regression tests.
Live gameplay timing and desktop delivery still require in-client testing.

## Method estimates and Sailing scenarios

All bundled training/minigame guides remain browsable. The method browser can use
this character's measured pace from matching method IDs in current/history runs,
weighted by observed active time. It never applies another method's pace merely
because both link to the same guide. One-off charting remains one-off and does
not project a repeatable hourly grind.

The existing nine Sailing methods retain their verified level, boat, crew,
facility, quest and tool notes, without a universal hourly rate. The expanded
scenario browser adds:

- Twelve rank scenarios across The Tempor Tantrum, The Jubbly Jive and The Gwenith
  Glide: exact completion XP plus required supply pickup XP, with an optional
  first-rank bonus applied once. Trimming/crystal-extractor XP is not inferred.
- 438 exact courier tasks with board, collection port, destination, cargo,
  quantity, level and base XP. The chooser filters to the observed Sailing level
  and supports port/cargo search. Repeat counts multiply the exact selected
  reward. Task 91's missing reward is excluded. Port/quest access and the selected
  board remain separate requirements; a level match alone does not establish all
  route access. Horizon's lure and incidental sailing XP are not invented.

The data comes from retained OSRS Wiki API revisions fetched on 16 October 2026:
Courier tasks **15357675**, and the three trial revisions recorded individually
in `sailing-xp-scenarios.json`. `tools/build-sailing-scenarios.py` reproducibly
extracts these finite rewards from the retained API JSON. The data file carries
exact `oldid` URLs, source timestamps and Wiki contributor attribution under
CC BY-NC-SA 3.0. The scenario values are planning inputs, never observed account
XP, deterministic bank allocation or a claim of universal XP/hour.
