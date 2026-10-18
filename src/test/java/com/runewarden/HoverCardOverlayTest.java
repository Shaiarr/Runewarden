/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HoverCardOverlayTest
{
    private Client client;
    private HoverCardOverlay overlay;

    @Before public void prepare()
    {
        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getCanvasWidth()).thenReturn(500); when(client.getCanvasHeight()).thenReturn(360);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(250, 300));
        overlay = new HoverCardOverlay(client);
    }

    private BufferedImage render()
    {
        BufferedImage image = new BufferedImage(500, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try { overlay.render(graphics); } finally { graphics.dispose(); }
        return image;
    }

    @Test public void cardUsesHighestDynamicTopLayerAndFirstFrameCurrentMeasurements()
    {
        assertEquals(OverlayLayer.ALWAYS_ON_TOP, overlay.getLayer());
        assertEquals(OverlayPosition.DYNAMIC, overlay.getPosition());
        assertEquals(Overlay.PRIORITY_HIGHEST, overlay.getPriority(), 0f);
        overlay.submit(Arrays.asList("Adamant dragon", "Attacks: Slash / Ranged / Magic / Dragonfire", "Usual protection: Varies"));
        render(); Rectangle first = overlay.lastBounds();
        assertTrue(first.width > 200); assertTrue(first.height > 40);
        assertTrue(first.y + first.height <= 300 - 12);
        assertWithin(first, 500, 360);
        overlay.submit(Collections.singletonList("Rat")); render();
        assertTrue(overlay.lastBounds().width < first.width);
    }

    @Test public void everyCornerClampsAllEdgesWithoutFlippingTheBoxBelowThePointer()
    {
        for (Point point : Arrays.asList(new Point(0, 0), new Point(499, 0), new Point(0, 359), new Point(499, 359)))
        {
            when(client.getMouseCanvasPosition()).thenReturn(point);
            overlay.submit(Arrays.asList("A long monster name", "Another reference line")); render();
            Rectangle bounds = overlay.lastBounds(); assertWithin(bounds, 500, 360);
            if (point.getY() > bounds.height + 12) assertTrue(bounds.y + bounds.height <= point.getY() - 12);
            else assertEquals(3, bounds.y);
        }
    }

    @Test public void unusuallyLongWordsAndTallCardsStayInsideCurrentCanvas()
    {
        when(client.getCanvasWidth()).thenReturn(170); when(client.getCanvasHeight()).thenReturn(130);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(165, 125));
        overlay.submit(Arrays.asList("Monster", "ExtremelyLongUnbrokenSourceName".repeat(30), "Last row"));
        render(); assertWithin(overlay.lastBounds(), 170, 130);
        assertTrue(overlay.lastBounds().height <= 124);
    }

    @Test public void frameConsumptionMenusLogoutAndPointerExitCannotLeaveAStaleCard()
    {
        overlay.submit(Collections.singletonList("First frame")); render(); assertFalse(overlay.lastBounds().isEmpty());
        render(); assertTrue(overlay.lastBounds().isEmpty());
        overlay.submit(Collections.singletonList("Menu")); when(client.isMenuOpen()).thenReturn(true);
        render(); assertTrue(overlay.lastBounds().isEmpty());
        when(client.isMenuOpen()).thenReturn(false); render(); assertTrue(overlay.lastBounds().isEmpty());
        overlay.submit(Collections.singletonList("Logout")); when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        render(); assertTrue(overlay.lastBounds().isEmpty());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getMouseCanvasPosition()).thenReturn(new Point(-1, 0));
        overlay.submit(Collections.singletonList("Outside")); render(); assertTrue(overlay.lastBounds().isEmpty());
    }

    @Test public void resourceRequirementColoursSurviveHtmlToTopCardConversion()
    {
        overlay.submitHtml("<col=e0be71>Logs</col><br><col=ff9a86>Requires level 80</col>");
        BufferedImage image = render();
        int redPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            if ((image.getRGB(x, y) & 0xffffff) == 0xff9a86) redPixels++;
        assertTrue("Required-level warning should remain visibly red", redPixels > 10);
    }

    private static void assertWithin(Rectangle rectangle, int width, int height)
    {
        assertFalse(rectangle.isEmpty());
        assertTrue(rectangle.x >= 3); assertTrue(rectangle.y >= 3);
        assertTrue(rectangle.x + rectangle.width <= width - 3);
        assertTrue(rectangle.y + rectangle.height <= height - 3);
    }
}
