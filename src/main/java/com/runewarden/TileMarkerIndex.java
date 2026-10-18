/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable browser metadata for the bundled packs; constructing this index never selects an overlay. */
final class TileMarkerIndex
{
    enum Category
    {
        BOSSES("Bosses"), RAIDS("Raids"), WAVES("Wave encounters"), MINIGAMES("Minigames"),
        SKILLING("Skilling"), COMBAT_TRAINING("Combat training"), OTHER("Other");

        final String title;
        Category(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    static final class Group
    {
        final String id;
        final String title;
        final List<GuideCatalog.MarkerPack> packs;

        private Group(String id, String title, List<GuideCatalog.MarkerPack> packs)
        {
            this.id = id;
            this.title = title;
            this.packs = Collections.unmodifiableList(new ArrayList<>(packs));
        }
    }

    private static final Pattern FIGHT_CAVES = Pattern.compile("fight-caves-r(\\d+)-w(\\d+)");
    private static final Set<String> CHAMBERS = Set.of("crab puzzle", "olm", "vanguards");
    private static final Set<String> THEATRE = Set.of("nylocas vasilias", "pestilent bloat", "sotetseg",
        "the maiden of sugadinti", "tob (all)", "verzik vitur", "xarpus");
    private static final Set<String> TOMBS = Set.of("akkha (butterfly tech)", "baba",
        "tumekens warden (advanced tactics)", "zebak (challenge room)");
    private static final Set<String> INFERNO = Set.of("inferno", "inferno-opening-low", "inferno-opening-late",
        "inferno-jad-reference", "inferno-triple-jad", "inferno-zuk");
    private static final Set<String> COLOSSEUM = Set.of("colosseum-wave-reference", "colosseum-sol-reference");
    private static final Set<String> MINIGAMES = Set.of("barbarian assault", "hallowed sepulchre",
        "tithe farm 20x5", "tithe farm 25x4", "volcanic mine", "werewolf skullball");
    private static final Set<String> SKILLING = Set.of("drift net fishing", "gem mining (3-tick)", "stealing artefacts");
    private static final Set<String> COMBAT_TRAINING = Set.of("ammonite crabs", "sand crabs");
    // These categories are curated browser metadata, not a field supplied by the source repositories.
    // An unfamiliar future ID goes to Other rather than being guessed to be a boss.
    private static final Set<String> BOSSES = Set.of("abyssal sire", "alchemical hydra", "barrows (dig tiles)",
        "commander zilyana (energy-efficient)", "commander zilyana", "demonic brutus", "doom (double park)",
        "doom (sidewalk)", "duke sucellus", "galvek", "general graardor (5-0)", "general graardor (6-0)",
        "general graardor (7-0 Bowfa)", "general graardor (9-0 Bowfa)", "grotesque guardians",
        "k'ril tsutsaroth (6-0)", "maggot king", "moons of peril", "phantom muspah", "sarachnis",
        "the leviathan", "the whisperer", "vardorvis", "vorkath", "yama (godfly duo partner)", "yama (godfly)",
        "yama (simple waves)", "yama (simplefly)", "zulrah", "hueycoatl-cues");

    private final List<Category> categories;
    private final Map<Category, List<Group>> groups;

    TileMarkerIndex(GuideCatalog catalog)
    {
        this(catalog.markerPacks());
    }

    TileMarkerIndex(List<GuideCatalog.MarkerPack> packs)
    {
        Map<Category, Map<String, MutableGroup>> building = new EnumMap<>(Category.class);
        for (GuideCatalog.MarkerPack pack : packs)
        {
            String id = pack.id;
            Matcher fight = FIGHT_CAVES.matcher(id);
            Category category;
            String groupId;
            String groupTitle;
            if (fight.matches())
            {
                category = Category.WAVES;
                groupId = "fight-caves-r" + Integer.parseInt(fight.group(1));
                groupTitle = "Fight Caves · Rotation " + Integer.parseInt(fight.group(1));
            }
            else if (INFERNO.contains(id))
            {
                category = Category.WAVES; groupId = "inferno"; groupTitle = "Inferno";
            }
            else if (COLOSSEUM.contains(id))
            {
                category = Category.WAVES; groupId = "colosseum"; groupTitle = "Fortis Colosseum";
            }
            else if (CHAMBERS.contains(id))
            {
                category = Category.RAIDS; groupId = "chambers-of-xeric"; groupTitle = "Chambers of Xeric";
            }
            else if (THEATRE.contains(id))
            {
                category = Category.RAIDS; groupId = "theatre-of-blood"; groupTitle = "Theatre of Blood";
            }
            else if (TOMBS.contains(id))
            {
                category = Category.RAIDS; groupId = "tombs-of-amascut"; groupTitle = "Tombs of Amascut";
            }
            else
            {
                category = MINIGAMES.contains(id) ? Category.MINIGAMES
                    : SKILLING.contains(id) ? Category.SKILLING
                    : COMBAT_TRAINING.contains(id) ? Category.COMBAT_TRAINING
                    : BOSSES.contains(id) ? Category.BOSSES : Category.OTHER;
                if (id.startsWith("tithe farm ")) { groupId = "tithe-farm"; groupTitle = "Tithe Farm"; }
                else if (id.equals("hueycoatl-cues")) { groupId = "hueycoatl"; groupTitle = "Hueycoatl"; }
                else
                {
                    int variant = id.indexOf(" (");
                    groupId = variant < 0 ? id : id.substring(0, variant);
                    groupTitle = pack.title == null || pack.title.isEmpty() ? groupId : pack.title;
                }
            }
            Map<String, MutableGroup> activity = building.computeIfAbsent(category, ignored -> new LinkedHashMap<>());
            MutableGroup group = activity.get(groupId);
            if (group == null) { group = new MutableGroup(groupId, groupTitle); activity.put(groupId, group); }
            group.packs.add(pack);
        }

        List<Category> available = new ArrayList<>();
        Map<Category, List<Group>> indexed = new EnumMap<>(Category.class);
        for (Category category : Category.values())
        {
            Map<String, MutableGroup> activity = building.get(category);
            if (activity == null || activity.isEmpty()) continue;
            List<Group> values = new ArrayList<>();
            for (MutableGroup group : activity.values())
            {
                Comparator<GuideCatalog.MarkerPack> order = group.id.startsWith("fight-caves-r")
                    ? Comparator.comparingInt(TileMarkerIndex::waveNumber)
                    : Comparator.comparing(TileMarkerIndex::label, String.CASE_INSENSITIVE_ORDER);
                group.packs.sort(order);
                values.add(new Group(group.id, group.title, group.packs));
            }
            values.sort((left, right) -> {
                boolean leftCaves = left.id.startsWith("fight-caves-r");
                boolean rightCaves = right.id.startsWith("fight-caves-r");
                if (leftCaves && rightCaves) return Integer.compare(rotationNumber(left.id), rotationNumber(right.id));
                return left.title.compareToIgnoreCase(right.title);
            });
            available.add(category);
            indexed.put(category, Collections.unmodifiableList(values));
        }
        categories = Collections.unmodifiableList(available);
        groups = Collections.unmodifiableMap(indexed);
    }

    List<Category> categories() { return categories; }
    List<Group> groups(Category category) { return groups.getOrDefault(category, Collections.emptyList()); }

    static String label(GuideCatalog.MarkerPack pack)
    {
        Matcher fight = FIGHT_CAVES.matcher(pack.id);
        if (fight.matches()) return "Wave " + Integer.parseInt(fight.group(2));
        String title = pack.title == null || pack.title.isEmpty() ? pack.id : pack.title;
        int variant = pack.id.indexOf(" (");
        if (variant < 0 || !pack.id.endsWith(")")) return title;
        String detail = pack.id.substring(variant + 2, pack.id.length() - 1);
        // Tithe Farm titles already carry their route variant; avoid adding it twice.
        if (title.toLowerCase(Locale.ROOT).contains("(" + detail.toLowerCase(Locale.ROOT) + ")")) return title;
        return title + " · " + detail;
    }

    private static int waveNumber(GuideCatalog.MarkerPack pack)
    {
        Matcher fight = FIGHT_CAVES.matcher(pack.id);
        return fight.matches() ? Integer.parseInt(fight.group(2)) : Integer.MAX_VALUE;
    }

    private static int rotationNumber(String groupId)
    {
        return Integer.parseInt(groupId.substring("fight-caves-r".length()));
    }

    private static final class MutableGroup
    {
        private final String id;
        private final String title;
        private final List<GuideCatalog.MarkerPack> packs = new ArrayList<>();
        private MutableGroup(String id, String title) { this.id = id; this.title = title; }
    }
}
