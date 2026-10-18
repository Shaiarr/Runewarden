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
    enum DoorState { UNKNOWN, OPEN, LOCKED }
    enum House
    {
        LAVINIA("Lavinia",13312,1666,3080,1676,3094,1668,3094,1670,3088),
        VICTOR("Victor",13313,1632,3093,1640,3103,1636,3103,1637,3090),
        CAIUS("Caius",13314,1631,3117,1640,3124,1631,3121,1638,3124);
        final String name;final int owner,minX,minY,maxX,maxY;final WorldPoint door,exit;
        House(String name,int owner,int minX,int minY,int maxX,int maxY,int dx,int dy,int ex,int ey)
        {this.name=name;this.owner=owner;this.minX=minX;this.minY=minY;this.maxX=maxX;this.maxY=maxY;door=new WorldPoint(dx,dy,0);exit=new WorldPoint(ex,ey,0);}
        boolean contains(WorldPoint p){return p!=null&&p.getPlane()==0&&p.getX()>=minX&&p.getX()<=maxX&&p.getY()>=minY&&p.getY()<=maxY;}
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
    private final EnumMap<House,DoorState> doors=new EnumMap<>(House.class);
    private final EnumMap<House,Long> departures=new EnumMap<>(House.class);
    private long distraction=-1,bonus=-1,loot=-1;
    private WorldPoint requested,looting;
    private boolean depleted,returnWarning;
    private House inside;
    void clear(){doors.clear();departures.clear();distraction=bonus=loot=-1;requested=looting=null;inside=null;depleted=returnWarning=false;}
    void observeDoor(House house,DoorState state,long now)
    {
        DoorState before=doors.getOrDefault(house,DoorState.UNKNOWN);doors.put(house,state);
        if(state==DoorState.LOCKED&&before==DoorState.OPEN)departures.put(house,now);
        else if(state==DoorState.OPEN||state==DoorState.UNKNOWN)departures.remove(house);
    }
    DoorState door(House house){return doors.getOrDefault(house,DoorState.UNKNOWN);}
    void enter(House next)
    {if(next!=inside){inside=next;bonus=loot=-1;requested=looting=null;depleted=returnWarning=false;}}
    void noticeDistraction(long now){distraction=now;}
    void noticeBonus(long now){if(inside!=null)bonus=now;}
    void warnReturn(){if(inside!=null)returnWarning=true;}
    void emptyContainer(){if(inside!=null){depleted=true;loot=-1;requested=null;}}
    void requestSearch(WorldPoint target)
    {if(inside!=null&&inside.contains(target)&&(!target.equals(looting)||depleted)){requested=target;looting=null;loot=-1;depleted=false;}}
    void observeValuables(int before,int after,long now)
    {if(inside!=null&&requested!=null&&after>before){looting=requested;requested=null;loot=now;}}
    Window distraction(long now){return distraction>=0&&now>=distraction&&now<distraction+24_000?new Window("Distraction",distraction,0,24_000):null;}
    Window nextDistraction(long now){return distraction>=0&&now>=distraction&&now<distraction+99_000?new Window("Next distraction",distraction,84_000,99_000):null;}
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
