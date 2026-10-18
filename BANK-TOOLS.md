# Guide bank search and banked experience

The guide bank filter uses RuneLite 1.13.2's `BankSearch.initSearch/reset/layoutBank` and the ordinary `bankSearchFilter` script callback. It creates a temporary uniquely named search. It does not create or alter bank tags, tabs, layouts, withdrawals or deposits. The user's prior search is restored when the filter is cleared or its guide is closed. Closing the bank releases the filter without opening a chatbox. Typing a different search or selecting another plugin's search suspends Runewarden filtering until the next bank visit.

Every one of the 773 bundled entries now has an explicit supply policy: named items, equipment/consumable alternatives, or a verified activity with no outside bank items. The filter activates when any guide is selected and the bank opens, including an empty filtered result for item-free activities. The machine-readable coverage audit is `docs/bank-supply-coverage.json`; the policy inputs are `guide-supply-plans.json` and `guide-loadouts.json` in the plugin resources.

Exact IDs come from the pinned canonical item snapshot and explicit source requirements. Audited policies are authoritative: older fallbacks cannot add unrelated bars, logs, seeds, ores or combat equipment. Farming profiles distinguish patch types and compost/watering-can variants; fixed production methods show their actual inputs. Equipment alternatives use real RuneLite item statistics and the selected Guide default / Melee / Ranged / Magic / All alternatives view. Twelve existing website boss presets retain their style-specific exact item IDs; other encounter profiles are clearly equipment alternatives rather than asserted optimal setups. Gauntlet/Hunllef, supplied-equipment minigames and marker-only views have explicit outside-item-free policies.

NPC requests, Slayer assignments, Gnome Restaurant orders, chosen spells and other genuine variable requirements use a compact **Choose requirement** bank picker. Its actual pending requirement and selected item names are visible. Chosen items and the equipment view persist per RuneLite profile, normalized character and guide, are merged immediately, and never carry over to another guide or account. The picker uses the actual observed bank; it does not invent task choices. General named aliases and already-covered food/equipment/transport families are resolved automatically. Selecting a guide with a variable requirement therefore activates its filter immediately while exposing the remaining choice.

Bank search acquisition defers while Withdraw-X or another non-search input is open. User and other-plugin searches are respected until the next bank visit or explicit selection of another guide. Search restoration keeps a closed bank out of chatbox search-input mode. A bounded initial refresh accommodates equipment metadata becoming available after bank opening.

Coverage is universal at the guide-selection and policy level. This does not claim that every encounter profile is an optimal gear setup, or that a static guide knows a player's randomly assigned item/order before the player chooses it. Dynamic family pools are genuine alternatives, and the variable picker exists for those decisions. Base healing-food families exclude harmful rock cakes/nightshade and restricted blighted supplies; context-specific items can still be explicitly selected where applicable.

`BankedExperience` reads the real BANK container only while the bank interface is visible. It holds an in-memory snapshot, records when it was observed and updates while the bank is open. Inventory data never stands in for bank data. The local calculator is standalone and has no account sync or bank upload. Logout, connection loss, account changes and plugin shutdown clear the local snapshot. The last observation is retained over a world hop for the same account.

## Recipe coverage

There are 210 recipes across eight processing skills:196 XP-bearing methods and 14 zero-XP unfinished-potion preparation steps.116 recipes have an explicit deterministic output model.

| Skill | Recipes | Methods |
| --- | ---: | --- |
| Crafting | 27 | Sapphire through dragonstone cutting, flax spinning, glassblowing, gem bracelets, dragonhide bodies, elemental battlestaves |
| Fletching | 29 | Longbow cutting/stringing, headless arrows, metal/amethyst/dragon arrows and darts |
| Herblore | 47 | Fifteen grimy herb cleanings, fourteen unfinished-potion preparation steps, and eighteen standard potions |
| Firemaking | 16 | Every normal-log entry in the pinned client calculator, including Sailing trees |
| Cooking | 22 | Twenty-one raw fish and wine |
| Smithing | 13 | One-bar daggers and regular-furnace bronze/silver/gold/steel/mithril/adamantite/runite smelting |
| Prayer | 53 | Burial/scattering and an explicit 3.5× lit gilded altar alternative for bones |
| Construction | 3 | Eight-plank oak larders, six-plank teak benches, six-plank mahogany tables |

