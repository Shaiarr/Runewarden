/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.inject.Injector;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;

/** Account-scoped reminders from native Time Tracking; never creates or persists crop clocks. */
@Singleton
final class HarvestClockTool
{
    static final Color GOLD = new Color(224, 190, 113), BACKGROUND = new Color(17, 18, 21),
        WHITE = new Color(243, 240, 232), MUTED = new Color(185, 183, 175);
    private final Client client;
    private final ConfigManager manager;
    private final PluginManager plugins;
    private final OverlayManager overlays;
    private final RunewardenConfig config;
    private final HarvestClockOverlay overlay;
    private final HarvestSelections selections;
    private final HarvestTimerWindows windows = new HarvestTimerWindows();
    private ItemManager items;
    private FarmHarvestTimers farmTimers;
    static final Tab[] PURE_TABS={Tab.HERB,Tab.FLOWER,Tab.ALLOTMENT,Tab.BUSH,Tab.GRAPE,Tab.HOPS};
    private volatile Observation observation = new Observation(Reading.empty("Log in to track harvests."), null);
    private static final class Observation
    {
        final Reading reading; final HarvestSelections.Session session; final List<BirdHouseReading> birds;
        Observation(Reading reading, HarvestSelections.Session session) { this(reading,session,Collections.emptyList()); }
        Observation(Reading reading, HarvestSelections.Session session,List<BirdHouseReading> birds)
        { this.reading=reading;this.session=session;this.birds=birds; }
    }
    private volatile boolean started;
    private volatile Runnable openAction = () -> {};
    private volatile Consumer<String> openRunAction = group -> {};
    private volatile Predicate<String> runGroupSupport = group -> false;
    private JButton openRun;
    private String profile;
    private int warmup, ticks;
    private JPanel panel, dashboard;
    private JLabel summary, dashboardSummary, dashboardStatus;
    private Timer panelTimer;
    private final Map<String, JCheckBox> choices = new LinkedHashMap<>();
    private final Map<String, JLabel> statuses = new LinkedHashMap<>();
    private volatile DisplayCache displayCache;

    @Inject HarvestClockTool(Client client, ConfigManager manager, PluginManager plugins,
        OverlayManager overlays, RunewardenConfig config)
    {
        this.client = client; this.manager = manager; this.plugins = plugins;
        this.overlays = overlays; this.config = config;
        selections = new HarvestSelections(manager);
        overlay = new HarvestClockOverlay(this, config);
    }
    @Inject void setItemManager(ItemManager value) { items = value; }
    @Inject void setFarmTimers(FarmHarvestTimers value) { farmTimers=value; }
    net.runelite.client.ui.overlay.Overlay overlay() { return overlay; }
    void setHudLocked(boolean locked) { overlay.setHudLocked(locked); }
    boolean started() { return started; }
    void setOpenAction(Runnable action) { openAction = action == null ? () -> {} : action; }
    void setOpenRunAction(Consumer<String> action) { openRunAction = action == null ? group -> {} : action; }
    void setRunGroupSupport(Predicate<String> support)
    { runGroupSupport=support==null?group->false:support;SwingUtilities.invokeLater(this::refreshPanel); }
    void openPanel() { SwingUtilities.invokeLater(() -> openAction.run()); }

    void start()
    {
        if (started) return;
        started = true; clear();
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
        started = false; clear(); overlays.remove(overlay);
        SwingUtilities.invokeLater(() -> { if (panelTimer != null) panelTimer.stop(); panelTimer = null; });
    }
    /** Clear before another account's native observations are loaded. */
    void clear()
    {
        observation = new Observation(Reading.empty("Log in to track harvests."), null);
        displayCache = null; selections.clear(); windows.clear(); profile = null; warmup = 3; ticks = 0;
        SwingUtilities.invokeLater(this::refreshPanel);
    }
    Reading reading()
    {
        Observation current = observation;
        return current.reading.active && !selections.isCurrent(current.session)
            ? Reading.empty("Loading harvest observations.") : current.reading;
    }
    Set<String> selectedGroupIds()
    {
        Observation captured = observation;
        HarvestSelections.Session expected = selections.session();
        Set<String> result = selections.selected(captured.reading.active && selections.isCurrent(captured.session)
            ? captured.reading.harvests : Collections.emptyList());
        return selections.isCurrent(expected) ? result : Collections.emptySet();
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
        Observation captured = observation;
        Reading current = captured.reading;
        if (!current.active || !selections.isCurrent(captured.session)) return Collections.emptyList();
        Set<String> ids = selections.selected(current.harvests);
        List<Harvest> result = new ArrayList<>();
        for (Harvest harvest : current.harvests) if (ids.contains(harvest.id)) result.add(harvest);
        return selections.isCurrent(captured.session) ? Collections.unmodifiableList(result) : Collections.emptyList();
    }
    List<BirdHouseReading> birdHouses()
    {
        Observation captured=observation;
        return started&&captured.reading.active&&selections.isCurrent(captured.session)&&isGroupSelected("bird-house")
            ? captured.birds:Collections.emptyList();
    }

