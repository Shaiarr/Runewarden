# Slayer planning and shared activity context

The Slayer module now contains a full character-specific planning workflow as well as the native assignment coach. It is a manual decision tool: it does not request, cancel, block or fight a task.

## Choose a master and a purpose

The planner can read the native task table for Turael/Aya/Spria, Mazchna/Achtryn, Vannaka, Chaeldar, Duradel/Kuradal, Nieve/Steve, Krystilia, Konar and Mortimer, independently of the currently assigned master. Unknown master IDs are not mapped to guessed tables. A **Hide locked masters** filter uses the character's combat/Slayer levels and observed Priest in Peril, Lost City and Shilo Village quest states. A potential level-99 Slayer cape access route is left for a check: level 99 alone is not reported as proof that the player has the cape. Mortimer access stays unverified because no complete verified access model has been added.

Each native table is indexed only while its planning page is visible, in batches of at most eight rows and an outer 1.5 ms budget. The relevant master and method quest-state queries are refreshed every ten visible ticks, rather than querying the entire quest list each tick. Native skill levels, named block slots, assignment unlock bits, reward block bits, areas and task bounds are copied into UI snapshots on the client thread. The temporary disable switch for the Fossil Island wyvern reward block is respected.

The list supports search and **Possible + unchecked**, **Known eligible** or **All task references**. Task decisions and personal measurements are independently saved per master and RuneScape profile. The selected purpose can be **Balanced, AFK, Normal, Intensive, Boss, XP or Profit**. Personal Prefer choices remain a personal ordering preference; they do not double the game's task weights. Personal XP/profit sorting requires the player's own entered rates. Unrated tasks are kept visible and labelled unrated rather than being assigned invented rates.

The bundled method layer contains 32 source-linked options, including ordinary and boss alternatives for bloodveld, abyssal demons/Sire, black demons/gorillas, greater/tormented demons, cannon dagannoth/kalphite/suqah, Vorkath, brutal/baby black dragons, Cerberus, Kraken, Guardians, Thermy and Hydra. Boss-method Slayer gates and relevant quest states are shown. New methods include preparation/sample/comparison milestones, and individual suggested items can be added to the plan without forcing mutually exclusive equipment alternatives. The original twelve options remain: multi-target Ancient spell methods for dust devils, Nechryael, jellies and smoke devils; stab/dragonbane methods for the four ordinary metal-dragon task families; Catacombs iron dragons; sustained gargoyle melee; short unextended dark beast tasks; and high-stat low-effort drakes. Native alternative creature families and matching bundled boss guides remain available for the full task catalogue. This is not a claim that every possible task has a hand-authored training method. In particular, possessing effective dragonbane gear can make metal dragons worth doing; there is no universal metal-dragon block recommendation.

## Task decisions, preparation and rates

The narrow sidebar uses three separate editor pages:

1. **Decision and access**: Prefer/Do/Skip/Block, desired effort, personal reason and confirmation of unresolved quest/area access.
2. **Creature and setup**: an exact NPC variant, its full wrapped name and static combat reference, chosen location, source-linked methods, item-name/ID search, saved supplies, required protection items, loadout/item/guide links and route previews.
3. **Personal rates**: planned task credits, credits per hour, Slayer XP per credit and net GP per credit. Blank is unknown; an explicitly entered zero is a real zero. Negative net GP is supported.

These rates use assignment credits rather than claiming that every task-counter decrement is one NPC kill. Bracelets, boss count mechanics and other native modifiers can make those differ. A manually chosen task-credit count overrides the scenario's default base-bound midpoint. Otherwise the calculation explicitly uses the midpoint of the native **base** task bounds, not an asserted statistical expected count. Extensions and other task-credit modifiers may require changing the personal count.

Supply item names come from the local item catalogue and icons from native RuneLite ItemManager images. The user can search, add and remove supplies, open their source records, and focus the bank on the chosen preparation. An active assignment's missing required protection/finishing equipment is also included, preferring an observed-owned alternative where possible. Explicitly chosen guide requirements are included. A worn Slayer helmet that already satisfies a protection rule does not add an unnecessary basic protection item.