The selected skill's plan allocates observed ingredients once. **Use production chains** orders selected producers before consumers, so an uncut gem can become jewellery, logs can become strung bows, ores can become bars and daggers, and grimy herbs can become finished potions. Produced items are forecast material balances; the real bank observation is never changed. Competing selected alternatives retain their priority and cannot reuse the same input or intermediate. The ordered production steps expose each conversion. Turning chains off restores the original direct-material scenario and excludes zero-XP preparation steps. Recipes above the character's current unboosted level allocate no materials; projected XP does not assume the player already unlocked a later method.

Current XP, allocated banked XP, projected XP/level and remaining target XP are shown. Targets accept either level 1–99 or XP 0–200,000,000. The selected completion method builds a consolidated material shopping list after the bank plan's allocations, recursively using only explicitly selected, unlocked upstream conversions. It accounts for XP earned by those conversions when choosing the smallest number of final actions. Per-skill XP stays separate: smelting gold does not count as Crafting XP. Locked upstream methods are bought as intermediates; a locked final method is rejected. Quantities use checked integer arithmetic, and the original bank map stays untouched. Source buttons open the item database and the shopping list can be copied.

## Sources and deliberate limits

XP and level values come from the official pinned RuneLite 1.13.2 `CraftingAction`, `FletchingAction`, `HerbloreAction`, `FiremakingAction`, `CookingAction`, `SmithingAction`, `PrayerAction`, `PrayerBonus` and `ConstructionAction` calculator sources. Each recipe records its source enum member. Source archive used: `net.runelite:client:1.13.2:sources`. Actual ingredients use canonical item IDs from the same website item snapshot. Standard ratios were checked against the wiki's [Crafting table](https://oldschool.runescape.wiki/w/Crafting), [Herblore table](https://oldschool.runescape.wiki/w/Herblore), [Fletching table](https://oldschool.runescape.wiki/w/Fletching), [Smithing table](https://oldschool.runescape.wiki/w/Smithing) and [Construction table](https://oldschool.runescape.wiki/w/Construction). The imported name data retains the site's existing wiki-derived attribution and licence notices.

