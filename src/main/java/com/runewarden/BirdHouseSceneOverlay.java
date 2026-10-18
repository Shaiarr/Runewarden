/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Individual native bird-house observations, tethered to their loaded scene objects. */
@Singleton
final class BirdHouseSceneOverlay extends Overlay
{
    private final Client client;private final HarvestClockTool tool;private final RunewardenConfig config;private final ItemManager items;
    @Inject BirdHouseSceneOverlay(Client client,HarvestClockTool tool,RunewardenConfig config,ItemManager items)
    {
        this.client=client;this.tool=tool;this.config=config;this.items=items;
        setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        if(!config.harvestHud()||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)return null;
        WorldView view=client.getTopLevelWorldView();
        if(view==null||view.isInstance()||view.getPlane()!=0||view.getScene()==null)return null;
        HudPreferences.Style style=HudStyle.settings(config,"harvest");if(!HudStyle.visible(style))return null;
        long now=Instant.now().getEpochSecond();double scale=style.scale/100d*style.font/12d;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setFont(HudStyle.label(11));
            for(HarvestClockTool.BirdHouseReading bird:tool.birdHouses())
            {
                if(client.getVarpValue(bird.site.varp)!=bird.state)continue;
                GameObject object=findObject(view,bird.site);if(object==null)continue;
                Point anchor=object.getCanvasLocation(),top=object.getCanvasTextLocation(g,"",90);
                if(anchor==null||top==null)continue;
                String title=bird.site.name,status=bird.status(now);
                int width=Math.max(145,Math.max(g.getFontMetrics().stringWidth(title),g.getFontMetrics().stringWidth(status))+54),height=42;
                int sw=(int)Math.ceil(width*scale),sh=(int)Math.ceil(height*scale),cw=client.getCanvasWidth(),ch=client.getCanvasHeight();
                if(cw<sw||ch<sh||top.getX()<0||top.getX()>=cw||top.getY()<0||top.getY()>=ch)continue;
                int x=Math.max(0,Math.min(top.getX()-sw/2,cw-sw)),y=Math.max(0,Math.min(top.getY()-sh-5,ch-sh));
                HudStyle.tether(g,new Rectangle(x,y,sw,sh),anchor,style.accent,cw,ch);
                Graphics2D card=(Graphics2D)g.create();
                try
                {
                    card.translate(x,y);card.scale(scale,scale);HudStyle.panel(card,width,height,style);
                    boolean radial=config.harvestTimerDisplay()!=RunewardenConfig.TimerDisplay.TEXT;
                    if(radial&&bird.seeded())RadialTimer.paint(card,23,21,17,(double)bird.remaining(now)/HarvestClockTool.BirdHouseReading.DURATION,style.accent,"",false);
                    BufferedImage icon=items==null?null:items.getImage(bird.itemId());
                    if(icon!=null)card.drawImage(icon,10,8,26,26,null);
                    card.setColor(style.text);card.drawString(title,47,16);card.setColor(style.accent);card.drawString(status,47,32);
                }
                finally{card.dispose();}
            }
        }
        finally{g.dispose();}
        return null;
    }
    /** Four exact native spaces only; no scene-wide scans or marker at an unloaded location. */
    static GameObject findObject(WorldView view,HarvestClockTool.BirdHouseSite site)
    {
        if(view==null||view.isInstance()||view.getPlane()!=site.point.getPlane()||view.getScene()==null)return null;
        Tile[][][] tiles=view.getScene().getTiles();int plane=view.getPlane(),x=site.point.getX()-view.getBaseX(),y=site.point.getY()-view.getBaseY();
        if(tiles==null||plane<0||plane>=tiles.length||tiles[plane]==null||x<0||x>=tiles[plane].length||tiles[plane][x]==null||y<0||y>=tiles[plane][x].length)return null;
        Tile tile=tiles[plane][x][y];if(tile==null||tile.getGameObjects()==null)return null;
        for(GameObject object:tile.getGameObjects())if(object!=null&&object.getWorldView()==view&&object.getPlane()==plane&&birdHouseObject(object.getId()))return object;
        return null;
    }
    private static boolean birdHouseObject(int id)
    {
        return id>=ObjectID.BIRDHOUSE_NOT_BUILT&&id<=ObjectID.BIRDHOUSE_4
            ||id>=ObjectID.BIRDHOUSE_MAPLE_BUILT&&id<=ObjectID.BIRDHOUSE_REDWOOD_BIRD;
    }
}
