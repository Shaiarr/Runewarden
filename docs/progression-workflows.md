# Connected progression and native claims

Runewarden 0.7.0 connects personal progression, native account observations,
source-backed preparation, item targeting and daily claims. All data stays bound
to the current RuneScape profile. These controls do not perform game actions.

## Daily claims

`My routines` opens native daily claims. Players choose which reminders appear;
their personal once/daily/weekly checklist remains available in `My routines`.
Native claims have no manual completion button: the server's claim variables,
reward claims, account mode and membership observations determine their status.

| Claim | Allowance and eligibility | Native observation |
| --- | --- | --- |
| Zaff battlestaves | 15/30/60/120 for claimed Varrock tiers; 7,000 coins each | `ZAFF_LAST_CLAIMED` |
| Cromperty pure essence | 100/150/250 for claimed Ardougne medium/hard/elite | `ARDOUGNE_FREE_ESSENCE` |
| Lundail random runes | 40/80/120/200 for claimed Wilderness tiers | `LUNDAIL_LAST_CLAIMED` |
| Flax keeper conversions | 30/60/120/250 for claimed Kandarin tiers; bring flax | `SEERS_FREE_FLAX` |
| Rantz ogre arrows | 25/50/100/150 for claimed Western tiers | `WESTERN_RANTZ_ARROWS` |
| Robin bonemeal and slime | 13/26/39 for claimed Morytania medium/hard/elite; bring bones | `MORYTANIA_SLIME_CLAIMED`, including partially used allowance |
| Thirus dynamite | 20/40/80 for claimed Kourend medium/hard/elite | `KOUREND_FREE_DYNAMITE` |
| Bert sand | The Hand in the Sand complete; 84 buckets; excluded for UIM | `HANDSAND_QUEST`, `YANILLE_SAND_CLAIMED`, `IRONMAN` |
| NMZ herb boxes | At most 15, 9,500 NMZ points each; excluded for Ironmen | `NZONE_HERBBOXES_PURCHASED`, `NZONE_REWARDPOINTS`, `IRONMAN` |

Bert's delivery is shown as automatic after the Ardougne elite reward has been
claimed, including an otherwise non-member login. Diary tasks finished without
claiming their reward show `Claim diary reward`, rather than the higher allowance.
A claimed lower tier continues to supply its own allowance until the higher
reward is claimed. Supplies link into the shared bank focus. No price/profit is
invented: Zaff's fixed cost and NMZ's fixed reward-point price are shown.

The daily reset countdown uses 00:00 UTC. Crossing that wall-clock boundary never
changes a server claim bit or fabricates readiness. Login warm-up, missing native
state, logout and a character switch clear or hide readings. Reminder selections
use the existing guarded routine record and are included in backup/undo.
Personal checklist repeats retain their existing explicit user-defined timing;
they are not used to infer a game reward reset.

## Goals and next unlocks

The `Account progress` next-unlocks view offers actually unclaimed completed diary
rewards, Rigour/Augury unlock items, near-complete/prepared diaries, lower-tier
incomplete combat achievements and checked nearby music entries. Suggestions
retain their current native state and do not assert unmodeled access. Cards open
the specific diary/task/track or can add a goal. Immutable suggestion records also
feed the command dashboard and discovery providers.

Personal goals expose detached plan snapshots and a read-only set of active,
unachieved item, equipment-set, collection and explicit unlock item targets for
ground-loot targeting. Goal cards connect to bank focus, item sources, linked
boss guides, diary rewards, CA prerequisites and music quest prerequisites.
Skill targets show the training options unlocked between the observed base
level and target level. Diary/CA targets show source-backed rewards/point gains.
Missing typed skill/quest prerequisites can be queued before the parent goal.
Alternative routes remain alternatives; diary bank focus does not merge every
branch into a supposedly mandatory shopping list.

Newly reached goals emit a detached completion event once, after an observation
baseline for that particular goal. Old completions on login and a late first
observation do not replay a success event. Removal, archive, undo, sharing and
per-character generation guards remain in effect.

## Combat achievement account targets

