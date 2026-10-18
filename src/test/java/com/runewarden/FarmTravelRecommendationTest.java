/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

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
    @Test public void missingDestinationAndDifferentFloorNeverBecomeNearbyAdvice()
    {
        WorldPoint patch=new WorldPoint(2858,3432,0);
        assertEquals("",FarmTravelPlan.RouteAdvice.EMPTY.recommendation("rsprofile.owner",patch));
        FarmTravelPlan.RouteAdvice upstairs=new FarmTravelPlan.RouteAdvice("rsprofile.owner",new WorldPoint(2858,3432,1),patch,false,false,false,List.of());
        assertEquals("",upstairs.recommendation("rsprofile.owner",patch));
        assertEquals("",upstairs.recommendation("rsprofile.owner",null));
    }
}
