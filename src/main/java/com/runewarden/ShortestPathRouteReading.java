/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.Plugin;

/** Reads only the companion's public, completed result; never creates another search. */
final class ShortestPathRouteReading
{
    final WorldPoint start,target;
    final Set<WorldPoint> targets;
    final boolean reached,bankVisited;
    final int cost;
    final String reason,itemStatus;
    final List<String> transports;
    ShortestPathRouteReading(WorldPoint start,WorldPoint target,Set<WorldPoint> targets,
        boolean reached,boolean bankVisited,int cost,String reason,String itemStatus,List<String> transports)
    {this.start=start;this.target=target;this.targets=Collections.unmodifiableSet(new LinkedHashSet<>(targets));this.reached=reached;
        this.bankVisited=bankVisited;this.cost=cost;this.reason=reason;this.itemStatus=itemStatus;this.transports=Collections.unmodifiableList(new ArrayList<>(transports));}

    static ShortestPathRouteReading read(Plugin plugin)
    {
        try
        {
            Object search=get(plugin,"getActiveSearch");
            if(search==null||!Boolean.TRUE.equals(get(search,"isDone")))return null;
            Object result=get(search,"getResult");if(result==null)return null;
            WorldPoint start=point(get(result,"getStart"));
            if(start==null||!Objects.equals(start,point(get(search,"getStart"))))return null;
            Object destinations=get(get(search,"getTargets"),"points");if(!(destinations instanceof Collection<?>))return null;
            Set<WorldPoint> targets=new LinkedHashSet<>();for(Object value:(Collection<?>)destinations)
            {WorldPoint point=point(value);if(point==null||targets.size()>=64)return null;targets.add(point);}
            boolean reached=Boolean.TRUE.equals(get(result,"isReached"));WorldPoint target=point(get(result,"getTarget"));
            if(reached&&(target==null||!targets.contains(target)))return null;
            Object path=get(result,"getPathSteps");if(!(path instanceof List<?>))return null;
            List<String> transports=new ArrayList<>();boolean bank=false;
            for(Object step:(List<?>)path)
            {
                bank=Boolean.TRUE.equals(get(step,"isBankVisited"));
                Object transport=get(step,"getTransport");
                if(transport!=null&&transports.size()<12)
                {Object display=get(transport,"getDisplayInfo");if(display instanceof String)transports.add(GuideNavigation.safe((String)display));}
            }
            Object arrival=get(result,"getArrival");String status=arrival==null?"":GuideNavigation.safe(String.valueOf(get(arrival,"getStatus")));
            return new ShortestPathRouteReading(start,target,targets,reached,bank,((Number)get(result,"getPathCost")).intValue(),
                GuideNavigation.safe(String.valueOf(get(result,"getTerminationReason"))),status,transports);
        }
        catch(ReflectiveOperationException|RuntimeException unsupported){return null;}
    }
    private static Object get(Object object,String method) throws ReflectiveOperationException
    {return object.getClass().getMethod(method).invoke(object);}
    private static WorldPoint point(Object value)
    {
        if(!(value instanceof Number)||((Number)value).intValue()==-1)return null;int packed=((Number)value).intValue();
        WorldPoint point=new WorldPoint(packed&0x7fff,(packed>>>15)&0x7fff,(packed>>>30)&3);
        return GuideNavigation.valid(point)?point:null;
    }
}
