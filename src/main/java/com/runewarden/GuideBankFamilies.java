/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Banked alternatives selected by their actual game definitions and cached equipment stats. */
final class GuideBankFamilies
{
    static final Set<String> DYNAMIC = Set.of("food", "healing-potions", "prayer-restoration", "run-energy",
        "antipoison", "antifire", "dragonfire-protection", "combat-potions", "teleports", "runes",
        "melee-equipment", "ranged-equipment", "magic-equipment", "all-equipment", "ammunition", "logs",
        "ores", "bars", "herbs", "grimy-herbs", "unfinished-potions", "seeds", "saplings", "bones", "ashes",
        "ensouled-heads", "nails", "essence", "pouches", "skilling-outfit");

    private GuideBankFamilies() { }

    static boolean matches(Collection<String> families, int id, ItemManager manager, String skill)
    {
        if (families.isEmpty()) return false;
        ItemComposition definition = manager.getItemComposition(id);
        if (definition == null || definition.getName() == null) return false;
        String name = GuideItemResolver.normalize(definition.getName());
        for (String family : families)
        {
            if (matches(family, name, definition, manager.getItemStats(id), skill)) return true;
        }
        return false;
    }

    static boolean matches(String family, String name, ItemComposition definition, ItemStats stats, String skill)
    {
        switch (family)
        {
            case "food":
                return hasAction(definition, "Eat") && !name.contains("poison") && !name.contains("rotten")
                    && !name.contains("burnt") && !name.contains("poorly cooked") && !name.contains("rock cake")
                    && !name.contains("nightshade") && !name.startsWith("blighted ");
            case "healing-potions":
                return starts(name, "saradomin brew(", "guthix rest(", "ambrosia(", "nectar(");
            case "prayer-restoration":
                return starts(name, "prayer potion(", "super restore(", "sanfew serum(",
                    "prayer regeneration potion(", "moonlight moth", "moonlight potion(", "ancient brew(", "forgotten brew(");
            case "run-energy":
                return starts(name, "energy potion(", "super energy(", "stamina potion(", "extreme energy potion(",
                    "extended stamina potion(") || name.equals("strange fruit") || name.equals("purple sweets");
            case "antipoison":
                return name.contains("antipoison") || name.contains("antidote") || name.contains("anti-venom") || name.startsWith("sanfew serum(");
            case "antifire": return name.contains("antifire");
            case "dragonfire-protection":
                return name.contains("antifire") || name.contains("anti-dragon shield") || name.contains("dragonfire shield")
                    || name.contains("dragonfire ward") || name.contains("ancient wyvern shield");
            case "combat-potions":
                return starts(name, "attack potion(", "strength potion(", "defence potion(", "combat potion(", "super attack(",
                    "super strength(", "super defence(", "super combat potion(", "ranging potion(", "magic potion(",
                    "bastion potion(", "battlemage potion(", "divine super ", "divine ranging potion(", "divine magic potion(",
                    "divine bastion potion(", "divine battlemage potion(", "zamorak brew(", "ancient brew(", "forgotten brew(")
                    || name.equals("imbued heart") || name.equals("saturated heart");
            case "teleports":
                return name.contains("teleport") || name.contains("ring of dueling") || name.contains("games necklace")
                    || name.contains("amulet of glory") || name.contains("skills necklace") || name.contains("combat bracelet")
                    || name.contains("necklace of passage") || name.equals("royal seed pod") || name.equals("ectophial")
                    || name.contains("construction cape") || name.contains("max cape") || name.contains("achievement diary cape")
                    || name.contains("xeric's talisman") || name.equals("drakan's medallion") || name.equals("stony basalt") || name.equals("icy basalt");
            case "runes": return name.endsWith(" rune") || name.contains("rune pouch");
            case "ammunition": return stats != null && stats.getEquipment() != null && stats.getEquipment().getSlot() == 13
                || name.endsWith(" bolts") || name.endsWith(" arrow") || name.endsWith(" arrows") || name.endsWith(" dart")
                || name.endsWith(" darts") || name.endsWith(" javelin") || name.endsWith(" chinchompa") || name.equals("cannonball");
            case "logs": return name.endsWith(" logs") || name.equals("logs") || name.endsWith(" log");
            case "ores": return name.endsWith(" ore") || name.equals("coal");
            case "bars": return Set.of("bronze bar", "iron bar", "steel bar", "silver bar", "gold bar", "mithril bar",
                "adamantite bar", "runite bar", "lovakite bar", "lead bar", "cupronickel bar").contains(name);
            case "grimy-herbs": return name.startsWith("grimy ");
            case "herbs": return name.startsWith("grimy ") || Set.of("guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed",
                "toadflax", "irit leaf", "avantoe", "kwuarm", "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol", "huasca").contains(name);
            case "unfinished-potions": return name.endsWith(" potion(unf)");
            case "seeds": return name.endsWith(" seed") || name.equals("seaweed spore");
            case "saplings": return name.endsWith(" sapling");
            case "bones": return name.endsWith("bones") && !name.startsWith("long ") && !name.startsWith("curved ");
            case "ashes": return name.endsWith(" ashes") && !name.equals("ashes");
            case "ensouled-heads": return name.startsWith("ensouled ") && name.endsWith(" head");
            case "nails": return name.endsWith(" nails") || name.equals("iron nails");
            case "essence": return Set.of("rune essence", "pure essence", "daeyalt essence", "guardian essence", "dark essence block", "dark essence fragments").contains(name);
            case "pouches": return Set.of("small pouch", "medium pouch", "large pouch", "giant pouch", "colossal pouch").contains(name);
            case "skilling-outfit":
                if (skill == null) return false;
                try
                {
                    String key = skill.equalsIgnoreCase("runecrafting") ? "RUNECRAFT" : skill.toUpperCase(Locale.ROOT);
                    SkillingOutfits.Outfit outfit = SkillingOutfits.OUTFITS.get(Skill.valueOf(key));
                    if (outfit != null) for (SkillingOutfits.Piece piece : outfit.pieces) if (piece.matches(name)) return true;
                }
                catch (IllegalArgumentException ignored) { }
                return false;
            case "all-equipment": case "melee-equipment": case "ranged-equipment": case "magic-equipment":
                return equipment(family, stats, definition);
            default: return false;
        }
    }

