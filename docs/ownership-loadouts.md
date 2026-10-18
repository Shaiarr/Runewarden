# Ownership and loadout readiness

Runewarden keeps a character-isolated ledger of **observed** storage, with timestamps and a distinction between a saved observation and a current-session observation. It does not present the sum of differently dated snapshots as a guaranteed current account balance.

## Supported native observations

| Source | Observation trigger | Persistence |
|---|---|---|
| Inventory / equipment | Logged-in game tick and native item-container changes | Last observed slots and quantities |
| Bank | Bank items widget visible, including item-container changes | Last observed full bank; unseen bank remains unknown |
| Seed vault / seed box / tackle box / looting bag | Native item-container change with a non-null actual container | Last observed contents |
| Quiver | Worn cape definition has native `QUIVER_AMMO_AVAILABLE` and native temporary-ammunition ID/count varps are valid; native container observations also accepted | Last observed contents; `quiverAvailable` distinguishes currently usable contents |
| Gravestone / Death's Office | Recovery module publishes native container while the corresponding recovery interface is visible | Last observed contents, never inferred from the death inventory |
| Rune pouch | Native six-slot type/quantity varbits, decoded through `EnumID.RUNEPOUCH_RUNE`, only while a recognised pouch is carried | Current-session facts only |
| Spellbook | Native `VarbitID.SPELLBOOK`, validated 0–3 | Current-session fact only |
| Blast Furnace coffer | Native coffer varbit while its HUD is visible | Last observed balance |
| Kingdom coffer | Native Miscellania coffer varbit in Kingdom regions after the native quest variable is positive | Last observed balance |

Pinned API definitions are RuneLite 1.13.2. Rune-pouch decoding follows the public `RunepouchOverlay` API facts; Kingdom and Blast Furnace observation gates follow public native plugin sources. No other plugin's settings, fields, storage, or bank tags are read or modified. Containers for which no reliable observation has arrived remain unknown.

## Exact saved loadouts

The Loadouts module captures worn equipment slots, inventory quantities, observed pouch runes and observed quiver ammunition. Capture records the current spellbook. Players can edit each requirement's quantity, exact IDs/explicit alternatives, location and slot, remove requirements, change the spellbook, replace or delete presets, and export/import a bounded JSON bundle.

Readiness counts **current-session carried items in the required location**. A sword in the inventory does not satisfy a weapon-slot requirement. Banked items are displayed separately as last seen and never counted as already packed. Repeated quantity requirements consume the available quantities once; alternatives are explicit. Equipment candidates with alternatives are omitted from exact DPS candidate maps instead of being guessed.

The summary separates packed items from configured departure checks. With no extra checks, its positive heading remains **Items ready**. When checks are configured, **Checklist ready** requires every item, spellbook and mandatory check to pass. Missing facts display **Unknown**, and unknown checks prevent the ready result. This confirms the explicit checklist rather than every possible encounter requirement.

Players can add up to 24 labelled departure checks per preset:

| Check | Ready evidence | Unknown evidence |
|---|---|---|
| Minimum charges/doses | Explicit native encoded item-ID variants pool their currently carried/worn charges and meet the selected minimum | Unsupported hidden charge state, missing native state catalogue, or unobserved inventory/equipment |
| Active effect | Shared native effect observation says the selected stable effect key is active | Effects module disabled, current character not observed, or unsupported effect |
| Manual check | Player explicitly confirms this named check in the current login session | Not confirmed, restarted, profile changed or account/session cleared |

Native encoded charge counts are reusable from current ledger quantities even when the optional supply HUD is hidden. A supported item absent from observed inventory and equipment has zero available charges; bank stock never counts as carried. Arbitrary weapon/tome charges are not inferred from item presence: configure a manual departure check when the native source cannot verify them. Effect checks distinguish observed inactive (missing) from unavailable observations (unknown). Manual confirmations are never saved or exported.

Capture **Update**, item edits, spellbook edits, export/import and persisted presets preserve the check definitions. Editing a preset clears its manual confirmations. Invalid or duplicate check IDs and malformed charge/effect definitions are rejected. Existing version-1 plans load with an empty checklist; new saves/exports use version 2 under the same setting key so older clients preserve unsupported data instead of overwriting these checks.

“Focus supplies in bank” hands the missing requirement IDs to the shared temporary bank filter. It preserves the active guide and restores the preceding ordinary search. Selecting another guide or closing the bank clears the temporary loadout target; a manually typed search immediately takes ownership.

## Bounds and recovery

- At most 30 presets, 100 item requirements and 24 departure checks per preset, 32 explicit alternatives per item/charge-pool requirement.
- Positive integer quantities at most `Integer.MAX_VALUE`; bounded item IDs and native slots.
- Import limit 220 KB, duplicate IDs/slots and unsupported versions rejected before mutation.
- Native storage snapshots at most 2,500 slots each and 600 KB serialised.
- Both profile stores use `RunewardenDataStore` for schema checks, last-good recovery and protection against silently replacing future or malformed data.
- Game-thread mutations carry character and generation guards. Queued old-character UI work is discarded.

## Shared API

`OwnershipLedger.snapshot()` returns an immutable `View`. Its `storages` map contains immutable `Observation` values (`quantities`, `slots`, `observedAt`, `currentSession`). Missing entries mean unknown. `query(itemId)` returns exact-ID observed totals, counts by source, and the set of still-unknown sources. The inventory/worn/pouch/spellbook records are directly reusable for preparation, progression, clue and travel tools.

`LoadoutReadinessTool.presets()` and `selected()` expose immutable `LoadoutPlan` models. `LoadoutPlan.equipment()` includes only exact singleton-ID equipment-slot requirements. DPS comparisons use this exact equipment interface and label observed ownership separately from theory gear.

`LoadoutReadinessTool.readiness()` returns the selected current-profile readiness result or null. It is safe for command-centre rendering and reads immutable supplier/ledger snapshots. `LoadoutReadiness` exposes `itemsReady`, `ready` and `checks` with `READY`, `MISSING` and `UNKNOWN` statuses. `setChargeFacts(totals, supported)` and `setActiveEffects(active, known)` accept client-thread snapshot suppliers. Those facts are copied on the client tick; Swing rendering never polls native client state.
