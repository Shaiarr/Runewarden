/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
public final class SkillingCoachOverlay extends Overlay
{
    private final Client client;
    private final RunewardenConfig config;
    private final SkillingCoach coach;
    private final ItemManager itemManager;
    @Inject SkillingCoachOverlay(Client client, RunewardenConfig config, SkillingCoach coach, ItemManager itemManager)
    {
        this.client = client; this.config = config; this.coach = coach; this.itemManager = itemManager;
        setPosition(OverlayPosition.TOP_LEFT); setLayer(OverlayLayer.ABOVE_WIDGETS); setMovable(true); setResizable(false);
    }
    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        HudPreferences.Style style = HudStyle.settings(config, "outfit");
        if (!HudStyle.visible(style)) return null;
        if (!config.skillingOutfitReminder() || client.getGameState() != GameState.LOGGED_IN) { return null; }
        SkillingCoach.Reminder reminder = coach.reminder();
        if (reminder == null || reminder.missing.isEmpty()) { return null; }
        int columns = config.hudCompact() ? 2 : 1;
        int width = 264, rows = (reminder.missing.size() + columns - 1) / columns, height = 56 + rows * 30;
        Graphics2D g = HudStyle.canvas(graphics, style);
        try
        {
            HudStyle.panel(g, width, height, style);
            g.setColor(style.accent); g.setFont(HudStyle.label(12));
            g.drawString(reminder.skill.getName().toUpperCase(java.util.Locale.ROOT) + " OUTFIT", 12, 20);
            g.setFont(HudStyle.body(12)); g.setColor(HudStyle.muted(style));
            g.drawString(fit(g, reminder.outfit, width - 24), 12, 40);
            int index = 0;
            for (SkillingOutfits.Piece piece : reminder.missing)
            {
                int x = 12 + index % columns * 120, y = 50 + index / columns * 30;
                BufferedImage image = itemManager.getImage(piece.image);
                if (image != null) { g.drawImage(image, x, y, 24, 24, null); }
                g.setColor(style.text);
                g.drawString(fit(g, piece.label, columns == 2 ? 84 : 208), x + 30, y + 17);
                index++;
            }
        }
        finally { g.dispose(); }
        return HudStyle.dimension(style, width, height);
    }

    private static String fit(Graphics2D graphics, String value, int width)
    {
        if (graphics.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && graphics.getFontMetrics().stringWidth(value + "…") > width)
            value = value.substring(0, value.length() - 1);
        return value + "…";
    }
}
