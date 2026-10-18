/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Cached native item icons and compact capacity bars; no native work in the paint loop. */
final class ContainerHudOverlay extends Overlay
{
    private static final int WIDTH=264;private final ContainerHudTool tool;private final RunewardenConfig config;
    ContainerHudOverlay(ContainerHudTool tool,RunewardenConfig config)
    {this.tool=tool;this.config=config;setPosition(OverlayPosition.BOTTOM_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style=HudStyle.settings(config,"container-hud");ContainerHudTool.Snapshot reading=tool.snapshot();
        if(!tool.enabled()||!HudStyle.visible(style)||reading.containers.isEmpty())return null;
        Graphics2D g=HudStyle.canvas(graphics,style);try{return paint(g,style,reading);}finally{g.dispose();}
    }
    static Dimension paint(Graphics2D g,HudPreferences.Style style,ContainerHudTool.Snapshot state)
    {
        int height=31;for(ContainerHudTool.Container row:state.containers)height+=53+row.contents.size()*21;
        HudStyle.panel(g,WIDTH,height,style);g.setFont(HudStyle.label(12));g.setColor(style.accent);g.drawString("CONTAINERS",12,20);int y=45;
        for(ContainerHudTool.Container row:state.containers)
        {
            if(row.image!=null)g.drawImage(row.image,10,y-13,28,24,null);
            g.setFont(HudStyle.body(12));g.setColor(style.text);g.drawString(row.name,45,y);
            String count=row.known()?row.count+" / "+ContainerHudRules.CAPACITY:"? / "+ContainerHudRules.CAPACITY;
            g.drawString(count,WIDTH-12-g.getFontMetrics().stringWidth(count),y);
            g.setFont(HudStyle.label(10));g.setColor(HudStyle.muted(style));g.drawString(row.location.title,45,y+14);
            if(!row.known()){String unknown="Unknown";g.drawString(unknown,WIDTH-12-g.getFontMetrics().stringWidth(unknown),y+14);}
            int barY=y+23;g.setColor(new Color(255,255,255,25));g.fillRoundRect(12,barY,240,6,4,4);
            if(row.known()){g.setColor(style.accent);g.fillRoundRect(12,barY,(int)Math.round(240*row.count/(double)ContainerHudRules.CAPACITY),6,4,4);}
            y+=47;
            for(ContainerHudTool.Content content:row.contents)
            {if(content.image!=null)g.drawImage(content.image,16,y-12,21,18,null);g.setFont(HudStyle.body(11));g.setColor(style.text);g.drawString(content.name,45,y+1);String amount=Integer.toString(content.amount);g.drawString(amount,WIDTH-12-g.getFontMetrics().stringWidth(amount),y+1);y+=21;}
            y+=6;
        }
        return HudStyle.dimension(style,WIDTH,height);
    }
}
