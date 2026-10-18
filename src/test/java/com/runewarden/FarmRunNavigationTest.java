/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class FarmRunNavigationTest
{
    @Test public void nativeMapMarkerSnapshotMatchesEveryPinnedCoreCoordinate() throws Exception
    {
        Class<?> nativeType=Class.forName("net.runelite.client.plugins.worldmap.FarmingPatchLocation");
        java.lang.reflect.Method values=nativeType.getDeclaredMethod("values"),locations=nativeType.getDeclaredMethod("getLocations");
        values.setAccessible(true);locations.setAccessible(true);java.util.Set<WorldPoint> nativePoints=new java.util.HashSet<>();
        for(Object value:(Object[])values.invoke(null))for(WorldPoint point:(WorldPoint[])locations.invoke(value))nativePoints.add(point);
        assertEquals(60,FarmPatchDestinations.sites().size());
        for(FarmPatchDestinations.Site site:FarmPatchDestinations.sites())assertTrue(site.point.toString(),nativePoints.contains(site.point));
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());int mapped=0;
        for(FarmPatchCatalog.Patch patch:catalog.patches())
        {
            WorldPoint point=FarmPatchDestinations.forPatch(patch);
            if(point!=null){assertTrue(patch.id,patch.sentAt(point));assertTrue(nativePoints.contains(point));mapped++;}
        }
        assertEquals(97,mapped);
    }

    @Test public void startCheckAndFinishRouteAutomaticallyAndClearOnlyThroughTheOwnedPort()
    {
        Fixture f=new Fixture();assertTrue(f.port.points.isEmpty());f.base.planner.startSelectedRun();
        assertEquals(List.of(new WorldPoint(2663,3375,0)),f.port.points);f.base.tick(20);assertEquals(1,f.port.points.size());
        f.base.planner.checkNext(f.first.id,false);assertEquals(new WorldPoint(2810,3462,0),f.port.points.get(1));
        f.base.planner.checkNext(f.second.id,false);assertFalse(f.base.planner.currentRun().active);
        assertEquals(2,f.base.planner.currentRun().completedStops);assertTrue(f.port.clears>0);
        f.base.tick(10);assertEquals(2,f.port.points.size());
    }
    @Test public void nativeReplantRoutesNextOnlyAfterARealTransition()
    {
        Fixture f=new Fixture();f.base.planner.startSelectedRun();
        when(f.base.nativeState.client.getVarbitValue(f.first.varbit)).thenReturn(32);f.base.tick(5);
        assertEquals(1,f.base.planner.currentRun().completedStops);assertEquals(new WorldPoint(2810,3462,0),f.port.points.get(1));
    }
    @Test public void stopClearsRouteAndProfileSwitchNeverMigratesTheActiveRun()
    {
        Fixture f=new Fixture();f.base.planner.startSelectedRun();f.base.planner.stopActiveRun();f.base.tick(10);
        assertFalse(f.base.planner.currentRun().active);assertEquals(1,f.port.points.size());assertTrue(f.port.clears>0);
        f.base.planner.startSelectedRun();int routed=f.port.points.size();f.base.nativeState.profile.set("rsprofile.other");
        assertNull(f.base.planner.currentRun());f.base.tick(5);FarmPatchPlanner.RunReading next=f.base.planner.currentRun();
        assertEquals("rsprofile.other",next.profile);assertFalse(next.active);assertEquals(0,next.startedAt);assertEquals(0,next.completedStops);
        assertEquals(routed,f.port.points.size());
    }
    @Test public void failedShortestPathAvailabilityRetriesWithoutSpammingAndManualReplacementSuspendsIt()
    {
        FarmRunNavigation navigation=new FarmRunNavigation();Port port=new Port();port.accept=false;navigation.setPort(port);navigation.begin();WorldPoint point=new WorldPoint(2663,3375,0);
        for(int tick=0;tick<10;tick++)navigation.update("first",point,"Ardougne",tick);
        assertEquals(2,port.attempts);port.accept=true;navigation.update("first",point,"Ardougne",10);assertEquals(3,port.attempts);
        for(int tick=11;tick<40;tick++)navigation.update("first",point,"Ardougne",tick);assertEquals(3,port.attempts);
        port.revision++;navigation.update("second",new WorldPoint(2810,3462,0),"Catherby",40);assertEquals(3,port.attempts);
        navigation.begin();navigation.update("second",new WorldPoint(2810,3462,0),"Catherby",41);assertEquals(4,port.attempts);
    }
    @Test public void restartingTravelRestoresTheSameFarmDestinationOnceAndNeverOverridesAManualReplacement()
    {
        FarmRunNavigation navigation=new FarmRunNavigation();Port port=new Port();navigation.setPort(port);navigation.begin();WorldPoint point=new WorldPoint(2663,3375,0);
        navigation.update("first",point,"Ardougne",0);assertEquals(1,port.attempts);
        port.generation++;navigation.update("first",point,"Ardougne",1);assertEquals(2,port.attempts);
        navigation.update("first",point,"Ardougne",20);assertEquals(2,port.attempts);
        port.revision++;port.generation++;navigation.update("first",point,"Ardougne",21);assertEquals(2,port.attempts);
    }
    static final class Port implements FarmRunNavigation.Port
    {
        final List<WorldPoint> points=new ArrayList<>();long revision,generation;boolean accept=true;int clears,attempts;
        public long manualRevision(){return revision;}
        public long generation(){return generation;}
        public boolean route(WorldPoint point,String title,long expected){attempts++;if(!accept || expected!=revision)return false;points.add(point);return true;}
        public void clear(){clears++;}
    }

    static final class Fixture
    {
        final FarmPatchPlannerTest.Fixture base=new FarmPatchPlannerTest.Fixture();final FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();final Port port=new Port();
        final FarmPatchCatalog.Patch first=base.catalog.forGuide("herb").stream().filter(p->p.region.equals("Ardougne")).findFirst().get();
        final FarmPatchCatalog.Patch second=base.catalog.forGuide("herb").stream().filter(p->p.region.equals("Catherby")).findFirst().get();
        Fixture()
        {
            route.stops.add(first.id);route.stops.add(second.id);route.seedItemId=5295;route.quantity=1;route.compostItemId=0;
            FarmPatchPlanner.Saved saved=new FarmPatchPlanner.Saved();saved.routes.put("herb",route);
            base.nativeState.settings.put("runewarden|rsprofile.characterA|"+FarmPatchPlanner.KEY,new Gson().toJson(saved));
            base.planner.setNavigationPort(port);base.planner.start();base.tick(6);
        }
    }
}
