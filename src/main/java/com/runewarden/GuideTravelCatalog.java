/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/** Sourced arrival points for authored preparation/travel steps, never combat positions. */
@Singleton
public final class GuideTravelCatalog
{
    public static final class Entry
    {
        public final String activity,label,source,sourceText,provenance;
        public final List<String> guideIds;
        public final int x,y,plane,sourceLine,sourceColumn;
        private Entry(Row row)
        {
            activity=row.activity;label=row.label;source=row.source;sourceText=row.sourceText;provenance=row.provenance;
            guideIds=Collections.unmodifiableList(new ArrayList<>(row.guideIds));
            x=row.x;y=row.y;plane=row.plane;sourceLine=row.sourceLine;sourceColumn=row.sourceColumn;
        }
        public WorldPoint location(){return new WorldPoint(x,y,plane);}
    }
    private static final class Row
    {
        String activity,label,source,sourceText,provenance;List<String> guideIds;
        int x,y,plane,sourceLine,sourceColumn;
        boolean valid()
        {
            return label!=null&&!label.isBlank()&&label.length()<=200&&source!=null&&!source.isBlank()&&source.length()<=500
                &&sourceText!=null&&!sourceText.isBlank()&&provenance!=null&&!provenance.isBlank()
                &&sourceLine>0&&sourceColumn>=0&&sourceColumn<=1&&x>0&&x<16384&&y>0&&y<16384&&plane>=0&&plane<=3
                &&guideIds!=null&&!guideIds.isEmpty()&&guideIds.size()<=40;
        }
    }
    private static final class File {int schemaVersion;List<Row> entries;}
    private final Map<String,Entry> byGuide;
    private final List<Entry> entries;
    @Inject public GuideTravelCatalog(Gson gson)
    {
        Map<String,Entry> index=new LinkedHashMap<>();List<Entry> records=new ArrayList<>();
        for(String name:new String[]{"guide-travel-minigames.json","guide-travel-training.json"})
        {
            try(InputStream input=GuideTravelCatalog.class.getResourceAsStream("/com/runewarden/"+name))
            {
                if(input==null)throw new IllegalStateException("Missing guide travel data: "+name);
                File file=gson.fromJson(new InputStreamReader(input,StandardCharsets.UTF_8),File.class);
                if(file==null||file.schemaVersion!=1||file.entries==null||file.entries.size()>250)
                    throw new IllegalStateException("Invalid guide travel data: "+name);
                for(Row row:file.entries)
                {
                    if(row==null||!row.valid())throw new IllegalStateException("Invalid guide arrival point: "+name);
                    Entry entry=new Entry(row);records.add(entry);
                    for(String id:entry.guideIds)
                    {
                        if(id==null||!(id.startsWith("training:")||id.startsWith("minigame:"))||index.putIfAbsent(id,entry)!=null)
                            throw new IllegalStateException("Invalid or duplicate travel guide: "+id);
                    }
                }
            }
            catch(java.io.IOException failure){throw new IllegalStateException("Cannot read guide travel data: "+name,failure);}
        }
        byGuide=Collections.unmodifiableMap(index);entries=Collections.unmodifiableList(records);
    }
    public Entry find(String guideId){return byGuide.get(guideId);}
    public List<Entry> all(){return entries;}
    public int size(){return byGuide.size();}
    public WorldPoint destination(GuideCatalog.Entry guide,GuideCatalog.Step step)
    {
        if(!isPreparation(guide,step))return null;Entry entry=find(guide.id);return entry==null?null:entry.location();
    }
    /** Section identity is authored data: wording, NPC state and proximity cannot turn a lesson into travel. */
    static boolean isPreparation(GuideCatalog.Entry guide,GuideCatalog.Step step)
    {
        if(guide==null||step==null||guide.sections==null||!("training".equals(guide.category)||"minigame".equals(guide.category)))return false;
        for(GuideCatalog.Section section:guide.sections)
            if(section!=null&&("prepare".equals(section.id)||"preparation".equals(section.id)||"travel".equals(section.id))&&contains(section.steps,step))return true;
        return false;
    }
    private static boolean contains(List<GuideCatalog.Step> steps,GuideCatalog.Step selected)
    {
        if(steps!=null)for(GuideCatalog.Step step:steps)if(step==selected||step!=null&&contains(step.alternatives,selected))return true;
        return false;
    }
}
