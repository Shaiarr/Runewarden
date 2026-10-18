/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** Local Easy-to-Grandmaster planning with authoritative native completion bits and points. */
@Singleton
final class CombatAchievementsTool
{
    static final String PLAN_KEY = "combatAchievementPlanV1";
    private static final int PAGE_SIZE = 6;
    private final Client client;
    private final ConfigManager config;
    private final Gson gson;
    private final CombatAchievementCatalog catalog;
    private final GuideCatalog guides;
    private final RunewardenDataStore dataStore;
    private final Map<Integer, List<CombatAchievementCatalog.Requirement>> access = new java.util.LinkedHashMap<>();
    private final Map<String, Quest> questIndex = new HashMap<>();
    private final Set<Skill> requiredSkills = new HashSet<>();
    private final Set<String> requiredQuests = new HashSet<>();
    private volatile boolean started;
    private volatile Snapshot snapshot = Snapshot.empty();
    private String profile;
    private long generation;
    private int ticks, stableSamples;
    private int[] warmupBits;
    private Set<Integer> baseline;
    private int baselinePoints;
    private Plan plan = new Plan();
    private Consumer<GoalPlan> addGoal = ignored -> { };
    void setGoalAction(Consumer<GoalPlan> action) { addGoal = action == null ? ignored -> { } : action; }
    private Consumer<String> openGuide = ignored -> {};
    private IntConsumer openItem = ignored -> {};
    private Consumer<CombatAchievementCatalog.Task> completed = ignored -> {};
    private JPanel panel, content, filters;
    private JComboBox<String> mode, target, difficulty, boss, type;
    private JTextField search;
    private JCheckBox incomplete;
    private boolean changing;
    private int page;
    private Integer selected;

    @Inject CombatAchievementsTool(Client client, ConfigManager config, Gson gson,
        CombatAchievementCatalog catalog, GuideCatalog guides, RunewardenDataStore dataStore)
    {
        this.client = client; this.config = config; this.gson = gson; this.catalog = catalog; this.guides = guides; this.dataStore = dataStore;
        for (Quest quest : Quest.values()) questIndex.put(QuestOrder.normalize(quest.getName()), quest);
        for (CombatAchievementCatalog.Task task : catalog.tasks())
        { List<CombatAchievementCatalog.Requirement> requirements = catalog.requirements(task, guides); access.put(task.id, requirements); for (CombatAchievementCatalog.Requirement requirement : requirements) indexRequirement(requirement); }

    }
    private void indexRequirement(CombatAchievementCatalog.Requirement requirement)
    {
        if ("quest".equals(requirement.kind)) requiredQuests.add(requirement.key);
        if ("skill".equals(requirement.kind)) try { requiredSkills.add(Skill.valueOf(requirement.key.toUpperCase(Locale.ROOT).replace("RUNECRAFTING", "RUNECRAFT"))); } catch (IllegalArgumentException ignored) { }
        if (requirement.children != null) for (CombatAchievementCatalog.Requirement child : requirement.children) indexRequirement(child);
    }
    List<CombatAchievementCatalog.Requirement> requirements(int taskId) { return access.getOrDefault(taskId, Collections.emptyList()); }
    CombatAchievementsTool(Client client, ConfigManager config, Gson gson, CombatAchievementCatalog catalog, GuideCatalog guides)
    { this(client, config, gson, catalog, guides, null); }

