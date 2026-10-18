/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import com.google.gson.Gson;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
public class RunLootCheckpointTest
{
    @Test public void loginAutomaticallyStartsPlainSessionWithoutASelectedGuide() throws Exception
    {
        Harness h=new Harness();try{assertTrue(h.f.session.snapshot().running());assertNull(h.f.session.snapshot().context.guideId);h.loot(1);assertEquals(100,h.tool.reading().value);assertEquals(100,h.f.session.currentRecord().grossLoot);}finally{h.stop();}
    }
    @Test public void restartRestoresCheckpointPausedWithStoredPricesAndCosts() throws Exception
    {
        Harness h=new Harness();try
        {
            h.loot(10);RunLootTool.Reading before=h.tool.reading();h.tool.setManualCost(before.profile,before.epoch,300);h.tool.stop();
            when(h.items.getItemPrice(anyInt())).thenReturn(9999L);h.tool=h.newTool();h.tool.start();h.tool.onGameTick();
            RunLootTool.Reading restored=h.tool.reading();assertTrue(restored.paused);assertEquals(1000,restored.value);assertEquals(300,restored.manualCost);assertEquals(700,restored.netValue);
            h.loot(5);assertEquals("Restored run requires explicit resume",1000,h.tool.reading().value);
            h.tool.setPaused(restored.profile,restored.epoch,false);h.loot(1);assertEquals(109_989,h.tool.reading().value);
        }finally{h.stop();}
    }
    @Test public void activityStartPauseFinishAndNewActivityUseOneRunBoundary() throws Exception
    {
        Harness h=new Harness();try
        {
            SharedActivitySession.Context first=context("one","First activity"),second=context("two","Second activity");
            h.f.session.startActivity(first);h.tool.onGameTick();h.loot(10);h.f.session.pause();h.tool.onGameTick();assertTrue(h.tool.reading().paused);
            h.loot(5);assertEquals(1000,h.tool.reading().value);h.f.session.resume();h.tool.onGameTick();h.loot(1);assertEquals(1100,h.tool.reading().value);
            h.f.session.startActivity(second);h.tool.onGameTick();assertEquals(0,h.tool.reading().value);
            h.f.session.finish();h.tool.onGameTick();assertTrue(h.tool.reading().paused);
        }finally{h.stop();}
    }
    @Test public void checkpointsAreProfileIsolatedAndHistoryFutureVersionSurvives() throws Exception
    {
        Harness h=new Harness();try
        {
            h.loot(10);RunLootTool.Reading a=h.tool.reading();h.tool.setManualCost(a.profile,a.epoch,1);
            h.f.profile.set("rsprofile.bob");h.f.session.onGameTick();h.tool.onGameTick();assertEquals(0,h.tool.reading().value);h.loot(2);
            h.f.profile.set("rsprofile.alice");h.f.session.onGameTick();h.tool.onGameTick();assertEquals(1000,h.tool.reading().value);assertTrue(h.tool.reading().paused);
            String address=ActivitySessionTest.key(RunewardenConfig.GROUP,"rsprofile.alice","runLootHistoryV1");String future="{\"version\":12,\"runs\":[]}";h.f.settings.put(address,future);
            h.tool.stop();h.tool.start();h.tool.onGameTick();RunLootTool.Reading r=h.tool.reading();h.tool.finishRun(r.profile,r.epoch);
            assertEquals(future,h.f.settings.get(address));
        }finally{h.stop();}
    }
    @Test public void sharedCommandsApplyToLootBeforeTheNextGameTick() throws Exception
    {
        Harness h=new Harness();try
        {
            h.f.session.startActivity(context("one","First activity"));h.loot(10);
            h.f.session.pause();h.loot(5);
            assertTrue(h.tool.reading().paused);assertEquals(1000,h.tool.reading().value);
            h.f.session.resume();h.loot(1);assertEquals(1100,h.tool.reading().value);
            h.f.session.startActivity(context("two","Second activity"));h.loot(2);
            assertEquals(200,h.tool.reading().value);
            h.f.session.finish();h.loot(3);
            assertTrue(h.tool.reading().paused);assertEquals(200,h.tool.reading().value);
        }finally{h.stop();}
    }
    @Test public void manualAndSharedPausesBothExcludeTheirEntirePausedDuration() throws Exception
    {
        Harness h=new Harness();try
        {
            h.f.now.addAndGet(6000);h.loot(1);RunLootTool.Reading running=h.tool.reading();
            h.tool.setPaused(running.profile,running.epoch,true);h.f.now.addAndGet(90_000);
            h.tool.setPaused(running.profile,running.epoch,false);
            assertEquals(6000,h.tool.reading().elapsedMillis);
            h.f.now.addAndGet(600);h.tool.onGameTick();assertEquals(6600,h.tool.reading().elapsedMillis);
            h.f.session.pause();h.tool.onGameTick();h.f.now.addAndGet(30_000);h.tool.onGameTick();
            assertEquals(6600,h.tool.reading().elapsedMillis);
            h.f.session.resume();h.tool.onGameTick();h.f.now.addAndGet(600);h.tool.onGameTick();
            assertEquals(7200,h.tool.reading().elapsedMillis);
        }finally{h.stop();}
    }
    @Test public void stoppingWhileSuspendedNeverCheckpointsOfflineTime() throws Exception
    {
        Harness h=new Harness();try
        {
            h.f.now.addAndGet(6000);h.loot(10);assertEquals(6000,h.tool.reading().elapsedMillis);
            when(h.f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);h.tool.suspend();
            h.f.now.addAndGet(90_000);h.tool.stop();
            when(h.f.client.getGameState()).thenReturn(GameState.LOGGED_IN);h.tool=h.newTool();h.tool.start();h.tool.onGameTick();
            assertEquals(1000,h.tool.reading().value);assertTrue(h.tool.reading().paused);
            assertEquals(6000,h.tool.reading().elapsedMillis);
        }finally{h.stop();}
    }
    @Test public void switchingCharacterAfterSuspensionKeepsTheOldRunsOnlineDuration() throws Exception
    {
        Harness h=new Harness();try
        {
            h.f.now.addAndGet(6000);h.loot(10);h.tool.suspend();h.f.now.addAndGet(90_000);
            h.f.profile.set("rsprofile.bob");h.f.session.onGameTick();h.tool.onGameTick();
            assertEquals(0,h.tool.reading().value);
            h.f.profile.set("rsprofile.alice");h.f.session.onGameTick();h.tool.onGameTick();
            assertEquals(1000,h.tool.reading().value);assertTrue(h.tool.reading().paused);
            assertEquals(6000,h.tool.reading().elapsedMillis);
        }finally{h.stop();}
    }
    @Test public void sharedCurrentReportKeepsFinalLootAndCostsUntilExplicitReset() throws Exception
    {
        Harness h=new Harness();try
        {
            h.f.session.startActivity(context("one","First activity"));h.loot(10);RunLootTool.Reading before=h.tool.reading();h.tool.setManualCost(before.profile,before.epoch,300);
            h.tool.finishRun(before.profile,before.epoch);assertEquals(700,h.f.session.currentRecord().netLoot());assertEquals(1000,h.f.session.currentRecord().grossLoot);assertEquals(1000,h.tool.reading().value);
            h.f.session.startActivity(context("two","Second activity"));h.loot(5);RunLootTool.Reading second=h.tool.reading();h.tool.setManualCost(second.profile,second.epoch,100);
            h.tool.newRun(second.profile,second.epoch);assertEquals(0,h.f.session.currentRecord().grossLoot);assertEquals(0,h.tool.reading().value);
        }finally{h.stop();}
    }
    private static SharedActivitySession.Context context(String id,String title)
    {return new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,id,title,"training:"+id,id,Skill.WOODCUTTING,10000);}
    private static final class Harness
    {
        final ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();final ItemManager items=mock(ItemManager.class);RunLootTool tool;
        Harness(){when(items.canonicalize(anyInt())).thenAnswer(c->c.getArgument(0));when(items.getItemPrice(anyInt())).thenReturn(100L);tool=newTool();tool.start();tool.onGameTick();}
        RunLootTool newTool(){return new RunLootTool(f.client,mock(ClientThread.class),f.config,items,mock(LootManager.class),new Gson(),f.store,f.session,f.now::get);}
        void loot(int quantity){NPCComposition npc=mock(NPCComposition.class);when(npc.getName()).thenReturn("Goblin");tool.onServerNpcLoot(new ServerNpcLoot(npc,Arrays.asList(new ItemStack(440,quantity))));}
        void stop() throws Exception{tool.stop();SwingUtilities.invokeAndWait(()->{});}
    }
}
