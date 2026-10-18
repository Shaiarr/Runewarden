/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercise the real native scene projection, rather than manufacturing a distant canvas point. */
public class DistantSceneLabelsTest
{
    @Test public void nativeRespawnTimerProjectsAndDrawsFortyEightTilesFromThePlayer()
    {
        NodeFixture f=new NodeFixture();
        assertEquals(48,f.node.distanceTo(f.playerPoint));
        assertEquals(1,f.coach.nodes().size());
        assertTrue(f.coach.nodes().get(0).respawn);
        LocalPoint local=LocalPoint.fromWorld(f.view,f.node);
        assertNotNull(local);
        Point projected=Perspective.localToCanvas(f.client,local,0,80);
        assertNotNull(projected);assertEquals(400,projected.getX());assertEquals(300,projected.getY());
        BufferedImage image=f.render();
        assertNotEquals("An observed node stays readable when the player walks beyond the old eighteen-tile cutoff",0,image.getRGB(400,290));
    }

    @Test public void distantRespawnLabelStillRequiresTheSameLoadedPlaneWorldViewAndLoggedInClient()
    {
        NodeFixture f=new NodeFixture();assertTrue(painted(f.render()));
        when(f.view.getPlane()).thenReturn(1);assertFalse(painted(f.render()));
        when(f.view.getPlane()).thenReturn(0);when(f.view.getId()).thenReturn(77);assertFalse(painted(f.render()));
        when(f.view.getId()).thenReturn(WorldView.TOPLEVEL);when(f.view.getBaseX()).thenReturn(0);assertFalse(painted(f.render()));
        when(f.view.getBaseX()).thenReturn(3200);when(f.client.getCameraY()).thenReturn(48*128+64+2048);assertFalse(painted(f.render()));
        when(f.client.getCameraY()).thenReturn(48*128+64-2048);when(f.client.getGameState()).thenReturn(GameState.LOADING);assertFalse(painted(f.render()));
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(f.config.nodeTimers()).thenReturn(false);assertFalse(painted(f.render()));
    }

    @Test public void birdHouseLookupUsesTheWholeLoadedSceneAndRejectsForeignOrUnloadedObjects()
    {
        HarvestClockTool.BirdHouseSite site=HarvestClockTool.BIRD_SITES.get(0);
        WorldView view=mock(WorldView.class);Scene scene=mock(Scene.class);GameObject object=mock(GameObject.class);
        Tile[][][] tiles=new Tile[1][104][104];Tile tile=mock(Tile.class);tiles[0][93][98]=tile;
        when(view.getScene()).thenReturn(scene);when(scene.getTiles()).thenReturn(tiles);
        when(view.getBaseX()).thenReturn(site.point.getX()-93);when(view.getBaseY()).thenReturn(site.point.getY()-98);
        when(object.getId()).thenReturn(ObjectID.BIRDHOUSE_1);when(object.getWorldView()).thenReturn(view);
        when(tile.getGameObjects()).thenReturn(new GameObject[]{object});
        WorldPoint distantPlayer=new WorldPoint(view.getBaseX()+12,view.getBaseY()+12,0);
        assertTrue(site.point.distanceTo(distantPlayer)>80);
        assertSame("A native bird house is found at the far end of the loaded scene",object,BirdHouseSceneOverlay.findObject(view,site));
        when(object.getWorldView()).thenReturn(mock(WorldView.class));assertNull(BirdHouseSceneOverlay.findObject(view,site));
        when(object.getWorldView()).thenReturn(view);when(object.getPlane()).thenReturn(1);assertNull(BirdHouseSceneOverlay.findObject(view,site));
        when(object.getPlane()).thenReturn(0);tiles[0][93][98]=null;assertNull(BirdHouseSceneOverlay.findObject(view,site));
    }

    private static boolean painted(BufferedImage image)
    {
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0)return true;
        return false;
    }

    private static final class NodeFixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final WorldView view=mock(WorldView.class);
        final Scene scene=mock(Scene.class);final RunewardenConfig config=mock(RunewardenConfig.class);
        final WorldPoint playerPoint=new WorldPoint(3208,3208,0),node=new WorldPoint(3256,3248,0);
        final SkillingCoach coach=new SkillingCoach(client,mock(ItemManager.class),mock(ConfigManager.class),config);
        final NodeTimerOverlay overlay=new NodeTimerOverlay(client,config,coach);
        NodeFixture()
        {
            when(config.nodeTimers()).thenReturn(true);when(config.hudOpacity()).thenReturn(85);
            when(config.nodeTimerDisplay()).thenReturn(RunewardenConfig.TimerDisplay.TEXT);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(view);when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(view);
            when(player.getWorldView()).thenReturn(view);when(player.getWorldLocation()).thenReturn(playerPoint);
            when(view.getId()).thenReturn(WorldView.TOPLEVEL);when(view.getBaseX()).thenReturn(3200);when(view.getBaseY()).thenReturn(3200);
            when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);when(view.getScene()).thenReturn(scene);
            when(scene.getTiles()).thenReturn(new Tile[4][104][104]);
            when(view.getTileSettings()).thenReturn(new byte[4][104][104]);when(view.getTileHeights()).thenReturn(new int[4][105][105]);
            when(client.getViewportWidth()).thenReturn(800);when(client.getViewportHeight()).thenReturn(600);when(client.getScale()).thenReturn(512);
            when(client.getCameraX()).thenReturn(56*128+64);when(client.getCameraY()).thenReturn(48*128+64-2048);when(client.getCameraZ()).thenReturn(-80);
            coach.startUp();ScriptEvent script=mock(ScriptEvent.class);
            when(script.getArguments()).thenReturn(new Object[]{ScriptID.ADD_OVERLAYTIMER_LOC,(node.getX()<<14)|node.getY(),ObjectID.ROSEWOOD_TREE_UPDATE_STUMP,0,0,199});
            ScriptPreFired event=new ScriptPreFired(ScriptID.ADD_OVERLAYTIMER_LOC);event.setScriptEvent(script);coach.onScriptPreFired(event);
        }
        BufferedImage render()
        {
            BufferedImage image=new BufferedImage(800,600,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
            try{overlay.render(graphics);}finally{graphics.dispose();}
            return image;
        }
    }
}
