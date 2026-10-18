/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AgilityCoachTest
{
    @Test public void existingCourseActivatesImmediatelyAndOnlyPositiveXpAtTheFinishCounts()
    {
        Fixture f=new Fixture();f.start();assertEquals("Draynor",f.coach.reading().title);
        f.gain(0);assertEquals(0,f.coach.reading().laps);f.gain(120);assertEquals(1,f.coach.reading().laps);assertEquals(120,f.coach.reading().xp);
        f.coach.onStatChanged(new StatChanged(Skill.AGILITY,f.xp.get(),51,51));assertEquals(1,f.coach.reading().laps);
        f.tick();f.location.set(new WorldPoint(3103,3261,1));f.gain(120);assertEquals(1,f.coach.reading().laps);
    }
    @Test public void otherSkillAndOffCourseXpNeverBecomeAgilityLaps()
    {
        Fixture f=new Fixture();f.start();f.coach.onStatChanged(new StatChanged(Skill.ATTACK,9000,50,50));assertEquals(0,f.coach.reading().xp);
        f.location.set(new WorldPoint(3200,3200,0));f.tick();f.gain(100);assertFalse(f.coach.reading().active());assertEquals(0,f.coach.reading().laps);
    }
    @Test public void aCharacterSwitchCannotDisplayOrAccumulateAnotherCharactersSession()
    {
        Fixture f=new Fixture();f.start();f.gain(120);f.profile.set("rsprofile.b");assertFalse(f.coach.reading().active());f.tick();assertEquals(0,f.coach.reading().xp);assertEquals(0,f.coach.reading().laps);
        f.profile.set("rsprofile.a");f.tick();assertEquals(120,f.coach.reading().xp);assertEquals(1,f.coach.reading().laps);
    }
    @Test public void checkpointsRestoreTotalsButNeverReviveAnOfflineLapInterval()
    {
        Fixture f=new Fixture();f.start();f.gain(120);f.coach.stop();f.coach.start();f.tick();assertEquals(1,f.coach.reading().laps);
        f.gain(120);assertEquals(2,f.coach.reading().laps);assertEquals(0,f.coach.reading().lastLap);
    }
    @Test public void enablingInsideSeedsObjectsOnceAndDespawnRemovesTheirIdentity()
    {
        Fixture f=new Fixture();Scene scene=mock(Scene.class);Tile tile=f.tile();GameObject object=f.object(ObjectID.GNOME_LOG_BALANCE1);
        when(tile.getGameObjects()).thenReturn(new GameObject[]{object});when(scene.getTiles()).thenReturn(new Tile[][][]{{{tile}}});when(f.view.getScene()).thenReturn(scene);
        when(f.player.getLocalLocation()).thenReturn(null);f.start();f.tick();assertEquals(1,f.coach.obstacles().size());verify(scene,times(1)).getTiles();
        f.coach.onTileObject(tile,object,null);assertTrue(f.coach.obstacles().isEmpty());
    }
    @Test public void anotherWorldViewsObjectsCannotActivateWyrmOrPolluteTheScene()
    {
        Fixture f=new Fixture();f.location.set(new WorldPoint(3200,3200,0));f.start();GameObject object=f.object(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER);when(object.getWorldView()).thenReturn(mock(WorldView.class));
        f.coach.onTileObject(f.tile(),null,object);f.tick();assertTrue(f.coach.obstacles().isEmpty());assertFalse(f.coach.reading().active());
    }
    @Test public void wyrmNeedsAnObservedTransitionAndRepeatedSixDoesNotCountTwice()
    {
        Fixture f=new Fixture();f.location.set(new WorldPoint(3200,3200,0));f.start();f.coach.onTileObject(f.tile(),null,f.object(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER));f.tick();
        f.varbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS,5);f.varbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS,6);f.varbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS,6);
        assertEquals("Colossal Wyrm Basic",f.coach.reading().title);assertEquals(1,f.coach.reading().laps);assertEquals(6,f.coach.reading().progress);
    }
    @Test public void unrelatedWyrmVarbitsOutsideTheObservedCourseCannotCreateALap()
    {Fixture f=new Fixture();f.start();f.varbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS,6);assertEquals(0,f.coach.reading().laps);assertEquals("Draynor",f.coach.reading().title);}
    @Test public void initialSixWhenLoggingInIsNotANewWyrmLap()
    {
        Fixture f=new Fixture();when(f.client.getVarbitValue(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS)).thenReturn(6);f.location.set(new WorldPoint(3200,3200,0));f.start();
        f.coach.onTileObject(f.tile(),null,f.object(ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER));f.varbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS,6);assertEquals(0,f.coach.reading().laps);
    }
    @Test public void arenaCountdownBeginsOnlyAfterAnObservedTargetChangeAndExpires()
    {
        Fixture f=new Fixture();f.location.set(new WorldPoint(2800,9550,3));assertEquals(11157,f.location.get().getRegionID());AtomicReference<WorldPoint> target=new AtomicReference<>(new WorldPoint(2800,9550,0));when(f.client.getHintArrowPoint()).thenAnswer(call->target.get());f.start();assertEquals(0,f.coach.reading().arenaTicks);
        target.set(new WorldPoint(2800,9550,3));f.tick();assertEquals(0,f.coach.reading().arenaTicks);target.set(new WorldPoint(2801,9550,0));f.tick();assertEquals(100,f.coach.reading().arenaTicks);for(int i=0;i<100;i++)f.tick();assertEquals(0,f.coach.reading().arenaTicks);
    }
    @Test public void groundExpiryAloneAndBankWithdrawalsDoNotCountAsCollectedMarks()
    {
        Fixture f=new Fixture();f.start();Tile tile=f.tile();TileItem mark=mock(TileItem.class);when(mark.getId()).thenReturn(ItemID.GRACE);when(mark.getQuantity()).thenReturn(1);
        f.coach.onItemSpawned(new ItemSpawned(tile,mark));f.tick();assertEquals(1,f.coach.reading().groundMarks);
        f.coach.onItemDespawned(new ItemDespawned(tile,mark));f.tick();assertEquals(0,f.coach.reading().marks);assertEquals(0,f.coach.reading().groundMarks);
        for(int i=0;i<3;i++)f.tick();f.markQuantity.set(10);f.tick();assertEquals(0,f.coach.reading().marks);
    }
    @Test public void nearbyMarkDespawnPlusInventoryGainConfirmsPickupOnce()
    {
        Fixture f=new Fixture();f.start();Tile tile=f.tile();TileItem mark=mock(TileItem.class);when(mark.getId()).thenReturn(ItemID.GRACE);when(mark.getQuantity()).thenReturn(1);
        f.coach.onItemSpawned(new ItemSpawned(tile,mark));f.coach.onItemDespawned(new ItemDespawned(tile,mark));f.markQuantity.set(1);f.tick();f.tick();assertEquals(1,f.coach.reading().marks);
    }
    @Test public void stoppedLifecycleAndOldQueuedStartCannotReviveTheHud()
    {
        Fixture f=new Fixture();f.coach.start();f.coach.stop();for(Runnable callback:f.callbacks)callback.run();f.coach.onGameTick();assertFalse(f.coach.reading().active());
        Graphics2D g=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB).createGraphics();try{assertNull(f.coach.hud().render(g));}finally{g.dispose();}
    }
    @Test public void hudRendersInsideCourseAndSceneToggleControlsTheRealObjectClickbox()
    {
        Fixture f=new Fixture();f.start();GameObject object=f.object(ObjectID.GNOME_LOG_BALANCE1);when(object.getClickbox()).thenReturn(new java.awt.Rectangle(30,30,40,40));f.coach.onTileObject(f.tile(),null,object);
        BufferedImage image=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{assertNotNull(f.coach.hud().render(g));f.coach.scene().render(g);assertNotEquals(0,image.getRGB(40,40));}finally{g.dispose();}
        AgilityCoach.Obstacle obstacle=f.coach.obstacles().iterator().next();assertTrue(AgilitySceneOverlay.enabled(f.coach.preferences(),obstacle));f.coach.preferences().obstacles=false;assertFalse(AgilitySceneOverlay.enabled(f.coach.preferences(),obstacle));
    }
    @Test public void nativePanelFitsTheSidebarAndKeepsInputsAcrossTickRefreshes() throws Exception
    {
        Fixture f=new Fixture();f.start();AtomicReference<JPanel> panel=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{panel.set(f.coach.createPanel());panel.get().setSize(206,1400);layout(panel.get());assertCheckboxesFit(panel.get());});
        f.tick();SwingUtilities.invokeAndWait(()->assertEquals(4,panel.get().getComponentCount()));
    }
    @Test public void futureSavedSessionsRemainIntactWhenTheCoachRuns()
    {
        Fixture f=new Fixture();String address=AgilityCoach.GROUP+"/rsprofile.a/"+AgilityCoach.KEY;String future="{\"version\":99,\"sessions\":{}}";f.saved.put(address,future);f.start();f.gain(120);f.coach.stop();assertEquals(future,f.saved.get(address));
    }
    @Test public void refreshingTheSameGameTickCannotDoubleTheElapsedRateTime()
    {
        Fixture f=new Fixture();f.start();f.gain(120);f.tick();int rate=f.coach.reading().xpHour;f.coach.onGameTick();f.coach.onGameTick();assertEquals(rate,f.coach.reading().xpHour);
    }
    @Test public void shortcutLevelAndNativeTooltipAreAvailableWhileOutsideACourse()
    {
        Fixture f=new Fixture();f.location.set(new WorldPoint(2936,3357,0));f.start();GameObject object=f.object(ObjectID.FAI_FALADOR_CASTLE_CRUMBLE_MID);f.coach.onTileObject(f.tile(),null,object);f.tick();assertEquals(1,f.coach.obstacles().size());assertNotNull(f.coach.obstacles().iterator().next().shortcut);assertEquals(5,f.coach.obstacles().iterator().next().shortcut.getLevel());assertTrue(f.coach.reading().shortcuts.get(0).contains("Agility 5"));
    }
    private static void layout(Container value){value.doLayout();for(Component child:value.getComponents())if(child instanceof Container)layout((Container)child);}
    private static void assertCheckboxesFit(Container panel){for(Component child:panel.getComponents()){if(child instanceof JCheckBox)assertTrue(((JCheckBox)child).getPreferredSize().width<=206);if(child instanceof Container)assertCheckboxesFit((Container)child);}}
    static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);final Player player=mock(Player.class);final WorldView view=mock(WorldView.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.a");final AtomicReference<WorldPoint> location=new AtomicReference<>(new WorldPoint(3103,3261,0));final AtomicInteger ticks=new AtomicInteger(10),xp=new AtomicInteger(1000),markQuantity=new AtomicInteger();final List<Runnable> callbacks=new ArrayList<>();final Map<String,String> saved=new HashMap<>();final AgilityCoach coach;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->saved.get(call.getArgument(0)+"/"+call.getArgument(1)+"/"+call.getArgument(2)));
            doAnswer(call->{saved.put(call.getArgument(0)+"/"+call.getArgument(1)+"/"+call.getArgument(2),String.valueOf((Object)call.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),any());
            doAnswer(call->{callbacks.add(call.getArgument(0));return null;}).when(thread).invoke(any(Runnable.class));
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(client.getTickCount()).thenAnswer(call->ticks.get());when(client.getSkillExperience(Skill.AGILITY)).thenAnswer(call->xp.get());when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(50);when(client.getBoostedSkillLevel(Skill.AGILITY)).thenReturn(50);
            when(player.getWorldView()).thenReturn(view);when(player.getWorldLocation()).thenAnswer(call->location.get());when(player.getLocalLocation()).thenAnswer(call->new LocalPoint(6000,6000,view));when(view.getId()).thenReturn(-1);
            ItemContainer inventory=mock(ItemContainer.class);when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);when(inventory.getItems()).thenAnswer(call->new Item[]{new Item(ItemID.GRACE,markQuantity.get())});
            coach=new AgilityCoach(client,thread,config,new RunewardenDataStore(config,new Gson()),new RunewardenConfig(){},mock(net.runelite.client.ui.overlay.tooltip.TooltipManager.class));
        }
        void start(){coach.start();coach.onGameTick();}
        void tick(){ticks.incrementAndGet();coach.onGameTick();}
        void gain(int amount){xp.addAndGet(amount);coach.onStatChanged(new StatChanged(Skill.AGILITY,xp.get(),50,50));}
        void varbit(int id,int value){VarbitChanged event=new VarbitChanged();event.setVarbitId(id);event.setValue(value);coach.onVarbitChanged(event);}
        Tile tile(){Tile tile=mock(Tile.class);when(tile.getPlane()).thenReturn(0);when(tile.getWorldLocation()).thenAnswer(call->location.get());when(tile.getLocalLocation()).thenAnswer(call->new LocalPoint(6000,6000,view));return tile;}
        GameObject object(int id){GameObject object=mock(GameObject.class);when(object.getId()).thenReturn(id);when(object.getWorldLocation()).thenAnswer(call->location.get());when(object.getWorldView()).thenReturn(view);when(object.getLocalLocation()).thenAnswer(call->new LocalPoint(6000,6000,view));return object;}
    }
}
