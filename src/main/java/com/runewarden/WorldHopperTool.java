/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.game.WorldService;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.worldhopper.ping.Ping;
import net.runelite.client.plugins.worldhopper.ping.TCPInfo;
import net.runelite.client.plugins.worldhopper.ping.RetransmitCalculator;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

/** Manual world switching through RuneLite's public world service and normal client confirmation flow.
 * Native behaviour attribution: docs/third-party-notices/world-hopper-runelite.txt (RuneLite 1.13.2).
 */
@Singleton
final class WorldHopperTool
{
    static final String MENU="Hop-to (Runewarden)", COMMAND="rwhop";
    private static final long REFRESH_MS=60_000;
    private final Client client;private final ClientThread clientThread;private final ConfigManager store;
    private final WorldService worlds;private final KeyManager keys;private final RunewardenConfig config;
    private final ExecutorService worker;private final ToIntFunction<World> ping;private final LongSupplier clock;
    private final AtomicBoolean workInFlight=new AtomicBoolean();
    private final Map<Integer,World> known=new LinkedHashMap<>();private final Map<Integer,Integer> populations=new HashMap<>(),pings=new HashMap<>();
    private final Map<Integer,Integer> geographies=new HashMap<>();
    private final Set<Integer> favourites=new HashSet<>(),hiddenNativeFavourites=new HashSet<>();private final Map<String,String> display=new HashMap<>();
    private final ArrayDeque<Integer> pingQueue=new ArrayDeque<>();
    private volatile boolean running,visible,pingDemand,pinging;private volatile long epoch,probeEpoch,lifecycle;
    private volatile Thread activeWorker;private volatile Snapshot reading=Snapshot.empty(null,"World hopper disabled");
    private volatile Runnable listener=()->{};
    private String profile,status="Loading worlds";private int currentWorld,lastWorld,currentPing=-1,currentLoss=-1,total,f2pTotal,favourite1,favourite2,ticks,attempts;
    private boolean member;private long lastRefresh=Long.MIN_VALUE,lastProbe=Long.MIN_VALUE,lastRead=Long.MIN_VALUE,lastCurrentProbe=Long.MIN_VALUE,serial;
    private RetransmitCalculator retransmit=new RetransmitCalculator();
    private GameState state=GameState.UNKNOWN;private net.runelite.api.World pendingHop;private World pendingSource;
    private int pendingPopulation;private WorldWarning confirmation;
    private final HotkeyListener previousKey,nextKey;

    static final class WorldWarning
    {
        final long token;final int world;final String message;
        WorldWarning(long token,int world,String message){this.token=token;this.world=world;this.message=message;}
    }
    static final class WorldRow
    {
        final int id,population,ping,region,geography;final String location,activity,typeLabel,reason,risk;
        final boolean members,favourite,eligible,safe;final Set<String> types;
        WorldRow(World world,int population,int ping,boolean favourite,boolean eligible,String reason)
        {this(world,population,ping,favourite,eligible,reason,-1);}
        WorldRow(World world,int population,int ping,boolean favourite,boolean eligible,String reason,int geography)
        {
            id=world.getId();this.population=population;this.ping=ping;this.favourite=favourite;this.eligible=eligible;this.reason=reason;this.geography=geography;
            region=world.getRegion()==null?-1:world.getRegion().getId();location=regionName(region);
            activity=clean(world.getActivity());Set<String> labels=new LinkedHashSet<>();for(Enum<?> type:world.getTypes())labels.add(type.name());
            types=Collections.unmodifiableSet(labels);members=types.contains("MEMBERS");risk=risk(types);safe=normal(types);
            typeLabel=String.join(" · ",labels).replace('_',' ');
        }
    }
    static final class Snapshot
    {
        final String profile,status;final int currentWorld,lastWorld,currentPing,currentLoss,totalLevel;final boolean pingRunning,members,loggedIn;
        final List<WorldRow> rows;final WorldWarning confirmation;
        Snapshot(String profile,String status,int currentWorld,int lastWorld,int currentPing,int currentLoss,int totalLevel,boolean members,
            boolean pingRunning,boolean loggedIn,List<WorldRow> rows,WorldWarning confirmation)
        {this.profile=profile;this.status=status;this.currentWorld=currentWorld;this.lastWorld=lastWorld;this.currentPing=currentPing;this.currentLoss=currentLoss;
            this.totalLevel=totalLevel;this.members=members;this.pingRunning=pingRunning;this.loggedIn=loggedIn;this.rows=Collections.unmodifiableList(new ArrayList<>(rows));this.confirmation=confirmation;}
        static Snapshot empty(String profile,String status){return new Snapshot(profile,status,0,0,-1,-1,0,false,false,false,Collections.emptyList(),null);}
    }

