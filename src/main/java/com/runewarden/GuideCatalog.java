/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/** Bundled, reproducible guide data. No runtime code or remote guide downloads. */
@Singleton
public final class GuideCatalog
{
    public static final class Step
    {
        String id;
        String text;
        List<String> notes;
        List<String> dialogue;
        List<Step> alternatives;
        Location location;
        int markerNumber;
        boolean puzzle;
        boolean alternative;
        CompletionRule completion;
    }

    /** Explicit observed state only; never inferred from step wording or player proximity. */
    static final class CompletionRule
    {
        String kind;
        int itemId;
        int quantity;
        String quest;
        String skill;
        int level;
    }

    public static final class Location
    {
        int x;
        int y;
        int plane;
        WorldPoint point() { return new WorldPoint(x, y, plane); }
    }

    public static final class Section
    {
        String id;
        String title;
        String markerPackId;
        List<Integer> markerNumbers;
        List<Step> steps;
        List<JsonObject> items;
    }

    public static final class Entry
    {
        String id;
        String title;
        String category;
        String subtitle;
        String skill;
        String source;
        String markerPackId;
        List<String> requirements;
        List<JsonObject> requirementDetails;
        List<String> optionalUnlocks;
        List<JsonObject> items;
        List<JsonObject> recommended;
        List<Section> sections;

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }

        List<DisplayStep> flattened()
        {
            List<DisplayStep> result = new ArrayList<>();
            if (sections != null)
            {
                for (Section section : sections)
                {
                    if (section.steps != null)
                    {
                        for (Step step : section.steps) addStep(result, section, step, false);
                    }
                }
            }
            return result;
        }

        private static void addStep(List<DisplayStep> result, Section section, Step step, boolean alternative)
        {
            result.add(new DisplayStep(section, step, alternative || step.alternative));
            if (step.alternatives != null)
            {
                for (Step branch : step.alternatives) addStep(result, section, branch, true);
            }
        }

        @Override public String toString() { return title; }
    }

    static final class DisplayStep
    {
        final String section;
        final Section group;
        final Step step;
        final boolean alternative;
        DisplayStep(Section section, Step step, boolean alternative)
        {
            this.section = section.title;
            this.group = section;
            this.step = step;
            this.alternative = alternative;
        }
    }

    public static final class Marker
    {
        int regionId;
        int regionX;
        int regionY;
        int z;
        int number;
        String color;
        String label;

        WorldPoint point()
        {
            return new WorldPoint(((regionId >> 8) << 6) + regionX,
                ((regionId & 255) << 6) + regionY, z);
        }

        String displayLabel()
        {
            return Integer.toString(number) + (label == null || label.isEmpty() ? "" : " · " + label);
        }
    }

    public static final class MarkerPack
    {
        String id;
        String title;
        String source;
        String wiki;
        List<Marker> tiles;
        @Override public String toString() { return title; }
    }

    private static final class GuideFile { List<Entry> entries; }
    private static final class MarkerFile { List<MarkerPack> packs; }
    private final Gson gson;
    private QuestOrder questOrder;
    private GuideTravelCatalog travel;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, MarkerPack> packs = new LinkedHashMap<>();
    private boolean loaded;

    @Inject public GuideCatalog(Gson gson) { this.gson = gson; }

    /** Reuses RuneLite's injected Gson for every bundled data reader. */
    synchronized QuestOrder questOrder()
    {
        if (questOrder == null) questOrder = new QuestOrder(gson);
        return questOrder;
    }

    synchronized GuideTravelCatalog travel()
    {
        if (travel == null) travel = new GuideTravelCatalog(gson);
        return travel;
    }

    synchronized void load()
    {
        if (loaded) return;
        GuideFile guides = read("guides.json", GuideFile.class);
        MarkerFile markers = read("marker-packs.json", MarkerFile.class);
        if (guides == null || guides.entries == null || markers == null || markers.packs == null)
            throw new IllegalStateException("The bundled guide library is missing.");
        for (Entry entry : guides.entries)
        {
            if ("quest".equals(entry.category))
            {
                if (entry.sections != null && !entry.sections.isEmpty() || entry.items != null && !entry.items.isEmpty()
                    || entry.recommended != null && !entry.recommended.isEmpty() || entry.markerPackId != null)
                    throw new IllegalStateException("Quest walkthroughs belong to Quest Helper.");
                entry.sections = Collections.emptyList(); entry.items = Collections.emptyList(); entry.recommended = Collections.emptyList();
            }
            if (entry.id == null || entry.title == null || entry.sections == null || entries.put(entry.id, entry) != null)
                throw new IllegalStateException("Invalid or duplicate bundled guide.");
        }
        for (MarkerPack pack : markers.packs)
        {
            if (pack.id == null || pack.tiles == null || packs.put(pack.id, pack) != null)
                throw new IllegalStateException("Invalid or duplicate marker pack.");
        }
        loaded = true;
    }

    private <T> T read(String resource, Class<T> type)
    {
        try (InputStream stream = GuideCatalog.class.getResourceAsStream("/com/runewarden/" + resource))
        {
            if (stream == null) throw new IllegalStateException("Missing resource " + resource);
            return gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), type);
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read " + resource, error); }
    }

    List<Entry> search(String query, String category)
    {
        load();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries.values())
        {
            if ((category == null || category.isEmpty() || category.equals(entry.category)
                || ("boss".equals(category) && "waves".equals(entry.category)))
                && (entry.title + " " + entry.subtitle + " " + entry.id).toLowerCase(Locale.ROOT).contains(needle))
                result.add(entry);
        }
        result.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        return result;
    }

    Entry entry(String id) { load(); return entries.get(id); }
    MarkerPack pack(String id) { load(); return packs.get(id); }
    List<MarkerPack> markerPacks() { load(); return Collections.unmodifiableList(new ArrayList<>(packs.values())); }
}
