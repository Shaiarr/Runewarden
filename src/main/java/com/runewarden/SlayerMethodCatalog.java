/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Small sourced method layer; the complete task and creature lists still come from native catalogues. */
@Singleton
final class SlayerMethodCatalog
{
    static final class Method
    {
        String id, title, effort, purpose, area, note, source;
        List<String> tasks; List<Integer> items;
        int magic, slayer; String quest;
        List<String> milestones = Collections.emptyList();
    }
    private static final class Data { int version; String checked; List<Method> methods; }
    private final List<Method> methods;
    @Inject SlayerMethodCatalog(Gson gson)
    {
        try (InputStream stream = SlayerMethodCatalog.class.getResourceAsStream("/com/runewarden/slayer-planning-methods.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing Slayer planning methods");
            Data data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.version != 1 || data.methods == null || data.methods.isEmpty()) throw new IllegalStateException("Invalid Slayer planning methods");
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (Method method : data.methods)
            {
                if (method == null || method.id == null || !ids.add(method.id) || method.tasks == null || method.tasks.isEmpty() || method.items == null || method.milestones == null
                    || method.title == null || method.area == null || method.note == null || method.source == null || !method.source.startsWith("https://oldschool.runescape.wiki/w/")
                    || method.magic < 0 || method.magic > 99 || method.slayer < 0 || method.slayer > 99
                    || !List.of("Relaxed", "Normal", "Intensive").contains(method.effort)) throw new IllegalStateException("Invalid Slayer method");
                if (method.quest != null) try { net.runelite.api.Quest.valueOf(method.quest); } catch (IllegalArgumentException invalid) { throw new IllegalStateException("Unknown method quest", invalid); }
            }
            methods = Collections.unmodifiableList(new ArrayList<>(data.methods));
        }
        catch (IOException error) { throw new IllegalStateException("Could not read Slayer planning methods", error); }
    }
    List<Method> forTask(String task)
    {
        List<Method> result = new ArrayList<>(); String key = SlayerTaskCatalog.normalize(task);
        for (Method method : methods) if (method.tasks.stream().anyMatch(name -> SlayerTaskCatalog.normalize(name).equals(key))) result.add(method);
        return Collections.unmodifiableList(result);
    }
    List<String> quests() { List<String> result = new ArrayList<>(); for (Method method : methods) if (method.quest != null && !result.contains(method.quest)) result.add(method.quest); return Collections.unmodifiableList(result); }
    int size() { return methods.size(); }
}
