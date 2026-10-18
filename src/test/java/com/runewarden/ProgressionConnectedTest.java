/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionConnectedTest
{
    @Test public void nativeAccountThresholdsCoverAllSixTiersWithoutAssumingRegularTotals()
    {
        Map<String,Integer> thresholds=CombatAchievementsTool.validatedThresholds(new int[]{41,169,436,1100,1955,2675});
        assertEquals(6,thresholds.size());assertEquals(Integer.valueOf(2675),thresholds.get("Grandmaster"));
        for(int[] invalid:List.of(new int[6],new int[]{1,2},new int[]{41,169,436,1100,1955,1955},new int[]{41,169,-1,1100,1955,2675}))assertTrue(CombatAchievementsTool.validatedThresholds(invalid).isEmpty());
        try{thresholds.put("Easy",1);fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void connectedEncounterRequirementsCoverLinkedBossesAndRetainUnverifiedAccess()
    {
        Gson gson=new Gson();GuideCatalog guides=new GuideCatalog(gson);CombatAchievementCatalog catalog=new CombatAchievementCatalog(gson);
        int connected=0,typed=0,typedFacts=0;
        for(CombatAchievementCatalog.Task task:catalog.tasks())
        {
            List<CombatAchievementCatalog.Requirement> requirements=catalog.requirements(task,guides);
            if(!requirements.isEmpty())connected++;
            if(requirements.stream().anyMatch(r->List.of("skill","quest","choice").contains(r.kind)))typed++;
            typedFacts+=requirements.stream().filter(r->List.of("skill","quest","choice").contains(r.kind)).count();
        }
        assertTrue(connected>=600);assertTrue("Typed task coverage "+typed,typed>=150);assertTrue(typedFacts>=150);
        CombatAchievementCatalog.Task sire=catalog.tasks().stream().filter(task->task.boss.equals("Abyssal Sire")).findFirst().get();
        List<CombatAchievementCatalog.Requirement> requirements=catalog.requirements(sire,guides);
        assertTrue(requirements.stream().anyMatch(r->r.kind.equals("skill")&&r.value==85&&r.baseLevelOnly));
        assertTrue(requirements.stream().anyMatch(r->r.kind.equals("access")&&r.key.contains("assignment")));
        CombatAchievementCatalog.Task vorkath=catalog.tasks().stream().filter(task->task.boss.equals("Vorkath")).findFirst().get();
        assertTrue(catalog.requirements(vorkath,guides).stream().anyMatch(r->r.kind.equals("quest")&&r.key.equals("Dragon Slayer II")));
    }
    @Test public void alternativeCaRoutesUseAnyOfAndUnknownAccessDoesNotBecomeAQuestLock()
    {
        CombatAchievementCatalog.Requirement a=new CombatAchievementCatalog.Requirement();a.kind="skill";a.key="Slayer";a.value=85;
        CombatAchievementCatalog.Requirement b=new CombatAchievementCatalog.Requirement();b.kind="skill";b.key="Magic";b.value=50;
        CombatAchievementCatalog.Requirement either=new CombatAchievementCatalog.Requirement();either.kind="choice";either.children=List.of(a,b);
        Map<Skill,Integer> levels=new EnumMap<>(Skill.class);levels.put(Skill.SLAYER,1);levels.put(Skill.MAGIC,50);
        CombatAchievementsTool.Snapshot snapshot=new CombatAchievementsTool.Snapshot("test",1,0,Set.of(),levels,Map.of(),true);
        assertEquals(1,CombatAchievementsTool.requirementState(either,snapshot));
        a.baseLevelOnly=true;assertEquals(0,CombatAchievementsTool.requirementState(a,snapshot));
        CombatAchievementCatalog.Requirement unknown=new CombatAchievementCatalog.Requirement();unknown.kind="access";unknown.key="A hydra assignment";
        assertEquals(0,CombatAchievementsTool.requirementState(unknown,snapshot));
    }
    @Test public void caSnapshotsCopyAccountFactsRatherThanRetainingMutableCallerMaps()
    {
        Map<Skill,Integer> levels=new EnumMap<>(Skill.class);levels.put(Skill.SLAYER,85);Map<String,QuestState> quests=new LinkedHashMap<>();quests.put("Song of the Elves",QuestState.FINISHED);
        CombatAchievementsTool.Snapshot snapshot=new CombatAchievementsTool.Snapshot("test",1,0,Set.of(),levels,quests,true);
        levels.clear();quests.clear();assertEquals(Integer.valueOf(85),snapshot.levels.get(Skill.SLAYER));assertEquals(QuestState.FINISHED,snapshot.quests.get("Song of the Elves"));
    }
    @Test public void diarySupplyFocusDoesNotTreatEveryAlternativeAsRequired()
    {
        DiaryCatalog.Requirement first=new DiaryCatalog.Requirement();first.kind="item";first.itemId=995;
        DiaryCatalog.Requirement alternative=new DiaryCatalog.Requirement();alternative.kind="item";alternative.itemId=590;
        DiaryCatalog.Requirement choice=new DiaryCatalog.Requirement();choice.kind="choice";choice.children=List.of(alternative);
        assertEquals(Set.of(995),DiaryPlannerTool.itemTargets(List.of(first,choice)));
        try{DiaryPlannerTool.itemTargets(List.of(first)).add(1);fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void skillGoalBenefitsUseActualTrainingUnlockLevels()
    {
        Gson gson=new Gson();GuideCatalog guides=new GuideCatalog(gson);GoalCatalog catalog=new GoalCatalog(guides,new ActivityXpCatalog(gson,guides));
        Map<Skill,Integer> levels=new EnumMap<>(Skill.class);levels.put(Skill.MINING,29);GoalCatalog.Facts facts=new GoalCatalog.Facts(Map.of(),levels,Map.of(),100);
        GoalPlan goal=GoalPlan.skill(Skill.MINING,net.runelite.api.Experience.getXpForLevel(61),0);
        List<String> benefits=catalog.benefits(goal,facts);assertFalse(benefits.isEmpty());assertTrue(benefits.stream().allMatch(text->text.startsWith("Level ")||text.endsWith("more training options")));
        assertTrue(benefits.stream().anyMatch(text->text.contains("Motherlode")||text.contains("Mining")));
    }
}
