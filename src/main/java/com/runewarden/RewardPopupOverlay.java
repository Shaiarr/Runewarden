/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/** One read-only reward card. The controller owns ordering and its bounded queue. */
@Singleton
final class RewardPopupOverlay extends Overlay
{
    static final int WIDTH = 420;
    private static final Color BLACK = new Color(12, 13, 17);
    private static final Color WHITE = new Color(244, 241, 234);
    private static final Color GOLD = new Color(224, 190, 113);
    private static final Color MUTED = new Color(180, 181, 181);
    private static final Font HEADER = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    private static final Font NAME = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    private static final Font META = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font SMALL = new Font(Font.SANS_SERIF, Font.BOLD, 9);
    private final Client client;
    private final ItemManager items;
    private final RewardSettings settings;
    private final LongSupplier clock;
    private final Options fixed;
    private RewardSettings.Preferences lastPreferences;
    private Options cachedOptions;
    private volatile Current current;
    private RunewardenConfig hudConfig = new RunewardenConfig() {};
    @Inject void setHudConfig(RunewardenConfig value) { hudConfig = value; }
    void setHudLocked(boolean locked) { setMovable(!locked); }

    private static final class Options
    {
        final boolean enabled, animations;
        final long duration;
        final int scale;
        Options(boolean enabled, boolean animations, int seconds, int scale)
        {
            this.enabled = enabled; this.animations = animations;
            this.duration = Math.max(1, Math.min(30, seconds)) * 1_000_000_000L;
            this.scale = Math.max(50, Math.min(200, scale));
        }
    }
    private static final class Current
    {
        final RewardNotification entry;
        final BufferedImage image;
        final long started;
        final Color tier;
        final String rarity, quantity;
        Layout layout;
        Current(RewardNotification entry, BufferedImage image, long started)
        {
            this.entry = entry; this.image = image; this.started = started;
            this.tier = entry.rarityKnown && entry.tier != null ? entry.tier.color() : WHITE;
            this.rarity = entry.rarityKnown && entry.tier != null ? entry.tier.label().toUpperCase(Locale.ROOT) : "UNRATED";
            this.quantity = entry.quantity > 1 ? "×" + entry.quantity : "";
        }
    }
    static final class Layout
    {
        final List<String> name, metadata;
        final int height;
        Layout(List<String> name, List<String> metadata)
        {
            this.name = name; this.metadata = metadata;
            this.height = Math.max(114, 45 + name.size() * 22 + metadata.size() * 17 + 14);
        }
    }
    static final class Motion
    {
        final double alpha, offset, remaining, burst, glow;
        Motion(double alpha, double offset, double remaining, double burst, double glow)
        { this.alpha = alpha; this.offset = offset; this.remaining = remaining; this.burst = burst; this.glow = glow; }
    }