    void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? ignored -> {} : action; }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> {} : action; }
    void setCompletionAction(Consumer<CombatAchievementCatalog.Task> action) { completed = action == null ? ignored -> {} : action; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    Snapshot reading() { Snapshot value = snapshot; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value : Snapshot.empty(); }
    void openTask(int id) { if (catalog.find(id) != null) SwingUtilities.invokeLater(() -> { selected = id; page = 0; if (panel != null) render(); }); }
    static final int[] THRESHOLD_VARIABLES = {VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM,
        VarbitID.CA_THRESHOLD_HARD, VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER};
    static Map<String, Integer> validatedThresholds(int[] values)
    {
        if (values == null || values.length != 6) return Collections.emptyMap();
        Map<String, Integer> result = new java.util.LinkedHashMap<>(); int last = 0;
        for (int index = 0; index < values.length; index++)
        { int value = values[index]; if (value <= last || value > 100_000) return Collections.emptyMap(); result.put(CombatAchievementCatalog.DIFFICULTIES.get(index), value); last = value; }
        return Collections.unmodifiableMap(result);
    }
    int rewardThreshold(String tier) { Integer nativeValue = reading().thresholds.get(tier); return nativeValue == null ? catalog.threshold(tier) : nativeValue; }
    String nextRewardTier() { Snapshot value = reading(); for (String tier : CombatAchievementCatalog.DIFFICULTIES) if (value.points < rewardThreshold(tier)) return tier; return "Grandmaster"; }
    List<Integer> queued() { return Collections.unmodifiableList(new ArrayList<>(plan.queued)); }

    synchronized void clear()
    {
        profile = null; generation++; ticks = 0; stableSamples = 0; warmupBits = null; baseline = null; baselinePoints = 0;
        plan = new Plan(); snapshot = Snapshot.empty(); selected = null; page = 0;
        SwingUtilities.invokeLater(() -> { if (panel != null) render(); });
    }

    /** Client thread. Only 21 packed vars and the required account facts are sampled every five ticks. */
    synchronized void onGameTick()
    {
        if (!started) return;
        String next = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || next == null || next.isEmpty())
        { if (profile != null) clear(); return; }
        if (!next.equals(profile))
        {
            clear(); profile = next; plan = dataStore == null ? readPlan(config.getConfiguration(RunewardenConfig.GROUP, profile, PLAN_KEY))
                : dataStore.read(RunewardenConfig.GROUP, profile, PLAN_KEY, 1, Plan.class, this::validPlan, Plan::new);
        }
        if (ticks++ % 5 != 0) return;
        int[] bits = new int[CombatAchievementCatalog.COMPLETION_VARPS.length];
        for (int index = 0; index < bits.length; index++) bits[index] = client.getVarpValue(CombatAchievementCatalog.COMPLETION_VARPS[index]);
        Set<Integer> finished = new LinkedHashSet<>();
        for (CombatAchievementCatalog.Task task : catalog.tasks()) if (CombatAchievementCatalog.complete(bits, task.id)) finished.add(task.id);
        int points = Math.max(0, client.getVarbitValue(VarbitID.CA_POINTS));
        boolean consistent = points == catalog.points(finished);
        if (baseline == null)
        {
            stableSamples = consistent && Arrays.equals(bits, warmupBits) ? stableSamples + 1 : consistent ? 1 : 0;
            warmupBits = bits;
            if (stableSamples >= 3) { baseline = new LinkedHashSet<>(finished); baselinePoints = points; }
        }
        else if (!finished.containsAll(baseline) || points < baselinePoints)
        { baseline = null; stableSamples = 0; warmupBits = bits; }
        else if (consistent)
        {
            for (int id : finished) if (!baseline.contains(id)) completed.accept(catalog.find(id));
            baseline = new LinkedHashSet<>(finished); baselinePoints = points;
        }
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        for (Skill skill : requiredSkills) levels.put(skill, client.getRealSkillLevel(skill));
        Map<String, QuestState> quests = new HashMap<>();
        for (String name : requiredQuests)
        {
            Quest quest = questIndex.get(QuestOrder.normalize(name));
            if (quest != null) try { quests.put(name, quest.getState(client)); } catch (RuntimeException ignored) { }
        }
        Snapshot previous = snapshot;
        int[] thresholds = new int[THRESHOLD_VARIABLES.length];
        for (int index = 0; index < thresholds.length; index++) thresholds[index] = client.getVarbitValue(THRESHOLD_VARIABLES[index]);
        Snapshot current = new Snapshot(profile, generation, points, finished, levels, quests, baseline != null,
            validatedThresholds(thresholds), client.getVarbitValue(VarbitID.GIM_GROUPSIZE));
        snapshot = current;
        if (previous.same(current)) return;
        SwingUtilities.invokeLater(() -> { if (snapshot == current && panel != null && panel.isShowing()) render(); });
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column();
        panel.addHierarchyListener(event -> { if (panel.isShowing()) render(); });
        mode = combo(List.of("Recommended next", "Browse tasks", "My plan"));
        mode.setName("runewarden.ca.mode");
        mode.addActionListener(event -> { if (!changing) { selected = null; page = 0; render(); } }); panel.add(SidebarUi.field("Combat achievements", mode));
        filters = SidebarUi.section("FILTERS");
        difficulty = combo(withFirst("All tiers", CombatAchievementCatalog.DIFFICULTIES));
        difficulty.setName("runewarden.ca.tier");
        boss = combo(withFirst("All bosses", catalog.bosses())); type = combo(withFirst("All types", catalog.types()));
        search = new JTextField(); style(search); incomplete = new JCheckBox("Incomplete only", true); incomplete.setForeground(SidebarUi.TEXT); incomplete.setOpaque(false); incomplete.setFont(GuideText.FONT);
        filters.add(SidebarUi.field("Tier", difficulty)); filters.add(SidebarUi.field("Boss / activity", boss));
        filters.add(SidebarUi.field("Task type", type)); filters.add(SidebarUi.field("Search", search)); filters.add(incomplete);
        for (JComboBox<String> control : List.of(difficulty, boss, type)) control.addActionListener(event -> { if (!changing) { page = 0; render(); } });
        incomplete.addActionListener(event -> { page = 0; render(); });
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed(); } public void removeUpdate(DocumentEvent event) { changed(); } public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() { page = 0; render(); }
        });
        panel.add(filters); content = SidebarUi.column(); panel.add(content); render(); return panel;
    }

    private void render()
    {
        if (content == null) return;
        content.removeAll(); filters.setVisible(selected == null && mode.getSelectedIndex() == 1);
        if (selected != null) renderTask(catalog.find(selected));
        else
        {
            summary();
            if (mode.getSelectedIndex() == 2) renderPlan();
            else renderTasks(mode.getSelectedIndex() == 0);
        }
        content.revalidate(); content.repaint(); panel.revalidate();
    }

    private void summary()
    {
        Snapshot current = snapshot;
        JPanel summary = SidebarUi.section("PROGRESSION");
        summary.add(SidebarUi.heading(current.observed() ? current.points + " points" : "Log in to view progress"));
        summary.add(SidebarUi.label(current.observed() ? current.finished.size() + " / " + catalog.tasks().size() + " tasks complete" : catalog.tasks().size() + " task guides"));
        changing = true; target = combo(CombatAchievementCatalog.DIFFICULTIES);
        target.setName("runewarden.ca.target");
        String tier = plan.target == null ? nextRewardTier() : plan.target; target.setSelectedItem(tier); changing = false;
        String expectedProfile = profile; long expectedGeneration = generation;
        target.addActionListener(event -> { synchronized (this) { if (!changing && current(expectedProfile, expectedGeneration)) { plan.target = (String) target.getSelectedItem(); persist(); render(); } } });
        summary.add(SidebarUi.field("Reward target", target));
        int threshold = rewardThreshold(tier), left = Math.max(0, threshold - current.points);
        JProgressBar progress = new JProgressBar(0, threshold); progress.setValue(Math.min(threshold, current.points));
        progress.setStringPainted(true); progress.setString(current.observed() ? left + " points to " + tier : threshold + " points · " + tier);
        progress.setFont(GuideText.FONT); progress.setForeground(SidebarUi.GOLD); progress.setBackground(SidebarUi.CARD);
        progress.setMinimumSize(new Dimension(0, 24)); progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24)); summary.add(progress);
        summary.add(SidebarUi.label(current.thresholds.isEmpty() ? "Reference reward targets" : current.groupSize >= 2 && current.groupSize <= 5 ? "Group Ironman · " + current.groupSize + " players · native targets" : "Native account reward targets"));
        content.add(summary);
    }

    private List<CombatAchievementCatalog.Task> choices(boolean recommended)
    {
        Snapshot current = snapshot;
        List<CombatAchievementCatalog.Task> result = catalog.filter(current.finished,
            recommended ? null : (String) difficulty.getSelectedItem(), recommended ? null : (String) boss.getSelectedItem(),
            recommended ? null : (String) type.getSelectedItem(), recommended ? "" : search.getText(), recommended || incomplete.isSelected());
        if (recommended && current.observed()) result.removeIf(task -> blocked(task, current));
        return result;
    }

    private void renderTasks(boolean recommended)
    {
        List<CombatAchievementCatalog.Task> tasks = choices(recommended);
        JPanel heading = SidebarUi.section(recommended ? "NEXT TASKS" : "TASKS");
        heading.add(SidebarUi.label(tasks.size() + (recommended ? " incomplete · lower tiers first" : " matching tasks")));
        if (recommended) heading.add(button("Queue next five", () -> {
            int added = 0;
            for (CombatAchievementCatalog.Task task : tasks) if (plan.queued.size() < 100 && !plan.queued.contains(task.id) && !snapshot.finished.contains(task.id))
            { plan.queued.add(task.id); if (++added >= 5) break; }
            persist(); render();
        }, true));
        content.add(heading);
        int pages = Math.max(1, (tasks.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
        for (int index = page * PAGE_SIZE; index < Math.min(tasks.size(), (page + 1) * PAGE_SIZE); index++) taskCard(tasks.get(index), false);
        paging(pages);
    }

    private void taskCard(CombatAchievementCatalog.Task task, boolean queued)
    {
        JPanel card = SidebarUi.section(null); card.add(SidebarUi.heading(task.name));
        card.add(SidebarUi.label(task.difficulty + " · " + task.points + " pt · " + task.type));
        card.add(SidebarUi.label(task.boss + (snapshot.finished.contains(task.id) ? " · Complete" : "")));
        if (blocked(task, snapshot)) card.add(SidebarUi.label("Missing requirement"));
        JButton open = SidebarUi.button("Method", () -> { selected = task.id; render(); });
        JButton queue = button(queued ? "Remove" : plan.queued.contains(task.id) ? "Queued" : "Queue", () -> {
            if (queued) plan.queued.remove(Integer.valueOf(task.id)); else if (!plan.queued.contains(task.id) && plan.queued.size() < 100) plan.queued.add(task.id);
            persist(); render();
        }, true);
        queue.setEnabled(snapshot.observed() && (queued || (!snapshot.finished.contains(task.id) && !plan.queued.contains(task.id))));
        card.add(SidebarUi.row(open, queue));
        if (queued)
        {
            card.add(SidebarUi.row(button("Up", () -> move(task.id, -1), true), button("Down", () -> move(task.id, 1), true)));
        }
        content.add(card);
    }

    private void renderPlan()
    {
        int pendingPoints = 0;
        for (int id : plan.queued) if (!snapshot.finished.contains(id)) { CombatAchievementCatalog.Task task = catalog.find(id); if (task != null) pendingPoints += task.points; }
        JPanel summary = SidebarUi.section("MY PLAN"); summary.add(SidebarUi.label(plan.queued.size() + " tasks · " + pendingPoints + " available points")); content.add(summary);
        List<CombatAchievementCatalog.Task> tasks = new ArrayList<>();
        for (int id : plan.queued) { CombatAchievementCatalog.Task task = catalog.find(id); if (task != null) tasks.add(task); }
        int pages = Math.max(1, (tasks.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
        for (int index = page * PAGE_SIZE; index < Math.min(tasks.size(), (page + 1) * PAGE_SIZE); index++) taskCard(tasks.get(index), true);
        paging(pages);
    }

    private void renderTask(CombatAchievementCatalog.Task task)
    {
        content.add(SidebarUi.button("Back", () -> { selected = null; render(); }));
        if (task == null) return;
        JPanel objective = SidebarUi.section(task.name);
        objective.add(SidebarUi.label(task.difficulty + " · " + task.points + " points · " + task.type));
        objective.add(SidebarUi.label(task.boss + (snapshot.finished.contains(task.id) ? " · Complete" : "")));
        objective.add(SidebarUi.heading(task.description)); content.add(objective);
        if (!requirements(task.id).isEmpty())
        {
            JPanel requirements = SidebarUi.section("UNLOCKS");
            for (CombatAchievementCatalog.Requirement requirement : requirements(task.id))
            {
                int status = requirementState(requirement, snapshot);
                String label = "skill".equals(requirement.kind) ? requirement.value + " " + requirement.key + (requirement.boostable ? " · boostable" : requirement.baseLevelOnly ? " · base level" : "") : requirement.label == null || requirement.label.isEmpty() ? requirement.key : requirement.label;
                GuideText text = SidebarUi.label((status > 0 ? "Complete · " : status < 0 ? "Needed · " : "Check · ") + label);
                if (status < 0) text.setForeground(new Color(255, 160, 139)); requirements.add(text);
                if ("quest".equals(requirement.kind) && guides.entry("quest:" + requirement.key) != null)
                    requirements.add(SidebarUi.button("Quest Helper", () -> openGuide.accept("quest:" + requirement.key)));
            }
            content.add(requirements);
        }
        if (!task.context.isEmpty())
        {
            JPanel preparation = SidebarUi.section("PREPARATION");
            for (String paragraph : task.context) preparation.add(SidebarUi.label(paragraph)); content.add(preparation);
        }
        if (!task.method.isEmpty())
        {
            JPanel method = SidebarUi.section("METHOD"); int index = 0;
            for (String paragraph : task.method) { method.add(SidebarUi.heading("Step " + ++index)); method.add(SidebarUi.label(paragraph)); } content.add(method);
        }
        if (task.guideId != null)
        {
            GuideCatalog.Entry guide = guides.entry(task.guideId);
            if (guide != null) { content.add(SidebarUi.heading(guide.title)); content.add(SidebarUi.button("Boss guide & markers", () -> openGuide.accept(task.guideId))); }
        }
        if (!task.items.isEmpty())
        {
            JPanel items = SidebarUi.section("REFERENCED ITEMS");
            for (int index = 0; index < Math.min(8, task.items.size()); index++)
            {
                CombatAchievementCatalog.ItemReference item = task.items.get(index);
                items.add(SidebarUi.label(item.name)); items.add(SidebarUi.button("View item", () -> openItem.accept(item.itemId)));
            }
            content.add(SidebarUi.disclosure("Items", items));
        }
        content.add(button(plan.queued.contains(task.id) ? "Remove from plan" : "Add to plan", () -> {
            if (plan.queued.contains(task.id)) plan.queued.remove(Integer.valueOf(task.id)); else if (plan.queued.size() < 100) plan.queued.add(task.id);
            persist(); render();
        }, true));
        content.add(button("Track personal goal", () -> addGoal.accept(GoalPlan.progress("ca", Integer.toString(task.id), 1, task.name, Collections.emptyList())), true));
        content.add(SidebarUi.button("Task Wiki", () -> LinkBrowser.browse(task.source)));
    }

    private void paging(int pages)
    {
        if (pages <= 1) return;
        JButton previous = SidebarUi.button("Previous", () -> { page--; render(); }); previous.setEnabled(page > 0);
        JButton next = SidebarUi.button("Next", () -> { page++; render(); }); next.setEnabled(page < pages - 1);
        content.add(SidebarUi.label("Page " + (page + 1) + " / " + pages)); content.add(SidebarUi.row(previous, next));
    }
    private void move(int id, int direction)
    {
        int index = plan.queued.indexOf(id), destination = index + direction;
        if (index >= 0 && destination >= 0 && destination < plan.queued.size()) { Collections.swap(plan.queued, index, destination); persist(); render(); }
    }
    private JButton button(String label, Runnable action, boolean accountBound)
    {
        String expectedProfile = profile; long expectedGeneration = generation;
        JButton button = SidebarUi.button(label, () -> { synchronized (this) { if (!accountBound || current(expectedProfile, expectedGeneration)) action.run(); } });
        button.setEnabled(!accountBound || snapshot.observed()); return button;
    }
    private boolean current(String expectedProfile, long expectedGeneration)
    { return started && expectedProfile != null && expectedProfile.equals(profile) && expectedProfile.equals(config.getRSProfileKey()) && generation == expectedGeneration; }
    private void persist() { if (profile != null && profile.equals(config.getRSProfileKey())) { if (dataStore == null) config.setConfiguration(RunewardenConfig.GROUP, profile, PLAN_KEY, gson.toJson(plan)); else dataStore.write(RunewardenConfig.GROUP, profile, PLAN_KEY, 1, plan); } }
    private boolean validPlan(Plan value)
    {
        if (value == null || value.version != 1 || value.queued == null || value.queued.size() > 100
            || value.target != null && !CombatAchievementCatalog.DIFFICULTIES.contains(value.target)) return false;
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Integer id : value.queued) if (id == null || catalog.find(id) == null || !ids.add(id)) return false;
        return true;
    }
    private Plan readPlan(String json)
    {
        if (json == null || json.length() > 50_000) return new Plan();
        try
        {
            Plan parsed = gson.fromJson(json, Plan.class); if (parsed == null || parsed.version != 1 || parsed.queued == null) return new Plan();
            Plan clean = new Plan(); if (CombatAchievementCatalog.DIFFICULTIES.contains(parsed.target)) clean.target = parsed.target;
            for (Integer id : parsed.queued) if (id != null && catalog.find(id) != null && !clean.queued.contains(id) && clean.queued.size() < 100) clean.queued.add(id);
            return clean;
        }
        catch (RuntimeException invalid) { return new Plan(); }
    }
    static int requirementState(CombatAchievementCatalog.Requirement requirement, Snapshot snapshot)
    {
        if (!snapshot.observed()) return 0;
        if ("choice".equals(requirement.kind) && requirement.children != null)
        { boolean unknown = false; for (CombatAchievementCatalog.Requirement child : requirement.children) { int state = requirementState(child, snapshot); if (state > 0) return 1; unknown |= state == 0; } return unknown ? 0 : -1; }
        if ("skill".equals(requirement.kind))
            try { Integer level = snapshot.levels.get(Skill.valueOf(requirement.key.toUpperCase(Locale.ROOT).replace("RUNECRAFTING", "RUNECRAFT"))); return level == null ? 0 : level >= requirement.value ? 1 : requirement.baseLevelOnly ? 0 : -1; }
            catch (IllegalArgumentException invalid) { return 0; }
        if ("quest".equals(requirement.kind)) { QuestState state = snapshot.quests.get(requirement.key); return state == null ? 0 : state == QuestState.FINISHED || requirement.started && state == QuestState.IN_PROGRESS ? 1 : -1; }
        return 0;
    }
    private boolean blocked(CombatAchievementCatalog.Task task, Snapshot snapshot)
    { for (CombatAchievementCatalog.Requirement requirement : requirements(task.id)) if (requirementState(requirement, snapshot) < 0 && !requirement.boostable) return true; return false; }
    private static List<String> withFirst(String first, List<String> values) { List<String> result = new ArrayList<>(); result.add(first); result.addAll(values); return result; }
    private static JComboBox<String> combo(List<String> values) { JComboBox<String> combo = new JComboBox<>(values.toArray(new String[0])); style(combo); return combo; }
    private static void style(JComponent control)
    { control.setFont(GuideText.FONT); control.setForeground(SidebarUi.TEXT); control.setBackground(SidebarUi.CARD); control.setMinimumSize(new Dimension(0, 30)); control.setPreferredSize(new Dimension(180, 30)); control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); }
    private static final class Plan { int version = 1; String target; List<Integer> queued = new ArrayList<>(); }
    static final class Snapshot
    {
        final String profile; final long generation; final int points; final Set<Integer> finished;
        final Map<Skill, Integer> levels; final Map<String, QuestState> quests; final boolean notificationBaseline;
        final Map<String, Integer> thresholds; final int groupSize;
        Snapshot(String profile, long generation, int points, Set<Integer> finished, Map<Skill, Integer> levels, Map<String, QuestState> quests, boolean baseline)
        { this(profile, generation, points, finished, levels, quests, baseline, Collections.emptyMap(), 0); }
        Snapshot(String profile, long generation, int points, Set<Integer> finished, Map<Skill, Integer> levels, Map<String, QuestState> quests, boolean baseline, Map<String, Integer> thresholds, int groupSize)
        { this.profile = profile; this.generation = generation; this.points = points; this.finished = Collections.unmodifiableSet(new LinkedHashSet<>(finished)); this.levels = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(levels)); this.quests = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(quests)); this.notificationBaseline = baseline; this.thresholds = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(thresholds)); this.groupSize = groupSize; }
        static Snapshot empty() { return new Snapshot(null, 0, 0, Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), false); }
        boolean observed() { return profile != null; }
        boolean same(Snapshot other) { return java.util.Objects.equals(profile, other.profile) && generation == other.generation && points == other.points && finished.equals(other.finished) && levels.equals(other.levels) && quests.equals(other.quests) && notificationBaseline == other.notificationBaseline && thresholds.equals(other.thresholds) && groupSize == other.groupSize; }
    }
}
