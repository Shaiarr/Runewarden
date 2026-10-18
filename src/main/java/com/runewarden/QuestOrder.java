/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local snapshot of the Wiki's regular-account route; never invents positions for absent quests. */
final class QuestOrder
{
    private static final class Data
    {
        String source;
        int revision;
        String retrieved;
        List<String> order;
        Map<String, String> aliases;
    }
    private final Data data;
    private final Map<String, Integer> ranks = new HashMap<>();

    QuestOrder(Gson gson)
    {
        try (InputStream input = QuestOrder.class.getResourceAsStream("/com/runewarden/quest-order.json"))
        {
            if (input == null) throw new IllegalStateException("Missing quest order");
            data = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Data.class);
            if (data.order == null || data.order.isEmpty() || data.revision <= 0)
                throw new IllegalStateException("Invalid quest order");
            for (int i = 0; i < data.order.size(); i++)
                if (ranks.put(normalize(data.order.get(i)), i + 1) != null)
                    throw new IllegalStateException("Duplicate quest route row");
            for (Map.Entry<String, String> alias : data.aliases.entrySet())
            {
                Integer rank = ranks.get(normalize(alias.getValue()));
                if (rank == null) throw new IllegalStateException("Unknown quest route alias");
                ranks.put(normalize(alias.getKey()), rank);
            }
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read quest order", error); }
    }

    static String normalize(String title)
    {
        return Normalizer.normalize(title, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    int rank(String title) { return ranks.getOrDefault(normalize(title), Integer.MAX_VALUE); }
    int rank(GuideCatalog.Entry entry) { return rank(entry.title); }
    boolean ranked(GuideCatalog.Entry entry) { return rank(entry) != Integer.MAX_VALUE; }
    String source() { return data.source + "?oldid=" + data.revision; }
    int revision() { return data.revision; }
    int rowCount() { return data.order.size(); }

    List<GuideCatalog.Entry> filter(List<GuideCatalog.Entry> entries, boolean optimal,
        boolean hideCompleted, Set<String> finished)
    {
        List<GuideCatalog.Entry> result = new ArrayList<>();
        for (GuideCatalog.Entry entry : entries)
            if (!hideCompleted || !"quest".equals(entry.category) || !finished.contains(entry.id)) result.add(entry);
        if (optimal) result.sort((a, b) -> {
            int order = Integer.compare(rank(a), rank(b));
            return order != 0 ? order : a.title.compareToIgnoreCase(b.title);
        });
        return result;
    }
}
