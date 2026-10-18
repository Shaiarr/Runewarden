/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
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
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardNotificationsTest
{
    @Test public void loginBaselineAndStaleNativeTextDoNotReplayHistoricalRewards()
    {
        Fixture f = new Fixture();
        f.nativeTitle.set("Collection log"); f.nativeMain.set("New item: Abyssal whip");
        f.controller.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        f.controller.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "New item added to your collection log: Abyssal whip"));
        for (int i = 0; i < 5; i++) f.tick();
        assertNull(f.controller.lastDisplayed()); assertEquals(0, f.controller.queued());
        assertFalse(f.popup.isActive()); verify(f.items, never()).getImage(anyInt(), anyInt(), anyBoolean());
    }

    @Test public void collectionChatAnd3346Then3347NativeFallbackProduceOneCard()
    {
        Fixture f = new Fixture(); assertEquals(3346, ScriptID.NOTIFICATION_START); assertEquals(3347, ScriptID.NOTIFICATION_DELAY);
        f.collection("Abyssal whip"); f.nativeCollection("Abyssal whip"); f.tick();
        RewardNotification first = f.controller.lastDisplayed(); assertNotNull(first);
        assertEquals(RewardNotification.Kind.COLLECTION_LOG, first.kind); assertEquals(4151, first.itemId);
        assertTrue(first.detail.contains("Log 123 / 1456")); assertTrue(first.detail.contains("Ownership rarity"));
        assertEquals(RarityTier.WHITE, first.tier); assertTrue(first.rarityKnown);
        assertTrue(first.ownershipText.contains("37.2%")); assertTrue(first.ownershipText.contains("WikiSync"));
        assertEquals("", first.oddsText); assertEquals(0, f.controller.queued());
        f.nativeCollection("Abyssal whip"); f.collection("Abyssal whip"); f.tick();
        assertSame(first, f.controller.lastDisplayed()); verify(f.items, times(1)).getImage(4151, 1, false);
    }

    @Test public void npcLootAndMirroredNativeEventRetainExactSourceChanceOnce()
    {
        Fixture f = new Fixture(); ServerNpcLoot event = f.npc("Abyssal demon", 4151, 2);
        f.controller.onServerNpcLoot(event); f.controller.onServerNpcLoot(event);
        f.controller.onLootReceived(new LootReceived("Abyssal demon", 1, LootRecordType.NPC,
            Collections.singletonList(new ItemStack(4151, 2)), 1, null));
        f.tick(); assertNull(f.controller.lastDisplayed()); f.tick();
        RewardNotification card = f.controller.lastDisplayed(); assertNotNull(card);
        assertEquals(RewardNotification.Kind.RARE_DROP, card.kind); assertEquals(2, card.quantity);
        assertEquals(RarityTier.BLUE, card.tier); assertEquals("Abyssal demon", card.source);
        assertEquals("1/512", card.oddsText); assertEquals("", card.ownershipText); assertTrue(card.rarityKnown);
        assertEquals(0, f.controller.queued()); verify(f.items, times(1)).getImage(4151, 2, true);
    }

    @Test public void rareDropAndCollectionUnlockFromSameRewardCoalesceBeforeDisplay()
    {
        Fixture f = new Fixture(); f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1));
        f.collection("Abyssal whip"); f.tick();
        RewardNotification card = f.controller.lastDisplayed(); assertEquals(RewardNotification.Kind.COLLECTION_LOG, card.kind);
        assertEquals("Abyssal demon", card.source); assertEquals("1/512", card.oddsText); assertTrue(card.ownershipText.contains("37.2%"));
        f.tick(); assertSame(card, f.controller.lastDisplayed()); assertEquals(0, f.controller.queued());
        verify(f.items, times(1)).getImage(4151, 1, false);
    }

    @Test public void lateCollectionUnlockUpgradesTheActiveRareDropWithoutASecondQueuedCard()
    {
        Fixture f = new Fixture(); f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick();
        assertEquals(RewardNotification.Kind.RARE_DROP, f.controller.lastDisplayed().kind);
        f.collection("Abyssal whip"); f.tick();
        assertEquals(RewardNotification.Kind.COLLECTION_LOG, f.controller.lastDisplayed().kind);
        assertEquals(0, f.controller.queued()); assertTrue(f.popup.isActive());
        verify(f.items, times(2)).getImage(4151, 1, false);
    }

    @Test public void conditionalRaidTableChanceDoesNotBecomeAPerRaidRarity()
    {
        Fixture f = new Fixture();
        f.controller.onLootReceived(new LootReceived("Chambers of Xeric", -1, LootRecordType.EVENT,
            Collections.singletonList(new ItemStack(21043, 1)), 1, null));
        f.tick(); f.tick(); assertNull(f.controller.lastDisplayed());
        f.collection("Kodai insignia"); f.tick(); RewardNotification card = f.controller.lastDisplayed();
        assertEquals(RarityTier.BLUE, card.tier); assertTrue(card.rarityKnown);
        assertTrue(card.oddsText.contains("Purple-table")); assertTrue(card.oddsText.contains("1/30"));
        assertTrue(card.ownershipText.contains("3.6%")); assertTrue(card.detail.contains("Ownership rarity"));

        Fixture chanceOnly = new Fixture(); chanceOnly.settings.update(p -> p.collectionUseOwnership = false);
        chanceOnly.controller.onLootReceived(new LootReceived("Chambers of Xeric", -1, LootRecordType.EVENT,
            Collections.singletonList(new ItemStack(21043, 1)), 1, null));
        chanceOnly.collection("Kodai insignia"); chanceOnly.tick(); RewardNotification unknown = chanceOnly.controller.lastDisplayed();
        assertFalse(unknown.rarityKnown); assertEquals(RarityTier.WHITE, unknown.tier);
        assertFalse(unknown.detail.contains("Drop-chance rarity"));
    }

    @Test public void unknownItemsAndAmbiguousSourcesNeverInventMetadata()
    {
        Fixture unknown = new Fixture(); unknown.collection("Unknown reward not in any catalogue 991"); unknown.tick();
        RewardNotification blank = unknown.controller.lastDisplayed(); assertNotNull(blank);
        assertEquals(-1, blank.itemId); assertEquals(RarityTier.WHITE, blank.tier); assertFalse(blank.rarityKnown);
        assertEquals("", blank.source); assertEquals("", blank.oddsText); assertEquals("", blank.ownershipText);

        Fixture ambiguous = new Fixture(); ambiguous.settings.update(p -> p.collectionUseOwnership = false);
        ambiguous.controller.onServerNpcLoot(ambiguous.npc("Abyssal demon", 4151, 1));
        ambiguous.controller.onServerNpcLoot(ambiguous.npc("Greater abyssal demon", 4151, 1));
        ambiguous.collection("Abyssal whip"); ambiguous.tick();
        RewardNotification card = ambiguous.controller.lastDisplayed(); assertEquals("", card.source); assertEquals("", card.oddsText);
        assertFalse(card.rarityKnown); assertEquals(RarityTier.WHITE, card.tier);
    }

    @Test public void ambiguousCollectionNameDoesNotGuessAChompyHatVariantOrOwnership()
    {
        Fixture f = new Fixture(); f.collection("Chompy bird hat"); f.tick();
        RewardNotification card = f.controller.lastDisplayed(); assertNotNull(card);
        assertEquals("Chompy bird hat", card.itemName); assertEquals(-1, card.itemId);
        assertFalse(card.rarityKnown); assertEquals("", card.ownershipText); assertEquals(RarityTier.WHITE, card.tier);
        verify(f.items, never()).getImage(anyInt(), anyInt(), anyBoolean());
    }

    @Test public void deferredUnlockCallbacksCannotCrossCharactersOrLoginGenerations()
    {
        Fixture f = new Fixture(); List<Runnable> pending = new ArrayList<>();
        doAnswer(call -> { pending.add(call.getArgument(0)); return null; }).when(f.thread).invokeLater(any(Runnable.class));
        f.controller.unlock(unlock("Old character track")); f.profile.set("b"); pending.remove(0).run();
        assertEquals(0, f.controller.queued()); f.tick(); assertNull(f.controller.lastDisplayed());
        f.controller.unlock(unlock("Old login track")); f.controller.clear(); f.tick(); pending.remove(0).run();
        assertEquals(0, f.controller.queued()); assertNull(f.controller.lastDisplayed());
    }

    @Test public void unlockQueueRemainsBoundedAndDoesNotOverlapActivePopups()
    {
        Fixture f = new Fixture();
        for (int i = 0; i < 50; i++) f.controller.unlock(unlock("Track " + i));
        assertEquals(20, f.controller.queued()); f.tick(); assertEquals(19, f.controller.queued());
        assertEquals("Track 30", f.controller.lastDisplayed().itemName); f.tick(); assertEquals(19, f.controller.queued());
        f.clock.set(7_000_000_000L); f.tick(); assertEquals(18, f.controller.queued());
        assertEquals("Track 31", f.controller.lastDisplayed().itemName);
    }

    @Test public void masterSwitchClearsRegularRewardsButExplicitDemoStillWorks()
    {
        Fixture f = new Fixture(); f.controller.unlock(unlock("A newly unlocked track")); f.tick(); assertTrue(f.popup.isActive());
        f.settings.update(p -> p.enabled = false); f.controller.preferencesChanged(); assertFalse(f.popup.isActive());
        assertNull(f.controller.lastDisplayed()); f.controller.unlock(unlock("Disabled track")); f.collection("Abyssal whip"); f.tick();
        assertEquals(0, f.controller.queued()); assertNull(f.controller.lastDisplayed());
        RewardNotification preview = new RewardNotification(RewardNotification.Kind.DEMO, "Preview", "Gold tier", 995, 1,
            RarityTier.GOLD, "", "", "", "", true);
        f.controller.preview(preview); assertSame(preview, f.controller.lastDisplayed()); assertTrue(f.popup.isActive());
    }

    @Test public void explicitPreviewWorksBeforeLoginButStopsWithThePlugin()
    {
        Fixture f = new Fixture(); when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); f.controller.onGameTick();
        RewardNotification preview = new RewardNotification(RewardNotification.Kind.DEMO, "Preview", "Purple tier", 995, 1,
            RarityTier.PURPLE, "", "", "", "", true);
        f.controller.preview(preview); assertSame(preview, f.controller.lastDisplayed()); assertTrue(f.popup.isActive());
        f.controller.onGameTick(); assertTrue(f.popup.isActive());
        f.controller.stop(); f.controller.preview(preview); assertFalse(f.popup.isActive()); assertNull(f.controller.lastDisplayed());
    }

    @Test public void rareDropFiltersRequireBothTierAndValueWithoutFilteringCollectionUnlocks()
    {
        Fixture f = new Fixture(); f.settings.update(p -> { p.minTier = RarityTier.PURPLE; p.valueFloor = 10_000_000; });
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick(); assertNull(f.controller.lastDisplayed());
        f.settings.update(p -> p.minTier = RarityTier.BLUE);
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick(); assertNull(f.controller.lastDisplayed());
        f.settings.update(p -> p.valueFloor = 500_000);
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick(); assertEquals(RewardNotification.Kind.RARE_DROP, f.controller.lastDisplayed().kind);
        f.settings.update(p -> { p.minTier = RarityTier.GOLD; p.valueFloor = Long.MAX_VALUE; });
        f.collection("Abyssal whip"); f.tick(); assertEquals(RewardNotification.Kind.COLLECTION_LOG, f.controller.lastDisplayed().kind);
    }

    @Test public void distinctSameSourceDropsMergePendingQuantityRatherThanLosingAnOccurrence()
    {
        Fixture f = new Fixture();
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1));
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick();
        assertEquals(2, f.controller.lastDisplayed().quantity);
    }

    @Test public void laterSameItemDropBeyondCollectionWindowStillCelebrates()
    {
        Fixture f = new Fixture(); f.collection("Abyssal whip"); f.tick();
        assertEquals(RewardNotification.Kind.COLLECTION_LOG, f.controller.lastDisplayed().kind);
        f.tick(); f.tick(); f.tick();
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick();
        assertEquals(1, f.controller.queued());
        f.clock.set(7_000_000_000L); f.tick();
        assertEquals(RewardNotification.Kind.RARE_DROP, f.controller.lastDisplayed().kind);
        assertEquals("Abyssal demon", f.controller.lastDisplayed().source);
    }

    @Test public void laterCollectionUnlockDoesNotRemoveAnOlderQueuedRareDrop()
    {
        Fixture f = new Fixture();
        f.controller.preview(new RewardNotification(RewardNotification.Kind.DEMO, "Preview", "White tier", 995, 1,
            RarityTier.WHITE, "", "", "", "", true));
        f.controller.onServerNpcLoot(f.npc("Abyssal demon", 4151, 1)); f.tick(); f.tick();
        assertEquals(1, f.controller.queued()); f.tick(); f.tick(); f.tick();
        f.collection("Abyssal whip"); f.tick(); assertEquals(2, f.controller.queued());
        f.clock.set(7_000_000_000L); f.tick();
        assertEquals(RewardNotification.Kind.RARE_DROP, f.controller.lastDisplayed().kind); assertEquals(1, f.controller.queued());
        f.clock.set(14_000_000_000L); f.tick();
        assertEquals(RewardNotification.Kind.COLLECTION_LOG, f.controller.lastDisplayed().kind); assertEquals(0, f.controller.queued());
    }

    private static RewardNotification unlock(String name)
    { return new RewardNotification(RewardNotification.Kind.MUSIC_UNLOCK, "Music unlocked", name, -1, 1, RarityTier.GREEN, "", "", "", "", true); }
    private static ChatMessage chat(ChatMessageType type, String text)
    { ChatMessage event = new ChatMessage(); event.setType(type); event.setMessage(text); return event; }

    private static final class Fixture
    {
        final Client client = mock(Client.class); final ClientThread thread = mock(ClientThread.class);
        final ConfigManager config = mock(ConfigManager.class); final ItemManager items = mock(ItemManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("a"); final AtomicInteger ticks = new AtomicInteger(100);
        final AtomicLong clock = new AtomicLong(); final AtomicReference<String> nativeTitle = new AtomicReference<>(""), nativeMain = new AtomicReference<>("");
        final RewardSettings settings; final RewardPopupOverlay popup; final RewardNotifications controller;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getTickCount()).thenAnswer(call -> ticks.get()); when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT)).thenReturn(123); when(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)).thenReturn(1456);
            when(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)).thenAnswer(call -> nativeTitle.get());
            when(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)).thenAnswer(call -> nativeMain.get());
            when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0)); when(items.getItemPrice(anyInt())).thenReturn(1_000_000L);
            ItemComposition whip = mock(ItemComposition.class); when(whip.getName()).thenReturn("Abyssal whip"); when(items.getItemComposition(4151)).thenReturn(whip);
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            Gson gson = new Gson(); settings = new RewardSettings(config, gson);
            popup = new RewardPopupOverlay(client, items, clock::get, false, 6, 100);
            RewardSoundboard sound = new RewardSoundboard(mock(ScheduledExecutorService.class), mock(net.runelite.client.audio.AudioPlayer.class));
            controller = new RewardNotifications(client, thread, config, items, new RarityCatalog(gson), settings, popup, sound);
            controller.start(); controller.onGameTick();
        }
        void tick() { ticks.incrementAndGet(); controller.onGameTick(); }
        void collection(String name) { controller.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, "New item added to your collection log: " + name)); }
        void nativeCollection(String name)
        {
            nativeTitle.set("Collection log"); nativeMain.set("New item: " + name);
            controller.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START)); controller.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        }
        ServerNpcLoot npc(String source, int id, int quantity)
        { NPCComposition npc = mock(NPCComposition.class); when(npc.getName()).thenReturn(source); return new ServerNpcLoot(npc, Arrays.asList(new ItemStack(id, quantity))); }
    }
}
