/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.Overlay;

/** CTRL and a card edge resize only Runewarden presentation, without native game actions. */
@Singleton
final class HudResizeController implements MouseListener, KeyListener
{
    private static final int EDGE = 8;
    private static final long SCENE_FRESH_NANOS = 500_000_000L;
    private final Client client;
    private final RunewardenConfig config;
    private final ConfigManager manager;
    private final MouseManager mouse;
    private final KeyManager keys;
    private final ClientUI ui;
    private final LongSupplier clock;
    private final Map<String, Overlay> widgets = new LinkedHashMap<>();
    private final Map<String, SceneRegions> scene = new LinkedHashMap<>();
    private boolean started, suppressClick, cursorOwned;
    private Drag drag;
    private static final class SceneRegions
    {
        final long rendered;
        final List<Rectangle> regions = new ArrayList<>();
        SceneRegions(long rendered) { this.rendered = rendered; }
    }
    private static final class Hit
    {
        final String widget;
        final Rectangle bounds;
        final int edges;
        Hit(String widget, Rectangle bounds, int edges)
        { this.widget = widget; this.bounds = bounds; this.edges = edges; }
    }
    private static final class Drag
    {
        final Hit hit;
        final Point origin;
        final String profile;
        final HudPreferences.Style style;
        int scale;
        Drag(Hit hit, Point origin, String profile, HudPreferences.Style style)
        { this.hit = hit; this.origin = origin; this.profile = profile; this.style = style; this.scale = style.scale; }
    }

    @Inject HudResizeController(Client client, RunewardenConfig config, ConfigManager manager,
        MouseManager mouse, KeyManager keys, ClientUI ui)
    { this(client, config, manager, mouse, keys, ui, System::nanoTime); }

    HudResizeController(Client client, RunewardenConfig config, ConfigManager manager,
        MouseManager mouse, KeyManager keys, ClientUI ui, LongSupplier clock)
    {
        this.client = client; this.config = config; this.manager = manager;
        this.mouse = mouse; this.keys = keys; this.ui = ui; this.clock = clock;
    }

    synchronized void start(Map<String, Overlay> owned)
    {
        widgets.clear(); widgets.putAll(owned);
        if (started) return;
        scene.clear();
        started = true;
        // Native stretched mode translates at index zero. Receive its logical canvas
        // coordinates, while still consuming resize gestures before the game sees them.
        mouse.registerMouseListener(this);
        keys.registerKeyListener(this);
    }

    synchronized void register(String key, Overlay overlay)
    { if (HudPreferences.known(key) && overlay != null) widgets.put(key, overlay); }

    synchronized void stop()
    {
        cancel(); started = false; scene.clear(); widgets.clear(); suppressClick = false;
        mouse.unregisterMouseListener(this); keys.unregisterKeyListener(this);
    }

    /** A scene overlay starts a new frame, including frames in which nothing is visible. */
    synchronized void beginScene(String key)
    { if (HudPreferences.known(key)) scene.put(key, new SceneRegions(clock.getAsLong())); }

    synchronized void sceneBounds(String key, Rectangle bounds)
    {
        SceneRegions current = scene.get(key);
        if (current != null && bounds != null && !bounds.isEmpty() && current.regions.size() < 64)
            current.regions.add(new Rectangle(bounds));
    }

    synchronized void validateProfile()
    { if (drag != null && !valid(drag)) cancel(); }

    synchronized void cancel()
    {
        if (drag != null) { HudStyle.clearResizePreview(drag.hit.widget); suppressClick = true; }
        drag = null; resetCursor();
    }

    private boolean available()
    { return started && !config.hudLocked() && client.getGameState() == GameState.LOGGED_IN && !client.isMenuOpen(); }

    private boolean valid(Drag value)
    { return available() && Objects.equals(value.profile, manager.getRSProfileKey()); }

    private Hit hit(Point point)
    {
        Hit best = null;
        for (Map.Entry<String, Overlay> entry : widgets.entrySet())
        {
            Overlay overlay = entry.getValue();
            if (overlay != null) best = nearer(best, entry.getKey(), overlay.getBounds(), point);
        }
        long now = clock.getAsLong();
        for (Map.Entry<String, SceneRegions> entry : scene.entrySet())
            if (now - entry.getValue().rendered <= SCENE_FRESH_NANOS)
                for (Rectangle bounds : entry.getValue().regions) best = nearer(best, entry.getKey(), bounds, point);
        return best;
    }

    private static Hit nearer(Hit previous, String widget, Rectangle bounds, Point point)
    {
        if (bounds == null || bounds.isEmpty() || !bounds.contains(point)) return previous;
        Rectangle center = new Rectangle(bounds);
        center.grow(-Math.min(EDGE, Math.max(1, bounds.width / 3)), -Math.min(EDGE, Math.max(1, bounds.height / 3)));
        int edge = center.outcode(point);
        if (edge == 0) return previous;
        return previous == null || (long) bounds.width * bounds.height <= (long) previous.bounds.width * previous.bounds.height
            ? new Hit(widget, new Rectangle(bounds), edge) : previous;
    }

