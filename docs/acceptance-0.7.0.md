# Agreed expansion — 0.7.0 acceptance record

> Historical version record. See [the 0.7.2 release review](release-review-0.7.2.md) for the current active release boundary.

This maps the four main workstreams and the original 13-feature/six-quality audit
to shipped workflows. It is a coverage record, not an assertion that every OSRS
mechanic or live compatibility combination has been verified. Detailed scope is
in [capability-audit.md](capability-audit.md) and the linked module documents.

## Four main workstreams

| Agreement | Delivered connection | Review evidence |
| --- | --- | --- |
| Broader minigame coaching | 20 native contexts with 122 learning milestones, richer current objectives/roles, numbered object/NPC references, phase following and manual learning controls | `MinigameLessonCatalog`, `MinigameNativeObjectives`, `MinigameRunProgress`; native/lesson/progress/layout tests; `minigame-learning-plans.md` |
| Unified activity/run tracking | One hub/run ID for all seven activity kinds, multi-skill XP, loot/costs/outcomes/milestones and searchable/comparable history; profile/assignment/route/instance evidence boundaries | `SharedActivitySession`, `ActivityHubTool`, `RunewardenCoordinator`; shared-session/coordinator/loot regression tests; `activity-session.md` |
| Full gear/DPS comparison workflow | Exact target search, multiple saved/sandbox setups, ranked comparisons, per-set styles/spells/darts, target scenarios, cost/ownership and readiness links | `DpsComparisonTool`, `DpsWorkbenchPlan`; calculation/workbench/206px tests; `normal-combat-dps.md`; unsupported formula contexts remain excluded |
| Deeper Slayer and farming planning | Slayer master eligibility, task/creature/method/preparation, honest weight scenarios and point/skip/block projections; patch-level crop/compost/protection, reconciled supplies, active routes and explicit XP/yield/price scenarios | `SlayerPlanEngine`, `SlayerMethodCatalog`, `FarmPatchPlanner`; planning/workflow/profile/layout tests; `slayer-planning.md`, `farm-patch-planner.md` |

## Additional audit features

| ID | Delivered workflow and connections | Scope retained |
| --- | --- | --- |
| F01 | Exact saved loadouts, quantities/alternatives/slots, spellbook and mandatory charge/effect/manual departure checks; missing bank supplies and item sources | Native hidden charges stay unknown; session-only confirmations are not portable completion |
| F02 | Protection/effect/boost HUD, native consumable restoration previews, pooled supported doses/charges, ammo/quiver/pouch thresholds and notifications | 647 ID-encoded item states; coarse clocks do not become exact countdowns |
| F03 | 48 diary tiers/492 native task observations, reward claims/benefits, boost checks, requirements, preparation and next-unlock opportunities | Unverified prose/account-specific requirements remain checks |
| F04 | Native clue/STASH/puzzle workflow, carried-versus-worn requirements, clue bank focus and item-aware routes; owned stale focus clears on clue change | Native core solving/highlighting remains optional integration; unvisited STASH stays unknown |
| F05 | Collection/pet/KC/gear/item/unlock/music/CA/diary goals with observed progress, acquisition/guide/preparation links and next-grind opportunities | Ownership, collection completion, spendable currency and population rarity remain separate |
| F06 | Shared ownership/currency ledger, supported containers, native pouch/quiver, item-location search and reusable preparation facts | Last-seen observations retain freshness; unvisited storage is unknown |
| F07 | Item-aware guide routes with source gates, saved destinations, observed banks/explicit bank stops and advertised activity worlds | Existing Shortest Path routing preferences are retained; no fabricated per-query risk option, auto-walk or auto-hop |
| F08 | Guide/clue/goal/personal ground targets with observed ownership and native deadlines, linked to item sources | Earned drops and floor observations remain separate; no pickup/drop earnings loop |
| F09 | Death journal, native recovery evidence, per-death copied return kit, missing-only bank focus, loadout and activity-guide return | Pre-death contents do not imply loss; no universal fee/timer or inferred deathpile type |
| F10 | Universal observed XP, verified recipe actions, active/elapsed rates, milestones, production chains and material allocation; Sailing reward scenarios | Reference and observed rates differ; 12 Sailing rank scenarios/438 courier rewards do not establish an hourly rate |
| F11 | Activity-aware stop/resource supervision, individual causes, delay/focus/sound preferences and intentional-downtime suppression | Generic activities need actual work evidence before idle/full-inventory alarms; no guessed interruption cause |
| F12 | Nine server-observed daily claim activities, eligibility/costs/partial claims, selected reminders and supply links; custom recurring routines | Native claims use native state; custom recurrence uses its stated UTC semantics |
| F13 | Unified paged/scoped search, account-aware Find a plan, editable typed milestone plans, progress-free sharing/import review and undo/redo | Personal cost/group/risk annotations stay personal; unknown access is not ranked as verified |

## Quality requirements

| ID | Included evidence | Remaining external validation |
| --- | --- | --- |
| Q01 Recovery | Safe versioned saves, rejected originals/last-good recovery, profile-bound backup preview/merge/undo and unfinished run checkpoints; malformed/future/schema/profile/restart tests | Live user backup/restore remains part of the testing pass |
| Q02 Lifecycle/interoperability | Real start/stop module contract, native overlay registration/locks, profile/hop/reconnect/queued-callback guards, public integration boundaries and mocked full-plugin lifecycle tests | Logged-in Quest Helper/Shortest Path/Bank Tags/loot/time/renderer combinations |
| Q03 Performance | Refreshed all-supported-static-data probe, 100,000 warm and cache cycles, bounded sources/history/UI lists and measured redacted module tick diagnostics | Stock-versus-plugin live CPU/RSS/frame-time and long-session measurements; no relative performance claim |
| Q04 Usability | Native RuneLite wrappers, narrow populated/expanded layout checks, keyboard focus/accessible labels, scoped/paged search, typed plan controls and independent display/alert settings | Actual Windows scaling/font/rendering and in-game interaction testing |
| Q05 Content/account facts | Coverage matrix, native account-specific CA thresholds, expanded source/typed requirements, native claim eligibility, Sailing, observed-versus-estimated provenance | Unsupported branches/effects/access models remain explicit, not claimed complete |
| Q06 Maintenance/release | Pinned API/dependencies, SHA-256 verification, unified dataset fingerprints/provenance/notices, CI workflow, source/changelog/package mapping and support report | Remote CI execution, public repository publication and actual Plugin Hub submission/review are not claimed |

## Product exclusions retained

Website synchronization and the alternate minimap experiment remain removed.
No feature performs gameplay inputs. The native client continues to own rendering,
login and account handling. Community distribution remains free and local.
