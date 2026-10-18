/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.skillcalculator.skills.FarmingAction;

/** Owned immutable data decoded from the pinned native farming definitions, with their BSD notice. */
@Singleton
public final class FarmPatchCatalog
{
    static final class Data
    { int version; String runeLiteVersion; List<Patch> patches; Map<String, List<State>> states; Map<String, Crop> crops; }
    public static final class Patch
    {
        public String id, name, region, type, group;
        public int regionId, varbit, farmer;
        public boolean healthCheck;
        public List<Integer> sentRegions;
        public String label() { return region + (name == null || name.isEmpty() ? "" : " · " + name); }
        public boolean sentAt(WorldPoint point)
        {
            if (point == null || sentRegions == null || !sentRegions.contains(point.getRegionID())) return false;
            int x = point.getX(), y = point.getY();
            if ("Catherby".equals(region))
            {
                if ("FRUIT_TREE".equals(type)) return x >= 2840 || y < 3440 || point.getPlane() == 1;
                if (x >= 2816 && y < 3456) return x < 2840 && y >= 3440 && point.getPlane() == 0;
            }
            if ("Falador".equals(region) && !"TREE".equals(type) && y < 3272) return false;
            if ("Port Sarim".equals(region)) return y < 3272;
            if ("Fossil Island".equals(region) && "HARDWOOD_TREE".equals(type))
            {
                if (point.getPlane() != 0 || x == 3753 && y >= 3868 && y <= 3870) return false;
                if ((x == 3729 || x == 3728 || x == 3747 || x == 3746) && y <= 3832 && y >= 3830) return false;
            }
            return true;
        }
    }
    public static final class State
    { public String crop, state; public int stage, stages, tickMinutes; }
    public static final class Crop
    { public String name, type; public int itemId; }
    public static final class Seed
    {
        public final int id;
        public final String name, type;
        public final Integer level;
        public final Integer quantity;
        public final String levelSource;
        public final String cropKey;
        public final int produceId, growthMinutes;
        public final Double plantingXp, healthXp, harvestXp;
        public final boolean plantingOnFirstHarvest;
        public final String xpSource;
        Seed(int id, String name, String type, Integer level, Integer quantity, String source, String key,
            int produce, int minutes, Double planting, Double health, Double harvest, boolean deferredPlanting, String xpSource)
        { this.id = id; this.name = name; this.type = type; this.level = level; this.quantity = quantity; levelSource = source;
            cropKey = key; produceId = produce; growthMinutes = minutes; plantingXp = planting; healthXp = health; harvestXp = harvest; plantingOnFirstHarvest = deferredPlanting; this.xpSource = xpSource; }
        @Override public String toString() { return name + (level == null ? "" : " · " + level); }
    }
    public static final class PaymentItem
    {
        public final int itemId;
        public final long quantity;
        public final String name;
        public final boolean notesAllowed;
        PaymentItem(RawItem raw) { itemId = raw.itemId; quantity = raw.quantity; name = raw.name; notesAllowed = raw.notesAllowed; }
    }
    public static final class Payment
    {
        public final int seedId;
        public final boolean available;
        public final String source;
        public final String reason;
        public final List<PaymentItem> items;
        Payment(RawPayment raw)
        { seedId = raw.seedId; available = raw.available; source = raw.source; reason = raw.reason == null ? "" : raw.reason; List<PaymentItem> next = new ArrayList<>(); for (RawItem item : raw.items) next.add(new PaymentItem(item)); items = Collections.unmodifiableList(next); }
    }
    private static final class PaymentData { int version; List<RawPayment> payments; List<SeedFact> seedFacts; }
    private static final class SeedFact { int seedId; Integer level, quantity; String source; }
    private static final class RawPayment { int seedId; boolean available; String source, reason; List<RawItem> items; }
    private static final class RawItem { int itemId; long quantity; String name; boolean notesAllowed; }
    private final Map<String, Patch> patches;
    private final Map<String, List<State>> states;
    private final Map<String, Crop> crops;
    private final Map<String, List<Seed>> seeds;
    private final Map<Integer, Seed> seedIndex;
    private final Map<Integer, Payment> payments;
    public FarmPatchCatalog(Gson gson) { this(gson, new SkillingResourceCatalog(gson)); }
    @Inject public FarmPatchCatalog(Gson gson, SkillingResourceCatalog resources)
    {
        Data data = gson.fromJson(new InputStreamReader(FarmPatchCatalog.class.getResourceAsStream("/com/runewarden/farming-native-snapshot.json"), StandardCharsets.UTF_8), Data.class);
        if (data == null || data.version != 1 || !"1.13.2".equals(data.runeLiteVersion) || data.patches == null || data.patches.size() > 250 || data.states == null || data.crops == null) throw new IllegalStateException("Invalid native farming snapshot");
        Map<String, Patch> next = new LinkedHashMap<>(); for (Patch patch : data.patches) next.put(patch.id, patch);
        patches = Collections.unmodifiableMap(next); states = Collections.unmodifiableMap(data.states); crops = Collections.unmodifiableMap(data.crops);
        PaymentData paymentData = gson.fromJson(new InputStreamReader(FarmPatchCatalog.class.getResourceAsStream("/com/runewarden/farming-protection-payments.json"), StandardCharsets.UTF_8), PaymentData.class);
        if (paymentData == null || paymentData.version != 1 || paymentData.payments == null || paymentData.payments.size() > 150) throw new IllegalStateException("Invalid farming payment snapshot");
        Map<Integer, SeedFact> extra = new LinkedHashMap<>();
        if (paymentData.seedFacts != null) for (SeedFact fact : paymentData.seedFacts)
        {
            if (fact == null || fact.seedId <= 0 || fact.level != null && (fact.level < 1 || fact.level > 99) || fact.quantity != null && (fact.quantity < 1 || fact.quantity > 100) || fact.source == null || !fact.source.startsWith("https://oldschool.runescape.wiki/w/")) throw new IllegalStateException("Invalid farming seed fact");
            extra.put(fact.seedId, fact);
        }
        Map<String, List<Seed>> choices = new LinkedHashMap<>();
        for (Map.Entry<String, Crop> entry : crops.entrySet())
        {
            Crop crop = entry.getValue();
            if (crop.type == null || crop.name == null || crop.name.startsWith("Any ")) continue;
            String seedName;
            if ("SEAWEED".equals(crop.type)) seedName = "Seaweed spore";
            else if ("MUSHROOM".equals(crop.type)) seedName = "Mushroom spore";
            else if ("Goutweed".equals(crop.name)) seedName = "Gout tuber";
            else if (crop.type.contains("TREE") || "CALQUAT".equals(crop.type) || "CELASTRUS".equals(crop.type) || "REDWOOD".equals(crop.type)) seedName = crop.name.replace(" tree", "") + " sapling";
            else seedName = crop.name + " seed";
            Set<Integer> ids = GuideItemResolver.resolveName(seedName);
            // Quest variants have the same display names as these ordinary allotment seeds.
            if ("Potato seed".equals(seedName)) ids = Collections.singleton(ItemID.POTATO_SEED);
            else if ("Onion seed".equals(seedName)) ids = Collections.singleton(ItemID.ONION_SEED);
            else if ("Tomato seed".equals(seedName)) ids = Collections.singleton(ItemID.TOMATO_SEED);
            if (ids.size() == 1)
            {
                int id = ids.iterator().next(); Integer level = null; String source = ""; Double planting = null, health = null, harvest = null; String xpSource = ""; boolean deferredPlanting = false;
                SkillingResourceCatalog.Resource resource = resources.find(id);
                if (resource != null) for (SkillingResourceCatalog.Action action : resource.actions())
                    if (action.skill == Skill.FARMING)
                    {
                        if ("Plant crop".equals(action.name) && action.level > 0 && action.level <= 99) { level = action.level; source = action.source; planting = action.xp; xpSource = action.source; deferredPlanting = action.note != null && action.note.contains("first harvest"); }
                        if ("Check health".equals(action.name)) { health = action.xp; xpSource = action.source; }
                        if ("Harvest crop".equals(action.name)) { harvest = action.xp; xpSource = action.source; }
                    }
                if (level == null) for (FarmingAction action : FarmingAction.values()) if (action.getIcon() == crop.itemId)
                { level = action.getLevel(); source = "RuneLite 1.13.2 FarmingAction." + action.name(); break; }
                Integer quantity = "ALLOTMENT".equals(crop.type) ? 3 : 1;
                if ("HOPS".equals(crop.type))
                {
                    quantity = null;
                    if ("Jute".equals(crop.name)) quantity = 3;
                    else if (java.util.Arrays.asList("Barley", "Hammerstone", "Asgarnian", "Yanillian", "Krandorian", "Wildblood").contains(crop.name)) quantity = 4;
                }
                SeedFact fact = extra.get(id);
                if (fact != null) { if (level == null && fact.level != null) { level = fact.level; source = fact.source; } if (fact.quantity != null) quantity = fact.quantity; }
                int growthMinutes = 0;
                for (State state : states.getOrDefault(crop.type, Collections.emptyList()))
                    if (state != null && entry.getKey().equals(state.crop) && "GROWING".equals(state.state) && state.tickMinutes > 0)
                        growthMinutes = Math.max(growthMinutes, (state.stages - 1) * state.tickMinutes);
                // Native herb Produce icons are clean herbs; harvests are the exact grimy counterpart.
                int produce = crop.itemId;
                if ("HERB".equals(crop.type))
                { String suffix = "Guam".equals(crop.name) || "Irit".equals(crop.name) ? " leaf" : "Ranarr".equals(crop.name) ? " weed" : "";
                    Set<Integer> grimy = GuideItemResolver.resolveName("Grimy " + crop.name + suffix); if (grimy.size() == 1) produce = grimy.iterator().next(); else produce = 0; }
                choices.computeIfAbsent(crop.type, ignored -> new ArrayList<>()).add(new Seed(id, seedName, crop.type, level, quantity, source,
                    entry.getKey(), produce, growthMinutes, planting, health, harvest, deferredPlanting, xpSource));
            }
        }
        choices.values().forEach(values -> values.sort(java.util.Comparator.comparing(value -> value.name)));
        Map<String, List<Seed>> frozen = new LinkedHashMap<>(); choices.forEach((type, values) -> frozen.put(type, Collections.unmodifiableList(values))); seeds = Collections.unmodifiableMap(frozen);
        Map<Integer, Seed> byId = new LinkedHashMap<>(); seeds.values().forEach(values -> values.forEach(seed -> byId.put(seed.id, seed))); seedIndex = Collections.unmodifiableMap(byId);
        Map<Integer, Payment> paymentIndex = new LinkedHashMap<>();
        for (RawPayment payment : paymentData.payments)
        {
            if (payment == null || payment.seedId <= 0 || payment.source == null || !payment.source.startsWith("https://oldschool.runescape.wiki/w/") || payment.items == null || payment.items.size() > 8) throw new IllegalStateException("Invalid farming payment");
            for (RawItem item : payment.items) if (item == null || item.itemId <= 0 || item.quantity < 1 || item.quantity > 1_000 || item.name == null || item.name.length() > 80) throw new IllegalStateException("Invalid protection item");
            paymentIndex.put(payment.seedId, new Payment(payment));
        }
        payments = Collections.unmodifiableMap(paymentIndex);
    }
    public List<Patch> patches() { return Collections.unmodifiableList(new ArrayList<>(patches.values())); }
    public Patch patch(String id) { return patches.get(id); }
    public Crop crop(String key) { return crops.get(key); }
    /** Tree labels describe the tree, so display its own sapling rather than a generic tree or fruit. */
    int iconId(State state)
    {
        Crop crop=state==null?null:crops.get(state.crop);if(crop==null)return 0;
        if(crop.type!=null && (crop.type.contains("TREE") || "CALQUAT".equals(crop.type) || "CELASTRUS".equals(crop.type) || "REDWOOD".equals(crop.type)))
            for(Seed seed:seeds(crop.type))if(java.util.Objects.equals(seed.cropKey,state.crop))return seed.id;
        return crop.itemId;
    }
    public State state(String type, int value)
    { List<State> values = states.get(type); return values == null || value < 0 || value >= values.size() ? null : values.get(value); }
    public List<Seed> seeds(String type) { return seeds.getOrDefault(type, Collections.emptyList()); }
    public Seed seed(int id) { return seedIndex.get(id); }
    public Payment payment(int seedId) { return payments.get(seedId); }
    public List<Payment> payments() { return Collections.unmodifiableList(new ArrayList<>(payments.values())); }
    public List<Patch> forGuide(String guideId)
    {
        String types;
        switch (guideId == null ? "" : guideId)
        {
            case "training:herb-runs": types = "HERB"; break;
            case "training:allotment-runs": types = "ALLOTMENT,FLOWER"; break;
            case "training:tree-runs": types = "TREE"; break;
            case "training:fruit-tree-runs": types = "FRUIT_TREE"; break;
            case "training:hardwood-runs": types = "HARDWOOD_TREE"; break;
            case "training:giant-seaweed": types = "SEAWEED"; break;
            case "training:calquat-runs": types = "CALQUAT"; break;
            case "training:crystal-tree-runs": types = "CRYSTAL_TREE"; break;
            case "training:celastrus-runs": types = "CELASTRUS"; break;
            case "training:redwood-farming": types = "REDWOOD"; break;
            case "training:hespori": types = "HESPORI"; break;
            case "training:farming-contracts": types = "GUILD"; break;
            default: return Collections.emptyList();
        }
        List<Patch> result = new ArrayList<>();
        for (Patch patch : patches.values()) if ("GUILD".equals(types) ? "Farming Guild".equals(patch.region) && !patch.type.contains("COMPOST") : ("," + types + ",").contains("," + patch.type + ",")) result.add(patch);
        result.sort(java.util.Comparator.comparing(Patch::label).thenComparing(value -> value.type)); return Collections.unmodifiableList(result);
    }
}
