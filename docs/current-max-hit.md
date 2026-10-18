# Current max hit

The character HUD and Max hit sidebar read current boosted levels, active damage prayers, attack stance, and equipped RuneLite item statistics. Ordinary base hits are labelled `BASE MELEE`, `BASE RANGED`, or `BASE MAGIC`. Special attacks, ammunition procs, Slayer/salve/dragonbane and other target bonuses are excluded.

## Supported weapons

- Ordinary melee, matching complete Void sets, Osmumten's fang's normal damage range reduction, Soulreaper axe's observed soul stacks, and Dinh's bulwark's defensive-equipment strength conversion. Bulwark Block mode has no attack estimate.
- Scythe: `BASE FIRST HIT` is its first splat. The detail lists the half/quarter splats and their target-size conditions; the HUD does not present a three-splat sum as a single hit.
- Ordinary bows/crossbows/ballistas/thrown weapons. Self-ammo crystal bows and Bow of faerdhinen include worn crystal armour. Irrelevant ammunition-slot strength is excluded for self-ammo/thrown weapons.
- Blowpipes use a loaded dart observed from a recent server check of the equipped pipe, or an explicit override labelled `INPUT RANGED`. Inventory copies, ammo slots and unrelated or old check messages do not establish the worn load.
- Tridents of the seas/swamp, Sanguinesti, Shadow, Eye of Ayak, Warped sceptre, powered revenant sceptres, Bone staff, Dawnbringer and Gauntlet staves. Sanguinesti's maximum includes its 20% normal +8 damage proc. Shadow detects exact ToA targets automatically; its optional override is inside collapsed calculation details. Magic prayers, elite mage Void, ancient-spell Virtus, Chaos gauntlets, matching elemental amulets and standard-spell smoke staves are included.
- Normal spells from the bundled Wiki numeric spell table. Elemental spells scale at the current published Magic thresholds. The selected spell can be entered manually. A visible autocast spell object maps through the published gameval spell IDs or an exact cache-name match, and is used only while the attack style is casting.

`REF TBOW` uses the exact current interacting NPC ID's **Wiki base** Magic level/attack bonus, with the Xerician cap. Conflicting shared-ID monster variants are deliberately unavailable. This is a static reference estimate, not a claim to observe defence drains, raid scaling, or boss phase changes.

## Inputs and limits

The Max hit sidebar displays the automatic result first. Relevant spell, loaded-dart and Shadow controls are inside collapsed details, with Use automatic inputs to reset overrides. Inputs are session-local and the result marks their scope. A user-selected spell describes a manual spell cast with current equipment, even while another weapon stance is selected. Observed autocast state does not override a powered-staff attack.

Unavailable levels/styles/item records, uncharged weapons, missing dart/spell inputs, unimplemented weapon/set multipliers and unknown target variants show `—`. Charged tomes are not guessed. Enhanced Magic Dart remains unavailable without verified Slayer task state. No client actions, spell selection, equipment changes, or special attacks are performed.

Prayer-adjusted level, stance, Void, base damage and weapon multipliers are floored in game order. Soulreaper strength stacks are additive with prayer, not multiplied by it. Magic damage uses tenths of a percent to retain fractional equipment bonuses. Shadow caps amplified gear damage before adding the active prayer bonus.

Sources verified 2026-10-16:

- [OSRS Wiki open DPS calculator damage arithmetic](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/PlayerVsNPCCalc.ts)
- [Gear-specific arithmetic](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/lib/Equipment.ts)
- [Prayer factors](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/enums/Prayer.ts)
- [Spell facts and scaling](https://github.com/weirdgloop/osrs-dps-calc/blob/main/src/types/Spell.ts). Bundled numeric spell facts record the retrieved commit in `combat-spells.json`; attribution is in `licenses/combat-spells-attribution.txt`.
- RuneLite 1.13.2 `AttackStylesPlugin`, `ItemManager`, public `SOULREAPER_STACKS`/`AUTOCAST_SPELL_OBJ` and item equipment statistic APIs. The implementation is original Java arithmetic; it does not import the TypeScript calculator.

A loaded equipable `ItemStats` record with an omitted all-zero equipment block contributes zero. An absent item-stat record remains unknown while unavailable.

## Automatic sidebar and observed inputs

The sidebar now opens with a large live result and a full-width equipment card.
The card names the equipped weapon, attack stance, current/base damage-skill
level, worn strength/magic damage, worn item count, and active damage prayer.
The shared calculation reads the actual public WORN container (ID 94) on each
refresh; it does not require players to enter their gear. A missing worn weapon
while the player composition still reports one shows an updating state rather
than calculating an unarmed hit from incomplete equipment.

Calculation details and overrides start collapsed. Only fields relevant to the
current weapon appear there. An unavailable attack shows its relevant missing
state; spell, dart and Shadow controls are no longer permanently displayed.

Autocast uses public `AUTOCAST_SPELL_OBJ`, with the spell object IDs published in
the pinned RuneLite `gameval.ItemID` mapped directly to the spell catalogue.
Defensive autocast uses `AUTOCAST_DEFMODE`, matching RuneLite's attack-style
reader. The fallback cache-name lookup remains available for other exact spell
objects. Shadow automatically uses its ToA multiplier when the current target
is an exact ToA NPC ID in the Wiki calculator's published list. A target-free
base estimate uses its normal outside-ToA multiplier.

Blowpipe darts are hidden from the worn item container. Runewarden can now learn
them from the server's `Darts: [type] dart x [quantity]` check message after the
player uses **Check on the equipped blowpipe**. The observation updates the live
result automatically. Inventory-copy checks and player chat cannot establish a
worn blowpipe's load. Account changes, weapon changes and observed load/unload
operations invalidate that observation. An optional dart override remains in
the collapsed details when no native observation is available.

The check-message format was verified against the primary implementation at
https://github.com/Sintry1/slayer-task-loot (its BlowpipeDart and game-message
reader); the implementation here independently correlates the equipped-item
Check and rejects unrelated messages. The ToA target IDs follow the Wiki DPS
calculator `src/lib/constants.ts` verified on 2026-10-16.
