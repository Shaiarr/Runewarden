/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

final class VarlamoreThievingSceneOverlay extends Overlay
{
    private final Client client;
    private final VarlamoreThievingCoach coach;
    private final Overlay minimap;

    VarlamoreThievingSceneOverlay(Client client,VarlamoreThievingCoach coach)
    {
        this.client=client;this.coach=coach;
        setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);
        minimap=new Overlay()
        {
            {setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_WIDGETS);}
            @Override public Dimension render(Graphics2D graphics){renderMinimap(graphics);return null;}
        };
    }
    Overlay minimap(){return minimap;}

    @Override public Dimension render(Graphics2D graphics)
    {
        VarlamoreThievingCoach.Snapshot state=coach.reading();
        if(!state.active||client.getLocalPlayer()==null)return null;
        WorldView view=client.getTopLevelWorldView();if(view==null)return null;
        VarlamoreThievingCoach.Preferences prefs=coach.preferences();
        for(VarlamoreThievingCoach.Target target:state.targets)
        {
            Polygon polygon;Point text;
            if(target.npc!=null)
            {
                NPC npc=target.npc;
                // Snapshots can outlive a native NPC despawn or composition teardown.
                // Actor projection reads that composition's footprint internally.
                if(npc.getWorldView()!=view||view.npcs()==null||view.npcs().byIndex(npc.getIndex())!=npc
                    ||npc.getComposition()==null||npc.getTransformedComposition()==null)continue;
                polygon=npc.getCanvasTilePoly();text=npc.getCanvasTextLocation(graphics,target.label,35);
            }
            else
            {
                if(target.object==null||target.object.getWorldView()!=view||target.object.getPlane()!=view.getPlane())continue;
                polygon=target.object.getCanvasTilePoly();text=target.object.getCanvasTextLocation(graphics,target.label,30);
            }
            if(polygon!=null)OverlayUtil.renderPolygon(graphics,polygon,target.color);
            if(prefs.labels&&prefs.doors&&state.house==null&&state.availableHouse!=null&&target.object!=null
                &&state.availableHouse.door.equals(target.object.getWorldLocation()))
            {
                Point anchor=target.object.getCanvasLocation();
                HudPreferences.Style style=HudStyle.settings(coach.hudConfiguration(),"thieving");
                if(!HudStyle.visible(style))continue;
                double scale=style.scale/100d*style.font/12d*prefs.scalePercent/100d;
                if(doorCardBounds(anchor,client.getCanvasWidth(),client.getCanvasHeight(),scale)!=null)
                    paintDoorCard(graphics,anchor,client.getCanvasWidth(),client.getCanvasHeight(),scale,state.availableHouse.name,
                        state.keys>0?"ACCESSIBLE · Keys "+state.keys:"House key required",target.timer,coach.now(),style);
                else if(text!=null)badge(graphics,text,target.label);
                continue;
            }
            if(text==null)continue;
            if(prefs.radialTimers&&target.timer!=null)
            {
                long now=coach.now(),seconds=Math.max(0,(target.timer.latest-now+999)/1000);
                RadialTimer.paint(graphics,text.getX(),text.getY()-24,17,
                    RadialTimer.fraction(now,target.timer.started,target.timer.latest),target.color,seconds+"s",true);
            }
            if(prefs.labels)
            {
                if(prefs.doors&&state.availableHouse!=null&&target.object!=null&&target.label.startsWith("ENTER"))
                    badge(graphics,text,target.label);
                else OverlayUtil.renderTextLocation(graphics,text,target.label,target.color);
            }
        }
        return null;
    }

    static Rectangle doorCardBounds(Point anchor,int canvasWidth,int canvasHeight,double scale)
    {
        if(anchor==null||!Double.isFinite(scale)||scale<.25||scale>6||anchor.getX()<0||anchor.getY()<0
            ||anchor.getX()>=canvasWidth||anchor.getY()>=canvasHeight)return null;
        int width=(int)Math.ceil(210*scale),height=(int)Math.ceil(64*scale),gap=(int)Math.ceil(28*scale);
        if(canvasWidth<width+8||anchor.getY()<height+gap+4)return null;
        int x=Math.max(4,Math.min(anchor.getX()-width/2,canvasWidth-width-4));
        return new Rectangle(x,anchor.getY()-height-gap,width,height);
    }

    static void paintDoorCard(Graphics2D graphics,Point anchor,int canvasWidth,int canvasHeight,double scale,
        String houseName,String status,VarlamoreThievingState.Window ownerTimer,long now,HudPreferences.Style style)
    {
        Rectangle bounds=doorCardBounds(anchor,canvasWidth,canvasHeight,scale);if(bounds==null)return;
        HudStyle.tether(graphics,bounds,anchor,style.accent,canvasWidth,canvasHeight);
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.clipRect(0,0,canvasWidth,canvasHeight);g.translate(bounds.x,bounds.y);g.scale(scale,scale);
            HudStyle.panel(g,210,64,style);g.setFont(HudStyle.label(12));g.setColor(style.text);g.drawString(houseName+"'s house",12,17);
            g.setFont(HudStyle.label(11));g.setColor(status.startsWith("House key")?VarlamoreThievingCoach.WARNING:VarlamoreThievingCoach.READY);g.drawString(status,12,35);
            g.setFont(HudStyle.body(10));g.setColor(style.muted);g.drawString(ownerTimer==null?"Owner away":now>=ownerTimer.latest?"Owner return due":"Owner back · "+ownerTimer.remaining(now),12,52);
        }
        finally{g.dispose();}
    }

    private void renderMinimap(Graphics2D graphics)
    {
        VarlamoreThievingCoach.Snapshot state=coach.reading();
        if(!state.active||state.house!=null||state.availableHouse==null||!coach.preferences().doors||client.getLocalPlayer()==null)return;
        WorldView view=client.getTopLevelWorldView();
        if(view==null||client.getLocalPlayer().getWorldView()!=view||view.getPlane()!=state.availableHouse.exterior.getPlane())return;
        LocalPoint local=LocalPoint.fromWorld(view,state.availableHouse.exterior);if(local==null)return;
        Point point=Perspective.localToMinimap(client,local);if(point==null)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            int x=point.getX(),y=point.getY();
            g.setColor(new Color(8,10,13,235));g.fillOval(x-7,y-7,14,14);
            g.setColor(HudStyle.GOLD);g.drawOval(x-7,y-7,14,14);
            g.setColor(VarlamoreThievingCoach.READY);g.fillOval(x-4,y-4,8,8);
        }
        finally{g.dispose();}
    }

    private static void badge(Graphics2D graphics,Point point,String label)
    {
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setFont(HudStyle.label(12));int width=g.getFontMetrics().stringWidth(label)+20;
            int x=Math.max(0,point.getX()-width/2),y=Math.max(0,point.getY()-18);
            g.setColor(new Color(8,10,13,235));g.fillRoundRect(x,y,width,25,7,7);
            g.setColor(HudStyle.GOLD);g.drawRoundRect(x,y,width,25,7,7);
            g.setColor(VarlamoreThievingCoach.READY);g.drawString(label,x+10,y+17);
        }
        finally{g.dispose();}
    }
}
