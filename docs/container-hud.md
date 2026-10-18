# Container HUD

The movable black, white and gold **Container HUD** combines Fish Barrel and Plank Sack capacity displays. Native item icons accompany the container names, capacity bars and inventory/equipment/bank location. The local panel can hide either container, plank breakdowns or banked containers. Shared HUD appearance and position controls use `container-hud`.

## Plank Sack

All seven public RuneLite 1.13.2 `PLANK_SACK_*` varbits are read: plain, oak, teak, mahogany, camphor, ironwood and rosewood. Their combined capacity is 28. Negative, individually over-capacity or combined over-capacity readings become unknown. Counts remain container contents and are not added to the ownership ledger or counted again as bank/inventory supplies. Native bank visibility establishes a bank location only while that bank container is actually open.

## Fish Barrel

A barrel starts with an unknown amount. The pinned Hub implementation does not expose a verified total-count varbit, so Runewarden does not guess from the misleadingly named `FISH_BARREL_25/27` fields.

* A native **Check** item click, followed by a newly loaded native response interface and strictly parsed Check text, establishes the count. Existing dialog text alone does not establish a new reading.
* Native full and bank-empty messages can establish 28 or 0. Public/private player chat cannot change a count. A failed bank deposit invalidates it.
* While a carried/equipped barrel is open, recognised native catch/bonus-catch messages are reconciled with fish added to inventory. Counts advance only from a known starting point; inconsistent changes or concurrent cooking leave an unknown reading rather than inventing an amount.
* A Fill click alone supplies no count. A subsequent eligible-fish removal can update an already known amount. With an unknown starting amount, a partial fill remains unknown; eligible fish remaining after the confirmed removal can establish a full barrel.
* Authoritative Check/full/empty receipts reset pending catch deltas so the same fish cannot be counted twice. Losing the barrel, switching account or logging out clears the observation state.

A banked barrel displays **Unknown** because its hidden contents are not a newly observed carried count. Closing the bank removes that bank-only row. This is deliberately separate from bank-memory ownership snapshots.

## Cost and sources

Renderer work is bounded to cached immutable rows and native icon images. Only the seven relevant plank varbit events trigger refreshes; game ticks also cover aggregated varp updates. No synthetic clicks, item interactions, game menu replacements or external network requests are performed at runtime.

Reference implementations reviewed through the official Plugin Hub manifest:

* [Fish Barrel](https://github.com/molo-pl/runelite-plugins/tree/6aaa5993260cce2d2f46d74325071050f2382b2b/src/main/java/com/molopl/plugins/fishbarrel), pinned `6aaa5993260cce2d2f46d74325071050f2382b2b`.
* [Plank Sack Tracker](https://github.com/Talkarcabbage/planksacktracker/tree/e13a56ed6cbba3e54701d43924f12df16556fe2e), pinned `e13a56ed6cbba3e54701d43924f12df16556fe2e`.
* OSRS Wiki, [Fish barrel revision 15373691](https://oldschool.runescape.wiki/w/Fish_barrel?oldid=15373691) and [Plank sack revision 15215027](https://oldschool.runescape.wiki/w/Plank_sack?oldid=15215027), for capacity and bank behaviour.
* RuneLite 1.13.2 generated public `ItemID` and `VarbitID` identifiers.

`ContainerHudTest` covers all seven native plank types, shared capacity validation, strict Check parsing, new-response gating, Fill/Empty click limits, catch/inventory reconciliation, cooking ambiguity, duplicate authoritative receipts, bank visibility, unrelated varbit cost, profile/logout isolation and HUD layout. The headless report is `build/reports/container-hud.png`; runtime icons come from RuneLite's native `ItemManager`.
