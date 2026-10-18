/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Present obstacle geometry, matching the native agility clickbox/shortcut model. */
public final class AgilitySceneOverlay extends Overlay
{
    private static final Color GOLD=new Color(224,190,113),MARK=new Color(108,230,170),PORTAL=new Color(171,133,247),TRAP=new Color(240,113,83);
    private final Client client;
    private final AgilityCoach coach;
    private final net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips;
    AgilitySceneOverlay(Client client,AgilityCoach coach,net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips)
    {this.client=client;this.coach=coach;this.tooltips=tooltips;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    @Override public Dimension render(Graphics2D graphics)
    {
        Player player=client.getLocalPlayer();
        if(client.getGameState()!=GameState.LOGGED_IN||player==null||player.getWorldView()==null||!coach.running())return null;
        AgilityCoach.Preferences preferences=coach.preferences();Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            for(AgilityCoach.Obstacle obstacle:coach.obstacles())
            {
                if(obstacle.object.getWorldView()!=player.getWorldView()||obstacle.tile.getPlane()!=player.getWorldView().getPlane()||obstacle.object.getLocalLocation()==null||obstacle.object.getLocalLocation().distanceTo(player.getLocalLocation())>=2350)continue;
                int id=obstacle.object.getId();boolean trap=AgilityObstacles.TRAP_OBSTACLE_IDS.contains(id);
                if(!enabled(preferences,obstacle))continue;
                Color color=trap?TRAP:AgilityObstacles.PORTAL_OBSTACLE_IDS.contains(id)?PORTAL:obstacle.shortcut!=null&&obstacle.shortcut.getLevel()>client.getBoostedSkillLevel(net.runelite.api.Skill.AGILITY)?Color.ORANGE:GOLD;
                Shape shape=trap?obstacle.object.getCanvasTilePoly():obstacle.object.getClickbox();paint(g,shape,color);
                net.runelite.api.Point mouse=client.getMouseCanvasPosition();
                if(obstacle.shortcut!=null&&shape!=null&&mouse!=null&&shape.contains(mouse.getX(),mouse.getY()))
                    tooltips.add(new net.runelite.client.ui.overlay.tooltip.Tooltip(obstacle.shortcut.getTooltip()));
            }
            if(preferences.marks)
            {
                for(Tile tile:coach.groundMarks())paintGround(g,tile,player,MARK);
                for(Tile tile:coach.groundSticks())paintGround(g,tile,player,PORTAL);
            }
        }
        finally{g.dispose();}
        return null;
    }
    static boolean enabled(AgilityCoach.Preferences p,AgilityCoach.Obstacle o)
    {
        int id=o.object.getId();
        if(o.shortcut!=null)return p.shortcuts;
        if(AgilityObstacles.TRAP_OBSTACLE_IDS.contains(id))return p.traps;
        if(AgilityObstacles.PORTAL_OBSTACLE_IDS.contains(id))return p.portals;
        if(AgilityObstacles.SEPULCHRE_SKILL_OBSTACLE_IDS.contains(id))return p.skilling;
        if(AgilityObstacles.SEPULCHRE_OBSTACLE_IDS.contains(id))return p.sepulchre;
        return p.obstacles;
    }
    private void paintGround(Graphics2D g,Tile tile,Player player,Color color)
    {
        if(tile.getPlane()!=player.getWorldView().getPlane()||tile.getItemLayer()==null||tile.getLocalLocation()==null||tile.getLocalLocation().getWorldView()!=player.getLocalLocation().getWorldView()||tile.getLocalLocation().distanceTo(player.getLocalLocation())>=2350)return;
        Polygon polygon=tile.getItemLayer().getCanvasTilePoly();paint(g,polygon,color);
    }
    private static void paint(Graphics2D g,Shape shape,Color color)
    {if(shape==null)return;g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),42));g.fill(shape);g.setColor(color);g.draw(shape);}
}
