/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Movable local action/batch HUD; bars fill while rings show remaining time. */
final class ActionProgressOverlay extends Overlay
{
    private static final int WIDTH=264;
    private final ActionProgressTool tool;private final RunewardenConfig config;
    ActionProgressOverlay(ActionProgressTool tool,RunewardenConfig config)
    {this.tool=tool;this.config=config;setPosition(OverlayPosition.BOTTOM_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style=HudStyle.settings(config,"action-progress");if(!HudStyle.visible(style)||!tool.enabled())return null;
        ActionProgressTool.Snapshot state=tool.snapshot();if(!state.cycle()&&!state.batch())return null;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try{return paint(g,style,state,tool.preferences(),tool.now());}finally{g.dispose();}
    }
    static Dimension paint(Graphics2D g,HudPreferences.Style style,ActionProgressTool.Snapshot state,ActionProgressTool.Preferences prefs,long now)
    {
        int height=32+(state.cycle()?51:0)+(state.batch()?58:0);HudStyle.panel(g,WIDTH,height,style);
        g.setColor(style.accent);g.setFont(HudStyle.label(12));g.drawString("ACTION PROGRESS",12,20);int y=43;
        if(state.cycle())
        {
            long left=Math.max(0,state.cycleEnd-now);double remaining=RadialTimer.fraction(now,state.cycleStart,state.cycleEnd);
            int textLeft=icon(g,state.cycleIcon,y);
            g.setFont(HudStyle.body(12));g.setColor(style.text);g.drawString(fit(g,state.title,WIDTH-textLeft-78),textLeft,y);
            if(prefs.seconds){String seconds=seconds(left,!state.exactCycle);g.drawString(seconds,WIDTH-12-g.getFontMetrics().stringWidth(seconds),y);}
            g.setFont(HudStyle.label(10));g.setColor(HudStyle.muted(style));g.drawString(state.basis,textLeft,y+14);
            progress(g,style,remaining,1-remaining,y+29,prefs.display,"",!state.exactCycle);y+=51;
        }
        if(state.batch())
        {
            int textLeft=icon(g,state.batchIcon,y);
            g.setFont(HudStyle.body(12));g.setColor(style.text);g.drawString(fit(g,state.batchTitle,WIDTH-textLeft-12),textLeft,y);
            g.setFont(HudStyle.label(10));g.setColor(HudStyle.muted(style));
            g.drawString(state.completed+" / "+state.batchTotal+" · "+state.remaining+" left",textLeft,y+15);
            if(prefs.seconds){String eta=state.batchEta<0?"ETA —":seconds(state.batchEta,true);g.drawString(eta,WIDTH-12-g.getFontMetrics().stringWidth(eta),y+15);}
            progress(g,style,1-state.batchFraction(),state.batchFraction(),y+31,prefs.display,"",true);
        }
        return HudStyle.dimension(style,WIDTH,height);
    }
    private static int icon(Graphics2D g,BufferedImage image,int y)
    {
        if(image==null)return 12;
        double scale=Math.min(1,Math.min(30d/image.getWidth(),28d/image.getHeight()));
        int width=Math.max(1,(int)Math.round(image.getWidth()*scale)),height=Math.max(1,(int)Math.round(image.getHeight()*scale));
        g.drawImage(image,12+(30-width)/2,y-12,width,height,null);return 48;
    }
    private static void progress(Graphics2D g,HudPreferences.Style style,double remaining,double done,int y,ActionProgressTool.Display display,String label,boolean estimated)
    {
        int left=12;
        if(display!=ActionProgressTool.Display.BAR){RadialTimer.paint(g,25,y-3,11,remaining,style.accent,label,estimated);left=44;}
        if(display!=ActionProgressTool.Display.RING)
        {
            int width=WIDTH-left-12;g.setColor(HudStyle.muted(style));g.fillRoundRect(left,y-7,width,8,6,6);
            g.setColor(style.accent);g.fillRoundRect(left,y-7,(int)Math.round(width*Math.max(0,Math.min(1,done))),8,6,6);
        }
    }
    static String seconds(long millis,boolean estimated)
    {return(estimated?"~":"")+String.format(Locale.ROOT,"%.1fs",Math.max(0,millis)/1000d);}
    private static String fit(Graphics2D g,String text,int width)
    {if(text==null)return "";while(text.length()>1&&g.getFontMetrics().stringWidth(text)>width)text=text.substring(0,text.length()-1);return text;}
}
