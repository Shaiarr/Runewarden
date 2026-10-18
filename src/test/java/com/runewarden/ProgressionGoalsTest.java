/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ProgressionGoalsTest
{
    private final Gson gson = new Gson();
    @Test public void newTypesRoundTripWhileLegacyGoalsRetainIdentity()
    {
        GoalPlan.Data data = new GoalPlan.Data(); data.goals.add(GoalPlan.skill(Skill.MINING, 10000, 500));
        data.goals.add(GoalPlan.progress("set", "torva", 1, "Torva set", List.of(26382, 26384, 26386)));
        for (String type : List.of("collection", "kc", "diary", "ca", "music", "metric", "currency", "unlock")) data.goals.add(GoalPlan.progress(type, List.of("ca", "music", "collection").contains(type) ? "123" : "verified-reference", 1, type, List.of()));
        List<GoalPlan> result = GoalPlan.decode(gson, gson.toJson(data)); assertEquals(10, result.size());
        assertEquals(data.goals.get(0).id, result.get(0).id); assertEquals(List.of(26382, 26384, 26386), result.get(1).itemIds);
    }
    @Test public void sharedPlansDiscardPersonalProgressAndDeduplicateDefinitions()
    {
        GoalPlan goal = GoalPlan.skill(Skill.MINING, 10000, 5000); goal.achievedAt = 123; goal.archived = true;
        List<GoalPlan> imported = GoalPlan.importPlan(gson, GoalPlan.share(gson, List.of(goal, goal)));
        assertEquals(1, imported.size()); assertNotEquals(goal.id, imported.get(0).id); assertEquals(0, imported.get(0).achievedAt); assertEquals(0, imported.get(0).startXp); assertFalse(imported.get(0).archived);
    }
    @Test public void invalidOrExecutableLookingPlanTextCannotBecomeGoals()
    {
        for (String text : List.of("", "https://example.org", "RWPLAN1:bad", "RWPLAN2:e30"))
            try { GoalPlan.importPlan(gson, text); fail(text); } catch (IllegalArgumentException expected) { }
        try { GoalPlan.progress("item", "4151", 1, "<script>", List.of(4151)); fail(); } catch (IllegalArgumentException expected) { }
    }
    @Test public void undoRedoAndImportStayBoundToExactAccountGeneration()
    {
        ConfigManager config = mock(ConfigManager.class); AtomicReference<String> profile = new AtomicReference<>("a"); Map<String, String> values = new HashMap<>();
        when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
        when(config.getConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY))).thenAnswer(call -> values.get(call.getArgument(1)));
        doAnswer(call -> { values.put(call.getArgument(1), call.getArgument(3)); return null; }).when(config).setConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY), anyString());
        GoalPlan.Store store = new GoalPlan.Store(gson, config); GoalPlan.Session first = store.bind("a");
        assertTrue(store.importGoals(first, List.of(GoalPlan.progress("kc", "Brutus", 1000, "1,000 Brutus", List.of()))));
        String id = store.goals().get(0).id; assertTrue(store.remove(first, id)); assertTrue(store.undo(first)); assertEquals(id, store.goals().get(0).id);
        assertTrue(store.redo(first)); assertTrue(store.goals().isEmpty());
        profile.set("b"); store.bind("b"); assertFalse(store.undo(first)); assertFalse(store.canUndo());
        assertFalse(store.importGoals(first, List.of(GoalPlan.quest("quest:Cook's Assistant"))));
    }
    @Test public void futureVersionSavedPlanIsPreservedAndCannotBeOverwritten()
    {
        ConfigManager config = mock(ConfigManager.class); when(config.getRSProfileKey()).thenReturn("rsprofile.test");
        String future = "{\"version\":9,\"goals\":[]}";
        when(config.getConfiguration(RunewardenConfig.GROUP, "rsprofile.test", GoalPlan.KEY)).thenReturn(future);
        GoalPlan.Store store = new GoalPlan.Store(gson, config, new RunewardenDataStore(config, gson)); GoalPlan.Session session = store.bind("rsprofile.test");
        assertFalse(store.writable()); assertFalse(store.put(session, GoalPlan.quest("quest:Cook's Assistant")));
        verify(config, never()).setConfiguration(eq(RunewardenConfig.GROUP), eq("rsprofile.test"), eq(GoalPlan.KEY), anyString());
    }
}
