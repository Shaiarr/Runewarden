/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemLayer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/** Contextual floor items. Native item instances keep distinct ownership and expiry deadlines. */
@Singleton
final class GroundLootTool
{
    static final String GROUP = "runewardengroundloot", KEY = "preferencesV1";
    static final int MAX_ITEMS = 4096, MAX_ROWS = 160, MAX_TARGETS = 128;
    private final Client client;
    private final ConfigManager config;
    private final ItemManager items;
    private final Gson gson;
    private final RunewardenDataStore store;
    private final GroundLootOverlay overlay;
    private final GroundLootBeams beams;
    private final Map<TileItem, Observed> observed = new IdentityHashMap<>();
    private final Map<Integer, ItemFacts> definitions = new LinkedHashMap<Integer, ItemFacts>()
    { @Override protected boolean removeEldestEntry(Map.Entry<Integer, ItemFacts> entry) { return size() > 512; } };
    private volatile Preferences preferences = new Preferences();
    private volatile Reading reading = Reading.EMPTY;
    private volatile boolean started;
    private volatile Supplier<Set<Integer>> targetItems = Collections::emptySet;
    private volatile IntConsumer openItemAction;
    private volatile String profile;
    private volatile long epoch;
    private WorldView seededView;
    private Scene seededScene;
    private int seededX, seededY, seededPlane;
    private boolean seeded, overflowing;
    private JPanel panel, rows, targets;
    private GuideText summary, feedback;
    private Timer timer;
    private boolean refreshing;
    private int targetPage;
    private JCheckBox enabled, guideTargets, manualTargets, labels, tiles, timers, ownOnly;
    private JSpinner floor;
    private JCheckBox beamEnabled,guideBeamChoice,manualBeamChoice,radialTimers;
    private JComboBox<GroundLootTier> minimumBeamTier,targetBeamTier;
    private JComboBox<GroundLootBeams.Style> beamStyle;
    private JSpinner whiteFloor,greenFloor,blueFloor,purpleFloor,goldFloor,beamLimit;

    @Inject GroundLootTool(Client client, ConfigManager config, ItemManager items, Gson gson, RunewardenDataStore store, ClientThread thread)
    {
        this.client = client; this.config = config; this.items = items; this.gson = gson;
        this.store = store;
        beams = new GroundLootBeams(client,thread);
        overlay = new GroundLootOverlay(client, this); reloadPreferences();
    }
    GroundLootTool(Client client, ConfigManager config, ItemManager items, Gson gson, RunewardenDataStore store)
    { this(client,config,items,gson,store,null); }
    GroundLootTool(Client client, ConfigManager config, ItemManager items, Gson gson)
    { this(client, config, items, gson, new RunewardenDataStore(config, gson),null); }

    void start()
    {
        started = true; clear(); reloadPreferences();
        SwingUtilities.invokeLater(() -> {
            if (!started) return;
            if (timer != null) timer.stop();
            timer = new Timer(1000, event -> { if (panel != null && panel.isShowing()) refreshPanel(); });
            timer.start(); refreshPanel();
        });
    }
    void stop()
    {
        started = false; clear();
        SwingUtilities.invokeLater(() -> { if (timer != null) timer.stop(); timer = null; refreshPanel(); });
    }
    void clear()
    {
        profile = null; epoch++; clearScene(); definitions.clear();
    }
    void clearScene()
    {
        observed.clear(); reading = Reading.EMPTY; beams.clear(); seeded = false; seededScene = null; seededView = null; overflowing = false;
    }
    GroundLootOverlay overlay() { return overlay; }
    Reading reading() { Reading value=reading;return value.profile!=null&&!value.profile.equals(config.getRSProfileKey())?Reading.EMPTY:value; }
    int activeBeams() { return beams.activeCount(); }
    Preferences preferences() { return preferences; }
    boolean started() { return started; }
    void setOpenItemAction(IntConsumer action) { openItemAction = action; }
    void setTargetItems(Supplier<Set<Integer>> supplier) { targetItems = supplier == null ? Collections::emptySet : supplier; }

    private boolean currentAccount()
    {
        if (!started) return false;
        if (!preferences.enabled)
        { if (profile != null || seeded || !observed.isEmpty()) clear(); return false; }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { clear(); return false; }
        String current = config.getRSProfileKey();
        if (current == null || current.isEmpty()) { clear(); return false; }
        if (!current.equals(profile)) { clear(); profile = current; }
        return true;
    }

