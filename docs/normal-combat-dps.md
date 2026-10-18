# Combat workbench: targets, ranked setups and equipment experiments

The Combat DPS module is a standalone workbench as well as a live equipment
reference. Search monsters by name or exact NPC ID without entering combat,
choose the precise variant, then compare the currently worn equipment with saved
Loadouts and editable sandbox sets. Conflicting shared NPC IDs are never resolved
by name. Returning to `Use interacting monster` follows the transformed native
NPC ID; the optional keep switch retains the last native target until the
character/login session changes. Explicit target selections are saved per
character. Boss Academy can open the same target search for its selected encounter.
Manual searches also expose exact variants of shared IDs, while native target
following continues to reject ambiguous IDs. The manually selected version saves
with the character's experiment.

The headline reads the current native stance, worn equipment, boosted levels and
active prayers. The ranking compares **up to eight equipment sets at once**
(including current equipment); additional saved sets remain selectable in Sets
& budget. It sorts supported normal-attack DPS first and keeps unrated entries
visible with their reason on selection. The selected setup card displays
accuracy, interval, primary maximum, expected damage per attack, exact full-HP
attack-cycle TTK, and percentage DPS change from the current attack. TTK includes
misses and overkill instead of dividing HP by DPS. Scythe expected damage includes
all supported splats; primary max describes its first splat.

Every set has its own best-supported/explicit attack-style choice, damaging spell
and blowpipe dart selection. A planned spell can explicitly assume its spellbook
without changing the live spellbook, equipment or attack settings. Required Magic
levels still apply; unavailable weapon/spell mechanics stay unrated. The current
live calculator continues to require the observed spellbook. These experiments
use the character's current boosted combat levels and active prayers.

Saved Loadouts enter theory comparison even when their equipment has not been
observed. Every equipment requirement must still resolve to one exact item and
slot; alternatives remain unrated. Current bank/inventory/equipment observations
separately label `Observed owned equipment`, `Equipment needed`, or `Ownership
incomplete`. Old bank snapshots never prove an item absent. This is equipment
ownership, not departure readiness: charge, unlock and supply checks belong to
Loadouts. Unobserved tradeable gear has a priced portion based on RuneLite's
cached prices; untradeable/unpriced requirements stay explicit. An optional gp
purchase budget marks estimates above budget and does not hide theory gear.
Equipment rows open the shared item database and its source links.

Duplicate selected equipment into **up to twelve character-specific sandbox
sets**, rename it, replace items in their native equipment slots, set ammo
quantities, or clear slots. Native item stats validate equipment slots; a
comparison requires an exact weapon. Sandbox editing never changes saved
Loadouts or sends an equipment action. Two-handed weapon/shield conflicts stay
unrated. All planned data is bounded and saved through the shared recoverable
store at `runewarden.dpsWorkbenchV1`; future/corrupt data is preserved. Profile
changes clear visible experiments and stale queued controls cannot write to the
new character. Open disclosure panels and unsaved text drafts survive live
combat refreshes.

Target & model optionally overrides HP, Defence and Magic **levels** for manual
scenarios. Empty fields retain the pinned Wiki base values, and Restore Wiki
base removes all overrides. Target identity, size, defensive bonuses, attributes,
weakness and unsupported-encounter flags remain intact; setting Defence to zero
does not make an excluded raid/scaling model supported. Scenario values are
labelled manual and are never claimed as observed drains.

Ranking evaluates damage distributions without running TTK for each attack mode.
Only the selected setup runs the bounded exact TTK recurrence, and the panel
reuses unchanged fingerprints. Work occurs on the client thread; Swing search
uses the local static target/item indexes and performs no native client reads.
Movement, healing and encounter downtime extend real kill times.

## Supported mechanics

| Area | Included |
| --- | --- |
| Accuracy | Native gear attack bonuses and attack stance; exact target melee/Magic/light/standard/heavy ranged defence; negative-roll formula; targets that use Defence for Magic defence |
| Levels and equipment | Observed boosts/prayers, melee/ranger/mage Void, crystal bow/Bowfa armour bonuses, crush Inquisitor, Soulreaper stacks, Bulwark defensive-stat strength |
| Damage distribution | Accurate zero damage raised to one; positive/negative flat armour; misses, overkill and multi-splat convolution |
| Weapons | Supported ordinary melee/ranged weapons and powered staves; Fang minimum/maximum and outside-ToA stab accuracy; Scythe target-size splats; Dark bow two hits; Tbow base-target Magic scaling; Sanguinesti 20% +8 proc |
| Target bonuses | Applicable salve, dragon hunter lance/crossbow/wand, supported demonbane weapons, rat-bone weapons, golem/leaf-bladed bonuses, Keris damage and 1/51 triple proc, matching elemental spell weaknesses |
| Other accuracy effects | Brimstone ring mixture; one-handed Confliction gauntlets accuracy |
| Ammunition | Verified weapon/ammo compatibility; self-ammo strength exclusion; equipped blowpipe Check observation or explicit dart override; native quiver ammo when compatibility leaves one exact choice; known charged quiver bonus |

Slayer bonus applicability is an explicit `Unknown / No task bonus / On assigned
task` comparison input. A relevant mask/helmet with unknown applicability stays
unrated. Salve takes precedence when applicable. An unrelated imbued item cannot
make an unimbued helmet eligible for ranged/Magic Slayer bonuses.

Observed blowpipe darts belong to the worn weapon and do not establish the load
of another saved copy. A saved blowpipe needs an explicit dart override. Quiver
ammo uses the native varps also read by RuneLite's Ammo plugin, gated by the worn
cape's `QUIVER_AMMO_AVAILABLE` parameter. Two different compatible ammo choices
stay unknown. A saved set uses its explicitly saved ammo slot and does not borrow
the current quiver's hidden setup.

