/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

/** Explicit route planning over Shortest Path's public event API; never performs game inputs. */
@Singleton
final class GuideNavigation
{
    private final Client client;
    private final EventBus eventBus;
    private final PluginManager pluginManager;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final GuideCatalog guides;
    private boolean started, posting;
    private String profile, clearId, routeId, owner;
    private Object ownedTarget;
    private WorldPoint previousLocation;
    private long transportRevision=Long.MIN_VALUE;
    private boolean transportDirty;
    private volatile RouteSummary pendingSummary;
    private Map<WorldPoint,String> routeChoices=Collections.emptyMap();
    private int ticks, routeTick;
    private long epoch, serial;
    private volatile long manualRevision;
    private volatile Plan plan = Plan.empty("Choose a destination");
    private volatile WorldPoint lastBank;
    private JPanel panel, body;
    private JTextField search, coordinates;
    private JTextField worldSearch;
    private JPanel worldBody;
    private volatile List<WorldChoice> worlds=Collections.emptyList();
    private JComboBox<Destination> stops;
    private Supplier<GuideCatalog.Entry> guideContext = () -> null;
    private Supplier<GoalCatalog.Facts> accountFacts = () -> null;
    private Supplier<OwnershipLedger.View> ownership = () -> null;
    private Function<String,List<Destination>> arrivalOptions = ignored -> null;
    private Supplier<Map<String,Object>> transportOptions = Collections::emptyMap;
    private LongSupplier transportRevisionSource = () -> 0;
    private Function<Plugin,ShortestPathRouteReading> routeReader=ShortestPathRouteReading::read;
    void setRouteReader(Function<Plugin,ShortestPathRouteReading> reader){routeReader=reader==null?ShortestPathRouteReading::read:reader;}
    /** Null means this guide has no dynamic arrivals; empty means its observed arrivals are not ready. */
    void setArrivalOptions(Function<String,List<Destination>> supplier) { arrivalOptions=supplier==null?ignored->null:supplier; }
    void setTransportOptionsSupplier(Supplier<Map<String,Object>> supplier) { transportOptions=supplier==null?Collections::emptyMap:supplier; }
    void setTransportRevisionSupplier(LongSupplier supplier) { transportRevisionSource=supplier==null?()->0:supplier; }
    private TravelNotebook notebook;
    private Consumer<Set<Integer>> bankFocus = ignored -> { };
    private java.util.function.IntConsumer openItem = ignored -> { };
    private java.util.function.IntFunction<String> itemNames = id -> "Item " + id;
    void setBankFocusAction(Consumer<Set<Integer>> action) { bankFocus = action == null ? ignored -> { } : action; }
    void setOpenItemAction(java.util.function.IntConsumer action) { openItem = action == null ? ignored -> { } : action; }
    void setItemNameLookup(java.util.function.IntFunction<String> lookup) { itemNames = lookup == null ? id -> "Item " + id : lookup; }
    private JPanel savedBody, bankBody, editor;
    private JTextField destinationName, destinationCoordinates;
    private JTextArea destinationItems;
    private JCheckBox destinationGuide;
    private String editingDestination,editingGuideId; private JComboBox<String> travelMode; private JComboBox<TravelNotebook.Entry> savedPick,bankPick; private GuideText destinationError;
    private volatile List<TravelNotebook.Entry> itinerary = Collections.emptyList();
    private Plan onward;
    void setDataStore(RunewardenDataStore store) { notebook = config == null || store == null ? null : new TravelNotebook(config, store);if(notebook!=null&&profile!=null)notebook.bind(profile); }
    void setGuideContextSupplier(Supplier<GuideCatalog.Entry> supplier) { guideContext = supplier == null ? () -> null : supplier; }
    void setFactsSupplier(Supplier<GoalCatalog.Facts> supplier) { accountFacts = supplier == null ? () -> null : supplier; }
    void setOwnershipSupplier(Supplier<OwnershipLedger.View> supplier) { ownership = supplier == null ? () -> null : supplier; }
    List<TravelNotebook.Entry> savedDestinations() { return notebook == null ? Collections.emptyList() : notebook.destinations(); }
    List<TravelNotebook.Entry> bankStops() { return notebook == null ? Collections.emptyList() : notebook.banks(); }
    private Consumer<String> openGuide = id -> { };

    static final class Destination
    {
        final String title, guideId; final WorldPoint point;
        Destination(String title,String guideId,WorldPoint point) { this.title=title; this.guideId=guideId; this.point=point; }
        @Override public String toString() { return title; }
    }
    static final class WorldChoice
    {
        final int id,population,location; final String activity,types;
        WorldChoice(int id,int population,int location,String activity,String types)
        { this.id=id; this.population=population; this.location=location; this.activity=activity; this.types=types; }
    }
    static final class Access
    {
        final int state; final String guideId, bankState; final List<String> details; final List<Map<Integer,Integer>> requirements;
        Access(int state,String guideId,String bankState,List<String> details,List<Map<Integer,Integer>> requirements)
        {this.state=state;this.guideId=guideId;this.bankState=bankState;this.details=Collections.unmodifiableList(new ArrayList<>(details));this.requirements=TravelNotebook.copyRequirements(requirements);}
        static Access none(){return new Access(1,null,"",Collections.emptyList(),Collections.emptyList());}
    }
    static final class Plan
    {
        final String title, status, reason, itemStatus;
        final Object target; final WorldPoint point;
        final boolean reached, bankVisited, answered;
        final Integer cost; final List<String> transports; final Access access;
        Plan(String title,String status,Object target,WorldPoint point,boolean reached,boolean bankVisited,
            boolean answered,Integer cost,String reason,String itemStatus,List<String> transports)
        {this(title,status,target,point,reached,bankVisited,answered,cost,reason,itemStatus,transports,Access.none());}
        Plan(String title,String status,Object target,WorldPoint point,boolean reached,boolean bankVisited,
            boolean answered,Integer cost,String reason,String itemStatus,List<String> transports,Access access)
        { this.access=access; this.title=title; this.status=status; this.target=target; this.point=point; this.reached=reached;
          this.bankVisited=bankVisited; this.answered=answered; this.cost=cost; this.reason=reason;
          this.itemStatus=itemStatus; this.transports=Collections.unmodifiableList(new ArrayList<>(transports)); }
        static Plan empty(String status) { return new Plan("Travel",status,null,null,false,false,false,null,"","",Collections.emptyList()); }
    }
    private static final class RouteSummary
    {
        final String owner;final Object target;final WorldPoint start;
        boolean confirmed,requested;
        RouteSummary(String owner,Object target,WorldPoint start)
        {this.owner=owner;this.target=target;this.start=start;}
    }

