/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.inject.Injector;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;

/** Read-only harvest dashboard backed by RuneLite's own, account-scoped Time Tracking data. */
@Singleton
final class HarvestClockTool
{
    static final Color GOLD = new Color(224, 190, 113);
    static final Color BACKGROUND = new Color(17, 18, 21);
    static final Color WHITE = new Color(243, 240, 232);
    static final Color MUTED = new Color(185, 183, 175);
    private final Client client;
    private final ConfigManager configManager;
    private final PluginManager pluginManager;
    private final OverlayManager overlays;
    private final HarvestClockOverlay overlay;
    private final HarvestSelections selections;
    private final HarvestTimerWindows timerWindows=new HarvestTimerWindows();
    private volatile DisplayCache displayCache;
    private static final class DisplayCache
    {
        final Reading reading;final long second,revision;final List<Harvest> rows;
        DisplayCache(Reading reading,long second,long revision,List<Harvest> rows){this.reading=reading;this.second=second;this.revision=revision;this.rows=rows;}
    }
    private final RunewardenConfig config;
    private volatile Reading reading = Reading.empty("Log in to track harvests.");
    private volatile HarvestSelections.Session readingSession;
    private volatile boolean started;
    private volatile Runnable openAction = () -> {};
    private volatile Consumer<String> openRunAction = group -> {};
    private volatile java.util.function.Supplier<FarmPatchPlanner.RunReading> farmRun = () -> null;
    private volatile String profile;
    private int warmup;
    private int ticks;
    private JPanel panel;
    private JPanel rows;
    private GuideText summary;
    private Timer panelTimer;
    private JPanel dashboardPanel;
    private JPanel dashboardRows;
    private GuideText dashboardSummary;
    private final Map<String, JCheckBox> choices = new LinkedHashMap<>();
    private final Map<String, GuideText> statuses = new LinkedHashMap<>();
    private boolean refreshing;

    @Inject HarvestClockTool(Client client, ConfigManager configManager, PluginManager pluginManager,
        OverlayManager overlays, RunewardenConfig config)
    {
        this.client = client;
        this.configManager = configManager;
        this.pluginManager = pluginManager;
        this.overlays = overlays;
        this.selections = new HarvestSelections(configManager);
        this.config=config;
        this.overlay = new HarvestClockOverlay(this, config);
    }

    net.runelite.client.ui.overlay.Overlay overlay() { return overlay; }
    void setHudLocked(boolean locked) { overlay.setHudLocked(locked); }

    void start()
    {
        if (started) return;
        started = true;
        clear();
        // This module can be toggled independently while HUD movement is locked.
        boolean movable = overlay.isMovable();
        overlay.setHudLocked(false);
        try { overlays.add(overlay); }
        finally { overlay.setHudLocked(!movable); }
        SwingUtilities.invokeLater(() ->
        {
            if (!started) return;
            if (panelTimer != null) panelTimer.stop();
            panelTimer = new Timer(1000, event -> refreshPanel());
            panelTimer.start();
        });
    }

    void stop()
    {
        started = false;
        clear();
        overlays.remove(overlay);
        SwingUtilities.invokeLater(() ->
        {
            if (panelTimer != null) panelTimer.stop();
            panelTimer = null;
        });
    }

    /** Call on logout and RuneScape profile change before the next game tick. */
    void clear()
    {
        reading = Reading.empty("Log in to track harvests.");
        readingSession = null;
        displayCache=null;
        selections.clear();
        timerWindows.clear();
        profile = null;
        warmup = 3;
        ticks = 0;
        SwingUtilities.invokeLater(this::refreshPanel);
    }

