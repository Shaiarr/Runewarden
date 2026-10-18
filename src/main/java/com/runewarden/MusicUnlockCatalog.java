/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;

/** Shared factual music library plus a bounded, public-API native track index. */
@Singleton
public final class MusicUnlockCatalog
{
    public static final class Level { String skill; int level; }
    public static final class RequiredItem { int id, quantity; String name; }
    public static final class Route
    {
        String name, quest, notes; int x, y, plane;
        List<Level> levels = new ArrayList<>(); List<RequiredItem> items = new ArrayList<>();
        WorldPoint point() { return x > 0 && y > 0 && plane >= 0 && plane <= 3 ? new WorldPoint(x, y, plane) : null; }
    }
    static final class Fact
    {
        String name, wiki, unlock, location, region, kind, quest;
        boolean members, holiday;
        List<Route> routes = new ArrayList<>();
    }
    private static final class Data { String retrieved, wikiSource, routeSource; List<Fact> tracks; }

    public static final class Track
    {
        public final int id, bank, bit;
        public final String name, unlock, source, region, location, kind, quest;
        public final boolean holiday, nativeDefinition;
        final List<Route> routes;
        final String search;
        Track(int id, String name, String hint, int bank, int bit, boolean holiday, Fact fact)
        {
            this.id = id; this.name = name; this.bank = bank; this.bit = bit; nativeDefinition = id >= 0;
            unlock = fact != null && fact.unlock != null && !fact.unlock.isEmpty() ? fact.unlock : hint;
            source = fact == null ? "https://oldschool.runescape.wiki/w/Special:Search?search=" + encode(name) : fact.wiki;
            region = fact == null || fact.region == null ? "Unclassified" : fact.region;
            location = fact == null || fact.location == null ? "" : fact.location;
            kind = fact == null || fact.kind == null ? "UNKNOWN" : fact.kind; quest = fact == null ? null : fact.quest;
            this.holiday = holiday || (fact != null && fact.holiday);
            routes = fact == null || fact.routes == null ? Collections.emptyList() : Collections.unmodifiableList(fact.routes);
            search = (name + " " + region + " " + location + " " + unlock).toLowerCase(Locale.ROOT);
        }
    }

    public static final class Access
    {
        final boolean blocked, unknown;
        final List<String> details;
        Access(boolean blocked, boolean unknown, List<String> details)
        { this.blocked = blocked; this.unknown = unknown; this.details = Collections.unmodifiableList(details); }
        boolean checked() { return !blocked && !unknown; }
    }
    static final class Stop
    {
        final Track track; final Route route; final int distance;
        Stop(Track track, Route route, int distance) { this.track = track; this.route = route; this.distance = distance; }
    }

    private final Map<String, Fact> facts = new LinkedHashMap<>();
    private final Map<String, Quest> quests = new HashMap<>();
    private final Map<String, GuideCatalog.Entry> questGuides = new HashMap<>();
    private final Map<Integer, Track> nativeTracks = new LinkedHashMap<>();
    private final Set<Integer> handledRows = new LinkedHashSet<>();
    private final Map<Integer, Integer> retries = new HashMap<>();
    private final List<Track> bundled;
    private final Set<String> neededQuests = new LinkedHashSet<>();
    private volatile List<Track> tracks;
    private List<Integer> rows;
    private int cursor;
    private boolean complete;
    private long revision;

    @Inject public MusicUnlockCatalog(Gson gson, GuideCatalog guides)
    {
        Data data = gson.fromJson(new InputStreamReader(MusicUnlockCatalog.class.getResourceAsStream(
            "/com/runewarden/music-unlocks.json"), StandardCharsets.UTF_8), Data.class);
        if (data == null || data.tracks == null || data.tracks.isEmpty()) throw new IllegalStateException("Missing music unlock facts");
        for (Quest quest : Quest.values()) { quests.put(quest.name(), quest); quests.put(normalize(quest.getName()), quest); }
        for (GuideCatalog.Entry entry : guides.search("", "quest")) questGuides.put(normalize(entry.title), entry);
        List<Track> entries = new ArrayList<>(); int fallbackId = -1;
        for (Fact fact : data.tracks)
        {
            if (fact.name == null || fact.wiki == null || facts.put(normalize(fact.name), fact) != null)
                throw new IllegalStateException("Invalid music unlock fact");
            entries.add(new Track(fallbackId--, fact.name, "", Integer.MIN_VALUE, -1, fact.holiday, fact));
            if (fact.quest != null) neededQuests.add(fact.quest);
            for (Route route : fact.routes) if (route.quest != null) neededQuests.add(route.quest);
        }
        entries.sort(Comparator.comparing(track -> track.name, String.CASE_INSENSITIVE_ORDER));
        bundled = Collections.unmodifiableList(entries); tracks = bundled;
    }

