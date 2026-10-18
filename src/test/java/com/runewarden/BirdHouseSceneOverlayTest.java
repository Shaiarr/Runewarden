/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.inject.Injector;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class BirdHouseSceneOverlayTest
{
    @Test public void eachHouseUsesOnlyItsOwnNativeTimestampAndItem()
    {
        Fixture f=new Fixture();long now=1_000_000;
        f.record(0,3,now-100);f.record(1,27,now-4000);f.record(2,5,now-60);f.record(3,0,now-60);
        List<HarvestClockTool.BirdHouseReading> birds=HarvestClockTool.readBirdHouses(f.manager,f.client,"account-one",now);
        assertEquals(4,birds.size());assertEquals("48m 20s",birds.get(0).status(now));assertEquals("Ready",birds.get(1).status(now));
        assertEquals("Add seeds",birds.get(2).status(now));assertEquals("Empty",birds.get(3).status(now));
        assertEquals(21512,birds.get(0).itemId());assertEquals(22204,birds.get(1).itemId());assertEquals(21515,birds.get(2).itemId());
        verify(f.manager).getConfiguration("timetracking","account-one","birdhouse.1626");
        verify(f.manager).getConfiguration("timetracking","account-one","birdhouse.1629");
        try{birds.clear();fail("Immutable native observations");}catch(UnsupportedOperationException expected){ }
    }
    @Test public void missingMalformedFutureOrMismatchedRecordsNeverProduceAClock()
    {
        Fixture f=new Fixture();long now=1000;
        f.records.put("birdhouse.1626","3:900");f.states.put(1626,6);
        f.records.put("birdhouse.1627","27:1001");f.states.put(1627,27);
        f.records.put("birdhouse.1628","bad:900");f.states.put(1628,3);
        f.records.put("birdhouse.1629","30:900");f.states.put(1629,30);
        assertTrue(HarvestClockTool.readBirdHouses(f.manager,f.client,"account-one",now).isEmpty());
        assertTrue(HarvestClockTool.readBirdHouses(f.manager,f.client,"another-account",now).isEmpty());
    }
    @Test public void exactHouseLookupRejectsOtherObjectsInstancesPlanesAndUnloadedTiles()
    {
        Fixture f=new Fixture();HarvestClockTool.BirdHouseSite site=HarvestClockTool.BIRD_SITES.get(0);
        assertSame(f.object,BirdHouseSceneOverlay.findObject(f.view,site));
        when(f.object.getId()).thenReturn(1001);assertNull(BirdHouseSceneOverlay.findObject(f.view,site));
        when(f.object.getId()).thenReturn(ObjectID.BIRDHOUSE_1);when(f.view.isInstance()).thenReturn(true);assertNull(BirdHouseSceneOverlay.findObject(f.view,site));
        when(f.view.isInstance()).thenReturn(false);when(f.view.getPlane()).thenReturn(1);assertNull(BirdHouseSceneOverlay.findObject(f.view,site));
        when(f.view.getPlane()).thenReturn(0);when(f.view.getBaseX()).thenReturn(0);assertNull(BirdHouseSceneOverlay.findObject(f.view,site));
    }
    @Test public void sceneCardAndTetherUseActualHouseObjectAndHideOnStateOrProfileChange() throws Exception
    {
        Fixture f=new Fixture();f.record(0,3,Instant.now().getEpochSecond()-100);f.start();
        try
        {
            BufferedImage image=f.render();assertNotEquals("Native house card",0,image.getRGB(200,180));
            assertNotEquals("Real projected object endpoint",0,image.getRGB(235,300));assertNotEquals(0,image.getRGB(235,250));
            verify(f.items).getImage(21512);
            f.states.put(1626,0);assertEquals("Changed varp hides stale clock before next tracker poll",0,f.render().getRGB(235,300));
            f.states.put(1626,3);f.profile.set("account-two");assertEquals(0,f.render().getRGB(235,300));
        }
        finally{f.stop();}
    }
    @Test public void deselectedBirdHousesAndDisabledHarvestHudHaveNoSceneCard() throws Exception
    {
        Fixture f=new Fixture();f.record(0,3,Instant.now().getEpochSecond()-100);f.start();
        try
        {
            assertNotEquals(0,f.render().getRGB(235,300));
            f.tool.setGroupsTracked(List.of("bird-house"),false);assertEquals(0,f.render().getRGB(235,300));
            f.tool.setGroupsTracked(List.of("bird-house"),true);when(f.config.harvestHud()).thenReturn(false);assertEquals(0,f.render().getRGB(235,300));
        }
        finally{f.stop();}
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager manager=mock(ConfigManager.class);
        final PluginManager plugins=mock(PluginManager.class);final RunewardenConfig config=mock(RunewardenConfig.class);
        final ItemManager items=mock(ItemManager.class);final WorldView view=mock(WorldView.class);final GameObject object=mock(GameObject.class);
        final AtomicReference<String> profile=new AtomicReference<>("account-one");final Map<String,String> records=new HashMap<>();final Map<Integer,Integer> states=new HashMap<>();
        final HarvestClockTool tool=new HarvestClockTool(client,manager,plugins,mock(OverlayManager.class),config);
        final BirdHouseSceneOverlay overlay=new BirdHouseSceneOverlay(client,tool,config,items);
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getTopLevelWorldView()).thenReturn(view);when(client.getCanvasWidth()).thenReturn(400);when(client.getCanvasHeight()).thenReturn(400);
            when(manager.getRSProfileKey()).thenAnswer(call->profile.get());
            when(manager.getConfiguration(eq("timetracking"),anyString(),anyString())).thenAnswer(call->"account-one".equals(call.getArgument(1))?records.get(call.getArgument(2)):null);
            when(client.getVarpValue(anyInt())).thenAnswer(call->states.getOrDefault(call.getArgument(0),-1));
            when(config.harvestHud()).thenReturn(true);when(config.hudOpacity()).thenReturn(85);
            when(config.harvestTimerDisplay()).thenReturn(RunewardenConfig.TimerDisplay.BOTH);
            Scene scene=mock(Scene.class);when(view.getScene()).thenReturn(scene);Tile[][][] tiles=new Tile[1][64][64];when(scene.getTiles()).thenReturn(tiles);
            HarvestClockTool.BirdHouseSite site=HarvestClockTool.BIRD_SITES.get(0);when(view.getBaseX()).thenReturn(site.point.getX()-32);when(view.getBaseY()).thenReturn(site.point.getY()-32);
            Tile tile=mock(Tile.class);tiles[0][32][32]=tile;when(tile.getGameObjects()).thenReturn(new GameObject[]{object});
            when(object.getId()).thenReturn(ObjectID.BIRDHOUSE_1);when(object.getWorldView()).thenReturn(view);
            when(object.getCanvasTextLocation(any(),anyString(),anyInt())).thenReturn(new Point(200,220));when(object.getCanvasLocation()).thenReturn(new Point(235,300));
            Injector injector=mock(Injector.class);TimeTrackingPlugin nativePlugin=new NativeTracker(injector);
            when(plugins.getPlugins()).thenReturn(List.of(nativePlugin));when(plugins.isPluginActive(nativePlugin)).thenReturn(true);
            FarmingTracker farming=mock(FarmingTracker.class);BirdHouseTracker birds=mock(BirdHouseTracker.class);
            when(injector.getInstance(FarmingTracker.class)).thenReturn(farming);when(injector.getInstance(BirdHouseTracker.class)).thenReturn(birds);
            when(injector.getInstance(FarmingContractManager.class)).thenReturn(mock(FarmingContractManager.class));
            when(birds.getSummary()).thenReturn(SummaryState.IN_PROGRESS);when(birds.getCompletionTime()).thenReturn(Instant.now().getEpochSecond()+2900);
            for(Tab tab:Tab.FARMING_TABS){when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);when(farming.getCompletionTime(tab)).thenReturn(-1L);}
        }
        void record(int index,int state,long observed){HarvestClockTool.BirdHouseSite site=HarvestClockTool.BIRD_SITES.get(index);states.put(site.varp,state);records.put("birdhouse."+site.varp,state+":"+observed);}
        void start(){tool.start();for(int tick=0;tick<5;tick++)tool.onGameTick();}
        void stop() throws Exception{tool.stop();SwingUtilities.invokeAndWait(()->{});}
        BufferedImage render(){BufferedImage image=new BufferedImage(400,400,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();overlay.render(g);g.dispose();return image;}
    }
    private static final class NativeTracker extends TimeTrackingPlugin
    {NativeTracker(Injector injector){this.injector=injector;}}
}