    @Inject WorldHopperTool(Client client,ClientThread clientThread,ConfigManager store,WorldService worlds,KeyManager keys,RunewardenConfig config)
    {this(client,clientThread,store,worlds,keys,config,newWorker(),WorldHopperTool::pingOfficialWorld,System::currentTimeMillis);}

    WorldHopperTool(Client client,ClientThread clientThread,ConfigManager store,WorldService worlds,KeyManager keys,RunewardenConfig config,
        ExecutorService worker,ToIntFunction<World> ping,LongSupplier clock)
    {
        this.client=client;this.clientThread=clientThread;this.store=store;this.worlds=worlds;this.keys=keys;this.config=config;
        this.worker=worker;this.ping=ping;this.clock=clock;
        previousKey=new HotkeyListener(config::worldHopPreviousKey){@Override public void hotkeyPressed(){cycle(false);}};
        nextKey=new HotkeyListener(config::worldHopNextKey){@Override public void hotkeyPressed(){cycle(true);}};
    }
    private static ExecutorService newWorker()
    {
        return new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Runewarden-world-probe");t.setDaemon(true);return t;});
    }
    void start()
    {
        if(running)return;running=true;epoch++;lifecycle++;status="Loading worlds";
        if(worker instanceof ThreadPoolExecutor)((ThreadPoolExecutor)worker).setKeepAliveTime(30,TimeUnit.SECONDS);
        keys.registerKeyListener(previousKey);keys.registerKeyListener(nextKey);
        long ticket=epoch;clientThread.invokeLater(()->{if(!running||ticket!=epoch)return;bindProfile();capture();publish();readInitialWorlds();});
    }
    void stop()
    {
        if(!running)return;running=false;epoch++;lifecycle++;cancelWork();pendingHop=null;confirmation=null;currentPing=-1;
        // Release idle thread-local state promptly. Reuse the executor and in-flight guard on restart:
        // a DNS call that ignores interruption must finish before another probe can be submitted.
        if(worker instanceof ThreadPoolExecutor)((ThreadPoolExecutor)worker).setKeepAliveTime(0,TimeUnit.SECONDS);
        keys.unregisterKeyListener(previousKey);keys.unregisterKeyListener(nextKey);reading=Snapshot.empty(null,"World hopper disabled");notifyChange();
    }
    Snapshot snapshot()
    {
        Snapshot snapshot=reading;
        return snapshot.profile!=null&&!Objects.equals(snapshot.profile,store.getRSProfileKey())?Snapshot.empty(store.getRSProfileKey(),"Loading character"):snapshot;
    }
    void setChangeListener(Runnable action){listener=action==null?()->{}:action;}
    void setVisible(boolean value)
    {visible=value;clientThread.invokeLater(()->{if(!running)return;bindProfile();capture();if(geographies.size()<known.size())captureGeographies();
        if(visible&&(lastRefresh==Long.MIN_VALUE||clock.getAsLong()-lastRefresh>=REFRESH_MS)){lastRefresh=clock.getAsLong();worlds.refresh();}
        if(!visible&&!needsCurrentPing())cancelWork();else scheduleProbe(true);publish();});}
    void setPingDemand(boolean value)
    {pingDemand=value;clientThread.invokeLater(()->{if(!running)return;if(!visible&&!needsCurrentPing())cancelWork();else scheduleProbe(true);publish();});}
    String display(String key,String fallback){synchronized(display){return display.getOrDefault(key,fallback);}}
    void saveDisplay(String key,String value)
    {
        if(key==null||!key.matches("[A-Za-z]{1,32}")||value==null||value.length()>1024)return;
        synchronized(display){display.put(key,value);}String account=reading.profile;
        execute(()->store.setConfiguration(RunewardenConfig.GROUP,account,"worldHopperDisplay_"+key,value));
    }
    void refreshWorlds()
    {
        execute(()->{long now=clock.getAsLong();if(lastRefresh!=Long.MIN_VALUE&&now-lastRefresh<REFRESH_MS){status="World list is current";publish();return;}
            lastRefresh=now;status="Refreshing worlds";worlds.refresh();publish();if(known.isEmpty())readInitialWorlds();});
    }
    void refreshPings()
    {
        execute(()->{if(!config.worldHopPing()){status="Enable world pings in settings";publish();return;}
            pingQueue.clear();if(known.containsKey(currentWorld))pingQueue.add(currentWorld);
            if(visible)for(int id:known.keySet())if(id!=currentWorld)pingQueue.add(id);
            lastProbe=Long.MIN_VALUE;scheduleProbe(true);publish();});
    }
    void toggleFavourite(int id)
    {
        execute(()->{if(!known.containsKey(id))return;boolean added=!isFavourite(id);if(added){favourites.add(id);hiddenNativeFavourites.remove(id);}else{favourites.remove(id);hiddenNativeFavourites.add(id);}
            store.setConfiguration(RunewardenConfig.GROUP,profile,"worldHopperFavourite_"+id,added);publish();});
    }
    void selectWorld(int id){execute(()->select(id,false));}
    void previousWorld(){execute(()->{if(lastWorld>0)select(lastWorld,false);else{status="No previous world this session";publish();}});}
    void confirmHop(long token)
    {
        execute(()->{if(confirmation==null||confirmation.token!=token)return;int id=confirmation.world;World world=known.get(id);
            boolean unchanged=world!=null&&confirmation.message.equals("World "+id+" · "+risk(typeNames(world)));confirmation=null;select(id,unchanged);});
    }
    void cancelHop(){execute(()->{pendingHop=null;confirmation=null;attempts=0;status="World hop cancelled";publish();});}
    void cycle(boolean forward)
    {
        execute(()->{
            capture();if(state!=GameState.LOGGED_IN){status="Log in to quick-hop";publish();return;}
            World current=known.get(currentWorld);if(current==null||known.size()<2)return;
            Set<String> wanted=typeNames(current);if(config.worldHopOutOfDanger()){wanted.remove("PVP");wanted.remove("HIGH_RISK");}
            wanted.removeAll(Set.of("BOUNTY","SKILL_TOTAL","LAST_MAN_STANDING"));
            List<World> list=new ArrayList<>(known.values());int origin=list.indexOf(current);
            for(int n=1;n<list.size();n++)
            {
                World world=list.get(Math.floorMod(origin+(forward?n:-n),list.size()));Set<String> types=typeNames(world);
                types.removeAll(Set.of("BOUNTY","LAST_MAN_STANDING"));if(types.contains("SKILL_TOTAL")&&totalMet(world))types.remove("SKILL_TOTAL");
                if(wanted.equals(types)&&population(world)<1950&&population(world)>=0&&eligible(world).isEmpty()&&filtersMatch(world))
                {select(world.getId(),false);return;}
            }
            status="No eligible world in that direction";publish();
        });
    }
    void onProfileChanged(){if(!running)return;epoch++;cancelWork();pendingHop=null;confirmation=null;profile=null;currentPing=-1;lastWorld=0;favourites.clear();synchronized(display){display.clear();}reading=Snapshot.empty(null,"Loading character");notifyChange();}
    void onGameStateChanged(GameStateChanged event)
    {if(!running)return;bindProfile();capture();if(geographies.size()<known.size())captureGeographies();if(state!=GameState.LOGGED_IN&&state!=GameState.LOGIN_SCREEN)
        {pendingHop=null;confirmation=null;cancelWork();currentPing=-1;currentLoss=-1;lastCurrentProbe=Long.MIN_VALUE;retransmit=new RetransmitCalculator();}publish();}
    void onGameTick()
    {
        if(!running)return;boolean changed=bindProfile();changed|=capture();ticks++;
        if(geographies.size()<known.size())changed|=captureGeographies();
        if(known.isEmpty())readInitialWorlds();
        if(visible&&lastRefresh!=Long.MIN_VALUE&&clock.getAsLong()-lastRefresh>=REFRESH_MS){lastRefresh=clock.getAsLong();worlds.refresh();}
        if(pendingHop!=null)
        {
            World world=known.get(pendingHop.getId());String error=world==null?"World is no longer available":eligible(world);
            if(state!=GameState.LOGGED_IN||!error.isEmpty()){pendingHop=null;status=error.isEmpty()?"World hop cancelled":error;changed=true;}
            else if(pendingSource==null||!typeNames(world).equals(typeNames(pendingSource)))
            {
                // A warning authorises the selected world mode, not a later refreshed PvP/high-risk mode.
                int id=world.getId();pendingHop=null;pendingSource=null;select(id,false);changed=true;
            }
            else if(client.getWidget(InterfaceID.Worldswitcher.BUTTONS)==null)
            {client.openWorldHopper();if(++attempts>=3){pendingHop=null;status="World switcher did not open after 3 attempts";changed=true;}}
            else
            {
                net.runelite.api.World target=pendingHop;
                if(!Objects.equals(world.getAddress(),pendingSource.getAddress()))target.setAddress(world.getAddress());
                if(!Objects.equals(world.getActivity(),pendingSource.getActivity()))target.setActivity(world.getActivity());
                if(!Objects.equals(world.getRegion(),pendingSource.getRegion()))target.setLocation(world.getRegion()==null?0:world.getRegion().getId());
                if(population(world)!=pendingPopulation)target.setPlayerCount(population(world));
                pendingHop=null;pendingSource=null;attempts=0;client.hopToWorld(target);status="Requested World "+target.getId();changed=true;
            }
        }
        if(visible&&config.worldHopPing()||needsCurrentPing())scheduleProbe(false);
        if(changed)publish();
    }
    void onWorldsFetch(WorldsFetch event)
    {
        if(!running||event==null||event.getWorldResult()==null)return;List<World> list=copyOfficial(event.getWorldResult());long ticket=lifecycle;
        clientThread.invokeLater(()->{if(running&&ticket==lifecycle){bindProfile();acceptWorlds(list);}});
    }
    void onWorldListLoad(WorldListLoad event)
    {if(!running||event==null)return;for(net.runelite.api.World world:event.getWorlds())if(known.containsKey(world.getId()))populations.put(world.getId(),world.getPlayerCount());lastRefresh=clock.getAsLong();publish();}
    void onChatMessage(ChatMessage event)
    {if(running&&event.getType()==ChatMessageType.GAMEMESSAGE&&"Please finish what you're doing before using the World Switcher.".equals(event.getMessage())){pendingHop=null;attempts=0;status="Finish the current activity before hopping";publish();}}
    void onCommandExecuted(CommandExecuted event)
    {
        if(!running||!COMMAND.equalsIgnoreCase(event.getCommand()))return;
        String[] args=event.getArguments();if(args.length!=1){message("Usage: ::rwhop world");return;}
        try{int id=Integer.parseInt(args[0]);if(id>0&&id<300)id+=300;selectWorld(id);}catch(NumberFormatException invalid){message("Use a valid world number");}
    }
    void onMenuEntryAdded(MenuEntryAdded event)
    {
        if(!running||!config.worldHopMenu()||state!=GameState.LOGGED_IN)return;
        int component=event.getActionParam1(),group=component>>>16;
        if(group!=InterfaceID.FRIENDS&&group!=InterfaceID.CHATCHANNEL_CURRENT&&component!=InterfaceID.ClansSidepanel.PLAYERLIST&&component!=InterfaceID.ClansGuestSidepanel.PLAYERLIST)return;
        String option=event.getOption();if(!Set.of("Message","Add friend","Remove friend","Kick").contains(option))return;
        MenuEntry[] entries=client.getMenuEntries();if(entries!=null)for(MenuEntry entry:entries)if(MENU.equals(entry.getOption())&&Objects.equals(event.getTarget(),entry.getTarget()))return;
        ChatPlayer player=chatPlayer(event.getTarget());if(player==null||player.getWorld()==0||player.getWorld()==currentWorld)return;
        World target=known.get(player.getWorld()),current=known.get(currentWorld);
        if(target==null||current==null||!typeNames(current).contains("PVP")&&typeNames(target).contains("PVP"))return;
        client.createMenuEntry("Message".equals(option)?-2:-1).setOption(MENU).setTarget(event.getTarget()).setType(MenuAction.RUNELITE)
            .onClick(entry->{ChatPlayer latest=chatPlayer(entry.getTarget());if(latest!=null)selectWorld(latest.getWorld());});
    }
    void onMenuOptionClicked(MenuOptionClicked event){/* MenuEntry.onClick owns the deliberate action. */}

