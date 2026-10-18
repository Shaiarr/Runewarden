/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Farming routes share the ordinary guide selection, supplies and native harvest observations. */
@Singleton
final class FarmRunsTool
{
    private static final Color BACKGROUND = new Color(17, 18, 21), CARD = new Color(29, 30, 33);
    private static final Color GOLD = new Color(224, 190, 113), MUTED = new Color(185, 183, 175);
    private static final String CONFIG_GROUP = "runewarden", CONFIG_KEY = "farmRun";
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager configManager;
    private final GuideCatalog catalog;
    private final RunewardenGuides guides;
    private final HarvestClockTool harvests;
    private final ItemManager itemManager;
    private final FarmPatchPlanner patchPlanner;
    private volatile boolean started;
    private volatile String profile;
    private volatile long profileEpoch;
    private volatile Run remembered;
    private volatile Runnable openGuideAction = () -> {};
    private JPanel panel, details;
    private JComboBox<Run> runs;
    private JLabel icon;
    private GuideText title, description, requirements, timerStatus, activeStatus, step;
    private JCheckBox track;
    private JButton open;
    private Timer timer;
    private boolean rebuilding;
    private Run displayed;

    FarmRunsTool(Client client, ClientThread clientThread, ConfigManager configManager,
        GuideCatalog catalog, RunewardenGuides guides, HarvestClockTool harvests, ItemManager itemManager)
    { this(client, clientThread, configManager, catalog, guides, harvests, itemManager, null); }

