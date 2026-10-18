/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatAchievementCatalogTest
{
    private static CombatAchievementCatalog catalog;
    @BeforeClass public static void load() { catalog = new CombatAchievementCatalog(new Gson()); }

    @Test public void containsEveryCurrentTaskAndCorrectWeightedRewardTargets()
    {
        assertEquals(655, catalog.tasks().size()); assertEquals(2697, catalog.totalPoints());
        for (int id = 0; id <= 654; id++) assertEquals(id, catalog.find(id).id);
        int cumulative = 0;
        for (String tier : CombatAchievementCatalog.DIFFICULTIES)
        {
            for (CombatAchievementCatalog.Task task : catalog.tasks()) if (tier.equals(task.difficulty)) cumulative += task.points;
            assertEquals(tier, cumulative, catalog.threshold(tier));
        }
        assertEquals(41, catalog.threshold("Easy")); assertEquals(169, catalog.threshold("Medium"));
        assertEquals(436, catalog.threshold("Hard")); assertEquals(1100, catalog.threshold("Elite"));
        assertEquals(1965, catalog.threshold("Master"));
    }

    @Test public void completionMappingHandlesSignedBitsChunkBoundariesAndNewestChunk()
    {
        int[] bits = new int[21]; bits[0] = Integer.MIN_VALUE; bits[1] = 1; bits[20] = 1 << 14;
        assertTrue(CombatAchievementCatalog.complete(bits, 31)); assertTrue(CombatAchievementCatalog.complete(bits, 32));
        assertTrue(CombatAchievementCatalog.complete(bits, 654)); assertFalse(CombatAchievementCatalog.complete(bits, 30));
        assertFalse(CombatAchievementCatalog.complete(bits, -1)); assertFalse(CombatAchievementCatalog.complete(bits, 672));
        assertEquals(3387, CombatAchievementCatalog.COMPLETION_VARPS[13]);
        assertEquals(5673, CombatAchievementCatalog.COMPLETION_VARPS[20]);
    }

    @Test public void taskMethodsAndUnlockFactsHaveRevisionSpecificSources()
    {
        assertEquals(373, catalog.tasks().stream().filter(task -> !task.method.isEmpty()).count());
        for (CombatAchievementCatalog.Task task : catalog.tasks())
        {
            assertTrue(task.name, task.revision > 0); assertTrue(task.name, task.source.contains("?oldid="));
            for (String paragraph : task.method) assertFalse(task.name, paragraph.contains("{{"));
        }
        CombatAchievementCatalog.Task noxious = catalog.find(0);
        assertEquals("Noxious Foe", noxious.name); assertEquals("Slayer", noxious.requirements.get(0).key);
        assertEquals(60, noxious.requirements.get(0).value); assertTrue(noxious.requirements.get(0).boostable);
        assertTrue(noxious.items.stream().anyMatch(item -> item.itemId == 4168));
        assertTrue(catalog.find(28).requirements.stream().anyMatch(requirement -> "Priest in Peril".equals(requirement.key)));
    }

    @Test public void filteringSeparatesDifficultyBossTypeSearchAndRealCompletion()
    {
        HashSet<Integer> done = new HashSet<>(); done.add(0);
        assertTrue(catalog.filter(done, "Easy", null, null, "Noxious", true).isEmpty());
        List<CombatAchievementCatalog.Task> result = catalog.filter(Collections.emptySet(), "Easy", "Aberrant Spectre", "Kill Count", "Noxious", true);
        assertEquals(1, result.size()); assertEquals(0, result.get(0).id);
        assertEquals(1, catalog.points(done)); assertEquals("Medium", catalog.nextTier(41));
    }
}
