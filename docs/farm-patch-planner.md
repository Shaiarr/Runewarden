# Patch-level farm runs

The Farm Runs category embeds a patch planner beneath the run selector and guide controls. It supports native patch observations, a character-specific route order, individual patch crops, preparation, a live run HUD and linked guide/loadout/navigation actions.

## Coverage

The bundled RuneLite 1.13.2 snapshot contains **107 patch definitions, 23 patch types and 95 produce definitions**. Farming routes initially select their relevant patch types: herbs; allotments/flowers; trees; fruit trees; hardwoods; giant seaweed; calquat; crystal; celastrus; redwood; Hespori; or Farming Guild contract patches. **Add other patches** allows any of the 107 patch definitions to be composed into a custom run, including bush, hops, cactus, mushroom and belladonna patches. The picker filters by patch type and pages ten results at a time; custom stops are validated against the full catalogue on restore. Bird houses and Tithe Farm continue to use their existing native timer/activity modules.

Patch state is decoded from actual native farming transmit varbits only in a region known to transmit that patch. The planner waits three settled ticks after entering a region or transmission context, and pauses observations while a modal interface is present or the player is in an instance. Catherby's floor/coordinate split, Falador/Port Sarim's boundary and Fossil Island's ladder/plane exceptions follow the pinned native definitions.

Unvisited patches remain **Not observed**. Saved observations retain their timestamp. A growing crop reaching an estimated deadline becomes **Due to check · estimated**; it never becomes a confirmed healthy harvest merely because time passed. Disease and death are shown only when actually decoded. Native observed states remain distinct from the player's manually checked route stops.

Quest/level/diary remarks cover the explicitly modelled patch gates, including Troll Stronghold, Weiss, Fossil Island, Morytania, Lletya, Prifddinas, Farming Guild tiers, Avium Savannah and the Harmony herb diary. Newer unmodelled access routes are marked unchecked and can be added deliberately. This is not a complete account-access solver: the Fire of Nourishment and every transport charge are not automatically verified.

Ordinary herb, tree, fruit-tree and all eight allotment choices have verified planting-level checks against the observed base and boosted Farming level. The source is the existing revision-pinned `SkillingResourceCatalog` Plant crop action or RuneLite's public `FarmingAction` enum, with additional primary Wiki facts for bushes, cacti, mushrooms and Yanillian hops. A level shortfall appears in preparation and the next-stop objective; native boosts satisfy the comparison. Choices without a verified fact remain unknown.

The protection snapshot covers **49 crops**, including **33 exact gardener-payment models**, fifteen herbs without gardener protection and disease-immune poison ivy. Players can enable payments for each selected crop type. Counts preserve full baskets/sacks rather than substituting loose produce; native validated noted variants count as carried payment. Payments are excluded where no native gardener is defined (including Harmony allotments), and observed Falador elite/Kourend easy diary disease-protection waivers remove unnecessary payments. Unknown payment models remain marked unknown.

## Preparation and routing

- Players include/remove patches, move stops up/down, mark them checked and start another run. Every patch can inherit its run's crop or select a different crop, opt out of replanting, select no/normal/super/ultracompost, and override gardener payment independently.
- Crop choices resolve exact seed/sapling names against the bundled item index and are offered only where that name has one exact ID.
- Allotment planting packs three seeds per selected patch; ordinary hops pack four and jute three; supported other planting routes pack one. Unknown newer fibre-crop quantities are not guessed. Quantities aggregate across remaining stops, with checked stops removed from the shopping/preparation list.
- The preparation list includes the spade, applicable seed dibber, chosen seeds/saplings and selected compost for supported compostable types. Actual OSRS item sprites accompany preparation items when the native image service is available.
- Current carried items, last-observed bank and seed-vault stock use the shared ownership ledger. Required, carried, stored and missing quantities remain separate. Unknown storage is not reported as a confirmed zero stock.
- The explicit bank-focus action uses the shared temporary bank filter without selecting another guide.
- Transport hints are static options from the Wiki patch-location descriptions. They are not a claim that the character owns or has unlocked each teleport. A route anchor can be captured from a matching native gardener/tool-leprechaun actually seen in the patch's transmit area, or explicitly pinned by the player from a tile in that area. **Route to saved anchor** sends this exact saved point through the shared navigation callback. It is an approach anchor, not an invented patch-centre coordinate; manual pins are retained rather than overwritten by wandering NPCs.

