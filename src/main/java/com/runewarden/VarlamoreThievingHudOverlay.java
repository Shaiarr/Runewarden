/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

final class VarlamoreThievingHudOverlay extends Overlay
{
    private final Client client;private final VarlamoreThievingCoach coach;private final RunewardenConfig config;
    VarlamoreThievingHudOverlay(Client client,VarlamoreThievingCoach coach,RunewardenConfig config)
    {this.client=client;this.coach=coach;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean value){setMovable(!value);}
    @Override public Dimension render(Graphics2D graphics)
    {
        VarlamoreThievingCoach.Snapshot state=coach.reading();VarlamoreThievingCoach.Preferences prefs=coach.preferences();
        HudPreferences.Style style=HudStyle.settings(config,"thieving");if(!state.active||!prefs.hud||!HudStyle.visible(style))return null;
        Graphics2D g=HudStyle.canvas(graphics,style);int width=284;
        try
        {
            g.setFont(HudStyle.body(12));List<String> objective=wrap(g.getFontMetrics(),state.objective,width-24,prefs.compact?2:3);
            int factCount=Math.min(prefs.compact?2:5,state.facts.size()),timers=prefs.radialTimers?Math.min(3,state.timers.size()):0;
            int height=59+objective.size()*16+factCount*16+(timers>0?70:0);
            double fit=Math.min(prefs.scalePercent/100d,Math.min(client.getCanvasWidth()>0?(client.getCanvasWidth()-16)/(double)width:1,client.getCanvasHeight()>0?(client.getCanvasHeight()-20)/(double)height:1));
            fit=Math.max(.5,fit);g.scale(fit,fit);HudStyle.panel(g,width,height,style);
            g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("VARLAMORE THIEVING",12,18);
            g.setFont(HudStyle.label(12));g.setColor(style.text);g.drawString(state.title,12,38);
            int y=58;g.setFont(HudStyle.body(12));g.setColor(state.objective.startsWith("OWNER")?VarlamoreThievingCoach.WARNING:style.accent);
            for(String line:objective){g.drawString(line,12,y);y+=16;}
            g.setFont(HudStyle.body(11));g.setColor(HudStyle.muted(style));
            for(int i=0;i<factCount;i++){String text=state.facts.get(i);while(text.length()>1&&g.getFontMetrics().stringWidth(text)>width-24)text=text.substring(0,text.length()-1);g.drawString(text,12,y);y+=16;}
            for(int i=0;i<timers;i++)
            {
                VarlamoreThievingState.Window timer=state.timers.get(i);long now=coach.now();int x=48+i*94;
                long seconds=Math.max(0,(timer.latest-now+999)/1000);
                RadialTimer.paint(g,x,y+25,20,RadialTimer.fraction(now,timer.started,timer.latest),timer.label.equals("Bonus opportunity")?VarlamoreThievingCoach.BONUS:style.accent,seconds>0?seconds+"s":"due",true);
                String label=timer.label.equals("Next distraction")?"Next cycle":timer.label.equals("Bonus opportunity")?"Bonus":timer.label;
                g.setFont(HudStyle.body(10));g.setColor(HudStyle.muted(style));g.drawString(label,x-g.getFontMetrics().stringWidth(label)/2,y+58);
            }
            return HudStyle.dimension(style,(int)Math.ceil(width*fit),(int)Math.ceil(height*fit));
        }
        finally{g.dispose();}
    }

    private static List<String> wrap(FontMetrics metrics, String text, int width, int maximum)
    {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (width <= 0 || maximum <= 0) return lines;
        while (!remaining.isEmpty() && lines.size() < maximum)
        {
            if (metrics.stringWidth(remaining) <= width) { lines.add(remaining); remaining = ""; break; }
            int end = 1;
            while (end < remaining.length() && metrics.stringWidth(remaining.substring(0, end + 1)) <= width) end++;
            int space = remaining.lastIndexOf(' ', end);
            if (space > 0) end = space;
            lines.add(remaining.substring(0, end).trim());
            remaining = remaining.substring(end).trim();
        }
        if (!remaining.isEmpty() && !lines.isEmpty())
        {
            int last = lines.size() - 1;
            String line = lines.get(last);
            while (!line.isEmpty() && metrics.stringWidth(line + "…") > width) line = line.substring(0, line.length() - 1);
            lines.set(last, line.stripTrailing() + "…");
        }
        return lines;
    }

}
