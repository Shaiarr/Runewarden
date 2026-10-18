/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideNavigationReceiverTest
{
    @Test public void transportEventsCannotCompleteAPartialStaleOrReleasedRouteAndPollingNeedsNoSecondSearch()
    {
        Fixture f=new Fixture();assertTrue(f.nav.routeOwned("farm",f.target,"Patch"));
        f.receiver.complete(List.of("Unproven event text"));assertFalse(f.nav.snapshot().answered);
        f.reading=new ShortestPathRouteReading(new WorldPoint(3100,3100,0),f.target,Set.of(f.target),true,false,20,"REACHED","",List.of("Stale origin"));
        f.receiver.complete(Collections.emptyList());assertFalse(f.nav.snapshot().answered);
        f.reading=new ShortestPathRouteReading(f.origin,new WorldPoint(3300,3300,0),Set.of(new WorldPoint(3300,3300,0)),true,false,20,"REACHED","",List.of("Stale target"));
        f.nav.onGameTick();assertFalse(f.nav.snapshot().answered);
        f.reading=new ShortestPathRouteReading(f.origin,f.target,Set.of(f.target),true,false,20,"REACHED","",List.of("Actual completed route"));
        f.nav.onGameTick();assertTrue("No event is required once the exact native result completes",f.nav.snapshot().answered);
        assertEquals(List.of("Actual completed route"),f.nav.snapshot().transports);assertFalse(f.receiver.names().contains("query"));
        f.nav.clearOwned("farm");int sent=f.receiver.messages.size();f.receiver.complete(List.of("Old callback"));f.nav.onGameTick();
        assertFalse(f.nav.ownsRoute("farm",f.target));assertEquals(sent,f.receiver.messages.size());
    }

    @Test public void repeatedCapabilityChangesCoalesceToAtMostOneReplanPerThreeTicks()
    {
        Fixture f=new Fixture();java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong(1);
        f.nav.setTransportRevisionSupplier(revision::get);f.nav.routeOwned("farm",f.target,"Patch");
        for(int i=0;i<8;i++){revision.incrementAndGet();f.nav.onGameTick();}
        assertEquals(3,f.receiver.names().stream().filter("path"::equals).count());assertFalse(f.receiver.names().contains("query"));
    }
    @Test public void verifiedOwnHouseUsesTheNativeVirtualOriginAndGuestOrCombatInstancesStayBlocked()
    {
        Fixture f=new Fixture();WorldView view=mock(WorldView.class);when(f.client.isInInstancedRegion()).thenReturn(true);
        when(f.client.getTopLevelWorldView()).thenReturn(view);when(f.client.getWorldView(WorldView.TOPLEVEL)).thenReturn(view);when(view.isInstance()).thenReturn(true);
        int[][][] chunks=new int[4][13][13];chunks[0][0][0]=((1856/8)<<14)|((7048/8)<<3);
        when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        when(f.player.getLocalLocation()).thenReturn(new net.runelite.api.coords.LocalPoint(2*128+64,3*128+64,WorldView.TOPLEVEL));
        assertFalse("A guest's house is not this account's declared POH",f.nav.routeOwned("guide",f.target,"Guest house"));
        when(f.client.getVarbitValue(net.runelite.api.gameval.VarbitID.POH_BUILDING_MODE)).thenReturn(1);
        assertTrue(f.nav.routeOwned("guide",f.target,"Own house"));
        PluginMessage path=f.receiver.messages.stream().filter(m->"path".equals(m.getName())).findFirst().get();assertEquals(new WorldPoint(1858,7051,0),path.getData().get("start"));
        chunks[0][0][0]=((2400/8)<<14)|((5104/8)<<3);
        assertFalse("Even a stale building flag cannot allow another instance",f.nav.routeOwned("guide",f.target,"Combat instance"));
    }
    @Test public void capabilityChangesRefreshOwnedRoutesOnceAndNeverOverrideAManualReplacement()
    {
        Fixture f=new Fixture();java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong(1);
        f.nav.setTransportRevisionSupplier(revision::get);assertTrue(f.nav.routeOwned("guide",f.target,"Training"));
        long first=f.receiver.names().stream().filter("path"::equals).count();
        for(int i=0;i<5;i++)f.nav.onGameTick();assertEquals(first,f.receiver.names().stream().filter("path"::equals).count());
        revision.incrementAndGet();f.nav.onGameTick();assertEquals(first+1,f.receiver.names().stream().filter("path"::equals).count());
        f.nav.onGameTick();assertEquals(first+1,f.receiver.names().stream().filter("path"::equals).count());
        f.events.post(new PluginMessage("shortestpath","path",Map.of("target",new WorldPoint(3300,3300,0))));
        revision.incrementAndGet();int sent=f.receiver.messages.size();f.nav.onGameTick();assertEquals(sent,f.receiver.messages.size());
    }
    @Test public void eachRequestUsesTheCurrentNativeOriginAndInvalidOriginsNeverPost()
    {
        Fixture f=new Fixture();
        assertTrue(f.nav.preview("Preview",f.target,Collections.emptyList()));
        assertEquals(f.origin,f.receiver.messages.get(0).getData().get("start"));
        WorldPoint moved=new WorldPoint(2801,3449,0);when(f.player.getWorldLocation()).thenReturn(moved);
        assertTrue(f.nav.routeOwned("guide",f.target,"After teleport"));
        PluginMessage path=f.receiver.messages.stream().filter(m->"path".equals(m.getName())).reduce((a,b)->b).get();
        assertEquals(moved,path.getData().get("start"));
        int sent=f.receiver.messages.size();when(f.player.getWorldLocation()).thenReturn(new WorldPoint(0,0,0));
        assertFalse(f.nav.routeOwned("guide",f.target,"Unknown position"));assertFalse(f.nav.preview("Unknown position",f.target,Collections.emptyList()));
        assertEquals(sent,f.receiver.messages.size());assertTrue(f.nav.snapshot().status.contains("current location"));
    }

    @Test public void nativeFarmArrivalSelectionUsesMultipleTargetsAndTheActualResult()
    {
        Fixture f=new Fixture();GuideCatalog.Entry guide=f.catalog.entry("training:fruit-tree-runs");
        GuideCatalog.Step prepare=guide.flattened().get(0).step;
        WorldPoint catherby=new WorldPoint(2808,3463,0),gnome=new WorldPoint(2475,3446,0);
        f.nav.setArrivalOptions(id->id.equals(guide.id)?List.of(new GuideNavigation.Destination("Catherby",id,catherby),new GuideNavigation.Destination("Gnome Stronghold",id,gnome)):null);
        f.nav.setTransportOptionsSupplier(()->Map.of("usePoh",true,"useTeleportationItems","Inventory"));
        assertTrue(f.nav.routeStep(guide,prepare));
        PluginMessage path=f.receiver.messages.stream().filter(m->"path".equals(m.getName())).findFirst().get();
        assertEquals(List.of(catherby,gnome),path.getData().get("target"));
        assertEquals(true,((Map<?,?>)path.getData().get("config")).get("usePoh"));
        assertFalse(f.receiver.names().contains("query"));
        f.reading=new ShortestPathRouteReading(f.origin,catherby,Set.of(catherby,gnome),true,false,20,"REACHED","",List.of("Construction cape: Tele to POH","Catherby Portal"));
        f.receiver.complete(Collections.emptyList());
        assertEquals(catherby,f.nav.snapshot().point);assertEquals("Catherby",f.nav.snapshot().title);
        assertTrue(f.nav.snapshot().answered);assertEquals(2,f.nav.snapshot().transports.size());assertTrue(f.nav.ownsRoute("guide",catherby));
        f.nav.clearOwned("guide");assertNull(f.receiver.target);
    }

    @Test public void unavailableDynamicFarmArrivalsDoNotFallBackToAFixedTown()
    {
        Fixture f=new Fixture();GuideCatalog.Entry guide=f.catalog.entry("training:fruit-tree-runs");GuideCatalog.Step prepare=guide.flattened().get(0).step;
        f.nav.setArrivalOptions(id->id.equals(guide.id)?Collections.emptyList():null);
        assertTrue(f.nav.hasTravelDestination(guide,prepare));assertFalse(f.nav.routeStep(guide,prepare));
        assertTrue(f.receiver.messages.isEmpty());assertTrue(f.nav.snapshot().status.contains("farm destinations"));
    }

    @Test public void teleportReplansOnlyAnOwnedRouteAndClearsItsOldTravelSummary()
    {
        Fixture f=new Fixture();assertTrue(f.nav.routeOwned("farm",f.target,"Patch"));
        assertFalse(f.receiver.names().contains("query"));
        f.reading=new ShortestPathRouteReading(f.origin,f.target,Set.of(f.target),true,false,20,"REACHED","",List.of("Construction cape"));f.receiver.complete(List.of("Ignored event text"));
        assertTrue(f.nav.snapshot().answered);
        WorldPoint arrival=new WorldPoint(2801,3449,0);when(f.player.getWorldLocation()).thenReturn(arrival);f.nav.onGameTick();
        assertFalse(f.nav.snapshot().answered);assertTrue(f.nav.snapshot().transports.isEmpty());
        PluginMessage lastPath=f.receiver.messages.stream().filter(m->"path".equals(m.getName())).reduce((a,b)->b).get();
        assertEquals(arrival,lastPath.getData().get("start"));
        f.events.post(new PluginMessage("shortestpath","path",Map.of("target",new WorldPoint(3300,3300,0))));
        int sent=f.receiver.messages.size();when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3000,3000,0));f.nav.onGameTick();
        assertEquals(sent,f.receiver.messages.size());
    }
    @Test public void firstSelectionBindsCharacterOwnershipBeforeTheFirstTravelTick()
    {
        Fixture f=new Fixture();f.nav.start();
        assertTrue(f.nav.routeOwned("guide",f.target,"First selected guide"));
        assertEquals("Shortest Path destination confirmed",f.nav.snapshot().status);
        f.nav.clearOwned("guide");assertNull(f.receiver.target);
    }
    @Test public void automaticFarmDestinationCannotOverwriteANewerManualPath()
    {
        Fixture f=new Fixture();long expected=f.nav.manualRevision();
        f.events.post(new PluginMessage("shortestpath","path",Map.of("target",new WorldPoint(3300,3300,0))));
        assertFalse(f.nav.routeAutomatic("farm",f.target,"Next patch",expected));
        assertEquals(new WorldPoint(3300,3300,0),f.receiver.target);
    }

    @Test public void configuredButNotStartedPluginCannotClaimAvailableOrReceiveRequests()
    {
        Fixture f=new Fixture();when(f.plugins.isPluginActive(f.plugin)).thenReturn(false);
        assertFalse(f.nav.available());assertFalse(f.nav.routeOwned("guide",f.target,"Test"));
        assertEquals("Enable Shortest Path to draw a route",f.nav.snapshot().status);
        assertTrue(f.receiver.messages.isEmpty());
    }

    @Test public void postedRequestWaitsForAnActualReceiverAndExpiresWithoutOne()
    {
        Fixture f=new Fixture();f.events.unregister(f.receiver);
        assertTrue(f.nav.routeOwned("guide",f.target,"Test"));
        assertEquals("Waiting for Shortest Path…",f.nav.snapshot().status);
        for(int i=0;i<11;i++)f.nav.onGameTick();
        assertEquals("No reply from Shortest Path",f.nav.snapshot().status);
    }

    @Test public void actualEventBusReceiverMustConfirmExactDestinationBeforeSuccess()
    {
        Fixture f=new Fixture();assertTrue(f.nav.routeOwned("guide",f.target,"Test"));
        assertEquals("Shortest Path destination confirmed",f.nav.snapshot().status);
        assertEquals(f.origin,f.receiver.messages.get(0).getData().get("start"));
        assertEquals(f.target,f.receiver.target);
        assertEquals(List.of("path","getTarget"),f.receiver.names());
        f.nav.clearOwned("guide");assertNull(f.receiver.target);
        assertEquals(List.of("path","getTarget","getTarget","clear"),f.receiver.names());
    }

    @Test public void rejectedDestinationAndChangedAccountNeverAppearConfirmed()
    {
        Fixture f=new Fixture();f.receiver.accept=false;
        assertTrue(f.nav.routeOwned("guide",f.target,"Test"));
        assertEquals("Shortest Path did not accept this destination",f.nav.snapshot().status);
        f.receiver.accept=true;f.receiver.reply=false;f.nav.routeOwned("guide",f.target,"Next");
        PluginMessage request=f.receiver.messages.get(f.receiver.messages.size()-1);
        when(f.config.getRSProfileKey()).thenReturn("rsprofile.other");
        f.events.post(new PluginMessage("shortestpath","currentTarget",Map.of("id",request.getData().get("id"),"set",true,"target",List.of(f.target))));
        assertEquals("Choose a destination",f.nav.snapshot().status);
        f.nav.clearOwned("guide");assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void manualReplacementSurvivesGuideCleanup()
    {
        Fixture f=new Fixture();f.nav.routeOwned("guide",f.target,"Test");
        WorldPoint manual=new WorldPoint(3300,3300,0);
        f.events.post(new PluginMessage("shortestpath","path",Map.of("target",manual)));
        f.nav.clearOwned("guide");assertEquals(manual,f.receiver.target);
        assertFalse(f.receiver.names().contains("clear"));
    }

    @Test public void nonTravelAndLocationlessRowsReleaseGuideRoute()
    {
        Fixture f=new Fixture();f.nav.routeOwned("guide",f.target,"Old guide step");
        GuideCatalog.Entry quest=f.catalog.entry("quest:Cook's Assistant");
        assertFalse(f.nav.routeStep(quest,new GuideCatalog.Step()));assertNull(f.receiver.target);
        f.nav.routeOwned("guide",f.target,"Another guide step");
        GuideCatalog.Entry boss=new GuideCatalog.Entry();boss.category="boss";boss.title="Static combat lesson";
        assertFalse(f.nav.routeStep(boss,new GuideCatalog.Step()));assertNull(f.receiver.target);
    }

    @Test public void questMetadataCannotClaimItsOwnRouteOrInteractionTarget()
    {
        Fixture f=new Fixture(); GuideCatalog.Entry quest=f.catalog.entry("quest:Cook's Assistant");
        assertTrue(quest.flattened().isEmpty());
        GuideCatalog.Step legacy = new GuideCatalog.Step(); legacy.location = new GuideCatalog.Location();
        legacy.location.x=3205; legacy.location.y=3205;
        assertFalse(f.nav.routeStep(quest,legacy));
        assertFalse(f.receiver.names().contains("path"));
    }

    @Test public void nonQuestGuidanceKeepsItsExplicitLocationWithoutQuestSceneScanning()
    {
        Fixture f=new Fixture(); GuideCatalog.Entry training=f.catalog.entry("training:giants-foundry");
        GuideCatalog.Step step = new GuideCatalog.Step(); step.location = new GuideCatalog.Location();
        step.location.x=3205; step.location.y=3205;
        assertEquals(new WorldPoint(3205,3205,0),f.nav.stepDestination(training,step));
        assertTrue(f.nav.routeStep(training,step));
        verify(f.client,never()).getTopLevelWorldView();
    }

    public static final class Receiver
    {
        final EventBus events;final List<PluginMessage> messages=new ArrayList<>();Object target;
        boolean accept=true,reply=true;
        Receiver(EventBus events){this.events=events;}
        @Subscribe public void onPluginMessage(PluginMessage event)
        {
            if(!"shortestpath".equals(event.getNamespace()))return;
            if("path".equals(event.getName())){messages.add(event);if(accept)target=event.getData().get("target");}
            else if("query".equals(event.getName()))messages.add(event);
            else if("clear".equals(event.getName())){messages.add(event);target=null;}
            else if("getTarget".equals(event.getName()))
            {
                messages.add(event);if(!reply)return;
                Map<String,Object> data=new HashMap<>();data.put("id",event.getData().get("id"));data.put("set",target!=null);
                if(target!=null)data.put("target",target instanceof Collection<?>?target:List.of(target));
                events.post(new PluginMessage("shortestpath","currentTarget",data));
            }
        }
        List<String> names(){List<String> names=new ArrayList<>();for(PluginMessage event:messages)names.add(event.getName());return names;}
        void complete(List<String> instructions){events.post(new PluginMessage("shortestpath","transports",Map.of("displayInfo",instructions)));}
    }

    public static final class NavSubscriber
    {
        final GuideNavigation nav;NavSubscriber(GuideNavigation nav){this.nav=nav;}
        @Subscribe public void onPluginMessage(PluginMessage event){nav.onPluginMessage(event);}
    }

    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);
        final ConfigManager config=mock(ConfigManager.class);final PluginManager plugins=mock(PluginManager.class);
        final Plugin plugin=new shortestpath.ShortestPathPlugin();final EventBus events=new EventBus();
        final WorldPoint origin=new WorldPoint(3200,3200,0),target=new WorldPoint(3000,3000,0);
        final GuideCatalog catalog=new GuideCatalog(new Gson());final GuideNavigation nav;
        ShortestPathRouteReading reading;
        final Receiver receiver=new Receiver(events);
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(origin);when(config.getRSProfileKey()).thenReturn("rsprofile.test");
            when(plugins.getPlugins()).thenReturn(List.of(plugin));when(plugins.isPluginEnabled(plugin)).thenReturn(true);when(plugins.isPluginActive(plugin)).thenReturn(true);
            nav=new GuideNavigation(client,events,plugins,null,config,catalog);nav.setRouteReader(plugin->reading);nav.start();nav.onGameTick();
            events.register(new NavSubscriber(nav));events.register(receiver);
        }
    }
}
