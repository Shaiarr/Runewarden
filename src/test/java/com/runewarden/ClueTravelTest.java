/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.dbtable.DBRowConfig;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.item.AllRequirementsCollection;
import net.runelite.client.plugins.cluescrolls.clues.item.SingleItemRequirement;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ClueTravelTest
{
    private static final class Fixture
    {
        final Client client=mock(Client.class); final Player player=mock(Player.class);
        final ConfigManager config=mock(ConfigManager.class); final PluginManager plugins=mock(PluginManager.class);
        final EventBus events=mock(EventBus.class); final Gson gson=new Gson();
        final OwnershipLedger ownership=new OwnershipLedger(client,config,gson,() -> 1000L);
        final GuideNavigation navigation=new GuideNavigation(client,events,plugins,null,config,null);
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            when(config.getRSProfileKey()).thenReturn("rsprofile.one"); when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
            when(plugins.getPlugins()).thenReturn(Collections.emptyList()); ownership.start(); ownership.onGameTick();
        }
        void shortestPath()
        { Plugin plugin=new shortestpath.ShortestPathPlugin(); when(plugins.getPlugins()).thenReturn(Collections.singleton(plugin)); when(plugins.isPluginEnabled(plugin)).thenReturn(true); when(plugins.isPluginActive(plugin)).thenReturn(true); navigation.start(); navigation.onGameTick(); }
        List<PluginMessage> sent()
        { ArgumentCaptor<Object> captor=ArgumentCaptor.forClass(Object.class); verify(events,atLeastOnce()).post(captor.capture());
          List<PluginMessage> result=new ArrayList<>(); for (Object value : captor.getAllValues()) if (value instanceof PluginMessage) result.add((PluginMessage)value); return result; }
    }

    @Test public void coordinateInputRejectsInvalidBoundsAndExtraTokens()
    {
        assertEquals(new WorldPoint(3200,3201,0),GuideNavigation.parseCoordinates("3200, 3201"));
        assertEquals(new WorldPoint(3200,3201,2),GuideNavigation.parseCoordinates("3200 3201 2"));
        for (String text : new String[]{"-1 3200","3200 3200 4","3200 x","3200 3200 1 extra","99999999999999999 1"}) assertNull(text,GuideNavigation.parseCoordinates(text));
    }

    @Test public void planningCarriesExactAlternativesAndReadsTheCompletedNativeRouteWithoutAnotherSearch()
    {
        Fixture f=new Fixture(); f.shortestPath(); WorldPoint point=new WorldPoint(3000,3000,0);
        Map<Integer,Integer> alternatives=new LinkedHashMap<>(); alternatives.put(952,1); alternatives.put(300,2);
        assertTrue(f.navigation.preview("Dig step",point,Collections.singletonList(alternatives)));
        PluginMessage path=f.sent().get(0);assertEquals("path",path.getName());
        Map<?,?> target=(Map<?,?>)path.getData().get("target"); assertEquals(point,target.get("point"));
        alternatives.put(952,8); assertEquals(Integer.valueOf(1),((Map<?,?>)((List<?>)target.get("requirements")).get(0)).get(952));
        assertEquals(2,f.sent().size());PluginMessage confirmation=f.sent().get(1);
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","currentTarget",Map.of("id",confirmation.getData().get("id"),"set",true,"target",List.of(target))));
        Set<WorldPoint> points=new LinkedHashSet<>(Set.of(point));List<String> transports=new ArrayList<>(List.of("Actual native bank detour"));
        ShortestPathRouteReading result=new ShortestPathRouteReading(new WorldPoint(3200,3200,0),point,points,true,true,42,"REACHED","WITHDRAW_FROM_BANK",transports);
        points.clear();transports.clear();f.navigation.setRouteReader(plugin->result);f.navigation.onGameTick();
        assertTrue(f.navigation.snapshot().reached); assertTrue(f.navigation.snapshot().bankVisited); assertEquals(Integer.valueOf(42),f.navigation.snapshot().cost);
        assertEquals("WITHDRAW_FROM_BANK",f.navigation.snapshot().itemStatus);assertEquals(List.of("Actual native bank detour"),f.navigation.snapshot().transports);
        assertFalse(f.sent().stream().anyMatch(message->"query".equals(message.getName())));
        assertTrue(f.navigation.showPreview("clue")); assertEquals("path",f.sent().get(2).getName());
    }

    @Test public void changedAccountCannotAcceptThePriorAccountsCompletedNativeRoute()
    {
        Fixture f=new Fixture(); f.shortestPath(); f.navigation.preview("Old account",new WorldPoint(3000,3000,0),Collections.emptyList());
        Object id=f.sent().get(1).getData().get("id");WorldPoint point=new WorldPoint(3000,3000,0);
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","currentTarget",Map.of("id",id,"set",true,"target",List.of(point))));
        f.navigation.setRouteReader(plugin->new ShortestPathRouteReading(new WorldPoint(3200,3200,0),point,Set.of(point),true,false,42,"REACHED","",Collections.emptyList()));
        when(f.config.getRSProfileKey()).thenReturn("two");
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","transports",Map.of("displayInfo",List.of("Old account")))); assertFalse(f.navigation.snapshot().answered);
        f.navigation.onGameTick(); assertNull(f.navigation.snapshot().point);
    }

    @Test public void queuedManualActionAndClueGuardRejectAccountSwitchBeforeNextTick() throws Exception
    {
        Fixture f=new Fixture(); ClientThread thread=mock(ClientThread.class);
        GuideNavigation navigation=new GuideNavigation(f.client,f.events,f.plugins,thread,f.config,null);
        navigation.start(); navigation.onGameTick();
        ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson); data.onGameTick();
        assertTrue(data.isCurrentProfile("rsprofile.one"));
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel=navigation.createPanel();
            for (Component child : descendants(panel)) if (child instanceof JButton && "Open world switcher".equals(((JButton)child).getText()))
                ((JButton)child).doClick();
        });
        ArgumentCaptor<Runnable> queued=ArgumentCaptor.forClass(Runnable.class); verify(thread).invokeLater(queued.capture());
        when(f.config.getRSProfileKey()).thenReturn("rsprofile.two");
        assertFalse(data.isCurrentProfile("rsprofile.one"));
        queued.getValue().run(); verify(f.client,never()).openWorldHopper();
    }

    @Test public void cleanupChecksCurrentTargetAndNeverClearsAManualReplacement()
    {
        Fixture f=new Fixture(); f.shortestPath(); WorldPoint point=new WorldPoint(3000,3000,0);
        f.navigation.routeOwned("clue",point,"Clue"); f.navigation.clearOwned("clue");
        PluginMessage check=f.sent().get(2); assertEquals("getTarget",check.getName());
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","path",Collections.singletonMap("target",new WorldPoint(3100,3100,0))));
        Map<String,Object> answer=new HashMap<>(); answer.put("id",check.getData().get("id")); answer.put("set",true); answer.put("target",Collections.singletonList(point));
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","currentTarget",answer)); assertEquals(3,f.sent().size());
    }

    @Test public void cleanupOfStillOwnedPathUsesPublicReceiverConfirmation()
    {
        Fixture f=new Fixture(); f.shortestPath(); WorldPoint point=new WorldPoint(3000,3000,0);
        f.navigation.routeOwned("clue",point,"Clue"); f.navigation.clearOwned("clue"); PluginMessage check=f.sent().get(2);
        Map<String,Object> answer=new HashMap<>(); answer.put("id",check.getData().get("id")); answer.put("set",true); answer.put("target",Collections.singletonList(point));
        f.navigation.onPluginMessage(new PluginMessage("shortestpath","currentTarget",answer)); assertEquals("clear",f.sent().get(3).getName());
    }

    @Test public void nativeEmoteSuppliesSeparateCarriedFromActuallyEquipped()
    {
        Fixture f=new Fixture(); ClueScrollPlugin nativePlugin=mock(ClueScrollPlugin.class);
        EmoteClue clue=EmoteClue.forText("Bow upstairs in the Edgeville Monastery. Equip a completed prayer book."); assertNotNull(clue);
        when(plugins(f).getPlugins()).thenReturn(Collections.singletonList(nativePlugin)); when(f.plugins.isPluginEnabled(nativePlugin)).thenReturn(true); when(nativePlugin.getClue()).thenReturn(clue);
        ItemContainer inventory=mock(ItemContainer.class),equipment=mock(ItemContainer.class);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SARADOMINBOOK_COMPLETE,1)}); when(equipment.getItems()).thenReturn(new Item[0]);
        when(f.client.getItemContainer(InventoryID.INV)).thenReturn(inventory); when(f.client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
        ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson);
        data.onGameTick(); data.onGameTick(); data.onGameTick(); ClueDataService.Snapshot reading=data.snapshot();
        assertFalse(reading.key.isEmpty()); assertEquals("Bow",reading.instruction); assertEquals(1,reading.needs.size());
        assertEquals("Equip before the emote",reading.needs.get(0).state); assertFalse(reading.needs.get(0).fulfilled);
        when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.SARADOMINBOOK_COMPLETE,1)}); data.onGameTick(); assertTrue(data.snapshot().needs.get(0).fulfilled);
        when(nativePlugin.getClue()).thenReturn(null); data.onGameTick(); assertTrue(data.snapshot().destinations.isEmpty()); assertTrue(data.snapshot().needs.isEmpty());
    }
    private static PluginManager plugins(Fixture fixture) { return fixture.plugins; }

    @Test public void compositeEquipmentSetIsNotFlattenedIntoAFalseRouteAlternative()
    {
        Fixture f=new Fixture(); ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson);
        assertTrue(data.groups(Collections.singletonList(new AllRequirementsCollection(new SingleItemRequirement(952),new SingleItemRequirement(1205)))).isEmpty());
        assertFalse(data.groups(Collections.singletonList(new SingleItemRequirement(952))).isEmpty());
    }

    @Test public void stashAliasesAreBundledAndFilledStateStartsUnknown()
    {
        Fixture f=new Fixture(); ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson);
        assertTrue(data.stashes().size()>=110);
        ClueDataService.StashReference reference=data.stashes().get(0); assertNotNull(data.chartKey(reference.chartText,4));
        assertEquals(Arrays.asList(10472,7336),reference.itemIds); // Native IDs, not nested Cert/Placeholder constants.
        assertNull(data.stashState(reference.unit()).filled); assertNull(data.chartKey("This unknown chart row",4));
        assertEquals("WARRIORS_GUILD_BANK_29047",data.chartKey("Warriors' Guild bank",14));
        assertEquals("WARRIORS_GUILD_BANK",data.chartKey("Warriors' Guild bank",12));
    }

    @Test public void visibleNativeChartChecksEstablishFilledStateAndProfileChangeDiscardsIt()
    {
        Fixture f=new Fixture(); ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson);
        ClueDataService.StashReference reference=data.stashes().get(0);
        Widget chart=mock(Widget.class),column=mock(Widget.class),text=mock(Widget.class),built=mock(Widget.class),filled=mock(Widget.class);
        when(f.client.getWidget(493,2)).thenReturn(chart); when(f.client.getWidget(493,4)).thenReturn(column);
        when(text.getType()).thenReturn(WidgetType.TEXT); when(text.getText()).thenReturn(reference.chartText);
        for (Widget tick : new Widget[]{built,filled}) { when(tick.getType()).thenReturn(WidgetType.GRAPHIC); when(tick.getSpriteId()).thenReturn(SpriteID.STASH_UNITS_GREEN_CHECK_MARK); }
        when(column.getChildren()).thenReturn(new Widget[]{text,built,filled});
        data.onGameTick(); data.onGameTick(); data.onGameTick(); assertEquals(Boolean.TRUE,data.stashState(reference.unit()).filled);
        when(f.config.getRSProfileKey()).thenReturn("two"); data.onGameTick(); assertNull(data.stashState(reference.unit()).filled);
    }

    @Test public void nativeCacheRequirementsKeepBoostAndPartialQuestUncertainty()
    {
        Fixture f=new Fixture(); int clue=600,skill=601,quest=602;
        when(f.client.getDBTableRows(DBTableID.CluehelperClueAnagram.ID)).thenReturn(Collections.singletonList(clue));
        when(f.client.getDBTableField(clue,2,0)).thenReturn(new Object[]{"Exact native clue text"});
        when(f.client.getDBTableField(clue,6,0)).thenReturn(new Object[]{skill,quest});
        DBRowConfig skillRow=mock(DBRowConfig.class),questRow=mock(DBRowConfig.class);
        when(f.client.getDBRowConfig(skill)).thenReturn(skillRow); when(skillRow.getTableID()).thenReturn(DBTableID.CluehelperRequirementStat.ID);
        when(f.client.getDBTableField(skill,0,0)).thenReturn(new Object[]{Skill.MINING.ordinal()}); when(f.client.getDBTableField(skill,1,0)).thenReturn(new Object[]{55});
        when(f.client.getRealSkillLevel(Skill.MINING)).thenReturn(50); when(f.client.getBoostedSkillLevel(Skill.MINING)).thenReturn(55);
        when(f.client.getDBRowConfig(quest)).thenReturn(questRow); when(questRow.getTableID()).thenReturn(DBTableID.CluehelperRequirementQuest.ID);
        when(f.client.getDBTableField(quest,0,0)).thenReturn(new Object[]{"Start Legends' Quest"});
        when(f.client.getDBTableField(quest,1,0)).thenReturn(new Object[]{Quest.LEGENDS_QUEST.getId()});
        when(f.client.getDBTableField(quest,2,0)).thenReturn(new Object[]{2});
        when(f.client.getDBTableField(Quest.LEGENDS_QUEST.getId(),DBTableID.Quest.COL_DISPLAYNAME,0)).thenReturn(new Object[]{"Legends' Quest"});
        when(f.client.getDBTableField(Quest.LEGENDS_QUEST.getId(),DBTableID.Quest.COL_ENDSTATE,0)).thenReturn(new Object[]{10});
        when(f.client.getIntStack()).thenReturn(new int[]{0});
        ClueAccessIndex index=new ClueAccessIndex(f.client,f.gson); index.onGameTick(); assertTrue(index.ready());
        List<ClueAccessIndex.Access> gates=index.access("Exact native clue text"); assertEquals(2,gates.size());
        assertTrue(gates.get(0).state.contains("boost acceptance not checked")); assertEquals("Quest-stage access unknown",gates.get(1).state);
        assertEquals("quest:Legends' Quest",gates.get(1).guideId);
    }

    @Test public void activityWorldsUseObservedNativeActivityAndNeverHop()
    {
        Fixture f=new Fixture(); f.shortestPath(); net.runelite.api.World first=mock(net.runelite.api.World.class),second=mock(net.runelite.api.World.class);
        when(first.getId()).thenReturn(514); when(first.getActivity()).thenReturn("Guardians of the Rift"); when(first.getPlayerCount()).thenReturn(1500);
        when(second.getId()).thenReturn(515); when(second.getActivity()).thenReturn("Wintertodt");
        when(f.client.getWorldList()).thenReturn(new net.runelite.api.World[]{first,second});
        for (int i=0;i<10;i++) f.navigation.onGameTick();
        assertEquals(1,f.navigation.matchingWorlds("Guardians").size()); assertEquals(514,f.navigation.matchingWorlds("Guardians").get(0).id);
        verify(f.client,never()).hopToWorld(any(net.runelite.api.World.class));
    }

    @Test public void emptyClueAndTravelPanelsFit206Pixels() throws Exception
    {
        Fixture f=new Fixture(); ClueDataService data=new ClueDataService(f.client,f.plugins,f.ownership,f.config,f.gson);
        ClueCoachTool clues=new ClueCoachTool(data,f.navigation,mock(ClientThread.class),mock(ItemManager.class));
        SwingUtilities.invokeAndWait(() -> {
            for (JPanel panel : Arrays.asList(clues.createPanel(),f.navigation.createPanel()))
            {
                JPanel wrapper=new JPanel(new java.awt.BorderLayout()); wrapper.add(panel);
                wrapper.setSize(206,2000); layout(wrapper); wrapper.setSize(206,Math.max(800,panel.getPreferredSize().height)); layout(wrapper);
                for (Component child : descendants(panel)) if (child instanceof GuideText)
                    assertTrue("Wrapped text compressed",child.getHeight()>=child.getPreferredSize().height);
                assertTrue(panel.getWidth()<=206);
            }
        });
    }
    private static List<Component> descendants(Container root)
    { List<Component> all=new ArrayList<>(); for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container) all.addAll(descendants((Container)child)); } return all; }
    private static void layout(Container root) { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container)child); }
}