Unverified crop levels, seed amounts and protection checks remain visibly unknown. Runewarden's exact saved loadouts can supplement a chosen route with the player's transport supplies. Preparation and patch-picker disclosure state survives native refreshes, and open dropdowns are not replaced by background refreshes.

## Run workflow and coaching

**New run** starts a route, resets its checked stops and publishes its start callback. Pause, resume and finish are explicit controls. Merely browsing another run does not replace the active run. Restoring saved data preserves its route/progress and requires an explicit resume; character switches hide the old run immediately.

The harvest HUD shows the active route's next stop and checked/remaining count even when no native growth timer is available. Its farm-run menu opens the current route. Checked stops immediately disappear from preparation and scenario totals.

Automatic checking uses an exact, locally observed transition into the selected crop's initial growing state after a previously observed different patch state. An initial snapshot or elapsed timer never counts as completion. A seed being planted does not prove compost or gardener payment: those stops keep a **Replant observed · confirm compost / payment** checkpoint until the player confirms the remaining work. A replanted stop with no such pending work can advance automatically and retains **Native replant observed** evidence; manual confirmation retains a separate provenance label.

## XP, growth and optional yield scenarios

Named planting, health-check and harvest XP are taken from the existing revision-pinned primary Wiki resource facts, retaining their distinct actions and source. Full-growth base XP assumes successful growth and the corresponding checks/first harvest; it is not observed or guaranteed earned XP. Deferred herb planting credit is awarded only with the first harvest; an explicit zero-yield scenario does not award that credit. Missing action facts remain unrated.

Nominal growth durations come from the pinned native crop stage count and growth interval. Observed growing patches expose an estimated earliest/latest stage window; their first observed stage timestamp is preserved when a later observation merely refreshes last-seen time. Disease, death, unknown health and server cycle timing are not converted into a guaranteed completion claim.

Players can enter an explicit expected harvest quantity for each patch where the harvest action is verified. The estimate combines those user assumptions with exact harvested product IDs (grimy herbs rather than their clean icons), verified action XP and current native `ItemManager` prices. The consumed-input cost includes remaining seeds, compost and modeled protection payments, excluding reusable tools. Revenue and margin are shown only when every relevant harvest/yield/price fact is available. They are a full-growth scenario before GE sale tax, optional transport/clearance charges and any unmodeled bonuses, not a profit guarantee. Unpriced/untradeable inputs are not assigned fabricated prices.

Bird-house readiness continues to use the native public summary. Farming Guild contracts now read `hasContract`, `getContractName`, `getSummary` and `getCompletionTime` from the native public `FarmingContractManager` service; the dashboard opens the contract route from that observation. Seaweed and Hespori remain individually decoded patch states, with unknown/estimated/observed status preserved.

## Persistence and source maintenance

The `runewarden` character key `farmingPatchPlansV1` uses `RunewardenDataStore`: bounded schema validation, last-good recovery and protection against overwriting future/invalid data. Observations and route callbacks carry character and generation guards. There is no website integration, runtime Wiki polling or access to native plugins' private fields/configuration.

`FarmingTracker.predictPatch` is public but its `FarmingPatch` argument and `PatchPrediction` result are package-private in the pinned release. Production code therefore uses its own small data model rather than a private-API bridge. `tools/GenerateFarmingSnapshot.java` is an **offline** fixture that records the pinned native definitions into JSON; it is never compiled into the plugin or client. The originating BSD-2-Clause notice is retained in `licenses/runelite-farming-data-BSD-2-Clause.txt`.

`tools/build-farming-protection.py` builds payment and supplemental planting data from retained revision-pinned primary Wiki plant responses plus the cited Wiki tables. Ordinary tree/fruit/herb payment pages retain their exact revisions; the indexed allotment/bush tables and supplemental pages retain their source URLs. Runtime prices come solely from the native cached item-price service; no live Wiki request is made.

`FarmRunsTool.currentRun()` publishes immutable `FarmPatchPlanner.RunReading` values for shared activity tracking. `setStartAction`, `setRouteAction` and `setLoadoutAction` connect the command-centre modules; guide actions stay connected to the ordinary guide selector and bank supplies. Native client reads and price capture run on the client thread, while Swing uses published observations.

Primary sources:

