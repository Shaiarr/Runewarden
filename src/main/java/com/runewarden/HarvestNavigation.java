/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/** Owned farm destinations over public events. The separately installed Shortest Path computes all routes. */
@Singleton
final class HarvestNavigation
{
    private static final String NAMESPACE="shortestpath",COMPANION="shortestpath.ShortestPathPlugin";
    private final Client client;private final EventBus events;private final PluginManager plugins;
    private final ClientThread thread;private final ConfigManager config;private final PohTravelCapabilities house;
    private boolean running,confirmed,answered,reached,suspended,dirty;
    private int posting,ticks,sentTick,retryAfter,attempts;
    private volatile long epoch,manualRevision;
    private long serial,transportRevision;
    private String profile,owner,ackId,queryId,pollId;
    private WorldPoint target,origin,previous;
    private List<String> transports=Collections.emptyList();
    private Cleanup cleanup;

    private static final class Cleanup
    {
        final String id,profile;final WorldPoint target;final long epoch;
        CleanupSubscriber subscriber;
        Cleanup(String id,String profile,WorldPoint target,long epoch)
        {this.id=id;this.profile=profile;this.target=target;this.epoch=epoch;}
    }

    /** RuneLite unregisters its plugin subscriber during shutdown, before an asynchronous target reply. */
    public static final class CleanupSubscriber
    {
        private final HarvestNavigation navigation;
        CleanupSubscriber(HarvestNavigation navigation){this.navigation=navigation;}
        @Subscribe public void onPluginMessage(PluginMessage event){navigation.onPluginMessage(event);}
    }

    @Inject HarvestNavigation(Client client,EventBus events,PluginManager plugins,ClientThread thread,
        ConfigManager config,PohTravelCapabilities house)
    {this.client=client;this.events=events;this.plugins=plugins;this.thread=thread;this.config=config;this.house=house;}

    void start(){cancelCleanup();epoch++;running=true;profile=null;ticks=0;forget();}
    void stop(){epoch++;clearOwned(owner);running=false;forget();}
    long generation(){return epoch;}
    long manualRevision(){return manualRevision;}

    void onGameStateChanged(GameState state)
    {
        if(state==GameState.LOGIN_SCREEN||state==GameState.LOGGING_IN||state==GameState.HOPPING)
        {epoch++;manualRevision++;profile=null;cancelCleanup();forget();}
    }

    void onGameTick()
    {
        if(!running)return;
        bindProfile();ticks++;
        if(profile==null)return;
        WorldPoint location=currentLocation();boolean teleported=valid(previous)&&valid(location)&&previous.distanceTo(location)>16;
        previous=location;
        long revision=house==null?0:house.revision();
        if(revision!=transportRevision){transportRevision=revision;dirty=true;}
        if(owner==null)return;
        if(!canTravel()||!available())
        {if(!suspended){suspended=true;invalidateRequest();}return;}
        if(suspended){suspended=false;dirty=true;}
        if(teleported){dirty=true;invalidateRequest();}
        if(dirty&&ticks-sentTick>=3){send();return;}
        if((ackId!=null&&ticks-sentTick>10)||(queryId!=null&&ticks-sentTick>60))
        {invalidateRequest();retryAfter=ticks+(attempts<3?3:30);}
        if(!confirmed&&ackId==null&&queryId==null&&ticks>=retryAfter&&ticks-sentTick>=3)
        {send();return;}
        if(confirmed&&pollId==null&&ticks%10==0)
        {pollId=id("check");post("getTarget",Map.of("id",pollId));}
    }

    boolean routeAutomatic(String routeOwner,WorldPoint point,String title,long expectedManualRev)
    {
        if(!running)return false;
        bindProfile();
        if(expectedManualRev!=manualRevision||routeOwner==null||routeOwner.isBlank()||routeOwner.length()>120
            ||!valid(point)||!canTravel()||profile==null||!available())return false;
        if(Objects.equals(owner,routeOwner)&&Objects.equals(target,point))return true;
        cancelCleanup();epoch++;owner=routeOwner;target=point;
        attempts=0;dirty=false;suspended=false;send();return true;
    }

    FarmTravelPlan.RouteAdvice routeAdvice(String routeOwner)
    {
        if(!running||!Objects.equals(owner,routeOwner)||!Objects.equals(profile,currentProfile()))return FarmTravelPlan.RouteAdvice.EMPTY;
        return new FarmTravelPlan.RouteAdvice(profile,currentLocation(),target,!suspended&&confirmed,answered,reached,transports);
    }

    /** Only an exact acknowledged destination can be removed, including after module stop. */
    void clearOwned(String routeOwner)
    {
        if(owner==null||!Objects.equals(owner,routeOwner))return;
        WorldPoint owned=target;String account=profile;forget();
        if(account==null)return;
        cancelCleanup();Cleanup ticket=new Cleanup(id("clear"),account,owned,epoch);cleanup=ticket;
        ticket.subscriber=new CleanupSubscriber(this);events.register(ticket.subscriber);
        later(()->
        {
            if(cleanup!=ticket)return;
            if(ticket.epoch!=epoch||!Objects.equals(ticket.profile,currentProfile())||!available())
            {cancelCleanup();return;}
            post("getTarget",Map.of("id",ticket.id));
            if(cleanup==ticket)
            {
                if(thread==null){cancelCleanup();return;}
                int[] frames={0};
                thread.invokeLater((java.util.function.BooleanSupplier)()->
                {
                    if(cleanup!=ticket)return true;
                    if(ticket.epoch!=epoch||!Objects.equals(ticket.profile,currentProfile())||++frames[0]>=30)
                    {cancelCleanup();return true;}
                    return false;
                });
            }
        });
    }

