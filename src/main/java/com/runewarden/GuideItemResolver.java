/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemVariationMapping;

/** Exact IDs come from the bundled, pinned item snapshot. */
public final class GuideItemResolver
{
    private static final Map<String, List<Integer>> NAMES = loadNames();

    private static Map<String, List<Integer>> loadNames()
    {
        Map<String, List<Integer>> names = new java.util.LinkedHashMap<>();
        JsonObject data = new JsonParser().parse(new InputStreamReader(
            GuideItemResolver.class.getResourceAsStream("/com/runewarden/guide-item-names.json"),
            StandardCharsets.UTF_8)).getAsJsonObject();
        data.entrySet().forEach(entry -> {
            List<Integer> ids = new java.util.ArrayList<>();
            entry.getValue().getAsJsonArray().forEach(value -> ids.add(value.getAsInt()));
            names.put(entry.getKey(), Collections.unmodifiableList(ids));
        });
        return Collections.unmodifiableMap(names);
    }

    static Map<String, List<Integer>> nameIndex() { return NAMES; }

    private GuideItemResolver() { }

    public static int resolve(JsonObject item)
    {
        Set<Integer> ids = resolveAll(item);
        return ids.isEmpty() ? -1 : ids.iterator().next();
    }

    static Set<Integer> resolveName(String name)
    {
        JsonObject item = new JsonObject(); item.addProperty("name", name); return resolveAll(item);
    }

    public static Set<Integer> resolveAll(JsonObject item)
    {
        if (item == null) return Collections.emptySet();
        Set<Integer> ids = new LinkedHashSet<>();
        for (String key : new String[]{"itemId", "id"})
        {
            if (item.has(key) && item.get(key).isJsonPrimitive())
            {
                try { int id = item.get(key).getAsInt(); if (id > 0) ids.add(id); }
                catch (RuntimeException ignored) { }
            }
        }
        if (!ids.isEmpty() || !item.has("name")) return ids;
        String name = normalize(item.get("name").getAsString());
        switch (name)
        {
            case "coins": return Collections.singleton(995);
            case "knife": return Collections.singleton(946);
            case "chisel": return Collections.singleton(1755);
            case "rope": return Collections.singleton(954);
            case "swamp paste": return Collections.singleton(1941);
            default: break;
        }
        Set<Integer> family = family(name);
        if (!family.isEmpty()) return family;
        List<Integer> exact = NAMES.get(name);
        if (exact != null) { ids.addAll(exact); return ids; }
        if (name.startsWith("any ")) name = name.substring(4);
        if (name.equals("axe") || name.equals("pickaxe"))
        {
            final String tool = name;
            NAMES.forEach((candidate, values) -> {
                if (candidate.endsWith(" " + tool) && !candidate.contains("battleaxe")
                    && (!tool.equals("axe") || !candidate.contains("pickaxe"))) ids.addAll(values);
            });
            Set<Integer> variations = new LinkedHashSet<>(ids);
            for (int id : variations) ids.addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(id)));
        }
        // Quest item lists commonly spell potion doses as "(3) or (4)".
        if (name.contains(" or"))
        {
            String[] alternatives = name.split("\\s+or\\s*");
            String prefix = alternatives[0].replaceFirst("\\s*\\([^)]*\\)$", "");
            for (String alternative : alternatives)
            {
                String candidate = alternative.startsWith("(") ? prefix + alternative : alternative;
                List<Integer> found = NAMES.get(normalize(candidate));
                if (found != null) ids.addAll(found);
            }
        }
        if (ids.isEmpty())
        {
            String plain = name.replaceFirst("^(any |an |a |some |many |several )", "")
                .replaceAll("\\((?![0-9]+\\))[^)]*\\)", "").replaceFirst("\\s+(for|to|if|when|which)\\b.*$", "").trim();
            if (plain.equals("coin")) plain = "coins";
            if (plain.equals("axe") || plain.equals("pickaxe")) return resolveName("Any " + plain);
            List<Integer> found = NAMES.get(plain);
            if (found != null) ids.addAll(found);
            if (ids.isEmpty() && plain.endsWith("s"))
            {
                found = NAMES.get(plain.substring(0, plain.length() - 1));
                if (found != null) ids.addAll(found);
            }
            final String prefix = plain.endsWith("s") ? plain.substring(0, plain.length() - 1) : plain;
            if (ids.isEmpty() && prefix.length() > 3)
                NAMES.forEach((candidate, values) -> { if (candidate.startsWith(prefix + "(")) ids.addAll(values); });
        }
        return ids;
    }

    private static Set<Integer> family(String name)
    {
        Set<Integer> ids = new LinkedHashSet<>();
        String[] members = null;
        switch (name)
        {
            case "allotment seeds": members = new String[]{"Potato seed", "Onion seed", "Cabbage seed", "Tomato seed", "Sweetcorn seed", "Strawberry seed", "Watermelon seed", "Snape grass seed"}; break;
            case "flower seeds": members = new String[]{"Marigold seed", "Rosemary seed", "Nasturtium seed", "Woad seed", "Limpwurt seed", "White lily seed"}; break;
            case "herb seeds": members = new String[]{"Guam seed", "Marrentill seed", "Tarromin seed", "Harralander seed", "Ranarr seed", "Toadflax seed", "Irit seed", "Avantoe seed", "Kwuarm seed", "Snapdragon seed", "Cadantine seed", "Lantadyme seed", "Dwarf weed seed", "Torstol seed", "Huasca seed"}; break;
            case "tree saplings": members = new String[]{"Oak sapling", "Willow sapling", "Maple sapling", "Yew sapling", "Magic sapling"}; break;
            case "fruit tree saplings": members = new String[]{"Apple sapling", "Banana sapling", "Orange sapling", "Curry sapling", "Pineapple sapling", "Papaya sapling", "Palm sapling", "Dragonfruit sapling"}; break;
            case "hardwood saplings": members = new String[]{"Teak sapling", "Mahogany sapling"}; break;
            case "any compost": members = new String[]{"Compost", "Supercompost", "Ultracompost", "Bottomless compost bucket"}; break;
            case "any watering can":
                NAMES.forEach((candidate, values) -> {
                    if (candidate.equals("watering can") || candidate.startsWith("watering can(") || candidate.equals("gricoller's can")) ids.addAll(values);
                });
                break;
            default: break;
        }
        if (members != null) for (String member : members)
        {
            List<Integer> found = NAMES.get(normalize(member));
            if (found != null) ids.addAll(found);
        }
        return ids;
    }

    static String normalize(String text)
    {
        return text.replace('\u2019', '\'').trim().toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ").replace(" (", "(");
    }
}
