/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

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
    @Test public void startingFarmRunRoutesToTheFirstRealFarmMarkerWithoutAPinOrRouteButton() throws Exception
    {
        Fixture f=new Fixture();assertTrue(f.port.points.isEmpty());f.base.planner.startSelectedRun();
        assertEquals(List.of(new WorldPoint(2663,3375,0)),f.port.points);
        f.base.tick(20);assertEquals("Unchanged objectives must not resubmit the same path",1,f.port.points.size());
        JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->button(panel,"Mark next stop checked").doClick());flush();
        assertEquals(new WorldPoint(2810,3462,0),f.port.points.get(1));assertEquals(2,f.port.points.size());
        assertNull("There is no separate path button",button(panel,"Route to saved anchor"));
        f.base.planner.stopActiveRun();f.base.tick(20);assertFalse(f.base.planner.currentRun().active);assertEquals(2,f.port.points.size());assertTrue(f.port.clears>0);
    }
    @Test public void nativeReplantCompletionAutomaticallyRoutesTheNextPatch()
    {
        Fixture f=new Fixture();f.base.planner.startSelectedRun();
        when(f.base.nativeState.client.getVarbitValue(f.first.varbit)).thenReturn(32);f.base.tick(5);
        assertEquals(1,f.base.planner.currentRun().completedStops);assertEquals(new WorldPoint(2810,3462,0),f.port.points.get(1));
    }
    @Test public void aPlayerPinTakesPrecedenceOverTheNativeMapMarker()
    {
        Fixture f=new Fixture(false);f.route.destinations.put(f.first.id,new int[]{2662,3374,0});f.begin();f.base.planner.startSelectedRun();
        assertEquals(new WorldPoint(2662,3374,0),f.port.points.get(0));
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
    @Test public void endRunInvalidatesQueuedStartAndCompletionButtons() throws Exception
    {
        Fixture f=new Fixture();f.base.planner.startSelectedRun();JPanel panel=f.panel();List<Runnable> pending=new ArrayList<>();
        doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(f.base.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(()->{button(panel,"New run").doClick();button(panel,"Mark next stop checked").doClick();});
        f.base.planner.stopActiveRun();pending.forEach(Runnable::run);f.base.tick(20);flush();
        assertFalse(f.base.planner.currentRun().active);assertEquals(1,f.port.points.size());
    }
    @Test public void restartingTravelRestoresTheSameFarmDestinationOnceAndNeverOverridesAManualReplacement()
    {
        FarmRunNavigation navigation=new FarmRunNavigation();Port port=new Port();navigation.setPort(port);navigation.begin();WorldPoint point=new WorldPoint(2663,3375,0);
        navigation.update("first",point,"Ardougne",0);assertEquals(1,port.attempts);
        port.generation++;navigation.update("first",point,"Ardougne",1);assertEquals(2,port.attempts);
        navigation.update("first",point,"Ardougne",20);assertEquals(2,port.attempts);
        port.revision++;port.generation++;navigation.update("first",point,"Ardougne",21);assertEquals(2,port.attempts);
    }
    @Test public void profileChangeClearsOwnedRoutingAndDoesNotRouteTheNewCharacter()
    {
        Fixture f=new Fixture();f.base.planner.startSelectedRun();f.base.nativeState.profile.set("rsprofile.other");
        assertNull("Previous character data must hide before the next native tick",f.base.planner.currentRun());f.base.tick(20);
        // The planner may prepare an idle route for the new profile's still-visible picker. It
        // must contain none of the old run's lifecycle or completion state and never draw a path.
        FarmPatchPlanner.RunReading fresh=f.base.planner.currentRun();assertNotNull(fresh);assertEquals("rsprofile.other",fresh.profile);
        assertFalse(fresh.active);assertEquals(0,fresh.startedAt);assertEquals(0,fresh.completedStops);
        assertEquals(1,f.port.points.size());assertTrue(f.port.clears>0);
        String saved=f.base.nativeState.settings.get("runewarden|rsprofile.other|"+FarmPatchPlanner.KEY);
        if(saved!=null)assertTrue("Fresh native observations may save, old routes must not migrate",new Gson().fromJson(saved,FarmPatchPlanner.Saved.class).routes.isEmpty());
    }
    @Test public void finalCheckedStopEndsTheRunAndClearsItsPath() throws Exception
    {
        Fixture f=new Fixture();java.util.concurrent.atomic.AtomicInteger ended=new java.util.concurrent.atomic.AtomicInteger();f.base.planner.setFinishAction(ended::incrementAndGet);f.base.planner.startSelectedRun();
        JPanel panel=f.panel();SwingUtilities.invokeAndWait(()->button(panel,"Mark next stop checked").doClick());flush();
        SwingUtilities.invokeAndWait(()->button(panel,"Mark next stop checked").doClick());flush();
        assertFalse(f.base.planner.currentRun().active);assertEquals(0,f.base.planner.currentRun().remainingStops);assertEquals(1,ended.get());assertTrue(f.port.clears>=2);
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
        final FarmPatchCatalog.Patch first=base.catalog.forGuide("training:herb-runs").stream().filter(p->p.region.equals("Ardougne")).findFirst().get();
        Fixture(){this(true);}
        Fixture(boolean begin)
        {
            FarmPatchCatalog.Patch second=base.catalog.forGuide("training:herb-runs").stream().filter(p->p.region.equals("Catherby")).findFirst().get();
            route.stops.add(first.id);route.stops.add(second.id);route.seeds.put("HERB",5295);route.compost=false;
            base.planner.setNavigationPort(port);if(begin)begin();
        }
        void begin(){FarmPatchPlanner.Saved saved=new FarmPatchPlanner.Saved();saved.routes.put("training:herb-runs",route);base.nativeState.settings.put("runewarden|rsprofile.characterA|"+FarmPatchPlanner.KEY,new Gson().toJson(saved));base.planner.start();base.planner.selectGuide("training:herb-runs");base.tick(6);}
        JPanel panel() throws Exception{JPanel[] result=new JPanel[1];SwingUtilities.invokeAndWait(()->result[0]=base.planner.createPanel());flush();return result[0];}
    }
    private static JButton button(Container parent,String label)
    {for(Component child:parent.getComponents()){if(child instanceof JButton && label.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,label);if(found!=null)return found;}}return null;}
    private static void flush() throws Exception{SwingUtilities.invokeAndWait(()->{});SwingUtilities.invokeAndWait(()->{});}
}
