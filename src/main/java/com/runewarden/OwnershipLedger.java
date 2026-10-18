/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/** Profile-isolated, last-observed ownership. An unobserved storage never means zero. */
@Singleton
public final class OwnershipLedger
{
    public static final String KEY = "ownershipLedgerV1";
    private static final int MAX_JSON = 600_000, MAX_SLOTS = 2_500;
    private static final Set<String> CURRENCY_LABELS=Set.of("Kingdom coffer","Blast Furnace coffer","Slayer points","Tithe Farm points","Giants' Foundry reward points","Nightmare Zone reward points","Soul Wars zeal","Mixology lye points","Mixology aga points","Mixology mox points");
    public enum Storage
    {
        INVENTORY("Inventory", InventoryID.INV), EQUIPMENT("Equipment", InventoryID.WORN), BANK("Bank", InventoryID.BANK),
        SEED_VAULT("Seed vault", InventoryID.SEED_VAULT), SEED_BOX("Seed box", InventoryID.SEED_BOX),
        TACKLE_BOX("Tackle box", InventoryID.TACKLE_BOX), LOOTING_BAG("Looting bag", InventoryID.LOOTING_BAG),
        QUIVER("Quiver", InventoryID.DIZANAS_QUIVER_AMMO), POTION_STORE("Potion storage · doses", -1), GRAVESTONE("Gravestone", -1), DEATH_OFFICE("Death's Office", -1);
        public final String label;
        final int containerId;
        Storage(String label, int containerId) { this.label = label; this.containerId = containerId; }
    }
    public static final class SlotItem
    {
        public final int id;
        public final long quantity;
        SlotItem(int id, long quantity) { this.id = id; this.quantity = quantity; }
        @Override public boolean equals(Object other)
        { return other instanceof SlotItem && ((SlotItem) other).id == id && ((SlotItem) other).quantity == quantity; }
        @Override public int hashCode() { return 31 * id + Long.hashCode(quantity); }
    }
    public static final class Observation
    {
        public final Map<Integer, Long> quantities;
        public final Map<Integer, SlotItem> slots;
        public final long observedAt;
        public final boolean currentSession;
        Observation(Map<Integer, SlotItem> slots, long time, boolean current)
        {
            this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
            Map<Integer, Long> counts = new LinkedHashMap<>();
            slots.values().forEach(item -> counts.merge(item.id, item.quantity, Long::sum));
            quantities = Collections.unmodifiableMap(counts); observedAt = time; currentSession = current;
        }
        public long quantity(int id) { return quantities.getOrDefault(id, 0L); }
    }
    public static final class Balance
    {
        public final long quantity, observedAt;
        public final boolean currentSession;
        Balance(long quantity, long observedAt, boolean currentSession)
        { this.quantity = quantity; this.observedAt = observedAt; this.currentSession = currentSession; }
    }
    public static final class View
    {
        public final boolean active;
        public final String profile;
        public final Map<Storage, Observation> storages;
        public final Integer spellbook;
        public final Map<Integer, Long> runePouch;
        public final boolean runePouchObserved;
        public final boolean quiverAvailable;
        public final Map<String, Balance> currencies;
        View(String profile, Map<Storage, Observation> storages, Integer spellbook, Map<Integer, Long> runes,
            boolean pouchObserved, boolean quiverAvailable, Map<String, Balance> currencies)
        {
            this.profile = profile; active = profile != null;
            EnumMap<Storage, Observation> copy = new EnumMap<>(Storage.class); copy.putAll(storages);
            this.storages = Collections.unmodifiableMap(copy); this.spellbook = spellbook;
            runePouch = Collections.unmodifiableMap(new LinkedHashMap<>(runes)); runePouchObserved = pouchObserved;
            this.quiverAvailable = quiverAvailable;
            this.currencies = Collections.unmodifiableMap(new LinkedHashMap<>(currencies));
        }
        public long quantity(Storage storage, int itemId)
        { Observation value = storages.get(storage); return value == null ? 0L : value.quantity(itemId); }
        public boolean known(Storage storage) { return storages.containsKey(storage); }
        public long potionDoses(Set<Integer> alternatives)
        {
            Observation potions=storages.get(Storage.POTION_STORE);if(potions==null)return 0;
            Set<Integer> counted=new java.util.LinkedHashSet<>();long total=0;
            for(Integer id:alternatives){int one=LoadoutItemVariants.oneDose(id);if(one>0&&counted.add(one))total+=potions.quantity(one);}return total;
        }
    }
    public static final class Quantity
    {
        public final long observedTotal;
        public final Map<Storage, Long> byStorage;
        public final Set<Storage> unknownStorages;
        Quantity(Map<Storage, Long> counts, Set<Storage> unknown)
        {
            byStorage = Collections.unmodifiableMap(counts); unknownStorages = Collections.unmodifiableSet(unknown);
            long total = 0; for (Map.Entry<Storage,Long> entry : counts.entrySet()) if(entry.getKey()!=Storage.POTION_STORE) total += entry.getValue(); observedTotal = total;
        }
        public boolean fullyKnown() { return unknownStorages.isEmpty(); }
    }
    private static final class Saved
    {
        int version = 1;
        Map<String, SavedStorage> storages = new LinkedHashMap<>();
        Map<String, SavedBalance> currencies = new LinkedHashMap<>();
    }
    private static final class SavedStorage { long observedAt; Map<Integer, SlotItem> slots; }
    private static final class SavedBalance { long quantity, observedAt; }
    private static final int[] RUNE_TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
        VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
    private static final int[] RUNE_QUANTITIES = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
        VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6};
    private static final View EMPTY = new View(null, Collections.emptyMap(), null, Collections.emptyMap(), false, false, Collections.emptyMap());
    private final Client client;
    private final ConfigManager config;
    private final Gson gson;
    private final LongSupplier clock;
    private final RunewardenDataStore store;
    private final ItemManager itemManager;
    private final Map<Storage, Observation> observed = new EnumMap<>(Storage.class);
    private final Map<String, Balance> currencies = new LinkedHashMap<>();
    private volatile View view = EMPTY;
    private volatile String profile;
    private volatile boolean started, activeSession;
    private boolean dirty;
    private int ticks;

    @Inject public OwnershipLedger(Client client, ConfigManager config, Gson gson, ItemManager items, RunewardenDataStore store)
    { this(client, config, gson, System::currentTimeMillis, store, items); }
    OwnershipLedger(Client client, ConfigManager config, Gson gson, LongSupplier clock)
    { this(client, config, gson, clock, new RunewardenDataStore(config, gson), null); }
    OwnershipLedger(Client client, ConfigManager config, Gson gson, LongSupplier clock, RunewardenDataStore store)
    { this(client, config, gson, clock, store, null); }
    OwnershipLedger(Client client, ConfigManager config, Gson gson, LongSupplier clock, RunewardenDataStore store, ItemManager items)
    { this.client = client; this.config = config; this.gson = gson; this.clock = clock; this.store = store; itemManager = items; }
    public void start() { started = true; clear(); }
    public void stop() { save(); started = false; clear(); }
    public void clear()
    { save(); activeSession = false; profile = null; observed.clear(); currencies.clear(); view = EMPTY; dirty = false; ticks = 0; }
    private boolean ready()
    { return started && profile != null && profile.equals(config.getRSProfileKey()) && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null; }
    public View snapshot() { return started && activeSession && profile != null && profile.equals(config.getRSProfileKey()) ? view : EMPTY; }
    public Quantity query(int itemId)
    {
        View state = snapshot(); Map<Storage, Long> counts = new EnumMap<>(Storage.class);
        Set<Storage> unknown = EnumSet.allOf(Storage.class);
        state.storages.forEach((storage, values) -> { counts.put(storage, storage==Storage.POTION_STORE ? values.quantity(LoadoutItemVariants.oneDose(itemId)) : values.quantity(itemId)); unknown.remove(storage); });
        return new Quantity(counts, unknown);
    }
    /** Shared native account observations, not inferred currencies or item prices. */
    public void observeCurrency(String label,long amount)
    {if(ready()&&CURRENCY_LABELS.contains(label)&&amount>=0&&amount<=Integer.MAX_VALUE)balance(label,(int)amount);}
    public void onGameTick()
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { activeSession = false; return; }
        String next = config.getRSProfileKey(); if (next == null) return;
        if (!next.equals(profile)) { clear(); profile = next; load(); }
        activeSession = true;
        read(Storage.INVENTORY); read(Storage.EQUIPMENT);
        boolean quiverAvailable = observeWornQuiver();
        Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (bank != null && !bank.isHidden()) read(Storage.BANK);
        Widget potionStore=client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
        if(potionStore!=null&&!potionStore.isHidden())
        {Item[] potions=PotionStorageObservation.read(potionStore.getChildren());if(potions!=null)observe(Storage.POTION_STORE,potions);}
        Integer book = client.getVarbitValue(VarbitID.SPELLBOOK); if (book < 0 || book > 3) book = null;
        Map<Integer, Long> runes = new LinkedHashMap<>();
        boolean hasPouch = hasPouch() && client.getEnum(EnumID.RUNEPOUCH_RUNE) != null;
        if (hasPouch)
            for (int i = 0; i < RUNE_TYPES.length; i++)
            {
                int type = client.getVarbitValue(RUNE_TYPES[i]), count = client.getVarbitValue(RUNE_QUANTITIES[i]);
                int id = type > 0 ? client.getEnum(EnumID.RUNEPOUCH_RUNE).getIntValue(type) : -1;
                if (id > 0 && count > 0) runes.merge(id, (long) count, Long::sum);
            }
        Widget furnace = client.getWidget(InterfaceID.BlastFurnaceHud.DISPLAY);
        if (furnace != null && !furnace.isHidden()) balance("Blast Furnace coffer", client.getVarbitValue(VarbitID.BLAST_FURNACE_COFFER));
        int region = client.getLocalPlayer().getWorldLocation() == null ? -1 : client.getLocalPlayer().getWorldLocation().getRegionID();
        if ((region == 10044 || region == 10300) && client.getVarpValue(VarPlayerID.MISC_QUEST) > 0)
            balance("Kingdom coffer", client.getVarbitValue(VarbitID.MISC_COFFERS));
        view = new View(profile, observed, book, runes, hasPouch, quiverAvailable, currencies);
        if (++ticks % 5 == 0) save();
    }
    public void onItemContainerChanged(int containerId)
    {
        if (!ready()) return;
        for (Storage storage : Storage.values()) if (storage.containerId >= 0 && storage.containerId == containerId)
        {
            if (storage == Storage.BANK)
            { Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS); if (bank == null || bank.isHidden()) return; }
            read(storage); republish(); return;
        }
    }
    /** Accepts verified native observations from another Runewarden module on the game thread. */
    public void observe(Storage storage, Item[] items)
    {
        if (!ready() || storage == null || items == null || items.length > MAX_SLOTS) return;
        Map<Integer, SlotItem> slots = new LinkedHashMap<>();
        for (int i = 0; i < items.length; i++)
            if (items[i] != null && items[i].getId() > 0 && items[i].getQuantity() > 0)
                slots.put(i, new SlotItem(items[i].getId(), items[i].getQuantity()));
        Observation old = observed.get(storage);
        if (old == null || !old.currentSession || !old.slots.equals(slots) || clock.getAsLong() - old.observedAt >= 60_000)
        { observed.put(storage, new Observation(slots, clock.getAsLong(), true)); dirty = true; republish(); }
    }
    private void read(Storage storage)
    { ItemContainer container = client.getItemContainer(storage.containerId); if (container != null) observe(storage, container.getItems()); }
    private boolean hasPouch()
    {
        Observation inventory = observed.get(Storage.INVENTORY); if (inventory == null || !inventory.currentSession) return false;
        return inventory.quantities.containsKey(ItemID.BH_RUNE_POUCH) || inventory.quantities.containsKey(ItemID.BH_RUNE_POUCH_TROUVER)
            || inventory.quantities.containsKey(ItemID.DIVINE_RUNE_POUCH) || inventory.quantities.containsKey(ItemID.DIVINE_RUNE_POUCH_TROUVER);
    }
    private boolean observeWornQuiver()
    {
        Observation worn = observed.get(Storage.EQUIPMENT); if (worn == null || !worn.currentSession || itemManager == null) return false;
        SlotItem cape = worn.slots.get(EquipmentInventorySlot.CAPE.getSlotIdx()); if (cape == null) return false;
        net.runelite.api.ItemComposition definition = itemManager.getItemComposition(cape.id);
        if (definition == null || definition.getIntValue(ParamID.QUIVER_AMMO_AVAILABLE) != 1) return false;
        int id = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO), count = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
        if (count == 0) { observe(Storage.QUIVER, new Item[0]); return true; }
        else if (id > 0 && id <= 1_000_000 && count > 0) { observe(Storage.QUIVER, new Item[]{new Item(id, count)}); return true; }
        return false;
    }
    private void balance(String label, int quantity)
    {
        if (quantity < 0) return; Balance old = currencies.get(label);
        if (old == null || !old.currentSession || old.quantity != quantity)
        { currencies.put(label, new Balance(quantity, clock.getAsLong(), true)); dirty = true; }
    }
    private void republish()
    { view = new View(profile, observed, view.spellbook, view.runePouch, view.runePouchObserved, view.quiverAvailable, currencies); }
    private void load()
    {
        try
        {
            Saved saved = store.read(RunewardenConfig.GROUP, profile, KEY, 1, Saved.class,
                this::validSaved, Saved::new);
            if (saved == null || saved.version != 1 || saved.storages == null || saved.storages.size() > Storage.values().length) return;
            long now = clock.getAsLong();
            for (Storage storage : Storage.values())
            {
                SavedStorage value = saved.storages.get(storage.name());
                if (value == null || value.observedAt <= 0 || value.observedAt > now || value.slots == null || value.slots.size() > MAX_SLOTS) continue;
                Map<Integer, SlotItem> slots = new LinkedHashMap<>(); boolean valid = true;
                for (Map.Entry<Integer, SlotItem> entry : value.slots.entrySet())
                {
                    SlotItem item = entry.getValue(); Integer slot = entry.getKey();
                    if (slot == null || slot < 0 || slot >= MAX_SLOTS || item == null || item.id <= 0 || item.id > 1_000_000 || item.quantity < 1 || item.quantity > Integer.MAX_VALUE) { valid = false; break; }
                    slots.put(slot, item);
                }
                if (valid) observed.put(storage, new Observation(slots, value.observedAt, false));
            }
            if (saved.currencies != null && saved.currencies.size() <= 32)
                saved.currencies.forEach((label, value) -> {
                    if (CURRENCY_LABELS.contains(label) && value != null && value.quantity >= 0
                        && value.quantity <= Integer.MAX_VALUE && value.observedAt > 0 && value.observedAt <= now)
                        currencies.put(label, new Balance(value.quantity, value.observedAt, false));
                });
        }
        catch (RuntimeException ignored) { /* A malformed local snapshot is discarded. */ }
        republish();
    }
    private void save()
    {
        if (!dirty || profile == null || !profile.equals(config.getRSProfileKey())) return;
        Saved saved = new Saved();
        observed.forEach((storage, observation) -> {
            SavedStorage value = new SavedStorage(); value.observedAt = observation.observedAt; value.slots = observation.slots; saved.storages.put(storage.name(), value);
        });
        currencies.forEach((label, balance) -> { SavedBalance value = new SavedBalance(); value.quantity = balance.quantity; value.observedAt = balance.observedAt; saved.currencies.put(label, value); });
        String json = gson.toJson(saved);
        if (json.length() <= MAX_JSON && store.write(RunewardenConfig.GROUP, profile, KEY, 1, saved)) dirty = false;
    }
    private boolean validSaved(Saved saved)
    {
        if (saved == null || saved.version != 1 || saved.storages == null || saved.storages.size() > Storage.values().length
            || saved.currencies == null || saved.currencies.size() > 32) return false;
        long now = clock.getAsLong();
        for (Map.Entry<String, SavedStorage> entry : saved.storages.entrySet())
        {
            try { Storage.valueOf(entry.getKey()); } catch (RuntimeException invalid) { return false; }
            SavedStorage value = entry.getValue();
            if (value == null || value.observedAt <= 0 || value.observedAt > now || value.slots == null || value.slots.size() > MAX_SLOTS) return false;
            for (Map.Entry<Integer, SlotItem> slot : value.slots.entrySet())
            {
                SlotItem item = slot.getValue();
                if (slot.getKey() == null || slot.getKey() < 0 || slot.getKey() >= MAX_SLOTS || item == null || item.id <= 0 || item.id > 1_000_000 || item.quantity < 1 || item.quantity > Integer.MAX_VALUE) return false;
            }
        }
        for (Map.Entry<String, SavedBalance> entry : saved.currencies.entrySet())
        {
            SavedBalance value = entry.getValue();
            if (!CURRENCY_LABELS.contains(entry.getKey()) || value == null
                || value.quantity < 0 || value.quantity > Integer.MAX_VALUE || value.observedAt <= 0 || value.observedAt > now) return false;
        }
        return true;
    }
}
