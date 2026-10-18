# Rarity data and reward event audit

`RarityCatalog` is an offline lookup with two distinct metrics:

- `ownershipPercent` is the published percentage of a historical WikiSync collection-log sample that obtained a slot. Its text labels that population and snapshot date. It is not the percentage of all OSRS players and never becomes a drop probability.
- `denominator` is present only for a single matched, supported source probability. It is a base Wiki reference for that source, not a live claim to read every loot modifier. `exactOdds` remains false for conditional reward tables, source ranges, unobserved activity conditions, skilling level/action, task-dependent weapon drops, rare-drop-table luck, cyclic components and unsupported source matches.

The ownership copy has 1,717 named slots, 1,701 with percentages, from the pinned CollectionLogPopupEnhanced source. Its data file last changed on **2026-09-26**; the identical file on the publisher's October15 data branch does not make it a fresh ownership sample. Four unnamed records were omitted. The data remain a labelled snapshot rather than being polled.

The source-rate copy comes from `osrs-collection-log-data` commit `0452ad13e77b29618ab1b6d22e3a728c5e396510`, published **2026-10-15**. Its generator queries the Wiki dropsline bucket and combines fixed repeated rolls as `1-(1-p)^rolls`. Runewarden keeps 4,000 item/source rows and guards context that the flattened table cannot resolve. Conditional raid odds may be displayed as **Purple-table chance**; they never become odds per completed raid. Ownership may independently colour a collection unlock.

`forItem(id)` supplies ownership and only an unambiguous sole-source rate. `classify(id, source)` requires a matching source; it cannot borrow a rate from another NPC. `itemId(name)` uses local exact normalized collection names and conservative charge-state aliases; ambiguous names remain unresolved. `itemName(id)` is only a local fallback when a native item definition is unavailable. Tier thresholds belong to `RewardSettings`, not the data catalogue.

## Native event hooks verified against RuneLite 1.13.2

The official pinned `ScreenshotPlugin` confirms these independent event paths:

1. `ChatMessage`, server `GAMEMESSAGE` or `SPAM`, with `New item added to your collection log: ` followed by the item name.
2. `ScriptPreFired`: `ScriptID.NOTIFICATION_START` (3346) starts a native notification. At `NOTIFICATION_DELAY` (3347), `VarClientID.NOTIFICATION_TITLE` (387) identifies `Collection log`; `NOTIFICATION_MAIN` (388) contains `New item:` and its name. This also supports native-popup-only settings. Deduplicate it against chat.
3. Progress comes from public `VarPlayerID.COLLECTION_COUNT` (2943) and `COLLECTION_COUNT_MAX` (2944), after login and with `0 <= count <= maximum`, `maximum > 0`. Reset account-local observations on profile changes. This is the player's own progress, independently of the population ownership sample.

The upstream Popup Enhanced implementation itself also listens to the server chat path. Its presentation and rarity preferences are not treated as authoritative combat/drop mechanics. Runewarden need not enable, query, modify or depend on that plugin, and does not need to change the native game notification settings.

Use the existing canonical server loot events for rare-drop correlation. Inventory changes are not evidence of a drop or a collection unlock. Public/player/clan chat is not a substitute for the player's own server messages. Do not substitute the number of bundled item rows for the game's current collection-log maximum.

Primary sources, retrieved 2026-10-16:

- [Official RuneLite ScreenshotPlugin](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/screenshot/ScreenshotPlugin.java), also inspected in the pinned source JAR.
- [CollectionLogPopupEnhanced pinned source](https://github.com/TimHeessels/collection-log-popup-enhanced/tree/99ce2f01b1a0c56af75f39e2afbc8f226cf8a94e).
- [Publisher's data repository and generation description](https://github.com/TimHeessels/osrs-collection-log-data).
- [WikiSync collection log table population instructions](https://oldschool.runescape.wiki/w/Collection_log/Table?oldid=15341154).

Full provenance and the retained upstream BSD notice are bundled under `licenses/rarity-*`.