All six `CA_THRESHOLD_*` native varbits are sampled alongside native CA points.
A complete, positive, strictly increasing set is used as the current account's
reward targets. This includes any Group Ironman adjustment supplied by the
game. Runewarden does not calculate a guessed group-size deduction. Missing or
incoherent native thresholds fall back to clearly labeled bundled reference
values. The observed `GIM_GROUPSIZE` is displayed when present.

Task-specific typed facts are combined with access requirements from their
exact linked encounter guide. **611 of 655 tasks** have a valid linked encounter
guide. There are also **8 directly typed task pages**, **5** of which have no
linked guide. Their union therefore gives source-connected checks for **616 of
655 tasks**, containing **1,221 source checks**. The directly machine-checkable
skill/quest subset covers **325 tasks, 332 typed checks**.
The remaining checks retain their source text and an unknown state. Strictly
anchored base-level facts and exact named completed quests are recognized;
partial-quest access, equipment alternatives, task assignments and protection
prose are not silently declared met. Encounter base-level requirements do not
pretend that boostability is verified. Task-specific verified boostable facts
retain their original flag. Composite choices evaluate as alternatives.

All 48 diary tiers and 492 task observations are unchanged in scope. Diary
boostable skill checks now accept an actually observed sufficient boosted level;
unboostable tasks still use base levels. Prepared-task counts and per-route
bank supply buttons reuse those checks. All music source routes can be opened,
with direct entry routing and known supply focus. Nearby music access sampling
is requested by visible account consumers; an unused hidden music collection
continues to avoid reading inventory/equipment for that workflow.

## Sources and validation

- Native mappings, claim predicates and membership/account restrictions:
  RuneLite **1.13.2** `DailyTasksPlugin.java` and the pinned API gameval IDs.
  BSD-2 notice: `licenses/daily-claims-runelite-BSD-2-Clause.txt`.
- Exact diary allowance/reward facts: the existing pinned Quest Helper diary
  snapshot `diary-catalog.json` and its retained source commit/attribution.
  See `account-progression.md` and `THIRD-PARTY` references in the bundle.
- Encounter and task requirements: existing revision-pinned `guides.json` and
  `combat-achievements.json`, retaining their primary Wiki URLs/revision IDs.
- Music routes and quest access: existing `music-unlocks.json` and native Music
  DB definitions. The current supported music bank/row bounds are unchanged.
- CA thresholds: native `CA_THRESHOLD_EASY/MEDIUM/HARD/ELITE/MASTER/GRANDMASTER`,
  `CA_POINTS` and `GIM_GROUPSIZE`, not an externally maintained threshold formula.

Focused automated checks cover native eligibility/allowances/costs, partial
claims, server-bit reset behavior, UIM/Ironman restrictions, automatic delivery,
profile isolation, stale actions, reminder preferences, native-width rendering,
no client calls while rendering, native thresholds, source-connected access,
boostability, alternative supplies, immutable consumers and milestone baselines.
A logged-in game pass remains necessary for server UI timing and interactions.

### Shared goal supplies and typed skill handoff

`GoalCatalog.goalSupplyTargets` resolves supply candidates for targets created
without explicit item IDs. Diary goals use native typed task/route item IDs,
CA goals use validated item references and their linked guide, and music goals
use exact sourced route item IDs. Item, equipment-set, collection, unlock,
quest and selected training-method targets retain their own explicit/source
items. Alternatives are candidate unions for highlighting and bank focus; this
does not claim every alternative must be owned. Virtual skill-guide illustration
items are excluded using the pinned API's top-level guide-item constants.
Currency progress and personal collection completion still use their original
authoritative observations. Archived and achieved goals contribute no targets.
The aggregate target set is cached on goal refresh, rather than rebuilding
source lookups for every ground-loot tick.

`openSkillTarget(Skill, long)` accepts only native individual skills and an XP
target of 1–200,000,000. It reuses an exact active target, or creates a new target
with current client XP on the game thread and focuses it after the save and view
refresh. It does not overwrite a different target or depend on an asynchronous
add-then-open timing race. Character/generation guards also cover the handoff.
