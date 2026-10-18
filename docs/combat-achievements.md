# Combat Achievements

The **Combat achievements** sidebar combines the complete task reference with observed account completion and a character-specific plan. The snapshot contains **655 tasks**, native IDs **0–654**, worth **2,697 points**. It was retrieved on 16 October 2026 from the OSRS Wiki's `combat_achievement` Bucket and every task's revision API page.

**Recommended next** lists unfinished tasks with lower tiers first, then kill-count/mechanical/restriction work before speed work. Verified unboostable missing requirements are excluded. This is a transparent progression heuristic, not a claim that a task is the globally fastest or that unverified account unlocks have been met. **Browse tasks** filters by Easy, Medium, Hard, Elite, Master or Grandmaster, boss/activity, task type, text and incomplete status. Only six task cards are constructed per page.

Open **Method** for the exact objective, preparation/restrictions from its source article, applicable observed unlock checks, and the available task-specific strategy. **373 tasks have a separate sourced Strategy/Method section**; simpler kill-count and other tasks retain their exact objective and task-specific introductory notes. Native completion is never replaced by ticking a checklist. Referenced items open the shared item browser, and matching boss/activity guides use the normal guide/marker selection. Every task retains its exact Wiki revision link. Strategy text is a static guide; it does not read attacks or control actions.

**My plan** supports adding tasks, queueing the next five suggestions explicitly, removing and reordering tasks, and keeping a reward target. The plan's unearned point sum excludes tasks actually completed. Plans retain up to 100 unique valid IDs and live under the current RuneScape profile. Delayed actions capture the displayed profile and generation; a button from a previous character cannot edit the next character's plan. Game completion cannot be toggled by these controls.

## Native completion and points

The task ID is the bit index in RuneLite's public `CA_TASK_COMPLETED_0..20` varplayers: chunk `id / 32`, bit `id % 32`. The array uses the pinned 1.13.2 symbolic IDs, preserving the noncontiguous later varplayers. Unsigned shifts handle bit 31 correctly, and the 21st chunk covers the newest IDs above 639. The mapping is independently corroborated by the public `AKAddons/runelite-goal-planner` tracker and Wiki Bucket's native IDs; no code from that plugin is copied.

The account point total is the observed public `VarbitID.CA_POINTS`. The six
native `CA_THRESHOLD_EASY/MEDIUM/HARD/ELITE/MASTER/GRANDMASTER` varbits supply
account-specific reward targets when all six values are positive, strictly
increasing and no greater than 100,000. This includes any Group Ironman adjustment
provided by the game; Runewarden does not calculate a group-size deduction.
Observed `GIM_GROUPSIZE` is displayed when available. Missing or incoherent native
thresholds fall back to **Reference reward targets**: the revision-sourced Wiki
values **41 / 169 / 436 / 1,100 / 1,965 / 2,697**. Those fallback values match the
cumulative weighted points in the bundled task set. New task releases still
require refreshing the task snapshot and reference targets.

Task-specific requirements are combined with those of the exact linked encounter
guide. **611 of 655 tasks** have a valid linked guide. Eight task pages also have
directly typed requirements; five of those lack a linked guide. Their union gives
**616 source-connected tasks and 1,221 source checks**. Within that union,
**325 tasks have 332 machine-checkable skill/quest checks**. These counts describe
coverage, not complete verified access to every encounter: other source checks
retain their text and an unknown state. Partial-quest access, equipment
alternatives, task assignments and conditional protection requirements are not
silently considered satisfied.

Every five game ticks, sampling reads the 21 packed completion vars, points, six
native thresholds, group size and only the skill/quest facts required by the
combined access index. All task methods and context remain available when access
cannot be verified. Group size, access choices, equipment and conditional
restrictions retain their source descriptions. See
[progression-workflows.md](progression-workflows.md) for the shared goal and
preparation connections, and
[`CombatAchievementCatalog.requirements`](../src/main/java/com/runewarden/CombatAchievementCatalog.java)
and
[`CombatAchievementsTool`](../src/main/java/com/runewarden/CombatAchievementsTool.java)
for requirement merging, observation and threshold validation.

## New completion callback

`setCompletionAction(Consumer<CombatAchievementCatalog.Task>)` emits only new known task transitions after an initial baseline. Three stable samples five ticks apart must agree, with native points matching the sum of the known completed tasks. Historical login bits establish the baseline without popups. Subsequent consistent false-to-true transitions emit each task once; point/bit regressions trigger fresh calibration. Logout, profile changes and stop discard the baseline. Unknown future tasks or inconsistent transmissions cannot produce guessed celebrations.

Integration API: `start()`, `stop()`, `clear()`, `onGameTick()`, `createPanel()`, `setOpenGuideAction(Consumer<String>)`, `setOpenItemAction(IntConsumer)` and the completion callback above. The module makes no network requests, changes no native settings and performs no game inputs.

The reproducible adapter is `tools/build-combat-achievements.py`. Its input API responses were retained during development; output records source revisions for all 655 task pages. Wiki data attribution and adaptation terms are bundled in `licenses/combat-achievements-CC-BY-NC-SA.txt`.
