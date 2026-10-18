/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HarvestNavigationTest
{
    @Test public void exactPublicAcknowledgmentAndCorrelatedResultAreRequiredForAdvice()
    {
        Fixture f=new Fixture();assertTrue(f.route());f.drain();
        assertEquals(List.of("path","getTarget","query"),f.receiver.names());
        assertEquals(f.origin,f.receiver.first("path").getData().get("start"));
        assertFalse(f.nav.routeAdvice("farm").answered);
        f.events.post(new PluginMessage("shortestpath","transports",Map.of("displayInfo",List.of("Uncorrelated"))));
        f.result("old-query",f.origin,f.target,"Old callback");assertFalse(f.nav.routeAdvice("farm").answered);
        String id=(String)f.receiver.first("query").getData().get("id");
        f.result(id,new WorldPoint(3100,3100,0),f.target,"Wrong origin");assertFalse(f.nav.routeAdvice("farm").answered);
        f.result(id,f.origin,new WorldPoint(3300,3300,0),"Wrong destination");assertFalse(f.nav.routeAdvice("farm").answered);
        f.result(id,f.origin,f.target,"<col=ff0000>Camelot tablet</col>");
        FarmTravelPlan.RouteAdvice advice=f.nav.routeAdvice("farm");assertTrue(advice.owned&&advice.answered&&advice.reached);
        assertEquals(List.of("Camelot tablet"),advice.transports);assertFalse(f.nav.routeAdvice("another-run").owned);
    }

    @Test public void externalManualRouteWinsOverOldAutomaticWorkAndCleanup()
    {
        Fixture f=new Fixture();long captured=f.nav.manualRevision();assertTrue(f.route());f.drain();
        WorldPoint manual=new WorldPoint(3300,3300,0);f.events.post(new PluginMessage("shortestpath","path",Map.of("target",manual)));
        assertTrue(f.nav.manualRevision()>captured);assertFalse(f.nav.routeAutomatic("farm",f.target,"Patch",captured));
        f.nav.clearOwned("farm");f.drain();assertEquals(manual,f.receiver.target);assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void nativeMenuReplacementIsDetectedWithoutDeletingItsRoute()
    {
        Fixture f=new Fixture();f.route();f.drain();long before=f.nav.manualRevision();
        WorldPoint manual=new WorldPoint(3333,3333,0);f.receiver.target=manual;
        f.tick(10);assertTrue(f.nav.manualRevision()>before);f.nav.clearOwned("farm");f.drain();
        assertEquals(manual,f.receiver.target);assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void explicitStopStillChecksAndClearsOnlyItsOwnQueuedDestination()
    {
        Fixture f=new Fixture();f.route();f.drain();f.nav.stop();f.events.unregister(f.subscriber);f.drain();
        assertNull(f.receiver.target);assertTrue(f.receiver.names().contains("clear"));
        assertFalse(f.nav.routeAutomatic("farm",f.target,"Patch",f.nav.manualRevision()));
    }

    @Test public void stoppedCleanupSubscriberExpiresWhenNoReceiverReplies()
    {
        Fixture f=new Fixture();f.route();f.drain();f.receiver.reply=false;f.nav.stop();f.events.unregister(f.subscriber);f.drain();
        long before=f.nav.manualRevision();
        f.events.post(new PluginMessage("shortestpath","path",Map.of("target",new WorldPoint(3333,3333,0))));
        assertEquals("Expired one-shot listener must be unregistered",before,f.nav.manualRevision());
        assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void aManualRouteBetweenCleanupRequestAndReplySurvives()
    {
        Fixture f=new Fixture();f.route();f.drain();f.nav.clearOwned("farm");
        WorldPoint manual=new WorldPoint(3300,3300,0);f.events.post(new PluginMessage("shortestpath","path",Map.of("target",manual)));
        f.drain();assertEquals(manual,f.receiver.target);assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void missingReceiverRetriesAreBoundedAndInactivePluginCannotReceiveWork()
    {
        Fixture f=new Fixture();f.receiver.reply=false;f.route();f.drain();f.tick(40);
        assertEquals(3,f.receiver.count("path"));assertFalse(f.nav.routeAdvice("farm").owned);
        when(f.plugins.isPluginActive(f.plugin)).thenReturn(false);int sent=f.receiver.messages.size();
        assertFalse(f.nav.routeAutomatic("next",f.target,"Patch",f.nav.manualRevision()));f.tick(5);
        assertEquals(sent,f.receiver.messages.size());
    }

    @Test public void stableFarmDestinationDoesNotRestartSearchAndTeleportUsesCurrentOrigin()
    {
        Fixture f=new Fixture();f.route();f.drain();for(int i=0;i<5;i++)assertTrue(f.route());f.tick(4);
        assertEquals(1,f.receiver.count("path"));
        WorldPoint moved=new WorldPoint(2801,3449,0);when(f.player.getWorldLocation()).thenReturn(moved);f.tick(1);
        assertEquals(2,f.receiver.count("path"));assertEquals(moved,f.receiver.last("path").getData().get("start"));
        assertFalse(f.nav.routeAdvice("farm").answered);
    }

    @Test public void combatSuspendsAdviceAndResumesWithoutLeakingAnOldResult()
    {
        Fixture f=new Fixture();f.route();f.drain();String old=(String)f.receiver.first("query").getData().get("id");
        when(f.player.getInteracting()).thenReturn(mock(NPC.class));f.tick(3);f.result(old,f.origin,f.target,"Stale combat callback");
        assertFalse(f.nav.routeAdvice("farm").answered);when(f.player.getInteracting()).thenReturn(null);f.tick(3);
        assertEquals(2,f.receiver.count("path"));assertFalse(f.nav.routeAdvice("farm").answered);
    }

    @Test public void logoutAndProfileChangeInvalidateQueuedRequestsAndAdvice()
    {
        Fixture f=new Fixture();f.route();when(f.config.getRSProfileKey()).thenReturn("rsprofile.other");f.drain();f.tick(1);
        assertFalse(f.receiver.names().contains("query"));assertFalse(f.nav.routeAdvice("farm").owned);
        f.route();f.nav.onGameStateChanged(GameState.HOPPING);f.drain();assertFalse(f.nav.routeAdvice("farm").owned);
    }

    @Test public void unknownCoordinatesAndUnverifiedInstancesCannotBeUsedAsRouteOrigins()
    {
        Fixture f=new Fixture();when(f.player.getWorldLocation()).thenReturn(new WorldPoint(0,0,0));assertFalse(f.route());
        when(f.player.getWorldLocation()).thenReturn(f.origin);when(f.client.isInInstancedRegion()).thenReturn(true);assertFalse(f.route());
        assertTrue(f.receiver.messages.isEmpty());
    }

    public static final class Subscriber
    {
        final HarvestNavigation nav;Subscriber(HarvestNavigation nav){this.nav=nav;}
        @Subscribe public void onPluginMessage(PluginMessage event){nav.onPluginMessage(event);}
    }
    public static final class Receiver
    {
        final EventBus events;final List<PluginMessage> messages=new ArrayList<>();Object target;boolean reply=true;
        Receiver(EventBus events){this.events=events;}
        @Subscribe public void onPluginMessage(PluginMessage event)
        {
            if(!"shortestpath".equals(event.getNamespace()))return;
            if(List.of("path","query","clear","getTarget").contains(event.getName()))messages.add(event);
            if("path".equals(event.getName()))target=event.getData().get("target");
            else if("clear".equals(event.getName()))target=null;
            else if("getTarget".equals(event.getName())&&reply)
            {
                Map<String,Object> result=new HashMap<>();result.put("id",event.getData().get("id"));result.put("set",target!=null);
                if(target!=null)result.put("target",List.of(target));events.post(new PluginMessage("shortestpath","currentTarget",result));
            }
        }
        List<String> names(){List<String> names=new ArrayList<>();for(PluginMessage m:messages)names.add(m.getName());return names;}
        long count(String name){return messages.stream().filter(m->name.equals(m.getName())).count();}
        PluginMessage first(String name){return messages.stream().filter(m->name.equals(m.getName())).findFirst().get();}
        PluginMessage last(String name){return messages.stream().filter(m->name.equals(m.getName())).reduce((a,b)->b).get();}
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final ConfigManager config=mock(ConfigManager.class);
        final PluginManager plugins=mock(PluginManager.class);final Plugin plugin=new shortestpath.ShortestPathPlugin();
        final ClientThread thread=mock(ClientThread.class);final Queue<Runnable> queued=new ConcurrentLinkedQueue<>();
        final EventBus events=new EventBus();final WorldPoint origin=new WorldPoint(3200,3200,0),target=new WorldPoint(3000,3000,0);
        final Receiver receiver=new Receiver(events);final HarvestNavigation nav;final Subscriber subscriber;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(origin);
            when(config.getRSProfileKey()).thenReturn("rsprofile.test");when(plugins.getPlugins()).thenReturn(List.of(plugin));when(plugins.isPluginActive(plugin)).thenReturn(true);
            doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            doAnswer(call->{java.util.function.BooleanSupplier action=call.getArgument(0);queued.add(new Runnable(){public void run(){if(!action.getAsBoolean())queued.add(this);}});return null;}).when(thread).invokeLater(any(java.util.function.BooleanSupplier.class));
            nav=new HarvestNavigation(client,events,plugins,thread,config,null);subscriber=new Subscriber(nav);events.register(subscriber);events.register(receiver);nav.start();nav.onGameTick();
        }
        boolean route(){return nav.routeAutomatic("farm",target,"Patch",nav.manualRevision());}
        void tick(int count){for(int i=0;i<count;i++){nav.onGameTick();drain();}}
        void drain(){int count=0;for(Runnable task;(task=queued.poll())!=null;){if(++count>1000)throw new AssertionError("Unbounded client queue");task.run();}}
        void result(String id,WorldPoint start,WorldPoint point,String instruction)
        {events.post(new PluginMessage("shortestpath","result",Map.of("id",id,"start",start,"target",point,"reached",true,"transports",List.of(Map.of("displayInfo",instruction)))));}
    }
}
