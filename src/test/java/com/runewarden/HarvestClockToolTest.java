package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HarvestClockToolTest
{
    @Test public void derivesOnlyNativeObservedHarvestGroups()
    {
        FarmingTracker farming = mock(FarmingTracker.class);
        BirdHouseTracker birds = mock(BirdHouseTracker.class);
        for (Tab tab : Tab.FARMING_TABS)
        {
            when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);
            when(farming.getCompletionTime(tab)).thenReturn(-1L);
        }
        when(farming.getSummary(Tab.HERB)).thenReturn(SummaryState.IN_PROGRESS);
        when(farming.getCompletionTime(Tab.HERB)).thenReturn(2000L);
        when(birds.getSummary()).thenReturn(SummaryState.COMPLETED);
        when(birds.getCompletionTime()).thenReturn(0L);
        HarvestClockTool.Reading reading = HarvestClockTool.readTrackers(farming, birds);
        assertEquals(HarvestClockTool.PURE_TABS.length + 1, reading.harvests.size());
        assertEquals(1, reading.dueCount(1900));
        assertEquals("Bird houses", reading.firstDue(1900).name);
        assertEquals("Herb", reading.next(1900).name);
        assertEquals(2, reading.dueCount(2000));
        assertNull(reading.next(2000));
        for (Tab tab : HarvestClockTool.PURE_TABS)
        {
            verify(farming).getSummary(tab);
            verify(farming).getCompletionTime(tab);
        }
        for(Tab mixed:new Tab[]{Tab.TREE,Tab.FRUIT_TREE,Tab.SPECIAL})
        {verify(farming,never()).getSummary(mixed);verify(farming,never()).getCompletionTime(mixed);}
        assertTrue(reading.harvests.get(1).estimated);
        assertFalse(reading.harvests.get(0).estimated);
        try { reading.harvests.clear(); fail("Harvest snapshots must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void emptyAndUnknownNeverBecomeReadyFromInvalidCompletionTimes()
    {
        HarvestClockTool.Harvest unknown = new HarvestClockTool.Harvest("Tree", SummaryState.UNKNOWN, 1, true);
        HarvestClockTool.Harvest empty = new HarvestClockTool.Harvest("Herb", SummaryState.EMPTY, 0, true);
        HarvestClockTool.Harvest invalid = new HarvestClockTool.Harvest("Hops", SummaryState.IN_PROGRESS, -1, true);
        assertFalse(unknown.due(1000));
        assertFalse(empty.due(1000));
        assertFalse(invalid.due(1000));
        assertEquals("Not observed", unknown.status(1000));
        assertEquals("Empty", empty.status(1000));
        assertEquals("Not observed", invalid.status(1000));
    }

    @Test public void nativeCropCompletionIsAVisitReminderNotAnExactHealthyHarvestClaim()
    {
        HarvestClockTool.Harvest crop = new HarvestClockTool.Harvest("Tree", SummaryState.COMPLETED, 0, true);
        assertEquals("Due to check · estimated", crop.status(1000));
        HarvestClockTool.Harvest growing = new HarvestClockTool.Harvest("Herb", SummaryState.IN_PROGRESS, 4600, true);
        assertEquals("In 1h 0m · estimated", growing.status(1000));
        HarvestClockTool.Reading reading = new HarvestClockTool.Reading(true, false, "", Arrays.asList(crop, growing));
        assertEquals(1, reading.dueCount(1000));
        assertSame(growing, reading.next(1000));
    }

    @Test public void nativeBirdHouseDeadlineDoesNotClaimAnEstimatedCropTimer()
    {
        HarvestClockTool.Harvest birds = new HarvestClockTool.Harvest("bird-house", "Bird houses", SummaryState.IN_PROGRESS, 4600, false);
        assertEquals("In 1h 0m", birds.status(1000));
        assertEquals("Due to check · estimated", birds.status(4600));
        assertEquals("Ready · observed", new HarvestClockTool.Harvest("Bird houses", SummaryState.COMPLETED, 0, false).status(4600));
    }

    @Test public void independentHarvestCategoriesUseActualNativeItemIcons()
    {
        HarvestClockTool tool = new HarvestClockTool(mock(Client.class), mock(ConfigManager.class),
            mock(PluginManager.class), mock(OverlayManager.class), mock(RunewardenConfig.class));
        ItemManager items = mock(ItemManager.class); tool.setItemManager(items);
        tool.icon("bird-house"); verify(items).getImage(Tab.BIRD_HOUSE.getItemID());
        for (Tab tab : HarvestClockTool.PURE_TABS)
        {
            tool.icon(HarvestClockTool.groupId(tab)); verify(items,atLeastOnce()).getImage(tab.getItemID());
        }
        for(FarmHarvestTimers.Group group:FarmHarvestTimers.GROUPS)
        {tool.icon(group.id);verify(items,atLeastOnce()).getImage(group.iconId);}
        assertNull(tool.icon("special"));
        assertNull(tool.icon("unrecognised-group"));
        verifyNoMoreInteractions(items);
    }

    @Test public void clockUsesRealSelectedTimezoneIncludingDaylightSaving()
    {
        Instant summer = Instant.parse("2026-10-16T14:26:31Z");
        assertEquals("16:26:31", CommandDashboardOverlay.clockText(summer, false, ZoneId.of("Europe/Brussels")));
        assertEquals("14:26:31", CommandDashboardOverlay.clockText(summer, true, ZoneId.of("Europe/Brussels")));
        Instant winter = Instant.parse("2026-12-16T14:26:31Z");
        assertEquals("15:26:31", CommandDashboardOverlay.clockText(winter, false, ZoneId.of("Europe/Brussels")));
    }

    @Test public void logoutAndProfileTransitionClearDashboardBeforeNativeReload() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager manager = mock(ConfigManager.class);
        PluginManager plugins = mock(PluginManager.class);
        RunewardenConfig config = mock(RunewardenConfig.class);
        OverlayManager overlays = mock(OverlayManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(manager.getRSProfileKey()).thenReturn("account-one");
        when(plugins.getPlugins()).thenReturn(Collections.emptyList());
        HarvestClockTool tool = new HarvestClockTool(client, manager, plugins, overlays, config);
        tool.start();
        try
        {
            tool.onGameTick();
            assertEquals("Loading harvest observations.", tool.reading().message);
            for (int i = 0; i < 10; i++) tool.onGameTick();
            assertEquals("Time Tracking is unavailable.", tool.reading().message);
            when(manager.getRSProfileKey()).thenReturn("account-two");
            tool.onGameTick();
            assertEquals("Loading harvest observations.", tool.reading().message);
            assertTrue(tool.reading().harvests.isEmpty());
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            tool.clear();
            tool.onGameTick();
            assertEquals("Log in to track harvests.", tool.reading().message);
            assertFalse(tool.reading().active);
        }
        finally { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
        verify(overlays).add(any(HarvestClockOverlay.class));
        verify(overlays).remove(any(HarvestClockOverlay.class));
    }

    @Test public void overlayIsMovableReadableAndIndependentlyConfigurable() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager manager = mock(ConfigManager.class);
        PluginManager plugins = mock(PluginManager.class);
        RunewardenConfig config = mock(RunewardenConfig.class);
        HarvestClockTool tool = new HarvestClockTool(client, manager, plugins, mock(OverlayManager.class), config);
        HarvestClockOverlay overlay = new HarvestClockOverlay(tool, config);
        BufferedImage image = new BufferedImage(436, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(2, 2);
        assertNull(overlay.render(graphics));
        tool.start();
        try
        {
            when(config.clockHud()).thenReturn(true);
            assertNull(overlay.render(graphics));
            when(config.harvestHud()).thenReturn(true);
            assertNull("Unobserved or empty timers must not cover the game", overlay.render(graphics));
            assertTrue(overlay.isMovable());
            assertEquals("Open harvest control", overlay.getMenuEntries().get(0).getOption());
            File output = new File("build/reports/harvest-clock-hud.png");
            output.getParentFile().mkdirs();
            ImageIO.write(image, "png", output);
            when(config.clockHud()).thenReturn(false);
            assertNull(overlay.render(graphics));
            when(config.harvestHud()).thenReturn(false);
            assertNull(overlay.render(graphics));
        }
        finally { graphics.dispose(); tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
}