    private void select(int id,boolean approved)
    {
        pendingHop=null;pendingSource=null;attempts=0;confirmation=null;capture();World world=known.get(id);String error=world==null?"Unknown world":eligible(world);
        if(!error.isEmpty()){status=error;publish();return;}
        String risk=risk(typeNames(world));
        if(!approved&&!risk.isEmpty()){confirmation=new WorldWarning(++serial,id,"World "+id+" · "+risk);status="Confirm this world type";publish();return;}
        confirmation=null;net.runelite.api.World target=client.createWorld();target.setId(id);target.setAddress(world.getAddress());target.setActivity(world.getActivity());
        target.setPlayerCount(population(world));target.setLocation(world.getRegion()==null?0:world.getRegion().getId());target.setTypes(WorldUtil.toWorldTypes(world.getTypes()));
        if(state==GameState.LOGIN_SCREEN){client.changeWorld(target);status="Selected World "+id;}
        else{pendingHop=target;pendingSource=world;pendingPopulation=population(world);attempts=0;status="Opening world switcher for "+id;if(config.worldHopMessage())message("Hopping to World "+id+"…");}
        publish();
    }
    private String eligible(World world)
    {
        if(state!=GameState.LOGGED_IN&&state!=GameState.LOGIN_SCREEN)return "Log in or open the login screen";
        if(world.getId()==currentWorld)return "Already on this world";
        if(population(world)<0)return "World is offline";if(population(world)>=2000)return "World is full";
        Set<String> types=typeNames(world);
        if(state==GameState.LOGGED_IN&&types.contains("MEMBERS")&&!member)return "Members account required";
        if(state==GameState.LOGGED_IN&&types.contains("SKILL_TOTAL")&&!totalMet(world))return "Total level "+requiredTotal(world)+" required";
        return "";
    }
    private boolean totalMet(World world){int requirement=requiredTotal(world);return requirement>0&&(typeNames(world).contains("MEMBERS")?total:f2pTotal)>=requirement;}
    private static int requiredTotal(World world){try{return Integer.parseInt(clean(world.getActivity()).split(" ")[0]);}catch(RuntimeException invalid){return -1;}}
    private int population(World world){return populations.getOrDefault(world.getId(),world.getPlayers());}
    private boolean capture()
    {
        GameState next=client.getGameState();int actual=client.getWorld();boolean changed=state!=next||actual!=currentWorld;state=next;
        if(actual!=currentWorld&&(state==GameState.LOGGED_IN||state==GameState.LOGIN_SCREEN))
        {if(currentWorld>0&&state==GameState.LOGGED_IN)lastWorld=currentWorld;currentWorld=actual;cancelWork();currentPing=-1;currentLoss=-1;
            lastCurrentProbe=Long.MIN_VALUE;lastProbe=Long.MIN_VALUE;retransmit=new RetransmitCalculator();}
        int level=client.getTotalLevel(),free=0;for(Skill skill:Skill.values())if(!skill.isMembers())free+=client.getRealSkillLevel(skill);
        Set<net.runelite.api.WorldType> worldTypes=client.getWorldType();boolean members=worldTypes!=null&&worldTypes.contains(net.runelite.api.WorldType.MEMBERS)||client.getVarpValue(VarPlayer.MEMBERSHIP_DAYS)>0;
        int fav1=state==GameState.LOGGED_IN?client.getVarbitValue(VarbitID.WORLDSWITCHER_FAVOURITE_1):0,fav2=state==GameState.LOGGED_IN?client.getVarbitValue(VarbitID.WORLDSWITCHER_FAVOURITE_2):0;
        changed|=level!=total||free!=f2pTotal||members!=member||fav1!=favourite1||fav2!=favourite2;
        total=level;f2pTotal=free;member=members;favourite1=fav1;favourite2=fav2;return changed;
    }
    private boolean bindProfile()
    {
        GameState game=client.getGameState();String next=game==GameState.LOGGED_IN?store.getRSProfileKey():game==GameState.LOGIN_SCREEN?null:profile;
        if(Objects.equals(profile,next))return false;epoch++;cancelWork();profile=next;pendingHop=null;confirmation=null;favourites.clear();hiddenNativeFavourites.clear();lastWorld=0;currentPing=-1;currentLoss=-1;retransmit=new RetransmitCalculator();pings.clear();
        synchronized(display){display.clear();for(String key:List.of("search","membership","region","type","safe","favourites","sort","descending","showActivity","showLocation","pinFavourites","doubleClickHop"))
            {String value=store.getConfiguration(RunewardenConfig.GROUP,profile,"worldHopperDisplay_"+key);if(value!=null&&value.length()<=1024)display.put(key,value);}}
        for(int id:known.keySet())readFavourite(id);
        return true;
    }
    private void readInitialWorlds()
    {
        long now=clock.getAsLong();if(lastRead!=Long.MIN_VALUE&&now-lastRead<3000||!workInFlight.compareAndSet(false,true))return;lastRead=now;long ticket=lifecycle;
        // World lists are public data: a character switch must not strand an initial login-screen load.
        submit(()->{if(!running||ticket!=lifecycle)return;List<World> list=copyOfficial(worlds.getWorlds());clientThread.invokeLater(()->{if(running&&ticket==lifecycle){bindProfile();acceptWorlds(list);}});});
    }
    private void acceptWorlds(List<World> list)
    {
        if(list.isEmpty()){status="World list unavailable";publish();return;}
        known.clear();for(World world:list)known.put(world.getId(),world);populations.clear();pings.keySet().retainAll(known.keySet());
        geographies.keySet().retainAll(known.keySet());captureGeographies();
        for(int id:known.keySet())readFavourite(id);
        favourites.retainAll(known.keySet());lastRefresh=clock.getAsLong();status="World list ready";capture();publish();
    }
    private void scheduleProbe(boolean force)
    {
        if(!running||!(visible&&config.worldHopPing()||needsCurrentPing())||known.isEmpty()||workInFlight.get())return;
        long now=clock.getAsLong();if(!force&&lastProbe!=Long.MIN_VALUE&&now-lastProbe<3000)return;
        int id;
        if(!visible||!config.worldHopPing()||needsCurrentPing()&&(lastCurrentProbe==Long.MIN_VALUE||now-lastCurrentProbe>=6000))id=currentWorld;
        else if(!pingQueue.isEmpty())id=pingQueue.removeFirst();
        else{List<Integer> ids=new ArrayList<>(known.keySet());id=ids.get(Math.floorMod(ticks/5,ids.size()));}
        World world=known.get(id);if(world==null||!official(world.getAddress())||!workInFlight.compareAndSet(false,true))return;
        long ticket=epoch,probe=probeEpoch;String account=profile;lastProbe=now;if(id==currentWorld)lastCurrentProbe=now;pinging=true;publish();
        java.io.FileDescriptor fd=id==currentWorld&&state==GameState.LOGGED_IN?client.getSocketFD():null;
        submit(()->{
            if(!running||ticket!=epoch||probe!=probeEpoch)return;
            TCPInfo tcp=null;try{if(fd!=null)tcp=Ping.getTCPInfo(fd);}catch(RuntimeException|LinkageError unavailable){/* Platform has no TCP_INFO support. */}
            final TCPInfo tcpInfo=tcp;
            if(!running||ticket!=epoch||probe!=probeEpoch)return;
            int value;try{value=ping.applyAsInt(world);}catch(RuntimeException failure){value=-1;}final int result=value>=0?value:-1;
            clientThread.invokeLater(()->{if(running&&ticket==epoch&&probe==probeEpoch&&Objects.equals(account,profile)
                &&(account==null||Objects.equals(account,store.getRSProfileKey()))&&known.containsKey(id)&&(visible||needsCurrentPing()))
                {
                    pings.put(id,result);
                    if(id==currentWorld)
                    {
                        currentPing=result;currentLoss=-1;
                        if(tcpInfo!=null&&state==GameState.LOGGED_IN)
                        {int rtt=(int)Math.max(0,Math.min(Integer.MAX_VALUE,tcpInfo.getRTT()/1000));if(result<0){currentPing=rtt;pings.put(id,rtt);}
                            retransmit.record(tcpInfo);int loss=retransmit.getRetransmitPercent();currentLoss=loss>=0&&loss<=100?loss:-1;}
                    }
                    pinging=false;publish();
                }});});
    }
    private void submit(Runnable task)
    {
        try{worker.execute(()->{activeWorker=Thread.currentThread();try{task.run();}finally{activeWorker=null;workInFlight.set(false);}});}
        catch(RejectedExecutionException stopped){workInFlight.set(false);pinging=false;}
    }
    private void cancelWork()
    {
        probeEpoch++;pingQueue.clear();pinging=false;Thread thread=activeWorker;if(thread!=null)thread.interrupt();
        // Do not reset the in-flight guard or create another worker while an OS DNS lookup remains blocked.
    }
    private void publish()
    {
        List<WorldRow> rows=new ArrayList<>();for(World world:known.values())
            rows.add(new WorldRow(world,population(world),pings.getOrDefault(world.getId(),-1),isFavourite(world.getId()),eligible(world).isEmpty(),eligible(world),geographies.getOrDefault(world.getId(),-1)));
        reading=new Snapshot(profile,status,currentWorld,lastWorld,currentPing,currentLoss,total,member,pinging,state==GameState.LOGGED_IN,rows,confirmation);notifyChange();
    }
    private void notifyChange(){Runnable action=listener;SwingUtilities.invokeLater(()->{if(listener==action)action.run();});}
    private void execute(Runnable action)
    {
        if(!running)return;long ticket=epoch;String account=reading.profile;
        clientThread.invokeLater(()->{if(running&&ticket==epoch&&Objects.equals(account,profile)
            &&(account==null?client.getGameState()==GameState.LOGIN_SCREEN:Objects.equals(account,store.getRSProfileKey())))action.run();});
    }
    private boolean needsCurrentPing(){return state==GameState.LOGGED_IN&&(pingDemand||config.worldHopDisplayPing());}
    private boolean filtersMatch(World world)
    {
        Set<String> types=typeNames(world);boolean members=types.contains("MEMBERS");String membership=display("membership","All worlds");
        if(membership.equals("Free worlds")&&members||membership.equals("Members worlds")&&!members)return false;
        if(Boolean.parseBoolean(display("safe","true"))&&!normal(types))return false;
        if(Boolean.parseBoolean(display("favourites","false"))&&!isFavourite(world.getId()))return false;
        Set<String> regions=selected(display("region",""));if(!regions.isEmpty()&&!regions.contains(regionName(world.getRegion()==null?-1:world.getRegion().getId())))return false;
        Set<String> kinds=selected(display("type",""));if(!kinds.isEmpty())
        {if(kinds.stream().noneMatch(kind->matchesType(types,kind)))return false;}
        String needle=display("search","").trim().toLowerCase(Locale.ROOT).replaceFirst("^#","");
        return needle.isEmpty()||(world.getId()+" "+clean(world.getActivity())).toLowerCase(Locale.ROOT).contains(needle);
    }
    private static Set<String> selected(String text)
    {Set<String> values=new HashSet<>();for(String part:text.split("\\|"))if(!part.isBlank()&&!part.startsWith("All"))values.add(part);return values;}
    private boolean captureGeographies()
    {
        if(client.getGameState().getState()<GameState.LOGIN_SCREEN.getState())return false;
        EnumComposition locations=client.getEnum(EnumID.WORLD_LOCATIONS);if(locations==null)return false;boolean changed=false;
        for(int id:known.keySet()){int value=locations.getIntValue(id);Integer old=geographies.put(id,value);changed|=old==null||old!=value;}
        return changed;
    }
    private void message(String text){if(config.worldHopMessage())client.addChatMessage(ChatMessageType.CONSOLE,"Runewarden",text,null);}
    private boolean isFavourite(int id){return favourites.contains(id)||(id==favourite1||id==favourite2)&&!hiddenNativeFavourites.contains(id);}
    private void readFavourite(int id){String value=store.getConfiguration(RunewardenConfig.GROUP,profile,"worldHopperFavourite_"+id);if("true".equals(value))favourites.add(id);else if("false".equals(value))hiddenNativeFavourites.add(id);}
    private static boolean normal(Set<String> types){return types.stream().allMatch(type->Set.of("MEMBERS","SKILL_TOTAL","LAST_MAN_STANDING").contains(type));}
    static boolean matchesType(Set<String> types,String selected)
    {if(selected.equals("NORMAL"))return normal(types);if(selected.equals("HIGH_RISK"))return types.contains("HIGH_RISK")&&!types.contains("PVP");if(selected.equals("SEASONAL"))return types.contains("SEASONAL")||types.contains("TOURNAMENT");return types.contains(selected);}
    private ChatPlayer chatPlayer(String name)
    {
        String clean=Text.removeTags(name);FriendsChatManager fc=client.getFriendsChatManager();if(fc!=null){FriendsChatMember member=fc.findByName(clean);if(member!=null)return member;}
        ClanChannel clan=client.getClanChannel();if(clan!=null){ClanChannelMember member=clan.findMember(clean);if(member!=null)return member;}
        clan=client.getGuestClanChannel();if(clan!=null){ClanChannelMember member=clan.findMember(clean);if(member!=null)return member;}
        FriendContainer friends=client.getFriendContainer();return friends==null?null:friends.findByName(clean);
    }
    private static Set<String> typeNames(World world){Set<String> types=new HashSet<>();for(Enum<?> type:world.getTypes())types.add(type.name());return types;}
    private static String risk(Set<String> types)
    {List<String> warnings=new ArrayList<>();for(String type:new TreeSet<>(types))if(!Set.of("MEMBERS","SKILL_TOTAL","LAST_MAN_STANDING").contains(type))warnings.add(type.replace('_',' '));return String.join(" · ",warnings);}
    private static String clean(String text){if(text==null||"-".equals(text))return "";String clean=Text.removeTags(text).replace('\n',' ');return clean.substring(0,Math.min(180,clean.length()));}
    private static String regionName(int region)
    {switch(region){case 0:return "United States";case 1:return "United Kingdom";case 3:return "Australia";case 7:return "Germany";case 8:return "Brazil";case 9:return "Japan";case 10:return "Singapore";case 12:return "South Africa";default:return "Unknown";}}
    private static List<World> copyOfficial(WorldResult result)
    {List<World> list=new ArrayList<>();if(result!=null&&result.getWorlds()!=null)for(World world:result.getWorlds())if(world!=null&&world.getId()>0&&world.getId()<10000&&official(world.getAddress())&&list.size()<1000)
        list.add(World.builder().id(world.getId()).address(world.getAddress()).activity(world.getActivity()).region(world.getRegion()).players(world.getPlayers()).types(world.getTypes().clone()).build());
        list.sort(Comparator.comparingInt(World::getId));return list;}
    private static boolean official(String address){return address!=null&&address.length()<160&&address.toLowerCase(Locale.ROOT).matches("[a-z0-9-]+\\.runescape\\.com");}
    private static int pingOfficialWorld(World world)
    {
        if(!official(world.getAddress()))return -1;long start=System.nanoTime();
        try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(world.getAddress(),43594),1000);return (int)Math.min(Integer.MAX_VALUE,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));}
        catch(java.io.IOException failure){return -1;}
    }
}
