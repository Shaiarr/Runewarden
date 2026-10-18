/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmTravelRecommendationTest
{
    @Test public void aSolvedCapeAndCatherbyPortalRouteIsDisplayedInsteadOfAStaticCamelotSuggestion()
    {
        WorldPoint catherby=new WorldPoint(2858,3432,0),origin=new WorldPoint(3210,3420,0);
        FarmTravelPlan.RouteAdvice advice=new FarmTravelPlan.RouteAdvice("rsprofile.owner",origin,catherby,true,true,true,List.of("Construction cape","Catherby Portal"));
        assertEquals("Construction cape → Catherby Portal",advice.recommendation("rsprofile.owner",catherby));
        assertFalse(advice.recommendation("rsprofile.owner",catherby).contains("Camelot"));
    }
    @Test public void unknownHouseCapabilitiesOrAnUnsolvedRouteCannotBecomeAHouseTeleportRecommendation()
    {
        WorldPoint catherby=new WorldPoint(2858,3432,0),origin=new WorldPoint(3210,3420,0);
        FarmTravelPlan.RouteAdvice pending=new FarmTravelPlan.RouteAdvice("rsprofile.owner",origin,catherby,true,false,false,List.of());
        assertEquals("",pending.recommendation("rsprofile.owner",catherby));
        FarmTravelPlan.RouteAdvice failed=new FarmTravelPlan.RouteAdvice("rsprofile.owner",origin,catherby,true,true,false,List.of("Construction cape","Catherby Portal"));
        assertEquals("",failed.recommendation("rsprofile.owner",catherby));
    }
    @Test public void anotherDestinationProfileOrPlayersReplacementRouteDoesNotLeakIntoFarmAdvice()
    {
        WorldPoint catherby=new WorldPoint(2858,3432,0),taverley=new WorldPoint(2933,3436,0),origin=new WorldPoint(3210,3420,0);
        FarmTravelPlan.RouteAdvice own=new FarmTravelPlan.RouteAdvice("rsprofile.owner",origin,catherby,true,true,true,List.of("Catherby Portal"));
        assertEquals("",own.recommendation("rsprofile.other",catherby));assertEquals("",own.recommendation("rsprofile.owner",taverley));
        FarmTravelPlan.RouteAdvice manual=new FarmTravelPlan.RouteAdvice("rsprofile.owner",origin,catherby,false,true,true,List.of("Catherby Portal"));
        assertEquals("",manual.recommendation("rsprofile.owner",catherby));
    }
    @Test public void alreadyAtTheFarmUsesWalkingInsteadOfRepeatingAnEarlierTeleport()
    {
        WorldPoint catherby=new WorldPoint(2858,3432,0);
        FarmTravelPlan.RouteAdvice arrived=new FarmTravelPlan.RouteAdvice("rsprofile.owner",new WorldPoint(2856,3430,0),catherby,true,true,true,List.of("Construction cape","Catherby Portal"));
        assertEquals("Nearby · walk to patch",arrived.recommendation("rsprofile.owner",catherby));
    }
    @Test public void farmPreparationExposesEveryEligibleNativeArrivalIncludingCatherbyWithoutStartingARun()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();f.planner.start();f.tick(6);
        List<GuideNavigation.Destination> options=f.planner.travelDestinations("training:fruit-tree-runs");
        assertTrue(options.size()>1);assertTrue(options.stream().anyMatch(option->option.title.contains("Catherby")&&option.point.equals(new WorldPoint(2858,3432,0))));
        assertFalse("A known unmet unlock is not an eligible arrival",options.stream().anyMatch(option->option.title.contains("Lletya")));
        assertTrue(options.stream().allMatch(option->"training:fruit-tree-runs".equals(option.guideId)));
        assertNull(f.planner.currentRun());
        clearInvocations(f.nativeState.client);f.planner.travelDestinations("training:fruit-tree-runs");verifyNoInteractions(f.nativeState.client);
    }
    @Test public void coldOrChangedProfilesWaitForEligibleFarmFactsRatherThanFallingBackToTaverley()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();f.planner.start();
        assertTrue(f.planner.travelDestinations("training:tree-runs").isEmpty());assertNull("Other guides retain their own sourced arrival provider",f.planner.travelDestinations("training:diamond-bracelets"));
        f.tick(6);assertFalse(f.planner.travelDestinations("training:tree-runs").isEmpty());f.nativeState.profile.set("rsprofile.other");assertTrue(f.planner.travelDestinations("training:tree-runs").isEmpty());
    }
    @Test public void anActiveCustomSequenceKeepsItsOwnNextStopInsteadOfTheCatalogsAlphabeticalFirstPatch()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture(false);java.util.Collections.reverse(f.route.stops);f.begin();f.base.planner.startSelectedRun();
        int posted=f.port.points.size();List<GuideNavigation.Destination> options=f.base.planner.travelDestinations("training:herb-runs");
        assertEquals(1,options.size());assertTrue(options.get(0).title.contains("Catherby"));assertEquals(new WorldPoint(2810,3462,0),options.get(0).point);
        assertEquals("Reading preview alternatives cannot post another route",posted,f.port.points.size());
        assertEquals(f.route.stops.get(0),f.base.planner.currentRun().nextStopId);
    }
    @Test public void savedPinsTakePriorityAndRepeatedCombinedGardenMarkersAreDeduplicated()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();
        List<FarmPatchCatalog.Patch> allotments=f.catalog.forGuide("training:allotment-runs").stream().filter(p->p.region.equals("Ardougne")).collect(java.util.stream.Collectors.toList());
        allotments.forEach(p->route.stops.add(p.id));route.destinations.put(allotments.get(0).id,new int[]{2662,3374,0});
        FarmPatchPlanner.Saved saved=new FarmPatchPlanner.Saved();saved.routes.put("training:allotment-runs",route);f.nativeState.settings.put("runewarden|rsprofile.characterA|"+FarmPatchPlanner.KEY,new Gson().toJson(saved));
        f.planner.start();f.tick(6);List<GuideNavigation.Destination> options=f.planner.travelDestinations("training:allotment-runs");
        assertEquals(2,options.size());assertTrue(options.stream().anyMatch(option->option.point.equals(new WorldPoint(2662,3374,0))));
        assertEquals(1,options.stream().filter(option->option.point.equals(new WorldPoint(2663,3375,0))).count());
    }
}
