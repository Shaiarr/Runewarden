/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideCoachOverlayTest
{
    @Test public void measuredWrappingFitsWideGlyphsLongWordsAndTruncates()
    {
        Graphics2D g = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            g.setFont(HudStyle.body(12));
            FontMetrics metrics = g.getFontMetrics();
            assertEquals("Short cue", GuideCoachOverlay.wrap(metrics, "Short cue", 240, 1).get(0));
            String longText = "Move to the next numbered tile when the visible wave reaches the blue reference. Then click the tail and wait for the next cue. W".repeat(4);
            List<String> lines = GuideCoachOverlay.wrap(metrics, longText, 240, 3);
            assertEquals(3, lines.size());
            assertTrue(lines.get(2).endsWith("…"));
            for (String line : lines) assertTrue(metrics.stringWidth(line) <= 240);
            for (String line : GuideCoachOverlay.wrap(metrics, "W".repeat(120), 240, 3))
                assertTrue(metrics.stringWidth(line) <= 240);
            assertTrue(GuideCoachOverlay.wrap(metrics, null, 240, 7).isEmpty());
        }
        finally { g.dispose(); }
    }

    @Test public void stepProgressShowsConfirmedCompletionAndDoesNotTreatLastStepNavigationAsFinished() throws Exception
    {
        Client client=mock(Client.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        GuideCoachOverlay overlay=new GuideCoachOverlay(client);
        BufferedImage image=new BufferedImage(300,300,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try
        {
            overlay.select("Giants' Foundry","Current task","Work at the trip hammer.",2,8,2);
            Dimension bounds=overlay.render(graphics);assertNotNull(bounds);
            int filled=image.getRGB(25,bounds.height-35),empty=image.getRGB(220,bounds.height-35);
            assertNotEquals("A visible filled segment must differ from the remaining track",filled,empty);
            for(int y=bounds.height-38;y<bounds.height-32;y++)assertEquals("The bar must have readable thickness",filled,image.getRGB(25,y));
            overlay.select("Giants' Foundry","Current task","Work at the trip hammer.",7,8,7);overlay.render(graphics);
            assertEquals("Confirmed completions must advance the actual drawn bar",image.getRGB(25,bounds.height-35),image.getRGB(200,bounds.height-35));
            overlay.select("Giants' Foundry","Reference","Last step review",8,8,0);overlay.render(graphics);
            assertEquals("Navigating to the last step is not completion evidence",image.getRGB(25,bounds.height-35),image.getRGB(220,bounds.height-35));
            File output=new File("build/reports/guide-step-progress.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
            overlay.clear();assertNull(overlay.render(graphics));
        }
        finally{graphics.dispose();}
    }

    @Test public void coachHidesWhenInactiveAndCompactCardIsSmaller() throws Exception
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        RunewardenConfig config = mock(RunewardenConfig.class);
        when(config.guideHud()).thenReturn(true);
        when(config.hudCompact()).thenReturn(true);
        when(config.hudOpacity()).thenReturn(85);
        GuideCoachOverlay overlay = new GuideCoachOverlay(client, config);
        BufferedImage image = new BufferedImage(528, 540, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.scale(2, 2);
            assertNull(overlay.render(g));
            overlay.select("Fight Caves", "Wave 31 · reset at the pillar", "Move to the numbered tile. Wait behind the pillar and separate the monsters before stepping out. Keep your escape tile clear. ".repeat(5), 31, 63);
            Dimension compact = overlay.render(g);
            assertNotNull(compact);
            assertEquals(264, compact.width);
            File output = new File("build/reports/guide-coach-hud.png");
            output.getParentFile().mkdirs();
            ImageIO.write(image, "png", output);
            when(config.hudCompact()).thenReturn(false);
            assertTrue(overlay.render(g).height > compact.height);
            when(config.guideHud()).thenReturn(false);
            assertNull(overlay.render(g));
            when(config.guideHud()).thenReturn(true);
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            assertNull(overlay.render(g));
            overlay.clear();
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            assertNull(overlay.render(g));
            assertTrue(overlay.isMovable());
        }
        finally { g.dispose(); }
    }
}
