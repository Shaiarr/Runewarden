/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Public overlay presentation belonging exclusively to Runewarden's own cards. */
final class HudLayoutPositions
{
    private HudLayoutPositions() {}
    static String capture(ConfigManager unused, Map<String, Overlay> owned)
    {
        StringBuilder result = new StringBuilder("RWP1|");
        owned.forEach((id, overlay) ->
        {
            if (overlay == null || !HudPreferences.known(id)) return;
            Point location = overlay.getPreferredLocation();
            if (location != null)
            {
                // Native drag origins are intentionally package-private. Rendered bounds are
                // public absolute canvas coordinates, independent of those native origins.
                Rectangle bounds = overlay.getBounds();
                if (bounds.width > 0 && bounds.height > 0) location = bounds.getLocation();
                append(result, id, 0, location.x + ":" + location.y);
                append(result, id, 2, "MANUAL");
                append(result, id, 3, "LEFT");
                append(result, id, 4, "TOP");
            }
            if (overlay.getPreferredPosition() != null)
                append(result, id, 1, overlay.getPreferredPosition().name());
        });
        return result.toString();
    }
    private static void append(StringBuilder result, String id, int field, String value)
    { result.append(id).append(':').append(field).append('=').append(encode(value)).append('|'); }
    static Map<String, String> decode(String encoded)
    {
        if (encoded == null || !encoded.startsWith("RWP1|") || encoded.length() > 8192)
            throw new IllegalArgumentException("Invalid layout positions");
        Map<String, String> values = new LinkedHashMap<>();
        for (String record : encoded.substring(5).split("\\|"))
        {
            if (record.isEmpty()) continue;
            String[] pair = record.split("=", -1), key = pair[0].split(":", -1);
            if (pair.length != 2 || key.length != 2 || !HudPreferences.known(key[0]) || !key[1].matches("[0-4]"))
                throw new IllegalArgumentException("Invalid position widget");
            String value = new String(Base64.getUrlDecoder().decode(pair[1]), StandardCharsets.UTF_8);
            if (value.length() > 100 || !value.matches("[A-Za-z0-9_\\-:, .{}\"xy]+") || values.put(pair[0], value) != null)
                throw new IllegalArgumentException("Invalid position value");
            if ("0".equals(key[1])) point(value);
            if ("1".equals(key[1])) position(value);
        }
        return values;
    }
    interface Storage
    {
        void reset(Overlay overlay);
        void save(Overlay overlay);
    }
    static void apply(String encoded, ConfigManager unused, OverlayManager overlays, Map<String, Overlay> owned)
    {
        apply(encoded, new Storage()
        {
            public void reset(Overlay overlay) { overlays.resetOverlay(overlay); }
            public void save(Overlay overlay) { overlays.saveOverlay(overlay); }
        }, owned);
    }
    static void apply(String encoded, Storage storage, Map<String, Overlay> owned)
    {
        Map<String, String> values = decode(encoded);
        owned.forEach((id, overlay) ->
        {
            if (overlay == null || !HudPreferences.known(id)) return;
            Dimension size = overlay.getPreferredSize();
            // Reset through the public manager also resets native private origin state.
            // Never write or unset RuneLite's configuration group ourselves.
            storage.reset(overlay);
            overlay.setPreferredSize(size);
            String snapped = values.get(id + ":1"), location = values.get(id + ":0");
            overlay.setPreferredPosition(snapped == null ? null : position(snapped));
            // Old layout codes with non-default native anchors cannot be represented
            // by public setters. Keep any snap setting; let RuneLite place that card.
            boolean absolute = defaultValue(values, id + ":2", "MANUAL", "AUTO")
                && defaultValue(values, id + ":3", "LEFT") && defaultValue(values, id + ":4", "TOP");
            overlay.setPreferredLocation(location == null || !absolute ? null : point(location));
            if (overlay.getPreferredLocation() != null) overlay.getBounds().setLocation(overlay.getPreferredLocation());
            storage.save(overlay);
            overlay.revalidate();
        });
    }
    private static boolean defaultValue(Map<String, String> values, String key, String... permitted)
    {
        String value = values.get(key);
        if (value == null) return true;
        for (String candidate : permitted) if (candidate.equals(value)) return true;
        return false;
    }
    private static Point point(String value)
    {
        if (!value.matches("-?[0-9]{1,6}:-?[0-9]{1,6}")) throw new IllegalArgumentException("Invalid HUD coordinates");
        String[] parts = value.split(":");
        int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]);
        if (Math.abs(x) > 100000 || Math.abs(y) > 100000) throw new IllegalArgumentException("Invalid HUD coordinates");
        return new Point(x, y);
    }
    private static OverlayPosition position(String value)
    {
        OverlayPosition position;
        try { position = OverlayPosition.valueOf(value); }
        catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Invalid HUD snap position"); }
        if (position == OverlayPosition.DYNAMIC || position == OverlayPosition.TOOLTIP || position == OverlayPosition.DETACHED)
            throw new IllegalArgumentException("Invalid HUD snap position");
        return position;
    }
    static String share(String style, String positions)
    {
        HudPreferences.decode(style); decode(positions);
        return "RUNEWARDEN-LAYOUT:" + encode(style) + '.' + encode(positions);
    }
    static String[] unshare(String text)
    {
        if (text == null || text.length() > 24000 || !text.startsWith("RUNEWARDEN-LAYOUT:"))
            throw new IllegalArgumentException("Paste a Runewarden layout code");
        String[] pieces = text.substring(18).split("\\.", -1);
        if (pieces.length != 2) throw new IllegalArgumentException("Invalid layout code");
        String style = new String(Base64.getUrlDecoder().decode(pieces[0]), StandardCharsets.UTF_8);
        String positions = new String(Base64.getUrlDecoder().decode(pieces[1]), StandardCharsets.UTF_8);
        HudPreferences.decode(style); decode(positions);
        return new String[] {style, positions};
    }
    private static String encode(String text)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
