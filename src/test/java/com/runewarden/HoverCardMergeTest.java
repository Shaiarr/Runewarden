package com.runewarden;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HoverCardMergeTest
{
    @Test public void matchingItemFactsAndEffectsMergeInEitherSubmissionOrder()
    {
        HoverCardOverlay card = card();
        card.submitItemHtml(385, "Shark<br>Cooking XP 210"); card.submitItemSupplement(385, List.of("Shark · per use", "Hitpoints +5 · wasted 15"));
        int first = draw(card); assertTrue(first > 70);
        card.submitItemSupplement(385, List.of("Shark · per use", "Hitpoints +5 · wasted 15")); card.submitItemHtml(385, "Shark<br>Cooking XP 210");
        assertEquals(first, draw(card));
    }
    @Test public void unrelatedSceneOrItemCardCannotContaminateNativeItemPreview()
    {
        HoverCardOverlay card = card();
        card.submitItemHtml(995, "Coins<br>Different item"); card.submitItemSupplement(385, List.of("Shark · per use", "Hitpoints +5"));
        int ownHeight = draw(card);
        card.submit(List.of("A mob", "Unrelated scene information")); card.submitItemSupplement(385, List.of("Shark · per use", "Hitpoints +5"));
        assertEquals(ownHeight, draw(card));
        card.submitItemHtml(385, "Shark<br>Cooking XP 210"); card.submitItemSupplement(385, List.of("Shark · per use", "Hitpoints +5"));
        assertTrue(draw(card) > ownHeight);
    }
    @Test public void consumedFrameAndClearRemoveBothSources()
    {
        HoverCardOverlay card = card(); card.submitItemSupplement(385, List.of("Shark", "Hitpoints +5"));
        assertTrue(draw(card) > 0); assertEquals(0, draw(card));
        card.submitItemHtml(385, "Shark"); card.submitItemSupplement(385, List.of("Hitpoints +5")); card.clear(); assertEquals(0, draw(card));
    }
    private static HoverCardOverlay card()
    {
        Client client = mock(Client.class); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(120, 300)); when(client.getCanvasWidth()).thenReturn(500); when(client.getCanvasHeight()).thenReturn(330);
        return new HoverCardOverlay(client);
    }
    private static int draw(HoverCardOverlay card)
    {
        Graphics2D graphics = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { card.render(graphics); return card.lastBounds().height; } finally { graphics.dispose(); }
    }
}
