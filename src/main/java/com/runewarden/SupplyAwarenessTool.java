/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Supported ID-encoded charges and observed ammo/runes. Hidden weapon charges remain unknown. */
@Singleton
final class SupplyAwarenessTool
{
    static final String KEY = "supplyPreferencesV1";
    private static final int MAX_TRACKED = 256, PAGE_SIZE = 8;
    private static final Set<String> KINDS = Set.of("ABYSSAL_BRACELET","BELLOWS","FRUIT_BASKET","FUNGICIDE_SPRAY",
        "GUTHIX_REST","IMPBOX","POTION","SACK","TELEPORT","WATERCAN","WATERSKIN");
    static final class Charge { int itemId, charges; String type; }
    static final class Reference { String runeLiteVersion, source, license; List<Charge> items; }
    static final class Preferences
    {
        int version = 1, ammo = 100, runes = 100, doses = 2, charges = 2;
        boolean notifications;
        Map<Integer,Integer> thresholds = new LinkedHashMap<>();
    }
    static final class Row
    {
        final int itemId, threshold;
        final String name, kind, unit, key;
        final long amount;
        final Set<Integer> variants;
        Row(int id, String name, String kind, String unit, long amount, int threshold)
        { this(id,name,kind,unit,amount,threshold,kind+":"+id,Collections.singleton(id)); }
        Row(int id, String name, String kind, String unit, long amount, int threshold, String key, Set<Integer> variants)
        {
            itemId = id; this.name = name; this.kind = kind; this.unit = unit; this.amount = amount;
            this.threshold = threshold; this.key = key; this.variants = Collections.unmodifiableSet(new LinkedHashSet<>(variants));
        }
        boolean low() { return amount <= threshold; }
        String label() { return String.format(Locale.ROOT,"%,d %s",amount,unit); }
    }
    private final Client client;
    private final ClientThread thread;
    private final ConfigManager config;
    private final RunewardenDataStore data;
    private final OwnershipLedger ownership;
    private final ItemManager items;
    private final Notifier notifier;
    private final Map<Integer,Charge> charges = new LinkedHashMap<>();
    private final Map<Integer,Integer> supported;
    private volatile Map<Integer,Long> totals = Map.of();
    private volatile List<Row> rows = List.of();
    private volatile Preferences preferences = new Preferences();
    private final Set<String> warned = new HashSet<>();
    private final Map<String,Row> tracked = new LinkedHashMap<>();
    private volatile String profile;
    private volatile long epoch;
    private volatile boolean started;
    private int ticks, page;
    private JPanel panel, body;
    private JSpinner ammoSetting, runeSetting, doseSetting, chargeSetting;
    private JCheckBox notificationsSetting;
    private long settingsEpoch = -1;
    private IntConsumer openItem = ignored -> { };

