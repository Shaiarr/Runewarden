/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;

/** Read-only native music collection with sourced unlock coaching and account-isolated baselines. */
@Singleton
public final class MusicUnlockTool
{
    static final int PAGE_SIZE = 8;
    private static final int[] MUSIC_BANKS = {VarPlayerID.MUSICMULTI_1, VarPlayerID.MUSICMULTI_2, VarPlayerID.MUSICMULTI_3,
        VarPlayerID.MUSICMULTI_4, VarPlayerID.MUSICMULTI_5, VarPlayerID.MUSICMULTI_6, VarPlayerID.MUSICMULTI_7,
        VarPlayerID.MUSICMULTI_8, VarPlayerID.MUSICMULTI_9, VarPlayerID.MUSICMULTI_10, VarPlayerID.MUSICMULTI_11,
        VarPlayerID.MUSICMULTI_12, VarPlayerID.MUSICMULTI_13, VarPlayerID.MUSICMULTI_14, VarPlayerID.MUSICMULTI_15,
        VarPlayerID.MUSICMULTI_16, VarPlayerID.MUSICMULTI_17, VarPlayerID.MUSICMULTI_18, VarPlayerID.MUSICMULTI_19,
        VarPlayerID.MUSICMULTI_20, VarPlayerID.MUSICMULTI_21, VarPlayerID.MUSICMULTI_22, VarPlayerID.MUSICMULTI_23,
        VarPlayerID.MUSICMULTI_24, VarPlayerID.MUSICMULTI_25, VarPlayerID.MUSICMULTI_26, VarPlayerID.MUSICMULTI_27};
    private final Client client;
    private final ConfigManager config;
    private final MusicUnlockCatalog catalog;
    private volatile boolean started, visible, accessRequested;
    private volatile long epoch;
    private String profile;
    private int warmup, ticks;
    private int[] previousBanks;
    private long previousCatalogRevision = -1;
    private Map<Integer, Integer> states = Collections.emptyMap();
    private Map<String, QuestState> quests = Collections.emptyMap();
    private Map<Skill, Integer> levels = Collections.emptyMap();
    private Map<Integer, Integer> carried = Collections.emptyMap();
    private boolean accessObserved;
    private WorldPoint position, renderedPosition;
    private volatile Reading reading = Reading.empty();
    private Consumer<WorldPoint> routeAction = ignored -> { };
    private Consumer<GoalPlan> addGoal = ignored -> { };
    private Consumer<java.util.Set<Integer>> bankFocus = ignored -> { };
    public void setRouteAction(Consumer<WorldPoint> action) { routeAction = action == null ? ignored -> { } : action; }
    void setGoalAction(Consumer<GoalPlan> action) { addGoal = action == null ? ignored -> { } : action; }
    void setBankFocusAction(Consumer<java.util.Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    private Consumer<String> openGuide = id -> { };
    private Consumer<MusicUnlockCatalog.Track> unlockAction = track -> { };
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private JPanel panel, body;
    private GuideText collection;
    private JProgressBar progress;
    private JTextField search;
    private JComboBox<String> mode, region;
    private int page;
    private Integer selected;
    private Timer debounce;

    @Inject public MusicUnlockTool(Client client, ConfigManager config, MusicUnlockCatalog catalog)
    { this.client = client; this.config = config; this.catalog = catalog; }
    public void setOpenGuideAction(Consumer<String> action) { openGuide = action == null ? id -> { } : action; }
    public void setUnlockAction(Consumer<MusicUnlockCatalog.Track> action) { unlockAction = action == null ? track -> { } : action; }
    /** Immutable, profile-guarded states: 1 unlocked, 0 locked, -1 unknown. */
    public Map<Integer, Integer> observedUnlocks()
    { Reading value = reading; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value.states : Collections.emptyMap(); }
    Reading reading() { Reading value = reading; return value.profile != null && value.profile.equals(config.getRSProfileKey()) ? value : Reading.empty(); }
    List<MusicUnlockCatalog.Stop> nearbyStops()
    { accessRequested = true; Reading view = reading(); return catalog.nearby(view.tracks, view.states, view.quests, view.levels, view.carried, view.position, view.accessObserved); }
    public void openTrack(int id) { if (catalog.tracks().stream().anyMatch(track -> track.id == id)) SwingUtilities.invokeLater(() -> { selected = id; page = 0; if (panel != null) render(); }); }
    public void start() { started = true; clear(); }
    public void stop()
    { started = false; clear(); if (debounce != null) SwingUtilities.invokeLater(() -> debounce.stop()); }
    public void clear()
    {
        epoch++; profile = null; warmup = 5; ticks = 0; previousBanks = null; previousCatalogRevision = -1;
        states = Collections.emptyMap(); quests = Collections.emptyMap(); levels = Collections.emptyMap(); carried = Collections.emptyMap();
        accessObserved = false; accessRequested = false; position = renderedPosition = null; reading = Reading.empty(); requestRender();
    }

    /** The 27 small varp reads remain active for unlock celebrations even when the sidebar is closed. */
    public void onGameTick()
    {
        if (!started) return;
        String current = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || current == null || current.isEmpty())
        { if (profile != null) clear(); return; }
        if (!current.equals(profile)) { clear(); profile = current; }
        if (warmup-- > 0) return;
        boolean definitionsChanged = catalog.readNativeChunk(client);
        int[] banks = new int[MUSIC_BANKS.length];
        for (int i = 0; i < banks.length; i++) banks[i] = client.getVarpValue(MUSIC_BANKS[i]);
        boolean changed = definitionsChanged || previousBanks == null || !Arrays.equals(previousBanks, banks)
            || previousCatalogRevision != catalog.revision();
        if (changed)
        {
            Map<Integer, Integer> next = new LinkedHashMap<>();
            for (MusicUnlockCatalog.Track track : catalog.tracks())
            {
                int unlocked = MusicUnlockCatalog.unlocked(track, banks); next.put(track.id, unlocked);
                // A newly discovered definition and a login/reconnect baseline never celebrate old progress.
                if (unlocked == 1 && states.getOrDefault(track.id, -1) == 0) unlockAction.accept(track);
            }
            states = Collections.unmodifiableMap(next); previousBanks = banks; previousCatalogRevision = catalog.revision();
        }
        if ((visible || accessRequested) && ticks++ % (visible ? 10 : 20) == 0) { observeAccess(); accessRequested = false; changed = true; }
        WorldPoint nextPosition = !client.isInInstancedRegion() ? client.getLocalPlayer().getWorldLocation() : null;
        position = nextPosition;
        if (visible && (renderedPosition == null ? position != null : position == null || renderedPosition.distanceTo(position) >= 8))
        { renderedPosition = position; changed = true; }
        if (changed)
        {
            reading = new Reading(profile, epoch, catalog.tracks(), states, quests, levels, carried, position, accessObserved,
                catalog.nativeComplete(), catalog.indexedRows(), catalog.nativeRowCount());
            requestRender();
        }
    }

