/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/** Immutable advice from a separately installed Shortest Path; no route budgets or planning engine. */
final class FarmTravelPlan
{
    /** A selected Shortest Path result, captured on the client thread for the farm sidebar. */
    static final class RouteAdvice
    {
        static final RouteAdvice EMPTY=new RouteAdvice(null,null,null,false,false,false,Collections.emptyList());
        final String profile;final WorldPoint origin,target;final boolean owned,answered,reached;final List<String> transports;
        RouteAdvice(String profile,WorldPoint origin,WorldPoint target,boolean owned,boolean answered,boolean reached,List<String> transports)
        {this.profile=profile;this.origin=origin;this.target=target;this.owned=owned;this.answered=answered;this.reached=reached;this.transports=Collections.unmodifiableList(new java.util.ArrayList<>(transports));}
        String recommendation(String currentProfile,WorldPoint destination)
        {
            if(profile==null||!profile.equals(currentProfile)||origin==null||destination==null)return "";
            if(origin.getPlane()==destination.getPlane()&&origin.distanceTo2D(destination)<=8)return "Nearby · walk to patch";
            if(!owned||!answered||!reached||!destination.equals(target))return "";
            java.util.List<String> selected=new java.util.ArrayList<>();
            for(String transport:transports)if(transport!=null&&!transport.isBlank()&&selected.size()<6)selected.add(transport);
            return selected.isEmpty()?"Walk to patch":String.join(" → ",selected);
        }
    }
}
