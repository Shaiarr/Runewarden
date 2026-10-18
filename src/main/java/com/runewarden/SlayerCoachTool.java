/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.LinkBrowser;

/** One local Slayer assignment view, independent of whether the native Slayer plugin is enabled. */
@Singleton
final class SlayerCoachTool
{
    private static final Color CARD = new Color(30, 31, 34), GOLD = new Color(224, 190, 113);
    private final Client client;
    private final ConfigManager config;
    private final ItemManager items;
    private final SlayerTaskCatalog tasks;
    private final MobKnowledgeCatalog monsters;
    private final GuideCatalog guides;
    private final SlayerPlanningTool planner;
    private volatile boolean started;
    private volatile State state = State.empty("Log in to view your assignment");
    private String profile;
    private String taskKey;
    private Identity identity;
    private List<MobKnowledgeCatalog.Entry> taskMonsters = Collections.emptyList();
    private Consumer<String> openGuide = ignored -> {};
    private IntConsumer openItem = ignored -> {};
    private Runnable openLoadouts = () -> { };
    private Consumer<net.runelite.api.coords.WorldPoint> route = ignored -> { };
    private JPanel panel, gearPanel, referencePanel, guidePanel;
    private JPanel extraReference;
    private GuideText title, location, counters;
    private JProgressBar progress;
    private JComboBox<MonsterChoice> creatures;
    private JButton assignmentWiki, locationWiki;
    private JButton detailToggle;
    private boolean changing;
    private boolean showDetails;
    private JPanel shell;
    private long observedCredits, completedTasks, pointsGained;
    private State observed;
    private volatile ActivityObservation activity = ActivityObservation.empty();
    static final class ActivityObservation
    {
        final String profile, id, title, location, guideId;
        final int remaining, initial, points, streak;
        final Set<Integer> npcIds, itemIds;
        final long observedCredits, completedTasks, pointsGained;
        ActivityObservation(String profile, String id, String title, String location, String guideId, int remaining, int initial, int points, int streak,
            Set<Integer> npcs, Set<Integer> items, long credits, long completed, long gained)
        { this.profile = profile; this.id = id; this.title = title; this.location = location; this.guideId = guideId; this.remaining = remaining; this.initial = initial; this.points = points; this.streak = streak; npcIds = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(npcs)); itemIds = Collections.unmodifiableSet(new java.util.LinkedHashSet<>(items)); observedCredits = credits; completedTasks = completed; pointsGained = gained; }
        static ActivityObservation empty() { return new ActivityObservation(null, "", "", null, "", 0, 0, 0, 0, Collections.emptySet(), Collections.emptySet(), 0, 0, 0); }
    }
    ActivityObservation activityObservation()
    { ActivityObservation value = activity; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value : ActivityObservation.empty(); }
    void setBankFocusAction(Consumer<Set<Integer>> action) { if (planner != null) planner.setBankFocusAction(action); }
    void setStartActivityAction(Consumer<SlayerPlanningTool.Prepared> action) { if (planner != null) planner.setStartActivityAction(action); }

    @Inject SlayerCoachTool(Client client, ConfigManager config, ItemManager items,
        SlayerTaskCatalog tasks, MobKnowledgeCatalog monsters, GuideCatalog guides, SlayerPlanningTool planner)
    {
        this.client = client; this.config = config; this.items = items;
        this.tasks = tasks; this.monsters = monsters; this.guides = guides; this.planner = planner; if (planner != null) planner.setItemManager(items);
    }
    SlayerCoachTool(Client client, ConfigManager config, ItemManager items, SlayerTaskCatalog tasks, MobKnowledgeCatalog monsters, GuideCatalog guides)
    { this(client, config, items, tasks, monsters, guides, null); }

    void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? ignored -> {} : action; if (planner != null) planner.setOpenGuideAction(openGuide); }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> {} : action; if (planner != null) planner.setOpenItemAction(openItem); }
    void setOpenLoadoutsAction(Runnable action) { openLoadouts = action == null ? () -> { } : action; if (planner != null) planner.setOpenLoadoutsAction(openLoadouts); }
    void setRouteAction(Consumer<net.runelite.api.coords.WorldPoint> action) { route = action == null ? ignored -> { } : action; if (planner != null) planner.setRouteAction(route); }
    void start() { started = true; clear(); if (planner != null) planner.start(); }
    void stop() { started = false; clear(); if (planner != null) planner.stop(); }
    State reading() { return state; }
    void clear()
    {
        profile = null; taskKey = null; identity = null; taskMonsters = Collections.emptyList(); observed = null; observedCredits = completedTasks = pointsGained = 0; activity = ActivityObservation.empty();
        if (planner != null) planner.clear();
        publish(State.empty("Log in to view your assignment"));
    }

    /** Client thread. Name/location DB reads occur only when assignment identity changes. */
    void onGameTick()
    {
        if (!started) return;
        String nextProfile = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || nextProfile == null || nextProfile.isEmpty())
        { if (profile != null) clear(); return; }
        if (!nextProfile.equals(profile)) { clear(); profile = nextProfile; }
        if (planner != null) planner.onGameTick();
        int points = Math.max(0, client.getVarbitValue(VarbitID.SLAYER_POINTS));
        int master = client.getVarbitValue(VarbitID.SLAYER_MASTER);
        int streak = master == 7 ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)
            : master == 10 ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)
            : client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
        int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        if (remaining <= 0)
        {
            taskKey = null; identity = null; taskMonsters = Collections.emptyList();
            publish(new State("No active assignment", null, 0, 0, points, Math.max(0, streak), master, Collections.emptyList(), taskMonsters));
            return;
        }
        int task = client.getVarpValue(VarPlayerID.SLAYER_TARGET), area = client.getVarpValue(VarPlayerID.SLAYER_AREA);
        int boss = task == 98 ? client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID) : 0;
        String key = task + ":" + area + ":" + boss;
        if (!key.equals(taskKey) || identity == null)
        {
            identity = readIdentity(task, boss, area);
            if (identity != null)
            {
                taskKey = key;
                List<String> hints = tasks.task(identity.name).hints();
                List<MobKnowledgeCatalog.Entry> references = new ArrayList<>(monsters.matching(hints));
                String ordinary = hints.isEmpty() ? "" : SlayerTaskCatalog.normalize(hints.get(0));
                references.sort((a, b) -> Boolean.compare(!SlayerTaskCatalog.normalize(a.name).equals(ordinary), !SlayerTaskCatalog.normalize(b.name).equals(ordinary)));
                taskMonsters = Collections.unmodifiableList(references);
            }
        }
        if (identity == null)
        {
            publish(new State("Assignment data loading", null, remaining, 0, points, Math.max(0, streak), master, Collections.emptyList(), Collections.emptyList()));
            return;
        }
        int initial = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == 2)
            initial += client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1
                ? -client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE) : client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
        publish(new State(identity.name, identity.location, remaining, Math.max(0, initial), points, Math.max(0, streak), master,
            gear(tasks.gear(identity.name)), taskMonsters));
    }

    Identity readIdentity(int task, int boss, int area)
    {
        try
        {
            List<Integer> rows = task == 98
                ? client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID, DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, boss)
                : client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, task);
            if (rows == null || rows.isEmpty()) return null;
            int row = rows.get(0);
            if (task == 98) row = ((Number) first(client.getDBTableField(row, DBTableID.SlayerTaskSublist.COL_TASK, 0))).intValue();
            Object name = first(client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0));
            if (!(name instanceof String) || ((String) name).trim().isEmpty()) return null;
            String location = null;
            if (area > 0)
            {
                List<Integer> areas = client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, area);
                if (areas == null || areas.isEmpty()) return null;
                Object found = first(client.getDBTableField(areas.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0));
                if (found instanceof String) location = (String) found;
            }
            return new Identity((String) name, location);
        }
        catch (RuntimeException loading) { return null; }
    }

    private static Object first(Object[] values) { return values == null || values.length == 0 ? null : values[0]; }

    private List<GearStatus> gear(List<SlayerTaskCatalog.Gear> rules)
    {
        Set<Integer> equipped = ids(client.getItemContainer(InventoryID.EQUIPMENT));
        Set<Integer> carried = ids(client.getItemContainer(InventoryID.INVENTORY));
        boolean helmet = false;
        for (int id : equipped)
        {
            net.runelite.api.ItemComposition composition = items.getItemComposition(id);
            String name = composition == null ? null : composition.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains("slayer helmet")) { helmet = true; break; }
        }
        List<GearStatus> result = new ArrayList<>();
        for (SlayerTaskCatalog.Gear rule : rules)
        {
            boolean worn = (rule.slayerHelmet && helmet) || rule.itemIds.stream().anyMatch(equipped::contains);
            boolean inInventory = rule.itemIds.stream().anyMatch(carried::contains);
            boolean ready = worn || ("carried".equals(rule.mode) && inInventory);
            String status = ready ? "Ready" : inInventory ? "In inventory · equip" : "equipped".equals(rule.mode) ? "Not equipped" : "Not carried";
            result.add(new GearStatus(rule, ready, status));
        }
        return result;
    }

    private static Set<Integer> ids(ItemContainer container)
    {
        Set<Integer> ids = new HashSet<>();
        if (container != null && container.getItems() != null)
            for (Item item : container.getItems()) if (item != null && item.getId() > 0 && item.getQuantity() > 0) ids.add(item.getId());
        return ids;
    }

    JPanel createPanel()
    {
        if (panel != null) return shell == null ? panel : shell;
        panel = column(); panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));
        JPanel assignment = section("ASSIGNMENT"); panel.add(assignment);
        title = text("Slayer assignment", true); title.setFont(GuideText.FONT.deriveFont(Font.BOLD, 15f)); assignment.add(title);
        location = text("", false); assignment.add(location);
        progress = new JProgressBar(); progress.setStringPainted(true); progress.setForeground(GOLD); progress.setBackground(CARD);
        progress.setFont(GuideText.FONT); progress.setAlignmentX(0);
        progress.setMinimumSize(new Dimension(0, 23)); progress.setPreferredSize(new Dimension(190, 23));
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 23)); assignment.add(progress);
        counters = text("", false); assignment.add(counters);
        assignmentWiki = button("Task Wiki", () -> openWiki(state.name)); assignment.add(assignmentWiki);
        locationWiki = button("Location Wiki", () -> { if (state.location != null) openWiki(state.location); }); assignment.add(locationWiki);
        gearPanel = section(null); panel.add(gearPanel);
        JPanel creatureSection = section("CREATURE REFERENCE"); panel.add(creatureSection);
        creatures = new JComboBox<>(); creatures.setBackground(CARD); creatures.setForeground(new Color(242, 239, 230));
        creatures.setFont(GuideText.FONT); creatures.setAlignmentX(0);
        creatures.setMinimumSize(new Dimension(0, 29)); creatures.setPreferredSize(new Dimension(190, 29));
        creatures.setMaximumSize(new Dimension(Integer.MAX_VALUE, 29));
        creatures.addActionListener(event -> { if (!changing) showReference((MonsterChoice) creatures.getSelectedItem()); });
        creatureSection.add(creatures);
        referencePanel = column(); creatureSection.add(referencePanel);
        guidePanel = section(null); panel.add(guidePanel);
        refresh(state);
        if (planner == null) return panel;
        shell = SidebarUi.column(); JComboBox<String> mode = new JComboBox<>(new String[]{"Assignment", "Task planning"});
        mode.setFont(GuideText.FONT); mode.setForeground(SidebarUi.TEXT); mode.setBackground(SidebarUi.CARD); mode.setMinimumSize(new Dimension(0, 30)); mode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); shell.add(mode);
        JPanel pages = new JPanel(new java.awt.CardLayout())
        {
            @Override public Dimension getPreferredSize() { for (java.awt.Component child : getComponents()) if (child.isVisible()) return new Dimension(190, child.getPreferredSize().height); return new Dimension(190, 0); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        pages.setOpaque(false); pages.add(panel, "assignment"); pages.add(planner.createPanel(), "planning"); shell.add(pages);
        mode.addActionListener(event -> { ((java.awt.CardLayout) pages.getLayout()).show(pages, mode.getSelectedIndex() == 0 ? "assignment" : "planning"); shell.revalidate(); shell.repaint(); });
        return shell;
    }

    private void publish(State next)
    {
        if (planner != null) planner.setAssignment(next);
        observe(next);
        if (state.signature().equals(next.signature())) return;
        state = next;
        SwingUtilities.invokeLater(() -> { if (state == next && panel != null) refresh(next); });
    }

    private void observe(State next)
    {
        if (profile == null) return;
        if (observed != null)
        {
            boolean sameMaster = observed.master == next.master;
            boolean sameTask = sameMaster && observed.name.equals(next.name) && java.util.Objects.equals(observed.location, next.location);
            if (sameTask && observed.remaining > next.remaining && next.remaining > 0) observedCredits += observed.remaining - next.remaining;
            int completed = sameMaster ? Math.max(0, next.streak - observed.streak) : 0;
            if (completed > 0) { completedTasks += completed; if (next.remaining == 0 && observed.remaining > 0) observedCredits += observed.remaining; }
            if (sameMaster && next.points > observed.points) pointsGained += next.points - observed.points;
        }
        observed = next;
        Set<Integer> npcs = new java.util.LinkedHashSet<>(), supplies = new java.util.LinkedHashSet<>(); String guide = "";
        if (planner != null)
        {
            SlayerPlanningTool.Rule rule = planner.rule(next.master, next.name); if (rule.npcId > 0) npcs.add(rule.npcId); supplies.addAll(rule.supplies); guide = rule.guide;
        }
        activity = new ActivityObservation(profile, "slayer:" + (taskKey == null ? "none" : taskKey) + ":" + next.streak,
            next.name, next.location, guide, next.remaining, next.initial, next.points, next.streak, npcs, supplies, observedCredits, completedTasks, pointsGained);
    }
    private void refresh(State next)
    {
        title.setText(next.name);
        location.setText(next.location == null ? "" : next.location);
        assignmentWiki.setEnabled(next.remaining > 0 && !"Assignment data loading".equals(next.name));
        locationWiki.setEnabled(next.location != null && !next.location.isEmpty());
        boolean totalKnown = next.initial >= next.remaining && next.initial > 0;
        progress.setMaximum(totalKnown ? next.initial : Math.max(1, next.remaining));
        progress.setValue(totalKnown ? next.initial - next.remaining : 0);
        progress.setString(next.remaining + " left" + (totalKnown ? " · " + (next.initial - next.remaining) + "/" + next.initial : ""));
        counters.setText("Points " + next.points + "   ·   Streak " + next.streak + (next.master == 7 ? "\nWilderness" : next.master == 10 ? "\nMortimer" : ""));
        gearPanel.removeAll();
        gearPanel.setVisible(!next.gear.isEmpty());
        if (!next.gear.isEmpty()) gearPanel.add(text("TASK EQUIPMENT", true));
        for (GearStatus gear : next.gear)
        {
            GuideText status = text(gear.rule.title + "\n" + gear.status, false);
            status.setForeground(gear.ready ? new Color(140, 211, 155) : GOLD); gearPanel.add(status);
            if (gear.rule.notes != null && !gear.rule.notes.isEmpty()) gearPanel.add(text(gear.rule.notes, false));
            gearPanel.add(button("View item", () -> openItem.accept(gear.rule.itemIds.get(0))));
        }
        MonsterChoice previous = (MonsterChoice) creatures.getSelectedItem();
        changing = true;
        DefaultComboBoxModel<MonsterChoice> model = new DefaultComboBoxModel<>();
        MonsterChoice keep = null;
        for (MobKnowledgeCatalog.Entry entry : next.monsters)
        {
            MonsterChoice choice = new MonsterChoice(entry); model.addElement(choice);
            if (previous != null && previous.monster.id == entry.id) keep = choice;
        }
        creatures.setModel(model);
        if (keep != null) creatures.setSelectedItem(keep);
        changing = false;
        showReference((MonsterChoice) creatures.getSelectedItem());
        panel.revalidate(); panel.repaint();
    }

    private void showReference(MonsterChoice choice)
    {
        referencePanel.removeAll(); guidePanel.removeAll();
        guidePanel.setVisible(false);
        if (choice == null)
        {
            referencePanel.add(text("No creature reference matched", false));
            return;
        }
        MobKnowledgeCatalog.Entry monster = choice.monster;
        MobKnowledgeCatalog.Card card = monsters.describe(monster.id, monster.name);
        for (int index = 0; index < Math.min(3, card.lines.size()); index++)
        {
            String line = card.lines.get(index);
            int colon = line.indexOf(':');
            String label = index == 1 ? "Weakness" : index == 2 ? "Usual protection" : "Attacks";
            referencePanel.add(fact(label, colon < 0 ? line : line.substring(colon + 1).trim()));
        }
        extraReference = column();
        for (int index = 3; index < card.lines.size(); index++) extraReference.add(text(card.lines.get(index), false));
        extraReference.setVisible(showDetails);
        detailToggle = button(showDetails ? "Hide combat details" : "More combat details", () -> {
            showDetails = !showDetails; extraReference.setVisible(showDetails);
            detailToggle.setText(showDetails ? "Hide combat details" : "More combat details");
            extraReference.revalidate(); referencePanel.revalidate(); panel.revalidate(); panel.repaint();
        });
        referencePanel.add(detailToggle); referencePanel.add(extraReference);
        referencePanel.add(button("Creature Wiki", () -> LinkBrowser.browse(MobKnowledgeCatalog.wikiLookup(monster.id))));
        List<GuideCatalog.Entry> related = guides.search(monster.name, "boss");
        int count = 0;
        for (GuideCatalog.Entry guide : related)
        {
            if (++count > 5) break;
            if (count == 1) { guidePanel.setVisible(true); guidePanel.add(text("GUIDES", true)); }
            guidePanel.add(text(guide.title, true));
            guidePanel.add(button("Open guide", () -> openGuide.accept(guide.id)));
        }
        if (referencePanel.getParent() != null) { referencePanel.getParent().revalidate(); referencePanel.getParent().repaint(); }
    }

    private static void openWiki(String name)
    {
        if (name != null && !name.isEmpty()) LinkBrowser.browse("https://oldschool.runescape.wiki/w/Special:Search?search=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
    }
    private static JPanel column() { return SidebarUi.column(); }
    private static JPanel section(String label)
    {
        return SidebarUi.section(label);
    }
    private static JPanel fact(String label, String value)
    {
        JPanel row = column(); row.setBorder(BorderFactory.createEmptyBorder(3, 0, 5, 0));
        GuideText heading = text(label, true); heading.setFont(GuideText.FONT.deriveFont(Font.BOLD, 12f));
        row.add(heading); row.add(text(value, false)); return row;
    }
    private static GuideText text(String text, boolean heading)
    {
        GuideText t = SidebarUi.label(text); t.setAlignmentX(0); t.setFont(GuideText.FONT);
        t.setForeground(heading ? GOLD : new Color(240, 238, 232));
        if (heading) t.setFont(GuideText.FONT.deriveFont(Font.BOLD)); return t;
    }
    private static JButton button(String label, Runnable action)
    {
        JButton button = new JButton(label); button.setFont(GuideText.FONT); button.setForeground(GOLD); button.setBackground(CARD);
        button.setFocusPainted(false); button.setAlignmentX(0); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.setMinimumSize(new Dimension(0, 30)); button.setPreferredSize(new Dimension(190, 30));
        button.addActionListener(event -> action.run()); return button;
    }
    static final class Identity { final String name, location; Identity(String name, String location) { this.name = name; this.location = location; } }
    static final class GearStatus
    {
        final SlayerTaskCatalog.Gear rule; final boolean ready; final String status;
        GearStatus(SlayerTaskCatalog.Gear rule, boolean ready, String status) { this.rule = rule; this.ready = ready; this.status = status; }
    }
    static final class State
    {
        final String name, location;
        final int remaining, initial, points, streak, master;
        final List<GearStatus> gear;
        final List<MobKnowledgeCatalog.Entry> monsters;
        State(String name, String location, int remaining, int initial, int points, int streak, int master,
            List<GearStatus> gear, List<MobKnowledgeCatalog.Entry> monsters)
        { this.name = name; this.location = location; this.remaining = remaining; this.initial = initial; this.points = points; this.streak = streak; this.master = master; this.gear = gear; this.monsters = monsters; }
        static State empty(String name) { return new State(name, null, 0, 0, 0, 0, 0, Collections.emptyList(), Collections.emptyList()); }
        String signature()
        {
            StringBuilder key = new StringBuilder(name).append('|').append(location).append('|').append(remaining).append('|').append(initial).append('|').append(points).append('|').append(streak).append('|').append(master);
            for (GearStatus entry : gear) key.append('|').append(entry.status);
            return key.toString();
        }
    }
    private static final class MonsterChoice
    {
        final MobKnowledgeCatalog.Entry monster;
        MonsterChoice(MobKnowledgeCatalog.Entry monster) { this.monster = monster; }
        @Override public String toString() { return monster.name + (monster.version == null || monster.version.isEmpty() ? "" : " · " + monster.version); }
    }
}
