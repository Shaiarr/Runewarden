/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A movable, entrance-only preparation card; it disappears inside the encounter. */
@Singleton
final class WaveEntryOverlay extends Overlay
{
    private static final int WIDTH = 264;
    private final Client client;
    private final WaveEntryCoach coach;
    private final RunewardenConfig config;

    @Inject WaveEntryOverlay(Client client, WaveEntryCoach coach, RunewardenConfig config)
    {
        this.client = client; this.coach = coach; this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true); setResizable(false);
    }

    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "entrance");
        if (!HudStyle.visible(style)) return null;
        WaveEntryCoach.Snapshot card = coach.snapshot();
        if (!config.waveEntryCoach() || client.getGameState() != GameState.LOGGED_IN || !card.visible()) return null;
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            HudStyle.panel(g, WIDTH, 108, style);
            g.setFont(HudStyle.label(11)); g.setColor(style.accent);
            line(g, card.title, 20);
            g.setFont(HudStyle.label(12)); g.setColor(style.text);
            line(g, card.primary, 43);
            g.setFont(HudStyle.body(11));
            g.setColor(card.ready ? new Color(125, 219, 159) : style.accent);
            line(g, card.secondary, 66);
            g.setColor(HudStyle.muted(style));
            line(g, card.detail, 88);
            return HudStyle.dimension(style, WIDTH, 108);
        }
        finally { g.dispose(); }
    }

    private static void line(Graphics2D graphics, String text, int y)
    {
        String fitted = text;
        int width = WIDTH - 24;
        if (graphics.getFontMetrics().stringWidth(fitted) > width)
        {
            while (!fitted.isEmpty() && graphics.getFontMetrics().stringWidth(fitted + "…") > width)
                fitted = fitted.substring(0, fitted.length() - 1);
            fitted += "…";
        }
        graphics.drawString(fitted, 12, y);
    }
}
