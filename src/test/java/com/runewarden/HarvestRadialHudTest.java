/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.inject.Injector;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
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

public class HarvestRadialHudTest
{
    private static final class NativeTracker extends TimeTrackingPlugin
    {void attach(Injector injector){this.injector=injector;}}
    @Test public void radialModesRenderInTheMovableHarvestHudAndSelectionChangesInvalidateCachedRows() throws Exception
    {
        Client client=mock(Client.class);ConfigManager manager=mock(ConfigManager.class);PluginManager plugins=mock(PluginManager.class);
        FarmingTracker farming=mock(FarmingTracker.class);BirdHouseTracker birds=mock(BirdHouseTracker.class);RunewardenConfig config=mock(RunewardenConfig.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(manager.getRSProfileKey()).thenReturn("rsprofile.timer-test");
        long now=Instant.now().getEpochSecond();
        for(Tab tab:Tab.FARMING_TABS){when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);when(farming.getCompletionTime(tab)).thenReturn(-1L);}
        when(farming.getSummary(Tab.HERB)).thenReturn(SummaryState.IN_PROGRESS);when(farming.getCompletionTime(Tab.HERB)).thenReturn(now+3600);
        when(birds.getSummary()).thenReturn(SummaryState.IN_PROGRESS);when(birds.getCompletionTime()).thenReturn(now+3000);
        NativeTracker nativePlugin=new NativeTracker();Injector injector=mock(Injector.class);nativePlugin.attach(injector);
        when(injector.getInstance(FarmingTracker.class)).thenReturn(farming);when(injector.getInstance(BirdHouseTracker.class)).thenReturn(birds);
        when(plugins.getPlugins()).thenReturn(List.of(nativePlugin));when(plugins.isPluginActive(nativePlugin)).thenReturn(true);when(config.harvestHud()).thenReturn(true);
        HarvestClockTool tool=new HarvestClockTool(client,manager,plugins,mock(OverlayManager.class),config);tool.start();
        try
        {
            for(int i=0;i<10;i++)tool.onGameTick();
            List<HarvestClockTool.Harvest> captured=tool.displayHarvests(now);
            assertEquals(2,captured.size());assertSame(captured,tool.displayHarvests(now));assertTrue(captured.stream().allMatch(HarvestClockTool.Harvest::hasWindow));
            HarvestClockOverlay overlay=new HarvestClockOverlay(tool,config);assertTrue(overlay.isMovable());
            BufferedImage image=new BufferedImage(820,175,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
            try
            {
                int column=0;for(RunewardenConfig.TimerDisplay mode:RunewardenConfig.TimerDisplay.values())
                {
                    when(config.harvestTimerDisplay()).thenReturn(mode);Graphics2D pane=(Graphics2D)g.create();pane.translate(column++*275,20);
                    try{Dimension expected=new Dimension(264,mode==RunewardenConfig.TimerDisplay.TEXT?98:118);assertEquals(expected,overlay.render(pane));}finally{pane.dispose();}
                }
            }
            finally{g.dispose();}
            File output=new File("build/reports/harvest-radial-modes.png");output.getParentFile().mkdirs();ImageIO.write(image,"png",output);
            assertTrue(tool.setGroupsTracked(List.of("herb"),false));
            assertEquals(1,tool.displayHarvests(now).size());assertNotSame(captured,tool.displayHarvests(now));
            when(manager.getRSProfileKey()).thenReturn("rsprofile.other");assertTrue(tool.displayHarvests(now).isEmpty());
        }
        finally{tool.stop();SwingUtilities.invokeAndWait(()->{});}
    }
}
