/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideTravelCatalogTest
{
    @Test public void allTwentyReleasedActivitiesHaveRealSourcedArrivalPoints()
    {
        GuideTravelCatalog catalog=new GuideTravelCatalog(new Gson());assertEquals(94,catalog.all().size());assertEquals(112,catalog.size());
        EnumSet<MinigameActivityCatalog.Activity> found=EnumSet.noneOf(MinigameActivityCatalog.Activity.class);
        for(GuideTravelCatalog.Entry entry:catalog.all())if(entry.activity!=null)
            assertTrue("One arrival per activity: "+entry.activity,found.add(MinigameActivityCatalog.Activity.valueOf(entry.activity)));
        assertEquals(EnumSet.allOf(MinigameActivityCatalog.Activity.class),found);
        assertEquals(new WorldPoint(3361,3149,0),catalog.find("training:giants-foundry").location());
        assertEquals(new WorldPoint(1630,3963,0),catalog.find("training:wintertodt").location());
        assertEquals(new WorldPoint(3614,9477,0),catalog.find("training:guardians-rift").location());
        assertEquals(new WorldPoint(1388,2920,0),catalog.find("training:mastering-mixology").location());
        assertEquals(new WorldPoint(3060,9766,0),catalog.find("training:motherlode").location());
        assertEquals(new WorldPoint(2723,5253,1),catalog.find("training:dorgesh-kaan-course").location());
        assertEquals(new WorldPoint(3757,3756,0),catalog.find("training:bird-house-runs").location());
    }
    @Test public void everyCoordinateMatchesItsExactRetainedPrimarySourceLine() throws Exception
    {
        GuideTravelCatalog catalog=new GuideTravelCatalog(new Gson());
        Pattern point=Pattern.compile("new WorldPoint\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
        GuideCatalog guides=new GuideCatalog(new Gson());Set<String> ids=new HashSet<>();
        for(GuideTravelCatalog.Entry entry:catalog.all())
        {
            Path source=Path.of(entry.source);assertFalse(source.isAbsolute());assertFalse(entry.source.contains(".."));
            // Upstream files are retained as reference evidence, outside all build source sets.
            if(source.startsWith("vendor"))source=Path.of("docs/reference-sources").resolve(source);
            List<String> lines=Files.readAllLines(source,StandardCharsets.UTF_8);assertTrue(entry.sourceLine<=lines.size());
            String line=lines.get(entry.sourceLine-1);assertEquals(entry.source+":"+entry.sourceLine,entry.sourceText,line);
            if(entry.source.endsWith(".tsv"))
            {
                String[] coordinate=line.split("\\t",-1)[entry.sourceColumn].split(" ");
                assertEquals(entry.location(),new WorldPoint(Integer.parseInt(coordinate[0]),Integer.parseInt(coordinate[1]),Integer.parseInt(coordinate[2])));
                assertTrue(entry.provenance.contains("37abff3a3345185fc11fccecae04d8170de25a11"));
            }
            else
            {
                Matcher match=point.matcher(line);boolean exact=false;
                while(match.find())if(entry.x==Integer.parseInt(match.group(1))&&entry.y==Integer.parseInt(match.group(2))&&entry.plane==Integer.parseInt(match.group(3)))exact=true;
                assertTrue("Coordinate absent from source: "+entry.source+":"+entry.sourceLine,exact);
                assertTrue(entry.provenance.contains("RuneLite 1.13.2")||entry.provenance.contains("5f791937a99a676f23c02aa48c4e4d5b50e7edad"));
            }
            for(String id:entry.guideIds)
            {assertTrue("Duplicate guide: "+id,ids.add(id));assertNotNull("No actual guide: "+id,guides.entry(id));}
        }
        assertEquals(catalog.size(),ids.size());
    }
    @Test public void actualGuidePreparationRoutesButLessonsAndPortableMethodsDoNot()
    {
        GuideTravelCatalog travel=new GuideTravelCatalog(new Gson());GuideCatalog guides=new GuideCatalog(new Gson());
        for(String id:new String[]{"training:giants-foundry","training:guardians-rift","training:tempoross","training:motherlode","training:gnome-course","training:dorgesh-kaan-course","training:herbiboar","training:bird-house-runs","training:master-farmers","training:sepulchre-floor-5","minigame:barbarian-assault","minigame:castle-wars","minigame:fishing-trawler","minigame:mage-training-arena","minigame:soul-wars","minigame:trouble-brewing"})
        {
            GuideCatalog.Entry guide=guides.entry(id);assertNotNull("Missing actual guide: "+id,guide);GuideCatalog.DisplayStep first=guide.flattened().get(0);
            assertEquals("prepare",first.group.id);assertEquals(travel.find(id).location(),travel.destination(guide,first.step));
            for(GuideCatalog.DisplayStep step:guide.flattened())if("method".equals(step.group.id))assertNull(travel.destination(guide,step.step));
        }
        assertNull(travel.find("training:high-alchemy"));assertNull(travel.find("crafting:diamond"));assertNull(travel.find("encounter:zulrah"));
        assertNull(travel.destination(guides.entry("encounter:zulrah"),new GuideCatalog.Step()));assertNull(travel.destination(null,null));
    }
}
