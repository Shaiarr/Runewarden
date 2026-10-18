/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideCatalogTest
{
    @Test public void bundledLibraryRetainsQuestRequirementsWithoutAnyQuestWalkthroughs()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        catalog.load();
        assertEquals(238, catalog.search("", "quest").size());
        assertEquals(323, catalog.search("", "training").size() + catalog.search("", "minigame").stream()
            .filter(entry -> entry.id.startsWith("training:")).count());
        assertNotNull(catalog.entry("quest:Cook's Assistant"));
        assertNotNull(catalog.entry("quest:While Guthix Sleeps"));
        assertNotNull(catalog.entry("training:guardians-rift"));
        assertTrue(catalog.search("", "minigame").size() >= 48);
        for (GuideCatalog.Entry entry : catalog.search("", ""))
        {
            if ("quest".equals(entry.category)) { assertTrue(entry.id, entry.flattened().isEmpty()); assertTrue(entry.items.isEmpty()); assertNull(entry.markerPackId); continue; }
            assertFalse("Missing steps for " + entry.id, entry.flattened().isEmpty());
            for (GuideCatalog.DisplayStep step : entry.flattened())
            {
                assertNotNull(entry.id, step.step.id);
                assertNotNull(entry.id, step.step.text);
                assertFalse(entry.id, step.step.text.trim().isEmpty());
                assertNotNull(entry.id, step.group);
            }
        }
    }

    @Test public void importedMarkersKeepValidSourceCoordinatesAndColours()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        assertTrue(catalog.markerPacks().size() >= 56);
        int count = 0;
        for (GuideCatalog.MarkerPack pack : catalog.markerPacks())
        {
            assertNotNull(pack.id, pack.source);
            Set<Integer> numbers = new HashSet<>();
            for (GuideCatalog.Marker marker : pack.tiles)
            {
                assertTrue(pack.id, marker.regionX >= 0 && marker.regionX < 64);
                assertTrue(pack.id, marker.regionY >= 0 && marker.regionY < 64);
                assertTrue(pack.id, marker.z >= 0 && marker.z <= 3);
                assertTrue(pack.id, marker.number > 0 && numbers.add(marker.number));
                assertTrue(pack.id, marker.color.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"));
                count++;
            }
        }
        assertTrue(count >= 1471);
    }

    @Test public void hueyMovementCuesReferToThePublishedTiles()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        GuideCatalog.MarkerPack pack = catalog.pack("hueycoatl-cues");
        assertEquals(11, pack.tiles.size());
        assertEquals(new WorldPoint(1510, 3287, 0), pack.tiles.get(7).point());
        assertEquals(new WorldPoint(1517, 3286, 0), pack.tiles.get(1).point());
        GuideCatalog.Entry guide = catalog.entry("boss:hueycoatl");
        assertEquals(pack.id, guide.markerPackId);
        assertTrue(guide.flattened().stream().anyMatch(step -> step.step.text.contains("blue marker 2") && step.step.text.contains("marker 3")));
        assertTrue(guide.flattened().stream().anyMatch(step -> step.step.markerNumber == 8));
    }

    @Test public void everyManualWaveHasAnExplicitOverlayPack()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        GuideCatalog.Entry inferno = catalog.entry("waves:inferno");
        assertNotNull(inferno);
        assertEquals(69, inferno.sections.size());
        int fightCaves = 0;
        for (GuideCatalog.Entry guide : catalog.search("", "waves"))
        {
            if (guide.id.contains("fight-caves")) { fightCaves++; assertEquals(63, guide.sections.size()); }
            for (GuideCatalog.Section section : guide.sections)
            {
                assertNotNull(guide.id + " " + section.id, section.markerPackId);
                assertNotNull(guide.id + " " + section.id, catalog.pack(section.markerPackId));
                assertFalse(guide.id + " " + section.id, section.steps.isEmpty());
            }
        }
        assertEquals(15, fightCaves);
        GuideCatalog.MarkerPack opening = catalog.pack(inferno.sections.get(0).markerPackId);
        assertEquals(new WorldPoint(2274, 5353, 0), opening.tiles.get(0).point());
    }

    @Test public void fullBossAcademyHasActionablePhasesAndValidMarkerReferences()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        int encounters = 0;
        int phases = 0;
        int withMarkers = 0;
        for (GuideCatalog.Entry entry : catalog.search("", "boss"))
        {
            if (!entry.id.startsWith("encounter:")) continue;
            encounters++;
            phases += entry.sections.size();
            assertTrue(entry.id, entry.source.startsWith("https://"));
            if (entry.markerPackId != null)
            {
                withMarkers++;
                assertNotNull(entry.id, catalog.pack(entry.markerPackId));
            }
            for (GuideCatalog.Section phase : entry.sections)
            {
                assertTrue(entry.id + " " + phase.title, phase.steps.stream().anyMatch(step -> step.text.startsWith("Position: ")));
                assertTrue(entry.id + " " + phase.title, phase.steps.stream().anyMatch(step -> step.text.startsWith("Movement cue: ")));
            }
        }
        assertEquals(117, encounters);
        assertEquals(298, phases);
        assertTrue(withMarkers >= 39);
        assertNotNull(catalog.entry("encounter:the-gauntlet"));
        assertNotNull(catalog.entry("encounter:the-corrupted-gauntlet"));
    }

    @Test public void shortPathRoutesAreTravelOnly()
    {
        GuideCatalog.Entry quest = new GuideCatalog.Entry();
        quest.category = "quest";
        GuideCatalog.Step step = new GuideCatalog.Step();
        step.location = new GuideCatalog.Location();
        step.text = "Talk to the Cook in Lumbridge Castle.";
        assertFalse("Quest Helper owns quest travel",RunewardenGuides.isTravelStep(quest, step));
        step.text = "Defeat the boss and return to the Cook.";
        assertFalse(RunewardenGuides.isTravelStep(quest, step));
        step.text = "Talk to the Cook.";
        quest.category = "boss";
        assertFalse(RunewardenGuides.isTravelStep(quest, step));
        quest.category = "training";
        assertTrue(RunewardenGuides.isTravelStep(quest, step));
        step.location = null;
        assertFalse(RunewardenGuides.isTravelStep(quest, step));
    }
}
