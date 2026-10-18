/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import net.runelite.api.Skill;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
public class TrainingAfkAlertTest
{
    static class Harness
    {
        final ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();final Notifier notifier=mock(Notifier.class);final TrainingProgressTool tool=new TrainingProgressTool(f.client,mock(ClientThread.class),f.config,f.store,f.session,mock(RunewardenConfig.class),notifier);
        Harness(){this(SharedActivitySession.Kind.TRAINING);}
        Harness(SharedActivitySession.Kind kind){f.session.startActivity(ActivityHubTest.context(kind,"activity"));tool.start();tool.onGameTick();tool.setAlertPreferences(true,true,false,3);}
        void tick(int n){for(int i=0;i<n;i++){f.tick(1);tool.onGameTick();}}
    }
    @Test public void delayAndOneShotResetOnlyWhenWorkResumes()
    {Harness h=new Harness();h.tick(2);verify(h.notifier,never()).notify(anyString());h.tick(2);verify(h.notifier,times(1)).notify(contains("idle"));h.tick(40);verify(h.notifier,times(1)).notify(anyString());verify(h.f.client,times(1)).playSoundEffect(anyInt(),eq(25));
        h.f.animation.set(123);h.tick(1);h.f.animation.set(-1);h.tick(12);verify(h.notifier,times(2)).notify(anyString());}
    @Test public void focusPreferenceSuppressesUntilClientLosesFocus()
    {Harness h=new Harness();h.tool.setAlertPreferences(true,false,true,1);h.tick(20);verify(h.notifier,never()).notify(anyString());FocusChanged focus=new FocusChanged();focus.setFocused(false);h.tool.onFocusChanged(focus);h.tick(1);verify(h.notifier,times(1)).notify(anyString());}
    @Test public void bankTravelIntentionalPauseAndOfflineSuppressAlerts()
    {Harness h=new Harness();Widget bank=mock(Widget.class);when(h.f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);h.tick(20);assertEquals(TrainingProgressTool.StopCause.BANKING,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
        when(bank.isHidden()).thenReturn(true);h.tool.setTravelSuppressed(true);h.tick(20);assertEquals(TrainingProgressTool.StopCause.TRAVELLING,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
        h.tool.setTravelSuppressed(false);h.f.session.pause();h.tick(20);verify(h.notifier,never()).notify(anyString());}
    @Test public void lowHealthAlertIsIndependentOfIdleAndNotificationIsOffByDefault()
    {Harness h=new Harness();h.tool.setAlertPreferences(false,false,false,0);when(h.f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(10);h.tick(10);verify(h.notifier,never()).notify(anyString());
        h.tool.setAlertPreferences(true,false,false,2);h.tick(3);verify(h.notifier).notify(contains("low health"));h.tick(10);verify(h.notifier,times(1)).notify(anyString());}
    @Test public void genericFarmBossAndMinigameRunsReceiveRealHealthAndPrayerAlertsWithoutAnXpSkill()
    {
        for(SharedActivitySession.Kind kind:new SharedActivitySession.Kind[]{SharedActivitySession.Kind.FARMING,SharedActivitySession.Kind.BOSS,SharedActivitySession.Kind.MINIGAME})
        {
            Harness h=new Harness(kind);h.tool.setAlertPreferences(true,false,false,1);when(h.f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(10);h.tick(2);
            assertEquals(kind.name(),TrainingProgressTool.StopCause.LOW_HEALTH,h.tool.reading().cause);verify(h.notifier).notify(contains("low health"));
            when(h.f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(80);when(h.f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(30);h.tick(1);
            when(h.f.client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);h.tick(2);assertEquals(TrainingProgressTool.StopCause.LOW_PRAYER,h.tool.reading().cause);verify(h.notifier).notify(contains("low prayer"));
            h.tick(10);verify(h.notifier,times(2)).notify(anyString());verify(h.f.client,never()).getSkillExperience(any(Skill.class));
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(400,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();try{assertNull("XP widget remains skill-only",h.tool.overlay().render(g));}finally{g.dispose();}
        }
    }
    @Test public void unverifiedAnimationsFullBackpacksAndPhaseChangesDoNotCreateGenericIdleOrFullAlerts()
    {
        for(SharedActivitySession.Kind kind:new SharedActivitySession.Kind[]{SharedActivitySession.Kind.FARMING,SharedActivitySession.Kind.BOSS,SharedActivitySession.Kind.MINIGAME})
        {
            Harness h=new Harness(kind);h.tool.setAlertPreferences(true,false,false,0);h.f.animation.set(123);
            net.runelite.api.ItemContainer inventory=mock(net.runelite.api.ItemContainer.class);net.runelite.api.Item[] items=new net.runelite.api.Item[28];java.util.Arrays.fill(items,new net.runelite.api.Item(995,1));when(inventory.getItems()).thenReturn(items);when(h.f.client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(inventory);
            h.f.session.observeOutcome("minigame-phase-changes","Observed phase changes",1);h.f.session.observeOutcome("minigame-score-increases","Observed score increases",500);h.tick(30);h.f.animation.set(-1);h.tick(30);
            assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);assertEquals(0,h.tool.reading().activeMillis);verify(h.notifier,never()).notify(anyString());
        }
    }
    @Test public void explicitVerifiedWorkArmsGenericIdleEpisodeWhileAnimationsAloneCannot()
    {
        Harness h=new Harness(SharedActivitySession.Kind.MINIGAME);h.tool.setAlertPreferences(true,false,false,2);h.tick(30);verify(h.notifier,never()).notify(anyString());
        h.tool.observeAction(1);h.tick(1);assertEquals(TrainingProgressTool.StopCause.WORKING,h.tool.reading().cause);h.tick(12);assertEquals(TrainingProgressTool.StopCause.IDLE,h.tool.reading().cause);verify(h.notifier,times(1)).notify(contains("idle"));
        h.tick(20);verify(h.notifier,times(1)).notify(anyString());
    }
    @Test public void exactNativeFarmReplantCounterArmsWorkButDoesNotFabricateActionCounts()
    {
        Harness h=new Harness(SharedActivitySession.Kind.FARMING);h.tool.setAlertPreferences(true,false,false,1);h.tick(20);verify(h.notifier,never()).notify(anyString());
        h.f.session.observeOutcome("farm-replants","Observed replants",1);h.tick(1);assertEquals(TrainingProgressTool.StopCause.WORKING,h.tool.reading().cause);assertEquals(0,h.tool.reading().actions);
        h.tick(12);verify(h.notifier,times(1)).notify(contains("idle"));
        Harness boss=new Harness(SharedActivitySession.Kind.BOSS);boss.tool.setAlertPreferences(true,false,false,0);boss.f.session.observeOutcome("farm-replants","Unrelated field",1);boss.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,boss.tool.reading().cause);verify(boss.notifier,never()).notify(anyString());
    }
    @Test public void bankTravelPauseAndOfflineResetGenericWorkSoIntentionalDowntimeCannotAlarm()
    {
        Harness h=new Harness(SharedActivitySession.Kind.MINIGAME);h.tool.setAlertPreferences(true,false,false,0);h.tool.observeAction(1);
        Widget bank=mock(Widget.class);when(h.f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);h.tick(2);assertEquals(TrainingProgressTool.StopCause.BANKING,h.tool.reading().cause);
        when(bank.isHidden()).thenReturn(true);h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
        h.tool.observeAction(1);h.tool.setTravelSuppressed(true);h.tick(2);assertEquals(TrainingProgressTool.StopCause.TRAVELLING,h.tool.reading().cause);h.tool.setTravelSuppressed(false);h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);
        h.tool.observeAction(1);h.f.session.pause();h.tick(2);assertEquals(TrainingProgressTool.StopCause.PAUSED,h.tool.reading().cause);h.f.session.resume();h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);
        h.tool.observeAction(1);when(h.f.client.getGameState()).thenReturn(net.runelite.api.GameState.HOPPING);h.f.session.suspend();h.tool.onGameTick();assertEquals(TrainingProgressTool.StopCause.OFFLINE,h.tool.reading().cause);when(h.f.client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
    }
    @Test public void nativeMovementSuppressesGenericIdleUntilNewVerifiedWork()
    {
        Harness h=new Harness(SharedActivitySession.Kind.FARMING);h.tool.setAlertPreferences(true,false,false,0);java.util.concurrent.atomic.AtomicReference<net.runelite.api.coords.WorldPoint> position=new java.util.concurrent.atomic.AtomicReference<>(new net.runelite.api.coords.WorldPoint(3200,3200,0));when(h.f.player.getWorldLocation()).thenAnswer(c->position.get());h.tick(1);
        h.tool.observeAction(1);position.set(new net.runelite.api.coords.WorldPoint(3201,3200,0));h.tick(1);assertEquals(TrainingProgressTool.StopCause.TRAVELLING,h.tool.reading().cause);h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
    }
    @Test public void genericInitialZeroPrayerAndHistoricalRestoredWorkStaySilent()
    {
        Harness h=new Harness(SharedActivitySession.Kind.BOSS);h.tool.setAlertPreferences(true,false,false,0);h.tick(20);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
        h.tool.observeAction(1);h.tool.stop();h.tool.start();h.tool.onGameTick();h.tool.setAlertPreferences(true,false,false,0);h.tick(20);assertEquals(1,h.tool.reading().actions);assertEquals(TrainingProgressTool.StopCause.READY,h.tool.reading().cause);verify(h.notifier,never()).notify(anyString());
        h.f.session.finish();h.tick(1);assertEquals(TrainingProgressTool.StopCause.FINISHED,h.tool.reading().cause);
    }
}
