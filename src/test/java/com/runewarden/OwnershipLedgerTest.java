/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OwnershipLedgerTest
{
    @Test public void nativeActivityCurrenciesPersistTogetherWithCoffersAndStayProfileBound()
    {
        Fixture f=new Fixture();f.ledger.onGameTick();
        String[] labels={"Kingdom coffer","Blast Furnace coffer","Slayer points","Tithe Farm points","Giants' Foundry reward points","Nightmare Zone reward points","Soul Wars zeal","Mixology lye points","Mixology aga points","Mixology mox points"};
        for(int i=0;i<labels.length;i++)f.ledger.observeCurrency(labels[i],100+i);
        f.ledger.observeCurrency("Invented currency",123);f.ledger.stop();f.clock.addAndGet(1000);f.ledger.start();f.ledger.onGameTick();
        assertEquals(10,f.ledger.snapshot().currencies.size());
        for(int i=0;i<labels.length;i++){OwnershipLedger.Balance value=f.ledger.snapshot().currencies.get(labels[i]);assertEquals(100+i,value.quantity);assertFalse(value.currentSession);}
        f.profile.set("rsprofile.characterB");assertTrue(f.ledger.snapshot().currencies.isEmpty());f.ledger.observeCurrency("Slayer points",1000);f.ledger.onGameTick();assertTrue(f.ledger.snapshot().currencies.isEmpty());
    }
    @Test public void unseenBankIsUnknownButObservedEmptyBankIsKnown()
    {
        Fixture fixture = new Fixture(); fixture.ledger.onGameTick();
        assertTrue(fixture.ledger.query(995).unknownStorages.contains(OwnershipLedger.Storage.BANK));
        fixture.bank(new Item[0]); fixture.ledger.onGameTick();
        assertFalse(fixture.ledger.query(995).unknownStorages.contains(OwnershipLedger.Storage.BANK));
        assertEquals(0, fixture.ledger.snapshot().quantity(OwnershipLedger.Storage.BANK, 995));
    }
    @Test public void nativeContainerEventsPreserveExactIdsSlotsAndIgnorePlaceholders()
    {
        Fixture fixture = new Fixture(); fixture.ledger.onGameTick();
        fixture.ledger.observe(OwnershipLedger.Storage.SEED_BOX, new Item[]{new Item(5295, 30), new Item(5295, 7), new Item(1, 0), new Item(-1, 1)});
        OwnershipLedger.Observation values = fixture.ledger.snapshot().storages.get(OwnershipLedger.Storage.SEED_BOX);
        assertEquals(37L, values.quantities.get(5295).longValue()); assertEquals(2, values.slots.size());
        assertTrue(values.currentSession); assertEquals(100_000L, values.observedAt);
        try { values.quantities.put(1, 1L); fail("Immutable"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void lastObservedBankSurvivesRestartWithTimestampAndStaleStatus()
    {
        Fixture fixture = new Fixture(); fixture.bank(new Item[]{new Item(995, 500)}); fixture.ledger.onGameTick(); fixture.ledger.stop();
        fixture.bankWidget = null; fixture.clock.set(200_000); fixture.ledger.start(); fixture.ledger.onGameTick();
        OwnershipLedger.Observation old = fixture.ledger.snapshot().storages.get(OwnershipLedger.Storage.BANK);
        assertNotNull(old); assertFalse(old.currentSession); assertEquals(100_000, old.observedAt); assertEquals(500, old.quantity(995));
    }
    @Test public void accountChangeImmediatelyHidesPreviousCharacterAndNeverWritesIntoNewProfile()
    {
        Fixture fixture = new Fixture(); fixture.bank(new Item[]{new Item(995, 500)}); fixture.ledger.onGameTick();
        fixture.profile.set("rsprofile.characterB"); assertFalse(fixture.ledger.snapshot().active); assertEquals(0, fixture.ledger.query(995).observedTotal);
        fixture.bankWidget = null; fixture.ledger.onGameTick(); assertFalse(fixture.ledger.snapshot().known(OwnershipLedger.Storage.BANK));
        assertFalse(fixture.settings.containsKey("runewarden|rsprofile.characterB|" + OwnershipLedger.KEY));
    }
    @Test public void futureSchemaSnapshotIsRetainedAndCannotBeSilentlyOverwritten()
    {
        Fixture fixture = new Fixture(); String address = "runewarden|rsprofile.characterA|" + OwnershipLedger.KEY;
        fixture.settings.put(address, "{\"version\":99,\"storages\":{}}");
        fixture.bank(new Item[]{new Item(995, 1)}); for (int i = 0; i < 10; i++) fixture.ledger.onGameTick();
        assertEquals("{\"version\":99,\"storages\":{}}", fixture.settings.get(address));
    }
    @Test public void extraStoragesAreObservedOnlyFromActualNonNullContainerEvents()
    {
        Fixture fixture = new Fixture(); fixture.ledger.onGameTick();
        fixture.ledger.onItemContainerChanged(InventoryID.SEED_VAULT);
        assertFalse(fixture.ledger.snapshot().known(OwnershipLedger.Storage.SEED_VAULT));
        fixture.container(InventoryID.SEED_VAULT, new Item[]{new Item(5315, 3)});
        fixture.ledger.onItemContainerChanged(InventoryID.SEED_VAULT);
        assertEquals(3L, fixture.ledger.query(5315).byStorage.get(OwnershipLedger.Storage.SEED_VAULT).longValue());
    }
    @Test public void noRunePouchMeansUnknownContentsRatherThanClaimedEmptyPouch()
    {
        Fixture fixture = new Fixture(); fixture.ledger.onGameTick();
        assertFalse(fixture.ledger.snapshot().runePouchObserved); assertTrue(fixture.ledger.snapshot().runePouch.isEmpty());
        assertEquals(Integer.valueOf(0), fixture.ledger.snapshot().spellbook);
        when(fixture.client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(8); fixture.ledger.onGameTick();
        assertNull(fixture.ledger.snapshot().spellbook);
    }
    @Test public void quiverUsesVerifiedNativeCapeParameterAndDoesNotRemainCarriedAfterUnequipping()
    {
        Fixture fixture = new Fixture(); net.runelite.client.game.ItemManager items = mock(net.runelite.client.game.ItemManager.class);
        net.runelite.api.ItemComposition cape = mock(net.runelite.api.ItemComposition.class);
        when(cape.getIntValue(net.runelite.api.ParamID.QUIVER_AMMO_AVAILABLE)).thenReturn(1); when(items.getItemComposition(28955)).thenReturn(cape);
        Item[] worn = new Item[14]; java.util.Arrays.fill(worn, new Item(-1, 0)); worn[1] = new Item(28955, 1); fixture.container(InventoryID.WORN, worn);
        when(fixture.client.getVarpValue(net.runelite.api.gameval.VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(892);
        when(fixture.client.getVarpValue(net.runelite.api.gameval.VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)).thenReturn(250);
        Gson gson = new Gson(); OwnershipLedger ledger = new OwnershipLedger(fixture.client, fixture.config, gson, fixture.clock::get, new RunewardenDataStore(fixture.config, gson), items);
        ledger.start(); ledger.onGameTick(); assertTrue(ledger.snapshot().quiverAvailable); assertEquals(250, ledger.snapshot().quantity(OwnershipLedger.Storage.QUIVER, 892));
        fixture.container(InventoryID.WORN, new Item[0]); ledger.onGameTick(); assertFalse(ledger.snapshot().quiverAvailable);
        LoadoutPlan plan = new LoadoutPlan("one", "Quiver", java.util.Collections.singletonList(new LoadoutPlan.Requirement(java.util.Collections.singleton(892), 100, LoadoutPlan.Location.QUIVER, -1)), null);
        assertFalse(LoadoutReadiness.check(plan, ledger.snapshot()).ready);
    }
    @Test public void pouchRequiresNativeEnumBeforeItsContentsAreReportedKnown()
    {
        Fixture fixture = new Fixture(); fixture.container(InventoryID.INV, new Item[]{new Item(net.runelite.api.gameval.ItemID.BH_RUNE_POUCH, 1)});
        fixture.ledger.onGameTick(); assertFalse(fixture.ledger.snapshot().runePouchObserved);
        net.runelite.api.EnumComposition runes = mock(net.runelite.api.EnumComposition.class); when(runes.getIntValue(1)).thenReturn(556);
        when(fixture.client.getEnum(net.runelite.api.EnumID.RUNEPOUCH_RUNE)).thenReturn(runes);
        when(fixture.client.getVarbitValue(VarbitID.RUNE_POUCH_TYPE_1)).thenReturn(1); when(fixture.client.getVarbitValue(VarbitID.RUNE_POUCH_QUANTITY_1)).thenReturn(500);
        fixture.ledger.onGameTick(); assertTrue(fixture.ledger.snapshot().runePouchObserved); assertEquals(500L, fixture.ledger.snapshot().runePouch.get(556).longValue());
    }
    @Test public void immutableSidebarQueriesNeverReadNativeClientState()
    {
        Fixture fixture = new Fixture(); fixture.ledger.onGameTick(); clearInvocations(fixture.client);
        assertTrue(fixture.ledger.snapshot().active); fixture.ledger.query(995);
        verify(fixture.client, never()).getGameState(); verify(fixture.client, never()).getLocalPlayer(); verify(fixture.client, never()).getItemContainer(anyInt());
    }
    static final class Fixture
    {
        final Client client = mock(Client.class);
        final ConfigManager config = mock(ConfigManager.class);
        final AtomicReference<String> profile = new AtomicReference<>("rsprofile.characterA");
        final AtomicLong clock = new AtomicLong(100_000);
        final Map<String, String> settings = new HashMap<>();
        final Map<Integer, ItemContainer> containers = new HashMap<>();
        final OwnershipLedger ledger;
        Widget bankWidget;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); Player player = mock(Player.class);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0)); when(client.getLocalPlayer()).thenReturn(player);
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(), nullable(String.class), anyString())).thenAnswer(call -> settings.get(call.getArgument(0) + "|" + call.getArgument(1) + "|" + call.getArgument(2)));
            doAnswer(call -> { settings.put(call.getArgument(0) + "|" + call.getArgument(1) + "|" + call.getArgument(2), call.getArgument(3)); return null; })
                .when(config).setConfiguration(anyString(), nullable(String.class), anyString(), anyString());
            when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenAnswer(call -> bankWidget);
            when(client.getItemContainer(anyInt())).thenAnswer(call -> containers.get(call.getArgument(0)));
            container(InventoryID.INV, new Item[0]); container(InventoryID.WORN, new Item[0]);
            ledger = new OwnershipLedger(client, config, new Gson(), clock::get); ledger.start();
        }
        void bank(Item[] values) { bankWidget = mock(Widget.class); container(InventoryID.BANK, values); }
        void container(int id, Item[] values) { ItemContainer container = mock(ItemContainer.class); when(container.getItems()).thenReturn(values); containers.put(id, container); }
    }
}
