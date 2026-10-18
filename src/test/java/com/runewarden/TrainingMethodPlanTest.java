/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingMethodPlanTest
{
    private final ActivityXpCatalog catalog=new ActivityXpCatalog(new Gson(),new GuideCatalog(new Gson()));
    @Test public void everyRealSkillHasExplicitPreparationLoopAndGoalposts()
    {
        assertEquals(24,Skill.values().length);
        for(Skill skill:Skill.values())
        {
            List<ActivityXpCatalog.Method> methods=catalog.plans(skill);assertFalse(skill.getName(),methods.isEmpty());
            for(ActivityXpCatalog.Method method:methods)
            {assertFalse(method.id,method.preparation.isEmpty());assertFalse(method.id,method.loop.isEmpty());assertFalse(method.id,method.milestones.isEmpty());assertNotNull(method.id,method.source);}
        }
        assertTrue(catalog.plans(null).size()>=330);
        assertTrue(catalog.plans(null).stream().filter(m->!m.rates.isEmpty()).count()>=109);
        assertTrue(catalog.plans(null).stream().filter(m->m.xpPerAction>0).count()>=115);
    }
    @Test public void dedicatedMeleeVariantsSelectTheCorrectSkillAndShareTheRealGuide()
    {
        ActivityXpCatalog.Method strength=catalog.find("activity:strength-sand-crabs");
        assertEquals(Skill.STRENGTH,strength.skill);assertEquals("training:sand-crabs",strength.guideId);
        assertEquals(4,strength.xpPerAction,0);assertEquals("damage dealt",strength.actionUnit);
        assertEquals(0,catalog.find("activity:hitpoints-sand-crabs").xpPerAction,0);
        assertEquals(0,catalog.find("training:scurrius-training").xpPerAction,0);
    }
    @Test public void structuredUnlocksDistinguishMissingUnknownAndCompletedQuest()
    {
        ActivityXpCatalog.Method method=catalog.find("training:maniacal-monkey-hunter");
        List<TrainingMethodPlan.Requirement> unknown=TrainingMethodPlan.requirements(method,Map.of(Skill.HUNTER,60),Map.of(),126);
        assertFalse(TrainingMethodPlan.blocked(unknown));assertTrue(unknown.stream().anyMatch(r->r.label.contains("Monkey Madness")&&r.state==0));
        List<TrainingMethodPlan.Requirement> missing=TrainingMethodPlan.requirements(method,Map.of(Skill.HUNTER,59),Map.of(GoalCatalog.questKey("Monkey Madness II"),QuestState.NOT_STARTED),126);
        assertTrue(TrainingMethodPlan.blocked(missing));
        List<TrainingMethodPlan.Requirement> ready=TrainingMethodPlan.requirements(method,Map.of(Skill.HUNTER,60),Map.of(GoalCatalog.questKey("Monkey Madness II"),QuestState.FINISHED),126);
        assertTrue(ready.stream().anyMatch(r->r.label.contains("Monkey Madness")&&r.state==1));
    }
    @Test public void levelGoalpostsAreClippedDeduplicatedAndIncludeRealUnlocks()
    {
        long xp=Experience.getXpForLevel(63)+100;
        List<TrainingMethodPlan.Goalpost> path=TrainingMethodPlan.goalposts(catalog,Skill.WOODCUTTING,xp,75,null);
        assertFalse(path.isEmpty());Set<Integer> levels=path.stream().map(p->p.level).collect(Collectors.toSet());assertEquals(levels.size(),path.size());
        assertEquals(75,path.get(path.size()-1).level);assertTrue(path.stream().allMatch(p->p.level>63&&p.level<=75&&p.remainingXp>0));
        assertTrue(path.stream().filter(p->p.level==65).flatMap(p->p.unlocks.stream()).anyMatch(s->s.contains("Sulliuscep")));
        assertTrue(TrainingMethodPlan.goalposts(catalog,Skill.WOODCUTTING,Experience.getXpForLevel(80),75,null).isEmpty());
    }
    @Test public void projectionIntegratesActualLevelTransitionsAndPartialCurrentXp()
    {
        ActivityXpCatalog.Method method=new ActivityXpCatalog.Method();
        ActivityXpCatalog.Rate a=new ActivityXpCatalog.Rate();a.minLevel=1;a.maxLevel=1;a.minXpPerHour=100;a.maxXpPerHour=200;
        ActivityXpCatalog.Rate b=new ActivityXpCatalog.Rate();b.minLevel=2;b.maxLevel=99;b.minXpPerHour=1000;b.maxXpPerHour=2000;
        method.rates.add(a);method.rates.add(b);long first=Experience.getXpForLevel(2)-10,second=Experience.getXpForLevel(3)-Experience.getXpForLevel(2);
        ActivityXpCalculator.Projection result=ActivityXpCalculator.project(method,10,Experience.getXpForLevel(3),null);
        assertTrue(result.complete());assertEquals(first/200d+second/2000d,result.fastestHours,1e-9);assertEquals(first/100d+second/1000d,result.slowestHours,1e-9);
        assertEquals(2,result.segments.size());
    }
    @Test public void unavailableBandsAreExplicitAndPersonalPaceBridgesThem()
    {
        ActivityXpCatalog.Method method=catalog.find("training:mastering-mixology");long current=Experience.getXpForLevel(80),target=Experience.getXpForLevel(82);
        ActivityXpCalculator.Projection missing=ActivityXpCalculator.project(method,current,target,null);
        assertFalse(missing.complete());assertEquals(Experience.getXpForLevel(81)-current,missing.unpricedXp);
        ActivityXpCalculator.Projection personal=ActivityXpCalculator.project(method,current,target,60000d);
        assertTrue(personal.complete());assertEquals((target-current)/60000d,personal.fastestHours,1e-9);
    }
    @Test public void lockedAndOneOffMethodsNeverBecomeRepeatableTimeEstimates()
    {
        ActivityXpCatalog.Method locked=catalog.find("training:trees-redwood");
        assertTrue(ActivityXpCalculator.project(locked,0,Experience.getXpForLevel(99),50000d).locked);
        assertFalse(ActivityXpCalculator.project(catalog.find("training:sailing-charting"),0,10000,50000d).complete());
        for(double rate:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1})
            try{ActivityXpCalculator.project(locked,0,10000,rate);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void scalingReferenceIsIntegratedPerLevelRatherThanExtrapolatingTheStartingLevel()
    {
        ActivityXpCatalog.Method method=catalog.find("activity:wintertodt-no-fletch");long from=Experience.getXpForLevel(50),to=Experience.getXpForLevel(52);
        ActivityXpCalculator.Projection result=ActivityXpCalculator.project(method,from,to,null);
        double expected=(Experience.getXpForLevel(51)-from)/(350000*50d/99)+(to-Experience.getXpForLevel(51))/(350000*51d/99);
        assertEquals(expected,result.fastestHours,1e-9);assertTrue(result.complete());
    }
    @Test public void wineFailuresAndTimeGatedRunsDoNotInventRates()
    {
        ActivityXpCatalog.Method wines=catalog.find("training:wines");assertNull(wines.ratesAt(67));assertEquals(470000,wines.ratesAt(68).minXpPerHour,0);
        assertTrue(catalog.find("training:tree-runs").rates.isEmpty());assertTrue(catalog.find("training:tree-runs").attention.contains("growth"));
        ActivityXpCatalog.Method tithe=catalog.find("training:tithe-farm");if(tithe==null)tithe=catalog.find("minigame:tithe-farm");assertNotNull(tithe);assertFalse(tithe.rates.isEmpty());
        assertEquals(6,ActivityXpCalculator.actionsFor(1001,200));
    }
    @Test public void ratesDoNotOverlapAndReferenceAssumptionsAreRetained()
    {
        for(ActivityXpCatalog.Method method:catalog.plans(null))
        {
            for(int level=1;level<=99;level++){int count=0;for(ActivityXpCatalog.Rate rate:method.rates)if(level>=rate.minLevel&&level<=rate.maxLevel)count++;assertTrue(method.id+" level "+level,count<=1);}
            if(!method.rates.isEmpty())assertFalse(method.id,method.rateConditions.isEmpty());
            if(method.recipeId!=null)assertTrue(method.id,BankedExperienceCalculator.recipes().stream().anyMatch(r->r.id.equals(method.recipeId)));
        }
        assertTrue(catalog.find("training:karambwan").rateConditions.stream().anyMatch(s->s.contains("bait gathering")));
        assertTrue(catalog.find("training:karambwan").rateBasis.contains("oldid=15368158"));
    }
}
