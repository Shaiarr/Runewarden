/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Clock, world, scoped max hit and native equipment slots move as one compact block. */
final class CommandDashboardOverlay extends Overlay
{
    static final int WIDTH = 184;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Font CLOCK_COMPACT = new Font(Font.MONOSPACED, Font.BOLD, 20);
    private static final Font CLOCK_COMFORTABLE = new Font(Font.MONOSPACED, Font.BOLD, 24);
    private static final Font HIT_COMPACT = new Font(Font.MONOSPACED, Font.BOLD, 19);
    private static final Font HIT_COMFORTABLE = new Font(Font.MONOSPACED, Font.BOLD, 22);
    private final CommandDashboardTool tool;
    private final RunewardenConfig config;

    CommandDashboardOverlay(CommandDashboardTool tool, RunewardenConfig config)
    {
        this.tool = tool;
        this.config = config;
        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
        setResizable(false);
    }

    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "dashboard");
        if (!HudStyle.visible(style)) return null;
        if (!tool.started() || !config.dashboardHud()) return null;
        CommandDashboardTool.Dashboard reading = tool.reading();
        if (!reading.loggedIn) return null;
        Layout layout = new Layout(config.hudCompact(), config.clockHud(), config.dashboardEquipment());
        Graphics2D canvas = HudStyle.canvas(graphics, style);
        try
        {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            HudStyle.panel(canvas, layout.width, layout.height, style);
            canvas.setColor(style.accent);
            canvas.setFont(HudStyle.label(layout.compact ? 11 : 12));
            canvas.drawString("RUNEWARDEN", layout.margin, layout.compact ? 17 : 20);
            if (config.clockHud())
            {
                canvas.setFont(HudStyle.body(9));
                canvas.setColor(HudStyle.muted(style));
                right(canvas, config.clockUtc() ? "UTC" : "LOCAL", layout.width - layout.margin, layout.compact ? 17 : 20);
                canvas.setColor(style.text);
                canvas.setFont(layout.compact ? CLOCK_COMPACT : CLOCK_COMFORTABLE);
                canvas.drawString(clockText(Instant.now(), config.clockUtc(), ZoneId.systemDefault()),
                    layout.margin, layout.compact ? 40 : 47);
            }
            canvas.setFont(HudStyle.label(layout.compact ? 12 : 14));
            canvas.setColor(style.accent);
            right(canvas, "W" + reading.world, layout.width - layout.margin, config.clockHud()
                ? (layout.compact ? 39 : 46) : layout.headerBottom - 8);
            canvas.setColor(HudStyle.muted(style));
            canvas.setFont(HudStyle.body(layout.compact ? 10 : 11));
            canvas.drawString(fit(canvas, reading.worldDescription, layout.width - 2 * layout.margin),
                layout.margin, layout.headerBottom - 8);
            divider(canvas, layout, layout.headerBottom, style);
            canvas.setFont(HudStyle.label(layout.compact ? 10 : 11));
            canvas.setColor(style.accent);
            canvas.drawString("MAX HIT", layout.margin, layout.headerBottom + (layout.compact ? 19 : 24));
            canvas.setFont(layout.compact ? HIT_COMPACT : HIT_COMFORTABLE);
            canvas.setColor(style.text);
            right(canvas, reading.maxHit.value == null ? "—" : reading.maxHit.value.toString(),
                layout.width - layout.margin, layout.headerBottom + (layout.compact ? 20 : 25));
            canvas.setColor(HudStyle.muted(style));
            canvas.setFont(HudStyle.body(layout.compact ? 9 : 10));
            canvas.drawString(fit(canvas, reading.maxHit.scope, layout.width - 2 * layout.margin),
                layout.margin, layout.metricsBottom - 9);
            if (config.dashboardEquipment())
            {
                divider(canvas, layout, layout.metricsBottom, style);
                canvas.setColor(style.accent);
                canvas.setFont(HudStyle.label(10));
                canvas.drawString("WORN EQUIPMENT", layout.margin, layout.gridY - (layout.compact ? 6 : 8));
                for (CommandDashboardTool.SlotPosition position : CommandDashboardTool.LAYOUT)
                {
                    CommandDashboardTool.WornSlot worn = null;
                    for (CommandDashboardTool.WornSlot candidate : reading.equipment)
                        if (candidate.position.slot == position.slot) { worn = candidate; break; }
                    drawSlot(canvas, position, worn, layout.slot(position), style);
                }
            }
            return HudStyle.dimension(style, layout.width, layout.height);
        }
        finally { canvas.dispose(); }
    }

    static String clockText(Instant instant, boolean utc, ZoneId local)
    {
        return CLOCK.withZone(utc ? ZoneOffset.UTC : local).format(instant);
    }

    /** Native item images are centred at their original dimensions, never stretched or rescaled. */
    private static void drawSlot(Graphics2D canvas, CommandDashboardTool.SlotPosition position,
        CommandDashboardTool.WornSlot worn, Rectangle bounds, HudPreferences.Style style)
    {
        canvas.setColor(new Color(22, 23, 26, 215));
        canvas.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 4, 4);
        canvas.setColor(worn != null && worn.equipped() ? style.accent.darker() : HudStyle.muted(style).darker());
        canvas.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 4, 4);
        if (worn != null && worn.image != null)
            canvas.drawImage(worn.image, bounds.x + (bounds.width - worn.image.getWidth()) / 2,
                bounds.y + (bounds.height - worn.image.getHeight()) / 2, null);
        else
        {
            canvas.setFont(HudStyle.body(9));
            canvas.setColor(HudStyle.muted(style));
            String label = worn != null && worn.equipped() ? "…" : position.label;
            canvas.drawString(label, bounds.x + (bounds.width - canvas.getFontMetrics().stringWidth(label)) / 2,
                bounds.y + (bounds.height - canvas.getFontMetrics().getHeight()) / 2 + canvas.getFontMetrics().getAscent());
        }
    }

    private static void divider(Graphics2D canvas, Layout layout, int y, HudPreferences.Style style)
    {
        canvas.setColor(style.accent.darker());
        canvas.drawLine(layout.margin, y, layout.width - layout.margin, y);
    }

    private static void right(Graphics2D canvas, String text, int x, int y)
    {
        canvas.drawString(text, x - canvas.getFontMetrics().stringWidth(text), y);
    }

    private static String fit(Graphics2D graphics, String value, int width)
    {
        if (value == null) return "";
        if (graphics.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && graphics.getFontMetrics().stringWidth(value + "…") > width)
            value = value.substring(0, value.length() - 1);
        return value + "…";
    }

    static final class Layout
    {
        final boolean compact;
        final int width, height, margin, headerBottom, metricsBottom, gridY, slotWidth, slotHeight, slotStep, rowStep;
        Layout(boolean compact, boolean clock, boolean equipment)
        {
            this.compact = compact;
            width = compact ? WIDTH : 208;
            margin = compact ? 11 : 13;
            headerBottom = (compact ? 62 : 70) - (clock ? 0 : 18);
            metricsBottom = headerBottom + (compact ? 40 : 49);
            gridY = metricsBottom + (compact ? 21 : 24);
            slotWidth = compact ? 40 : 44;
            slotHeight = compact ? 34 : 38;
            slotStep = compact ? 44 : 50;
            rowStep = compact ? 35 : 41;
            height = equipment ? gridY + 4 * rowStep + slotHeight + (compact ? 7 : 10)
                : metricsBottom + (compact ? 7 : 10);
        }
        Rectangle slot(CommandDashboardTool.SlotPosition position)
        {
            int left = (width - (2 * slotStep + slotWidth)) / 2;
            return new Rectangle(left + position.column * slotStep, gridY + position.row * rowStep, slotWidth, slotHeight);
        }
    }
}
