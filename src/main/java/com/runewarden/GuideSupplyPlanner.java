/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit per-guide supply policy, including item-free and player-choice activities. */
final class GuideSupplyPlanner
{
    enum GearView
    {
        GUIDE("Guide default", null), MELEE("Melee", "melee"), RANGED("Ranged", "ranged"),
        MAGIC("Magic", "magic"), ALL("All alternatives", "all");
        final String label;
        final String style;
        GearView(String label, String style) { this.label = label; this.style = style; }
        @Override public String toString() { return label; }
    }

    static final class Metadata
    {
        String kind;
        List<Integer> itemIds;
        List<String> itemNames;
        List<String> families;
        List<String> styles;
        Map<String, List<Integer>> styleItemIds;
        List<String> source;
        String notes;
        List<String> unresolved;
    }

    static final class Plan
    {
        final String kind;
        final Set<Integer> itemIds;
        final Set<String> families;
        final List<String> unresolved;
        final String notes;
        final boolean audited;
        Plan(String kind, Set<Integer> itemIds, Set<String> families, List<String> unresolved, String notes, boolean audited)
        {
            this.kind = kind; this.itemIds = Collections.unmodifiableSet(itemIds);
            this.families = Collections.unmodifiableSet(families); this.unresolved = Collections.unmodifiableList(unresolved);
            this.notes = notes == null ? "" : notes; this.audited = audited;
        }
        boolean isItemFree() { return "none".equals(kind); }
        boolean hasGearView() { return "loadout".equals(kind) || families.stream().anyMatch(f -> f.endsWith("-equipment")); }
    }

    private static final Map<String, Metadata> METHODS = read("guide-supply-plans.json");
    private static final Map<String, Metadata> LOADOUTS = read("guide-loadouts.json");

    private GuideSupplyPlanner() { }

    static Plan resolve(GuideCatalog.Entry guide, Set<Integer> knownItems, int unresolvedLegacy, GearView view)
    { return resolve(guide, knownItems, unresolvedLegacy, view, Collections.emptySet()); }

    static Plan resolve(GuideCatalog.Entry guide, Set<Integer> knownItems, int unresolvedLegacy, GearView view, Set<Integer> chosenItems)
    {
        if (guide == null) return new Plan("none", new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>(), "", true);
        Metadata metadata = LOADOUTS.get(guide.id);
        if (metadata == null) metadata = METHODS.get(guide.id);
        boolean audited = metadata != null;
        if (metadata == null) metadata = fallback(guide, unresolvedLegacy);
        Set<Integer> ids = audited ? new LinkedHashSet<>() : new LinkedHashSet<>(knownItems);
        Set<String> families = new LinkedHashSet<>();
        List<String> unresolved = metadata.unresolved == null ? new ArrayList<>() : new ArrayList<>(metadata.unresolved);
        if (metadata.itemIds != null) ids.addAll(metadata.itemIds);
        if (metadata.itemNames != null) for (String name : metadata.itemNames)
        {
            Set<Integer> named = GuideItemResolver.resolveName(name);
            if (named.isEmpty()) unresolved.add("Item: " + name); else ids.addAll(named);
        }
        if (metadata.families != null) for (String family : metadata.families)
        {
            family = canonicalFamily(family);
            if (GuideBankFamilies.DYNAMIC.contains(family)) families.add(family);
            else
            {
                Set<Integer> named = GuideItemResolver.resolveName(family.replace('-', ' '));
                if (named.isEmpty()) unresolved.add("Supply group: " + family); else ids.addAll(named);
            }
        }
        List<String> pending = new ArrayList<>();
        for (String requirement : unresolved)
        {
            Set<Integer> named = GuideItemResolver.resolveName(requirement);
            if (!named.isEmpty()) { ids.addAll(named); continue; }
            if (!coveredGeneralRequirement(requirement, families)) pending.add(requirement);
        }
        unresolved = pending;
        boolean gear = "loadout".equals(metadata.kind) || metadata.styles != null
            || families.stream().anyMatch(f -> f.endsWith("-equipment"));
        if (gear)
        {
            Set<String> styles = new LinkedHashSet<>();
            if (view == GearView.ALL) styles.addAll(Arrays.asList("melee", "ranged", "magic"));
            else if (view.style != null) styles.add(view.style);
            else if (metadata.styles != null) styles.addAll(metadata.styles);
            else for (String style : Arrays.asList("melee", "ranged", "magic"))
                if (families.contains(style + "-equipment")) styles.add(style);
            if (styles.isEmpty()) styles.addAll(Arrays.asList("melee", "ranged", "magic"));
            families.removeIf(family -> family.endsWith("-equipment"));
            for (String style : styles)
            {
                families.add(style + "-equipment");
                if (metadata.styleItemIds != null && metadata.styleItemIds.get(style) != null)
                    ids.addAll(metadata.styleItemIds.get(style));
            }
            // Fixed training spells and restricted minigames keep their authored supply policy.
            if (LOADOUTS.containsKey(guide.id))
            {
                if (styles.contains("ranged")) families.add("ammunition");
                if (styles.contains("magic")) families.add("runes");
            }
        }
        String kind = metadata.kind == null ? (gear ? "loadout" : "items") : metadata.kind;
        if (guide.skill != null && !"none".equals(kind)) families.add("skilling-outfit");
        if ("none".equals(kind)) { ids.clear(); families.clear(); unresolved.clear(); }
        else ids.addAll(chosenItems);
        return new Plan(kind, ids, families, unresolved, metadata.notes, audited);
    }

