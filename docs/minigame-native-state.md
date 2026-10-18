# Minigame activity observations

**0.7.3:** native objectives, supported highlights and optional guide/lesson following are enabled across all twenty contexts. The previous eight-activity hold was removed after comparison with current Hub/core helpers; see [release-review-0.7.3.md](release-review-0.7.3.md).

The Minigame Coach can activate from twenty verified native activity contexts. This is distinct from the larger guide catalogue. A visible dedicated game interface, an actual instance, or nearby unique indoor task objects establish context; loading a neighbouring map region does not enable the ten primary readers.

| Activity | Native observations | Current reader detail |
| --- | --- | --- |
| Wintertodt | Visible round HUD; native warmth, energy and point text; observed braziers; carried roots/kindling | Repair a broken brazier, light an unlit brazier, feed kindling or gather/fletch roots. |
| Guardians of the Rift | Visible round HUD; current elemental/catalytic energy; native portal text; carried fragments, essence and stones | Mine ordinary remains, use the workbench, enter a currently active altar or empower the Great Guardian. Large-remains targets require real level 56 Agility. Huge remains are highlighted only while the player is on the verified portal shelf. |
| Tempoross | Visible encounter HUD; native energy, essence, storm and point text; native tether state; raw/cooked/crystallised fish inventory | Remain tethered while secured, fish the spirit pool at observed zero energy, load cooked fish, cook raw fish or catch fish. No future wave or storm timing is inferred. |
| Giants' Foundry | Native stage/heat/progress, commission attributes, all 15 purchased-mould unlocks, Smithing level, crucible counts, worn uniform pieces, bonus-widget state and gauge geometry | Optimal owned/usable Forte–Blade–Tip combination, alloy score, exact section boundaries, remaining action ranges, safe heat actions and native bonus reclick. Initial heating/cooling tick estimates are labelled estimates. |
| Mastering Mixology | Visible lab HUD; native order types/modifiers and paste quantities; current potion inventory | Mix the displayed recipe, process it at its requested station and deliver the finished potion. Fixed references use verified template coordinates. This does not predict quick actions. |
| Mahogany Homes | Anchored native assignment/reminder chat and dialogue, customer bounds, actual actionable furniture and observed tier | Per-account confirmed customer/address, tier guide, full-contract material ranges, explicit route handoff and furniture steps. Unknown assignments still require actual actionable furniture; furniture varps never identify a customer. Other minigames take precedence while a contract is stored. |
| Pest Control | Visible active-game HUD; native time, Void Knight health, damage and activity | Protect the knight and follow native portal shield status. No future portal rotation is predicted. |
| Blast Furnace | Visible furnace HUD or nearby unique conveyor/dispenser objects; native stored coal/bar counts and hot-bar state | Load ore, cool bars if required and collect bars. The optional guide follows a single verified carried ore/bar or stored-bar method. Empty bank trips retain the last unambiguous method until exit or scene/account reset. |
| Tithe Farm | An actual instance plus nearby live plot objects; observed dry/grown/dead plots; native round score | Water dry plants, harvest mature plants, clear dead plants or plant/water empty plots. Counts describe observed nearby objects; no unobserved growth timer is invented. |
| Barbarian Assault | An actual instance and visible role HUD; native role, wave, heard call and call to send | Send the native call and follow the current role. No future NPC movement or combat solve is supplied. |
| Nightmare Zone | Verified dream region/plane; native dream points, banked points and absorption | Manage supplies. An absorption guide is selected only when absorption is observed. |
| Pyramid Plunder | Verified active room and native positive timer; current room, requirement and trap state | Pass the spear trap and choose the planned looting room. |
| Mage Training Arena | Verified native training-room context; native points/bonus/alchemy table; actual Telekinetic walls, finish, stationary guardian and collision map | Shortest-pull Telekinetic solver, pull sequence, nearest valid standing-edge marker and casting-side cue. Moving/ambiguous/incomplete scenes do not publish speculative next pulls. Alchemy names a 30-coin item only from a complete unambiguous table. |
| Volcanic Mine | Visible dedicated HUD; timer, stability, players, vent and chamber readings; player-selected role | Team overview, Vent A/B/C or boulder-miner objective; unknown readings are scoped to the selected role. The exit reminder takes priority. The plugin does not guess team assignments or pressure changes. |
| Blast Mine | Visible dedicated native HUD; native stored ore counts | Excavate, load and light dynamite, move clear and deposit blasted ore. |
| Fishing Trawler | Visible dedicated native HUD; native timer, water, activity, net and catch text | Contribute on the current deck using the activity's repair/bailing mechanics. |
| Drift net fishing | An actual instance whose current template chunk resolves to the core-verified underwater region; both native net states/catches and carried replacement nets | Empty a full net, set a known empty frame when a replacement is carried, restock or herd fish. Unknown net states are not treated as set or full. |
| Trouble Brewing | Visible dedicated game HUD; actual timer/ingredient/score text and carried logs/knife/ingredients | Gather scrapey logs, acquire a knife, cut logs into bark or deposit actual carried ingredients into the crew's correct hopper. Highlights do not guess which team's hoppers to use. |
| Soul Wars | Visible dedicated active-game HUD; avatar health/strength, native activity and carried fragments/bandages | Restock, contribute to capture or take carried fragments to the obelisk. Offering fragments is explicitly conditional on team control; no undocumented ownership mapping or enemy solve is inferred. |
| Castle Wars | Exactly one active team HUD, its displayed timer/scores/flag text and an actually carried/worn standard | Collect bandages, return an enemy standard to the team's stand or protect a friendly carried standard. Ambiguous simultaneous team HUDs do not choose a team or flag. No enemy path or action prediction is supplied. |

