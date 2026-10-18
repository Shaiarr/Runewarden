/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/** Explicit profile-bound timer choices. A stale Swing action cannot change another account. */
final class HarvestSelections
{
    static final String KEY = "harvestGroupsV2", LEGACY_KEY = "harvestGroupsV1";
    private static final Set<String> LEGACY_GROUPS=Set.of("bird-house","herb","tree","fruit-tree","special","flower","allotment","bush","grape","hops");
    static final Set<String> GROUPS;
    static
    {
        Set<String> groups=new LinkedHashSet<>(Set.of("bird-house","herb","flower","allotment","bush","grape","hops"));
        for(FarmHarvestTimers.Group group:FarmHarvestTimers.GROUPS)groups.add(group.id);
        GROUPS=Collections.unmodifiableSet(groups);
    }
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
            String value=config.getConfiguration(RunewardenConfig.GROUP,nextProfile,KEY);
            chosen=parse(value,false);
            if(value==null)
            {
                Set<String> previous=parse(config.getConfiguration(RunewardenConfig.GROUP,nextProfile,LEGACY_KEY),true);
                if(previous!=null)
                {
                    Set<String> migrated=new LinkedHashSet<>(previous);migrated.remove("special");
                    for(FarmHarvestTimers.Group group:FarmHarvestTimers.GROUPS)
                        if(previous.contains(group.legacyGroup))migrated.add(group.id);
                    chosen=Collections.unmodifiableSet(migrated);
                    if(nextProfile.equals(config.getRSProfileKey()))save(migrated);
                }
            }
        }
        return session();
    }

    synchronized long revision(){return revision;}

    synchronized Session session() { return new Session(profile, epoch); }

    synchronized Set<String> selected(Collection<HarvestClockTool.Harvest> observations)
    {
        if (profile == null) return Collections.emptySet();
        if (chosen != null) return chosen;
        Set<String> defaults = new LinkedHashSet<>();
        for (HarvestClockTool.Harvest harvest : observations) if (GROUPS.contains(harvest.id)&&harvest.observed()) defaults.add(harvest.id);
        return Collections.unmodifiableSet(defaults);
    }

    synchronized boolean toggle(Session expected, String group, boolean enabled, Collection<HarvestClockTool.Harvest> observations)
    {
        return GROUPS.contains(group) && update(expected, List.of(group), enabled, observations);
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

    private static Set<String> parse(String value,boolean legacy)
    {
        if (value == null) return null;
        try
        {
            JsonElement parsed = new JsonParser().parse(value);
            if (!parsed.isJsonArray()) return null;
            Set<String> result = new LinkedHashSet<>();
            JsonArray array = parsed.getAsJsonArray();
            for (JsonElement element : array)
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() &&
                    (legacy?LEGACY_GROUPS:GROUPS).contains(element.getAsString()))
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
