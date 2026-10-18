/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

/** Shared countdown visual. Its caller supplies the observed interval and provenance. */
final class RadialTimer
{
    private static final BasicStroke RING = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke ESTIMATE = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 3f}, 0f);
    private static final Color BACKGROUND = new Color(10, 12, 16, 225);
    private static final Color TRACK = new Color(90, 86, 76, 160);

    private RadialTimer() { }

    static double fraction(long now, long start, long end)
    {
        if (end <= start) return Double.NaN;
        return Math.max(0d, Math.min(1d, ((double) end - now) / ((double) end - start)));
    }

    static void paint(Graphics2D graphics, int centerX, int centerY, int radius,
        double remainingFraction, Color accent, String label, boolean estimated)
    {
        if (graphics == null || radius < 4 || radius > 100 || !Double.isFinite(remainingFraction)) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = radius * 2, x = centerX - radius, y = centerY - radius;
            g.setColor(BACKGROUND); g.fillOval(x - 2, y - 2, diameter + 4, diameter + 4);
            g.setStroke(RING); g.setColor(TRACK); g.drawOval(x, y, diameter, diameter);
            g.setStroke(estimated ? ESTIMATE : RING); g.setColor(accent == null ? HudStyle.GOLD : accent);
            double remaining = Math.max(0d, Math.min(1d, remainingFraction));
            if (remaining > 0d) g.draw(new Arc2D.Double(x, y, diameter, diameter, 90d, -360d * remaining, Arc2D.OPEN));
            String text = label == null ? "" : label;
            if (estimated && !text.isEmpty() && !text.startsWith("~")) text = "~" + text;
            g.setFont(HudStyle.label(Math.max(8, Math.min(12, radius - 2))));
            if (g.getFontMetrics().stringWidth(text) > diameter - 2) g.setFont(HudStyle.label(8));
            if (g.getFontMetrics().stringWidth(text) <= diameter - 2)
            {
                g.setColor(HudStyle.WHITE);
                g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2,
                    centerY + (g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            }
        }
        finally { g.dispose(); }
    }
}
