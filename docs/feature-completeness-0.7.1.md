# Feature completeness assessment — 0.7.1

> Historical version record. See [the 0.7.2 release review](release-review-0.7.2.md) for the current active release boundary.

Assessment date: 17 October 2026. This evaluates the local source and bundled
content against the intended free, standalone RuneLite command centre. It is not
a comparison measured against every Plugin Hub plugin. Website synchronization
and an alternate minimap remain outside the product scope.

## Product judgment

**Approximately 7/10 as a connected development product.** Functional breadth is
around **8–8.5/10**; depth is around **6–6.5/10**. These are subjective judgments,
not test-derived scores or measured percentages of a finite specification.
They do not rate live reliability, performance or Hub readiness.

The strongest part is the connected workflow: a guide, goal or plan can lead to
preparation, item sources, bank focus, a route and a shared activity record. The
HUD, account observations, supplies, recoverable saves and profile boundaries
support those connections. The command centre is consequently more than a list
of unrelated tools. The larger ambition of a seasoned OSRS guide for every
activity still exceeds the supported mechanics and content depth.

Evidence: [capability-audit.md](capability-audit.md),
[acceptance-0.7.0.md](acceptance-0.7.0.md),
[`RunewardenPlugin`](../src/main/java/com/runewarden/RunewardenPlugin.java),
[`RunewardenCoordinator`](../src/main/java/com/runewarden/RunewardenCoordinator.java),
[`CommandCenterTool`](../src/main/java/com/runewarden/CommandCenterTool.java),
[activity-session.md](activity-session.md) and [hud.md](hud.md).

## Five substantive gaps

### 1. Encounter-specific boss and wave coaching

Boss Academy currently supplies authored phase notes, numbered references,
rehearsal state and attempt records. Exact matching owner-loot/death evidence can
record outcomes, but this is not complete live mechanic coaching for every boss,
raid or Gauntlet variant. Fight Caves entrance timing and an opening marker load
are separate from the remaining waves. Inferno and Colosseum use explicitly armed
static openings/references; they do not solve arbitrary observed wave states.

The missing depth is a complete encounter workflow with the relevant preparation,
phase progression, mechanic explanation and supported situational feedback
consistently available for each encounter. Bundled marker counts do not establish
that coverage.

Evidence: [encounter-practice.md](encounter-practice.md),
[wave-entry.md](wave-entry.md), [tile-markers.md](tile-markers.md),
[`EncounterPracticeTool`](../src/main/java/com/runewarden/EncounterPracticeTool.java)
and [`WaveEntryCoach`](../src/main/java/com/runewarden/WaveEntryCoach.java).

### 2. Specialist depth across minigames

There are **20 native activity contexts and 122 learning milestones**. Their
observation and coaching depth varies. Mahogany Homes recognizes actionable
furniture in a verified house but does not identify the assigned customer.
Volcanic Mine reads native conditions but does not infer the team vent assignment
or route. Mage Training Arena has room-specific advice and supported counters,
not a complete Telekinetic maze solver. Other contexts similarly combine native
current-objective information with manual learning controls.

This supports learning and supervision for those contexts; it does not establish
that every minigame, role, team strategy and method has a complete specialist HUD.

Evidence: [minigame-native-state.md](minigame-native-state.md),
[minigame-learning-plans.md](minigame-learning-plans.md),
[`MinigameActivityCatalog`](../src/main/java/com/runewarden/MinigameActivityCatalog.java),
[`MinigameLessonCatalog`](../src/main/java/com/runewarden/MinigameLessonCatalog.java)
and [`MinigameNativeObjectives`](../src/main/java/com/runewarden/MinigameNativeObjectives.java).

### 3. Full combat-model coverage

The workbench compares exact target variants, current/saved/sandbox equipment,
normal attack styles, supported spells/ammunition, ownership and estimated
purchase cost. It is not yet a complete specialist combat calculator. Raid
scaling, phase-dependent encounters and several named bosses remain unrated;
documented examples include Vardorvis, Zulrah, Corp and Nex/Nightmare scaling.
Special attacks, live defence drains, thralls, poison/venom, healing and movement
downtime are not modeled. Several ammunition, spell and set effects are also
unsupported.

As a result, a supported normal-attack ranking is useful evidence for a setup,
but cannot establish the best full-encounter setup for all content.

Evidence: [normal-combat-dps.md](normal-combat-dps.md),
[`DpsCalculator`](../src/main/java/com/runewarden/DpsCalculator.java),
[`DpsReferenceCatalog`](../src/main/java/com/runewarden/DpsReferenceCatalog.java)
and [`DpsComparisonTool`](../src/main/java/com/runewarden/DpsComparisonTool.java).