`MinigameActivityCatalog.detect(Client)` runs on the client thread once per game tick. Ordinary activity readers use the bounded object cache. Only the active Telekinetic room examines at most a 41×41 tile window for its current walls and finish; its collision BFS is cached until the guardian, walls, finish or scene changes. The controller supplies a one-time scene seed and subsequent object spawn/despawn events. The primary catalogue keeps at most 512 relevant object observations; the cached ID union includes detection-only evidence objects as well as render targets. Scene/account reset releases those observations and the remembered Blast Furnace method. Each immutable snapshot supplies one current step, available facts, suggested object IDs and optional fixed reference tiles.

Most reference tiles are template-world `WorldPoint` coordinates. Telekinetic references are the observed non-instanced MTA maze coordinates. Scene rendering must resolve their local instanced copies. Suggested object IDs are not a claim that every matching object is reachable: the client only draws actual cached scene objects, and task-specific gates constrain targets where verified. The coach does not click, move, operate objects or send horn calls.

The ten primary readers were checked against pinned RuneLite 1.13.2 API/core sources and the following primary plugin sources:

- [RuneLite core](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins): Wintertodt, Blast Furnace, Pest Control, Tithe Farm and Barbarian Assault.
- [Guardians of the Rift Helper](https://github.com/DatBear/Guardians-of-the-Rift-Helper/tree/cdee9d0d5cf9aa7782bfe8f5c36c20a2b127d11c): native HUD, energy and huge-remains shelf bounds.
- [Easy Giants Foundry](https://github.com/Toofifty/easy-giantsfoundry/tree/9324c7f55cba4c0fc5245a7bc85f3db521a8cceb): current stage is the native progress fraction multiplied by the number of native stage sprites; heat bands derive from native gauge widths.
- [Mastering Mixology](https://github.com/hex-agon/mastering-mixology/tree/10f4ceda7fb044f006d0aecc449afcf2339acfa0): order/modifier indices, potion items, recipes and station template coordinates.
- [Mahogany Homes](https://github.com/TheStonedTurtle/Mahogany-Homes/tree/7ec419eed23dbd8f3970c40bbe0b03d5b951a0dd): house bounds, hotspot associations and furniture states 5–8 identifying the built contract tier.
- [Tempoross](https://github.com/nmlynch94/runelite-external-plugins/tree/96ddee1178127c982fd94d59fbc6fdc56ab6a308): public encounter fields and native tether state.

Upstream notices are bundled in `com/runewarden/licenses/minigame-coach-BSD-2-Clause.txt`; the six additional contexts have separate source notes in `minigame-extra-runelite-attribution.txt`.

The four newer contexts use the pinned public `gameval/InterfaceID`, `ItemID`,
`ObjectID` and `VarbitID` definitions. Drift net region and status mapping are
verified against the pinned core `driftnet/DriftNetPlugin.java` and
`DriftNetStatus.java`; their notice is retained in
`minigame-field-runelite-attribution.txt`. Instruction references are the existing
bundled Wiki-sourced guides, plus the primary Fancy Dan transcript and Cooking
experience-table descriptions of cutting and depositing scrapey bark. Fresh
fetches of several Wiki activity pages were blocked; this does not establish a
fresh complete audit of those games. The new HUDs therefore retain the specific
scope in this table rather than claiming complete minigame solves.

Minigame and ground-loot preferences now use the shared guarded data store.
Unsupported versions and malformed settings retain their original payload;
ordinary display controls cannot silently overwrite them. The shared recovery
screen owns explicit restore/reset actions.

Focused tests cover dedicated-interface visibility, outside/lobby exclusion, inventory transitions, brazier ordering, Agility/portal gates, Foundry native heat bands, Mixology order/item mappings, contract-tier ambiguity, Blast Furnace method retention/exit, instance-only Tithe activation, native role calls, bounded scene retention and immutable snapshots. These tests use deterministic native observations; they are not a claim of live-session testing on every activity.

## Version 0.7 depth pass

All twenty activity contexts now have an ordered practice plan, with 122 milestones in total. Current native objectives were deepened for Wintertodt, GOTR, Tempoross, Pest Control, Barbarian Assault, Volcanic Mine, Blast Mine, Tithe Farm, Pyramid Plunder and Fishing Trawler. Native NPC references and numbered contextual targets join the existing object/template references. The detailed matrix and limitations are in [minigame-learning-plans.md](minigame-learning-plans.md). Native phase following can position the plan across all twenty supported contexts and never marks unseen milestones complete.

## Specialist depth added for the 1.0 candidate

See [minigame-specialists.md](minigame-specialists.md) for equations, profile/lifecycle behavior, source provenance, tests and precise remaining limits. Native bonus reclick text is a current skilling bonus indicator, not combat timing. Full upstream notices ship both as docs and in the plugin JAR at `com/runewarden/licenses/minigame-specialists-BSD.txt`.
