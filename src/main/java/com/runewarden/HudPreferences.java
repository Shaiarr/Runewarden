/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, versioned layout data. Shared layouts contain presentation only. */
final class HudPreferences
{
    static final String[] WIDGETS = {"resources", "dashboard", "guide", "harvest", "outfit", "entrance", "inventory", "field-effects", "training", "action-progress", "agility", "thieving", "minigame", "equipment-inspector", "hunter-rumours", "sailing", "skill-progress", "clue-timers", "container-hud", "farm-patches", "node-timers", "reward-popup", "world-ping"};
    enum Visibility { ALWAYS, COMBAT, OUT_OF_COMBAT }
    enum Frame { ROUNDED, SQUARE, BORDERLESS }
    enum Values { CURRENT_MAX, CURRENT, PERCENT }
    static final class Style
    {
        final int scale, font, opacity;
        final Color accent, text, background;
        final Color muted;
        final Visibility visibility;
        final Frame frame;
        final Values values;
        Style(int scale, int font, int opacity, Color accent, Color text, Color background,
            Visibility visibility, Frame frame, Values values)
        {
            this.scale = clamp(scale, 60, 180); this.font = clamp(font, 9, 18); this.opacity = clamp(opacity, 10, 100);
            this.accent = accent; this.text = text; this.background = background;
            this.muted = new Color((text.getRed() + background.getRed()) / 2,
                (text.getGreen() + background.getGreen()) / 2, (text.getBlue() + background.getBlue()) / 2);
            this.visibility = visibility; this.frame = frame; this.values = values;
        }
        static Style defaults(int opacity)
        {
            return new Style(100, 12, opacity, HudStyle.GOLD, HudStyle.WHITE, new Color(12, 13, 16),
                Visibility.ALWAYS, Frame.ROUNDED, Values.CURRENT_MAX);
        }
        Style withScale(int value)
        {
            return new Style(value, font, opacity, accent, text, background, visibility, frame, values);
        }
        String encode()
        {
            return scale + "," + font + "," + opacity + "," + hex(accent) + "," + hex(text) + "," + hex(background)
                + "," + visibility + "," + frame + "," + values;
        }
        private static String hex(Color c) { return String.format(java.util.Locale.ROOT, "%06x", c.getRGB() & 0xffffff); }
    }

    private HudPreferences() {}
    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    static boolean known(String key)
    {
        for (String widget : WIDGETS) if (widget.equals(key)) return true;
        return false;
    }
    static Map<String, Style> decode(String value)
    {
        if (value == null || value.isEmpty()) return Collections.emptyMap();
        if (value.length() > 8192 || !value.startsWith("RW1|")) throw new IllegalArgumentException("Unsupported layout");
        Map<String, Style> styles = new LinkedHashMap<>();
        String[] records = value.substring(4).split("\\|", -1);
        for (String record : records)
        {
            if (record.isEmpty()) continue;
            String[] pair = record.split("=", -1);
            if (pair.length != 2 || !known(pair[0]) || styles.containsKey(pair[0])) throw new IllegalArgumentException("Invalid widget");
            String[] p = pair[1].split(",", -1);
            if (p.length != 9) throw new IllegalArgumentException("Invalid style");
            styles.put(pair[0], new Style(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                color(p[3]), color(p[4]), color(p[5]), Visibility.valueOf(p[6]), Frame.valueOf(p[7]), Values.valueOf(p[8])));
        }
        return Collections.unmodifiableMap(styles);
    }
    static Color color(String hex)
    {
        if (!hex.matches("[a-fA-F0-9]{6}")) throw new IllegalArgumentException("Use six hexadecimal colour digits");
        return new Color(Integer.parseInt(hex, 16));
    }
    static String encode(Map<String, Style> styles)
    {
        StringBuilder result = new StringBuilder("RW1|");
        for (String key : WIDGETS)
            if (styles.containsKey(key)) result.append(key).append('=').append(styles.get(key).encode()).append('|');
        return result.toString();
    }
    static String share(String layout)
    {
        decode(layout);
        return "RUNEWARDEN:" + Base64.getUrlEncoder().withoutPadding().encodeToString(layout.getBytes(StandardCharsets.UTF_8));
    }
    static String unshare(String text)
    {
        if (text == null || text.length() > 12000 || !text.trim().startsWith("RUNEWARDEN:"))
            throw new IllegalArgumentException("Paste a Runewarden layout code");
        String value = new String(Base64.getUrlDecoder().decode(text.trim().substring(11)), StandardCharsets.UTF_8);
        return encode(decode(value));
    }
    static String preset(String name, int opacity)
    {
        Map<String, Style> styles = new LinkedHashMap<>();
        for (String key : WIDGETS)
        {
            Style base = Style.defaults(opacity);
            boolean combat = "PvM".equals(name) && ("resources".equals(key) || "inventory".equals(key));
            int scale = "AFK".equals(name) && "resources".equals(key) ? 125 : 100;
            styles.put(key, new Style(scale, "AFK".equals(name) ? 14 : 12, opacity,
                base.accent, base.text, base.background, combat ? Visibility.COMBAT : Visibility.ALWAYS, base.frame, base.values));
        }
        return encode(styles);
    }
}
