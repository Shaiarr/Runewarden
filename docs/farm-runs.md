# Farm runs

The **Farm runs** sidebar module offers herb, allotment/flower, regular tree, fruit tree, hardwood, giant seaweed, calquat, crystal tree, celastrus, redwood, Farming Guild contract, Hespori, Tithe Farm and bird-house guides. Selecting a run activates the existing bundled guide, so the same guide selection drives its coach, checklist and automatic bank-supply filter. **Open run checklist** opens that guide without resetting its current step.

**Track this run** changes only the native timer groups associated with that run. Other timer choices remain selected. The selected route picker is saved per RuneScape character. Restoring that picker does not replace an already active quest or another guide. Logout and profile transitions clear the old character's picker; delayed preference writes are rejected when their captured profile no longer matches.

Harvest statuses come from RuneLite 1.13.2's public `FarmingTracker` and `BirdHouseTracker` summaries. The sidebar names the actual native summary group rather than claiming an individual patch or crop is ready. A growth estimate becoming due means the group needs checking. No growth time is invented for unobserved patches, Farming Guild contracts or Tithe Farm.

The patch planner supports different crops, compost tiers, gardener protection and optional explicit yield assumptions for every included stop. Its remaining-stop preparation reconciles carried, bank and seed-vault observations and can focus those exact supplies in the bank. A started run exposes its next stop/progress in the harvest HUD and links to the shared loadout, guide and navigation modules. Exact native replant transitions can advance eligible stops; unverified compost/payment work stays available for manual confirmation. See [farm-patch-planner.md](farm-patch-planner.md) for model boundaries and sources.

The public native `FarmingContractManager` now adds the actual observed contract name, state and estimated completion to the dashboard and Farming Contracts route. This does not manufacture a growth time for an unobserved contract. A confirmed native bird-house completion is labeled observed; a time-based due estimate remains a check reminder.

| Run | Native timer group |
| --- | --- |
| Herbs | Herb |
| Allotments and flowers | Allotment and Flower |
| Regular tree, hardwood and redwood | Tree |
| Fruit tree, calquat, crystal tree and celastrus | Fruit Tree |
| Giant seaweed and Hespori | Special |
| Bird houses | Bird Houses |
| Farming Guild contracts and Tithe Farm | No growth timer |

These group mappings follow the pinned RuneLite source `net/runelite/client/plugins/timetracking/farming/PatchImplementation.java`, including `HARDWOOD_TREE(Tab.TREE)`, `REDWOOD(Tab.TREE)`, `CALQUAT(Tab.FRUIT_TREE)`, `CELASTRUS(Tab.FRUIT_TREE)`, `CRYSTAL_TREE(Tab.FRUIT_TREE)`, `SEAWEED(Tab.SPECIAL)` and `HESPORI(Tab.SPECIAL)`. `FarmingPatch` is package-private; the integration does not bypass that boundary or duplicate the native observations.

Representative run images use actual item sprites from `ItemManager`, with exact IDs audited against the bundled canonical OSRS item-name index. They are navigation artwork, not an assertion that the pictured seed or sapling is required for every player's route.

The movable harvest HUD now supports independent text, radial and combined countdowns. Its ring follows the remaining time since the current native deadline was first observed, rather than implying a known planting timestamp or a full crop-growth percentage. Crop estimates remain marked as estimates. See [gathering-timers.md](gathering-timers.md) for the timer provenance and display controls.