    void setOpenAction(Runnable action) { openAction = action == null ? () -> {} : action; }
    void openPanel() { SwingUtilities.invokeLater(() -> openAction.run()); }
    Reading reading()
    {
        Reading current = reading;
        return current.active && !selections.isCurrent(readingSession)
            ? Reading.empty("Loading harvest observations.") : current;
    }
    boolean started() { return started; }
    void setOpenRunAction(Consumer<String> action) { openRunAction = action == null ? group -> {} : action; }
    void setFarmRunSupplier(java.util.function.Supplier<FarmPatchPlanner.RunReading> supplier) { farmRun = supplier == null ? () -> null : supplier; }
    FarmPatchPlanner.RunReading farmRun() { FarmPatchPlanner.RunReading run = farmRun.get(); return run != null && run.active ? run : null; }
    void openFarmRun()
    {
        FarmPatchPlanner.RunReading run = farmRun();
        if (run != null) SwingUtilities.invokeLater(() -> {
            FarmPatchPlanner.RunReading current = farmRun();
            if (current != null && run.profile.equals(current.profile) && run.guideId.equals(current.guideId) && run.startedAt == current.startedAt) openRunAction.accept("farm:" + run.guideId);
        });
    }
    Set<String> selectedGroupIds()
    {
        return selections.isCurrent(selections.session()) ? selections.selected(reading().harvests) : Collections.emptySet();
    }
    boolean isGroupSelected(String group) { return selectedGroupIds().contains(group); }
    boolean trackGroups(Collection<String> groups) { return setGroupsTracked(groups, true); }
    boolean setGroupsTracked(Collection<String> groups, boolean tracked)
    {
        boolean changed = selections.update(selections.session(), groups, tracked, reading().harvests);
        if (changed) SwingUtilities.invokeLater(this::refreshPanel);
        return changed;
    }
    List<Harvest> selectedHarvests()
    {
        Reading current = reading();
        HarvestSelections.Session session = readingSession;
        if (!current.active || !selections.isCurrent(session)) return Collections.emptyList();
        Set<String> ids = selections.selected(current.harvests);
        List<Harvest> result = new ArrayList<>();
        for (Harvest harvest : current.harvests) if (ids.contains(harvest.id)) result.add(harvest);
        return Collections.unmodifiableList(result);
    }
    List<Harvest> displayHarvests(long now)
    {
        Reading current=reading();if(!current.active)return Collections.emptyList();long revision=selections.revision();
        DisplayCache cached=displayCache;
        if(cached!=null&&cached.reading==current&&cached.second==now&&cached.revision==revision)return cached.rows;
        List<Harvest> result=new ArrayList<>();
        for(Harvest harvest:selectedHarvests())if(harvest.observed())result.add(harvest);
        result.sort(Comparator.comparing((Harvest harvest)->!harvest.due(now))
            .thenComparingLong(harvest->harvest.due(now)?0:harvest.completion).thenComparing(harvest->harvest.name));
        List<Harvest> frozen=Collections.unmodifiableList(result);displayCache=new DisplayCache(current,now,revision,frozen);return frozen;
    }