    @Inject GuideNavigation(Client client, EventBus eventBus, PluginManager pluginManager,
        ClientThread clientThread,ConfigManager config,GuideCatalog guides)
    {
        this.client = client;
        this.eventBus = eventBus;
        this.pluginManager = pluginManager;
        this.clientThread=clientThread; this.config=config; this.guides=guides;
    }

    GuideNavigation(Client client,EventBus eventBus,PluginManager pluginManager)
    { this(client,eventBus,pluginManager,null,null,null); }

    void start() { started=true; clear(); }
    void stop()
    {
        clearOwned(owner);started=false;routeId=null;lastBank=null;
        worlds=Collections.emptyList();itinerary=Collections.emptyList();onward=null;
        if(notebook!=null)notebook.clear();plan=Plan.empty("Travel disabled");refresh();
    }
    Plan snapshot() { return config!=null&&profile!=null&&!Objects.equals(profile,config.getRSProfileKey())?Plan.empty("Choose a destination"):plan; }
    WorldPoint lastBank() { return config!=null&&profile!=null&&!Objects.equals(profile,config.getRSProfileKey())?null:lastBank; }
    void setOpenGuideAction(Consumer<String> action) { openGuide=action==null ? id -> { } : action; }

    long generation() { return epoch; }
    long manualRevision() { return manualRevision; }
    boolean ownsRoute(String routeOwner,WorldPoint point)
    { return ownedTarget!=null && clearId==null && Objects.equals(owner,routeOwner) && profile!=null
        && (config==null||Objects.equals(profile,config.getRSProfileKey())) && Objects.equals(plan.point,point); }

