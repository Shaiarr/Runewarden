/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Search and guide cross-references are built off the game and Swing threads. */
@Singleton
public final class ItemKnowledgeCatalog
{
    public static final class ItemRef
    {
        final int id;
        final String name;
        ItemRef(int id, String name) { this.id = id; this.name = name; }
    }

    private final Gson gson;
    private final GuideCatalog guides;
    private final Map<Integer, ItemRef> items = new ConcurrentHashMap<>();
    private volatile Map<Integer, List<GuideCatalog.Entry>> guideItems = Collections.emptyMap();
    private volatile List<GuideTitle> guideTitles = Collections.emptyList();
    private final Map<String, List<GuideCatalog.Entry>> sourceGuideCache = Collections.synchronizedMap(
        new LinkedHashMap<String, List<GuideCatalog.Entry>>(32, 0.75f, true)
        {
            @Override protected boolean removeEldestEntry(Map.Entry<String, List<GuideCatalog.Entry>> eldest)
            { return size() > 256; }
        });
    private volatile Map<Integer, ItemSourceService.Lookup> bundled = Collections.emptyMap();
    private volatile boolean loaded;
    private static final class AcquisitionIndex { List<AcquisitionItem> items; int cards; }
    private static final class AcquisitionItem { int itemId, bucket, sourceCount; String title; }
    private volatile Map<Integer,AcquisitionItem> acquisitionIndex=Collections.emptyMap();
    private volatile int acquisitionCardCount;
    private final Map<Integer,Map<Integer,ItemSourceService.Lookup>> acquisitionBuckets = new LinkedHashMap<Integer,Map<Integer,ItemSourceService.Lookup>>(4,0.75f,true)
    { @Override protected boolean removeEldestEntry(Map.Entry<Integer,Map<Integer,ItemSourceService.Lookup>> eldest){return size()>4;} };

    private static final class GuideTitle
    {
        final GuideCatalog.Entry guide;
        final String title, prefix, suffix;
        GuideTitle(GuideCatalog.Entry guide)
        {
            this.guide = guide; title = normalize(guide.title);
            prefix = title + " "; suffix = " " + title;
        }
    }

    @Inject ItemKnowledgeCatalog(Gson gson, GuideCatalog guides)
    { this.gson = gson; this.guides = guides; }

    synchronized void load()
    {
        if (loaded) return;
        Map<String, List<Integer>> names = GuideItemResolver.nameIndex();
        for (Map.Entry<String, List<Integer>> entry : names.entrySet())
        {
            if (entry.getKey().isEmpty()) continue;
            String name = Character.toUpperCase(entry.getKey().charAt(0)) + entry.getKey().substring(1);
            for (int id : entry.getValue()) if (id > 0) items.putIfAbsent(id, new ItemRef(id, name));
        }
        Map<Integer, List<GuideCatalog.Entry>> crossReferences = new LinkedHashMap<>();
        List<GuideCatalog.Entry> all = guides.search("", "");
        for (GuideCatalog.Entry guide : all)
        {
            Set<Integer> ids = new LinkedHashSet<>();
            add(ids, guide.items); add(ids, guide.recommended);
            if (guide.sections != null) for (GuideCatalog.Section section : guide.sections) add(ids, section.items);
            for (int id : ids) crossReferences.computeIfAbsent(id, ignored -> new ArrayList<>()).add(guide);
        }
        crossReferences.replaceAll((id, entries) -> Collections.unmodifiableList(entries));
        guideItems = Collections.unmodifiableMap(crossReferences);
        List<GuideTitle> titles = new ArrayList<>();
        for (GuideCatalog.Entry guide : all) titles.add(new GuideTitle(guide));
        guideTitles = Collections.unmodifiableList(titles);
        sourceGuideCache.clear();
        ItemSourceService.Bundle file = gson.fromJson(new InputStreamReader(
            ItemKnowledgeCatalog.class.getResourceAsStream("/com/runewarden/item-sources.json"),
            StandardCharsets.UTF_8), ItemSourceService.Bundle.class);
        Map<Integer, ItemSourceService.Lookup> sourceMap = new LinkedHashMap<>();
        if (file != null && file.items != null) for (ItemSourceService.Lookup lookup : file.items)
            sourceMap.put(lookup.itemId, lookup);
        AcquisitionIndex acquisition = gson.fromJson(new InputStreamReader(
            ItemKnowledgeCatalog.class.getResourceAsStream("/com/runewarden/item-acquisition.json"),
            StandardCharsets.UTF_8), AcquisitionIndex.class);
        Map<Integer,AcquisitionItem> index=new LinkedHashMap<>();
        if(acquisition!=null&&acquisition.items!=null)for(AcquisitionItem item:acquisition.items)
        {index.put(item.itemId,item);items.putIfAbsent(item.itemId,new ItemRef(item.itemId,item.title));}
        acquisitionIndex=Collections.unmodifiableMap(index);acquisitionCardCount=acquisition==null?0:acquisition.cards;
        bundled = Collections.unmodifiableMap(sourceMap);
        loaded = true;
    }

