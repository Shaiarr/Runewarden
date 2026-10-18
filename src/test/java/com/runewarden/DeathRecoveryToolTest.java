package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DeathRecoveryToolTest
{
    @Test public void loginBaselineAndNonPlayerDeathsDoNotCreateHistory()
    {
        Fixture f = new Fixture(); f.tool.start(); f.tool.onActorDeath(new ActorDeath(f.player));
        f.tick(); f.tick(); f.tool.onActorDeath(new ActorDeath(f.player));
        assertTrue(f.tool.snapshot().deaths.isEmpty());
        f.tick(); f.tool.onActorDeath(new ActorDeath(mock(Player.class)));
        assertTrue(f.tool.snapshot().deaths.isEmpty());
    }
    @Test public void deathRecordsCarriedSnapshotAndDeduplicatesUntilRespawn()
    {
        Fixture f = new Fixture(); f.warm();
        f.tool.onActorDeath(new ActorDeath(f.player)); f.tool.onActorDeath(new ActorDeath(f.player));
        assertEquals(1, f.tool.snapshot().deaths.size());
        DeathRecoveryTool.DeathView death = f.tool.snapshot().deaths.get(0);
        assertEquals(new WorldPoint(3200, 3200, 0), death.point); assertEquals(301, death.world);
        assertEquals(2, death.carried.size()); assertFalse(death.acknowledged);
        assertEquals("Inventory before death", death.carried.get(0).location);
        assertEquals("Worn before death", death.carried.get(1).location);
        when(f.client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(0); f.tick();
        f.tool.onActorDeath(new ActorDeath(f.player)); assertEquals(1, f.tool.snapshot().deaths.size());
    }
    @Test public void accountSwitchAndReconnectRestoreOnlyOwnSavedHistory()
    {
        Fixture f = new Fixture(); f.warm(); f.tool.onActorDeath(new ActorDeath(f.player));
        f.profile.set("rsprofile.bob"); f.tick(); assertTrue(f.tool.snapshot().deaths.isEmpty());
        f.profile.set("rsprofile.alice"); f.tick(); assertEquals(1, f.tool.snapshot().deaths.size());
        GameStateChanged hop = new GameStateChanged(); hop.setGameState(GameState.HOPPING); f.tool.onGameStateChanged(hop);
        assertTrue(f.tool.snapshot().deaths.isEmpty()); f.tick(); assertEquals(1, f.tool.snapshot().deaths.size());
        // Reconnect state does not arm another death immediately.
        f.tool.onActorDeath(new ActorDeath(f.player)); assertEquals(1, f.tool.snapshot().deaths.size());
    }
    @Test public void hiddenRetrievalContainerDoesNotEstablishStorage()
    {
        Fixture f = new Fixture(); f.container(InventoryID.GRAVESTONE, new Item(4151, 1)); f.warm();
        assertNull(f.tool.snapshot().recovery); assertFalse(f.ledger.snapshot().known(OwnershipLedger.Storage.GRAVESTONE));
    }
    @Test public void visibleNativeGraveAndOfficeAreSeparateAndFeeIsActualWidgetText()
    {
        Fixture f = new Fixture(); f.warm();
        Widget grave = f.show(InterfaceID.GravestoneGeneric.UNIVERSE);
        Widget fee = f.show(InterfaceID.GravestoneGeneric.FEE); when(fee.getText()).thenReturn("<col=ff0000>Reclaim fee: 100,000 coins</col>");
        f.container(InventoryID.GRAVESTONE, new Item(4151, 1)); f.tick();
        assertEquals("Gravestone", f.tool.snapshot().recovery.kind);
        assertEquals("Reclaim fee: 100,000 coins", f.tool.snapshot().recovery.fee);
        assertEquals(1, f.ledger.snapshot().quantity(OwnershipLedger.Storage.GRAVESTONE, 4151));
        when(grave.isHidden()).thenReturn(true); f.show(InterfaceID.DeathOffice.UNIVERSE);
        f.container(InventoryID.DEATH_PERMANENT, new Item(11802, 1)); f.tick();
        assertEquals("Death's Office", f.tool.snapshot().recovery.kind);
        assertEquals(1, f.ledger.snapshot().quantity(OwnershipLedger.Storage.DEATH_OFFICE, 11802));
        assertEquals(1, f.ledger.snapshot().quantity(OwnershipLedger.Storage.GRAVESTONE, 4151));
    }
    @Test public void genericRetrievalUsesObservedItemChildrenWithoutAssumingBossOrGrave()
    {
        Fixture f = new Fixture(); f.warm(); f.show(InterfaceID.GravestoneRetrieval.UNIVERSE);
        Widget itemList = f.show(InterfaceID.GravestoneRetrieval.ITEMS); Widget child = mock(Widget.class);
        when(child.getItemId()).thenReturn(4151); when(child.getItemQuantity()).thenReturn(1);
        when(itemList.getDynamicChildren()).thenReturn(new Widget[] {child}); f.tick();
        assertEquals("Item retrieval", f.tool.snapshot().recovery.kind); assertEquals(4151, f.tool.snapshot().recovery.items.get(0).id);
        assertFalse(f.ledger.snapshot().known(OwnershipLedger.Storage.GRAVESTONE));
        assertFalse(f.ledger.snapshot().known(OwnershipLedger.Storage.DEATH_OFFICE));
    }
    @Test public void emptyObservedStorageDoesNotClaimAllDeathsRecovered()
    {
        Fixture f = new Fixture(); f.warm(); f.tool.onActorDeath(new ActorDeath(f.player));
        f.show(InterfaceID.GravestoneGeneric.UNIVERSE); f.container(InventoryID.GRAVESTONE); f.tick();
        assertTrue(f.tool.snapshot().recovery.items.isEmpty()); assertFalse(f.tool.snapshot().deaths.get(0).acknowledged);
    }
    @Test public void futureVersionHistoryIsPreservedAndQuarantined()
    {
        Fixture f = new Fixture(); String future = "{\"version\":99,\"deaths\":[]}";
        f.settings.put(Fixture.key(DeathRecoveryTool.GROUP, "rsprofile.alice", DeathRecoveryTool.KEY), future); f.warm();
        f.tool.onActorDeath(new ActorDeath(f.player));
        assertEquals(future, f.settings.get(Fixture.key(DeathRecoveryTool.GROUP, "rsprofile.alice", DeathRecoveryTool.KEY)));
        assertTrue(f.store.blocked(DeathRecoveryTool.GROUP, "rsprofile.alice", DeathRecoveryTool.KEY));
    }
    @Test public void invalidSavedCoordinatesAndUnboundedItemsAreRejected()
    {
        DeathRecoveryTool.Saved saved = new DeathRecoveryTool.Saved();
        DeathRecoveryTool.Record death = new DeathRecoveryTool.Record(); death.at = 1; death.world = 301; death.x = 16384; saved.deaths.add(death);
        assertFalse(DeathRecoveryTool.valid(saved)); death.x = 3200; assertTrue(DeathRecoveryTool.valid(saved));
        for (int i = 0; i < 65; i++) death.carried.add(new DeathRecoveryTool.Carried(1, 1, "Item", "Inventory"));
        assertFalse(DeathRecoveryTool.valid(saved));
    }

    static final class Fixture
    {
        final Client client = mock(Client.class); final Player player = mock(Player.class); final ConfigManager config = mock(ConfigManager.class);
        final ItemManager items = mock(ItemManager.class); final AtomicReference<String> profile = new AtomicReference<>("rsprofile.alice");
        final AtomicInteger tick = new AtomicInteger(); final Map<String, String> settings = new HashMap<>();
        final RunewardenDataStore store; final OwnershipLedger ledger; final DeathRecoveryTool tool;
        Fixture()
        {
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(), anyString())).thenAnswer(call -> settings.get(key(call.getArgument(0), null, call.getArgument(1))));
            when(config.getConfiguration(anyString(), nullable(String.class), anyString())).thenAnswer(call -> settings.get(key(call.getArgument(0), call.getArgument(1), call.getArgument(2))));
            doAnswer(call -> { settings.put(key(call.getArgument(0), null, call.getArgument(1)), String.valueOf((Object) call.getArgument(2))); return null; })
                .when(config).setConfiguration(anyString(), anyString(), any());
            doAnswer(call -> { settings.put(key(call.getArgument(0), call.getArgument(1), call.getArgument(2)), String.valueOf((Object) call.getArgument(3))); return null; })
                .when(config).setConfiguration(anyString(), nullable(String.class), anyString(), any());
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0)); when(client.getWorld()).thenReturn(301);
            when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(99); when(client.getTickCount()).thenAnswer(call -> tick.get());
            when(items.getItemComposition(anyInt())).thenAnswer(call -> { ItemComposition item = mock(ItemComposition.class); when(item.getName()).thenReturn("Item " + call.getArgument(0)); return item; });
            container(InventoryID.INV, new Item(385, 12)); container(InventoryID.WORN, new Item(4151, 1));
            store = new RunewardenDataStore(config, new Gson()); ledger = new OwnershipLedger(client, config, new Gson(), items, store); ledger.start();
            tool = new DeathRecoveryTool(client, config, mock(ClientThread.class), items, store, ledger);
        }
        static String key(String group, String profile, String key) { return group + "/" + profile + "/" + key; }
        void container(int id, Item... contents)
        { ItemContainer container = mock(ItemContainer.class); when(container.getItems()).thenReturn(contents); when(client.getItemContainer(id)).thenReturn(container); }
        Widget show(int id)
        { Widget widget = mock(Widget.class); when(client.getWidget(id)).thenReturn(widget); return widget; }
        void tick() { tick.incrementAndGet(); ledger.onGameTick(); tool.onGameTick(); }
        void warm() { tool.start(); tick(); tick(); tick(); }
    }
}
