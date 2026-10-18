# Minigame specialist helpers for the 1.0 candidate

The existing twenty native activity contexts remain enabled. This pass adds executable specialist logic to the three concrete parity gaps identified in the release audit, plus player-selected activity methods. These are read-only helpers; no action is performed for the player.

## Giants' Foundry

`MinigameFoundry` contains 33 mould score records. Each of the three parts is independently optimised for both native commission attributes. Purchased parts require their exact native unlock bit to be `1` and the real Smithing level to meet the mould's use requirement. The six default parts in each group are available from level 15. Mould guidance never assumes every shop reward was purchased. The sidebar lists the best Forte, Blade and Tip and total mould score; the HUD prioritises these during mould selection.

The six crucible quantities are displayed individually. A complete 28-metal batch receives an alloy score from the two largest metal contributions; a pure metal receives its pure-metal score without a spurious alloy bonus. Incomplete or invalid batches have no final alloy score.

Tool sections come from the native sprite sequence. The current section is `floor(progress × sections / 1000)`; a completed sword has no next tool. Remaining actions use the exact fractional boundary. No uniform gives a fixed count; one to three pieces gives a best/worst action range because the extra progress is probabilistic; all four pieces give deterministic +1 action progress. Heat capacity counts only actions whose starting heat is in the tool's actual native band. Bonus reclick text is displayed only when the native bonus widget is currently active.

Heating/cooling estimates use the upstream measured standard/dunk heat sequence, and stop before a method would skip over the entire native band. These are **initial adjustment estimates**, not a travel-aware full-sword solver or a guarantee of enough heat to finish every remaining action. After travel or a stage transition the next native snapshot recalculates them. Current tool/action/heat facts are prioritised over setup details in the small HUD; the sidebar retains all facts.

Sources:

- [Easy Giants' Foundry](https://github.com/Toofifty/easy-giantsfoundry): mould scores, tool progress/heat constants, uniform items, native bonus widget and measured heat sequence. Existing stage-source pin: `9324c7f55cba4c0fc5245a7bc85f3db521a8cceb`.
- [RuneLite 1.13.2 native varbits](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java): mould ownership 13916–13930; commission, crucible and progress state.
- [OSRS Wiki Foundry strategies](https://oldschool.runescape.wiki/w/Giants%27_Foundry/Strategies): mould use levels and independently checked default/purchased commission totals.

## Telekinetic Theatre

`MinigameTelekinetic` examines actual loaded maze walls, the finish ground object, the guardian's confirmed tile and the public collision map. Each graph edge is a full cardinal pull until collision stops the guardian, rather than a walking step. Bounded BFS returns a shortest-pull route. The published HUD contains pulls remaining and the sequence; one numbered reference marks the nearest valid tile along the next casting side.

The helper pauses while the native guardian is moving. A missing collision map, multiple possible guardians, multiple finishes, incomplete bounds, an unreachable finish or an oversized maze does not manufacture a route. The complete native guardian form produces the reward/next-maze step. Solver work is cached until the confirmed guardian location, finish, wall topology, scene or plane changes. Only while this room is active, at most a 41×41 tile window is examined; the graph is bounded to 33×33 points. Ordinary minigames retain their event-fed scene cache.

This solves the currently loaded puzzle and indicates the casting side. It does not cast spells, click the guardian, move the player or simulate combat content. The standing-edge convention and collision semantics follow the existing [RuneLite core TelekineticRoom](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/mta/telekinetic/TelekineticRoom.java).

## Mahogany Homes

`MinigameHomes` captures a customer only from anchored native assignment/reminder messages or the NPC's full assignment dialogue. Public chat is ignored. Shared furniture states do not establish a customer. All twelve customer addresses and full-contract material ranges are included. Actual repair/remove/build actions still determine local furniture highlights.

Confirmed customer and tier persist through the guarded Runewarden store under the exact RS profile. Profile changes clear the previous in-memory state before restoring the new account. Completion clears the saved assignment. A new requested tier alone does not invent a customer. If the plugin is enabled mid-contract without prior saved evidence, opening the native contract reminder recovers it.

`Route to customer` hands the confirmed address to Runewarden Travel/Shortest Path only after a deliberate click. Its callback checks current profile and activity. Stored contracts do not override another recognised minigame. Within a completed house the helper prompts the player to speak to their customer, including upstairs where applicable.

Material values are the **full contract's native customer/tier range**, not an assertion of the exact remaining planks after arbitrary earlier repairs. The selected tier connects to the existing guide and its bank focus. Exact per-hotspot remaining-material accounting and unlocked-teleport price optimisation remain outside this implementation.

Customer data and native message formats are based on [Mahogany Homes](https://github.com/TheStonedTurtle/Mahogany-Homes/tree/7ec419eed23dbd8f3970c40bbe0b03d5b951a0dd).

## Method controls and Volcanic Mine

Players can choose whether to fletch Wintertodt roots, whether to cook Tempoross fish, and a fish batch of 1–25. Warmth, native tether and the observed spirit phase keep priority over batch choices. Missing inventory capacity does not assume the player can continue filling a batch.

Volcanic Mine offers team overview, Vent A/B/C and boulder-miner roles. The chosen vent's actual pressure/chamber readings inform the objective; an unrelated unknown vent no longer blocks a selected role. The exit reminder remains first priority. Role assignment remains a player/team decision, and the helper does not invent pressure changes or team paths.

## Validation and remaining release evidence

Focused tests cover default versus purchased mould optima, unlock and level bounds, alloy/pure-metal scores, fractional section boundaries, partial/full uniform ranges, exact heat limits and overshoot; sliding-maze shortest paths and unreachable/cross-plane/oversized cases; actual native collision-room integration and moving/ambiguous guardians; assignment parsing, full-contract ranges, persistence/callback profile isolation; method/phase precedence and selected-role ambiguity. Existing twenty-context, HUD/sidebar layout, activity handoff and lifecycle tests are retained.

These deterministic tests are not evidence of Windows or in-game testing. Live commissioning, room entry/reload, dialogue wording, scaling and plugin coexistence still belong in the release acceptance run. Broader activity coverage is retained; this pass does not claim every specialist feature of every other minigame plugin.

Full upstream licenses are in `docs/licenses/` and also bundled inside the plugin JAR at `com/runewarden/licenses/minigame-specialists-BSD.txt`.
