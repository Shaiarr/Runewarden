/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ActivityHubTest
{
    static SharedActivitySession.Context context(SharedActivitySession.Kind kind,String id)
    {return new SharedActivitySession.Context(kind,id,kind+" activity",null,null,kind==SharedActivitySession.Kind.TRAINING?Skill.WOODCUTTING:null,0);}
    @Test public void allActivityKindsShareOneCompleteJournalAndStableRunIdentity()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();
        for(SharedActivitySession.Kind kind:SharedActivitySession.Kind.values())
        {
            f.session.startActivity(context(kind,kind.name()));String id=f.session.snapshot().runId;f.tick(5);
            f.session.observeXp(id,Skill.WOODCUTTING,120);f.session.observeXp(id,Skill.FIREMAKING,40);
            f.session.observeOutcome("completions","Completions",2);f.session.reportLoot(id,1000,200,3,1,Map.of("Reward chest",2L));
            assertTrue(f.session.reachMilestone("practice","Practice target complete","goals"));f.session.finish();f.session.finish();
            SharedActivitySession.RunRecord record=f.session.history().get(0);assertEquals(id,record.runId);assertEquals(kind,record.context.kind);
            assertEquals(160,record.totalXp());assertEquals(800,record.netLoot());assertEquals(2,record.outcomes.get("completions").value);assertEquals(1,record.milestones.size());assertEquals(3000,record.activeMillis);
        }
        assertEquals(SharedActivitySession.Kind.values().length,f.session.history().size());
    }
    @Test public void detectionIsOptInAndCannotReplaceManualOrPausedRun()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();SharedActivitySession.Context detected=context(SharedActivitySession.Kind.MINIGAME,"game");
        assertFalse(f.session.offerDetected(detected));assertNull(f.session.snapshot().runId);assertEquals(detected,f.session.snapshot().selected);
        f.session.setAutoDetect(true);assertTrue(f.session.offerDetected(detected));String id=f.session.snapshot().runId;assertTrue(f.session.currentRecord().detected);
        assertFalse(f.session.offerDetected(context(SharedActivitySession.Kind.BOSS,"boss")));assertEquals(id,f.session.snapshot().runId);
        f.session.pause();assertFalse(f.session.offerDetected(context(SharedActivitySession.Kind.QUEST,"quest")));assertEquals(id,f.session.snapshot().runId);
        f.session.finish();assertTrue(f.session.offerDetected(context(SharedActivitySession.Kind.BOSS,"boss")));assertNotEquals(id,f.session.snapshot().runId);
    }
    @Test public void endingDetectionKeepsItDismissedAcrossContextGapsUntilAnExplicitStart()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();SharedActivitySession.Context detected=context(SharedActivitySession.Kind.MINIGAME,"entry:1");
        f.session.setAutoDetect(true);assertTrue(f.session.offerDetected(detected));String first=f.session.snapshot().runId;f.session.finish();
        assertFalse(f.session.offerDetected(detected));assertEquals(first,f.session.snapshot().runId);f.session.offerDetected(null);
        assertFalse(f.session.offerDetected(detected));assertEquals(first,f.session.snapshot().runId);
        assertTrue(f.session.startSelected());assertNotEquals(first,f.session.snapshot().runId);
    }
    @Test public void startingNextActivityArchivesAllMetricsBeforeTheyCanBeReset()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();f.session.startActivity(context(SharedActivitySession.Kind.BOSS,"one"));String id=f.session.snapshot().runId;
        f.session.observeXp(id,Skill.RANGED,1234);f.session.reportLoot(id,500,900,1,0,Map.of());f.session.startActivity(context(SharedActivitySession.Kind.MINIGAME,"two"));
        assertEquals(-400,f.session.history().get(0).netLoot());assertEquals(1234,f.session.history().get(0).totalXp());assertEquals(0,f.session.currentRecord().totalXp());
        f.session.observeXp(id,Skill.RANGED,99);assertEquals(0,f.session.currentRecord().totalXp());
    }
    @Test public void journalMetricsResumePausedAndRemainCharacterIsolated()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();f.session.startActivity(context(SharedActivitySession.Kind.SLAYER,"task"));String id=f.session.snapshot().runId;
        f.session.observeOutcome("credits","Assignment credits",7);f.session.reportTraining(id,300,3,1200);f.session.stop();f.session.start();f.session.onGameTick();
        assertEquals(SharedActivitySession.State.PAUSED,f.session.snapshot().state);assertEquals(7,f.session.currentRecord().outcomes.get("credits").value);assertEquals(3,f.session.currentRecord().actions);
        f.session.resume();f.session.finish();f.profile.set("rsprofile.bob");assertNull(f.session.currentRecord());assertTrue(f.session.history().isEmpty());f.session.onGameTick();assertTrue(f.session.history().isEmpty());
        f.profile.set("rsprofile.alice");f.session.onGameTick();assertEquals(1,f.session.history().size());assertEquals(7,f.session.history().get(0).outcomes.get("credits").value);
    }
    @Test public void futureJournalCannotBeOverwrittenAndOldCheckpointMigratesWithoutFabricatingXp()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();f.session.startActivity(context(SharedActivitySession.Kind.FARMING,"farm"));f.session.stop();
        f.settings.remove(ActivitySessionTest.key("runewardenactivity","rsprofile.alice","runMetricsV1"));String future="{\"version\":8,\"runs\":[]}";
        String address=ActivitySessionTest.key("runewardenactivity","rsprofile.alice","runHistoryV1");f.settings.put(address,future);f.session.start();f.session.onGameTick();
        assertTrue(f.session.currentRecord().partial);assertEquals(0,f.session.currentRecord().totalXp());f.session.finish();assertEquals(future,f.settings.get(address));
    }
    @Test public void oneNativeXpEventCountsSecondarySkillsOnceAndNeverCatchesUpPausedOrOfflineXp()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();AtomicInteger fire=new AtomicInteger(2000);when(f.client.getSkillExperience(Skill.FIREMAKING)).thenAnswer(c->fire.get());
        ActivityHubTool hub=new ActivityHubTool(f.client,mock(ClientThread.class),f.config,f.session);hub.start();hub.onGameTick();f.session.startActivity(context(SharedActivitySession.Kind.MINIGAME,"wintertodt"));
        f.xp.set(1100);hub.onStatChanged(ActivitySessionTest.xp(1100));fire.set(2200);hub.onStatChanged(new StatChanged(Skill.FIREMAKING,2200,1,1));
        hub.onStatChanged(new StatChanged(Skill.FIREMAKING,2200,1,1));assertEquals(300,f.session.currentRecord().totalXp());
        f.session.pause();fire.set(2300);hub.onStatChanged(new StatChanged(Skill.FIREMAKING,2300,1,1));f.session.resume();fire.set(2350);hub.onStatChanged(new StatChanged(Skill.FIREMAKING,2350,1,1));assertEquals(350,f.session.currentRecord().totalXp());
        GameStateChanged offline=new GameStateChanged();offline.setGameState(GameState.HOPPING);hub.onGameStateChanged(offline);fire.set(9000);hub.onStatChanged(new StatChanged(Skill.FIREMAKING,9000,1,1));assertEquals(350,f.session.currentRecord().totalXp());
        fire.set(9025);hub.onStatChanged(new StatChanged(Skill.FIREMAKING,9025,1,1));assertEquals(375,f.session.currentRecord().totalXp());
    }
    @Test public void targetAndLevelMilestonesAreDeduplicatedAndSnapshotsCannotBeMutated()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();ActivityHubTool hub=new ActivityHubTool(f.client,mock(ClientThread.class),f.config,f.session);hub.start();hub.onGameTick();
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"wood","Wood",null,"wood",Skill.WOODCUTTING,2000));
        f.xp.set(2000);hub.onStatChanged(ActivitySessionTest.xp(2000));SharedActivitySession.RunRecord r=f.session.currentRecord();assertEquals(4,r.milestones.stream().filter(m->m.id.startsWith("xp-target:")).count());
        assertFalse(f.session.reachMilestone("xp-target:100","Duplicate","goals"));
        try{r.experience.put(Skill.COOKING,2L);fail();}catch(UnsupportedOperationException expected){}
        try{r.milestones.clear();fail();}catch(UnsupportedOperationException expected){}
        f.session.observeXp(r.runId,Skill.WOODCUTTING,20);assertEquals(1000,r.totalXp());assertEquals(1020,f.session.currentRecord().totalXp());
    }
    @Test public void staleQueuedStartCannotReplaceAnotherRunOrAnotherCharacter()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();ClientThread thread=mock(ClientThread.class);ActivityHubTool hub=new ActivityHubTool(f.client,thread,f.config,f.session);hub.start();
        hub.startContext(context(SharedActivitySession.Kind.BOSS,"queued"));org.mockito.ArgumentCaptor<Runnable> task=org.mockito.ArgumentCaptor.forClass(Runnable.class);verify(thread).invoke(task.capture());
        f.session.startActivity(context(SharedActivitySession.Kind.QUEST,"manual"));String id=f.session.snapshot().runId;task.getValue().run();assertEquals(id,f.session.snapshot().runId);
        clearInvocations(thread);hub.startContext(context(SharedActivitySession.Kind.BOSS,"queued"));verify(thread).invoke(task.capture());f.profile.set("rsprofile.bob");task.getValue().run();f.profile.set("rsprofile.alice");assertEquals(id,f.session.snapshot().runId);
    }
    @Test public void boundedHistoryKeepsNewestHundredAndRejectsStaleDelete()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();for(int i=0;i<103;i++){f.session.startActivity(context(SharedActivitySession.Kind.OTHER,"run:"+i));f.session.finish();}
        assertEquals(100,f.session.history().size());String id=f.session.history().get(0).runId;f.session.removeHistory("rsprofile.bob",id);assertEquals(100,f.session.history().size());
        f.session.removeHistory("rsprofile.alice",id);assertEquals(99,f.session.history().size());assertEquals("run:101",f.session.history().get(0).context.id);
    }
}
