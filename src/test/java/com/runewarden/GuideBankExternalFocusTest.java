/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GuideBankExternalFocusTest
{
    @Test public void explicitLoadoutFocusWorksWithoutGuideAndReleasesPreviousSearch()
    {
        Fixture fixture = new Fixture(); fixture.filter.onGameTick(); assertFalse(fixture.filter.isActive());
        fixture.filter.setExternalTargets(Collections.singleton(385)); assertTrue(fixture.filter.isActive());
        assertTrue(fixture.matches(385)); assertFalse(fixture.matches(379));
        fixture.filter.clearExternalTargets(); assertFalse(fixture.filter.isActive()); assertEquals("rune", fixture.query.get());
    }
    @Test public void selectingGuideClearsLoadoutFocusAndUserSearchTakesOwnership()
    {
        Fixture fixture = new Fixture(); fixture.filter.onGameTick(); fixture.filter.setExternalTargets(Collections.singleton(385));
        GuideCatalog.Entry guide = new GuideCatalog.Entry(); guide.id = "test:knife"; guide.items = new java.util.ArrayList<>();
        com.google.gson.JsonObject knife = new com.google.gson.JsonObject(); knife.addProperty("id", 946); guide.items.add(knife);
        fixture.guide.set(guide); fixture.filter.onGameTick(); assertTrue(fixture.matches(946)); assertFalse(fixture.matches(385));
        fixture.query.set("shark"); fixture.filter.onGameTick(); assertFalse(fixture.filter.isActive()); assertEquals("shark", fixture.query.get());
    }
    @Test public void closedBankClearsTemporaryLoadoutAndDoesNotReapplyItOnNextVisit()
    {
        Fixture fixture = new Fixture(); fixture.filter.onGameTick(); fixture.filter.setExternalTargets(Collections.singleton(385));
        fixture.filter.onGameTick(); fixture.open.set(false); fixture.filter.onGameTick();
        fixture.open.set(true); fixture.filter.onGameTick(); assertFalse(fixture.filter.isActive());
    }
    @Test public void stoppedModuleCannotBeReactivatedByExternalFocusTicksOrQueuedControls()
    {
        Fixture fixture = new Fixture(); fixture.filter.onGameTick(); fixture.filter.setExternalTargets(Collections.singleton(385));
        assertTrue(fixture.filter.isActive()); fixture.filter.stop(); assertEquals("rune", fixture.query.get());
        fixture.guide.set(guide("test:knife", 946));
        fixture.filter.setExternalTargets(Collections.singleton(385)); fixture.filter.setEnabled(true); fixture.filter.onGameTick();
        assertFalse(fixture.filter.isActive()); assertEquals("rune", fixture.query.get());
        assertTrue(fixture.filter.requiredItemIds().isEmpty());
        int[] stack = new int[]{7, 385}; when(fixture.client.getIntStack()).thenReturn(stack); when(fixture.client.getIntStackSize()).thenReturn(2);
        when(fixture.client.getObjectStack()).thenReturn(new Object[]{GuideBankFilter.QUERY}); when(fixture.client.getObjectStackSize()).thenReturn(1);
        ScriptCallbackEvent callback = new ScriptCallbackEvent(); callback.setEventName("bankSearchFilter"); fixture.filter.onScriptCallbackEvent(callback);
        assertEquals("A stopped filter leaves another script's stack untouched", 7, stack[0]);
        fixture.filter.start(); fixture.filter.onGameTick(); assertTrue(fixture.filter.isActive()); assertTrue(fixture.matches(946));
    }
    @Test public void externalFocusBetweenGuideSelectionAndTickLoadsTheNewGuidesSavedChoices()
    {
        Fixture fixture = new Fixture(); GuideCatalog.Entry first = guide("test:first", 946), second = guide("test:second", 1755);
        fixture.guide.set(first); fixture.filter.onGameTick(); fixture.filter.addChosenItem(221);
        fixture.guide.set(second); fixture.filter.onGameTick(); fixture.filter.addChosenItem(225);
        fixture.guide.set(first); fixture.filter.onGameTick();
        assertTrue(fixture.filter.requiredItems().contains(221)); assertFalse(fixture.filter.requiredItems().contains(225));
        fixture.guide.set(second); fixture.filter.setExternalTargets(Collections.singleton(385));
        fixture.filter.clearExternalTargets(); fixture.filter.onGameTick();
        assertTrue("New guide's saved choice is restored", fixture.filter.requiredItems().contains(225));
        assertFalse("Previous guide's choice is not carried across", fixture.filter.requiredItems().contains(221));
        assertTrue(fixture.matches(1755)); assertTrue(fixture.matches(225)); assertFalse(fixture.matches(221));
    }
    private static GuideCatalog.Entry guide(String id, int itemId)
    {
        GuideCatalog.Entry guide = new GuideCatalog.Entry(); guide.id = id; guide.items = new java.util.ArrayList<>();
        com.google.gson.JsonObject item = new com.google.gson.JsonObject(); item.addProperty("id", itemId); guide.items.add(item); return guide;
    }
    private static final class Fixture
    {
        final Client client = mock(Client.class); final AtomicReference<String> query = new AtomicReference<>("rune");
        final AtomicReference<Boolean> open = new AtomicReference<>(true); final AtomicReference<GuideCatalog.Entry> guide = new AtomicReference<>();
        final GuideBankFilter filter;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); Player player = mock(Player.class); when(player.getName()).thenReturn("One"); when(client.getLocalPlayer()).thenReturn(player);
            Widget widget = mock(Widget.class); when(widget.isHidden()).thenAnswer(call -> !open.get()); when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(widget);
            when(client.getVarcStrValue(VarClientID.MESLAYERINPUT)).thenAnswer(call -> query.get());
            doAnswer(call -> { query.set(call.getArgument(1)); return null; }).when(client).setVarcStrValue(eq(VarClientID.MESLAYERINPUT), anyString());
            when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.SEARCH.getType());
            ItemManager items = mock(ItemManager.class); when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
            net.runelite.client.config.ConfigManager config = mock(net.runelite.client.config.ConfigManager.class);
            java.util.Map<String, String> settings = new java.util.HashMap<>();
            when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(call -> settings.get(call.getArgument(1)));
            doAnswer(call -> { settings.put(call.getArgument(1), call.getArgument(2)); return null; })
                .when(config).setRSProfileConfiguration(anyString(), anyString(), anyString());
            filter = new GuideBankFilter(client, mock(ClientThread.class), mock(BankSearch.class), guide::get, items, config); filter.start();
        }
        boolean matches(int id)
        {
            int[] stack = new int[]{0, id}; when(client.getIntStack()).thenReturn(stack); when(client.getIntStackSize()).thenReturn(2);
            when(client.getObjectStack()).thenReturn(new Object[]{GuideBankFilter.QUERY}); when(client.getObjectStackSize()).thenReturn(1);
            ScriptCallbackEvent callback = new ScriptCallbackEvent(); callback.setEventName("bankSearchFilter"); filter.onScriptCallbackEvent(callback); return stack[0] == 1;
        }
    }
}
