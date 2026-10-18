/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VarlamoreThievingCoachTest
{
    private Client client;private Player player;private WorldView view;private ConfigManager config;private VarlamoreThievingCoach coach;
    private final AtomicLong clock=new AtomicLong(100000);
    private final AtomicReference<WorldPoint> position=new AtomicReference<>(new WorldPoint(1690,3105,0));
    private final AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");
    private final List<NPC> actors=new ArrayList<>();private final AtomicReference<Item[]> carried=new AtomicReference<>(new Item[]{new Item(29325,3),new Item(29332,10)});
    @Before @SuppressWarnings("unchecked") public void setup()
    {
        client=mock(Client.class);player=mock(Player.class);view=mock(WorldView.class);config=mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getWorld()).thenReturn(301);when(client.getRealSkillLevel(Skill.THIEVING)).thenReturn(80);
        when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenAnswer(e->position.get());when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.getBaseX()).thenReturn(1600);when(view.getBaseY()).thenReturn(3072);when(client.getCanvasWidth()).thenReturn(500);when(client.getCanvasHeight()).thenReturn(330);
        IndexedObjectSet<NPC> npcs=mock(IndexedObjectSet.class);when(npcs.iterator()).thenAnswer(e->actors.iterator());
        when(npcs.byIndex(anyInt())).thenAnswer(e->actors.stream().filter(n->n.getIndex()==(Integer)e.getArgument(0)).findFirst().orElse(null));doReturn(npcs).when(view).npcs();
        when(config.getRSProfileKey()).thenAnswer(e->profile.get());ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenAnswer(e->carried.get());when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        coach=new VarlamoreThievingCoach(client,config,new RunewardenDataStore(config,new Gson()),new RunewardenConfig(){},clock::get);coach.start();
    }
    @Test public void joiningMidDistractionShowsBoundedCountdownWithoutInventingAnExactStart()
    {
        NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);when(citizen.getInteracting()).thenReturn(urchin);
        coach.onGameTick();assertTrue(coach.reading().active);assertTrue(coach.reading().objective.startsWith("DISTRACTED"));
        assertFalse(coach.reading().distraction.synced);assertEquals("≥ 0s",coach.reading().distraction.elapsed(clock.get()));
        assertEquals("≤ 24s",coach.reading().distraction.left(clock.get()));assertEquals("~60–99s",coach.reading().distraction.nextText(clock.get()));
        clock.addAndGet(6000);coach.onGameTick();assertEquals("≥ 6s",coach.reading().distraction.elapsed(clock.get()));assertEquals("≤ 18s",coach.reading().distraction.left(clock.get()));
        assertTrue(coach.reading().targets.stream().anyMatch(t->t.npc==citizen&&t.label.equals("DISTRACTED")));
    }
    @Test public void unrelatedNpcOrPlayerInteractionIsNotADistraction()
    {
        NPC citizen=npc(13302,1690,3105);when(citizen.getInteracting()).thenReturn(mock(Player.class));coach.onGameTick();assertFalse(coach.reading().objective.startsWith("DISTRACTED"));
        NPC unrelated=npc(13309,1691,3105);when(citizen.getInteracting()).thenReturn(unrelated);assertFalse(VarlamoreThievingCoach.distracted(citizen));
    }
    @Test public void nativeStartMessageAddsEstimatedDistractionAndNextCycleOnlyToCurrentAccount()
    {
        NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);when(citizen.getInteracting()).thenReturn(urchin);
        chat(ChatMessageType.PUBLICCHAT,"You notice an urchin distract a wealthy citizen nearby.");coach.onGameTick();assertFalse(coach.reading().distraction.synced);
        chat(ChatMessageType.GAMEMESSAGE,"You notice an urchin distract a wealthy citizen nearby.");coach.onGameTick();assertEquals(2,coach.reading().timers.size());assertTrue(coach.reading().distraction.synced);
        profile.set("rsprofile-b");assertFalse(coach.reading().active);coach.onGameTick();assertFalse(coach.reading().distraction.synced);
    }
    @Test public void observedNativeTransitionStartsTheCountdownWithoutNeedingChatAndNativeEndStopsIt()
    {
        NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);coach.onGameTick();
        clock.addAndGet(600);when(citizen.getInteracting()).thenReturn(urchin);coach.onGameTick();assertTrue(coach.reading().distraction.synced);
        clock.addAndGet(7000);coach.onGameTick();assertEquals("~7s",coach.reading().distraction.elapsed(clock.get()));assertEquals("≤ 17s",coach.reading().distraction.left(clock.get()));
        when(citizen.getInteracting()).thenReturn(null);coach.onGameTick();assertFalse(coach.reading().distraction.active);assertEquals("~77–92s",coach.reading().distraction.nextText(clock.get()));
        clock.addAndGet(100000);coach.onGameTick();assertEquals("Due · waiting",coach.reading().distraction.nextText(clock.get()));
    }
    @Test public void aSameWorldSceneLoadPreservesTheCycleButAWorldHopClearsIt()
    {
        NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);when(citizen.getInteracting()).thenReturn(urchin);
        chat(ChatMessageType.GAMEMESSAGE,"You notice an urchin distract a wealthy citizen nearby.");coach.onGameTick();long started=coach.reading().distraction.started;
        GameStateChanged loading=new GameStateChanged();loading.setGameState(GameState.LOADING);coach.onGameStateChanged(loading);clock.addAndGet(5000);coach.onGameTick();
        assertEquals(started,coach.reading().distraction.started);assertEquals("~5s",coach.reading().distraction.elapsed(clock.get()));
        GameStateChanged hop=new GameStateChanged();hop.setGameState(GameState.HOPPING);coach.onGameStateChanged(hop);coach.onGameTick();assertFalse(coach.reading().distraction.synced);
    }
    @Test public void aUniqueVacantHouseGetsOneRouteThatClearsOnEntry()
    {
        RouteFixture route=new RouteFixture();wall(51998,1668,3094);npc(13312,1668,3102);coach.onGameTick();
        assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().availableHouse);
        assertEquals(VarlamoreThievingState.House.LAVINIA.exterior,route.receiver.target);assertEquals(1,route.receiver.count("path"));
        assertTrue(coach.reading().targets.stream().anyMatch(t->t.label.equals("ENTER · Lavinia")));
        for(int i=0;i<20;i++)coach.onGameTick();assertEquals(1,route.receiver.count("path"));
        position.set(new WorldPoint(1670,3088,0));coach.onGameTick();assertNull(route.receiver.target);
        assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().house);route.nav.stop();
    }
    @Test public void ownerProximityNoKeyAndConflictingHousesCannotCreateAnEntryRoute()
    {
        RouteFixture route=new RouteFixture();wall(51998,1668,3094);NPC owner=npc(13312,1668,3095);coach.onGameTick();
        assertNull(coach.reading().availableHouse);assertNull(route.receiver.target);
        when(owner.getWorldLocation()).thenReturn(new WorldPoint(1668,3102,0));carried.set(new Item[0]);coach.onGameTick();
        assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().availableHouse);assertTrue(coach.reading().houseStatus.contains("key required"));assertNull(route.receiver.target);
        carried.set(new Item[]{new Item(29325,3)});WallObject conflicting=wall(51998,1636,3103);coach.onGameTick();assertNull(coach.reading().availableHouse);assertNull(route.receiver.target);
        despawn(conflicting);coach.onGameTick();assertNotNull(route.receiver.target);
        when(owner.getWorldLocation()).thenReturn(new WorldPoint(1668,3095,0));coach.onGameTick();assertNull(route.receiver.target);route.nav.stop();
    }
    @Test public void aLoadedOwnerAtTheDoorStillBlocksEntryBeyondTheHighlightRadius()
    {
        position.set(new WorldPoint(1720,3120,0));RouteFixture route=new RouteFixture();wall(51998,1668,3094);npc(13312,1668,3095);
        coach.onGameTick();assertNull(coach.reading().availableHouse);assertNull(route.receiver.target);route.nav.stop();
    }
    @Test public void aHouseRouteClearsOnDoorOpeningDisableAndLeavingTheActivity() throws Exception
    {
        RouteFixture route=new RouteFixture();WallObject locked=wall(51998,1668,3094);coach.onGameTick();assertNotNull(route.receiver.target);
        despawn(locked);WallObject open=wall(51999,1668,3094);coach.onGameTick();assertNull(route.receiver.target);
        despawn(open);wall(51998,1668,3094);coach.onGameTick();assertNotNull(route.receiver.target);
        SwingUtilities.invokeAndWait(()->((JCheckBox)named(coach.createPanel(),"varlamoreThievinghouseRoute")).doClick());coach.onGameTick();assertNull(route.receiver.target);
        SwingUtilities.invokeAndWait(()->((JCheckBox)named(coach.createPanel(),"varlamoreThievinghouseRoute")).doClick());coach.onGameTick();assertNotNull(route.receiver.target);
        position.set(new WorldPoint(3200,3200,0));coach.onGameTick();assertNull(route.receiver.target);route.nav.stop();
    }
    @Test public void switchingVacantHousesClearsTheOldLineWhileAReplacementIsBlocked()
    {
        RouteFixture route=new RouteFixture();WallObject lavinia=wall(51998,1668,3094);coach.onGameTick();assertEquals(VarlamoreThievingState.House.LAVINIA.exterior,route.receiver.target);
        despawn(lavinia);wall(51999,1668,3094);wall(51998,1636,3103);when(player.getInteracting()).thenReturn(mock(NPC.class));coach.onGameTick();
        assertEquals(VarlamoreThievingState.House.VICTOR,coach.reading().availableHouse);assertNull(route.receiver.target);
        when(player.getInteracting()).thenReturn(null);coach.onGameTick();assertEquals(VarlamoreThievingState.House.VICTOR.exterior,route.receiver.target);route.nav.stop();
    }
    @Test public void choosingAManualPathSuspendsAutomaticHouseRoutesUntilExplicitlyResumed() throws Exception
    {
        RouteFixture route=new RouteFixture();wall(51998,1668,3094);coach.onGameTick();
        WorldPoint manual=new WorldPoint(1700,3100,0);route.events.post(new net.runelite.client.events.PluginMessage("shortestpath","path",java.util.Map.of("target",manual)));
        for(int i=0;i<20;i++)coach.onGameTick();assertEquals(manual,route.receiver.target);
        SwingUtilities.invokeAndWait(()->((JCheckBox)named(coach.createPanel(),"varlamoreThievinghouseRoute")).doClick());coach.onGameTick();assertEquals(manual,route.receiver.target);
        SwingUtilities.invokeAndWait(()->((JCheckBox)named(coach.createPanel(),"varlamoreThievinghouseRoute")).doClick());coach.onGameTick();assertEquals(VarlamoreThievingState.House.LAVINIA.exterior,route.receiver.target);
        coach.stop();assertNull(route.receiver.target);route.nav.stop();
    }
    @Test public void conflictingDoorFormsNeverCreateADepartureEstimate()
    {
        position.set(new WorldPoint(1670,3088,0));wall(51999,1668,3094);coach.onGameTick();wall(51998,1668,3094);coach.onGameTick();
        assertTrue(coach.reading().timers.isEmpty());assertFalse(coach.reading().facts.contains("Owner · away · departure not observed"));
    }
    @Test public void enteringAnAlreadyLockedHouseLeavesReturnTimeUnknown()
    {
        position.set(new WorldPoint(1670,3088,0));wall(51998,1668,3094);coach.onGameTick();assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().house);
        assertTrue(coach.reading().timers.isEmpty());assertTrue(coach.reading().facts.contains("Owner · away · departure not observed"));
    }
    @Test public void observedDoorTransitionTracksReturnAndNativeWhistlePrioritisesExit()
    {
        position.set(new WorldPoint(1670,3088,0));WallObject open=wall(51999,1668,3094);coach.onGameTick();despawn(open);clock.addAndGet(600);wall(51998,1668,3094);coach.onGameTick();
        assertEquals(1,coach.reading().timers.size());assertEquals("Owner return",coach.reading().timers.get(0).label);
        wall(52998,1670,3088);chat(ChatMessageType.SPAM,"You hear someone outside spot the homeowner coming back!");coach.onGameTick();
        assertTrue(coach.reading().objective.startsWith("OWNER RETURNING"));assertTrue(coach.reading().targets.stream().anyMatch(t->t.label.equals("Exit window")));
    }
    @Test public void bonusFurnitureFollowsTheExistingNativeArrowAndStopsWhenItDisappears()
    {
        position.set(new WorldPoint(1670,3088,0));GameObject chest=object(52008,1672,3090);coach.onGameTick();
        when(client.getHintArrowType()).thenReturn(HintArrowType.COORDINATE);when(client.getHintArrowPoint()).thenReturn(new WorldPoint(1672,3090,0));chat(ChatMessageType.GAMEMESSAGE,"You notice something shine somewhere else in the house.");coach.onGameTick();
        assertTrue(coach.reading().objective.startsWith("BONUS"));assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==chest&&t.label.startsWith("BONUS")));
        when(client.getHintArrowPoint()).thenReturn(new WorldPoint(3200,3200,0));coach.onGameTick();assertFalse(coach.reading().targets.stream().anyMatch(t->t.label.startsWith("BONUS")));verify(client,never()).clearHintArrow();
    }
    @Test public void manuallySelectedContainerStartsItsRangeOnlyAfterActuallyReceivingLoot()
    {
        position.set(new WorldPoint(1670,3088,0));object(52008,1672,3090);coach.onGameTick();
        MenuOptionClicked click=mock(MenuOptionClicked.class);when(click.getId()).thenReturn(52008);when(click.getMenuOption()).thenReturn("Search");when(click.getMenuAction()).thenReturn(MenuAction.GAME_OBJECT_FIRST_OPTION);when(click.getParam0()).thenReturn(72);when(click.getParam1()).thenReturn(18);
        coach.onMenuOptionClicked(click);coach.onGameTick();assertTrue(coach.reading().timers.isEmpty());
        carried.set(new Item[]{new Item(29325,3),new Item(29332,11)});clock.addAndGet(600);coach.onGameTick();assertTrue(coach.reading().timers.stream().anyMatch(t->t.label.equals("Container")));
        chat(ChatMessageType.SPAM,"You can't spot anything else worth taking from there.");coach.onGameTick();assertTrue(coach.reading().objective.startsWith("Container empty"));assertFalse(coach.reading().timers.stream().anyMatch(t->t.label.equals("Container")));
    }
    @Test public void itemCountsAndGloryValueAreNativeAndDoNotOverflow()
    {
        npc(13302,1690,3105);when(client.getVarpValue(4130)).thenReturn(8000);coach.onGameTick();assertEquals(3,coach.reading().keys);assertEquals(10,coach.reading().valuables);assertEquals(650,coach.reading().tradeIn);
    }
    @Test public void outsideCityWrongPlaneWorldHopAndStopClearDisplay()
    {
        npc(13302,1690,3105);coach.onGameTick();assertTrue(coach.reading().active);when(client.getWorld()).thenReturn(302);assertFalse(coach.reading().active);coach.onGameTick();assertEquals(302,coach.reading().world);
        position.set(new WorldPoint(1690,3105,1));coach.onGameTick();assertFalse(coach.reading().active);position.set(new WorldPoint(3200,3200,0));coach.onGameTick();assertFalse(coach.reading().active);coach.stop();assertFalse(coach.started());
    }
    @Test public void sceneCacheIsBoundedAndNativeDiagnosticsDoNotInventMappings()
    {
        position.set(new WorldPoint(1670,3088,0));for(int i=0;i<300;i++)object(52008,1672,3090);assertEquals(192,coach.observedCount());
        when(client.getVarbitValue(9928)).thenReturn(1);when(client.getVarbitValue(9929)).thenReturn(37);assertArrayEquals(new int[]{1,37,0,0,0},coach.nativeActivityFields());
    }
    @Test public void aLoadedAccessibleDoorKeepsItsFloatingCardFiftyThreeTilesAway()
    {
        position.set(new WorldPoint(1721,3105,0));when(view.getBaseX()).thenReturn(1648);
        WallObject door=wall(51998,1668,3094);when(door.getCanvasLocation()).thenReturn(new Point(250,260));
        when(door.getCanvasTextLocation(any(),anyString(),anyInt())).thenReturn(new Point(250,230));coach.onGameTick();
        assertEquals(53,position.get().distanceTo(door.getWorldLocation()));
        assertTrue("The loaded door must be a scene target beyond the former 40-tile cutoff",coach.reading().targets.stream().anyMatch(t->t.object==door));
        BufferedImage image=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();coach.scene().render(g);g.dispose();
        assertNotEquals("The actual distant door must retain its floating card and pointer",0,image.getRGB(250,260));
        despawn(door);coach.onGameTick();image=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB);g=image.createGraphics();coach.scene().render(g);g.dispose();
        assertEquals("An unloaded door may not leave a distant marker behind",0,image.getRGB(250,260));
    }
    @Test public void hudFitsCanvasAndEveryDisplayOptionIsEditable() throws Exception
    {
        NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);when(citizen.getInteracting()).thenReturn(urchin);chat(ChatMessageType.GAMEMESSAGE,"You notice an urchin distract a wealthy citizen nearby.");coach.onGameTick();
        Graphics2D g=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB).createGraphics();try{java.awt.Dimension size=coach.hud().render(g);assertNotNull(size);assertTrue(size.width<=500);assertTrue(size.height<=330);}finally{g.dispose();}
        SwingUtilities.invokeAndWait(()->{JCheckBox enabled=(JCheckBox)named(coach.createPanel(),"varlamoreThievinghud");enabled.doClick();assertFalse(coach.preferences().hud);});
        coach.stop();SwingUtilities.invokeAndWait(()->{});
    }
    @Test public void distractionAndHouseHudRenderTogetherWithReadableCountdowns() throws Exception
    {
        RouteFixture route=new RouteFixture();NPC citizen=npc(13302,1690,3105),urchin=npc(13306,1691,3105);
        coach.onGameTick();when(citizen.getInteracting()).thenReturn(urchin);clock.addAndGet(600);coach.onGameTick();
        wall(51998,1668,3094);clock.addAndGet(7000);coach.onGameTick();
        assertEquals("~7s",coach.reading().distraction.elapsed(clock.get()));assertEquals("≤ 17s",coach.reading().distraction.left(clock.get()));
        assertEquals("~77–92s",coach.reading().distraction.nextText(clock.get()));assertEquals(VarlamoreThievingState.House.LAVINIA,coach.reading().availableHouse);
        BufferedImage image=new BufferedImage(500,380,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
        try
        {
            g.setColor(new java.awt.Color(18,19,21));g.fillRect(0,0,500,380);g.setColor(HudStyle.GOLD);g.setFont(HudStyle.body(10));g.drawString("VISUAL QA FIXTURE · mocked client state",10,15);
            g.translate(10,30);java.awt.Dimension size=coach.hud().render(g);assertNotNull(size);assertTrue(size.width<=500);assertTrue(size.height<=330);
        }
        finally{g.dispose();}
        java.nio.file.Path directory=java.nio.file.Path.of("build/previews");java.nio.file.Files.createDirectories(directory);javax.imageio.ImageIO.write(image,"png",directory.resolve("varlamore-hud.png").toFile());
        route.nav.stop();
    }
    @Test public void aNativeNpcCompositionTornDownBeforeRenderIsNeverProjected()
    {
        NPC citizen=npc(13302,1690,3105);coach.onGameTick();assertTrue(coach.reading().active);
        when(citizen.getComposition()).thenReturn(null);
        doThrow(new NullPointerException("native footprint composition is gone")).when(citizen).getCanvasTextLocation(any(),anyString(),anyInt());
        renderScene();verify(citizen,never()).getCanvasTilePoly();verify(citizen,never()).getCanvasTextLocation(any(),anyString(),anyInt());
        when(citizen.getComposition()).thenReturn(mock(NPCComposition.class));when(citizen.getTransformedComposition()).thenReturn(null);
        renderScene();verify(citizen,never()).getCanvasTilePoly();verify(citizen,never()).getCanvasTextLocation(any(),anyString(),anyInt());
    }
    @Test public void aDespawnedOrReplacedNpcDoesNotRenderFromThePriorTickSnapshot()
    {
        NPC citizen=npc(13302,1690,3105);coach.onGameTick();assertTrue(coach.reading().targets.stream().anyMatch(t->t.npc==citizen));
        actors.remove(citizen);coach.onNpcDespawned(new NpcDespawned(citizen));
        renderScene();verify(citizen,never()).getCanvasTextLocation(any(),anyString(),anyInt());
        int previousIndex=citizen.getIndex();NPC replacement=npc(13302,1690,3105);when(replacement.getIndex()).thenReturn(previousIndex);
        renderScene();verify(citizen,never()).getCanvasTilePoly();verify(replacement,never()).getCanvasTextLocation(any(),anyString(),anyInt());
    }
    @Test public void logoutHopAndMissingLocalPlayerDoNotProjectPreviouslyObservedNpcs()
    {
        NPC citizen=npc(13302,1690,3105);coach.onGameTick();renderScene();
        verify(citizen).getCanvasTextLocation(any(),eq("Wealthy citizen"),eq(35));clearInvocations(citizen);
        when(client.getLocalPlayer()).thenReturn(null);renderScene();verify(citizen,never()).getCanvasTilePoly();
        when(client.getLocalPlayer()).thenReturn(player);
        for(GameState transition:List.of(GameState.HOPPING,GameState.LOGIN_SCREEN))
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);coach.onGameTick();assertTrue(coach.reading().active);
            when(client.getGameState()).thenReturn(transition);GameStateChanged event=new GameStateChanged();event.setGameState(transition);coach.onGameStateChanged(event);
            renderScene();assertFalse(coach.reading().active);verify(citizen,never()).getCanvasTextLocation(any(),anyString(),anyInt());
        }
    }
    private void renderScene(){Graphics2D g=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB).createGraphics();try{assertNull(coach.scene().render(g));}finally{g.dispose();}}
    private final class RouteFixture
    {
        final net.runelite.client.eventbus.EventBus events=new net.runelite.client.eventbus.EventBus();
        final HarvestNavigationTest.Receiver receiver=new HarvestNavigationTest.Receiver(events);
        final HarvestNavigation nav;
        RouteFixture()
        {
            net.runelite.client.plugins.PluginManager plugins=mock(net.runelite.client.plugins.PluginManager.class);
            net.runelite.client.plugins.Plugin companion=new shortestpath.ShortestPathPlugin();
            when(plugins.getPlugins()).thenReturn(List.of(companion));when(plugins.isPluginActive(companion)).thenReturn(true);
            nav=new HarvestNavigation(client,events,plugins,null,config,null);events.register(new HarvestNavigationTest.Subscriber(nav));events.register(receiver);
            nav.start();nav.onGameTick();coach.setNavigation(nav);
        }
    }
    private NPC npc(int id,int x,int y){NPC npc=mock(NPC.class);when(npc.getId()).thenReturn(id);when(npc.getIndex()).thenReturn(actors.size()+1);when(npc.getWorldLocation()).thenReturn(new WorldPoint(x,y,0));when(npc.getWorldView()).thenReturn(view);NPCComposition definition=mock(NPCComposition.class);when(npc.getComposition()).thenReturn(definition);when(npc.getTransformedComposition()).thenReturn(definition);actors.add(npc);return npc;}
    private WallObject wall(int id,int x,int y){WallObject wall=mock(WallObject.class);when(wall.getId()).thenReturn(id);when(wall.getWorldLocation()).thenReturn(new WorldPoint(x,y,0));when(wall.getWorldView()).thenReturn(view);WallObjectSpawned event=new WallObjectSpawned();event.setWallObject(wall);coach.onWallObjectSpawned(event);return wall;}
    private void despawn(WallObject wall){WallObjectDespawned event=new WallObjectDespawned();event.setWallObject(wall);coach.onWallObjectDespawned(event);}
    private GameObject object(int id,int x,int y){GameObject object=mock(GameObject.class);when(object.getId()).thenReturn(id);when(object.getWorldLocation()).thenReturn(new WorldPoint(x,y,0));when(object.getWorldView()).thenReturn(view);GameObjectSpawned event=new GameObjectSpawned();event.setGameObject(object);coach.onGameObjectSpawned(event);return object;}
    private void chat(ChatMessageType type,String text){ChatMessage event=new ChatMessage();event.setType(type);event.setMessage(text);coach.onChatMessage(event);}
    private static Component named(Container parent,String name){for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component found=named((Container)child,name);if(found!=null)return found;}}return null;}
}
