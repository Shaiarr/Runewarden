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

/** Contracts against the unmodified, separately supplied Shortest Path 1.21.3 JAR. */
public class HarvestShortestPathContractTest
{
    @Test public void nativeWalkingAndCarriedTabletWinWithoutForcingAHouseDetour() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            when(f.pathConfig.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
            ItemContainer held=mock(ItemContainer.class);when(held.getItems()).thenReturn(new Item[]{new Item(9789,1),new Item(8010,1)});
            when(f.client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(held);
            WorldPoint patch=new WorldPoint(2808,3463,0);when(f.player.getWorldLocation()).thenReturn(new WorldPoint(2808,3462,0));f.start();
            assertTrue(f.nav.routeAutomatic("farm",patch,"Nearby patch",f.nav.manualRevision()));
            f.until(()->f.nav.routeAdvice("farm").answered);
            ActiveSearch walk=f.receiver.getActiveSearch();assertTrue(walk.getResult().isReached());
            assertTrue("One tile must use walking",walk.getPath().stream().allMatch(s->s.getTransport()==null));
            assertEquals("Nearby · walk to patch",f.nav.routeAdvice("farm").recommendation("rsprofile.contract",patch));
            when(f.player.getWorldLocation()).thenReturn(f.origin);
            WorldPoint camelot=new WorldPoint(2757,3479,0);
            assertTrue(f.nav.routeAutomatic("farm",camelot,"Camelot",f.nav.manualRevision()));
            f.until(()->f.nav.routeAdvice("farm").answered);
            FarmTravelPlan.RouteAdvice advice=f.nav.routeAdvice("farm");assertTrue(advice.reached);
            assertTrue(advice.transports.toString(),advice.transports.stream().anyMatch(s->s.contains("Camelot tablet")));
            assertFalse(advice.transports.stream().anyMatch(s->s.contains("POH")));
        }
        finally{f.close();}
    }

    @Test public void explicitNativeHouseAndCarriedCapeUseActualPohThenCatherbyTransport() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            when(f.pathConfig.useTeleportationItems()).thenReturn(TeleportationItem.INVENTORY);
            when(f.pathConfig.usePoh()).thenReturn(true);when(f.pathConfig.pohNexusPortals()).thenReturn(Set.of(PohNexusPortal.CATHERBY));
            when(f.config.getConfiguration("shortestpath","usePoh")).thenReturn("true");
            when(f.config.getConfiguration("shortestpath","pohNexusPortals")).thenReturn("[\"CATHERBY\"]");
            ItemContainer worn=mock(ItemContainer.class);when(worn.getItems()).thenReturn(new Item[]{new Item(9789,1)});
            when(f.client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);f.start();
            WorldPoint catherby=new WorldPoint(2808,3463,0);
            assertTrue(f.nav.routeAutomatic("farm",catherby,"Catherby",f.nav.manualRevision()));
            f.until(()->f.nav.routeAdvice("farm").answered);
            FarmTravelPlan.RouteAdvice advice=f.nav.routeAdvice("farm");assertTrue(advice.reached);
            assertTrue(advice.transports.toString(),advice.transports.stream().anyMatch(s->s.contains("Construction cape")&&s.contains("POH")));
            assertTrue(advice.transports.toString(),advice.transports.stream().anyMatch(s->s.contains("Catherby")));
            verify(f.config,never()).setConfiguration(eq("shortestpath"),anyString(),any(Object.class));
            ActiveSearch nativeRoute=f.receiver.getActiveSearch();f.nav.clearOwned("farm");f.drain();assertNull(f.receiver.getActiveSearch());
            assertNotNull(nativeRoute.getResult());
        }
        finally{f.close();}
    }

    @Test public void manualNativeRouteAndItsSearchSurviveFarmCleanup() throws Exception
    {
        Fixture f=new Fixture();
        try
        {
            f.start();assertTrue(f.nav.routeAutomatic("farm",f.destination,"Farm",f.nav.manualRevision()));
            f.until(()->f.nav.routeAdvice("farm").answered);long before=f.nav.manualRevision();
            WorldPoint manual=new WorldPoint(3223,3220,0);
            f.events.post(new PluginMessage("shortestpath","path",Map.of("target",manual,"start",f.origin)));f.drain();
            f.until(()->f.receiver.getActiveSearch()!=null&&f.receiver.getActiveSearch().getResult()!=null);
            ActiveSearch selected=f.receiver.getActiveSearch();assertTrue(f.nav.manualRevision()>before);
            f.nav.clearOwned("farm");f.drain();assertSame(selected,f.receiver.getActiveSearch());
            assertFalse(f.nav.routeAdvice("farm").owned);
        }
        finally{f.close();}
    }

    public static final class Subscriber
    {
        private final HarvestNavigation nav;Subscriber(HarvestNavigation nav){this.nav=nav;}
        @Subscribe public void onPluginMessage(PluginMessage event){nav.onPluginMessage(event);}
    }

    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);
        final ConfigManager config=mock(ConfigManager.class);final PluginManager plugins=mock(PluginManager.class);
        final ClientThread thread=mock(ClientThread.class);final Queue<Runnable> queued=new ConcurrentLinkedQueue<>();
        final EventBus events=new EventBus();final OverlayManager overlays=mock(OverlayManager.class);
        final WorldPoint origin=new WorldPoint(3222,3218,0),destination=new WorldPoint(3222,3220,0);
        final ShortestPathPlugin receiver=new ShortestPathPlugin();final HarvestNavigation nav;boolean active;
        final PohTravelCapabilities house;
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
            Gson gson=new Gson();house=new PohTravelCapabilities(client,thread,config,new RunewardenDataStore(config,gson),gson);
            nav=new HarvestNavigation(client,events,plugins,thread,config,house);
            events.register(new Subscriber(nav));events.register(receiver);
        }
        void start() throws Exception
        {SwingUtilities.invokeAndWait(()->ShortestPathLifecycle.start(receiver));active=true;drain();house.start();drain();nav.start();nav.onGameTick();}
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
        {nav.stop();drain();house.stop();active=false;events.unregister(receiver);SwingUtilities.invokeAndWait(()->ShortestPathLifecycle.stop(receiver));drain();}
    }
}
