/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A scene-anchored countdown only when the observed node has a supported duration. */
@Singleton
public final class NodeTimerOverlay extends Overlay
{
    private static final Font FONT=new Font(Font.SANS_SERIF,Font.BOLD,13),SMALL=new Font(Font.SANS_SERIF,Font.BOLD,10);
    private static final Color BACKGROUND=new Color(10,12,16,225),GOLD=new Color(224,190,113);
    private final Client client;
    private final RunewardenConfig config;
    private final SkillingCoach coach;
    private HudResizeController resize;
    @Inject void setResizeController(HudResizeController value) { resize = value; }
    @Inject NodeTimerOverlay(Client client, RunewardenConfig config, SkillingCoach coach)
    {
        this.client=client;this.config=config;this.coach=coach;
        setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        if(resize!=null)resize.beginScene("node-timers");
        if(!config.nodeTimers()||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)return null;
        HudPreferences.Style style=HudStyle.settings(config,"node-timers");if(!HudStyle.visible(style))return null;
        WorldView view=client.getLocalPlayer().getWorldView();if(view==null)return null;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            long now=System.currentTimeMillis();
            RunewardenConfig.TimerDisplay display=display(config.nodeTimerDisplay());
            for(SkillingCoach.Node node:coach.nodes())
            {
                if(node.worldView!=view.getId()||node.point.getPlane()!=view.getPlane())continue;
                LocalPoint local=LocalPoint.fromWorld(view,node.point);if(local==null)continue;
                if(node.motherlode&&(Perspective.getTileHeight(client,local,view.getPlane())< -500)
                    !=(Perspective.getTileHeight(client,client.getLocalPlayer().getLocalLocation(),view.getPlane())< -500))continue;
                LocalPoint center=local.plus((node.sizeX-1)*Perspective.LOCAL_HALF_TILE_SIZE,(node.sizeY-1)*Perspective.LOCAL_HALF_TILE_SIZE);
                Point point=Perspective.localToCanvas(client,center,view.getPlane(),node.height);if(point==null)continue;
                Graphics2D card=HudStyle.canvas(g,style);
                try
                {
                    double factor=style.scale/100d*style.font/12d;
                    // The resource anchor stays on the original game object while its card grows.
                    card.translate(point.getX()/factor,point.getY()/factor);
                    Dimension painted=paintNode(card,node,now,display,0,0);
                    if(resize!=null)
                    {
                        Dimension size=HudStyle.dimension(style,painted.width,painted.height);
                        int offset=hasRadial(node,now,display)&&display==RunewardenConfig.TimerDisplay.RADIAL?0:(int)Math.round(2*factor);
                        resize.sceneBounds("node-timers",new java.awt.Rectangle(point.getX()-size.width/2,point.getY()-size.height+offset,size.width,size.height));
                    }
                }
                finally{card.dispose();}
            }
        }
        finally{g.dispose();}
        return null;
    }
    static RunewardenConfig.TimerDisplay display(RunewardenConfig.TimerDisplay value)
    {return value==null?RunewardenConfig.TimerDisplay.TEXT:value;}
    static boolean hasRadial(SkillingCoach.Node node,long now,RunewardenConfig.TimerDisplay mode)
    {return display(mode)!=RunewardenConfig.TimerDisplay.TEXT&&node.hasCountdown(now);}
    /** Geometry-only renderer also used by headless QA; the caller owns its Graphics2D. */
    static Dimension paintNode(Graphics2D g,SkillingCoach.Node node,long now,RunewardenConfig.TimerDisplay mode,int centerX,int baselineY)
    {
        mode=display(mode);boolean radial=hasRadial(node,now,mode);Color accent=node.respawn?GOLD:Color.WHITE;
        g.setFont(FONT);String label=node.label(now);
        if(radial&&mode==RunewardenConfig.TimerDisplay.RADIAL)
        {
            int width=66,height=59,x=centerX-width/2,y=baselineY-height;
            g.setColor(BACKGROUND);g.fillRoundRect(x,y,width,height,9,9);
            g.setColor(accent);g.setFont(SMALL);String kind=node.respawn?"RESPAWN":"LIFE ~";
            g.drawString(kind,centerX-g.getFontMetrics().stringWidth(kind)/2,y+11);
            RadialTimer.paint(g,centerX,y+35,19,RadialTimer.fraction(now,node.started,node.endsAt()),accent,node.countdown(now),!node.respawn);
            return new Dimension(width,height);
        }
        int height=radial?29:23,width=g.getFontMetrics().stringWidth(label)+(radial?44:12),x=centerX-width/2,y=baselineY-height+2;
        g.setColor(BACKGROUND);g.fillRoundRect(x,y,width,height,7,7);g.setColor(accent);g.drawRoundRect(x,y,width,height,7,7);
        if(radial)RadialTimer.paint(g,x+15,y+14,11,RadialTimer.fraction(now,node.started,node.endsAt()),accent,"",!node.respawn);
        g.setFont(FONT);g.setColor(accent);g.drawString(label,x+(radial?32:6),y+(radial?19:16));
        return new Dimension(width,height);
    }
}
