/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Native scene labels, targets and native deadline urgency. Never edits the floor-item menu. */
final class GroundLootOverlay extends Overlay
{
    private static final Color GOLD = new Color(224, 190, 113), WHITE = new Color(243, 240, 232), URGENT = new Color(255, 113, 94);
    private final Client client;
    private final GroundLootTool tool;
    GroundLootOverlay(Client client, GroundLootTool tool)
    {
        this.client = client; this.tool = tool; setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE);
    }
    @Override public Dimension render(Graphics2D graphics)
    {
        GroundLootTool.Preferences settings = tool.preferences();
        if (!tool.started() || !settings.enabled || !settings.labels && !settings.tiles && !settings.radialTimers || client.getGameState() != GameState.LOGGED_IN) return null;
        WorldView view = client.getTopLevelWorldView(); if (view == null) return null;
        GroundLootTool.Reading reading = tool.reading();
        Map<LocalPoint, Integer> labelRows = new HashMap<>(); Set<LocalPoint> marked = new HashSet<>();
        graphics.setFont(HudStyle.label(12));
        for (GroundLootTool.Entry item : reading.entries)
        {
            if (item.view != view || item.world.getPlane() != view.getPlane()) continue;
            int ticks = item.despawnTick - reading.tick;
            GroundLootTier tier=GroundLootTier.value(item.value,settings);
            Color color = ticks > 0 && ticks <= 50 ? URGENT : item.target() ? settings.targetBeamTier.color : tier==null?WHITE:tier.color;
            if (settings.tiles && item.target() && marked.add(item.local))
            {
                Polygon polygon = Perspective.getCanvasTilePoly(client, item.local);
                if (polygon != null) OverlayUtil.renderPolygon(graphics, polygon, new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
            }
            if(!settings.labels&&!settings.radialTimers)continue;
            int row = labelRows.getOrDefault(item.local, 0); labelRows.put(item.local, row + 1);
            if (row >= 4) continue;
            String title = title(item), detail = detail(item, reading.tick, settings.timers);
            Point point = Perspective.getCanvasTextLocation(client, graphics, item.local, title, 38 + row * 34);
            if (point != null)
            {
                if(settings.radialTimers)paintTimers(graphics,item,reading.tick,point.getX()-15,point.getY()-7,color);
                if(settings.labels)OverlayUtil.renderTextLocation(graphics, point, title, color);
            }
            if(!settings.labels)continue;
            graphics.setFont(HudStyle.body(11));
            Point below = Perspective.getCanvasTextLocation(client, graphics, item.local, detail, 24 + row * 34);
            if (below != null) OverlayUtil.renderTextLocation(graphics, below, detail, color);
            graphics.setFont(HudStyle.label(12));
        }
        return null;
    }
    static final class Deadline
    {
        final String kind,label;final int end;final double fraction;final Color color;
        Deadline(String kind,int end,int start,int now,Color color)
        {this.kind=kind;this.end=end;this.color=color;label=GroundLootTool.countdown(end-now);fraction=RadialTimer.fraction(now,start,end);}
    }
    static List<Deadline> deadlines(GroundLootTool.Entry item,int tick,Color despawnColor)
    {
        List<Deadline> timers=new ArrayList<>(2);
        if(item.despawnTick>tick&&item.despawnTick>item.observedTick)timers.add(new Deadline("D",item.despawnTick,item.observedTick,tick,despawnColor));
        if((item.ownership==net.runelite.api.TileItem.OWNERSHIP_SELF||item.ownership==net.runelite.api.TileItem.OWNERSHIP_GROUP)
            &&!item.privateItem&&item.visibleTick>tick&&item.visibleTick>item.observedTick)
            timers.add(new Deadline("P",item.visibleTick,item.observedTick,tick,GroundLootTier.BLUE.color));
        return Collections.unmodifiableList(timers);
    }
    /** Native deadlines, with the observed remaining interval as the radial baseline. */
    static int paintTimers(Graphics2D graphics,GroundLootTool.Entry item,int tick,int centerX,int centerY,Color despawnColor)
    {
        List<Deadline> timers=deadlines(item,tick,despawnColor);int slot=0;
        Graphics2D ring=(Graphics2D)graphics.create();
        try{for(Deadline timer:timers)
        {
            int x=centerX-slot++*29;RadialTimer.paint(ring,x,centerY,11,timer.fraction,timer.color,timer.label,false);
            ring.setColor(timer.color);ring.setFont(HudStyle.label(8));ring.drawString(timer.kind,x-3,centerY+21);
        }}finally{ring.dispose();}
        return timers.size();
    }
    static String title(GroundLootTool.Entry item)
    {
        String name = item.name.length() > 24 ? item.name.substring(0, 23) + "…" : item.name;
        String result = (item.guideTarget ? "GUIDE · " : item.manualTarget ? "TARGET · " : "") + name;
        if (item.quantity > 1) result += " ×" + GroundLootTool.amount(item.quantity);
        if (item.value > 0) result += " · " + GroundLootTool.amount(item.value) + "gp";
        return result;
    }
    static String detail(GroundLootTool.Entry item, int tick, boolean timers)
    {
        String result = item.ownershipLabel();
        if (timers && item.despawnTick > tick) result += " · Ends " + GroundLootTool.countdown(item.despawnTick - tick);
        if (timers && (item.ownership == net.runelite.api.TileItem.OWNERSHIP_SELF || item.ownership == net.runelite.api.TileItem.OWNERSHIP_GROUP)
            && !item.privateItem && item.visibleTick > tick)
            result += " (public " + GroundLootTool.countdown(item.visibleTick - tick) + ")";
        return result;
    }
}
