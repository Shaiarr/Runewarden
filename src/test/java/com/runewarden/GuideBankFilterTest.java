/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GuideBankFilterTest
{
    private Client client;
    private BankSearch bankSearch;
    private Widget widget;
    private AtomicReference<String> query;
    private AtomicReference<GuideCatalog.Entry> selected;
    private GuideBankFilter filter;

    @Before public void setUp()
    {
        client = mock(Client.class); bankSearch = mock(BankSearch.class); widget = mock(Widget.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        query = new AtomicReference<>("rune"); selected = new AtomicReference<>();
        when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(widget);
        when(client.getVarcStrValue(VarClientID.MESLAYERINPUT)).thenAnswer(i -> query.get());
        when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.SEARCH.getType());
        doAnswer(i -> { query.set(i.getArgument(1)); return null; }).when(client).setVarcStrValue(eq(VarClientID.MESLAYERINPUT), anyString());
        ItemManager items = mock(ItemManager.class); when(items.canonicalize(anyInt())).thenAnswer(i -> i.getArgument(0));
        filter = new GuideBankFilter(client, mock(ClientThread.class), bankSearch, selected::get, items);
        filter.start();
    }

    private void selectGuide()
    {
        GuideCatalog.Entry guide = new GuideCatalog.Entry(); guide.title = "Glass run";
        JsonObject glass = new JsonObject(); glass.addProperty("name", "Molten glass");
        JsonObject unknown = new JsonObject(); unknown.addProperty("name", "Your best combat gear");
        guide.items = Arrays.asList(glass, unknown); selected.set(guide);
    }

    @Test public void opensAutomaticTemporarySearchAndRestoresUsersPriorSearchOnGuideClear()
    {
        selectGuide(); filter.onGameTick();
        assertTrue(filter.isActive()); assertEquals(GuideBankFilter.QUERY, query.get());
        assertTrue(filter.requiredItems().contains(1775));
        selected.set(null); filter.onGameTick();
        assertFalse(filter.isActive()); assertEquals("rune", query.get());
        verify(bankSearch).reset(true);
    }

    @Test public void closingBankRestoresQueryWithoutReopeningAnyInterface()
    {
        selectGuide(); filter.onGameTick(); clearInvocations(bankSearch);
        when(widget.isHidden()).thenReturn(true); filter.onGameTick();
        assertEquals("rune", query.get()); assertFalse(filter.isActive());
        verifyNoInteractions(bankSearch);
    }

    @Test public void guideDismissalReleasesItsSearchAndRequirementsBeforeAnotherGameTick()
    {
        selectGuide();filter.onGameTick();assertFalse(filter.requiredItems().isEmpty());
        selected.set(null);filter.onGuideDismissed();assertTrue(filter.requiredItems().isEmpty());
        assertFalse(filter.isActive());assertEquals("rune",query.get());
    }

    @Test public void guideDismissalPreservesAnIndependentlySelectedSupplyFocus()
    {
        net.runelite.api.Player player=mock(net.runelite.api.Player.class);when(client.getLocalPlayer()).thenReturn(player);when(player.getName()).thenReturn("Player");
        selectGuide();filter.onGameTick();filter.setExternalTargets("loadout",java.util.Set.of(385));
        selected.set(null);filter.onGuideDismissed();assertEquals(java.util.Set.of(385),filter.requiredItemIds());assertTrue(filter.isActive());
        filter.onGameTick();assertEquals(java.util.Set.of(385),filter.requiredItemIds());
        filter.clearExternalTargets("loadout");assertTrue(filter.requiredItemIds().isEmpty());
    }

    @Test public void userOrOtherPluginTakingSearchOwnershipIsRespected()
    {
        selectGuide(); filter.onGameTick(); query.set("tag:potions"); clearInvocations(bankSearch);
        filter.onGameTick(); assertFalse(filter.isActive());
        filter.onGameTick(); filter.stop();
        assertEquals("tag:potions", query.get()); verifyNoInteractions(bankSearch);
    }

    @Test public void callbackOnlyFiltersItsOwnQueryAndExactRequiredItemIds()
    {
        selectGuide(); filter.onGameTick();
        int[] stack = {0, 1775}; Object[] strings = {GuideBankFilter.QUERY};
        when(client.getIntStack()).thenReturn(stack); when(client.getIntStackSize()).thenReturn(2);
        when(client.getObjectStack()).thenReturn(strings); when(client.getObjectStackSize()).thenReturn(1);
        ScriptCallbackEvent event = new ScriptCallbackEvent(); event.setEventName("bankSearchFilter");
        filter.onScriptCallbackEvent(event); assertEquals(1, stack[0]);
        stack[1] = 995; filter.onScriptCallbackEvent(event); assertEquals(0, stack[0]);
        strings[0] = "coins"; stack[0] = 1; filter.onScriptCallbackEvent(event); assertEquals(1, stack[0]);
    }

    @Test public void variableItemGuideStillActivatesItsOwnFilter()
    {
        GuideCatalog.Entry guide = new GuideCatalog.Entry(); guide.title = "Boss";
        JsonObject item = new JsonObject(); item.addProperty("name", "Your best combat gear");
        guide.items = Arrays.asList(item); selected.set(guide); filter.onGameTick();
        assertTrue(filter.isActive()); assertEquals(GuideBankFilter.QUERY, query.get());
        assertFalse(filter.supplyPlan().unresolved.isEmpty());
    }

    @Test public void itemNameResolverSupportsPotionDoseChoicesAndToolsWithoutInventedIds()
    {
        JsonObject item = new JsonObject(); item.addProperty("name", "Defence Potion (3) or (4)");
        assertTrue(GuideItemResolver.resolveAll(item).contains(133));
        assertTrue(GuideItemResolver.resolveAll(item).contains(2432));
        item.addProperty("name", "Any axe"); assertTrue(GuideItemResolver.resolveAll(item).contains(1351));
        assertFalse(GuideItemResolver.resolveAll(item).contains(1265));
    }

    @Test public void actualTrainingGuidesFilterExplicitPreparationItemsAndFarmingFamilies()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        selected.set(catalog.entry("training:allotment-runs")); filter.onGameTick();
        assertTrue(filter.isActive());
        assertTrue(filter.requiredItems().contains(5318)); // Potato seeds
        assertTrue(filter.requiredItems().contains(5341)); // Rake
        assertTrue(filter.requiredItems().contains(952)); // Spade
        assertTrue(filter.requiredItems().contains(5343)); // Seed dibber
        assertTrue(filter.requiredItems().contains(6032)); // Compost
        selected.set(catalog.entry("training:cut-diamonds")); filter.onGameTick();
        assertTrue(filter.requiredItems().contains(1755)); // Chisel
        assertTrue(filter.requiredItems().contains(1617)); // Uncut diamond
        assertFalse(filter.requiredItems().contains(1601)); // Finished diamond isn't an input
        selected.set(catalog.entry("training:trees-willow")); filter.onGameTick();
        assertTrue(filter.requiredItems().contains(1351));
        selected.set(catalog.entry("training:shrimp-fishing")); filter.onGameTick();
        assertTrue(filter.requiredItems().contains(303));
        selected.set(catalog.entry("training:air-battlestaves")); filter.onGameTick();
        assertTrue(filter.requiredItems().containsAll(Arrays.asList(1391, 573)));
        selected.set(catalog.entry("training:karambwan")); filter.onGameTick();
        assertTrue(filter.requiredItems().containsAll(Arrays.asList(3157, 3159, 3150)));
        selected.set(catalog.entry("minigame:fishing-trawler")); filter.onGameTick();
        assertTrue(filter.requiredItems().containsAll(Arrays.asList(583, 954, 1941)));
    }

    @Test public void bundledFilterCoverageIsMeasuredWithoutGuessingCombatLoadouts()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson()); java.util.Map<String, Integer> coverage = new java.util.TreeMap<>();
        for (GuideCatalog.Entry guide : catalog.search("", null))
        {
            selected.set(guide); filter.onGameTick();
            if (!filter.requiredItems().isEmpty()) coverage.merge(guide.category, 1, Integer::sum);
        }
        System.out.println("Exact-item filter coverage: " + coverage);
        assertTrue(coverage.getOrDefault("training", 0) >= 140);
        assertEquals("Quest Helper owns quest bank supplies",Integer.valueOf(0),Integer.valueOf(coverage.getOrDefault("quest", 0)));
        assertTrue(coverage.getOrDefault("minigame", 0) >= 1);

    }

    @Test public void everySelectedGuideAcquiresTheFilterIncludingVerifiedItemFreeGuides()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        for (GuideCatalog.Entry guide : catalog.search("", null))
        {
            selected.set(guide); filter.onGameTick();
            if ("quest".equals(guide.category)) { assertFalse(guide.id, filter.isActive()); continue; }
            assertTrue(guide.id, filter.isActive()); assertEquals(GuideBankFilter.QUERY, query.get());
        }
        selected.set(catalog.entry("minigame:rogues-den")); filter.onGameTick();
        assertTrue(filter.requiredItems().isEmpty()); assertTrue(filter.status().contains("No bank supplies needed"));
    }

    @Test public void quantityAndOtherChatPromptsAreNotStolenByAutomaticSearch()
    {
        selectGuide(); when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(7);
        filter.onGameTick(); assertFalse(filter.isActive()); verify(bankSearch, never()).initSearch();
        when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(InputType.NONE.getType());
        filter.onGameTick(); assertTrue(filter.isActive()); verify(bankSearch).initSearch();
    }

    @Test public void chosenRequirementIsPersistedPerAccountAndGuide()
    {
        net.runelite.api.Player player = mock(net.runelite.api.Player.class);
        when(player.getName()).thenReturn("Cathy Wampus"); when(client.getLocalPlayer()).thenReturn(player);
        net.runelite.client.config.ConfigManager config = mock(net.runelite.client.config.ConfigManager.class);
        java.util.Map<String, String> settings = new java.util.HashMap<>();
        when(config.getRSProfileConfiguration(anyString(), anyString())).thenAnswer(i -> settings.get(i.getArgument(1)));
        doAnswer(i -> { settings.put(i.getArgument(1), i.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(anyString(), anyString(), anyString());
        ItemManager manager = mock(ItemManager.class); when(manager.canonicalize(anyInt())).thenAnswer(i -> i.getArgument(0));
        filter = new GuideBankFilter(client, mock(ClientThread.class), bankSearch, selected::get, manager, config);
        filter.start();
        selectGuide(); selected.get().id = "variable:one"; filter.onGameTick(); filter.addChosenItem(221);
        assertTrue(filter.requiredItems().contains(221)); assertEquals(1, settings.size());
        GuideCatalog.Entry first = selected.get(); selected.set(new GuideCatalog(new Gson()).entry("training:copper-tin")); filter.onGameTick();
        assertFalse(filter.requiredItems().contains(221)); selected.set(first); filter.onGameTick();
        assertTrue(filter.requiredItems().contains(221));
        when(player.getName()).thenReturn("Other account"); filter.onGameTick(); assertFalse(filter.requiredItems().contains(221));
    }

    @Test public void requirementPickerFitsNarrowSidebarWithLongPendingText() throws Exception
    {
        selected.set(new GuideCatalog(new Gson()).entry("minigame:gnome-restaurant"));
        javax.swing.JPanel[] panel = new javax.swing.JPanel[1];
        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = filter.createPanel());
        filter.onGameTick();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            assertFalse("The real variable-item fixture must have a pending choice", filter.supplyPlan().unresolved.isEmpty());
            String pending = filter.supplyPlan().unresolved.get(0);
            panel[0].setSize(201, 4000);
            for (int pass = 0; pass < 9; pass++) layout(panel[0]);
            java.util.List<java.awt.Component> controls = descendants(panel[0]);
            assertTrue("Complete pending requirement remains visible", controls.stream()
                .filter(c -> c instanceof GuideText && visibleWithin(c, panel[0]))
                .map(c -> (GuideText) c).anyMatch(c -> c.getText().contains(pending)));
            for (java.awt.Component control : controls)
            {
                if (!(control instanceof GuideText || control instanceof javax.swing.JComboBox
                    || control instanceof javax.swing.JTextField || control instanceof javax.swing.AbstractButton)) continue;
                if (!visibleWithin(control, panel[0])) continue;
                java.awt.Container parent = control.getParent();
                assertTrue("Control clipped at left: " + control.getClass().getSimpleName(), control.getX() >= 0);
                assertTrue("Control exceeds actual 201px layout: " + control.getClass().getSimpleName(),
                    control.getX() + control.getWidth() <= parent.getWidth());
                if (control instanceof GuideText)
                {
                    assertTrue("Wrapped requirement lost its height: " + ((GuideText) control).getText(),
                        control.getHeight() >= control.getPreferredSize().height);
                    assertTrue("Requirement must use its available card width",
                        control.getWidth() >= parent.getWidth() - parent.getInsets().left - parent.getInsets().right - 1);
                }
            }
        });
    }

    private static void layout(java.awt.Container parent)
    {
        parent.invalidate(); parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) if (child instanceof java.awt.Container) layout((java.awt.Container) child);
    }
    private static java.util.List<java.awt.Component> descendants(java.awt.Container parent)
    {
        java.util.List<java.awt.Component> result = new java.util.ArrayList<>();
        for (java.awt.Component child : parent.getComponents())
        { result.add(child); if (child instanceof java.awt.Container) result.addAll(descendants((java.awt.Container) child)); }
        return result;
    }
    private static boolean visibleWithin(java.awt.Component child, java.awt.Container root)
    {
        for (java.awt.Component current = child; current != root; current = current.getParent())
            if (current == null || !current.isVisible()) return false;
        return true;
    }
}
