/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.game.ItemManager;

/** Native patch growth ranges and confirmed compost, projected above the actual patch model. */
@Singleton
public final class FarmPatchOverlay extends Overlay
{
    private final Client client; private final FarmPatchPlanner planner;private final ItemManager items;
    private RunewardenConfig config;private HudResizeController resize;
    @Inject FarmPatchOverlay(Client client,FarmPatchPlanner planner,ItemManager items)
    {this.client=client;this.planner=planner;this.items=items;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.ABOVE_SCENE);}
    @Inject void setHudConfiguration(RunewardenConfig config){this.config=config;}
    @Inject void setResizeController(HudResizeController resize){this.resize=resize;}
    static String timerLabel(FarmPatchPlanner.PatchReading reading,long nowSeconds)
    {
        if(reading==null || reading.state==null)return "Not observed";
        if("GROWING".equals(reading.state.state) && reading.latestGrowthSecond()>0)
        {
            long early=Math.max(0,reading.earliestGrowthSecond()-nowSeconds),late=Math.max(0,reading.latestGrowthSecond()-nowSeconds);
            String earlyLabel=FarmGrowthTiming.remaining(early),lateLabel=FarmGrowthTiming.remaining(late);
            return late==0?"Due to check · estimated":earlyLabel.equals(lateLabel)?"~"+lateLabel:"~"+earlyLabel+"–"+lateLabel;
        }
        return reading.status(nowSeconds);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        if(resize!=null)resize.beginScene("farm-patches");
        FarmPatchPlanner.HudOptions options=planner.hudOptions();
        if(!options.enabled || client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null)return null;
        HudPreferences.Style style=config==null?HudPreferences.Style.defaults(85):HudStyle.settings(config,"farm-patches");
        if(!HudStyle.visible(style))return null;double scale=style.scale/100d*style.font/12d;
        FarmPatchScene.Snapshot snapshot=planner.sceneReading();WorldView view=client.getTopLevelWorldView();
        if(!snapshot.matches(view))return null;
        java.util.Map<String,FarmPatchPlanner.PatchReading> readings=planner.readings();long now=System.currentTimeMillis();
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));int shown=0;
            for(FarmPatchScene.Anchor anchor:snapshot.anchors)
            {
                if(++shown>24 || anchor.object.getWorldView()!=view || anchor.object.getPlane()!=view.getPlane())continue;
                FarmPatchPlanner.PatchReading reading=readings.get(anchor.patchId);if(reading==null || !reading.currentSession)continue;
                net.runelite.api.Renderable renderable=anchor.object.getRenderable();int height=Math.max(50,Math.min(800,renderable==null?50:renderable.getModelHeight()+35));
                Point point=anchor.object.getCanvasTextLocation(g,"",height);if(point==null)continue;
                FarmPatchCatalog.Crop crop=reading.state==null?null:planner.cropReference(reading.state.crop);
                String title=crop==null || "WEEDS".equals(reading.state.crop)?reading.patch.type.replace('_',' '):crop.name;
                String timer=timerLabel(reading,now/1000),compost=FarmCompostEvidence.label(reading.observedCompost);
                boolean ring="Rings".equals(options.style);int inset=50;
                int width=Math.min(260,Math.max(110,Math.max(g.getFontMetrics().stringWidth(title),Math.max(g.getFontMetrics().stringWidth(timer),options.compost?g.getFontMetrics().stringWidth(compost):0))+inset+10));
                int heightBox=options.compost?53:39,scaledWidth=(int)Math.ceil(width*scale),scaledHeight=(int)Math.ceil(heightBox*scale);
                int x=point.getX()-scaledWidth/2,y=point.getY()-scaledHeight-5;
                if(client.getCanvasWidth()<scaledWidth || point.getY()<0 || point.getX()<0 || point.getX()>client.getCanvasWidth())continue;
                x=Math.max(0,Math.min(x,client.getCanvasWidth()-scaledWidth));y=Math.max(0,y);
                if(resize!=null)resize.sceneBounds("farm-patches",new java.awt.Rectangle(x,y,scaledWidth,scaledHeight));
                Graphics2D card=(Graphics2D)g.create();
                try
                {
                    card.translate(x,y);card.scale(scale,scale);HudStyle.panel(card,width,heightBox,style);
                    if(ring)
                    {
                        boolean growing=reading.state!=null && "GROWING".equals(reading.state.state);double fraction=growing?RadialTimer.fraction(now,reading.growthCycleStartSecond()*1000,reading.latestGrowthSecond()*1000):0;
                        RadialTimer.paint(card,24,heightBox/2,19,fraction,style.accent,"",!reading.growthClockKnown());
                    }
                    int iconId=planner.cropIconId(reading.state);
                    java.awt.image.BufferedImage icon=iconId>0&&items!=null?items.getImage(iconId):null;
                    if(icon!=null)
                    {
                        int iconWidth=Math.max(1,icon.getWidth()),iconHeight=Math.max(1,icon.getHeight());double iconScale=Math.min(28d/iconWidth,28d/iconHeight);
                        int drawWidth=Math.max(1,(int)Math.round(iconWidth*iconScale)),drawHeight=Math.max(1,(int)Math.round(iconHeight*iconScale));
                        card.drawImage(icon,24-drawWidth/2,heightBox/2-drawHeight/2,drawWidth,drawHeight,null);
                    }
                    card.setColor(style.text);card.drawString(title,inset,14);card.setColor(style.accent);card.drawString(timer,inset,28);
                    if(options.compost){card.setColor(reading.observedCompost==null?style.muted:style.text);card.drawString(compost,inset,42);}
                }
                finally{card.dispose();}
            }
        }
        finally{g.dispose();}
        return null;
    }
}
