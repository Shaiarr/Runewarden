/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/** Small numeric spell facts indexed by the native AUTOCAST_SPELL_OBJ value. */
final class CombatSpellCatalog
{
    static final class Spell
    {
        final int objectId, base;
        final String name, spellbook, element;
        private Spell(int objectId, String name, int base, String spellbook, String element)
        {
            this.objectId = objectId; this.name = name; this.base = base;
            this.spellbook = spellbook; this.element = element;
        }
        int maxHit(int level)
        {
            if ("Magic Dart".equals(name)) return 10 + level / 10;
            if (element == null) return base;
            if (name.endsWith("Strike")) return tier(level, 5, 9, 13, 2, 4, 6, 8);
            if (name.endsWith("Bolt")) return tier(level, 23, 29, 35, 9, 10, 11, 12);
            if (name.endsWith("Blast")) return tier(level, 47, 53, 59, 13, 14, 15, 16);
            if (name.endsWith("Wave")) return tier(level, 65, 70, 75, 17, 18, 19, 20);
            if (name.endsWith("Surge")) return tier(level, 85, 90, 95, 21, 22, 23, 24);
            return base;
        }
        private static int tier(int level, int water, int earth, int fire, int airHit, int waterHit, int earthHit, int fireHit)
        { return level >= fire ? fireHit : level >= earth ? earthHit : level >= water ? waterHit : airHit; }
    }

    private CombatSpellCatalog() { }

    static Spell fromSpellObject(int objectId)
    {
        for (Spell spell : AUTOCAST_SPELLS)
        {
            if (spell.objectId == objectId) return spell;
        }
        return null;
    }

    // Spell objects and normal maximums are gameplay facts; no monster or DPS catalogue is loaded.
    private static final Spell[] AUTOCAST_SPELLS = {
        new Spell(3273, "Wind Strike", 2, "standard", "air"),
        new Spell(3275, "Water Strike", 4, "standard", "water"),
        new Spell(3277, "Earth Strike", 6, "standard", "earth"),
        new Spell(3279, "Fire Strike", 8, "standard", "fire"),
        new Spell(3281, "Wind Bolt", 9, "standard", "air"),
        new Spell(3285, "Water Bolt", 10, "standard", "water"),
        new Spell(3288, "Earth Bolt", 11, "standard", "earth"),
        new Spell(3291, "Fire Bolt", 12, "standard", "fire"),
        new Spell(3293, "Crumble Undead", 15, "standard", null),
        new Spell(3294, "Wind Blast", 13, "standard", "air"),
        new Spell(3297, "Water Blast", 14, "standard", "water"),
        new Spell(3302, "Earth Blast", 15, "standard", "earth"),
        new Spell(3307, "Fire Blast", 16, "standard", "fire"),
        new Spell(3309, "Claws of Guthix", 20, "standard", null),
        new Spell(3310, "Flames of Zamorak", 20, "standard", null),
        new Spell(3311, "Saradomin Strike", 20, "standard", null),
        new Spell(3313, "Wind Wave", 17, "standard", "air"),
        new Spell(3315, "Water Wave", 18, "standard", "water"),
        new Spell(3319, "Earth Wave", 19, "standard", "earth"),
        new Spell(3321, "Fire Wave", 20, "standard", "fire"),
        new Spell(4176, "Magic Dart", 0, "standard", null),
        new Spell(4629, "Smoke Rush", 13, "ancient", null),
        new Spell(4630, "Shadow Rush", 14, "ancient", null),
        new Spell(4632, "Blood Rush", 15, "ancient", null),
        new Spell(4633, "Ice Rush", 16, "ancient", null),
        new Spell(4635, "Smoke Burst", 17, "ancient", null),
        new Spell(4636, "Shadow Burst", 18, "ancient", null),
        new Spell(4638, "Blood Burst", 21, "ancient", null),
        new Spell(4639, "Ice Burst", 22, "ancient", null),
        new Spell(4641, "Smoke Blitz", 23, "ancient", null),
        new Spell(4642, "Shadow Blitz", 24, "ancient", null),
        new Spell(4644, "Blood Blitz", 25, "ancient", null),
        new Spell(4645, "Ice Blitz", 26, "ancient", null),
        new Spell(4647, "Smoke Barrage", 27, "ancient", null),
        new Spell(4648, "Shadow Barrage", 28, "ancient", null),
        new Spell(4650, "Blood Barrage", 29, "ancient", null),
        new Spell(4651, "Ice Barrage", 30, "ancient", null),
        new Spell(5000, "Magic Dart", 0, "standard", null),
        new Spell(21876, "Wind Surge", 21, "standard", "air"),
        new Spell(21877, "Water Surge", 22, "standard", "water"),
        new Spell(21878, "Earth Surge", 23, "standard", "earth"),
        new Spell(21879, "Fire Surge", 24, "standard", "fire")
    };
}