    @Inject RewardPopupOverlay(Client client, ItemManager items, RewardSettings settings)
    {
        this(client, items, settings, System::nanoTime, null);
    }
    /** Deterministic fixture constructor, without native state or a background timer. */
    RewardPopupOverlay(Client client, ItemManager items, LongSupplier clock, boolean animations, int seconds, int scale)
    {
        this(client, items, null, clock, new Options(true, animations, seconds, scale));
    }
    private RewardPopupOverlay(Client client, ItemManager items, RewardSettings settings, LongSupplier clock, Options fixed)
    {
        this.client = client; this.items = items; this.settings = settings; this.clock = clock; this.fixed = fixed;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);
        setMovable(true); setResizable(false);
    }

    /** Client thread only, so ItemManager always supplies the native item image safely. */
    void show(RewardNotification entry)
    {
        if (entry == null || !options().enabled && entry.kind != RewardNotification.Kind.DEMO) { clear(); return; }
        BufferedImage image = entry.itemId > 0
            ? items.getImage(entry.itemId, Math.max(1, entry.quantity), entry.quantity > 1) : null;
        current = new Current(entry, image, clock.getAsLong());
    }
    void clear() { current = null; }
    boolean isActive()
    {
        Current selected = current;
        if (selected == null) return false;
        Options options = options();
        if (!options.enabled && selected.entry.kind != RewardNotification.Kind.DEMO || age(selected) >= options.duration)
        {
            if (current == selected) current = null;
            return false;
        }
        return true;
    }
    private long age(Current selected) { return Math.max(0, clock.getAsLong() - selected.started); }
    private Options options()
    {
        if (fixed != null) return fixed;
        RewardSettings.Preferences preferences = settings.snapshot();
        if (preferences != lastPreferences)
        {
            lastPreferences = preferences;
            cachedOptions = new Options(preferences.enabled, preferences.animations, preferences.durationSeconds, preferences.scalePercent);
        }
        return cachedOptions;
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!isActive()) return null;
        Current selected = current;
        if (selected == null) return null;
        if (selected.entry.kind != RewardNotification.Kind.DEMO && client.getGameState() != GameState.LOGGED_IN)
        { clear(); return null; }
        Options options = options();
        HudPreferences.Style style = HudStyle.settings(hudConfig, "reward-popup");
        if (!HudStyle.visible(style)) return null;
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (selected.layout == null) selected.layout = layout(g, selected.entry);
            Layout layout = selected.layout;
            int requested = (int) Math.round(options.scale * style.scale / 100d * style.font / 12d);
            double scale = fittedScale(client.getCanvasWidth(), client.getCanvasHeight(), layout.height, requested);
            int extra = options.animations ? 18 : 0;
            Motion motion = motion(age(selected), options.duration, options.animations);
            g.scale(scale, scale);
            g.translate(0, extra + motion.offset);
            g.setComposite(AlphaComposite.SrcOver.derive((float) motion.alpha));
            draw(g, selected, layout, motion, options.animations);
            return new Dimension((int) Math.ceil(WIDTH * scale), (int) Math.ceil((layout.height + extra) * scale));
        }
        finally { g.dispose(); }
    }

    static double fittedScale(int canvasWidth, int canvasHeight, int height, int requested)
    {
        int width = canvasWidth > 0 ? canvasWidth : 500, availableHeight = canvasHeight > 0 ? canvasHeight : 500;
        return Math.max(.2, Math.min(Math.max(50, Math.min(200, requested)) / 100d,
            Math.min(Math.max(84, width - 24) / (double) WIDTH, Math.max(60, availableHeight - 36) / (double) (height + 18))));
    }
    static Motion motion(long elapsed, long duration, boolean enabled)
    {
        double age = Math.max(0, elapsed), remaining = Math.max(0, Math.min(1, 1 - age / Math.max(1, duration)));
        if (!enabled) return new Motion(1, 0, remaining, 1, 0);
        double enter = Math.max(0, Math.min(1, age / 260_000_000d));
        double exit = Math.max(0, Math.min(1, (duration - age) / 320_000_000d));
        double eased = 1 - Math.pow(1 - enter, 3);
        return new Motion(Math.min(eased, exit), -18 * (1 - eased) - 9 * (1 - exit), remaining,
            Math.max(0, Math.min(1, age / 900_000_000d)), .5 + .5 * Math.sin(age / 200_000_000d));
    }
    static Layout layout(Graphics2D graphics, RewardNotification entry)
    {
        graphics.setFont(NAME);
        String title = clean(entry.itemName);
        if (title.isEmpty()) title = clean(entry.title);
        if (title.isEmpty()) title = "Runewarden";
        List<String> names = GuideCoachOverlay.wrap(graphics.getFontMetrics(), title, WIDTH - 118, 2);
        List<String> metadata = new ArrayList<>();
        graphics.setFont(META);
        add(metadata, "Source · ", entry.source, graphics.getFontMetrics(), 2);
        add(metadata, "Drop chance · ", entry.oddsText, graphics.getFontMetrics(), 1);
        add(metadata, "Player ownership · ", entry.ownershipText, graphics.getFontMetrics(), 2);
        add(metadata, "", entry.detail, graphics.getFontMetrics(), 2);
        return new Layout(names, metadata);
    }
    private static void add(List<String> lines, String prefix, String value, FontMetrics metrics, int maximum)
    {
        String text = clean(value);
        if (!text.isEmpty() && lines.size() < 7)
            lines.addAll(GuideCoachOverlay.wrap(metrics, prefix + text, WIDTH - 117, Math.min(maximum, 7 - lines.size())));
    }
    private static String clean(String value)
    {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 180 ? text.substring(0, 179) + "…" : text;
    }
    private static void draw(Graphics2D g, Current selected, Layout layout, Motion motion, boolean animate)
    {
        RewardNotification entry = selected.entry;
        Color tier = selected.tier;
        int height = layout.height;
        g.setColor(new Color(0, 0, 0, 95)); g.fillRoundRect(3, 5, WIDTH - 6, height - 5, 15, 15);
        g.setColor(BLACK); g.fillRoundRect(0, 0, WIDTH - 1, height - 6, 12, 12);
        g.setColor(new Color(91, 79, 51)); g.drawRoundRect(0, 0, WIDTH - 2, height - 7, 12, 12);
        g.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), 50));
        g.fillRoundRect(5, 5, 78, height - 16, 8, 8);
        if (animate)
        {
            g.setStroke(new BasicStroke(3));
            g.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), (int) (30 + 48 * motion.glow)));
            g.drawRoundRect(2, 2, WIDTH - 6, height - 11, 11, 11);
            g.setStroke(new BasicStroke(1));
        }
        g.setColor(GOLD);
        for (int x : new int[] {0, WIDTH - 2})
        {
            int direction = x == 0 ? 1 : -1;
            g.drawLine(x + direction * 6, 0, x + direction * 31, 0);
            g.drawLine(x, 7, x, 22);
        }
        g.setFont(HEADER); g.setColor(GOLD); g.drawString(header(entry.kind), 16, 23);
        g.setFont(SMALL); g.setColor(tier);
        g.drawString(selected.rarity, WIDTH - 17 - g.getFontMetrics().stringWidth(selected.rarity), 23);
        int iconY = Math.min(43, height - 71);
        g.setColor(new Color(9, 10, 13)); g.fillRoundRect(16, iconY, 60, 58, 7, 7);
        g.setColor(tier.darker()); g.drawRoundRect(16, iconY, 60, 58, 7, 7);
        if (selected.image != null)
            g.drawImage(selected.image, 46 - selected.image.getWidth() / 2, iconY + (58 - selected.image.getHeight()) / 2, null);
        else drawEmblem(g, 46, iconY + 28, tier, entry.kind);
        if (animate && motion.burst < 1)
        {
            g.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), (int) (190 * (1 - motion.burst))));
            for (int i = 0; i < 7; i++)
            {
                double angle = i * Math.PI * 2 / 7, radius = 21 + 21 * motion.burst;
                int x = 46 + (int) (Math.cos(angle) * radius), y = iconY + 28 + (int) (Math.sin(angle) * radius);
                g.drawLine(x - 2, y, x + 2, y); g.drawLine(x, y - 2, x, y + 2);
            }
        }
        if (entry.quantity > 1)
        {
            g.setFont(SMALL); g.setColor(WHITE);
            g.drawString(selected.quantity, 46 - g.getFontMetrics().stringWidth(selected.quantity) / 2, Math.min(height - 17, iconY + 73));
        }
        int y = 52;
        g.setFont(NAME); g.setColor(WHITE);
        for (String line : layout.name) { g.drawString(line, 98, y); y += 22; }
        y += 3;
        g.setFont(META);
        for (String line : layout.metadata)
        {
            g.setColor(line.startsWith("Player ownership ·") ? tier : MUTED);
            g.drawString(line, 98, y); y += 17;
        }
        g.setColor(new Color(50, 47, 39)); g.fillRoundRect(16, height - 12, WIDTH - 34, 2, 2, 2);
        g.setColor(tier); g.fillRoundRect(16, height - 12, (int) ((WIDTH - 34) * motion.remaining), 2, 2, 2);
    }
    private static String header(RewardNotification.Kind kind)
    {
        if (kind == RewardNotification.Kind.COLLECTION_LOG) return "COLLECTION LOG";
        if (kind == RewardNotification.Kind.RARE_DROP) return "RARE DROP";
        if (kind == RewardNotification.Kind.MUSIC_UNLOCK) return "MUSIC UNLOCK";
        if (kind == RewardNotification.Kind.COMBAT_ACHIEVEMENT) return "COMBAT ACHIEVEMENT";
        return "PREVIEW";
    }
    private static void drawEmblem(Graphics2D graphics, int x, int y, Color tier, RewardNotification.Kind kind)
    {
        graphics.setColor(tier); graphics.setStroke(new BasicStroke(2));
        if (kind == RewardNotification.Kind.MUSIC_UNLOCK)
        {
            graphics.drawLine(x + 1, y - 14, x + 1, y + 8); graphics.drawLine(x + 1, y - 14, x + 12, y - 17);
            graphics.fillOval(x - 9, y + 4, 11, 8);
        }
        else
        {
            java.awt.Polygon shield = new java.awt.Polygon(new int[] {x - 14, x + 14, x + 11, x, x - 11},
                new int[] {y - 15, y - 15, y + 5, y + 17, y + 5}, 5);
            graphics.drawPolygon(shield); graphics.drawLine(x - 6, y - 5, x, y + 6); graphics.drawLine(x, y + 6, x + 8, y - 9);
        }
        graphics.setStroke(new BasicStroke(1));
    }
}