    /** Client thread only. Native Time Tracking remains responsible for observing and storing patches. */
    void onGameTick()
    {
        if (!started) return;
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            if (profile != null) clear(); else reading = Reading.empty("Log in to track harvests.");
            return;
        }
        String currentProfile = configManager.getRSProfileKey();
        if (currentProfile == null || currentProfile.isEmpty())
        {
            if (profile != null) clear();
            reading = Reading.empty("Waiting for account profile.");
            return;
        }
        if (!currentProfile.equals(profile))
        {
            reading = Reading.empty("Loading harvest observations.");
            selections.bind(currentProfile);
            timerWindows.clear();
            profile = currentProfile;
            warmup = 3;
        }
        if (warmup-- > 0 || ticks++ % 5 != 0) return;
        TimeTrackingPlugin nativePlugin = findTimeTracking();
        if (nativePlugin == null || !pluginManager.isPluginActive(nativePlugin))
        {
            reading = Reading.disabled(nativePlugin != null);
            timerWindows.clear();
            return;
        }
        Injector injector = nativePlugin.getInjector();
        if (injector == null)
        {
            reading = Reading.empty("Time Tracking is starting.");
            return;
        }
        // Public, typed RuneLite services. No reflection or access to native private fields.
        HarvestSelections.Session expected = selections.session();
        FarmingTracker farming = injector.getInstance(FarmingTracker.class);
        BirdHouseTracker birdHouses = injector.getInstance(BirdHouseTracker.class);
        FarmingContractManager contracts = injector.getInstance(FarmingContractManager.class);
        Reading captured = readTrackers(farming, birdHouses, contracts);
        if (selections.isCurrent(expected)) { readingSession = expected; reading = timerWindows.observe(captured,Instant.now().getEpochSecond()); }
    }

    private TimeTrackingPlugin findTimeTracking()
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin instanceof TimeTrackingPlugin) return (TimeTrackingPlugin) plugin;
        }
        return null;
    }

    static Reading readTrackers(FarmingTracker farming, BirdHouseTracker birdHouses)
    {
        List<Harvest> harvests = new ArrayList<>();
        harvests.add(new Harvest("bird-house", "Bird houses", birdHouses.getSummary(), birdHouses.getCompletionTime(), false));
        for (Tab tab : Tab.FARMING_TABS)
        {
            String label = tab.getName().replace(" Patches", "");
            harvests.add(new Harvest(tab.name().toLowerCase(Locale.ROOT).replace('_', '-'), label,
                farming.getSummary(tab), farming.getCompletionTime(tab), true));
        }
        return new Reading(true, false, "", harvests);
    }
    static Reading readTrackers(FarmingTracker farming, BirdHouseTracker birdHouses, FarmingContractManager contracts)
    {
        Reading nativeTimers = readTrackers(farming, birdHouses);
        String name = contracts == null || !contracts.hasContract() ? null : contracts.getContractName();
        Harvest contract = name == null ? null : new Harvest("contract", "Contract · " + name, contracts.getSummary(), contracts.getCompletionTime(), true);
        return new Reading(true, false, "", nativeTimers.harvests, contract);
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        JPanel header = SidebarUi.section("TIMERS");
        GuideText title = SidebarUi.heading("Track categories");
        title.setForeground(GOLD);
        title.setFont(GuideText.FONT.deriveFont(Font.BOLD));
        header.add(title);
        summary = SidebarUi.label(" ");
        summary.setForeground(WHITE);
        summary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        summary.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        header.add(summary);
        JComboBox<RunewardenConfig.TimerDisplay> display=new JComboBox<>(RunewardenConfig.TimerDisplay.values());
        display.setName("harvest.timer-display");display.setSelectedItem(NodeTimerOverlay.display(config.harvestTimerDisplay()));
        display.setToolTipText("Rings count down from the first observed remaining time; they do not measure full crop growth.");
        display.addActionListener(event->configManager.setConfiguration(RunewardenConfig.GROUP,"harvestTimerDisplay",display.getSelectedItem()));
        header.add(SidebarUi.field("HUD countdown",display));
        panel.add(header, BorderLayout.NORTH);
        rows = SidebarUi.column();
        panel.add(rows, BorderLayout.CENTER);
        for (Harvest harvest : groupCatalog())
        {
            JPanel row = new JPanel(new BorderLayout(0, 4));
            row.setBackground(new Color(29, 30, 33));
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 2, 0, new Color(78, 68, 44)),
                BorderFactory.createEmptyBorder(8, 8, 8, 6)));
            JCheckBox choice = new JCheckBox(harvest.name);
            choice.setOpaque(false);
            choice.setForeground(WHITE);
            choice.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            choice.putClientProperty("harvestGroup", harvest.id);
            choice.addActionListener(event ->
            {
                if (refreshing) return;
                HarvestSelections.Session expected = (HarvestSelections.Session) choice.getClientProperty("harvestSession");
                selections.toggle(expected, harvest.id, choice.isSelected(), reading().harvests);
                refreshPanel();
            });
            GuideText status = SidebarUi.label("Not observed");
            status.setForeground(MUTED);
            status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            status.setBorder(BorderFactory.createEmptyBorder(0, 23, 0, 0));
            row.add(choice, BorderLayout.NORTH);
            row.add(status, BorderLayout.CENTER);
            choices.put(harvest.id, choice);
            statuses.put(harvest.id, status);
            rows.add(row);
        }
        refreshPanel();
        return panel;
    }

    /** Read-only command-centre overview; timer selection stays in the Timers module. */
    JPanel createDashboardPanel()
    {
        if (dashboardPanel != null) return dashboardPanel;
        dashboardPanel = new JPanel(new BorderLayout(0, 10));
        dashboardPanel.setBackground(BACKGROUND);
        dashboardPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        JLabel title = new JLabel("HARVEST STATUS");
        title.setForeground(GOLD);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        dashboardSummary = SidebarUi.label(" ");
        dashboardSummary.setForeground(WHITE);
        dashboardSummary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        header.add(title, BorderLayout.NORTH);
        header.add(dashboardSummary, BorderLayout.CENTER);
        dashboardPanel.add(header, BorderLayout.NORTH);
        dashboardRows = SidebarUi.column();
        dashboardPanel.add(dashboardRows, BorderLayout.CENTER);
        refreshPanel();
        return dashboardPanel;
    }

    private void refreshPanel()
    {
        if (panel == null && dashboardPanel == null) return;
        Reading current = reading();
        long now = Instant.now().getEpochSecond();
        Set<String> selected = selectedGroupIds();
        List<Harvest> visible = displayHarvests(now);
        long due = visible.stream().filter(harvest -> harvest.due(now)).count();
        if (summary != null)
        {
            summary.setText(current.active ? selected.size() + " selected · " + due + " due" : current.message);
            HarvestSelections.Session session = selections.session();
            refreshing = true;
            for (Harvest group : groupCatalog())
            {
                Harvest harvest = current.harvests.stream().filter(item -> item.id.equals(group.id)).findFirst().orElse(group);
                JCheckBox choice = choices.get(group.id);
                choice.setSelected(selected.contains(group.id));
                choice.setEnabled(selections.isCurrent(session));
                choice.putClientProperty("harvestSession", session);
                GuideText status = statuses.get(group.id);
                status.setText(harvest.status(now));
                status.setForeground(harvest.due(now) && selected.contains(group.id) ? GOLD : MUTED);
            }
            refreshing = false;
        }
        if (dashboardPanel != null)
        {
            dashboardSummary.setText(!current.active ? current.message : visible.isEmpty()
                ? selected.isEmpty() ? "No timers selected" : "Awaiting observations"
                : due + " due · " + (visible.size() - due) + " growing");
            dashboardRows.removeAll();
            HarvestSelections.Session expected = selections.session();
            for (Harvest harvest : visible)
            {
                JButton row = new JButton("<html><b>" + harvest.name + "</b><br>" + harvest.status(now) + "</html>");
                row.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                row.setBackground(new Color(29, 30, 33));
                row.setForeground(harvest.due(now) ? GOLD : WHITE);
                row.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 2, 0,
                    harvest.due(now) ? GOLD : new Color(66, 64, 57)), BorderFactory.createEmptyBorder(9, 10, 9, 6)));
                row.setFocusPainted(false);
                row.setOpaque(true);
                row.addActionListener(event -> { if (selections.isCurrent(expected)) openRunAction.accept(harvest.id); });
                row.setToolTipText("Open Farm runs");
                dashboardRows.add(row);
            }
            if (current.contract != null)
            {
                Harvest contract = current.contract;
                JButton row = SidebarUi.wrappedButton(contract.name + " · " + contract.status(now), () -> { if (selections.isCurrent(expected)) openRunAction.accept("contract"); });
                dashboardRows.add(row);
            }
            dashboardRows.revalidate();
            dashboardRows.repaint();
        }
    }

    private static List<Harvest> groupCatalog()
    {
        List<Harvest> groups = new ArrayList<>();
        groups.add(new Harvest("bird-house", "Bird houses", SummaryState.UNKNOWN, -1, false));
        for (Tab tab : Tab.FARMING_TABS) groups.add(new Harvest(tab.name().toLowerCase(Locale.ROOT).replace('_', '-'),
            tab.getName().replace(" Patches", ""), SummaryState.UNKNOWN, -1, true));
        return groups;
    }

    static final class Harvest
    {
        final String id;
        final String name;
        final SummaryState state;
        final long completion;
        final boolean estimated;
        final long windowStart;
        Harvest(String name, SummaryState state, long completion, boolean estimated)
        {
            this(name.toLowerCase(Locale.ROOT).replace(' ', '-'), name, state, completion, estimated);
        }
        Harvest(String id, String name, SummaryState state, long completion, boolean estimated)
        {
            this(id,name,state,completion,estimated,-1);
        }
        Harvest(String id,String name,SummaryState state,long completion,boolean estimated,long windowStart)
        {
            this.id = id;
            this.name = name;
            this.state = state == null ? SummaryState.UNKNOWN : state;
            this.completion = completion;
            this.estimated = estimated;
            this.windowStart=windowStart;
        }
        Harvest withWindow(long start){return new Harvest(id,name,state,completion,estimated,start);}
        boolean hasWindow(){return windowStart>=0&&completion>windowStart;}
        double remainingFraction(long now){return hasWindow()?RadialTimer.fraction(now,windowStart,completion):Double.NaN;}
        boolean observed() { return state == SummaryState.COMPLETED || state == SummaryState.IN_PROGRESS && completion > 0; }
        boolean due(long now)
        {
            return state == SummaryState.COMPLETED || state == SummaryState.IN_PROGRESS && completion > 0 && completion <= now;
        }
        boolean pending(long now) { return state == SummaryState.IN_PROGRESS && completion > now; }
        String status(long now)
        {
            if (!estimated && state == SummaryState.COMPLETED) return "Ready · observed";
            if (due(now)) return "Due to check · estimated";
            if (pending(now)) return "In " + countdown(completion - now) + " · estimated";
            return state == SummaryState.EMPTY ? "Empty" : "Not observed";
        }
    }

    static final class Reading
    {
        final boolean active;
        final boolean canEnable;
        final String message;
        final List<Harvest> harvests;
        final Harvest contract;
        Reading(boolean active, boolean canEnable, String message, List<Harvest> harvests)
        { this(active, canEnable, message, harvests, null); }
        Reading(boolean active, boolean canEnable, String message, List<Harvest> harvests, Harvest contract)
        {
            this.active = active;
            this.canEnable = canEnable;
            this.message = message;
            this.harvests = Collections.unmodifiableList(new ArrayList<>(harvests));
            this.contract = contract;
        }
        static Reading empty(String message) { return new Reading(false, false, message, Collections.emptyList()); }
        static Reading disabled(boolean available)
        {
            return new Reading(false, false, available ? "Enable Time Tracking in RuneLite's plugin settings." : "Time Tracking is unavailable.", Collections.emptyList());
        }
        int dueCount(long now) { return (int) harvests.stream().filter(harvest -> harvest.due(now)).count(); }
        Harvest next(long now)
        {
            return harvests.stream().filter(harvest -> harvest.pending(now))
                .min(java.util.Comparator.comparingLong(harvest -> harvest.completion)).orElse(null);
        }
        Harvest firstDue(long now) { return harvests.stream().filter(harvest -> harvest.due(now)).findFirst().orElse(null); }
    }

    static String countdown(long seconds)
    {
        seconds = Math.max(0, seconds);
        if (seconds >= 86400) return seconds / 86400 + "d " + seconds % 86400 / 3600 + "h";
        if (seconds >= 3600) return seconds / 3600 + "h " + seconds % 3600 / 60 + "m";
        if (seconds >= 60) return seconds / 60 + "m " + seconds % 60 + "s";
        return seconds + "s";
    }
}
