/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Compact, movable current effects card. No native state reads inside the row renderer. */
public final class FieldStatusOverlay extends Overlay
{
    private final Client client; private final RunewardenConfig config; private final FieldCoach coach;
    FieldStatusOverlay(Client client, RunewardenConfig config, FieldCoach coach)
    {
        this.client = client; this.config = config; this.coach = coach;
        setPosition(OverlayPosition.BOTTOM_LEFT); setLayer(OverlayLayer.ABOVE_WIDGETS); setMovable(true); setResizable(false);
    }
    public void setHudLocked(boolean locked) { setMovable(!locked); }
    @Override public Dimension render(Graphics2D original)
    {
        if (client.getGameState() != GameState.LOGGED_IN) return null;
        HudPreferences.Style style = HudStyle.settings(config, "field-effects");
        if (!HudStyle.visible(style)) return null;
        FieldCoach.Reading view = coach.snapshot();
        List<FieldCoach.Row> rows = new ArrayList<>(view.effects);
        rows.addAll(view.boosts);
        if (rows.isEmpty()) return null;
        int shown = Math.min(rows.size(), config.hudCompact() ? 6 : 12);
        RunewardenConfig.TimerDisplay display=config.effectTimerDisplay();
        boolean radial=display==RunewardenConfig.TimerDisplay.BOTH||display==RunewardenConfig.TimerDisplay.RADIAL;
        boolean text=display!=RunewardenConfig.TimerDisplay.RADIAL;
        int rowHeight=radial?28:18;
        int width = 250, height = 31 + shown * rowHeight + (shown < rows.size() ? 18 : 0);
        Graphics2D graphics = HudStyle.canvas(original, style);
        try
        {
            HudStyle.panel(graphics, width, height, style); graphics.setFont(HudStyle.label(11)); graphics.setColor(style.accent);
            graphics.drawString("ACTIVE EFFECTS", 10, 18);
            graphics.setFont(HudStyle.body(11));
            for (int i = 0; i < shown; i++)
            {
                FieldCoach.Row row = rows.get(i); graphics.setColor(row.warning ? new Color(255, 153, 132) : style.text);
                boolean timed=radial&&row.remainingTicks>0&&row.observedWindowTicks>0;
                int y=38+i*rowHeight;
                graphics.drawString(row.label, timed?38:10, y);
                if(text||!timed)graphics.drawString(row.value, width - 10 - graphics.getFontMetrics().stringWidth(row.value), y);
                if(timed)
                {
                    String center=row.remainingTicks<100?Long.toString((row.remainingTicks*600+999)/1000):"";
                    RadialTimer.paint(graphics,21,y-4,10,Math.min(1d,row.remainingTicks/(double)row.observedWindowTicks),
                        row.warning?new Color(255,153,132):style.accent,center,row.estimated);
                }
            }
            if (shown < rows.size()) { graphics.setColor(style.muted); graphics.drawString("+" + (rows.size() - shown) + " in Timers", 10, height - 9); }
            return HudStyle.dimension(style, width, height);
        }
        finally { graphics.dispose(); }
    }
}
