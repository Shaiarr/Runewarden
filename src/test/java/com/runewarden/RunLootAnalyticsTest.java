/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunLootAnalyticsTest
{
    @Test public void nativeWidthKeepsRunStatsReadableAndCostsCollapsed() throws Exception
    {
        Harness h = new Harness();
        try
        {
            h.loot(440, 10);
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup());
                javax.swing.JPanel panel = h.tool.createPanel(); panel.setSize(206, 2000); layout(panel);
                panel.setSize(206, panel.getPreferredSize().height); layout(panel);
                assertEquals(0, components(panel, javax.swing.JTextField.class).stream().filter(field -> visible(panel, field)).count());
                assertFalse(components(panel, javax.swing.JButton.class).stream().anyMatch(button -> "‹".equals(button.getText()) && visible(panel, button)));
                for (javax.swing.JButton button : components(panel, javax.swing.JButton.class)) if (visible(panel, button))
                { assertTrue(button.getWidth() >= 90); assertTrue(button.getWidth() <= 206); }
                savePreview(panel, "run-loot");
                components(panel, javax.swing.JButton.class).stream().filter(button -> "Costs ▸".equals(button.getText())).findFirst().get().doClick();
                layout(panel);
                javax.swing.JTextField input = components(panel, javax.swing.JTextField.class).get(0);
                assertTrue(visible(panel, input)); assertTrue(input.getWidth() >= 184);
            });
        }
        finally { h.stop(); }
    }

    private static boolean visible(java.awt.Container root, java.awt.Component child)
    { for (java.awt.Component current = child; current != null && current != root; current = current.getParent()) if (!current.isVisible()) return false; return true; }
    private static void layout(java.awt.Container parent)
    { parent.doLayout(); for (java.awt.Component child : parent.getComponents()) if (child instanceof java.awt.Container) layout((java.awt.Container) child); }
    private static <T> java.util.List<T> components(java.awt.Container parent, Class<T> type)
    {
        java.util.List<T> result = new java.util.ArrayList<>();
        for (java.awt.Component child : parent.getComponents())
        { if (type.isInstance(child)) result.add(type.cast(child)); if (child instanceof java.awt.Container) result.addAll(components((java.awt.Container) child, type)); }
        return result;
    }
    private static void savePreview(javax.swing.JPanel panel, String name)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(206, Math.max(1, panel.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        java.io.File file = new java.io.File("build/reports/" + name + "-206px.png"); file.getParentFile().mkdirs();
        try { javax.imageio.ImageIO.write(image, "png", file); } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    @Test public void heartbeatReusesImmutableLootAndHistorySnapshots() throws Exception
    {
        Harness h = new Harness();
        try
        {
            h.loot(440, 10); RunLootTool.Reading first = h.tool.reading();
            h.tool.onGameTick(); RunLootTool.Reading second = h.tool.reading();
            assertSame(first.items, second.items); assertSame(first.sources, second.sources); assertSame(first.history, second.history);
            h.tool.newRun(second.profile, second.epoch); RunLootTool.Reading saved = h.tool.reading();
            h.loot(440, 1); RunLootTool.Reading nextDrop = h.tool.reading();
            assertSame(saved.history, nextDrop.history); assertNotSame(saved.items, nextDrop.items);
        }
        finally { h.stop(); }
    }

    @Test public void itemLimitAggregatesOverflowWithoutLosingObservedQuantitiesOrValue() throws Exception
    {
        Harness h = new Harness();
        try
        {
            java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
            for (int i = 0; i < 2050; i++) stacks.add(new ItemStack(10_000 + i, 2));
            when(h.items.getItemPrice(12_049)).thenReturn(0L);
            NPCComposition npc = mock(NPCComposition.class); when(npc.getName()).thenReturn("Goblin");
            h.tool.onServerNpcLoot(new ServerNpcLoot(npc, stacks));
            RunLootTool.Reading current = h.tool.reading();
            assertEquals(2049, current.items.size()); assertEquals(409_800, current.value); assertEquals(1, current.unpricedItems);
            RunLootTool.LootItem extra = current.items.stream().filter(item -> item.aggregate).findFirst().get();
            assertEquals(4, extra.quantity); assertEquals(200, extra.value());
        }
        finally { h.stop(); }
    }

    @Test public void ratesUseActiveElapsedTimeAndNetIncludesExplicitExpenseTotal()
    {
        RunLootTool.Reading reading = new RunLootTool.Reading(true, "a", 1, false, 1_800_000,
            Collections.singletonList(new RunLootTool.LootItem(440, "Iron ore", 100, 100, null)),
            Collections.emptyMap(), 3000, Collections.emptyList());
        assertEquals(10_000, reading.value); assertEquals(7000, reading.netValue);
        assertEquals(20_000, reading.gpPerHour); assertEquals(14_000, reading.netPerHour);
        assertEquals(-4000, RunLootTool.perHour(-2000, 1_800_000));
        assertEquals(0, RunLootTool.perHour(1000, 0));
        assertEquals(Long.MAX_VALUE, RunLootTool.perHour(Long.MAX_VALUE, 1));
    }

    @Test public void finishSavesObservedGrossCostAndUnpricedTotalsThenPausesFreshRun() throws Exception
    {
        Harness h = new Harness();
        try
        {
            h.loot(440, 10); when(h.items.getItemPrice(100)).thenReturn(0L); h.loot(100, 1);
            RunLootTool.Reading before = h.tool.reading(); h.tool.setManualCost(before.profile, before.epoch, 300);
            h.tool.finishRun(before.profile, before.epoch);
            RunLootTool.Reading after = h.tool.reading();
            assertEquals(0, after.value); assertEquals(0, after.manualCost); assertTrue(after.paused);
            assertEquals(1, after.history.size());
            RunLootTool.RunSummary run = after.history.get(0);
            assertEquals(1000, run.lootValue); assertEquals(300, run.manualCost); assertEquals(700, run.net());
            assertEquals(1, run.unpricedItems); assertEquals(Long.valueOf(2), run.sources.get("NPC · Goblin"));
            assertNotNull(h.values.get("a"));
            h.tool.setManualCost(before.profile, before.epoch, 9000);
            assertEquals(0, h.tool.reading().manualCost);
            h.tool.finishRun(after.profile, after.epoch); assertEquals(1, h.tool.reading().history.size());
        }
        finally { h.stop(); }
    }

    @Test public void newRunArchivesAndHistoryIsBoundedAndRestoredOnlyForMatchingCharacter() throws Exception
    {
        Harness h = new Harness();
        try
        {
            for (int i = 0; i < 55; i++)
            { h.loot(440, i + 1); RunLootTool.Reading run = h.tool.reading(); h.tool.newRun(run.profile, run.epoch); }
            assertEquals(50, h.tool.reading().history.size());
            assertEquals(5500, h.tool.reading().history.get(0).lootValue);
            assertEquals(600, h.tool.reading().history.get(49).lootValue);
            h.profile.set("b"); h.tool.onGameTick(); assertTrue(h.tool.reading().history.isEmpty());
            h.profile.set("a"); h.tool.onGameTick(); assertEquals(50, h.tool.reading().history.size());
            h.tool.stop(); h.tool.start(); h.tool.onGameTick(); assertEquals(50, h.tool.reading().history.size());
        }
        finally { h.stop(); }
    }

    @Test public void staleCostFinishAndHistoryRemovalActionsCannotWriteAnotherCharacter() throws Exception
    {
        Harness h = new Harness();
        try
        {
            h.loot(440, 10); RunLootTool.Reading run = h.tool.reading(); h.tool.newRun(run.profile, run.epoch);
            RunLootTool.Reading old = h.tool.reading(); String oldId = old.history.get(0).id;
            h.profile.set("b");
            h.tool.setManualCost(old.profile, old.epoch, 1000); h.tool.finishRun(old.profile, old.epoch);
            h.tool.removeSavedRun(old.profile, old.epoch, oldId);
            h.tool.onGameTick(); assertEquals(0, h.tool.reading().manualCost); assertTrue(h.tool.reading().history.isEmpty());
            assertNull(h.values.get("b"));
            h.profile.set("a"); h.tool.onGameTick(); assertEquals(1, h.tool.reading().history.size());
            RunLootTool.Reading current = h.tool.reading(); h.tool.removeSavedRun(current.profile, current.epoch, oldId);
            assertTrue(h.tool.reading().history.isEmpty());
            h.tool.stop(); h.tool.start(); h.tool.onGameTick(); assertTrue(h.tool.reading().history.isEmpty());
        }
        finally { h.stop(); }
    }

    @Test public void manualFeesCanProduceLossWithoutInventedInventoryExpensesAndMalformedHistoryIsIgnored() throws Exception
    {
        Harness h = new Harness();
        try
        {
            RunLootTool.Reading run = h.tool.reading(); h.tool.setManualCost(run.profile, run.epoch, -1);
            assertEquals(0, h.tool.reading().manualCost);
            h.tool.setManualCost(run.profile, run.epoch, 1000); h.tool.finishRun(run.profile, run.epoch);
            assertEquals(-1000, h.tool.reading().history.get(0).net());
            h.tool.onGameTick(); verify(h.client, never()).getItemContainer(anyInt());
            h.values.put("a", "{invalid"); h.tool.stop(); h.tool.start(); h.tool.onGameTick();
            assertTrue(h.tool.reading().history.isEmpty());
        }
        finally { h.stop(); }
    }

    private static final class Harness
    {
        final Gson gson = new Gson(); final Client client = mock(Client.class); final ItemManager items = mock(ItemManager.class);
        final ConfigManager config = mock(ConfigManager.class); final AtomicReference<String> profile = new AtomicReference<>("a");
        final Map<String, String> values = new HashMap<>(); final RunLootTool tool;
        Harness()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq("runLootHistoryV1")))
                .thenAnswer(call -> values.get(call.getArgument(1)));
            doAnswer(call -> { values.put(call.getArgument(1), call.getArgument(3)); return null; })
                .when(config).setConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq("runLootHistoryV1"), anyString());
            when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0)); when(items.getItemPrice(anyInt())).thenReturn(100L);
            tool = new RunLootTool(client, mock(ClientThread.class), config, items, mock(LootManager.class), gson);
            tool.start(); tool.onGameTick();
        }
        void loot(int item, int quantity)
        {
            NPCComposition npc = mock(NPCComposition.class); when(npc.getName()).thenReturn("Goblin");
            tool.onServerNpcLoot(new ServerNpcLoot(npc, Arrays.asList(new ItemStack(item, quantity))));
        }
        void stop() throws Exception { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
}