    private static boolean equipment(String family, ItemStats stats, ItemComposition definition)
    {
        ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
        if (equipment == null) return false;
        boolean melee = equipment.getAstab() > 0 || equipment.getAslash() > 0 || equipment.getAcrush() > 0 || equipment.getStr() > 0;
        boolean ranged = equipment.getArange() > 0 || equipment.getRstr() > 0;
        boolean magic = equipment.getAmagic() > 0 || equipment.getMdmg() > 0;
        boolean defence = equipment.getDstab() > 0 || equipment.getDslash() > 0 || equipment.getDcrush() > 0
            || equipment.getDmagic() > 0 || equipment.getDrange() > 0;
        boolean neutral = !melee && !ranged && !magic && (defence || equipment.getPrayer() > 0);
        switch (family)
        {
            case "melee-equipment": return melee || neutral || (defence && equipment.getAmagic() <= 0 && equipment.getArange() <= 0);
            case "ranged-equipment": return ranged || neutral;
            case "magic-equipment": return magic || neutral;
            default: return melee || ranged || magic || defence || equipment.getPrayer() > 0;
        }
    }

    private static boolean hasAction(ItemComposition definition, String action)
    { return definition != null && definition.getInventoryActions() != null && Arrays.asList(definition.getInventoryActions()).contains(action); }
    private static boolean starts(String text, String... prefixes)
    { for (String prefix : prefixes) if (text.startsWith(prefix)) return true; return false; }
}
