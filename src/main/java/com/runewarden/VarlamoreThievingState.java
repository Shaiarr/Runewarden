/* SPDX-License-Identifier: BSD-2-Clause
 * House coordinates and native observations adapted from sololegends' MIT plugin.
 * See com/runewarden/licenses/varlamore-thieving-MIT.txt.
 */
package com.runewarden;

import java.util.EnumMap;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;

/** Observed starts and source-backed timing ranges; unknown starts remain unknown. */
final class VarlamoreThievingState
{
    private static final long DISTRACTION_MAX=24_000, CYCLE_MIN=84_000, CYCLE_MAX=99_000, START_DEDUP=3_000;
    enum DoorState { UNKNOWN, OPEN, LOCKED }
    enum House
    {
        LAVINIA("Lavinia",13312,1666,3080,1676,3094,1668,3094,1670,3088),
        VICTOR("Victor",13313,1632,3093,1640,3103,1636,3103,1637,3090),
        CAIUS("Caius",13314,1631,3117,1640,3124,1631,3121,1638,3124);
        final String name;final int owner,minX,minY,maxX,maxY;final WorldPoint door,exit,exterior;
        House(String name,int owner,int minX,int minY,int maxX,int maxY,int dx,int dy,int ex,int ey)
        {this.name=name;this.owner=owner;this.minX=minX;this.minY=minY;this.maxX=maxX;this.maxY=maxY;door=new WorldPoint(dx,dy,0);exit=new WorldPoint(ex,ey,0);exterior=new WorldPoint(dx==minX?dx-1:dx,dy==maxY?dy+1:dy,0);}
        boolean contains(WorldPoint p){return near(p,0);}
        boolean near(WorldPoint p,int margin){return p!=null&&p.getPlane()==0&&p.getX()>=minX-margin&&p.getX()<=maxX+margin&&p.getY()>=minY-margin&&p.getY()<=maxY+margin;}
        static House at(WorldPoint point){for(House h:values())if(h.contains(point))return h;return null;}
        static House doorAt(WorldPoint point){if(point!=null)for(House h:values())if(h.door.equals(point))return h;return null;}
        static House owner(int id){for(House h:values())if(h.owner==id)return h;return null;}
    }
    static final class Window
    {
        final String label;final long started,earliest,latest;
        Window(String label,long started,long earliestDelay,long latestDelay)
        {this.label=label;this.started=started;earliest=started+earliestDelay;latest=started+latestDelay;}
        String remaining(long now)
        {
            if(now>=latest)return "Timing window passed";
            long low=Math.max(0,(earliest-now+999)/1000),high=Math.max(0,(latest-now+999)/1000);
            return low==0?"≤ "+high+"s":low==high?"~"+high+"s":"~"+low+"–"+high+"s";
        }
    }
    /** A native active flag plus bounds anchored to the last witnessed or first-seen start. */
    static final class DistractionReading
    {
        static final DistractionReading EMPTY=new DistractionReading(false,false,-1,-1,null,null);
        final boolean active,synced;
        final long observedAt,started;
        final Window remaining,next;
        DistractionReading(boolean active,boolean synced,long observedAt,long started,Window remaining,Window next)
        {this.active=active;this.synced=synced;this.observedAt=observedAt;this.started=started;this.remaining=remaining;this.next=next;}
        String elapsed(long now)
        {return observedAt<0||now<observedAt?"Unknown":(synced?"~":"≥ ")+((now-observedAt)/1000)+"s";}
        String left(long now)
        {
            if(!active)return "Not distracted";
            return remaining!=null&&now<remaining.latest?"≤ "+((remaining.latest-now+999)/1000)+"s":"Active · timing unknown";
        }
        String nextText(long now)
        {return next==null?"Waiting for first distraction":now>=next.latest?"Due · waiting":next.remaining(now);}
    }
    private final EnumMap<House,DoorState> doors=new EnumMap<>(House.class);
    private final EnumMap<House,Long> departures=new EnumMap<>(House.class);
    private long distraction=-1,distractionObservedAt=-1,lastDistractionNotice=-1,bonus=-1,loot=-1;
    private boolean distractionActive,distractionSynced,distractionNativeObserved;
    private WorldPoint requested,looting;
    private boolean depleted,returnWarning;
    private House inside;
    void clear()
    {clearHouseObservations();distraction=distractionObservedAt=lastDistractionNotice=-1;distractionActive=distractionSynced=distractionNativeObserved=false;}
    void clearHouseObservations()
    {doors.clear();departures.clear();bonus=loot=-1;requested=looting=null;inside=null;depleted=returnWarning=false;}
    void observeDoor(House house,DoorState state,long now)
    {
        DoorState before=doors.getOrDefault(house,DoorState.UNKNOWN);doors.put(house,state);
        if(state==DoorState.LOCKED&&before==DoorState.OPEN)departures.put(house,now);
        else if(state==DoorState.OPEN||state==DoorState.UNKNOWN)departures.remove(house);
    }
    DoorState door(House house){return doors.getOrDefault(house,DoorState.UNKNOWN);}
    void enter(House next)
    {if(next!=inside){inside=next;bonus=loot=-1;requested=looting=null;depleted=returnWarning=false;}}
    void noticeDistraction(long now)
    {
        if(lastDistractionNotice>=0&&now>=lastDistractionNotice&&now-lastDistractionNotice<=START_DEDUP)return;
        lastDistractionNotice=now;
        // The same cycle can be witnessed through chat and several native NPCs in one tick.
        if(distractionSynced&&distraction>=0&&now>=distraction&&now-distraction<=START_DEDUP)return;
        startDistraction(now,true,false);
    }
    void observeDistraction(boolean active,boolean witnessedTransition,long now)
    {
        if(!active){distractionActive=false;return;}
        boolean stillActive=distractionActive&&(distractionNativeObserved||now>=distraction&&now-distraction<DISTRACTION_MAX);
        if(!stillActive)
        {
            // A temporary missing interaction cannot be another cycle before its earliest possible start.
            long earliestNext=CYCLE_MIN-(distractionSynced?0:DISTRACTION_MAX);
            if(distraction>=0&&now>=distraction&&now-distraction<earliestNext)
                distractionActive=true;
            else startDistraction(now,witnessedTransition,true);
        }
        distractionNativeObserved=true;
    }
    private void startDistraction(long now,boolean synced,boolean nativeObserved)
    {distraction=distractionObservedAt=now;distractionSynced=synced;distractionActive=true;distractionNativeObserved=nativeObserved;}
    DistractionReading distractionReading(long now)
    {
        if(distraction<0||now<distraction)return DistractionReading.EMPTY;
        boolean active=distractionActive&&(distractionNativeObserved||now-distraction<DISTRACTION_MAX);
        Window remaining=active&&now-distraction<DISTRACTION_MAX?new Window("Distraction",distraction,0,DISTRACTION_MAX):null;
        // A first-seen active NPC may already be anywhere within its 24-second window.
        Window next=new Window("Next distraction",distraction,CYCLE_MIN-(distractionSynced?0:DISTRACTION_MAX),CYCLE_MAX);
        return new DistractionReading(active,distractionSynced,distractionObservedAt,distraction,remaining,next);
    }
    void noticeBonus(long now){if(inside!=null)bonus=now;}
    void warnReturn(){if(inside!=null)returnWarning=true;}
    void emptyContainer(){if(inside!=null){depleted=true;loot=-1;requested=null;}}
    void requestSearch(WorldPoint target)
    {if(inside!=null&&inside.contains(target)&&(!target.equals(looting)||depleted)){requested=target;looting=null;loot=-1;depleted=false;}}
    void observeValuables(int before,int after,long now)
    {if(inside!=null&&requested!=null&&after>before){looting=requested;requested=null;loot=now;}}
    Window distraction(long now){return distractionReading(now).remaining;}
    Window nextDistraction(long now){return distractionReading(now).next;}
    Window ownerReturn(House house,long now)
    {Long start=departures.get(house);return start!=null&&now>=start?new Window("Owner return",start,180_000,190_000):null;}
    Window bonus(long now){return bonus>=0&&now>=bonus&&now<bonus+7_000?new Window("Bonus opportunity",bonus,0,7_000):null;}
    Window container(long now){return loot>=0&&now>=loot&&now<loot+60_000?new Window("Container",loot,50_000,60_000):null;}
    boolean depleted(){return depleted;}
    boolean returnWarning(){return returnWarning;}
    WorldPoint looting(){return looting;}
    static long tradeIn(int quantity,int glory){return Math.max(0L,quantity)*(glory>=8000?65L:55L);}
    static boolean activityArea(WorldPoint point)
    {return point!=null&&point.getPlane()==0&&(point.getRegionID()==6448||point.getRegionID()==6704);}
    static String count(long amount){return String.format(Locale.ROOT,"%,d",Math.max(0,amount));}
}
