/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Local, encyclopaedic monster facts. No animation, phase prediction or hover HTTP. */
@Singleton
final class MobKnowledgeCatalog
{
    static final class ElementalWeakness
    {
        String element;
        int severity;
        String label() { return title(element) + " " + severity + "%"; }
    }

    static final class Entry
    {
        int id;
        String name;
        String version;
        int size;
        int magicLevel;
        int magicAttackBonus;
        List<String> attackStyles;
        Map<String, Integer> defenceBonuses;
        ElementalWeakness elementalWeakness;
        List<String> attributes;
        boolean ambiguousStats;
    }

    static final class Card
    {
        final boolean catalogued;
        final boolean exactId;
        final String variant;
        final List<String> lines;
        final List<String> summary;
        Card(boolean catalogued, boolean exactId, String variant, List<String> lines)
        {
            this.catalogued = catalogued;
            this.exactId = exactId;
            this.variant = variant;
            this.lines = Collections.unmodifiableList(lines);
            List<String> compact = new ArrayList<>(lines.subList(0, Math.min(3, lines.size())));
            if (compact.size() == 3 && lines.size() > 3 && lines.get(3).startsWith("Lowest melee defence bonus:"))
                compact.set(1, compact.get(1) + " · " + lines.get(3));
            this.summary = Collections.unmodifiableList(compact);
        }
    }

    private static final class Data
    {
        String source;
        String commit;
        String retrieved;
        List<Entry> entries;
        Map<String, String> protectionOverrides;
    }

    private final Data data;
    private final Map<Integer, Entry> exactStats = new HashMap<>();
    private final Map<Integer, Card> byId = new HashMap<>();
    private final Map<String, Card> byName = new HashMap<>();
    private static final Card UNKNOWN = new Card(false, false, "Not catalogued", Arrays.asList(
        "Attacks: Not catalogued", "Weakness: Not catalogued", "Usual protection: Not catalogued"));

    @Inject MobKnowledgeCatalog(Gson gson)
    {
        try (InputStream stream = MobKnowledgeCatalog.class.getResourceAsStream("/com/runewarden/mob-knowledge.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing monster reference");
            data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.entries == null || data.entries.isEmpty() || data.commit == null)
                throw new IllegalStateException("Invalid monster reference");
        }
        catch (IOException error) { throw new IllegalStateException("Could not read monster reference", error); }
        Map<Integer, List<Entry>> ids = new LinkedHashMap<>();
        Map<String, List<Entry>> names = new LinkedHashMap<>();
        for (Entry entry : data.entries)
        {
            ids.computeIfAbsent(entry.id, ignored -> new ArrayList<>()).add(entry);
            names.computeIfAbsent(normalize(entry.name), ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<Integer, List<Entry>> group : ids.entrySet())
        {
            List<Entry> entries = group.getValue();
            byId.put(group.getKey(), card(entries, names.get(normalize(entries.get(0).name)), true));
            Entry first = entries.get(0);
            boolean sameStats = entries.stream().allMatch(entry -> entry.size == first.size
                && entry.magicLevel == first.magicLevel && entry.magicAttackBonus == first.magicAttackBonus);
            if (sameStats) exactStats.put(group.getKey(), first);
        }
        for (Map.Entry<String, List<Entry>> group : names.entrySet())
            byName.put(group.getKey(), card(group.getValue(), group.getValue(), false));
    }

    /** Exact-ID base stats only; shared IDs with conflicting stats deliberately return null. */
    Entry exact(int npcId) { return exactStats.get(npcId); }

    Card describe(int npcId, String npcName)
    {
        Card exact = byId.get(npcId);
        return exact != null ? exact : byName.getOrDefault(normalize(npcName), UNKNOWN);
    }

