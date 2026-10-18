/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Task reporting appears only beside a matching creature in a verified task context. */
final class SlayerTaskOverlay extends Overlay
{
    private final SlayerTaskHud tool;private final RunewardenConfig config;private final ItemManager items;
    @Inject SlayerTaskOverlay(SlayerTaskHud tool,RunewardenConfig config,ItemManager items)
    {
        this.tool=tool;this.config=config;this.items=items;
        setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);
    }
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D original)
    {
        SlayerTaskHud.Reading view=tool.reading();HudPreferences.Style style=HudStyle.settings(config,"slayer-task");
        if(!config.slayerTaskHud()||!view.active()||!view.context||!HudStyle.visible(style))return null;
        int width=268;
        Graphics2D g=HudStyle.canvas(original,style);
        try
        {
            g.setFont(HudStyle.body(11));java.util.List<String> lines=wrap(g,view.itemNote,248);int height=126+lines.size()*14;
            g.clipRect(0,0,width,height);HudStyle.panel(g,width,height,style);g.setColor(style.accent);g.setFont(HudStyle.label(11));g.drawString("SLAYER",10,18);
            BufferedImage icon=items==null?null:items.getImage(view.iconId);if(icon!=null)g.drawImage(icon,10,26,30,28,null);
            g.setColor(style.text);g.setFont(HudStyle.label(13));g.drawString(HudStyle.fit(g,view.name,210),46,39);
            g.setFont(HudStyle.body(12));g.drawString(view.counts(),46,56);
            g.setColor(style.muted);g.setFont(HudStyle.body(11));g.drawString(HudStyle.fit(g,view.area(),248),10,76);
            g.drawString("Points balance "+view.points,10,95);String streak=view.streakLabel+" "+view.streak;
            g.drawString(streak,10,111);
            int y=134;for(String line:lines){g.drawString(line,10,y);y+=14;}
            return HudStyle.dimension(style,width,height);
        }
        finally{g.dispose();}
    }
    private static java.util.List<String> wrap(Graphics2D g,String text,int width)
    {
        java.util.List<String> lines=new java.util.ArrayList<>();if(text.isEmpty())return lines;String line="";
        for(String word:text.split(" ")){if(!line.isEmpty()&&g.getFontMetrics().stringWidth(line+" "+word)>width){lines.add(line);line=word;}else line+=line.isEmpty()?word:" "+word;}
        if(!line.isEmpty())lines.add(line);return lines;
    }
}
