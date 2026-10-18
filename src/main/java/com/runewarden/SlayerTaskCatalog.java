/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Task aliases from the pinned native Slayer plugin and separately sourced equipment facts. */
@Singleton
final class SlayerTaskCatalog
{
    static final class Task
    {
        String name;
        int icon;
        List<String> targetNames;
        List<String> hints()
        {
            List<String> result = new ArrayList<>();
            String base = name.replaceFirst("(?i)^the ", "");
            result.add(base.replaceFirst("(?i)s$", ""));
            if (targetNames != null) result.addAll(targetNames);
            return result;
        }
    }

    static final class Gear
    {
        List<String> tasks;
        String title;
        List<Integer> itemIds;
        String mode;
        boolean slayerHelmet;
        String notes;
    }

    private static final class Data { List<Task> tasks; List<Gear> gear; }
    private final Map<String, Task> tasks = new HashMap<>();
    private final List<Gear> gear;

    @Inject SlayerTaskCatalog(Gson gson)
    {
        try (InputStream stream = SlayerTaskCatalog.class.getResourceAsStream("/com/runewarden/slayer-task-reference.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing Slayer task reference");
            Data data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            for (Task task : data.tasks) tasks.put(normalize(task.name), task);
            gear = data.gear;
        }
        catch (IOException error) { throw new IllegalStateException("Could not read Slayer task reference", error); }
    }

    Task task(String name)
    {
        Task found = tasks.get(normalize(name));
        if (found != null) return found;
        Task fallback = new Task(); fallback.name = name == null ? "" : name; fallback.targetNames = Collections.emptyList(); return fallback;
    }

    List<Gear> gear(String task)
    {
        String name = normalize(task);
        List<Gear> result = new ArrayList<>();
        for (Gear rule : gear)
            for (String candidate : rule.tasks) if (normalize(candidate).equals(name)) { result.add(rule); break; }
        return result;
    }

    int taskCount() { return tasks.size(); }
    static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceFirst("^the ", "").replaceAll("[^a-z0-9]", ""); }
}
