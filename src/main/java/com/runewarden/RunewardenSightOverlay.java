/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.KeyCode;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Current actor visibility, with a mouse tooltip for the actual hovered NPC. */
final class RunewardenSightOverlay extends Overlay
{
    private static final Color GOLD = new Color(234, 201, 124);
    private static final Color BLOCKED = new Color(174, 168, 151);
    private final Client client;
    private final RunewardenSightTool tool;
    private final HoverCardOverlay hoverCards;

    RunewardenSightOverlay(Client client, RunewardenSightTool tool)
    { this(client, tool, new HoverCardOverlay(client)); }

    RunewardenSightOverlay(Client client, RunewardenSightTool tool, HoverCardOverlay hoverCards)
    {
        this.client = client;
        this.tool = tool;
        this.hoverCards = hoverCards;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        if (!tool.overlayVisible() || client.getGameState() != GameState.LOGGED_IN) return null;
        if (tool.isHoverReading() && !tool.hoverActive()) return null;
        RunewardenSightTool.SightReading sight = tool.reading();
        if (sight.target == null || sight.player == null || sight.target.isDead()) return null;
        Point from = sight.player.getCanvasTextLocation(graphics, "", 0);
        Point to = sight.target.getCanvasTextLocation(graphics, "", 0);
        if (tool.isHoverReading()) submitHoverCard(sight);
        if (from == null || to == null) return null;
        Color color = sight.playerToNpc ? GOLD : BLOCKED;
        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();
        try
        {
            Shape outline = sight.target.getConvexHull();
            if (outline != null)
            {
                graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 20));
                graphics.fill(outline);
                graphics.setColor(color);
                graphics.setStroke(new BasicStroke(1.5f));
                graphics.draw(outline);
            }
            graphics.setColor(new Color(0, 0, 0, 185));
            graphics.setStroke(new BasicStroke(4f));
            graphics.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
            graphics.setColor(color);
            graphics.setStroke(sight.playerToNpc ? new BasicStroke(2f) : new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f));
            graphics.drawLine(from.getX(), from.getY(), to.getX(), to.getY());
            graphics.fillOval(from.getX() - 3, from.getY() - 3, 6, 6);
            graphics.fillOval(to.getX() - 3, to.getY() - 3, 6, 6);
            if (tool.isHoverReading()) return null;
            String label = "Your LOS " + (sight.playerToNpc ? "clear" : "blocked") + " | Target LOS " + (sight.npcToPlayer ? "clear" : "blocked");
            FontMetrics metrics = graphics.getFontMetrics();
            int width = metrics.stringWidth(label), x = (from.getX() + to.getX()) / 2 - width / 2, y = (from.getY() + to.getY()) / 2 - 9;
            x = Math.max(4, Math.min(Math.max(4, client.getCanvasWidth() - width - 10), x));
            y = Math.max(metrics.getHeight() + 5, y);
            graphics.setColor(new Color(14, 14, 15, 225));
            graphics.fillRoundRect(x - 5, y - metrics.getAscent() - 4, width + 10, metrics.getHeight() + 7, 7, 7);
            graphics.setColor(color);
            graphics.drawString(label, x, y);
        }
        finally { graphics.setStroke(oldStroke); graphics.setColor(oldColor); }
        return null;
    }
    private void submitHoverCard(RunewardenSightTool.SightReading sight)
    {
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || client.isMenuOpen()) return;
        List<String> content = new ArrayList<>();
        content.add(sight.targetName);
        if (sight.knowledge != null) content.add(sight.knowledge.variant);
        content.add("Player → NPC: " + (sight.playerToNpc ? "Clear" : "Blocked")
            + " · NPC → Player: " + (sight.npcToPlayer ? "Clear" : "Blocked"));
        content.add("Footprint distance: " + sight.distance + " tiles");
        if (sight.knowledge != null)
            content.addAll(client.isKeyPressed(KeyCode.KC_SHIFT) ? sight.knowledge.lines : sight.knowledge.summary);
        hoverCards.submit(content);
    }

}
