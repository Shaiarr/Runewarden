/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.inject.Injector;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.TimeTrackingPlugin;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** The real timer pipeline must never let a mixed native tab hide a ready crop family. */
public class HarvestSplitHudTest
{
    @Test public void nativeRecordsReachDistinctSelectedHudRowsWithoutMixedTabSummaries() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            f.start();List<HarvestClockTool.Harvest> rows=f.tool.displayHarvests(f.now);
            assertEquals(Set.of("fruit-tree","tree","celastrus","hardwood","redwood","hespori"),ids(rows));
            assertTrue(row(rows,"fruit-tree").due(f.now));assertTrue(row(rows,"tree").due(f.now));
            for(String id:List.of("celastrus","hardwood","redwood","hespori"))
            {
                HarvestClockTool.Harvest harvest=row(rows,id);
                assertEquals(id,SummaryState.IN_PROGRESS,harvest.state);
                assertEquals(id,(long)f.deadlinesByGroup.get(id),harvest.completion);
                assertFalse(id,harvest.due(f.now));
            }
            assertNotEquals(row(rows,"celastrus").completion,row(rows,"hardwood").completion);
            assertNotEquals(row(rows,"hardwood").completion,row(rows,"redwood").completion);
            assertNotEquals(row(rows,"redwood").completion,row(rows,"hespori").completion);
            verify(f.farming,never()).getSummary(Tab.FRUIT_TREE);verify(f.farming,never()).getCompletionTime(Tab.FRUIT_TREE);
            verify(f.farming,never()).getSummary(Tab.TREE);verify(f.farming,never()).getCompletionTime(Tab.TREE);
            verify(f.farming,never()).getSummary(Tab.SPECIAL);verify(f.farming,never()).getCompletionTime(Tab.SPECIAL);
            assertNotNull("Legacy choices migrate through the actual tool binding",f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY));
            BufferedImage image=new BufferedImage(300,400,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
            try
            {
                Dimension size=f.tool.overlay().render(graphics);assertNotNull(size);assertEquals(264,size.width);
                assertTrue("All six selected crop families render in the expanded HUD",size.height>200);
                assertNotEquals("The actual HUD renderer must paint",0,image.getRGB(15,20));
            }
            finally{graphics.dispose();}
        }
        finally{f.close();}
    }

    @Test public void independentCropCheckboxesUpdateTheRealHudAndHideImmediatelyOnAccountChange() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            f.start();AtomicReference<JPanel> panel=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->panel.set(f.tool.createPanel()));
            JCheckBox celastrus=checkbox(panel.get(),"celastrus");
            assertEquals("Celastrus",celastrus.getText());assertTrue(celastrus.isSelected());
            assertEquals("Hardwood trees",checkbox(panel.get(),"hardwood").getText());
            assertEquals("Hespori",checkbox(panel.get(),"hespori").getText());
            assertEquals("Redwood",checkbox(panel.get(),"redwood").getText());
            assertFalse(checkboxes(panel.get()).stream().anyMatch(box->"special".equals(box.getClientProperty("harvestGroup"))));
            SwingUtilities.invokeAndWait(celastrus::doClick);
            assertEquals(Set.of("fruit-tree","tree","hardwood","redwood","hespori"),ids(f.tool.displayHarvests(f.now)));
            assertTrue(checkbox(panel.get(),"fruit-tree").isSelected());
            assertTrue(checkbox(panel.get(),"hardwood").isSelected());assertTrue(checkbox(panel.get(),"hespori").isSelected());
            assertFalse(f.tool.selectedGroupIds().contains("celastrus"));
            String saved=f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY);
            f.profile.set("rsprofile.other");
            assertTrue(f.tool.displayHarvests(f.now).isEmpty());assertTrue(f.tool.selectedGroupIds().isEmpty());
            Graphics2D graphics=new BufferedImage(300,400,BufferedImage.TYPE_INT_ARGB).createGraphics();
            try{assertNull(f.tool.overlay().render(graphics));}finally{graphics.dispose();}
            SwingUtilities.invokeAndWait(celastrus::doClick);
            assertEquals("A delayed checkbox cannot alter its old account's V2 choices",saved,f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY));
            assertNull(f.saved(RunewardenConfig.GROUP,"rsprofile.other",HarvestSelections.KEY));
            for(int tick=0;tick<10;tick++)f.tool.onGameTick();
            assertTrue("The new account has no observed native patch records",f.tool.displayHarvests(f.now).isEmpty());
        }
        finally{f.close();}
    }

    @Test public void overviewAlwaysKeepsAllCategoriesEditableAndSynchronizesWithTimers() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            f.start();AtomicReference<JPanel> overview=new AtomicReference<>(),timers=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->{overview.set(f.tool.createDashboardPanel());timers.set(f.tool.createPanel());});
            assertEquals(24,checkboxes(overview.get()).size());
            JCheckBox hardwood=checkbox(overview.get(),"hardwood");assertTrue(hardwood.isSelected());
            SwingUtilities.invokeAndWait(hardwood::doClick);
            assertFalse(f.tool.selectedGroupIds().contains("hardwood"));
            assertSame("An unticked row must remain available to re-enable",hardwood,checkbox(overview.get(),"hardwood"));
            assertFalse(checkbox(timers.get(),"hardwood").isSelected());
            SwingUtilities.invokeAndWait(()->checkbox(timers.get(),"hardwood").doClick());
            assertTrue(hardwood.isSelected());assertTrue(ids(f.tool.displayHarvests(f.now)).contains("hardwood"));
            JCheckBox herb=checkbox(overview.get(),"herb");assertFalse(herb.isSelected());assertTrue(herb.isEnabled());
            SwingUtilities.invokeAndWait(herb::doClick);
            assertTrue("An unobserved category can be explicitly tracked",f.tool.selectedGroupIds().contains("herb"));
            assertTrue(checkbox(timers.get(),"herb").isSelected());
            assertFalse("Selecting an unobserved category cannot fabricate a timer",ids(f.tool.displayHarvests(f.now)).contains("herb"));
        }
        finally{f.close();}
    }

    @Test public void anEmptyOverviewKeepsItsControlsAndRejectsStaleAccountClicks() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            f.start();AtomicReference<JPanel> overview=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->overview.set(f.tool.createDashboardPanel()));
            SwingUtilities.invokeAndWait(()->{for(JCheckBox box:checkboxes(overview.get()))if(box.isSelected())box.doClick();});
            assertTrue(f.tool.selectedGroupIds().isEmpty());assertTrue(f.tool.displayHarvests(f.now).isEmpty());
            assertEquals("[]",f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY));
            assertEquals(24,checkboxes(overview.get()).size());
            JCheckBox herb=checkbox(overview.get(),"herb");
            SwingUtilities.invokeAndWait(herb::doClick);
            assertEquals(Set.of("herb"),f.tool.selectedGroupIds());
            String saved=f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY);
            f.profile.set("rsprofile.other");SwingUtilities.invokeAndWait(herb::doClick);
            assertEquals(saved,f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY));
            assertNull(f.saved(RunewardenConfig.GROUP,"rsprofile.other",HarvestSelections.KEY));
            for(int tick=0;tick<10;tick++)f.tool.onGameTick();
            f.tool.setRunGroupSupport(group->false);
            SwingUtilities.invokeAndWait(()->{});
            assertEquals(24,checkboxes(overview.get()).size());assertFalse(herb.isSelected());
            SwingUtilities.invokeAndWait(herb::doClick);
            assertEquals("[\"herb\"]",f.saved(RunewardenConfig.GROUP,"rsprofile.other",HarvestSelections.KEY));
            assertEquals("The previous account's selections remain unchanged",saved,f.saved(RunewardenConfig.GROUP,f.owner,HarvestSelections.KEY));
        }
        finally{f.close();}
    }

    private static Set<String> ids(List<HarvestClockTool.Harvest> rows)
    {Set<String> ids=new java.util.LinkedHashSet<>();for(HarvestClockTool.Harvest row:rows)ids.add(row.id);return ids;}
    private static HarvestClockTool.Harvest row(List<HarvestClockTool.Harvest> rows,String id)
    {return rows.stream().filter(harvest->id.equals(harvest.id)).findFirst().orElseThrow();}
    private static JCheckBox checkbox(Container root,String group)
    {return checkboxes(root).stream().filter(box->group.equals(box.getClientProperty("harvestGroup"))).findFirst().orElseThrow();}
    private static List<JCheckBox> checkboxes(Container root)
    {
        List<JCheckBox> result=new ArrayList<>();
        for(Component child:root.getComponents())
        {
            if(child instanceof JCheckBox)result.add((JCheckBox)child);
            if(child instanceof Container)result.addAll(checkboxes((Container)child));
        }
        return result;
    }

    private static final class Fixture
    {
        final String owner="rsprofile.split-timers";final long now=Instant.now().getEpochSecond();
        final AtomicReference<String> profile=new AtomicReference<>(owner);
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);
        final FarmingTracker farming=mock(FarmingTracker.class);final BirdHouseTracker birds=mock(BirdHouseTracker.class);
        final FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());
        final Map<String,String> values=new HashMap<>();final Map<Long,Long> deadlinesByObservation=new HashMap<>();
        final Map<String,Long> deadlinesByGroup=new HashMap<>();final HarvestClockTool tool;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());
            when(config.getConfiguration(anyString(),anyString(),anyString()))
                .thenAnswer(call->saved(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
            doAnswer(call->{values.put(key(call.getArgument(0),call.getArgument(1),call.getArgument(2)),call.getArgument(3));return null;})
                .when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
            values.put(key(RunewardenConfig.GROUP,owner,HarvestSelections.LEGACY_KEY),"[\"fruit-tree\",\"tree\",\"special\"]");
            for(Tab tab:Tab.FARMING_TABS)
            {when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);when(farming.getCompletionTime(tab)).thenReturn(-1L);}
            for(Tab tab:List.of(Tab.FRUIT_TREE,Tab.TREE,Tab.SPECIAL))
            {when(farming.getSummary(tab)).thenReturn(SummaryState.IN_PROGRESS);when(farming.getCompletionTime(tab)).thenReturn(now+86400);}
            when(birds.getSummary()).thenReturn(SummaryState.UNKNOWN);when(birds.getCompletionTime()).thenReturn(-1L);
            when(farming.getTickTime(anyInt(),anyInt(),anyLong(),eq(owner)))
                .thenAnswer(call->(int)call.getArgument(1)<=0?now-1:deadlinesByObservation.get((long)call.getArgument(2)));
            record("FRUIT_TREE","fruit-tree","HARVESTABLE",now-60,0);
            record("TREE","tree","HARVESTABLE",now-61,0);
            record("CELASTRUS","celastrus","GROWING",now-62,now+3600);
            record("HARDWOOD_TREE","hardwood","GROWING",now-63,now+7200);
            record("REDWOOD","redwood","GROWING",now-64,now+10800);
            record("HESPORI","hespori","GROWING",now-65,now+14400);
            PluginManager plugins=mock(PluginManager.class);NativeTracker nativePlugin=new NativeTracker();Injector injector=mock(Injector.class);
            when(injector.getInstance(FarmingTracker.class)).thenReturn(farming);when(injector.getInstance(BirdHouseTracker.class)).thenReturn(birds);
            when(injector.getInstance(FarmingContractManager.class)).thenReturn(mock(FarmingContractManager.class));nativePlugin.attach(injector);
            when(plugins.getPlugins()).thenReturn(List.of(nativePlugin));when(plugins.isPluginActive(nativePlugin)).thenReturn(true);
            RunewardenConfig hudConfig=new RunewardenConfig(){@Override public boolean hudCompact(){return false;}};
            tool=new HarvestClockTool(client,config,plugins,mock(OverlayManager.class),hudConfig);
            tool.setFarmTimers(new FarmHarvestTimers(client,config,catalog));
        }
        void record(String type,String group,String desired,long observed,long deadline)
        {
            FarmPatchCatalog.Patch patch=catalog.patches().stream().filter(candidate->type.equals(candidate.type)).findFirst().orElseThrow();
            for(int value=0;value<256;value++)
            {
                FarmPatchCatalog.State state=catalog.state(type,value);
                if(state==null||!desired.equals(state.state)||"WEEDS".equals(state.crop)||"SCARECROW".equals(state.crop))continue;
                if("HARVESTABLE".equals(desired)&&state.tickMinutes!=0&&state.stage!=state.stages-1)continue;
                if("GROWING".equals(desired)&&(state.tickMinutes<=0||state.stage>=state.stages-1))continue;
                values.put(key(TimeTrackingConfig.CONFIG_GROUP,owner,patch.regionId+"."+patch.varbit),value+":"+observed);
                deadlinesByObservation.put(observed,deadline);deadlinesByGroup.put(group,deadline);return;
            }
            throw new AssertionError(type+" has no usable native "+desired+" record");
        }
        String key(String group,String account,String property){return group+":"+account+":"+property;}
        String saved(String group,String account,String property){return values.get(key(group,account,property));}
        void start(){tool.start();for(int tick=0;tick<10;tick++)tool.onGameTick();}
        void close() throws Exception{tool.stop();SwingUtilities.invokeAndWait(()->{});}
    }
    private static final class NativeTracker extends TimeTrackingPlugin
    {void attach(Injector injector){this.injector=injector;}}
}
