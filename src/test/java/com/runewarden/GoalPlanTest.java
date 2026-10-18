/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoalPlanTest
{
    private final Gson gson = new Gson();

    @Test public void milestoneCheckpointsUseRealXpThresholdsAndIncludeExactTarget()
    {
        GoalPlan goal = GoalPlan.skill(Skill.MINING, Experience.getXpForLevel(99), Experience.getXpForLevel(79));
        assertEquals(Experience.getXpForLevel(80), (int) goal.milestones().get(0));
        assertEquals(Experience.getXpForLevel(99), (int) goal.milestones().get(goal.milestones().size() - 1));
        goal.targetXp = 14_000_000;
        assertEquals(14_000_000, (int) goal.milestones().get(goal.milestones().size() - 1));
        assertTrue(goal.milestones().contains(Experience.getXpForLevel(95)));
        assertEquals(0, goal.percent(goal.startXp));
        assertEquals(100, goal.percent(goal.targetXp));
        assertEquals(0, goal.remaining(200_000_000));
    }

    @Test public void archiveRemoveAndReorderArePersistedWithoutChangingOtherProfiles()
    {
        Harness h = new Harness(); GoalPlan.Session session = h.store.bind("a");
        GoalPlan mining = GoalPlan.skill(Skill.MINING, Experience.getXpForLevel(99), 0);
        GoalPlan quest = GoalPlan.quest("quest:Bone Voyage");
        assertTrue(h.store.put(session, mining)); assertTrue(h.store.put(session, quest));
        assertTrue(h.store.move(session, quest.id, -1));
        mining.archived = true; assertTrue(h.store.put(session, mining));
        assertEquals(quest.id, h.store.goals().get(0).id);
        assertTrue(h.store.goals().get(1).archived);
        assertTrue(h.store.remove(session, quest.id));
        h.store.clear(); h.store.bind("a");
        assertEquals(1, h.store.goals().size()); assertEquals(mining.id, h.store.goals().get(0).id);
        assertTrue(h.store.goals().get(0).archived);
        h.profile.set("b"); h.store.bind("b");
        assertTrue(h.store.goals().isEmpty());
        assertEquals(1, GoalPlan.decode(gson, h.values.get("a")).size());
    }

    @Test public void staleButtonsCannotMutateDifferentAccountOrLaterLoginOfSameAccount()
    {
        Harness h = new Harness(); GoalPlan.Session old = h.store.bind("a");
        GoalPlan first = GoalPlan.skill(Skill.HUNTER, 1000, 0);
        assertTrue(h.store.put(old, first));
        h.profile.set("b");
        assertFalse(h.store.remove(old, first.id));
        assertFalse(h.store.put(old, GoalPlan.quest("quest:Cook's Assistant")));
        h.store.bind("b"); h.profile.set("a"); h.store.clear(); h.store.bind("a");
        assertFalse(h.store.remove(old, first.id));
        assertFalse(h.store.move(old, first.id, 1));
        assertEquals(first.id, h.store.goals().get(0).id);
    }

    @Test public void malformedOrUnsupportedSavedDataCannotCreateInvalidGoals()
    {
        assertTrue(GoalPlan.decode(gson, "not json").isEmpty());
        assertTrue(GoalPlan.decode(gson, "{\"version\":2,\"goals\":[]}").isEmpty());
        GoalPlan.Data data = new GoalPlan.Data();
        GoalPlan valid = GoalPlan.skill(Skill.CRAFTING, 2000, 100); valid.intensity = null;
        GoalPlan bad = valid.copy(); bad.id = "bad"; bad.targetXp = 200_000_001;
        data.goals.add(valid); data.goals.add(valid); data.goals.add(bad);
        List<GoalPlan> decoded = GoalPlan.decode(gson, gson.toJson(data));
        assertEquals(1, decoded.size()); assertEquals("Normal", decoded.get(0).intensity);
        decoded.get(0).targetXp = 0;
        assertEquals(2000, valid.targetXp);
    }

    @Test public void snapshotsCannotEditStoredGoalsAndPendingGoalLimitIsBounded()
    {
        Harness h = new Harness(); GoalPlan.Session session = h.store.bind("a");
        GoalPlan goal = GoalPlan.skill(Skill.WOODCUTTING, 100, 0); h.store.put(session, goal);
        h.store.goals().get(0).targetXp = 900;
        assertEquals(100, h.store.goals().get(0).targetXp);
        for (int i = 1; i < GoalPlan.MAX_GOALS; i++) assertTrue(h.store.put(session, GoalPlan.skill(Skill.MINING, 100 + i, 0)));
        assertFalse(h.store.put(session, GoalPlan.quest("quest:Cook's Assistant")));
        assertEquals(GoalPlan.MAX_GOALS, h.store.goals().size());
    }

    private final class Harness
    {
        final ConfigManager config = mock(ConfigManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("a");
        final Map<String, String> values = new HashMap<>();
        final GoalPlan.Store store = new GoalPlan.Store(gson, config);
        Harness()
        {
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY)))
                .thenAnswer(call -> values.get(call.getArgument(1)));
            doAnswer(call -> { values.put(call.getArgument(1), call.getArgument(3)); return null; })
                .when(config).setConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY), anyString());
        }
    }
}
