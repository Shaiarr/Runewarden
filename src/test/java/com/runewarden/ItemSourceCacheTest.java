/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemSourceCacheTest
{
    private static ItemSourceService.Lookup lookup(int characters)
    {
        ItemSourceService.Lookup value = new ItemSourceService.Lookup(); value.title = "Item";
        value.sources.add(new ItemSourceService.Source("Drop / reward", "Boss", "x".repeat(characters), "https://oldschool.runescape.wiki/w/Boss"));
        return value;
    }

    @Test public void retainedPayloadEvictsLeastRecentlyUsedLookupBeforeMemoryLimit()
    {
        ItemSourceService.Lookup value = lookup(1000);
        long weight = ItemSourceService.LookupCache.estimate("a", value);
        ItemSourceService.LookupCache cache = new ItemSourceService.LookupCache(128, weight * 2);
        cache.put("a", value, 100); cache.put("b", value, 100);
        assertSame(value, cache.get("a", 0));
        cache.put("c", value, 100);
        assertNull(cache.get("b", 0)); assertSame(value, cache.get("a", 0)); assertSame(value, cache.get("c", 0));
        assertTrue(cache.characters() <= weight * 2); assertEquals(2, cache.size());
    }

    @Test public void oversizedCurrentLookupKeepsAllItsSourcesWithoutEnteringCache()
    {
        ItemSourceService.Lookup value = lookup(10_000);
        ItemSourceService.LookupCache cache = new ItemSourceService.LookupCache(128, 1000);
        cache.put("large", value, 100);
        assertNull(cache.get("large", 0)); assertEquals(0, cache.size()); assertEquals(0, cache.characters());
        assertEquals(1, value.sources.size()); assertEquals(10_000, value.sources.get(0).details.length());
    }

    @Test public void countLimitStillAppliesToTinyResponses()
    {
        ItemSourceService.Lookup value = lookup(0);
        ItemSourceService.LookupCache cache = new ItemSourceService.LookupCache(2, 1_000_000);
        cache.put("a", value, 100); cache.put("b", value, 100); cache.put("c", value, 100);
        assertNull(cache.get("a", 0)); assertEquals(2, cache.size());
    }

    @Test public void replacementAndExpiryReleaseTheirPreviouslyAccountedPayload()
    {
        ItemSourceService.Lookup large = lookup(1000), small = lookup(10);
        ItemSourceService.LookupCache cache = new ItemSourceService.LookupCache(128, 1_000_000);
        cache.put("item", large, 100); cache.put("item", small, 100);
        assertEquals(ItemSourceService.LookupCache.estimate("item", small), cache.characters());
        assertNull(cache.get("item", 100)); assertEquals(0, cache.characters()); assertEquals(0, cache.size());
    }
}
