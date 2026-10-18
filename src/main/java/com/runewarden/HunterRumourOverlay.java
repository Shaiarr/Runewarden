/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Movable shared-style HUD. Catch observations never masquerade as an exact hidden pity counter. */
@Singleton
final class HunterRumourOverlay extends Overlay
{
    private static final int WIDTH=258;private final Client client;private final RunewardenConfig config;private final HunterRumourCoach coach;
    @Inject HunterRumourOverlay(Client client,RunewardenConfig config,HunterRumourCoach coach)
    {this.client=client;this.config=config;this.coach=coach;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        HunterRumourCoach.State state=coach.reading();HudPreferences.Style style=HudStyle.settings(config,"hunter-rumours");
        if(client.getGameState()!=GameState.LOGGED_IN||!state.hud||state.rumour==null||!HudStyle.visible(style))return null;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            g.setFont(HudStyle.label(13));List<String>title=GuideCoachOverlay.wrap(g.getFontMetrics(),state.rumour.name,WIDTH-24,2);
            int height=125+title.size()*17;double requested=style.scale/100d*style.font/12d;
            double viewport=Math.min(1d,Math.min(client.getCanvasWidth()>16?(client.getCanvasWidth()-16)/(WIDTH*requested):1d,client.getCanvasHeight()>24?(client.getCanvasHeight()-24)/(height*requested):1d));
            g.scale(viewport,viewport);HudStyle.panel(g,WIDTH,height,style);g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("HUNTER RUMOURS",12,18);
            int y=38;g.setFont(HudStyle.label(13));g.setColor(style.text);for(String line:title){g.drawString(line,12,y);y+=17;}
            g.setFont(HudStyle.body(11));g.setColor(style.muted);g.drawString(fit(g,(state.master==null?"Guild hunter":state.master.name)+" · "+(state.manual?"Manual reference":state.verified?"Observed this session":"Last seen"),WIDTH-24),12,y);y+=20;
            g.setColor(style.accent);g.setFont(HudStyle.label(12));g.drawString(state.ready?"RARE PIECE FOUND":"Matching catches · "+state.catches,12,y);y+=18;
            g.setFont(HudStyle.body(11));g.setColor(style.text);g.drawString(state.ready?"Return to the Hunter Guild":"Guild hunter kit · "+state.outfitPieces+" / 4",12,y);y+=17;
            g.setColor(style.muted);g.drawString("Reference threshold · "+state.referenceThreshold,12,y);y+=17;
            if(state.rareCarried&&!state.ready){g.setColor(style.accent);g.drawString("Rare part carried",12,y);}
            return new Dimension((int)Math.ceil(WIDTH*requested*viewport),(int)Math.ceil(height*requested*viewport));
        }
        finally{g.dispose();}
    }
    private static String fit(Graphics2D g,String text,int width)
    {if(g.getFontMetrics().stringWidth(text)<=width)return text;while(text.length()>1&&g.getFontMetrics().stringWidth(text+"…")>width)text=text.substring(0,text.length()-1);return text+"…";}
}
