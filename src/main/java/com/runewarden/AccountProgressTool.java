/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.ConfigManager;

/** Shared observed progression. Persisted facts retain provenance and never fill unseen state with zero. */
@Singleton
final class AccountProgressTool
{
    static final String KEY = "accountProgressV1";
    private static final int MAX_FACTS = 3000;
    private static final Pattern KC = Pattern.compile("Your (.+?) (?:kill|completion) count is: ([\\d,]+)\\.?", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETED_KC = Pattern.compile("Your completed (.+?) count is: ([\\d,]+)\\.?", Pattern.CASE_INSENSITIVE);
    private static final String COLLECTION = "New item added to your collection log: ";
    static final class Metric
    {
        final String key, label, kind; final int variable; final boolean spendable;
        Metric(String key, String label, String kind, int variable, boolean spendable)
        { this.key = key; this.label = label; this.kind = kind; this.variable = variable; this.spendable = spendable; }
        @Override public String toString() { return label; }
    }
    static final List<Metric> METRICS = List.of(
        new Metric("kudos", "Museum Kudos", "varbit", VarbitID.VM_KUDOS, false),
        new Metric("rigour", "Rigour unlocked", "varbit", VarbitID.PRAYER_RIGOUR_UNLOCKED, false),
        new Metric("augury", "Augury unlocked", "varbit", VarbitID.PRAYER_AUGURY_UNLOCKED, false),
        new Metric("arceuus", "Arceuus spellbook unlocked", "varbit", VarbitID.ARCEUUS_SPELLBOOK_UNLOCKED, false),
        new Metric("slayer-points", "Slayer points", "varbit", VarbitID.SLAYER_POINTS, true),
        new Metric("tithe-points", "Tithe Farm points", "varbit", VarbitID.HOSIDIUS_TITHE_REWARDPOINTS, true),
        new Metric("foundry-points", "Giants' Foundry reward points", "varp", VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, true),
        new Metric("nmz-points", "Nightmare Zone reward points", "varp", VarPlayerID.NZONE_REWARDPOINTS, true),
        new Metric("soulwars-zeal", "Soul Wars zeal", "varp", VarPlayerID.SOUL_WARS_ZEAL_TOKENS, true),
        new Metric("mixology-lye", "Mixology lye points", "varp", VarPlayerID.MIXOLOGY_LYE_POINTS, true),
        new Metric("mixology-aga", "Mixology aga points", "varp", VarPlayerID.MIXOLOGY_AGA_POINTS, true),
        new Metric("mixology-mox", "Mixology mox points", "varp", VarPlayerID.MIXOLOGY_MOX_POINTS, true)
    );
    static final class Saved
    { int version = 1; Map<Integer, Long> collection = new LinkedHashMap<>(); Map<String, Long> killCounts = new LinkedHashMap<>(); Map<String, String> killNames = new LinkedHashMap<>(); long collectionObservedAt; }
    static final class Snapshot
    {
        final String profile; final long generation; final Map<String, Long> values;
        final Map<Integer, Long> collection; final Map<String, Long> killCounts;
        final Map<String, String> killNames;
        final Map<Skill, Integer> levels, boostedLevels; final Map<String, QuestState> quests;
        final long collectionObservedAt; final int collectionMaximum; final boolean live;
        Snapshot(String profile, long generation, Map<String, Long> values, Saved saved,
            Map<Skill, Integer> levels, Map<String, QuestState> quests, int maximum, boolean live)
        { this(profile, generation, values, saved, levels, levels, quests, maximum, live); }
        Snapshot(String profile, long generation, Map<String, Long> values, Saved saved,
            Map<Skill, Integer> levels, Map<Skill, Integer> boosted, Map<String, QuestState> quests, int maximum, boolean live)
        {
            this.boostedLevels = Collections.unmodifiableMap(new EnumMap<>(boosted));
            this.profile = profile; this.generation = generation; this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            collection = Collections.unmodifiableMap(new LinkedHashMap<>(saved.collection)); killCounts = Collections.unmodifiableMap(new LinkedHashMap<>(saved.killCounts));
            killNames = Collections.unmodifiableMap(new LinkedHashMap<>(saved.killNames));
            this.levels = Collections.unmodifiableMap(new EnumMap<>(levels)); this.quests = Collections.unmodifiableMap(new LinkedHashMap<>(quests));
            collectionObservedAt = saved.collectionObservedAt; collectionMaximum = maximum; this.live = live;
        }
        static Snapshot empty() { return new Snapshot(null, 0, Collections.emptyMap(), new Saved(), new EnumMap<>(Skill.class), Collections.emptyMap(), 0, false); }
        Long value(String key) { return values.get(key); }
    }
    private final Client client; private final ConfigManager config; private final Gson gson;
    private final DiaryCatalog diaries; private final RarityCatalog rarity;
    private final MusicUnlockTool music; private final CombatAchievementsTool combat;
    private final MusicUnlockCatalog musicCatalog; private final CombatAchievementCatalog combatCatalog;
    private final OwnershipLedger ownership; private final RunewardenDataStore dataStore;
    private Saved saved = new Saved(); private volatile String profile; private volatile long generation; private int ticks, warmup;
    private boolean started, collectionNotificationStarted; private volatile Snapshot snapshot = Snapshot.empty();
    private JPanel panel, body; private JComboBox<String> mode; private JTextField search; private int page;
    private Consumer<Opportunity> openOpportunity = ignored -> { };
    void setOpenOpportunityAction(Consumer<Opportunity> action) { openOpportunity = action == null ? ignored -> { } : action; }
    private Consumer<GoalPlan> addGoal = ignored -> { }; private java.util.function.IntConsumer openItem = ignored -> { };
    private final Map<String, Quest> questIndex = new LinkedHashMap<>();

    @Inject AccountProgressTool(Client client, ConfigManager config, Gson gson, DiaryCatalog diaries, RarityCatalog rarity,
        MusicUnlockTool music, CombatAchievementsTool combat, OwnershipLedger ownership, RunewardenDataStore dataStore,
        MusicUnlockCatalog musicCatalog, CombatAchievementCatalog combatCatalog)
    {
        this.client = client; this.config = config; this.gson = gson; this.diaries = diaries; this.rarity = rarity;
        this.music = music; this.combat = combat; this.ownership = ownership; this.dataStore = dataStore;
        this.musicCatalog = musicCatalog; this.combatCatalog = combatCatalog;
        for (Quest quest : Quest.values()) questIndex.put(GoalCatalog.questKey(quest.getName()), quest);
    }
    void setGoalAction(Consumer<GoalPlan> action) { addGoal = action == null ? ignored -> { } : action; }
    void setOpenItemAction(java.util.function.IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear() { profile = null; generation++; ticks = 0; warmup = 3; collectionNotificationStarted = false; saved = new Saved(); snapshot = Snapshot.empty(); requestRender(); }
    Snapshot reading()
    { Snapshot value = snapshot; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value : Snapshot.empty(); }
    DiaryCatalog diaryCatalog() { return diaries; }
    OwnershipLedger ownership() { return ownership; }
    CombatAchievementCatalog.Task combatTask(int id) { return combatCatalog.find(id); }
    List<CombatAchievementCatalog.Requirement> combatRequirements(int id) { return combat.requirements(id); }
    MusicUnlockCatalog.Track musicTrack(int id) { for (MusicUnlockCatalog.Track track : musicCatalog.tracks()) if (track.id == id) return track; return null; }
    GuideCatalog.Entry musicQuestGuide(int id) { MusicUnlockCatalog.Track track = musicTrack(id); return track == null ? null : musicCatalog.guide(track.quest); }
    Set<Integer> goalSupplyTargets(GoalPlan goal)
    {
        Set<Integer> ids=new LinkedHashSet<>();if(goal==null||goal.archived||goal.achievedAt>0)return Collections.emptySet();
        if("diary".equals(goal.kind))
        {DiaryCatalog.Diary diary=diaries.find(goal.referenceKey);if(diary!=null){collectSupplyIds(diary.requirements,ids);Snapshot current=reading();for(DiaryCatalog.Task task:diary.tasks)if(!Long.valueOf(1).equals(current.value("task:"+task.id))){collectSupplyIds(task.requirements,ids);if(task.variants!=null)for(DiaryCatalog.Variant variant:task.variants)collectSupplyIds(variant.requirements,ids);}}}
        else if("ca".equals(goal.kind))
        {CombatAchievementCatalog.Task task=combatTask(Integer.parseInt(goal.referenceKey));if(task!=null)for(CombatAchievementCatalog.ItemReference item:task.items)if(item.itemId>0&&!GuideIllustrationItems.IDS.contains(item.itemId)&&GuideItemResolver.resolveName(item.name).contains(item.itemId))ids.add(item.itemId);}
        else if("music".equals(goal.kind))
        {MusicUnlockCatalog.Track track=musicTrack(Integer.parseInt(goal.referenceKey));if(track!=null)for(MusicUnlockCatalog.Route route:track.routes)for(MusicUnlockCatalog.RequiredItem item:route.items)if(item.id>0&&item.quantity>0)ids.add(item.id);}
        return Collections.unmodifiableSet(ids);
    }
    private static void collectSupplyIds(List<DiaryCatalog.Requirement> requirements,Set<Integer> ids)
    {if(requirements!=null)for(DiaryCatalog.Requirement requirement:requirements){if("item".equals(requirement.kind)){if(requirement.itemId>0)ids.add(requirement.itemId);else for(int id:GuideItemResolver.resolveName(requirement.label))if(!GuideIllustrationItems.IDS.contains(id))ids.add(id);}collectSupplyIds(requirement.children,ids);}}
    List<String> benefits(GoalPlan goal)
    {
        if ("diary".equals(goal.kind)) { DiaryCatalog.Diary diary = diaries.find(goal.referenceKey); return diary == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(diary.rewards)); }
        if ("ca".equals(goal.kind)) { CombatAchievementCatalog.Task task = combatTask(Integer.parseInt(goal.referenceKey)); return task == null ? Collections.emptyList() : List.of(task.points + " combat achievement points", task.boss); }
        if ("music".equals(goal.kind)) { MusicUnlockCatalog.Track track = musicTrack(Integer.parseInt(goal.referenceKey)); return track == null ? Collections.emptyList() : List.of(track.unlock); }
        return Collections.emptyList();
    }
    String goalGuide(GoalPlan goal)
    {
        if ("ca".equals(goal.kind)) { CombatAchievementCatalog.Task task = combatTask(Integer.parseInt(goal.referenceKey)); return task == null ? null : task.guideId; }
        if ("kc".equals(goal.kind)) for (CombatAchievementCatalog.Task task : combatCatalog.tasks()) if (normalize(task.boss).equals(normalize(goal.referenceKey)) && task.guideId != null) return task.guideId;
        return null;
    }
    static final class Opportunity
    {
        final String id, title, detail, module, reference; final int priority; final Set<Integer> itemTargets;
        private final GoalPlan target;
        Opportunity(String id, String title, String detail, String module, String reference, int priority, GoalPlan target, Set<Integer> items)
        { this.id=id;this.title=title;this.detail=detail;this.module=module;this.reference=reference;this.priority=priority;this.target=target==null?null:target.copy();this.itemTargets=Collections.unmodifiableSet(new LinkedHashSet<>(items)); }
        GoalPlan goal() { return target==null?null:target.copy(); }
    }
    /** Read-only suggestions use only observed account state and the bundled source facts. */
    List<Opportunity> opportunities()
    {
        Snapshot current=reading(); if(!current.live)return Collections.emptyList(); List<Opportunity> result=new ArrayList<>();
        for(DiaryCatalog.Diary diary:diaries.diaries())
        {
            Long done=current.value("diary:"+diary.id),claimed=current.value("claimed:"+diary.id);if(done==null)continue;
            if(done==diary.tasks.size()&&Long.valueOf(0).equals(claimed))
            {result.add(new Opportunity("claim:"+diary.id,"Claim "+diary.name,"All "+done+" tasks complete · reward unclaimed","Achievement diaries",diary.id,0,null,Set.of()));continue;}
            if(done>=diary.tasks.size())continue;int ready=0,missing=0;Set<Integer> supplies=new LinkedHashSet<>();
            for(DiaryCatalog.Task task:diary.tasks)if(!Long.valueOf(1).equals(current.value("task:"+task.id)))
            {
                boolean known=!task.requirements.isEmpty(),blocked=false;for(DiaryCatalog.Requirement need:task.requirements){int status=requirementState(need);blocked|=status<0;known&=status>0;}
                if(known)ready++;if(blocked)missing++;supplies.addAll(DiaryPlannerTool.itemTargets(task.requirements));
            }
            if(ready>0||done>0)result.add(new Opportunity("diary:"+diary.id,diary.name,(diary.tasks.size()-done)+" tasks left · "+ready+" prepared"+(missing>0?" · "+missing+" need unlocks":""),"Achievement diaries",diary.id,10+(int)(diary.tasks.size()-done),GoalPlan.progress("diary",diary.id,diary.tasks.size(),diary.name,Collections.emptyList()),supplies));
        }
        for(String key:List.of("rigour","augury"))if(Long.valueOf(0).equals(current.value("metric:"+key)))
        {String name="rigour".equals(key)?"Rigour":"Augury",scroll="rigour".equals(key)?"Dexterous prayer scroll":"Arcane prayer scroll";Set<Integer> ids=GuideItemResolver.resolveName(scroll);result.add(new Opportunity("unlock:"+key,"Unlock "+name,scroll,"Account progress",key,5,GoalPlan.progress("unlock",key,1,name,new ArrayList<>(ids)),ids));}
        CombatAchievementsTool.Snapshot achievements=combat.reading();int offered=0;
        if(current.profile.equals(achievements.profile)&&achievements.notificationBaseline)for(CombatAchievementCatalog.Task task:combatCatalog.filter(achievements.finished,null,null,null,"",true))
        {
            boolean blocked=false,unknown=false;for(CombatAchievementCatalog.Requirement need:combat.requirements(task.id)){int state=CombatAchievementsTool.requirementState(need,achievements);blocked|=state<0&&!need.boostable;unknown|=state==0;}
            if(blocked)continue;result.add(new Opportunity("ca:"+task.id,task.name,task.boss+" · "+task.points+" pt"+(unknown?" · access checks pending":""),"Combat achievements",Integer.toString(task.id),30+task.points,GoalPlan.progress("ca",Integer.toString(task.id),1,task.name,Collections.emptyList()),Set.of()));if(++offered>=5)break;
        }
        for(MusicUnlockCatalog.Stop stop:music.nearbyStops())result.add(new Opportunity("music:"+stop.track.id,stop.track.name,stop.distance+" tiles to music entry","Music unlocks",Integer.toString(stop.track.id),40,GoalPlan.progress("music",Integer.toString(stop.track.id),1,stop.track.name,Collections.emptyList()),Set.of()));
        result.sort(java.util.Comparator.comparingInt((Opportunity value)->value.priority).thenComparing(value->value.title));return Collections.unmodifiableList(result);
    }
    static final class Choice
    {
        final String key, label; final long target; final List<Integer> items;
        Choice(String key, String label, long target, List<Integer> items) { this.key = key; this.label = label; this.target = target; this.items = items; }
        @Override public String toString() { return label; }
    }
    List<Choice> choices(String kind, String filter)
    {
        String needle = normalize(filter); List<Choice> values = new ArrayList<>();
        if ("diary".equals(kind)) for (DiaryCatalog.Diary diary : diaries.diaries())
        { if (normalize(diary.name).contains(needle)) values.add(new Choice(diary.id, diary.name, diary.tasks.size(), Collections.emptyList())); }
        else if ("ca".equals(kind)) for (CombatAchievementCatalog.Task task : combatCatalog.tasks())
        { if (normalize(task.name + task.boss).contains(needle)) values.add(new Choice(Integer.toString(task.id), task.name, 1, Collections.emptyList())); }
        else if ("music".equals(kind)) for (MusicUnlockCatalog.Track track : musicCatalog.tracks())
        { if (track.id >= 0 && normalize(track.name).contains(needle)) values.add(new Choice(Integer.toString(track.id), track.name, 1, Collections.emptyList())); }
        else if ("kc".equals(kind))
        {
            java.util.TreeSet<String> bosses = new java.util.TreeSet<>(); for (CombatAchievementCatalog.Task task : combatCatalog.tasks()) bosses.add(task.boss);
            bosses.addAll(reading().killNames.values());
            for (String boss : bosses) if (normalize(boss).contains(needle)) values.add(new Choice(boss, boss, 100, Collections.emptyList()));
        }
        else if ("metric".equals(kind) || "currency".equals(kind) || "unlock".equals(kind))
        {
            if ("metric".equals(kind))
            { values.add(new Choice("collection-slots", "Collection log slots", 100, Collections.emptyList())); values.add(new Choice("music-tracks", "Music tracks unlocked", 100, Collections.emptyList())); values.add(new Choice("ca-points", "Combat achievement points", 100, Collections.emptyList())); }
            for (Metric metric : METRICS)
                if (("currency".equals(kind) ? metric.spendable : "unlock".equals(kind) ? !metric.spendable && !"kudos".equals(metric.key) : "kudos".equals(metric.key)) && normalize(metric.label).contains(needle))
                    values.add(new Choice(metric.key, metric.label, metric.spendable ? 100 : "kudos".equals(metric.key) ? 153 : 1, unlockItemIds(metric.key)));
        }
        else if ("item".equals(kind) || "collection".equals(kind))
        {
            if (needle.length() < 2) return values;
            for (Map.Entry<String, List<Integer>> name : GuideItemResolver.nameIndex().entrySet())
                if (normalize(name.getKey()).contains(needle)) for (int id : name.getValue())
                { if (!"collection".equals(kind) || rarity.forItem(id).ownershipPercent != null) values.add(new Choice(Integer.toString(id), name.getKey() + " · " + id, 1, List.of(id))); if (values.size() >= 40) return values; }
        }
        return values.size() <= 80 ? values : new ArrayList<>(values.subList(0, 80));
    }
    private static List<Integer> unlockItemIds(String key)
    { String name = "rigour".equals(key) ? "Dexterous prayer scroll" : "augury".equals(key) ? "Arcane prayer scroll" : null; return name == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(GuideItemResolver.resolveName(name))); }
    private boolean active() { return started && profile != null && profile.equals(config.getRSProfileKey()) && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null; }
    void onGameTick()
    {
        if (!started) return;
        String next = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || next == null || next.isEmpty())
        { if (profile != null) clear(); return; }
        if (!next.equals(profile))
        {
            clear(); profile = next;
            saved = dataStore.read(RunewardenConfig.GROUP, profile, KEY, 1, Saved.class, AccountProgressTool::validSaved, Saved::new);
        }
        if (warmup-- > 0 || ticks++ % 5 != 0) return;
        Map<Integer, Integer> varps = new LinkedHashMap<>(), varbits = new LinkedHashMap<>();
        for (int variable : diaries.varps()) varps.put(variable, client.getVarpValue(variable));
        for (int variable : diaries.varbits()) varbits.put(variable, client.getVarbitValue(variable));
        Map<String, Long> values = new LinkedHashMap<>();
        for (DiaryCatalog.Diary diary : diaries.diaries())
        {
            int complete = 0, known = 0;
            for (DiaryCatalog.Task task : diary.tasks)
            { Integer state = DiaryCatalog.completed(task, varps, varbits); if (state != null) { known++; complete += state; values.put("task:" + task.id, (long) state); } }
            if (known == diary.tasks.size()) values.put("diary:" + diary.id, (long) complete);
            if (diary.claimedVariable != null) values.put("claimed:" + diary.id, varbits.get(diary.claimedVariable) == 0 ? 0L : 1L);
        }
        for (Metric metric : METRICS) values.put("metric:" + metric.key, (long) Math.max(0, "varp".equals(metric.kind) ? client.getVarpValue(metric.variable) : client.getVarbitValue(metric.variable)));
        int count = client.getVarpValue(VarPlayerID.COLLECTION_COUNT), maximum = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
        if (maximum > 0 && count >= 0 && count <= maximum) values.put("metric:collection-slots", (long) count);
        CombatAchievementsTool.Snapshot achievements = combat.reading();
        if (profile.equals(achievements.profile) && achievements.notificationBaseline)
        { values.put("metric:ca-points", (long) achievements.points); for (int id : achievements.finished) values.put("ca:" + id, 1L); }
        Map<Integer, Integer> tracks = music.observedUnlocks();
        if (!tracks.isEmpty())
        { long total = 0, known = 0; for (Map.Entry<Integer, Integer> track : tracks.entrySet()) if (track.getValue() >= 0) { known++; values.put("music:" + track.getKey(), track.getValue().longValue()); if (track.getValue() == 1) total++; } if (known > 0) values.put("metric:music-tracks", total); }
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class), boosted = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) { levels.put(skill, client.getRealSkillLevel(skill)); boosted.put(skill, client.getBoostedSkillLevel(skill)); }
        Map<String, QuestState> quests = new LinkedHashMap<>();
        // Only diary-mentioned quests are read, not every quest on every tick.
        Set<String> needed = new LinkedHashSet<>();
        for (DiaryCatalog.Diary diary : diaries.diaries()) { collectQuests(diary.requirements, needed); for (DiaryCatalog.Task task : diary.tasks) collectQuests(task.requirements, needed); }
        for (String key : needed) { Quest quest = questIndex.get(GoalCatalog.questKey(key)); if (quest != null) try { quests.put(GoalCatalog.questKey(key), quest.getState(client)); } catch (RuntimeException ignored) { } }
        Snapshot before = snapshot;
        if (profile.equals(before.profile) && before.values.equals(values) && before.collection.equals(saved.collection)
            && before.killCounts.equals(saved.killCounts) && before.levels.equals(levels) && before.boostedLevels.equals(boosted) && before.quests.equals(quests) && before.collectionMaximum == maximum) return;
        snapshot = new Snapshot(profile, generation, values, saved, levels, boosted, quests, maximum, true); requestRender();
    }
    private static void collectQuests(List<DiaryCatalog.Requirement> requirements, Set<String> target)
    { if (requirements != null) for (DiaryCatalog.Requirement requirement : requirements) { if ("quest".equals(requirement.kind)) target.add(requirement.key); collectQuests(requirement.children, target); } }
    static boolean validSaved(Saved value)
    {
        if (value == null || value.version != 1 || value.collection == null || value.killCounts == null || value.killNames == null || value.collection.size() > MAX_FACTS || value.killCounts.size() > 250 || value.killNames.size() > 250 || value.collectionObservedAt < 0) return false;
        for (Map.Entry<Integer, Long> item : value.collection.entrySet()) if (item.getKey() == null || item.getKey() <= 0 || item.getValue() == null || item.getValue() < 1 || item.getValue() > Integer.MAX_VALUE) return false;
        for (Map.Entry<String, Long> kc : value.killCounts.entrySet()) if (kc.getKey() == null || kc.getKey().isEmpty() || kc.getKey().length() > 100 || kc.getValue() == null || kc.getValue() < 0 || kc.getValue() > Integer.MAX_VALUE) return false;
        for (Map.Entry<String, String> name : value.killNames.entrySet()) if (name.getKey() == null || name.getValue() == null || name.getKey().length() > 100 || name.getValue().length() > 100 || name.getValue().matches(".*[<>\\r\\n].*")) return false;
        return true;
    }
    void onItemContainerChanged(ItemContainerChanged event)
    { if (event.getContainerId() == InventoryID.COLLECTION_TRANSMIT) observeCollection(event.getItemContainer()); }
    void onScriptPostFired(ScriptPostFired event)
    { if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST) observeCollection(client.getItemContainer(InventoryID.COLLECTION_TRANSMIT)); }
    void onScriptPreFired(ScriptPreFired event)
    {
        if (!active() || warmup > 0) return;
        if (event.getScriptId() == ScriptID.NOTIFICATION_START) collectionNotificationStarted = true;
        if (event.getScriptId() != ScriptID.NOTIFICATION_DELAY || !collectionNotificationStarted) return;
        collectionNotificationStarted = false;
        String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE), main = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
        if ("Collection log".equalsIgnoreCase(title) && main != null && main.replaceAll("<[^>]*>", "").startsWith("New item:"))
            recordCollectionName(main.replaceAll("<[^>]*>", "").substring("New item:".length()).trim());
    }
    private void observeCollection(ItemContainer container)
    {
        if (!active() || warmup > 0 || container == null) return;
        boolean changed = false;
        for (Item item : container.getItems()) if (item != null && item.getId() > 0 && item.getQuantity() > 0 && saved.collection.size() < MAX_FACTS)
        { Long before = saved.collection.put(item.getId(), Math.max(saved.collection.getOrDefault(item.getId(), 0L), (long) item.getQuantity())); changed |= before == null || before < item.getQuantity(); }
        if (changed) { saved.collectionObservedAt = System.currentTimeMillis(); persist(); ticks = 0; }
    }
    void onChatMessage(ChatMessage event)
    {
        if (!active() || warmup > 0 || (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)) return;
        String text = event.getMessage().replaceAll("<[^>]*>", ""); Matcher matcher = KC.matcher(text);
        if (!matcher.matches()) matcher = COMPLETED_KC.matcher(text);
        if (matcher.matches() && saved.killCounts.size() < 250)
        { try { String name = matcher.group(1).trim(); String key = normalize(name); long count = Long.parseLong(matcher.group(2).replace(",", "")); if (name.length() <= 100 && !key.isEmpty() && count >= 0 && count <= Integer.MAX_VALUE) { saved.killCounts.put(key, count); saved.killNames.put(key, name); persist(); ticks = 0; } } catch (NumberFormatException ignored) { } }
        if (text.startsWith(COLLECTION))
            recordCollectionName(text.substring(COLLECTION.length()).trim());
    }
    private void recordCollectionName(String name)
    { int id = rarity.itemId(name); if (id > 0 && saved.collection.size() < MAX_FACTS) { saved.collection.put(id, Math.max(1, saved.collection.getOrDefault(id, 0L))); saved.collectionObservedAt = System.currentTimeMillis(); persist(); ticks = 0; } }
    private void persist() { if (active()) dataStore.write(RunewardenConfig.GROUP, profile, KEY, 1, saved); }
    static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    Long progress(GoalPlan goal)
    {
        Snapshot current = reading(); if (!current.live) return null;
        switch (goal.kind)
        {
            case "item": case "set":
                if (!profile.equals(ownership.snapshot().profile)) return null;
                long amount = "set".equals(goal.kind) ? Long.MAX_VALUE : 0;
                boolean known = false;
                for (int id : goal.itemIds)
                { OwnershipLedger.Quantity quantity = ownership.query(id); if (quantity.observedTotal > 0 || quantity.fullyKnown()) known = true; if ("set".equals(goal.kind)) amount = Math.min(amount, quantity.observedTotal); else amount += quantity.observedTotal; }
                return known ? Math.max(0, amount == Long.MAX_VALUE ? 0 : amount) : null;
            case "collection": return current.collection.get(Integer.parseInt(goal.referenceKey));
            case "kc": return current.killCounts.get(normalize(goal.referenceKey));
            case "diary": return current.value("diary:" + goal.referenceKey);
            case "ca": if(current.value("ca:"+goal.referenceKey)!=null)return 1L;return current.values.containsKey("metric:ca-points")?Long.valueOf(0):null;
            case "music": return current.value("music:" + goal.referenceKey);
            case "metric": case "currency": case "unlock": return current.value("metric:" + goal.referenceKey);
            default: return null;
        }
    }
    int requirementState(DiaryCatalog.Requirement requirement)
    {
        Snapshot current = reading(); if (!current.live || requirement == null) return 0;
        if ("skill".equals(requirement.kind))
        { try { Skill skill = Skill.valueOf(requirement.key); Integer level = current.levels.get(skill); if (requirement.boostable && current.boostedLevels.getOrDefault(skill, 0) >= requirement.value) return 1; return level == null ? 0 : level >= requirement.value ? 1 : -1; } catch (RuntimeException ignored) { return 0; } }
        if ("quest".equals(requirement.kind))
        { QuestState state = current.quests.get(GoalCatalog.questKey(requirement.key)); return state == null ? 0 : state == QuestState.FINISHED || requirement.started && state == QuestState.IN_PROGRESS ? 1 : -1; }
        if ("item".equals(requirement.kind) && requirement.quantityKnown)
        {
            Set<Integer> ids = requirement.itemId > 0 ? Collections.singleton(requirement.itemId) : GuideItemResolver.resolveName(requirement.label);
            if (ids.isEmpty()) return 0; boolean allKnown = true;
            for (int id : ids) { OwnershipLedger.Quantity quantity = ownership.query(id); if (quantity.observedTotal >= requirement.value) return 1; allKnown &= quantity.fullyKnown(); }
            return allKnown ? -1 : 0;
        }
        if (("all".equals(requirement.kind) || "choice".equals(requirement.kind)) && requirement.children != null)
        {
            boolean any = "choice".equals(requirement.kind), unknown = false;
            for (DiaryCatalog.Requirement child : requirement.children) { int state = requirementState(child); if (any && state > 0) return 1; if (!any && state < 0) return -1; unknown |= state == 0; }
            return unknown ? 0 : any ? -1 : 1;
        }
        return 0;
    }
    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("account-progress");
        panel.addHierarchyListener(event -> { if (panel.isShowing()) render(); });
        JPanel filters = SidebarUi.section("Account progression");
        mode = new JComboBox<>(new String[] {"Next unlocks", "Collection observed", "Unlocks and currencies", "Observed kill counts"}); mode.setFont(GuideText.FONT);
        mode.addActionListener(event -> { page = 0; render(); }); filters.add(mode);
        search = new JTextField(); search.setFont(GuideText.FONT); search.addActionListener(event -> { page = 0; render(); }); filters.add(SidebarUi.field("Find item or activity", search));
        panel.add(filters); body = SidebarUi.column(); panel.add(body); render(); return panel;
    }
    private void requestRender() { Snapshot expected = snapshot; SwingUtilities.invokeLater(() -> { if (snapshot == expected && panel != null && panel.isShowing()) render(); }); }
    private void render()
    {
        if (body == null) return; body.removeAll(); Snapshot current = reading();
        if (current.profile == null) { body.add(SidebarUi.label("Log in to view account progress.")); finishRender(); return; }
        String needle = normalize(search.getText()); List<Runnable> rows = new ArrayList<>();
        if (mode.getSelectedIndex() == 0)
        {
            for (Opportunity opportunity : opportunities()) if (normalize(opportunity.title + opportunity.detail).contains(needle)) rows.add(() -> {
                JPanel card = SidebarUi.section(opportunity.title); card.add(SidebarUi.label(opportunity.detail));
                card.add(SidebarUi.button("Open activity", () -> { if (current.profile.equals(config.getRSProfileKey()) && current.generation == generation) openOpportunity.accept(opportunity); }));
                GoalPlan goal = opportunity.goal(); if (goal != null) card.add(SidebarUi.button("Add goal", () -> queue(current, goal)));
                body.add(card);
            });
        }
        else if (mode.getSelectedIndex() == 1)
        {
            Long total = current.value("metric:collection-slots"); body.add(SidebarUi.label((total == null ? "?" : total) + " / " + (current.collectionMaximum > 0 ? current.collectionMaximum : "?") + " collection slots"));
            body.add(SidebarUi.label(current.collection.size() + " personally observed items"));
            for (Map.Entry<Integer, Long> item : current.collection.entrySet())
            {
                String name = rarity.itemName(item.getKey()); if (name == null || name.isEmpty()) name = "Item " + item.getKey();
                if (!normalize(name).contains(needle)) continue; String label = name;
                rows.add(() -> { JPanel row = SidebarUi.section(label); row.add(SidebarUi.label("Obtained · " + item.getValue())); row.add(SidebarUi.button("Item and sources", () -> { if (current.profile.equals(config.getRSProfileKey())) openItem.accept(item.getKey()); })); row.add(SidebarUi.button("Track collection goal", () -> queue(current, GoalPlan.progress("collection", Integer.toString(item.getKey()), 1, label, Collections.emptyList())))); body.add(row); });
            }
        }
        else if (mode.getSelectedIndex() == 2)
        {
            for (Metric metric : METRICS)
            {
                if (!normalize(metric.label).contains(needle)) continue;
                rows.add(() -> { JPanel row = SidebarUi.section(metric.label); Long value = current.value("metric:" + metric.key); row.add(SidebarUi.label(value == null ? "Unknown" : Long.toString(value))); row.add(SidebarUi.button("Add goal", () -> queue(current, GoalPlan.progress(metric.spendable ? "currency" : metric.key.equals("kudos") ? "metric" : "unlock", metric.key, metric.spendable ? Math.max(1, (value == null ? 0 : value) + 100) : metric.key.equals("kudos") ? 153 : 1, metric.label, unlockItemIds(metric.key))))); body.add(row); });
            }
        }
        else for (Map.Entry<String, Long> kc : current.killCounts.entrySet())
        {
            if (!kc.getKey().contains(needle)) continue;
            String name = current.killNames.getOrDefault(kc.getKey(), kc.getKey());
            rows.add(() -> { JPanel row = SidebarUi.section(name); row.add(SidebarUi.label(kc.getValue() + " kills · last observed")); row.add(SidebarUi.button("Track next milestone", () -> queue(current, GoalPlan.progress("kc", name, Math.max(100, ((kc.getValue() / 100) + 1) * 100), name, Collections.emptyList())))); body.add(row); });
        }
        int pages = Math.max(1, (rows.size() + 7) / 8); page = Math.max(0, Math.min(page, pages - 1));
        if (rows.isEmpty()) body.add(SidebarUi.label("No observed matches"));
        for (int index = page * 8; index < Math.min(rows.size(), page * 8 + 8); index++) rows.get(index).run();
        if (pages > 1) { javax.swing.JLabel count = new javax.swing.JLabel((page + 1) + " / " + pages, javax.swing.SwingConstants.CENTER); count.setFont(GuideText.FONT); count.setForeground(SidebarUi.TEXT); body.add(SidebarUi.row(SidebarUi.button("‹", () -> { page--; render(); }), count, SidebarUi.button("›", () -> { page++; render(); }))); }
        finishRender();
    }
    private void queue(Snapshot expected, GoalPlan goal) { if (expected.profile != null && expected.profile.equals(config.getRSProfileKey()) && expected.generation == generation) addGoal.accept(goal); }
    private void finishRender() { body.revalidate(); body.repaint(); }
}
