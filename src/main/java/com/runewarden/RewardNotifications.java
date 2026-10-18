/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

/** Observed unlock/loot celebrations, isolated to the active character and a small bounded queue. */
@Singleton
final class RewardNotifications
{
    private static final String COLLECTION_MESSAGE = "New item added to your collection log: ";
    static final int QUEUE_LIMIT = 20;
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final ItemManager items;
    private final RarityCatalog rarity;
    private final RewardSettings settings;
    private final RewardPopupOverlay popup;
    private final RewardSoundboard sounds;
    private final ArrayDeque<QueuedReward> queue = new ArrayDeque<>();
    private final Map<Integer, RecentDrop> recent = new LinkedHashMap<>();
    private final Map<String, PendingCollection> collections = new LinkedHashMap<>();
    private final Map<String, PendingDrop> pendingDrops = new LinkedHashMap<>();
    private final Set<String> collectionSeen = new LinkedHashSet<>();
    private final Map<Integer, Integer> recentCollections = new LinkedHashMap<>();
    private final Set<Object> lootSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, Integer> unlockSeen = new LinkedHashMap<>();
    private boolean started, nativeNotificationStarted;
    private String profile;
    private RewardNotification lastDisplayed;
    private int lastDisplayTick;
    private int lastDisplayDropTick = -1;
    private long epoch;
    private int eventTick = -1;

    @Inject RewardNotifications(Client client, ClientThread clientThread, ConfigManager config, ItemManager items,
        RarityCatalog rarity, RewardSettings settings, RewardPopupOverlay popup, RewardSoundboard sounds)
    {
        this.client = client; this.clientThread = clientThread; this.config = config; this.items = items;
        this.rarity = rarity; this.settings = settings; this.popup = popup; this.sounds = sounds;
    }

    void start() { started = true; clear(); }
    void stop() { started = false; clear(); }
    void clear()
    {
        ++epoch; profile = null; queue.clear(); recent.clear(); collections.clear(); pendingDrops.clear();
        collectionSeen.clear(); recentCollections.clear(); unlockSeen.clear(); lootSeen.clear(); eventTick = -1; nativeNotificationStarted = false;
        popup.clear(); sounds.stop();
        lastDisplayed = null;
    }
    int queued() { return queue.size(); }
    RewardNotification lastDisplayed() { return lastDisplayed; }
    void preferencesChanged()
    {
        if (!settings.snapshot().enabled)
        { queue.clear(); collections.clear(); pendingDrops.clear(); popup.clear(); sounds.stop(); lastDisplayed = null; }
    }

    private boolean current()
    {
        return started && profile != null && profile.equals(config.getRSProfileKey())
            && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
    }

