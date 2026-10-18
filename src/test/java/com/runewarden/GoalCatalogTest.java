/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoalCatalogTest
{
    private final Gson gson = new Gson();
    private final GuideCatalog guides = new GuideCatalog(gson);
    private final GoalCatalog catalog = new GoalCatalog(guides, new ActivityXpCatalog(gson, guides));

    @Test public void missingQuestUnlocksQueueDependencyChainBeforeTrainingGoal()
    {
        GoalCatalog.Facts facts = facts(1, QuestState.NOT_STARTED);
        List<GoalCatalog.Requirement> requirements = catalog.requirements(guides.entry("training:ammonite-crabs"), facts);
        assertEquals(-1, requirements.get(0).state);
        List<GoalPlan> chain = catalog.unlockGoals(requirements, facts, Collections.emptyList());
        assertTrue(index(chain, "quest:Druidic Ritual") < index(chain, "quest:The Dig Site"));
        assertTrue(index(chain, "quest:The Dig Site") < index(chain, "quest:Bone Voyage"));
        assertTrue(chain.stream().anyMatch(goal -> goal.skill() == Skill.AGILITY && goal.targetXp == Experience.getXpForLevel(10)));
        assertTrue(chain.stream().anyMatch(goal -> goal.skill() == Skill.HERBLORE && goal.targetXp == Experience.getXpForLevel(10)));
        assertTrue(chain.stream().anyMatch(goal -> goal.skill() == Skill.THIEVING && goal.targetXp == Experience.getXpForLevel(25)));
        assertEquals(1, catalog.requirements(guides.entry("quest:Bone Voyage"), facts).stream().filter(item -> "100 Kudos".equals(item.label)).count());
    }

    @Test public void unknownFactsStayUnknownAndNeverCreateInventedUnlockGoals()
    {
        GoalCatalog.Facts facts = facts(99, QuestState.FINISHED);
        List<GoalCatalog.Requirement> unknown = catalog.requirements(guides.entry("training:nightmare-zone-absorptions"), facts);
        assertEquals(1, unknown.size()); assertEquals(0, unknown.get(0).state);
        assertTrue(catalog.unlockGoals(unknown, facts, Collections.emptyList()).isEmpty());
        GoalCatalog.Facts absent = new GoalCatalog.Facts(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 0);
        assertEquals(0, catalog.requirements(guides.entry("training:ammonite-crabs"), absent).get(0).state);
    }

    @Test public void existingAndAlreadyMetPrerequisitesAreNotQueuedAgain()
    {
        GoalCatalog.Facts facts = facts(99, QuestState.NOT_STARTED);
        List<GoalCatalog.Requirement> requirements = catalog.requirements(guides.entry("training:ammonite-crabs"), facts);
        List<GoalPlan> chain = catalog.unlockGoals(requirements, facts, List.of(GoalPlan.quest("quest:Bone Voyage")));
        assertTrue(chain.isEmpty());
        assertTrue(catalog.unlockGoals(catalog.requirements(guides.entry("training:ammonite-crabs"), facts(99, QuestState.FINISHED)),
            facts(99, QuestState.FINISHED), Collections.emptyList()).isEmpty());
    }

    @Test public void alternativeRequirementsNeverPickAnArbitraryRouteAndRfdAliasesMatchActualQuestState()
    {
        GuideCatalog.Entry entry = new GuideCatalog.Entry(); entry.requirementDetails = new ArrayList<>();
        JsonObject choice = new JsonObject(); choice.addProperty("label", "One of two quest routes");
        JsonArray any = new JsonArray();
        for (String name : new String[]{"Bone Voyage", "The Dig Site"})
        { JsonObject option = new JsonObject(); option.addProperty("kind", "quest"); option.addProperty("key", name); any.add(option); }
        choice.add("anyOf", any); entry.requirementDetails.add(choice);
        GoalCatalog.Facts facts = facts(1, QuestState.NOT_STARTED);
        List<GoalCatalog.Requirement> needed = catalog.requirements(entry, facts);
        assertEquals(-1, needed.get(0).state);
        assertTrue(catalog.unlockGoals(needed, facts, Collections.emptyList()).isEmpty());
        Map<String, QuestState> quests = new LinkedHashMap<>();
        quests.put(GoalCatalog.questKey("Recipe for Disaster - King Awowogei"), QuestState.FINISHED);
        GoalCatalog.Facts rfd = new GoalCatalog.Facts(Collections.emptyMap(), Collections.emptyMap(), quests, 126);
        assertEquals(QuestState.FINISHED, rfd.quest("Recipe for Disaster/Freeing King Awowogei"));
    }

    @Test public void methodFiltersIncludeTrainingMinigamesWithoutInventingRates()
    {
        List<ActivityXpCatalog.Method> methods = catalog.methods(Skill.ATTACK, "AFK");
        assertTrue(methods.stream().anyMatch(method -> "training:ammonite-crabs".equals(method.guideId)));
        assertTrue(methods.stream().anyMatch(method -> "training:nightmare-zone-absorptions".equals(method.guideId)));
        assertTrue(methods.stream().allMatch(method -> "AFK".equals(GoalCatalog.intensity(method.intensity))));
        GoalPlan goal = GoalPlan.skill(Skill.ATTACK, 100_000, 0);
        ActivityXpCatalog.Method unpriced = new ActivityXpCatalog.Method();
        assertTrue(GoalPlannerTool.estimate(goal, unpriced, 50, 0).contains("without a reference band"));
        goal.customXpPerHour = 50_000; goal.weeklyHours = 2;
        assertEquals("Your rate: 2.0 hours · 1.0 weeks", GoalPlannerTool.estimate(goal, unpriced, 50, 0));
    }

    private GoalCatalog.Facts facts(int level, QuestState state)
    {
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class), xp = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) { levels.put(skill, level); xp.put(skill, Experience.getXpForLevel(level)); }
        Map<String, QuestState> quests = new LinkedHashMap<>();
        for (GuideCatalog.Entry entry : guides.search("", "quest")) quests.put(GoalCatalog.questKey(entry.title), state);
        return new GoalCatalog.Facts(xp, levels, quests, 126);
    }
    private static int index(List<GoalPlan> goals, String quest)
    { for (int i = 0; i < goals.size(); i++) if (quest.equals(goals.get(i).questGuideId)) return i; return -1; }
}
