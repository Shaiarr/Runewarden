/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Actual native skill icons and local measured goals in an independently movable card. */
public final class SkillProgressOverlay extends Overlay
{
    private static final int WIDTH=268;
    private final Client client;
    private final SkillProgressTool tool;
    private final RunewardenConfig config;
    private final SkillIconManager icons;
    SkillProgressOverlay(Client client,SkillProgressTool tool,RunewardenConfig config,SkillIconManager icons)
    {this.client=client;this.tool=tool;this.config=config;this.icons=icons;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);setResizable(false);}
    void setHudLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        SkillProgressTool.View view=tool.reading();SkillProgressTool.Preferences p=tool.preferences();HudPreferences.Style style=HudStyle.settings(config,"skill-progress");
        if(view.hud.isEmpty()||!p.hud||client.getGameState()!=GameState.LOGGED_IN||!HudStyle.visible(style))return null;
        int cardHeight=61+(p.remaining?16:0)+(p.session?16:0)+(p.eta?16:0),height=27+view.hud.size()*cardHeight;
        Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            HudStyle.panel(g,WIDTH,height,style);g.setFont(HudStyle.label(10));g.setColor(style.accent);g.drawString("SKILLS PROGRESS",12,18);int y=36;
            for(SkillProgressTool.Row row:view.hud)
            {
                BufferedImage icon=icons.getSkillImage(row.skill,true);if(icon!=null)g.drawImage(icon,12,y-10,null);
                g.setFont(HudStyle.label(12));g.setColor(style.text);String target=row.targetLabel();g.drawString(row.skill.getName()+"  "+row.level+(target.startsWith("Level ")?" → "+target.substring(6):""),34,y);y+=18;
                g.setFont(HudStyle.body(11));g.setColor(style.muted);g.drawString(SkillProgressTool.format(row.xp)+" / "+SkillProgressTool.format(row.target)+" XP",12,y);String fraction=String.format(java.util.Locale.ROOT,"%.1f%%",row.fraction()*100);g.drawString(fraction,WIDTH-12-g.getFontMetrics().stringWidth(fraction),y);y+=9;
                g.setColor(style.accent.darker().darker());g.fillRoundRect(12,y,WIDTH-24,5,4,4);g.setColor(style.accent);g.fillRoundRect(12,y,(int)Math.round((WIDTH-24)*row.fraction()),5,4,4);y+=20;
                g.setColor(style.muted);
                if(p.remaining){g.drawString(SkillProgressTool.format(row.remaining)+" XP remaining",12,y);y+=16;}
                if(p.session){g.drawString("+"+SkillProgressTool.format(row.gained)+" session · "+(row.rate>0?SkillProgressTool.format(row.rate)+"/hr":"Measuring rate"),12,y);y+=16;}
                if(p.eta){g.drawString((row.paused?"Paused · ":"")+"ETA "+SkillProgressTool.eta(row.eta),12,y);y+=16;}
                y+=14;
            }
            return HudStyle.dimension(style,WIDTH,height);
        }
        finally{g.dispose();}
    }
}
