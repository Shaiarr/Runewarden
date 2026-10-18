# Hunter Rumours

The Hunter rumours module remembers the assignments observed for Wolf, Teco, Aco,
Cervus, Ornus and Gilman. Native guild dialogue and the hunter's whistle identify
the current rumour. Ordinary player chat cannot change it. A fresh login shows
remembered assignments as **Last seen** until another native observation confirms
them; manual corrections are shown as **Manual reference**.

## Using the coach

The current assignment card contains the creature's actual OSRS item icon, trap
family, Hunter requirement, guild outfit count and carried rare part. It links to
the appropriate novice, adept, expert or master training guide and the rare item's
sources. Supplies can be sent to the shared bank filter. Each mapped hunting area
has a user-selected route preview, with its fairy ring code where the reference
provides one. No travel action or bank withdrawal is performed.

A native rare-piece message changes the HUD to **Rare piece found** and asks the
player to return to the guild. A carried part is reported separately; possession
alone does not establish that the current assignment is complete. Reward dialogue
clears only the speaking master's saved assignment. Gilman's offer to retain an
older assignment does not select it as the current assignment.

The movable HUD uses the shared visibility, scale, type, colours and frame settings
under **Hunter rumours**. The module also has profile-specific HUD and creature
highlight switches. Scene highlighting is limited to the confirmed current
creature, a matching world view and a valid scene snapshot. It stops after a rare
piece is found.

## Progress and reference scope

**Matching catches** counts observed Hunter XP changes that match the pinned
creature reference while near one of its mapped locations. It is not the game's
hidden bad-luck counter. Batched XP, boosts, alternative XP sources and catches
made while the plugin is stopped can make that observation incomplete. The
reference bad-luck threshold is adjusted from the actual worn guild hunter pieces;
it is displayed separately from catches, without a claimed drop chance or countdown.
There is no fabricated current-rumour varbit or hidden completion state.

The six hunters' tier requirements are 46, 57, 72 and 91 Hunter for novice, adept,
expert and master. Master access also checks **At First Light** for an unconfirmed
manual/reference assignment. Confirmed native dialogue establishes access without
replacing it with a guessed local gate.

The bundled data contains 29 creatures, 30 dialogue names, six masters and ten trap
families. It preserves every mapped upstream location. The two red chinchompa names
share the deduplicated union of their locations, including the Great Conch and
Tlati Rainforest additions. There are 438 name/location reference entries, including
39 shared entries under each red chinchompa name. Location labels retain upstream
quest/access qualifications; route previews do not claim that every access gate is
satisfied.

Falconry supplies use coins rather than treating a falcon icon as a bank item.
Herbiboar supplies use secateurs rather than treating its tracking icon as a required
noose wand. The helper does not yet inspect meat/clue pouches, force diary tasks,
change fairy ring menus, rewrite native dialogue or automate assignment blocking.
It does not calculate an exact hidden pity state or a rare-item probability.

## Sources

The official Plugin Hub project, Hunter Rumours, is pinned to
[`geel9/runelite-hunter-rumours@91b833b05b30bcb18cae812be5f0686b324d41c2`](https://github.com/geel9/runelite-hunter-rumours/tree/91b833b05b30bcb18cae812be5f0686b324d41c2).
Its `Hunter`, `Rumour`, `Creature`, `Trap`, `RumourLocation`, dialogue parser and
native event handling supply the facts and observation protocol. Its BSD 2-Clause
copyright and attribution are included in
`licenses/hunter-rumours-BSD-2-Clause.txt`. The UI and state machine are Runewarden
implementations; the pinned upstream's native-widget rewrites are not included.
The OSRS Wiki [Hunter Guild](https://oldschool.runescape.wiki/w/Hunter_Guild),
[At First Light](https://oldschool.runescape.wiki/w/At_First_Light) and
[Falconry](https://oldschool.runescape.wiki/w/Falconry) references were checked for
access and equipment distinctions. Native API compilation targets RuneLite 1.13.2.

## Verification

`HunterRumourTest` checks the pinned dataset and both chinchompa names, all six
masters, native/public-chat separation, guild dialogue ambiguities, independent
reward handoffs, matching XP observations, actual equipment and rare-item evidence,
profile/reconnect isolation, stale queued/panel actions, manual choices retained
through visible-panel refreshes, scene boundaries and the
206px sidebar plus a maximally scaled HUD in a small viewport. Production and test
sources compile with Java 11. These checks do not substitute for an in-game test of
new game dialogue or future changes to locations and XP rates.
