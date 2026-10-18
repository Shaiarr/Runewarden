/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;
import shortestpath.*;
import shortestpath.overlay.*;
import shortestpath.pathfinder.ActiveSearch;
import shortestpath.pathfinder.PathStep;
import shortestpath.transport.PohNexusPortal;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Uses the separately packaged upstream plugin, collision map and pathfinder through the real EventBus. */
public class GuideShortestPathContractTest
{
    @Test public void nativeCostChoosesWalkingOrACarriedTabletInsteadOfForcingTheKnownHouse() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            when(f.pathConfig.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
            when(f.pathConfig.usePoh()).thenReturn(true);when(f.pathConfig.pohNexusPortals()).thenReturn(Set.of(PohNexusPortal.CATHERBY));
            ItemContainer held=mock(ItemContainer.class);when(held.getItems()).thenReturn(new Item[]{new Item(9789,1),new Item(8010,1)});
            when(f.client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(held);
            WorldPoint patch=new WorldPoint(2808,3463,0);when(f.player.getWorldLocation()).thenReturn(new WorldPoint(2808,3462,0));f.start();
            assertTrue(f.nav.routeOwned("guide",patch,"Nearby patch"));
            f.until(()->f.receiver.getActiveSearch()!=null&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch walk=f.receiver.getActiveSearch();assertTrue(walk.getResult().isReached());
            assertEquals(WorldPointUtil.packWorldPoint(patch),walk.getResult().getTarget());
            assertTrue("A one-tile walk must not use a cape or house",walk.getPath().stream().allMatch(s->s.getTransport()==null));
            when(f.player.getWorldLocation()).thenReturn(f.origin);
            WorldPoint camelot=new WorldPoint(2757,3479,0); // Native teleportation_items.tsv: Camelot tablet.
            assertTrue(f.nav.routeOwned("guide",camelot,"Camelot"));
            f.until(()->f.receiver.getActiveSearch()!=walk&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch tablet=f.receiver.getActiveSearch();assertTrue(tablet.getResult().isReached());
            List<String> used=new ArrayList<>();for(PathStep step:tablet.getPath())if(step.getTransport()!=null)used.add(step.getTransport().getDisplayInfo());
            assertTrue("An available direct tablet should win against the Catherby house detour",used.stream().anyMatch(s->s.contains("Camelot tablet")));
            assertFalse(used.stream().anyMatch(s->s.contains("POH")||s.contains("Catherby Portal")));
        }
        finally{f.close();}
    }
    @Test public void actualAccountCapeAndKnownHousePortalChooseCatherbyInsteadOfTheFixedFirstFarmTown() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            when(f.pathConfig.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
            when(f.pathConfig.usePoh()).thenReturn(true);
            when(f.pathConfig.pohNexusPortals()).thenReturn(Set.of(PohNexusPortal.CATHERBY));
            ItemContainer worn=mock(ItemContainer.class);when(worn.getItems()).thenReturn(new Item[]{new Item(9789,1)});
            when(f.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
            f.start();
            WorldPoint taverley=new WorldPoint(2933,3436,0),catherby=new WorldPoint(2808,3463,0);
            GuideCatalog.Entry guide=f.catalog.entry("training:fruit-tree-runs");
            // The provider order deliberately favours the old fixed town. Native route cost decides.
            f.nav.setArrivalOptions(id->id.equals(guide.id)?List.of(new GuideNavigation.Destination("Taverley",id,taverley),new GuideNavigation.Destination("Catherby",id,catherby)):null);
            assertTrue(f.nav.routeStep(guide,guide.flattened().get(0).step));
            f.until(()->f.nav.snapshot().answered&&f.receiver.getActiveSearch()!=null&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch route=f.receiver.getActiveSearch();assertTrue(route.getResult().isReached());
            assertEquals(WorldPointUtil.packWorldPoint(catherby),route.getResult().getTarget());
            assertEquals(catherby,f.nav.snapshot().point);assertEquals("Catherby",f.nav.snapshot().title);
            List<String> edges=new ArrayList<>();List<PathStep> steps=route.getPath();
            for(int i=1;i<steps.size();i++)f.receiver.transportsForEdge(steps.get(i-1),steps.get(i)).forEach(t->edges.add(t.getDisplayInfo()));
            assertTrue("The real path must use the carried/worn cape, not only label the UI",edges.stream().anyMatch(s->s.contains("Construction cape")&&s.contains("POH")));
            assertTrue("The real path must leave through the account's known Catherby portal",edges.stream().anyMatch(s->s.contains("Catherby Portal")));
            assertTrue(f.nav.snapshot().transports.stream().anyMatch(s->s.contains("Catherby")));

            // While standing in the verified own-house template, use native SP's virtual POH origin.
            WorldView house=mock(WorldView.class);when(f.client.isInInstancedRegion()).thenReturn(true);
            when(f.client.getTopLevelWorldView()).thenReturn(house);when(f.client.getWorldView(WorldView.TOPLEVEL)).thenReturn(house);when(house.isInstance()).thenReturn(true);
            int[][][] chunks=new int[4][13][13];chunks[0][0][0]=((1856/8)<<14)|((7048/8)<<3);when(house.getInstanceTemplateChunks()).thenReturn(chunks);
            when(f.client.getVarbitValue(net.runelite.api.gameval.VarbitID.POH_BUILDING_MODE)).thenReturn(1);
            when(f.player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(2*128+64,3*128+64,WorldView.TOPLEVEL));
            f.nav.onGameTick();
            f.until(()->f.nav.snapshot().answered&&f.receiver.getActiveSearch()!=route&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch fromHouse=f.receiver.getActiveSearch();assertTrue(fromHouse.getResult().isReached());
            assertEquals(WorldPointUtil.packWorldPoint(new WorldPoint(1858,7051,0)),fromHouse.getStart());
            assertEquals(WorldPointUtil.packWorldPoint(catherby),fromHouse.getResult().getTarget());
            assertTrue(f.nav.snapshot().transports.stream().anyMatch(s->s.contains("Catherby")));

            // A teleport changes the next origin and discards the old cape/house travel advice.
            when(f.client.isInInstancedRegion()).thenReturn(false);
            WorldPoint arrived=new WorldPoint(2801,3449,0);when(f.player.getWorldLocation()).thenReturn(arrived);
            f.nav.onGameTick();assertFalse(f.nav.snapshot().answered);assertTrue(f.nav.snapshot().transports.isEmpty());
            f.until(()->f.nav.snapshot().answered&&f.receiver.getActiveSearch()!=fromHouse&&f.receiver.getActiveSearch().getResult()!=null);
            assertEquals(WorldPointUtil.packWorldPoint(arrived),f.receiver.getActiveSearch().getStart());
            assertFalse(f.nav.snapshot().transports.stream().anyMatch(s->s.contains("Construction cape")));
            f.nav.clearOwned("guide");f.drain();assertNull(f.receiver.getActiveSearch());
        }
        finally{f.close();}
    }
    @Test public void realUpstreamReceivesCalculatesAcknowledgesAndClearsOwnedGuideRoutes() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            assertNotNull(ShortestPathPlugin.class.getResource("/collision-map.zip"));
            assertTrue("The receiver must come from the bundled upstream JAR",ShortestPathPlugin.class.getProtectionDomain().getCodeSource().getLocation().toString().endsWith(".jar"));
            f.start();
            verify(f.overlays).add(any(PathTileOverlay.class));
            verify(f.overlays).add(any(PathMinimapOverlay.class));
            assertTrue(f.nav.available());
            assertTrue(f.nav.routeOwned("guide",f.destination,"Guide stop"));
            assertEquals("Waiting for Shortest Path…",f.nav.snapshot().status);
            assertNull("Upstream starts its route on the next client-thread turn",f.receiver.getActiveSearch());
            // Upstream publishes isDone before cleanup ends; getResult remains null until stats.end().
            f.until(()->f.receiver.getActiveSearch()!=null && f.receiver.getActiveSearch().isDone()&&f.receiver.getActiveSearch().getResult()!=null);
            assertEquals("Shortest Path destination confirmed",f.nav.snapshot().status);
            ActiveSearch actual=f.receiver.getActiveSearch();
            assertTrue(actual.getResult().isReached());assertFalse(actual.getPath().isEmpty());
            assertEquals(WorldPointUtil.packWorldPoint(f.destination),actual.getResult().getTarget());
            assertEquals(WorldPointUtil.packWorldPoint(f.origin),actual.getStart());

            // A user's replacement is still their route when a guide closes.
            WorldPoint manual=new WorldPoint(3225,3218,0);
            f.events.post(new PluginMessage("shortestpath","path",Map.of("start",f.origin,"target",manual)));
            f.until(()->f.receiver.getActiveSearch()!=null && f.receiver.getActiveSearch().getTargets().points().contains(WorldPointUtil.packWorldPoint(manual)));
            f.nav.clearOwned("guide");f.drain();
            assertNotNull(f.receiver.getActiveSearch());
            assertTrue(f.receiver.getActiveSearch().getTargets().points().contains(WorldPointUtil.packWorldPoint(manual)));

            // End run before upstream's queued start still cancels the route once initialised.
            assertTrue(f.nav.routeOwned("guide",f.destination,"Next guide stop"));
            f.nav.clearOwned("guide");f.drain();
            assertNull(f.receiver.getActiveSearch());

            assertTrue(f.nav.routeOwned("guide",f.destination,"Paused travel module"));
            f.nav.stop();f.drain();assertNull(f.receiver.getActiveSearch());
            assertFalse(f.nav.routeOwned("guide",f.destination,"Disabled travel"));
        }
        finally { f.close(); }
    }

    @Test public void selectingTheActualFoundryGuideAutomaticallyStartsTheRealBundledPathfinder() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            GuideCatalog.Entry foundry=f.catalog.entry("training:giants-foundry");
            GuideCatalog.Step first=foundry.flattened().get(0).step;
            assertNull("The actual authored guide relies on the sourced activity entrance",first.location);
            WorldPoint entrance=f.catalog.travel().destination(foundry,first);assertNotNull(entrance);
            WorldPoint approach=new WorldPoint(entrance.getX(),entrance.getY()-2,entrance.getPlane());
            when(f.player.getWorldLocation()).thenReturn(approach);
            f.start();
            RunewardenGuides guides=new RunewardenGuides(f.client,f.thread,f.config,f.catalog,f.nav,
                new GuideMarkerOverlay(f.client),new GuideCoachOverlay(f.client),mock(OverlayManager.class));
            SwingUtilities.invokeAndWait(()->{
                javax.swing.JComponent panel=guides.createPanel();guides.openGuide(foundry.id);
                assertFalse("Automatic guidance must not require a route button",hasButton(panel,"Route to this step"));
            });
            f.until(()->f.receiver.getActiveSearch()!=null&&f.receiver.getActiveSearch().isDone()&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch actual=f.receiver.getActiveSearch();assertTrue(actual.getResult().isReached());
            assertEquals(WorldPointUtil.packWorldPoint(entrance),actual.getResult().getTarget());
            assertEquals(WorldPointUtil.packWorldPoint(approach),actual.getStart());assertTrue(actual.getPath().size()>1);
            assertEquals("Shortest Path destination confirmed",f.nav.snapshot().status);
            for(int i=0;i<5;i++){guides.onGameTick();f.nav.onGameTick();f.drain();}
            assertSame("A stable step must not restart the route every tick",actual,f.receiver.getActiveSearch());
            guides.clearSelection();f.drain();assertNull(f.receiver.getActiveSearch());
        }
        finally{f.close();}
    }

    private static boolean hasButton(java.awt.Container parent,String title)
    {
        for(java.awt.Component component:parent.getComponents())
        {
            if(component instanceof javax.swing.JButton&&title.equals(((javax.swing.JButton)component).getText()))return true;
            if(component instanceof java.awt.Container&&hasButton((java.awt.Container)component,title))return true;
        }
        return false;
    }

    public static final class Subscriber
    {
        private final GuideNavigation nav;Subscriber(GuideNavigation nav){this.nav=nav;}
        @Subscribe public void onPluginMessage(PluginMessage event){nav.onPluginMessage(event);}
    }

    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);
        final ConfigManager config=mock(ConfigManager.class);final PluginManager plugins=mock(PluginManager.class);
        final ClientThread thread=mock(ClientThread.class);final Queue<Runnable> queued=new ConcurrentLinkedQueue<>();
        final EventBus events=new EventBus();final OverlayManager overlays=mock(OverlayManager.class);
        final WorldPoint origin=new WorldPoint(3222,3218,0),destination=new WorldPoint(3222,3220,0);
        final ShortestPathPlugin receiver=new ShortestPathPlugin();final GuideNavigation nav;boolean active;
        final GuideCatalog catalog=new GuideCatalog(new Gson());
        final ShortestPathConfig pathConfig=mock(ShortestPathConfig.class,CALLS_REAL_METHODS);
        final PortalNexusKeybinds nexusKeys=mock(PortalNexusKeybinds.class);

        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(origin);when(client.getClientThread()).thenReturn(Thread.currentThread());
            when(client.isClientThread()).thenReturn(true);when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            // RuneLite's Quest.getState runs QUEST_STATUS_GET and reads its native integer stack.
            when(client.getIntStack()).thenReturn(new int[]{1}); // Native NOT_STARTED result.
            when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
            when(config.getRSProfileKey()).thenReturn("rsprofile.contract");ConfigProfile profile=mock(ConfigProfile.class);when(profile.getId()).thenReturn(1L);when(config.getProfile()).thenReturn(profile);
            doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            doAnswer(call->{BooleanSupplier action=call.getArgument(0);queued.add(()->{if(!action.getAsBoolean())throw new AssertionError("Unexpected repeating client task");});return null;}).when(thread).invokeLater(any(BooleanSupplier.class));
            when(plugins.getPlugins()).thenReturn(List.of(receiver));when(plugins.isPluginActive(receiver)).thenAnswer(call->active);
            when(pathConfig.useTeleportationItems()).thenReturn(TeleportationItem.NONE);
            when(nexusKeys.apply(anyString())).thenAnswer(call->call.getArgument(0));
            Guice.createInjector(new AbstractModule()
            {
                @Override protected void configure()
                {
                    bind(Client.class).toInstance(client);bind(ClientThread.class).toInstance(thread);bind(EventBus.class).toInstance(events);
                    bind(ShortestPathConfig.class).toInstance(pathConfig);bind(ConfigManager.class).toInstance(config);bind(OverlayManager.class).toInstance(overlays);
                    bind(PathTileOverlay.class).toInstance(mock(PathTileOverlay.class));bind(PathMinimapOverlay.class).toInstance(mock(PathMinimapOverlay.class));
                    bind(PathMapOverlay.class).toInstance(mock(PathMapOverlay.class));bind(PathMapTooltipOverlay.class).toInstance(mock(PathMapTooltipOverlay.class));
                    bind(BankItemHighlightOverlay.class).toInstance(mock(BankItemHighlightOverlay.class));bind(SpellbookHighlightOverlay.class).toInstance(mock(SpellbookHighlightOverlay.class));
                    bind(InventoryHighlightOverlay.class).toInstance(mock(InventoryHighlightOverlay.class));bind(DebugOverlayPanel.class).toInstance(mock(DebugOverlayPanel.class));
                    bind(SpriteManager.class).toInstance(mock(SpriteManager.class));bind(WorldMapPointManager.class).toInstance(mock(WorldMapPointManager.class));
                    bind(KeyManager.class).toInstance(mock(KeyManager.class));bind(PortalNexusKeybinds.class).toInstance(nexusKeys);
                    bind(SpiritTreePatchState.class).toInstance(mock(SpiritTreePatchState.class));
                }
            }).injectMembers(receiver);
            nav=new GuideNavigation(client,events,plugins,thread,config,catalog);
            events.register(new Subscriber(nav));events.register(receiver);
        }
        void start() throws Exception
        {SwingUtilities.invokeAndWait(()->ShortestPathLifecycle.start(receiver));active=true;drain();nav.start();nav.onGameTick();}
        void drain(){int count=0;for(Runnable task;(task=queued.poll())!=null;){if(++count>1000)throw new AssertionError("Unbounded client queue");task.run();}}
        void until(BooleanSupplier condition) throws Exception
        {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            do
            {
                drain();SwingUtilities.invokeAndWait(()->{});drain();
                if(condition.getAsBoolean()){drain();return;}Thread.sleep(2);
            }while(System.nanoTime()<deadline);
            fail("Upstream route did not finish within ten seconds");
        }
        void close() throws Exception
        {active=false;events.unregister(receiver);SwingUtilities.invokeAndWait(()->ShortestPathLifecycle.stop(receiver));drain();}
    }
}
