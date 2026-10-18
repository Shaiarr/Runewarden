/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ActivitySessionTest
{
    private static final SharedActivitySession.Context WOOD=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"wc:test","Woodcutting test","training:woodcutting","wc:test",Skill.WOODCUTTING,100_000);
    @Test public void preparedSelectionDoesNotDiscardActiveActivityAndPauseSeparatesClocks()
    {
        Fixture f=new Fixture();f.session.select(WOOD);assertTrue(f.session.startSelected());f.tick(10);
        assertEquals(6000,f.session.snapshot().activeMillis);assertEquals(6000,f.session.snapshot().elapsedMillis);
        String id=f.session.snapshot().runId;
        f.session.select(new SharedActivitySession.Context(SharedActivitySession.Kind.QUEST,"quest:test","Next quest","quest:test",null,null,0));
        assertEquals(id,f.session.snapshot().runId);assertEquals(WOOD,f.session.snapshot().context);
        f.session.pause();f.tick(5);assertEquals(6000,f.session.snapshot().activeMillis);assertEquals(9000,f.session.snapshot().elapsedMillis);
        f.session.resume();f.tick(2);assertEquals(7200,f.session.snapshot().activeMillis);
    }
    @Test public void hopExcludesOfflineTimeAndSameProfileResumesWithoutDiscarding()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.tick(3);String id=f.session.snapshot().runId;
        when(f.client.getGameState()).thenReturn(GameState.HOPPING);f.session.onGameTick();f.now.addAndGet(90_000);
        assertEquals(SharedActivitySession.State.SUSPENDED,f.session.snapshot().state);
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);f.session.onGameTick();
        assertEquals(id,f.session.snapshot().runId);assertEquals(1800,f.session.snapshot().activeMillis);
        f.tick(1);assertEquals(2400,f.session.snapshot().activeMillis);
    }
    @Test public void checkpointRestoresPausedAndProfileSwitchDoesNotLeak()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.tick(7);f.session.stop();
        SharedActivitySession restarted=f.newSession();restarted.start();restarted.onGameTick();
        assertEquals(SharedActivitySession.State.PAUSED,restarted.snapshot().state);assertEquals(4200,restarted.snapshot().activeMillis);
        f.profile.set("rsprofile.bob");restarted.onGameTick();assertNull(restarted.snapshot().context);
        f.profile.set("rsprofile.alice");restarted.onGameTick();assertEquals(4200,restarted.snapshot().activeMillis);
        assertEquals(SharedActivitySession.State.PAUSED,restarted.snapshot().state);
    }
    @Test public void futureCheckpointIsRetainedWhenUserStartsNewInMemoryActivity()
    {
        Fixture f=new Fixture();String future="{\"version\":9,\"context\":\"new-format\"}";
        f.settings.put(key("runewardenactivity","rsprofile.alice","checkpointV1"),future);
        f.session.clear();f.session.onGameTick();assertNull(f.session.snapshot().context);
        assertTrue(f.session.startActivity(WOOD));f.tick(55);
        assertEquals(future,f.settings.get(key("runewardenactivity","rsprofile.alice","checkpointV1")));
    }
    @Test public void endingAnAutomaticActivitySurvivesBankGapsAndNewNativeEntryIdsUntilManualRestart()
    {
        Fixture f=new Fixture();f.session.setAutoDetect(true);
        SharedActivitySession.Context first=minigame("wintertodt",1),nextEntry=minigame("wintertodt",2);
        AtomicInteger starts=new AtomicInteger();f.session.setStartAction(ignored->starts.incrementAndGet());
        assertTrue(f.session.offerDetected(first));String ended=f.session.snapshot().runId;f.session.finish();
        f.session.offerDetected(null);assertFalse(f.session.offerDetected(nextEntry));
        assertEquals(SharedActivitySession.State.FINISHED,f.session.snapshot().state);assertEquals(ended,f.session.snapshot().runId);assertEquals(1,starts.get());
        assertTrue(f.session.startSelected());assertNotEquals(ended,f.session.snapshot().runId);assertEquals(nextEntry,f.session.snapshot().context);assertEquals(2,starts.get());
    }
    @Test public void aDifferentDetectedActivityReleasesDismissalAndAnotherProfileDoesNotInheritIt()
    {
        Fixture f=new Fixture();f.session.setAutoDetect(true);assertTrue(f.session.offerDetected(minigame("wintertodt",1)));f.session.finish();
        assertTrue(f.session.offerDetected(minigame("tempoross",1)));assertEquals("training:tempoross",f.session.snapshot().context.guideId);
        f.session.finish();f.profile.set("rsprofile.bob");f.session.onGameTick();
        assertTrue(f.session.offerDetected(minigame("tempoross",1)));assertEquals("rsprofile.bob",f.session.snapshot().profile);
    }
    @Test public void completedRunRetainsDetectionDismissalAcrossClientRestart()
    {
        Fixture f=new Fixture();f.session.setAutoDetect(true);assertTrue(f.session.offerDetected(minigame("wintertodt",1)));f.session.finish();f.session.stop();
        SharedActivitySession restarted=f.newSession();restarted.start();restarted.onGameTick();
        assertFalse(restarted.offerDetected(minigame("wintertodt",2)));assertEquals(SharedActivitySession.State.FINISHED,restarted.snapshot().state);
        assertTrue(restarted.startSelected());assertTrue(restarted.snapshot().running());
    }
    private static SharedActivitySession.Context minigame(String id,int entry)
    {return new SharedActivitySession.Context(SharedActivitySession.Kind.MINIGAME,"minigame:"+id+":"+entry,id,"training:"+id,null,null,0);}
    @Test public void trainingSeparatesAmbiguousXpAndRewardWindowsFromCorrelatedXp()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.tick(1);
        f.xp.set(1100);f.progress.onStatChanged(xp(1100));assertEquals(100,f.progress.reading().unattributedXp);assertEquals(0,f.progress.reading().trainingXp);
        f.animation.set(123);f.xp.set(1150);f.progress.onStatChanged(xp(1150));assertEquals(50,f.progress.reading().trainingXp);
        f.tick(3);f.progress.suppressRewardXp();f.xp.set(1650);f.progress.onStatChanged(xp(1650));assertEquals(50,f.progress.reading().trainingXp);
        f.progress.observeAction(3);assertEquals(3,f.progress.reading().actions);assertEquals(3,f.progress.reading().xpDrops);
        assertEquals(600,f.progress.reading().unattributedXp);
    }
    @Test public void pausedXpAndFirstPostHopBaselineNeverCatchUpIntoTraining()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.animation.set(123);f.tick(1);
        f.session.pause();f.xp.set(1300);f.progress.onStatChanged(xp(1300));f.session.resume();f.tick(1);
        f.xp.set(1350);f.progress.onStatChanged(xp(1350));assertEquals(50,f.progress.reading().trainingXp);
        when(f.client.getGameState()).thenReturn(GameState.HOPPING);f.session.onGameTick();f.progress.onGameTick();f.xp.set(9000);
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);f.tick(1);f.progress.onStatChanged(xp(9000));
        assertEquals(50,f.progress.reading().trainingXp);
    }
    @Test public void nativeRewardInterfaceReclassifiesSameTickXpAndMenuConfirmsExtendWindow()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.animation.set(123);f.tick(1);
        f.xp.set(1200);f.progress.onStatChanged(xp(1200));assertEquals(200,f.progress.reading().trainingXp);
        net.runelite.api.events.WidgetLoaded loaded=new net.runelite.api.events.WidgetLoaded();loaded.setGroupId(net.runelite.api.gameval.InterfaceID.QUESTSCROLL);f.progress.onWidgetLoaded(loaded);
        assertEquals(0,f.progress.reading().trainingXp);assertEquals(200,f.progress.reading().unattributedXp);
        net.runelite.api.events.MenuOptionClicked click=mock(net.runelite.api.events.MenuOptionClicked.class);
        when(click.getParam1()).thenReturn(net.runelite.api.gameval.InterfaceID.Xpreward.CONFIRM);f.progress.onMenuOptionClicked(click);
        f.xp.set(1700);f.progress.onStatChanged(xp(1700));assertEquals(0,f.progress.reading().trainingXp);assertEquals(700,f.progress.reading().unattributedXp);
    }
    @Test public void exactRecipeContextUsesItsIngredientReadiness()
    {
        Fixture f=new Fixture();BankedExperienceCalculator.Recipe recipe=BankedExperienceCalculator.recipes().stream().filter(r->r.ingredients.size()>1).findFirst().get();
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,recipe.id,recipe.name,null,recipe.id,recipe.skill,10000));
        when(f.client.getSkillExperience(recipe.skill)).thenReturn(1000);ItemContainer bag=mock(ItemContainer.class);when(bag.getItems()).thenReturn(new Item[0]);when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(bag);
        f.progress.start();f.tick(1);assertEquals(TrainingProgressTool.StopCause.MATERIALS_MISSING,f.progress.reading().cause);
    }
    @Test public void afkCausesUseObservedSuppliesInventoryAndHealth()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.tick(10);
        assertEquals(TrainingProgressTool.StopCause.IDLE,f.progress.reading().cause);
        ItemContainer bag=mock(ItemContainer.class);Item[] full=new Item[28];java.util.Arrays.fill(full,new Item(995,1));when(bag.getItems()).thenReturn(full);
        when(f.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(bag);f.tick(1);
        assertEquals(TrainingProgressTool.StopCause.INVENTORY_FULL,f.progress.reading().cause);
        f.progress.setRequiredMaterials(Collections.singletonMap(9001,1));f.tick(1);assertEquals(TrainingProgressTool.StopCause.MATERIALS_MISSING,f.progress.reading().cause);
        when(f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(10);f.tick(1);assertEquals(TrainingProgressTool.StopCause.LOW_HEALTH,f.progress.reading().cause);
        when(f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(80);NPC enemy=mock(NPC.class);when(enemy.getCombatLevel()).thenReturn(100);when(f.player.getInteracting()).thenReturn(enemy);f.tick(1);
        assertEquals(TrainingProgressTool.StopCause.COMBAT,f.progress.reading().cause);
    }
    @Test public void profileCheckpointRetainsMeasuredXpAndConfirmedActions()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.animation.set(123);f.tick(1);
        f.xp.set(1060);f.progress.onStatChanged(xp(1060));f.progress.observeAction(2);f.progress.stop();f.session.stop();
        f.session.start();f.session.onGameTick();f.progress.start();f.tick(1);
        assertEquals(60,f.progress.reading().trainingXp);assertEquals(2,f.progress.reading().actions);assertEquals(SharedActivitySession.State.PAUSED,f.progress.reading().session.state);
    }
    @Test public void visibleBankSuppressesAfkCausesAndWorkingTime()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);f.progress.start();f.animation.set(123);f.tick(3);long active=f.progress.reading().activeMillis;
        net.runelite.api.widgets.Widget bank=mock(net.runelite.api.widgets.Widget.class);when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);
        f.tick(10);assertEquals(TrainingProgressTool.StopCause.BANKING,f.progress.reading().cause);assertEquals(active,f.progress.reading().activeMillis);
        f.xp.set(1100);f.progress.onStatChanged(xp(1100));assertEquals(0,f.progress.reading().trainingXp);assertEquals(100,f.progress.reading().unattributedXp);
        when(bank.isHidden()).thenReturn(true);f.tick(1);assertEquals(TrainingProgressTool.StopCause.WORKING,f.progress.reading().cause);
    }
    @Test public void rateAndEtaRequireOneMinuteAndUseDistinctActiveElapsedClocks()
    {
        SharedActivitySession.Snapshot s=new SharedActivitySession.Snapshot("rsprofile.alice","id",WOOD,WOOD,SharedActivitySession.State.RUNNING,120_000,60_000,1);
        TrainingProgressTool.Reading r=new TrainingProgressTool.Reading(s,1000,1000,10,0,60_000,99_000,TrainingProgressTool.StopCause.WORKING);
        assertEquals(60_000,r.activeXpPerHour,.01);assertEquals(30_000,r.elapsedXpPerHour,.01);assertEquals(60_000,r.activeEtaMillis);assertEquals(120_000,r.elapsedEtaMillis);
        assertEquals(-1,new TrainingProgressTool.Reading(s,1000,1000,10,0,1000,99_000,TrainingProgressTool.StopCause.WORKING).activeEtaMillis);
    }
    @Test public void queuedTrainingControlsAreSingleShotAndRejectStaleProfileOrRun()
    {
        Fixture f=new Fixture();f.session.startActivity(WOOD);ClientThread thread=mock(ClientThread.class);
        TrainingProgressTool tool=new TrainingProgressTool(f.client,thread,f.config,f.store,f.session,mock(RunewardenConfig.class));tool.start();tool.onGameTick();
        tool.restartTracking();org.mockito.ArgumentCaptor<Runnable> task=org.mockito.ArgumentCaptor.forClass(Runnable.class);verify(thread).invoke(task.capture());
        verify(thread,never()).invoke(any(java.util.function.BooleanSupplier.class));String first=f.session.snapshot().runId;
        f.profile.set("rsprofile.bob");task.getValue().run();f.profile.set("rsprofile.alice");assertEquals(first,f.session.snapshot().runId);
        clearInvocations(thread);tool.restartTracking();verify(thread).invoke(task.capture());f.session.startActivity(WOOD);String newer=f.session.snapshot().runId;
        task.getValue().run();assertEquals(newer,f.session.snapshot().runId);
    }
    static StatChanged xp(int amount){return new StatChanged(Skill.WOODCUTTING,amount,1,1);}
    static String key(String group,String profile,String key){return group+"/"+profile+"/"+key;}
    static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);final Player player=mock(Player.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.alice");final AtomicLong now=new AtomicLong(1000);final AtomicInteger tick=new AtomicInteger(),xp=new AtomicInteger(1000),animation=new AtomicInteger(-1);
        final Map<String,String> settings=new HashMap<>();final RunewardenDataStore store=new RunewardenDataStore(config,new Gson());
        final SharedActivitySession session;final TrainingProgressTool progress;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(c->profile.get());
            when(config.getConfiguration(anyString(),nullable(String.class),anyString())).thenAnswer(c->settings.get(key(c.getArgument(0),c.getArgument(1),c.getArgument(2))));
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(c->settings.get(key(c.getArgument(0),null,c.getArgument(1))));
            doAnswer(c->{settings.put(key(c.getArgument(0),c.getArgument(1),c.getArgument(2)),String.valueOf((Object)c.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),nullable(String.class),anyString(),any());
            doAnswer(c->{settings.put(key(c.getArgument(0),null,c.getArgument(1)),String.valueOf((Object)c.getArgument(2)));return null;}).when(config).setConfiguration(anyString(),anyString(),any());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(client.getTickCount()).thenAnswer(c->tick.get());
            when(client.getSkillExperience(Skill.WOODCUTTING)).thenAnswer(c->xp.get());when(player.getAnimation()).thenAnswer(c->animation.get());
            when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(80);when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(80);
            session=newSession();session.start();session.onGameTick();progress=new TrainingProgressTool(client,mock(ClientThread.class),config,store,session,mock(RunewardenConfig.class));
        }
        SharedActivitySession newSession(){return new SharedActivitySession(client,config,store,now::get);}
        void tick(int count){for(int i=0;i<count;i++){now.addAndGet(600);tick.incrementAndGet();session.onGameTick();progress.onGameTick();}}
    }
}
