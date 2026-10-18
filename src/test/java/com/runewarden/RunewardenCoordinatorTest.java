/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.ServerNpcLoot;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real shared-session assertions across immutable producer boundaries; no mocked final services. */
public class RunewardenCoordinatorTest
{
    static final GuideCatalog GUIDES=new GuideCatalog(new Gson());
    static final ActivityXpCatalog METHODS=new ActivityXpCatalog(new Gson(),GUIDES);
    static final String A="slayer:38:0:0:10",B="slayer:19:0:0:10";
    static final class Fixture
    {
        final ActivitySessionTest.Fixture nativeState=new ActivitySessionTest.Fixture();
        final AtomicReference<MinigameRunProgress.View> mini=new AtomicReference<>(MinigameRunProgress.View.EMPTY);
        final AtomicReference<SlayerCoachTool.ActivityObservation> slayer=new AtomicReference<>(SlayerCoachTool.ActivityObservation.empty());
        final AtomicReference<FarmPatchPlanner.RunReading> farm=new AtomicReference<>();
        final AtomicReference<AccountProgressTool.Snapshot> account=new AtomicReference<>(AccountProgressTool.Snapshot.empty());
        final Map<String,Long> currencies=new LinkedHashMap<>();
        final RunewardenCoordinator coordinator=new RunewardenCoordinator(nativeState.client,nativeState.config,nativeState.session,mini::get,slayer::get,farm::get,GUIDES,METHODS,account::get,currencies::put);
        void tick(){coordinator.onGameTick();}
        long count(String key){SharedActivitySession.Counter counter=nativeState.session.currentRecord().outcomes.get(key);return counter==null?0:counter.value;}
        void start(SharedActivitySession.Context context){assertTrue(nativeState.session.startActivity(context));tick();}
    }
    static SlayerCoachTool.ActivityObservation task(String id,int remaining,long credits,long completed,long points)
    {return new SlayerCoachTool.ActivityObservation("rsprofile.alice",id,id.equals(B)?"Bloodvelds":remaining==0?"No active assignment":"Abyssal demons",null,"",remaining,2,100,remaining==0?11:10,Set.of(415),Set.of(),credits,completed,points);}
    static MinigameRunProgress.View mini(String activity,long entry,String phase,int changes,int score,String wave)
    {return new MinigameRunProgress.View(activity,activity,"training:"+activity,phase,entry,changes+1,changes,"Points",score,wave,List.of(phase));}
    static FarmPatchPlanner.RunReading farm(String profile,String guide,long start,int completed,int auto,boolean active)
    {
        FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();route.stops=new ArrayList<>(List.of("a","b","c"));route.startedAt=start;route.active=active;
        for(int i=0;i<completed;i++)route.completed.add(route.stops.get(i));for(int i=0;i<auto;i++)route.evidence.put(route.stops.get(i),"Native replant observed");
        return new FarmPatchPlanner.RunReading(profile,guide,route,null,null,0,0,Map.of(),List.of(),0,0,0,null,null,0,0,FarmTravelPlan.summarize(List.of()),0);
    }
    static SharedActivitySession.Context boss(String guide)
    {GuideCatalog.Entry entry=GUIDES.entry(guide);return new SharedActivitySession.Context(SharedActivitySession.Kind.BOSS,"boss:"+entry.id,entry.title,entry.id,null,null,0);}
    static ServerNpcLoot loot(String name)
    {NPCComposition npc=mock(NPCComposition.class);when(npc.getName()).thenReturn(name);return new ServerNpcLoot(npc,List.of());}
    static AccountProgressTool.Snapshot facts(String profile,Map<String,QuestState> quests,Map<String,Long> values)
    {return new AccountProgressTool.Snapshot(profile,1,values,new AccountProgressTool.Saved(),new EnumMap<>(Skill.class),quests,0,true);}

