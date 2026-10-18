/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Personal progression queue. All XP/unlock observations and mutations run on the client thread. */
@Singleton
final class GoalPlannerTool
{
    private static final Color BACKGROUND = new Color(17, 18, 21), CARD = new Color(31, 32, 36);
    private static final Color GOLD = new Color(235, 199, 123), WHITE = new Color(244, 241, 233);
    private static final Color MUTED = new Color(189, 188, 181), GREEN = new Color(128, 211, 157);
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final GoalCatalog catalog;
    private final GoalPlan.Store store;
    private final Gson gson;
    private static final String[] KINDS = {"Skill", "Quest", "Item / resource", "Equipment set", "Collection item", "Boss kill count", "Achievement diary", "Combat achievement", "Music track", "Account metric", "Currency", "Permanent unlock"};
    private static final String[] KIND_KEYS = {"skill", "quest", "item", "set", "collection", "kc", "diary", "ca", "music", "metric", "currency", "unlock"};
    private volatile boolean started;
    private boolean completionBaseline; private final java.util.Set<String> observedGoalIds = new java.util.LinkedHashSet<>(); private Consumer<GoalPlan> completed = ignored -> { };
    void setCompletionAction(Consumer<GoalPlan> action) { completed = action == null ? ignored -> { } : action; }
    private volatile View view = View.empty();
    private volatile java.util.Set<Integer> activeSupplyTargets = Collections.emptySet();
    private Consumer<GoalPlan> openProgress = ignored -> { };
    private Consumer<java.util.Set<Integer>> bankFocus = ignored -> { };
    private java.util.function.IntConsumer openItem = ignored -> { };
    void setOpenProgressAction(Consumer<GoalPlan> action) { openProgress = action == null ? ignored -> { } : action; }
    void setBankFocusAction(Consumer<java.util.Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    void setOpenItemAction(java.util.function.IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    private volatile Consumer<String> openGuide = id -> {};
    private volatile Consumer<String> openMethod = id -> {};
    private String profile;
    private int ticks, warmup;
    private JPanel panel, overview, rows, form, navigation, skillFields, questFields, options;
    private JLabel state;
    private GuideText error;
    private JButton save, cancel, addGoal, optionsToggle;
    private JComboBox<String> kind, targetMode, intensity;
    private JComboBox<Skill> skill;
    private JComboBox<GuideCatalog.Entry> quest;
    private JComboBox<ActivityXpCatalog.Method> method;
    private JTextField target, rate, budget, weekly, interval;
    private JCheckBox archived;
    private GoalPlan editing;
    private GoalPlan.Session editingSession;
    private boolean rendering;
    private volatile boolean panelShowing;
    private int goalPage;
    private JButton previousPage, nextPage;
    private JLabel pageLabel;
    private JPanel progressFields, transfer;
    private JTextField progressSearch, progressTarget, setItems, progressLabel;
    private JComboBox<AccountProgressTool.Choice> progressChoice;
    private JButton undo, redo, importPlan;
    private JTextArea planCode;
    private GuideText planPreview;
    private List<GoalPlan> pendingImport = Collections.emptyList();

    @Inject GoalPlannerTool(Client client, ClientThread clientThread, ConfigManager config, Gson gson,
        GoalCatalog catalog, RunewardenDataStore dataStore)
    {
        this.client = client; this.clientThread = clientThread; this.config = config;
        this.catalog = catalog; this.gson = gson; this.store = new GoalPlan.Store(gson, config, dataStore);
    }
    GoalPlannerTool(Client client, ClientThread clientThread, ConfigManager config, Gson gson, GoalCatalog catalog)
    { this(client, clientThread, config, gson, catalog, null); }
    void addGoal(GoalPlan goal)
    {
        View current = validView(); if (current == null || goal == null) return;
        mutate(current.session, () -> store.importGoals(current.session, List.of(goal)));
    }
    void addProgressGoal(String kind, String key, long target, String label, List<Integer> items)
    { addGoal(GoalPlan.progress(kind, key, target, label, items)); }
    /** Detached values; callers cannot mutate the live character plan. */
    List<GoalPlan> goalPlans()
    {
        View current = validView(); if (current == null) return Collections.emptyList();
        List<GoalPlan> result = new ArrayList<>(); for (GoalPlan goal : current.goals) result.add(goal.copy());
        return Collections.unmodifiableList(result);
    }
    GoalCatalog.Facts facts() { View current = validView(); return current == null ? null : current.facts; }
    java.util.Set<Integer> goalItemTargets() { return validView()==null?Collections.emptySet():activeSupplyTargets; }
    void openSkillTarget(Skill skill,long targetXp)
    {
        if(skill==null||skill==Skill.OVERALL||targetXp<1||targetXp>GoalPlan.MAX_XP)return;
        View displayed=validView();if(displayed==null)return;GoalPlan.Session expected=displayed.session;
        clientThread.invokeLater(()->{
            if(!active(expected))return;GoalPlan selected=null;
            for(GoalPlan goal:store.goals())if(!goal.archived&&"skill".equals(goal.kind)&&goal.skill()==skill&&goal.targetXp==targetXp){selected=goal;break;}
            if(selected==null){int observed=Math.max(0,Math.min(GoalPlan.MAX_XP,client.getSkillExperience(skill)));selected=GoalPlan.skill(skill,(int)targetXp,observed);if(!store.put(expected,selected))return;}
            String goalId=selected.id;refresh();SwingUtilities.invokeLater(()->{if(store.current(expected))focusGoal(goalId);});
        });
    }
    void openGoal(String id)
    { View displayed=validView();if(displayed==null)return;GoalPlan.Session expected=displayed.session;SwingUtilities.invokeLater(()->{if(store.current(expected))focusGoal(id);}); }
    private void focusGoal(String id)
    {
        View current=validView();if(current==null)return;int displayed=0;
        for(GoalPlan goal:current.goals){if(goal.id.equals(id)){if(archived!=null&&goal.archived)archived.setSelected(true);goalPage=displayed/8;if(panel!=null){resetEditing();render();}return;}if(!goal.archived||archived!=null&&archived.isSelected())displayed++;}
    }
    void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? id -> {} : action; }
    void setOpenMethodAction(Consumer<String> action) { openMethod = action == null ? id -> {} : action; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear()
    {
        profile = null; ticks = 0; warmup = 2; completionBaseline = false; observedGoalIds.clear(); store.clear(); view = View.empty(); activeSupplyTargets = Collections.emptySet();
        SwingUtilities.invokeLater(() -> { resetEditing(); render(); });
    }

    void onGameTick()
    {
        if (!started) return;
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        { if (profile != null) clear(); return; }
        String current = config.getRSProfileKey();
        if (current == null || current.isEmpty()) { if (profile != null) clear(); return; }
        if (!current.equals(profile))
        {
            store.bind(current); profile = current; warmup = 2; completionBaseline = false; observedGoalIds.clear();
            view = View.empty();
            SwingUtilities.invokeLater(() -> { resetEditing(); render(); });
        }
        if (warmup-- > 0 || ticks++ % 5 != 0) return;
        refresh();
    }

    private GoalCatalog.Facts observe()
    {
        Map<Skill, Integer> xp = new EnumMap<>(Skill.class), levels = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL)
        {
            xp.put(skill, Math.max(0, client.getSkillExperience(skill)));
            levels.put(skill, client.getRealSkillLevel(skill));
        }
        Map<String, QuestState> quests = new LinkedHashMap<>();
        java.util.Set<String> neededQuests = catalog.observedQuestKeys(store.goals());
        for (Quest quest : Quest.values())
        {
            if (!neededQuests.contains(GoalCatalog.questKey(quest.getName()))) continue;
            try { quests.put(GoalCatalog.questKey(quest.getName()), quest.getState(client)); }
            catch (RuntimeException ignored) { /* Unsupported observations stay unknown. */ }
        }
        return new GoalCatalog.Facts(xp, levels, quests, client.getLocalPlayer().getCombatLevel());
    }

    private void refresh()
    {
        GoalPlan.Session session = store.session();
        if (!active(session)) return;
        GoalCatalog.Facts facts = observe();
        List<GoalPlan> goals = store.goals();
        for (GoalPlan goal : goals)
        {
            Long observed = goal.progressGoal() ? catalog.progress(goal) : null;
            boolean known = "skill".equals(goal.kind) ? true : "quest".equals(goal.kind) ? catalog.guide(goal.questGuideId) != null && facts.quest(catalog.guide(goal.questGuideId).title) != null : observed != null;
            boolean previouslyObserved = observedGoalIds.contains(goal.id); if (known) observedGoalIds.add(goal.id);
            boolean reached = "skill".equals(goal.kind) ? facts.xp(goal.skill()) >= goal.targetXp
                : "quest".equals(goal.kind) ? facts.finished(catalog.guide(goal.questGuideId)) : observed != null && observed >= goal.targetAmount;
            if (reached && goal.achievedAt == 0)
            { goal.achievedAt = System.currentTimeMillis(); if (store.putObserved(session, goal) && completionBaseline && previouslyObserved) completed.accept(goal.copy()); }
            else if (!reached && goal.volatileGoal() && observed != null && goal.achievedAt != 0)
            { goal.achievedAt = 0; store.putObserved(session, goal); }
        }
        if (!store.current(session)) return; completionBaseline = true;
        Map<String, Long> progression = new LinkedHashMap<>();
        for (GoalPlan goal : store.goals()) if (goal.progressGoal()) progression.put(goal.id, catalog.progress(goal));
        java.util.Set<Integer> targets=new java.util.LinkedHashSet<>();for(GoalPlan goal:store.goals())targets.addAll(catalog.goalSupplyTargets(goal));activeSupplyTargets=Collections.unmodifiableSet(targets);
        View next = new View(session, facts, store.goals(), progression);
        View old = view; view = next;
        if (panelShowing && !next.same(old)) SwingUtilities.invokeLater(() -> { if (view == next) render(); });
    }
    private boolean active(GoalPlan.Session session)
    {
        return started && store.current(session) && client.getGameState() == GameState.LOGGED_IN
            && client.getLocalPlayer() != null;
    }
    private void mutate(GoalPlan.Session session, Runnable action)
    {
        clientThread.invokeLater(() ->
        {
            if (!active(session)) return;
            action.run(); refresh(); SwingUtilities.invokeLater(this::render);
        });
    }

    JPanel createPanel()
    {
        panel = vertical(BACKGROUND);
        panel.addHierarchyListener(event ->
        {
            if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0)
            { panelShowing = panel.isShowing(); if (panelShowing) render(); }
        });
        overview = vertical(BACKGROUND); panel.add(overview);
        overview.add(text("Personal goals", GOLD, true));
        state = new JLabel(); style(state); overview.add(state);
        addGoal = button("Add goal", this::newGoal); overview.add(addGoal);
        undo = button("Undo", () -> { View value = validView(); if (value != null) mutate(value.session, () -> store.undo(value.session)); });
        redo = button("Redo", () -> { View value = validView(); if (value != null) mutate(value.session, () -> store.redo(value.session)); });
        overview.add(SidebarUi.row(undo, redo));
        overview.add(button("Share or import plan ▸", () -> { transfer.setVisible(!transfer.isVisible()); panel.revalidate(); }));
        buildTransfer(); overview.add(transfer);
        archived = new JCheckBox("Show archived goals"); style(archived);
        archived.addActionListener(event -> { goalPage = 0; render(); }); overview.add(archived);
        rows = vertical(BACKGROUND); overview.add(rows);
        navigation = SidebarUi.row();
        previousPage = button("‹", () -> { goalPage--; render(); }); nextPage = button("›", () -> { goalPage++; render(); });
        pageLabel = new JLabel(); style(pageLabel); navigation.add(previousPage); navigation.add(pageLabel); navigation.add(nextPage); overview.add(navigation);
        buildForm(); panel.add(form); form.setVisible(false);
        render();
        return panel;
    }

