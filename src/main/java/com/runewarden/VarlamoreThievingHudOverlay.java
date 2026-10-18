/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
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
    private static final int WIDTH=284;
    private final Client client;
    private final VarlamoreThievingCoach coach;
    private final RunewardenConfig config;

    VarlamoreThievingHudOverlay(Client client,VarlamoreThievingCoach coach,RunewardenConfig config)
    {
        this.client=client;this.coach=coach;this.config=config;
        setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);setResizable(false);
    }
    void setHudLocked(boolean value){setMovable(!value);}

    @Override public Dimension render(Graphics2D graphics)
    {
        VarlamoreThievingCoach.Snapshot state=coach.reading();
        VarlamoreThievingCoach.Preferences prefs=coach.preferences();
        HudPreferences.Style style=HudStyle.settings(config,"thieving");
        if(!state.active||!prefs.hud||!HudStyle.visible(style))return null;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            long now=coach.now();
            VarlamoreThievingState.DistractionReading distraction=state.distraction;
            boolean distracted=distraction!=null&&distraction.active;
            g.setFont(HudStyle.body(11));
            List<String> objective=wrap(g.getFontMetrics(),state.objective,WIDTH-24,2);
            String houseText=state.houseStatus==null?"":state.houseStatus;
            if(state.availableHouse!=null)
            {
                String prefix=state.availableHouse.name+"'s house · ";
                if(houseText.startsWith(prefix))houseText=houseText.substring(prefix.length());
            }
            List<String> houseStatus=wrap(g.getFontMetrics(),houseText.replace("\n"," · "),WIDTH-40,2);
            List<String> facts=new ArrayList<>();
            for(String fact:state.facts)
            {
                // These values have prominent live cards below; do not repeat a stale tick snapshot.
                if(!fact.startsWith("Distraction ·")&&!fact.startsWith("Next distraction ·"))facts.add(fact);
            }
            List<VarlamoreThievingState.Window> timers=new ArrayList<>();
            if(prefs.radialTimers)for(VarlamoreThievingState.Window timer:state.timers)
                if(!timer.label.equals("Distraction")&&!timer.label.equals("Next distraction")&&timers.size()<3)timers.add(timer);
            int timingHeight=(distracted?56:0)+44;
            int houseHeight=44+houseStatus.size()*14;
            int fixedHeight=46+objective.size()*14+6+timingHeight+houseHeight+(timers.isEmpty()?0:64)+10;
            int factCount=Math.min(prefs.compact?2:4,Math.min(facts.size(),Math.max(0,(330-fixedHeight)/14)));
            int height=fixedHeight+factCount*14;
            double base=style.scale/100d*style.font/12d;
            double fit=Math.min(prefs.scalePercent/100d,Math.min(
                client.getCanvasWidth()>0?(client.getCanvasWidth()-16)/(WIDTH*base):1,
                client.getCanvasHeight()>0?(client.getCanvasHeight()-20)/(height*base):1));
            fit=Math.max(.25,fit);g.scale(fit,fit);HudStyle.panel(g,WIDTH,height,style);
            g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("VARLAMORE THIEVING",12,18);
            g.setFont(HudStyle.label(13));g.setColor(style.text);g.drawString(state.title,12,38);
            int y=54;g.setFont(HudStyle.body(11));
            g.setColor(state.objective.startsWith("OWNER")?VarlamoreThievingCoach.WARNING:style.text);
            for(String line:objective){g.drawString(line,12,y);y+=14;}
            y+=2;
            if(distracted)
            {
                timingCard(g,12,y,127,"DISTRACTED FOR",distraction.elapsed(now),style.accent,style);
                timingCard(g,145,y,127,"TIME LEFT",distraction.left(now),VarlamoreThievingCoach.READY,style);
                y+=56;
            }
            row(g,12,y,260,38,"NEXT DISTRACTION",distraction==null?"Awaiting start":distraction.nextText(now),style.accent,style);
            y+=44;
            g.setColor(new Color(style.accent.getRed(),style.accent.getGreen(),style.accent.getBlue(),28));
            g.fillRoundRect(12,y,260,houseHeight-6,6,6);
            g.setFont(HudStyle.label(9));g.setColor(HudStyle.muted(style));
            g.drawString(state.availableHouse==null?"HOUSE AVAILABILITY":"AVAILABLE HOUSE",20,y+14);
            g.setFont(HudStyle.label(13));g.setColor(state.availableHouse==null?style.text:VarlamoreThievingCoach.READY);
            g.drawString(state.availableHouse==null?"No vacant house observed":state.availableHouse.name+"'s house",20,y+32);
            g.setFont(HudStyle.body(11));g.setColor(style.text);
            int statusY=y+47;for(String line:houseStatus){g.drawString(line,20,statusY);statusY+=14;}
            y+=houseHeight;
            g.setFont(HudStyle.body(11));g.setColor(HudStyle.muted(style));
            for(int i=0;i<factCount;i++)
            {
                List<String> line=wrap(g.getFontMetrics(),facts.get(i),WIDTH-24,1);
                if(!line.isEmpty())g.drawString(line.get(0),12,y+10);
                y+=14;
            }
            int timerCount=timers.size();
            for(int i=0;i<timerCount;i++)
            {
                VarlamoreThievingState.Window timer=timers.get(i);
                int x=12+(WIDTH-24)*(2*i+1)/(2*timerCount);
                long seconds=Math.max(0,(timer.latest-now+999)/1000);
                RadialTimer.paint(g,x,y+25,18,RadialTimer.fraction(now,timer.started,timer.latest),
                    timer.label.equals("Bonus opportunity")?VarlamoreThievingCoach.BONUS:style.accent,
                    seconds>0?seconds+"s":"due",true);
                String label=timer.label.equals("Bonus opportunity")?"Bonus":timer.label;
                g.setFont(HudStyle.body(10));g.setColor(HudStyle.muted(style));
                g.drawString(label,x-g.getFontMetrics().stringWidth(label)/2,y+56);
            }
            return HudStyle.dimension(style,(int)Math.ceil(WIDTH*fit),(int)Math.ceil(height*fit));
        }
        finally{g.dispose();}
    }

    private static void timingCard(Graphics2D g,int x,int y,int width,String label,String value,Color color,HudPreferences.Style style)
    {
        row(g,x,y,width,50,label,value,color,style);
    }
    private static void row(Graphics2D g,int x,int y,int width,int height,String label,String value,Color color,HudPreferences.Style style)
    {
        g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),24));
        g.fillRoundRect(x,y,width,height,6,6);
        g.setFont(HudStyle.label(9));g.setColor(HudStyle.muted(style));g.drawString(label,x+8,y+13);
        int size=height==50?19:14;
        g.setFont(HudStyle.label(size));
        while(size>11&&g.getFontMetrics().stringWidth(value)>width-16)g.setFont(HudStyle.label(--size));
        g.setColor(color);
        // The DTO supplies the timing bounds; they remain visible instead of becoming fake exact times.
        List<String> rendered=wrap(g.getFontMetrics(),value,width-16,height==50?2:1);
        int baseline=height==50?(rendered.size()>1?29:37):30;
        for(String line:rendered){g.drawString(line,x+8,y+baseline);baseline+=14;}
    }

    private static List<String> wrap(FontMetrics metrics,String text,int width,int maximum)
    {
        List<String> lines=new ArrayList<>();
        String remaining=text==null?"":text.replaceAll("\\s+"," ").trim();
        if(width<=0||maximum<=0)return lines;
        while(!remaining.isEmpty()&&lines.size()<maximum)
        {
            if(metrics.stringWidth(remaining)<=width){lines.add(remaining);remaining="";break;}
            int end=1;
            while(end<remaining.length()&&metrics.stringWidth(remaining.substring(0,end+1))<=width)end++;
            int space=remaining.lastIndexOf(' ',end);if(space>0)end=space;
            lines.add(remaining.substring(0,end).trim());remaining=remaining.substring(end).trim();
        }
        if(!remaining.isEmpty()&&!lines.isEmpty())
        {
            int last=lines.size()-1;String line=lines.get(last);
            while(!line.isEmpty()&&metrics.stringWidth(line+"…")>width)line=line.substring(0,line.length()-1);
            lines.set(last,line.stripTrailing()+"…");
        }
        return lines;
    }
}