### 4. Consistent guide, training and source coverage

The **773 guide entries** contain **238 quest-category entries**, among other
categories; those counts are not a count of fully followed interactive helpers.
Exact stage mappings cover **191 quests and 2,473 literal stages**. The stage
adapter advances only a proven frontier and leaves unresolved branches or
unsupported stages unchanged. Optimal quest ordering ranks **220 of the 238**
quest-category entries; the remainder stay visible and unranked.

Training selection is also broader than its explicit quantitative models.
`ActivityXpCatalog` supplements **43 explicit activity scenarios** with guide
records to produce approximately **347 selectable methods**. Of the explicit
scenarios, **33 have published hourly-rate bands**, covering seven skills.
Sailing rank/courier reward scenarios do not establish hourly XP. This is not
evidence that every normal, AFK and intensive training option has a fully modeled
rate, access check and method-specific progression.

The item browser has **12,218 bundled named entries**, but its **1,577 offline
source/product/requirement cards cover 23 items**. Other items request their article
on selection; sources confined to prose or unusual templates may be absent from
the extracted cards. Combat Achievement coverage likewise distinguishes the
complete **655-task reference**, **373 separate strategy/method sections** and
**325 tasks with machine-checkable skill/quest checks**. Complete reference lists
are not complete contextual guidance or verified account access.

Evidence: [quest-stage-observation.md](quest-stage-observation.md),
[quest-order.md](quest-order.md), [item-database.md](item-database.md),
[combat-achievements.md](combat-achievements.md),
[`QuestStageProgress`](../src/main/java/com/runewarden/QuestStageProgress.java),
[`ActivityXpCatalog`](../src/main/java/com/runewarden/ActivityXpCatalog.java),
[`guides.json`](../src/main/resources/com/runewarden/guides.json) and
[`activity-xp-methods.json`](../src/main/resources/com/runewarden/activity-xp-methods.json).

### 5. Complete personalized planning

Find a plan, typed milestone plans, Slayer scenarios and patch-level farming
plans share useful observations. They do not jointly optimize all unlocks,
gear purchases, supplies, transport, time and expected returns. Unknown access
can still need confirmation; personal cost/risk/group annotations and rates
remain user assumptions rather than verified game facts.

Slayer has **12 sourced planning methods** and explicit task/point/block scenarios,
but duration/XP/net-GP projections require the relevant personal measurements.
Turael/Aya streak-reset skipping, task storage, Slayer-cape repeat rolls, partner
assignments, boss-sublist odds and complete eligibility/extension/weight scripts
are excluded. Farming covers **107 patch definitions**, while protection data
covers **49 crops, including 33 exact gardener-payment models**. It does not
automatically verify every patch access, transport option, charge or optional
cost. These are concrete planning limits, independent of display polish.

Evidence: [slayer-planning.md](slayer-planning.md),
[farm-patch-planner.md](farm-patch-planner.md),
[`OpportunityPlannerTool`](../src/main/java/com/runewarden/OpportunityPlannerTool.java),
[`WorkflowPlansTool`](../src/main/java/com/runewarden/WorkflowPlansTool.java),
[`SlayerPlanEngine`](../src/main/java/com/runewarden/SlayerPlanEngine.java) and
[`FarmPatchPlanner`](../src/main/java/com/runewarden/FarmPatchPlanner.java).

## Validation is a separate question

The gaps above describe missing functionality or incomplete content/model depth.
Passing automated checks does not close them. Conversely, the following
outstanding validation does not by itself mean an implemented feature is absent:

- Actual Windows startup, rendering/scaling, keyboard focus and in-game HUD use.
- Logged-in interoperability with the relevant native and external plugins.
- Long-session behavior and stock-versus-plugin CPU, RSS and frame-time
  measurements, including crowded scenes.
- External CI execution, public distribution and actual Plugin Hub review.

Headless layouts, mocked-client lifecycle tests, package verification and the
catalog/cache probe provide narrower evidence. They do not establish live
stability, a performance advantage over specialist plugins or Hub approval.
See [performance.md](performance.md),
[recovery-and-modules.md](recovery-and-modules.md) and the quality section of
[acceptance-0.7.0.md](acceptance-0.7.0.md).

## Priority judgment

Completing existing high-use encounter and minigame workflows has greater value
than adding more top-level categories. A full path from preparation through
in-activity coaching to an accurate run record would deepen the current product
more than another large reference count. This is a prioritization judgment,
not a commitment to implement additional features in this release.
