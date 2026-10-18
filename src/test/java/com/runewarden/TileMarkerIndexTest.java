/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileMarkerIndexTest
{
    @Test public void everyBundledPackOccursExactlyOnceInAnAccurateCategory()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        TileMarkerIndex index = new TileMarkerIndex(catalog);
        Set<String> seen = new HashSet<>();
        for (TileMarkerIndex.Category category : index.categories())
            for (TileMarkerIndex.Group group : index.groups(category))
                for (GuideCatalog.MarkerPack pack : group.packs) assertTrue("Duplicate " + pack.id, seen.add(pack.id));
        assertEquals(1008, seen.size());
        assertEquals(catalog.markerPacks().size(), seen.size());
        assertEquals(30, count(index, TileMarkerIndex.Category.BOSSES));
        assertEquals(14, count(index, TileMarkerIndex.Category.RAIDS));
        assertEquals(953, count(index, TileMarkerIndex.Category.WAVES));
        assertEquals(6, count(index, TileMarkerIndex.Category.MINIGAMES));
        assertEquals(3, count(index, TileMarkerIndex.Category.SKILLING));
        assertEquals(2, count(index, TileMarkerIndex.Category.COMBAT_TRAINING));
        assertFalse(index.categories().contains(TileMarkerIndex.Category.OTHER));
    }

    @Test public void fightCavesIsFifteenNumericRotationsOfSixtyThreeNumericWaves()
    {
        TileMarkerIndex index = new TileMarkerIndex(new GuideCatalog(new Gson()));
        List<TileMarkerIndex.Group> rotations = new ArrayList<>();
        for (TileMarkerIndex.Group group : index.groups(TileMarkerIndex.Category.WAVES))
            if (group.id.startsWith("fight-caves-r")) rotations.add(group);
        assertEquals(15, rotations.size());
        for (int r = 1; r <= 15; r++)
        {
            TileMarkerIndex.Group group = rotations.get(r - 1);
            assertEquals("fight-caves-r" + r, group.id);
            assertEquals(63, group.packs.size());
            for (int w = 1; w <= 63; w++)
            {
                assertEquals("fight-caves-r" + r + "-w" + w, group.packs.get(w - 1).id);
                assertEquals("Wave " + w, TileMarkerIndex.label(group.packs.get(w - 1)));
            }
        }
        assertEquals(6, group(index, TileMarkerIndex.Category.WAVES, "inferno").packs.size());
        assertEquals(2, group(index, TileMarkerIndex.Category.WAVES, "colosseum").packs.size());
    }

    @Test public void raidActivitiesAndBossVariantsAreGroupedTogether()
    {
        TileMarkerIndex index = new TileMarkerIndex(new GuideCatalog(new Gson()));
        assertEquals(3, index.groups(TileMarkerIndex.Category.RAIDS).size());
        assertEquals(3, group(index, TileMarkerIndex.Category.RAIDS, "chambers-of-xeric").packs.size());
        assertEquals(7, group(index, TileMarkerIndex.Category.RAIDS, "theatre-of-blood").packs.size());
        assertEquals(4, group(index, TileMarkerIndex.Category.RAIDS, "tombs-of-amascut").packs.size());
        assertEquals(4, group(index, TileMarkerIndex.Category.BOSSES, "general graardor").packs.size());
        assertEquals(4, group(index, TileMarkerIndex.Category.BOSSES, "yama").packs.size());
        assertEquals(2, group(index, TileMarkerIndex.Category.MINIGAMES, "tithe-farm").packs.size());
        assertEquals("Hueycoatl", group(index, TileMarkerIndex.Category.BOSSES, "hueycoatl").title);
    }

    @Test public void labelsDistinguishMethodsThatShareTheSameSourceTitle()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        assertEquals("General Graardor · 7-0 Bowfa", TileMarkerIndex.label(catalog.pack("general graardor (7-0 Bowfa)")));
        assertEquals("Yama · simplefly", TileMarkerIndex.label(catalog.pack("yama (simplefly)")));
        assertEquals("Commander Zilyana · energy-efficient", TileMarkerIndex.label(catalog.pack("commander zilyana (energy-efficient)")));
        assertEquals("Tithe Farm (20x5)", TileMarkerIndex.label(catalog.pack("tithe farm 20x5")));
    }

    @Test public void unfamiliarPackIsRetainedInOtherAndIndexListsCannotBeMutated()
    {
        GuideCatalog.MarkerPack pack = new GuideCatalog.MarkerPack();
        pack.id = "a-future-pack"; pack.title = "A future pack"; pack.tiles = Collections.emptyList();
        List<GuideCatalog.MarkerPack> input = new ArrayList<>();
        input.add(pack);
        TileMarkerIndex index = new TileMarkerIndex(input);
        input.clear();
        assertEquals(Collections.singletonList(TileMarkerIndex.Category.OTHER), index.categories());
        List<TileMarkerIndex.Group> groups = index.groups(TileMarkerIndex.Category.OTHER);
        assertSame(pack, groups.get(0).packs.get(0));
        try { index.categories().clear(); fail("Mutable categories"); } catch (UnsupportedOperationException expected) { }
        try { groups.clear(); fail("Mutable groups"); } catch (UnsupportedOperationException expected) { }
        try { groups.get(0).packs.clear(); fail("Mutable packs"); } catch (UnsupportedOperationException expected) { }
    }

    private static long count(TileMarkerIndex index, TileMarkerIndex.Category category)
    {
        return index.groups(category).stream().mapToLong(group -> group.packs.size()).sum();
    }

    private static TileMarkerIndex.Group group(TileMarkerIndex index, TileMarkerIndex.Category category, String id)
    {
        return index.groups(category).stream().filter(group -> id.equals(group.id)).findFirst().orElseThrow(AssertionError::new);
    }
}
