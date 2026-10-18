/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JCheckBox;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** Personal task decisions and native master-table facts; this never changes an assignment or game block list. */
@Singleton
final class SlayerPlanningTool
{
    static final String KEY = "slayerPlanningV1";
    enum Decision { PREFER, DO, SKIP, BLOCK }
    static final class Measurement
    {
        String method = "", creature = "", area = ""; int credits; double minutes;
    }
    static final class Rule
    {
        String decision = "DO", effort = "Any", note = "", access = "AUTO", method = "", creature = "", area = "", guide = "";
        int npcId, plannedCount, measuredCredits;
        double measuredMinutes;
        double killsPerHour, xpPerKill, netGpPerKill;
        boolean xpKnown, gpKnown, areaConfirmed;
        List<Integer> supplies = new ArrayList<>();
        List<Measurement> measurements = new ArrayList<>();
    }
    static final class Settings
    {
        String combatChecking = "UNKNOWN", goal = "Balanced";
        int completions = 10;
        boolean includeUnchecked;
        int[] rewards;
    }
    static final class Plan { int version = 1; Map<Integer, Map<String, Rule>> masters = new LinkedHashMap<>(); Map<Integer, Settings> settings = new LinkedHashMap<>(); }
    static final class Requirement
    {
        final Skill skill; final int level;
        Requirement(Skill skill, int level) { this.skill = skill; this.level = level; }
    }
    static final class Area
    {
        final int id; final String name, hint;
        Area(int id, String name, String hint) { this.id = id; this.name = name; this.hint = hint; }
    }
    static final class WeightChange { final int bit, delta; WeightChange(int bit, int delta) { this.bit = bit; this.delta = delta; } }
    static final class NativeTask
    {
        final int row, taskId, weight, minimum, maximum, combat;
        final String name, unlock;
        final List<Requirement> all, any; final List<Area> areas;
        int unlockBit, blockBit; List<WeightChange> weightChanges = Collections.emptyList();
        NativeTask(int row, int taskId, String name, int weight, int minimum, int maximum, int combat,
            List<Requirement> all, List<Requirement> any, List<Area> areas, String unlock)
        { this.row = row; this.taskId = taskId; this.name = name; this.weight = weight; this.minimum = minimum; this.maximum = maximum; this.combat = combat; this.all = Collections.unmodifiableList(all); this.any = Collections.unmodifiableList(any); this.areas = Collections.unmodifiableList(areas); this.unlock = unlock; }
    }
    static final class Snapshot
    {
        final String profile; final long generation; final int master, indexed, total;
        final List<NativeTask> tasks; final Set<Integer> blocked; final boolean blocksObserved;
        final Map<Skill, Integer> levels; final int combat;
        Map<Integer, Integer> bits = Collections.emptyMap(); Map<String, String> quests = Collections.emptyMap();
        String masterStatus = "Access unchecked"; int assignmentMaster, streak, points; int freeBlocks = -1;
        boolean storageUnlocked; int storedMaster, assignmentStreak; Integer storageUnlockCost;
        Snapshot(String profile, long generation, int master, int indexed, int total, List<NativeTask> tasks,
            Set<Integer> blocked, boolean blocksObserved, Map<Skill, Integer> levels, int combat)
        { this.profile = profile; this.generation = generation; this.master = master; this.indexed = indexed; this.total = total;
            this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks)); this.blocked = Collections.unmodifiableSet(new LinkedHashSet<>(blocked));
            this.blocksObserved = blocksObserved; this.levels = Collections.unmodifiableMap(new EnumMap<>(levels)); this.combat = combat; }
        static Snapshot empty() { return new Snapshot(null, 0, 0, 0, 0, Collections.emptyList(), Collections.emptySet(), false, new EnumMap<>(Skill.class), 0); }
    }
    private final Client client; private final ConfigManager config; private final RunewardenDataStore dataStore;
    private final GuideCatalog guides; private final SlayerTaskCatalog catalog;
    private final MobKnowledgeCatalog monsters; private final SlayerMethodCatalog methods; private final OwnershipLedger ownership;
    private final ItemKnowledgeCatalog itemKnowledge; private net.runelite.client.game.ItemManager itemManager;
    private boolean started; private volatile boolean visible;
    private volatile String profile; private volatile long generation; private volatile int master; private int cursor, ticks, warmup;
    private List<Integer> rows = Collections.emptyList(); private final Map<Integer, NativeTask> indexed = new LinkedHashMap<>();
    private volatile Plan plan = new Plan(); private volatile Snapshot snapshot = Snapshot.empty();
    private volatile SlayerCoachTool.State assignment = SlayerCoachTool.State.empty("No active assignment");
    private JPanel panel, body; private JComboBox<String> mode, masterChoice, goalChoice, eligibilityChoice; private JTextField search; private int page;
    private int observationTicks; private Map<String, String> questFacts = Collections.emptyMap();
    private volatile int requestedMaster; private boolean changingMaster;
    private JCheckBox hideLockedMasters; private List<Integer> shownMasters = new ArrayList<>(List.of(0,1,2,3,4,5,6,7,8,10));
    private String selected; private int detailPage; private String supplyQuery = "";
    private Consumer<String> openGuide = ignored -> { }; private Runnable openLoadouts = () -> { };
    private Consumer<WorldPoint> route = ignored -> { };
    private IntConsumer openItem = ignored -> { }; private Consumer<Set<Integer>> bankFocus = ignored -> { };
    private Consumer<Prepared> startActivity = ignored -> { };
    static final class Prepared
    {
        final String profile, task, creature, area, guideId; final int taskId, remaining; final Set<Integer> npcIds, itemIds;
        Prepared(String profile, String task, int taskId, String creature, String area, String guide, int remaining, Set<Integer> npcs, Set<Integer> items)
        { this.profile = profile; this.task = task; this.taskId = taskId; this.creature = creature; this.area = area; guideId = guide; this.remaining = remaining; npcIds = Collections.unmodifiableSet(new LinkedHashSet<>(npcs)); itemIds = Collections.unmodifiableSet(new LinkedHashSet<>(items)); }
    }

    @Inject SlayerPlanningTool(Client client, ConfigManager config, RunewardenDataStore dataStore, GuideCatalog guides, SlayerTaskCatalog catalog,
        MobKnowledgeCatalog monsters, SlayerMethodCatalog methods, OwnershipLedger ownership, ItemKnowledgeCatalog itemKnowledge)
    { this.client = client; this.config = config; this.dataStore = dataStore; this.guides = guides; this.catalog = catalog; this.monsters = monsters; this.methods = methods; this.ownership = ownership; this.itemKnowledge = itemKnowledge; }
    SlayerPlanningTool(Client client, ConfigManager config, RunewardenDataStore dataStore, GuideCatalog guides, SlayerTaskCatalog catalog)
    { this(client, config, dataStore, guides, catalog, null, null, null, null); }
    void setItemManager(net.runelite.client.game.ItemManager items) { itemManager = items; }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void setBankFocusAction(Consumer<Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    void setStartActivityAction(Consumer<Prepared> action) { startActivity = action == null ? ignored -> { } : action; }
    void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? ignored -> { } : action; }
    void setOpenLoadoutsAction(Runnable action) { openLoadouts = action == null ? () -> { } : action; }
    void setRouteAction(Consumer<WorldPoint> action) { route = action == null ? ignored -> { } : action; }
    void setAssignment(SlayerCoachTool.State state) { assignment = state; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear()
    { profile = null; generation++; master = 0; cursor = ticks = 0; warmup = 3; observationTicks = 0; questFacts = Collections.emptyMap(); rows = Collections.emptyList(); indexed.clear(); plan = new Plan(); snapshot = Snapshot.empty(); assignment = SlayerCoachTool.State.empty("No active assignment"); selected = null; page = 0; }
    Snapshot reading()
    { Snapshot value = snapshot; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value : Snapshot.empty(); }
    void onGameTick()
    {
        if (!started) return;
        String next = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || next == null || next.isEmpty()) { if (profile != null) clear(); return; }
        if (!next.equals(profile)) { clear(); profile = next; plan = dataStore.read(RunewardenConfig.GROUP, profile, KEY, 1, Plan.class, SlayerPlanningTool::valid, Plan::new); }
        int nativeMaster = client.getVarbitValue(VarbitID.SLAYER_MASTER);
        int nextMaster = requestedMaster > 0 ? requestedMaster : nativeMaster;
        if (nextMaster != master) { master = nextMaster; rows = Collections.emptyList(); indexed.clear(); cursor = 0; warmup = 3; }
        if (!visible) return;
        if (warmup-- > 0) return;
        if (master <= 0)
        {
            snapshot = new Snapshot(profile, generation, 0, 0, 0, Collections.emptyList(), Collections.emptySet(), false, new EnumMap<>(Skill.class), 0);
            SwingUtilities.invokeLater(() -> { if (visible) render(); }); return;
        }
        if (rows.isEmpty())
        {
            try { List<Integer> found = client.getDBRowsByValue(DBTableID.SlayerMasterTask.ID, DBTableID.SlayerMasterTask.COL_MASTER_ID, 0, master); if (found != null && found.size() <= 400) rows = new ArrayList<>(new LinkedHashSet<>(found)); }
            catch (RuntimeException loading) { return; }
        }
        long deadline = System.nanoTime() + 1_500_000L; int batch = 0;
        while (cursor < rows.size() && batch++ < 8)
        {
            int row = rows.get(cursor++); NativeTask task = readTask(row); if (task != null) indexed.put(row, task);
            if (System.nanoTime() > deadline) break;
        }
        if (cursor >= rows.size() && indexed.size() < rows.size() && ticks++ % 50 == 0) cursor = 0;
        int[] slots = blockSlots(master); Set<Integer> blocked = new LinkedHashSet<>();
        if (slots != null) for (int slot : slots) { int task = client.getVarbitValue(slot); if (task > 0) blocked.add(task); }
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class); for (Skill skill : Skill.values()) levels.put(skill, client.getRealSkillLevel(skill));
        Snapshot before = snapshot;
        Snapshot current = new Snapshot(profile, generation, master, indexed.size(), rows.size(), new ArrayList<>(indexed.values()), blocked, slots != null, levels, client.getLocalPlayer().getCombatLevel());
        Map<Integer, Integer> bits = new LinkedHashMap<>();
        for (NativeTask task : current.tasks)
        {
            if (task.unlockBit > 0) bits.put(task.unlockBit, client.getVarbitValue(task.unlockBit));
            if (task.blockBit > 0) bits.put(task.blockBit, client.getVarbitValue(task.blockBit));
            if (task.blockBit == VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK) bits.put(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK, client.getVarbitValue(VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK));
            for (WeightChange change : task.weightChanges) if (change.bit > 0) bits.put(change.bit, client.getVarbitValue(change.bit));
        }
        bits.put(VarbitID.WESTERN_ELITE_REWARD, client.getVarbitValue(VarbitID.WESTERN_ELITE_REWARD));
        bits.put(VarbitID.KOUREND_ELITE_REWARD, client.getVarbitValue(VarbitID.KOUREND_ELITE_REWARD));
        bits.put(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, client.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE));
        current.bits = Collections.unmodifiableMap(bits); current.assignmentMaster = nativeMaster;
        current.streak = Math.max(0, master == 7 ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED) : master == 10 ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED) : client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED));
        current.points = Math.max(0, client.getVarbitValue(VarbitID.SLAYER_POINTS));
        current.storageUnlocked = client.getVarbitValue(VarbitID.SLAYER_UNLOCK_STORAGE) > 0;
        current.storedMaster = Math.max(0, client.getVarbitValue(VarbitID.SLAYER_MASTER_STORED));
        current.storageUnlockCost = integer(DBTableID.SlayerUnlock.Row.SLAYER_REWARDS_UNLOCK_STORAGE, DBTableID.SlayerUnlock.COL_COST);
        if (current.storageUnlockCost != null && (current.storageUnlockCost < 0 || current.storageUnlockCost > 1_000_000)) current.storageUnlockCost = null;
        current.assignmentStreak = Math.max(0, nativeMaster == 7 ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED) : nativeMaster == 10 ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED) : client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED));
        int capacity = master == 10 ? 2 : Math.min(6, Math.max(0, client.getVarpValue(VarPlayerID.QP)) / 50) + (bits.getOrDefault(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 0) > 0 ? 1 : 0);
        current.freeBlocks = slots == null ? -1 : Math.max(0, capacity - blocked.size());
        if (questFacts.isEmpty() || ++observationTicks % 10 == 0)
        {
            Map<String, String> quests = new LinkedHashMap<>();
            List<Quest> requiredQuests = new ArrayList<>(List.of(Quest.PRIEST_IN_PERIL, Quest.LOST_CITY, Quest.SHILO_VILLAGE, Quest.DESERT_TREASURE_I));
            if (methods != null) for (String name : methods.quests()) { Quest quest = Quest.valueOf(name); if (!requiredQuests.contains(quest)) requiredQuests.add(quest); }
            for (Quest quest : requiredQuests)
            { try { QuestState value = quest.getState(client); quests.put(quest.name(), value == null ? "UNKNOWN" : value.name()); } catch (RuntimeException pending) { quests.put(quest.name(), "UNKNOWN"); } }
            questFacts = Collections.unmodifiableMap(quests);
        }
        current.quests = questFacts; current.masterStatus = masterAccess(master, current);
        snapshot = current;
        if (before.indexed != current.indexed || before.master != current.master || !before.blocked.equals(current.blocked) || !before.levels.equals(current.levels) || !before.bits.equals(current.bits) || !before.quests.equals(current.quests) || !before.masterStatus.equals(current.masterStatus) || before.streak != current.streak || before.points != current.points || before.freeBlocks != current.freeBlocks || before.storageUnlocked != current.storageUnlocked || before.storedMaster != current.storedMaster || !java.util.Objects.equals(before.storageUnlockCost, current.storageUnlockCost) || before.assignmentStreak != current.assignmentStreak || before.assignmentMaster != current.assignmentMaster)
            SwingUtilities.invokeLater(() -> { if (snapshot == current && visible) render(); });
    }
    NativeTask readTask(int row)
    {
        try
        {
            Integer taskRow = integer(row, DBTableID.SlayerMasterTask.COL_TASK), weight = integer(row, DBTableID.SlayerMasterTask.COL_WEIGHT);
            if (taskRow == null || weight == null) return null;
            String name = string(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE); Integer taskId = integer(taskRow, DBTableID.SlayerTask.COL_ID);
            if (name == null || name.isEmpty() || taskId == null) return null;
            Integer min = integer(row, DBTableID.SlayerMasterTask.COL_MIN_AMOUNT), max = integer(row, DBTableID.SlayerMasterTask.COL_MAX_AMOUNT), combat = integer(taskRow, DBTableID.SlayerTask.COL_MIN_COMLEVEL);
            List<Area> areas = new ArrayList<>();
            for (int index = 0; index < 32; index++)
            {
                Object[] field = field(row, DBTableID.SlayerMasterTask.COL_AREAS, index); if (field == null || field.length == 0 || !(field[0] instanceof Number)) break;
                int areaRow = ((Number) field[0]).intValue(); if (areaRow <= 0) break;
                Integer area = integer(areaRow, DBTableID.SlayerArea.COL_AREA_ID); String areaName = string(areaRow, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER), hint = string(areaRow, DBTableID.SlayerArea.COL_AREA_HINT);
                if (area != null && areaName != null) areas.add(new Area(area, areaName, hint));
            }
            String unlock = null; Integer unlockRow = integer(row, DBTableID.SlayerMasterTask.COL_TASK_UNLOCK);
            if (unlockRow != null && unlockRow > 0) { String label = string(unlockRow, DBTableID.SlayerUnlock.COL_NAME); String description = string(unlockRow, DBTableID.SlayerUnlock.COL_DESCRIPTION); if (label != null) unlock = label + (description == null || description.isEmpty() ? "" : " · " + description); }
            NativeTask result = new NativeTask(row, taskId, name, Math.max(0, weight), min == null ? -1 : min, max == null ? -1 : max, combat == null ? -1 : combat,
                requirements(taskRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ALL), requirements(taskRow, DBTableID.SlayerTask.COL_MIN_STAT_REQUIREMENT_ANY), areas, unlock);
            Integer unlockBit = unlockRow == null ? null : integer(unlockRow, DBTableID.SlayerUnlock.COL_BIT);
            result.unlockBit = unlockBit == null ? 0 : Math.max(0, unlockBit);
            Integer blockRow = integer(taskRow, DBTableID.SlayerTask.COL_BLOCK_UNLOCK);
            Integer blockBit = blockRow == null ? null : integer(blockRow, DBTableID.SlayerUnlock.COL_BIT);
            result.blockBit = blockBit == null ? 0 : Math.max(0, blockBit);
            List<WeightChange> changes = new ArrayList<>();
            for (int index = 0; index < 16; index++)
            {
                Object[] values = field(taskRow, DBTableID.SlayerTask.COL_UNLOCK_WEIGHTING, index);
                if (values == null || values.length < 2 || !(values[0] instanceof Number) || !(values[1] instanceof Number)) break;
                Integer bit = integer(((Number) values[0]).intValue(), DBTableID.SlayerUnlock.COL_BIT);
                if (bit != null && bit > 0) changes.add(new WeightChange(bit, ((Number) values[1]).intValue()));
            }
            result.weightChanges = Collections.unmodifiableList(changes); return result;
        }
        catch (RuntimeException loading) { return null; }
    }
    private List<Requirement> requirements(int row, int column)
    {
        List<Requirement> result = new ArrayList<>();
        for (int index = 0; index < 16; index++)
        {
            Object[] values = field(row, column, index); if (values == null || values.length < 2 || !(values[0] instanceof Number) || !(values[1] instanceof Number)) break;
            int level = ((Number) values[0]).intValue(), skill = ((Number) values[1]).intValue();
            if (level >= 1 && level <= 99 && skill >= 0 && skill < Skill.values().length) result.add(new Requirement(Skill.values()[skill], level));
        }
        return result;
    }
    private Object[] field(int row, int column, int index) { try { return client.getDBTableField(row, column, index); } catch (RuntimeException missing) { return null; } }
    private Integer integer(int row, int column) { Object[] values = field(row, column, 0); return values != null && values.length > 0 && values[0] instanceof Number ? ((Number) values[0]).intValue() : null; }
    private String string(int row, int column) { Object[] values = field(row, column, 0); return values != null && values.length > 0 && values[0] instanceof String ? ((String) values[0]).replaceAll("<[^>]*>", "") : null; }
    static int[] blockSlots(int master)
    {
        switch (master)
        {
            case 1: return new int[]{VarbitID.SLAYER_BLOCKED_TURAEL_1, VarbitID.SLAYER_BLOCKED_TURAEL_2, VarbitID.SLAYER_BLOCKED_TURAEL_3, VarbitID.SLAYER_BLOCKED_TURAEL_4, VarbitID.SLAYER_BLOCKED_TURAEL_5, VarbitID.SLAYER_BLOCKED_TURAEL_6, VarbitID.SLAYER_BLOCKED_TURAEL_DIARY};
            case 2: return new int[]{VarbitID.SLAYER_BLOCKED_MAZCHNA_1, VarbitID.SLAYER_BLOCKED_MAZCHNA_2, VarbitID.SLAYER_BLOCKED_MAZCHNA_3, VarbitID.SLAYER_BLOCKED_MAZCHNA_4, VarbitID.SLAYER_BLOCKED_MAZCHNA_5, VarbitID.SLAYER_BLOCKED_MAZCHNA_6, VarbitID.SLAYER_BLOCKED_MAZCHNA_DIARY};
            case 3: return new int[]{VarbitID.SLAYER_BLOCKED_VANNAKA_1, VarbitID.SLAYER_BLOCKED_VANNAKA_2, VarbitID.SLAYER_BLOCKED_VANNAKA_3, VarbitID.SLAYER_BLOCKED_VANNAKA_4, VarbitID.SLAYER_BLOCKED_VANNAKA_5, VarbitID.SLAYER_BLOCKED_VANNAKA_6, VarbitID.SLAYER_BLOCKED_VANNAKA_DIARY};
            case 4: return new int[]{VarbitID.SLAYER_BLOCKED_CHAELDAR_1, VarbitID.SLAYER_BLOCKED_CHAELDAR_2, VarbitID.SLAYER_BLOCKED_CHAELDAR_3, VarbitID.SLAYER_BLOCKED_CHAELDAR_4, VarbitID.SLAYER_BLOCKED_CHAELDAR_5, VarbitID.SLAYER_BLOCKED_CHAELDAR_6, VarbitID.SLAYER_BLOCKED_CHAELDAR_DIARY};
            case 5: return new int[]{VarbitID.SLAYER_BLOCKED_DURADEL_1, VarbitID.SLAYER_BLOCKED_DURADEL_2, VarbitID.SLAYER_BLOCKED_DURADEL_3, VarbitID.SLAYER_BLOCKED_DURADEL_4, VarbitID.SLAYER_BLOCKED_DURADEL_5, VarbitID.SLAYER_BLOCKED_DURADEL_6, VarbitID.SLAYER_BLOCKED_DURADEL_DIARY};
            case 6: return new int[]{VarbitID.SLAYER_BLOCKED_NIEVE_1, VarbitID.SLAYER_BLOCKED_NIEVE_2, VarbitID.SLAYER_BLOCKED_NIEVE_3, VarbitID.SLAYER_BLOCKED_NIEVE_4, VarbitID.SLAYER_BLOCKED_NIEVE_5, VarbitID.SLAYER_BLOCKED_NIEVE_6, VarbitID.SLAYER_BLOCKED_NIEVE_DIARY};
            case 7: return new int[]{VarbitID.SLAYER_BLOCKED_KRYSTILIA_1, VarbitID.SLAYER_BLOCKED_KRYSTILIA_2, VarbitID.SLAYER_BLOCKED_KRYSTILIA_3, VarbitID.SLAYER_BLOCKED_KRYSTILIA_4, VarbitID.SLAYER_BLOCKED_KRYSTILIA_5, VarbitID.SLAYER_BLOCKED_KRYSTILIA_6, VarbitID.SLAYER_BLOCKED_KRYSTILIA_DIARY};
            case 8: return new int[]{VarbitID.SLAYER_BLOCKED_KONAR_1, VarbitID.SLAYER_BLOCKED_KONAR_2, VarbitID.SLAYER_BLOCKED_KONAR_3, VarbitID.SLAYER_BLOCKED_KONAR_4, VarbitID.SLAYER_BLOCKED_KONAR_5, VarbitID.SLAYER_BLOCKED_KONAR_6, VarbitID.SLAYER_BLOCKED_KONAR_DIARY};
            case 10: return new int[]{VarbitID.SLAYER_BLOCKED_MORTIMER_1, VarbitID.SLAYER_BLOCKED_MORTIMER_2};
            default: return null;
        }
    }
    static boolean valid(Plan plan)
    {
        if (plan == null || plan.version != 1 || plan.masters == null || plan.masters.size() > 20 || plan.settings == null || plan.settings.size() > 20) return false;
        for (Map.Entry<Integer, Map<String, Rule>> master : plan.masters.entrySet())
        {
            if (master.getKey() == null || master.getKey() < 0 || master.getKey() > 100 || master.getValue() == null || master.getValue().size() > 200) return false;
            for (Map.Entry<String, Rule> entry : master.getValue().entrySet()) if (entry.getKey() == null || entry.getKey().length() > 120 || !validRule(entry.getValue())) return false;
        }
        for (Map.Entry<Integer, Settings> entry : plan.settings.entrySet()) if (entry.getKey() == null || entry.getKey() < 0 || entry.getKey() > 100 || !validSettings(entry.getValue())) return false;
        return true;
    }
    static boolean validRule(Rule rule)
    {
        return rule != null && rule.decision != null && rule.effort != null && rule.access != null && List.of("PREFER", "DO", "SKIP", "BLOCK").contains(rule.decision)
            && List.of("Any", "Relaxed", "Normal", "Intensive").contains(rule.effort) && List.of("AUTO", "YES", "NO").contains(rule.access)
            && clean(rule.note, 400) && clean(rule.method, 100) && clean(rule.creature, 160) && clean(rule.area, 160) && clean(rule.guide, 160)
            && rule.measuredCredits >= 0 && rule.measuredCredits <= 100_000 && Double.isFinite(rule.measuredMinutes) && rule.measuredMinutes >= 0 && rule.measuredMinutes <= 100_000
            && rule.npcId >= 0 && rule.npcId <= 1_000_000 && rule.plannedCount >= 0 && rule.plannedCount <= 100_000
            && Double.isFinite(rule.killsPerHour) && rule.killsPerHour >= 0 && rule.killsPerHour <= 100_000
            && Double.isFinite(rule.xpPerKill) && rule.xpPerKill >= 0 && rule.xpPerKill <= 10_000_000
            && Double.isFinite(rule.netGpPerKill) && Math.abs(rule.netGpPerKill) <= 100_000_000
            && rule.supplies != null && rule.supplies.size() <= 64 && rule.supplies.stream().allMatch(id -> id != null && id > 0 && id <= 1_000_000)
            && rule.measurements != null && rule.measurements.size() <= 20 && rule.measurements.stream().allMatch(value -> value != null && clean(value.method,100) && clean(value.creature,160) && clean(value.area,160)
                && value.credits > 0 && value.credits <= 100_000 && Double.isFinite(value.minutes) && value.minutes > 0 && value.minutes <= 100_000);
    }
    private static boolean validSettings(Settings value)
    {
        if (value == null || value.combatChecking == null || value.goal == null || !List.of("UNKNOWN", "ENABLED", "DISABLED").contains(value.combatChecking)
            || !List.of("Balanced", "AFK", "Normal", "Intensive", "Boss", "XP", "Profit").contains(value.goal)
            || value.completions < 1 || value.completions > 1000) return false;
        if (value.rewards != null) { if (value.rewards.length != 6) return false; for (int point : value.rewards) if (point < 0 || point > 1_000_000) return false; }
        return true;
    }
    private static boolean clean(String text, int length)
    { return text != null && text.length() <= length && text.indexOf('<') < 0 && text.indexOf('>') < 0 && text.indexOf('\r') < 0 && text.indexOf('\n') < 0; }
    private static Rule copy(Rule rule)
    {
        Rule saved = new Rule(); saved.decision = rule.decision; saved.effort = rule.effort; saved.note = rule.note; saved.access = rule.access;
        saved.method = rule.method; saved.creature = rule.creature; saved.area = rule.area; saved.guide = rule.guide; saved.npcId = rule.npcId;
        saved.measuredCredits = rule.measuredCredits; saved.measuredMinutes = rule.measuredMinutes; saved.plannedCount = rule.plannedCount; saved.killsPerHour = rule.killsPerHour; saved.xpPerKill = rule.xpPerKill; saved.netGpPerKill = rule.netGpPerKill;
        saved.xpKnown = rule.xpKnown; saved.gpKnown = rule.gpKnown; saved.areaConfirmed = rule.areaConfirmed; saved.supplies = new ArrayList<>(rule.supplies); saved.measurements = new ArrayList<>(rule.measurements); return saved;
    }
    private Plan copyPlan()
    { Plan copy = new Plan(); for (Map.Entry<Integer, Map<String, Rule>> entry : plan.masters.entrySet()) copy.masters.put(entry.getKey(), new LinkedHashMap<>(entry.getValue())); copy.settings.putAll(plan.settings); return copy; }
    Rule rule(String task) { return rule(master, task); }
    Rule rule(int forMaster, String task)
    { return copy(SlayerPlanEngine.rule(plan.masters.getOrDefault(forMaster, Collections.emptyMap()), task)); }
    Settings settings()
    { Settings value = plan.settings.get(master); return value == null ? new Settings() : copySettings(value); }
    private static Settings copySettings(Settings value)
    { Settings copy = new Settings(); copy.combatChecking = value.combatChecking; copy.goal = value.goal; copy.completions = value.completions; copy.includeUnchecked = value.includeUnchecked; copy.rewards = value.rewards == null ? null : value.rewards.clone(); return copy; }
    boolean saveRule(Snapshot expected, String task, Rule rule)
    {
        if (!current(expected) || !validRule(rule)) return false;
        Plan copy = copyPlan(); copy.masters.computeIfAbsent(expected.master, ignored -> new LinkedHashMap<>()).put(SlayerTaskCatalog.normalize(task), copy(rule));
        if (!valid(copy) || !dataStore.write(RunewardenConfig.GROUP, profile, KEY, 1, copy)) return false;
        plan = copy; return true;
    }
    boolean saveSettings(Snapshot expected, Settings settings)
    {
        if (!current(expected) || !validSettings(settings)) return false;
        Plan copy = copyPlan(); copy.settings.put(expected.master, copySettings(settings));
        if (!valid(copy) || !dataStore.write(RunewardenConfig.GROUP, profile, KEY, 1, copy)) return false;
        plan = copy; return true;
    }
    private boolean current(Snapshot expected)
    { return started && expected != null && expected.profile != null && expected.profile.equals(profile) && expected.profile.equals(config.getRSProfileKey()) && expected.generation == generation && expected.master == master; }
    static String masterName(int id)
    { switch (id) { case 1: return "Turael / Aya / Spria"; case 2: return "Mazchna / Achtryn"; case 3: return "Vannaka"; case 4: return "Chaeldar"; case 5: return "Duradel / Kuradal"; case 6: return "Nieve / Steve"; case 7: return "Krystilia"; case 8: return "Konar"; case 10: return "Mortimer"; default: return "Unknown master"; } }
    static String masterAccess(int id, Snapshot account)
    {
        int minimum = id == 2 ? 20 : id == 3 ? 40 : id == 4 ? 70 : id == 5 ? 100 : id == 6 ? 85 : id == 8 ? 75 : 0;
        if (id == 10 || minimum == 0 && id != 1 && id != 7) return "Access unchecked";
        String quest = id == 2 ? "PRIEST_IN_PERIL" : id == 4 ? "LOST_CITY" : id == 5 ? "SHILO_VILLAGE" : null;
        if (quest != null && !"FINISHED".equals(account.quests.get(quest))) return "UNKNOWN".equals(account.quests.get(quest)) || !account.quests.containsKey(quest) ? "Quest access unchecked" : "Quest required: " + quest.replace('_', ' ');
        if (account.combat < minimum || id == 5 && account.levels.getOrDefault(Skill.SLAYER, 0) < 50)
            return account.levels.getOrDefault(Skill.SLAYER, 0) >= 99 ? "Check Slayer cape access" : "Requirements unmet";
        return id == 7 ? "Level access met · first task required" : "Standard access met";
    }
    static int[] defaultRewards(int master)
    {
        switch (master)
        { case 1: return new int[]{0,0,0,0,0,0}; case 2: return new int[]{6,30,90,150,210,300}; case 3: return new int[]{8,40,120,200,280,400};
          case 4: return new int[]{10,50,150,250,350,500}; case 5: return new int[]{15,75,225,375,525,750}; case 6: return new int[]{12,60,180,300,420,600};
          case 7: return new int[]{25,125,375,625,875,1250}; case 8: return new int[]{18,90,270,450,630,900}; default: return null; }
    }
    static int[] observedRewards(Snapshot state)
    {
        if (state.master == 6 && state.bits.getOrDefault(VarbitID.WESTERN_ELITE_REWARD, 0) > 0) return defaultRewards(5);
        if (state.master == 8 && state.bits.getOrDefault(VarbitID.KOUREND_ELITE_REWARD, 0) > 0) return new int[]{20,100,300,500,700,1000};
        return defaultRewards(state.master);
    }
    JPanel createPanel()
    {
        if (panel != null) return panel; panel = SidebarUi.column(); panel.setName("slayer-planning");
        panel.addHierarchyListener(event -> { visible = panel.isShowing(); if (visible) render(); });
        JPanel filters = SidebarUi.section("Plan your Slayer");
        masterChoice = combo("Current master", "Turael / Aya / Spria", "Mazchna / Achtryn", "Vannaka", "Chaeldar", "Duradel / Kuradal", "Nieve / Steve", "Krystilia", "Konar", "Mortimer");
        masterChoice.addActionListener(event -> { if (changingMaster || masterChoice.getSelectedIndex() < 0) return; requestedMaster = shownMasters.get(masterChoice.getSelectedIndex()); selected = null; page = 0; if (body != null) { body.removeAll(); body.add(SidebarUi.label("Loading " + (requestedMaster == 0 ? "current master" : masterName(requestedMaster)))); finish(); } }); filters.add(SidebarUi.field("Master", masterChoice));
        hideLockedMasters = check("Hide locked masters", false); hideLockedMasters.addActionListener(event -> render()); filters.add(hideLockedMasters);
        mode = combo("Tasks", "Simulation", "My preferred tasks", "Planned skips / blocks", "Storage & task hunting"); mode.addActionListener(event -> { selected = null; page = 0; render(); }); filters.add(mode);
        goalChoice = combo("Balanced", "AFK", "Normal", "Intensive", "Boss", "XP", "Profit"); goalChoice.addActionListener(event -> { page = 0; render(); }); filters.add(SidebarUi.field("Sort for", goalChoice));
        eligibilityChoice = combo("Possible + unchecked", "Known eligible", "All task references"); eligibilityChoice.addActionListener(event -> { page = 0; render(); }); filters.add(eligibilityChoice);
        search = new JTextField(); style(search); search.addActionListener(event -> { page = 0; selected = null; render(); }); filters.add(SidebarUi.field("Find task", search)); panel.add(filters);
        body = SidebarUi.column(); panel.add(body); render(); return panel;
    }
    private Map<String, Rule> rules() { return plan.masters.getOrDefault(master, Collections.emptyMap()); }
    private void render()
    {
        if (body == null) return; body.removeAll(); Snapshot state = reading();
        if (state.profile == null) { body.add(SidebarUi.label("Log in to plan Slayer tasks.")); finish(); return; }
        refreshMasters(state);
        if (selected != null) { NativeTask task = state.tasks.stream().filter(item -> SlayerTaskCatalog.normalize(item.name).equals(selected)).findFirst().orElse(null); if (task != null) { detail(task, state); finish(); return; } selected = null; }
        JPanel header = SidebarUi.section(masterName(state.master)); header.add(SidebarUi.label(state.masterStatus)); header.add(SidebarUi.label(state.indexed + " / " + state.total + " task references")); body.add(header);
        if (mode.getSelectedIndex() == 1) { simulation(state); finish(); return; }
        if (mode.getSelectedIndex() == 4) { acquisition(state); finish(); return; }
        if (state.tasks.isEmpty()) body.add(SidebarUi.label(state.master <= 0 ? "No master selected" : "Task references loading"));
        List<NativeTask> found = new ArrayList<>(); String needle = SlayerTaskCatalog.normalize(search.getText()); Settings settings = settings();
        Map<Integer, SlayerPlanEngine.Odds> odds = SlayerPlanEngine.odds(state, rules(), settings.combatChecking);
        for (NativeTask task : state.tasks)
        {
            Rule rule = rule(task.name); Decision decision = Decision.valueOf(rule.decision); SlayerPlanEngine.Check check = SlayerPlanEngine.check(task, state, rule, settings.combatChecking);
            if (!SlayerTaskCatalog.normalize(task.name).contains(needle) || eligibilityChoice.getSelectedIndex() == 0 && check.access == SlayerPlanEngine.Access.INELIGIBLE || eligibilityChoice.getSelectedIndex() == 1 && check.access != SlayerPlanEngine.Access.ELIGIBLE) continue;
            if (mode.getSelectedIndex() == 2 && decision != Decision.PREFER || mode.getSelectedIndex() == 3 && decision != Decision.SKIP && decision != Decision.BLOCK) continue; found.add(task);
        }
        String goal = (String) goalChoice.getSelectedItem();
        found.sort(Comparator.comparingDouble((NativeTask task) -> score(task, rule(task.name), goal)).reversed().thenComparing(task -> task.name));
        int pages = Math.max(1, (found.size() + 4) / 5); page = Math.max(0, Math.min(page, pages - 1));
        for (int index = page * 5; index < Math.min(found.size(), page * 5 + 5); index++)
        {
            NativeTask task = found.get(index); Rule rule = rule(task.name); SlayerPlanEngine.Check check = SlayerPlanEngine.check(task, state, rule, settings.combatChecking);
            JPanel card = SidebarUi.section(task.name); card.add(SidebarUi.label(friendly(rule.decision) + " · " + accessLabel(check.access)));
            SlayerPlanEngine.Odds chance = odds.get(task.row); card.add(SidebarUi.label(oddsLabel(chance)));
            card.add(SidebarUi.label(suggestion(task, rule, goal)));
            card.add(SidebarUi.wrappedButton("Creature, method and preparation", () -> { selected = SlayerTaskCatalog.normalize(task.name); render(); })); body.add(card);
        }
        if (pages > 1) { javax.swing.JLabel count = new javax.swing.JLabel((page + 1) + " / " + pages, javax.swing.SwingConstants.CENTER); count.setFont(GuideText.FONT); count.setForeground(SidebarUi.TEXT); body.add(SidebarUi.row(SidebarUi.button("‹", () -> { page--; render(); }), count, SidebarUi.button("›", () -> { page++; render(); }))); }
        finish();
    }
    static List<Integer> masterChoices(Snapshot state, boolean hideLocked, int selected)
    {
        List<Integer> ids = new ArrayList<>(); ids.add(0);
        for (int id : new int[]{1,2,3,4,5,6,7,8,10})
        {
            String status = masterAccess(id, state);
            boolean locked = status.startsWith("Quest required") || status.equals("Requirements unmet");
            if (!hideLocked || !locked || selected == id) ids.add(id);
        }
        return Collections.unmodifiableList(ids);
    }
    private void refreshMasters(Snapshot state)
    {
        if (masterChoice == null) return;
        changingMaster = true;
        try {
            shownMasters = masterChoices(state, hideLockedMasters.isSelected(), requestedMaster); masterChoice.removeAllItems();
            for (int id : shownMasters) masterChoice.addItem(id == 0 ? "Current master" : masterName(id));
            masterChoice.setSelectedIndex(Math.max(0, shownMasters.indexOf(requestedMaster)));
        } finally { changingMaster = false; }
    }
    private double score(NativeTask task, Rule rule, String goal)
    {
        double score = "PREFER".equals(rule.decision) ? 1000 : "SKIP".equals(rule.decision) || "BLOCK".equals(rule.decision) ? -1000 : 0;
        if ("XP".equals(goal)) return score + (rule.xpKnown && rule.killsPerHour > 0 ? Math.log1p(rule.killsPerHour * rule.xpPerKill) * 10 : -500);
        if ("Profit".equals(goal)) return score + (rule.gpKnown && rule.killsPerHour > 0 ? Math.signum(rule.netGpPerKill) * Math.log1p(Math.abs(rule.killsPerHour * rule.netGpPerKill)) * 10 : -500);
        if ("AFK".equals(goal) && "Relaxed".equals(rule.effort) || goal.equals(rule.effort)) score += 100;
        if (methods != null && methods.forTask(task.name).stream().anyMatch(method -> "AFK".equals(goal) ? "Relaxed".equals(method.effort) : goal.equals(method.effort) || goal.equals(method.purpose))) score += 50;
        if ("Boss".equals(goal) && !rule.guide.isEmpty()) score += 100;
        return score + task.weight / 100.0;
    }
    private String suggestion(NativeTask task, Rule rule, String goal)
    {
        if ("XP".equals(goal)) return rule.xpKnown && rule.killsPerHour > 0 ? "Your rate · " + whole(rule.xpPerKill * rule.killsPerHour) + " XP/h" : "XP rate unrated";
        if ("Profit".equals(goal)) return rule.gpKnown && rule.killsPerHour > 0 ? "Your net · " + whole(rule.netGpPerKill * rule.killsPerHour) + " GP/h" : "Profit unrated";
        if (!rule.creature.isEmpty()) return rule.creature + (rule.effort.equals("Any") ? "" : " · " + rule.effort);
        if (methods != null) for (SlayerMethodCatalog.Method method : methods.forTask(task.name)) if ("AFK".equals(goal) && "Relaxed".equals(method.effort) || goal.equals(method.effort) || goal.equals(method.purpose)) return method.title + " · " + method.effort;
        return task.minimum >= 0 && task.maximum >= task.minimum ? "Base amount " + task.minimum + "–" + task.maximum : "Choose your creature and method";
    }
    private void detail(NativeTask task, Snapshot state)
    {
        body.add(SidebarUi.button("‹ Task list", () -> { selected = null; render(); }));
        JComboBox<String> detailMode = combo("Decision and access", "Creature and setup", "Personal rates"); detailMode.setSelectedIndex(detailPage); detailMode.addActionListener(event -> { detailPage = detailMode.getSelectedIndex(); render(); }); body.add(detailMode);
        Rule saved = rule(task.name); Settings settings = settings();
        JPanel card = SidebarUi.section(task.name); SlayerPlanEngine.Check checks = SlayerPlanEngine.check(task, state, saved, settings.combatChecking);
        card.add(SidebarUi.label(state.masterStatus)); card.add(SidebarUi.label(accessLabel(checks.access))); for (String reason : checks.reasons) card.add(SidebarUi.label(reason));
        card.add(SidebarUi.label(oddsLabel(SlayerPlanEngine.odds(state, rules(), settings.combatChecking).get(task.row))));
        JPanel decisions = SidebarUi.column(), setup = SidebarUi.column(), rates = SidebarUi.column();
        if (detailPage == 0) card.add(decisions); else if (detailPage == 1) card.add(setup); else card.add(rates);
        JComboBox<String> decision = combo("PREFER", "DO", "SKIP", "BLOCK"); decision.setSelectedItem(saved.decision); decisions.add(SidebarUi.field("My decision", decision));
        JComboBox<String> effort = combo("Any", "Relaxed", "Normal", "Intensive"); effort.setSelectedItem(saved.effort); decisions.add(SidebarUi.field("Effort", effort));
        JComboBox<String> access = combo("Unchecked", "Access confirmed", "Unavailable"); access.setSelectedIndex("YES".equals(saved.access) ? 1 : "NO".equals(saved.access) ? 2 : 0); decisions.add(SidebarUi.field("Quest / area access", access));
        JTextField note = field(saved.note); decisions.add(SidebarUi.field("My reason / setup", note));
        List<MobKnowledgeCatalog.Entry> choices = monsters == null ? Collections.emptyList() : monsters.matching(catalog.task(task.name).hints());
        JComboBox<String> creature = combo(); creature.addItem("Choose creature"); int creatureIndex = 0;
        for (MobKnowledgeCatalog.Entry entry : choices) { creature.addItem(entry.name + (entry.version == null || entry.version.isEmpty() ? "" : " · " + entry.version) + " [" + entry.id + "]"); if (entry.id == saved.npcId) creatureIndex = creature.getItemCount() - 1; }
        creature.setSelectedIndex(creatureIndex); setup.add(SidebarUi.field("Creature variant", creature));
        JPanel creatureFacts = SidebarUi.column(); setup.add(creatureFacts);
        Runnable refreshFacts = () -> { creatureFacts.removeAll(); int index = creature.getSelectedIndex() - 1; if (index >= 0) { MobKnowledgeCatalog.Entry entry = choices.get(index); creatureFacts.add(SidebarUi.heading(entry.name + (entry.version == null || entry.version.isEmpty() ? "" : " · " + entry.version))); for (String line : monsters.describe(entry.id, entry.name).summary) creatureFacts.add(SidebarUi.label(line)); } creatureFacts.revalidate(); creatureFacts.repaint(); };
        creature.addActionListener(event -> refreshFacts.run()); refreshFacts.run();
        JTextField area = field(saved.area); setup.add(SidebarUi.field("Chosen location", area));
        boolean restricted = assignment.location != null && SlayerTaskCatalog.normalize(assignment.name).equals(SlayerTaskCatalog.normalize(task.name));
        if (restricted) setup.add(SidebarUi.label("Assigned area · " + assignment.location));
        JCheckBox areaConfirmed = check("Assigned area checked", saved.areaConfirmed); if (restricted) setup.add(areaConfirmed);
        if (detailPage == 1) supplyPicker(setup, task, state);
        JTextField count = field(saved.plannedCount == 0 ? "" : Integer.toString(saved.plannedCount)); rates.add(SidebarUi.field("Planned task credits", count));
        JTextField rate = field(saved.killsPerHour == 0 ? "" : Double.toString(saved.killsPerHour)); rates.add(SidebarUi.field("My task credits / hour", rate));
        JTextField xp = field(saved.xpKnown ? Double.toString(saved.xpPerKill) : ""); rates.add(SidebarUi.field("My XP / task credit", xp));
        JTextField gp = field(saved.gpKnown ? Double.toString(saved.netGpPerKill) : ""); rates.add(SidebarUi.field("My net GP / task credit", gp));
        GuideText status = SidebarUi.label("");
        card.add(SidebarUi.button("Save task plan", () -> {
            try { Rule update = copy(saved); update.decision = (String) decision.getSelectedItem(); update.effort = (String) effort.getSelectedItem(); update.access = access.getSelectedIndex() == 1 ? "YES" : access.getSelectedIndex() == 2 ? "NO" : "AUTO"; update.note = note.getText().trim(); update.area = area.getText().trim(); update.areaConfirmed = areaConfirmed.isSelected(); update.supplies = new ArrayList<>(rule(task.name).supplies); update.plannedCount = count.getText().isBlank() ? 0 : Integer.parseInt(count.getText().trim()); update.killsPerHour = number(rate); update.xpKnown = !xp.getText().isBlank(); update.gpKnown = !gp.getText().isBlank(); update.xpPerKill = number(xp); update.netGpPerKill = number(gp);
                int index = creature.getSelectedIndex() - 1; update.npcId = index >= 0 ? choices.get(index).id : 0; update.creature = index >= 0 ? choices.get(index).name : "";
                if (detailPage == 1 && (saved.npcId != update.npcId || !saved.area.equals(update.area))) resetRates(update);
                status.setText(saveRule(state, task.name, update) ? "Task plan saved" : "Could not save this task plan");
            } catch (IllegalArgumentException invalid) { status.setText("Check the quantities and rates"); }
        })); card.add(status); body.add(card);
        if (detailPage == 1 && !task.areas.isEmpty()) { JPanel areas = SidebarUi.section("Assignment area options"); for (Area option : task.areas) { areas.add(SidebarUi.label(option.name)); if (option.hint != null && !option.hint.isEmpty()) areas.add(SidebarUi.label(option.hint)); areas.add(SidebarUi.button("Location Wiki", () -> wiki(option.name))); } body.add(areas); }
        if (detailPage == 1) { methodCards(task, state); preparation(task, state, saved, restricted); }
        if (detailPage == 2) personalMeasurement(task, state, saved);
    }
    private void supplyPicker(JPanel target, NativeTask task, Snapshot state)
    {
        JTextField query = field(supplyQuery); query.addActionListener(event -> { supplyQuery = query.getText().trim(); render(); }); target.add(SidebarUi.field("Add supply · name or item ID", query));
        if (supplyQuery.isBlank() || itemKnowledge == null) return;
        List<ItemKnowledgeCatalog.ItemRef> found = new ArrayList<>();
        try { ItemKnowledgeCatalog.ItemRef exact = itemKnowledge.item(Integer.parseInt(supplyQuery)); if (exact != null) found.add(exact); } catch (NumberFormatException name) { }
        if (found.isEmpty()) found.addAll(itemKnowledge.search(supplyQuery)); int shown = 0;
        for (ItemKnowledgeCatalog.ItemRef item : found)
        {
            if (shown++ >= 5) break;
            target.add(SidebarUi.wrappedButton("Add " + item.name, () -> { Rule update = rule(task.name); if (update.supplies.size() < 64 && !update.supplies.contains(item.id)) update.supplies.add(item.id); if (saveRule(state, task.name, update)) { supplyQuery = ""; render(); } }));
        }
        if (found.isEmpty()) target.add(SidebarUi.label("No item found"));
    }
    private void methodCards(NativeTask task, Snapshot state)
    {
        if (methods == null) return;
        for (SlayerMethodCatalog.Method method : methods.forTask(task.name))
        {
            JPanel card = SidebarUi.section(method.title); card.add(SidebarUi.label(method.effort + " · " + method.purpose)); card.add(SidebarUi.label(method.area)); card.add(SidebarUi.label(method.note));
            if (method.slayer > 0) card.add(SidebarUi.label((state.levels.getOrDefault(Skill.SLAYER, 0) >= method.slayer ? "✓ " : "✕ ") + method.slayer + " Slayer"));
            for (int step = 0; step < method.milestones.size(); step++) card.add(SidebarUi.label((step + 1) + ". " + method.milestones.get(step)));
            if (method.magic > 0) card.add(SidebarUi.label((state.levels.getOrDefault(Skill.MAGIC, 0) >= method.magic ? "✓ " : "✕ ") + method.magic + " Magic"));
            if (method.quest != null) card.add(SidebarUi.label(method.quest.replace('_', ' ') + " · " + state.quests.getOrDefault(method.quest, "UNKNOWN")));
            card.add(SidebarUi.button("Use this method", () -> { Rule update = rule(task.name); if (!update.method.equals(method.id)) resetRates(update); update.method = method.id; update.effort = method.effort; update.area = method.area; update.areaConfirmed = false; if (saveRule(state, task.name, update)) render(); }));
            for (int item : method.items) card.add(SidebarUi.wrappedButton("Pack " + itemLabel(item), () -> { Rule update = rule(task.name); if (!update.supplies.contains(item) && update.supplies.size() < 64) update.supplies.add(item); if (saveRule(state, task.name, update)) render(); }));
            card.add(SidebarUi.button("Method reference", () -> LinkBrowser.browse(method.source))); body.add(card);
        }
    }
    private void preparation(NativeTask task, Snapshot state, Rule saved, boolean restricted)
    {
        JPanel card = SidebarUi.section("Prepare and start");
        for (SlayerTaskCatalog.Gear gear : catalog.gear(task.name)) { card.add(SidebarUi.label(gear.title)); if (!gear.itemIds.isEmpty()) card.add(itemButton(gear.itemIds.get(0), "View required item", state)); }
        for (int id : saved.supplies)
        {
            String count = "";
            if (ownership != null) { OwnershipLedger.View owned = ownership.snapshot(); long total = owned.quantity(OwnershipLedger.Storage.INVENTORY, id) + owned.quantity(OwnershipLedger.Storage.EQUIPMENT, id) + owned.quantity(OwnershipLedger.Storage.BANK, id); count = " · " + total + " observed"; }
            card.add(itemButton(id, itemLabel(id) + count, state));
            card.add(SidebarUi.button("Remove supply", () -> { Rule update = rule(task.name); update.supplies.removeIf(item -> item == id); if (saveRule(state, task.name, update)) render(); }));
        }
        card.add(SidebarUi.button("Choose saved loadout", () -> { if (current(state)) openLoadouts.run(); }));
        card.add(SidebarUi.button("Show my supplies in bank", () -> { if (current(state)) bankFocus.accept(requiredItems(task, rule(task.name))); }));
        List<GuideCatalog.Entry> related = relatedGuides(task.name); int shown = 0;
        for (GuideCatalog.Entry guide : related)
        {
            if (shown++ >= 6) break;
            card.add(SidebarUi.wrappedButton(guide.title + " guide", () -> { if (current(state)) openGuide.accept(guide.id); }));
            card.add(SidebarUi.wrappedButton("Use " + guide.title + " for this plan", () -> { Rule update = rule(task.name); update.guide = guide.id; if (saveRule(state, task.name, update)) render(); }));
            WorldPoint point = firstStop(guide); if (!restricted && point != null) card.add(SidebarUi.button("Preview route", () -> { if (current(state)) route.accept(point); }));
        }
        if (restricted) card.add(SidebarUi.label("Boss alternatives need an assigned-area match."));
        if (!saved.guide.isEmpty()) card.add(SidebarUi.label("Selected guide · " + saved.guide));
        GuideText status = SidebarUi.label(""); card.add(SidebarUi.button("Start Slayer run", () -> {
            Prepared prepared = prepare(state, task); if (prepared == null) { status.setText("A matching assignment, saved creature and area confirmation are required"); return; }
            bankFocus.accept(prepared.itemIds); startActivity.accept(prepared); status.setText("Slayer run started");
        })); card.add(status); card.add(SidebarUi.button("Task strategy Wiki", () -> wiki(task.name))); body.add(card);
    }
    Prepared prepare(Snapshot state, NativeTask task)
    {
        if (!current(state) || state.tasks.stream().noneMatch(entry -> entry.row == task.row && entry.taskId == task.taskId)) return null; Rule rule = rule(task.name);
        boolean assigned = assignment.remaining > 0 && SlayerTaskCatalog.normalize(assignment.name).equals(SlayerTaskCatalog.normalize(task.name));
        if (!assigned || assignment.master != state.master || rule.npcId <= 0 || assignment.location != null && !rule.areaConfirmed) return null;
        String area = assigned && assignment.location != null ? assignment.location : rule.area;
        if (monsters != null && monsters.matching(catalog.task(task.name).hints()).stream().noneMatch(entry -> entry.id == rule.npcId)) return null;
        return new Prepared(state.profile, task.name, task.taskId, rule.creature, area, rule.guide, assigned ? assignment.remaining : rule.plannedCount,
            Set.of(rule.npcId), requiredItems(task, rule));
    }
    private Set<Integer> requiredItems(NativeTask task, Rule rule)
    {
        Set<Integer> result = new LinkedHashSet<>(rule.supplies);
        boolean assigned = assignment.remaining > 0 && SlayerTaskCatalog.normalize(assignment.name).equals(SlayerTaskCatalog.normalize(task.name));
        if (assigned) for (SlayerCoachTool.GearStatus gear : assignment.gear) if (!gear.ready && !gear.rule.itemIds.isEmpty())
        {
            int selected = gear.rule.itemIds.get(0);
            if (ownership != null) for (int id : gear.rule.itemIds) if (ownership.query(id).observedTotal > 0) { selected = id; break; }
            result.add(selected);
        }
        GuideCatalog.Entry guide = rule.guide.isEmpty() ? null : guides.entry(rule.guide);
        if (guide != null && guide.items != null) for (com.google.gson.JsonObject item : guide.items) result.addAll(GuideItemResolver.resolveAll(item));
        return Collections.unmodifiableSet(result);
    }
    private List<GuideCatalog.Entry> relatedGuides(String task)
    { List<GuideCatalog.Entry> result = new ArrayList<>(); for (String name : catalog.task(task).hints()) for (GuideCatalog.Entry guide : guides.search(name, "boss")) if (!result.contains(guide)) result.add(guide); return result; }
    private void personalMeasurement(NativeTask task, Snapshot state, Rule saved)
    {
        JPanel card = SidebarUi.section("Measure this method");
        JTextField credits = field(saved.measuredCredits > 0 ? Integer.toString(saved.measuredCredits) : "");
        JTextField minutes = field(saved.measuredMinutes > 0 ? Double.toString(saved.measuredMinutes) : "");
        card.add(SidebarUi.field("Assignment credits completed", credits)); card.add(SidebarUi.field("Elapsed minutes, including banking", minutes));
        GuideText result = SidebarUi.label("");
        card.add(SidebarUi.button("Use measured rate", () -> {
            try { int count = Integer.parseInt(credits.getText().trim()); double elapsed = Double.parseDouble(minutes.getText().trim());
                Rule update = rule(task.name); update.killsPerHour = SlayerAcquisitionPlan.creditsPerHour(count, elapsed); update.measuredCredits = count; update.measuredMinutes = elapsed;
                Measurement sample = new Measurement(); sample.method = update.method; sample.creature = update.creature; sample.area = update.area; sample.credits = count; sample.minutes = elapsed;
                update.measurements.add(sample); if (update.measurements.size() > 20) update.measurements.remove(0);
                if (saveRule(state, task.name, update)) { result.setText(whole(update.killsPerHour) + " credits/h saved for " + (update.method.isEmpty() ? task.name : update.method)); render(); } else result.setText("Sample exceeds the supported rate or quantity range");
            } catch (IllegalArgumentException invalid) { result.setText("Enter positive credits and elapsed minutes"); }
        })); card.add(result);
        for (int index = saved.measurements.size() - 1; index >= Math.max(0, saved.measurements.size() - 5); index--)
        {
            Measurement sample = saved.measurements.get(index); card.add(SidebarUi.label((sample.method.isEmpty() ? "Custom method" : sample.method) + " · " + whole(SlayerAcquisitionPlan.creditsPerHour(sample.credits,sample.minutes)) + " credits/h\n" + sample.creature + (sample.area.isEmpty() ? "" : " · " + sample.area)));
        }
        body.add(card);
    }
    private static void resetRates(Rule rule)
    { rule.killsPerHour = rule.xpPerKill = rule.netGpPerKill = rule.measuredMinutes = 0; rule.measuredCredits = 0; rule.xpKnown = rule.gpKnown = false; }
    private void acquisition(Snapshot state)
    {
        JPanel storage = SidebarUi.section("Task storage");
        storage.add(SidebarUi.label(state.storageUnlocked ? "✓ Task Storage unlocked" : "Task Storage unlock · " + (state.storageUnlockCost == null ? "500 points" : state.storageUnlockCost + " points · native")));
        storage.add(SidebarUi.label(state.storedMaster > 0 ? "Stored assignment · " + masterName(state.storedMaster) : "Stored slot empty · observed"));
        storage.add(SidebarUi.label(state.storageUnlocked
            ? state.storedMaster > 0 ? "Swap the stored and active assignments in Slayer Rewards, or retrieve the stored assignment when none is active." : "Store the current assignment in Slayer Rewards, then request another task. Storing and swapping cost no points."
            : "Unlock Task Storage in Slayer Rewards before saving an assignment."));
        storage.add(SidebarUi.button("Storage reference", () -> LinkBrowser.browse(SlayerAcquisitionPlan.SOURCE))); body.add(storage);
        JPanel card = SidebarUi.section("Choose how to obtain tasks");
        JComboBox<String> strategy = combo("Pay 30 points per cancellation", "Turael / Aya replacement", "Milestone boosting: nine + one");
        JTextField horizon = field(Integer.toString(settings().completions));
        JCheckBox resettable = check("Current family is absent from Turael / Aya's list", false);
        card.add(SidebarUi.field("Strategy", strategy)); card.add(SidebarUi.field("Next completed tasks", horizon)); card.add(resettable);
        JPanel result = SidebarUi.column();
        card.add(SidebarUi.button("Compare consequences", () -> {
            if (!current(state)) return; result.removeAll();
            if (strategy.getSelectedIndex() == 1 && !SlayerAcquisitionPlan.sameStreakCounter(assignment.master,state.master))
            { result.add(SidebarUi.label("Select a master using the active assignment's streak counter for this reset comparison")); result.revalidate(); result.repaint(); return; }
            try { int count = Integer.parseInt(horizon.getText().trim()); int[] schedule = settings().rewards == null ? observedRewards(state) : settings().rewards;
                SlayerAcquisitionPlan.Result outcome = SlayerAcquisitionPlan.calculate(SlayerAcquisitionPlan.Mode.values()[strategy.getSelectedIndex()], strategy.getSelectedIndex() == 2 ? state.master : assignment.master, strategy.getSelectedIndex() == 1 ? state.assignmentStreak : state.streak, count, schedule, resettable.isSelected(), assignment.remaining > 0);
                result.add(SidebarUi.label(outcome.reason));
                if (outcome.available) { result.add(SidebarUi.label("Immediate point spend · " + outcome.pointsSpent));
                    if (strategy.getSelectedIndex() > 0) result.add(SidebarUi.label("Scenario completion rewards · " + outcome.completionRewards + " points\nSame horizon at reward master · " + outcome.preservedRewards));
                    int n = 1; for (String step : outcome.steps) result.add(SidebarUi.label(n++ + ". " + step)); }
            } catch (IllegalArgumentException invalid) { result.add(SidebarUi.label("Choose 1–1000 completed tasks")); }
            result.revalidate(); result.repaint();
        })); card.add(result); body.add(card);
        JPanel cape = SidebarUi.section("Slayer cape repeat scenario");
        JComboBox<String> previous = combo("Choose previous completed task"); for (NativeTask task : state.tasks) previous.addItem(task.name);
        cape.add(SidebarUi.field("Previous task from this master", previous));
        JCheckBox available = check("Cape carried / worn and previous task still assignable", false); cape.add(available);
        JPanel chances = SidebarUi.column(); cape.add(SidebarUi.button("Calculate repeat chance", () -> {
            if (!current(state)) return; chances.removeAll();
            int index = previous.getSelectedIndex() - 1; if (index < 0 || !available.isSelected()) { chances.add(SidebarUi.label("Choose the previous task and confirm the cape conditions")); }
            else { NativeTask task = state.tasks.get(index); SlayerPlanEngine.Odds base = SlayerPlanEngine.odds(state, rules(), settings().combatChecking).get(task.row);
                if (base != null) { double low = SlayerAcquisitionPlan.capeChance(base.minimum, true, true, base.maximum > 0, true), high = SlayerAcquisitionPlan.capeChance(base.maximum, true, true, base.maximum > 0, true);
                    chances.add(SidebarUi.label(String.format(java.util.Locale.ROOT, "%s · %.2f–%.2f%% including repeat opportunity", task.name, low * 100, high * 100))); }
                chances.add(SidebarUi.label("Konar repeats the location too. A boss-family repeat can roll a different boss; its individual chance remains unrated.")); }
            chances.revalidate(); chances.repaint();
        })); cape.add(chances); cape.add(SidebarUi.button("Cape reference", () -> wiki("Slayer cape"))); body.add(cape);
    }
    private void simulation(Snapshot state)
    {
        Settings saved = settings(); JPanel card = SidebarUi.section("Assignment scenario");
        JComboBox<String> combat = combo("Unknown", "Combat checking enabled", "Combat checking disabled"); combat.setSelectedIndex("ENABLED".equals(saved.combatChecking) ? 1 : "DISABLED".equals(saved.combatChecking) ? 2 : 0); card.add(SidebarUi.field("Master dialogue setting", combat));
        JTextField count = field(Integer.toString(saved.completions)); card.add(SidebarUi.field("Tasks to complete", count));
        int[] schedule = saved.rewards == null ? observedRewards(state) : saved.rewards;
        JTextField rewards = field(schedule == null ? "" : join(schedule));
        JCheckBox customRewards = check("Custom rewards", saved.rewards != null || schedule == null); rewards.setEnabled(customRewards.isSelected()); customRewards.addActionListener(event -> rewards.setEnabled(customRewards.isSelected())); card.add(customRewards);
        card.add(SidebarUi.field("Points: base, 10, 50, 100, 250, 1000", rewards));
        card.add(SidebarUi.label(saved.rewards == null ? "Native diary reward checks applied" : "Your point reward schedule"));
        JCheckBox include = check("Use unchecked tasks", saved.includeUnchecked); card.add(include);
        GuideText status = SidebarUi.label(""); card.add(SidebarUi.button("Save and calculate", () -> {
            try { Settings update = new Settings(); update.combatChecking = combat.getSelectedIndex() == 1 ? "ENABLED" : combat.getSelectedIndex() == 2 ? "DISABLED" : "UNKNOWN"; update.completions = Integer.parseInt(count.getText().trim()); update.includeUnchecked = include.isSelected(); update.goal = (String) goalChoice.getSelectedItem(); List<Integer> parsed = parseInts(rewards.getText(), 6, true); if (customRewards.isSelected() && parsed.size() != 6) throw new IllegalArgumentException(); update.rewards = customRewards.isSelected() ? parsed.stream().mapToInt(Integer::intValue).toArray() : null; if (saveSettings(state, update)) render(); else status.setText("Could not save the scenario"); }
            catch (IllegalArgumentException invalid) { status.setText("Enter six point rewards and 1–1000 tasks"); }
        })); card.add(status); body.add(card);
        Map<String, Rule> rules = rules(); int streak = state.streak;
        SlayerPlanEngine.Projection result = SlayerPlanEngine.project(state, rules, saved.combatChecking, saved.includeUnchecked, saved.completions, streak, schedule);
        JPanel resultCard = SidebarUi.section("Projection"); resultCard.add(SidebarUi.label(result.reason));
        if (result.available)
        {
            resultCard.add(SidebarUi.label("Start streak " + streak + " · observed"));
            resultCard.add(SidebarUi.label(String.format(java.util.Locale.ROOT, "%.2f skips / completed task", result.skipsPerCompleted)));
            resultCard.add(SidebarUi.label("New planned blocks " + result.plannedBlocks + " · " + result.plannedBlockCost + " points"));
            resultCard.add(SidebarUi.label(state.freeBlocks < 0 ? "Free block slots · unchecked" : "Free block slots · " + state.freeBlocks));
            if (state.freeBlocks >= 0 && result.plannedBlocks > state.freeBlocks) resultCard.add(SidebarUi.label("This plan needs more free block slots."));
            if (result.plannedBlockCost > state.points) resultCard.add(SidebarUi.label("New blocks exceed your current " + state.points + " points."));
            resultCard.add(SidebarUi.label("Completion rewards " + result.rewards + " points"));
            resultCard.add(SidebarUi.label("Expected net " + whole(result.netPoints) + " points"));
            resultCard.add(SidebarUi.label("Expected skip spend " + whole(result.skipPointsPerCompleted * result.completed) + " points"));
            resultCard.add(SidebarUi.label(Double.isFinite(result.estimatedMinutes) ? "Your time model · " + whole(result.estimatedMinutes) + " min" : "Time · rate coverage incomplete"));
            resultCard.add(SidebarUi.label(Double.isFinite(result.estimatedXp) ? "Your XP model · " + whole(result.estimatedXp) : "XP · rate coverage incomplete"));
            resultCard.add(SidebarUi.label(Double.isFinite(result.estimatedNetGp) ? "Your net GP model · " + whole(result.estimatedNetGp) : "GP · rate coverage incomplete"));
            resultCard.add(SidebarUi.label("30 points per skip · " + SlayerAcquisitionPlan.blockCost(state.master) + " per new block. Unset task counts use the midpoint of base amounts; extensions and task-credit modifiers may change results."));
            if (state.masterStatus.contains("required") || state.masterStatus.contains("unmet") || state.masterStatus.contains("unchecked")) resultCard.add(SidebarUi.label("Master access must be checked before using this scenario."));
        }
        body.add(resultCard);
        if (result.available) blockCandidates(state, rules, result);
    }
    private void blockCandidates(Snapshot state, Map<String, Rule> rules, SlayerPlanEngine.Projection baseline)
    {
        if (SlayerAcquisitionPlan.blockCost(state.master) < 0) return;
        List<NativeTask> skips = new ArrayList<>();
        for (NativeTask task : state.tasks) if ("SKIP".equals(SlayerPlanEngine.rule(rules, task.name).decision)
            && SlayerPlanEngine.check(task, state, rule(task.name), settings().combatChecking).access != SlayerPlanEngine.Access.INELIGIBLE) skips.add(task);
        skips.sort(Comparator.comparingInt((NativeTask task) -> task.weight).reversed()); int shown = 0;
        for (NativeTask task : skips)
        {
            if (shown++ >= 5) break;
            double saving = baseline.acceptedWeight <= 0 ? 0 : 30.0 * task.weight / baseline.acceptedWeight;
            if (saving <= 0) continue;
            JPanel card = SidebarUi.section("Block candidate · " + task.name);
            card.add(SidebarUi.label(String.format(java.util.Locale.ROOT, "Avoids %.2f skip points / completed task", saving)));
            card.add(SidebarUi.label(SlayerAcquisitionPlan.blockCost(state.master) + "-point cost repaid after about " + whole(Math.ceil(SlayerAcquisitionPlan.blockCost(state.master) / saving)) + " completed tasks"));
            if (baseline.conditional) card.add(SidebarUi.label("Under this scenario's access assumptions"));
            card.add(SidebarUi.button("Model this block", () -> { Rule update = rule(task.name); update.decision = "BLOCK"; if (saveRule(state, task.name, update)) render(); })); body.add(card);
        }
    }
    static List<Integer> parseIds(String text) { return parseInts(text, 64, false); }
    private static List<Integer> parseInts(String text, int limit, boolean allowZero)
    {
        List<Integer> values = new ArrayList<>(); if (text.trim().isEmpty()) return values;
        for (String part : text.trim().split("[,;\\s]+")) { int value = Integer.parseInt(part); if (value < (allowZero ? 0 : 1) || value > 1_000_000 || values.size() >= limit) throw new IllegalArgumentException(); values.add(value); }
        return allowZero ? values : new ArrayList<>(new LinkedHashSet<>(values));
    }
    private String itemLabel(int id) { ItemKnowledgeCatalog.ItemRef ref = itemKnowledge == null ? null : itemKnowledge.item(id); return ref == null ? "Item " + id : ref.name; }
    private JPanel itemButton(int id, String label, Snapshot state)
    {
        JPanel row = new JPanel(new java.awt.BorderLayout(5, 0)); row.setOpaque(false); row.setAlignmentX(0);
        javax.swing.JLabel image = new javax.swing.JLabel(); image.setPreferredSize(new java.awt.Dimension(32, 32));
        if (itemManager != null) { net.runelite.client.util.AsyncBufferedImage icon = itemManager.getImage(id); if (icon != null) icon.addTo(image); }
        row.add(image, java.awt.BorderLayout.WEST); row.add(SidebarUi.wrappedButton(label, () -> { if (current(state)) openItem.accept(id); }), java.awt.BorderLayout.CENTER); return row;
    }
    private static double number(JTextField field) { return field.getText().isBlank() ? 0 : Double.parseDouble(field.getText().trim()); }
    private static String join(List<Integer> values) { List<String> labels = new ArrayList<>(); for (int value : values) labels.add(Integer.toString(value)); return String.join(", ", labels); }
    private static String join(int[] values) { List<String> labels = new ArrayList<>(); for (int value : values) labels.add(Integer.toString(value)); return String.join(", ", labels); }
    private static String whole(double value) { return String.format(java.util.Locale.ROOT, "%,.0f", value); }
    static String oddsLabel(SlayerPlanEngine.Odds odds)
    { if (odds == null) return "Chance unavailable"; return String.format(java.util.Locale.ROOT, odds.exact ? "Chance %.2f%% · confirmed table" : "Chance %.2f–%.2f%% · access range", odds.minimum * 100, odds.maximum * 100); }
    private static String accessLabel(SlayerPlanEngine.Access access) { return access == SlayerPlanEngine.Access.ELIGIBLE ? "Known eligible" : access == SlayerPlanEngine.Access.INELIGIBLE ? "Unavailable" : "Access unchecked"; }
    private static WorldPoint firstStop(GuideCatalog.Entry guide)
    { if (guide.sections != null) for (GuideCatalog.Section section : guide.sections) if (section.steps != null) for (GuideCatalog.Step step : section.steps) if (step.location != null) return step.location.point(); return null; }
    private static String friendly(String text) { return text.substring(0, 1) + text.substring(1).toLowerCase(java.util.Locale.ROOT); }
    private static void wiki(String name) { LinkBrowser.browse("https://oldschool.runescape.wiki/w/Special:Search?search=" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)); }
    private static JComboBox<String> combo(String... choices) { JComboBox<String> value = new JComboBox<>(choices); style(value); return value; }
    private static JTextField field(String text) { JTextField value = new JTextField(text); style(value); return value; }
    private static JCheckBox check(String text, boolean selected) { JCheckBox value = new JCheckBox("<html>" + text + "</html>", selected); value.setFont(GuideText.FONT); value.setForeground(SidebarUi.TEXT); value.setOpaque(false); value.setAlignmentX(0); return value; }
    private static void style(javax.swing.JComponent component) { component.setFont(GuideText.FONT); component.setBackground(SidebarUi.CARD); component.setForeground(SidebarUi.TEXT); component.setMinimumSize(new java.awt.Dimension(0, 29)); component.setPreferredSize(new java.awt.Dimension(190, 29)); component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 29)); }
    private void finish() { body.revalidate(); body.repaint(); }
}
