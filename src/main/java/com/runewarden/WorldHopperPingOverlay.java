/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Paints the hopper's bounded, published connection measurements without network work. */
final class WorldHopperPingOverlay extends Overlay
{
    private final WorldHopperTool tool;
    private final RunewardenConfig config;

    @Inject WorldHopperPingOverlay(WorldHopperTool tool, RunewardenConfig config)
    {
        this.tool=tool;this.config=config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);setResizable(false);
    }

    void setHudLocked(boolean locked){setMovable(!locked);}

    @Override public Dimension render(Graphics2D graphics)
    {
        if(!config.worldHopDisplayPing())return null;
        WorldHopperTool.Snapshot reading=tool.snapshot();
        HudPreferences.Style style=HudStyle.settings(config,"world-ping");
        if(!reading.loggedIn||!HudStyle.visible(style)||(reading.currentPing<0&&reading.currentLoss<0))return null;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try{return paint(g,style,reading.currentWorld,reading.currentPing,reading.currentLoss);}
        finally{g.dispose();}
    }

    static Dimension paint(Graphics2D g,HudPreferences.Style style,int world,int ping,int retransmit)
    {
        int width=190,height=retransmit>0?76:56;
        HudStyle.panel(g,width,height,style);
        g.setFont(HudStyle.label(11));g.setColor(style.accent);
        g.drawString("WORLD "+world,12,20);
        g.setFont(HudStyle.body(14));g.setColor(style.text);
        g.drawString(ping<0?"Latency unavailable":ping+" ms",12,43);
        if(retransmit>0)
        {
            g.setFont(HudStyle.body(11));g.setColor(style.accent);
            g.drawString(retransmit+"% retransmitted",12,63);
        }
        return HudStyle.dimension(style,width,height);
    }
}
