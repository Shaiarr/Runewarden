/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static net.runelite.api.ItemID.*;
import net.runelite.api.Skill;
import static net.runelite.api.gameval.VarbitID.*;

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
            benefit("Helmet", PROSPECTOR_HELMET, WORN_ITEMS_PROSPECTOR_HEAD, "prospector helmet", "golden prospector helmet"),
            benefit("Jacket", PROSPECTOR_JACKET, WORN_ITEMS_PROSPECTOR_BODY, "prospector jacket", "golden prospector jacket"),
            benefit("Legs", PROSPECTOR_LEGS, WORN_ITEMS_PROSPECTOR_LEGS, "prospector legs", "golden prospector legs"),
            benefit("Boots", PROSPECTOR_BOOTS, WORN_ITEMS_PROSPECTOR_BOOTS, "prospector boots", "golden prospector boots")));
        map.put(Skill.WOODCUTTING, new Outfit("Lumberjack / Forestry",
            benefit("Hat", LUMBERJACK_HAT, WORN_ITEMS_LUMBERJACK_HEAD, "lumberjack hat", "forestry hat"),
            benefit("Top", LUMBERJACK_TOP, WORN_ITEMS_LUMBERJACK_BODY, "lumberjack top", "forestry top"),
            benefit("Legs", LUMBERJACK_LEGS, WORN_ITEMS_LUMBERJACK_LEGS, "lumberjack legs", "forestry legs"),
            benefit("Boots", LUMBERJACK_BOOTS, WORN_ITEMS_LUMBERJACK_BOOTS, "lumberjack boots", "forestry boots")));
        map.put(Skill.FISHING, new Outfit("Angler / Spirit angler",
            piece("Hat", ANGLER_HAT, "angler hat", "spirit angler headband", "spirit angler hat", "krill angler hat"),
            piece("Top", ANGLER_TOP, "angler top", "spirit angler top"),
            piece("Waders", ANGLER_WADERS, "angler waders", "spirit angler waders", "spirit angler legs"),
            piece("Boots", ANGLER_BOOTS, "angler boots", "spirit angler boots")));
        map.put(Skill.FARMING, new Outfit("Farmer's outfit",
            piece("Strawhat", FARMERS_STRAWHAT, "farmer's strawhat"),
            piece("Top", FARMERS_JACKET, "farmer's jacket", "farmer's shirt"),
            piece("Trousers", FARMERS_BORO_TROUSERS, "farmer's boro trousers"),
            piece("Boots", FARMERS_BOOTS, "farmer's boots")));
        map.put(Skill.FIREMAKING, new Outfit("Pyromancer outfit",
            piece("Hood", PYROMANCER_HOOD, "pyromancer hood"), piece("Top", PYROMANCER_GARB, "pyromancer garb"),
            piece("Robe", PYROMANCER_ROBE, "pyromancer robe"), piece("Boots", PYROMANCER_BOOTS, "pyromancer boots")));
        map.put(Skill.CONSTRUCTION, new Outfit("Carpenter's outfit",
            benefit("Helmet", CARPENTERS_HELMET, WORN_ITEMS_CARPENTER_HEAD, "carpenter's helmet"),
            benefit("Shirt", CARPENTERS_SHIRT, WORN_ITEMS_CARPENTER_BODY, "carpenter's shirt"),
            benefit("Trousers", CARPENTERS_TROUSERS, WORN_ITEMS_CARPENTER_LEGS, "carpenter's trousers"),
            benefit("Boots", CARPENTERS_BOOTS, WORN_ITEMS_CARPENTER_BOOTS, "carpenter's boots")));
        map.put(Skill.SMITHING, new Outfit("Smiths' uniform",
            piece("Tunic", SMITHS_TUNIC, "smiths' tunic"), piece("Trousers", SMITHS_TROUSERS, "smiths' trousers"),
            piece("Gloves", SMITHS_GLOVES, "smiths' gloves"), piece("Boots", SMITHS_BOOTS, "smiths' boots")));
        map.put(Skill.RUNECRAFT, new Outfit("Raiments of the Eye",
            benefit("Hat", HAT_OF_THE_EYE, WORN_ITEMS_RC_OUTFIT_HAT, "hat of the eye", "hat of the eye red", "hat of the eye blue", "hat of the eye green"),
            benefit("Robe top", ROBE_TOP_OF_THE_EYE, WORN_ITEMS_RC_OUTFIT_TOP, "robe top of the eye", "robe top of the eye red", "robe top of the eye blue", "robe top of the eye green"),
            benefit("Robe bottoms", ROBE_BOTTOMS_OF_THE_EYE, WORN_ITEMS_RC_OUTFIT_LEGS, "robe bottoms of the eye", "robe bottoms of the eye red", "robe bottoms of the eye blue", "robe bottoms of the eye green"),
            benefit("Boots", BOOTS_OF_THE_EYE, WORN_ITEMS_RC_OUTFIT_BOOTS, "boots of the eye")));
        map.put(Skill.AGILITY, new Outfit("Graceful outfit",
            benefit("Hood", GRACEFUL_HOOD, WORN_ITEMS_GRACEFUL_HOOD, "graceful hood"),
            benefit("Top", GRACEFUL_TOP, WORN_ITEMS_GRACEFUL_TORSO, "graceful top"),
            benefit("Legs", GRACEFUL_LEGS, WORN_ITEMS_GRACEFUL_LEGS, "graceful legs"),
            benefit("Gloves", GRACEFUL_GLOVES, WORN_ITEMS_GRACEFUL_GLOVES, "graceful gloves"),
            benefit("Boots", GRACEFUL_BOOTS, WORN_ITEMS_GRACEFUL_BOOTS, "graceful boots"),
            benefit("Cape", GRACEFUL_CAPE, WORN_ITEMS_GRACEFUL_CAPE, "graceful cape")));
        map.put(Skill.PRAYER, new Outfit("Zealot's robes",
            benefit("Helm", ZEALOTS_HELM, WORN_ITEMS_ZEALOT_HEAD, "zealot's helm"),
            benefit("Robe top", ZEALOTS_ROBE_TOP, WORN_ITEMS_ZEALOT_BODY, "zealot's robe top"),
            benefit("Robe bottom", ZEALOTS_ROBE_BOTTOM, WORN_ITEMS_ZEALOT_LEGS, "zealot's robe bottom"),
            benefit("Boots", ZEALOTS_BOOTS, WORN_ITEMS_ZEALOT_BOOTS, "zealot's boots")));
        map.put(Skill.THIEVING, new Outfit("Rogue equipment",
            piece("Mask", ROGUE_MASK, "rogue mask"), piece("Top", ROGUE_TOP, "rogue top"),
            piece("Trousers", ROGUE_TROUSERS, "rogue trousers"), piece("Gloves", ROGUE_GLOVES, "rogue gloves"),
            piece("Boots", ROGUE_BOOTS, "rogue boots")));
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
