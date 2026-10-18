/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiaryCatalogTest
{
    private final DiaryCatalog catalog = new DiaryCatalog(new Gson());
    @Test public void allRegionsAndTasksHaveExactSourceObservationsAndRewardReferences()
    {
        assertEquals(48, catalog.diaries().size()); assertEquals(12, catalog.regions().size()); assertEquals(492, catalog.taskCount());
        HashSet<String> tasks = new HashSet<>(); int quests = 0, skills = 0;
        for (DiaryCatalog.Diary diary : catalog.diaries())
        {
            assertTrue(diary.source.contains("5f791937a99a676f23c02aa48c4e4d5b50e7edad")); assertNotNull(diary.claimedVariable); assertFalse(diary.rewards.isEmpty());
            for (DiaryCatalog.Task task : diary.tasks)
            { assertTrue(tasks.add(task.id)); assertNotNull(task.observation); assertFalse(task.title.isEmpty()); for (DiaryCatalog.Requirement requirement : task.requirements) { if ("quest".equals(requirement.kind)) quests++; if ("skill".equals(requirement.kind)) skills++; } }
        }
        assertTrue(quests > 150); assertTrue(skills > 300);
    }
    @Test public void exactSignedBitAndAbsentObservationAreDifferent()
    {
        DiaryCatalog.Task task = new DiaryCatalog.Task(); task.observation = new DiaryCatalog.Observation(); task.observation.kind = "varpBit"; task.observation.variable = 123; task.observation.bit = 31;
        assertNull(DiaryCatalog.completed(task, Collections.emptyMap(), Collections.emptyMap()));
        assertEquals(Integer.valueOf(0), DiaryCatalog.completed(task, Map.of(123, 0), Collections.emptyMap()));
        assertEquals(Integer.valueOf(1), DiaryCatalog.completed(task, Map.of(123, Integer.MIN_VALUE), Collections.emptyMap()));
    }
    @Test public void karamjaPartialCountersDoNotCountAsComplete()
    {
        DiaryCatalog.Task banana = catalog.find("karamja_easy").tasks.stream().filter(task -> task.title.toLowerCase().contains("banana")).findFirst().get();
        int variable = banana.observation.variable;
        assertEquals(Integer.valueOf(0), DiaryCatalog.completed(banana, Collections.emptyMap(), Map.of(variable, 4)));
        assertEquals(Integer.valueOf(1), DiaryCatalog.completed(banana, Collections.emptyMap(), Map.of(variable, 5)));
    }
    @Test public void routeVariantsCoalesceToOneTaskWithoutLosingInstructions()
    {
        DiaryCatalog.Diary diary = catalog.find("ardougne_medium");
        assertEquals(12, diary.tasks.size());
        DiaryCatalog.Task balloon = diary.tasks.stream().filter(task -> task.title.equals("Castle Wars Balloon")).findFirst().get();
        assertTrue(balloon.steps.size() >= 3); assertEquals("varpBit", balloon.observation.kind);
        assertEquals(2, balloon.variants.size());
        assertTrue(balloon.requirements.stream().anyMatch(requirement -> "reference".equals(requirement.kind) && requirement.label.contains("Route-dependent")));
        assertFalse(balloon.requirements.stream().anyMatch(requirement -> "Yew logs".equals(requirement.label)));
    }
    @Test public void itemReferencesUseUnnotedTopLevelIdsRatherThanCertOrPlaceholderConstants()
    {
        DiaryCatalog.Task darts = catalog.find("varrock_elite").tasks.stream().filter(task -> task.title.contains("Darts")).findFirst().get();
        assertEquals(2347, darts.requirements.stream().filter(item -> "Hammer".equals(item.label)).findFirst().get().itemId);
        assertEquals(314, darts.requirements.stream().filter(item -> "Feather".equals(item.label)).findFirst().get().itemId);
        assertEquals(2363, darts.requirements.stream().filter(item -> "Runite bar".equals(item.label)).findFirst().get().itemId);
    }
}
