# Additional minigame contexts

**0.7.3:** native objectives, supported highlights and optional guide/lesson following are enabled across all twenty contexts. The previous eight-activity hold was removed after comparison with current Hub/core helpers; see [release-review-0.7.3.md](release-review-0.7.3.md).

`MinigameExtraActivities` supplies six additional contexts to the shared minigame
coach. It has no session state and reads the current public client state on the
client thread. Each result provides one current instruction, observed facts, an
existing local guide and resource/station object IDs. It does not operate the
game, select combat targets or synthesize encounter timers.

| Context | Required current evidence | Facts and instruction |
| --- | --- | --- |
| Nightmare Zone | Entire loaded region array is `[9033]` and player plane is above zero, matching RuneLite's KBD exclusion | Native current/banked points and absorption; supply/protection reminder |
| Pyramid Plunder | Player in region 7749 and native elapsed-run counter is positive | Native room, room requirement, timer text and entrance-trap state; trap-first or room-looting instruction |
| Mage Training Arena | Region 13462, with planes 0/1/2 identifying Enchanting/Graveyard/Alchemy; or visible native Telekinetic interface | Room and native points; current enchantment bonus, maze text or 30-coin alchemy item where available |
| Volcanic Mine | Visible native interface 611 root | Existing native time, points, stability, player, vent and chamber strings; team role/boulder/exit reminder |
| Blast Mine | Visible native interface 598 root | Native stored ore counters; excavation, loading, lighting, clearing and deposit loop |
| Fishing Trawler | Visible native interface 366 root | Existing native time, water, activity, net and catch strings; contribution reminder for either deck |

Missing or hidden text fields are omitted. Volcanic Mine and Fishing Trawler text
is mirrored without interpreting undocumented units or predicting future state.
Pyramid Plunder's elapsed counter gates entry; its displayed timer comes from the
native timer text rather than a newly started local clock. Nightmare Zone links
the absorption-rumble guide only while a positive native absorption effect is
observed. In the release, both absorption states keep only native facts; neither state automatically selects a guide, recommends a protection action or changes the guide's bank filter.

Alchemy chooses a 30-coin item only when all five native rows have readable names
and numeric values and exactly one row has that value. Incomplete or conflicting
tables keep a general instruction. Enchantment bonus shapes and Telekinetic maze
solutions are not inferred.

Resource highlights identify actual native objects present in the scene. They are
station/reference highlights, not a declaration that every container is unopened
or every action is currently available. Pyramid's entrance trap is highlighted
while its native trap flag is active; other rooms use their resource/station
sets. Burning Blast Mine walls are excluded from resource targets. No fixed tile
positions are supplied by this helper.

The core catalog unions `allHighlightObjectIds()` into its scene seed and calls
`detect(Client)` only after its primary contexts. `supportedActivities()` exposes
the six capabilities for the sidebar. The helper returns `Snapshot.EMPTY`
outside a confirmed context, on logout or without an observed player.

## Primary references

All native entry rules and IDs are pinned to RuneLite **1.13.2**:

- [NightmareZonePlugin.java](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/nightmarezone/NightmareZonePlugin.java), plus its overlay's current points and absorption reads.
- [PyramidPlunderPlugin.java](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/pyramidplunder/PyramidPlunderPlugin.java), plus `PyramidPlunderOverlay.java`'s room/trap reads.
- [MTA room implementations](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/mta): `AlchemyRoom`, `EnchantmentRoom`, `GraveyardRoom`, `TelekineticRoom`.
- [BlastMineOreCountOverlay.java](https://github.com/runelite/runelite/blob/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/blastmine/BlastMineOreCountOverlay.java) and `BlastMineRockType.java`.
- [`gameval` native identifier declarations](https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-api/src/main/java/net/runelite/api/gameval): `InterfaceID`, `ObjectID`, `VarbitID`, `VarPlayerID`.

Reviewed source artifact SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| `net.runelite:client:1.13.2:sources` | `86d51d6cb3428af7e9bae3c85b0d82f80b1360609f2bc2cacc37b45cd93d6b47` |
| `net.runelite:runelite-api:1.13.2:sources` | `1ffc10c5a6f071ffd4a20b92d851e9c954629b43e25acd52611b7111c499a7fb` |

Original wording follows the already bundled Wiki-linked guides for each
activity. Relevant upstream BSD notices are retained in
`src/main/resources/com/runewarden/licenses/minigame-extra-runelite-attribution.txt`.

Focused tests cover the NMZ/KBD collision, Pyramid's two-factor entry/trap
transition, all four MTA room checks and partial alchemy tables, native VM and
Trawler facts, hidden Blast Mine child compatibility, logout, and immutable
complete scene evidence registration.