## Deliberate unrated cases

Targets with conflicting shared IDs remain unknown. Raid scaling, phase-dependent
stats, protected/immune targets and special damage transforms are excluded using
the pinned calculator's target lists and named branches. Examples include
Chambers, ToA, ToB, Zulrah, Corp, Nex/Nightmare scaling, respiratory
systems and Tormented/Demonic gorillas. Vardorvis needs an explicit HP snapshot;
shared Duke/Yama IDs need an exact manual variant and still retain any unsupported
mechanic restrictions.

Enchanted/seeking ammo effects, Sunfire minimum damage, unobserved tome charge
state, unsupported set/weapon effects, vampyre rules and special-requirement
spells stay unrated. Poison/venom, thralls, target healing, live defence-drain
observation, attack movement and prayer prediction are outside the model.
Manual special and defence scenarios are described below. The module sends no
game actions.

## Data and verification

- Calculation/weapon/style/ammo reference: the OSRS Wiki DPS calculator,
  commit [`462054e6c0686ce318a051288ab91b9bfa1e91b5`](https://github.com/weirdgloop/osrs-dps-calc/tree/462054e6c0686ce318a051288ab91b9bfa1e91b5),
  whose last `PlayerVsNPCCalc.ts` change was 2 September 2026.
- NPC facts: the existing pinned monster source at
  [`a27fc3c898eb15accbfa0457c89b6b6a1b132f1f`](https://github.com/weirdgloop/osrs-dps-calc/blob/a27fc3c898eb15accbfa0457c89b6b6a1b132f1f/cdn/json/monsters.json).
  The reference contains 2,860 rows; exact-ID aggregation rejects conflicting
  HP, Defence, Magic, size, defensive bonuses, attributes and weakness.
- Spell requirements: [OSRS Wiki List of spells, revision 15343816](https://oldschool.runescape.wiki/w/List_of_spells?oldid=15343816).
  All 50 bundled damaging spell names have a verified level entry.
- Runtime bonuses/speed: RuneLite 1.13.2 `ItemManager` / `ItemEquipmentStats`;
  quiver observations follow the same version's `AmmoPlugin` native read path.
- `tools/build-dps-reference.py` produces the bundled static reference without
  evaluating upstream executable code. It rejects unresolved target constants
  and ammunition categories. No combat render/tick performs HTTP requests.

Java 11 focused tests cover accuracy branches, Fang versus ToA-style rerolls,
minimum damage, both armour signs, overkill, prayers/stances, multi-splats,
target bonuses, spells, Sanguinesti/Keris procs, ammo/quiver ambiguity, exact
variant rejection, ranked theory/owned sets, sandbox editing, target scenarios, recoverable per-profile persistence, stale callback guards,
metadata retry, independently checked special distributions, defence-drain
rounding/floors, explicit phase snapshots, preparation/recovery journals, and a
206px sidebar layout. The existing Max Hit tests remain part
of the same focused check.

Source and licence details are bundled in
`src/main/resources/com/runewarden/licenses/dps-reference-attribution.txt`.

## Manual special attacks and defence scenarios

Each setup has **Compare one special attack**, supporting 25 weapons:
Dragon dagger, Abyssal dagger, Voidwaker, Dragon/crystal halberds,
Elder maul, Dragon warhammer, Dragon mace/sword/longsword/scimitar,
Arclight/Emberlight, Abyssal whip, Bandos/Armadyl/Saradomin/Zamorak godswords,
Toxic/Blazing/Rosewood blowpipes, Dragon knife, Heavy/light ballistas and Fang.
The worn normal-attack headline does not change when a saved set uses a special.

Specials use their own accuracy factors, damage truncation order, defence style
and energy cost. Dragon dagger hits roll accuracy independently; Abyssal dagger's
second hit is conditional on the first accuracy roll. Large-target halberds roll
their second hit at three-quarters of the special attack roll. Voidwaker is a
guaranteed magic-damage special; Fang retains its special maximum. Godsword
damage applies the common 1.1 multiplier before the weapon-specific multiplier.
The burst rate, total burst maximum and damage per 100% special energy describe
one attack. A special has **no repeated-special full-fight TTK**. Healing, energy
regeneration and delayed effects are separate; unsupported specials stay unrated.

**Phase & defence reductions** accepts explicitly entered successful Elder maul,
Dragon warhammer, Arclight/Emberlight and Tonalztics hits, Bandos godsword damage,
Vulnerability or Accursed sceptre, Seercull damage, and Eye of Ayak magic-defence
bonus reduction. The pinned reference defines ordering, base-versus-current
level arithmetic and NPC-specific floors. These inputs are planning scenarios,
not live drain observations. BGS overflow into missing Strength/Attack levels is
unrated; use final level overrides with the BGS scenario cleared instead.

Manually chosen Araxxor normal/enraged phases and Vardorvis HP snapshots support
their proven defence/Magic changes. Vardorvis uses its exact selected quest,
post-quest or awakened HP range and remains immune to defence drain. These are
snapshot rates: full-fight TTK is omitted because encounter phases change.
Other excluded raid, scaling, immunity or target transformations remain unrated.

The additions were checked against the same pinned Wiki calculator commit,
particularly `PlayerVsNPCCalc.ts`, `Equipment.ts`, `scaling/DefenceReduction.ts`,
`scaling/Phases.ts` and `scaling/Vardorvis.ts`. No tick/render callback fetches
these files or executes upstream code.
