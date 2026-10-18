# Training plans

The training browser now stores explicit preparation, activity loops, method milestones and typed prerequisites for every real skill, including separate Attack, Strength, Defence and Hitpoints selections. The authored steps are shared with the existing guide workflows; they do not execute game inputs.

The current bundle contains 340 explicit method plans across 24 skills, 115 plans with sourced hourly reference bands and 118 models with XP per successful action. Guide-only activity entries remain browsable and are not counted as explicit plans. These counts describe breadth, not feature parity with every specialist plugin.

## Planning

Select a skill, intensity, method and target. Preparation & unlocks shows observed missing requirements, fulfilled requirements and facts that still need checking. A known missing requirement prevents starting that training session; the guide itself remains available. Quest facts use the same immutable account snapshot as goals, and an account reset clears the previous character's facts.

The path combines five-level goalposts with real method unlocks before the selected target. It advances from observed account XP. The current method's authored practice milestones and repeatable loop remain in disclosures so the sidebar stays readable.

Time estimates integrate each level separately. A level-scaled Wintertodt rate changes when the player levels, and a route crossing several reference bands uses the corresponding rate for each band. If any part of the target lacks a reference band, the browser reports that XP gap instead of extrapolating a source rate through it. The goals module uses the same projection.

Your pace accepts either XP/hour or successful actions/hour when exact XP/action is available. The latter excludes unsuccessful catches, burns and other failed attempts. Save this method's pace stores an override for the current RuneLite account profile. Clearing and saving removes it. Measured pace uses attributed training XP from matching method sessions and their running time, excluding unrelated observed quest/lamp XP.

## Sources and conditions

`tools/build-training-plans.py` rebuilds the metadata offline from the bundled authored guides and pinned RuneLite recipe facts. Each imported rate retains a Wiki source revision in `rateBasis`, with its setup and throughput assumptions in `rateConditions`. The primary training pages were retrieved through the public Wiki API on 17 October 2026; revision IDs are retained in `planSources.revisions`.

Ten additional current guides cover early Hunter routes, moonlight moths, rainbow crabs, lead rocks, infernal shale and small rubium rocks. Small rubium rocks require 48 Mining; the separate large rubium deposits still require 68 Mining. Their different items and workflows remain separate.

Examples include standard potion inventories, ordinary versus fletching knives, cooked-wine failures below 68 Cooking, bait gathering and fish-barrel use for karambwan, rune pouch/team requirements at Guardians of the Rift, and automatic versus manually offered bones with a specific bone choice. Reference throughput does not include collecting or making materials unless the conditions explicitly say so. These are conditional benchmarks, not guaranteed personal rates.

Where a source publishes level points rather than a complete formula, a band uses the envelope between adjacent published benchmark rows. Intermediate values are not presented as exact measured rates. No optimistic interpolation or extrapolation fills an unverified range.

Farming runs are short visits separated by growth time; their effective active XP/hour is not treated as endlessly repeatable training. Tithe Farm supplies a separate repeatable active Farming plan. Slayer pace remains task-, master-, block-, gear- and travel-dependent. Combat damage scenarios exclude monster XP modifiers and shared attack styles; Hitpoints and Scurrius use observed XP rather than an approximate generic damage model.

Sailing's current source still labels several generic rate estimates obsolete or provisional. Sailing therefore retains exact courier and trial reward scenarios, separate first-rank bonuses, the account's observed pace and explicit unlocks, without invented universal XP/hour. Its 438 known courier tasks and 12 trial rank scenarios remain available.

## Resource accounting

Recipe links identify exact XP/action and the corresponding bank recipe. Activity time and round scenarios stay independent of deterministic bank allocation. No supplies are removed or allocated a second time by choosing a time estimate, training target or personal pace. Cooking action counts describe successful outputs; they are not a claim that raw supplies cannot burn.

## Validation and remaining limits

The focused training/goal/session suite checks every real skill, typed missing/unknown/fulfilled unlocks, actual level transitions, scaled rates, absent bands, locked/one-off activities, unique goalposts, recipe identity, bank non-mutation, sidebar width, Sailing rewards and attributed measured pace. Windows and long in-game validation belong to the release acceptance pass.

Many reference rates require equipment or diary facts which cannot be established from skill levels alone. Those conditions remain visible rather than being silently assumed complete. Not every supported method has a published verified hourly rate, and not every skill has a legitimate AFK or intensive alternative at every level. A method's presence does not claim that it is always the fastest or cheapest route.
