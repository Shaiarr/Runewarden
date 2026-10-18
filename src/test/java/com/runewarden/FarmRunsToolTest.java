/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmRunsToolTest
{
    @Test public void pickerUsesStandaloneRunTypesAndNativeTimerGroupsOpenCorrespondingRuns() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                assertEquals(FarmPatchPlanner.runTypes().size(), f.picker().getItemCount());
                assertFalse(f.tool.openGroup("special"));assertFalse(f.tool.openGroup("grape"));assertFalse(f.tool.openGroup("contract"));
                assertEquals("herb",f.planner.currentRun().runId);
                f.tool.openGroup("fruit-tree"); f.tool.refresh(); assertEquals("fruit", f.planner.currentRun().runId);
                f.tool.openGroup("bird-house"); f.tool.refresh(); assertEquals("birdhouse", f.planner.currentRun().runId);
                f.tool.openGroup("flower"); f.tool.refresh(); assertEquals("allotment", f.planner.currentRun().runId);
            });
        }
        finally { f.close(); }
    }
    @Test public void refreshKeepsPickerAndStopControlsWhileRunActionsStayExplicit() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JComboBox<?> crops = named(f.panel, "runewarden.farm-runs.crop", JComboBox.class);
                JCheckBox include = controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get();
                f.tool.refresh();
                assertSame(crops, named(f.panel, "runewarden.farm-runs.crop", JComboBox.class));
                assertSame(include, controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get());
                assertFalse(f.planner.currentRun().active);
                button(f.panel, "Start run").doClick(); f.tool.refresh();
                assertTrue(f.planner.currentRun().active); assertFalse(crops.isEnabled()); assertFalse(f.picker().isEnabled());
                button(f.panel, "End run").doClick(); f.tool.refresh();
                assertFalse(f.planner.currentRun().active); assertTrue(crops.isEnabled());
            });
        }
        finally { f.close(); }
    }
    @Test public void oldLocationCheckboxCannotEditNextAccountsPlan() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JCheckBox old = controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get();
                f.profile.set("account-b"); for (int i = 0; i < 6; i++) f.tool.onGameTick();
                FarmPatchPlanner.RunReading before = f.planner.currentRun();
                long selected = before.stops.stream().filter(stop -> stop.selected).count();
                old.doClick();
                assertEquals(selected, f.planner.currentRun().stops.stream().filter(stop -> stop.selected).count());
                assertEquals("account-b", f.planner.currentRun().profile);
            });
        }
        finally { f.close(); }
    }
    private static JButton button(Container root, String text)
    { return controls(root, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().get(); }
    private static <T extends Component> T named(Container root, String name, Class<T> type)
    { return controls(root, type).stream().filter(component -> name.equals(component.getName())).findFirst().get(); }
    private static <T> List<T> controls(Container root, Class<T> type)
    {
        List<T> found = new ArrayList<>();
        for (Component component : root.getComponents())
        {
            if (type.isInstance(component)) found.add(type.cast(component));
            if (component instanceof Container) found.addAll(controls((Container) component, type));
        }
        return found;
    }
    private static final class Fixture
    {
        final AtomicReference<String> profile = new AtomicReference<>("account-a");
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final ConfigManager config = mock(ConfigManager.class);
        final Map<String, String> values = new HashMap<>();
        final FarmPatchPlanner planner;
        final FarmRunsTool tool;
        JPanel panel;
        Fixture()
        {
            Player player = mock(Player.class);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(2809, 3463, 0));
            when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.HUNTER)).thenReturn(99);
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(), anyString(), anyString()))
                .thenAnswer(call -> values.get(call.getArgument(1) + ":" + call.getArgument(2)));
            doAnswer(call -> { values.put(call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3).toString()); return null; })
                .when(config).setConfiguration(anyString(), anyString(), anyString(), anyString());
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            planner = new FarmPatchPlanner(client, thread, config, new Gson(), new FarmPatchCatalog(new Gson()));
            tool = new FarmRunsTool(planner, null);
        }
        void start() throws Exception
        {
            tool.start(); for (int i = 0; i < 6; i++) tool.onGameTick();
            SwingUtilities.invokeAndWait(() -> panel = tool.createPanel());
        }
        JComboBox<?> picker() { return named(panel, "runewarden.farm-runs.selector", JComboBox.class); }
        void close() throws Exception { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
}
