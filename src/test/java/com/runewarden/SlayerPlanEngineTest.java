/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerPlanEngineTest
{
    @Test public void denominatorExcludesBlockedDisabledAndInsufficientStats()
    {
        SlayerPlanningTool.NativeTask a = task(1, 8), blocked = task(2, 20), locked = task(3, 30), low = task(4, 40);
        locked.unlockBit = 123; low = new SlayerPlanningTool.NativeTask(4,4,"Task 4",40,10,20,0,List.of(new SlayerPlanningTool.Requirement(Skill.SLAYER,99)),List.of(),List.of(),null);
        SlayerPlanningTool.Snapshot account = state(List.of(a, blocked, locked, low), Set.of(2), 90); account.bits = Map.of(123, 0);
        Map<Integer, SlayerPlanEngine.Odds> odds = SlayerPlanEngine.odds(account, confirmed(a, blocked, locked, low), "ENABLED");
        assertEquals(1, odds.get(1).minimum, 0); assertTrue(odds.get(1).exact); assertEquals(8, odds.get(1).knownWeight);
        assertEquals(0, odds.get(2).maximum, 0); assertEquals(0, odds.get(3).maximum, 0); assertEquals(0, odds.get(4).maximum, 0);
    }
    @Test public void unconfirmedAccessProducesBoundsInsteadOfFalsePrecision()
    {
        SlayerPlanningTool.NativeTask a = task(1,8), b = task(2,2), c = task(3,3);
        Map<String, SlayerPlanningTool.Rule> rules = confirmed(a,c);
        Map<Integer, SlayerPlanEngine.Odds> odds = SlayerPlanEngine.odds(state(List.of(a,b,c), Set.of(), 99), rules, "ENABLED");
        assertEquals(8.0/13, odds.get(1).minimum, 1e-12); assertEquals(8.0/11, odds.get(1).maximum, 1e-12);
        assertEquals(0, odds.get(2).minimum, 0); assertEquals(2.0/13, odds.get(2).maximum, 1e-12); assertFalse(odds.get(1).exact);
    }
    @Test public void unfinishedNativeIndexCannotAssertPositiveLowerBound()
    {
        SlayerPlanningTool.NativeTask a = task(1,8); SlayerPlanningTool.Snapshot partial = new SlayerPlanningTool.Snapshot("rsprofile.a", 1,5,1,10,List.of(a),Set.of(),true,levels(99),126);
        SlayerPlanEngine.Odds odds = SlayerPlanEngine.odds(partial, confirmed(a), "ENABLED").get(1);
        assertEquals(0, odds.minimum, 0); assertEquals(1, odds.maximum, 0); assertFalse(odds.exact);
    }
    @Test public void personalPreferenceNeverChangesNativeChance()
    {
        SlayerPlanningTool.NativeTask a=task(1,8), b=task(2,2); Map<String,SlayerPlanningTool.Rule> rules=confirmed(a,b);
        rules.get("task1").decision="PREFER";
        assertEquals(.8, SlayerPlanEngine.odds(state(List.of(a,b),Set.of(),99),rules,"ENABLED").get(1).minimum, 1e-12);
        rules.get("task1").decision="BLOCK";
        assertEquals(.8, SlayerPlanEngine.odds(state(List.of(a,b),Set.of(),99),rules,"ENABLED").get(1).minimum, 1e-12);
    }
    @Test public void nativeEligibilityCannotBeOverriddenByManualAccessConfirmation()
    {
        SlayerPlanningTool.NativeTask a=task(1,8); a.unlockBit=101;
        SlayerPlanningTool.Snapshot state=state(List.of(a),Set.of(),99); state.bits=Map.of(101,0);
        assertEquals(SlayerPlanEngine.Access.INELIGIBLE, SlayerPlanEngine.check(a,state,yes(),"DISABLED").access);
        state.bits=Map.of(101,1); assertEquals(SlayerPlanEngine.Access.ELIGIBLE,SlayerPlanEngine.check(a,state,yes(),"DISABLED").access);
    }
    @Test public void nativeTaskBlockRewardIsSeparateFromPersonalBlockPlan()
    {
        SlayerPlanningTool.NativeTask a=task(1,8); a.blockBit=101; SlayerPlanningTool.Snapshot state=state(List.of(a),Set.of(),99); state.bits=Map.of(101,1);
        assertEquals(SlayerPlanEngine.Access.INELIGIBLE, SlayerPlanEngine.check(a,state,yes(),"DISABLED").access);
    }
    @Test public void fossilWyvernBlockCanBeTemporarilyDisabledWithoutInventingASecondBlock()
    {
        SlayerPlanningTool.NativeTask a=task(1,8); a.blockBit=net.runelite.api.gameval.VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK;
        SlayerPlanningTool.Snapshot account=state(List.of(a),Set.of(),99);account.bits=Map.of(a.blockBit,1,net.runelite.api.gameval.VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK,1);
        assertEquals(SlayerPlanEngine.Access.ELIGIBLE,SlayerPlanEngine.check(a,account,yes(),"ENABLED").access);
    }
    @Test public void combatCheckingDialogueChoiceIsExplicit()
    {
        SlayerPlanningTool.NativeTask a=new SlayerPlanningTool.NativeTask(1,1,"Task 1",8,10,20,100,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),null);
        SlayerPlanningTool.Snapshot state=new SlayerPlanningTool.Snapshot("rsprofile.a",1,5,1,1,List.of(a),Set.of(),true,levels(99),80);
        assertEquals(SlayerPlanEngine.Access.UNCHECKED,SlayerPlanEngine.check(a,state,yes(),"UNKNOWN").access);
        assertEquals(SlayerPlanEngine.Access.INELIGIBLE,SlayerPlanEngine.check(a,state,yes(),"ENABLED").access);
        assertEquals(SlayerPlanEngine.Access.ELIGIBLE,SlayerPlanEngine.check(a,state,yes(),"DISABLED").access);
    }
    @Test public void alternativeStatRequirementNeedsOnlyOneSatisfiedStat()
    {
        SlayerPlanningTool.NativeTask a=new SlayerPlanningTool.NativeTask(1,1,"Task 1",8,10,20,0,List.of(),List.of(new SlayerPlanningTool.Requirement(Skill.STRENGTH,60),new SlayerPlanningTool.Requirement(Skill.AGILITY,60)),List.of(),null);
        Map<Skill,Integer> levels=levels(40); levels.put(Skill.STRENGTH,60);
        SlayerPlanningTool.Snapshot state=new SlayerPlanningTool.Snapshot("rsprofile.a",1,5,1,1,List.of(a),Set.of(),true,levels,126);
        assertEquals(SlayerPlanEngine.Access.ELIGIBLE,SlayerPlanEngine.check(a,state,yes(),"ENABLED").access);
    }
    @Test public void unknownUnlockIdentityDoesNotBecomeAnUnlockedTask()
    {
        SlayerPlanningTool.NativeTask a=new SlayerPlanningTool.NativeTask(1,1,"Task 1",8,10,20,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),"Optional unlock");
        assertEquals(SlayerPlanEngine.Access.UNCHECKED,SlayerPlanEngine.check(a,state(List.of(a),Set.of(),99),yes(),"ENABLED").access);
    }
    @Test public void skipsUseGeometricFailuresBeforeEachAcceptedTaskAndBlocksRemoveWeight()
    {
        SlayerPlanningTool.NativeTask a=task(1,8), b=task(2,3), c=task(3,2); Map<String,SlayerPlanningTool.Rule> rules=confirmed(a,b,c);
        rules.get("task2").decision="SKIP";rules.get("task3").decision="BLOCK";
        SlayerPlanEngine.Projection p=SlayerPlanEngine.project(state(List.of(a,b,c),Set.of(),99),rules,"ENABLED",false,10,9,SlayerPlanningTool.defaultRewards(5));
        assertTrue(p.available);assertEquals(.375,p.skipsPerCompleted,1e-12);assertEquals(11.25,p.skipPointsPerCompleted,1e-12);
        assertEquals(1,p.plannedBlocks);assertEquals(100,p.plannedBlockCost);assertEquals(210,p.rewards);assertEquals(-2.5,p.netPoints,1e-12);
    }
    @Test public void alreadyBlockedTasksDoNotChargeAnotherBlockCost()
    {
        SlayerPlanningTool.NativeTask a=task(1,8),b=task(2,2); Map<String,SlayerPlanningTool.Rule> rules=confirmed(a,b);rules.get("task2").decision="BLOCK";
        SlayerPlanEngine.Projection p=SlayerPlanEngine.project(state(List.of(a,b),Set.of(2),99),rules,"ENABLED",false,1,9,SlayerPlanningTool.defaultRewards(5));
        assertEquals(0,p.plannedBlocks);assertEquals(75,p.netPoints,0);
    }
    @Test public void unknownAccessRequiresExplicitScenarioAssumption()
    {
        SlayerPlanningTool.NativeTask a=task(1,8);
        assertFalse(SlayerPlanEngine.project(state(List.of(a),Set.of(),99),Map.of(),"ENABLED",false,10,9,SlayerPlanningTool.defaultRewards(5)).available);
        SlayerPlanEngine.Projection p=SlayerPlanEngine.project(state(List.of(a),Set.of(),99),Map.of(),"ENABLED",true,10,9,SlayerPlanningTool.defaultRewards(5));
        assertTrue(p.available);assertTrue(p.conditional);
    }
    @Test public void anAllSkippedPlanHasNoFiniteCompletedTaskProjection()
    {
        SlayerPlanningTool.NativeTask a=task(1,8);Map<String,SlayerPlanningTool.Rule> rules=confirmed(a);rules.get("task1").decision="SKIP";
        assertFalse(SlayerPlanEngine.project(state(List.of(a),Set.of(),99),rules,"ENABLED",false,10,0,SlayerPlanningTool.defaultRewards(5)).available);
    }
    @Test public void partialPersonalRatesStayUnratedInsteadOfCountingMissingAsZero()
    {
        SlayerPlanningTool.NativeTask a=task(1,8),b=task(2,2);Map<String,SlayerPlanningTool.Rule> rules=confirmed(a,b);
        SlayerPlanningTool.Rule r=rules.get("task1");r.killsPerHour=100;r.xpKnown=true;r.xpPerKill=100;r.gpKnown=true;r.netGpPerKill=0;
        SlayerPlanEngine.Projection p=SlayerPlanEngine.project(state(List.of(a,b),Set.of(),99),rules,"ENABLED",false,10,9,SlayerPlanningTool.defaultRewards(5));
        assertTrue(Double.isNaN(p.estimatedMinutes));assertTrue(Double.isNaN(p.estimatedXp));assertTrue(Double.isNaN(p.estimatedNetGp));
    }
    @Test public void personalTaskCreditCountAndNegativeSupplyAdjustedProfitAreRespected()
    {
        SlayerPlanningTool.NativeTask a=task(1,8);Map<String,SlayerPlanningTool.Rule> rules=confirmed(a); SlayerPlanningTool.Rule r=rules.get("task1");
        r.plannedCount=40;r.killsPerHour=80;r.xpKnown=true;r.xpPerKill=120;r.gpKnown=true;r.netGpPerKill=-30;
        SlayerPlanEngine.Projection p=SlayerPlanEngine.project(state(List.of(a),Set.of(),99),rules,"ENABLED",false,2,9,SlayerPlanningTool.defaultRewards(5));
        assertEquals(60,p.estimatedMinutes,1e-12);assertEquals(9600,p.estimatedXp,1e-12);assertEquals(-2400,p.estimatedNetGp,1e-12);
    }
    @Test public void pointBonusesUseTheHighestApplicableMilestoneAndFirstFourGiveZero()
    {
        int[] schedule=SlayerPlanningTool.defaultRewards(5);
        assertEquals(0,SlayerPlanEngine.reward(4,schedule));assertEquals(15,SlayerPlanEngine.reward(5,schedule));
        assertEquals(75,SlayerPlanEngine.reward(10,schedule));assertEquals(225,SlayerPlanEngine.reward(50,schedule));
        assertEquals(375,SlayerPlanEngine.reward(100,schedule));assertEquals(525,SlayerPlanEngine.reward(250,schedule));assertEquals(750,SlayerPlanEngine.reward(1000,schedule));
        assertEquals(30,SlayerPlanEngine.reward(10,SlayerPlanningTool.defaultRewards(2)));
    }
    @Test public void activeUnverifiedWeightModifierCannotProduceAnExactPercentage()
    {
        SlayerPlanningTool.NativeTask a=task(1,8);a.weightChanges=List.of(new SlayerPlanningTool.WeightChange(99,5));SlayerPlanningTool.Snapshot account=state(List.of(a),Set.of(),99);account.bits=Map.of(99,1);
        SlayerPlanEngine.Odds odds=SlayerPlanEngine.odds(account,confirmed(a),"ENABLED").get(1);assertFalse(odds.exact);assertEquals(0,odds.minimum,0);assertEquals(1,odds.maximum,0);
        assertFalse(SlayerPlanEngine.project(account,confirmed(a),"ENABLED",false,10,9,SlayerPlanningTool.defaultRewards(5)).available);
    }
    @Test public void masterAccessUsesObservedQuestsAndLevelGatesWithoutAssumingCapeOwnership()
    {
        SlayerPlanningTool.Snapshot account=state(List.of(task(1,8)),Set.of(),90);account.quests=Map.of("SHILO_VILLAGE","NOT_STARTED");
        assertTrue(SlayerPlanningTool.masterAccess(5,account).startsWith("Quest required"));account.quests=Map.of("SHILO_VILLAGE","FINISHED");assertEquals("Standard access met",SlayerPlanningTool.masterAccess(5,account));
        assertEquals("Access unchecked",SlayerPlanningTool.masterAccess(10,account));
    }
    @Test public void masterFilterHidesKnownLocksAndPreservesUnverifiedOrExplicitlySelectedMasters()
    {
        SlayerPlanningTool.Snapshot low = new SlayerPlanningTool.Snapshot("rsprofile.a",1,1,1,1,List.of(task(1,8)),Set.of(),true,levels(20),20);
        low.quests = Map.of("LOST_CITY","NOT_STARTED","SHILO_VILLAGE","NOT_STARTED","PRIEST_IN_PERIL","FINISHED");
        List<Integer> visible = SlayerPlanningTool.masterChoices(low,true,0);assertTrue(visible.contains(1));assertTrue(visible.contains(2));assertFalse(visible.contains(5));assertTrue(visible.contains(10));
        assertTrue(SlayerPlanningTool.masterChoices(low,true,5).contains(5));assertEquals(10,SlayerPlanningTool.masterChoices(low,false,0).size());
    }
    @Test public void observedDiaryRewardsAdjustTheCorrectMasterSchedule()
    {
        SlayerPlanningTool.Snapshot konar = new SlayerPlanningTool.Snapshot("rsprofile.a",1,8,1,1,List.of(task(1,8)),Set.of(),true,levels(99),126);
        konar.bits = Map.of(net.runelite.api.gameval.VarbitID.KOUREND_ELITE_REWARD,1);
        assertArrayEquals(new int[]{20,100,300,500,700,1000},SlayerPlanningTool.observedRewards(konar));
        SlayerPlanningTool.Snapshot nieve = new SlayerPlanningTool.Snapshot("rsprofile.a",1,6,1,1,List.of(task(1,8)),Set.of(),true,levels(99),126);
        nieve.bits = Map.of(net.runelite.api.gameval.VarbitID.WESTERN_ELITE_REWARD,1);
        assertArrayEquals(SlayerPlanningTool.defaultRewards(5),SlayerPlanningTool.observedRewards(nieve));
    }
    @Test public void oldVersionOnePreferencesAcquireSafeDefaultPlanningFields()
    {
        SlayerPlanningTool.Plan plan=new Gson().fromJson("{\"version\":1,\"masters\":{\"5\":{\"dragons\":{\"decision\":\"DO\",\"effort\":\"Normal\",\"note\":\"Have dragonbane gear\"}}}}",SlayerPlanningTool.Plan.class);
        assertTrue(SlayerPlanningTool.valid(plan));assertEquals("AUTO",plan.masters.get(5).get("dragons").access);assertTrue(plan.settings.isEmpty());
    }
    @Test public void invalidRatesAndMalformedSuppliesAreRejected()
    {
        SlayerPlanningTool.Rule rule=yes();rule.killsPerHour=Double.NaN;assertFalse(SlayerPlanningTool.validRule(rule));rule.killsPerHour=100;rule.supplies=List.of(-1);assertFalse(SlayerPlanningTool.validRule(rule));
        assertEquals(List.of(4162,560),SlayerPlanningTool.parseIds("4162, 560 4162"));
        try { SlayerPlanningTool.parseIds("-1"); fail(); }catch(IllegalArgumentException expected){}
    }
    @Test public void curatedMethodsContainContextAndSourcesWithoutInventedDefaultRates()
    {
        SlayerMethodCatalog catalog=new SlayerMethodCatalog(new Gson());assertTrue(catalog.size() >= 30);
        assertTrue(catalog.forTask("Steel dragons").stream().anyMatch(method->method.note.contains("dragonbane")));
        assertTrue(catalog.forTask("Dust devils").stream().allMatch(method->method.magic==70 && "DESERT_TREASURE_I".equals(method.quest)));
        assertTrue(catalog.forTask("Gargoyles").get(0).source.startsWith("https://oldschool.runescape.wiki/"));
    }
    private static SlayerPlanningTool.NativeTask task(int id,int weight)
    { return new SlayerPlanningTool.NativeTask(id,id,"Task "+id,weight,10,20,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),null); }
    private static Map<Skill,Integer> levels(int value) {Map<Skill,Integer> result=new EnumMap<>(Skill.class);for(Skill skill:Skill.values())result.put(skill,value);return result;}
    private static SlayerPlanningTool.Snapshot state(List<SlayerPlanningTool.NativeTask> tasks,Set<Integer> blocked,int level)
    {return new SlayerPlanningTool.Snapshot("rsprofile.a",1,5,tasks.size(),tasks.size(),tasks,blocked,true,levels(level),126);}
    private static SlayerPlanningTool.Rule yes(){SlayerPlanningTool.Rule rule=new SlayerPlanningTool.Rule();rule.access="YES";return rule;}
    private static Map<String,SlayerPlanningTool.Rule> confirmed(SlayerPlanningTool.NativeTask... tasks)
    {Map<String,SlayerPlanningTool.Rule> result=new LinkedHashMap<>();for(SlayerPlanningTool.NativeTask task:tasks)result.put(SlayerTaskCatalog.normalize(task.name),yes());return result;}
}
