/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/** Explicit profile-bound timer choices. A stale Swing action cannot change another account. */
final class HarvestSelections
{
    static final String KEY = "harvestGroupsV1";
    static final Set<String> GROUPS = Set.of("bird-house", "herb", "tree", "fruit-tree", "special", "flower", "allotment", "bush", "grape", "hops");
    private final ConfigManager config;
    private String profile;
    private long epoch,revision;
    private Set<String> chosen;

    HarvestSelections(ConfigManager config) { this.config = config; }

    synchronized void clear()
    {
        profile = null;
        chosen = null;
        epoch++; revision++;
    }

    synchronized Session bind(String nextProfile)
    {
        if (nextProfile == null || nextProfile.isEmpty()) { clear(); return session(); }
        if (!nextProfile.equals(profile))
        {
            profile = nextProfile;
            epoch++; revision++;
            chosen = parse(config.getConfiguration(RunewardenConfig.GROUP, nextProfile, KEY));
        }
        return session();
    }

    synchronized long revision(){return revision;}

    synchronized Session session() { return new Session(profile, epoch); }

    synchronized Set<String> selected(Collection<HarvestClockTool.Harvest> observations)
    {
        if (profile == null) return Collections.emptySet();
        if (chosen != null) return Collections.unmodifiableSet(new LinkedHashSet<>(chosen));
        Set<String> defaults = new LinkedHashSet<>();
        for (HarvestClockTool.Harvest harvest : observations) if (harvest.observed()) defaults.add(harvest.id);
        return Collections.unmodifiableSet(defaults);
    }

    synchronized boolean toggle(Session expected, String group, boolean enabled, Collection<HarvestClockTool.Harvest> observations)
    {
        if (!current(expected) || !GROUPS.contains(group)) return false;
        Set<String> next = new LinkedHashSet<>(selected(observations));
        if (enabled) next.add(group); else next.remove(group);
        save(next);
        return true;
    }

    synchronized boolean add(Session expected, Collection<String> groups, Collection<HarvestClockTool.Harvest> observations)
    {
        return update(expected, groups, true, observations);
    }

    synchronized boolean update(Session expected, Collection<String> groups, boolean enabled, Collection<HarvestClockTool.Harvest> observations)
    {
        if (!current(expected)) return false;
        Set<String> next = new LinkedHashSet<>(selected(observations));
        for (String group : groups) if (GROUPS.contains(group))
        {
            if (enabled) next.add(group); else next.remove(group);
        }
        save(next);
        return true;
    }

    synchronized boolean isCurrent(Session expected) { return current(expected); }

    private boolean current(Session expected)
    {
        return expected != null && profile != null && profile.equals(expected.profile) && epoch == expected.epoch
            && profile.equals(config.getRSProfileKey());
    }

    private void save(Set<String> next)
    {
        chosen = Collections.unmodifiableSet(new LinkedHashSet<>(next));
        revision++;
        // Use the captured profile explicitly: never resolve a different account during a delayed EDT action.
        JsonArray encoded = new JsonArray();
        for (String group : next) encoded.add(group);
        config.setConfiguration(RunewardenConfig.GROUP, profile, KEY, encoded.toString());
    }

    private static Set<String> parse(String value)
    {
        if (value == null) return null;
        try
        {
            JsonElement parsed = new JsonParser().parse(value);
            if (!parsed.isJsonArray()) return null;
            Set<String> result = new LinkedHashSet<>();
            JsonArray array = parsed.getAsJsonArray();
            for (JsonElement element : array)
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && GROUPS.contains(element.getAsString()))
                    result.add(element.getAsString());
            return Collections.unmodifiableSet(result);
        }
        catch (RuntimeException ignored) { return null; }
    }

    static final class Session
    {
        final String profile;
        final long epoch;
        Session(String profile, long epoch) { this.profile = profile; this.epoch = epoch; }
    }
}