Cooking is potential XP before burning or bad wine. No deterministic cooking output, success probability, reusable Prayer input, construction refund or Firemaking ash output is invented. Tools, quest/room access, moulds and other reusable requirements are described for the chosen method and are not consumed by the material calculator. Thread for dragonhide crafting is not allocated because consumption is probabilistic. Herblore's selected chain explicitly consumes a clean herb and vial of water per unfinished potion and awards no XP for that preparation step. Vials and herbs must be observed, produced or present in the shopping list. The preparation ratios and levels are sourced from the Wiki's [unfinished potion creation calculator module](https://oldschool.runescape.wiki/w/Module:Unfinished_potion_creation_calculator). Druidic Ritual and Tai Bwo Wannai Trio requirements remain in the relevant method notes.

The deterministic recipe plan does not infer outfit bonuses, saved materials, chemistry/production effects, boosts, account restrictions, tools, alternate coal ratios, Blast Furnace, Goldsmith gauntlets, Farming growth yields, rune multipliers or variable minigame rewards. Foundry, Mixology and Mahogany Homes now have separate activity scenarios. Prayer altar rows explicitly require both burners lit and do not assume saved-bone procs. Pinned RuneLite display values are used consistently, including its rounded base XP values. Activity scenarios and the broader method browser are described below; their XP is not added to this material plan.


## Training methods and activity scenarios

The calculator has **Banked materials** and **Training methods** views. The latter includes every one of the 285 training and 51 minigame guides, searchable by skill, name and AFK/Normal/Intensive intensity. It adds 43 curated, revision-sourced method scenarios, including nine current Sailing methods. Activities without a single training skill appear under Activities and link to their activity guide; they do not receive an invented XP value. The 17 Runecraft guides use the correct RuneLite RUNECRAFT alias. Typed guide level requirements and unlock requirements remain visible.

Current levels and XP are observed on the client thread for every skill. Rate calculations use the method's level-qualified source band; a rate documented only at level 81 is not assigned to a level-60 player. Entering an observed XP/hour overrides the reference for time-to-target. Estimates use current-level rates and do not pretend a fixed rate remains exact through every future level. Unknown rates require a personal observation. Existing step-by-step guides provide milestones and supplies through the Open guide button, and Goals can open a particular method directly.

The method view is an **independent scenario** using the complete observed bank. Its XP is never added to, or subtracted from, the deterministic recipe plan. Selecting an alternative compares uses of the same materials; it does not spend them or chain produced items into another method.

* **Giants' Foundry:** bronze/iron (9/19), iron/steel, steel/mithril, mithril/adamant and adamant/rune (14/14). Ordinary bars, weapons and armour are counted by actual material equivalents: a battleaxe contributes two bars; a platebody four. Inputs belong to exactly one metal. Both alloy components limit whole swords. Users enter final sword quality; hand-in XP applies the exact formula `(floor(q²/73) + floor(1.5q) + 1) × 30`. Forging-action XP is explicitly additional, not fabricated. Reference hourly rates use the source's average optimal commissions and moulds (up to level 81), no Smiths' Uniform, with the source's ±10% performance spread. No mould unlock is assumed from the bank.
* **Mastering Mixology:** 15 clean herb species and 14 corresponding unfinished potions convert to exact Mox/Aga/Lye paste quantities. Grimy herbs are excluded until cleaned; Huasca's unfinished potion is not yet mapped. Ten selectable potion recipes use their actual level, paste mix and requested-potion XP. Every required paste limits the number of potions. Refining XP (6 per input herb or unfinished potion) is shown separately from potion XP. Order availability is stochastic, so a single-recipe scenario assumes those potions are eventually requested. No digweed, processing speedup XP, multi-order resin bonus or reward purchase XP is invented. At level 81 the sourced time reference is 45k/hour without speedups to about 70k/hour with all speedups; lower levels require an observed rate.
* **Mahogany Homes:** beginner, novice, adept and expert average contracts use the Wiki's published average plank, steel-bar and total-XP values. Both material types limit the scenario. Contracts vary, so the UI labels an average estimate and ±10% variation. Full carpenter outfit is an explicit opt-in 2.5% XP modifier. The default time bands exclude a plank sack and other optional travel improvements. Tools, teleports and access requirements remain visible.
* **Wintertodt:** group roots versus fletched kindling references scale the Wiki's 350k and 290k level-99 Firemaking rates by current level/99. No banked logs or reward-crate XP is assumed.
* **Guardians of the Rift:** user-entered successful rounds show the exact `45 × current Runecraft level` completion bonus, conditional on at least 300 energy. Crafting XP is explicitly additional. Total training-time estimates require the player's observed XP/hour; ordinary banked essence is not consumed.
* **Tempoross:** the solo cooking reference is sourced at level 35 and 99 only. Intermediate levels require an observation; the calculator does not silently interpolate. Harpoonfish are gathered inside the encounter.
* **Hunters' Rumours:** the optimised Master block-list reference is qualified by level, with level-99 Hunter cape assumptions stated; an Aco expert scenario is limited to the documented level-99 rate. Actual assignments, block lists and travel affect results.
* **Agility:** rooftop references omit optional diary/teleport bonuses; Brimhaven includes active pillar/spike and low-focus floor-spike variants. Hallowed Sepulchre covers all five floors with private-instance/practised-player assumptions. Colossal Wyrm basic/advanced routes have separate rates.
* **Sailing:** nine revision-linked method references cover current training choices. They do not assume one universal XP/hour rate. One-off charting rewards are distinguished from repeatable training; use an observed pace where a defensible level-qualified rate is unavailable. See `docs/activity-session.md` for the shared activity and training workflow.

Numerical method data was fetched from current OSRS Wiki API revisions on **16 October 2026**. Every curated method stores an `oldid` URL, and the dataset's attribution and licence are bundled at `src/main/resources/com/runewarden/licenses/activity-xp-CC-BY-NC-SA.txt`. No per-hover or per-calculation HTTP request occurs. Production data parsing uses the client-injected Gson in ActivityXpCatalog; the older static recipe loader now uses JsonParser without creating a Gson instance.

Validation covers all 336 guide links, Runecraft alias mapping, level-qualified rates, Wintertodt scaling, exact Foundry quality rounding and two-bar battleaxes, no repeated material ownership, missing alloy/steel/paste limits, explicit outfit application, ignored grimy herbs, separate refining XP, target-time validation, GOTR conditional completion bonuses and sidebar navigation/width.

Performance: the shared ActivityXpCatalog builds immutable, sorted per-skill method indexes once for both Goals and the training browser. Account/bank snapshots continue to update while the sidebar is hidden, but hidden calculator views do not reconstruct Swing rows. Showing a view renders the latest snapshot; queued visible refreshes are coalesced.

## Sidebar layout

The calculator uses the shared SidebarUi building blocks at the actual narrow sidebar width. Banked materials and Training methods have separate compact control/result cards; switching views measures only the active page, so a hidden long material list does not create blank scroll space. Item and result text wraps to its current parent width. Personal pace, method notes and unlock details are collapsible; Foundry quality, Mixology potion, Homes outfit and Rift round inputs appear only for the relevant method. The bank filter uses a short status and a conditional missing-requirement picker, with the complete chosen-item names available on hover. Existing account, search-ownership, local cache and hidden-refresh behaviour is preserved. Layout checks exercise both a 206-pixel content column and RuneLite's 242-pixel wrapped panel.

## 0.8 preparation and loadout depth

- Saved loadouts can preserve exact inventory positions with **Save inventory positions**.
  Ordinary saves continue using aggregate quantities. Positions are editable and survive
  Runewarden JSON export/import. Native bank tabs, Bank Tags and Inventory Setups settings
  are untouched; typing a search or another bank filter immediately takes ownership.
- **Pool potion doses** converts saved carried potion bottles into a minimum-dose budget,
  using verified RuneLite potion-charge data and explicit variants. The item editor also
  offers dose variants and RuneLite item-variation IDs. Dose pooling does not equate an empty
  vial with a usable potion. RuneLite variants are explicit alternatives; sufficient charge
  or degradation state can additionally be required through departure checks.
- Integral item allocation reassigns overlapping alternatives so a generic food requirement
  cannot consume the only item needed by a second exact requirement. Slot-specific and
  any-slot requirements share the same physical inventory pools; one stack cannot count
  twice. Dose budgets consume remaining bottles and share only their unallocated doses.
- Carried readiness remains separate from last-seen bank quantities. Bank counts now retain
  the observation time and distinguish this-session observations from previous-session
  snapshots. Neither stale bank contents nor potion storage can make a carried plan ready.
- Visible native potion-storage rows are read without scripts or game actions. Amounts are
  labelled as **doses**, stored under one-dose potion identities and shown separately from
  item quantities. Unreadable, abbreviated or missing native labels remain unknown. Only
  potion families recognised by the bundled RuneLite charge catalog are supported.
- Guide bank supplies include the current quest step's concrete items as well as whole-guide
  supplies. Moving to a step with different requirements refreshes the existing temporary
  search; the filter still yields immediately to user searches and other filter owners.
- Storage currency labels now use points for reward currencies and gp for coffers.

The native bank filter changes only its temporary local search, never bank layouts or
items. Runewarden JSON is its own loadout interchange format; importing another plugin's
private settings or undocumented setup format is not claimed.
