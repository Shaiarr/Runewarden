/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Dimension;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared, allocation-light presentation for the local Runewarden HUD. */
final class HudStyle
{
    static final Color GOLD = new Color(224, 190, 113);
    static final Color WHITE = new Color(243, 240, 232);
    static final Color MUTED = new Color(185, 183, 175);
    static final Color BORDER = new Color(102, 89, 58);
    private static final Font[] LABELS = fonts(Font.BOLD);
    private static final Font[] BODIES = fonts(Font.PLAIN);
    private static volatile String encoded = "";
    private static volatile Map<String, HudPreferences.Style> styles = Collections.emptyMap();
    private static volatile boolean combat;
    private static volatile boolean bank;
    private static volatile long tickNanos;
    private static volatile int tick;
    private static volatile int defaultOpacity = 85;
    private static volatile HudPreferences.Style defaultStyle = HudPreferences.Style.defaults(85);
    private static final Map<String, Integer> resizePreview = new ConcurrentHashMap<>();

    private HudStyle() {}

    private static Font[] fonts(int weight)
    {
        Font[] fonts = new Font[33];
        for (int size = 8; size < fonts.length; size++) fonts[size] = new Font(Font.SANS_SERIF, weight, size);
        return fonts;
    }

    static String fit(Graphics2D graphics, String value, int width)
    {
        if (value == null) return "";
        if (graphics.getFontMetrics().stringWidth(value) <= width) return value;
        while (value.length() > 1 && graphics.getFontMetrics().stringWidth(value + "…") > width)
            value = value.substring(0, value.length() - 1);
        return value + "…";
    }

    static Font label(int size) { return LABELS[Math.max(8, Math.min(32, size))]; }
    static Font body(int size) { return BODIES[Math.max(8, Math.min(32, size))]; }

    static HudPreferences.Style settings(RunewardenConfig config, String widget)
    {
        String current = config.hudWidgetSettings();
        if (current == null) current = "";
        if (!current.equals(encoded))
        {
            synchronized (HudStyle.class)
            {
                if (!current.equals(encoded))
                {
                    styles = HudPreferences.safeDecode(current);
                    encoded = current;
                }
            }
        }
        HudPreferences.Style result = styles.get(widget);
        if (result == null)
        {
            int opacity = config.hudOpacity();
            if (opacity != defaultOpacity)
            {
                defaultStyle = HudPreferences.Style.defaults(opacity);
                defaultOpacity = opacity;
            }
            result = defaultStyle;
        }
        Integer preview = resizePreview.get(widget);
        return preview == null ? result : result.withScale(preview);
    }

    /** A drag is painted immediately; its one saved setting is committed on release. */
    static void previewScale(String widget, int scale) { resizePreview.put(widget, HudPreferences.clamp(scale, 60, 180)); }
    static void clearResizePreview(String widget) { if (widget != null) resizePreview.remove(widget); }

    static void context(boolean inCombat, boolean bankOpen, long nanos, int number)
    {
        combat = inCombat; bank = bankOpen; tickNanos = nanos; tick = number;
    }
    static boolean bankOpen() { return bank; }
    static boolean inCombat(net.runelite.api.Player player)
    {
        if (player == null) return false;
        net.runelite.api.Actor target = player.getInteracting();
        return player.getHealthRatio() >= 0 || target instanceof net.runelite.api.Player
            || target instanceof net.runelite.api.NPC && ((net.runelite.api.NPC) target).getCombatLevel() > 0;
    }
    static long tickNanos() { return tickNanos; }
    static int tick() { return tick; }
    static boolean visible(HudPreferences.Style style)
    {
        return style.visibility == HudPreferences.Visibility.ALWAYS
            || style.visibility == HudPreferences.Visibility.COMBAT && combat
            || style.visibility == HudPreferences.Visibility.OUT_OF_COMBAT && !combat;
    }
    static Graphics2D canvas(Graphics2D graphics, HudPreferences.Style style)
    {
        Graphics2D result = (Graphics2D) graphics.create();
        double scale = style.scale / 100d * style.font / 12d;
        result.scale(scale, scale);
        return result;
    }
    static Dimension dimension(HudPreferences.Style style, int width, int height)
    {
        double scale = style.scale / 100d * style.font / 12d;
        return new Dimension((int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
    }
    static Font label(HudPreferences.Style style, int base) { return label(base); }
    static Font body(HudPreferences.Style style, int base) { return body(base); }
    static Color muted(HudPreferences.Style style)
    {
        return style.muted;
    }
    /** Connect a scene card to its real projected target, without moving the endpoint. */
    static void tether(Graphics2D graphics,java.awt.Rectangle card,net.runelite.api.Point anchor,
        Color accent,int canvasWidth,int canvasHeight)
    {
        if(card==null||anchor==null||anchor.getX()<0||anchor.getY()<0
            ||anchor.getX()>=canvasWidth||anchor.getY()>=canvasHeight||card.contains(anchor.getX(),anchor.getY()))return;
        Graphics2D g=(Graphics2D)graphics.create();
        try
        {
            g.clipRect(0,0,canvasWidth,canvasHeight);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int x=Math.max(card.x+3,Math.min(anchor.getX(),card.x+card.width-3));
            int y=anchor.getY()>=card.y+card.height?card.y+card.height:card.y;
            g.setStroke(new java.awt.BasicStroke(3.5f));g.setColor(new Color(6,8,10,220));g.drawLine(x,y,anchor.getX(),anchor.getY());
            g.fillOval(anchor.getX()-3,anchor.getY()-3,7,7);
            g.setStroke(new java.awt.BasicStroke(1.4f));g.setColor(accent==null?GOLD:accent);g.drawLine(x,y,anchor.getX(),anchor.getY());
            g.fillOval(anchor.getX()-2,anchor.getY()-2,5,5);
        }
        finally{g.dispose();}
    }
    static void panel(Graphics2D graphics, int width, int height, HudPreferences.Style style)
    {
        int radius = style.frame == HudPreferences.Frame.ROUNDED ? 9 : 0;
        Color background = new Color(style.background.getRed(), style.background.getGreen(), style.background.getBlue(),
            (int) Math.round(style.opacity * 2.55));
        panel(graphics, width, height, radius, background,
            style.frame == HudPreferences.Frame.BORDERLESS ? null : style.accent.darker(), style.accent);
    }

    private static void panel(Graphics2D graphics, int width, int height, int radius,
        Color background, Color border, Color accent)
    {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(background);
        graphics.fillRoundRect(0, 0, width, height, radius, radius);
        if (border != null)
        {
            graphics.setColor(border);
            graphics.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
            graphics.setColor(accent);
            graphics.drawLine(10, 0, Math.min(width - 11, 42), 0);
        }
    }

    static Color background(RunewardenConfig config)
    {
        int opacity = Math.max(40, Math.min(100, config.hudOpacity()));
        return new Color(12, 13, 16, (int) Math.round(opacity * 2.55));
    }

    static void panel(Graphics2D graphics, int width, int height, RunewardenConfig config)
    {
        panel(graphics, width, height, 9, background(config), BORDER, GOLD);
    }
}