For a location-restricted assignment the actual native assigned area stays authoritative. A boss/creature alternative cannot start the shared Slayer run until the user confirms that it is valid in that assigned area. Choosing a guide never silently removes the area restriction. Route previews to an authored boss-guide stop are offered only when the assignment is unrestricted.

Starting a Slayer run requires a matching active native assignment, an exact valid creature choice and any required area confirmation. The resulting immutable `Prepared` context carries the profile, task ID/name, selected creature, assigned area, selected guide ID, native remaining count, exact NPC IDs and required item IDs. Starting it is an explicit click; nothing in the client is clicked automatically.

## Assignment probabilities

`SlayerPlanEngine` evaluates known native stat requirements, alternate stat requirements, actual game blocks, unlocks, zero weights and the user-confirmed combat-checking dialogue setting. The player separately confirms unresolved quest/area access. Such a confirmation cannot override a known native block, failed stat requirement or disabled task unlock.

The denominator includes only tasks that can still be assigned. For fully indexed base-weight tables, with eligible weight K, unchecked weight U and a task's weight w:

- A known eligible task has probability bounds w/(K+U) to w/K.
- An unchecked task has bounds 0 to w/(K+w).
- A known ineligible task has probability zero.
- Missing cache rows prevent a positive lower probability bound.

Only a complete table with all relevant access checks resolved has a precise conditional percentage. This is conditional on requesting a task from the selected master. Native reward-dependent weighting metadata is read, but its full client-script semantics have not been verified. When an active such modifier exists, exact percentages are suppressed rather than assuming that the cached integer is an additive or replacement weight.

## Skip, block and point scenario

The **Simulation** page saves a combat-check setting, number of completed tasks, point schedule and optional explicit inclusion of unchecked accesses. It never silently treats an unchecked task as available. A base-weight scenario involving an active unverified weight modifier is also conditional and requires that explicit scenario option.

Personal planned blocks are removed from the modeled table and use the selected master's cost (40 Turael/Aya/Spria, 50 Mazchna, 60 Vannaka, 70 Chaeldar, 80 Konar, 90 Nieve/Steve, 100 Duradel/Kuradal or Krystilia), except for tasks already observed blocked in game. Planned skips remain in the table and cost 30 points per roll. With accepted weight A and skipped weight S, expected skips per completed task are S/A. This geometric expectation is used rather than S/(A+S), which would understate repeated skips. All-skipped plans have no finite completed-task projection.

The scenario applies the highest relevant completion milestone at 10, 50, 100, 250 and 1000 tasks; the first four consecutive tasks earn no points. Current normal, Wilderness and Mortimer streaks are read separately even when planning a different master. Known base schedules are supplied for the eight established master families. Observed claimed elite Western Provinces/Kourend diary rewards automatically adjust Nieve/Konar. Mortimer's full reward schedule remains user-entered. A personal six-value reward schedule can override defaults.