    @Inject FarmRunsTool(Client client, ClientThread clientThread, ConfigManager configManager,
        GuideCatalog catalog, RunewardenGuides guides, HarvestClockTool harvests, ItemManager itemManager, FarmPatchPlanner patchPlanner)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.configManager = configManager;
        this.catalog = catalog;
        this.guides = guides;
        this.harvests = harvests;
        this.itemManager = itemManager;
        this.patchPlanner = patchPlanner;
        if (patchPlanner != null) { patchPlanner.setItemManager(itemManager); harvests.setFarmRunSupplier(patchPlanner::currentRun); }
    }

    void setOpenGuideAction(Runnable action) { openGuideAction = action == null ? () -> {} : action; }
    void setBankFocusAction(java.util.function.Consumer<java.util.Set<Integer>> action)
    { if (patchPlanner != null) patchPlanner.setBankFocusAction(action); }
    void setRouteAction(java.util.function.Consumer<net.runelite.api.coords.WorldPoint> action) { if (patchPlanner != null) patchPlanner.setRouteAction(action); }
    void setClearRouteAction(Runnable action){if(patchPlanner!=null)patchPlanner.setClearRouteAction(action);}
    void setNavigation(GuideNavigation navigation){if(patchPlanner!=null)patchPlanner.setNavigation(navigation);}
    void stopActiveRun(){if(patchPlanner!=null)patchPlanner.stopActiveRun();}
    void setFinishAction(Runnable action){if(patchPlanner!=null)patchPlanner.setFinishAction(action);}
    void setLoadoutAction(Runnable action) { if (patchPlanner != null) patchPlanner.setLoadoutAction(action); }
    void setStartAction(java.util.function.Consumer<FarmPatchPlanner.RunReading> action) { if (patchPlanner != null) patchPlanner.setStartAction(action); }
    FarmPatchPlanner.RunReading currentRun() { return patchPlanner == null ? null : patchPlanner.currentRun(); }
    GuideLiveProgress.Reading guideProgress(String guideId){return patchPlanner==null?GuideLiveProgress.Reading.UNKNOWN:patchPlanner.guideProgress(guideId);}
    List<GuideNavigation.Destination> travelDestinations(String guideId){return patchPlanner==null?null:patchPlanner.travelDestinations(guideId);}
    boolean isPatchGuide(String guideId){return patchPlanner!=null&&patchPlanner.isPatchGuide(guideId);}
    void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event) { if(started && patchPlanner!=null)patchPlanner.onMenuOptionClicked(event); }
    void onChatMessage(net.runelite.api.events.ChatMessage event) { if(started && patchPlanner!=null)patchPlanner.onChatMessage(event); }

    void start()
    {
        if (started) return;
        started = true;
        if (patchPlanner != null) patchPlanner.start();
        SwingUtilities.invokeLater(() -> {
            if (!started) return;
            if (timer != null) timer.stop();
            timer = new Timer(1000, event -> refresh());
            timer.start();
        });
    }

    void stop()
    {
        started = false;
        if (patchPlanner != null) patchPlanner.stop();
        clear();
        SwingUtilities.invokeLater(() -> {
            if (timer != null) timer.stop();
            timer = null;
        });
    }

    void clear()
    {
        if (patchPlanner != null) patchPlanner.clear();
        profile = null;
        remembered = null;
        long epoch = ++profileEpoch;
        SwingUtilities.invokeLater(() -> {
            if (epoch != profileEpoch) return;
            if (runs != null)
            {
                rebuilding = true;
                runs.setSelectedItem(null);
                rebuilding = false;
                showRun(null);
            }
        });
    }

    /** Client thread only. Restoring a picker never replaces a quest or another active guide. */
    void onGameTick()
    {
        if (!started) return;
        if (patchPlanner != null) patchPlanner.onGameTick();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            if (profile != null) clear();
            return;
        }
        String next = configManager.getRSProfileKey();
        if (next == null || next.isEmpty()) { if (profile != null) clear(); return; }
        if (next.equals(profile)) return;
        profile = next;
        long epoch = ++profileEpoch;
        Run saved = find(configManager.getConfiguration(CONFIG_GROUP, next, CONFIG_KEY));
        remembered = saved;
        SwingUtilities.invokeLater(() -> {
            if (epoch != profileEpoch || runs == null) return;
            rebuilding = true;
            runs.setSelectedItem(saved);
            rebuilding = false;
            showRun(saved);
        });
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        catalog.load();
        panel = vertical(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        GuideText heading = text("FARM RUNS", GOLD, true);
        heading.setFont(GuideText.FONT.deriveFont(Font.BOLD, 16f));
        panel.add(heading);
        runs = new JComboBox<>();
        runs.setName("runewarden.farm-runs.selector");
        runs.addItem(null);
        for (Run run : RUNS) if (catalog.entry(run.guideId) != null) runs.addItem(run);
        runs.setRenderer(new javax.swing.DefaultListCellRenderer()
        {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                Object value, int index, boolean selected, boolean focus)
            {
                return super.getListCellRendererComponent(list, value == null ? "Choose a farm run" : value,
                    index, selected, focus);
            }
        });
        runs.setMaximumRowCount(15);
        runs.setFont(GuideText.FONT);
        runs.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        runs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        runs.addActionListener(event -> {
            if (rebuilding) return;
            Run run = (Run) runs.getSelectedItem();
            showRun(run);
            if (run != null && accountCurrent())
            {
                guides.openGuide(run.guideId);
                remembered = run;
                save(run);
                refresh();
            }
        });
        panel.add(runs);
        panel.add(Box.createVerticalStrut(10));
        details = vertical(CARD);
        details.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 2, 1, 0,
            GOLD), BorderFactory.createEmptyBorder(9, 10, 10, 8)));
        JPanel identity = new JPanel(new BorderLayout(8, 0));
        identity.setOpaque(false);
        identity.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        icon = new JLabel();
        icon.setPreferredSize(new Dimension(36, 36));
        identity.add(icon, BorderLayout.WEST);
        title = text("", GOLD, true);
        identity.add(title, BorderLayout.CENTER);
        details.add(identity);
        description = text("", Color.WHITE, false); details.add(description);
        requirements = text("", MUTED, false); details.add(requirements);
        timerStatus = text("", GOLD, true); timerStatus.setName("runewarden.farm-runs.timer-status"); details.add(timerStatus);
        track = new JCheckBox("Track this run");
        track.setName("runewarden.farm-runs.track");
        track.setOpaque(false); track.setForeground(Color.WHITE); track.setFont(GuideText.FONT);
        track.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        track.addActionListener(event -> {
            Run run = selectedRun();
            if (rebuilding || run == null) return;
            harvests.setGroupsTracked(run.groups, track.isSelected());
            refresh();
        });
        details.add(track);
        activeStatus = text("", MUTED, false); details.add(activeStatus);
        step = text("", Color.WHITE, false); step.setName("runewarden.farm-runs.current-step"); details.add(step);
        open = new JButton("Open run checklist");
        open.setBackground(new Color(48, 43, 32)); open.setForeground(GOLD); open.setFocusPainted(false);
        open.setFont(GuideText.FONT.deriveFont(Font.BOLD)); open.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        open.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        open.addActionListener(event -> {
            Run run = selectedRun();
            if (run == null || !accountCurrent()) return;
            GuideCatalog.Entry current = guides.selectedGuide();
            if (current == null || !run.guideId.equals(current.id)) guides.openGuide(run.guideId);
            if (patchPlanner != null && (current == null || !run.guideId.equals(current.id))) patchPlanner.startSelectedRun();
            openGuideAction.run();
            refresh();
        });
        details.add(open);
        panel.add(details);
        if (patchPlanner != null) panel.add(patchPlanner.createPanel());
        rebuilding = true;
        runs.setSelectedItem(profile == null ? null : remembered);
        rebuilding = false;
        showRun(selectedRun());
        return panel;
    }

    /** Opens the matching route picker from a ready-timer card; special crops have several routes. */
    void openGroup(String groupId)
    {
        Runnable select = () -> {
            createPanel();
            Run run = primaryRun(groupId);
            runs.setSelectedItem(run);
            if (run == null) showRun(null);
        };
        if (SwingUtilities.isEventDispatchThread()) select.run();
        else SwingUtilities.invokeLater(select);
    }

    Run selectedRun() { return runs == null ? null : (Run) runs.getSelectedItem(); }

    private boolean accountCurrent()
    {
        String expected = profile;
        return expected != null && expected.equals(configManager.getRSProfileKey())
            && client.getGameState() == GameState.LOGGED_IN;
    }

    private void save(Run run)
    {
        String expected = profile;
        long epoch = profileEpoch;
        if (expected == null) return;
        clientThread.invokeLater(() -> {
            if (epoch == profileEpoch && expected.equals(configManager.getRSProfileKey())
                && client.getGameState() == GameState.LOGGED_IN)
                configManager.setConfiguration(CONFIG_GROUP, expected, CONFIG_KEY, run.guideId);
        });
    }

    private void showRun(Run run)
    {
        displayed = run;
        if (patchPlanner != null) { patchPlanner.selectGuide(run == null ? null : run.guideId); patchPlanner.setGuideAction(id -> { guides.openGuide(id); SwingUtilities.invokeLater(openGuideAction); }); }
        if (details == null) return;
        details.setVisible(run != null);
        if (run == null) { panel.revalidate(); panel.repaint(); return; }
        GuideCatalog.Entry entry = catalog.entry(run.guideId);
        title.setText(run.label);
        description.setText(entry == null ? "" : entry.subtitle);
        requirements.setText(entry == null || entry.requirements == null || entry.requirements.isEmpty()
            ? "" : String.join(" · ", entry.requirements));
        icon.setIcon(null);
        if (itemManager != null) itemManager.getImage(run.iconId).addTo(icon);
        track.setVisible(!run.groups.isEmpty());
        refresh();
        panel.revalidate(); panel.repaint();
    }

    private void refresh()
    {
        if (panel == null || displayed == null) return;
        long now = Instant.now().getEpochSecond();
        timerStatus.setText(status(displayed, harvests.reading(), now));
        rebuilding = true;
        track.setSelected(!displayed.groups.isEmpty() && displayed.groups.stream().allMatch(harvests::isGroupSelected));
        track.setEnabled(accountCurrent());
        rebuilding = false;
        GuideCatalog.Entry active = guides.selectedGuide();
        boolean selected = active != null && displayed.guideId.equals(active.id);
        activeStatus.setText(selected ? "Active run" : "Ready to start");
        GuideCatalog.Step currentStep = selected ? guides.currentStep() : null;
        step.setText(currentStep == null ? "" : currentStep.text);
        open.setText(selected ? "Open run checklist" : "Start run");
        open.setEnabled(accountCurrent());
    }

    static String status(Run run, HarvestClockTool.Reading reading, long now)
    {
        if ("training:farming-contracts".equals(run.guideId))
            return !reading.active ? reading.message : reading.contract == null ? "Contract not observed" : reading.contract.name + " · " + reading.contract.status(now);
        if (run.groups.isEmpty()) return "No growth timer";
        if (!reading.active) return reading.message;
        StringBuilder result = new StringBuilder();
        for (String group : run.groups)
        {
            HarvestClockTool.Harvest harvest = reading.harvests.stream()
                .filter(item -> group.equals(item.id)).findFirst().orElse(null);
            if (result.length() > 0) result.append('\n');
            if (harvest == null) result.append("Not observed");
            else result.append(harvest.name).append(" · ").append(harvest.status(now));
        }
        return result.toString();
    }

    static Run find(String id)
    {
        for (Run run : RUNS) if (run.guideId.equals(id)) return run;
        return null;
    }

    static Run primaryRun(String groupId)
    {
        if (groupId != null && groupId.startsWith("farm:")) return find(groupId.substring(5));
        if ("contract".equals(groupId)) return find("training:farming-contracts");
        if ("special".equals(groupId)) return null;
        for (Run run : RUNS) if (run.groups.contains(groupId)) return run;
        return null;
    }

    private static JPanel vertical(Color background)
    {
        JPanel result = SidebarUi.column();
        result.setBackground(background); result.setAlignmentX(JPanel.LEFT_ALIGNMENT); return result;
    }

    private static GuideText text(String value, Color color, boolean bold)
    {
        GuideText text = new GuideText(value); text.setForeground(color);
        if (bold) text.setFont(GuideText.FONT.deriveFont(Font.BOLD)); return text;
    }

    static final class Run
    {
        final String label, guideId;
        final int iconId;
        final List<String> groups;
        Run(String label, String method, int iconId, String... groups)
        {
            this.label = label; this.guideId = "training:" + method; this.iconId = iconId;
            this.groups = Collections.unmodifiableList(Arrays.asList(groups));
        }
        @Override public String toString() { return label; }
    }

    // Exact representative item IDs from the bundled canonical OSRS item-name index.
    static final List<Run> RUNS = Collections.unmodifiableList(Arrays.asList(
        new Run("Herb run", "herb-runs", 5295, "herb"),
        new Run("Allotments & flowers", "allotment-runs", 5318, "allotment", "flower"),
        new Run("Tree run", "tree-runs", 5371, "tree"),
        new Run("Fruit tree run", "fruit-tree-runs", 5496, "fruit-tree"),
        new Run("Hardwood tree run", "hardwood-runs", 21477, "tree"),
        new Run("Giant seaweed run", "giant-seaweed", 21490, "special"),
        new Run("Calquat run", "calquat-runs", 5503, "fruit-tree"),
        new Run("Crystal tree run", "crystal-tree-runs", 23659, "fruit-tree"),
        new Run("Celastrus run", "celastrus-runs", 22856, "fruit-tree"),
        new Run("Redwood health check", "redwood-farming", 22859, "tree"),
        new Run("Farming contracts", "farming-contracts", 22993),
        new Run("Hespori", "hespori", 22875, "special"),
        new Run("Tithe Farm", "tithe-farm", 13353),
        new Run("Bird house run", "bird-house-runs", 21515, "bird-house")
    ));
}
