/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardPopupOverlayTest
{
    @Test public void movementFadesAtBothEndsAndReducedMotionRemainsStill()
    {
        RewardPopupOverlay.Motion start = RewardPopupOverlay.motion(0, 6_000_000_000L, true);
        assertEquals(0, start.alpha, .0001); assertEquals(-18, start.offset, .0001);
        RewardPopupOverlay.Motion hold = RewardPopupOverlay.motion(1_000_000_000L, 6_000_000_000L, true);
        assertEquals(1, hold.alpha, .0001); assertEquals(0, hold.offset, .0001);
        RewardPopupOverlay.Motion end = RewardPopupOverlay.motion(5_840_000_000L, 6_000_000_000L, true);
        assertEquals(.5, end.alpha, .0001); assertTrue(end.offset < 0);
        assertTrue(end.remaining < hold.remaining);
        RewardPopupOverlay.Motion still = RewardPopupOverlay.motion(0, 6_000_000_000L, false);
        assertEquals(1, still.alpha, .0001); assertEquals(0, still.offset, .0001);
    }

    @Test public void largeRequestedScaleFitsA500PixelNativeCanvasAndItsHeight()
    {
        double scale = RewardPopupOverlay.fittedScale(500, 330, 222, 200);
        assertTrue(Math.ceil(420 * scale) <= 476);
        assertTrue(Math.ceil((222 + 18) * scale) <= 294);
        double smaller = RewardPopupOverlay.fittedScale(320, 200, 222, 160);
        assertTrue(Math.ceil(420 * smaller) <= 296);
        assertTrue(Math.ceil((222 + 18) * smaller) <= 164);
    }

    @Test public void nativeIconIsRequestedOnceAndKeepsItsUnscaledPixels()
    {
        Client client = client(); ItemManager items = mock(ItemManager.class); AtomicLong now = new AtomicLong();
        AsyncBufferedImage image = new AsyncBufferedImage(mock(ClientThread.class), 36, 32, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.MAGENTA.getRGB()); image.setRGB(35, 31, Color.CYAN.getRGB());
        when(items.getImage(4151, 3, true)).thenReturn(image);
        RewardPopupOverlay overlay = new RewardPopupOverlay(client, items, now::get, false, 6, 100);
        overlay.show(entry(RewardNotification.Kind.DEMO, RarityTier.GOLD, true, 3));
        assertEquals(OverlayPosition.TOP_CENTER, overlay.getPosition());
        assertEquals(OverlayLayer.ABOVE_WIDGETS, overlay.getLayer()); assertTrue(overlay.isMovable());
        BufferedImage result = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try
        {
            for (int frame = 0; frame < 30; frame++) overlay.render(g);
            assertEquals(Color.MAGENTA.getRGB(), result.getRGB(28, 56));
            assertEquals(Color.CYAN.getRGB(), result.getRGB(63, 87));
        }
        finally { g.dispose(); }
        verify(items, times(1)).getImage(4151, 3, true);
    }

    @Test public void verifiedOddsOwnershipAndPersonalLogCountRemainSeparateWrappedLines()
    {
        RewardNotification notification = new RewardNotification(RewardNotification.Kind.COLLECTION_LOG, "Collection log", 
            "A deliberately very long collection log item name that must wrap within the native canvas", 4151, 1,
            RarityTier.GOLD, "Log 123 / 1456 · Rarity based on player ownership", 
            "A long source name with enough words to ensure the source reference wraps safely within the popup",
            "1 / 30 conditional on a raid unique", "0.8% of tracked players", true);
        Graphics2D g = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            RewardPopupOverlay.Layout layout = RewardPopupOverlay.layout(g, notification);
            assertEquals(2, layout.name.size()); assertTrue(layout.metadata.size() <= 7);
            String metadata = String.join(" ", layout.metadata);
            assertTrue(metadata.contains("Drop chance · 1 / 30"));
            assertTrue(metadata.contains("Player ownership · 0.8%"));
            assertTrue(metadata.contains("Log 123 / 1456"));
            for (String line : layout.metadata) assertTrue(g.getFontMetrics().stringWidth(line) <= 303);
            assertTrue(layout.height <= 222);
        }
        finally { g.dispose(); }
    }

    @Test public void missingReferenceFactsDoNotProduceInventedOddsOrOwnership()
    {
        RewardNotification notification = new RewardNotification(RewardNotification.Kind.COLLECTION_LOG,
            "Collection log", "Abyssal whip", 4151, 1, RarityTier.GOLD, "", "", "", "", false);
        Graphics2D g = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertTrue(RewardPopupOverlay.layout(g, notification).metadata.isEmpty()); }
        finally { g.dispose(); }
    }

    @Test public void expiryClearsTheSingleCurrentCardWithoutASeparateTimer()
    {
        Client client = client(); ItemManager items = mock(ItemManager.class); AtomicLong now = new AtomicLong();
        RewardPopupOverlay overlay = new RewardPopupOverlay(client, items, now::get, true, 6, 160);
        overlay.show(entry(RewardNotification.Kind.DEMO, RarityTier.BLUE, true, 1));
        assertTrue(overlay.isActive()); now.set(5_999_999_999L); assertTrue(overlay.isActive());
        now.incrementAndGet(); assertFalse(overlay.isActive());
        Graphics2D g = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertNull(overlay.render(g)); }
        finally { g.dispose(); }
        verify(items, times(1)).getImage(4151, 1, false);
    }

    @Test public void disablingPopupsStopsActiveCardsAndPreventsNewImageRequests()
    {
        Client client = client(); ItemManager items = mock(ItemManager.class);
        RewardSettings settings = new RewardSettings(mock(ConfigManager.class), new Gson());
        RewardPopupOverlay overlay = new RewardPopupOverlay(client, items, settings);
        overlay.show(entry(RewardNotification.Kind.COLLECTION_LOG, RarityTier.GREEN, true, 1)); assertTrue(overlay.isActive());
        settings.update(preferences -> preferences.enabled = false);
        assertFalse(overlay.isActive());
        overlay.show(entry(RewardNotification.Kind.RARE_DROP, RarityTier.PURPLE, true, 1));
        assertFalse(overlay.isActive()); verify(items, times(1)).getImage(4151, 1, false);
        overlay.show(entry(RewardNotification.Kind.DEMO, RarityTier.GOLD, true, 1));
        assertTrue("An explicit audition remains available while normal notifications are disabled", overlay.isActive());
        verify(items, times(2)).getImage(4151, 1, false);
    }

    @Test public void observedRewardClearsAtLogoutWhilePreviewNeedsNoAccount()
    {
        Client client = client(); AtomicLong now = new AtomicLong();
        RewardPopupOverlay overlay = new RewardPopupOverlay(client, mock(ItemManager.class), now::get, false, 6, 100);
        overlay.show(entry(RewardNotification.Kind.COLLECTION_LOG, RarityTier.WHITE, false, 1));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        Graphics2D g = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            assertNull(overlay.render(g)); assertFalse(overlay.isActive());
            overlay.show(entry(RewardNotification.Kind.DEMO, RarityTier.PURPLE, true, 1));
            Dimension preview = overlay.render(g); assertNotNull(preview); assertTrue(preview.width <= 476);
        }
        finally { g.dispose(); }
    }

    private static Client client()
    {
        Client client = mock(Client.class); when(client.getCanvasWidth()).thenReturn(500); when(client.getCanvasHeight()).thenReturn(330);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); return client;
    }
    private static RewardNotification entry(RewardNotification.Kind kind, RarityTier tier, boolean known, int quantity)
    {
        return new RewardNotification(kind, "Collection log", "Abyssal whip", 4151, quantity, tier,
            "Log 123 / 1456", "Abyssal demon", "1 / 512", "0.8% of tracked players", known);
    }
}
