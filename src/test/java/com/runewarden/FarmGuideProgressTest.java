/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmGuideProgressTest
{
    @Test public void openingAnAlreadyGrownPatchUsesItsNativeHarvestStepWithoutStartingARun()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();FarmPatchCatalog.Patch patch=ardougne(f);
        when(f.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(value(f.catalog,"HERB","RANARR","HARVESTABLE",0));
        f.planner.start();f.tick(6);GuideLiveProgress.Reading reading=f.planner.guideProgress("training:herb-runs");
        assertTrue(reading.known());assertEquals("method:1",reading.stepId);assertTrue(reading.body.startsWith("Harvest"));assertFalse(reading.terminal);assertNull(f.planner.currentRun());
        clearInvocations(f.nativeState.client);f.planner.guideProgress("training:herb-runs");verifyNoInteractions(f.nativeState.client);
    }
    @Test public void openingAtAnAlreadyPlantedHerbDoesNotRestartAtBringSeedsOrHarvest()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();when(f.nativeState.client.getVarbitValue(ardougne(f).varbit)).thenReturn(32);
        f.planner.start();f.tick(6);GuideLiveProgress.Reading reading=f.planner.guideProgress("training:herb-runs");
        assertEquals("method:3",reading.stepId);assertTrue(reading.body.contains("planted and growing"));assertTrue(reading.body.contains("Compost unknown"));assertTrue(reading.confirmedStepIds.isEmpty());
    }
    @Test public void matureTreeHealthPendingAndAlreadyCheckedTreeAreDifferentCurrentObjectives()
    {
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());FarmPatchCatalog.Patch tree=catalog.forGuide("training:fruit-tree-runs").get(0);
        FarmPatchCatalog.State mature=catalog.state("FRUIT_TREE",226),checked=catalog.state("FRUIT_TREE",value(catalog,"FRUIT_TREE","PALM","HARVESTABLE",0));
        GuideLiveProgress.Reading before=FarmGuideProgress.read("rsprofile.owner","training:fruit-tree-runs",null,new FarmPatchPlanner.PatchReading(tree,mature,1000,true),catalog);
        GuideLiveProgress.Reading after=FarmGuideProgress.read("rsprofile.owner","training:fruit-tree-runs",null,new FarmPatchPlanner.PatchReading(tree,checked,1000,true),catalog);
        assertEquals("method:1",before.stepId);assertEquals("method:2",after.stepId);assertTrue(after.body.contains("already checked"));assertNotEquals(before.objectiveKey,after.objectiveKey);
    }
    @Test public void aReplantDoesNotSilentlyCompleteTheMissingCompostOrPayment()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();FarmPatchCatalog.Patch patch=ardougne(f);FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();route.stops.add(patch.id);route.seeds.put("HERB",5295);route.compost=true;
        FarmPatchPlanner.Saved saved=new FarmPatchPlanner.Saved();saved.routes.put("training:herb-runs",route);f.nativeState.settings.put("runewarden|rsprofile.characterA|"+FarmPatchPlanner.KEY,new Gson().toJson(saved));
        f.planner.start();f.planner.selectGuide("training:herb-runs");f.tick(6);f.planner.startSelectedRun();when(f.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(32);f.tick(5);
        GuideLiveProgress.Reading reading=f.planner.guideProgress("training:herb-runs");assertEquals("method:2",reading.stepId);assertTrue(reading.body.contains("already planted"));assertTrue(reading.body.contains("Confirm"));assertFalse(reading.terminal);assertEquals(0,f.planner.currentRun().completedStops);
    }
    @Test public void checkedRouteCompletesButPausedOrEndedIncompleteRouteDoesNot()
    {
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());String guide="training:herb-runs";FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();route.startedAt=1000;route.stops.add(catalog.forGuide(guide).get(0).id);
        assertFalse(FarmGuideProgress.read("rsprofile.owner",guide,route,null,catalog).terminal);
        route.completed.addAll(route.stops);GuideLiveProgress.Reading done=FarmGuideProgress.read("rsprofile.owner",guide,route,null,catalog);assertTrue(done.terminal);assertTrue(done.known());
        route.startedAt=0;assertFalse("A browsed checklist is not a completed run",FarmGuideProgress.read("rsprofile.owner",guide,route,null,catalog).terminal);
    }
    @Test public void leavingTheObservedGardenOrSwitchingCharactersHidesOldLocalPatchObjectives()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();when(f.nativeState.client.getVarbitValue(ardougne(f).varbit)).thenReturn(32);f.planner.start();f.tick(6);assertTrue(f.planner.guideProgress("training:herb-runs").known());
        f.location.set(new WorldPoint(3200,3200,0));f.tick(1);assertFalse(f.planner.guideProgress("training:herb-runs").known());
        f.location.set(new WorldPoint(2663,3375,0));f.tick(6);assertTrue(f.planner.guideProgress("training:herb-runs").known());f.nativeState.profile.set("rsprofile.other");assertFalse(f.planner.guideProgress("training:herb-runs").known());
    }
    @Test public void activeRunUsesItsUnfinishedNextPatchInsteadOfTheFirstCompletedPatch() throws Exception
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.startSelectedRun();javax.swing.JPanel panel=f.panel();
        javax.swing.SwingUtilities.invokeAndWait(()->find(panel,"Mark next stop checked").doClick());javax.swing.SwingUtilities.invokeAndWait(()->{});
        f.base.location.set(new WorldPoint(2810,3462,0));when(f.base.nativeState.client.getVarbitValue(4774)).thenReturn(value(f.base.catalog,"HERB","RANARR","HARVESTABLE",0));f.base.tick(6);
        GuideLiveProgress.Reading reading=f.base.planner.guideProgress("training:herb-runs");assertTrue(reading.title.contains("Catherby"));assertEquals("method:1",reading.stepId);assertFalse(reading.objectiveKey.contains(f.first.id));
    }
    private static FarmPatchCatalog.Patch ardougne(FarmPatchPlannerTest.Fixture f){return f.catalog.forGuide("training:herb-runs").stream().filter(p->p.region.equals("Ardougne")).findFirst().get();}
    private static int value(FarmPatchCatalog catalog,String type,String crop,String state,int stage)
    {for(int value=0;value<256;value++){FarmPatchCatalog.State reading=catalog.state(type,value);if(reading!=null&&crop.equals(reading.crop)&&state.equals(reading.state)&&reading.stage==stage)return value;}throw new AssertionError(type+crop+state+stage);}
    private static javax.swing.JButton find(java.awt.Container root,String text)
    {for(java.awt.Component child:root.getComponents()){if(child instanceof javax.swing.JButton&&text.equals(((javax.swing.JButton)child).getText()))return(javax.swing.JButton)child;if(child instanceof java.awt.Container){javax.swing.JButton found=find((java.awt.Container)child,text);if(found!=null)return found;}}return null;}
}
