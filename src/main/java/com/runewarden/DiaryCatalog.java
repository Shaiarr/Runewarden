/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** All 48 diaries and 492 exact task observations, adapted from pinned BSD-2 source. */
@Singleton
final class DiaryCatalog
{
    static final List<String> TIERS = List.of("Easy", "Medium", "Hard", "Elite");
    static final class Requirement
    {
        String kind, key, label; int value, itemId; boolean boostable, started, quantityKnown;
        List<Requirement> children;
    }
    static final class Step { String text; int[] location; List<Requirement> requirements; }
    static final class Observation
    { String kind, incompleteOperation; int variable, bit, value; boolean incompleteValue; }
    static final class Variant { String title; List<Step> steps; List<Requirement> requirements; }
    static final class Task { String id, title; List<Step> steps; List<Requirement> requirements; Observation observation; List<Variant> variants; }
    static final class Diary
    {
        String id, name, region, tier, source, wiki; Integer claimedVariable;
        List<Task> tasks; List<Requirement> requirements; List<String> rewards;
        @Override public String toString() { return name; }
    }
    private static final class Data { int version; String sourceCommit; List<Diary> diaries; }
    private final List<Diary> diaries;
    private final Map<String, Diary> index = new LinkedHashMap<>();
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final Set<Integer> varps = new LinkedHashSet<>(), varbits = new LinkedHashSet<>();
    private final List<String> regions;

    @Inject DiaryCatalog(Gson gson)
    {
        try (InputStream input = DiaryCatalog.class.getResourceAsStream("/com/runewarden/diary-catalog.json"))
        {
            if (input == null) throw new IllegalStateException("Missing diary reference");
            Data data = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.version != 1 || data.diaries == null || data.diaries.size() != 48)
                throw new IllegalStateException("Invalid diary reference");
            diaries = Collections.unmodifiableList(data.diaries);
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not load diaries", error); }
        java.util.TreeSet<String> areaNames = new java.util.TreeSet<>();
        for (Diary diary : diaries)
        {
            if (diary.id == null || diary.name == null || !TIERS.contains(diary.tier) || diary.tasks == null || diary.tasks.isEmpty()
                || index.put(diary.id, diary) != null) throw new IllegalStateException("Invalid diary");
            areaNames.add(diary.region);
            if (diary.claimedVariable != null) varbits.add(diary.claimedVariable);
            for (Task task : diary.tasks)
            {
                if (task.id == null || tasks.put(task.id, task) != null || task.observation == null)
                    throw new IllegalStateException("Invalid diary task");
                if ("varpBit".equals(task.observation.kind))
                { if (task.observation.bit < 0 || task.observation.bit > 31) throw new IllegalStateException("Invalid task bit"); varps.add(task.observation.variable); }
                else if ("varbit".equals(task.observation.kind)) varbits.add(task.observation.variable);
                else throw new IllegalStateException("Unknown observation");
            }
        }
        regions = Collections.unmodifiableList(new ArrayList<>(areaNames));
    }
    List<Diary> diaries() { return diaries; }
    List<String> regions() { return regions; }
    Diary find(String id) { return index.get(id); }
    Task task(String id) { return tasks.get(id); }
    Set<Integer> varps() { return Collections.unmodifiableSet(varps); }
    Set<Integer> varbits() { return Collections.unmodifiableSet(varbits); }
    int taskCount() { return tasks.size(); }

    static Integer completed(Task task, Map<Integer, Integer> varps, Map<Integer, Integer> varbits)
    {
        if (task == null || task.observation == null) return null;
        Observation rule = task.observation;
        Integer observed = "varpBit".equals(rule.kind) ? varps.get(rule.variable) : varbits.get(rule.variable);
        if (observed == null) return null;
        boolean incomplete;
        if ("varpBit".equals(rule.kind)) incomplete = (((observed >>> rule.bit) & 1) != 0) == rule.incompleteValue;
        else switch (rule.incompleteOperation)
        {
            case "EQUAL": incomplete = observed == rule.value; break;
            case "NOT_EQUAL": incomplete = observed != rule.value; break;
            case "LESS_EQUAL": incomplete = observed <= rule.value; break;
            case "LESS": incomplete = observed < rule.value; break;
            case "GREATER_EQUAL": incomplete = observed >= rule.value; break;
            case "GREATER": incomplete = observed > rule.value; break;
            default: return null;
        }
        return incomplete ? 0 : 1;
    }
}