    void onGameTick()
    {
        if (!currentAccount()) return;
        ensureSeeded();
        Preferences settings=preferences;
        Set<Integer> guide = new LinkedHashSet<>();
        if (preferences.guideTargets)
        {
            Set<Integer> supplied = targetItems.get();
            if (supplied != null) for (Integer id : supplied)
            { if (id != null && id > 0) guide.add(id); if (guide.size() >= 1024) break; }
        }
        int tick = client.getTickCount();
        boolean ironman = client.getVarbitValue(VarbitID.IRONMAN) != 0;
        List<Entry> next = new ArrayList<>(), beamEntries = new ArrayList<>();
        observed.values().removeIf(item -> item.quantity <= 0 || item.item.getDespawnTime() > 0 && item.item.getDespawnTime() <= tick);
        for (Observed item : observed.values())
        {
            int ownership = item.item.getOwnership();
            if (!takeable(ownership, ironman) || preferences.ownOnly && ownership != TileItem.OWNERSHIP_SELF && ownership != TileItem.OWNERSHIP_GROUP) continue;
            boolean required = guide.contains(item.facts.id) || guide.contains(item.facts.canonicalId);
            boolean manual = preferences.manualTargets && (preferences.targets.containsKey(item.facts.id) || preferences.targets.containsKey(item.facts.canonicalId));
            long value = multiply(item.facts.price, item.quantity);
            boolean display=required||manual||item.facts.price>0&&value>=settings.valueFloor;
            boolean beam=settings.beams&&(required&&settings.guideBeams||manual&&settings.manualBeams
                ||item.facts.price>0&&value>=settings.threshold(settings.minimumBeamTier));
            if(!display&&!beam)continue;
            Entry entry=new Entry(item,ownership,required,manual,value);
            if(display)next.add(entry);if(beam)beamEntries.add(entry);
        }
        next.sort(Comparator.comparing((Entry item) -> !item.guideTarget)
            .thenComparing(item -> !item.manualTarget)
            .thenComparingInt(item -> item.despawnTick > tick ? item.despawnTick : Integer.MAX_VALUE)
            .thenComparing(Comparator.comparingLong((Entry item) -> item.value).reversed())
            .thenComparingInt(item -> item.id));
        int total = next.size();
        if (next.size() > MAX_ROWS) next = new ArrayList<>(next.subList(0, MAX_ROWS));
        List<GroundLootBeams.Candidate> selectedBeams=GroundLootBeams.select(beamEntries,settings);
        beams.sync(selectedBeams,settings,tick);
        reading = new Reading(next, total, observed.size(), overflowing, tick, epoch, profile,selectedBeams.size(),beams.activeCount());
    }

