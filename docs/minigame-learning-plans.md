# Minigame learning plans and current objectives

Runewarden 0.7 adds **122 authored, ordered learning milestones across all twenty native activity contexts**. The Minigames panel lets a player choose a plan before entering the activity, read a concise current milestone and use Complete & next, Back, Next, Undo completion or Restart. Complete & next immediately advances. The same panel can follow the current native objective across all twenty supported contexts. The overbroad 0.7.2 activity hold has been removed; see [minigame-coach.md](minigame-coach.md).

A followed native phase positions the reference plan; it does not check off unseen preparation or claim a completed game. Manual completion leaves automatic plan following off until the player enables it again. Leaving the activity or clearing the account clears the current visit journal and learning checklist. These are practice checklists for the current visit, rather than persistent achievement records.

| Activity | Milestones | Source lesson/reader detail |
| --- | ---: | --- |
| Wintertodt | 7 | Carried roots/kindling; chosen low-warmth threshold; a nearby incapacitated pyromancer with a carried rejuvenation potion. |
| Guardians of the Rift | 7 | Chosen fragment batch; carried fragments/essence/stones; elemental/catalytic contribution balance. A chosen batch cannot override carried essence or stones. |
| Tempoross | 7 | Actual fish quantities and live shoal, active spirit-pool and ammunition-crate NPC references for the current objective. |
| Giants' Foundry | 7 | Full commission, metal, mould, preform, temperature, tool and delivery learning sequence, backed by the existing native tool/heat reader. |
| Mastering Mixology | 6 | Paste, recipe, unfinished potion, requested processing station, delivery and reward sequence, backed by existing native order/item mapping. |
| Mahogany Homes | 6 | Contract, travel, remove, repair/build, customer and reward sequence; existing current-house action and proven-tier guide gates remain. |
| Pest Control | 6 | Four portal health/state facts. Open/shielded state comes from actual native portal NPC forms. Nearby spinners and observed open portals drive the current target references. |
| Blast Furnace | 6 | Preparation, coal, ore, cooling, collection and banking sequence; existing observed single-method guide retention remains. |
| Tithe Farm | 6 | Real unnoted fruit counts and sack references when harvested fruit can be deposited; dry plants still take priority. |
| Barbarian Assault | 7 | Exact received and outgoing native calls; role-specific objectives/NPC references; defender broken-trap priority; healer teammate health; wave-ten carried egg-form hand-offs. |
| Nightmare Zone | 5 | Preparation, entry, protection method, supply maintenance and finish sequence; native absorption and points remain current facts. |
| Pyramid Plunder | 5 | Highest eligible room from real Thieving, current native room/trap and room-advance objective. |
| Mage Training Arena | 6 | Preparation, all four room reference loops and reward sequence. Native room/table readers retain their exact evidence gates. |
| Volcanic Mine | 6 | Unknown-vent check objective, current boulder references and chosen exit reminder derived from an existing readable native clock. |
| Blast Mine | 6 | Observed excavated/loaded/destroyed rock forms select load/light/collect objectives, with real carried dynamite quantity. |
| Fishing Trawler | 6 | Deck-specific practice sequence; high-water objective only from a readable native percentage. |
| Drift net fishing | 5 | Net preparation, setting, herding, emptying and restocking; real fish-shoal NPC references during the herding objective. |
| Trouble Brewing | 6 | Crew preparation, gathering, processing, depositing, maintenance and reward sequence alongside native ingredient/inventory transitions. |
| Soul Wars | 6 | Preparation, capture, fragments, conditional offering, avatar support and reward sequence alongside current native facts. |
| Castle Wars | 6 | Preparation, flag states, capture, return, defence and finish sequence alongside current native team/flag/inventory observations. |

## Controls and presentation

Activity settings include a guardian fragment batch (0–500, default 100). Warmth and Volcanic Mine exit reminders use personal thresholds; both are configurable in Activity settings. The fragment preference is not an optimal-rate claim. Existing global HUD scale/lock and per-activity opt-outs remain.

NPC references, numbered target labels and compact HUD density have independent switches. Across the supported contexts, current contextual objects and NPCs receive numbered labels; these numbers identify observed resources/targets and do not assert safe standing positions. Existing verified Mixology template reference tiles retain their own numbered position markers. No unverified fixed tiles were added.

The scene overlay caches at most 64 object and 64 NPC render targets. NPC matching happens on the client tick against at most 2,048 native NPC entries; rendering uses the published cache and never enumerates NPCs or scene tiles. Its public-API access does not depend on another plugin being enabled. The bounded current-visit journal records up to eight recent objective labels, a monotonically increasing entry sequence and observed tick/phase-change counts. Current displayed numeric round scores are observations, not inferred rewards or wins.

## Native corrections and source evidence

The Defender interface uses the native detail/shout children 7 and 9. The prior shared Attacker/Defender branch incorrectly read children 8 and 10 for Defender; tests now cover both correct fields and misleading values in the old fields. Native identifiers for the new targets, egg forms, plants and portal forms come from the pinned RuneLite **1.13.2** `gameval` declarations, with the existing RuneLite notices retained in the distribution. Core Pest Control `Portal`, `PestControlPlugin`, `PestControlOverlay` and Barbarian Assault `Role` were reviewed from the retained pinned source artefact.

The learning text is original authored wording based on the existing bundled Wiki-linked activity guides and the retained Wintertodt/Strategies revision **15358528** and Tempoross revision **15358192**. Fresh web access to several activity pages was unavailable during this pass. The plans do not establish a fresh complete strategy audit of every game. Each lesson retains its primary Wiki reference; native-reader source links and source-artefact checksums remain in `minigame-native-state.md` and `minigame-extra-activities.md`.

These features do not supply every telekinetic maze solution, live wave solver, automatic team-role assignment, inferred future hazard timer, PVP opponent solve or automatic game input. Native game outcomes are not inferred from entering, leaving or a phase change. Live gameplay validation remains necessary for each native context.
