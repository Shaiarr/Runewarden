/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** Validates a correlated result from Shortest Path's public PluginMessage API. */
final class ShortestPathRouteReading
{
    final boolean reached;
    final List<String> transports;
    private ShortestPathRouteReading(boolean reached,List<String> transports)
    {this.reached=reached;this.transports=Collections.unmodifiableList(transports);}

    static ShortestPathRouteReading read(Map<String,Object> data,String id,WorldPoint start,WorldPoint target)
    {
        if(data==null||!Objects.equals(id,data.get("id"))||!Objects.equals(start,data.get("start"))
            ||!Objects.equals(target,data.get("target"))||!(data.get("reached") instanceof Boolean))return null;
        Object rows=data.get("transports");if(!(rows instanceof List<?>)||((List<?>)rows).size()>512)return null;
        List<String> descriptions=new ArrayList<>();
        for(Object row:(List<?>)rows)
        {
            if(!(row instanceof Map<?,?>))return null;
            Object text=((Map<?,?>)row).get("displayInfo");if(!(text instanceof String))return null;
            if(descriptions.size()<12){String clean=safe((String)text);if(!clean.isEmpty())descriptions.add(clean);}
        }
        return new ShortestPathRouteReading(Boolean.TRUE.equals(data.get("reached")),descriptions);
    }
    static boolean sameTarget(WorldPoint expected,Object actual)
    {
        if(expected==null)return false;
        if(actual instanceof List<?>){List<?> points=(List<?>)actual;return points.size()==1&&expected.equals(points.get(0));}
        return expected.equals(actual);
    }
    static boolean valid(WorldPoint point)
    {return point!=null&&point.getX()>0&&point.getY()>0&&point.getX()<16384&&point.getY()<16384&&point.getPlane()>=0&&point.getPlane()<=3;}
    static String safe(String value)
    {
        if(value==null)return "";
        String text=value.substring(0,Math.min(value.length(),1200)).replaceAll("<[^>]*>","").replace('\n',' ').replace('\r',' ');
        return text.substring(0,Math.min(text.length(),400));
    }
}
