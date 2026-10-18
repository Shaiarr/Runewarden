/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.game.SpriteManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClickTrainingCueTest
{
    @Test public void cueRequiresAReceivedTickAndExpiresWithoutInventingAnotherTick()
    {
        assertFalse(RunewardenResourceOverlay.clickCueActive(true, 0, 1_000_000_000L));
        assertFalse(RunewardenResourceOverlay.clickCueActive(true, 2_000_000_000L, 1_000_000_000L));
        assertTrue(RunewardenResourceOverlay.clickCueActive(true, 1_000_000_000L, 1_000_000_000L));
        assertTrue(RunewardenResourceOverlay.clickCueActive(true, 1_000_000_000L, 1_149_999_999L));
        assertFalse(RunewardenResourceOverlay.clickCueActive(true, 1_000_000_000L, 1_150_000_000L));
        assertFalse(RunewardenResourceOverlay.clickCueActive(true, 1_000_000_000L, 2_000_000_000L));
        assertFalse(RunewardenResourceOverlay.clickCueActive(false, 1_000_000_000L, 1_000_000_000L));
    }

    @Test public void clickWordFitsTheExistingTickCardAndToggleRestoresTheDot()
    {
        Client client = loggedInClient();
        final boolean[] enabled = {true};
        RunewardenConfig config = new RunewardenConfig() { @Override public boolean clickTrainingCue() { return enabled[0]; } };
        RunewardenResourceOverlay overlay = new RunewardenResourceOverlay(client, config, mock(SpriteManager.class));
        render(overlay, 0); // Warm font rasterisation before measuring the short native heartbeat window.
        BufferedImage cue = render(overlay, System.nanoTime());
        assertEquals(HudStyle.GOLD.getRGB(), cue.getRGB(407, 25));
        int darkText = 0;
        for (int x = 406; x < 452; x++) for (int y = 23; y < 42; y++)
            if (cue.getRGB(x, y) == java.awt.Color.BLACK.getRGB()) darkText++;
        assertTrue("CLICK must have visible dark glyphs inside the gold plate", darkText > 20);
        enabled[0] = false;
        BufferedImage dot = render(overlay, System.nanoTime());
        assertNotEquals(HudStyle.GOLD.getRGB(), dot.getRGB(407, 25));
        assertEquals("Toggle must preserve all card bounds", cue.getWidth(), dot.getWidth());
        HudStyle.context(false, false, 0, 0);
    }

    @Test public void staleHeartbeatAndOtherAppearanceNeverManufactureClickInstructions()
    {
        Client client = loggedInClient();
        RunewardenConfig config = new RunewardenConfig() { @Override public TickDisplay tickDisplayMode() { return TickDisplay.BAR; } };
        RunewardenResourceOverlay overlay = new RunewardenResourceOverlay(client, config, mock(SpriteManager.class));
        BufferedImage stale = render(overlay, System.nanoTime() - 700_000_000L);
        assertNotEquals(HudStyle.GOLD.getRGB(), stale.getRGB(407, 25));
        BufferedImage unknown = render(overlay, 0);
        assertNotEquals(HudStyle.GOLD.getRGB(), unknown.getRGB(407, 25));
        HudStyle.context(false, false, 0, 0);
    }

    private static BufferedImage render(RunewardenResourceOverlay overlay, long stamp)
    {
        BufferedImage image = new BufferedImage(458, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        HudStyle.context(false, false, stamp, 0);
        try { assertEquals(new Dimension(458, 50), overlay.render(g)); }
        finally { g.dispose(); }
        return image;
    }
    private static Client loggedInClient()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(99);
        return client;
    }
}