    /** Read-only native Time Tracking records: individual varp:observed-second, never the aggregate clock. */
    static List<BirdHouseReading> readBirdHouses(ConfigManager manager,Client client,String profile,long now)
    {
        List<BirdHouseReading> result=new ArrayList<>();
        for(BirdHouseSite site:BIRD_SITES)
        {
            String value=manager.getConfiguration("timetracking",profile,"birdhouse."+site.varp);
            if(value==null)continue;String[] parts=value.split(":");if(parts.length!=2)continue;
            try
            {
                int state=Integer.parseInt(parts[0]);long observed=Long.parseLong(parts[1]);
                if(state<0||state>27||state!=client.getVarpValue(site.varp)||observed<=0||observed>now)continue;
                result.add(new BirdHouseReading(site,state,observed));
            }
            catch(NumberFormatException ignored){ }
        }
        return Collections.unmodifiableList(result);
    }
    static final class BirdHouseSite
    {
        final String name;final int varp;final WorldPoint point;
        BirdHouseSite(String name,int varp,int x,int y){this.name=name;this.varp=varp;this.point=new WorldPoint(x,y,0);}
    }
    // Native Time Tracking identities; four object positions are the same verified points used by the farm-run planner.
    static final List<BirdHouseSite> BIRD_SITES=List.of(
        new BirdHouseSite("Meadow north",VarPlayerID.BIRDHOUSE_TRANSMIT_A,3677,3882),
        new BirdHouseSite("Meadow south",VarPlayerID.BIRDHOUSE_TRANSMIT_B,3678,3815),
        new BirdHouseSite("Valley north-east",VarPlayerID.BIRDHOUSE_TRANSMIT_C,3768,3761),
        new BirdHouseSite("Valley south-west",VarPlayerID.BIRDHOUSE_TRANSMIT_D,3763,3755));
    static final class BirdHouseReading
    {
        private static final int[] ICONS={21512,21515,21518,21521,22192,22195,22198,22201,22204};
        static final long DURATION=50*60;
        final BirdHouseSite site;final int state;final long observed;
        BirdHouseReading(BirdHouseSite site,int state,long observed){this.site=site;this.state=state;this.observed=observed;}
        int itemId(){return state==0?314:ICONS[(state-1)/3];}
        boolean seeded(){return state>0&&state%3==0;}
        long remaining(long now){return Math.max(0,observed+DURATION-now);}
        String status(long now){return state==0?"Empty":!seeded()?"Add seeds":remaining(now)==0?"Ready":countdown(remaining(now));}
    }
    List<Harvest> displayHarvests(long now)
    {
        Observation captured = observation;
        Reading current = captured.reading;
        if (!current.active || !selections.isCurrent(captured.session)) return Collections.emptyList();
        long revision = selections.revision();
        DisplayCache cached = displayCache;
        if (cached != null && cached.reading == current && cached.second == now && cached.revision == revision) return cached.rows;
        List<Harvest> result = new ArrayList<>();
        for (Harvest harvest : selectedHarvests()) if (harvest.observed()) result.add(harvest);
        result.sort(Comparator.comparing((Harvest harvest) -> !harvest.due(now))
            .thenComparingLong(harvest -> harvest.due(now) ? 0 : harvest.completion).thenComparing(harvest -> harvest.name));
        List<Harvest> frozen = Collections.unmodifiableList(result);
        displayCache = new DisplayCache(current, now, revision, frozen);
        return selections.isCurrent(captured.session) ? frozen : Collections.emptyList();
    }
    private static final class DisplayCache
    {
        final Reading reading; final long second, revision; final List<Harvest> rows;
        DisplayCache(Reading reading, long second, long revision, List<Harvest> rows)
        { this.reading = reading; this.second = second; this.revision = revision; this.rows = rows; }
    }

