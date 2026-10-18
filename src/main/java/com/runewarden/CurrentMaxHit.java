/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import static net.runelite.api.gameval.VarPlayerID.*;
import static net.runelite.api.gameval.VarbitID.*;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Base normal-attack maximum from current native equipment, levels, style and known autocast spell. */
final class CurrentMaxHit
{
    static final class Reading
    {
        public final Integer maxHit;
        public final Integer minHit;
        public final String scope;
        public final String reason;
        private Reading(Integer maxHit, String scope, String reason)
        { this(maxHit, maxHit == null ? null : 0, scope, reason); }
        private Reading(Integer maxHit, Integer minHit, String scope, String reason)
        { this.maxHit = maxHit; this.minHit = minHit; this.scope = scope; this.reason = reason; }
        public String text() { return maxHit == null ? "—" : Integer.toString(maxHit); }
    }

    private CurrentMaxHit() { }

    static Reading read(Client client, ItemManager items)
    {
        if (client == null || items == null || client.getGameState() != GameState.LOGGED_IN)
            return unknown("Log in to read equipment");
        ItemContainer container = worn(client);
        if (container == null || container.getItems() == null) return unknown("Equipment is not available");
        Item[] wornItems = container.getItems();
        int weaponSlot = EquipmentInventorySlot.WEAPON.getSlotIdx();
        Item wornWeapon = wornItems.length <= weaponSlot ? null : wornItems[weaponSlot];
        if ((wornWeapon == null || wornWeapon.getId() < 0 || wornWeapon.getQuantity() <= 0)
            && client.getLocalPlayer() != null && client.getLocalPlayer().getPlayerComposition() != null
            && client.getLocalPlayer().getPlayerComposition().getEquipmentId(net.runelite.api.kit.KitType.WEAPON) > 0)
            return unknown("Worn equipment is updating");
        int stanceIndex = client.getVarpValue(COM_MODE);
        String style = style(client, stanceIndex);
        if (style == null) return unknown("Attack style is not available");
        return readEquipment(client, items, wornItems, style, stanceIndex);
    }

