/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmPatchSceneCacheTest
{
    @Test public void staticSceneReusesDiscoveryWhileCropVarbitsStayFresh()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        FarmPatchScene.Snapshot initial=f.base.planner.sceneReading();
        clearInvocations(f.scene,f.base.nativeState.client);
        when(f.base.nativeState.client.getVarbitValue(f.patch.varbit)).thenReturn(32);
        f.base.tick(40);
        assertSame(initial,f.base.planner.sceneReading());
        assertSame(f.base.catalog.state(f.patch.type,32),f.reading().state);
        verify(f.scene,never()).getTiles();
        verify(f.base.nativeState.client,never()).getObjectDefinition(1001);
        verify(f.base.nativeState.client,atLeast(40)).getVarbitValue(f.patch.varbit);
    }

    @Test public void changedAnchorIsHiddenImmediatelyAndReplacementIsDiscoveredOnce()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        clearInvocations(f.scene);
        f.base.planner.onGameObjectChanged(f.object);
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        when(f.tiles[0][32][32].getGameObjects()).thenReturn(new GameObject[0]);
        f.base.tick(1);
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        verify(f.scene,times(1)).getTiles();
        GameObject replacement=object(f,2002,f.patch.varbit);
        when(f.tiles[0][32][32].getGameObjects()).thenReturn(new GameObject[]{replacement});
        f.base.planner.onGameObjectChanged(replacement);
        f.base.planner.onGameObjectChanged(replacement);
        f.base.tick(20);
        assertSame(replacement,f.base.planner.sceneReading().anchors.get(0).object);
        verify(f.scene,times(2)).getTiles();
    }

    @Test public void unrelatedAndForeignObjectChangesDoNotInvalidateTheScene()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        FarmPatchScene.Snapshot initial=f.base.planner.sceneReading();
        GameObject furniture=object(f,2002,-1),foreign=object(f,2003,f.patch.varbit),upstairs=object(f,2004,f.patch.varbit);
        when(foreign.getWorldView()).thenReturn(mock(WorldView.class));
        when(upstairs.getPlane()).thenReturn(1);
        clearInvocations(f.scene,f.base.nativeState.client);
        for(int i=0;i<20;i++)
        {
            f.base.planner.onGameObjectChanged(furniture);
            f.base.planner.onGameObjectChanged(foreign);
            f.base.planner.onGameObjectChanged(upstairs);
            f.base.tick(1);
        }
        assertSame(initial,f.base.planner.sceneReading());
        verify(f.scene,never()).getTiles();
        verify(f.base.nativeState.client,never()).getObjectDefinition(2003);
        verify(f.base.nativeState.client,never()).getObjectDefinition(2004);
    }

    @Test public void baseSceneViewAndPlaneChangesEachRediscoverTheirOwnObjects()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        FarmPatchScene.Snapshot before=f.base.planner.sceneReading();
        when(f.view.getBaseX()).thenReturn(before.baseX+1);
        f.base.tick(1);assertNotSame(before,f.base.planner.sceneReading());
        before=f.base.planner.sceneReading();
        Scene replacement=mock(Scene.class);when(replacement.getTiles()).thenReturn(f.tiles);
        when(f.view.getScene()).thenReturn(replacement);
        f.base.tick(1);assertNotSame(before,f.base.planner.sceneReading());
        before=f.base.planner.sceneReading();
        WorldView next=mock(WorldView.class);
        when(next.getBaseX()).thenReturn(before.baseX);when(next.getBaseY()).thenReturn(before.baseY);
        when(next.getScene()).thenReturn(replacement);when(f.object.getWorldView()).thenReturn(next);
        when(f.base.nativeState.client.getTopLevelWorldView()).thenReturn(next);
        f.base.tick(1);assertNotSame(before,f.base.planner.sceneReading());
        assertEquals(1,f.base.planner.sceneReading().anchors.size());
        before=f.base.planner.sceneReading();
        Tile[][][] planes=new Tile[2][][];planes[0]=f.tiles[0];planes[1]=f.tiles[0];
        when(replacement.getTiles()).thenReturn(planes);when(next.getPlane()).thenReturn(1);when(f.object.getPlane()).thenReturn(1);
        f.base.tick(1);assertNotSame(before,f.base.planner.sceneReading());
        assertEquals(1,f.base.planner.sceneReading().plane);
        assertEquals(1,f.base.planner.sceneReading().anchors.size());
        clearInvocations(replacement);f.base.tick(20);verify(replacement,never()).getTiles();
    }

    @Test public void loadingAndProfileChangesDiscardCachedDiscovery()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        FarmPatchScene.Snapshot initial=f.base.planner.sceneReading();
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOADING);
        f.base.tick(1);assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        clearInvocations(f.scene);f.base.tick(3);
        assertNotSame(initial,f.base.planner.sceneReading());verify(f.scene,times(1)).getTiles();
        initial=f.base.planner.sceneReading();f.base.nativeState.profile.set("rsprofile.other");
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        clearInvocations(f.scene);f.base.tick(3);
        assertNotSame(initial,f.base.planner.sceneReading());verify(f.scene,times(1)).getTiles();
        assertEquals(1,f.base.planner.sceneReading().anchors.size());
    }

    @Test public void loadingEventWithoutGameTickDiscardsReusedSceneAnchorsAndKeepsAccountRun()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        FarmPatchPlanner.RunReading before=f.base.planner.currentRun();
        GameObject replacement=object(f,2002,f.patch.varbit);
        when(f.tiles[0][32][32].getGameObjects()).thenReturn(new GameObject[]{replacement});
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOADING);
        f.base.planner.onSceneLoading(); // Native GameTick does not run during scene loading.
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        clearInvocations(f.scene);f.base.tick(2);
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());verify(f.scene,never()).getTiles();
        f.base.tick(1);
        assertSame(replacement,f.base.planner.sceneReading().anchors.get(0).object);
        verify(f.scene,times(1)).getTiles();
        assertEquals(before.profile,f.base.planner.currentRun().profile);
        assertEquals(before.runId,f.base.planner.currentRun().runId);
        assertEquals(before.epoch,f.base.planner.currentRun().epoch);
    }

    @Test public void emptyNonFarmingSceneDoesNotRepeatedlyScanOrReadDefinitions()
    {
        FarmPatchHudTest.Fixture f=new FarmPatchHudTest.Fixture();
        when(f.view.getBaseX()).thenReturn(0);when(f.view.getBaseY()).thenReturn(0);
        f.base.location.set(new WorldPoint(32,32,0));
        clearInvocations(f.scene,f.base.nativeState.client);f.base.tick(40);
        assertTrue(f.base.planner.sceneReading().matches(f.view));
        assertTrue(f.base.planner.sceneReading().anchors.isEmpty());
        verify(f.scene,never()).getTiles();
        verify(f.base.nativeState.client,never()).getObjectDefinition(1001);
    }

    private static GameObject object(FarmPatchHudTest.Fixture f,int id,int varbit)
    {
        WorldPoint point=f.object.getWorldLocation();
        GameObject object=mock(GameObject.class);when(object.getId()).thenReturn(id);
        when(object.getWorldView()).thenReturn(f.view);when(object.getWorldLocation()).thenReturn(point);
        ObjectComposition definition=mock(ObjectComposition.class);when(definition.getVarbitId()).thenReturn(varbit);
        when(f.base.nativeState.client.getObjectDefinition(id)).thenReturn(definition);return object;
    }
}
