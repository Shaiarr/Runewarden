/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class RarityCatalogTest
{
    private final RarityCatalog catalog = new RarityCatalog(new Gson());

    @Test public void exactNpcSourceHasIndependentDropOddsAndHistoricalOwnership()
    {
        RarityCatalog.Result result = catalog.classify(12653, "King Black Dragon");
        assertTrue(result.exactOdds); assertEquals(3000.0, result.denominator, 0);
        assertEquals("1/3,000", result.oddsText);
        assertNotNull(result.ownershipPercent); assertTrue(result.ownershipPercent >= 0 && result.ownershipPercent <= 100);
        assertTrue(result.ownershipText.contains("WikiSync collection-log accounts"));
        assertTrue(result.ownershipText.contains("snapshot 2026-09-26"));
        assertTrue(result.wiki.endsWith("id=12653"));
    }
    @Test public void raidPurpleTableIsNeverPresentedAsTheChancePerCompletedRaid()
    {
        RarityCatalog.Result result = catalog.classify(20997, "Chambers of Xeric");
        assertFalse(result.exactOdds); assertNull(result.denominator);
        assertTrue(result.oddsText.startsWith("Purple-table chance"));
        assertTrue(result.basis.contains("raid points"));
        assertEquals(3.9, result.ownershipPercent, .001);
        assertNotEquals(1.0 / result.ownershipPercent, 30.0, .01);
    }
    @Test public void scalingRangeAndComponentProgressionRemainUnknownForPerKillColouring()
    {
        int orb = catalog.itemId("Harmonised orb"); assertTrue(orb > 0);
        RarityCatalog.Result nightmare = catalog.classify(orb, "The Nightmare");
        assertFalse(nightmare.exactOdds); assertNull(nightmare.denominator); assertTrue(nightmare.oddsText.contains("range"));
        RarityCatalog.Result component = catalog.classify(22973, "Alchemical Hydra");
        assertFalse(component.exactOdds); assertNull(component.denominator); assertTrue(component.basis.contains("progression"));
    }
    @Test public void unknownSourceCannotBorrowTheOnlyKnownSourceOrUseOwnershipAsDropChance()
    {
        RarityCatalog.Result wrong = catalog.classify(12653, "Giant Mole");
        assertFalse(wrong.exactOdds); assertNull(wrong.denominator); assertEquals("", wrong.oddsText);
        assertNotNull(wrong.ownershipPercent);
        RarityCatalog.Result unknown = catalog.classify(99999999, "King Black Dragon");
        assertFalse(unknown.exactOdds); assertNull(unknown.ownershipPercent); assertEquals("", unknown.ownershipText);
    }
    @Test public void multipleSourcesNeedAnActualSourceAndFloatingPointNoiseDoesNotShiftThresholds()
    {
        assertFalse(catalog.forItem(11286).exactOdds);
        assertNull(catalog.forItem(11286).denominator);
        RarityCatalog.Result visage = catalog.classify(11286, "King Black Dragon");
        assertTrue(visage.exactOdds); assertEquals(5000.0, visage.denominator, 0);
        RarityCatalog.Result pickaxe = catalog.classify(11920, "King Black Dragon");
        assertTrue(pickaxe.exactOdds); assertEquals(1000.0, pickaxe.denominator, 0);
    }
    @Test public void staticNameResolutionSupportsRealChatNamesWithoutFetchingDefinitions()
    {
        assertEquals(20997, catalog.itemId("Twisted bow"));
        assertEquals(27277, catalog.itemId("Tumeken's shadow"));
        assertEquals("prince black dragon", catalog.itemName(12653).toLowerCase());
        assertEquals(-1, catalog.itemId("Invented item"));
        assertTrue(catalog.size() >= 1700);
    }
    @Test public void skillingPetsDoNotClaimOneFixedChanceWithoutTheRequiredActivityAndLevel()
    {
        RarityCatalog.Result rocky = catalog.classify(20663, "Rogue");
        assertFalse(rocky.exactOdds); assertNull(rocky.denominator);
        assertTrue(rocky.basis.contains("skill level"));
        assertNotNull(rocky.ownershipPercent);
    }
    @Test public void unobservedSlayerTaskAndRareDropTableLuckDoNotClaimAnExactChance()
    {
        RarityCatalog.Result jaw = catalog.classify(24268, "Basilisk Knight");
        assertFalse(jaw.exactOdds); assertNull(jaw.denominator); assertTrue(jaw.basis.contains("task"));
        RarityCatalog.Result spear = catalog.classify(1249, "King Black Dragon");
        assertFalse(spear.exactOdds); assertNull(spear.denominator); assertTrue(spear.basis.contains("luck"));
    }

}