- RuneLite 1.13.2: `FarmingWorld`, `PatchImplementation`, `Produce`, `FarmingTracker`, `CropState` and `PatchState` in the native Time Tracking plugin.
- <https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming>
- <https://oldschool.runescape.wiki/w/Farming/Patch_locations>
- <https://oldschool.runescape.wiki/w/Herb_patch>
- <https://oldschool.runescape.wiki/w/Allotment_patch>
- <https://oldschool.runescape.wiki/w/Bush_patch?oldid=14786922>
- <https://oldschool.runescape.wiki/w/Farming_contracts> (potato cactus protection payment; independently corroborated by the Allotment patch Snape grass Uses column)
- <https://oldschool.runescape.wiki/w/Yanillian_seed>
- <https://oldschool.runescape.wiki/w/Farming>


## Per-stop travel and reconciliation

Each of the 107 native patch records can save its own route/teleport, reusable equipment, consumed travel items, shared charge pool, charges needed for the stop, confirmed available pool balance and coin fees. Item fields accept exact local item names or IDs plus quantities. Missing/ambiguous item names are rejected rather than mapped to a guessed variant. Existing nearby transport suggestions remain visible. A custom stop can be pinned from its actual region as before.

Reusable equipment is packed once at the maximum required quantity across remaining stops. Consumed supplies add across stops; fees join the coin requirement. The bank focus and immutable shared `RunReading.requiredItems` now include these travel needs alongside crops, compost and gardener payments. Completed stops leave all these remaining-run totals. The input-cost estimate includes consumed travel supplies and fees, while excluding the purchase price of reusable teleport equipment. Teleport charges are an explicit player budget; carrying a charged item's ID does not manufacture an internal charge count. Pools shared by several stops aggregate usage and use the smallest confirmed stock budget if inconsistent edits exist. Unknown balances and shortfalls appear in the run summary.

Per-patch access confirmation can resolve an unobserved route/area; it cannot override an observed missing quest, level or diary requirement. The shared reading publishes inaccessible-stop, missing-charge and unchecked-charge counts plus travel fees for other modules. The run stays manually startable so players can use their own transport/access knowledge, and all route edits retain the existing captured-profile/generation checks. Older version-one routes acquire an empty travel map through default-field migration.

Focused pure tests cover reusable-vs-consumed reconciliation, pooled charges, unknown balances, fee costs, immutable publications, old-save migration, invalid quantities and completed-stop removal from the real farm-run supply publication.

## Patch scene HUD and observed compost

`FarmPatchOverlay` projects a timer above each nearby actual patch object. The object's native definition varbit must match a locally transmitted patch record: the scan does not guess a patch from a nearby tile name. It checks at most 1,089 nearby tiles every five ticks and publishes at most 24 unique patch anchors. Multi-tile objects are deduplicated. The renderer checks the original world view, scene, base coordinates, plane, login and current RuneScape profile before drawing; instanced scenes are excluded instead of projecting ordinary-world patch data into an unrelated instance.

Growing crops show an explicitly estimated earliest–latest maturity range and a dashed gold dial using the shared timer renderer. A passed estimate becomes "Due to check", never a manufactured harvestable reading. Native harvestable, dead, diseased, weeds and empty states remain actual observed states. The Farm runs → Patch HUD controls enable patch timers, observed-compost labels and Rings/Compact styles. Preferences remain in the character's validated farm-planning save.

Compost is observed using the public menu and server-chat events used by RuneLite 1.13.2's native `CompostTracker`: an Inspect or compost/spell target must identify the exact patch varbit and current scene; a matching GAMEMESSAGE/SPAM confirmation must arrive within 30 seconds while the player is beside the target footprint. A click, selected ultracompost plan, carried bucket or unrelated chat message never proves treatment. Ordinary, super and ultra confirmations are distinguished. Unknown stays unknown. The tracker uses only Runewarden's own save and never writes native Time Tracking configuration. Old-session treatment is retained as last-seen information until revisiting; fresh session observations require fresh confirmation rather than asserting the old treatment belongs to a newly planted crop.

The event parser's four message forms derive from the pinned RuneLite BSD-2-Clause CompostTracker source/bytecode. The complete Copyright (c) 2022 LlemonDuck BSD-2-Clause notice is retained in the source and bundled runelite-compost-tracker-BSD-2-Clause.txt. Source: https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/timetracking/farming/CompostTracker.java. Generated compost-transmit symbol names are not assigned an invented numeric encoding.
