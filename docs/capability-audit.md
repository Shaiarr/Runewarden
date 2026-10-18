# Runewarden capability audit

For current behavior and remaining live/review gates, see
[release-readiness-0.9.5.md](release-readiness-0.9.5.md). The
[popular Hub plugin comparison](hub-utility-audit.md) records specialist gaps.
Quest walkthroughs moved to the external Quest Helper in 0.9.3; Runewarden keeps
quest planning and native account progress. The current native-extension comparison also records the integrated World Hopper, its explicit manual controls and its measurement boundaries.

## Historical 0.7.3 implementation

This records implemented development workflows and their supported evidence.
The original agreement is mapped in [acceptance-0.7.0.md](acceptance-0.7.0.md).
Headless checks do not establish live gameplay, Windows execution or Hub approval.
Current release boundaries are recorded in [release-review-0.7.3.md](release-review-0.7.3.md).

| Capability | Implemented workflow | Evidence and scope |
| --- | --- | --- |
| Command centre | Current run, next plan step, selected preparation, farm route, low supplies, selected claimables and account opportunities in one front page | Character-bound immutable snapshots; links open the relevant module |
| Activity tracking | Universal hub for training, quests, minigames, farming, bosses, Slayer and other activities; multi-skill XP, loot/costs, outcomes, milestones and searchable/comparable history | One run ID; opt-in detection preserves manual runs; native assignment, route and instance identities constrain attribution; histories/checkpoints are bounded |
| Plan discovery | Find methods/quests against observed levels, quest state, active goals, effort, time and optional personal cost/group/risk settings | Known blockers and unchecked access remain distinct; published rate bands apply only at their stated levels; personal classifications are not authoritative game facts |
| Reusable plans | Editable ordered steps with exact skill targets, quests, guides, items, modules and destinations; next-step advancement, undo/redo and reviewed progress-free sharing | Native quest/XP completion and manual completion retain their source; saved character completion is never imported from another player's code |
| Loadouts | Exact quantities, worn slots, alternatives, spellbook, departure charges/effects and session-only manual checks; bank focus and item sources | Schema 2 preserves checks and reads schema 1; mandatory unknown checks prevent readiness; no blank claim of hidden charge knowledge |
| Ownership/currencies | Profile-bound carried/worn/bank and supported native storage observations, rune pouch/quiver, coffers and observed activity balances; shared item-location search | Last-seen quantities retain timestamps; unsupported or unvisited storage remains unknown, and historical snapshots are not current possession guarantees |
| Effects/supplies | Native effect/protection clocks, boosts/drains, native food/potion previews; pooled supported doses/charges, worn/thrown ammo, quiver/pouch supplies, low/zero reminders | 647 exact native ID-encoded charge states; hidden loads remain unknown; one-shot alerts respect banking and profile changes |
| Diaries/unlocks | All 48 diary tiers and 492 task observations, reward claims, typed source requirements, boosted/base checks, useful benefits and account opportunity cards | Task completion and claiming a reward are separate; unverified prose stays an explicit check |
| Clues/STASH | Native current-clue requirements, emote worn/carried distinctions, supported STASH observations, automatic/explicit clue bank focus and supply-aware routes | Native Clue Scroll/Puzzle Solver retain their solving/highlighting; clue changes clear owned stale targets and preserve unrelated preparation |
| Goals | Skill/quest, collection/pet, KC, gear/item, currency, unlock, diary, CA and music targets with source/guide/preparation links | Personal observations are separate from population rarity; native quantities, earned completion and spendable balances stay distinct |
| Travel | Guide item-aware public Shortest Path requests, native source access checks, 60 saved destinations, 12 observed banks, explicit bank-stop plans and activity-world browser | No walking/hopping automation; only verified owned routes are cleared; unknown/stale bank/access facts remain labelled; existing Shortest Path routing preferences remain authoritative |
| Ground loot | Current guide, clue and goal targets plus personal targets; native ownership, visibility/despawn deadlines and focused scene references | Earned drops are separate from floor observations; no additional earnings from pickup/drop loops |
| Death/recovery | Carried-before-death journal, native observed storage/fees, per-death copied return kit, missing-only bank focus and return-to-guide links | A pre-death item is not presumed lost; kit departure checks stay unknown unless verified; recovery types/timers are not guessed universally |
| Training/materials | Actual XP and verified recipe action observations, active/elapsed rates and ETA, milestones, deterministic material chains and shopping requirements | Shared inputs are allocated once; rewards/lamps are not treated as recipe actions; reference, observed and entered rates remain distinct |
| Sailing | 12 rank reward scenarios and 438 exact courier-task rewards linked to pinned primary revisions | No invented hourly rate or reward for unresolved task data |
| AFK supervision | Configurable delays, individual causes, native focus-aware notifications and optional sound across active contexts | Banking/travel/movement/pauses/offline suppress ordinary downtime; generic no-skill activities need work evidence before idle/full-inventory alarms |
| Native routines | Nine native claimable activities, server-observed availability/partial use, costs/eligibility, chosen reminders and supply links | Native claim state is not replaced by a fabricated local midnight reset; custom daily/weekly checklists retain explicit UTC semantics |
| Minigames | 20 native contexts and 122 authored learning milestones; native-state objectives, supported object/NPC highlights and optional guide/lesson following across all twenty | [Exact support matrix](minigame-native-state.md) and [learning plans](minigame-learning-plans.md); current score/phase observations do not imply a win or future mechanic prediction |
| Combat workbench | Searchable exact target variants, multiple saved/sandbox setups, ranking, per-set styles/spells/darts, target-stat scenarios, accuracy/max hit/speed/DPS/TTK and ownership/purchase budgets | Pinned supported normal-attack formulas only; unsupported effects/scaling are excluded from ranking; a theory setup is not called owned or ready |
| Slayer | Account/master/task filters, explicit access checks, base-weight scenario ranges, personal creature/method/rate choices, skip/block/point projections, area-constrained preparation and exact assignment progress | 12 sourced planning methods; chance models retain unverified modifiers; assignment credits are not mislabelled NPC kills; no automatic task actions |
| Farming | 107 native patches, per-patch crop/compost/protection choices, remaining supplies/ownership, active run/next stop, observed replants, contract information, timers and XP/yield/current-price scenarios | Player yield assumptions are explicit; unknown health/access/fees stay unknown; replant evidence cannot confirm compost or gardener payment |
| Encounter practice | Authored phases/numbered markers, rehearsal notes and attempt journals, exact native owner-loot/death evidence, linked shared activity runs | Static guide references are not an arbitrary live wave solver; unsupported group/raid outcomes remain manual |
| Music/CA/rewards | 853 music references, 655 CA tasks, native progress/reward targets, source access and linked preparation, five-tier popup/soundboard | 611 CA tasks link to source guides, 325 have typed skill/quest checks; validated native reward thresholds support account-specific targets; unavailable native thresholds retain labelled references |
| Global search | Paged/scoped search across modules, guides, items/storage, monsters, goals, plans, music, CAs and account opportunities | Eight actions per page, balanced bounded results and stale-profile/restart guards |

## Release controls

Versioned safe saves retain rejected originals and last-good records. Reviewed
backups include the active character and shared Runewarden settings, with merge
restore and undo. The recovery journal itself is not exported across characters.
Real module suspension stops service work; common account observations remain
available to consumers. Diagnostics retain bounded measured tick samples and
redact names, locations, inventories, setting values and exception messages.

Dependency locks/checksums, data fingerprints/source notices, parser checks,
Java regression/integration tests, native-width rendering and packaged-classpath
verification ship with the source. The refreshed local catalog/cache probe
includes the new static data and checks reference/cache bounds. Its measurements
do not establish a relative RAM/FPS advantage over specialist plugins.

## Remaining live release validation

Logged-in interoperability, long gameplay sessions, crowded-scene frame time,
live Windows startup and actual Plugin Hub review remain outstanding external
validation. Native-width/headless fixtures and mocked-client lifecycle tests are
reported as such. The project has no website integration or alternate minimap.
