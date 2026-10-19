/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Container;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SidebarRefreshPerformanceTest
{
    @Test public void effectRefreshCoalescesQueuedTicksAndReusesLabelsWithoutLosingLatestOrLogout() throws Exception
    {
        Client client = mock(Client.class); ConfigManager config = mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("alice");
        when(client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME)).thenReturn(400);
        AtomicInteger tick = new AtomicInteger(); when(client.getTickCount()).thenAnswer(call -> tick.get());
        FieldCoach tool = new FieldCoach(client, config, new RunewardenConfig() {}, mock(ItemManager.class),
            mock(ItemStatChanges.class), new HoverCardOverlay(client), mock(Notifier.class));
        tool.start(); tool.onGameTick();
        SwingUtilities.invokeAndWait(tool::createPanel);
        JPanel panel = (JPanel) get(tool, "panel"), values = (JPanel) get(tool, "values");
        GuideText label = (GuideText) values.getComponent(0);
        AtomicInteger writes = new AtomicInteger();
        label.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent event) { writes.incrementAndGet(); }
            @Override public void removeUpdate(DocumentEvent event) { }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        SwingUtilities.invokeAndWait(() ->
        {
            panel.addNotify();
            assertTrue(panel.isShowing());
            for (int i = 1; i <= 100; i++) { tick.set(i); tool.onGameTick(); }
            assertTrue(((AtomicBoolean) get(tool, "refreshQueued")).get());
            clearInvocations(config);
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertSame("Tick updates must reuse the existing Swing label", label, values.getComponent(0));
        assertEquals("Divine combat · 3:00", label.getText());
        assertEquals("Only the latest queued display is applied", 1, writes.get());
        verify(config, times(2)).getRSProfileKey();
        SwingUtilities.invokeAndWait(() -> { tool.onGameTick(); clearInvocations(config); });
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals("Unchanged visible text must not be rewritten", 1, writes.get());
        assertSame(tool.knownEffectKeys(), tool.knownEffectKeys());
        try { tool.knownEffectKeys().clear(); fail("Known effects must stay immutable"); }
        catch (UnsupportedOperationException expected) { }
        SwingUtilities.invokeAndWait(() ->
        {
            panel.removeNotify();
            tool.onGameTick();
            assertFalse(((AtomicBoolean) get(tool, "refreshQueued")).get());
            panel.addNotify();
            tool.onGameTick(); // Queue a previously valid account reading.
            when(config.getRSProfileKey()).thenReturn("bob");
        });
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals("Log in to view effects.", ((GuideText) values.getComponent(0)).getText());
        tool.stop(); SwingUtilities.invokeAndWait(panel::removeNotify);
    }

    @Test public void harvestRefreshIndexesOnceKeepsAllChoicesAndStopsPollingHiddenPanels() throws Exception
    {
        Client client = mock(Client.class); ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("alice");
        when(config.getConfiguration(RunewardenConfig.GROUP, "alice", HarvestSelections.KEY)).thenReturn("[]");
        HarvestClockTool tool = new HarvestClockTool(client, config, mock(PluginManager.class),
            mock(OverlayManager.class), new RunewardenConfig() {});
        tool.start();
        try
        {
            HarvestSelections selections = (HarvestSelections) get(tool, "selections");
            HarvestSelections.Session session = selections.bind("alice");
            List<HarvestClockTool.Harvest> rows = new ArrayList<>();
            for (String id : HarvestSelections.GROUPS)
                rows.add(new HarvestClockTool.Harvest(id, id, SummaryState.IN_PROGRESS, Long.MAX_VALUE / 2, true));
            HarvestClockTool.Reading reading = new HarvestClockTool.Reading(true, false, "", rows);
            CountingList counted = new CountingList(reading.harvests); set(reading, "harvests", counted);
            Class<?> type = Class.forName("com.runewarden.HarvestClockTool$Observation");
            Constructor<?> constructor = type.getDeclaredConstructor(HarvestClockTool.Reading.class, HarvestSelections.Session.class);
            constructor.setAccessible(true); set(tool, "observation", constructor.newInstance(reading, session));
            SwingUtilities.invokeAndWait(() ->
            {
                tool.createPanel(); tool.createDashboardPanel();
                ((Timer) get(tool, "panelTimer")).stop();
            });
            JPanel timers = (JPanel) get(tool, "panel"), dashboard = (JPanel) get(tool, "dashboard");
            List<JCheckBox> timerChoices = choices(timers), dashboardChoices = choices(dashboard);
            assertEquals(24, timerChoices.size()); assertEquals(24, dashboardChoices.size());
            counted.visits = 0; clearInvocations(config);
            SwingUtilities.invokeAndWait(() -> invoke(tool, "refreshPanel"));
            assertTrue("48 controls use the cached index, not 48 scans of 24 observations: " + counted.visits,
                counted.visits <= 24);
            assertTrue("Account validity is checked once for all controls",
                mockingDetails(config).getInvocations().size() <= 10);
            SwingUtilities.invokeAndWait(timerChoices.get(0)::doClick);
            assertTrue(dashboardChoices.get(0).isSelected());
            assertEquals(Set.of(timerChoices.get(0).getClientProperty("harvestGroup")), tool.selectedGroupIds());
            SwingUtilities.invokeAndWait(() ->
            {
                Timer timer = (Timer) get(tool, "panelTimer");
                clearInvocations(config);
                for (int i = 0; i < 100; i++)
                    timer.getActionListeners()[0].actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, "timer"));
                verifyNoInteractions(config);
                timers.addNotify();
                assertTrue(timers.isShowing());
                assertFalse("Showing a hidden panel must refresh it immediately", mockingDetails(config).getInvocations().isEmpty());
                timers.removeNotify();
                when(config.getRSProfileKey()).thenReturn("bob");
                clearInvocations(config);
                timerChoices.get(0).doClick();
                assertTrue(timerChoices.stream().noneMatch(JCheckBox::isEnabled));
                assertTrue(dashboardChoices.stream().noneMatch(JCheckBox::isEnabled));
                verify(config, never()).setConfiguration(anyString(), anyString(), anyString(), any());
            });
        }
        finally { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    private static final class CountingList extends AbstractList<HarvestClockTool.Harvest>
    {
        final List<HarvestClockTool.Harvest> rows; int visits;
        CountingList(List<HarvestClockTool.Harvest> rows) { this.rows = rows; }
        @Override public HarvestClockTool.Harvest get(int index) { visits++; return rows.get(index); }
        @Override public int size() { return rows.size(); }
    }
    private static List<JCheckBox> choices(Container parent)
    {
        List<JCheckBox> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (child instanceof JCheckBox) result.add((JCheckBox) child);
            else if (child instanceof Container) result.addAll(choices((Container) child));
        }
        return result;
    }
    private static Object get(Object target, String name)
    {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(Object target, String name, Object value)
    {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void invoke(Object target, String name)
    {
        try { Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
