# Bank memory

The Bank memory sidebar keeps up to eight named bank observations for the current
RuneLite character profile. A capture requires an actually open native bank and a
bank observation from the current login session. An unknown or closed bank cannot
be saved as an empty bank; an actually observed empty bank can. Snapshots retain
exact item IDs, quantities, names, observation/capture times and the canonical GE
unit prices observed at capture time. Unpriced items remain in the snapshot.

The **Bank contents** view searches saved item names or exact IDs and pages eight
item rows at a time. Item details opens the shared item database. Snapshots can be
renamed and removed without changing other saved observations. The **Changes**
view compares the selected before/after observations, showing added, removed and
changed item types, exact quantity changes and optional price-only rows.

Two value effects are kept separate:

- **Quantity change** estimates added/removed quantities using the after price for
  items still present, or the before price for removed items.
- **Stored GE change** compares the two historical total values, including price
  changes between captures. This is bank movement, not a profit measurement.

**Compare with open bank** takes an explicit temporary comparison observation. It
does not consume a named history slot. The temporary side is cleared when the bank
closes; a saved historical observation remains available while logged into its
character profile. Unknown prices are counted and excluded from estimated totals.
Arithmetic saturates instead of wrapping into negative values, with an upper-bound
marker on a saturated snapshot value.

## Local export and storage

An explicit export opens the existing selectable text dialog. Snapshot JSON, item
CSV/TSV and comparison CSV include item facts and quantities, without a character
profile identifier. Spreadsheet-like item names are quoted and neutralised as text.
There is no network request, automatic clipboard access or filesystem write.

History uses `RunewardenDataStore` and the owned `runewardenbankmemory` group,
`snapshotsV1` key, schema 1, addressed by the active RuneLite character profile.
Limits are eight snapshots, 2,500 item IDs per observation and 1,200,000 JSON
characters per saved record. Reaching a limit refuses the new save instead of
silently evicting history. Future schema data is preserved by the shared recovery
guard. These are ordinary bank-container snapshots: carried items, equipment,
pouches, potion storage and other secondary containers are not silently merged.

All native bank, definition and price reads occur on the client thread, on explicit
capture/comparison. Unchanged ticks check availability but do not reread prices,
definitions or recalculate historical differences. Item-definition names/canonical
IDs use an access-order cache limited to 2,500 entries. Logout/profile changes/stop
clear in-memory character state and queued actions reject the previous profile or
lifecycle epoch. Sidebar rendering and exports use saved immutable observations.

## Scope and primary precedent

The current Hub Bank Memory reference is pinned to
<https://github.com/Lazyfaith/runelite-bank-memory-plugin/tree/036fc3b672aeae93d73ec4bbcd713587d86eea32>.
Its published features include searchable bank saves, named snapshots, comparison
and CSV/TSV export. This independently written module implements those bounded
manual-history operations through the existing ownership ledger and local data
store. No source implementation was copied and no foreign plugin configuration is
read or changed.

This is not complete parity with that plugin. Automatic current-bank saves,
offline browsing across other character profiles, separate regular/League/
tournament/Deadman histories, High Alchemy valuation and inventory bank-count
tooltips are not implemented here. Existing shared ownership observations remain
separate from named bank history. There is no bank layout restoration, item moving,
withdrawal, import or game action.

## Root integration and verification

Type: `BankMemoryTool`; no overlay or additional common configuration is required.

Injection: `(Client, ClientThread, ConfigManager, Gson, ItemManager,
OwnershipLedger, RunewardenDataStore)`.

- Lifecycle: `start()`, `stop()`, `clear()`, `onGameTick()` after the ownership ledger.
- Sidebar: `createPanel()`; database links: `setOpenItemAction(IntConsumer)`.
- History: `capture(String)`, `rename(id, title)`, `delete(id)`.
- Comparison: `selectBefore(id)`, `selectAfter(id)`, `compareCurrent()`.
- Export: `exportSnapshotJson(id)`, `exportSnapshotCsv(id)`,
  `exportSnapshotTsv(id)`, `exportDiffCsv()`.

`BankMemoryTest` covers quantity/repricing separation, zero/unknown values,
saturation, malformed records, genuinely empty versus unavailable banks,
persistence/restart, profile/queued-action isolation, history deletion/renaming,
temporary comparison invalidation, bounded history/future-schema preservation,
exports, snapshot contents/search, price-only visibility, unchanged-tick work and
the actual 206-pixel sidebar with paging and painting. `OwnershipLedgerTest`
remains the native-container observation regression suite.