    void onPluginMessage(PluginMessage event)
    {
        if(!NAMESPACE.equals(event.getNamespace()))return;
        String name=event.getName();Map<String,Object> data=event.getData();
        if(posting==0&&("path".equals(name)||"clear".equals(name)))
        {manualOverride();return;}
        if(data==null)return;
        if("currentTarget".equals(name)&&cleanup!=null&&Objects.equals(cleanup.id,data.get("id")))
        {
            Cleanup ticket=cleanup;cancelCleanup();
            if(ticket.epoch==epoch&&Objects.equals(ticket.profile,currentProfile())&&Boolean.TRUE.equals(data.get("set"))
                &&ShortestPathRouteReading.sameTarget(ticket.target,data.get("target")))post("clear",Collections.emptyMap());
            return;
        }
        if(!running||owner==null||!Objects.equals(profile,currentProfile()))return;
        if("currentTarget".equals(name))
        {
            boolean acknowledgment=Objects.equals(ackId,data.get("id"))&&ackId!=null;
            boolean poll=Objects.equals(pollId,data.get("id"))&&pollId!=null;
            if(!acknowledgment&&!poll)return;
            if(poll)pollId=null;
            boolean same=Boolean.TRUE.equals(data.get("set"))&&ShortestPathRouteReading.sameTarget(target,data.get("target"));
            if(!same)
            {if(poll)manualOverride();else{ackId=null;invalidateRequest();retryAfter=ticks+30;}return;}
            if(acknowledgment)
            {
                ackId=null;confirmed=true;queryId=id("summary");String request=queryId;long ticket=epoch;
                later(()->{if(running&&ticket==epoch&&Objects.equals(request,queryId)&&Objects.equals(profile,currentProfile()))
                    post("query",Map.of("id",request,"start",origin,"target",target));});
            }
        }
        else if("result".equals(name)&&queryId!=null&&confirmed)
        {
            ShortestPathRouteReading reading=ShortestPathRouteReading.read(data,queryId,origin,target);
            if(reading!=null){queryId=null;answered=true;reached=reading.reached;transports=reading.transports;}
            // Uncorrelated transports broadcasts cannot establish route ownership or completion.
        }
    }

    void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option=ShortestPathRouteReading.safe(event.getMenuOption()).toLowerCase(java.util.Locale.ROOT);
        if(option.contains("path")||option.contains("destination"))manualOverride();
    }

    private void send()
    {
        if(owner==null||!canTravel()||!available())return;
        invalidateRequest();origin=currentLocation();previous=origin;dirty=false;suspended=false;attempts++;sentTick=ticks;retryAfter=ticks+30;
        transportRevision=house==null?0:house.revision();ackId=id("route");String request=ackId;long ticket=epoch;String account=profile;
        Map<String,Object> options=new LinkedHashMap<>();options.put("useTeleportationItems","Inventory");options.put("usePoh",false);
        if(house!=null)options.putAll(house.routeOptions());
        options.put("postTransports",false);
        post("path",Map.of("start",origin,"target",target,"config",options));
        later(()->{if(running&&ticket==epoch&&Objects.equals(account,currentProfile())&&Objects.equals(request,ackId))post("getTarget",Map.of("id",request));});
    }

    private void bindProfile()
    {
        String account=currentProfile();if(Objects.equals(profile,account))return;
        if(profile!=null)manualRevision++;
        epoch++;profile=account;cancelCleanup();previous=null;forget();
    }
    private void manualOverride(){manualRevision++;epoch++;cancelCleanup();forget();}
    private void cancelCleanup()
    {Cleanup previousCleanup=cleanup;cleanup=null;if(previousCleanup!=null&&previousCleanup.subscriber!=null)events.unregister(previousCleanup.subscriber);}
    private void forget(){owner=null;target=null;origin=null;dirty=false;suspended=false;attempts=0;invalidateRequest();}
    private void invalidateRequest(){ackId=null;queryId=null;pollId=null;confirmed=false;answered=false;reached=false;transports=Collections.emptyList();}
    private boolean available()
    {for(Plugin plugin:plugins.getPlugins())if(COMPANION.equals(plugin.getClass().getName())&&plugins.isPluginActive(plugin))return true;return false;}
    private boolean canTravel()
    {Player player=client.getLocalPlayer();return client.getGameState()==GameState.LOGGED_IN&&player!=null&&player.getInteracting()==null&&valid(currentLocation());}
    private WorldPoint currentLocation()
    {Player player=client.getLocalPlayer();return player==null?null:client.isInInstancedRegion()?house==null?null:house.ownHouseOrigin():player.getWorldLocation();}
    private String currentProfile(){return client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null?config.getRSProfileKey():null;}
    private String id(String kind){return "runewarden:harvest:"+kind+":"+epoch+":"+(++serial);}
    private static boolean valid(WorldPoint point){return ShortestPathRouteReading.valid(point);}
    private void later(Runnable action){if(thread==null)action.run();else thread.invokeLater(action);}
    private void post(String name,Map<String,?> values)
    {Map<String,Object> data=new LinkedHashMap<>();data.putAll(values);posting++;try{events.post(new PluginMessage(NAMESPACE,name,data));}finally{posting--;}}
}