    @Inject SupplyAwarenessTool(Client client, ClientThread thread, ConfigManager config, RunewardenDataStore data,
        OwnershipLedger ownership, ItemManager items, Notifier notifier, Gson gson)
    {
        this.client = client; this.thread = thread; this.config = config; this.data = data;
        this.ownership = ownership; this.items = items; this.notifier = notifier;
        try (InputStreamReader in = new InputStreamReader(SupplyAwarenessTool.class.getResourceAsStream("/com/runewarden/item-charge-reference.json"),StandardCharsets.UTF_8))
        {
            Reference refs = gson.fromJson(in,Reference.class);
            if (refs == null || !"1.13.2".equals(refs.runeLiteVersion) || !"BSD-2-Clause".equals(refs.license)
                || refs.source == null || !refs.source.endsWith("/itemcharges/ItemWithCharge.java") || refs.items == null || refs.items.size() < 400)
                throw new IllegalArgumentException("Invalid charge source reference");
            for (Charge charge : refs.items)
                if (charge == null || charge.itemId < 1 || charge.itemId > 1_000_000 || charge.charges < 0 || charge.charges > 1_000_000
                    || !KINDS.contains(charge.type) || charges.put(charge.itemId,charge) != null)
                    throw new IllegalArgumentException("Invalid or duplicate charge item");
        }
        catch (Exception error) { throw new IllegalStateException("Item charge reference unavailable",error); }
        Map<Integer,Integer> exact = new LinkedHashMap<>(); charges.forEach((id,charge) -> exact.put(id,charge.charges));
        supported = Collections.unmodifiableMap(exact);
    }
    void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear()
    {
        profile = null; epoch++; totals = Map.of(); rows = List.of(); warned.clear(); tracked.clear();
        ticks = 0; page = 0; preferences = new Preferences(); refresh();
    }
    private boolean current() { return started && profile != null && profile.equals(config.getRSProfileKey()); }
    Map<Integer,Integer> supportedItemCharges() { return supported; }
    Map<Integer,Long> chargeTotals() { return current() ? totals : Map.of(); }
    List<Row> rows() { return current() ? rows : List.of(); }
    List<FieldCoach.Row> hudRows()
    {
        List<FieldCoach.Row> result = new ArrayList<>();
        for (Row row : rows()) if (row.low()) result.add(new FieldCoach.Row(row.name,row.label(),true));
        return Collections.unmodifiableList(result);
    }
    void onGameTick()
    {
        if (!started) return;
        OwnershipLedger.View state = ownership.snapshot();
        if (!state.active || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        { if (profile != null) clear(); return; }
        if (!state.profile.equals(profile))
        {
            clear(); profile = state.profile;
            preferences = data.read(RunewardenConfig.GROUP,profile,KEY,1,Preferences.class,SupplyAwarenessTool::valid,Preferences::new);
        }
        Preferences prefs = preferences;
        OwnershipLedger.Observation inventory = state.storages.get(OwnershipLedger.Storage.INVENTORY);
        OwnershipLedger.Observation equipment = state.storages.get(OwnershipLedger.Storage.EQUIPMENT);
        Map<Integer,Long> carried = new LinkedHashMap<>();
        if (inventory != null && inventory.currentSession) inventory.quantities.forEach((id,count) -> carried.merge(id,count,Long::sum));
        if (equipment != null && equipment.currentSession) equipment.quantities.forEach((id,count) -> carried.merge(id,count,Long::sum));
        Map<Integer,Long> nextTotals = new LinkedHashMap<>(); Map<String,Row> live = new LinkedHashMap<>();
        for (Map.Entry<Integer,Long> item : carried.entrySet())
        {
            Charge charge = charges.get(item.getKey()); if (charge == null) continue;
            long amount = (long)charge.charges * item.getValue(); nextTotals.put(charge.itemId,amount);
            String nativeName = name(charge.itemId), family = family(nativeName), unit = unit(charge.type);
            String key = charge.type + ":" + family.toLowerCase(Locale.ROOT);
            int threshold = prefs.thresholds.getOrDefault(charge.itemId,"doses".equals(unit) ? prefs.doses : prefs.charges);
            Row previous = live.get(key); Set<Integer> variants = new LinkedHashSet<>(); variants.add(charge.itemId);
            if (previous != null) variants.addAll(previous.variants);
            live.put(key,new Row(previous == null ? charge.itemId : previous.itemId,family,charge.type,unit,
                amount + (previous == null ? 0 : previous.amount),Math.max(threshold,previous == null ? 0 : previous.threshold),key,variants));
        }
        if (equipment != null && equipment.currentSession)
        {
            OwnershipLedger.SlotItem weapon = equipment.slots.get(EquipmentInventorySlot.WEAPON.getSlotIdx());
            OwnershipLedger.SlotItem ammo = equipment.slots.get(EquipmentInventorySlot.AMMO.getSlotIdx());
            // Matches RuneLite's public AmmoPlugin: stackable thrown weapon takes precedence; blessings are not ammo.
            OwnershipLedger.SlotItem selected = stackable(weapon) ? weapon : stackable(ammo) ? ammo : null;
            if (selected != null) addNative(live,selected.id,name(selected.id),"AMMUNITION","ammo",selected.quantity,prefs.thresholds.getOrDefault(selected.id,prefs.ammo));
        }
        OwnershipLedger.Observation quiver = state.storages.get(OwnershipLedger.Storage.QUIVER);
        if (state.quiverAvailable && quiver != null && quiver.currentSession)
            quiver.quantities.forEach((id,count) -> addNative(live,id,name(id),"QUIVER","ammo in quiver",count,prefs.thresholds.getOrDefault(id,prefs.ammo)));
        if (state.runePouchObserved)
            state.runePouch.forEach((id,count) -> addNative(live,id,name(id),"RUNE_POUCH","runes in pouch",count,prefs.thresholds.getOrDefault(id,prefs.runes)));
        List<String> renamed = new ArrayList<>();
        for (Row previous : tracked.values()) if (KINDS.contains(previous.kind) && !live.containsKey(previous.key))
            for (Row present : live.values()) if (present.kind.equals(previous.kind) && !Collections.disjoint(present.variants,previous.variants))
            { renamed.add(previous.key); if (warned.remove(previous.key)) warned.add(present.key); break; }
        for (String key : renamed) tracked.remove(key);
        boolean carriedKnown = inventory != null && inventory.currentSession && equipment != null && equipment.currentSession;
        // Previously seen consumables may reach zero. Never turn an unavailable container into a zero fact.
        if (carriedKnown) for (Row previous : tracked.values())
            if (KINDS.contains(previous.kind) && !live.containsKey(previous.key))
                live.put(previous.key,new Row(previous.itemId,previous.name,previous.kind,previous.unit,0,previous.threshold,previous.key,previous.variants));
        tracked.putAll(live); while (tracked.size() > MAX_TRACKED) tracked.remove(tracked.keySet().iterator().next());
        List<Row> view = new ArrayList<>(live.values());
        view.sort(Comparator.comparing((Row row) -> !row.low()).thenComparing(row -> row.name));
        totals = Collections.unmodifiableMap(nextTotals); rows = Collections.unmodifiableList(view);
        Set<String> stillLow = new HashSet<>(); Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean banking = bank != null && !bank.isHidden();
        for (Row row : view) if (row.low())
        {
            stillLow.add(row.key);
            if (prefs.notifications && !banking && ticks > 3 && warned.add(row.key) && notifier != null)
                notifier.notify("Runewarden · " + row.name + ": " + row.label());
        }
        warned.retainAll(stillLow); ticks++;
        if (ticks % 5 == 0 && panel != null && panel.isShowing()) refresh();
    }
    private void addNative(Map<String,Row> live, int id, String name, String kind, String unit, long count, int threshold)
    { Row row = new Row(id,name,kind,unit,count,threshold); live.put(row.key,row); }
    private boolean stackable(OwnershipLedger.SlotItem item)
    { if (item == null) return false; ItemComposition definition = items.getItemComposition(item.id); return definition != null && definition.isStackable(); }
    private String name(int id)
    { ItemComposition definition = items.getItemComposition(id); return definition == null || definition.getName() == null ? "Item " + id : definition.getName(); }
    static String family(String name) { return name.replaceFirst("\\s*\\([0-9]+\\)$","").trim(); }
    private static String unit(String kind)
    {
        switch (kind)
        {
            case "POTION": case "GUTHIX_REST": return "doses";
            case "WATERCAN": case "WATERSKIN": return "water";
            case "FRUIT_BASKET": case "SACK": return "items";
            case "BELLOWS": return "air";
            case "FUNGICIDE_SPRAY": return "sprays";
            case "IMPBOX": return "imps";
            case "TELEPORT": return "teleports";
            default: return "charges";
        }
    }
    static boolean valid(Preferences prefs)
    {
        if (prefs == null || prefs.version != 1 || prefs.ammo < 0 || prefs.ammo > 100_000 || prefs.runes < 0 || prefs.runes > 100_000
            || prefs.doses < 0 || prefs.doses > 100 || prefs.charges < 0 || prefs.charges > 100_000 || prefs.thresholds == null || prefs.thresholds.size() > 100) return false;
        for (Map.Entry<Integer,Integer> entry : prefs.thresholds.entrySet())
            if (entry.getKey() == null || entry.getKey() < 1 || entry.getKey() > 1_000_000 || entry.getValue() == null || entry.getValue() < 0 || entry.getValue() > 1_000_000) return false;
        return true;
    }
    void preferencesNow(Preferences next)
    {
        if (!current() || !valid(next)) return;
        Preferences copy = new Preferences(); copy.ammo = next.ammo; copy.runes = next.runes; copy.doses = next.doses;
        copy.charges = next.charges; copy.notifications = next.notifications; copy.thresholds = Collections.unmodifiableMap(new LinkedHashMap<>(next.thresholds));
        if (data.write(RunewardenConfig.GROUP,profile,KEY,1,copy)) { preferences = copy; warned.clear(); onGameTick(); refresh(); }
    }
    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.setName("supply-awareness"); body = SidebarUi.column(); panel.add(body);
        JPanel settings = SidebarUi.column();
        ammoSetting = new JSpinner(new SpinnerNumberModel(100,0,100_000,50)); runeSetting = new JSpinner(new SpinnerNumberModel(100,0,100_000,50));
        doseSetting = new JSpinner(new SpinnerNumberModel(2,0,100,1)); chargeSetting = new JSpinner(new SpinnerNumberModel(2,0,100_000,1));
        notificationsSetting = new JCheckBox("Supply notifications"); notificationsSetting.setOpaque(false); notificationsSetting.setForeground(SidebarUi.TEXT);
        settings.add(SidebarUi.field("Low ammunition",ammoSetting)); settings.add(SidebarUi.field("Low pouch runes",runeSetting));
        settings.add(SidebarUi.field("Low potion doses",doseSetting)); settings.add(SidebarUi.field("Low other uses",chargeSetting)); settings.add(notificationsSetting);
        settings.add(SidebarUi.button("Save thresholds",() -> {
            String owner = profile; long ticket = epoch; Preferences next = new Preferences();
            next.ammo = (Integer)ammoSetting.getValue(); next.runes = (Integer)runeSetting.getValue(); next.doses = (Integer)doseSetting.getValue();
            next.charges = (Integer)chargeSetting.getValue(); next.notifications = notificationsSetting.isSelected(); next.thresholds = new LinkedHashMap<>(preferences.thresholds);
            thread.invokeLater(() -> { if (ticket == epoch && Objects.equals(owner,profile) && current()) preferencesNow(next); });
        })); panel.add(SidebarUi.disclosure("Supply thresholds",settings));
        panel.addHierarchyListener(event -> { if (panel.isShowing()) render(); }); render(); return panel;
    }
    private void refresh()
    {
        long ticket = epoch; SwingUtilities.invokeLater(() -> { if (ticket == epoch && panel != null && panel.isShowing()) render(); });
    }
    private void render()
    {
        if (body == null) return; body.removeAll(); body.add(SidebarUi.heading("AMMO, RUNES & CHARGES"));
        if (!current()) body.add(SidebarUi.label("Log in to observe your supplies"));
        else
        {
            if (settingsEpoch != epoch)
            {
                settingsEpoch = epoch; Preferences prefs = preferences; ammoSetting.setValue(prefs.ammo); runeSetting.setValue(prefs.runes);
                doseSetting.setValue(prefs.doses); chargeSetting.setValue(prefs.charges); notificationsSetting.setSelected(prefs.notifications);
            }
            List<Row> view = rows(); int pages = Math.max(1,(view.size()+PAGE_SIZE-1)/PAGE_SIZE); page = Math.min(page,pages-1);
            if (view.isEmpty()) body.add(SidebarUi.label("No supported charge items or ammunition observed"));
            for (int index = page*PAGE_SIZE; index < Math.min(view.size(),page*PAGE_SIZE+PAGE_SIZE); index++)
            {
                Row row = view.get(index); JPanel card = SidebarUi.section(row.name); card.add(SidebarUi.label((row.low()?"! ":"✓ ")+row.label()));
                String owner = profile; long ticket = epoch;
                card.add(SidebarUi.button("Item sources",() -> thread.invokeLater(() -> { if (ticket == epoch && Objects.equals(owner,profile) && current()) openItem.accept(row.itemId); }))); body.add(card);
            }
            if (pages > 1)
            {
                JLabel count = new JLabel((page+1)+" / "+pages); count.setForeground(SidebarUi.TEXT); count.setHorizontalAlignment(JLabel.CENTER);
                JButton previous = SidebarUi.button("‹",() -> { page = Math.max(0,page-1); render(); });
                JButton next = SidebarUi.button("›",() -> { page = Math.min(pages-1,page+1); render(); }); body.add(SidebarUi.row(previous,count,next));
            }
            if (view.stream().anyMatch(row -> row.amount == 0)) body.add(SidebarUi.button("Clear empty entries",() -> {
                String owner = profile; long ticket = epoch; thread.invokeLater(() -> { if (ticket != epoch || !Objects.equals(owner,profile) || !current()) return;
                    tracked.entrySet().removeIf(entry -> entry.getValue().amount == 0); onGameTick(); refresh(); });
            }));
        }
        SidebarUi.install(body); body.revalidate(); body.repaint();
    }
}
