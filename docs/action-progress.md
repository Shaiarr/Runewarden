# Action progress HUD

The movable **Action progress** HUD has a filling bar, remaining-time ring, or both, with optional seconds. Animation/cast progress and inventory-batch progress can be enabled independently. Its black, white and gold presentation uses the shared HUD style controls and `action-progress` layout entry. Preferences belong to the Runewarden local data store; no client input is generated.

## Timing evidence

| Display basis | Evidence | Scope |
| --- | --- | --- |
| Cast cycle | Local High Level Alchemy animation; five game ticks / 3 seconds. Low Level Alchemy and Superheat Item use three ticks / 1.8 seconds. | Fixed, sourced spell cycles. This does not confirm a successful cast or grant XP. |
| Standard cycle | A matching selected or confirmed recipe and its local animation. | Potion secondaries 2 ticks; gems 2; battlestaves 2; d'hide bodies 3; glass 3; jewellery 3; bow cutting 3; bow stringing 2; cook-all fish first 3 and then 4. Times carry `~` because start observation and chosen game interaction mode can differ. |
| Animation | Public `Client.loadAnimation`, `Animation.getFrameLengths` / `getDuration`, and actor animation frame. Native client cycles are 20 ms. | An animation bar is explicitly labelled **Animation**. Its end is not claimed to be item completion or action availability. |
| Inventory batch | Exact modelled ingredient consumption, exact product creation and matching skill XP within the same confirmation window. | Existing banked-XP recipes with positive XP and verified products. The active training plan is used when compatible. Otherwise a unique matching transformation can identify the recipe automatically. |

The Fletching knife exception is included: three ticks for the first bow and two afterwards. Jeweller's chisel gem cutting is excluded from the standard two-tick rule. Karambwan cooking and wine are excluded from the ordinary cook-all-fish cycle. Unfinished potions, fishing rolls, mining rolls and woodcutting rolls are not treated as guaranteed completed items. Unknown animation definitions do not produce a made-up countdown.

Cleaning herbs is an instant manual operation, so the HUD does **not** assign a cooldown to each click. A native **Clean** item click can select the corresponding herb batch immediately. Completed herbs require the grimy-to-clean item transformation and matching Herblore XP. Without a click, a unique transformation plus XP can identify the batch after confirmation. No initial batch ETA is invented: the HUD shows `ETA —` until at least three completed actions and sufficient observed time establish a personal rate. Later ETAs are marked `~` and include observed pauses and confirmation time. Dropping unused herbs does not count them as completed or fill the batch bar.

## Boundaries and cost

The HUD observes the local player and public client events. It hides during banking, combat with an NPC and logout; profile changes clear all batch/animation state. Bank withdrawals and menu clicks alone never prove that a container or batch was filled. Inventory recognition runs on client events/ticks; animation definitions have a bounded 64-entry cache. Rendering reads immutable cached snapshots.

Coverage remains partial for activities without a verified action start/end and for recipes whose bonuses change exact consumption or XP. Animation progress is a useful fallback for supported animations, not proof of universal action-timer coverage. Harvest and gathering-node rings remain separate and preserve their existing lifetime/respawn evidence.

## Sources and validation

* OSRS Wiki, [High Level Alchemy, revision 15362543](https://oldschool.runescape.wiki/w/High_Level_Alchemy?oldid=15362543).
* OSRS Wiki, [Game tick/Action lengths, revision 15319478](https://oldschool.runescape.wiki/w/Game_tick/Action_lengths?oldid=15319478). Only the explicit noncombat action lengths above are used; no rate is inferred from an hourly-XP figure or a weapon attack speed. The page's unverified automatic herb-cleaning claim is not imported.
* RuneLite 1.13.2 public API `Animation`, `Actor`, `Client` and generated `AnimationID`; recipe source/output provenance is retained in `banked-xp-recipes.json`.

`ActionProgressTest` covers native client-cycle conversion, tool/first-action exceptions, manual cleaning, exact output/XP confirmation, ambiguous or unrelated XP rejection, automatic recognition in either event order, bank/combat/profile/logout handling, discarded materials and all three HUD display modes. The headless visual report is `build/reports/action-progress-modes.png`.
