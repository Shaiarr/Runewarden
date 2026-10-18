/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Compact movable clue timers, rendered solely from client-thread snapshots. */
final class ClueJugglingOverlay extends Overlay
{
    private final ClueJugglingTool tool;private final RunewardenConfig config;
    ClueJugglingOverlay(ClueJugglingTool tool,RunewardenConfig config)
    {this.tool=tool;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);}
    void setLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        ClueJugglingTool.Preferences options=tool.preferences();ClueJugglingTool.Snapshot current=tool.reading();HudPreferences.Style style=HudStyle.settings(config,"clue-timers");
        if(!options.hud||current.rows.isEmpty()||!HudStyle.visible(style))return null;
        int count=options.combined?1:Math.min(8,current.rows.size()),width=218,height=24+count*43+(current.rows.size()>count?16:0);Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            HudStyle.panel(g,width,height,style);g.setFont(HudStyle.label(11));g.setColor(style.accent);g.drawString("CLUE JUGGLING",8,16);
            for(int i=0;i<count;i++)
            {
                ClueJugglingTracker.Row row=current.rows.get(i);int y=28+i*43;String timer=ClueJugglingTracker.time(row,options.ticks);
                boolean urgent=row.ticks>0&&row.ticks*.6<=options.notifySeconds;Color accent=urgent?new Color(243,125,99):style.accent;
                if(options.radial&&row.ticks>=0&&row.initialTicks>0)
                    RadialTimer.paint(g,22,y+15,13,(double)row.ticks/row.initialTicks,accent,"",!row.exact());
                else if(current.icons.get(row.id)!=null)g.drawImage(current.icons.get(row.id),7,y,null);
                g.setColor(style.text);g.setFont(HudStyle.label(11));String title=row.tier==ClueJugglingTracker.Tier.EXTRA?row.name:row.tier.label+" clue";
                while(title.length()>1&&g.getFontMetrics().stringWidth(title)>104)title=title.substring(0,title.length()-1);g.drawString(title,43,y+12);
                g.setColor(accent);g.drawString(timer,width-8-g.getFontMetrics().stringWidth(timer),y+12);
                g.setFont(HudStyle.body(9));g.setColor(style.muted);String status=row.ticks<0?"Revisit to verify expiry":row.certainty==ClueJugglingTracker.Certainty.REMEMBERED?"Saved · awaiting sighting":row.present?"In view":"Out of view";
                g.drawString(status+" · "+row.point.getX()+", "+row.point.getY(),43,y+28);
            }
            if(current.rows.size()>count){g.setColor(style.muted);g.setFont(HudStyle.body(10));g.drawString("+"+(current.rows.size()-count)+(options.combined?" tracked · earliest shown":" more tracked"),8,height-7);}
        }
        finally{g.dispose();}
        return HudStyle.dimension(style,width,height);
    }
}
