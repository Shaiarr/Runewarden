/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Optional movable view of the same public-equipment snapshot; no native reads during painting. */
final class EquipmentInspectorOverlay extends Overlay
{
    private final EquipmentInspectorTool tool;private final RunewardenConfig config;
    EquipmentInspectorOverlay(EquipmentInspectorTool tool,RunewardenConfig config)
    {this.tool=tool;this.config=config;setPosition(OverlayPosition.TOP_LEFT);setLayer(OverlayLayer.ABOVE_WIDGETS);setMovable(true);}
    void setLocked(boolean locked){setMovable(!locked);}
    @Override public Dimension render(Graphics2D graphics)
    {
        EquipmentInspectorTool.Preferences preferences=tool.preferences();EquipmentInspectorTool.Snapshot snapshot=tool.reading();
        HudPreferences.Style style=HudStyle.settings(config,"equipment-inspector");
        if(!preferences.hud||!snapshot.available()||snapshot.transformed||!HudStyle.visible(style))return null;
        int width=154,height=preferences.values?226:208;Graphics2D g=HudStyle.canvas(graphics,style);
        try
        {
            HudStyle.panel(g,width,height,style);g.setFont(HudStyle.label(11));g.setColor(style.accent);String name=snapshot.name;
            while(name.length()>1&&g.getFontMetrics().stringWidth(name)>width-16)name=name.substring(0,name.length()-1);g.drawString(name,8,16);
            g.setFont(HudStyle.body(10));g.setColor(style.text);g.drawString("Combat "+snapshot.combat,8,31);
            for(EquipmentInspectorTool.Slot slot:EquipmentInspectorTool.Slot.values())
            {
                int x=8+slot.cell%3*46,y=40+slot.cell/3*32;g.setColor(new Color(31,32,36,210));g.fillRoundRect(x,y,42,29,4,4);g.setColor(style.accent.darker());g.drawRoundRect(x,y,42,29,4,4);
                EquipmentInspectorTool.Piece item=snapshot.piece(slot);
                if(item!=null&&item.icon!=null)g.drawImage(item.icon,x+3,y-1,null);
                else{g.setColor(style.muted);g.setFont(HudStyle.body(8));String text=slot.kit==null?"?":slot.label;g.drawString(text,x+4,y+18);}
            }
            if(preferences.values){g.setColor(style.text);g.setFont(HudStyle.body(10));String value=GroundLootTool.amount(snapshot.value)+" gp visible"+(snapshot.unpriced>0?" +?":"");g.drawString(value,8,217);}
        }
        finally{g.dispose();}
        return HudStyle.dimension(style,width,height);
    }
}
