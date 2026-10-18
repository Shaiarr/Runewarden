/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ActivitySessionTest
{
    private static final SharedActivitySession.Context WOOD=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"wc:test","Woodcutting test",null,"wc:test",Skill.WOODCUTTING,100_000);
    @Test public void plainSessionNeedsNoGuideAndResetMakesOneNewIdentity()
    {
        Fixture f=new Fixture();assertTrue(f.session.begin());String id=f.session.snapshot().runId;assertNull(f.session.snapshot().context.guideId);f.tick(2);
        assertEquals(1200,f.session.snapshot().activeMillis);assertTrue(f.session.resetCurrent());assertNotEquals(id,f.session.snapshot().runId);assertEquals(0,f.session.snapshot().activeMillis);
    }
    @Test public void preparedSelectionDoesNotDiscardActiveSessionAndPauseSeparatesClocks()
    {
        Fixture f=new Fixture();f.session.select(WOOD);assertTrue(f.session.startSelected());f.tick(10);String id=f.session.snapshot().runId;
        f.session.select(new SharedActivitySession.Context(SharedActivitySession.Kind.OTHER,"next","Next session",null,null,null,0));assertEquals(id,f.session.snapshot().runId);
        f.session.pause();f.tick(5);assertEquals(6000,f.session.snapshot().activeMillis);assertEquals(9000,f.session.snapshot().elapsedMillis);
        f.session.resume();f.tick(2);assertEquals(7200,f.session.snapshot().activeMillis);
    }
    @Test public void hopExcludesOfflineTimeAndResumesOnlyTheSameCharacter()
    {
        Fixture f=new Fixture();f.session.begin();f.tick(3);String id=f.session.snapshot().runId;when(f.client.getGameState()).thenReturn(GameState.HOPPING);f.session.onGameTick();f.now.addAndGet(90_000);
        assertEquals(SharedActivitySession.State.SUSPENDED,f.session.snapshot().state);when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);f.session.onGameTick();
        assertEquals(id,f.session.snapshot().runId);assertEquals(1800,f.session.snapshot().activeMillis);f.tick(1);assertEquals(2400,f.session.snapshot().activeMillis);
    }
    @Test public void checkpointRestoresPausedAndProfileSwitchImmediatelyHidesOldData()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.tick(7);f.session.observeXp(f.session.snapshot().runId,Skill.WOODCUTTING,60);f.session.stop();
        SharedActivitySession restarted=f.newSession();restarted.start();restarted.onGameTick();assertEquals(SharedActivitySession.State.PAUSED,restarted.snapshot().state);assertEquals(4200,restarted.snapshot().activeMillis);assertEquals(60,restarted.currentRecord().totalXp());
        f.profile.set("rsprofile.bob");assertNull(restarted.snapshot().context);assertNull(restarted.currentRecord());restarted.resume();restarted.onGameTick();assertNull(restarted.snapshot().context);
        f.profile.set("rsprofile.alice");restarted.onGameTick();assertEquals(4200,restarted.snapshot().activeMillis);assertEquals(SharedActivitySession.State.PAUSED,restarted.snapshot().state);
    }
    @Test public void futureCheckpointAndOldHistoryAreNeverOverwritten()
    {
        Fixture f=new Fixture();String future="{\"version\":9,\"context\":\"new-format\"}",oldHistory="{\"version\":1,\"runs\":[\"retained\"]}";
        f.settings.put(key("runewardenactivity","rsprofile.alice","checkpointV1"),future);f.settings.put(key("runewardenactivity","rsprofile.alice","runHistoryV1"),oldHistory);
        f.session.clear();f.session.onGameTick();assertTrue(f.session.begin());f.tick(55);
        assertEquals(future,f.settings.get(key("runewardenactivity","rsprofile.alice","checkpointV1")));assertEquals(oldHistory,f.settings.get(key("runewardenactivity","rsprofile.alice","runHistoryV1")));
    }
    @Test public void finishedReportKeepsCurrentMeasurementsAndRejectsOldRunXp()
    {
        Fixture f=new Fixture();f.session.begin();String first=f.session.snapshot().runId;f.session.observeXp(first,Skill.COOKING,100);f.session.reportLoot(first,1000,200,2,0,java.util.Collections.singletonMap("Chest",1L));f.session.finish();f.tick(2);
        assertEquals(800,f.session.currentRecord().netLoot());assertEquals(100,f.session.currentRecord().totalXp());assertEquals(0,f.session.snapshot().activeMillis);
        f.session.resetCurrent();f.session.observeXp(first,Skill.COOKING,999);assertEquals(0,f.session.currentRecord().totalXp());
    }
    static StatChanged xp(int amount){return new StatChanged(Skill.WOODCUTTING,amount,1,1);}
    static String key(String group,String profile,String key){return group+"/"+profile+"/"+key;}
    static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);final Player player=mock(Player.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.alice");final AtomicLong now=new AtomicLong(1000);final AtomicInteger tick=new AtomicInteger(),xp=new AtomicInteger(1000),animation=new AtomicInteger(-1);
        final Map<String,String> settings=new HashMap<>();final RunewardenDataStore store=new RunewardenDataStore(config,new Gson());final SharedActivitySession session;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(c->profile.get());
            when(config.getConfiguration(anyString(),nullable(String.class),anyString())).thenAnswer(c->settings.get(key(c.getArgument(0),c.getArgument(1),c.getArgument(2))));
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(c->settings.get(key(c.getArgument(0),null,c.getArgument(1))));
            doAnswer(c->{settings.put(key(c.getArgument(0),c.getArgument(1),c.getArgument(2)),String.valueOf((Object)c.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),nullable(String.class),anyString(),any());
            doAnswer(c->{settings.put(key(c.getArgument(0),null,c.getArgument(1)),String.valueOf((Object)c.getArgument(2)));return null;}).when(config).setConfiguration(anyString(),anyString(),any());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(client.getTickCount()).thenAnswer(c->tick.get());when(client.getSkillExperience(Skill.WOODCUTTING)).thenAnswer(c->xp.get());when(player.getAnimation()).thenAnswer(c->animation.get());
            when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(80);when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(80);session=newSession();session.start();session.onGameTick();
        }
        SharedActivitySession newSession(){return new SharedActivitySession(client,config,store,now::get);}
        void tick(int count){for(int i=0;i<count;i++){now.addAndGet(600);tick.incrementAndGet();session.onGameTick();}}
    }
}