    int npcCount() { return byId.size(); }
    int variantCount() { return data.entries.size(); }
    List<Entry> search(String query)
    {
        String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);Map<Integer,Entry> found=new LinkedHashMap<>();
        if(needle.isEmpty())return Collections.emptyList();
        for(Entry e:data.entries)if(Integer.toString(e.id).equals(needle)||(e.name+" "+e.version).toLowerCase(Locale.ROOT).contains(needle))found.putIfAbsent(e.id,e);
        List<Entry> out=new ArrayList<>(found.values());out.sort(java.util.Comparator.comparing((Entry e)->e.name,String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.version,String.CASE_INSENSITIVE_ORDER));return Collections.unmodifiableList(out);
    }
    String sourceCommit() { return data.commit; }
    String retrieved() { return data.retrieved; }

    List<Entry> matching(List<String> names)
    {
        Map<Integer, Entry> matches = new LinkedHashMap<>();
        for (Entry entry : data.entries)
        {
            String monster = " " + normalize(entry.name).replaceAll("[^a-z0-9]+", " ").trim() + " ";
            for (String name : names)
            {
                String hint = normalize(name).replaceAll("[^a-z0-9]+", " ").trim();
                if (!hint.isEmpty() && monster.contains(" " + hint + " ")) { matches.putIfAbsent(entry.id, entry); break; }
            }
        }
        List<Entry> result = new ArrayList<>(matches.values());
        result.sort((a, b) -> { int order = a.name.compareToIgnoreCase(b.name); return order == 0 ? a.version.compareToIgnoreCase(b.version) : order; });
        return Collections.unmodifiableList(result);
    }

    static String wikiLookup(int npcId)
    {
        return "https://oldschool.runescape.wiki/w/Special:Lookup?type=npc&id=" + npcId;
    }

    private Card card(List<Entry> entries, List<Entry> family, boolean exact)
    {
        Entry first = entries.get(0);
        Set<String> styles = new LinkedHashSet<>();
        Set<String> versions = new LinkedHashSet<>();
        for (Entry entry : entries)
        {
            if (entry.attackStyles != null)
                for (String style : entry.attackStyles)
                {
                    String clean = clean(style);
                    if (!clean.isEmpty()) styles.add(clean);
                }
            if (entry.version != null && !entry.version.trim().isEmpty()) versions.add(clean(entry.version));
        }
        String variant = !exact ? "Name match · variants may differ"
            : versions.isEmpty() ? "NPC " + first.id
            : versions.size() == 1 ? versions.iterator().next() : "Multiple variants share NPC " + first.id;
        List<String> lines = new ArrayList<>();
        lines.add("Attacks: " + (styles.isEmpty() ? "Not documented" : String.join(" / ", styles)));
        String element = elemental(first);
        boolean sameElement = entries.stream().allMatch(entry -> elemental(entry).equals(element));
        lines.add("Elemental weakness: " + (sameElement ? element : "Varies by variant"));
        lines.add("Usual protection: " + usualProtection(family));
        boolean sameDefence = entries.stream().allMatch(entry -> Objects.equals(entry.defenceBonuses, first.defenceBonuses));
        if (!sameDefence) lines.add("Defence bonuses: Vary by variant");
        else if (first.defenceBonuses == null || first.defenceBonuses.isEmpty()) lines.add("Defence bonuses: Not documented");
        else
        {
            Map<String, Integer> bonuses = first.defenceBonuses;
            Integer stab = bonuses.get("stab"), slash = bonuses.get("slash"), crush = bonuses.get("crush");
            if (stab != null && slash != null && crush != null)
            {
                int lowest = Math.min(stab, Math.min(slash, crush));
                List<String> lowestStyles = new ArrayList<>();
                if (stab == lowest) lowestStyles.add("Stab");
                if (slash == lowest) lowestStyles.add("Slash");
                if (crush == lowest) lowestStyles.add("Crush");
                lines.add("Lowest melee defence bonus: " + String.join(" / ", lowestStyles) + " " + signed(lowest));
            }
            lines.add("Magic defence bonus: " + signed(bonuses.get("magic")));
            lines.add("Ranged defence bonus (L / S / H): " + signed(bonuses.get("light"))
                + " / " + signed(bonuses.get("standard")) + " / " + signed(bonuses.get("heavy")));
        }
        Set<String> commonAttributes = new LinkedHashSet<>(first.attributes == null ? Collections.emptyList() : first.attributes);
        for (Entry entry : entries)
            commonAttributes.retainAll(entry.attributes == null ? Collections.emptyList() : entry.attributes);
        if (!commonAttributes.isEmpty())
        {
            List<String> labels = new ArrayList<>();
            for (String attribute : commonAttributes) labels.add(title(attribute));
            lines.add("Attributes: " + String.join(" / ", labels));
        }
        lines.add("OSRS Wiki reference · " + (exact ? "NPC ID match" : "Name match"));
        return new Card(true, exact, variant, lines);
    }

    /** A fixed Wiki-style reference for the monster family, never a read of its current attack. */
    private String usualProtection(List<Entry> family)
    {
        String name = family.get(0).name;
        if (data.protectionOverrides != null && data.protectionOverrides.containsKey(name))
            return data.protectionOverrides.get(name);
        Set<String> protections = new LinkedHashSet<>();
        Set<String> attackTypes = new LinkedHashSet<>();
        boolean unsupported = false;
        for (Entry entry : family)
        {
            if (entry.attackStyles == null || entry.attackStyles.isEmpty()) { unsupported = true; continue; }
            for (String raw : entry.attackStyles)
            {
                String style = clean(raw).toLowerCase(Locale.ROOT);
                if (style.isEmpty()) continue;
                if (style.contains("100% prayer penetration") || style.startsWith("typeless"))
                {
                    protections.add("None — attacks ignore protection"); attackTypes.add("Typeless");
                }
                else if (Arrays.asList("melee", "stab", "slash", "crush", "magical melee", "ranged melee").contains(style))
                {
                    protections.add("Protect from Melee"); attackTypes.add("Melee");
                }
                else if ("ranged".equals(style)) { protections.add("Protect from Missiles"); attackTypes.add("Ranged"); }
                else if ("magic".equals(style)) { protections.add("Protect from Magic"); attackTypes.add("Magic"); }
                else if (!Arrays.asList("none", "n/a", "all", "various").contains(style))
                {
                    attackTypes.add(title(style)); unsupported = true;
                }
                else unsupported = true;
            }
        }
        if (protections.size() == 1 && !unsupported) return protections.iterator().next();
        return attackTypes.isEmpty() ? "Not verified" : "Varies — " + String.join(" + ", attackTypes);
    }

    private static String elemental(Entry entry)
    {
        ElementalWeakness weakness = entry.elementalWeakness;
        return weakness == null || weakness.element == null || weakness.severity <= 0
            || "none".equalsIgnoreCase(weakness.element) ? "None listed" : weakness.label();
    }

    private static String signed(Integer value) { return value == null ? "?" : value >= 0 ? "+" + value : value.toString(); }
    private static String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String clean(String value)
    {
        return value == null ? "" : value.replaceAll("<[^>]*>", " ").replaceAll("[\\p{Cntrl}]", "").replaceAll("\\s+", " ").trim();
    }
    private static String title(String value)
    {
        String clean = clean(value);
        return clean.isEmpty() ? clean : clean.substring(0, 1).toUpperCase(Locale.ROOT) + clean.substring(1);
    }
}
