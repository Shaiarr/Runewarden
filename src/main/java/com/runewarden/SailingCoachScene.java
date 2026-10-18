/* SPDX-License-Identifier: GPL-3.0-only
 * Native mappings/logic adapted from pinned BSD sources.
 * Full notices: com/runewarden/licenses/sailing-coach-BSD.txt.
 */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class SailingCoachScene extends Overlay
{
    private final Client client;private final SailingCoach coach;
    SailingCoachScene(Client client,SailingCoach coach){this.client=client;this.coach=coach;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    @Override public Dimension render(Graphics2D graphics)
    {
        SailingCoach.Snapshot state=coach.reading();if(!state.active||client.getLocalPlayer()==null)return null;
        WorldView top=client.getTopLevelWorldView(),local=client.getLocalPlayer().getWorldView();
        for(SailingCoach.Target target:state.targets)
        {
            if(target.object.getWorldView()!=top&&target.object.getWorldView()!=local)continue;
            Polygon polygon=target.object.getCanvasTilePoly();if(polygon!=null)OverlayUtil.renderPolygon(graphics,polygon,target.color);
            if(coach.preferences().labels){Point point=target.object.getCanvasTextLocation(graphics,target.label,20);if(point!=null)OverlayUtil.renderTextLocation(graphics,point,target.label,target.color);}
        }
        return null;
    }
}
