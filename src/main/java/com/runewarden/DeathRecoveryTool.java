/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;

/** Records observed deaths and actual retrieval interfaces without guessing item-loss rules. */
@Singleton
public final class DeathRecoveryTool
{
    public static final String GROUP = "runewardendeaths", KEY = "deathsV1";
    private static final int MAX_RECORDS = 20, MAX_ITEMS = 64;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());
    private final Client client;
    private final ConfigManager config;
    private final ClientThread clientThread;
    private final ItemManager items;
    private final RunewardenDataStore store;
    private final OwnershipLedger ownership;
    private Consumer<WorldPoint> route = ignored -> { };
    private IntConsumer openItem = ignored -> { };
    private Supplier<LoadoutPlan> selectedLoadout = () -> null;
    private Supplier<String> activityGuide = () -> "";
    private Consumer<Set<Integer>> bankFocus = ignored -> { };
    private Consumer<String> resumeGuide = ignored -> { };
    private Runnable openLoadouts = () -> { };
    private boolean running, enabled = true, armed;
    private int aliveTicks, sessionTicks, lastDeathTick = Integer.MIN_VALUE;
    private String profile;
    private long epoch;
    private Saved saved = new Saved();
    private Item[] beforeInventory = new Item[0], beforeEquipment = new Item[0];
    private volatile Reading reading = Reading.EMPTY;
    private Recovery observedRecovery;
    private JPanel panel, content;
    private int page; private final Map<Long,Integer> kitPages = new java.util.concurrent.ConcurrentHashMap<>();

    static final class Saved { int version = 1; List<Record> deaths = new ArrayList<>(); Recovery recovery; }
    static final class Carried
    {
        int id, quantity; String name, location;
        Carried() { }
        Carried(int id, int quantity, String name, String location)
        { this.id = id; this.quantity = quantity; this.name = name; this.location = location; }
    }
    static final class Record
    {
        long at; int x, y, plane, world; boolean instance, acknowledged;
        List<Carried> carried = new ArrayList<>();
        LoadoutPlan returnKit; String guideId = "", recoveryKind; long recoverySeenAt;
    }
    static final class Recovery
    {
        String kind, fee; long at; List<Carried> items = new ArrayList<>();
    }
    public static final class DeathView
    {
        public final long at; public final WorldPoint point; public final int world;
        public final boolean instance, acknowledged; public final List<ItemView> carried;
        public final String guideId, recoveryKind; public final long recoverySeenAt; public final KitView kit;
        DeathView(Record row, KitView kit)
        {
            at = row.at; point = new WorldPoint(row.x, row.y, row.plane); world = row.world;
            instance = row.instance; acknowledged = row.acknowledged; carried = views(row.carried);
            guideId = row.guideId == null ? "" : row.guideId; recoveryKind = row.recoveryKind; recoverySeenAt = row.recoverySeenAt; this.kit = kit;
        }
    }
    public static final class KitView
    {
        public final LoadoutPlan plan;
        public final LoadoutReadiness readiness;
        public final Set<Integer> missingItemIds;
        KitView(LoadoutPlan plan, OwnershipLedger.View ownership)
        { this.plan = plan.checked(); readiness = LoadoutReadiness.check(this.plan, ownership); missingItemIds = missingItems(this.plan, ownership); }
    }
    public static final class ItemView
    {
        public final int id, quantity; public final String name, location;
        ItemView(Carried item) { id = item.id; quantity = item.quantity; name = item.name; location = item.location; }
    }
    public static final class RecoveryView
    {
        public final String kind, fee; public final long at; public final boolean visible;
        public final List<ItemView> items;
        RecoveryView(Recovery recovery, boolean visible)
        { kind = recovery.kind; fee = recovery.fee; at = recovery.at; this.visible = visible; items = views(recovery.items); }
    }
    public static final class Reading
    {
        static final Reading EMPTY = new Reading(null, Collections.emptyList(), null);
        public final String profile; public final List<DeathView> deaths; public final RecoveryView recovery;
        Reading(String profile, List<DeathView> deaths, RecoveryView recovery)
        { this.profile = profile; this.deaths = Collections.unmodifiableList(new ArrayList<>(deaths)); this.recovery = recovery; }
    }

    @Inject public DeathRecoveryTool(Client client, ConfigManager config, ClientThread clientThread,
        ItemManager items, RunewardenDataStore store, OwnershipLedger ownership)
    { this.client = client; this.config = config; this.clientThread = clientThread; this.items = items; this.store = store; this.ownership = ownership; }
    public void setRouteAction(Consumer<WorldPoint> action) { route = action == null ? ignored -> { } : action; }
    public void setOpenItemAction(IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    public void setSelectedLoadoutSupplier(Supplier<LoadoutPlan> supplier) { selectedLoadout = supplier == null ? () -> null : supplier; }
    public void setActivityGuideSupplier(Supplier<String> supplier) { activityGuide = supplier == null ? () -> "" : supplier; }
    public void setBankFocusAction(Consumer<Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    public void setOpenLoadoutsAction(Runnable action) { openLoadouts = action == null ? () -> { } : action; }
    public void setResumeGuideAction(Consumer<String> action) { resumeGuide = action == null ? ignored -> { } : action; }
    public void start() { running = true; reload(); clear(); }
    public void stop() { running = false; clear(); }
    public void clear()
    {
        epoch++; profile = null; saved = new Saved(); observedRecovery = null; reading = Reading.EMPTY;
        armed = false; aliveTicks = 0; sessionTicks = 0; lastDeathTick = Integer.MIN_VALUE;
        beforeInventory = new Item[0]; beforeEquipment = new Item[0]; page = 0; kitPages.clear(); kitItemNames.clear(); refresh();
    }
    public Reading snapshot() { return ready() ? reading : Reading.EMPTY; }
    public void onConfigChanged(ConfigChanged event) { if (GROUP.equals(event.getGroup())) reload(); }
    private void reload() { String value = config.getConfiguration(GROUP, "enabled"); enabled = value == null || Boolean.parseBoolean(value); }
    private boolean ready()
    { return running && profile != null && profile.equals(config.getRSProfileKey()) && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null; }
    public void onGameStateChanged(GameStateChanged event) { if (event.getGameState() != GameState.LOGGED_IN) clear(); }
    public void onItemContainerChanged(int containerId)
    { if (ready() && (containerId == InventoryID.GRAVESTONE || containerId == InventoryID.DEATH_PERMANENT)) readRecovery(); }
    public void onGameTick()
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        { if (profile != null) clear(); return; }
        String current = config.getRSProfileKey(); if (current == null || current.isEmpty()) return;
        if (!current.equals(profile))
        {
            clear(); profile = current;
            saved = store.read(GROUP, profile, KEY, 1, Saved.class, DeathRecoveryTool::valid, Saved::new);
            publish();
        }
        sessionTicks++;
        if (client.getBoostedSkillLevel(Skill.HITPOINTS) > 0)
        {
            if (++aliveTicks >= 2 && sessionTicks >= 3) armed = true;
            beforeInventory = copyContainer(InventoryID.INV); beforeEquipment = copyContainer(InventoryID.WORN);
        }
        else aliveTicks = 0;
        readRecovery();
        if (sessionTicks % 3 == 0 && saved.deaths.stream().anyMatch(record -> record.returnKit != null)) publish();
    }
    public void onActorDeath(ActorDeath event)
    {
        if (!enabled || !ready() || !armed || event == null || event.getActor() != client.getLocalPlayer()) return;
        int tick = client.getTickCount(); if (tick == lastDeathTick) return;
        armed = false; aliveTicks = 0; lastDeathTick = tick;
        WorldPoint point = client.getLocalPlayer().getWorldLocation(); if (point == null) return;
        Record record = new Record(); record.at = Math.max(System.currentTimeMillis(), saved.deaths.isEmpty() ? 1 : saved.deaths.get(0).at + 1); record.world = client.getWorld();
        record.x = point.getX(); record.y = point.getY(); record.plane = point.getPlane(); record.instance = client.isInInstancedRegion();
        record.guideId = safe(activityGuide.get(), 160); epoch++;
        addItems(record.carried, beforeInventory, "Inventory before death"); addItems(record.carried, beforeEquipment, "Worn before death");
        saved.deaths.add(0, record); while (saved.deaths.size() > MAX_RECORDS) saved.deaths.remove(saved.deaths.size() - 1);
        persist(); publish();
    }
    private Item[] copyContainer(int id)
    { ItemContainer container = client.getItemContainer(id); return container == null || container.getItems() == null ? new Item[0] : container.getItems().clone(); }
    private void addItems(List<Carried> target, Item[] source, String location)
    {
        if (source == null) return;
        for (Item item : source)
            if (target.size() < MAX_ITEMS && item != null && item.getId() > 0 && item.getQuantity() > 0)
                target.add(new Carried(item.getId(), item.getQuantity(), safe(items.getItemComposition(item.getId()).getName(), 90), location));
    }
    private static List<ItemView> views(List<Carried> source)
    { List<ItemView> result = new ArrayList<>(); if (source != null) for (Carried item : source) result.add(new ItemView(item)); return Collections.unmodifiableList(result); }

    private void readRecovery()
    {
        if (!ready()) return;
        Recovery next = null;
        if (visible(InterfaceID.DeathOffice.UNIVERSE))
        {
            ItemContainer container = client.getItemContainer(InventoryID.DEATH_PERMANENT);
            if (container != null && container.getItems() != null)
            {
                next = recovery("Death's Office", container.getItems(), "");
                ownership.observe(OwnershipLedger.Storage.DEATH_OFFICE, container.getItems());
            }
        }
        else if (visible(InterfaceID.GravestoneGeneric.UNIVERSE))
        {
            ItemContainer container = client.getItemContainer(InventoryID.GRAVESTONE);
            if (container != null && container.getItems() != null)
            {
                next = recovery("Gravestone", container.getItems(), text(InterfaceID.GravestoneGeneric.FEE));
                ownership.observe(OwnershipLedger.Storage.GRAVESTONE, container.getItems());
            }
        }
        else if (visible(InterfaceID.GravestoneRetrieval.UNIVERSE))
        {
            Widget widget = client.getWidget(InterfaceID.GravestoneRetrieval.ITEMS);
            if (widget != null && widget.getDynamicChildren() != null)
            {
                List<Item> found = new ArrayList<>();
                for (Widget child : widget.getDynamicChildren())
                    if (found.size() < MAX_ITEMS && child != null && child.getItemId() > 0 && child.getItemQuantity() > 0)
                        found.add(new Item(child.getItemId(), child.getItemQuantity()));
                next = recovery("Item retrieval", found.toArray(new Item[0]), text(InterfaceID.GravestoneRetrieval.FEE));
            }
        }
        boolean changed = !sameRecovery(observedRecovery, next);
        observedRecovery = next;
        if (next != null && !sameRecovery(saved.recovery, next)) { saved.recovery = next; persist(); }
        if (changed) publish();
    }
    private Recovery recovery(String kind, Item[] contents, String fee)
    {
        Recovery result = new Recovery(); result.kind = kind; result.fee = safe(fee, 180); result.at = System.currentTimeMillis();
        addItems(result.items, contents, kind); return result;
    }
    private boolean visible(int id) { Widget widget = client.getWidget(id); return widget != null && !widget.isHidden(); }
    private String text(int id) { Widget widget = client.getWidget(id); return widget == null || widget.isHidden() ? "" : safe(widget.getText(), 180); }
    static boolean sameRecovery(Recovery one, Recovery two)
    {
        if (one == two) return true; if (one == null || two == null || !Objects.equals(one.kind, two.kind)
            || !Objects.equals(one.fee, two.fee) || one.items.size() != two.items.size()) return false;
        for (int i = 0; i < one.items.size(); i++)
        { Carried a = one.items.get(i), b = two.items.get(i); if (a.id != b.id || a.quantity != b.quantity) return false; }
        return true;
    }
    private void publish()
    {
        if (profile == null) { reading = Reading.EMPTY; refresh(); return; }
        OwnershipLedger.View owned = ownership.snapshot();
        List<DeathView> records = new ArrayList<>(); for (Record row : saved.deaths)
        {
            if (row.returnKit != null) for (LoadoutPlan.Requirement line : row.returnKit.requirements)
            { int id = line.firstId(); if (!kitItemNames.containsKey(id)) { net.runelite.api.ItemComposition composition = items.getItemComposition(id); if (composition != null) kitItemNames.put(id, safe(composition.getName(), 90)); } }
            records.add(new DeathView(row, row.returnKit == null ? null : new KitView(row.returnKit, owned)));
        }
        Recovery recovery = observedRecovery == null ? saved.recovery : observedRecovery;
        reading = new Reading(profile, records, recovery == null ? null : new RecoveryView(recovery, observedRecovery != null)); refresh();
    }
    private void persist() { if (ready()) store.write(GROUP, profile, KEY, 1, saved); }
    private static String safe(String value, int max)
    { String result = value == null ? "" : value.replaceAll("<[^>]*>", "").replace('\n', ' ').replace('\r', ' ').trim(); return result.substring(0, Math.min(max, result.length())); }
    static boolean valid(Saved data)
    {
        if (data == null || data.version != 1 || data.deaths == null || data.deaths.size() > MAX_RECORDS) return false;
        for (Record row : data.deaths)
            if (row == null || row.at <= 0 || row.x < 0 || row.x > 16383 || row.y < 0 || row.y > 16383 || row.plane < 0 || row.plane > 3
                || row.world < 1 || row.world > 20000 || !validItems(row.carried) || !validRecordPlan(row)) return false;
        return data.recovery == null || data.recovery.at > 0 && data.recovery.kind != null && data.recovery.kind.length() <= 60
            && data.recovery.fee != null && data.recovery.fee.length() <= 180 && validItems(data.recovery.items);
    }
    private static boolean validRecordPlan(Record row)
    {
        if (row.guideId != null && (row.guideId.length() > 160 || row.guideId.matches(".*[<>\\r\\n].*"))) return false;
        if (row.recoveryKind != null && !List.of("Gravestone", "Death's Office", "Item retrieval").contains(row.recoveryKind) || row.recoverySeenAt < 0) return false;
        if (row.returnKit != null) try { row.returnKit.checked(); } catch (IllegalArgumentException malformed) { return false; }
        return true;
    }
    static Set<Integer> missingItems(LoadoutPlan plan, OwnershipLedger.View state)
    {
        Map<Integer, Long> carried = new LinkedHashMap<>();
        for (OwnershipLedger.Storage storage : new OwnershipLedger.Storage[]{OwnershipLedger.Storage.INVENTORY, OwnershipLedger.Storage.EQUIPMENT, OwnershipLedger.Storage.QUIVER})
        {
            OwnershipLedger.Observation observation = state.storages.get(storage);
            if (observation != null && observation.currentSession && (storage != OwnershipLedger.Storage.QUIVER || state.quiverAvailable)) observation.quantities.forEach((id, quantity) -> carried.merge(id, quantity, Long::sum));
        }
        if (state.runePouchObserved) state.runePouch.forEach((id, quantity) -> carried.merge(id, quantity, Long::sum));
        Set<Integer> missing = new LinkedHashSet<>();
        for (LoadoutPlan.Requirement requirement : plan.requirements)
        {
            long remaining = requirement.quantity;
            for (int id : requirement.alternatives)
            { long have = carried.getOrDefault(id, 0L), used = Math.min(have, remaining); remaining -= used; carried.put(id, have - used); }
            if (remaining > 0) missing.addAll(requirement.alternatives);
        }
        return Collections.unmodifiableSet(missing);
    }
    private boolean bindSelectedKit(long deathAt)
    {
        if (!ready()) return false; LoadoutPlan choice = selectedLoadout.get(); if (choice == null) return false;
        LoadoutPlan checked; try { checked = choice.checked(); } catch (IllegalArgumentException malformed) { return false; }
        for (Record row : saved.deaths) if (row.at == deathAt)
        { LoadoutPlan previous = row.returnKit; row.returnKit = checked; if (!persistChecked()) { row.returnKit = previous; return false; } publish(); return true; }
        return false;
    }
    private void focusKit(long deathAt)
    {
        if (!ready() || !profile.equals(ownership.snapshot().profile)) return;
        for (Record row : saved.deaths) if (row.at == deathAt && row.returnKit != null) { bankFocus.accept(missingItems(row.returnKit, ownership.snapshot())); break; }
    }
    private boolean linkOpenRecovery(long deathAt)
    {
        if (!ready()) return false; readRecovery(); if (observedRecovery == null) return false;
        for (Record row : saved.deaths) if (row.at == deathAt)
        { String previousKind = row.recoveryKind; long previousAt = row.recoverySeenAt; row.recoveryKind = observedRecovery.kind; row.recoverySeenAt = observedRecovery.at; if (!persistChecked()) { row.recoveryKind = previousKind; row.recoverySeenAt = previousAt; return false; } publish(); return true; }
        return false;
    }
    private boolean persistChecked() { return ready() && store.write(GROUP, profile, KEY, 1, saved); }
    private static boolean validItems(List<Carried> values)
    {
        if (values == null || values.size() > MAX_ITEMS) return false;
        for (Carried item : values) if (item == null || item.id <= 0 || item.quantity <= 0 || item.name == null || item.name.length() > 90
            || item.location == null || item.location.length() > 60) return false;
        return true;
    }
    public JPanel createPanel()
    {
        panel = SidebarUi.column(); panel.add(SidebarUi.heading("Death & recovery"));
        JCheckBox capture = new JCheckBox("Record deaths", enabled); capture.setOpaque(false); capture.setForeground(SidebarUi.TEXT); capture.setFont(GuideText.FONT);
        capture.addActionListener(event -> { config.setConfiguration(GROUP, "enabled", capture.isSelected()); reload(); }); panel.add(capture);
        content = SidebarUi.column(); panel.add(content);
        panel.addHierarchyListener(event -> { if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) refresh(); });
        render(reading, epoch); return panel;
    }
    private void refresh()
    {
        if (content == null || panel == null || !panel.isShowing()) return; Reading captured = reading; long generation = epoch;
        SwingUtilities.invokeLater(() -> { if (content == null || generation != epoch) return; render(captured, generation); });
    }
    private void render(Reading current, long generation)
    {
        content.removeAll();
        if (current.profile == null) content.add(SidebarUi.label("Log in to view recovery."));
        else
        {
            if (current.recovery != null)
            {
                RecoveryView recovery = current.recovery;
                JPanel card = SidebarUi.section(recovery.kind + (recovery.visible ? " · open" : " · last seen"));
                card.add(SidebarUi.label(WHEN.format(Instant.ofEpochMilli(recovery.at))));
                if (!recovery.fee.isEmpty()) card.add(SidebarUi.label(recovery.fee));
                if (recovery.items.isEmpty()) card.add(SidebarUi.label("No items in observed storage."));
                else for (ItemView item : recovery.items) card.add(itemButton(item));
                content.add(card);
            }
            if (current.deaths.isEmpty()) content.add(SidebarUi.label("No recorded deaths."));
            int pages = Math.max(1, (current.deaths.size() + 3) / 4); page = Math.max(0, Math.min(page, pages - 1));
            if (pages > 1)
            {
                content.add(SidebarUi.label("History · " + (page + 1) + " / " + pages));
                content.add(SidebarUi.row(SidebarUi.button("Previous", () -> { page = Math.max(0, page - 1); render(reading, epoch); }),
                    SidebarUi.button("Next", () -> { page = Math.min(pages - 1, page + 1); render(reading, epoch); })));
            }
            for (DeathView death : current.deaths.subList(page * 4, Math.min(current.deaths.size(), (page + 1) * 4)))
            {
                JPanel card = SidebarUi.section(death.acknowledged ? "Recovered · confirmed by you" : "Death recorded");
                card.add(SidebarUi.label(WHEN.format(Instant.ofEpochMilli(death.at)) + " · World " + death.world));
                card.add(SidebarUi.label(death.point.getX() + ", " + death.point.getY() + " · floor " + death.point.getPlane()
                    + (death.instance ? " · instance" : "")));
                if (!death.instance)
                    card.add(SidebarUi.button("Route to recorded location", () -> runGuarded(current.profile, generation, () -> route.accept(death.point))));
                else card.add(SidebarUi.label("Instance · return through its entrance."));
                card.add(SidebarUi.label(death.recoveryKind == null ? "Recovery type · unverified" : death.recoveryKind + " · linked by you"));
                if (current.recovery != null && current.recovery.visible)
                {
                    GuideText linkStatus = SidebarUi.label("");
                    card.add(SidebarUi.button("Link open recovery", () -> runGuarded(current.profile, generation, () -> { boolean linked = linkOpenRecovery(death.at); SwingUtilities.invokeLater(() -> linkStatus.setText(linked ? "Recovery linked" : "Open its native recovery interface first")); }))); card.add(linkStatus);
                }
                addKit(card, current, generation, death);
                if (!death.guideId.isEmpty()) card.add(SidebarUi.button("Return to activity guide", () -> runGuarded(current.profile, generation, () -> resumeGuide.accept(death.guideId))));
                if (!death.acknowledged)
                    card.add(SidebarUi.button("Mark recovered", () -> runGuarded(current.profile, generation, () -> acknowledge(death.at))));
                JPanel carried = SidebarUi.column();
                for (ItemView item : death.carried) carried.add(itemButton(item));
                card.add(SidebarUi.disclosure("Carried before death (" + death.carried.size() + ")", carried));
                content.add(card);
            }
        }
        content.revalidate(); content.repaint();
    }
    private void addKit(JPanel card, Reading current, long generation, DeathView death)
    {
        JPanel kit = SidebarUi.section("Return kit");
        kit.add(SidebarUi.button("Choose return loadout", () -> runGuarded(current.profile, generation, openLoadouts)));
        GuideText status = SidebarUi.label("");
        kit.add(SidebarUi.button("Use selected loadout", () -> runGuarded(current.profile, generation, () -> { boolean bound = bindSelectedKit(death.at); SwingUtilities.invokeLater(() -> status.setText(bound ? "Return kit saved" : "Select a saved return loadout first")); }))); kit.add(status);
        if (death.kit != null)
        {
            KitView view = death.kit; kit.add(SidebarUi.heading(view.plan.name));
            kit.add(SidebarUi.label(view.readiness.itemsReady ? "Required items and spellbook ready" : "Pack / equip the return kit"));
            if (!view.readiness.spellbookKnown) kit.add(SidebarUi.label("Spellbook · unobserved"));
            else if (!view.readiness.spellbookMatches) kit.add(SidebarUi.label("Change to the saved spellbook"));
            int pages = Math.max(1, (view.readiness.lines.size() + 5) / 6);
            int itemPage = Math.max(0, Math.min(kitPages.getOrDefault(death.at,0),pages-1));
            if (pages > 1)
            {
                javax.swing.JLabel counter = new javax.swing.JLabel((itemPage+1)+" / "+pages,javax.swing.SwingConstants.CENTER);counter.setForeground(SidebarUi.TEXT);counter.setFont(GuideText.FONT);
                kit.add(SidebarUi.row(SidebarUi.button("‹",()->{kitPages.put(death.at,Math.max(0,itemPage-1));render(reading,epoch);}),counter,SidebarUi.button("›",()->{kitPages.put(death.at,Math.min(pages-1,itemPage+1));render(reading,epoch);}))); 
            }
            for (LoadoutReadiness.Line line : view.readiness.lines.subList(itemPage*6,Math.min(view.readiness.lines.size(),itemPage*6+6)))
            {
                int id = line.requirement.firstId();
                JPanel row = SidebarUi.column(); JPanel title = new JPanel(new java.awt.BorderLayout(5,0)); title.setOpaque(false);
                javax.swing.JLabel image = new javax.swing.JLabel(); net.runelite.client.util.AsyncBufferedImage icon = items.getImage(id); if (icon != null) icon.addTo(image);
                title.add(image,java.awt.BorderLayout.WEST); title.add(SidebarUi.label((line.ready() ? "✓ " : line.observed ? "! " : "? ") + line.requirement.quantity + " × " + itemName(id)),java.awt.BorderLayout.CENTER); row.add(title);
                row.add(SidebarUi.button("Item details", () -> openItem.accept(id))); kit.add(row);
            }
            for (LoadoutReadiness.CheckLine check : view.readiness.checks) kit.add(SidebarUi.label("? " + check.check.label + " · review before returning"));
            kit.add(SidebarUi.button("Bank missing kit items", () -> runGuarded(current.profile, generation, () -> focusKit(death.at))));
            kit.add(SidebarUi.button("Clear return kit", () -> runGuarded(current.profile, generation, () -> { for (Record row : saved.deaths) if (row.at == death.at) { LoadoutPlan previous = row.returnKit; row.returnKit = null; if (!persistChecked()) row.returnKit = previous; publish(); break; } })));
        }
        card.add(kit);
    }
    private String itemName(int id)
    {
        // Item names were captured on the client thread for the displayed snapshot.
        String name = kitItemNames.get(id); return name == null ? "Item " + id : name;
    }
    private final Map<Integer, String> kitItemNames = new java.util.concurrent.ConcurrentHashMap<>();
    private JPanel itemButton(ItemView item)
    {
        JPanel result = SidebarUi.column(); result.add(SidebarUi.label(item.quantity + " × " + item.name));
        result.add(SidebarUi.button("Item details", () -> openItem.accept(item.id))); return result;
    }
    private void runGuarded(String capturedProfile, long generation, Runnable action)
    { clientThread.invokeLater(() -> { if (generation == epoch && ready() && Objects.equals(capturedProfile, profile)) action.run(); }); }
    private void acknowledge(long at)
    {
        for (Record record : saved.deaths) if (record.at == at) { record.acknowledged = true; persist(); publish(); break; }
    }
}