    private static Reading readEquipment(Client client, ItemManager items, Item[] equipment, String style, int stanceIndex)
    {
        if (client == null || items == null || client.getGameState() != GameState.LOGGED_IN)
            return unknown("Log in to read combat levels");
        if (equipment == null || style == null) return unknown("Equipment or attack style is not available");
        List<String> names = new ArrayList<>();
        String weapon = "";
        int strength = 0, rangedStrength = 0, ammoStrength = 0, defenceSum = 0, magicDamage = 0;
        boolean ammunition = false;
        for (int slot = 0; slot < equipment.length; slot++)
        {
            Item item = equipment[slot];
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0) continue;
            ItemComposition definition = items.getItemComposition(item.getId());
            String name = definition == null || definition.getName() == null ? "" : definition.getName().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) return unknown("Equipment names are not available");
            names.add(name);
            if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx()) weapon = name;
            if (slot == EquipmentInventorySlot.AMMO.getSlotIdx()) ammunition = true;
            ItemStats stats = items.getItemStats(item.getId());
            if (stats == null) return unknown("Equipment bonuses are still loading");
            ItemEquipmentStats bonuses = stats.getEquipment();
            if (bonuses == null)
            {
                if (stats.isEquipable()) continue;
                return unknown("Equipment bonuses are not available");
            }
            strength += bonuses.getStr();
            rangedStrength += bonuses.getRstr();
            magicDamage += Math.round(bonuses.getMdmg() * 10); // equipment API uses percent, including 0.5% pieces
            defenceSum += bonuses.getDstab() + bonuses.getDslash() + bonuses.getDcrush() + bonuses.getDrange();
            if (slot == EquipmentInventorySlot.AMMO.getSlotIdx()) ammoStrength = bonuses.getRstr();
        }
        boolean casting = style.contains("casting") || (stanceIndex >= 4 && "defensive".equals(style));
        boolean powered = poweredBase(weapon, Math.max(0, client.getBoostedSkillLevel(Skill.MAGIC))) != null;
        if (casting || powered)
            return magic(client, weapon, names, magicDamage, casting);

        boolean ranged = "ranging".equals(style) || "longrange".equals(style);
        if (!ranged && !"accurate".equals(style) && !"aggressive".equals(style)
            && !"controlled".equals(style) && !"defensive".equals(style)) return unknown("Unsupported attack style");
        String unsupported = unsupportedWeapon(weapon, ranged);
        if (unsupported != null) return unknown(unsupported);
        if (has(names, "inquisitor's") || has(names, "berserker necklace")
            || (has(names, "dharok's greataxe") && has(names, "dharok's helm")
                && has(names, "dharok's platebody") && has(names, "dharok's platelegs")))
            return unknown("Equipment damage multiplier requires a dedicated formula");
        Skill skill = ranged ? Skill.RANGED : Skill.STRENGTH;
        if (client.getRealSkillLevel(skill) < 1) return unknown("Combat levels are not available");
        int boosted = Math.max(0, client.getBoostedSkillLevel(skill));
        int stance = ranged ? (stanceIndex == 0 ? 3 : 0) : "aggressive".equals(style) ? 3 : "controlled".equals(style) ? 1 : 0;
        int effective = effectiveLevel(boosted, prayerPercent(client, ranged), stance);
        if (!ranged && weapon.contains("soulreaper"))
            effective += boosted * Math.max(0, Math.min(5, client.getVarpValue(SOULREAPER_STACKS))) * 6 / 100;
        if (fullVoid(names, ranged ? "void ranger helm" : "void melee helm"))
        {
            boolean elite = ranged && has(names, "elite void top") && has(names, "elite void robe");
            effective = effective * (elite ? 9 : 11) / (elite ? 8 : 10);
        }
        boolean crystal = weapon.contains("crystal bow") || weapon.contains("faerdhinen");
        boolean blowpipe = weapon.contains("blowpipe");
        if (weapon.contains("twisted bow")) return unknown("Target Magic is not observed for Twisted bow");
        if (ranged)
        {
            boolean selfAmmo = crystal || blowpipe || containsAny(weapon, "dart", "knife", "knives", "thrownaxe");
            if (selfAmmo) rangedStrength -= ammoStrength;
            else if (!ammunition) return unknown("Ammunition is not equipped");
            if (blowpipe) return unknown("Loaded blowpipe darts are not observed");
        }
        else if (weapon.contains("bulwark"))
        {
            if ("defensive".equals(style)) return unknown("Bulwark Block mode does not attack");
            strength += bulwarkStrength(defenceSum);
        }
        int hit = baseHit(effective, ranged ? rangedStrength : strength);
        if (crystal || (!ranged && has(names, "crystal blessing"))) hit = hit * (40 + crystalPieces(names)) / 40;
        int minimum = 0;
        if (!ranged && weapon.contains("fang")) { minimum = hit * 3 / 20; hit -= minimum; }
        String scope = ranged ? "BASE RANGED" : "BASE MELEE";
        String reason = "Normal attack before target bonuses, special attacks and ammunition procs";
        if (!ranged && weapon.contains("scythe"))
        {
            scope = "BASE FIRST HIT";
            reason = "Scythe first splat " + hit + "; further splats " + hit / 2 + " / " + hit / 4
                + " require target size 2 / 3; before target bonuses";
        }
        return new Reading(hit, minimum, scope, reason);
    }

    private static Reading magic(Client client, String weapon, List<String> names, int damage, boolean casting)
    {
        if (client.getRealSkillLevel(Skill.MAGIC) < 1) return unknown("Magic level is not available");
        int level = Math.max(0, client.getBoostedSkillLevel(Skill.MAGIC));
        CombatSpellCatalog.Spell spell = casting
            ? CombatSpellCatalog.fromSpellObject(client.getVarpValue(AUTOCAST_SPELL_OBJ)) : null;
        Integer base = spell == null ? poweredBase(weapon, level) : Integer.valueOf(spell.maxHit(level));
        if (base == null) return unknown("Autocast spell is not observed or supported");
        if (containsAny(weapon, "uncharged", "(inactive)", "(empty)"))
            return unknown("Weapon is uncharged");
        if (spell != null && spell.name.equals("Magic Dart") && weapon.startsWith("slayer's staff (e)"))
            return unknown("Enhanced Magic Dart needs verified Slayer task state");
        if (spell != null && spell.name.endsWith("Bolt") && has(names, "chaos gauntlets")) base += 3;
        if (spell != null && isGodSpell(spell.name) && client.getVarpValue(MAGEARENA_CHARGE) > 0)
        {
            String deity = spell.name.equals("Claws of Guthix") ? "guthix" : spell.name.equals("Saradomin Strike") ? "saradomin" : "zamorak";
            for (String name : names) if (name.contains(deity) && name.endsWith("cape")) { base += 10; break; }
        }
        if (spell != null && spell.element != null && has(names, "twinflame staff"))
            return unknown("Twinflame secondary hit requires a dedicated formula");
        if (spell != null && spell.element != null && (has(names, "elemental amulet") || has(names, "amulet of " + spell.element))) base += 2;
        boolean shadow = spell == null && weapon.contains("tumeken's shadow");
        if (shadow) return unknown("Shadow damage needs verified raid context");
        if (fullVoid(names, "void mage helm") && has(names, "elite void top") && has(names, "elite void robe")) damage += 50;
        if (spell != null && "ancient".equals(spell.spellbook))
            for (String name : names) if (name.startsWith("virtus ")) damage += 30;
        if (spell != null && "standard".equals(spell.spellbook) && has(names, "smoke")) damage += 100;
        damage += magicPrayerBonus(client);
        int hit = Math.max(0, base * (1000 + damage) / 1000);
        if (spell != null && spell.element != null && has(names, "tome of " + spell.element))
            return unknown("Tome charge state is not observed; remove it for a base estimate");
        if (weapon.equals("dawnbringer") && spell == null) hit = Math.max(2, hit / 2);
        boolean sanguinesti = weapon.contains("sanguinesti staff");
        if (sanguinesti) hit += 8;
        String scope = "BASE MAGIC";
        String reason = spell != null ? spell.name : weapon;
        reason += "; before elemental weakness, Slayer, salve, special attacks and target multipliers";
        if (sanguinesti) reason += "; includes maximum of the 20% normal +8 damage proc";
        return new Reading(hit, scope, reason);
    }

    static Integer poweredBase(String weapon, int level)
    {
        if (weapon.startsWith("trident of the seas")) return Math.max(1, level / 3 - 5);
        if (weapon.startsWith("trident of the swamp")) return Math.max(1, level / 3 - 2);
        if (weapon.contains("sanguinesti staff")) return Math.max(1, level / 3);
        if (weapon.startsWith("tumeken's shadow")) return Math.max(1, level / 3 + 1);
        if (weapon.startsWith("eye of ayak")) return Math.max(1, level / 3 - 6);
        if (weapon.startsWith("warped sceptre")) return Math.max(1, (8 * level + 96) / 37);
        if (weapon.startsWith("accursed sceptre") && !weapon.startsWith("accursed sceptre (a)")) return Math.max(1, level / 3 - 6);
        if (weapon.startsWith("thammaron's sceptre") && !weapon.startsWith("thammaron's sceptre (a)")) return Math.max(1, level / 3 - 8);
        if (weapon.equals("dawnbringer")) return Math.max(1, level / 3 - 2);
        if (weapon.equals("bone staff")) return Math.max(1, level / 3 - 5) + 10;
        if (weapon.startsWith("crystal staff") || weapon.startsWith("corrupted staff"))
        {
            if (weapon.contains("(basic)")) return 23;
            if (weapon.contains("(attuned)")) return 31;
            if (weapon.contains("(perfected)")) return 39;
        }
        return null;
    }

    static int effectiveLevel(int boosted, int prayerPercent, int stanceBonus)
    {
        int prayed = Math.max(0, boosted) * prayerPercent / 100;
        if (prayerPercent == 105 && boosted <= 20) prayed = boosted + 1;
        return prayed + stanceBonus + 8;
    }

    static int baseHit(int effective, int strengthBonus)
    { return Math.max(0, (int) (((long) effective * (strengthBonus + 64) + 320) / 640)); }

    static int bulwarkStrength(int defenceSum) { return Math.max(0, (defenceSum - 800) / 12 - 38); }

    private static int prayerPercent(Client client, boolean ranged)
    {
        if (client.isPrayerActive(ranged ? Prayer.RIGOUR : Prayer.PIETY)) return 123;
        if (client.isPrayerActive(ranged ? Prayer.DEADEYE : Prayer.CHIVALRY)) return 118;
        if (client.isPrayerActive(ranged ? Prayer.EAGLE_EYE : Prayer.ULTIMATE_STRENGTH)) return 115;
        if (client.isPrayerActive(ranged ? Prayer.HAWK_EYE : Prayer.SUPERHUMAN_STRENGTH)) return 110;
        if (client.isPrayerActive(ranged ? Prayer.SHARP_EYE : Prayer.BURST_OF_STRENGTH)) return 105;
        return 100;
    }

    private static int magicPrayerBonus(Client client)
    {
        if (client.isPrayerActive(Prayer.AUGURY)) return 40;
        if (client.isPrayerActive(Prayer.MYSTIC_VIGOUR)) return 30;
        if (client.isPrayerActive(Prayer.MYSTIC_MIGHT)) return 20;
        if (client.isPrayerActive(Prayer.MYSTIC_LORE)) return 10;
        return 0;
    }

    private static boolean isGodSpell(String name)
    { return name.equals("Claws of Guthix") || name.equals("Saradomin Strike") || name.equals("Flames of Zamorak"); }

    private static boolean fullVoid(List<String> names, String helm)
    {
        return has(names, helm) && has(names, "void knight gloves")
            && (has(names, "void knight top") || has(names, "elite void top"))
            && (has(names, "void knight robe") || has(names, "elite void robe"));
    }

    private static int crystalPieces(List<String> names)
    { return (has(names, "crystal helm") ? 1 : 0) + (has(names, "crystal legs") ? 2 : 0) + (has(names, "crystal body") ? 3 : 0); }

    private static boolean has(List<String> names, String prefix)
    { for (String name : names) if (name.startsWith(prefix)) return true; return false; }

    private static boolean containsAny(String text, String... fragments)
    {
        for (String fragment : fragments) if (text.contains(fragment)) return true;
        return false;
    }

    static String unsupportedWeapon(String weapon, boolean ranged)
    {
        if (containsAny(weapon, "uncharged", "(inactive)", "(empty)")) return "Weapon is uncharged";
        if (ranged)
        {
            if (containsAny(weapon, "atlatl", "salamander", "lizard", "tonalztics", "craw's", "webweaver", "ogre", "chinchompa"))
                return "Weapon needs ammunition, target or damage-specific calculation";
            if (!containsAny(weapon, "shortbow", "longbow", "crossbow", "dart", "knife", "knives", "thrownaxe",
                "ballista", "twisted bow", "blowpipe", "crystal bow", "faerdhinen"))
                return "Ranged weapon formula is not supported";
        }
        else if (containsAny(weapon, "colossal blade", "dharok's greataxe", "torag's hammers", "macuahuitl", "temotli",
            "sulphur blades", "ivandis", "blisterwood", "salamander", "lizard"))
            return "Weapon needs a dedicated damage formula";
        return null;
    }

    static ItemContainer worn(Client client)
    {
        ItemContainer worn = client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
        return worn != null ? worn : client.getItemContainer(InventoryID.EQUIPMENT);
    }

    static String attackStyle(Client client) { return style(client, client.getVarpValue(COM_MODE)); }

    private static String style(Client client, int index)
    {
        if (index < 0) return null;
        if (index == 4) index += Math.max(0, Math.min(1, client.getVarbitValue(AUTOCAST_DEFMODE)));
        int type = client.getVarbitValue(COMBAT_WEAPON_CATEGORY);
        EnumComposition categories = client.getEnum(EnumID.WEAPON_STYLES);
        if (categories == null) return null;
        int styleEnum = categories.getIntValue(type);
        if (styleEnum < 0)
        {
            if (type == 22)
            {
                String[] blueMoon = {"accurate", "aggressive", null, "defensive", "casting", "defensive"};
                return index < blueMoon.length ? blueMoon[index] : null;
            }
            if (type == 30)
            {
                String[] partisan = {"accurate", "aggressive", "aggressive", "defensive"};
                return index < partisan.length ? partisan[index] : null;
            }
            return null;
        }
        EnumComposition styles = client.getEnum(styleEnum);
        if (styles == null || styles.getIntVals() == null || index >= styles.getIntVals().length) return null;
        StructComposition entry = client.getStructComposition(styles.getIntVals()[index]);
        if (entry == null) return null;
        String name = entry.getStringValue(ParamID.ATTACK_STYLE_NAME);
        return name == null ? null : name.toLowerCase(Locale.ROOT);
    }

    private static Reading unknown(String reason) { return new Reading(null, "MAX HIT", reason); }
}
