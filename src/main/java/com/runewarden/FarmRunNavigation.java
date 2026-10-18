/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** One route per active farm objective. A player's replacement path suspends automatic requests. */
final class FarmRunNavigation
{
    interface Port
    {
        long manualRevision();
        default long generation(){return 0;}
        boolean route(WorldPoint point,String title,long expectedManualRevision);
        void clear();
    }
    private Port port;
    private boolean active,suspended;
    private long revision,generation,lastAttempt=Long.MIN_VALUE;
    private String pendingStop,sentStop;
    private WorldPoint pendingPoint,sentPoint;
    void setPort(Port port){this.port=port;}
    void begin()
    {
        stop();active=true;suspended=false;revision=port==null?0:port.manualRevision();generation=port==null?0:port.generation();
    }
    void stop()
    {
        if((active || sentPoint!=null) && port!=null)port.clear();
        active=false;suspended=false;pendingStop=sentStop=null;pendingPoint=sentPoint=null;lastAttempt=Long.MIN_VALUE;
    }
    void update(String stop,WorldPoint point,String title,long tick)
    {
        if(!active || suspended || port==null)return;
        if(port.manualRevision()!=revision){suspended=true;return;}
        if(generation!=port.generation())
        {
            generation=port.generation();pendingStop=sentStop=null;pendingPoint=sentPoint=null;lastAttempt=Long.MIN_VALUE;
        }
        if(!Objects.equals(stop,pendingStop) || !Objects.equals(point,pendingPoint))
        {
            if(sentPoint!=null && !Objects.equals(point,sentPoint))port.clear();
            pendingStop=stop;pendingPoint=point;sentStop=null;sentPoint=null;lastAttempt=Long.MIN_VALUE;
        }
        if(point==null || Objects.equals(stop,sentStop)&&Objects.equals(point,sentPoint))return;
        if(lastAttempt!=Long.MIN_VALUE && tick-lastAttempt<5)return;
        lastAttempt=tick;
        if(port.route(point,title,revision)){sentStop=stop;sentPoint=point;generation=port.generation();}
    }
    boolean active(){return active;}
}
