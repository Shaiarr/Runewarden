/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class VarlamoreThievingSceneOverlay extends Overlay
{
    private final Client client;private final VarlamoreThievingCoach coach;
    VarlamoreThievingSceneOverlay(Client client,VarlamoreThievingCoach coach)
    {this.client=client;this.coach=coach;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    @Override public Dimension render(Graphics2D graphics)
    {
        VarlamoreThievingCoach.Snapshot state=coach.reading();if(!state.active||client.getLocalPlayer()==null)return null;
        WorldView view=client.getTopLevelWorldView();if(view==null)return null;VarlamoreThievingCoach.Preferences prefs=coach.preferences();
        for(VarlamoreThievingCoach.Target target:state.targets)
        {
            Polygon polygon;Point text;
            if(target.npc!=null)
            {
                NPC npc=target.npc;
                // Snapshots can outlive a native NPC despawn or composition teardown.
                // Actor projection reads that composition's footprint internally.
                if(npc.getWorldView()!=view||view.npcs()==null||view.npcs().byIndex(npc.getIndex())!=npc
                    ||npc.getComposition()==null||npc.getTransformedComposition()==null)continue;
                polygon=npc.getCanvasTilePoly();text=npc.getCanvasTextLocation(graphics,target.label,35);
            }
            else
            {
                if(target.object==null||target.object.getWorldView()!=view||target.object.getPlane()!=view.getPlane())continue;
                polygon=target.object.getCanvasTilePoly();text=target.object.getCanvasTextLocation(graphics,target.label,30);
            }
            if(polygon!=null)OverlayUtil.renderPolygon(graphics,polygon,target.color);
            if(text==null)continue;
            if(prefs.radialTimers&&target.timer!=null)
            {
                long now=coach.now(),seconds=Math.max(0,(target.timer.latest-now+999)/1000);
                RadialTimer.paint(graphics,text.getX(),text.getY()-24,17,RadialTimer.fraction(now,target.timer.started,target.timer.latest),target.color,seconds+"s",true);
            }
            if(prefs.labels)OverlayUtil.renderTextLocation(graphics,text,target.label,target.color);
        }
        return null;
    }
}
