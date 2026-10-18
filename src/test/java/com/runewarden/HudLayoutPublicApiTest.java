/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the public-state contract without mocking or exposing native origin internals. */
public class HudLayoutPublicApiTest
{
    private static final class Card extends Overlay
    { @Override public Dimension render(Graphics2D graphics) { return null; } }
    private static final class Storage implements HudLayoutPositions.Storage
    {
        final List<Overlay> reset = new ArrayList<>(), saved = new ArrayList<>();
        public void reset(Overlay overlay)
        {
            reset.add(overlay); overlay.setPreferredLocation(null);
            overlay.setPreferredPosition(null); overlay.setPreferredSize(null);
        }
        public void save(Overlay overlay) { saved.add(overlay); }
    }
    private static Map<String, Overlay> owned(Overlay overlay)
    { Map<String, Overlay> result = new LinkedHashMap<>(); result.put("inventory", overlay); return result; }
    private static String field(int id, String value)
    { return "inventory:" + id + '=' + Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + '|'; }

    @Test public void captureUsesPublicRenderedCanvasBoundsWithoutNativeConfiguration()
    {
        Card card = new Card(); card.setPreferredLocation(new Point(-25, -40));
        card.getBounds().setBounds(900, 600, 120, 200);
        Map<String, String> captured = HudLayoutPositions.decode(HudLayoutPositions.capture(null, owned(card)));
        assertEquals("900:600", captured.get("inventory:0"));
        assertEquals("MANUAL", captured.get("inventory:2"));
        assertEquals("LEFT", captured.get("inventory:3"));
        assertEquals("TOP", captured.get("inventory:4"));
        assertEquals(new Point(-25, -40), card.getPreferredLocation());
    }

    @Test public void applyResetsAndSavesOnlyOwnedCardsAndPreservesPublicSize()
    {
        Card card = new Card(), foreign = new Card(); Storage storage = new Storage();
        card.setPreferredSize(new Dimension(160, 90));
        Map<String, Overlay> cards = owned(card); cards.put("foreign-plugin", foreign);
        String code = "RWP1|" + field(0, "123:456") + field(1, "BOTTOM_RIGHT");
        HudLayoutPositions.apply(code, storage, cards);
        assertEquals(new Point(123, 456), card.getPreferredLocation());
        assertEquals(new Point(123, 456), card.getBounds().getLocation());
        assertEquals(new Dimension(160, 90), card.getPreferredSize());
        assertEquals(OverlayPosition.BOTTOM_RIGHT, card.getPreferredPosition());
        assertEquals(java.util.Collections.singletonList(card), storage.reset);
        assertEquals(java.util.Collections.singletonList(card), storage.saved);
        assertNull(foreign.getPreferredLocation());
    }

    @Test public void restoredPositionIsStableBeforeTheFirstRender()
    {
        Card card = new Card(); card.setPreferredLocation(new Point(20, 30));
        card.getBounds().setBounds(20, 30, 100, 100);
        HudLayoutPositions.apply("RWP1|" + field(0, "456:321"), new Storage(), owned(card));
        assertEquals("456:321", HudLayoutPositions.decode(HudLayoutPositions.capture(null, owned(card))).get("inventory:0"));
    }

    @Test public void malformedCoordinatesAndSnapCodesFailBeforeAnyMutation()
    {
        for (String record : new String[] { field(0, "1:2:3"), field(0, "100001:0"), field(0, "{\"x\":1}"),
            field(1, "OTHER"), field(1, "DYNAMIC"), field(1, "TOOLTIP") })
        {
            Card card = new Card(); Storage storage = new Storage(); card.setPreferredLocation(new Point(50, 60));
            try { HudLayoutPositions.apply("RWP1|" + record, storage, owned(card)); fail(record); }
            catch (IllegalArgumentException expected) { }
            assertTrue(storage.reset.isEmpty()); assertTrue(storage.saved.isEmpty());
            assertEquals(new Point(50, 60), card.getPreferredLocation());
        }
    }

    @Test public void legacyPrivateAnchorsResetFreePositionAndRetainAvailableSnap()
    {
        Card card = new Card(); Storage storage = new Storage();
        String code = "RWP1|" + field(0, "-20:-40") + field(1, "TOP_RIGHT")
            + field(2, "MANUAL") + field(3, "RIGHT") + field(4, "BOTTOM");
        HudLayoutPositions.apply(code, storage, owned(card));
        assertNull(card.getPreferredLocation()); assertEquals(OverlayPosition.TOP_RIGHT, card.getPreferredPosition());
        assertEquals(1, storage.saved.size());
    }

    @Test public void emptyCharacterLayoutClearsOnlyOwnedPositions()
    {
        Card card = new Card(); card.setPreferredLocation(new Point(10, 20));
        card.setPreferredPosition(OverlayPosition.BOTTOM_LEFT);
        Storage storage = new Storage(); HudLayoutPositions.apply("RWP1|", storage, owned(card));
        assertNull(card.getPreferredLocation()); assertNull(card.getPreferredPosition());
        assertEquals(1, storage.saved.size());
    }

    @Test public void disabledNativeTimersRequireTheUserToEnableTheirOwnPlugin()
    {
        HarvestClockTool.Reading available = HarvestClockTool.Reading.disabled(true);
        assertFalse(available.active); assertFalse(available.canEnable);
        assertEquals("Enable Time Tracking in RuneLite's plugin settings.", available.message);
        HarvestClockTool.Reading unavailable = HarvestClockTool.Reading.disabled(false);
        assertFalse(unavailable.canEnable); assertEquals("Time Tracking is unavailable.", unavailable.message);
    }
}
