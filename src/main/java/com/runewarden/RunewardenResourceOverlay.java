/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** Live resource display only; it neither predicts mechanics nor performs actions. */
@Singleton
public final class RunewardenResourceOverlay extends Overlay
{
    private static final int GAP = 6;
    private static final long CLICK_CUE_NANOS = 150_000_000L;
    private static final Color GOLD = new Color(224, 190, 113);
    private static final int[] SPRITES = {SpriteID.MINIMAP_ORB_HITPOINTS_ICON, SpriteID.MINIMAP_ORB_PRAYER_ICON,
        SpriteID.MINIMAP_ORB_SPECIAL_ICON, SpriteID.MINIMAP_ORB_RUN_ICON};
    private final Client client;
    private final RunewardenConfig config;
    private final SpriteManager spriteManager;
    private final BufferedImage[] icons = new BufferedImage[4];

    @Inject RunewardenResourceOverlay(Client client, RunewardenConfig config, SpriteManager spriteManager)
    {
        this.client = client;
        this.config = config;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.BOTTOM_LEFT);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setMovable(true);
        setResizable(false);
    }

    static final class ResourceState
    {
        final int health;
        final int maxHealth;
        final int prayer;
        final int maxPrayer;
        final double special;
        final double energy;
        final boolean runEnabled;
        final boolean running;

        ResourceState(int health, int maxHealth, int prayer, int maxPrayer, double special,
            double energy, boolean runEnabled, boolean running)
        {
            this.health = health;
            this.maxHealth = maxHealth;
            this.prayer = prayer;
            this.maxPrayer = maxPrayer;
            this.special = special;
            this.energy = energy;
            this.runEnabled = runEnabled;
            this.running = running;
        }
    }

    static ResourceState read(Client client)
    {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null) { return null; }
        int maxHealth = client.getRealSkillLevel(Skill.HITPOINTS);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        if (maxHealth < 1 || maxPrayer < 1) { return null; }
        int pose = player.getPoseAnimation();
        boolean running = player.getRunAnimation() >= 0 && pose == player.getRunAnimation()
            && pose != player.getIdlePoseAnimation();
        return new ResourceState(Math.max(0, client.getBoostedSkillLevel(Skill.HITPOINTS)), maxHealth,
            Math.max(0, client.getBoostedSkillLevel(Skill.PRAYER)), maxPrayer,
            Math.max(0, Math.min(1000, client.getVarpValue(VarPlayerID.SA_ENERGY))) / 10.0,
            Math.max(0, Math.min(10000, client.getEnergy())) / 100.0,
            client.getVarpValue(VarPlayerID.OPTION_RUN) == 1, running);
    }

    void setHudLocked(boolean locked) { setMovable(!locked); }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!config.resourceHud()) { return null; }
        HudPreferences.Style style = HudStyle.settings(config, "resources");
        if (!HudStyle.visible(style)) return null;
        ResourceState state = read(client);
        if (state == null) { return null; }
        Graphics2D canvas = HudStyle.canvas(graphics, style);
        try
        {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean compact = config.hudCompact();
            int cardWidth = compact ? 94 : 108;
            int height = compact ? 50 : 64;
            String[] titles = {"HEALTH", "PRAYER", "SPECIAL", compact
                ? state.running ? "RUNNING" : state.runEnabled ? "RUN ON" : "WALK" : "RUN ENERGY"};
            String[] values = {state.health + " / " + state.maxHealth, state.prayer + " / " + state.maxPrayer,
                percentage(state.special), percentage(state.energy)};
            double[] fractions = {state.health / (double) state.maxHealth, state.prayer / (double) state.maxPrayer,
                state.special / 100.0, state.energy / 100.0};
            if (style.values == HudPreferences.Values.CURRENT)
            {
                values[0] = Integer.toString(state.health); values[1] = Integer.toString(state.prayer);
            }
            else if (style.values == HudPreferences.Values.PERCENT)
            {
                values[0] = percentage(100 * fractions[0]); values[1] = percentage(100 * fractions[1]);
            }
            String[] details = {fractions[0] <= config.healthAlert() / 100d ? "LOW HEALTH" : "HITPOINTS",
                fractions[1] <= config.prayerAlert() / 100d ? "LOW PRAYER" : "PRAYER POINTS", "ATTACK ENERGY",
                state.running ? "RUNNING" : state.runEnabled ? "RUN ON" : "WALK MODE"};
            for (int i = 0; i < 4; i++)
            {
                if (icons[i] == null) { icons[i] = spriteManager.getSprite(SPRITES[i], 0); }
                drawCard(canvas, style, i * (cardWidth + GAP), cardWidth, height, compact, titles[i], values[i], details[i], fractions[i], icons[i],
                    (i == 0 && fractions[i] <= config.healthAlert() / 100d) || (i == 1 && fractions[i] <= config.prayerAlert() / 100d));
            }
            int width = 4 * cardWidth + 3 * GAP;
            if (config.tickDisplay())
            {
                Graphics2D ticker = (Graphics2D) canvas.create();
                try { ticker.translate(width + GAP, 0); drawTick(ticker, style, 58, height); }
                finally { ticker.dispose(); }
                width += 58 + GAP;
            }
            return HudStyle.dimension(style, width, height);
        }
        finally { canvas.dispose(); }
    }

    private static String percentage(double number)
    {
        return number == Math.rint(number) ? String.format(Locale.ROOT, "%.0f%%", number)
            : String.format(Locale.ROOT, "%.1f%%", number);
    }

    private void drawCard(Graphics2D graphics, HudPreferences.Style style, int x, int width, int height, boolean compact, String title, String value, String detail,
        double fraction, BufferedImage icon, boolean low)
    {
        Graphics2D frame = (Graphics2D) graphics.create();
        try { frame.translate(x, 0); HudStyle.panel(frame, width, height - 1, style); }
        finally { frame.dispose(); }
        int pulse = low ? (int) (100 + 155 * (.5 + .5 * Math.sin(System.nanoTime() / 270_000_000.0))) : 170;
        graphics.setColor(new Color(style.accent.getRed(), style.accent.getGreen(), style.accent.getBlue(), pulse));
        if (style.frame != HudPreferences.Frame.BORDERLESS) graphics.drawRoundRect(x, 0, width - 1, height - 2,
            style.frame == HudPreferences.Frame.ROUNDED ? 9 : 0, style.frame == HudPreferences.Frame.ROUNDED ? 9 : 0);
        if (icon != null)
        {
            double scale = Math.min(16.0 / icon.getWidth(), 16.0 / icon.getHeight());
            int iconWidth = Math.max(1, (int) (icon.getWidth() * scale));
            int iconHeight = Math.max(1, (int) (icon.getHeight() * scale));
            graphics.drawImage(icon, x + 8 + (16 - iconWidth) / 2, 7 + (16 - iconHeight) / 2, iconWidth, iconHeight, null);
        }
        graphics.setFont(HudStyle.label(10));
        graphics.setColor(style.accent);
        graphics.drawString(title, x + 29, 18);
        graphics.setFont(HudStyle.label(13));
        graphics.setColor(style.text);
        graphics.drawString(value, x + 8, compact ? 33 : 35);
        graphics.setColor(new Color(62, 60, 53));
        graphics.fillRoundRect(x + 8, 40, width - 16, 5, 4, 4);
        graphics.setColor(low ? style.text : style.accent);
        graphics.fillRoundRect(x + 8, 40, (int) Math.round((width - 16) * Math.max(0, Math.min(1, fraction))), 5, 4, 4);
        if (!compact)
        {
            graphics.setFont(HudStyle.body(9));
            graphics.setColor(low ? style.text : HudStyle.muted(style));
            graphics.drawString(detail, x + 8, 57);
        }
    }

    private void drawTick(Graphics2D graphics, HudPreferences.Style style, int width, int height)
    {
        HudStyle.panel(graphics, width, height - 1, style);
        graphics.setFont(HudStyle.label(9)); graphics.setColor(HudStyle.muted(style));
        graphics.drawString("TICK", 8, 15);
        int cycle = HudPreferences.clamp(config.tickCycle(), 1, 12);
        int current = Math.floorMod(HudStyle.tick(), cycle);
        long stamp = HudStyle.tickNanos();
        long now = System.nanoTime();
        if (clickCueActive(config.clickTrainingCue(), stamp, now))
        {
            graphics.setColor(style.accent);
            graphics.fillRoundRect(5, 21, width - 10, 23, 5, 5);
            graphics.setFont(HudStyle.label(13));
            while (graphics.getFontMetrics().stringWidth("CLICK") > width - 16 && graphics.getFont().getSize() > 9)
                graphics.setFont(HudStyle.label(graphics.getFont().getSize() - 1));
            double luminance = .2126 * style.accent.getRed() + .7152 * style.accent.getGreen() + .0722 * style.accent.getBlue();
            graphics.setColor(luminance > 145 ? Color.BLACK : Color.WHITE);
            graphics.drawString("CLICK", (width - graphics.getFontMetrics().stringWidth("CLICK")) / 2, 37);
            return;
        }
        double elapsed = stamp == 0 ? 1 : Math.max(0, Math.min(1, (now - stamp) / 600_000_000d));
        RunewardenConfig.TickDisplay mode = config.tickDisplayMode();
        graphics.setColor(style.accent);
        if (mode == RunewardenConfig.TickDisplay.BAR)
        {
            graphics.setColor(style.accent.darker()); graphics.fillRoundRect(8, 27, width - 16, 8, 4, 4);
            graphics.setColor(style.accent); graphics.fillRoundRect(8, 27, (int) ((width - 16) * elapsed), 8, 4, 4);
        }
        else if (mode == RunewardenConfig.TickDisplay.DOTS)
        {
            int columns = Math.min(cycle, 6);
            for (int i = 0; i < cycle; i++)
            {
                graphics.setColor(i == current ? style.accent : style.accent.darker().darker());
                graphics.fillOval(7 + i % columns * 8, 25 + i / columns * 10, 6, 6);
            }
        }
        else if (mode == RunewardenConfig.TickDisplay.NUMBER)
        {
            graphics.setFont(HudStyle.label(17)); graphics.drawString(Integer.toString(current + 1), 9, 35);
            graphics.setFont(HudStyle.body(10)); graphics.drawString("/ " + cycle, 27, 35);
        }
        else
        {
            int radius = 5 + (int) (5 * (1 - elapsed));
            graphics.setColor(new Color(style.accent.getRed(), style.accent.getGreen(), style.accent.getBlue(),
                (int) (70 + 185 * (1 - elapsed))));
            graphics.fillOval(width / 2 - radius, 31 - radius, radius * 2, radius * 2);
        }
    }

    static boolean clickCueActive(boolean enabled, long stamp, long now)
    {
        return enabled && stamp != 0 && now >= stamp && now - stamp < CLICK_CUE_NANOS;
    }
}