    /** Client thread only; native services retain ownership of observations and notifications. */
    void onGameTick()
    {
        if (!started) return;
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            if (profile != null) clear(); else observation = new Observation(Reading.empty("Log in to track harvests."), null);
            return;
        }
        String next = manager.getRSProfileKey();
        if (next == null || next.isEmpty())
        {
            if (profile != null) clear();
            observation = new Observation(Reading.empty("Waiting for account profile."), null); return;
        }
        if (!next.equals(profile))
        {
            observation = new Observation(Reading.empty("Loading harvest observations."), null);
            selections.bind(next); windows.clear(); profile = next; warmup = 3;
        }
        if (warmup-- > 0 || ticks++ % 5 != 0) return;
        TimeTrackingPlugin nativePlugin = null;
        for (Plugin plugin : plugins.getPlugins()) if (plugin instanceof TimeTrackingPlugin) { nativePlugin = (TimeTrackingPlugin) plugin; break; }
        if (nativePlugin == null || !plugins.isPluginActive(nativePlugin))
        { observation = new Observation(Reading.disabled(nativePlugin != null), null); windows.clear(); return; }
        Injector injector = nativePlugin.getInjector();
        if (injector == null) { observation = new Observation(Reading.empty("Time Tracking is starting."), null); return; }
        HarvestSelections.Session expected = selections.session();
        FarmingTracker nativeFarming=injector.getInstance(FarmingTracker.class);
        Reading captured = readTrackers(nativeFarming, injector.getInstance(BirdHouseTracker.class),
            injector.getInstance(FarmingContractManager.class));
        if(farmTimers!=null)
        {
            List<Harvest> groups=new ArrayList<>(captured.harvests);groups.addAll(farmTimers.read(nativeFarming,next));
            captured=new Reading(captured.active,captured.canEnable,captured.message,groups,captured.contract);
        }
        long now=Instant.now().getEpochSecond();
        List<BirdHouseReading> birds=readBirdHouses(manager,client,next,now);
        if (selections.isCurrent(expected))
        { observation = new Observation(windows.observe(captured,now), expected,birds); }
    }
    static String groupId(Tab tab) { return tab.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    private static List<Harvest> groupCatalog()
    {
        List<Harvest> result = new ArrayList<>();
        result.add(new Harvest("bird-house", "Bird houses", SummaryState.UNKNOWN, -1, false));
        for (Tab tab : PURE_TABS)
            result.add(new Harvest(groupId(tab), tab.getName().replace(" Patches", ""), SummaryState.UNKNOWN, -1, true));
        for(FarmHarvestTimers.Group group:FarmHarvestTimers.GROUPS)
            result.add(new Harvest(group.id,group.name,SummaryState.UNKNOWN,-1,true));
        return result;
    }
    BufferedImage icon(String group)
    {
        if (items == null) return null;
        if ("bird-house".equals(group)) return items.getImage(Tab.BIRD_HOUSE.getItemID());
        for (Tab tab : PURE_TABS) if (groupId(tab).equals(group)) return items.getImage(tab.getItemID());
        for(FarmHarvestTimers.Group category:FarmHarvestTimers.GROUPS)if(category.id.equals(group))return items.getImage(category.iconId);
        return null;
    }
    static Reading readTrackers(FarmingTracker farming, BirdHouseTracker birds)
    {
        List<Harvest> result = new ArrayList<>();
        result.add(new Harvest("bird-house", "Bird houses", birds.getSummary(), birds.getCompletionTime(), false));
        for (Tab tab : PURE_TABS)
            result.add(new Harvest(groupId(tab), tab.getName().replace(" Patches", ""), farming.getSummary(tab), farming.getCompletionTime(tab), true));
        return new Reading(true, false, "", result);
    }
    static Reading readTrackers(FarmingTracker farming, BirdHouseTracker birds, FarmingContractManager contracts)
    {
        Reading timers = readTrackers(farming, birds);
        String name = contracts == null || !contracts.hasContract() ? null : contracts.getContractName();
        Harvest contract = name == null ? null : new Harvest("contract", "Contract · " + name, contracts.getSummary(), contracts.getCompletionTime(), true);
        return new Reading(true, false, "", timers.harvests, contract);
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = column(); panel.add(label("TIMERS & HARVEST", GOLD));
        summary = label(" ", WHITE); panel.add(summary);
        JComboBox<RunewardenConfig.TimerDisplay> display = new JComboBox<>(RunewardenConfig.TimerDisplay.values());
        display.setName("harvest.timer-display");
        display.setSelectedItem(config.harvestTimerDisplay() == null ? RunewardenConfig.TimerDisplay.TEXT : config.harvestTimerDisplay());
        display.setToolTipText("Rings measure the remaining time since this session first observed the timer.");
        display.addActionListener(event -> manager.setConfiguration(RunewardenConfig.GROUP, "harvestTimerDisplay", display.getSelectedItem()));
        panel.add(display);
        for (Harvest harvest : groupCatalog())
        {
            JPanel card = new JPanel(new BorderLayout(4, 2)); card.setOpaque(false);
            card.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
            JCheckBox choice = new JCheckBox(harvest.name); choice.setOpaque(false); choice.setForeground(WHITE);
            choice.putClientProperty("harvestGroup", harvest.id);
            choice.addActionListener(event ->
            {
                selections.toggle((HarvestSelections.Session) choice.getClientProperty("harvestSession"), harvest.id,
                    choice.isSelected(), reading().harvests);
                refreshPanel();
            });
            JLabel status = label("Not observed", MUTED);
            card.add(choice, BorderLayout.NORTH); card.add(status, BorderLayout.CENTER);
            BufferedImage image = icon(harvest.id);
            if (image != null) card.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
            choices.put(harvest.id, choice); statuses.put(harvest.id, status); panel.add(card);
        }
        refreshPanel(); return panel;
    }
    JPanel createDashboardPanel()
    {
        if (dashboard != null) return dashboard;
        dashboard = column(); dashboard.add(label("HARVEST STATUS", GOLD));
        dashboardSummary = label(" ", WHITE); dashboard.add(dashboardSummary);
        dashboardStatus = label(" ", WHITE); dashboard.add(dashboardStatus);
        openRun = new JButton("Farm runs"); openRun.setForeground(GOLD); openRun.setBackground(BACKGROUND);
        openRun.addActionListener(event ->
        {
            HarvestSelections.Session expected = observation.session;
            List<Harvest> visible = displayHarvests(Instant.now().getEpochSecond());
            Harvest runnable=visible.stream().filter(harvest->runGroupSupport.test(harvest.id)).findFirst().orElse(null);
            if (selections.isCurrent(expected) && runnable!=null) openRunAction.accept(runnable.id);
        });
        dashboard.add(openRun);
        refreshPanel(); return dashboard;
    }
    private static JPanel column()
    {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND); panel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        return panel;
    }
    private static JLabel label(String text, Color color)
    {
        JLabel label = new JLabel(text); label.setForeground(color); label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0)); return label;
    }
    private void refreshPanel()
    {
        if (panel == null && dashboard == null) return;
        Reading current = reading(); long now = Instant.now().getEpochSecond();
        Set<String> selected = selectedGroupIds(); List<Harvest> visible = displayHarvests(now);
        long due = visible.stream().filter(harvest -> harvest.due(now)).count();
        if (summary != null)
        {
            summary.setText(html(current.active ? selected.size() + " selected · " + due + " due" : current.message));
            HarvestSelections.Session session = selections.session();
            for (Harvest group : groupCatalog())
            {
                Harvest harvest = current.harvests.stream().filter(item -> item.id.equals(group.id)).findFirst().orElse(group);
                JCheckBox choice = choices.get(group.id); choice.setSelected(selected.contains(group.id));
                choice.setEnabled(selections.isCurrent(session)); choice.putClientProperty("harvestSession", session);
                JLabel status = statuses.get(group.id); status.setText(harvest.status(now));
                status.setForeground(harvest.due(now) && selected.contains(group.id) ? GOLD : MUTED);
            }
            panel.repaint();
        }
        if (dashboard == null) return;
        dashboardSummary.setText(html(!current.active ? current.message : visible.isEmpty()
            ? selected.isEmpty() ? "No timers selected" : "Awaiting observations" : due + " due · " + (visible.size() - due) + " growing"));
        Harvest runnable=visible.stream().filter(harvest->runGroupSupport.test(harvest.id)).findFirst().orElse(null);
        openRun.setEnabled(current.active&&runnable!=null);
        openRun.setText(runnable==null?"Reminder only":"Open "+runnable.name.toLowerCase(Locale.ROOT)+" run");
        StringBuilder text = new StringBuilder("<html>");
        for (Harvest harvest : visible)
            text.append("<b>").append(escape(harvest.name)).append("</b><br>").append(escape(harvest.status(now))).append("<br><br>");
        if (current.contract != null) text.append(escape(current.contract.name)).append(" · ").append(escape(current.contract.status(now)));
        dashboardStatus.setText(text.append("</html>").toString());
    }
    private static String html(String value) { return "<html><body style='width:168px'>" + escape(value) + "</body></html>"; }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static final class Harvest
    {
        final String id, name; final SummaryState state; final long completion, windowStart; final boolean estimated;
        Harvest(String name, SummaryState state, long completion, boolean estimated)
        { this(name.toLowerCase(Locale.ROOT).replace(' ', '-'), name, state, completion, estimated); }
        Harvest(String id, String name, SummaryState state, long completion, boolean estimated)
        { this(id, name, state, completion, estimated, -1); }
        Harvest(String id, String name, SummaryState state, long completion, boolean estimated, long windowStart)
        {
            this.id = id; this.name = name; this.state = state == null ? SummaryState.UNKNOWN : state;
            this.completion = completion; this.estimated = estimated; this.windowStart = windowStart;
        }
        Harvest withWindow(long start) { return new Harvest(id, name, state, completion, estimated, start); }
        boolean hasWindow() { return windowStart >= 0 && completion > windowStart; }
        double remainingFraction(long now) { return hasWindow() ? RadialTimer.fraction(now, windowStart, completion) : Double.NaN; }
        boolean observed() { return state == SummaryState.COMPLETED || state == SummaryState.IN_PROGRESS && completion > 0; }
        boolean due(long now) { return state == SummaryState.COMPLETED || state == SummaryState.IN_PROGRESS && completion > 0 && completion <= now; }
        boolean pending(long now) { return state == SummaryState.IN_PROGRESS && completion > now; }
        String status(long now)
        {
            if (!estimated && state == SummaryState.COMPLETED) return "Ready · observed";
            if (due(now)) return "Due to check · estimated";
            if (pending(now)) return "In " + countdown(completion - now) + (estimated ? " · estimated" : "");
            return state == SummaryState.EMPTY ? "Empty" : "Not observed";
        }
    }
    static final class Reading
    {
        final boolean active, canEnable; final String message; final List<Harvest> harvests; final Harvest contract;
        Reading(boolean active, boolean canEnable, String message, List<Harvest> harvests)
        { this(active, canEnable, message, harvests, null); }
        Reading(boolean active, boolean canEnable, String message, List<Harvest> harvests, Harvest contract)
        {
            this.active = active; this.canEnable = canEnable; this.message = message;
            this.harvests = Collections.unmodifiableList(new ArrayList<>(harvests)); this.contract = contract;
        }
        static Reading empty(String message) { return new Reading(false, false, message, Collections.emptyList()); }
        static Reading disabled(boolean available)
        { return empty(available ? "Enable Time Tracking in RuneLite's plugin settings." : "Time Tracking is unavailable."); }
        int dueCount(long now) { return (int) harvests.stream().filter(harvest -> harvest.due(now)).count(); }
        Harvest next(long now) { return harvests.stream().filter(harvest -> harvest.pending(now)).min(Comparator.comparingLong(harvest -> harvest.completion)).orElse(null); }
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
