/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/** Adds progress to the actual skill widgets without replacing them or their native actions. */
public final class SkillTabProgressOverlay extends Overlay
{
    private final Client client;
    private final SkillProgressTool tool;
    private final RunewardenConfig config;
    private final TooltipManager tooltips;
    SkillTabProgressOverlay(Client client,SkillProgressTool tool,RunewardenConfig config,TooltipManager tooltips)
    {this.client=client;this.tool=tool;this.config=config;this.tooltips=tooltips;setPosition(OverlayPosition.DYNAMIC);setLayer(OverlayLayer.MANUAL);drawAfterInterface(InterfaceID.STATS);}
    @Override public Dimension render(Graphics2D graphics)
    {
        SkillProgressTool.Preferences p=tool.preferences();if(!p.tabBars&&!p.tabTooltips)return null;
        SkillProgressTool.View view=tool.reading();if(view.skills.isEmpty()||client.getGameState()!=net.runelite.api.GameState.LOGGED_IN)return null;
        HudPreferences.Style style=HudStyle.settings(config,"skill-progress");Point mouse=client.getMouseCanvasPosition();Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            for(SkillProgressTool.Row row:view.skills.values())
            {
                Widget widget=client.getWidget(component(row.skill));if(widget==null||widget.isHidden())continue;
                Rectangle bounds=widget.getBounds();if(bounds==null||bounds.width<10||bounds.height<10)continue;
                if(p.tabBars)
                {
                    int x=bounds.x+3,y=bounds.y+bounds.height-4,width=bounds.width-6;
                    g.setColor(new Color(10,12,16,220));g.fillRect(x,y,width,3);g.setColor(style.accent);g.fillRect(x,y,(int)Math.round(width*row.fraction()),3);
                }
                if(p.tabTooltips&&!client.isMenuOpen()&&mouse!=null&&bounds.contains(mouse.getX(),mouse.getY()))
                {
                    String text="<col=e0be71>"+row.skill.getName()+" · Level "+row.level+"</col><br>"+SkillProgressTool.format(row.xp)+" XP<br>Goal: "+row.targetLabel()+"<br>"+SkillProgressTool.format(row.remaining)+" XP remaining<br>"+(row.paused?"Paused · ":"")+(row.rate>0?SkillProgressTool.format(row.rate)+" XP/hr":"Measuring rate")+" · ETA "+SkillProgressTool.eta(row.eta);
                    tooltips.add(new Tooltip(text));
                }
            }
        }
        finally{g.dispose();}
        return null;
    }
    static int component(Skill skill)
    {
        switch(skill)
        {
            case ATTACK:return InterfaceID.Stats.ATTACK;
            case STRENGTH:return InterfaceID.Stats.STRENGTH;
            case DEFENCE:return InterfaceID.Stats.DEFENCE;
            case RANGED:return InterfaceID.Stats.RANGED;
            case PRAYER:return InterfaceID.Stats.PRAYER;
            case MAGIC:return InterfaceID.Stats.MAGIC;
            case RUNECRAFT:return InterfaceID.Stats.RUNECRAFT;
            case CONSTRUCTION:return InterfaceID.Stats.CONSTRUCTION;
            case HITPOINTS:return InterfaceID.Stats.HITPOINTS;
            case AGILITY:return InterfaceID.Stats.AGILITY;
            case HERBLORE:return InterfaceID.Stats.HERBLORE;
            case THIEVING:return InterfaceID.Stats.THIEVING;
            case CRAFTING:return InterfaceID.Stats.CRAFTING;
            case FLETCHING:return InterfaceID.Stats.FLETCHING;
            case SLAYER:return InterfaceID.Stats.SLAYER;
            case HUNTER:return InterfaceID.Stats.HUNTER;
            case MINING:return InterfaceID.Stats.MINING;
            case SMITHING:return InterfaceID.Stats.SMITHING;
            case FISHING:return InterfaceID.Stats.FISHING;
            case COOKING:return InterfaceID.Stats.COOKING;
            case FIREMAKING:return InterfaceID.Stats.FIREMAKING;
            case WOODCUTTING:return InterfaceID.Stats.WOODCUTTING;
            case FARMING:return InterfaceID.Stats.FARMING;
            case SAILING:return InterfaceID.Stats.SAILING;
            default:throw new IllegalArgumentException("Unsupported native skill");
        }
    }
}