    private void buildForm()
    {
        form = vertical(CARD);
        form.add(text("Goal editor", GOLD, true));
        kind = combo(KINDS);
        kind.addActionListener(event -> showFormKind()); form.add(SidebarUi.field("Goal", kind));
        List<Skill> skills = new ArrayList<>();
        for (Skill value : Skill.values()) if (value != Skill.OVERALL) skills.add(value);
        skill = new JComboBox<>(skills.toArray(new Skill[0])); style(skill);
        skill.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value instanceof Skill ? ((Skill) value).getName() : value, index, selected, focus);
                return this;
            }
        });
        skill.addActionListener(event -> { if (!rendering) fillMethods(null); });
        skillFields = SidebarUi.column(); skillFields.add(SidebarUi.field("Skill", skill)); form.add(skillFields);
        quest = new JComboBox<>(catalog.quests().toArray(new GuideCatalog.Entry[0])); style(quest);
        quest.setMaximumRowCount(12); questFields = SidebarUi.field("Quest", quest); form.add(questFields);
        progressFields = SidebarUi.column(); form.add(progressFields);
        progressSearch = field(""); progressSearch.addActionListener(event -> fillProgressChoices(null)); progressFields.add(SidebarUi.field("Find target", progressSearch));
        progressChoice = new JComboBox<>(); style(progressChoice); progressChoice.setMaximumRowCount(10);
        progressChoice.addActionListener(event -> { if (!rendering && progressChoice.getSelectedItem() instanceof AccountProgressTool.Choice) { AccountProgressTool.Choice choice = (AccountProgressTool.Choice) progressChoice.getSelectedItem(); progressTarget.setText(Long.toString(choice.target)); progressLabel.setText(choice.label); } });
        progressFields.add(SidebarUi.field("Target", progressChoice));
        progressTarget = field("1"); progressFields.add(SidebarUi.field("Amount", progressTarget));
        progressLabel = field(""); progressFields.add(SidebarUi.field("Goal name", progressLabel));
        setItems = field(""); progressFields.add(SidebarUi.field("Set item IDs (comma separated)", setItems));
        targetMode = combo(new String[] {"Level", "XP"});
        targetMode.addActionListener(event -> { if (!rendering) target.setText(targetMode.getSelectedIndex() == 0 ? "99" : "13034431"); });
        target = field("99"); skillFields.add(SidebarUi.field("Target", SidebarUi.row(targetMode, target)));
        intensity = combo(new String[] {"AFK", "Normal", "Intensive"}); intensity.setSelectedItem("Normal");
        intensity.addActionListener(event -> { if (!rendering) fillMethods(null); }); skillFields.add(SidebarUi.field("Effort", intensity));
        method = new JComboBox<>(); style(method); method.setMaximumRowCount(12);
        method.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value instanceof ActivityXpCatalog.Method
                    ? ((ActivityXpCatalog.Method) value).name : "Choose a method", index, selected, focus);
                return this;
            }
        });
        method.addActionListener(event -> { Object value = method.getSelectedItem(); method.setToolTipText(value == null ? null : value.toString()); });
        skillFields.add(SidebarUi.field("Method", method));
        optionsToggle = button("Options ▸", () ->
        { options.setVisible(!options.isVisible()); optionsToggle.setText(options.isVisible() ? "Options ▾" : "Options ▸"); form.revalidate(); });
        skillFields.add(optionsToggle); options = SidebarUi.column(); skillFields.add(options);
        rate = field(""); options.add(SidebarUi.field("XP/hour", rate));
        rate.setToolTipText("Leave blank to use the selected method's reference rate.");
        budget = field(""); options.add(SidebarUi.field("GP budget", budget));
        weekly = field(""); options.add(SidebarUi.field("Hours per week", weekly));
        interval = field("5"); options.add(SidebarUi.field("Milestone spacing (levels)", interval));
        options.setVisible(false);
        error = new GuideText(""); error.setForeground(new Color(244, 144, 130)); form.add(error);
        save = button("Save goal", this::saveForm);
        cancel = button("Back", () -> { resetEditing(); render(); }); form.add(SidebarUi.row(cancel, save));
        fillMethods(null); showFormKind();
    }
    private void buildTransfer()
    {
        transfer = SidebarUi.section("Shared plans"); transfer.setVisible(false);
        planCode = new JTextArea(4, 12); planCode.setLineWrap(true); planCode.setWrapStyleWord(true); planCode.setFont(GuideText.FONT);
        JScrollPane scroll = new JScrollPane(planCode); scroll.setMinimumSize(new java.awt.Dimension(0, 95)); scroll.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 95));
        transfer.add(scroll); planPreview = new GuideText(""); transfer.add(planPreview);
        transfer.add(button("Export active plan", () -> { View current = validView(); if (current != null) { List<GoalPlan> active = new ArrayList<>(); for (GoalPlan goal : current.goals) if (!goal.archived) active.add(goal); planCode.setText(GoalPlan.share(gson, active)); planPreview.setText(active.size() + " goals · definitions only"); pendingImport = Collections.emptyList(); importPlan.setEnabled(false); } }));
        transfer.add(button("Preview import", () ->
        {
            try
            {
                pendingImport = GoalPlan.importPlan(gson, planCode.getText());
                StringBuilder preview = new StringBuilder(pendingImport.size() + " goals");
                for (int index = 0; index < Math.min(6, pendingImport.size()); index++) { GoalPlan goal = pendingImport.get(index); preview.append("\n").append(goal.progressGoal() ? goal.label : "quest".equals(goal.kind) ? goal.questGuideId.replace("quest:", "") : goal.skill().getName() + " → " + targetLabel(goal.targetXp)); }
                if (pendingImport.size() > 6) preview.append("\n+").append(pendingImport.size() - 6).append(" more");
                planPreview.setText(preview.toString()); importPlan.setEnabled(validView() != null);
            }
            catch (IllegalArgumentException invalid) { pendingImport = Collections.emptyList(); importPlan.setEnabled(false); planPreview.setText(invalid.getMessage()); }
        }));
        importPlan = button("Import previewed plan", () ->
        {
            View current = validView(); if (current == null || pendingImport.isEmpty()) return;
            List<GoalPlan> incoming = new ArrayList<>();
            for (GoalPlan goal : pendingImport) { GoalPlan copy = goal.copy(); if ("skill".equals(copy.kind)) copy.startXp = current.facts.xp(copy.skill()); incoming.add(copy); }
            mutate(current.session, () -> { boolean changed = store.importGoals(current.session, incoming); SwingUtilities.invokeLater(() -> { if (store.current(current.session)) planPreview.setText(changed ? "Plan imported" : "Already present or plan exceeds 100 goals"); }); });
        }); importPlan.setEnabled(false); transfer.add(importPlan);
    }
    private void newGoal()
    {
        resetEditing(); rendering = true;
        try
        {
            kind.setSelectedIndex(0); targetMode.setSelectedIndex(0); target.setText("99");
            progressSearch.setText(""); progressLabel.setText(""); progressTarget.setText("1"); setItems.setText("");
            rate.setText(""); budget.setText(""); weekly.setText(""); interval.setText("5");
            intensity.setSelectedItem("Normal"); fillMethods(null); showFormKind();
        }
        finally { rendering = false; }
        overview.setVisible(false); form.setVisible(true); panel.revalidate(); panel.repaint();
    }
    private void showFormKind()
    {
        if (skillFields == null || questFields == null) return;
        boolean skillGoal = kind.getSelectedIndex() == 0;
        skillFields.setVisible(skillGoal); questFields.setVisible(kind.getSelectedIndex() == 1);
        progressFields.setVisible(kind.getSelectedIndex() > 1);
        if (kind.getSelectedIndex() > 1) { setItems.getParent().setVisible(kind.getSelectedIndex() == 3); progressChoice.getParent().setVisible(kind.getSelectedIndex() != 3); AccountProgressTool.Choice previous = (AccountProgressTool.Choice) progressChoice.getSelectedItem(); fillProgressChoices(previous == null ? null : previous.key); }
        form.revalidate();
    }
    private void fillProgressChoices(String selected)
    {
        if (progressChoice == null || catalog.progressTool() == null) return;
        boolean before = rendering; rendering = true;
        try
        {
            progressChoice.removeAllItems();
            for (AccountProgressTool.Choice choice : catalog.progressTool().choices(KIND_KEYS[kind.getSelectedIndex()], progressSearch.getText()))
            { progressChoice.addItem(choice); if (choice.key.equals(selected)) progressChoice.setSelectedItem(choice); }
            if (!before && progressChoice.getSelectedItem() instanceof AccountProgressTool.Choice)
            { AccountProgressTool.Choice choice = (AccountProgressTool.Choice) progressChoice.getSelectedItem(); progressTarget.setText(Long.toString(choice.target)); progressLabel.setText(choice.label); }
        }
        finally { rendering = before; }
    }
    private void fillMethods(String selected)
    {
        if (method == null) return;
        method.removeAllItems(); method.addItem(null);
        for (ActivityXpCatalog.Method item : catalog.methods((Skill) skill.getSelectedItem(), (String) intensity.getSelectedItem()))
        { method.addItem(item); if (item.id.equals(selected)) method.setSelectedItem(item); }
    }

    private void saveForm()
    {
        View displayed = validView();
        if (displayed == null) { error.setText("Log in to add a goal."); return; }
        try
        {
            GoalPlan goal;
            if (kind.getSelectedIndex() == 0)
            {
                Skill chosen = (Skill) skill.getSelectedItem();
                int value = Integer.parseInt(target.getText().trim().replace(",", ""));
                if (targetMode.getSelectedIndex() == 0 && (value < 2 || value > 99)) throw new IllegalArgumentException("Choose a level from 2 to 99.");
                int targetXp = targetMode.getSelectedIndex() == 0 ? Experience.getXpForLevel(value) : value;
                if (targetXp < 1 || targetXp > GoalPlan.MAX_XP) throw new IllegalArgumentException("Target XP must be 1–200,000,000.");
                goal = GoalPlan.skill(chosen, targetXp, displayed.facts.xp(chosen));
                goal.intensity = (String) intensity.getSelectedItem();
                ActivityXpCatalog.Method selected = (ActivityXpCatalog.Method) method.getSelectedItem();
                goal.methodId = selected == null ? null : selected.id;
                goal.customXpPerHour = parseLong(rate); goal.gpBudget = parseLong(budget);
                goal.weeklyHours = weekly.getText().trim().isEmpty() ? 0 : Double.parseDouble(weekly.getText().trim().replace(",", "."));
                goal.milestoneInterval = Integer.parseInt(interval.getText().trim());
                if (goal.customXpPerHour < 0 || goal.customXpPerHour > GoalPlan.MAX_XP || goal.gpBudget < 0)
                    throw new IllegalArgumentException("Enter a non-negative budget and XP rate.");
                if (!Double.isFinite(goal.weeklyHours) || goal.weeklyHours < 0 || goal.weeklyHours > 168)
                    throw new IllegalArgumentException("Weekly hours must be 0–168.");
                if (goal.milestoneInterval < 1 || goal.milestoneInterval > 25)
                    throw new IllegalArgumentException("Milestone interval must be 1–25.");
            }
            else if (kind.getSelectedIndex() == 1)
            {
                GuideCatalog.Entry chosen = (GuideCatalog.Entry) quest.getSelectedItem();
                if (chosen == null) throw new IllegalArgumentException("Choose a quest.");
                goal = GoalPlan.quest(chosen.id);
            }
            else
            {
                String type = KIND_KEYS[kind.getSelectedIndex()];
                long amount = Long.parseLong(progressTarget.getText().trim().replace(",", ""));
                AccountProgressTool.Choice choice = (AccountProgressTool.Choice) progressChoice.getSelectedItem();
                List<Integer> ids = new ArrayList<>(); String reference;
                if ("set".equals(type))
                { for (String id : setItems.getText().split(",")) { int itemId = Integer.parseInt(id.trim()); if (itemId < 1 || !GuideItemResolver.nameIndex().values().stream().anyMatch(known -> known.contains(itemId))) throw new IllegalArgumentException("Choose known item IDs."); if (!ids.contains(itemId)) ids.add(itemId); } reference = ids.toString(); }
                else { if (choice == null) throw new IllegalArgumentException("Find and choose a target first."); reference = choice.key; if ("item".equals(type) || "unlock".equals(type)) ids.addAll(choice.items); }
                goal = GoalPlan.progress(type, reference, amount, progressLabel.getText().trim(), ids);
            }
            GoalPlan.Session expected = displayed.session;
            if (editing != null)
            {
                if (editingSession == null || !store.current(editingSession)) throw new IllegalArgumentException("Account changed; choose the goal again.");
                goal.id = editing.id; goal.archived = editing.archived;
                if (goal.skill() == editing.skill() && "skill".equals(goal.kind)) goal.startXp = editing.startXp;
                if (goal.semanticKey().equals(editing.semanticKey())) goal.achievedAt = editing.achievedAt;
            }
            GoalPlan update = goal;
            if (editing == null && displayed.goals.size() >= GoalPlan.MAX_GOALS)
                throw new IllegalArgumentException("Archive or remove goals before adding more (100 maximum).");
            mutate(expected, () ->
            {
                store.put(expected, update);
                SwingUtilities.invokeLater(() -> { if (store.current(expected)) resetEditing(); });
            });
            error.setText("");
        }
        catch (NumberFormatException invalid) { error.setText("Enter numbers in the goal fields."); }
        catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
    }
    private static long parseLong(JTextField field) { return field.getText().trim().isEmpty() ? 0 : Long.parseLong(field.getText().trim().replace(",", "")); }

    private View validView()
    { View current = view; return current.session != null && store.current(current.session) ? current : null; }
    private void resetEditing()
    {
        editing = null; editingSession = null;
        if (save != null) { save.setText("Save goal"); error.setText(""); form.setVisible(false); overview.setVisible(true); options.setVisible(false); optionsToggle.setText("Options ▸"); }
    }
    private void edit(GoalPlan goal, GoalPlan.Session session)
    {
        if (!store.current(session)) return;
        rendering = true;
        try
        {
            editing = goal.copy(); editingSession = session;
            int selectedKind = java.util.Arrays.asList(KIND_KEYS).indexOf(goal.kind); kind.setSelectedIndex(Math.max(0, selectedKind));
            if ("skill".equals(goal.kind))
            {
                skill.setSelectedItem(goal.skill()); targetMode.setSelectedIndex(1); target.setText(Integer.toString(goal.targetXp));
                intensity.setSelectedItem(goal.intensity); fillMethods(goal.methodId);
                rate.setText(Long.toString(goal.customXpPerHour)); budget.setText(Long.toString(goal.gpBudget));
                weekly.setText(Double.toString(goal.weeklyHours)); interval.setText(Integer.toString(goal.milestoneInterval));
            }
            else if ("quest".equals(goal.kind)) { for (int i = 0; i < quest.getItemCount(); i++) if (quest.getItemAt(i).id.equals(goal.questGuideId)) quest.setSelectedIndex(i); }
            else
            { progressSearch.setText(goal.kind.equals("item") || goal.kind.equals("collection") ? goal.label.replaceFirst(" · \\d+$", "") : goal.label); fillProgressChoices(goal.referenceKey); progressTarget.setText(Long.toString(goal.targetAmount)); progressLabel.setText(goal.label); setItems.setText(goal.itemIds.toString().replace("[", "").replace("]", "")); }
            save.setText("Save changes"); overview.setVisible(false); form.setVisible(true); options.setVisible(false); optionsToggle.setText("Options ▸"); showFormKind(); panel.revalidate();
        }
        finally { rendering = false; }
    }

    private void render()
    {
        if (panel == null) return;
        View current = validView();
        save.setEnabled(current != null && store.writable()); addGoal.setEnabled(current != null && store.writable());
        undo.setEnabled(current != null && store.canUndo()); redo.setEnabled(current != null && store.canRedo());
        if (current == null)
        {
            pendingImport = Collections.emptyList(); importPlan.setEnabled(false);
            rows.removeAll();
            state.setText("Log in to load your goals."); navigation.setVisible(false); archived.setVisible(false);
            resetEditing(); panel.revalidate(); panel.repaint(); return;
        }
        if (form.isVisible()) return;
        rows.removeAll();
        long reached = current.goals.stream().filter(goal -> goal.achievedAt > 0).count();
        state.setText(store.writable() ? current.goals.size() + " goals · " + reached + " reached" : "Saved plan needs recovery");
        archived.setVisible(archived.isSelected() || current.goals.stream().anyMatch(goal -> goal.archived));
        if (current.goals.isEmpty()) rows.add(text("No goals yet", MUTED, false));
        List<GoalPlan> displayed = new ArrayList<>();
        for (GoalPlan goal : current.goals) if (!goal.archived || archived.isSelected()) displayed.add(goal);
        int pages = Math.max(1, (displayed.size() + 7) / 8); goalPage = Math.max(0, Math.min(goalPage, pages - 1));
        navigation.setVisible(pages > 1); previousPage.setEnabled(goalPage > 0); nextPage.setEnabled(goalPage + 1 < pages); pageLabel.setText((goalPage + 1) + " / " + pages);
        for (int index = goalPage * 8; index < Math.min(displayed.size(), goalPage * 8 + 8); index++)
        {
            rows.add(card(displayed.get(index), current, index + 1));
        }
        panel.revalidate(); panel.repaint();
    }

    private JPanel card(GoalPlan goal, View current, int number)
    {
        JPanel card = vertical(CARD);
        GuideCatalog.Entry questGuide = catalog.guide(goal.questGuideId);
        ActivityXpCatalog.Method chosen = catalog.method(goal.methodId);
        boolean skillGoal = "skill".equals(goal.kind);
        String title = skillGoal ? goal.skill().getName() + " → " + targetLabel(goal.targetXp)
            : goal.progressGoal() ? goal.label : questGuide == null ? goal.questGuideId : questGuide.title;
        card.add(text((goal.achievedAt > 0 ? "✓ " : number + ". ") + title, goal.achievedAt > 0 ? GREEN : GOLD, true));
        if (goal.archived) card.add(text("Archived", MUTED, false));
        if (skillGoal)
        {
            int xp = current.facts.xp(goal.skill());
            card.add(text(format(xp) + " XP · " + format(goal.remaining(xp)) + " to go", WHITE, false));
            JProgressBar progress = new JProgressBar(0, 100); progress.setValue(goal.percent(xp));
            progress.setStringPainted(true); progress.setString(goal.percent(xp) + "%"); style(progress);
            progress.setForeground(GOLD); card.add(progress);
            card.add(text(goal.intensity + (chosen == null ? " · Choose a method" : " · " + chosen.name), WHITE, false));
            String estimate = estimate(goal, chosen, current.facts.level(goal.skill()), xp);
            if (!estimate.isEmpty()) card.add(text(estimate, MUTED, false));
            if (chosen != null && goal.customXpPerHour == 0 && chosen.ratesAt(current.facts.level(goal.skill())) != null
                && chosen.note != null && !chosen.note.isEmpty()) card.add(text(chosen.note, MUTED, false));
            if (goal.gpBudget > 0) card.add(text("Budget " + format(goal.gpBudget) + " GP", MUTED, false));
            List<Integer> milestones = goal.milestones();
            int done = 0; for (int checkpoint : milestones) if (xp >= checkpoint) done++;
            card.add(text("Milestones " + done + "/" + milestones.size(), GOLD, false));
            JPanel checkpoints = vertical(CARD);
            checkpoints.setVisible(false);
            card.add(button("Show milestones", () ->
            {
                if (checkpoints.getComponentCount() == 0) for (int checkpoint : milestones)
                    checkpoints.add(text((xp >= checkpoint ? "✓ " : "○ ") + targetLabel(checkpoint), xp >= checkpoint ? GREEN : MUTED, false));
                checkpoints.setVisible(!checkpoints.isVisible()); card.revalidate();
            }));
            card.add(checkpoints);
        }
        else if (goal.progressGoal())
        {
            Long value = catalog.progress(goal); card.add(text((value == null ? "Unknown" : format(value)) + " / " + format(goal.targetAmount), WHITE, false));
            if (value != null) { JProgressBar bar = new JProgressBar(0, 100); bar.setValue((int) Math.min(100, value * 100.0 / goal.targetAmount)); bar.setStringPainted(true); bar.setForeground(GOLD); card.add(bar); }
        }
        List<String> benefits = catalog.benefits(goal, current.facts);
        if (!benefits.isEmpty()) { JPanel unlocked = vertical(CARD); for (String benefit : benefits) unlocked.add(text(benefit, WHITE, false)); card.add(SidebarUi.disclosure("Unlocks and rewards", unlocked)); }
        List<GoalCatalog.Requirement> requirements = catalog.requirements(goal, current.facts);
        if (!requirements.isEmpty())
        {
            boolean locked = requirements.stream().anyMatch(item -> item.state < 0), unknown = requirements.stream().anyMatch(item -> item.state == 0);
            card.add(text(locked ? "Unlocks needed" : unknown ? "Unlocks to check" : "Unlocks met", locked ? GOLD : unknown ? MUTED : GREEN, true));
            for (int r = 0; r < Math.min(5, requirements.size()); r++) { GoalCatalog.Requirement requirement = requirements.get(r); card.add(text(requirement.display(), requirement.state > 0 ? GREEN : MUTED, false)); }
            if (requirements.size() > 5)
            {
                JPanel more = vertical(CARD); more.setVisible(false);
                card.add(button("All " + requirements.size() + " requirements ▸", () -> { if (more.getComponentCount() == 0) for (int r = 5; r < requirements.size(); r++) { GoalCatalog.Requirement requirement = requirements.get(r); more.add(text(requirement.display(), requirement.state > 0 ? GREEN : MUTED, false)); } more.setVisible(!more.isVisible()); card.revalidate(); })); card.add(more);
            }
            List<GoalPlan> unlocks = catalog.unlockGoals(requirements, current.facts, current.goals);
            if (!unlocks.isEmpty()) card.add(button("Queue " + unlocks.size() + " unlock goals", () -> mutate(current.session, () ->
            {
                GoalCatalog.Facts facts = observe();
                List<GoalCatalog.Requirement> updated = catalog.requirements(goal, facts);
                List<GoalPlan> fresh = catalog.unlockGoals(updated, facts, store.goals());
                for (GoalPlan prerequisite : fresh) store.put(current.session, prerequisite);
                // Put the prerequisite chain before this target while preserving its dependency order.
                List<GoalPlan> ordered = store.goals();
                int targetIndex = index(ordered, goal.id);
                if (targetIndex < 0) return;
                for (GoalPlan prerequisite : fresh)
                {
                    int existingIndex = index(store.goals(), prerequisite.id);
                    while (existingIndex > targetIndex && store.move(current.session, prerequisite.id, -1)) existingIndex--;
                    targetIndex++;
                }
            })));
        }
        JPanel links = new JPanel(new java.awt.GridLayout(0, 2, 4, 4)); links.setOpaque(false);
        String guideId = skillGoal ? chosen == null ? null : chosen.guideId : catalog.goalGuide(goal);
        if (goal.progressGoal()) links.add(button("Open target", () -> { if (store.current(current.session)) openProgress.accept(goal.copy()); }));
        java.util.Set<Integer> targets = catalog.goalSupplyTargets(goal);
        if (!targets.isEmpty()) { links.add(button("Bank supplies", () -> { if (store.current(current.session)) bankFocus.accept(Collections.unmodifiableSet(targets)); }));
            links.add(button("Item sources", () -> { if (store.current(current.session)) openItem.accept(targets.iterator().next()); })); }
        if (guideId != null && catalog.guide(guideId) != null)
            links.add(button(guideId.startsWith("quest:") ? "Open Quest Helper" : "Open guide", () -> { if (store.current(current.session)) openGuide.accept(guideId); }));
        if (chosen != null) links.add(button("XP method", () -> { if (store.current(current.session)) openMethod.accept(chosen.id); }));
        links.add(button("Edit", () -> edit(goal, current.session)));
        links.add(button(goal.archived ? "Restore" : "Archive", () -> mutate(current.session, () ->
        { GoalPlan update = find(store.goals(), goal.id); if (update != null) { update.archived = !update.archived; store.put(current.session, update); } })));
        links.add(button("Move up", () -> mutate(current.session, () -> store.move(current.session, goal.id, -1))));
        links.add(button("Move down", () -> mutate(current.session, () -> store.move(current.session, goal.id, 1))));
        links.add(button("Remove", () -> mutate(current.session, () -> store.remove(current.session, goal.id))));
        card.add(links);
        return card;
    }

    static String estimate(GoalPlan goal, ActivityXpCatalog.Method method, int level, int currentXp)
    {
        if (goal.remaining(currentXp) == 0) return "Target reached";
        if(method!=null&&level<method.level)return "Unlock level "+method.level+" before this method";
        if(method!=null&&"oneoff".equals(method.kind))return "One-off rewards; choose a repeatable method for time estimates";
        double minimum,maximum;
        if(method==null)
        {
            if(goal.customXpPerHour<=0)return "Add your XP/hour for a time estimate";
            minimum=maximum=ActivityXpCalculator.hours(currentXp,goal.targetXp,goal.customXpPerHour);
        }
        else
        {
            ActivityXpCalculator.Projection projection=ActivityXpCalculator.project(method,currentXp,goal.targetXp,goal.customXpPerHour>0?Double.valueOf(goal.customXpPerHour):null);
            if(!projection.complete())return "Add your pace for "+format(projection.unpricedXp)+" XP without a reference band";
            minimum=projection.fastestHours;maximum=projection.slowestHours;
        }
        String duration = minimum == maximum ? hours(minimum) : hours(minimum) + "–" + hours(maximum);
        String result = (goal.customXpPerHour > 0 ? "Your rate: " : "Level-band reference: ") + duration + " hours";
        if (goal.weeklyHours > 0) result += " · " + hours(minimum / goal.weeklyHours) + (minimum == maximum ? "" : "–" + hours(maximum / goal.weeklyHours)) + " weeks";
        return result;
    }
    private static String hours(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private static String targetLabel(int xp)
    {
        int level = Experience.getLevelForXp(xp);
        return level <= 99 && Experience.getXpForLevel(level) == xp ? "Level " + level : format(xp) + " XP";
    }
    private static String format(long value) { return NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(value); }
    private static GoalPlan find(List<GoalPlan> goals, String id) { for (GoalPlan goal : goals) if (goal.id.equals(id)) return goal; return null; }
    private static int index(List<GoalPlan> goals, String id) { for (int i = 0; i < goals.size(); i++) if (goals.get(i).id.equals(id)) return i; return -1; }
    private static JPanel vertical(Color color)
    {
        JPanel result = SidebarUi.column();
        result.setBackground(color); result.setBorder(BorderFactory.createEmptyBorder(color.equals(CARD) ? 6 : 0, color.equals(CARD) ? 6 : 0, 6, color.equals(CARD) ? 6 : 0));
        result.setAlignmentX(Component.LEFT_ALIGNMENT); return result;
    }
    private static GuideText text(String value, Color color, boolean bold)
    { GuideText result = new GuideText(value); result.setForeground(color); if (bold) result.setFont(GuideText.FONT.deriveFont(Font.BOLD)); return result; }
    private static JButton button(String value, Runnable action)
    {
        JButton result = SidebarUi.button(value, action); style(result); result.setForeground(GOLD);
        result.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30)); return result;
    }
    private static JTextField field(String value) { JTextField result = new JTextField(value); style(result); return result; }
    private static JComboBox<String> combo(String[] values) { JComboBox<String> result = new JComboBox<>(values); style(result); return result; }
    private static void style(JComponent component)
    {
        component.setFont(GuideText.FONT); component.setBackground(new Color(38, 38, 35)); component.setForeground(WHITE);
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMinimumSize(new java.awt.Dimension(0, 26));
        component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Math.max(28, component.getPreferredSize().height)));
    }

    private static final class View
    {
        final GoalPlan.Session session; final GoalCatalog.Facts facts; final List<GoalPlan> goals; final Map<String, Long> progression;
        View(GoalPlan.Session session, GoalCatalog.Facts facts, List<GoalPlan> goals, Map<String, Long> progression)
        { this.session = session; this.facts = facts; this.goals = goals; this.progression = progression; }
        static View empty() { return new View(null, null, Collections.emptyList(), Collections.emptyMap()); }
        boolean same(View other)
        {
            if (session == null || other.session == null || session.generation != other.session.generation) return false;
            if (!progression.equals(other.progression)) return false;
            for (GoalPlan goal : goals) if ("skill".equals(goal.kind) && facts.xp(goal.skill()) != other.facts.xp(goal.skill())) return false;
            if (!facts.quests.equals(other.facts.quests) || !facts.levels.equals(other.facts.levels)
                || facts.combat != other.facts.combat) return false;
            if (goals.size() != other.goals.size()) return false;
            for (int i = 0; i < goals.size(); i++)
            {
                GoalPlan a = goals.get(i), b = other.goals.get(i);
                if (!a.id.equals(b.id) || !java.util.Objects.equals(a.kind, b.kind) || !java.util.Objects.equals(a.skill, b.skill)
                    || !java.util.Objects.equals(a.intensity, b.intensity) || a.targetXp != b.targetXp || a.archived != b.archived || a.achievedAt != b.achievedAt
                    || a.customXpPerHour != b.customXpPerHour || a.gpBudget != b.gpBudget || a.weeklyHours != b.weeklyHours
                    || a.milestoneInterval != b.milestoneInterval || !java.util.Objects.equals(a.methodId, b.methodId)
                    || !java.util.Objects.equals(a.questGuideId, b.questGuideId) || !a.semanticKey().equals(b.semanticKey()) || !java.util.Objects.equals(a.label, b.label)) return false;
            }
            return true;
        }
    }
}
