/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Movable, independently styled live agility card. */
public final class AgilityHudOverlay extends Overlay
{
    private final Client client;
    private final AgilityCoach coach;
    private final RunewardenConfig config;
    AgilityHudOverlay(Client client,AgilityCoach coach,RunewardenConfig config)
    {this.client=client;this.coach=coach;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        AgilityCoach.Snapshot value=coach.reading();HudPreferences.Style style=HudStyle.settings(config,"agility");
        if(!value.active()||!coach.preferences().hud||!HudStyle.visible(style)||client.getGameState()!=GameState.LOGGED_IN)return null;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            final int width=254;g.setFont(HudStyle.label(12));List<String> title=GuideCoachOverlay.wrap(g.getFontMetrics(),value.title,width-24,2);
            boolean timed="BRIMHAVEN".equals(value.key)&&value.arenaTicks>0;
            int height=110+title.size()*16+(timed?46:0)+(value.lapsGoal>0?16:0)+(value.progress>=0?16:0);
            HudStyle.panel(g,width,height,style);g.setColor(style.accent);g.setFont(HudStyle.label(10));g.drawString("AGILITY",12,18);
            g.setFont(HudStyle.label(12));g.setColor(style.text);int y=38;for(String line:title){g.drawString(line,12,y);y+=16;}
            g.setFont(HudStyle.body(12));g.drawString(String.format(Locale.ROOT,"%,d XP   ·   %,d XP/hr",value.xp,value.xpHour),12,y+3);y+=22;
            g.setColor(style.accent);g.setFont(HudStyle.label(12));g.drawString(value.laps+" laps",12,y);g.drawString(value.marks+" marks collected",99,y);y+=19;
            g.setColor(style.muted);g.setFont(HudStyle.body(11));g.drawString("Last "+AgilityCoach.duration(value.lastLap)+"   Best "+AgilityCoach.duration(value.bestLap)+"   "+value.lapsHour+"/hr",12,y);y+=17;
            if(value.groundMarks>0)g.drawString(value.groundMarks+" marks of grace on the ground",12,y);else g.drawString("Marks on ground: 0",12,y);y+=17;
            if(value.lapsGoal>0){g.drawString(value.lapsGoal+" laps to level "+coach.preferences().goal,12,y);y+=16;}
            if(value.progress>=0){g.drawString("Course progress: "+value.progress+" / 6",12,y);y+=16;}
            if(timed)
            {
                RadialTimer.paint(g,30,y+8,17,value.arenaTicks/100d,style.accent,Integer.toString((int)Math.ceil(value.arenaTicks*.6)),false);
                g.setColor(style.text);g.drawString("Next arena target",57,y+6);g.setColor(style.muted);g.drawString("Target changes every minute",57,y+22);
            }
            return HudStyle.dimension(style,width,height);
        }
        finally{g.dispose();}
    }
}
