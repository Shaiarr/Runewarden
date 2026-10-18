# Clues, STASH and travel

Runewarden follows RuneLite 1.13.2’s active public clue model. It does not ship another puzzle solver or replace the native clue markers. RuneLite’s **Clue Scroll** plugin must be enabled; its puzzle and scene overlays remain responsible for puzzle/target presentation. `ClueDataService` adapts the public `ClueScrollService` contract through the enabled native plugin’s public `getClue()` method, avoiding a hard dependency, private fields or child-injector manipulation.

## Current clue

- Native emote sequence, location, accepted equipment alternatives, equipment-slot restrictions, spades and 35 supported light-source alternatives. An observed fire-pit unlock removes its light requirement.
- Inventory, worn equipment and the shared last-observed bank are kept separate. Equipment in the bag is labelled **Equip before the emote**. Falo/other inventory-only requirements distinguish an item currently worn. Saved bank contents are labelled as snapshots.
- Native cryptic/anagram/cipher answers, map/coordinate destinations, remaining three-part clues, skill challenges and hot/cold candidates follow changes in the native model. A missing/dropped/unread clue clears the old presentation; it does not invent a completed step.
- Native clue DB requirement metadata is indexed 24 rows per game tick, bounded to 2000 rows. Exact matching text can supply skill, item and quest prerequisites. Base and boosted skill states remain distinct; quest completion is authoritative, while a partial quest-stage threshold that cannot be established stays **unknown**. Types without a matched native row are labelled unindexed.
- Item buttons use native item images and open Runewarden’s existing item database. Available quest prerequisites open the existing local quest guide.

## STASH

119 source chart aliases and accepted item examples are bundled from Dude, Where’s My Stuff?’s BSD-licensed data, with its stated Wiki revision 15102963. Native unit IDs/coordinates come from RuneLite’s `STASHUnit` enum. Item examples are accepted reference variants, **not a claim about which variants are currently stored**.

The native read-only `WATSON_STASH_UNIT_CHECK` script establishes whether the selected/current unit is built. Its three zero state arguments cannot establish filled contents: Runewarden intentionally does not use that return value for fill state. Filled/empty observations come from the visible native STASH chart’s check marks and are saved per character with an observation date. Unseen contents remain unknown. The two Warriors’ Guild chart rows are distinguished by tier. The browser supports location search, observed-state filters, paging, item-source buttons, construction reference and explicit routing.

Construction costs/boosted requirements are linked to the primary reference rather than inferred from location names. They are not yet an in-client material planner for every STASH tier.

## Travel

Public Shortest Path protocol checked against the Plugin Hub’s pinned commit `37abff3a3345185fc11fccecae04d8170de25a11`:

- `shortestpath/query` is a read-only route preview. A matching result supplies reached status, route cost, transport descriptions, bank-detour and required-item status.
- Exact single-item alternatives are encoded using `{point, requirements:[{itemId:quantity,...}]}`. A composite equipment set is retained in the preparation list and is not incorrectly flattened into an OR group.
- **Show route** is explicit. No movement, teleport, banking or item withdrawal is executed.
- Cleanup uses `getTarget`/`currentTarget` to confirm the displayed destination still matches the owned route. Another plugin’s path message, or a manual path/destination menu action, relinquishes ownership. Missing/older protocol support times out to a direct-route option and never clears a route without confirmation.
- The travel panel searches bundled guides’ actual step coordinates, accepts validated custom coordinates and offers the last bank the player was observed using. This is not advertised as the nearest bank or guaranteed-accessible bank.
- Activity-world matching reads the client’s observed native world list: advertised activity, population, location and world types. The player may copy a world number or explicitly open the native switcher. Runewarden never hops worlds automatically and does not guess activity worlds when the list is unavailable.

Client observations and protocol actions stay on the game thread. Swing receives snapshots. Profile/epoch guards discard stale UI work, old query responses and old chart observations. There are no per-hover requests, private plugin-config writes or additional runtime HTTP clients.

Focused tests cover the public protocol, manual replacement, profile changes, native emote equipment states, non-flattened sets, native cache requirements, observed chart states, item-ID namespace regression, activity-world matching and 206px panels. Live game validation remains necessary for client/cache changes and native widget lifecycle behavior.

## Personal travel plans in 0.7.0

The travel module now has route, saved-destination, bank-stop and activity-world
views. Up to 60 named destinations are saved per character with their exact
point, optional linked-guide access rules and arrival item alternatives. Players
can create, edit, preview and remove them. The supply editor accepts item names,
quantities and explicit alternatives; those remain independent required groups
in Shortest Path's public target protocol. Guide searches now send exact sourced
item quantities instead of dropping the pack list from the request.

Typed source skill, quest and combat-level requirements are checked against the
provided current-character facts. Alternative quest/skill routes remain OR
choices. Known unmet requirements stop a guide request from being called
reachable or displayed as a complete route. Missing quest observations, partial
access, unsupported prose and boostable levels without a verified boost remain
checks. A complete geometric result with unknown source access is explicitly
shown as requiring an access check. Existing source references remain available
through the linked preparation guide.

Arrival preparation reports currently observed carried groups separately from
the last bank observation. An unobserved bank is unknown, and a saved bank
snapshot retains its age and previous-session status. It is not substituted for
current carried items. The public receiver's native `CARRIED`, `BANK` and `UNKNOWN`
arrival results are presented in ordinary language. Item-source and bank-focus
links use the shared Runewarden modules.

Opening a native bank outside an instance records the player's actual bank-stop
point and observation time. Nearby points are deduplicated, and the 12 most
recent bank locations survive restarts. These are observed bank locations,
not a fabricated nearest-bank or universal-access catalog. Players may forget a
recorded bank or add up to ten explicit bank stops to a route. Each leg is
previewed and drawn only on request; completing a bank stop and continuing to the
next leg or original destination are manual controls. No opening, withdrawing,
walking or teleporting is performed.

Personal destinations and bank observations use guarded
`runewarden.travelPlansV1` saves and existing backup/recovery. Future or corrupt
records block overwrites. Character changes hide old entries immediately and
queued old-character actions are discarded.

The pinned public protocol exposes a temporary configuration override on `path`
messages, but its `query` handler does not accept a per-query risk override.
Runewarden therefore keeps both preview and display under the user's existing
Shortest Path configuration. It does not advertise an invented safe-route mode
or write that plugin's preferences. The scope of wilderness avoidance and allowed
transports remains the player's Shortest Path policy.

The additional tests cover personal CRUD and exact alternatives, save limits,
future-format preservation, profile isolation, genuine bank observations,
source-gate blocking, unknown access after geometric success, stale bank
ownership, item parsing, manual protocol behavior and the four 206px travel
views. Native game validation remains outstanding.
