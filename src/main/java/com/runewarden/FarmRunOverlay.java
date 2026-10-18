/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.MenuAction;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The current native farm-run checklist, with observed progress and elapsed time. */
@Singleton
final class FarmRunOverlay extends Overlay
{
    private static final int WIDTH=246,HEIGHT=142;
    private final FarmPatchPlanner planner;private final RunewardenConfig config;private final ItemManager items;
    private Runnable openAction;
    @Inject FarmRunOverlay(FarmPatchPlanner planner,RunewardenConfig config,ItemManager items)
    {
        this.planner=planner;this.config=config;this.items=items;
        setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);
        addMenuEntry(MenuAction.RUNELITE_OVERLAY,"Open harvest run","Runewarden",event->{if(openAction!=null)openAction.run();});
    }
    void setHudLocked(boolean locked){setMovable(!locked);}
    void setOpenAction(Runnable action){openAction=action;}
    @Override public Dimension render(Graphics2D graphics)
    {
        if(!config.farmRunHud())return null;
        FarmPatchPlanner.RunReading run=planner.currentRun();
        if(run==null||!run.active||run.nextStopId==null||run.totalStops<=run.completedStops)return null;
        HudPreferences.Style style=HudStyle.settings(config,"harvest-run");if(!HudStyle.visible(style))return null;
        BufferedImage icon=run.seedItemId>0&&items!=null?items.getImage(run.seedItemId):null;
        return paintRun(graphics,run,System.currentTimeMillis(),style,icon);
    }
    static Dimension paintRun(Graphics2D graphics,FarmPatchPlanner.RunReading run,long now,HudPreferences.Style style,BufferedImage icon)
    {
        Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            g.clipRect(0,0,WIDTH,HEIGHT);HudStyle.panel(g,WIDTH,HEIGHT,style);
            if(icon!=null)g.drawImage(icon,12,11,32,32,null);
            g.setColor(style.accent);g.setFont(HudStyle.label(12));g.drawString(fit(g,run.title.toUpperCase(java.util.Locale.ROOT),WIDTH-64),52,23);
            g.setColor(style.muted);g.setFont(HudStyle.body(11));g.drawString(run.completedStops+" / "+run.totalStops+" locations",52,40);
            g.setColor(style.text);g.setFont(HudStyle.label(12));
            String label="NEXT · "+(run.nextStopLabel==null?"Next location":run.nextStopLabel);
            String first=fit(g,label,WIDTH-24);g.drawString(first,12,65);
            if(!first.equals(label))
            {
                int cut=first.endsWith("…")?first.length()-1:first.length();
                g.drawString(fit(g,label.substring(Math.min(cut,label.length())).trim(),WIDTH-24),12,81);
            }
            String status=run.status;
            for(FarmPatchPlanner.StopReading stop:run.stops)if(stop.id.equals(run.nextStopId)){status=stop.status;break;}
            g.setFont(HudStyle.body(11));g.setColor(style.muted);g.drawString(fit(g,status,WIDTH-24),12,101);
            g.setColor(style.accent.darker().darker());g.fillRoundRect(12,112,WIDTH-24,6,4,4);
            double progress=run.totalStops==0?0:Math.max(0,Math.min(1,(double)run.completedStops/run.totalStops));
            g.setColor(style.accent);g.fillRoundRect(12,112,(int)Math.round((WIDTH-24)*progress),6,4,4);
            g.setColor(style.muted);g.drawString("Elapsed · "+elapsed(run.startedAt,now),12,134);
            long missing=run.supplies.stream().filter(supply->supply.missing>0).count();
            if(missing>0){String warning=missing+" supplies missing";g.setColor(style.accent);g.drawString(warning,WIDTH-12-g.getFontMetrics().stringWidth(warning),134);}
            return HudStyle.dimension(style,WIDTH,HEIGHT);
        }
        finally{g.dispose();}
    }
    private static String elapsed(long started,long now)
    {
        if(started<=0||now<started)return "—";long seconds=(now-started)/1000;
        return seconds>=3600?String.format(java.util.Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)
            :String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
    private static String fit(Graphics2D g,String value,int width)
    {
        if(value==null)return "";if(g.getFontMetrics().stringWidth(value)<=width)return value;
        while(!value.isEmpty()&&g.getFontMetrics().stringWidth(value+"…")>width)value=value.substring(0,value.length()-1);
        return value+"…";
    }
}