    /** One snapshot per native scene/base/plane, including enabling an already logged-in client. */
    private void ensureSeeded()
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null || view.getScene() == null) { clearScene(); return; }
        Scene scene = view.getScene(); int plane = view.getPlane();
        if (seeded && view == seededView && scene == seededScene && view.getBaseX() == seededX && view.getBaseY() == seededY && plane == seededPlane) return;
        observed.clear(); reading = Reading.EMPTY; beams.clear(); overflowing = false;
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) { seeded = false; return; }
        seeded = true; seededView = view; seededScene = scene; seededX = view.getBaseX(); seededY = view.getBaseY(); seededPlane = plane;
        for (Tile[] row : tiles[plane])
        {
            if (row == null) continue;
            for (Tile tile : row)
            {
                if (tile == null || tile.getGroundItems() == null) continue;
                for (TileItem item : tile.getGroundItems()) observe(tile, item, item == null ? 0 : item.getQuantity());
            }
        }
    }

    void onItemSpawned(ItemSpawned event)
    {
        if (event != null && currentAccount()) observe(event.getTile(), event.getItem(), event.getItem() == null ? 0 : event.getItem().getQuantity());
    }
    void onItemDespawned(ItemDespawned event)
    { if (event != null) observed.remove(event.getItem()); }
    void onItemQuantityChanged(ItemQuantityChanged event)
    {
        if (event == null || !currentAccount()) return;
        if (event.getNewQuantity() <= 0) { observed.remove(event.getItem()); return; }
        Observed item = observed.get(event.getItem());
        if (item == null) observe(event.getTile(), event.getItem(), event.getNewQuantity());
        else item.quantity = event.getNewQuantity();
    }
    void onGameStateChanged(GameStateChanged event)
    {
        if (event == null) return;
        if (event.getGameState() == GameState.LOADING) clearScene();
        else if (event.getGameState() != GameState.LOGGED_IN) clear();
    }
    void onWorldViewUnloaded(WorldViewUnloaded event)
    {
        if (event == null) return;
        if (event.getWorldView() == seededView) clearScene();
        else
        {
            observed.values().removeIf(item -> item.view == event.getWorldView());
            reading = Reading.EMPTY; beams.clear();
        }
    }
    void onConfigChanged(ConfigChanged event)
    { if (event != null && GROUP.equals(event.getGroup())) reloadPreferences(); }

    private void observe(Tile tile, TileItem item, int quantity)
    {
        if (tile == null || item == null || item.getId() <= 0 || quantity <= 0) return;
        Observed previous = observed.get(item);
        if (previous != null) { previous.quantity = quantity; return; }
        if (observed.size() >= MAX_ITEMS) { overflowing = true; return; }
        ItemLayer layer = tile.getItemLayer();
        WorldView view = layer == null ? client.getTopLevelWorldView() : layer.getWorldView();
        if (view == null || tile.getLocalLocation() == null || tile.getWorldLocation() == null) return;
        ItemFacts facts = definitions.get(item.getId());
        if (facts == null)
        {
            ItemComposition definition = items.getItemComposition(item.getId());
            String name = definition == null || definition.getName() == null ? "Item " + item.getId() : clean(definition.getName());
            int canonical = items.canonicalize(item.getId()); if (canonical <= 0) canonical = item.getId();
            facts = new ItemFacts(item.getId(), canonical, name, item.getId() == 995 ? 1 : Math.max(0, items.getItemPrice(item.getId())));
            definitions.put(item.getId(), facts);
        }
        observed.put(item, new Observed(item, view, tile.getWorldLocation(), tile.getLocalLocation(), facts, quantity, client.getTickCount()));
    }

    static boolean takeable(int ownership, boolean ironman)
    {
        return ownership == TileItem.OWNERSHIP_SELF || ownership == TileItem.OWNERSHIP_GROUP || ownership == TileItem.OWNERSHIP_NONE
            || ownership == TileItem.OWNERSHIP_OTHER && !ironman;
    }
    static long multiply(long price, int quantity)
    { return price <= 0 || quantity <= 0 ? 0 : price > Long.MAX_VALUE / quantity ? Long.MAX_VALUE : price * quantity; }
    static String countdown(int ticks)
    {
        if (ticks <= 0) return "";
        long seconds = ((long) ticks * 3 + 4) / 5;
        return seconds >= 60 ? seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60) : seconds + "s";
    }
    static String amount(long value)
    {
        if (value >= 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.1fb", value / 1_000_000_000d);
        if (value >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fm", value / 1_000_000d);
        if (value >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", value / 1000d);
        return Long.toString(value);
    }
    private static String clean(String text)
    {
        String plain = Text.removeTags(text).replace('\n', ' ').replace('\r', ' ').trim();
        return plain.substring(0, Math.min(100, plain.length()));
    }

    void reloadPreferences()
    {
        Preferences next = store.read(GROUP, null, KEY, 1, Preferences.class, GroundLootTool::validPreferences, Preferences::new);
        preferences = checked(next);
        SwingUtilities.invokeLater(this::refreshPanel);
    }
    private synchronized boolean update(Consumer<Preferences> edit)
    {
        Preferences next = preferences.copy(); edit.accept(next);
        if(!validBeams(next)){if(feedback!=null)feedback.setText("Use increasing GP thresholds from white to gold.");refreshPanel();return false;}
        next = checked(next);
        if (!store.write(GROUP, null, KEY, 1, next))
        { if (feedback != null) feedback.setText("Saved settings need recovery."); refreshPanel(); return false; }
        preferences = next;if(feedback!=null)feedback.setText(""); refreshPanel(); return true;
    }
    private static boolean validPreferences(Preferences p)
    {
        if (p == null || p.version != 1 || p.valueFloor < 0 || p.valueFloor > 1_000_000_000_000L || p.targets == null || p.targets.size() > MAX_TARGETS || !validBeams(p)) return false;
        for (Map.Entry<Integer, String> target : p.targets.entrySet())
            if (target.getKey() == null || target.getKey() <= 0 || target.getKey() > 1_000_000 || target.getValue() == null || target.getValue().length() > 100) return false;
        return true;
    }
    private static boolean validBeams(Preferences p)
    {
        return p!=null&&p.minimumBeamTier!=null&&p.targetBeamTier!=null&&p.beamStyle!=null&&p.beamLimit>=1&&p.beamLimit<=GroundLootBeams.MAX_BEAMS
            &&p.whiteFloor>=0&&p.whiteFloor<p.greenFloor&&p.greenFloor<p.blueFloor&&p.blueFloor<p.purpleFloor&&p.purpleFloor<p.goldFloor&&p.goldFloor<=1_000_000_000_000L;
    }
    private static Preferences checked(Preferences candidate)
    {
        Preferences result = candidate == null || candidate.version != 1 ? new Preferences() : candidate.copy();
        result.valueFloor = Math.max(0, Math.min(1_000_000_000_000L, result.valueFloor));
        Map<Integer, String> valid = new LinkedHashMap<>();
        if (result.targets != null) for (Map.Entry<Integer, String> target : result.targets.entrySet())
        {
            if (target.getKey() != null && target.getKey() > 0 && target.getKey() <= 1_000_000)
                valid.put(target.getKey(), target.getValue() == null ? "Item " + target.getKey() : clean(target.getValue()));
            if (valid.size() >= MAX_TARGETS) break;
        }
        result.targets = Collections.unmodifiableMap(valid); return result;
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 8, 0));
        panel.add(SidebarUi.heading("GROUND LOOT"));
        enabled = choice("Show ground loot", "groundLootEnabled", p -> p.enabled = enabled.isSelected());
        guideTargets = choice("Selected guide items", "groundLootGuideTargets", p -> p.guideTargets = guideTargets.isSelected());
        manualTargets = choice("My target items", "groundLootManualTargets", p -> p.manualTargets = manualTargets.isSelected());
        ownOnly = choice("Only your / group drops", "groundLootOwnOnly", p -> p.ownOnly = ownOnly.isSelected());
        floor = new JSpinner(new SpinnerNumberModel(preferences.valueFloor, 0L, 1_000_000_000_000L, 1000L));
        floor.setName("groundLootPriceFloor"); floor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.add(SidebarUi.field("Other loot · minimum GP", floor));
        floor.addChangeListener(event -> { if (!refreshing) update(p -> p.valueFloor = ((Number) floor.getValue()).longValue()); });
        JPanel appearance = SidebarUi.column();
        labels = choice(appearance, "Item labels", "groundLootLabels", p -> p.labels = labels.isSelected());
        tiles = choice(appearance, "Target tile highlights", "groundLootTiles", p -> p.tiles = tiles.isSelected());
        timers = choice(appearance, "Countdown text", "groundLootTimers", p -> p.timers = timers.isSelected());
        radialTimers = choice(appearance,"Radial loot timers","groundLootRadialTimers",p->p.radialTimers=radialTimers.isSelected());
        panel.add(SidebarUi.disclosure("Display options", appearance));
        JPanel beamOptions=SidebarUi.column();
        beamEnabled=choice(beamOptions,"Native loot beams","groundLootBeams",p->p.beams=beamEnabled.isSelected());
        guideBeamChoice=choice(beamOptions,"Guide item beams","groundLootGuideBeams",p->p.guideBeams=guideBeamChoice.isSelected());
        manualBeamChoice=choice(beamOptions,"My target beams","groundLootManualBeams",p->p.manualBeams=manualBeamChoice.isSelected());
        minimumBeamTier=new JComboBox<>(GroundLootTier.values());minimumBeamTier.setName("groundLootMinimumBeamTier");beamOptions.add(SidebarUi.field("Minimum value tier",minimumBeamTier));
        minimumBeamTier.addActionListener(event->{if(!refreshing)update(p->p.minimumBeamTier=(GroundLootTier)minimumBeamTier.getSelectedItem());});
        targetBeamTier=new JComboBox<>(GroundLootTier.values());targetBeamTier.setName("groundLootTargetBeamTier");beamOptions.add(SidebarUi.field("Target colour",targetBeamTier));
        targetBeamTier.addActionListener(event->{if(!refreshing)update(p->p.targetBeamTier=(GroundLootTier)targetBeamTier.getSelectedItem());});
        beamStyle=new JComboBox<>(GroundLootBeams.Style.values());beamStyle.setName("groundLootBeamStyle");beamOptions.add(SidebarUi.field("Beam style",beamStyle));
        beamStyle.addActionListener(event->{if(!refreshing)update(p->p.beamStyle=(GroundLootBeams.Style)beamStyle.getSelectedItem());});
        whiteFloor=threshold(beamOptions,"White · GP","groundLootWhiteFloor",preferences.whiteFloor,p->p.whiteFloor=((Number)whiteFloor.getValue()).longValue());
        greenFloor=threshold(beamOptions,"Green · GP","groundLootGreenFloor",preferences.greenFloor,p->p.greenFloor=((Number)greenFloor.getValue()).longValue());
        blueFloor=threshold(beamOptions,"Blue · GP","groundLootBlueFloor",preferences.blueFloor,p->p.blueFloor=((Number)blueFloor.getValue()).longValue());
        purpleFloor=threshold(beamOptions,"Purple · GP","groundLootPurpleFloor",preferences.purpleFloor,p->p.purpleFloor=((Number)purpleFloor.getValue()).longValue());
        goldFloor=threshold(beamOptions,"Gold · GP","groundLootGoldFloor",preferences.goldFloor,p->p.goldFloor=((Number)goldFloor.getValue()).longValue());
        beamLimit=new JSpinner(new SpinnerNumberModel(preferences.beamLimit,1,GroundLootBeams.MAX_BEAMS,1));beamLimit.setName("groundLootBeamLimit");beamOptions.add(SidebarUi.field("Maximum beams",beamLimit));
        beamLimit.addChangeListener(event->{if(!refreshing)update(p->p.beamLimit=((Number)beamLimit.getValue()).intValue());});
        panel.add(SidebarUi.disclosure("Loot beams",beamOptions));
        JPanel add = SidebarUi.section("MY TARGETS"); panel.add(add);
        JTextField input = new JTextField(); input.setName("groundLootTargetInput"); add.add(SidebarUi.field("Item name or ID", input));
        Runnable addTarget = () -> {
            String text = input.getText().trim(); Set<Integer> ids = new LinkedHashSet<>();
            try { int id = Integer.parseInt(text); if (id > 0 && id <= 1_000_000) ids.add(id); } catch (NumberFormatException ignored) { ids.addAll(GuideItemResolver.resolveName(text)); }
            if (ids.isEmpty()) { feedback.setText("No matching item"); return; }
            if (preferences.targets.size() >= MAX_TARGETS) { feedback.setText("Target list is full"); return; }
            if (update(p -> { for (int id : ids) { if (p.targets.size() >= MAX_TARGETS) break; p.targets.put(id, text.matches("[0-9]+") ? "Item " + id : text); } }))
            { input.setText(""); feedback.setText(""); }
        };
        add.add(SidebarUi.button("Add target", addTarget)); input.addActionListener(event -> addTarget.run());
        feedback = SidebarUi.label(""); add.add(feedback); targets = SidebarUi.column(); add.add(targets);
        JPanel nearby = SidebarUi.section("NEARBY LOOT"); panel.add(nearby);
        summary = SidebarUi.label("Log in to see nearby loot"); nearby.add(summary); rows = SidebarUi.column(); nearby.add(rows);
        refreshPanel(); return panel;
    }
    private JSpinner threshold(JPanel parent,String title,String name,long value,Consumer<Preferences> action)
    {
        JSpinner spinner=new JSpinner(new SpinnerNumberModel(value,0L,1_000_000_000_000L,1_000L));spinner.setName(name);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));parent.add(SidebarUi.field(title,spinner));
        spinner.addChangeListener(event->{if(!refreshing)update(action);});return spinner;
    }
    private JCheckBox choice(String text, String name, Consumer<Preferences> action) { return choice(panel, text, name, action); }
    private JCheckBox choice(JPanel parent, String text, String name, Consumer<Preferences> action)
    {
        JCheckBox check = new JCheckBox(text); check.setName(name); check.setOpaque(false); check.setForeground(SidebarUi.TEXT); check.setFont(GuideText.FONT);
        check.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); parent.add(check);
        check.addActionListener(event -> { if (!refreshing) update(action); }); return check;
    }
    private void refreshPanel()
    {
        if (panel == null || refreshing) return;
        refreshing = true;
        try
        {
            Preferences p = preferences; enabled.setSelected(p.enabled); guideTargets.setSelected(p.guideTargets); manualTargets.setSelected(p.manualTargets);
            ownOnly.setSelected(p.ownOnly); labels.setSelected(p.labels); tiles.setSelected(p.tiles); timers.setSelected(p.timers); radialTimers.setSelected(p.radialTimers); floor.setValue(p.valueFloor);
            beamEnabled.setSelected(p.beams);guideBeamChoice.setSelected(p.guideBeams);manualBeamChoice.setSelected(p.manualBeams);minimumBeamTier.setSelectedItem(p.minimumBeamTier);targetBeamTier.setSelectedItem(p.targetBeamTier);beamStyle.setSelectedItem(p.beamStyle);
            whiteFloor.setValue(p.whiteFloor);greenFloor.setValue(p.greenFloor);blueFloor.setValue(p.blueFloor);purpleFloor.setValue(p.purpleFloor);goldFloor.setValue(p.goldFloor);beamLimit.setValue(p.beamLimit);
            targets.removeAll();
            int count = 0; targetPage = Math.min(targetPage, Math.max(0, (p.targets.size() - 1) / 8));
            for (Map.Entry<Integer, String> target : p.targets.entrySet())
            {
                if (count++ < targetPage * 8) continue;
                if (count > (targetPage + 1) * 8) break;
                JPanel row = SidebarUi.column(); row.add(SidebarUi.label(target.getValue()));
                row.add(SidebarUi.button("Remove", () -> update(next -> next.targets.remove(target.getKey())))); targets.add(row);
            }
            if (p.targets.size() > 8)
            {
                javax.swing.JButton previous = SidebarUi.button("Previous", () -> { targetPage--; refreshPanel(); });
                javax.swing.JButton next = SidebarUi.button("Next", () -> { targetPage++; refreshPanel(); });
                previous.setEnabled(targetPage > 0); next.setEnabled((targetPage + 1) * 8 < p.targets.size());
                targets.add(SidebarUi.row(previous, next));
                targets.add(SidebarUi.label("Page " + (targetPage + 1) + " / " + ((p.targets.size() + 7) / 8)));
            }
            Reading snapshot = reading();
            summary.setText(!p.enabled ? "Ground loot hidden" : !started || profile == null ? "Log in to see nearby loot"
                : snapshot.total == 0 ? "No matching loot nearby" : snapshot.total + " matching stacks" + (snapshot.overflowing ? " · crowded scene" : ""));
            if(snapshot.activeBeams>0)summary.setText(snapshot.total==0?snapshot.activeBeams+" loot beams":summary.getText()+" · "+snapshot.activeBeams+" beams");
            rows.removeAll(); int shown = 0;
            for (Entry item : snapshot.entries)
            {
                if (++shown > 8) break;
                JPanel card = SidebarUi.section(null);
                GuideText name = SidebarUi.label(item.name + (item.quantity > 1 ? " × " + amount(item.quantity) : ""));
                GroundLootTier tier=GroundLootTier.value(item.value,p); name.setForeground(item.target() ? p.targetBeamTier.color : tier==null?SidebarUi.TEXT:tier.color); card.add(name);
                String context = item.guideTarget ? "Guide supply" : item.manualTarget ? "Your target" : amount(item.value) + " GP";
                String remaining = item.despawnTick > snapshot.tick ? " · " + countdown(item.despawnTick - snapshot.tick) : "";
                card.add(SidebarUi.label(context + " · " + item.ownershipLabel() + remaining));
                long expectedEpoch = snapshot.epoch;
                card.add(SidebarUi.button("Item details", () -> {
                    IntConsumer action = openItemAction;
                    if (started && expectedEpoch == epoch && snapshot.profile != null && snapshot.profile.equals(config.getRSProfileKey()) && action != null)
                        action.accept(item.canonicalId);
                }));
                rows.add(card);
            }
            if (snapshot.total > 8) rows.add(SidebarUi.label("+" + (snapshot.total - 8) + " more nearby"));
            panel.revalidate(); panel.repaint();
        }
        finally { refreshing = false; }
    }

    static final class Preferences
    {
        int version = 1;
        boolean enabled = true, guideTargets = true, manualTargets = true, labels = true, tiles = true, timers = true, ownOnly;
        long valueFloor = 25_000;
        boolean beams=true,guideBeams=true,manualBeams=true,radialTimers=true;
        GroundLootTier minimumBeamTier=GroundLootTier.WHITE,targetBeamTier=GroundLootTier.GOLD;
        GroundLootBeams.Style beamStyle=GroundLootBeams.Style.MODERN;
        int beamLimit=32;
        long whiteFloor=25_000,greenFloor=100_000,blueFloor=1_000_000,purpleFloor=10_000_000,goldFloor=100_000_000;
        long threshold(GroundLootTier tier)
        {switch(tier){case WHITE:return whiteFloor;case GREEN:return greenFloor;case BLUE:return blueFloor;case PURPLE:return purpleFloor;default:return goldFloor;}}
        Map<Integer, String> targets = new LinkedHashMap<>();
        Preferences copy()
        {
            Preferences result = new Preferences(); result.version = version; result.enabled = enabled; result.guideTargets = guideTargets; result.manualTargets = manualTargets;
            result.labels = labels; result.tiles = tiles; result.timers = timers; result.ownOnly = ownOnly; result.valueFloor = valueFloor;
            result.beams=beams;result.guideBeams=guideBeams;result.manualBeams=manualBeams;result.radialTimers=radialTimers;result.minimumBeamTier=minimumBeamTier;result.targetBeamTier=targetBeamTier;result.beamStyle=beamStyle;result.beamLimit=beamLimit;
            result.whiteFloor=whiteFloor;result.greenFloor=greenFloor;result.blueFloor=blueFloor;result.purpleFloor=purpleFloor;result.goldFloor=goldFloor;
            result.targets = targets == null ? null : new LinkedHashMap<>(targets); return result;
        }
    }
    private static final class ItemFacts
    {
        final int id, canonicalId; final String name; final long price;
        ItemFacts(int id, int canonicalId, String name, long price) { this.id = id; this.canonicalId = canonicalId; this.name = name; this.price = price; }
    }
    private static final class Observed
    {
        final TileItem item; final WorldView view; final WorldPoint world; final LocalPoint local; final ItemFacts facts; final int observedTick; int quantity;
        Observed(TileItem item, WorldView view, WorldPoint world, LocalPoint local, ItemFacts facts, int quantity, int tick)
        { this.item = item; this.view = view; this.world = world; this.local = local; this.facts = facts; this.quantity = quantity; observedTick=tick; }
    }
    static final class Entry
    {
        final int id, canonicalId, quantity, ownership, despawnTick, visibleTick, observedTick;
        final String name; final long value; final boolean guideTarget, manualTarget, privateItem;
        final WorldPoint world; final LocalPoint local; final WorldView view;
        Entry(Observed item, int ownership, boolean guideTarget, boolean manualTarget, long value)
        {
            id = item.facts.id; canonicalId = item.facts.canonicalId; name = item.facts.name; quantity = item.quantity; this.ownership = ownership;
            this.guideTarget = guideTarget; this.manualTarget = manualTarget; this.value = value;
            observedTick=item.observedTick; despawnTick = item.item.getDespawnTime(); visibleTick = item.item.getVisibleTime(); privateItem = item.item.isPrivate();
            world = item.world; local = item.local; view = item.view;
        }
        boolean target() { return guideTarget || manualTarget; }
        String ownershipLabel()
        { return ownership == TileItem.OWNERSHIP_SELF ? "Yours" : ownership == TileItem.OWNERSHIP_GROUP ? "Group" : ownership == TileItem.OWNERSHIP_NONE ? "Public" : "Other"; }
    }
    static final class Reading
    {
        static final Reading EMPTY = new Reading(Collections.emptyList(), 0, 0, false, 0, -1, null);
        final List<Entry> entries; final int total, tracked, tick, beamCandidates,activeBeams; final boolean overflowing; final long epoch; final String profile;
        Reading(List<Entry> entries, int total, int tracked, boolean overflowing, int tick, long epoch, String profile)
        {this(entries,total,tracked,overflowing,tick,epoch,profile,0,0);}
        Reading(List<Entry> entries,int total,int tracked,boolean overflowing,int tick,long epoch,String profile,int candidates,int active)
        { this.entries = Collections.unmodifiableList(new ArrayList<>(entries)); this.total = total; this.tracked = tracked; this.overflowing = overflowing; this.tick = tick; this.epoch = epoch; this.profile = profile;beamCandidates=candidates;activeBeams=active; }
    }
}
