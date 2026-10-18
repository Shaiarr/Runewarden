/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Saved exact loadouts, observed storage search and actionable departure checks. */
@Singleton
public final class LoadoutReadinessTool
{
    public static final String KEY = "loadoutPlansV1";
    private static final int MAX_JSON = 220_000, MAX_PLANS = 30;
    private static final String[] BOOKS = {"Standard", "Ancient", "Lunar", "Arceuus"};
    private static final Map<Integer, String> NAMES = names();
    private final OwnershipLedger ledger;
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final Gson gson;
    private final ItemManager items;
    private final RunewardenDataStore store;
    private volatile List<LoadoutPlan> plans = Collections.emptyList();
    private volatile String profile, selectedId;
    private volatile boolean started;
    private volatile boolean activeSession;
    private volatile long epoch;
    private volatile long renderedEpoch = -1;
    private volatile String renderedProfile;
    private int ticks;
    private JPanel panel, body, storageResults;
    private JTextField presetName, search;
    private JCheckBox preserveInventoryLayout;
    private JComboBox<LoadoutPlan> selectedBox;
    private final AtomicBoolean rendering = new AtomicBoolean(), renderAgain = new AtomicBoolean();
    private IntConsumer openItem = ignored -> {};
    private Consumer<Set<Integer>> bankFocus = ignored -> {};
    private volatile String status = "";
    private Supplier<Map<Integer,Long>> chargeTotals = Collections::emptyMap;
    private Supplier<Map<Integer,Integer>> chargeStates = Collections::emptyMap;
    private Supplier<Set<String>> activeEffects = Collections::emptySet, knownEffects = Collections::emptySet;
    private final Map<String,Set<String>> confirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Map<Integer,Long> observedCharges = Collections.emptyMap();
    private volatile Map<Integer,Integer> supportedCharges = Collections.emptyMap();
    private volatile Set<String> observedEffects = Collections.emptySet(), supportedEffects = Collections.emptySet();
    private static final class Saved
    {
        int version = 2;
        List<LoadoutPlan> plans = new ArrayList<>();
        String selected;
    }
    @Inject public LoadoutReadinessTool(OwnershipLedger ledger, Client client, ClientThread clientThread,
        ConfigManager config, Gson gson, ItemManager items, RunewardenDataStore store)
    { this.ledger = ledger; this.client = client; this.clientThread = clientThread; this.config = config; this.gson = gson; this.items = items; this.store = store; }
    public void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> {} : action; }
    public void setBankFocusAction(Consumer<Set<Integer>> action) { bankFocus = action == null ? ignored -> {} : action; }
    public void setChargeFacts(Supplier<Map<Integer,Long>> totals, Supplier<Map<Integer,Integer>> supported)
    { chargeTotals = totals == null ? Collections::emptyMap : totals; chargeStates = supported == null ? Collections::emptyMap : supported; }
    public void setActiveEffects(Supplier<Set<String>> active, Supplier<Set<String>> known)
    { activeEffects = active == null ? Collections::emptySet : active; knownEffects = known == null ? Collections::emptySet : known; }
    public void start() { started = true; clear(); }
    public void stop() { started = false; clear(); }
    public void clear()
    { epoch++; activeSession = false; profile = null; plans = Collections.emptyList(); selectedId = null; status = ""; ticks = 0; confirmations.clear(); observedCharges = Collections.emptyMap(); supportedCharges = Collections.emptyMap(); observedEffects = Collections.emptySet(); supportedEffects = Collections.emptySet(); refresh(); }
    public List<LoadoutPlan> presets() { return validProfile() ? plans : Collections.emptyList(); }
    public LoadoutPlan selected()
    {
        if (!validProfile()) return null;
        for (LoadoutPlan plan : plans) if (plan.id.equals(selectedId)) return plan;
        return null;
    }
    public void setSelectedId(String id)
    { mutate(() -> { for (LoadoutPlan plan : plans) if (plan.id.equals(id)) { selectedId = id; save(); break; } }); }
    private boolean validProfile()
    { return started && activeSession && profile != null && profile.equals(config.getRSProfileKey()); }
    public void onItemContainerChanged(int ignored) { refresh(); }
    public void onGameTick()
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { activeSession = false; confirmations.clear(); observedCharges = Collections.emptyMap(); observedEffects = Collections.emptySet(); supportedEffects = Collections.emptySet(); return; }
        String next = config.getRSProfileKey(); if (next == null) return;
        if (!next.equals(profile))
        {
            clear(); profile = next; activeSession = true;
            Saved loaded = store.read(RunewardenConfig.GROUP, profile, KEY, 2, Saved.class, LoadoutReadinessTool::validSaved, Saved::new);
            if (loaded != null && validSaved(loaded))
            {
                List<LoadoutPlan> safe = new ArrayList<>(); for (LoadoutPlan plan : loaded.plans) safe.add(plan.checked());
                plans = Collections.unmodifiableList(safe); selectedId = loaded.selected;
                if (selected() == null && !plans.isEmpty()) selectedId = plans.get(0).id;
            }
            refresh();
        }
        activeSession = true;
        observedCharges = Collections.unmodifiableMap(new LinkedHashMap<>(chargeTotals.get()));
        supportedCharges = Collections.unmodifiableMap(new LinkedHashMap<>(chargeStates.get()));
        observedEffects = Collections.unmodifiableSet(new LinkedHashSet<>(activeEffects.get()));
        supportedEffects = Collections.unmodifiableSet(new LinkedHashSet<>(knownEffects.get()));
        if (++ticks % 3 == 0 && panel != null && panel.isShowing()) refresh();
    }
    public JPanel createPanel()
    {
        panel = SidebarUi.column(); panel.setName("loadout-readiness");
        JPanel saved = SidebarUi.section("LOADOUTS");
        selectedBox = new JComboBox<>(); selectedBox.setName("loadout.select"); selectedBox.setFont(GuideText.FONT);
        selectedBox.addActionListener(event -> {
            if (rendering.get()) return;
            LoadoutPlan plan = (LoadoutPlan) selectedBox.getSelectedItem(); if (plan != null) setSelectedId(plan.id);
        }); saved.add(selectedBox);
        presetName = new JTextField(); presetName.setName("loadout.name"); saved.add(SidebarUi.field("Name", presetName));
        preserveInventoryLayout=new JCheckBox("Save inventory positions");preserveInventoryLayout.setOpaque(false);preserveInventoryLayout.setForeground(SidebarUi.TEXT);preserveInventoryLayout.setFont(GuideText.FONT);saved.add(preserveInventoryLayout);
        saved.add(SidebarUi.button("Save worn + inventory", () -> capture(presetName.getText(), false)));
        saved.add(SidebarUi.button("Pool potion doses", this::poolPotionDoses));
        saved.add(SidebarUi.row(SidebarUi.button("Update", () -> capture(presetName.getText(), true)),
            SidebarUi.button("Delete", this::deleteSelected))); panel.add(saved);
        body = SidebarUi.column(); panel.add(body);
        JPanel storage = SidebarUi.column();
        search = new JTextField(); search.setName("loadout.storage-search"); storage.add(SidebarUi.field("Item name or ID", search));
        storageResults = SidebarUi.column();
        Runnable find = () -> renderStorage(storageResults, search.getText());
        search.addActionListener(event -> find.run()); storage.add(SidebarUi.button("Find in storage", find)); storage.add(storageResults);
        panel.add(SidebarUi.disclosure("Where is my item?", storage));
        JTextArea transfer = new JTextArea(8, 12); transfer.setLineWrap(true); transfer.setWrapStyleWord(true); transfer.setFont(GuideText.FONT);
        JScrollPane scroll = new JScrollPane(transfer); scroll.setPreferredSize(new Dimension(180, 140)); scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        JPanel sharing = SidebarUi.column(); sharing.add(scroll);
        sharing.add(SidebarUi.row(SidebarUi.button("Export", () -> transfer.setText(exportPlans())),
            SidebarUi.button("Import", () -> importPlans(transfer.getText()))));
        panel.add(SidebarUi.disclosure("Share / restore loadouts", sharing));
        theme(panel); refresh(); return panel;
    }
    private void capture(String name, boolean replace)
    {
        String title = name == null ? "" : name.trim();
        boolean preserve=preserveInventoryLayout!=null&&preserveInventoryLayout.isSelected();
        mutate(() -> {
            OwnershipLedger.View state = ledger.snapshot();
            OwnershipLedger.Observation worn = state.storages.get(OwnershipLedger.Storage.EQUIPMENT), inventory = state.storages.get(OwnershipLedger.Storage.INVENTORY);
            if (worn == null || inventory == null || !worn.currentSession || !inventory.currentSession) { status = "Inventory and equipment not observed"; return; }
            LoadoutPlan old = selected(); if (replace && old == null) { status = "Select a loadout"; return; }
            if (!replace && plans.size() >= MAX_PLANS) { status = "30 loadouts saved"; return; }
            List<LoadoutPlan.Requirement> requirements = new ArrayList<>();
            worn.slots.forEach((slot, item) -> requirements.add(new LoadoutPlan.Requirement(Collections.singleton(item.id), item.quantity, LoadoutPlan.Location.EQUIPMENT, slot)));
            if(preserve)inventory.slots.forEach((slot, item) -> requirements.add(new LoadoutPlan.Requirement(Collections.singleton(item.id), item.quantity, LoadoutPlan.Location.INVENTORY, slot)));
            else inventory.quantities.forEach((id, count) -> requirements.add(new LoadoutPlan.Requirement(Collections.singleton(id), count, LoadoutPlan.Location.INVENTORY, -1)));
            if (state.runePouchObserved) state.runePouch.forEach((id, count) -> requirements.add(new LoadoutPlan.Requirement(Collections.singleton(id), count, LoadoutPlan.Location.RUNE_POUCH, -1)));
            OwnershipLedger.Observation quiver = state.storages.get(OwnershipLedger.Storage.QUIVER);
            if (quiver != null && quiver.currentSession && state.quiverAvailable) quiver.quantities.forEach((id, count) -> requirements.add(new LoadoutPlan.Requirement(Collections.singleton(id), count, LoadoutPlan.Location.QUIVER, -1)));
            LoadoutPlan plan = new LoadoutPlan(replace ? old.id : UUID.randomUUID().toString(), title.isEmpty() ? replace ? old.name : "Loadout " + (plans.size() + 1) : title, requirements, state.spellbook, replace ? old.checks() : Collections.emptyList());
            if (replace) confirmations.remove(old.id);
            List<LoadoutPlan> next = new ArrayList<>(plans); if (replace) next.removeIf(value -> value.id.equals(old.id)); next.add(plan);
            plans = Collections.unmodifiableList(next); selectedId = plan.id; status = "Loadout saved"; save();
        });
    }
    private void poolPotionDoses()
    {
        mutate(() -> {
            LoadoutPlan current=selected();if(current==null)return;
            List<LoadoutPlan.Requirement> next=new ArrayList<>();Map<Integer,Long> pooled=new LinkedHashMap<>();
            for(LoadoutPlan.Requirement need:current.requirements)
            {
                if(need.location==LoadoutPlan.Location.INVENTORY&&need.alternatives.size()==1&&LoadoutItemVariants.doses(need.firstId())>0)
                {int one=LoadoutItemVariants.oneDose(need.firstId());if(one>0){pooled.merge(one,need.quantity*(need.unit()==LoadoutPlan.Unit.DOSES?1:LoadoutItemVariants.doses(need.firstId())),Long::sum);continue;}}
                next.add(need);
            }
            pooled.forEach((id,doses)->next.add(new LoadoutPlan.Requirement(LoadoutItemVariants.potion(id),doses,LoadoutPlan.Location.INVENTORY,-1,LoadoutPlan.Unit.DOSES)));
            replace(current.withRequirements(next,current.spellbook));
        });
    }
    private void deleteSelected()
    {
        mutate(() -> { LoadoutPlan old = selected(); if (old == null) return; List<LoadoutPlan> next = new ArrayList<>(plans);
            next.removeIf(value -> value.id.equals(old.id)); confirmations.remove(old.id); plans = Collections.unmodifiableList(next); selectedId = next.isEmpty() ? null : next.get(0).id; status = "Loadout removed"; save(); });
    }
    public String exportPlans()
    { Saved saved = new Saved(); saved.plans = presets(); saved.selected = selectedId; return gson.toJson(saved); }
    public void importPlans(String json)
    {
        Saved imported;
        try
        {
            if (!boundedJson(json)) throw new IllegalArgumentException("Loadout data is too large");
            imported = gson.fromJson(json, Saved.class);
            if (!validSaved(imported)) throw new IllegalArgumentException("Invalid loadout data");
        }
        catch (RuntimeException failure) { status = "Import rejected: invalid loadout data"; refresh(); return; }
        mutate(() -> {
            Map<String, LoadoutPlan> merged = new LinkedHashMap<>(); for (LoadoutPlan plan : plans) merged.put(plan.id, plan);
            for (LoadoutPlan plan : imported.plans) { merged.put(plan.id, plan.checked()); confirmations.remove(plan.id); }
            if (merged.size() > MAX_PLANS) { status = "Import exceeds 30 loadouts"; return; }
            plans = Collections.unmodifiableList(new ArrayList<>(merged.values()));
            if (selected() == null && !plans.isEmpty()) selectedId = plans.get(0).id;
            status = imported.plans.size() + " loadouts imported"; save();
        });
    }
    static boolean validSaved(Saved saved)
    {
        if (saved == null || (saved.version != 1 && saved.version != 2) || saved.plans == null || saved.plans.size() > MAX_PLANS) return false;
        Set<String> ids = new LinkedHashSet<>();
        try { for (LoadoutPlan plan : saved.plans) if (plan == null || !ids.add(plan.checked().id)) return false; }
        catch (RuntimeException invalid) { return false; }
        return saved.selected == null || saved.selected.length() <= 64;
    }
    private void save()
    {
        if (!validProfile()) return;
        Saved saved = new Saved(); saved.plans = plans; saved.selected = selectedId;
        if (!store.write(RunewardenConfig.GROUP, profile, KEY, 2, saved)) status = "Loadout save needs recovery in Data settings";
    }
    private void mutate(Runnable action)
    {
        String owner = panel == null ? profile : renderedProfile; long expected = panel == null ? epoch : renderedEpoch;
        clientThread.invokeLater(() -> {
            if (expected != epoch || owner == null || !owner.equals(profile) || !validProfile() || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { refresh(); return; }
            try { action.run(); } catch (IllegalArgumentException invalid) { status = invalid.getMessage(); }
            refresh();
        });
    }
    private void refresh()
    {
        if (panel == null) return;
        renderAgain.set(true); if (!rendering.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            try { do { renderAgain.set(false); render(); } while (renderAgain.get()); }
            finally { rendering.set(false); if (renderAgain.get()) refresh(); }
        });
    }
    private void render()
    {
        if (body == null) return;
        renderedEpoch = epoch; renderedProfile = profile;
        if (storageResults != null && search != null) renderStorage(storageResults, search.getText());
        body.removeAll(); LoadoutPlan current = selected();
        selectedBox.removeAllItems(); for (LoadoutPlan plan : presets()) selectedBox.addItem(plan); selectedBox.setSelectedItem(current);
        if (!status.isEmpty()) body.add(SidebarUi.label(status));
        if (!validProfile()) { body.add(SidebarUi.label("Log in to view character loadouts")); finishRender(); return; }
        if (current == null) { body.add(SidebarUi.label("No loadout selected")); finishRender(); return; }
        OwnershipLedger.View state = ledger.snapshot(); LoadoutReadiness check = readiness(current, state);
        JPanel summary = SidebarUi.section(check.ready ? current.checks().isEmpty() ? "✓ ITEMS READY" : "✓ CHECKLIST READY" : "PREPARATION");
        summary.add(SidebarUi.label(current.name));
        int missing = (int) check.lines.stream().filter(line -> !line.ready()).count(); summary.add(SidebarUi.label((check.lines.size() - missing) + " / " + check.lines.size() + " items ready"));
        if (!check.checks.isEmpty())
        {
            long ready = check.checks.stream().filter(line -> line.state == LoadoutReadiness.CheckState.READY).count();
            long unknown = check.checks.stream().filter(line -> line.state == LoadoutReadiness.CheckState.UNKNOWN).count();
            summary.add(SidebarUi.label(ready + " / " + check.checks.size() + " departure checks" + (unknown > 0 ? " · " + unknown + " unknown" : "")));
        }
        if (current.spellbook != null) summary.add(SidebarUi.label((check.spellbookMatches && check.spellbookKnown ? "✓ " : "! ") + BOOKS[current.spellbook] + " spellbook"));
        summary.add(SidebarUi.button("Focus supplies in bank", () -> {
            String owner = renderedProfile; long expected = renderedEpoch; Set<Integer> ids = new LinkedHashSet<>();
            for (LoadoutReadiness.Line line : check.lines) if (!line.ready()) ids.addAll(line.requirement.alternatives);
            clientThread.invokeLater(() -> { if (expected == epoch && owner != null && owner.equals(profile) && validProfile()) bankFocus.accept(Collections.unmodifiableSet(ids)); });
        })); body.add(summary);
        for (LoadoutReadiness.CheckLine line : check.checks) addDepartureLine(current,line);
        body.add(SidebarUi.disclosure("Add departure check", departureEditor(current)));
        for (int i = 0; i < check.lines.size(); i++) addLine(current, check.lines.get(i), i);
        body.add(SidebarUi.disclosure("Add item requirement", editor(current, null, -1)));
        JPanel book = SidebarUi.column(); JComboBox<String> bookChoice = new JComboBox<>(new String[]{"Any spellbook", "Standard", "Ancient", "Lunar", "Arceuus"});
        bookChoice.setSelectedIndex(current.spellbook == null ? 0 : current.spellbook + 1); book.add(bookChoice);
        book.add(SidebarUi.button("Save spellbook", () -> mutate(() -> replace(current.withRequirements(current.requirements, bookChoice.getSelectedIndex() == 0 ? null : bookChoice.getSelectedIndex() - 1)))));
        body.add(SidebarUi.disclosure("Spellbook requirement", book));
        theme(body); finishRender();
    }
    private LoadoutReadiness readiness(LoadoutPlan plan, OwnershipLedger.View state)
    {
        Set<String> confirmed = confirmations.getOrDefault(plan.id,Collections.emptySet());
        return LoadoutReadiness.check(plan,state,new LoadoutReadiness.CheckFacts(observedCharges,supportedCharges,observedEffects,supportedEffects,confirmed));
    }
    public LoadoutReadiness readiness()
    { LoadoutPlan plan = selected(); return plan == null ? null : readiness(plan,ledger.snapshot()); }
    public void confirmDepartureCheck(String planId,String checkId,boolean confirmed)
    {
        mutate(() -> {
            LoadoutPlan plan = null; for (LoadoutPlan value : plans) if (value.id.equals(planId)) plan = value;
            if (plan == null || plan.checks().stream().noneMatch(check -> check.id.equals(checkId) && check.kind == LoadoutPlan.CheckKind.MANUAL)) return;
            Set<String> next = new LinkedHashSet<>(confirmations.getOrDefault(planId,Collections.emptySet()));
            if (confirmed) next.add(checkId); else next.remove(checkId); confirmations.put(planId,Collections.unmodifiableSet(next));
            status = confirmed ? "Departure check confirmed this session" : "Departure confirmation cleared";
        });
    }
    private void addDepartureLine(LoadoutPlan plan,LoadoutReadiness.CheckLine line)
    {
        JPanel card = SidebarUi.section(null);
        String prefix = line.state == LoadoutReadiness.CheckState.READY ? "✓ " : line.state == LoadoutReadiness.CheckState.UNKNOWN ? "? " : "! ";
        card.add(SidebarUi.heading(prefix + line.check.label)); card.add(SidebarUi.label(line.detail));
        if (line.check.kind == LoadoutPlan.CheckKind.MANUAL)
            card.add(SidebarUi.button(line.state == LoadoutReadiness.CheckState.READY ? "Clear confirmation" : "Confirm checked", () -> confirmDepartureCheck(plan.id,line.check.id,line.state != LoadoutReadiness.CheckState.READY)));
        if (line.check.kind == LoadoutPlan.CheckKind.CHARGES)
            for (Integer id : line.check.itemIds) card.add(SidebarUi.wrappedButton(itemName(id), () -> guardedItem(id)));
        card.add(SidebarUi.button("Remove check", () -> mutate(() -> {
            List<LoadoutPlan.DepartureCheck> next = new ArrayList<>(plan.checks()); next.removeIf(check -> check.id.equals(line.check.id));
            replace(new LoadoutPlan(plan.id,plan.name,plan.requirements,plan.spellbook,next));
        }))); body.add(card);
    }
    private JPanel departureEditor(LoadoutPlan plan)
    {
        JPanel edit = SidebarUi.column();
        JComboBox<LoadoutPlan.CheckKind> kind = new JComboBox<>(LoadoutPlan.CheckKind.values());
        edit.add(SidebarUi.field("Check type",kind)); JTextField label = new JTextField(); edit.add(SidebarUi.field("Check name",label));
        Set<Integer> candidates = new LinkedHashSet<>();
        for (LoadoutPlan.Requirement requirement : plan.requirements) for (Integer id : requirement.alternatives)
            if (supportedCharges.containsKey(id)) candidates.add(id);
        JTextField ids = new JTextField(join(candidates)); JPanel chargeFields = SidebarUi.column();
        chargeFields.add(SidebarUi.field("Charge item variants · IDs",ids)); JTextField minimum = new JTextField("1"); chargeFields.add(SidebarUi.field("Minimum carried charges / doses",minimum)); edit.add(chargeFields);
        JComboBox<String> effects = new JComboBox<>(); effects.setName("loadout.effect-choice"); java.util.Set<String> known = new java.util.TreeSet<>(supportedEffects);
        for (FieldCoach.Spec spec : FieldCoach.SPECS) known.add(spec.key); known.add("antipoison"); known.add("antivenom");
        for (String effect : known) effects.addItem(effect);
        effects.setRenderer(new javax.swing.DefaultListCellRenderer() { @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,Object value,int index,boolean selected,boolean focus)
            { String label = value == null ? "" : value.toString(); for (FieldCoach.Spec spec : FieldCoach.SPECS) if (spec.key.equals(label)) { label = spec.label; break; } if ("antipoison".equals(label)) label = "Poison immunity"; if ("antivenom".equals(label)) label = "Venom immunity"; return super.getListCellRendererComponent(list,label,index,selected,focus); } });
        JPanel effectFields = SidebarUi.field("Required active effect",effects); effectFields.setVisible(false); edit.add(effectFields);
        kind.addActionListener(event -> { chargeFields.setVisible(kind.getSelectedItem() == LoadoutPlan.CheckKind.CHARGES); effectFields.setVisible(kind.getSelectedItem() == LoadoutPlan.CheckKind.EFFECT); edit.revalidate(); });
        edit.add(SidebarUi.button("Add check", () -> {
            String title = label.getText().trim(), values = ids.getText(), amount = minimum.getText(); LoadoutPlan.CheckKind type = (LoadoutPlan.CheckKind)kind.getSelectedItem(); String effect = (String)effects.getSelectedItem();
            mutate(() -> {
                Set<Integer> selectedIds = new LinkedHashSet<>(); long threshold = 0;
                if (type == LoadoutPlan.CheckKind.CHARGES)
                {
                    if (values.length() > 300) throw new IllegalArgumentException("Too many item variants");
                    for (String value : values.split("[, ]+")) selectedIds.add(Integer.parseInt(value)); threshold = Long.parseLong(amount);
                }
                String name = title.isEmpty() ? type == LoadoutPlan.CheckKind.CHARGES ? "Charge supply" : type == LoadoutPlan.CheckKind.EFFECT ? effect + " active" : "Manual departure check" : title;
                LoadoutPlan.DepartureCheck check = new LoadoutPlan.DepartureCheck(UUID.randomUUID().toString(),name,type,selectedIds,threshold,type == LoadoutPlan.CheckKind.EFFECT ? effect : null);
                List<LoadoutPlan.DepartureCheck> next = new ArrayList<>(plan.checks()); next.add(check);
                replace(new LoadoutPlan(plan.id,plan.name,plan.requirements,plan.spellbook,next));
            });
        })); return edit;
    }
    private void addLine(LoadoutPlan plan, LoadoutReadiness.Line line, int index)
    {
        LoadoutPlan.Requirement requirement = line.requirement;
        JPanel card = SidebarUi.section(null); JPanel item = new JPanel(new BorderLayout(6, 0)); item.setOpaque(false);
        JLabel image = new JLabel(); if (items != null) { net.runelite.client.util.AsyncBufferedImage icon = items.getImage(requirement.firstId()); if (icon != null) icon.addTo(image); }
        item.add(image, BorderLayout.WEST); item.add(SidebarUi.label((line.ready() ? "✓ " : "! ") + itemName(requirement.firstId()) + (requirement.alternatives.size() > 1 ? " + alternatives" : "")), BorderLayout.CENTER); card.add(item);
        String location = requirement.location.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        card.add(SidebarUi.label(quantity(line.carried) + " / " + quantity(requirement.quantity) + (requirement.unit() == LoadoutPlan.Unit.DOSES ? " doses" : " items") + " · " + location + (requirement.slot >= 0 ? " slot " + requirement.slot : "")));
        if (!line.observed) card.add(SidebarUi.label("Contents not observed"));
        else if (line.missing > 0) card.add(SidebarUi.label("Missing " + quantity(line.missing) + " · bank " + (line.bankKnown ? quantity(line.banked) + (line.bankCurrentSession ? " this session" : " previous session") + "\n" + time(line.bankObservedAt) : "unknown")));
        if(line.storedDoses>0)card.add(SidebarUi.label("Potion storage · "+quantity(line.storedDoses)+" doses last seen"));
        card.add(SidebarUi.row(SidebarUi.button("Item", () -> guardedItem(requirement.firstId())),
            SidebarUi.button("Remove", () -> mutate(() -> { List<LoadoutPlan.Requirement> next = new ArrayList<>(plan.requirements); if (index < next.size()) next.remove(index); replace(plan.withRequirements(next, plan.spellbook)); }))));
        card.add(SidebarUi.disclosure("Edit", editor(plan, requirement, index))); body.add(card);
    }
    private JPanel editor(LoadoutPlan plan, LoadoutPlan.Requirement requirement, int index)
    {
        JPanel edit = SidebarUi.column();
        JTextField ids = new JTextField(requirement == null ? "" : join(requirement.alternatives)); edit.add(SidebarUi.field("Item IDs / alternatives", ids));
        JTextField quantity = new JTextField(requirement == null ? "1" : Long.toString(requirement.quantity)); edit.add(SidebarUi.field("Quantity", quantity));
        JComboBox<LoadoutPlan.Unit> unit = new JComboBox<>(LoadoutPlan.Unit.values()); unit.setSelectedItem(requirement == null ? LoadoutPlan.Unit.ITEMS : requirement.unit()); edit.add(SidebarUi.field("Count as", unit));
        edit.add(SidebarUi.button("Add potion dose variants", () -> {
            try{int first=Integer.parseInt(ids.getText().split("[, ]+")[0]);Set<Integer> variants=LoadoutItemVariants.potion(first);if(!variants.isEmpty()){ids.setText(join(variants));unit.setSelectedItem(LoadoutPlan.Unit.DOSES);}}catch(RuntimeException ignored){}
        }));
        edit.add(SidebarUi.button("Add RuneLite item variants", () -> {
            try{int first=Integer.parseInt(ids.getText().split("[, ]+")[0]);Set<Integer> variants=LoadoutItemVariants.variations(first);if(variants.size()<=32)ids.setText(join(variants));}catch(RuntimeException ignored){}
        }));
        JComboBox<LoadoutPlan.Location> location = new JComboBox<>(LoadoutPlan.Location.values()); location.setSelectedItem(requirement == null ? LoadoutPlan.Location.INVENTORY : requirement.location); edit.add(SidebarUi.field("Location", location));
        JTextField slot = new JTextField(requirement == null ? "-1" : Integer.toString(requirement.slot)); edit.add(SidebarUi.field("Slot (-1 = any)", slot));
        unit.addActionListener(event -> {if(unit.getSelectedItem()==LoadoutPlan.Unit.DOSES){slot.setText("-1");location.setSelectedItem(LoadoutPlan.Location.INVENTORY);}});
        edit.add(SidebarUi.button("Save requirement", () -> {
            Set<Integer> values = new LinkedHashSet<>(); long amount; int slotValue;
            try
            {
                if (ids.getText().length() > 300) throw new IllegalArgumentException();
                for (String value : ids.getText().split("[, ]+")) values.add(Integer.parseInt(value));
                amount = Long.parseLong(quantity.getText()); slotValue = Integer.parseInt(slot.getText());
                LoadoutPlan.Requirement replacement = new LoadoutPlan.Requirement(values, amount, (LoadoutPlan.Location) location.getSelectedItem(), slotValue, (LoadoutPlan.Unit) unit.getSelectedItem());
                mutate(() -> { List<LoadoutPlan.Requirement> next = new ArrayList<>(plan.requirements); if (index < 0) next.add(replacement); else next.set(index, replacement); replace(plan.withRequirements(next, plan.spellbook)); });
            }
            catch (RuntimeException invalid) { status = "Check item IDs, quantity and slot"; refresh(); }
        })); return edit;
    }
    private void replace(LoadoutPlan plan)
    {
        List<LoadoutPlan> next = new ArrayList<>(plans); boolean found = false;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(plan.id)) { next.set(i, plan); found = true; break; }
        if (found) { confirmations.remove(plan.id); plans = Collections.unmodifiableList(next); status = "Loadout updated"; save(); }
    }
    private void renderStorage(JPanel results, String query)
    {
        results.removeAll(); OwnershipLedger.View state = ledger.snapshot();
        Set<Integer> matches = new LinkedHashSet<>(); String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.matches("[0-9]{1,7}")) matches.add(Integer.parseInt(text));
        else if (text.length() > 1)
        {
            Set<Integer> observedIds = new LinkedHashSet<>(); state.storages.values().forEach(values -> observedIds.addAll(values.quantities.keySet()));
            List<Integer> sorted = new ArrayList<>(observedIds); sorted.sort(Comparator.comparing(LoadoutReadinessTool::itemName));
            for (int id : sorted) if (itemName(id).toLowerCase(Locale.ROOT).contains(text)) { matches.add(id); if (matches.size() >= 40) break; }
        }
        int shown = 0;
        for (int id : matches)
        {
            OwnershipLedger.Quantity quantity = ledger.query(id); if (quantity.byStorage.values().stream().noneMatch(value -> value > 0) && !text.matches("[0-9]+")) continue;
            if (++shown > 12) break;
            JPanel card = SidebarUi.section(itemName(id));
            for (Map.Entry<OwnershipLedger.Storage, Long> entry : quantity.byStorage.entrySet()) if (entry.getValue() > 0)
            {
                OwnershipLedger.Observation observation = state.storages.get(entry.getKey());
                card.add(SidebarUi.label(entry.getKey().label + " · " + quantity(entry.getValue()) + "\n" + time(observation.observedAt)));
            }
            if (quantity.byStorage.values().stream().noneMatch(value -> value > 0)) card.add(SidebarUi.label("Not present in observed storages"));
            if (!quantity.unknownStorages.isEmpty()) card.add(SidebarUi.label(quantity.unknownStorages.size() + " storages unknown"));
            card.add(SidebarUi.button("Item details", () -> guardedItem(id))); results.add(card);
        }
        if (shown == 0) results.add(SidebarUi.label("No observed matches"));
        for (Map.Entry<String, OwnershipLedger.Balance> entry : state.currencies.entrySet())
            results.add(SidebarUi.label(entry.getKey() + " · " + quantity(entry.getValue().quantity) + (entry.getKey().endsWith("coffer") ? " gp" : " points") + "\n" + time(entry.getValue().observedAt)));
        results.revalidate(); results.repaint();
    }
    private void guardedItem(int id)
    { mutate(() -> openItem.accept(id)); }
    private void finishRender() { body.revalidate(); body.repaint(); if (panel != null) { panel.revalidate(); panel.repaint(); } }
    private static String join(Set<Integer> values) { StringBuilder result = new StringBuilder(); for (int value : values) { if (result.length() > 0) result.append(','); result.append(value); } return result.toString(); }
    static String itemName(int id) { return NAMES.getOrDefault(id, "Item " + id); }
    private static String quantity(long number) { return String.format(Locale.ROOT, "%,d", number); }
    private static String time(long epoch) { return DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epoch)); }
    private static Map<Integer, String> names()
    {
        Map<Integer, String> result = new LinkedHashMap<>();
        GuideItemResolver.nameIndex().forEach((name, ids) -> ids.forEach(id -> result.putIfAbsent(id, name)));
        return Collections.unmodifiableMap(result);
    }
    private static boolean boundedJson(String json)
    {
        if (json == null || json.length() > MAX_JSON) return false;
        int depth = 0; boolean quoted = false, escape = false;
        for (int i = 0; i < json.length(); i++)
        {
            char c = json.charAt(i);
            if (quoted) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 32) return false; }
            else if (c == '}' || c == ']') { if (--depth < 0) return false; }
        }
        return !quoted && depth == 0;
    }
    private static void theme(java.awt.Container root)
    {
        for (java.awt.Component child : root.getComponents())
        {
            if (child instanceof JTextField || child instanceof JTextArea || child instanceof JComboBox)
            {
                child.setFont(GuideText.FONT); child.setBackground(SidebarUi.CARD); child.setForeground(SidebarUi.TEXT);
                if (child instanceof javax.swing.text.JTextComponent) ((javax.swing.text.JTextComponent) child).setCaretColor(SidebarUi.GOLD);
                if (child instanceof JComboBox && !"loadout.effect-choice".equals(child.getName())) ((JComboBox<?>) child).setRenderer(new javax.swing.DefaultListCellRenderer()
                {
                    @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
                    { super.getListCellRendererComponent(list, value, index, selected, focus); setBackground(selected ? new java.awt.Color(66, 57, 36) : SidebarUi.CARD); setForeground(SidebarUi.TEXT); setFont(GuideText.FONT); return this; }
                });
            }
            if (child instanceof java.awt.Container) theme((java.awt.Container) child);
        }
    }
}