    static Metadata metadata(String id) { return LOADOUTS.containsKey(id) ? LOADOUTS.get(id) : METHODS.get(id); }
    static Set<String> auditedIds()
    { Set<String> ids = new LinkedHashSet<>(METHODS.keySet()); ids.addAll(LOADOUTS.keySet()); return ids; }

    private static Metadata fallback(GuideCatalog.Entry guide, int unresolved)
    {
        Metadata metadata = new Metadata(); metadata.kind = "items"; metadata.families = new ArrayList<>();
        if ("markers".equals(guide.category))
        { metadata.kind = "none"; metadata.notes = "Marker layout only; select an encounter guide for supplies."; return metadata; }
        if ("boss".equals(guide.category) || "waves".equals(guide.category)
            || Set.of("attack", "strength", "defence", "ranged", "magic", "hitpoints", "slayer").contains(guide.skill == null ? "" : guide.skill))
        {
            metadata.kind = "loadout"; metadata.styles = Arrays.asList("melee", "ranged", "magic");
            metadata.families.addAll(Arrays.asList("food", "prayer-restoration", "combat-potions", "teleports"));
            metadata.notes = "Choose the equipment view for your method.";
        }
        if (guide.skill != null) metadata.families.add("skilling-outfit");
        metadata.unresolved = unresolved > 0 ? Collections.singletonList("Check this guide's general requirements") : Collections.emptyList();
        return metadata;
    }

    private static String canonicalFamily(String family)
    {
        switch (family)
        {
            case "combat-boosts": return "combat-potions";
            case "magic-runes": return "runes";
            case "poison-protection": return "antipoison";
            default: return family;
        }
    }

    private static boolean coveredGeneralRequirement(String requirement, Set<String> families)
    {
        String text = requirement.toLowerCase(java.util.Locale.ROOT);
        // These express choices, branches or account-specific requests rather than an item alias.
        if (text.matches(".*\\b(select|choose|random|assigned|depends|variant|varies|quantity must be tested)\\b.*")) return false;
        if (text.startsWith("access to ") || text.startsWith("complete ") || text.startsWith("required entry levels")
            || text.startsWith("no pet ") || text.startsWith("you can get all the required items")) return true;
        if ((text.contains("teleport") || text.contains("transport") || text.contains("fairy ring")) && families.contains("teleports")) return true;
        if ((text.contains("combat gear") || text.contains("combat equipment") || text.equals("armour") || text.contains("weapon and armour"))
            && families.stream().anyMatch(f -> f.endsWith("-equipment"))) return true;
        if (text.contains("food") && families.contains("food") && !text.contains("type of food")) return true;
        if ((text.contains("antipoison") || text.contains("antidote") || text.contains("anti-venom")) && families.contains("antipoison")) return true;
        if ((text.contains("stamina") || text.contains("run energy") || text.contains("energy restor")) && families.contains("run-energy")) return true;
        if (text.contains("prayer") && (text.contains("potion") || text.contains("restore")) && families.contains("prayer-restoration")) return true;
        if ((text.contains("runes") || text.contains("combat spell")) && families.contains("runes")) return true;
        if (text.contains("compost") && families.contains("any-compost")) return true;
        if (text.contains("seed") && families.contains("seeds")) return true;
        if (text.contains("sapling") && families.contains("saplings")) return true;
        if (text.contains("log") && families.contains("logs")) return true;
        if (text.contains("nail") && families.contains("nails")) return true;
        return false;
    }

    private static Map<String, Metadata> read(String resource)
    {
        try (InputStream input = GuideSupplyPlanner.class.getResourceAsStream("/com/runewarden/" + resource))
        {
            if (input == null) return Collections.emptyMap();
            JsonObject root = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Metadata> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet())
            {
                JsonObject source = entry.getValue().getAsJsonObject();
                Metadata metadata = new Metadata();
                metadata.kind = string(source, "kind"); metadata.notes = string(source, "notes");
                metadata.itemIds = integers(source.get("itemIds"));
                metadata.itemNames = strings(source.get("itemNames"));
                metadata.families = strings(source.get("families"));
                metadata.styles = strings(source.get("styles"));
                metadata.source = strings(source.get("source"));
                metadata.unresolved = strings(source.get("unresolved"));
                if (source.has("styleItemIds") && source.get("styleItemIds").isJsonObject())
                {
                    metadata.styleItemIds = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> style : source.getAsJsonObject("styleItemIds").entrySet())
                        metadata.styleItemIds.put(style.getKey(), integers(style.getValue()));
                }
                result.put(entry.getKey(), metadata);
            }
            return Collections.unmodifiableMap(result);
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read supply metadata " + resource, error); }
    }

    private static String string(JsonObject object, String key)
    { JsonElement value = object.get(key); return value == null || value.isJsonNull() ? null : value.getAsString(); }

    private static List<String> strings(JsonElement array)
    {
        if (array == null || array.isJsonNull()) return null;
        List<String> result = new ArrayList<>();
        for (JsonElement value : array.getAsJsonArray()) result.add(value.getAsString());
        return result;
    }

    private static List<Integer> integers(JsonElement array)
    {
        if (array == null || array.isJsonNull()) return null;
        List<Integer> result = new ArrayList<>();
        for (JsonElement value : array.getAsJsonArray()) result.add(value.getAsInt());
        return result;
    }
}
