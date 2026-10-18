/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideBankFamiliesTest
{
    @Test public void foodUsesActualEatActionAndRejectsUnsafeLookalikes()
    {
        ItemComposition food = mock(ItemComposition.class);
        when(food.getInventoryActions()).thenReturn(new String[]{"Eat", null, null, null, "Drop"});
        assertTrue(GuideBankFamilies.matches("food", "shark", food, null, null));
        assertFalse(GuideBankFamilies.matches("food", "poison karambwan", food, null, null));
        assertFalse(GuideBankFamilies.matches("food", "dwarven rock cake", food, null, null));
        assertFalse(GuideBankFamilies.matches("food", "blighted manta ray", food, null, null));
        when(food.getInventoryActions()).thenReturn(new String[]{"Use", null, null, null, "Drop"});
        assertFalse(GuideBankFamilies.matches("food", "raw shark", food, null, null));
    }

    @Test public void gearViewsUseCachedActualEquipmentBonuses()
    {
        ItemStats ranged = new ItemStats(true, 0, 0, ItemEquipmentStats.builder().slot(4).arange(30).amagic(-20).build());
        ItemStats magic = new ItemStats(true, 0, 0, ItemEquipmentStats.builder().slot(4).amagic(30).arange(-20).build());
        ItemStats neutral = new ItemStats(true, 0, 0, ItemEquipmentStats.builder().slot(2).prayer(5).build());
        assertTrue(GuideBankFamilies.matches("ranged-equipment", "masori body", null, ranged, null));
        assertFalse(GuideBankFamilies.matches("magic-equipment", "masori body", null, ranged, null));
        assertTrue(GuideBankFamilies.matches("magic-equipment", "ancestral robe top", null, magic, null));
        assertFalse(GuideBankFamilies.matches("ranged-equipment", "ancestral robe top", null, magic, null));
        assertTrue(GuideBankFamilies.matches("melee-equipment", "holy symbol", null, neutral, null));
        assertTrue(GuideBankFamilies.matches("magic-equipment", "holy symbol", null, neutral, null));
    }

    @Test public void potionsRunesAndConsumablesKeepTheirDistinctRoles()
    {
        assertTrue(GuideBankFamilies.matches("prayer-restoration", "super restore(4)", null, null, null));
        assertFalse(GuideBankFamilies.matches("prayer-restoration", "super energy(4)", null, null, null));
        assertFalse(GuideBankFamilies.matches("prayer-restoration", "sunlight moth mix(2)", null, null, null));
        assertTrue(GuideBankFamilies.matches("run-energy", "super energy(4)", null, null, null));
        assertTrue(GuideBankFamilies.matches("runes", "blood rune", null, null, null));
        assertFalse(GuideBankFamilies.matches("runes", "rune platebody", null, null, null));
        assertTrue(GuideBankFamilies.matches("antipoison", "anti-venom+(4)", null, null, null));
        assertTrue(GuideBankFamilies.matches("dragonfire-protection", "anti-dragon shield", null, null, null));
    }

    @Test public void relevantSkillingOutfitMatchesItsRealRecoloursOnly()
    {
        assertTrue(GuideBankFamilies.matches("skilling-outfit", "golden prospector helmet", null, null, "mining"));
        assertFalse(GuideBankFamilies.matches("skilling-outfit", "golden prospector helmet", null, null, "farming"));
        assertTrue(GuideBankFamilies.matches("skilling-outfit", "hat of the eye blue", null, null, "runecrafting"));
    }
}