    private static void add(Set<Integer> ids, List<JsonObject> list)
    { if (list != null) for (JsonObject item : list) ids.addAll(GuideItemResolver.resolveAll(item)); }

    boolean loaded() { return loaded; }
    int size() { return items.size(); }
    ItemRef item(int id) { return items.get(id); }
    synchronized ItemSourceService.Lookup bundled(int id)
    {
        AcquisitionItem indexed=acquisitionIndex.get(id);
        Map<Integer,ItemSourceService.Lookup> bucket=indexed==null?null:acquisitionBuckets.get(indexed.bucket);
        return bucket!=null&&bucket.containsKey(id)?bucket.get(id):bundled.get(id);
    }
    /** Worker-thread only. At most four source shards are retained; rendering never parses a shard. */
    ItemSourceService.Lookup loadBundled(int id)
    {
        AcquisitionItem indexed=acquisitionIndex.get(id);if(indexed==null)return bundled.get(id);
        Map<Integer,ItemSourceService.Lookup> bucket;
        synchronized(this){bucket=acquisitionBuckets.get(indexed.bucket);}
        if(bucket==null)
        {
            try(InputStreamReader reader=new InputStreamReader(ItemKnowledgeCatalog.class.getResourceAsStream(
                String.format(Locale.ROOT,"/com/runewarden/item-acquisition/%02d.json",indexed.bucket)),StandardCharsets.UTF_8))
            {
                ItemSourceService.Bundle file=gson.fromJson(reader,ItemSourceService.Bundle.class);Map<Integer,ItemSourceService.Lookup> found=new LinkedHashMap<>();
                if(file!=null&&file.items!=null)for(ItemSourceService.Lookup lookup:file.items)
                {
                    ItemSourceService.Lookup pinned=bundled.get(lookup.itemId);
                    if(pinned!=null)
                    {
                        ItemSourceService.Lookup merged=new ItemSourceService.Lookup(pinned);
                        for(ItemSourceService.Source card:lookup.sources)
                            if(merged.sources.stream().noneMatch(existing->existing.kind.equals(card.kind)&&existing.name.equals(card.name)))merged.sources.add(card);
                        lookup=merged;
                    }
                    found.put(lookup.itemId,lookup);
                }
                bucket=Collections.unmodifiableMap(found);synchronized(this){acquisitionBuckets.put(indexed.bucket,bucket);}
            }
            catch(java.io.IOException|RuntimeException failure){return bundled.get(id);}
        }
        return bucket.getOrDefault(id,bundled.get(id));
    }
    int sourceItemCount() { Set<Integer> ids=new LinkedHashSet<>(acquisitionIndex.keySet());ids.addAll(bundled.keySet());return ids.size(); }
    int sourceCardCount() { return acquisitionCardCount; }
    synchronized int loadedSourceShards() { return acquisitionBuckets.size(); }

    /** Only called with data copied from a native definition on the client thread. */
    void addNative(int id, String name)
    {
        if (id > 0 && name != null && !name.trim().isEmpty() && !"null".equalsIgnoreCase(name))
            items.put(id, new ItemRef(id, name));
    }

    List<ItemRef> search(String query)
    {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int numeric = -1;
        try { numeric = Integer.parseInt(needle); } catch (NumberFormatException ignored) { }
        List<ItemRef> results = new ArrayList<>();
        // Identical named variants share one search row; a numeric ID can still select each exact variant.
        Map<String, ItemRef> named = new LinkedHashMap<>();
        for (ItemRef item : items.values())
        {
            if (item.id == numeric) return Collections.singletonList(item);
            if (!item.name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            String key = item.name.toLowerCase(Locale.ROOT);
            ItemRef previous = named.get(key);
            if (previous == null || item.id < previous.id) named.put(key, item);
        }
        results.addAll(named.values());
        results.sort(Comparator.comparing((ItemRef item) -> !item.name.equalsIgnoreCase(needle))
            .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(item -> item.id));
        return results;
    }

    List<GuideCatalog.Entry> related(int itemId)
    { return guideItems.getOrDefault(itemId, Collections.emptyList()); }

    List<GuideCatalog.Entry> sourceGuides(String source)
    {
        if (source == null) return Collections.emptyList();
        List<GuideCatalog.Entry> cached = sourceGuideCache.get(source);
        if (cached != null) return cached;
        String plain = normalize(source);
        List<GuideCatalog.Entry> found = new ArrayList<>();
        for (GuideTitle guide : guideTitles)
        {
            if (guide.title.equals(plain) || (guide.title.length() > 5
                && (plain.startsWith(guide.prefix) || plain.endsWith(guide.suffix)))) found.add(guide.guide);
        }
        List<GuideCatalog.Entry> result = Collections.unmodifiableList(found);
        sourceGuideCache.put(source, result);
        return result;
    }

    private static String normalize(String value)
    {
        String plain = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (plain.equals("tzhaar fight cave") || plain.equals("fight cave")) return "fight caves";
        return plain;
    }
}
