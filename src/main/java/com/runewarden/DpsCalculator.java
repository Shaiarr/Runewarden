/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Prayer;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Normal attack arithmetic. Unknown target, ammo or mechanics stay unrated rather than entering rankings. */
final class DpsCalculator
{
    static final class Result
    {
        final boolean supported;
        final Double accuracy, expectedHit, dps, ttkSeconds;
        final Integer maxHit, minHit, attackTicks, specialEnergy, totalMaxHit;
        final boolean snapshotOnly;
        final String reason, mode;
        final List<String> mechanics;
        private final double[] distribution;
        private Result(String reason, String mode)
        {
            supported = false; this.reason = reason; this.mode = mode;
            accuracy = null; expectedHit = null; dps = null; ttkSeconds = null;
            maxHit = null; minHit = null; attackTicks = null; specialEnergy = null; totalMaxHit = null; snapshotOnly = false; distribution = null; mechanics = Collections.emptyList();
        }
        private Result(double accuracy, int minimum, int maximum, int ticks, double[] distribution, int hp,
            String mode, List<String> mechanics, boolean calculateTtk, Integer specialEnergy, boolean snapshotOnly)
        {
            this.specialEnergy = specialEnergy; this.snapshotOnly = snapshotOnly; totalMaxHit = distribution.length-1;
            supported = true; this.accuracy = accuracy; minHit = minimum; maxHit = maximum; attackTicks = ticks;
            expectedHit = mean(distribution); dps = expectedHit / (ticks * .6);
            this.distribution = distribution;
            double attacks = calculateTtk && specialEnergy == null && !snapshotOnly ? expectedAttacks(distribution, hp) : Double.NaN;
            ttkSeconds = Double.isFinite(attacks) ? attacks * ticks * .6 : null;
            this.mode = mode; reason = specialEnergy == null ? "Wiki target scenario · continuous normal attacks" : "One special attack · finite special energy";
            this.mechanics = Collections.unmodifiableList(new ArrayList<>(mechanics));
        }
        static Result unknown(String reason) { return new Result(reason, ""); }
        Result withTtk(int hp)
        { return supported ? new Result(accuracy, minHit, maxHit, attackTicks, distribution, hp, mode, mechanics, true, specialEnergy, snapshotOnly) : this; }
    }
    private static final class Gear
    {
        final List<String> names = new ArrayList<>();
        String weapon = "";
        int weaponId = -1, ammoId = -1, capeId = -1, str, rstr, mdmg, astab, aslash, acrush, arange, amagic, defenceSum, ammoStr, ammoRange;
        int speed;
        boolean twoHanded, shield;
        boolean has(String prefix) { for (String name : names) if (name.startsWith(prefix)) return true; return false; }
        boolean contains(String text) { for (String name : names) if (name.contains(text)) return true; return false; }
    }
    private DpsCalculator() { }

    static Result evaluate(Client client, ItemManager items, DpsReferenceCatalog references,
        DpsReferenceCatalog.Target target, Item[] equipment, DpsReferenceCatalog.Mode mode,
        CurrentMaxHit.Inputs inputs, Boolean onSlayerTask, boolean candidate)
    { return evaluatePlanned(client, items, references, target, equipment, mode, inputs, onSlayerTask, candidate, null, true); }

    /** Explicit planning inputs never change the client's attack style, spellbook or equipment. */
    static Result evaluatePlanned(Client client, ItemManager items, DpsReferenceCatalog references,
        DpsReferenceCatalog.Target target, Item[] equipment, DpsReferenceCatalog.Mode mode,
        CurrentMaxHit.Inputs inputs, Boolean onSlayerTask, boolean candidate, Integer plannedSpellbook, boolean calculateTtk)
    { return evaluateAttack(client, items, references, target, equipment, mode, inputs, onSlayerTask, candidate, plannedSpellbook, calculateTtk, false); }