    private void observeAccess()
    {
        Map<String, QuestState> questStates = new HashMap<>();
        for (String key : catalog.neededQuests())
        {
            Quest quest = catalog.quest(key); if (quest == null) continue;
            try { questStates.put(key, quest.getState(client)); } catch (RuntimeException loading) { }
        }
        Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) skillLevels.put(skill, client.getRealSkillLevel(skill));
        Map<Integer, Integer> items = new HashMap<>();
        addItems(items, client.getItemContainer(InventoryID.INVENTORY)); addItems(items, client.getItemContainer(InventoryID.EQUIPMENT));
        quests = Collections.unmodifiableMap(questStates); levels = Collections.unmodifiableMap(skillLevels);
        carried = Collections.unmodifiableMap(items); accessObserved = true;
    }
    private static void addItems(Map<Integer, Integer> target, ItemContainer container)
    {
        if (container == null) return;
        for (Item item : container.getItems()) if (item != null && item.getId() > 0 && item.getQuantity() > 0)
            target.merge(item.getId(), item.getQuantity(), (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
    }

    public JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("music-unlocks");
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0)
            { visible = panel.isShowing(); if (visible) { ticks = 0; render(); } }
        });
        JPanel overview = SidebarUi.section("Music collection"); collection = SidebarUi.label(""); overview.add(collection);
        progress = new JProgressBar(0, 100); progress.setStringPainted(true); progress.setForeground(SidebarUi.GOLD);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26)); overview.add(progress); panel.add(overview);
        JPanel filters = SidebarUi.section(null);
        search = new JTextField(); style(search); search.setName("music-unlocks.search"); filters.add(SidebarUi.field("Find track or location", search));
        mode = new JComboBox<>(new String[]{"Missing tracks", "Nearby stops", "All tracks", "Unlocked tracks"}); style(mode); mode.setName("music-unlocks.mode");
        mode.addActionListener(event -> { page = 0; selected = null; render(); }); filters.add(mode);
        List<String> regions = new ArrayList<>(); regions.add("All regions");
        catalog.tracks().stream().map(track -> track.region).distinct().sorted().forEach(regions::add);
        region = new JComboBox<>(regions.toArray(new String[0])); style(region);
        region.addActionListener(event -> { page = 0; selected = null; render(); }); filters.add(region); panel.add(filters);
        debounce = new Timer(180, event -> { page = 0; selected = null; render(); }); debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { debounce.restart(); }
        });
        body = SidebarUi.column(); panel.add(body); render(); return panel;
    }

    private void requestRender()
    {
        if (!visible || !refreshQueued.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> { refreshQueued.set(false); if (visible) render(); });
    }
    private void render()
    {
        if (body == null) return;
        Reading view = reading; List<MusicUnlockCatalog.Track> all = view.profile == null ? catalog.tracks() : view.tracks;
        long unlocked = all.stream().filter(track -> view.states.getOrDefault(track.id, -1) == 1).count();
        long unknown = all.stream().filter(track -> view.states.getOrDefault(track.id, -1) < 0).count();
        collection.setText(unlocked + " / " + all.size() + " unlocked" + (unknown > 0 ? " · " + unknown + " unknown" : ""));
        progress.setValue(all.isEmpty() ? 0 : (int) (unlocked * 100 / all.size())); progress.setString(progress.getValue() + "%");
        body.removeAll();
        if (selected != null)
        {
            MusicUnlockCatalog.Track track = all.stream().filter(item -> item.id == selected).findFirst().orElse(null);
            if (track != null) { detail(track, view); finishRender(); return; } selected = null;
        }
        if (view.profile == null) body.add(SidebarUi.label("Log in to read track unlocks."));
        else if (!view.complete) body.add(SidebarUi.label("Reading music index · " + view.indexed + " / " + view.totalRows));
        String needle = search.getText().trim().toLowerCase(Locale.ROOT), selectedRegion = (String) region.getSelectedItem();
        if (mode.getSelectedIndex() == 1)
        {
            body.add(SidebarUi.label("Nearby unlocks · direct distance to entry"));
            List<MusicUnlockCatalog.Stop> nearby = catalog.nearby(all, view.states, view.quests, view.levels, view.carried, view.position, view.accessObserved);
            int shown = 0;
            for (MusicUnlockCatalog.Stop stop : nearby)
                if (matches(stop.track, needle, selectedRegion)) { row(stop.track, view, stop.distance + " tiles to entry"); shown++; }
            if (shown == 0) body.add(SidebarUi.label(view.accessObserved ? "No checked nearby stops in this filter." : "Access checks loading."));
        }
        else
        {
            List<MusicUnlockCatalog.Track> found = new ArrayList<>();
            for (MusicUnlockCatalog.Track track : all)
            {
                int state = view.states.getOrDefault(track.id, -1);
                if ((mode.getSelectedIndex() == 0 && state != 0) || (mode.getSelectedIndex() == 3 && state != 1)) continue;
                if (matches(track, needle, selectedRegion)) found.add(track);
            }
            int pages = Math.max(1, (found.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.max(0, Math.min(page, pages - 1));
            for (int i = page * PAGE_SIZE; i < Math.min(found.size(), (page + 1) * PAGE_SIZE); i++) row(found.get(i), view, null);
            if (found.isEmpty()) body.add(SidebarUi.label("No matching tracks."));
            if (pages > 1)
            {
                javax.swing.JButton previous = SidebarUi.button("Previous", () -> { page--; render(); }); previous.setEnabled(page > 0);
                javax.swing.JButton next = SidebarUi.button("Next", () -> { page++; render(); }); next.setEnabled(page + 1 < pages);
                body.add(SidebarUi.row(previous, next)); body.add(SidebarUi.label((page + 1) + " / " + pages));
            }
        }
        finishRender();
    }
    private static boolean matches(MusicUnlockCatalog.Track track, String needle, String region)
    { return track.search.contains(needle) && ("All regions".equals(region) || track.region.equals(region)); }
    private void row(MusicUnlockCatalog.Track track, Reading view, String distance)
    {
        JPanel card = SidebarUi.section(track.name);
        card.add(SidebarUi.label(status(track, view) + " · " + track.region + (distance == null ? "" : "\n" + distance)));
        card.add(SidebarUi.button("Unlock details", () -> { selected = track.id; render(); })); body.add(card);
    }
    private void detail(MusicUnlockCatalog.Track track, Reading view)
    {
        body.add(SidebarUi.button("‹ Tracks", () -> { selected = null; render(); }));
        JPanel card = SidebarUi.section(track.name); card.add(SidebarUi.label(status(track, view) + (track.holiday ? " · Holiday" : "")));
        card.add(SidebarUi.label(track.unlock)); if (!track.location.isEmpty()) card.add(SidebarUi.label("Location: " + track.location));
        card.add(SidebarUi.button("Wiki unlock source ↗", () -> LinkBrowser.browse(track.source)));
        if (track.id >= 0) card.add(SidebarUi.button("Track music goal", () -> withAccount(view, () -> addGoal.accept(GoalPlan.progress("music", Integer.toString(track.id), 1, track.name, Collections.emptyList())))));
        body.add(card);
        addGuide(track.quest, view);
        int shown = 0;
        for (MusicUnlockCatalog.Route route : track.routes)
        {
            shown++;
            JPanel access = SidebarUi.section(route.name == null ? "Source route" : route.name);
            MusicUnlockCatalog.Access checked = catalog.access(route, view.quests, view.levels, view.carried, view.accessObserved);
            access.add(SidebarUi.label(checked.blocked ? "Known requirements pending" : checked.unknown ? "Access not fully observed" : "Known requirements met"));
            for (String requirement : checked.details) access.add(SidebarUi.label(requirement));
            if (route.point() != null) access.add(SidebarUi.button("Route to unlock", () -> withAccount(view, () -> routeAction.accept(route.point()))));
            java.util.Set<Integer> supplies = new java.util.LinkedHashSet<>(); for (MusicUnlockCatalog.RequiredItem item : route.items) if (item.id > 0) supplies.add(item.id);
            if (!supplies.isEmpty()) access.add(SidebarUi.button("Find route supplies in bank", () -> withAccount(view, () -> bankFocus.accept(Collections.unmodifiableSet(supplies)))));
            if (route.notes != null && !route.notes.isEmpty()) access.add(SidebarUi.label(route.notes));
            body.add(access); addGuide(route.quest, view);
        }
    }
    private void withAccount(Reading expected, Runnable action)
    { if (started && expected.epoch == epoch && expected.profile != null && expected.profile.equals(config.getRSProfileKey())) action.run(); }
    private void addGuide(String quest, Reading view)
    {
        GuideCatalog.Entry guide = catalog.guide(quest); if (guide == null) return;
        JPanel questCard = SidebarUi.section("Quest Helper"); questCard.add(SidebarUi.label(guide.title));
        questCard.add(SidebarUi.button("Open Quest Helper", () -> {
            if (started && view.epoch == epoch && view.profile != null && view.profile.equals(config.getRSProfileKey())) openGuide.accept(guide.id);
        })); body.add(questCard);
    }
    private static String status(MusicUnlockCatalog.Track track, Reading view)
    { int value = view.states.getOrDefault(track.id, -1); return value == 1 ? "Unlocked" : value == 0 ? "Missing" : "Unknown"; }
    private void finishRender() { body.revalidate(); body.repaint(); panel.revalidate(); }
    private static void style(javax.swing.JComponent component)
    {
        component.setFont(GuideText.FONT); component.setForeground(SidebarUi.TEXT); component.setBackground(SidebarUi.CARD);
        component.setMinimumSize(new Dimension(0, 30)); component.setPreferredSize(new Dimension(180, 30));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        if (component instanceof JTextField) ((JTextField) component).setCaretColor(SidebarUi.GOLD);
    }

    static final class Reading
    {
        final String profile; final long epoch; final List<MusicUnlockCatalog.Track> tracks;
        final Map<Integer, Integer> states, carried; final Map<String, QuestState> quests; final Map<Skill, Integer> levels;
        final WorldPoint position; final boolean accessObserved, complete; final int indexed, totalRows;
        Reading(String profile, long epoch, List<MusicUnlockCatalog.Track> tracks, Map<Integer, Integer> states,
            Map<String, QuestState> quests, Map<Skill, Integer> levels, Map<Integer, Integer> carried, WorldPoint position,
            boolean accessObserved, boolean complete, int indexed, int totalRows)
        {
            this.profile = profile; this.epoch = epoch; this.tracks = tracks; this.states = states; this.quests = quests;
            this.levels = levels; this.carried = carried; this.position = position; this.accessObserved = accessObserved;
            this.complete = complete; this.indexed = indexed; this.totalRows = totalRows;
        }
        static Reading empty()
        { return new Reading(null, 0, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), null, false, false, 0, 0); }
    }
}
