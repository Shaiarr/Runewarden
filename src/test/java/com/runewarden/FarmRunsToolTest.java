/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmRunsToolTest
{
    @Test public void everyRunOpensTheRealGuideUsedByTheBankFilter() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.tool.start();
        fixture.tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> {});
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = fixture.tool.createPanel();
                JComboBox<?> picker = picker(panel);
                assertEquals(15, picker.getItemCount());
                assertNull(fixture.guides.selectedGuide());
                for (FarmRunsTool.Run run : FarmRunsTool.RUNS)
                {
                    assertNotNull(fixture.catalog.entry(run.guideId));
                    picker.setSelectedItem(run);
                    assertEquals(run.guideId, fixture.guides.selectedGuide().id);
                    assertNotNull(fixture.guides.currentStep());
                    assertTrue(descendants(panel).stream().anyMatch(item -> item instanceof GuideText
                        && fixture.guides.currentStep().text.equals(((GuideText) item).getText())));
                }
            });
        }
        finally { fixture.tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    @Test public void runChecklistNavigationPreservesTheSelectedGuide() throws Exception
    {
        Fixture fixture = new Fixture();
        AtomicInteger opened = new AtomicInteger();
        fixture.tool.setOpenGuideAction(opened::incrementAndGet);
        fixture.tool.start(); fixture.tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> {});
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = fixture.tool.createPanel();
                picker(panel).setSelectedItem(FarmRunsTool.find("training:herb-runs"));
                GuideCatalog.Entry before = fixture.guides.selectedGuide();
                JButton open = (JButton) descendants(panel).stream().filter(item -> item instanceof JButton
                    && "Open run checklist".equals(((JButton) item).getText())).findFirst().get();
                open.doClick();
                assertSame(before, fixture.guides.selectedGuide());
                assertEquals(1, opened.get());
            });
        }
        finally { fixture.tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    @Test public void nativeTimerScopeMatchesPinnedPatchImplementations()
    {
        assertEquals(Collections.singletonList("tree"), FarmRunsTool.find("training:hardwood-runs").groups);
        assertEquals(Collections.singletonList("tree"), FarmRunsTool.find("training:redwood-farming").groups);
        assertEquals(Collections.singletonList("fruit-tree"), FarmRunsTool.find("training:calquat-runs").groups);
        assertEquals(Collections.singletonList("fruit-tree"), FarmRunsTool.find("training:celastrus-runs").groups);
        assertEquals(Collections.singletonList("fruit-tree"), FarmRunsTool.find("training:crystal-tree-runs").groups);
        assertEquals(Collections.singletonList("special"), FarmRunsTool.find("training:giant-seaweed").groups);
        assertTrue(FarmRunsTool.find("training:farming-contracts").groups.isEmpty());
        assertTrue(FarmRunsTool.find("training:tithe-farm").groups.isEmpty());
        assertNull("A generic special-patch timer cannot identify which crop is due", FarmRunsTool.primaryRun("special"));
    }

    @Test public void runStatusNeverClaimsAnIndividualCropIsReadyFromAGroupedTimer()
    {
        HarvestClockTool.Harvest herbs = new HarvestClockTool.Harvest("herb", "Herb", SummaryState.COMPLETED, 0, true);
        HarvestClockTool.Harvest trees = new HarvestClockTool.Harvest("tree", "Tree", SummaryState.IN_PROGRESS, 5000, true);
        HarvestClockTool.Harvest special = new HarvestClockTool.Harvest("special", "Special", SummaryState.UNKNOWN, -1, true);
        HarvestClockTool.Reading reading = new HarvestClockTool.Reading(true, false, "", Arrays.asList(herbs, trees, special));
        assertEquals("Herb · Due to check · estimated", FarmRunsTool.status(FarmRunsTool.find("training:herb-runs"), reading, 1000));
        assertEquals("Tree · In 1h 6m · estimated", FarmRunsTool.status(FarmRunsTool.find("training:hardwood-runs"), reading, 1000));
        assertEquals("Special · Not observed", FarmRunsTool.status(FarmRunsTool.find("training:giant-seaweed"), reading, 1000));
        assertEquals("No growth timer", FarmRunsTool.status(FarmRunsTool.find("training:tithe-farm"), reading, 1000));
        assertEquals("Log in to track harvests.", FarmRunsTool.status(FarmRunsTool.find("training:herb-runs"),
            HarvestClockTool.Reading.empty("Log in to track harvests."), 1000));
    }

    @Test public void trackRunTogglesOnlyItsOwnNativeGroups() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.harvests.start();
        fixture.tool.start();
        try
        {
            fixture.harvests.onGameTick();
            fixture.tool.onGameTick();
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = fixture.tool.createPanel();
                picker(panel).setSelectedItem(FarmRunsTool.find("training:allotment-runs"));
                JCheckBox track = (JCheckBox) descendants(panel).stream().filter(item -> item instanceof JCheckBox).findFirst().get();
                assertTrue(track.isEnabled());
                track.doClick();
                assertTrue(fixture.harvests.isGroupSelected("allotment"));
                assertTrue(fixture.harvests.isGroupSelected("flower"));
                assertFalse(fixture.harvests.isGroupSelected("tree"));
                verify(fixture.config).setConfiguration("runewarden", "test-profile", HarvestSelections.KEY,
                    "[\"allotment\",\"flower\"]");
            });
        }
        finally { fixture.tool.stop(); fixture.harvests.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    @Test public void restoringAPerCharacterPickerDoesNotReplaceAnActiveBossGuide() throws Exception
    {
        Fixture fixture = new Fixture();
        when(fixture.config.getConfiguration("runewarden", "test-profile", "farmRun")).thenReturn("training:herb-runs");
        SwingUtilities.invokeAndWait(() -> {
            fixture.tool.createPanel();
            fixture.guides.openGuide("boss:hueycoatl");
        });
        fixture.tool.start();
        try
        {
            fixture.tool.onGameTick();
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("training:herb-runs", fixture.tool.selectedRun().guideId);
                assertEquals("boss:hueycoatl", fixture.guides.selectedGuide().id);
            });
            fixture.tool.clear();
            SwingUtilities.invokeAndWait(() -> assertNull(fixture.tool.selectedRun()));
        }
        finally { fixture.tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    @Test public void staleCharacterPickerCannotActivateItsGuideOnAnotherProfile() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.tool.start(); fixture.tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> {});
        try
        {
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = fixture.tool.createPanel();
                fixture.guides.openGuide("boss:hueycoatl");
                when(fixture.config.getRSProfileKey()).thenReturn("different-profile");
                picker(panel).setSelectedItem(FarmRunsTool.find("training:herb-runs"));
                assertEquals("boss:hueycoatl", fixture.guides.selectedGuide().id);
                JButton start = (JButton) descendants(panel).stream().filter(item -> item instanceof JButton
                    && "Start run".equals(((JButton) item).getText())).findFirst().get();
                assertFalse(start.isEnabled());
                start.doClick();
                assertEquals("boss:hueycoatl", fixture.guides.selectedGuide().id);
            });
            verify(fixture.config, never()).setConfiguration(eq("runewarden"), anyString(), eq("farmRun"), anyString());
        }
        finally { fixture.tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }

    private static JComboBox<?> picker(Container panel)
    {
        return (JComboBox<?>) descendants(panel).stream().filter(item -> "runewarden.farm-runs.selector".equals(item.getName())).findFirst().get();
    }

    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            result.add(child);
            if (child instanceof Container) result.addAll(descendants((Container) child));
        }
        return result;
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final ConfigManager config = mock(ConfigManager.class);
        final GuideCatalog catalog = new GuideCatalog(new Gson());
        final HarvestClockTool harvests;
        final RunewardenGuides guides;
        final FarmRunsTool tool;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(config.getRSProfileKey()).thenReturn("test-profile");
            harvests = new HarvestClockTool(client, config, mock(PluginManager.class), mock(OverlayManager.class), mock(RunewardenConfig.class));
            ClientThread thread = mock(ClientThread.class);
            guides = new RunewardenGuides(client, thread, config, catalog,
                new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
                new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class));
            tool = new FarmRunsTool(client, thread, config, catalog, guides, harvests, null);
        }
    }
}
