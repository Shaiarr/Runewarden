/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmTravelPlanTest
{
    @Test public void publicRouteSnapshotCannotChangeWhenTheSenderReusesItsTransportList()
    {
        WorldPoint origin=new WorldPoint(3210,3420,0),target=new WorldPoint(2858,3432,0);
        List<String> transports=new ArrayList<>(List.of("Construction cape","Catherby Portal"));
        FarmTravelPlan.RouteAdvice advice=new FarmTravelPlan.RouteAdvice("owner",origin,target,true,true,true,transports);
        transports.clear();
        assertEquals("Construction cape → Catherby Portal",advice.recommendation("owner",target));
        try{advice.transports.clear();fail("Published native route data must be immutable");}
        catch(UnsupportedOperationException expected){ }
    }
    @Test public void sidebarAdviceSkipsEmptyResultsAndShowsAtMostSixSelectedTransports()
    {
        WorldPoint origin=new WorldPoint(3210,3420,0),target=new WorldPoint(2858,3432,0);
        List<String> transports=Arrays.asList("A"," ",null,"B","C","D","E","F","G");
        FarmTravelPlan.RouteAdvice advice=new FarmTravelPlan.RouteAdvice("owner",origin,target,true,true,true,transports);
        assertEquals("A → B → C → D → E → F",advice.recommendation("owner",target));
        FarmTravelPlan.RouteAdvice walking=new FarmTravelPlan.RouteAdvice("owner",origin,target,true,true,true,List.of());
        assertEquals("Walk to patch",walking.recommendation("owner",target));
    }
}