    boolean available()
    {
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if ("shortestpath.ShortestPathPlugin".equals(plugin.getClass().getName())
                && pluginManager.isPluginActive(plugin)) return true;
        }
        return false;
    }

    boolean canTravel()
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null
            && client.getLocalPlayer().getInteracting() == null
            && (!client.isInInstancedRegion()||ownHouseOrigin()!=null);
    }

    boolean route(WorldPoint target)
    {
        GuideCatalog.Entry context=guideContext.get();return routeGuide(context,target,"Guide destination",false);
    }

    /** Draws travel to an explicit step target or a sourced activity preparation entrance. */
    boolean routeStep(GuideCatalog.Entry guide, GuideCatalog.Step step)
    {
        if(guide!=null && GuideTravelCatalog.isPreparation(guide,step))
        {
            List<Destination> options=arrivalOptions.apply(guide.id);
            if(options!=null)
            {
                if(!options.isEmpty())return routeChoices("guide",guide,options);
                clearOwned("guide");plan=Plan.empty("Waiting for available farm destinations");refresh();return false;
            }
        }
        WorldPoint point=stepDestination(guide,step);
        if(point==null){clearOwned("guide");return false;}
        return routeGuide(guide,point,guide.title,false);
    }

    boolean hasTravelDestination(GuideCatalog.Entry guide, GuideCatalog.Step step)
    { return guide!=null&&GuideTravelCatalog.isPreparation(guide,step)&&arrivalOptions.apply(guide.id)!=null
        || stepDestination(guide,step)!=null; }

    WorldPoint stepDestination(GuideCatalog.Entry guide, GuideCatalog.Step step)
    {
        if(guide==null || step==null || !("training".equals(guide.category)||"minigame".equals(guide.category)))return null;
        if(step.location!=null && valid(step.location.point()))return step.location.point();
        return guides==null?null:guides.travel().destination(guide,step);
    }

    /** Selection may arrive before the Travel module's first tick; bind its ownership before posting. */
    private void bindProfile()
    {
        String account=currentProfile();
        if(!Objects.equals(profile,account))
        {
            clear();profile=account;
            if(notebook!=null)
            {
                notebook.bind(account);List<TravelNotebook.Entry> banks=notebook.banks();
                if(!banks.isEmpty())lastBank=banks.get(0).point;
            }
            refresh();refreshNotebook();
        }
    }

    void clear()
    {
        epoch++; profile=null;  clearId=null; routeId=null; owner=null; ownedTarget=null; lastBank=null;
        previousLocation=null;routeChoices=Collections.emptyMap();transportRevision=Long.MIN_VALUE;transportDirty=false;pendingSummary=null;
        worlds=Collections.emptyList();itinerary=new ArrayList<>();onward=null;if(notebook!=null)notebook.clear();
        plan=Plan.empty("Choose a destination"); refresh();
    }

    void onGameTick()
    {
        if (!started) return;
        bindProfile();
        if (profile==null) return;
        ticks++;
        WorldPoint location=currentLocation();
        WorldPoint previous=previousLocation;previousLocation=location;
        long revision=transportRevisionSource.getAsLong();
        if(revision!=transportRevision){transportRevision=revision;if(ownedTarget!=null)transportDirty=true;}
        // A teleport changes the relevant origin and may change the cheapest farm arrival.
        // Ordinary walking remains the native pathfinder's responsibility.
        boolean teleported=valid(previous)&&valid(location)&&previous.distanceTo(location)>16&&ownedTarget!=null;
        if(teleported)transportDirty=true;
        if(transportDirty&&ownedTarget!=null&&clearId==null&&canTravel()&&available()
            &&(teleported||ticks-routeTick>=3))show(owner,ownedTarget,plan.point,plan.title);
        if (ticks%10==1) readWorlds();
        net.runelite.api.widgets.Widget bank=client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS);
        if (bank!=null && !bank.isHidden() && !client.isInInstancedRegion()) { lastBank=client.getLocalPlayer().getWorldLocation(); if(notebook!=null&&notebook.observeBank(lastBank,System.currentTimeMillis()))refreshNotebook(); }
        refreshNativeSummary();
        if(routeId!=null && (ticks-routeTick>10 || !available()))
        {
            routeId=null;Plan old=plan;
            plan=withStatus(old,available()?"No reply from Shortest Path":"Shortest Path is not running");refresh();
        }
    }

    boolean preview(String title,WorldPoint point,List<Map<Integer,Integer>> requirements)
    {
        Access access=new Access(1,null,ownershipSummary(requirements,ownership.get()),Collections.emptyList(),requirements);
        return previewChecked(title,point,requirements,access,null);
    }
    private boolean previewChecked(String title,WorldPoint point,List<Map<Integer,Integer>> requirements,Access access,GuideCatalog.Entry guide)
    {
        if (!guard(point)) return false;
        Object target=target(point,requirements);
        if(access.state<0){plan=new Plan(safe(title),"Unlocks needed",target,point,false,false,false,null,"","",Collections.emptyList(),access);refresh();return false;}
        plan=new Plan(safe(title),"Finding route…",target,point,false,false,false,null,"","",Collections.emptyList(),access);
        // Starting the explicit route refreshes native account/item/house state.
        // Its completed public result supplies the summary without another search.
        return show("travel",target,point,title);
    }

    /** Automatic work cannot replace a route selected after its activity was chosen. Client thread only. */
    boolean routeAutomatic(String routeOwner,WorldPoint point,String title,long expectedManualRevision)
    {
        return manualRevision==expectedManualRevision && routeOwned(routeOwner,point,title);
    }

    boolean routeOwned(String routeOwner,WorldPoint point,String title) { if("guide".equals(routeOwner))return routeGuide(guideContext.get(),point,title,false);plan=new Plan(safe(title),"Prepared destination",point,point,false,false,false,null,"","",Collections.emptyList());return show(routeOwner,point,point,title); }
    boolean routeGuide(GuideCatalog.Entry guide,WorldPoint point,String title,boolean preview)
    {
        Access access=guideAccess(guide,accountFacts.get(),ownership.get());
        if(preview)return previewChecked(title,point,access.requirements,access,guide);
        if(!guard(point))return false;Object target=target(point,access.requirements);
        if(access.state<0){plan=new Plan(safe(title),"Unlocks needed",target,point,false,false,false,null,"","",Collections.emptyList(),access);refresh();return false;}
        plan=new Plan(safe(title),"Prepared destination",target,point,false,false,false,null,"","",Collections.emptyList(),access);return show("guide",target,point,title);
    }
    boolean routeOwned(String routeOwner,WorldPoint point,String title,List<Map<Integer,Integer>> requirements)
    {Object target=target(point,requirements);Access access=new Access(1,null,ownershipSummary(requirements,ownership.get()),Collections.emptyList(),requirements);plan=new Plan(safe(title),"Prepared destination",target,point,false,false,false,null,"","",Collections.emptyList(),access);return show(routeOwner,target,point,title); }
    boolean showPreview(String routeOwner)
    { Plan current=plan; if(current.access.state<0)return false;return current.point!=null && show(routeOwner,current.target,current.point,current.title); }
    private boolean show(String routeOwner,Object target,WorldPoint point,String title)
    {
        if (!guard(point)) return false;
        WorldPoint start=currentLocation();
        previousLocation=start;transportRevision=transportRevisionSource.getAsLong();transportDirty=false;
         clearId=null; ownedTarget=target; owner=routeOwner;
        if(!(target instanceof Collection<?>))routeChoices=Collections.emptyMap();
        Plan old=plan; plan=new Plan(safe(title),"Waiting for Shortest Path…",target,point,false,false,false,
            null,"","",Collections.emptyList(),old.access);
        routeId=id("route");routeTick=ticks;
        boolean summary="farm".equals(routeOwner)||"travel".equals(routeOwner)||target instanceof Collection<?>;
        pendingSummary=summary?new RouteSummary(routeOwner,target,start):null;
        Map<String,Object> request=new LinkedHashMap<>();request.put("target",target);request.put("start",start);
        Map<String,Object> options=new LinkedHashMap<>();Map<String,Object> supplied=transportOptions.get();
        if(supplied!=null)options.putAll(supplied);
        options.put("postTransports",summary);request.put("config",options);
        post("path",request);
        String requested=routeId;
        // Shortest Path queues its search initialisation. Ask after that queued work has run.
        execute(()->{
            if(!Objects.equals(requested,routeId))return;
            post("getTarget",Collections.singletonMap("id",requested));
        });
        refresh(); return true;
    }

    private boolean routeChoices(String routeOwner,GuideCatalog.Entry guide,List<Destination> options)
    {
        Map<WorldPoint,String> choices=new LinkedHashMap<>();
        for(Destination option:options)if(option!=null&&valid(option.point)&&choices.size()<64)choices.putIfAbsent(option.point,safe(option.title));
        if(choices.isEmpty())return false;
        WorldPoint first=choices.keySet().iterator().next();if(!guard(first))return false;
        Access access=guideAccess(guide,accountFacts.get(),ownership.get());
        if(access.state<0){plan=new Plan(guide.title,"Unlocks needed",first,first,false,false,false,null,"","",Collections.emptyList(),access);refresh();return false;}
        List<Object> targets=new ArrayList<>();for(WorldPoint point:choices.keySet())targets.add(target(point,access.requirements));
        Object target=Collections.unmodifiableList(targets);routeChoices=Collections.unmodifiableMap(choices);
        plan=new Plan(guide.title,"Finding best arrival…",target,first,false,false,false,null,"","",Collections.emptyList(),access);
        return show(routeOwner,target,first,guide.title);
    }

    /** Cleanup can clear a path only after the receiver confirms it is still our exact target. */
    void clearOwned(String routeOwner)
    {
        if (ownedTarget==null || !Objects.equals(owner,routeOwner) || currentProfile()==null
            || !Objects.equals(profile,currentProfile()) || !available()) return;
        routeId=null;pendingSummary=null;
        clearId=id("clear");String requested=clearId;
        long generation=epoch;String account=profile;
        Runnable check=()->{if(generation==epoch && Objects.equals(account,currentProfile())
            && Objects.equals(requested,clearId))post("getTarget",Collections.singletonMap("id",requested));};
        if(clientThread==null)check.run();else clientThread.invokeLater(check);
    }

    void onPluginMessage(PluginMessage event)
    {
        if (!"shortestpath".equals(event.getNamespace())) return;
        String name=event.getName(); Map<String,Object> data=event.getData();
        if("transports".equals(name)){receiveTransports(data);return;}
        if (!posting && ("path".equals(name) || "clear".equals(name))) { manualRevision++; owner=null; ownedTarget=null; clearId=null; routeId=null;routeChoices=Collections.emptyMap();pendingSummary=null; }
        if(!"currentTarget".equals(name))return;
        if (data==null || currentProfile()==null || !Objects.equals(profile,currentProfile())) return;
        if ("currentTarget".equals(name) && clearId!=null && clearId.equals(data.get("id")))
        {
            clearId=null;
            if (Boolean.TRUE.equals(data.get("set")) && sameTarget(ownedTarget,data.get("target"))) post("clear",Collections.emptyMap());
            ownedTarget=null; owner=null;
            plan=withStatus(plan,"Route released");refresh();
        }
        if("currentTarget".equals(name) && routeId!=null && routeId.equals(data.get("id")))
        {
            routeId=null;
            boolean accepted=Boolean.TRUE.equals(data.get("set")) && sameTarget(ownedTarget,data.get("target"));
            plan=withStatus(plan,accepted?"Shortest Path destination confirmed":"Shortest Path did not accept this destination");
            if(!accepted){ownedTarget=null;owner=null;pendingSummary=null;}
            else if(pendingSummary!=null)pendingSummary.confirmed=true;
            refresh();
        }
    }

    private void receiveTransports(Map<String,Object> data)
    {
        RouteSummary pending=pendingSummary;if(pending==null)return;
        // This event is only a wake-up: old cancelled searches can publish the current partial path.
        // The public completed result, exact ownership and origin are checked on the client thread.
        execute(()->{if(pendingSummary==pending)refreshNativeSummary();});
    }

    private void refreshNativeSummary()
    {
        RouteSummary pending=pendingSummary;
        if(pending==null||!pending.confirmed||pending.requested||clearId!=null
            ||!Objects.equals(owner,pending.owner)||!Objects.equals(ownedTarget,pending.target))return;
        ShortestPathRouteReading reading=null;
        for(Plugin plugin:pluginManager.getPlugins())
            if("shortestpath.ShortestPathPlugin".equals(plugin.getClass().getName())&&pluginManager.isPluginActive(plugin))
            {reading=routeReader.apply(plugin);break;}
        if(reading==null||!Objects.equals(pending.start,reading.start)||!targetPoints(pending.target).equals(reading.targets))return;
        WorldPoint destination=reading.reached?reading.target:plan.point;
        if(!routeChoices.isEmpty()&&!routeChoices.containsKey(destination))return;
        pending.requested=true;Plan old=plan;
        String arrival=reading.itemStatus;
        String status=reading.reached?old.access.state==0?"Route found · check access":"UNKNOWN".equals(arrival)?"Route found · check arrival supplies":"Route found":"No complete route found";
        plan=new Plan(routeChoices.getOrDefault(destination,old.title),status,old.target,destination,reading.reached&&old.access.state>=0,
            reading.bankVisited,true,reading.cost<0?null:reading.cost,reading.reason,arrival,reading.transports,old.access);refresh();
    }

    private static Set<WorldPoint> targetPoints(Object target)
    {
        Set<WorldPoint> points=new LinkedHashSet<>();
        if(target instanceof Collection<?>)for(Object element:(Collection<?>)target)points.addAll(targetPoints(element));
        else if(target instanceof Map<?,?>)points.addAll(targetPoints(((Map<?,?>)target).get("point")));
        else if(target instanceof WorldPoint)points.add((WorldPoint)target);
        return points;
    }

    private static Plan withStatus(Plan current,String status)
    {return new Plan(current.title,status,current.target,current.point,current.reached,current.bankVisited,current.answered,
        current.cost,current.reason,current.itemStatus,current.transports,current.access);}

    void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option=safe(event.getMenuOption()).toLowerCase(Locale.ROOT);
        if (option.contains("path") || option.contains("destination")) { manualRevision++; owner=null; ownedTarget=null; clearId=null;routeId=null;routeChoices=Collections.emptyMap();pendingSummary=null; }
    }

    private boolean guard(WorldPoint target)
    {
        String error=!started ? "Enable Travel to draw a route" : !valid(target) ? "Choose a valid destination" : !canTravel() ? "Travel needs an idle player outside an instance"
            : !valid(currentLocation()) ? "Waiting for the player's current location"
            : currentProfile()==null ? "Waiting for character profile" : !available() ? "Enable Shortest Path to draw a route" : null;
        if (error==null) { bindProfile(); return true; }
        if(!error.equals(plan.status)) { plan=Plan.empty(error); refresh(); }
        return false;
    }
    private WorldPoint currentLocation()
    { return client.getLocalPlayer()==null?null:client.isInInstancedRegion()?ownHouseOrigin():client.getLocalPlayer().getWorldLocation(); }
    private WorldPoint ownHouseOrigin()
    {
        // Native SP models the POH network at its public POH_LANDING_X/Y, independent of room rotation.
        // Building mode proves this is the player's house; a guest's portal selection is unknown.
        if(client.getVarbitValue(net.runelite.api.gameval.VarbitID.POH_BUILDING_MODE)!=1
            ||client.getLocalPlayer()==null||client.getLocalPlayer().getLocalLocation()==null)return null;
        net.runelite.api.WorldView view=client.getTopLevelWorldView();
        if(view==null||!view.isInstance()||view.getInstanceTemplateChunks()==null
            ||client.getWorldView(client.getLocalPlayer().getLocalLocation().getWorldView())!=view)return null;
        WorldPoint template=WorldPoint.fromLocalInstance(client,client.getLocalPlayer().getLocalLocation());
        // Bounds and virtual landing are the unchanged SP receiver's source coordinates.
        return template!=null&&template.getX()>=1856&&template.getX()<=2047&&template.getY()>=7040&&template.getY()<=7111
            ?new WorldPoint(1858,7051,0):null;
    }
    private String currentProfile()
    { return client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null ? null : config==null ? "fixture" : config.getRSProfileKey(); }
    private String id(String kind) { return "runewarden:"+kind+":"+epoch+":"+(++serial); }
    private void post(String name,Map<String,?> values)
    {
        Map<String,Object> data=new LinkedHashMap<>(); data.putAll(values); posting=true;
        try { eventBus.post(new PluginMessage("shortestpath",name,data)); } finally { posting=false; }
    }
    private void execute(Runnable action)
    {
        long generation=epoch; String account=profile;
        Runnable guarded=() -> { if (started && generation==epoch && Objects.equals(account,profile)
            && account!=null && Objects.equals(account,currentProfile())) action.run(); };
        if (clientThread==null) guarded.run(); else clientThread.invokeLater(guarded);
    }
    private void readWorlds()
    {
        net.runelite.api.World[] list=client.getWorldList(); List<WorldChoice> found=new ArrayList<>();
        if (list!=null) for (net.runelite.api.World world : list)
            if (world!=null && world.getId()>0 && found.size()<1000)
                found.add(new WorldChoice(world.getId(),world.getPlayerCount(),world.getLocation(),safe(world.getActivity()),
                    world.getTypes()==null ? "" : world.getTypes().toString().replace("[","").replace("]","").replace('_',' ')));
        worlds=Collections.unmodifiableList(found);
        if (worldBody!=null) SwingUtilities.invokeLater(this::renderWorlds);
    }
    List<WorldChoice> matchingWorlds(String term)
    {
        String query=safe(term).trim().toLowerCase(Locale.ROOT); List<WorldChoice> result=new ArrayList<>();
        for (WorldChoice world : worlds) if (query.isEmpty() || (world.id+" "+world.activity+" "+world.types).toLowerCase(Locale.ROOT).contains(query)) result.add(world);
        result.sort(Comparator.comparingInt(world -> world.id)); return result;
    }

    Access guideAccess(GuideCatalog.Entry guide,GoalCatalog.Facts facts,OwnershipLedger.View owned)
    {
        if(guide==null)return Access.none();List<String> details=new ArrayList<>();int state=1;
        if(guide.requirementDetails!=null&&!guide.requirementDetails.isEmpty())for(JsonObject requirement:guide.requirementDetails)
        {int checked=requirementState(requirement,facts);String label=jsonText(requirement,"label");if(label.isEmpty())label=jsonText(requirement,"key");details.add((checked>0?"✓ ":checked<0?"Needed · ":"Check · ")+label);if(checked<0)state=-1;else if(checked==0&&state>0)state=0;}
        else if(guide.requirements!=null&&!guide.requirements.isEmpty())for(String label:guide.requirements)
        {int checked=legacyRequirementState(label,facts);details.add((checked>0?"✓ ":checked<0?"Needed · ":"Check · ")+label);if(checked<0)state=-1;else if(checked==0&&state>0)state=0;}
        List<Map<Integer,Integer>> groups=new ArrayList<>();int unresolved=0;
        if(guide.items!=null)for(JsonObject item:guide.items)
        {
            int quantity=jsonNumber(item,"quantity",0);Set<Integer> ids=GuideItemResolver.resolveAll(item);
            if(quantity<1||quantity>1000000||ids.isEmpty()||ids.size()>256||groups.size()>=28){unresolved++;continue;}
            Map<Integer,Integer> alternatives=new LinkedHashMap<>();for(int id:ids)alternatives.put(id,quantity);groups.add(alternatives);
        }
        if(unresolved>0){details.add(unresolved+" guide supply groups need a choice");if(state>0)state=0;}
        String bank=ownershipSummary(groups,owned);
        return new Access(state,guide.id,bank,details,groups);
    }
    private int legacyRequirementState(String label,GoalCatalog.Facts facts)
    {
        if(facts==null)return 0;
        java.util.regex.Matcher level=java.util.regex.Pattern.compile("^([1-9][0-9]?) ([A-Za-z]+)$").matcher(label);
        if(level.matches()){net.runelite.api.Skill skill=GoalCatalog.skill(level.group(2));if(skill!=null)return facts.level(skill)>=Integer.parseInt(level.group(1))?1:0;}
        if(label.startsWith("Complete ")&&!label.startsWith("Complete or "))
        {
            String title=label.substring(9).replaceFirst(" for repeat kills$", "").replaceFirst(" miniquest$", "");
            String key=GoalCatalog.questKey(title);
            for(net.runelite.api.Quest quest:net.runelite.api.Quest.values())if(GoalCatalog.questKey(quest.getName()).equals(key)||key.equals("deserttreasureii")&&GoalCatalog.questKey(quest.getName()).equals("deserttreasureiithefallenempire"))
            {net.runelite.api.QuestState state=facts.quest(quest.getName());return state==null?0:state==net.runelite.api.QuestState.FINISHED?1:-1;}
        }return 0;
    }
    static int requirementState(JsonObject requirement,GoalCatalog.Facts facts)
    {
        if(requirement==null||facts==null)return 0;
        if(requirement.has("ironmanOnly")&&requirement.get("ironmanOnly").getAsBoolean())return 0;
        if(requirement.has("anyOf")&&requirement.get("anyOf").isJsonArray())
        {boolean unknown=false;for(JsonElement child:requirement.getAsJsonArray("anyOf"))if(child.isJsonObject()){int state=requirementState(child.getAsJsonObject(),facts);if(state>0)return 1;unknown|=state==0;}return unknown?0:-1;}
        String kind=jsonText(requirement,"kind"),key=jsonText(requirement,"key");
        if("skill".equals(kind)){net.runelite.api.Skill skill=GoalCatalog.skill(key);if(skill==null)return 0;Integer level=facts.levels.get(skill);if(level==null)return 0;int needed=jsonNumber(requirement,"value",1);return level>=needed?1:requirement.has("boostable")&&requirement.get("boostable").getAsBoolean()?0:-1;}
        if("quest".equals(kind)){net.runelite.api.QuestState state=facts.quest(key);boolean begun=requirement.has("started")&&requirement.get("started").getAsBoolean();return state==null?0:state==net.runelite.api.QuestState.FINISHED||begun&&state==net.runelite.api.QuestState.IN_PROGRESS?1:-1;}
        if("metric".equals(kind)&&"Combat".equalsIgnoreCase(key))return facts.combat>0?facts.combat>=jsonNumber(requirement,"value",1)?1:-1:0;
        return 0;
    }
    private String ownershipSummary(List<Map<Integer,Integer>> groups,OwnershipLedger.View view)
    {
        if(groups.isEmpty())return "";
        if(view==null||!Objects.equals(view.profile,currentProfile()))return "Arrival supplies · ownership not observed";
        int carried=0;for(Map<Integer,Integer> group:groups){boolean met=false;for(Map.Entry<Integer,Integer> item:group.entrySet()){long count=0;for(OwnershipLedger.Storage storage:List.of(OwnershipLedger.Storage.INVENTORY,OwnershipLedger.Storage.EQUIPMENT)){OwnershipLedger.Observation observed=view.storages.get(storage);if(observed!=null&&observed.currentSession)count+=observed.quantity(item.getKey());}met|=count>=item.getValue();}if(met)carried++;}
        String summary=carried+" / "+groups.size()+" arrival supply groups carried";
        OwnershipLedger.Observation bank=view.storages.get(OwnershipLedger.Storage.BANK);
        if(bank==null)return summary+" · bank not observed";
        long minutes=Math.max(0,(System.currentTimeMillis()-bank.observedAt)/60000);
        return summary+" · bank seen "+(minutes<1?"just now":minutes+"m ago")+(bank.currentSession?"":" · previous session");
    }
    private static String jsonText(JsonObject object,String key){try{return object.has(key)&&object.get(key).isJsonPrimitive()?object.get(key).getAsString():"";}catch(RuntimeException invalid){return "";}}
    private static int jsonNumber(JsonObject object,String key,int fallback){try{return object.has(key)?object.get(key).getAsInt():fallback;}catch(RuntimeException invalid){return fallback;}}
    static List<Map<Integer,Integer>> parseItemRequirements(String text)
    {
        if(text==null||text.trim().isEmpty())return Collections.emptyList();if(text.length()>6000)throw new IllegalArgumentException("Supply list is too long");List<Map<Integer,Integer>> groups=new ArrayList<>();
        for(String line:text.split("[\\r\\n;]+"))
        {
            if(line.trim().isEmpty())continue;if(groups.size()>=28)throw new IllegalArgumentException("At most 28 supply groups");Map<Integer,Integer> group=new LinkedHashMap<>();
            for(String alternative:line.split("\\|"))
            {
                java.util.regex.Matcher match=java.util.regex.Pattern.compile("^(.+?)(?:\\s+[x×]\\s*(\\d+))?$").matcher(alternative.trim());
                if(!match.matches())throw new IllegalArgumentException("Use an item name and quantity");String name=match.group(1).trim();int quantity=match.group(2)==null?1:Integer.parseInt(match.group(2));
                if(quantity<1||quantity>1000000)throw new IllegalArgumentException("Quantity must be 1–1,000,000");Set<Integer> ids;
                if(name.matches("[0-9]{1,6}"))ids=Collections.singleton(Integer.parseInt(name));else ids=GuideItemResolver.resolveName(name);
                if(ids.isEmpty()||ids.contains(0)||ids.size()>256)throw new IllegalArgumentException("Unknown item: "+safe(name));for(int id:ids)group.put(id,quantity);
            }groups.add(group);
        }return TravelNotebook.copyRequirements(groups);
    }
    private String itemRequirementsText(List<Map<Integer,Integer>> groups)
    {StringBuilder result=new StringBuilder();for(Map<Integer,Integer> group:groups){if(result.length()>0)result.append("\n");int index=0;for(Map.Entry<Integer,Integer> item:group.entrySet()){if(index++>0)result.append(" | ");String name=itemNames.apply(item.getKey());result.append(name==null||name.startsWith("Item ")?Integer.toString(item.getKey()):name).append(" x ").append(item.getValue());}}return result.toString();}

    static Object target(WorldPoint point,List<Map<Integer,Integer>> requirements)
    {
        if (requirements==null || requirements.isEmpty()) return point;
        if (requirements.size()>28) throw new IllegalArgumentException("Too many item groups");
        List<Map<Integer,Integer>> groups=new ArrayList<>();
        for (Map<Integer,Integer> group : requirements)
        {
            if (group==null || group.isEmpty() || group.size()>256) throw new IllegalArgumentException("Invalid alternatives");
            Map<Integer,Integer> ids=new LinkedHashMap<>();
            for (Map.Entry<Integer,Integer> entry : group.entrySet())
            {
                if (entry.getKey()==null || entry.getKey()<1 || entry.getKey()>1_000_000 || entry.getValue()==null || entry.getValue()<1 || entry.getValue()>1000000)
                    throw new IllegalArgumentException("Invalid item quantity");
                ids.put(entry.getKey(),entry.getValue());
            }
            groups.add(Collections.unmodifiableMap(ids));
        }
        Map<String,Object> data=new LinkedHashMap<>(); data.put("point",point); data.put("requirements",Collections.unmodifiableList(groups));
        return Collections.unmodifiableMap(data);
    }
    static boolean sameTarget(Object expected,Object actual)
    {
        if (expected==null) return false;
        if(expected instanceof Collection<?> && actual instanceof Collection<?>)return new HashSet<>((Collection<?>)expected).equals(new HashSet<>((Collection<?>)actual));
        if (actual instanceof Collection<?>) { Collection<?> all=(Collection<?>)actual;
            return all.size()==1 && Objects.equals(expected,all.iterator().next()); }
        return Objects.equals(expected,actual);
    }
    static boolean valid(WorldPoint point)
    { return point!=null && point.getX()>0 && point.getY()>0 && point.getX()<16384 && point.getY()<16384 && point.getPlane()>=0 && point.getPlane()<=3; }
    static WorldPoint parseCoordinates(String text)
    {
        if (text==null || text.length()>64) return null; String[] parts=text.trim().split("[ ,]+");
        if (parts.length!=2 && parts.length!=3) return null;
        try { WorldPoint point=new WorldPoint(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),parts.length==3 ? Integer.parseInt(parts[2]) : 0);
            return valid(point) ? point : null; } catch (NumberFormatException ignored) { return null; }
    }
    static String safe(String value)
    { if (value==null || "null".equals(value)) return ""; String text=value.replaceAll("<[^>]*>","").replace('\n',' ').replace('\r',' ');
      return text.substring(0,Math.min(400,text.length())); }

    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column(); panel.setName("travel-planner");
        JComboBox<String> mode=new JComboBox<>(new String[]{"Plan route","Saved destinations","Bank stops","Activity worlds"});travelMode=mode;mode.setFont(GuideText.FONT);panel.add(mode);
        JPanel routeBody=SidebarUi.column();JPanel finder=SidebarUi.section("Travel planner");
        search=new JTextField(); finder.add(SidebarUi.field("Find a guide stop",search)); stops=new JComboBox<>(); finder.add(stops);
        finder.add(SidebarUi.button("Find stops",this::findStops)); search.addActionListener(e -> findStops());
        finder.add(SidebarUi.button("Plan route to stop",() -> { Destination d=(Destination)stops.getSelectedItem();
            if (d!=null) execute(() -> routeGuide(guides==null?null:guides.entry(d.guideId),d.point,d.title,true)); }));
        finder.add(SidebarUi.button("Open guide",() -> { Destination d=(Destination)stops.getSelectedItem(); if (d!=null) openGuide.accept(d.guideId); }));
        routeBody.add(finder); JPanel custom=SidebarUi.section("Custom destination"); coordinates=new JTextField();
        custom.add(SidebarUi.field("X, Y, plane",coordinates)); custom.add(SidebarUi.button("Plan route to coordinates",() -> {
            WorldPoint point=parseCoordinates(coordinates.getText()); execute(() -> preview("Custom destination",point,Collections.emptyList())); }));
        custom.add(SidebarUi.button("Last observed bank",() -> execute(() -> preview("Last observed bank",lastBank,Collections.emptyList()))));
        routeBody.add(custom);panel.add(routeBody);body=SidebarUi.column(); panel.add(body);
        savedBody=SidebarUi.column();panel.add(savedBody);bankBody=SidebarUi.column();panel.add(bankBody);
        JPanel worldCard=SidebarUi.section("Activity worlds"); worldSearch=new JTextField();
        worldCard.add(SidebarUi.field("Activity or world number",worldSearch));
        worldCard.add(SidebarUi.button("Find matching worlds",this::renderWorlds)); worldSearch.addActionListener(e -> renderWorlds());
        worldCard.add(SidebarUi.button("Open world switcher",() -> execute(() -> { if (client.getGameState()==GameState.LOGGED_IN) client.openWorldHopper(); })));
        worldBody=SidebarUi.column(); worldCard.add(worldBody); panel.add(worldCard);
        Runnable updateMode=()->{routeBody.setVisible(mode.getSelectedIndex()==0);savedBody.setVisible(mode.getSelectedIndex()==1);bankBody.setVisible(mode.getSelectedIndex()==2);worldCard.setVisible(mode.getSelectedIndex()==3);panel.revalidate();};mode.addActionListener(event->updateMode.run());updateMode.run();
        renderNotebook();render();renderWorlds();return panel;
    }
    private void refreshNotebook(){if(panel!=null){long generation=epoch;SwingUtilities.invokeLater(()->{if(generation==epoch)renderNotebook();});}}
    private void renderNotebook()
    {
        if(savedBody==null)return;savedBody.removeAll();bankBody.removeAll();
        JPanel saved=SidebarUi.section("My destinations");savedPick=new JComboBox<>(savedDestinations().toArray(new TravelNotebook.Entry[0]));savedPick.setMaximumRowCount(10);saved.add(savedPick);
        saved.add(SidebarUi.button("Plan route to destination",()->{TravelNotebook.Entry entry=(TravelNotebook.Entry)savedPick.getSelectedItem();if(entry!=null)execute(()->previewSaved(entry));}));
        saved.add(SidebarUi.row(SidebarUi.button("Edit",()->{TravelNotebook.Entry entry=(TravelNotebook.Entry)savedPick.getSelectedItem();if(entry!=null)openDestinationEditor(entry,null);}),SidebarUi.button("Remove",()->{TravelNotebook.Entry entry=(TravelNotebook.Entry)savedPick.getSelectedItem();if(entry!=null)execute(()->{if(notebook!=null)notebook.remove(entry.id);refreshNotebook();});})));
        saved.add(SidebarUi.button("New destination",()->openDestinationEditor(null,null)));savedBody.add(saved);
        editor=SidebarUi.section("Destination details");destinationName=new JTextField();destinationCoordinates=new JTextField();destinationItems=new JTextArea(3,14);destinationName.setName("travel.destination.name");destinationCoordinates.setName("travel.destination.point");destinationItems.setName("travel.destination.items");destinationItems.setLineWrap(true);destinationItems.setWrapStyleWord(true);destinationItems.setFont(GuideText.FONT);destinationItems.setBackground(SidebarUi.CARD);destinationItems.setForeground(SidebarUi.TEXT);destinationItems.setCaretColor(SidebarUi.GOLD);destinationItems.setToolTipText("Spade x 1; Coins x 1000. Use | for alternatives.");
        editor.add(SidebarUi.field("Name",destinationName));editor.add(SidebarUi.field("X, Y, plane",destinationCoordinates));editor.add(SidebarUi.field("Arrival supplies",new JScrollPane(destinationItems)));
        destinationGuide=new JCheckBox("<html>Use selected guide<br>access</html>");destinationGuide.setOpaque(false);destinationGuide.setForeground(SidebarUi.TEXT);destinationGuide.setFont(GuideText.FONT);editor.add(destinationGuide);destinationError=SidebarUi.label("");editor.add(destinationError);
        editor.add(SidebarUi.button("Save destination",()->{
            String id=editingDestination,associated=editingGuideId,name=destinationName.getText(),coords=destinationCoordinates.getText(),items=destinationItems.getText();boolean useGuide=destinationGuide.isSelected();
            execute(()->{try{WorldPoint point=parseCoordinates(coords);List<Map<Integer,Integer>> requirements=parseItemRequirements(items);GuideCatalog.Entry context=useGuide?guideContext.get():null;String linked=useGuide?(associated!=null?associated:context==null?null:context.id):null;
                if(useGuide&&linked==null){editorError("Choose a guide before linking its access requirements");return;}
                boolean savedOk=notebook!=null&&notebook.put(id,name,point,linked,requirements);if(savedOk)refreshNotebook();else editorError("Choose a name, valid position and a writable character plan");
            }catch(IllegalArgumentException invalid){editorError(invalid.getMessage());}});
        }));editor.setVisible(false);savedBody.add(editor);
        JPanel banks=SidebarUi.section("Observed bank stops");List<TravelNotebook.Entry> known=bankStops();bankPick=new JComboBox<>(known.toArray(new TravelNotebook.Entry[0]));bankPick.setMaximumRowCount(10);banks.add(bankPick);
        if(known.isEmpty())banks.add(SidebarUi.label("No bank visit observed"));else{TravelNotebook.Entry latest=known.get(0);banks.add(SidebarUi.label("Last observed · "+new java.text.SimpleDateFormat("dd MMM HH:mm").format(new Date(latest.observedAt))));}
        banks.add(SidebarUi.button("Add bank stop",()->{TravelNotebook.Entry entry=(TravelNotebook.Entry)bankPick.getSelectedItem();if(entry!=null)execute(()->{if(itinerary.size()<10){List<TravelNotebook.Entry> next=new ArrayList<>(itinerary);next.add(entry);itinerary=Collections.unmodifiableList(next);refreshNotebook();}});}));
        banks.add(SidebarUi.button("Forget selected bank",()->{TravelNotebook.Entry entry=(TravelNotebook.Entry)bankPick.getSelectedItem();if(entry!=null)execute(()->{if(notebook!=null)notebook.forgetBank(entry.id);List<TravelNotebook.Entry> next=new ArrayList<>(itinerary);next.removeIf(stop->stop.id.equals(entry.id));itinerary=Collections.unmodifiableList(next);refreshNotebook();});}));bankBody.add(banks);
        int number=0;for(TravelNotebook.Entry stop:itinerary){JPanel card=SidebarUi.section("Stop "+(++number));card.add(SidebarUi.label(stop.name));bankBody.add(card);}
        if(!itinerary.isEmpty()){bankBody.add(SidebarUi.button("Plan route to next bank",()->execute(()->{if(onward==null&&plan.point!=null)onward=plan;TravelNotebook.Entry next=itinerary.get(0);preview(next.name,next.point,Collections.emptyList());})));
            bankBody.add(SidebarUi.button("Complete this bank stop",()->execute(()->{if(itinerary.isEmpty())return;itinerary=Collections.unmodifiableList(new ArrayList<>(itinerary.subList(1,itinerary.size())));if(!itinerary.isEmpty()){TravelNotebook.Entry next=itinerary.get(0);preview(next.name,next.point,Collections.emptyList());}else if(onward!=null){Plan destination=onward;onward=null;previewChecked(destination.title,destination.point,destination.access.requirements,destination.access,destination.access.guideId==null||guides==null?null:guides.entry(destination.access.guideId));}refreshNotebook();})));
            bankBody.add(SidebarUi.button("Clear bank stops",()->execute(()->{itinerary=Collections.emptyList();onward=null;refreshNotebook();})));}
        savedBody.revalidate();savedBody.repaint();bankBody.revalidate();bankBody.repaint();
    }
    private void editorError(String message){long generation=epoch;SwingUtilities.invokeLater(()->{if(generation==epoch&&destinationError!=null)destinationError.setText(safe(message));});}
    private void openDestinationEditor(TravelNotebook.Entry entry,Plan source)
    {
        if(editor==null)return;if(travelMode!=null)travelMode.setSelectedIndex(1);editingDestination=entry==null?null:entry.id;editingGuideId=entry!=null?entry.guideId:source==null?null:source.access.guideId;destinationGuide.setText(editingGuideId==null?"<html>Use selected guide<br>access</html>":"<html>Keep linked guide<br>access</html>");destinationName.setText(entry!=null?entry.name:source!=null?source.title:"");WorldPoint point=entry!=null?entry.point:source==null?null:source.point;
        destinationCoordinates.setText(point==null?"":point.getX()+", "+point.getY()+", "+point.getPlane());destinationItems.setText(itemRequirementsText(entry!=null?entry.requirements:source==null?Collections.emptyList():source.access.requirements));destinationGuide.setSelected(entry!=null?entry.guideId!=null:source!=null&&source.access.guideId!=null);destinationError.setText("");editor.setVisible(true);savedBody.setVisible(true);panel.revalidate();
    }
    private boolean previewSaved(TravelNotebook.Entry entry)
    {
        GuideCatalog.Entry guide=entry.guideId==null||guides==null?null:guides.entry(entry.guideId);Access sourced=entry.guideId!=null&&guide==null?new Access(0,entry.guideId,"",List.of("Linked guide access not catalogued"),Collections.emptyList()):guideAccess(guide,accountFacts.get(),ownership.get());
        Access access=new Access(sourced.state,entry.guideId,ownershipSummary(entry.requirements,ownership.get()),sourced.details,entry.requirements);
        return previewChecked(entry.name,entry.point,entry.requirements,access,guide);
    }

    private void findStops()
    {
        stops.removeAllItems(); if (guides==null || search.getText().trim().length()<2) return;
        Set<String> seen=new HashSet<>();
        for (GuideCatalog.Entry guide : guides.search(search.getText().trim(),"")) for (GuideCatalog.DisplayStep step : guide.flattened())
        {
            WorldPoint point=stepDestination(guide,step.step);
            if(point!=null&&stops.getItemCount()<60&&seen.add(guide.id+":"+point))
                stops.addItem(new Destination(guide.title+" · "+safe(step.section),guide.id,point));
        }
    }
    private void refresh()
    { if (panel==null) return; long generation=epoch; SwingUtilities.invokeLater(() -> { if (generation==epoch) render(); }); }
    private void render()
    {
        if (body==null) return; body.removeAll(); Plan current=plan; JPanel card=SidebarUi.section(current.title);
        card.add(SidebarUi.label(current.status));
        if (current.point!=null) card.add(SidebarUi.label(current.point.getX()+", "+current.point.getY()+" · plane "+current.point.getPlane()));
        if (current.answered)
        {
            if (current.cost!=null) card.add(SidebarUi.label("Route cost · "+current.cost));
            if (current.bankVisited) card.add(SidebarUi.label("Bank detour included"));
            if (!current.itemStatus.isEmpty()) card.add(SidebarUi.label(arrivalStatus(current.itemStatus)));
            if (!current.reason.isEmpty()) card.add(SidebarUi.label(current.reason.replace('_',' ')));
            for (String transport : current.transports) card.add(SidebarUi.label("→ "+transport));
        }
        if(!current.access.bankState.isEmpty())card.add(SidebarUi.label(current.access.bankState));
        if(!current.access.requirements.isEmpty())
        {
            JPanel supplies=SidebarUi.column();Set<Integer> required=new LinkedHashSet<>();
            for(Map<Integer,Integer> group:current.access.requirements){List<String> alternatives=new ArrayList<>();for(Map.Entry<Integer,Integer> item:group.entrySet()){required.add(item.getKey());if(alternatives.size()<4)alternatives.add(itemNames.apply(item.getKey())+" × "+item.getValue());}supplies.add(SidebarUi.label(String.join(" or ",alternatives)+(group.size()>4?" · "+(group.size()-4)+" more alternatives":"")));int first=group.keySet().iterator().next();supplies.add(SidebarUi.button("Item sources",()->execute(()->openItem.accept(first))));}
            supplies.add(SidebarUi.button("Find arrival supplies in bank",()->execute(()->bankFocus.accept(Collections.unmodifiableSet(required)))));card.add(SidebarUi.disclosure("Arrival supplies",supplies));
        }
        if(!current.access.details.isEmpty()){JPanel checks=SidebarUi.column();for(String detail:current.access.details)checks.add(SidebarUi.label(detail));card.add(SidebarUi.disclosure("Access and supplies",checks));}
        if(current.access.guideId!=null)card.add(SidebarUi.button("Open preparation guide",()->openGuide.accept(current.access.guideId)));
        if (current.point!=null) card.add(SidebarUi.button("Save this destination",()->openDestinationEditor(null,current)));
        card.add(SidebarUi.button("Clear my route",() -> execute(() -> clearOwned(owner))));
        body.add(card); body.revalidate(); body.repaint();
    }
    private static String arrivalStatus(String status)
    {if("CARRIED".equals(status))return "Required arrival items carried";if("BANK".equals(status)||"WITHDRAW_FROM_BANK".equals(status))return "Withdraw arrival items at the bank";if("UNKNOWN".equals(status))return "Arrival supplies unverified · open your bank";return "Arrival items · "+status.replace('_',' ').toLowerCase(Locale.ROOT);}
    private void renderWorlds()
    {
        if (worldBody==null) return; worldBody.removeAll();
        List<WorldChoice> matches=matchingWorlds(worldSearch.getText());
        if (worlds.isEmpty()) worldBody.add(SidebarUi.label("Open the world switcher to load the current world list"));
        else
        {
            worldBody.add(SidebarUi.label(matches.size()+" matching worlds"));
            for (int i=0;i<Math.min(12,matches.size());i++)
            {
                WorldChoice world=matches.get(i); JPanel row=SidebarUi.column();
                row.add(SidebarUi.button("World "+world.id+" · copy",() -> {
                    try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(Integer.toString(world.id)),null); }
                    catch (IllegalStateException | java.awt.HeadlessException ignored) { }
                }));
                row.add(SidebarUi.label(world.activity.isEmpty() || "-".equals(world.activity) ? "No advertised activity" : world.activity));
                row.add(SidebarUi.label((world.population<0 ? "Offline" : world.population+" players")+" · location "+world.location));
                if (!world.types.isEmpty()) row.add(SidebarUi.label(world.types)); worldBody.add(row);
            }
            if (matches.size()>12) worldBody.add(SidebarUi.label("Refine the activity search to see further matches"));
        }
        worldBody.revalidate(); worldBody.repaint();
    }
}
