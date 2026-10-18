/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Quiet reminder for selected, observed native harvest groups. */
final class HarvestClockOverlay extends Overlay
{
    private static final int WIDTH = 264;
    private final HarvestClockTool tool;
    private final RunewardenConfig config;

    HarvestClockOverlay(HarvestClockTool tool, RunewardenConfig config)
    {
        this.tool = tool;
        this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
        setResizable(false);
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open harvest control", "Runewarden", event -> tool.openPanel());
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open farm run", "Runewarden", event -> tool.openFarmRun());
    }

    static List<HarvestClockTool.Harvest> compactRows(List<HarvestClockTool.Harvest> observed, long now)
    {
        List<HarvestClockTool.Harvest> result = new ArrayList<>();
        HarvestClockTool.Harvest next = null;
        for (HarvestClockTool.Harvest harvest : observed)
            if (harvest.pending(now) && (next == null || harvest.completion < next.completion)) next = harvest;
        int dueSlots = next == null ? 3 : 2;
        for (HarvestClockTool.Harvest harvest : observed)
            if (harvest.due(now) && result.size() < dueSlots) result.add(harvest);
        if (next != null) result.add(next);
        return result;
    }

    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "harvest");
        if (!HudStyle.visible(style)) return null;
        if (!tool.started() || !config.harvestHud()) return null;
        long now = Instant.now().getEpochSecond();
        List<HarvestClockTool.Harvest> observed = tool.displayHarvests(now);
        FarmPatchPlanner.RunReading run = tool.farmRun();
        if (observed.isEmpty() && run == null) return null;
        List<HarvestClockTool.Harvest> visible = config.hudCompact() ? compactRows(observed, now) : observed;
        int hidden = observed.size() - visible.size();
        boolean estimated = visible.stream().anyMatch(harvest -> harvest.estimated);
        RunewardenConfig.TimerDisplay display=NodeTimerOverlay.display(config.harvestTimerDisplay());
        boolean radial=display!=RunewardenConfig.TimerDisplay.TEXT;
        int rowHeight=radial?32:22;
        int height = 36 + visible.size() * rowHeight + (hidden > 0 || estimated ? 18 : 0) + (run == null ? 0 : 44);
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            HudStyle.panel(g, WIDTH, height, style);
            g.setColor(style.accent);
            g.setFont(HudStyle.label(12));
            g.drawString(run != null ? "FARM RUN" : "HARVEST", 12, 20);
            int due = (int) observed.stream().filter(harvest -> harvest.due(now)).count();
            String summary = run != null ? run.remainingStops + " stops" : due > 0 ? due + " due" : "Next run";
            g.setColor(HudStyle.muted(style));
            g.drawString(summary, WIDTH - 12 - g.getFontMetrics().stringWidth(summary), 20);
            int y = 44;
            if (run != null)
            {
                g.setFont(HudStyle.body(12)); g.setColor(style.text); g.drawString(fit(g, run.nextStopLabel, WIDTH - 24), 12, y); y += 20;
                g.setColor(HudStyle.muted(style)); g.drawString(run.completedStops + " / " + run.totalStops + " stops · " + run.remainingStops + " remaining", 12, y); y += 24;
            }
            for (HarvestClockTool.Harvest harvest : visible)
            {
                g.setFont(HudStyle.body(12));
                g.setColor(harvest.due(now) ? style.accent : style.text);
                String time = harvest.due(now) ? "DUE" : HarvestClockTool.countdown(harvest.completion - now);
                if (harvest.estimated) time = "~" + time;
                boolean ring=radial&&(harvest.hasWindow()||harvest.due(now));
                if(ring&&display==RunewardenConfig.TimerDisplay.RADIAL)
                {
                    String compact=harvest.due(now)?"DUE":compactCountdown(harvest.completion-now);
                    RadialTimer.paint(g,WIDTH-28,y-3,15,harvest.due(now)?0:harvest.remainingFraction(now),style.accent,compact,harvest.estimated);
                    g.setFont(HudStyle.body(12));g.setColor(harvest.due(now)?style.accent:style.text);
                    g.drawString(fit(g,harvest.name,WIDTH-66),12,y);
                }
                else
                {
                    int timeWidth=g.getFontMetrics().stringWidth(time),left=ring?44:12;
                    if(ring)RadialTimer.paint(g,26,y-4,12,harvest.due(now)?0:harvest.remainingFraction(now),style.accent,"",harvest.estimated);
                    g.setFont(HudStyle.body(12));g.setColor(harvest.due(now)?style.accent:style.text);
                    g.drawString(fit(g,harvest.name,WIDTH-timeWidth-left-24),left,y);
                    g.drawString(time,WIDTH-timeWidth-12,y);
                }
                y+=rowHeight;
            }
            g.setColor(HudStyle.muted(style));
            if (estimated) g.drawString("~ Estimated", 12, height - 10);
            if (hidden > 0)
            {
                String more = "+" + hidden + " more";
                g.drawString(more, WIDTH - 12 - g.getFontMetrics().stringWidth(more), height - 10);
            }
            return HudStyle.dimension(style, WIDTH, height);
        }
        finally { g.dispose(); }
    }

    static String compactCountdown(long seconds)
    {
        seconds=Math.max(0,seconds);
        if(seconds>=86400)return ((seconds+86399)/86400)+"d";
        if(seconds>=3600)return ((seconds+3599)/3600)+"h";
        if(seconds>=60)return ((seconds+59)/60)+"m";
        return seconds+"s";
    }

    private static String fit(Graphics2D graphics, String value, int width)
    {
        if (graphics.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && graphics.getFontMetrics().stringWidth(value + "…") > width)
            value = value.substring(0, value.length() - 1);
        return value + "…";
    }
}