    void onGameTick()
    {
        if (!started) return;
        String account = config.getRSProfileKey();
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null || account == null || account.isEmpty())
        { if (profile != null || !queue.isEmpty()) clear(); return; }
        if (!account.equals(profile)) { clear(); profile = account; }
        int tick = client.getTickCount();
        if (eventTick != tick) { eventTick = tick; lootSeen.clear(); }
        if (!settings.snapshot().enabled)
        { queue.clear(); collections.clear(); pendingDrops.clear(); return; }
        recent.entrySet().removeIf(entry -> tick - entry.getValue().tick > 12 || tick < entry.getValue().tick);
        unlockSeen.entrySet().removeIf(entry -> tick - entry.getValue() > 20 || tick < entry.getValue());
        recentCollections.entrySet().removeIf(entry -> tick - entry.getValue() > 20 || tick < entry.getValue());
        List<PendingCollection> ready = new ArrayList<>();
        Iterator<PendingCollection> waiting = collections.values().iterator();
        while (waiting.hasNext())
        {
            PendingCollection entry = waiting.next();
            if (tick > entry.tick) { ready.add(entry); waiting.remove(); }
        }
        for (PendingCollection entry : ready) collection(entry);
        Iterator<PendingDrop> drops = pendingDrops.values().iterator();
        while (drops.hasNext())
        {
            PendingDrop drop = drops.next();
            if (tick - drop.tick >= 2)
            {
                if (!sameRewardTick(drop.tick, recentCollections.get(drop.notification.itemId))) enqueue(drop.notification, drop.tick);
                drops.remove();
            }
        }
        if (!popup.isActive() && !queue.isEmpty())
        { QueuedReward next = queue.removeFirst(); display(next.notification, next.dropTick); }
    }

    void onChatMessage(ChatMessage event)
    {
        if (!current() || event == null || !(event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)) return;
        String text = clean(event.getMessage());
        if (text.startsWith(COLLECTION_MESSAGE)) observeCollection(text.substring(COLLECTION_MESSAGE.length()).trim());
    }

    /** The same read-only native-notification fallback used by RuneLite's Screenshot plugin. */
    void onScriptPreFired(ScriptPreFired event)
    {
        if (!current() || event == null) return;
        if (event.getScriptId() == ScriptID.NOTIFICATION_START) nativeNotificationStarted = true;
        else if (event.getScriptId() == ScriptID.NOTIFICATION_DELAY && nativeNotificationStarted)
        {
            nativeNotificationStarted = false;
            String title = clean(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE));
            String main = clean(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN));
            if ("Collection log".equalsIgnoreCase(title) && main.startsWith("New item:"))
                observeCollection(main.substring("New item:".length()).trim());
        }
    }

    private void observeCollection(String name)
    {
        if (name.isEmpty() || !settings.snapshot().enabled || !settings.snapshot().collectionLogs) return;
        int id = rarity.itemId(name);
        if (id <= 0)
        {
            Set<Integer> candidates = GuideItemResolver.resolveName(name);
            if (candidates.size() == 1) id = candidates.iterator().next();
        }
        String key = itemKey(id, name);
        if (collectionSeen.contains(key) || collections.containsKey(key)) return;
        if (collections.size() >= QUEUE_LIMIT) return;
        collections.put(key, new PendingCollection(id, name, client.getTickCount()));
    }

    private void collection(PendingCollection observed)
    {
        RewardSettings.Preferences preferences = settings.snapshot();
        if (!preferences.collectionLogs) return;
        String key = itemKey(observed.id, observed.name);
        if (!collectionSeen.add(key)) return;
        trim(collectionSeen, 256);
        if (observed.id > 0) { recentCollections.put(observed.id, observed.tick); trim(recentCollections, 256); }
        pendingDrops.values().removeIf(pending -> pending.notification.itemId == observed.id && sameRewardTick(pending.tick, observed.tick));
        queue.removeIf(entry -> entry.notification.kind == RewardNotification.Kind.RARE_DROP
            && entry.notification.itemId == observed.id && sameRewardTick(entry.dropTick, observed.tick));
        RecentDrop drop = recent.get(observed.id);
        String source = drop == null || drop.ambiguous || !sameRewardTick(drop.tick, observed.tick) ? "" : drop.source;
        RarityCatalog.Result reference = rarity.classify(observed.id, source);
        boolean ownership = preferences.collectionUseOwnership && reference.ownershipPercent != null;
        boolean chance = !source.isEmpty() && reference.exactOdds && reference.denominator != null;
        boolean known = ownership || chance;
        RarityTier tier = ownership ? settings.classifyOwnershipPercent(reference.ownershipPercent)
            : chance ? settings.classifyDenominator(reference.denominator) : RarityTier.WHITE;
        String detail = progressText();
        if (known) detail += (detail.isEmpty() ? "" : " · ") + (ownership ? "Ownership rarity" : "Drop-chance rarity");
        RewardNotification notification = new RewardNotification(RewardNotification.Kind.COLLECTION_LOG, "Collection log", observed.name,
            observed.id, 1, tier, detail, source, source.isEmpty() ? "" : reference.oddsText, reference.ownershipText, known);
        if (lastDisplayed != null && lastDisplayed.kind == RewardNotification.Kind.RARE_DROP
            && lastDisplayed.itemId == observed.id && popup.isActive() && sameRewardTick(lastDisplayDropTick, observed.tick))
        {
            // Upgrade the same recent reward card without sounding a second celebration.
            popup.show(notification); lastDisplayed = notification; lastDisplayTick = client.getTickCount();
        }
        else enqueue(notification);
    }

    private String progressText()
    {
        int count = client.getVarpValue(VarPlayerID.COLLECTION_COUNT), maximum = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
        return maximum > 0 && maximum < 100_000 && count >= 0 && count <= maximum ? "Log " + count + " / " + maximum : "";
    }

    void onServerNpcLoot(ServerNpcLoot event)
    {
        if (event != null && event.getComposition() != null)
            loot(event, clean(event.getComposition().getName()), event.getItems());
    }
    void onLootReceived(LootReceived event)
    {
        if (event == null || event.getType() == null || event.getType() == LootRecordType.NPC || event.getType() == LootRecordType.PLAYER) return;
        loot(event, clean(event.getName()), event.getItems());
    }
    private void loot(Object identity, String source, Collection<ItemStack> stacks)
    {
        if (!current() || stacks == null || stacks.isEmpty() || !settings.snapshot().enabled) return;
        int tick = client.getTickCount();
        if (eventTick != tick) { lootSeen.clear(); eventTick = tick; }
        if (lootSeen.size() >= 128 || !lootSeen.add(identity)) return;
        for (ItemStack stack : stacks)
        {
            if (stack == null || stack.getId() <= 0 || stack.getQuantity() <= 0) continue;
            int canonical = items.canonicalize(stack.getId()); int id = canonical > 0 ? canonical : stack.getId();
            RecentDrop before = recent.get(id);
            recent.put(id, new RecentDrop(source, tick, before != null && tick - before.tick <= 2
                && (before.ambiguous || !before.source.equals(source))));
            trim(recent, 128);
            if (!settings.snapshot().rareDrops) continue;
            RarityCatalog.Result reference = rarity.classify(id, source);
            if (source.isEmpty() || !reference.exactOdds || reference.denominator == null) continue;
            RarityTier tier = settings.classifyDenominator(reference.denominator);
            RewardSettings.Preferences preferences = settings.snapshot();
            if (tier.ordinal() < preferences.minTier.ordinal()) continue;
            long price = Math.max(0, items.getItemPrice(id));
            long value = price > Long.MAX_VALUE / stack.getQuantity() ? Long.MAX_VALUE : price * stack.getQuantity();
            if (value < preferences.valueFloor) continue;
            ItemComposition item = items.getItemComposition(id);
            String name = item != null && item.getName() != null ? clean(item.getName()) : rarity.itemName(id);
            if (name == null || name.isEmpty()) name = "Item " + id;
            String pendingKey = id + ":" + source;
            PendingDrop existing = pendingDrops.get(pendingKey);
            if (sameRewardTick(tick, recentCollections.get(id)) || pendingDrops.size() >= QUEUE_LIMIT && existing == null) continue;
            int quantity = existing == null ? stack.getQuantity() : (int) Math.min(Integer.MAX_VALUE,
                (long) existing.notification.quantity + stack.getQuantity());
            RewardNotification notification = new RewardNotification(RewardNotification.Kind.RARE_DROP, "Rare drop", name,
                id, quantity, tier, "", source, reference.oddsText, "", true);
            pendingDrops.put(pendingKey, new PendingDrop(notification, existing == null ? tick : existing.tick));
        }
    }

    /** New-track/achievement observers call this only after a baseline-known completion transition. */
    void unlock(RewardNotification notification)
    {
        if (notification == null) return;
        String owner = profile; long ticket = epoch;
        clientThread.invokeLater(() -> {
            if (!current() || ticket != epoch || owner == null || !owner.equals(profile)) return;
            String key = notification.kind.name() + ":" + notification.itemName;
            if (unlockSeen.putIfAbsent(key, client.getTickCount()) != null) return;
            trim(unlockSeen, 128); enqueue(notification);
        });
    }

    void preview(RewardNotification notification)
    {
        if (notification == null || notification.kind != RewardNotification.Kind.DEMO) return;
        long ticket = epoch;
        clientThread.invokeLater(() -> {
            if (!started || ticket != epoch) return;
            popup.clear(); sounds.stop(); display(notification);
        });
    }

    private void enqueue(RewardNotification notification) { enqueue(notification, -1); }
    private void enqueue(RewardNotification notification, int dropTick)
    {
        RewardSettings.Preferences preferences = settings.snapshot();
        if (!preferences.enabled || !enabled(notification.kind, preferences)) return;
        if (queue.size() >= QUEUE_LIMIT)
        {
            if (notification.kind == RewardNotification.Kind.RARE_DROP) return;
            Iterator<QueuedReward> iterator = queue.iterator(); boolean removed = false;
            while (iterator.hasNext()) if (iterator.next().notification.kind == RewardNotification.Kind.RARE_DROP) { iterator.remove(); removed = true; break; }
            if (!removed) queue.removeFirst();
        }
        queue.addLast(new QueuedReward(notification, dropTick));
    }
    private void display(RewardNotification notification) { display(notification, -1); }
    private void display(RewardNotification notification, int dropTick)
    {
        RewardSettings.Preferences preferences = settings.snapshot();
        if (notification.kind != RewardNotification.Kind.DEMO && (!preferences.enabled || !enabled(notification.kind, preferences))) return;
        popup.show(notification);
        lastDisplayed = notification; lastDisplayTick = client.getTickCount();
        lastDisplayDropTick = dropTick;
        RewardSettings.Sound sound = preferences.sound(notification.tier);
        if (sound != null && sound.enabled && sound.volume > 0)
            sounds.play(RewardSoundboard.SoundPreset.fromId(sound.preset), sound.volume);
    }
    private static boolean enabled(RewardNotification.Kind kind, RewardSettings.Preferences preferences)
    {
        switch (kind)
        {
            case COLLECTION_LOG: return preferences.collectionLogs;
            case RARE_DROP: return preferences.rareDrops;
            case MUSIC_UNLOCK: return preferences.musicUnlocks;
            case COMBAT_ACHIEVEMENT: return preferences.combatAchievements;
            default: return true;
        }
    }
    private static String itemKey(int id, String name) { return id > 0 ? "id:" + id : "name:" + clean(name).toLowerCase(java.util.Locale.ROOT); }
    private static boolean sameRewardTick(int tick, Integer other)
    { return tick >= 0 && other != null && other >= 0 && Math.abs((long) tick - other) <= 2; }
    private static String clean(String value) { return value == null ? "" : Text.removeTags(value).trim(); }
    private static void trim(Set<?> set, int limit) { while (set.size() > limit) set.remove(set.iterator().next()); }
    private static void trim(Map<?, ?> map, int limit) { while (map.size() > limit) map.remove(map.keySet().iterator().next()); }
    private static final class PendingCollection
    { final int id, tick; final String name; PendingCollection(int id, String name, int tick) { this.id = id; this.name = name; this.tick = tick; } }
    private static final class PendingDrop
    { final RewardNotification notification; final int tick; PendingDrop(RewardNotification notification, int tick) { this.notification = notification; this.tick = tick; } }
    private static final class QueuedReward
    { final RewardNotification notification; final int dropTick; QueuedReward(RewardNotification notification, int dropTick) { this.notification = notification; this.dropTick = dropTick; } }
    private static final class RecentDrop
    { final String source; final int tick; final boolean ambiguous; RecentDrop(String source, int tick, boolean ambiguous) { this.source = source; this.tick = tick; this.ambiguous = ambiguous; } }
}