    @Test public void slayerAssignmentCreditsAndCompletionCloseOnlyTheExactObservedAssignment()
    {
        Fixture f=new Fixture();f.slayer.set(task(A,2,0,0,0));f.start(RunewardenCoordinator.slayerContext(f.slayer.get()));
        f.slayer.set(task(A,1,1,0,0));f.tick();assertEquals(1,f.count("slayer-credits"));
        f.slayer.set(task("slayer:none:11",0,2,1,18));f.tick();assertEquals(2,f.count("slayer-credits"));assertEquals(1,f.count("slayer-tasks"));assertEquals(18,f.count("slayer-points"));
        assertEquals(1,f.nativeState.session.currentRecord().milestones.size());f.tick();assertEquals(1,f.count("slayer-tasks"));
        f.slayer.set(task(B,10,3,1,18));f.tick();assertEquals(2,f.count("slayer-credits"));
    }
    @Test public void sameKindDifferentTaskCannotPolluteAManuallyStartedSlayerRun()
    {
        Fixture f=new Fixture();f.slayer.set(task(B,10,100,0,0));f.start(RunewardenCoordinator.slayerContext(task(A,2,0,0,0)));
        f.slayer.set(task(B,9,101,0,0));f.tick();assertEquals(0,f.count("slayer-credits"));assertTrue(f.nativeState.session.currentRecord().milestones.isEmpty());
    }
    @Test public void callbackUsesExactNativeIdAndRejectsStaleProfileTaskAndArea()
    {
        SlayerPlanningTool.Prepared prepared=new SlayerPlanningTool.Prepared("rsprofile.alice","Abyssal demons",38,"Abyssal demon",null,"",2,Set.of(415),Set.of());
        assertEquals(A,RunewardenCoordinator.slayerContext(prepared,task(A,2,0,0,0)).id);
        assertNull(RunewardenCoordinator.slayerContext(prepared,task(B,2,0,0,0)));assertNull(RunewardenCoordinator.slayerContext(prepared,task("slayer:none:11",0,2,1,18)));
        SlayerCoachTool.ActivityObservation otherProfile=new SlayerCoachTool.ActivityObservation("rsprofile.bob",A,"Abyssal demons",null,"",2,2,0,10,Set.of(),Set.of(),0,0,0);
        assertNull(RunewardenCoordinator.slayerContext(prepared,otherProfile));
        SlayerCoachTool.ActivityObservation otherArea=new SlayerCoachTool.ActivityObservation("rsprofile.alice",A,"Abyssal demons","Slayer Tower","",2,2,0,10,Set.of(),Set.of(),0,0,0);
        SlayerPlanningTool.Prepared restricted=new SlayerPlanningTool.Prepared("rsprofile.alice","Abyssal demons",38,"Abyssal demon","Catacombs of Kourend","",2,Set.of(415),Set.of());
        assertNull(RunewardenCoordinator.slayerContext(restricted,otherArea));
    }
    @Test public void pausedAndTemporarilyDisabledProducersRebaselineInsteadOfCatchingUp()
    {
        Fixture f=new Fixture();f.slayer.set(task(A,20,0,0,0));f.start(RunewardenCoordinator.slayerContext(f.slayer.get()));f.nativeState.session.pause();
        f.slayer.set(task(A,15,5,0,0));f.tick();f.nativeState.session.resume();f.slayer.set(task(A,14,6,0,0));f.tick();assertEquals(1,f.count("slayer-credits"));
        f.slayer.set(SlayerCoachTool.ActivityObservation.empty());f.tick();f.slayer.set(task(A,4,16,0,0));f.tick();assertEquals(1,f.count("slayer-credits"));
        f.slayer.set(task(A,3,17,0,0));f.tick();assertEquals(2,f.count("slayer-credits"));
    }
    @Test public void offlineAndProfileTransitionsCannotCatchUpNativeLifetimeCounters()
    {
        Fixture f=new Fixture();f.slayer.set(task(A,20,0,0,0));f.start(RunewardenCoordinator.slayerContext(f.slayer.get()));
        when(f.nativeState.client.getGameState()).thenReturn(GameState.HOPPING);f.nativeState.session.suspend();f.tick();f.slayer.set(task(A,5,15,0,0));
        when(f.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);f.nativeState.session.onGameTick();f.tick();assertEquals(0,f.count("slayer-credits"));
        f.slayer.set(task(A,4,16,0,0));f.tick();assertEquals(1,f.count("slayer-credits"));
        f.nativeState.profile.set("rsprofile.bob");f.nativeState.session.onGameTick();f.nativeState.session.setAutoDetect(true);NPC npc=mock(NPC.class);when(npc.getId()).thenReturn(415);when(npc.getName()).thenReturn("Abyssal demon");when(f.nativeState.player.getInteracting()).thenReturn(npc);
        f.tick();assertNull(f.nativeState.session.snapshot().runId);
    }
    @Test public void assignedTaskAtBankDoesNotAutomaticallyStartARun()
    {
        Fixture f=new Fixture();f.nativeState.session.setAutoDetect(true);f.slayer.set(task(A,20,0,0,0));f.tick();assertNull(f.nativeState.session.snapshot().runId);
        NPC npc=mock(NPC.class);when(npc.getId()).thenReturn(415);when(npc.getName()).thenReturn("Abyssal demon");when(f.nativeState.player.getInteracting()).thenReturn(npc);
        Widget bank=mock(Widget.class);when(f.nativeState.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);f.tick();assertNull(f.nativeState.session.snapshot().runId);
        when(bank.isHidden()).thenReturn(true);f.tick();assertEquals(A,f.nativeState.session.snapshot().context.id);assertTrue(f.nativeState.session.currentRecord().detected);
    }
    @Test public void farmingCountersRequireTheExactRouteStartAndKeepManualAndObservedSeparate()
    {
        Fixture f=new Fixture();FarmPatchPlanner.RunReading initial=farm("rsprofile.alice","training:herb-runs",1000,0,0,true);f.farm.set(initial);f.start(RunewardenCoordinator.farmContext(initial));
        f.farm.set(farm("rsprofile.alice","training:herb-runs",1000,1,0,true));f.tick();assertEquals(1,f.count("farm-stops"));assertEquals(0,f.count("farm-replants"));
        f.farm.set(farm("rsprofile.alice","training:herb-runs",1000,3,1,true));f.tick();assertEquals(3,f.count("farm-stops"));assertEquals(1,f.count("farm-replants"));assertEquals(1,f.nativeState.session.currentRecord().milestones.size());
        f.farm.set(farm("rsprofile.alice","training:herb-runs",2000,0,0,true));f.tick();f.farm.set(farm("rsprofile.alice","training:herb-runs",2000,1,1,true));f.tick();assertEquals(3,f.count("farm-stops"));assertEquals(1,f.count("farm-replants"));
    }
    @Test public void farmingBaselineDoesNotImportProgressObservedBeforeRunOrWhileRoutePaused()
    {
        Fixture f=new Fixture();FarmPatchPlanner.RunReading initial=farm("rsprofile.alice","training:herb-runs",1000,1,0,true);f.farm.set(initial);f.start(RunewardenCoordinator.farmContext(initial));assertEquals(0,f.count("farm-stops"));
        f.farm.set(farm("rsprofile.alice","training:herb-runs",1000,2,1,false));f.tick();assertEquals(0,f.count("farm-stops"));
        f.farm.set(farm("rsprofile.alice","training:herb-runs",1000,3,1,true));f.tick();assertEquals(1,f.count("farm-stops"));assertEquals(0,f.count("farm-replants"));
    }
    @Test public void minigameStartsFromBaselineAndPhaseOrScoreNeverBecomesAHistoricalWin()
    {
        Fixture f=new Fixture();f.mini.set(mini("wintertodt",1,"Reward phase",8,500,""));f.start(RunewardenCoordinator.minigameContext(f.mini.get()));
        assertTrue(f.nativeState.session.currentRecord().outcomes.isEmpty());assertTrue(f.nativeState.session.currentRecord().milestones.isEmpty());
        f.mini.set(mini("wintertodt",1,"Gather roots",9,520,""));f.tick();assertEquals(1,f.count("minigame-phase-changes"));assertEquals(20,f.count("minigame-score-increases"));assertEquals(0,f.count("wins"));
        assertTrue(f.nativeState.session.currentRecord().milestones.get(0).title.startsWith("Observed"));
        f.mini.set(mini("wintertodt",2,"Reward phase",0,900,""));f.tick();f.mini.set(mini("wintertodt",2,"Feed roots",1,950,""));f.tick();
        assertEquals(1,f.count("minigame-phase-changes"));assertEquals(20,f.count("minigame-score-increases"));
    }
    @Test public void anotherMinigameCannotPolluteManualRunAndRoundResetCannotInventPoints()
    {
        Fixture f=new Fixture();f.mini.set(mini("barbarian_assault",1,"Wave one",0,100,"1"));f.start(RunewardenCoordinator.minigameContext(f.mini.get()));
        f.mini.set(mini("barbarian_assault",1,"Wave two",1,200,"2"));f.tick();assertEquals(0,f.count("minigame-score-increases"));assertEquals(2,f.nativeState.session.currentRecord().milestones.size());
        f.mini.set(mini("tempoross",1,"Fish",0,300,""));f.tick();f.mini.set(mini("tempoross",1,"Load",1,500,""));f.tick();assertEquals(0,f.count("minigame-score-increases"));assertEquals(1,f.count("minigame-phase-changes"));
    }
    @Test public void matchingExplicitTrainingMethodMayTrackNativePhasesAcrossItsOwnInstances()
    {
        Fixture f=new Fixture();f.mini.set(mini("wintertodt",1,"Gather",0,0,""));f.start(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"activity:wintertodt-fletch","Fletch kindling","training:wintertodt","activity:wintertodt-fletch",Skill.FIREMAKING,0));
        f.mini.set(mini("wintertodt",1,"Feed",1,20,""));f.tick();assertEquals(1,f.count("minigame-phase-changes"));
        f.mini.set(mini("wintertodt",2,"Gather",0,100,""));f.tick();f.mini.set(mini("wintertodt",2,"Feed",1,120,""));f.tick();assertEquals(2,f.count("minigame-phase-changes"));assertEquals(40,f.count("minigame-score-increases"));
    }
    @Test public void foreignImmutableMinigameViewCannotStartOrContributeAndUnrelatedRecipeCannotBorrowGuideEvidence()
    {
        Fixture f=new Fixture();f.nativeState.session.setAutoDetect(true);f.mini.set(new MinigameRunProgress.View("wintertodt","Wintertodt","training:wintertodt","Reward",1,1,0,"Points",500,"",List.of("Reward"),"rsprofile.bob"));
        f.tick();assertNull(f.nativeState.session.snapshot().runId);
        f.mini.set(mini("wintertodt",1,"Gather",0,0,""));f.start(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"crafting","Diamond bracelets","training:wintertodt","crafting:diamond-bracelet",Skill.CRAFTING,0));
        f.mini.set(mini("wintertodt",1,"Feed",1,100,""));f.tick();assertEquals(0,f.count("minigame-phase-changes"));assertTrue(f.nativeState.session.currentRecord().milestones.isEmpty());
    }
    @Test public void questCompletionRequiresLiveSameQuestTransitionAndReenableUsesNewBaseline()
    {
        Fixture f=new Fixture();GuideCatalog.Entry quest=GUIDES.entry("quest:Cook's Assistant");String key=GoalCatalog.questKey(quest.title);
        f.account.set(facts("rsprofile.alice",Map.of(key,QuestState.IN_PROGRESS),Map.of()));f.start(new SharedActivitySession.Context(SharedActivitySession.Kind.QUEST,quest.id,quest.title,quest.id,null,null,0));
        f.account.set(facts("rsprofile.alice",Map.of(key,QuestState.FINISHED),Map.of()));f.tick();assertEquals(1,f.count("quest-completed"));f.tick();assertEquals(1,f.count("quest-completed"));
        f.account.set(AccountProgressTool.Snapshot.empty());f.tick();f.account.set(facts("rsprofile.alice",Map.of(key,QuestState.IN_PROGRESS),Map.of()));f.tick();assertEquals(1,f.count("quest-completed"));
        f.nativeState.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.QUEST,quest.id,quest.title,quest.id,null,null,0));f.account.set(facts("rsprofile.alice",Map.of(key,QuestState.FINISHED),Map.of()));f.tick();assertEquals(0,f.count("quest-completed"));
    }
    @Test public void bossLootRequiresExactGuideNameOnlineProfileAndDeduplicatesSameEvent()
    {
        Fixture f=new Fixture();f.start(boss("boss:sarachnis"));ServerNpcLoot event=loot("Sarachnis");f.coordinator.onServerNpcLoot(event);f.coordinator.onServerNpcLoot(event);assertEquals(1,f.count("boss-clears"));
        f.coordinator.onServerNpcLoot(loot("Zulrah"));assertEquals(1,f.count("boss-clears"));f.nativeState.session.pause();f.coordinator.onServerNpcLoot(loot("Sarachnis"));assertEquals(1,f.count("boss-clears"));f.nativeState.session.resume();
        when(f.nativeState.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);f.coordinator.onServerNpcLoot(loot("Sarachnis"));assertEquals(1,f.count("boss-clears"));when(f.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        f.nativeState.profile.set("rsprofile.bob");f.coordinator.onServerNpcLoot(loot("Sarachnis"));f.nativeState.profile.set("rsprofile.alice");assertEquals(1,f.count("boss-clears"));
        f.nativeState.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.BOSS,"manual","Sarachnis","boss:zulrah",null,null,0));f.coordinator.onServerNpcLoot(loot("Sarachnis"));assertEquals(0,f.count("boss-clears"));
    }
    @Test public void spendableCurrencyUsesOnlyTheCurrentProfilesLiveNativeSnapshot()
    {
        Fixture f=new Fixture();AccountProgressTool.Metric metric=AccountProgressTool.METRICS.stream().filter(m->m.spendable).findFirst().get();
        f.account.set(facts("rsprofile.bob",Map.of(),Map.of(metric.key,999L)));f.tick();assertTrue(f.currencies.isEmpty());f.account.set(facts("rsprofile.alice",Map.of(),Map.of(metric.key,123L)));f.tick();assertEquals(Long.valueOf(123),f.currencies.get(metric.label));
    }
}
