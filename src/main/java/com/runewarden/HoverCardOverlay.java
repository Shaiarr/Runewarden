/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A current-frame card shared by scene and widget hovers, painted above all game elements. */
@Singleton
final class HoverCardOverlay extends Overlay
{
    private static final Color GOLD = new Color(234, 201, 124), WHITE = new Color(240, 237, 228);
    private static final Pattern COLOUR = Pattern.compile("<col=([0-9a-fA-F]{6})>");
    private static final int MARGIN = 3, PADDING = 9, POINTER_GAP = 12;
    private final Client client;
    private List<Line> pending = Collections.emptyList();
    private List<Line> supplement = Collections.emptyList();
    private int pendingItemId = -1, supplementItemId = -1;
    private Rectangle lastBounds = new Rectangle();

    @Inject HoverCardOverlay(Client client)
    {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
    }

    void submit(List<String> lines)
    {
        List<Line> content = new ArrayList<>();
        for (int i = 0; i < Math.min(40, lines.size()); i++)
            if (lines.get(i) != null) content.add(new Line(lines.get(i), i == 0 ? GOLD : WHITE));
        pending = content; pendingItemId = -1;
    }

    void submitHtml(String html)
    {
        if (html == null) return;
        List<Line> content = new ArrayList<>();
        for (String row : html.split("(?i)<br\\s*/?>"))
        {
            if (content.size() >= 40) break;
            Matcher colour = COLOUR.matcher(row);
            Color ink = colour.find() ? new Color(Integer.parseInt(colour.group(1), 16)) : WHITE;
            String text = row.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&nbsp;", " ").replace("&#39;", "'").replace("&quot;", "\"");
            content.add(new Line(text, ink));
        }
        pending = content; pendingItemId = -1;
    }

    /** Item facts and native effects can arrive in either overlay order in the same frame. */
    void submitItemHtml(int itemId, String html)
    { submitHtml(html); pendingItemId = itemId; }

    void submitItemSupplement(int itemId, List<String> lines)
    {
        List<Line> content = new ArrayList<>();
        for (int i = 0; i < Math.min(18, lines.size()); i++)
            if (lines.get(i) != null) content.add(new Line(lines.get(i), i == 0 ? GOLD : WHITE));
        supplement = content; supplementItemId = itemId;
    }

    void clear()
    { pending = Collections.emptyList(); supplement = Collections.emptyList(); pendingItemId = -1; supplementItemId = -1; lastBounds = new Rectangle(); }
    Rectangle lastBounds() { return new Rectangle(lastBounds); }

    @Override public Dimension render(Graphics2D original)
    {
        List<Line> content = pending;
        if (!supplement.isEmpty())
        {
            // The hovered native item takes precedence over an unrelated scene card.
            if (pendingItemId == supplementItemId)
            { content = new ArrayList<>(pending); content.addAll(supplement); }
            else content = supplement;
        }
        pending = Collections.emptyList();
        supplement = Collections.emptyList(); pendingItemId = -1; supplementItemId = -1;
        lastBounds = new Rectangle();
        Point mouse = client.getMouseCanvasPosition();
        int canvasWidth = client.getCanvasWidth(), canvasHeight = client.getCanvasHeight();
        if (content.isEmpty() || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()
            || mouse == null || mouse.getX() < 0 || mouse.getY() < 0 || mouse.getX() >= canvasWidth || mouse.getY() >= canvasHeight
            || canvasWidth < 2 * (MARGIN + PADDING) + 1 || canvasHeight < 2 * (MARGIN + PADDING) + 1) return null;
        Graphics2D graphics = (Graphics2D) original.create();
        try
        {
            graphics.setFont(GuideText.FONT);
            FontMetrics metrics = graphics.getFontMetrics();
            if (canvasHeight - MARGIN * 2 < metrics.getHeight() + PADDING * 2) return null;
            int textLimit = Math.max(1, Math.min(440, canvasWidth - MARGIN * 2) - PADDING * 2);
            List<Line> lines = new ArrayList<>();
            for (Line line : content) wrap(lines, line, metrics, textLimit);
            int maxRows = Math.max(1, (canvasHeight - MARGIN * 2 - PADDING * 2) / metrics.getHeight());
            if (lines.size() > maxRows)
            {
                lines = new ArrayList<>(lines.subList(0, maxRows));
                lines.set(maxRows - 1, new Line("…", GOLD));
            }
            int width = 1;
            for (Line line : lines) width = Math.max(width, metrics.stringWidth(line.text));
            width += PADDING * 2;
            int height = metrics.getHeight() * lines.size() + PADDING * 2;
            Rectangle bounds = place(canvasWidth, canvasHeight, mouse, width, height);
            lastBounds = bounds;
            graphics.clip(bounds);
            graphics.setColor(new Color(13, 13, 15, 245));
            graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            graphics.setColor(new Color(161, 131, 67, 240));
            graphics.drawRoundRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1, 8, 8);
            int baseline = bounds.y + PADDING + metrics.getAscent();
            for (Line line : lines)
            {
                graphics.setColor(line.colour);
                graphics.drawString(line.text, bounds.x + PADDING, baseline);
                baseline += metrics.getHeight();
            }
        }
        finally { graphics.dispose(); }
        return null;
    }

    /** Geometry is based on this frame's measured content, including its first appearance. */
    static Rectangle place(int canvasWidth, int canvasHeight, Point mouse, int measuredWidth, int measuredHeight)
    {
        int width = Math.min(Math.max(1, measuredWidth), Math.max(1, canvasWidth - MARGIN * 2));
        int height = Math.min(Math.max(1, measuredHeight), Math.max(1, canvasHeight - MARGIN * 2));
        int x = clamp(mouse.getX() + POINTER_GAP, MARGIN, canvasWidth - width - MARGIN);
        int y = clamp(mouse.getY() - height - POINTER_GAP, MARGIN, canvasHeight - height - MARGIN);
        return new Rectangle(x, y, width, height);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }

    private static void wrap(List<Line> result, Line source, FontMetrics metrics, int width)
    {
        StringBuilder row = new StringBuilder();
        for (String word : source.text.split("\\s+"))
        {
            if (row.length() > 0 && metrics.stringWidth(row + " " + word) > width)
            {
                result.add(new Line(row.toString(), source.colour)); row.setLength(0);
            }
            String rest = word;
            while (!rest.isEmpty() && metrics.stringWidth(rest) > width)
            {
                int end = 1;
                while (end < rest.length() && metrics.stringWidth(rest.substring(0, end + 1)) <= width) end++;
                result.add(new Line(rest.substring(0, end), source.colour)); rest = rest.substring(end);
            }
            if (!rest.isEmpty()) { if (row.length() > 0) row.append(' '); row.append(rest); }
        }
        if (row.length() > 0 || source.text.isEmpty()) result.add(new Line(row.toString(), source.colour));
    }

    private static final class Line
    {
        final String text;
        final Color colour;
        Line(String text, Color colour) { this.text = text; this.colour = colour; }
    }
}
