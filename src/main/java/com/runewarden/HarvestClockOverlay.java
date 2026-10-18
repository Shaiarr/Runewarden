/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Selected native harvest groups, with countdown rings for observed remaining windows. */
final class HarvestClockOverlay extends Overlay
{
    private static final int WIDTH = 264;
    private final HarvestClockTool tool;
    private final RunewardenConfig config;
    HarvestClockOverlay(HarvestClockTool tool, RunewardenConfig config)
    {
        this.tool = tool; this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT); setPriority(OverlayPriority.MED);
        setLayer(OverlayLayer.ABOVE_WIDGETS); setMovable(true); setResizable(false);
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Open harvest control", "Runewarden", event -> tool.openPanel());
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
        if (!HudStyle.visible(style) || !tool.started() || !config.harvestHud()) return null;
        long now = Instant.now().getEpochSecond();
        List<HarvestClockTool.Harvest> observed = tool.displayHarvests(now);
        if (observed.isEmpty()) return null;
        List<HarvestClockTool.Harvest> visible = config.hudCompact() ? compactRows(observed, now) : observed;
        int hidden = observed.size() - visible.size();
        boolean estimated = visible.stream().anyMatch(harvest -> harvest.estimated);
        RunewardenConfig.TimerDisplay display = config.harvestTimerDisplay();
        boolean radial = display != null && display != RunewardenConfig.TimerDisplay.TEXT;
        int rowHeight = radial ? 32 : 22, height = 36 + visible.size() * rowHeight + (hidden > 0 || estimated ? 18 : 0);
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            HudStyle.panel(g, WIDTH, height, style);
            g.setColor(style.accent); g.setFont(HudStyle.label(12)); g.drawString("HARVEST", 12, 20);
            long due = observed.stream().filter(harvest -> harvest.due(now)).count();
            String summary = due > 0 ? due + " due" : "Next run";
            g.setColor(HudStyle.muted(style)); right(g, summary, 20);
            int y = 44;
            for (HarvestClockTool.Harvest harvest : visible)
            {
                boolean ready = harvest.due(now), ring = radial && (harvest.hasWindow() || ready);
                BufferedImage icon = tool.icon(harvest.id);
                int left = icon == null ? 12 : 38;
                if (icon != null) g.drawImage(icon, 12, y - 16, 22, 20, null);
                String time = ready ? "DUE" : HarvestClockTool.countdown(harvest.completion - now);
                if (harvest.estimated) time = "~" + time;
                g.setFont(HudStyle.body(12)); g.setColor(ready ? style.accent : style.text);
                int reserve = g.getFontMetrics().stringWidth(time) + 24;
                if (ring && display == RunewardenConfig.TimerDisplay.RADIAL)
                {
                    RadialTimer.paint(g, WIDTH - 28, y - 3, 15, ready ? 0 : harvest.remainingFraction(now),
                        style.accent, ready ? "DUE" : compactCountdown(harvest.completion - now), harvest.estimated);
                    reserve = 60;
                }
                else
                {
                    if (ring)
                    {
                        RadialTimer.paint(g, WIDTH - reserve - 6, y - 4, 12, ready ? 0 : harvest.remainingFraction(now), style.accent, "", harvest.estimated);
                        reserve += 32;
                    }
                    g.setFont(HudStyle.body(12)); g.setColor(ready ? style.accent : style.text); right(g, time, y);
                }
                g.setFont(HudStyle.body(12)); g.setColor(ready ? style.accent : style.text);
                g.drawString(fit(g, harvest.name, WIDTH - left - reserve), left, y); y += rowHeight;
            }
            g.setColor(HudStyle.muted(style));
            if (estimated) g.drawString("~ Estimated", 12, height - 10);
            if (hidden > 0) right(g, "+" + hidden + " more", height - 10);
            return HudStyle.dimension(style, WIDTH, height);
        }
        finally { g.dispose(); }
    }
    private static void right(Graphics2D g, String text, int y)
    { g.drawString(text, WIDTH - 12 - g.getFontMetrics().stringWidth(text), y); }
    static String compactCountdown(long seconds)
    {
        seconds = Math.max(0, seconds);
        if (seconds >= 86400) return (seconds + 86399) / 86400 + "d";
        if (seconds >= 3600) return (seconds + 3599) / 3600 + "h";
        if (seconds >= 60) return (seconds + 59) / 60 + "m";
        return seconds + "s";
    }
    private static String fit(Graphics2D g, String value, int width)
    {
        if (g.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && g.getFontMetrics().stringWidth(value + "…") > width) value = value.substring(0, value.length() - 1);
        return value + "…";
    }
}
