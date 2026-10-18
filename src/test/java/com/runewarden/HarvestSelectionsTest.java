package com.runewarden;

import com.google.inject.Injector;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HarvestSelectionsTest
{
    private static HarvestClockTool.Harvest harvest(String id, SummaryState state, long done)
    { return new HarvestClockTool.Harvest(id, id, state, done, true); }

    @Test public void defaultsIncludeOnlyObservedNonemptyGroupsAndCustomEmptyStaysEmpty()
    {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("account-a");
        HarvestSelections choices = new HarvestSelections(config);
        HarvestSelections.Session session = choices.bind("account-a");
        List<HarvestClockTool.Harvest> observations = Arrays.asList(harvest("herb", SummaryState.COMPLETED, 0),
            harvest("tree", SummaryState.IN_PROGRESS, 2000), harvest("bird-house", SummaryState.UNKNOWN, -1),
            harvest("fruit-tree", SummaryState.EMPTY, 0), harvest("special", SummaryState.IN_PROGRESS, -1));
        assertEquals(Set.of("herb", "tree"), choices.selected(observations));
        assertTrue(choices.update(session, Set.of("herb", "tree"), false, observations));
        assertTrue(choices.selected(observations).isEmpty());
        verify(config).setConfiguration(RunewardenConfig.GROUP, "account-a", HarvestSelections.KEY, "[]");
        when(config.getConfiguration(RunewardenConfig.GROUP, "account-a", HarvestSelections.KEY)).thenReturn("[]");
        choices.clear();
        choices.bind("account-a");
        assertTrue(choices.selected(observations).isEmpty());
    }

    @Test public void savedSelectionsAreIsolatedAndDelayedOldAccountEditsAreRejected()
    {
        ConfigManager config = mock(ConfigManager.class);
        AtomicReference<String> profile = new AtomicReference<>("account-a");
        Map<String, String> values = new HashMap<>();
        when(config.getRSProfileKey()).thenAnswer(event -> profile.get());
        when(config.getConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(HarvestSelections.KEY)))
            .thenAnswer(event -> values.get(event.getArgument(1)));
        doAnswer(event -> { values.put(event.getArgument(1), event.getArgument(3)); return null; })
            .when(config).setConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(HarvestSelections.KEY), anyString());
        HarvestSelections choices = new HarvestSelections(config);
        HarvestSelections.Session old = choices.bind("account-a");
        assertTrue(choices.add(old, Set.of("herb"), Collections.emptyList()));
        assertEquals("[\"herb\"]", values.get("account-a"));
        profile.set("account-b");
        assertFalse(choices.toggle(old, "tree", true, Collections.emptyList()));
        HarvestSelections.Session next = choices.bind("account-b");
        assertTrue(choices.selected(Collections.emptyList()).isEmpty());
        assertTrue(choices.add(next, Set.of("bird-house"), Collections.emptyList()));
        assertFalse(choices.toggle(old, "herb", false, Collections.emptyList()));
        assertEquals("[\"herb\"]", values.get("account-a"));
        assertEquals("[\"bird-house\"]", values.get("account-b"));
        profile.set("account-a");
        choices.bind("account-a");
        assertEquals(Set.of("herb"), choices.selected(Collections.emptyList()));
        choices.clear();
        assertFalse(choices.toggle(old, "tree", true, Collections.emptyList()));
    }

    @Test public void trackingOneRunPreservesOtherSelectionsAndRejectsUnknownIds()
    {
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("account-a");
        HarvestSelections choices = new HarvestSelections(config);
        HarvestSelections.Session session = choices.bind("account-a");
        assertTrue(choices.add(session, Set.of("bird-house", "bogus"), Collections.emptyList()));
        assertTrue(choices.add(session, Set.of("herb", "flower", "allotment"), Collections.emptyList()));
        assertTrue(choices.update(session, Set.of("herb", "flower", "allotment"), false, Collections.emptyList()));
        assertEquals(Set.of("bird-house"), choices.selected(Collections.emptyList()));
        assertFalse(choices.toggle(session, "bogus", true, Collections.emptyList()));
    }

    @Test public void timerCheckboxChangesNamedDashboardRowsAndOldButtonCannotOpenAnotherAccount() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        PluginManager plugins = mock(PluginManager.class);
        FarmingTracker farming = mock(FarmingTracker.class);
        BirdHouseTracker birds = mock(BirdHouseTracker.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(config.getRSProfileKey()).thenReturn("account-a");
        for (Tab tab : Tab.FARMING_TABS)
        {
            when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);
            when(farming.getCompletionTime(tab)).thenReturn(-1L);
        }
        when(farming.getSummary(Tab.HERB)).thenReturn(SummaryState.COMPLETED);
        when(farming.getCompletionTime(Tab.HERB)).thenReturn(0L);
        when(birds.getSummary()).thenReturn(SummaryState.COMPLETED);
        when(birds.getCompletionTime()).thenReturn(0L);
        NativeTracker nativePlugin = new NativeTracker();
        Injector injector = mock(Injector.class);
        when(injector.getInstance(FarmingTracker.class)).thenReturn(farming);
        when(injector.getInstance(BirdHouseTracker.class)).thenReturn(birds);
        nativePlugin.attach(injector);
        when(plugins.getPlugins()).thenReturn(Collections.singletonList(nativePlugin));
        when(plugins.isPluginActive(nativePlugin)).thenReturn(true);
        RunewardenConfig hudConfig = mock(RunewardenConfig.class);
        when(hudConfig.harvestHud()).thenReturn(true);
        HarvestClockTool tool = new HarvestClockTool(client, config, plugins, mock(OverlayManager.class), hudConfig);
        tool.start();
        AtomicReference<String> opened = new AtomicReference<>();
        tool.setOpenRunAction(opened::set);
        try
        {
            for (int i = 0; i < 10; i++) tool.onGameTick();
            assertEquals(Set.of("herb", "bird-house"), tool.selectedGroupIds());
            assertEquals(2, tool.displayHarvests(1000).size());
            AtomicReference<JPanel> timers = new AtomicReference<>();
            AtomicReference<JPanel> dashboard = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> { timers.set(tool.createPanel()); dashboard.set(tool.createDashboardPanel()); });
            List<JButton> before = components(dashboard.get(), JButton.class);
            assertEquals(2, before.size());
            assertTrue(before.stream().anyMatch(button -> button.getText().contains("Bird houses")));
            assertTrue(before.stream().anyMatch(button -> button.getText().contains("Herb")));
            HarvestClockOverlay overlay = new HarvestClockOverlay(tool, hudConfig);
            BufferedImage preview = new BufferedImage(528, 196, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = preview.createGraphics();
            graphics.scale(2, 2);
            assertEquals(new Dimension(264, 98), overlay.render(graphics));
            graphics.dispose();
            File output = new File("build/reports/harvest-selected-groups.png");
            output.getParentFile().mkdirs();
            ImageIO.write(preview, "png", output);
            JCheckBox herbs = components(timers.get(), JCheckBox.class).stream()
                .filter(box -> "herb".equals(box.getClientProperty("harvestGroup"))).findFirst().get();
            SwingUtilities.invokeAndWait(herbs::doClick);
            assertEquals(Set.of("bird-house"), tool.selectedGroupIds());
            assertEquals(1, components(dashboard.get(), JButton.class).size());
            Graphics2D reduced = preview.createGraphics();
            assertEquals(new Dimension(264, 58), overlay.render(reduced));
            reduced.dispose();
            verify(config).setConfiguration(RunewardenConfig.GROUP, "account-a", HarvestSelections.KEY, "[\"bird-house\"]");
            assertTrue(tool.trackGroups(Set.of("tree")));
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(Set.of("bird-house", "tree"), tool.selectedGroupIds());
            assertEquals(1, tool.displayHarvests(1000).size());
            assertEquals(1, components(dashboard.get(), JButton.class).size());
            when(config.getRSProfileKey()).thenReturn("account-b");
            SwingUtilities.invokeAndWait(before.get(0)::doClick);
            assertNull(opened.get());
            assertTrue(tool.selectedHarvests().isEmpty());
            tool.clear();
            assertTrue(tool.selectedGroupIds().isEmpty());
        }
        finally { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    private static <T> List<T> components(Container root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        for (Component component : root.getComponents())
        {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container) result.addAll(components((Container) component, type));
        }
        return result;
    }

    private static final class NativeTracker extends TimeTrackingPlugin
    {
        void attach(Injector injector) { this.injector = injector; }
    }
}
