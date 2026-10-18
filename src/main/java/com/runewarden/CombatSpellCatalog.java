/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Numeric Wiki spell facts used for an explicit calculator input. */
@Singleton
final class CombatSpellCatalog
{
    static final class Spell
    {
        String name;
        @SerializedName("max_hit") int base;
        String spellbook;
        String element;
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
        @Override public String toString() { return name; }
    }
    private static final class Data { List<Spell> spells; }
    private final List<Spell> spells;
    @Inject CombatSpellCatalog(Gson gson)
    {
        try (InputStream stream = getClass().getResourceAsStream("/com/runewarden/combat-spells.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing spell facts");
            Data data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.spells == null || data.spells.isEmpty()) throw new IllegalStateException("Invalid spell facts");
            spells = Collections.unmodifiableList(data.spells);
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read spell facts", error); }
    }
    /** Public gameval spell objects used by AUTOCAST_SPELL_OBJ. */
    Spell fromSpellObject(int itemId)
    {
        String name;
        switch (itemId)
        {
            case 3273: name = "Wind Strike"; break;
            case 3275: name = "Water Strike"; break;
            case 3277: name = "Earth Strike"; break;
            case 3279: name = "Fire Strike"; break;
            case 3281: name = "Wind Bolt"; break;
            case 3285: name = "Water Bolt"; break;
            case 3288: name = "Earth Bolt"; break;
            case 3291: name = "Fire Bolt"; break;
            case 3294: name = "Wind Blast"; break;
            case 3297: name = "Water Blast"; break;
            case 3302: name = "Earth Blast"; break;
            case 3307: name = "Fire Blast"; break;
            case 3313: name = "Wind Wave"; break;
            case 3315: name = "Water Wave"; break;
            case 3319: name = "Earth Wave"; break;
            case 3321: name = "Fire Wave"; break;
            case 21876: name = "Wind Surge"; break;
            case 21877: name = "Water Surge"; break;
            case 21878: name = "Earth Surge"; break;
            case 21879: name = "Fire Surge"; break;
            case 4629: name = "Smoke Rush"; break;
            case 4630: name = "Shadow Rush"; break;
            case 4632: name = "Blood Rush"; break;
            case 4633: name = "Ice Rush"; break;
            case 4635: name = "Smoke Burst"; break;
            case 4636: name = "Shadow Burst"; break;
            case 4638: name = "Blood Burst"; break;
            case 4639: name = "Ice Burst"; break;
            case 4641: name = "Smoke Blitz"; break;
            case 4642: name = "Shadow Blitz"; break;
            case 4644: name = "Blood Blitz"; break;
            case 4645: name = "Ice Blitz"; break;
            case 4647: name = "Smoke Barrage"; break;
            case 4648: name = "Shadow Barrage"; break;
            case 4650: name = "Blood Barrage"; break;
            case 4651: name = "Ice Barrage"; break;
            case 3309: name = "Claws of Guthix"; break;
            case 3310: name = "Flames of Zamorak"; break;
            case 3311: name = "Saradomin Strike"; break;
            case 4176: case 5000: name = "Magic Dart"; break;
            case 3293: name = "Crumble Undead"; break;
            default: return null;
        }
        return named(name);
    }

    List<Spell> spells() { return spells; }
    Spell named(String name)
    {
        if (name == null) return null;
        for (Spell spell : spells) if (spell.name.equalsIgnoreCase(name.trim())) return spell;
        return null;
    }
}
