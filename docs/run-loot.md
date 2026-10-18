# Loot this run

Shared activity pause, resume, finish and run changes are applied before each
loot event, including events arriving before the next game tick. Suspended
duration freezes at disconnect/logout for subsequent checkpoints, so closing
the client or switching characters while offline does not extend the saved run.

The Loot this run sidebar starts a fresh local run when a logged-in RuneScape profile is first observed. **New run** saves a nonempty prior run and resets loot and elapsed time. Pause stops recording until Resume; Clear loot clears the loot totals while retaining elapsed time and the entered expense total. Nothing is uploaded. Finished runs are stored in character-specific RuneLite configuration. World hops, reconnects, and logout hide the current run immediately; the same character can resume it, excluding time offline. Observing a different character clears the visible current run and loads that character's checkpoint. Stopping the plugin checkpoints the current run; a fresh client restores it paused. Buttons capture the visible run and profile, so a delayed action from an older character or run cannot clear or pause the new one.

NPC drops come from RuneLite 1.13.2's public `ServerNpcLoot` event, emitted by its independent `LootManager`. Player drops use `PlayerLootReceived`. Those records describe drops earned, including items left on the floor; this is not a pickup counter. The implementation does not infer loot from bank, inventory, or ground-item deltas.

Chest, clue, minigame, and pickpocket rewards emitted by an already-running native Loot Tracker are accepted via its public `LootReceived` event. Runewarden never enables that plugin and never changes its cloud settings. Mirrored NPC/player `LootReceived` events are excluded to avoid counting drops twice. Legacy `NpcLootReceived` ground-area heuristics are not used alongside the canonical server event. Sources that do not emit a supported loot event are not guessed.

Actual quantities are retained even when a price is unavailable. Notes are canonicalized through ItemManager and merged with their item. Prices use RuneLite's configured item-price service; coins are valued at one gp. Value is an estimate of drops earned, not realized sale proceeds or profit after supplies. Reward event counts affect the source count, never multiply item quantities a second time. Repeated delivery of the same event object during one game tick is suppressed, while distinct identical drops remain separate. No original native plugin code is copied.

Tests cover NPC/player mirror exclusion, repeated event identity, identical separate kills, noted quantities, unpriced drops, multi-reward amounts, pause/resume, stale run controls, character switches, bank/inventory exclusion, logout clearing, and overflow.

## Run analytics

The full-width stat card shows gross loot, costs, estimated net and hourly rates. Rates appear after one active minute to avoid presenting an extrapolation from the first few milliseconds. Compact actions sit below the card; **Costs** expands the **Supplies and fees** expense total. Set the total amount spent on food, potions, runes, ammunition, charges and entry fees. Setting it replaces the previous total. The plugin does not infer spending from inventory movements, so withdrawing or banking items cannot create fake expenses.

**Save run** archives the observed totals and pauses a fresh empty run. **New run** archives a nonempty current run before starting the next. Saved records retain estimated gross value, entered expenses, net value, active elapsed time, unpriced item count and source counts. History keeps the latest 50 records per RuneScape character, displays five at a time, and compares weighted historical net GP/hour with the current rate. Individual saved runs can be removed. Same-character world hops keep the in-progress run; character changes clear its observations and load that character's saved history. Unfinished current runs are checkpointed every 50 game ticks and on pause, suspend, stop or lifecycle clear. Restored checkpoints retain item quantities, estimated prices, sources, entered costs and measured active duration, and start paused after a client restart. Offline time is excluded.

History and current checkpoints use RuneLite's profile-specific configuration through RunewardenDataStore and the injected Gson. Rejected/future-version payloads are preserved for recovery rather than overwritten automatically. Every cost edit, finish/new-run action and history removal is guarded by the exact character and session shown on the button. Pause and offline time remain excluded from the hourly rates. Unknown item prices stay unpriced rather than being assigned an invented value. Net profit remains an estimate because the expense total is supplied by the player.

Live rows are paged to twelve item entries and eight sources. Up to 2,048 distinct item IDs retain individual rows; further distinct IDs enter an explicit **Additional loot** aggregate that preserves observed quantity, estimated value and unpriced-entry counts. Source keys are bounded to 256 plus an **Other sources** bucket. Saved summaries retain up to 64 named sources, while their full gross/cost totals remain intact. Normal game ticks reuse sorted immutable item/source data, and saved-history copies change only on history edits. The one-second UI timer skips hidden panels and retains unchanged rows; it updates only time/rate labels when the displayed data has not changed.


## Shared activity

Starting an explicit shared activity creates one run boundary for training and loot. A changed activity run ID archives nonempty prior totals, resets the current run and records the activity title in history. Shared pause/resume and finish state control recording. Browsing or preparing a different guide/method alone leaves the running totals intact. Current item rows remain RuneLite price estimates: a newly observed drop can refresh the displayed estimate for that item, while archived runs keep their saved totals. No spending is inferred from container changes.