    static Result evaluateAttack(Client client, ItemManager items, DpsReferenceCatalog references,
        DpsReferenceCatalog.Target target, Item[] equipment, DpsReferenceCatalog.Mode mode,
        CurrentMaxHit.Inputs inputs, Boolean onSlayerTask, boolean candidate, Integer plannedSpellbook, boolean calculateTtk, boolean special)
    {
        if (target == null) return Result.unknown("Select a target with unambiguous combat stats");
        if (target.unsupportedReason != null) return Result.unknown(target.unsupportedReason);
        if (target.hitpoints == null || target.hitpoints < 1 || target.hitpoints > 50_000
            || target.defenceLevel == null || target.defenceLevel < 0 || target.magicLevel == null || target.magicLevel < 0
            || target.magicAttackBonus == null || target.size == null || target.size < 1 || target.defenceBonuses == null)
            return Result.unknown("Target HP, size or defensive levels are not verified");
        if (mode == null || !(mode.melee() || mode.magic() || mode.ranged())) return Result.unknown("Current attack mode is not verified");
        if (equipment == null || client == null || items == null || client.getGameState() != GameState.LOGGED_IN) return Result.unknown("Equipment is unavailable");
        Gear gear = new Gear();
        for (int slot = 0; slot < equipment.length; ++slot)
        {
            Item item = equipment[slot]; if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) continue;
            if (slot != EquipmentInventorySlot.AMMO.getSlotIdx() && item.getQuantity() != 1) return Result.unknown("An equipment slot has an invalid quantity");
            ItemComposition definition = items.getItemComposition(item.getId());
            ItemStats itemStats = items.getItemStats(item.getId());
            if (definition == null || definition.getName() == null || itemStats == null) return Result.unknown("Equipment facts are still loading");
            if (!itemStats.isEquipable()) return Result.unknown("A saved item is not equippable");
            String name = definition.getName().toLowerCase(Locale.ROOT); gear.names.add(name);
            if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx()) { gear.weapon = name; gear.weaponId = item.getId(); }
            if (slot == EquipmentInventorySlot.AMMO.getSlotIdx()) gear.ammoId = item.getId();
            if (slot == EquipmentInventorySlot.CAPE.getSlotIdx()) gear.capeId = item.getId();
            if (slot == EquipmentInventorySlot.SHIELD.getSlotIdx()) gear.shield = true;
            ItemEquipmentStats stats = itemStats.getEquipment();
            if (stats == null) { if (itemStats.isEquipable() && slot != EquipmentInventorySlot.WEAPON.getSlotIdx()) continue; return Result.unknown("An equipment bonus is unavailable"); }
            if (stats.getSlot() != slot) return Result.unknown("Saved item does not match its equipment slot");
            gear.str += stats.getStr(); gear.rstr += stats.getRstr(); gear.mdmg += Math.round(stats.getMdmg() * 10);
            gear.astab += stats.getAstab(); gear.aslash += stats.getAslash(); gear.acrush += stats.getAcrush();
            gear.arange += stats.getArange(); gear.amagic += stats.getAmagic();
            gear.defenceSum += stats.getDstab() + stats.getDslash() + stats.getDcrush() + stats.getDrange();
            if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
            { gear.weapon = name; gear.weaponId = item.getId(); gear.speed = stats.getAspeed(); gear.twoHanded = stats.isTwoHanded(); }
            if (slot == EquipmentInventorySlot.AMMO.getSlotIdx())
            { gear.ammoId = item.getId(); gear.ammoStr = stats.getRstr(); gear.ammoRange = stats.getArange(); }
            if (slot == EquipmentInventorySlot.SHIELD.getSlotIdx()) gear.shield = true;
        }
        if (gear.twoHanded && gear.shield) return Result.unknown("A two-handed weapon and shield cannot be equipped together");
        DpsReferenceCatalog.Weapon weapon = references.weapon(gear.weaponId);
        if (weapon == null) return Result.unknown("Weapon category and attack interval are not catalogued");
        if (gear.weapon.contains("uncharged") || gear.weapon.contains("(inactive)") || gear.weapon.contains("(empty)"))
            return Result.unknown("Weapon is uncharged");
        if (gear.weapon.startsWith("keris partisan of amascut")) { gear.str -= 22; gear.astab -= 50; }
        if (target.has("vampyre1") || target.has("vampyre2") || target.has("vampyre3"))
            return Result.unknown("Vampyre weapon restrictions and damage transforms are not modelled");
        if (target.has("flying") && mode.melee() && !"Polearm".equals(weapon.category))
            return Result.unknown("This target's flying/melee restriction is outside the model");
        if (target.has("leafy") && !gear.weapon.startsWith("leaf-bladed")) return Result.unknown("This target requires a verified leaf-bladed weapon");
        if (gear.weapon.startsWith("bone ") && !target.has("rat")) return Result.unknown("Rat-bone weapons cannot damage this target");
        DpsSpecialAttack spec = special ? DpsSpecialAttack.forWeapon(gear.weapon) : null;
        if (special && (spec == null || mode.casting() || mode.magic())) return Result.unknown("This weapon's special attack is not supported for the selected mode");
        if (spec != null && (mode.ranged() != (gear.weapon.contains("blowpipe") || gear.weapon.contains("ballista") || gear.weapon.contains("knife"))))
            return Result.unknown("Choose the weapon's attacking combat style for its special");
        String unsupported = unsupported(gear, mode, target);
        if (unsupported != null) return Result.unknown(unsupported);

        CombatSpellCatalog.Spell spell = mode.casting() && inputs != null ? inputs.spell : null;
        if (mode.casting() && spell == null) return Result.unknown("Observe or select the damaging spell in Max hit");
        if (spell != null)
        {
            int requiredBook = "standard".equals(spell.spellbook) ? 0 : "ancient".equals(spell.spellbook) ? 1 : "arceuus".equals(spell.spellbook) ? 3 : -1;
            if (requiredBook < 0 || (plannedSpellbook == null ? client.getVarbitValue(VarbitID.SPELLBOOK) : plannedSpellbook) != requiredBook)
                return Result.unknown("Selected spell is not on the observed current spellbook");
            Integer requiredLevel = references.spellLevel(spell.name);
            if (requiredLevel == null) return Result.unknown("Selected spell's Magic requirement is not verified");
            if (client.getBoostedSkillLevel(Skill.MAGIC) < requiredLevel) return Result.unknown("Current Magic level is too low for the selected spell");
        }
        if (spell != null && (spell.name.contains("Demonbane") || spell.name.equals("Magic Dart")
            || spell.name.equals("Iban Blast") || spell.name.equals("Claws of Guthix")
            || spell.name.equals("Saradomin Strike") || spell.name.equals("Flames of Zamorak")))
            return Result.unknown("This spell's required weapon or additional buff state is outside the DPS model");
        if (spell != null && spell.name.equals("Crumble Undead") && !target.has("undead"))
            return Result.unknown("Crumble Undead requires an undead target");
        if (spell != null && "fire".equals(spell.element))
        {
            Boolean sunfire = sunfireAvailable(client);
            if (sunfire == null) return Result.unknown("Inventory or rune pouch rune state is not available");
            if (sunfire) return Result.unknown("Sunfire rune minimum damage is not modelled");
        }

        int ticks = gear.speed > 0 ? gear.speed : weapon.speed == null ? 0 : weapon.speed;
        if (mode.ranged())
        {
            if ("Rapid".equals(mode.stance)) ticks--;
            if (weapon.ammo == null && !"Thrown".equals(weapon.category)) return Result.unknown("Weapon ammunition compatibility is not verified");
            boolean selfAmmo = "Thrown".equals(weapon.category) || weapon.ammo != null && weapon.ammo.isEmpty();
            if (selfAmmo) { gear.rstr -= gear.ammoStr; gear.arange -= gear.ammoRange; }
            else
            {
                boolean regular = weapon.ammo.contains(gear.ammoId);
                if (!candidate && gear.capeId > 0)
                {
                    ItemComposition cape = items.getItemComposition(gear.capeId);
                    if (cape != null && cape.getIntValue(ParamID.QUIVER_AMMO_AVAILABLE) == 1)
                    {
                        int quiverId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
                        boolean quiver = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT) > 0 && weapon.ammo.contains(quiverId);
                        if (quiver && regular && quiverId != gear.ammoId) return Result.unknown("Both observed ammo slots are compatible; active ammunition is ambiguous");
                        if (quiver && !regular)
                        {
                            ItemStats quiverStats = items.getItemStats(quiverId); ItemComposition quiverDefinition = items.getItemComposition(quiverId);
                            if (quiverStats == null || quiverStats.getEquipment() == null || quiverDefinition == null || quiverDefinition.getName() == null)
                                return Result.unknown("Observed quiver ammunition stats are unavailable");
                            gear.rstr += quiverStats.getEquipment().getRstr() - gear.ammoStr;
                            gear.arange += quiverStats.getEquipment().getArange() - gear.ammoRange;
                            gear.ammoId = quiverId; gear.names.add(quiverDefinition.getName().toLowerCase(Locale.ROOT)); regular = true;
                        }
                    }
                }
                if (!regular) return Result.unknown("No uniquely compatible ammunition is observed for this equipment");
                if (gear.capeId == 28951 || gear.has("blessed dizana's quiver") || gear.has("dizana's max cape"))
                { gear.arange += 10; gear.rstr += 1; }
                if (gear.contains("bolts (e)") || gear.contains("dragon bolts (e)") || gear.contains("seeking"))
                    return Result.unknown("Ammunition proc or minimum-hit effects are not modelled");
            }
            if (gear.weapon.contains("blowpipe"))
            {
                if (inputs == null || inputs.dartStrength == null || candidate && inputs.observedDart)
                    return Result.unknown("Loaded darts are not observed for this blowpipe; an explicit dart override can compare it");
                gear.rstr += inputs.dartStrength;
            }
        }
        if (mode.casting())
            ticks = mode.stance.contains("Autocast") && gear.weapon.contains("harmonised nightmare staff")
                && "standard".equals(spell.spellbook) ? 4 : 5;
        if (ticks < 1 || ticks > 20) return Result.unknown("Attack interval is not verified");

        List<String> mechanics = new ArrayList<>();
        mechanics.add(spec == null ? "Normal attacks · movement and healing downtime are separate" : spec.note());
        if (target.scenarioDescription != null) mechanics.add(target.scenarioDescription);
        boolean salveEnhanced = target.has("undead") && (gear.has("salve amulet (e)") || gear.has("salve amulet(ei)"));
        boolean salve = target.has("undead") && (gear.has("salve amulet") && (!mode.ranged() && !mode.magic()
            || gear.has("salve amulet(i)") || gear.has("salve amulet(ei)")));
        boolean imbuedMask = false;
        for (String name : gear.names)
            if ((name.contains("slayer helmet") || name.contains("black mask")) && name.contains("(i)")) imbuedMask = true;
        boolean taskMask = mode.melee() ? gear.contains("slayer helmet") || gear.contains("black mask") : imbuedMask;
        if (!salve && taskMask && onSlayerTask == null) return Result.unknown("Slayer task applicability is unknown; choose Yes or No in comparison details");
        boolean task = !salve && taskMask && Boolean.TRUE.equals(onSlayerTask);
        if (task) mechanics.add("Slayer-task applicability explicitly selected");
        if (salve) mechanics.add("Salve bonus against undead");
        int accuracyPrayer = accuracyPrayer(client, mode);
        Skill accuracySkill = mode.magic() ? Skill.MAGIC : mode.ranged() ? Skill.RANGED : Skill.ATTACK;
        if (client.getRealSkillLevel(accuracySkill) < 1 || client.getRealSkillLevel(mode.magic() ? Skill.MAGIC : mode.ranged() ? Skill.RANGED : Skill.STRENGTH) < 1)
            return Result.unknown("Combat levels are unavailable");
        int stanceAccuracy = "Accurate".equals(mode.stance) ? mode.magic() ? 2 : 3 : "Controlled".equals(mode.stance) ? 1 : 0;
        int effectiveAttack = Math.max(0, client.getBoostedSkillLevel(accuracySkill)) * accuracyPrayer / 100 + stanceAccuracy + (mode.magic() ? 9 : 8);
        if (voidSet(gear, mode.magic() ? "void mage helm" : mode.ranged() ? "void ranger helm" : "void melee helm"))
            effectiveAttack = effectiveAttack * (mode.magic() ? 29 : 11) / (mode.magic() ? 20 : 10);
        int attackBonus = mode.magic() ? gear.amagic : mode.ranged() ? gear.arange : "stab".equals(mode.type) ? gear.astab : "slash".equals(mode.type) ? gear.aslash : gear.acrush;
        boolean shadow = mode.magic() && !mode.casting() && gear.weapon.contains("tumeken's shadow");
        if (shadow) attackBonus *= inputs != null && inputs.tombsOfAmascut ? 4 : 3;
        long baseAttackRoll = (long) effectiveAttack * (attackBonus + 64);
        long attackRoll = baseAttackRoll;
        int min = 0, max;
        if (mode.magic())
        {
            Integer base = spell == null ? CurrentMaxHit.poweredBase(gear.weapon, client.getBoostedSkillLevel(Skill.MAGIC)) : spell.maxHit(client.getBoostedSkillLevel(Skill.MAGIC));
            if (base == null) return Result.unknown("Powered magic weapon damage is not supported");
            if (spell != null && spell.name.endsWith("Bolt") && gear.has("chaos gauntlets")) base += 3;
            if (spell != null && spell.element != null && (gear.has("elemental amulet") || gear.has("amulet of " + spell.element))) base += 2;
            int damageBonus = shadow ? Math.min(1000, gear.mdmg * (inputs != null && inputs.tombsOfAmascut ? 4 : 3)) : gear.mdmg;
            if (voidSet(gear, "void mage helm") && gear.has("elite void top") && gear.has("elite void robe")) damageBonus += 50;
            if (spell != null && "ancient".equals(spell.spellbook)) for (String name : gear.names) if (name.startsWith("virtus ")) damageBonus += 30;
            int additiveAccuracy = 0;
            if (spell != null && "standard".equals(spell.spellbook) && gear.contains("smoke")) { damageBonus += 100; additiveAccuracy += 10; }
            if (salve && (gear.has("salve amulet(ei)") || gear.has("salve amulet(i)")))
            { int percent = salveEnhanced ? 20 : 15; damageBonus += percent * 10; additiveAccuracy += percent; }
            if (client.isPrayerActive(Prayer.AUGURY)) damageBonus += 40;
            else if (client.isPrayerActive(Prayer.MYSTIC_VIGOUR)) damageBonus += 30;
            else if (client.isPrayerActive(Prayer.MYSTIC_MIGHT)) damageBonus += 20;
            else if (client.isPrayerActive(Prayer.MYSTIC_LORE)) damageBonus += 10;
            max = Math.max(0, base * (1000 + damageBonus) / 1000);
            attackRoll = factor(attackRoll, 100 + additiveAccuracy, 100);
            if (task) { max = max * 23 / 20; attackRoll = factor(attackRoll, 23, 20); }
            if (target.has("dragon"))
            {
                if (gear.weapon.startsWith("dragon hunter lance")) { max = max * 6 / 5; attackRoll = factor(attackRoll, 6, 5); }
                else if (gear.weapon.startsWith("dragon hunter wand")) { max = max * 7 / 5; attackRoll = factor(attackRoll, 7, 4); }
                else if (gear.weapon.startsWith("dragon hunter crossbow")) { max = max * 5 / 4; attackRoll = factor(attackRoll, 13, 10); }
            }
            if (target.weakness != null && spell != null && spell.element != null && spell.element.equals(target.weakness.element))
            { max += base * target.weakness.severity / 100; attackRoll += factor(baseAttackRoll, target.weakness.severity, 100); mechanics.add(spell.element + " elemental weakness " + target.weakness.severity + "%"); }
            if (gear.weapon.equals("dawnbringer") && spell == null) max = Math.max(2, max / 2);
        }
        else
        {
            int strengthPrayer = strengthPrayer(client, mode.ranged());
            int strengthLevel = client.getBoostedSkillLevel(mode.ranged() ? Skill.RANGED : Skill.STRENGTH);
            int effectiveStrength = Math.max(0, strengthLevel) * strengthPrayer / 100;
            if (!mode.ranged() && strengthPrayer == 105 && strengthLevel <= 20) effectiveStrength = strengthLevel + 1;
            if (!mode.ranged() && gear.weapon.contains("soulreaper"))
                effectiveStrength += strengthLevel * Math.max(0, Math.min(5, client.getVarpValue(VarPlayerID.SOULREAPER_STACKS))) * 6 / 100;
            effectiveStrength += 8 + ("Accurate".equals(mode.stance) && mode.ranged() || "Aggressive".equals(mode.stance) && mode.melee() ? 3 : "Controlled".equals(mode.stance) ? 1 : 0);
            if (voidSet(gear, mode.ranged() ? "void ranger helm" : "void melee helm"))
            { boolean elite = mode.ranged() && gear.has("elite void top") && gear.has("elite void robe"); effectiveStrength = effectiveStrength * (elite ? 9 : 11) / (elite ? 8 : 10); }
            int strengthBonus = mode.ranged() ? gear.rstr : gear.str;
            if (gear.weapon.contains("bulwark")) strengthBonus += CurrentMaxHit.bulwarkStrength(gear.defenceSum);
            max = CurrentMaxHit.baseHit(effectiveStrength, strengthBonus);
            int crystal = crystalPieces(gear);
            boolean crystalBow = mode.ranged() && (gear.weapon.contains("crystal bow") || gear.weapon.contains("faerdhinen"));
            if (crystalBow || mode.melee() && gear.has("crystal blessing")) max = max * (40 + crystal) / 40;
            if (crystalBow) attackRoll = factor(attackRoll, 20 + crystal, 20);
            if (salve) { int num = salveEnhanced ? 6 : 7, den = salveEnhanced ? 5 : 6; max = max * num / den; attackRoll = factor(attackRoll, num, den); }
            else if (task) { int num = mode.ranged() ? 23 : 7, den = mode.ranged() ? 20 : 6; max = max * num / den; attackRoll = factor(attackRoll, num, den); }
            int demonbane = 0;
            if (target.has("demon"))
            {
                if (gear.weapon.startsWith("arclight") || gear.weapon.startsWith("emberlight")) demonbane = 70;
                else if (gear.weapon.startsWith("silverlight") || gear.weapon.startsWith("darklight")) demonbane = 60;
                else if (gear.weapon.startsWith("bone claws") || gear.weapon.startsWith("burning claws")) demonbane = 5;
                else if (gear.weapon.startsWith("scorching bow")) demonbane = 30;
            }
            if (demonbane > 0)
            { demonbane = demonbane * ("Duke Sucellus".equals(target.name) ? 70 : 100) / 100; max += max * demonbane / 100; attackRoll += factor(attackRoll, demonbane, 100); mechanics.add("Demonbane weapon bonus"); }
            if (target.has("dragon"))
            {
                if (gear.weapon.startsWith("dragon hunter lance")) { max = max * 6 / 5; attackRoll = factor(attackRoll, 6, 5); mechanics.add("Dragon hunter lance bonus"); }
                if (gear.weapon.startsWith("dragon hunter wand")) { max = max * 7 / 5; attackRoll = factor(attackRoll, 7, 4); mechanics.add("Dragon hunter wand bonus"); }
                if (gear.weapon.startsWith("dragon hunter crossbow")) { max = max * 5 / 4; attackRoll = factor(attackRoll, 13, 10); mechanics.add("Dragon hunter crossbow bonus"); }
            }
            if (target.has("golem") && gear.weapon.startsWith("barronite mace")) max = max * 23 / 20;
            if (target.has("golem") && gear.weapon.startsWith("granite hammer")) { max = max * 13 / 10; attackRoll = factor(attackRoll, 13, 10); }
            if (target.has("leafy") && gear.weapon.startsWith("leaf-bladed battleaxe")) max = max * 47 / 40;
            if (target.has("rat") && (gear.weapon.startsWith("bone mace") || gear.weapon.startsWith("bone shortbow"))) max += 10;
            if (mode.melee() && target.has("kalphite") && gear.weapon.startsWith("keris"))
            {
                max = max * (gear.weapon.startsWith("keris partisan of amascut") ? 115 : 133) / 100;
                if (gear.weapon.startsWith("keris partisan of breaching")) attackRoll = factor(attackRoll, 133, 100);
            }
            if (mode.melee() && "crush".equals(mode.type))
            { int inquisitor = (gear.has("inquisitor's great helm") ? 1 : 0) + (gear.has("inquisitor's hauberk") ? 2 : 0) + (gear.has("inquisitor's plateskirt") ? 2 : 0); max = max * (200 + inquisitor) / 200; attackRoll = factor(attackRoll, 200 + inquisitor, 200); }
            if (mode.ranged() && gear.weapon.contains("twisted bow"))
            { int magic = Math.min(250, Math.max(target.magicLevel, target.magicAttackBonus)); max = CurrentMaxHit.twistedBowHit(max, magic, 250); attackRoll = tbowAccuracyRoll(attackRoll, magic); mechanics.add("Twisted bow base-target Magic scaling"); }
            if (gear.weapon.contains("fang")) { min = max * 3 / 20; max -= min; mechanics.add("Fang hit range and stab double-accuracy roll"); }
        }
        if (max < 0 || max > 1000) return Result.unknown("Damage range is outside the bounded normal model");
        if (spec != null)
        {
            int[] range = spec.damageRange(min, max); min = range[0]; max = range[1];
            attackRoll = spec.accuracyRoll(attackRoll);
            mechanics.add("Special energy " + spec.energy + "% · per-attack burst, not sustained DPS");
        }
        String defenceType = mode.ranged() ? "Thrown".equals(weapon.category) ? "light" : "Bow".equals(weapon.category) ? "standard" : "heavy" : mode.type;
        if (spec != null && spec.defenceStyle != null) defenceType = spec.defenceStyle;
        Integer defenceBonus = target.defenceBonuses.get(defenceType);
        if (defenceBonus == null) return Result.unknown("Target defence for this attack type is unknown");
        long defenceRoll = (long) (("magic".equals(defenceType) && !target.magicUsesDefence ? target.magicLevel : target.defenceLevel) + 9) * (defenceBonus + 64);
        boolean fangAccuracy = gear.weapon.contains("fang") && "stab".equals(mode.type);
        double chance = spec != null && spec.guaranteed ? 1 : fangAccuracy ? fangAccuracy(attackRoll, defenceRoll) : normalAccuracy(attackRoll, defenceRoll);
        if (mode.magic() && gear.has("confliction gauntlets") && !gear.twoHanded)
        { double doubled = fangAccuracy(attackRoll, defenceRoll); chance = CombatRollProbability.conflictionAccuracy(chance, doubled); mechanics.add("Confliction gauntlets accuracy"); }
        if (mode.magic() && gear.has("brimstone ring"))
        { double proc = normalAccuracy(attackRoll, factor(defenceRoll, 9, 10)); chance = chance * .75 + proc * .25; mechanics.add("Brimstone ring accuracy mixture"); }
        int armour = "magic".equals(defenceType) ? 0 : target.defenceBonuses.getOrDefault("flat_armour", 0);
        double[] distribution = max == 0 ? new double[] {1} : hitDistribution(chance, min, max, armour);
        if (max > 0 && gear.weapon.contains("sanguinesti staff"))
        { distribution = procDistribution(chance, min, max, armour, .2, 1, 8); max += 8; mechanics.add("Sanguinesti 20% +8 accurate-hit proc"); }
        if (max > 0 && mode.melee() && gear.weapon.contains("scythe"))
        { int splats = Math.min(3, target.size); for (int i = 1; i < splats; ++i) distribution = convolve(distribution, hitDistribution(chance, 0, max / (1 << i), armour)); mechanics.add("Scythe " + splats + " splats for target size " + target.size); }
        if (max > 0 && mode.ranged() && gear.weapon.startsWith("dark bow")) { distribution = convolve(distribution, hitDistribution(chance, min, max, armour)); mechanics.add("Dark bow two-hit attack"); }
        if (max > 0 && mode.melee() && target.has("kalphite") && gear.weapon.startsWith("keris"))
        { distribution = procDistribution(chance, min, max, armour, 1.0 / 51, 3, 0); max *= 3; mechanics.add("Keris 1/51 triple-damage proc"); }
        if (spec != null) distribution = spec.distribution(chance, min, max, armour, target.size, attackRoll, defenceRoll);
        if (inputs != null && inputs.manualSpell && spell != null) mechanics.add("Spell explicitly selected");
        if (gear.weapon.contains("blowpipe")) mechanics.add(inputs.observedDart ? "Darts observed from equipped Check" : "Darts explicitly selected");
        if (candidate && gear.weapon.contains("soulreaper")) mechanics.add("Uses current observed Soulreaper stacks");
        if (plannedSpellbook != null && spell != null) mechanics.add("Planned " + spell.spellbook + " spellbook · equip/unlock requirements checked separately");
        return new Result(chance, min, max, ticks, distribution, target.hitpoints, mode.label() + (spec == null ? "" : " · Special"), mechanics, calculateTtk, spec == null ? null : spec.energy, target.snapshotOnly);
    }

    private static String unsupported(Gear gear, DpsReferenceCatalog.Mode mode, DpsReferenceCatalog.Target target)
    {
        if (!mode.magic())
        { String reason = CurrentMaxHit.unsupportedWeapon(gear.weapon, mode.ranged()); if (reason != null) return reason; }
        if (gear.has("berserker necklace") || gear.has("amulet of avarice") || gear.has("efaritay's aid")
            || gear.has("amulet of the damned") || gear.weapon.contains("craw's") || gear.weapon.contains("webweaver")
            || gear.weapon.contains("thammaron") || gear.weapon.contains("accursed sceptre")
            || gear.weapon.contains("twinflame") || gear.weapon.contains("purging") || gear.weapon.contains("nature's recurve")
            || gear.weapon.contains("verac's flail") || gear.weapon.contains("karil's") || gear.weapon.contains("dharok")
            || gear.weapon.contains("ivandis") || gear.weapon.contains("hallowed") || gear.weapon.contains("sunspear")
            || gear.has("obsidian helmet") && gear.has("obsidian platebody") && gear.has("obsidian platelegs"))
            return "Equipment has a target, charge, set or proc effect outside the normal DPS model";
        if (mode.magic() && gear.contains("tome of ")) return "Tome charge state is not observed";
        if (mode.ranged() && gear.ammoId > 0 && (gear.contains("bolts (e)") || gear.contains("dragon bolts (e)") || gear.contains("seeking")))
            return "Ammunition proc or minimum-hit effects are not modelled";
        if (mode.melee() && gear.weapon.contains("bulwark") && !"Accurate".equals(mode.stance)) return "Bulwark Block mode does not attack";
        return null;
    }
    private static int accuracyPrayer(Client client, DpsReferenceCatalog.Mode mode)
    {
        if (mode.magic())
        { if (client.isPrayerActive(Prayer.AUGURY)) return 125; if (client.isPrayerActive(Prayer.MYSTIC_VIGOUR)) return 120; if (client.isPrayerActive(Prayer.MYSTIC_MIGHT)) return 115; if (client.isPrayerActive(Prayer.MYSTIC_LORE)) return 110; if (client.isPrayerActive(Prayer.MYSTIC_WILL)) return 105; return 100; }
        if (mode.ranged())
        { if (client.isPrayerActive(Prayer.RIGOUR)) return 120; if (client.isPrayerActive(Prayer.DEADEYE)) return 118; if (client.isPrayerActive(Prayer.EAGLE_EYE)) return 115; if (client.isPrayerActive(Prayer.HAWK_EYE)) return 110; if (client.isPrayerActive(Prayer.SHARP_EYE)) return 105; return 100; }
        if (client.isPrayerActive(Prayer.PIETY)) return 120; if (client.isPrayerActive(Prayer.CHIVALRY)) return 115;
        if (client.isPrayerActive(Prayer.INCREDIBLE_REFLEXES)) return 115; if (client.isPrayerActive(Prayer.IMPROVED_REFLEXES)) return 110; if (client.isPrayerActive(Prayer.CLARITY_OF_THOUGHT)) return 105; return 100;
    }
    private static int strengthPrayer(Client client, boolean ranged)
    {
        if (client.isPrayerActive(ranged ? Prayer.RIGOUR : Prayer.PIETY)) return 123;
        if (client.isPrayerActive(ranged ? Prayer.DEADEYE : Prayer.CHIVALRY)) return 118;
        if (client.isPrayerActive(ranged ? Prayer.EAGLE_EYE : Prayer.ULTIMATE_STRENGTH)) return 115;
        if (client.isPrayerActive(ranged ? Prayer.HAWK_EYE : Prayer.SUPERHUMAN_STRENGTH)) return 110;
        if (client.isPrayerActive(ranged ? Prayer.SHARP_EYE : Prayer.BURST_OF_STRENGTH)) return 105; return 100;
    }
    private static boolean voidSet(Gear gear, String helm)
    { return gear.has(helm) && gear.has("void knight gloves") && (gear.has("void knight top") || gear.has("elite void top")) && (gear.has("void knight robe") || gear.has("elite void robe")); }
    private static int crystalPieces(Gear gear)
    { return (gear.has("crystal helm") ? 1 : 0) + (gear.has("crystal legs") ? 2 : 0) + (gear.has("crystal body") ? 3 : 0); }
    private static Boolean sunfireAvailable(Client client)
    {
        net.runelite.api.ItemContainer inventory = client.getItemContainer(net.runelite.api.gameval.InventoryID.INV);
        if (inventory == null || inventory.getItems() == null) return null;
        boolean pouch = false;
        if (inventory != null && inventory.getItems() != null)
            for (Item item : inventory.getItems()) if (item != null && item.getQuantity() > 0)
            {
                if (item.getId() == ItemID.SUNFIRERUNE) return true;
                if (item.getId() == ItemID.BH_RUNE_POUCH || item.getId() == ItemID.BH_RUNE_POUCH_TROUVER
                    || item.getId() == ItemID.DIVINE_RUNE_POUCH || item.getId() == ItemID.DIVINE_RUNE_POUCH_TROUVER) pouch = true;
            }
        if (!pouch) return false;
        net.runelite.api.EnumComposition runes = client.getEnum(net.runelite.api.EnumID.RUNEPOUCH_RUNE);
        if (runes == null) return null;
        int[] types = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
            VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
        int[] amounts = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
            VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6};
        for (int i = 0; i < types.length; ++i) if (client.getVarbitValue(amounts[i]) > 0)
        {
            int type = client.getVarbitValue(types[i]); if (type <= 0) return null;
            int id = runes.getIntValue(type); if (id <= 0) return null;
            if (id == ItemID.SUNFIRERUNE) return true;
        }
        return false;
    }
    static long factor(long value, int numerator, int denominator) { return value * numerator / denominator; }
    static long tbowAccuracyRoll(long attack, int magic)
    { int value = Math.max(0, Math.min(250, magic)); int term = 3 * value / 10 - 100; int percent = Math.max(0, Math.min(140, 140 + (3 * value - 10) / 100 - term * term / 100)); return attack * percent / 100; }

    static double normalAccuracy(long attack, long defence)
    {
        return CombatRollProbability.normalAccuracy(attack, defence);
    }
    static double fangAccuracy(long attack, long defence)
    {
        // Retain the calculator's signed-roll policy pending native-mechanics verification.
        if (attack > 0 && defence < -2) return normalAccuracy(attack, defence);
        return CombatRollProbability.fangAccuracy(attack, defence);
    }
    static double[] hitDistribution(double accuracy, int minimum, int maximum, int armour)
    {
        if (maximum < 0 || minimum < 0 || minimum > maximum) throw new IllegalArgumentException("Invalid damage range");
        double[] probabilities = new double[Math.max(1, maximum - armour) + 1]; probabilities[0] = 1 - accuracy;
        double each = accuracy / (maximum - minimum + 1);
        for (int raw = minimum; raw <= maximum; ++raw) probabilities[Math.max(0, Math.max(1, raw) - armour)] += each;
        return probabilities;
    }
    static double[] convolve(double[] first, double[] second)
    { double[] result = new double[first.length + second.length - 1]; for (int i = 0; i < first.length; ++i) for (int j = 0; j < second.length; ++j) result[i + j] += first[i] * second[j]; return result; }
    static double mean(double[] probabilities)
    { double mean = 0; for (int damage = 1; damage < probabilities.length; ++damage) mean += damage * probabilities[damage]; return mean; }
    static double[] procDistribution(double accuracy, int minimum, int maximum, int armour, double procChance, int multiplier, int additive)
    {
        double[] result = new double[Math.max(1, maximum * multiplier + additive - armour) + 1]; result[0] = 1 - accuracy;
        double each = accuracy / (maximum - minimum + 1);
        for (int raw = minimum; raw <= maximum; ++raw)
        {
            result[Math.max(0, Math.max(1, raw) - armour)] += each * (1 - procChance);
            result[Math.max(0, Math.max(1, raw * multiplier + additive) - armour)] += each * procChance;
        }
        return result;
    }
    /** Expected attack cycles including overkill and misses. The first attack cycle is included in displayed TTK. */
    static double expectedAttacks(double[] probabilities, int hitpoints)
    {
        if (hitpoints <= 0) return 0;
        double positive = 1 - probabilities[0]; if (positive <= 1e-12) return Double.POSITIVE_INFINITY;
        double[] expectations = new double[hitpoints + 1];
        for (int hp = 1; hp <= hitpoints; ++hp)
        {
            double previous = 0;
            for (int damage = 1; damage < probabilities.length && damage < hp; ++damage) previous += probabilities[damage] * expectations[hp - damage];
            expectations[hp] = (1 + previous) / positive;
        }
        return expectations[hitpoints];
    }
}