The result includes expected skip spend, completion rewards, new block cost, expected net points and the selected horizon. Free block capacity is compared against observed quest points and the elite Lumbridge completion bit (Mortimer's named family has two slots). Insufficient free slots/current points are shown. The calculation does not purchase slots, remove existing blocks or assume that expected spending guarantees a particular random sequence.

Task duration, XP and net GP are only aggregated when **every accepted task** has the necessary personal measurements. Missing coverage remains unknown rather than zero. The most frequent planned skips become explainable block candidates, including modeled skip-point savings per completed task and a break-even horizon using that master's actual block price. **Model this block** changes the personal scenario only.

## Storage and acquisition decisions

The **Storage & task hunting** page reads the named `SLAYER_UNLOCK_STORAGE` and `SLAYER_MASTER_STORED` public varbits. It reports the unlock and stored-master observation, reads the named storage unlock row's cost from the native DB when available (500-point sourced fallback), and explains free store/swap operations. It deliberately does not decode the packed stored-task varp into an invented name, location or kill count. The page provides the store, request, retrieve/swap workflow; the player performs every action in Slayer Rewards.

Point cancellation, Turael/Aya replacement and milestone boosting have separate calculations. Cancellation costs 30 points and preserves the streak. Replacement requires explicit confirmation that the task family is absent from Turael/Aya's list; it resets the relevant ordinary or Wilderness streak and shows a same-horizon reward comparison. The model does not assume Mortimer replacement rules. Nine-plus-one boosting uses absolute streak milestones, including the higher 50/100/250/1000 rewards, and does not confuse asking for an easy fresh assignment with replacing an active one. Mazchna/Vannaka default schedules are corrected to the updated 6/8 base points.

The cape scenario requires the player to identify the previous task from this same master and confirm carried/worn cape and continued eligibility. It applies `0.1 + 0.9p` to the repeat target rather than adding ten percentage points to the full normal roll. Uncertainty bounds remain bounds. Konar's repeated location is explained; a boss-family repeat is not reported as a guarantee of the same boss. Individual boss sublist probability remains unknown without verified sublist weights, rather than assuming a uniform distribution.

The personal-rates page accepts credits completed and elapsed minutes including travel/banking, computes credits/hour, and saves up to 20 samples with their method, creature and area in the per-master task plan. The five most recent measurements are shown for comparison. Selecting another method or changing the creature/area clears the active numeric rate model while retaining history, so an ordinary-monster sample is not silently applied to a boss alternative. These are explicit player measurements and never reinterpret task-counter decrements as NPC kills.

Partner assignments and a complete eligibility/extension/weight script emulator remain outside the probability model.

## Shared activity observation

`SlayerCoachTool.activityObservation()` returns an immutable, profile-checked view with stable task context, current remaining/original count, points/streak, selected NPC/item IDs and observed cumulative assignment credits, completed-task count and positive points changes for the current loaded profile session. Positive points changes are observed changes, not a claim that every increase came from a task reward. A count dropping to zero does not invent completion or remaining credits unless the corresponding native streak advances. Profile changes/logout clear the previous observation; the shared activity service owns durable run history and its own counter baseline.

Root integrations are callbacks rather than direct service coupling:

- `setStartActivityAction(Consumer<SlayerPlanningTool.Prepared>)`
- `setBankFocusAction(Consumer<Set<Integer>>)`
- Existing guide, item, loadout and route callbacks are retained.

Root schedules game-state changes on ClientThread and rechecks the captured profile before acting. Swing callbacks themselves use snapshots/local catalogues and do not read Client APIs.

## Validation and sources

Focused tests cover native filtering, probability denominators and uncertainty, combat-setting differences, reward unlocks/temporary wyvern block disable, geometric repeated skips, planned versus native blocks, point milestones/diary schedules, all-skipped plans, negative net GP, incomplete rates, old version-one preference migration, profile isolation, explicit creature/area start checks, immutable start contexts and native assignment observation semantics. Populated 206-pixel editor/simulator pages are rendered and checked, including an assertion that Swing rendering makes no Client reads.

Primary references: RuneLite 1.13.2 public gameval DB/Varbit/VarPlayer APIs; the existing pinned RuneLite task alias map and monster catalogue; OSRS Wiki Slayer task, Slayer training, Slayer Rewards, Pay-to-play Magic training, Metal dragons/Strategies, Slayer task/Iron dragon and Pay-to-play melee training. The new method dataset records source links, retrieval date and its separate Wiki licence attribution. No external block-list recommendation or default numeric XP/GP rate was imported.

The reward and block-cost update is sourced to the 2025 Summer Sweep Up Slayer changes, linked via the Wiki Slayer Rewards history. Cape conditions: https://oldschool.runescape.wiki/w/Slayer_cape. Storage facts: https://oldschool.runescape.wiki/w/Slayer_Rewards.
