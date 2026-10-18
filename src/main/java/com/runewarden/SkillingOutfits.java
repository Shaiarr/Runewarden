/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/** Match actual equipped item definitions, including recolours and combined gloves. */
final class SkillingOutfits
{
    static final class Piece
    {
        final String label;
        final int image;
        final int benefitVarbit;
        final String[] names;
        Piece(String label, int image, int benefitVarbit, String... names)
        { this.label = label; this.image = image; this.benefitVarbit = benefitVarbit; this.names = names; }
        boolean matches(String raw)
        {
            String name = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('’', '\'');
            for (String candidate : names)
            {
                if (name.equals(candidate) || name.startsWith(candidate + " (") ) { return true; }
            }
            return false;
        }
    }
    static final class Outfit
    {
        final String name;
        final List<Piece> pieces;
        Outfit(String name, Piece... pieces) { this.name = name; this.pieces = List.of(pieces); }
    }
    private static Piece piece(String label, int image, String... names) { return new Piece(label, image, -1, names); }
    private static Piece benefit(String label, int image, int varbit, String... names) { return new Piece(label, image, varbit, names); }
    static final Map<Skill, Outfit> OUTFITS;
    static
    {
        Map<Skill, Outfit> map = new EnumMap<>(Skill.class);
        map.put(Skill.MINING, new Outfit("Prospector kit",
            benefit("Helmet", ItemID.PROSPECTOR_HELMET, VarbitID.WORN_ITEMS_PROSPECTOR_HEAD, "prospector helmet", "golden prospector helmet"),
            benefit("Jacket", ItemID.PROSPECTOR_JACKET, VarbitID.WORN_ITEMS_PROSPECTOR_BODY, "prospector jacket", "golden prospector jacket"),
            benefit("Legs", ItemID.PROSPECTOR_LEGS, VarbitID.WORN_ITEMS_PROSPECTOR_LEGS, "prospector legs", "golden prospector legs"),
            benefit("Boots", ItemID.PROSPECTOR_BOOTS, VarbitID.WORN_ITEMS_PROSPECTOR_BOOTS, "prospector boots", "golden prospector boots")));
        map.put(Skill.WOODCUTTING, new Outfit("Lumberjack / Forestry",
            benefit("Hat", ItemID.LUMBERJACK_HAT, VarbitID.WORN_ITEMS_LUMBERJACK_HEAD, "lumberjack hat", "forestry hat"),
            benefit("Top", ItemID.LUMBERJACK_TOP, VarbitID.WORN_ITEMS_LUMBERJACK_BODY, "lumberjack top", "forestry top"),
            benefit("Legs", ItemID.LUMBERJACK_LEGS, VarbitID.WORN_ITEMS_LUMBERJACK_LEGS, "lumberjack legs", "forestry legs"),
            benefit("Boots", ItemID.LUMBERJACK_BOOTS, VarbitID.WORN_ITEMS_LUMBERJACK_BOOTS, "lumberjack boots", "forestry boots")));
        map.put(Skill.FISHING, new Outfit("Angler / Spirit angler",
            piece("Hat", ItemID.ANGLER_HAT, "angler hat", "spirit angler headband", "spirit angler hat", "krill angler hat"),
            piece("Top", ItemID.ANGLER_TOP, "angler top", "spirit angler top"),
            piece("Waders", ItemID.ANGLER_WADERS, "angler waders", "spirit angler waders", "spirit angler legs"),
            piece("Boots", ItemID.ANGLER_BOOTS, "angler boots", "spirit angler boots")));
        map.put(Skill.FARMING, new Outfit("Farmer's outfit",
            piece("Strawhat", ItemID.FARMERS_STRAWHAT, "farmer's strawhat"),
            piece("Top", ItemID.FARMERS_JACKET, "farmer's jacket", "farmer's shirt"),
            piece("Trousers", ItemID.FARMERS_BORO_TROUSERS, "farmer's boro trousers"),
            piece("Boots", ItemID.FARMERS_BOOTS, "farmer's boots")));
        map.put(Skill.FIREMAKING, new Outfit("Pyromancer outfit",
            piece("Hood", ItemID.PYROMANCER_HOOD, "pyromancer hood"), piece("Top", ItemID.PYROMANCER_GARB, "pyromancer garb"),
            piece("Robe", ItemID.PYROMANCER_ROBE, "pyromancer robe"), piece("Boots", ItemID.PYROMANCER_BOOTS, "pyromancer boots")));
        map.put(Skill.CONSTRUCTION, new Outfit("Carpenter's outfit",
            benefit("Helmet", ItemID.CARPENTERS_HELMET, VarbitID.WORN_ITEMS_CARPENTER_HEAD, "carpenter's helmet"),
            benefit("Shirt", ItemID.CARPENTERS_SHIRT, VarbitID.WORN_ITEMS_CARPENTER_BODY, "carpenter's shirt"),
            benefit("Trousers", ItemID.CARPENTERS_TROUSERS, VarbitID.WORN_ITEMS_CARPENTER_LEGS, "carpenter's trousers"),
            benefit("Boots", ItemID.CARPENTERS_BOOTS, VarbitID.WORN_ITEMS_CARPENTER_BOOTS, "carpenter's boots")));
        map.put(Skill.SMITHING, new Outfit("Smiths' uniform",
            piece("Tunic", ItemID.SMITHS_TUNIC, "smiths' tunic"), piece("Trousers", ItemID.SMITHS_TROUSERS, "smiths' trousers"),
            piece("Gloves", ItemID.SMITHS_GLOVES, "smiths' gloves"), piece("Boots", ItemID.SMITHS_BOOTS, "smiths' boots")));
        map.put(Skill.RUNECRAFT, new Outfit("Raiments of the Eye",
            benefit("Hat", ItemID.HAT_OF_THE_EYE, VarbitID.WORN_ITEMS_RC_OUTFIT_HAT, "hat of the eye", "hat of the eye red", "hat of the eye blue", "hat of the eye green"),
            benefit("Robe top", ItemID.ROBE_TOP_OF_THE_EYE, VarbitID.WORN_ITEMS_RC_OUTFIT_TOP, "robe top of the eye", "robe top of the eye red", "robe top of the eye blue", "robe top of the eye green"),
            benefit("Robe bottoms", ItemID.ROBE_BOTTOMS_OF_THE_EYE, VarbitID.WORN_ITEMS_RC_OUTFIT_LEGS, "robe bottoms of the eye", "robe bottoms of the eye red", "robe bottoms of the eye blue", "robe bottoms of the eye green"),
            benefit("Boots", ItemID.BOOTS_OF_THE_EYE, VarbitID.WORN_ITEMS_RC_OUTFIT_BOOTS, "boots of the eye")));
        map.put(Skill.AGILITY, new Outfit("Graceful outfit",
            benefit("Hood", ItemID.GRACEFUL_HOOD, VarbitID.WORN_ITEMS_GRACEFUL_HOOD, "graceful hood"),
            benefit("Top", ItemID.GRACEFUL_TOP, VarbitID.WORN_ITEMS_GRACEFUL_TORSO, "graceful top"),
            benefit("Legs", ItemID.GRACEFUL_LEGS, VarbitID.WORN_ITEMS_GRACEFUL_LEGS, "graceful legs"),
            benefit("Gloves", ItemID.GRACEFUL_GLOVES, VarbitID.WORN_ITEMS_GRACEFUL_GLOVES, "graceful gloves"),
            benefit("Boots", ItemID.GRACEFUL_BOOTS, VarbitID.WORN_ITEMS_GRACEFUL_BOOTS, "graceful boots"),
            benefit("Cape", ItemID.GRACEFUL_CAPE, VarbitID.WORN_ITEMS_GRACEFUL_CAPE, "graceful cape")));
        map.put(Skill.PRAYER, new Outfit("Zealot's robes",
            benefit("Helm", ItemID.ZEALOTS_HELM, VarbitID.WORN_ITEMS_ZEALOT_HEAD, "zealot's helm"),
            benefit("Robe top", ItemID.ZEALOTS_ROBE_TOP, VarbitID.WORN_ITEMS_ZEALOT_BODY, "zealot's robe top"),
            benefit("Robe bottom", ItemID.ZEALOTS_ROBE_BOTTOM, VarbitID.WORN_ITEMS_ZEALOT_LEGS, "zealot's robe bottom"),
            benefit("Boots", ItemID.ZEALOTS_BOOTS, VarbitID.WORN_ITEMS_ZEALOT_BOOTS, "zealot's boots")));
        map.put(Skill.THIEVING, new Outfit("Rogue equipment",
            piece("Mask", ItemID.ROGUE_MASK, "rogue mask"), piece("Top", ItemID.ROGUE_TOP, "rogue top"),
            piece("Trousers", ItemID.ROGUE_TROUSERS, "rogue trousers"), piece("Gloves", ItemID.ROGUE_GLOVES, "rogue gloves"),
            piece("Boots", ItemID.ROGUE_BOOTS, "rogue boots")));
        OUTFITS = Collections.unmodifiableMap(map);
    }
    static List<Piece> missing(Outfit outfit, List<String> equippedNames, Set<Integer> activeBenefits)
    {
        List<Piece> missing = new ArrayList<>();
        for (Piece piece : outfit.pieces)
        {
            boolean worn = piece.benefitVarbit >= 0 && activeBenefits.contains(piece.benefitVarbit);
            if (!worn) { for (String name : equippedNames) { if (piece.matches(name)) { worn = true; break; } } }
            if (!worn) { missing.add(piece); }
        }
        return missing;
    }
}
