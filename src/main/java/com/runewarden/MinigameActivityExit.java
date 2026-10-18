/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** Retains witnessed activity membership across an unavailable HUD; only a proven departure closes it. */
final class MinigameActivityExit
{
    private MinigameActivityCatalog.Snapshot witnessed;
    private final Set<Long> areas=new HashSet<>();
    private Boolean instance;
    void clear(){witnessed=null;areas.clear();instance=null;}
    MinigameActivityCatalog.Snapshot observe(MinigameActivityCatalog.Snapshot next,Client client)
    {
        if(next==null||client==null||client.getGameState()!=GameState.LOGGED_IN)return null;
        Player player=client.getLocalPlayer();WorldView view=client.getTopLevelWorldView();
        if(player==null||view==null)return null;
        WorldPoint point=BossGuideLifecycle.sourcePoint(view,player.getLocalLocation(),player.getWorldLocation());
        if(point==null)return null;
        MinigameActivityCatalog.Snapshot left=null;
        if(witnessed!=null&&next.activity!=witnessed.activity)
        {
            boolean different=next.activity!=null;
            boolean outside=instance!=null&&instance!=view.isInstance()||!areas.contains(area(point))&&!knownInternal(witnessed.activity,point);
            if(different||outside){left=witnessed;clear();}
        }
        if(next.activity!=null)
        {
            if(witnessed==null||witnessed.activity!=next.activity){clear();instance=view.isInstance();}
            witnessed=next;if(areas.size()<32)areas.add(area(point));
        }
        return left;
    }
    private static boolean knownInternal(MinigameActivityCatalog.Activity activity,WorldPoint point)
    {
        int region=point.getRegionID();
        if(activity==MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA)return region==13462;
        if(activity==MinigameActivityCatalog.Activity.VOLCANIC_MINE)return (region==15262||region==15263)&&point.getPlane()==1;
        if(activity==MinigameActivityCatalog.Activity.BARBARIAN_ASSAULT)return region==7508||region==7509;
        return false;
    }
    private static long area(WorldPoint point){return ((long)point.getPlane()<<32)|(point.getRegionID()&0xffffffffL);}
}