    private void cursor(Hit hit)
    {
        if (hit == null) { resetCursor(); return; }
        int code;
        switch (hit.edges)
        {
            case Rectangle.OUT_TOP: code = Cursor.N_RESIZE_CURSOR; break;
            case Rectangle.OUT_LEFT: code = Cursor.W_RESIZE_CURSOR; break;
            case Rectangle.OUT_RIGHT: code = Cursor.E_RESIZE_CURSOR; break;
            case Rectangle.OUT_BOTTOM: code = Cursor.S_RESIZE_CURSOR; break;
            case Rectangle.OUT_TOP | Rectangle.OUT_LEFT: code = Cursor.NW_RESIZE_CURSOR; break;
            case Rectangle.OUT_TOP | Rectangle.OUT_RIGHT: code = Cursor.NE_RESIZE_CURSOR; break;
            case Rectangle.OUT_BOTTOM | Rectangle.OUT_LEFT: code = Cursor.SW_RESIZE_CURSOR; break;
            default: code = Cursor.SE_RESIZE_CURSOR;
        }
        ui.setCursor(Cursor.getPredefinedCursor(code));
        cursorOwned = true;
    }

    private void resetCursor()
    { if (cursorOwned) { ui.setCursor(ui.getDefaultCursor()); cursorOwned = false; } }
    private void repaint(MouseEvent event) { event.getComponent().repaint(); }

    @Override public synchronized MouseEvent mousePressed(MouseEvent event)
    {
        suppressClick = false;
        if (event.isConsumed() || event.getButton() != MouseEvent.BUTTON1 || !event.isControlDown()
            || event.isAltDown() || !available()) return event;
        Hit selected = hit(event.getPoint());
        if (selected == null) return event;
        drag = new Drag(selected, event.getPoint(), manager.getRSProfileKey(), HudStyle.settings(config, selected.widget));
        cursor(selected); event.consume(); return event;
    }

    @Override public synchronized MouseEvent mouseDragged(MouseEvent event)
    {
        if (drag == null) return event;
        event.consume();
        if (!valid(drag)) { suppressClick = true; cancel(); return event; }
        int dx = event.getX() - drag.origin.x, dy = event.getY() - drag.origin.y;
        double x = 0, y = 0;
        boolean horizontal = (drag.hit.edges & (Rectangle.OUT_LEFT | Rectangle.OUT_RIGHT)) != 0;
        boolean vertical = (drag.hit.edges & (Rectangle.OUT_TOP | Rectangle.OUT_BOTTOM)) != 0;
        if (horizontal) x = ((drag.hit.edges & Rectangle.OUT_LEFT) != 0 ? -dx : dx) / (double) drag.hit.bounds.width;
        if (vertical) y = ((drag.hit.edges & Rectangle.OUT_TOP) != 0 ? -dy : dy) / (double) drag.hit.bounds.height;
        double change = horizontal && vertical ? (Math.abs(x) >= Math.abs(y) ? x : y) : horizontal ? x : y;
        drag.scale = HudPreferences.clamp((int) Math.round(drag.style.scale * (1 + change)), 60, 180);
        HudStyle.previewScale(drag.hit.widget, drag.scale);
        Overlay overlay = widgets.get(drag.hit.widget); if (overlay != null) overlay.revalidate();
        repaint(event); return event;
    }

    @Override public synchronized MouseEvent mouseReleased(MouseEvent event)
    {
        if (drag == null)
        {
            if (suppressClick && event.getButton() == MouseEvent.BUTTON1) event.consume();
            return event;
        }
        if (event.getButton() != MouseEvent.BUTTON1) return event;
        Drag finished = drag;
        try
        {
            if (valid(finished) && finished.scale != finished.style.scale)
            {
                Map<String, HudPreferences.Style> styles = new LinkedHashMap<>(HudPreferences.safeDecode(config.hudWidgetSettings()));
                styles.put(finished.hit.widget, finished.style.withScale(finished.scale));
                String layout = HudPreferences.encode(styles);
                manager.setConfiguration(RunewardenConfig.GROUP, "hudWidgetSettings", layout);
                if (finished.profile != null && finished.profile.equals(manager.getRSProfileKey()))
                    manager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterLayout", layout);
            }
        }
        finally { suppressClick = true; cancel(); event.consume(); repaint(event); }
        return event;
    }

    @Override public synchronized MouseEvent mouseClicked(MouseEvent event)
    {
        if (suppressClick && event.getButton() == MouseEvent.BUTTON1) { event.consume(); suppressClick = false; }
        return event;
    }

    @Override public synchronized MouseEvent mouseMoved(MouseEvent event)
    { if (drag == null) { if (available() && event.isControlDown() && !event.isAltDown()) cursor(hit(event.getPoint())); else resetCursor(); } return event; }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
    @Override public synchronized MouseEvent mouseExited(MouseEvent event) { if (drag == null) resetCursor(); return event; }
    @Override public synchronized void keyPressed(KeyEvent event)
    {
        if (event.getKeyCode() == KeyEvent.VK_CONTROL && available() && !event.isAltDown() && drag == null)
        {
            net.runelite.api.Point point = client.getMouseCanvasPosition();
            if (point != null) cursor(hit(new Point(point.getX(), point.getY())));
        }
    }
    @Override public synchronized void keyReleased(KeyEvent event)
    { if (event.getKeyCode() == KeyEvent.VK_CONTROL && drag == null) resetCursor(); }
    @Override public void keyTyped(KeyEvent event) { }
    @Override public synchronized void focusLost() { suppressClick = drag != null; cancel(); }
}