    public List<Track> tracks() { return tracks; }
    int bundledCount() { return bundled.size(); }
    long revision() { return revision; }
    boolean nativeComplete() { return complete; }
    int indexedRows() { return handledRows.size(); }
    int nativeRowCount() { return rows == null ? 0 : rows.size(); }
    Set<String> neededQuests() { return Collections.unmodifiableSet(neededQuests); }
    Quest quest(String key) { return key == null ? null : quests.getOrDefault(key, quests.get(normalize(key.replace('_', ' ')))); }
    GuideCatalog.Entry guide(String key)
    {
        Quest quest = quest(key); return quest == null ? null : questGuides.get(normalize(quest.getName()));
    }

    /** Client thread only: at most 32 definitions, additionally bounded by a 1.5ms soft budget. */
    boolean readNativeChunk(Client client)
    {
        if (complete) return false;
        if (rows == null)
        {
            List<Integer> nativeRows;
            try { nativeRows = client.getDBTableRows(DBTableID.Music.ID); }
            catch (RuntimeException loading) { return false; }
            if (nativeRows == null || nativeRows.isEmpty() || nativeRows.size() > 4096) return false;
            rows = new ArrayList<>(new LinkedHashSet<>(nativeRows));
        }
        long deadline = System.nanoTime() + 1_500_000L; int checked = 0; boolean changed = false;
        while (cursor < rows.size() && checked++ < 32)
        {
            int id = rows.get(cursor++);
            if (handledRows.contains(id)) continue;
            try
            {
                String name = string(client, id, DBTableID.Music.COL_DISPLAYNAME);
                Object hidden = first(client.getDBTableField(id, DBTableID.Music.COL_HIDDEN, 0));
                if (name == null || name.isEmpty() || hidden == null) throw new IllegalStateException("Music cache loading");
                handledRows.add(id);
                if (truth(hidden)) continue;
                String hint = string(client, id, DBTableID.Music.COL_UNLOCKHINT);
                Object[] variables = client.getDBTableField(id, DBTableID.Music.COL_VARIABLE, 0);
                int bank = Integer.MIN_VALUE, bit = -1;
                if (variables != null && variables.length >= 2 && variables[0] instanceof Number && variables[1] instanceof Number)
                { bank = ((Number) variables[0]).intValue(); bit = ((Number) variables[1]).intValue(); }
                boolean holiday = truth(first(client.getDBTableField(id, DBTableID.Music.COL_HOLIDAY, 0)));
                Fact fact = facts.get(normalize(name));
                nativeTracks.put(id, new Track(id, name, hint == null ? "Unlock details not catalogued" : hint, bank, bit, holiday, fact));
                changed = true;
            }
            catch (RuntimeException loading)
            {
                handledRows.remove(id); int attempts = retries.getOrDefault(id, 0) + 1; retries.put(id, attempts);
                if (attempts >= 5) handledRows.add(id);
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (cursor >= rows.size())
        {
            if (handledRows.size() < rows.size()) cursor = 0;
            else complete = true;
        }
        if (changed)
        {
            List<Track> merged = new ArrayList<>(nativeTracks.values()); Set<String> nativeNames = new LinkedHashSet<>();
            for (Track track : merged) nativeNames.add(normalize(track.name));
            for (Track track : bundled) if (!nativeNames.contains(normalize(track.name))) merged.add(track);
            merged.sort(Comparator.comparing(track -> track.name, String.CASE_INSENSITIVE_ORDER));
            tracks = Collections.unmodifiableList(merged); revision++;
        }
        return changed;
    }

    /** -1 unknown, 0 locked, 1 unlocked; an invalid native variable never becomes a guessed lock. */
    static int unlocked(Track track, int[] banks)
    {
        if (!track.nativeDefinition || track.bank == Integer.MIN_VALUE) return -1;
        if (track.bank > banks.length) return -1;
        if (track.bank < 1 || track.bit < 0) return 1; // Exact native music_isunlocked rule for automatic tracks.
        if (track.bit > 31) return -1;
        return (banks[track.bank - 1] >>> track.bit) & 1;
    }

    Access access(Route route, Map<String, QuestState> states, Map<Skill, Integer> levels, Map<Integer, Integer> carried, boolean observed)
    {
        List<String> details = new ArrayList<>(); boolean blocked = false, unknown = !observed;
        if (route.quest != null)
        {
            Quest quest = quest(route.quest); QuestState state = states.get(route.quest);
            String title = quest == null ? route.quest.replace('_', ' ') : quest.getName();
            details.add("Quest access: " + title + (state == QuestState.FINISHED ? " · complete" : " · verify progress"));
            if (state == null) unknown = true; else if (state != QuestState.FINISHED) blocked = true;
        }
        for (Level need : route.levels)
        {
            Skill skill;
            try { skill = Skill.valueOf(need.skill.toUpperCase(Locale.ROOT).replace("RUNECRAFTING", "RUNECRAFT")); }
            catch (RuntimeException unsupported) { unknown = true; details.add(need.level + " " + need.skill); continue; }
            Integer current = levels.get(skill); details.add(need.level + " " + need.skill + (current == null ? " · unknown" : " · current " + current));
            if (current == null) unknown = true; else if (current < need.level) blocked = true;
        }
        for (RequiredItem need : route.items)
        {
            if (need.id <= 0) { unknown = true; details.add(need.quantity + " × " + need.name.replace('_', ' ') + " · verify supply"); }
            else
            {
                boolean have = carried.getOrDefault(need.id, 0) >= need.quantity;
                details.add(need.quantity + " × " + need.name + (have ? " · carried" : " · not carried"));
                if (!have) blocked = true;
            }
        }
        return new Access(blocked, unknown, details);
    }

    List<Stop> nearby(List<Track> all, Map<Integer, Integer> unlocked, Map<String, QuestState> quests,
        Map<Skill, Integer> levels, Map<Integer, Integer> carried, WorldPoint player, boolean observed)
    {
        if (player == null || !observed) return Collections.emptyList();
        List<Stop> candidates = new ArrayList<>();
        for (Track track : all)
        {
            if (unlocked.getOrDefault(track.id, -1) != 0 || !("NORMAL".equals(track.kind) || "MINIGAME".equals(track.kind))) continue;
            Stop best = null;
            for (Route route : track.routes)
            {
                WorldPoint point = route.point(); if (point == null || point.getPlane() != player.getPlane()) continue;
                if (!access(route, quests, levels, carried, observed).checked()) continue;
                int distance = player.distanceTo2D(point);
                if (best == null || distance < best.distance) best = new Stop(track, route, distance);
            }
            if (best != null) candidates.add(best);
        }
        candidates.sort(Comparator.comparingInt((Stop stop) -> stop.distance).thenComparing(stop -> stop.track.name));
        return Collections.unmodifiableList(new ArrayList<>(candidates.subList(0, Math.min(8, candidates.size()))));
    }

    private static Object first(Object[] values) { return values == null || values.length == 0 ? null : values[0]; }
    private static String string(Client client, int row, int column)
    { Object value = first(client.getDBTableField(row, column, 0)); return value instanceof String ? net.runelite.client.util.Text.removeTags((String) value).trim() : null; }
    private static boolean truth(Object value) { return Boolean.TRUE.equals(value) || (value instanceof Number && ((Number) value).intValue() != 0); }
    private static String encode(String name) { return java.net.URLEncoder.encode(name, StandardCharsets.UTF_8); }
    static String normalize(String name) { return name == null ? "" : name.replace('’', '\'').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ""); }
}
