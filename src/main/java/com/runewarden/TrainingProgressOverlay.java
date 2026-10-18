/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
/** Small read-only activity measurement card; RuneLite supplies native Alt-drag positioning. */
final class TrainingProgressOverlay extends Overlay
{
    private final Client client;private final TrainingProgressTool tool;
    private final RunewardenConfig config;
    TrainingProgressOverlay(Client client,TrainingProgressTool tool,RunewardenConfig config)
    {this.client=client;this.tool=tool;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        TrainingProgressTool.Reading r=tool.reading();if(!tool.showHud()||r.session.context==null||r.session.context.skill==null||r.session.state==SharedActivitySession.State.FINISHED||client.getGameState()!=GameState.LOGGED_IN)return null;
        HudPreferences.Style style=HudStyle.settings(config,"training");if(!HudStyle.visible(style))return null;
        Graphics2D g=HudStyle.canvas(graphics,style);try
        {
            int width=268,height=204;HudStyle.panel(g,width,height,style);g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("TRAINING · "+r.cause.name().replace('_',' '),12,19);
            g.setFont(HudStyle.label(12));g.setColor(style.text);int y=39;
            int titleLeft=12;
            if(r.icon!=null)
            {
                double scale=Math.min(1,Math.min(28d/r.icon.getWidth(),28d/r.icon.getHeight()));
                int iconWidth=Math.max(1,(int)Math.round(r.icon.getWidth()*scale)),iconHeight=Math.max(1,(int)Math.round(r.icon.getHeight()*scale));
                g.drawImage(r.icon,12+(28-iconWidth)/2,28,iconWidth,iconHeight,null);titleLeft=48;
            }
            for(String line:GuideCoachOverlay.wrap(g.getFontMetrics(),r.session.context.title,width-titleLeft-12,2)){g.drawString(line,titleLeft,y);y+=16;}
            g.setFont(HudStyle.body(12));g.drawString(String.format(java.util.Locale.ROOT,"%,d XP · %,d drops",r.trainingXp,r.xpDrops),12,y+3);
            g.setColor(style.muted);g.drawString(TrainingProgressTool.rate(r.activeXpPerHour),12,y+22);
            g.drawString("Working "+TrainingProgressTool.time(r.activeMillis)+" · elapsed "+TrainingProgressTool.time(r.session.elapsedMillis),12,y+41);
            g.setColor(style.accent);g.drawString("ETA "+TrainingProgressTool.time(r.activeEtaMillis),12,y+61);
            double fraction=r.progressFraction();g.setFont(HudStyle.label(11));g.setColor(style.text);
            g.drawString(r.session.context.targetXp>0?"Run target":"Next level",12,157);
            String percent=String.format(java.util.Locale.ROOT,"%.1f%%",fraction*100);g.drawString(percent,width-12-g.getFontMetrics().stringWidth(percent),157);
            g.setColor(style.accent.darker().darker());g.fillRoundRect(12,167,width-24,9,6,6);g.setColor(style.accent);g.fillRoundRect(12,167,(int)Math.round((width-24)*fraction),9,6,6);
            g.setFont(HudStyle.body(11));g.setColor(style.muted);g.drawString(String.format(java.util.Locale.ROOT,"%,d XP left",Math.max(0,r.progressTargetXp-r.currentXp)),12,192);
            return HudStyle.dimension(style,width,height);
        }finally{g.dispose();}
    }
}
