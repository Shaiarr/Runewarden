/* SPDX-License-Identifier: BSD-2-Clause
 * Native mappings/logic adapted from pinned BSD sources.
 * Full notices: com/runewarden/licenses/sailing-coach-BSD.txt.
 */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class SailingCoachHud extends Overlay
{
    private final Client client;private final SailingCoach coach;private final RunewardenConfig config;
    SailingCoachHud(Client client,SailingCoach coach,RunewardenConfig config)
    {this.client=client;this.coach=coach;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        SailingCoach.Snapshot state=coach.reading();SailingCoach.Preferences p=coach.preferences();HudPreferences.Style style=HudStyle.settings(config,"sailing");
        if(!state.active||!p.hud||!HudStyle.visible(style))return null;Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            int width=286;g.setFont(HudStyle.body(12));List<String> objective=GuideCoachOverlay.wrap(g.getFontMetrics(),state.objective,width-24,3);
            List<String> lines=new ArrayList<>(),ordered=new ArrayList<>(state.facts);ordered.sort(java.util.Comparator.comparingInt(s->s.startsWith("Trial progress")?0:s.startsWith("Observed run")?1:s.startsWith("Latest split")?2:3));
            for(String fact:ordered){if(lines.size()>=(p.compact?4:9))break;lines.addAll(GuideCoachOverlay.wrap(g.getFontMetrics(),fact,width-24,Math.min(2,(p.compact?4:9)-lines.size())));}
            int height=58+objective.size()*16+lines.size()*16;double fit=Math.min(p.scalePercent/100d,Math.min(client.getCanvasWidth()>0?(client.getCanvasWidth()-16)/(double)width:1,client.getCanvasHeight()>0?(client.getCanvasHeight()-20)/(double)height:1));fit=Math.max(.5,fit);g.scale(fit,fit);HudStyle.panel(g,width,height,style);
            g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("SAILING COMMAND",12,18);g.setFont(HudStyle.label(12));g.setColor(style.text);g.drawString(state.title,12,38);
            int y=59;g.setFont(HudStyle.body(12));g.setColor(state.objective.startsWith("LOW HULL")?new java.awt.Color(255,119,100):style.accent);for(String line:objective){g.drawString(line,12,y);y+=16;}
            g.setColor(HudStyle.muted(style));for(String line:lines){g.drawString(line,12,y);y+=16;}
            return HudStyle.dimension(style,(int)Math.ceil(width*fit),(int)Math.ceil(height*fit));
        }
        finally{g.dispose();}
    }
}
