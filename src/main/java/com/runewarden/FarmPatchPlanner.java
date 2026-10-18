/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

/** Native patch observations and an explicitly selected, account-scoped farm run. */
@Singleton
public final class FarmPatchPlanner
{
    public static final String KEY="farmHudV1";
    private static final String GROUP="runewarden";
    static final class Seen { int value; long at,growthAt; String compost; long compostAt; }
    static final class HudOptions { boolean enabled=true,compost=true; String style="Rings"; }
    static final class Route
    {
        List<String> stops=new ArrayList<>();
        Set<String> completed=new LinkedHashSet<>(),planted=new LinkedHashSet<>();
        Map<String,String> evidence=new LinkedHashMap<>();
        int seedItemId,quantity,compostItemId=21483;
        boolean active;
        long startedAt;
    }
    static final class Saved
    {
        int version=1; String selected="herb";
        Map<String,Seen> patches=new LinkedHashMap<>();
        Map<String,Route> routes=new LinkedHashMap<>();
        HudOptions hud=new HudOptions();
    }
    public static final class RunType
    {
        public final String id,title,type; public final int iconId;
        RunType(String id,String title,String type,int icon){this.id=id;this.title=title;this.type=type;iconId=icon;}
        @Override public String toString(){return title;}
    }
    public static final class CropChoice
    {
        public final int id; public final String name; public final Integer level,quantity;
        CropChoice(int id,String name,Integer level,Integer quantity){this.id=id;this.name=name;this.level=level;this.quantity=quantity;}
        @Override public String toString(){return name+(level==null?"":" · level "+level);}
    }
    public static final class StopReading
    {
        public final String id,label,status,unlock; public final boolean selected,done; public final WorldPoint destination;
        StopReading(String id,String label,String status,String unlock,boolean selected,boolean done,WorldPoint destination)
        {this.id=id;this.label=label;this.status=status;this.unlock=unlock;this.selected=selected;this.done=done;this.destination=destination;}
    }
    public static final class SupplyReading
    {
        public final int itemId; public final String name,note; public final long required,carried,missing;
        SupplyReading(int id,String name,long required,long carried)
        {this(id,name,required,carried,"");}
        SupplyReading(int id,String name,long required,long carried,String note)
        {itemId=id;this.name=name;this.required=required;this.carried=carried;this.note=note;missing=Math.max(0,required-carried);}
    }
    public static final class RunReading
    {
        public final String profile,runId,title,nextStopId,nextStopLabel,status,routeAdvice;
        public final long epoch,startedAt; public final boolean active;
        public final int totalStops,completedStops,seedItemId,quantity,compostItemId;
        public final List<StopReading> stops; public final List<SupplyReading> supplies;
        public final List<String> preparationNotes;
        RunReading(String profile,long epoch,RunType type,Route route,String next,String label,String status,String advice,
            List<StopReading> stops,List<SupplyReading> supplies)
        {this(profile,epoch,type,route,next,label,status,advice,stops,supplies,List.of());}
        RunReading(String profile,long epoch,RunType type,Route route,String next,String label,String status,String advice,
            List<StopReading> stops,List<SupplyReading> supplies,List<String> notes)
        {
            this.profile=profile;this.epoch=epoch;runId=type.id;title=type.title;nextStopId=next;nextStopLabel=label;
            this.status=status;routeAdvice=advice;startedAt=route.startedAt;active=route.active;
            totalStops=route.stops.size();completedStops=route.completed.size();seedItemId=route.seedItemId;
            quantity=route.quantity;compostItemId=route.compostItemId;
            this.stops=Collections.unmodifiableList(stops);this.supplies=Collections.unmodifiableList(supplies);
            preparationNotes=List.copyOf(notes);
        }
    }
    public static final class PatchReading
    {
        public final FarmPatchCatalog.Patch patch;
        public final FarmPatchCatalog.State state;
        public final long observedAt;
        public final long growthObservedAt;
        public final boolean currentSession;
        public final String observedCompost; public final long compostObservedAt;
        private final FarmGrowthTiming.Window growth;
        PatchReading(FarmPatchCatalog.Patch patch, FarmPatchCatalog.State state, long time, boolean current)
        { this(patch, state, time, time, current); }
        PatchReading(FarmPatchCatalog.Patch patch, FarmPatchCatalog.State state, long time, long growthTime, boolean current)
        { this(patch,state,time,growthTime,current,null,0); }
        PatchReading(FarmPatchCatalog.Patch patch,FarmPatchCatalog.State state,long time,long growthTime,boolean current,String compost,long compostAt)
        { this(patch,state,time,growthTime,current,compost,compostAt,null,null,false); }
        PatchReading(FarmPatchCatalog.Patch patch,FarmPatchCatalog.State state,long time,long growthTime,boolean current,String compost,long compostAt,
            Integer precision,Integer offset,boolean leagues)
        { this.patch=patch;this.state=state;observedAt=time;growthObservedAt=growthTime;currentSession=current;observedCompost=compost;compostObservedAt=compostAt;
            growth=FarmGrowthTiming.predict(state,time,growthTime,precision,offset,leagues); }
        public String status(long now)
        {
            if (state == null) return "Not observed";
            if ("WEEDS".equals(state.crop)) return state.stage == 0 ? "Empty / raked" : "Weeds";
            if ("HARVESTABLE".equals(state.state)) return patch.healthCheck ? "Harvest / health check" : "Harvestable";
            if ("GROWING".equals(state.state) && state.tickMinutes > 0)
            {
                long end = latestGrowthSecond();
                return now >= end ? "Due to check · estimated" : "Growing · " + FarmPatchOverlay.timerLabel(this,now);
            }
            return state.state.substring(0, 1) + state.state.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
        public long latestGrowthSecond()
        { return growth.latest; }
        public long earliestGrowthSecond()
        { return growth.earliest; }
        boolean growthClockKnown(){return growth.clockKnown;}
        long growthCycleStartSecond(){return growth.cycleStart;}
    }
    private static final List<RunType> TYPES=List.of(
        new RunType("herb","Herb run","HERB",5295),new RunType("tree","Tree run","TREE",5370),
        new RunType("fruit","Fruit tree run","FRUIT_TREE",5502),new RunType("hardwood","Hardwood run","HARDWOOD_TREE",21477),
        new RunType("allotment","Allotment run","ALLOTMENT",5318),new RunType("hops","Hops run","HOPS",5305),
        new RunType("bush","Bush run","BUSH",5101),new RunType("seaweed","Seaweed run","SEAWEED",21490),
        new RunType("birdhouse","Birdhouse run","BIRDHOUSE",21512));
    private static final List<CropChoice> HOUSES=List.of(
        new CropChoice(21512,"Bird house",5,1),new CropChoice(21515,"Oak bird house",14,1),
        new CropChoice(21518,"Willow bird house",24,1),new CropChoice(21521,"Teak bird house",34,1),
        new CropChoice(22192,"Maple bird house",44,1),new CropChoice(22195,"Mahogany bird house",49,1),
        new CropChoice(22198,"Yew bird house",59,1),new CropChoice(22201,"Magic bird house",74,1),new CropChoice(22204,"Redwood bird house",89,1));
    // Space (birdhouse), OSRS Wiki revision 14063104; only the four native coordinates are reproduced.
    private static final List<FarmPatchCatalog.Patch> BIRDHOUSES=List.of(
        birdhouse("birdhouse:0","Verdant Valley north-east"),
        birdhouse("birdhouse:1","Verdant Valley south-west"),
        birdhouse("birdhouse:2","Mushroom Meadow north"),
        birdhouse("birdhouse:3","Mushroom Meadow south"));
    private static final int[][] HOUSE_POINTS={{3768,3761},{3763,3755},{3677,3882},{3678,3815}};
    private static final Map<Integer,Integer> HOUSE_LOGS=Map.of(21512,1511,21515,1521,21518,1519,21521,6333,
        22192,1517,22195,6332,22198,1515,22201,1513,22204,19669);
    // Verified bird-house bait: low-tier seeds count one unit; wildblood and Ranarr–Torstol count two.
    private static int birdSeedUnits(int id)
    {
        if(id==5311||id>=5295&&id<=5304)return 2;
        return id>=5305&&id<=5310||id>=5291&&id<=5294||id>=5096&&id<=5106||id>=5318&&id<=5324||id==22887||id==22879?1:0;
    }
    private static final String[] TREE_PROTECTION={
        "5370|1|basket of tomatoes (5)","5371|1|basket of apples (5)","5372|1|basket of oranges (5)",
        "5373|10|cactus spines","5374|25|coconuts","5496|9|sweetcorn","5497|4|baskets of apples (5)",
        "5498|3|baskets of strawberries (5)","5499|5|baskets of bananas (5)","5500|10|watermelons",
        "5501|10|pineapples","5502|15|papayas","22866|15|coconuts"};
    private final Client client; private final ClientThread thread; private final ConfigManager config;
    private final Gson gson; private final FarmPatchCatalog catalog;
    private final FarmRunNavigation automaticRoute=new FarmRunNavigation();
    private HarvestNavigation navigation;
    private volatile boolean started,activeSession;
    private volatile String profile;
    private volatile long epoch;
    private Saved saved=new Saved();
    private final Set<String> current=new LinkedHashSet<>();
    private final Map<String,Long> birdBaseline=new HashMap<>();
    private final Set<String> birdUnseeded=new LinkedHashSet<>();
    private volatile Map<String,PatchReading> readings=Collections.emptyMap();
    private volatile List<CropChoice> choices=Collections.emptyList();
    private volatile RunReading run;
    private Map<String,String> access=Collections.emptyMap();
    private Set<String> localContext=Collections.emptySet();
    private volatile FarmPatchScene.Snapshot scene=FarmPatchScene.Snapshot.EMPTY;
    private volatile HudOptions hudOptions=new HudOptions();
    private PendingCompost pendingCompost;
    private int region=-1,settled,ticks;
    private boolean dirty;
    private String message="";
    private static final class PendingCompost
    { String patch,profile;long epoch,at;FarmPatchScene.Snapshot scene;WorldPoint point;int width,height; }

    @Inject public FarmPatchPlanner(Client client,ClientThread thread,ConfigManager config,Gson gson,FarmPatchCatalog catalog)
    {this.client=client;this.thread=thread;this.config=config;this.gson=gson;this.catalog=catalog;}
    public static List<RunType> runTypes(){return TYPES;}
    public List<CropChoice> cropChoices(){return validProfile()?choices:List.of();}
    public RunReading currentRun(){return validProfile()?run:null;}
    public Map<String,PatchReading> readings(){return validProfile()?readings:Map.of();}
    FarmPatchCatalog.Crop cropReference(String key){return catalog.crop(key);}
    int cropIconId(FarmPatchCatalog.State state){return catalog.iconId(state);}
    FarmPatchScene.Snapshot sceneReading(){return validProfile()?scene:FarmPatchScene.Snapshot.EMPTY;}
    HudOptions hudOptions(){return hudOptions;}
    private boolean validProfile(){return started&&activeSession&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null&&profile!=null&&profile.equals(config.getRSProfileKey());}
    public void setNavigation(HarvestNavigation value)
    {
        navigation=value;
        automaticRoute.setPort(value==null?null:new FarmRunNavigation.Port(){
            public long manualRevision(){return value.manualRevision();}
            public long generation(){return value.generation();}
            public boolean route(WorldPoint point,String title,long revision){return value.routeAutomatic("farm",point,title,revision);}
            public void clear(){value.clearOwned("farm");}
        });
    }
    void setNavigationPort(FarmRunNavigation.Port port){automaticRoute.setPort(port);}
    public void start(){if(!started){started=true;clear();}}
    public void stop(){clear();started=false;}
    public void clear()
    {
        save();automaticRoute.stop();birdBaseline.clear();birdUnseeded.clear();activeSession=false;profile=null;epoch++;saved=new Saved();current.clear();
        readings=Map.of();choices=List.of();run=null;access=Map.of();localContext=Set.of();scene=FarmPatchScene.Snapshot.EMPTY;
        hudOptions=new HudOptions();pendingCompost=null;region=-1;settled=ticks=0;dirty=false;message="";
    }
    public void withView(RunReading expected,Runnable action)
    {
        if(expected==null||action==null)return;
        thread.invokeLater(()->{if(validProfile()&&expected.epoch==epoch&&expected.profile.equals(profile)&&expected.runId.equals(saved.selected))action.run();});
    }
    private void edit(java.util.function.Consumer<Route> action)
    {
        String owner=profile;long generation=epoch;
        thread.invokeLater(()->{if(!validProfile()||generation!=epoch||!Objects.equals(owner,profile))return;
            action.accept(route());dirty=true;publish();save();});
    }
    public void selectRun(String id)
    {
        String normalized=normalize(id);
        edit(route->{if(!saved.selected.equals(normalized)){stopInternal();saved.selected=normalized;epoch++;message="";route();}});
    }
    public void includeStop(String id,boolean include)
    {
        edit(route->{if(route.active||patch(id)==null||!available(type()).stream().anyMatch(p->p.id.equals(id)))return;
            if(include){if(!route.stops.contains(id))route.stops.add(id);}
            else{route.stops.remove(id);route.completed.remove(id);route.planted.remove(id);route.evidence.remove(id);}});
    }
    public void moveStop(String id,int direction)
    {edit(route->{if(route.active)return;int from=route.stops.indexOf(id),to=from+Integer.signum(direction);if(from>=0&&to>=0&&to<route.stops.size())Collections.swap(route.stops,from,to);});}
    public void chooseSeed(int id)
    {edit(route->{if(route.active)return;for(CropChoice choice:cropChoices(type()))if(choice.id==id){route.seedItemId=id;route.quantity=choice.quantity==null?0:choice.quantity;break;}});}
    public void setQuantity(int amount){edit(route->{if(!route.active&&!"birdhouse".equals(saved.selected)&&amount>=1&&amount<=100)route.quantity=amount;});}
    public void setCompost(int id){edit(route->{if(!route.active&&Set.of(0,6032,6034,21483).contains(id))route.compostItemId="birdhouse".equals(saved.selected)?0:id;});}
    public void setHudOptions(boolean enabled,boolean compost,String style)
    {edit(route->{saved.hud.enabled=enabled;saved.hud.compost=compost;if(Set.of("Rings","Text","Hybrid").contains(style))saved.hud.style=style;});}
    public void startSelectedRun()
    {
        edit(route->{
            CropChoice crop=cropChoices(type()).stream().filter(c->c.id==route.seedItemId).findFirst().orElse(null);
            if(route.stops.isEmpty()){message="Select at least one location";return;}
            if(crop==null||route.quantity<1){message="Select a crop and quantity";return;}
            Skill skill="birdhouse".equals(saved.selected)?Skill.HUNTER:Skill.FARMING;
            if(crop.level!=null&&client.getBoostedSkillLevel(skill)<crop.level){message=crop.level+" "+skill.getName()+" required";return;}
            if(route.stops.stream().anyMatch(id->!access.getOrDefault(id,"Unlock not checked").isEmpty())){message="Check the selected location unlocks";return;}
            stopInternal();route.completed.clear();route.planted.clear();route.evidence.clear();route.startedAt=System.currentTimeMillis();
            if("birdhouse".equals(saved.selected))for(HarvestClockTool.BirdHouseReading bird:HarvestClockTool.readBirdHouses(config,client,profile,route.startedAt/1000))
                birdBaseline.put(birdStop(bird.site),bird.observed);
            route.active=true;epoch++;message="";automaticRoute.begin();updateAutomaticRoute();
        });
    }
    public void checkNext(String expectedStop,boolean skip)
    {edit(route->{if(route.active&&Objects.equals(expectedStop,nextStop(route))){route.completed.add(expectedStop);route.evidence.put(expectedStop,skip?"Skipped":"Checked");updateAutomaticRoute();}});}
    public void stopActiveRun(){edit(route->{stopInternal();epoch++;message="Stopped";});}
    private void stopInternal(){automaticRoute.stop();birdBaseline.clear();birdUnseeded.clear();for(Route route:saved.routes.values())route.active=false;}
    static boolean clearsCompost(FarmPatchCatalog.State before,FarmPatchCatalog.State next,long previousObservation,long compostAt)
    {
        if(before==null || next==null || compostAt<=0)return false;
        if("WEEDS".equals(next.crop) || "EMPTY".equals(next.state))return !"WEEDS".equals(before.crop) && !"EMPTY".equals(before.state);
        return "GROWING".equals(next.state) && next.stage==0 && ("DEAD".equals(before.state)||"HARVESTABLE".equals(before.state)||!java.util.Objects.equals(before.crop,next.crop)) && compostAt<=previousObservation;
    }
    public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
    {
        if(!validProfile() || client.getGameState()!=GameState.LOGGED_IN || client.getLocalPlayer()==null)return;
        net.runelite.api.MenuAction action=event.getMenuAction();
        boolean inspect="Inspect".equals(event.getMenuOption()) && List.of(net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION,net.runelite.api.MenuAction.GAME_OBJECT_SECOND_OPTION,net.runelite.api.MenuAction.GAME_OBJECT_THIRD_OPTION,net.runelite.api.MenuAction.GAME_OBJECT_FOURTH_OPTION,net.runelite.api.MenuAction.GAME_OBJECT_FIFTH_OPTION).contains(action);
        boolean use=action==net.runelite.api.MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
        if(!inspect && !use)return;
        if(use)
        {
            net.runelite.api.widgets.Widget selected=client.getSelectedWidget();
            if((selected==null || !Set.of(net.runelite.api.gameval.ItemID.BUCKET_COMPOST,net.runelite.api.gameval.ItemID.BUCKET_SUPERCOMPOST,net.runelite.api.gameval.ItemID.BUCKET_ULTRACOMPOST,net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED).contains(selected.getItemId())) && (selected==null || selected.getId()!=net.runelite.api.gameval.InterfaceID.MagicSpellbook.FERTILE_SOIL))return;
        }
        net.runelite.api.WorldView view=client.getTopLevelWorldView();
        if(view==null || view.isInstance() || event.getMenuEntry()!=null && event.getMenuEntry().getWorldViewId()!=view.getId())return;
        int x=event.getParam0(),y=event.getParam1(),plane=view.getPlane();net.runelite.api.Scene nativeScene=view.getScene();
        if(nativeScene==null || x<0 || y<0)return;
        net.runelite.api.Tile[][][] tiles=nativeScene.getTiles();if(tiles==null || plane<0 || plane>=tiles.length || tiles[plane]==null || x>=tiles[plane].length || tiles[plane][x]==null || y>=tiles[plane][x].length || tiles[plane][x][y]==null)return;
        net.runelite.api.GameObject target=null;net.runelite.api.GameObject[] objects=tiles[plane][x][y].getGameObjects();
        if(objects!=null)for(net.runelite.api.GameObject object:objects)if(object!=null && object.getId()==event.getId() && object.getWorldView()==view){target=object;break;}
        if(target==null)return;net.runelite.api.ObjectComposition definition=client.getObjectDefinition(target.getId());if(definition==null)return;
        WorldPoint point=WorldPoint.fromScene(view,x,y,plane);FarmPatchCatalog.Patch match=null;
        for(FarmPatchCatalog.Patch patch:catalog.patches())if(patch.varbit==definition.getVarbitId() && !patch.type.contains("COMPOST") && patch.sentAt(point)){if(match!=null)return;match=patch;}
        if(match==null)return;
        PendingCompost pending=new PendingCompost();pending.patch=match.id;pending.profile=profile;pending.epoch=epoch;pending.at=System.currentTimeMillis();pending.point=point;pending.width=Math.max(1,target.sizeX());pending.height=Math.max(1,target.sizeY());
        pending.scene=new FarmPatchScene.Snapshot(view,nativeScene,List.of(),view.getBaseX(),view.getBaseY(),plane);pendingCompost=pending;
    }
    public void onChatMessage(net.runelite.api.events.ChatMessage event)
    {
        if(event.getType()!=net.runelite.api.ChatMessageType.GAMEMESSAGE && event.getType()!=net.runelite.api.ChatMessageType.SPAM)return;
        String compost=FarmCompostEvidence.parse(event.getMessage());PendingCompost pending=pendingCompost;long now=System.currentTimeMillis();
        if(compost==null || pending==null)return;pendingCompost=null;
        if(!validProfile() || pending.epoch!=epoch || !pending.profile.equals(profile) || now-pending.at>30_000 || !pending.scene.matches(client.getTopLevelWorldView()) || client.getLocalPlayer()==null)return;
        WorldPoint player=client.getLocalPlayer().getWorldLocation();
        if(player==null || player.getPlane()!=pending.point.getPlane() || player.getX()<pending.point.getX()-1 || player.getX()>pending.point.getX()+pending.width || player.getY()<pending.point.getY()-1 || player.getY()>pending.point.getY()+pending.height)return;
        Seen seen=saved.patches.get(pending.patch);if(seen==null || !current.contains(pending.patch))return;
        seen.compost=compost;seen.compostAt=now;dirty=true;publish();save();
    }
    private static FarmPatchCatalog.Patch birdhouse(String id,String name)
    {FarmPatchCatalog.Patch patch=new FarmPatchCatalog.Patch();patch.id=id;patch.name=name;patch.region="Fossil Island";patch.type="BIRDHOUSE";return patch;}
    public void onGameTick()
    {
        if(!started)return;
        if(client.getGameState()==GameState.LOADING)
        {settled=0;scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;return;}
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null)
        {if(activeSession)clear();return;}
        String owner=config.getRSProfileKey();
        if(owner==null||owner.isBlank()){if(activeSession)clear();return;}
        if(!owner.equals(profile)){clear();profile=owner;load();}
        activeSession=true;ticks++;
        if(ticks%5==1)readAccess();
        WorldPoint location=client.getLocalPlayer().getWorldLocation();
        if(location==null||client.isInInstancedRegion())
        {settled=0;scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;publish();return;}
        if(location.getRegionID()!=region){region=location.getRegionID();settled=0;scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;}
        List<FarmPatchCatalog.Patch> local=new ArrayList<>();Set<String> context=new LinkedHashSet<>();
        for(FarmPatchCatalog.Patch patch:catalog.patches())if(patch.sentAt(location)){local.add(patch);context.add(patch.id);}
        if(!context.equals(localContext)){localContext=context;settled=0;}
        boolean modal=false;
        if(client.getComponentTable()!=null)for(WidgetNode widget:client.getComponentTable())
            if(widget.getModalMode()!=WidgetModalMode.NON_MODAL){modal=true;break;}
        if(modal)settled=0;
        else if(++settled>=3)
        {
            long now=System.currentTimeMillis();
            for(FarmPatchCatalog.Patch patch:local)
            {
                int value=client.getVarbitValue(patch.varbit);if(catalog.state(patch.type,value)==null)continue;
                Seen before=saved.patches.get(patch.id);
                if(before==null||before.value!=value||!current.contains(patch.id)||now-before.at>=300_000)
                {
                    observeReplant(patch,before,value);Seen next=new Seen();next.value=value;next.at=now;
                    next.growthAt=before!=null&&before.value==value&&current.contains(patch.id)?before.growthAt>0?before.growthAt:before.at:now;
                    if(before!=null&&current.contains(patch.id)&&!clearsCompost(catalog.state(patch.type,before.value),catalog.state(patch.type,value),before.at,before.compostAt))
                    {next.compost=before.compost;next.compostAt=before.compostAt;}
                    saved.patches.put(patch.id,next);current.add(patch.id);dirty=true;
                }
            }
        }
        if(ticks%5==1)scene=settled>=3?FarmPatchScene.scan(client,client.getTopLevelWorldView(),location,local):FarmPatchScene.Snapshot.EMPTY;
        completePlanted();if(settled>=3)observeBirdRefill(location);updateAutomaticRoute();publish();
        if(ticks%10==0)save();
    }
    private void readAccess()
    {
        Map<String,String> next=new LinkedHashMap<>();Map<Quest,QuestState> quests=new HashMap<>();
        for(Quest quest:new Quest[]{Quest.BONE_VOYAGE,Quest.MY_ARMS_BIG_ADVENTURE,Quest.MAKING_FRIENDS_WITH_MY_ARM,Quest.SONG_OF_THE_ELVES,
            Quest.PRIEST_IN_PERIL,Quest.MOURNINGS_END_PART_I,Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE})
            try{quests.put(quest,quest.getState(client));}catch(RuntimeException loading){/* No unlock is assumed. */}
        int farming=client.getBoostedSkillLevel(Skill.FARMING);
        List<FarmPatchCatalog.Patch> patches=new ArrayList<>(catalog.patches());patches.addAll(BIRDHOUSES);
        for(FarmPatchCatalog.Patch patch:patches)
        {
            String gate="";Quest quest=null;
            if("Fossil Island".equals(patch.region)||"Seaweed".equals(patch.region))quest=Quest.BONE_VOYAGE;
            else if("Troll Stronghold".equals(patch.region))quest=Quest.MY_ARMS_BIG_ADVENTURE;
            else if("Weiss".equals(patch.region))quest=Quest.MAKING_FRIENDS_WITH_MY_ARM;
            else if("Prifddinas".equals(patch.region))quest=Quest.SONG_OF_THE_ELVES;
            else if("Morytania".equals(patch.region))quest=Quest.PRIEST_IN_PERIL;
            else if("Avium Savannah".equals(patch.region))quest=Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE;
            if(quest!=null&&quests.get(quest)!=QuestState.FINISHED)gate=quests.containsKey(quest)?"Needs "+quest.getName():"Unlock not checked";
            if("Farming Guild".equals(patch.region))
            {
                int level="HERB".equals(patch.type)||"TREE".equals(patch.type)||"HESPORI".equals(patch.type)?65:
                    "FRUIT_TREE".equals(patch.type)||"CELASTRUS".equals(patch.type)||"REDWOOD".equals(patch.type)?85:45;
                if(farming<level)gate=level+" Farming required";
            }
            if(("Harmony".equals(patch.region)||"Harmony Island".equals(patch.region))&&"HERB".equals(patch.type)&&client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE)==0)gate="Needs elite Morytania diary";
            if("Lletya".equals(patch.region)&&quests.get(Quest.MOURNINGS_END_PART_I)!=QuestState.IN_PROGRESS&&quests.get(Quest.MOURNINGS_END_PART_I)!=QuestState.FINISHED)gate="Start Mourning's End Part I";
            if(Set.of("Auburnvale","Kastori","Anglers' Retreat").contains(patch.region)&&!saved.patches.containsKey(patch.id))gate="Access not checked";
            next.put(patch.id,gate);
        }
        access=next;
    }
    private void observeReplant(FarmPatchCatalog.Patch patch,Seen before,int value)
    {
        if(before==null||!current.contains(patch.id))return;
        FarmPatchCatalog.State previous=catalog.state(patch.type,before.value),next=catalog.state(patch.type,value);
        if(previous==null||next==null||!"GROWING".equals(next.state)||next.stage!=0||"GROWING".equals(previous.state)&&Objects.equals(previous.crop,next.crop)&&previous.stage==0)return;
        for(Route route:saved.routes.values())
        {
            FarmPatchCatalog.Seed seed=catalog.seed(route.seedItemId);
            if(route.active&&route.stops.contains(patch.id)&&!route.completed.contains(patch.id)&&seed!=null&&Objects.equals(seed.cropKey,next.crop))
            {route.planted.add(patch.id);route.evidence.put(patch.id,"Replant observed · compost unchecked");dirty=true;}
        }
    }
    private void completePlanted()
    {
        for(Route route:saved.routes.values())if(route.active)for(String id:route.planted)
        {
            Seen seen=saved.patches.get(id);
            if(!route.completed.contains(id)&&(route.compostItemId==0||seen!=null&&seen.compostAt>=route.startedAt&&compostRank(seen.compost)>=compostRank(route.compostItemId)))
            {route.completed.add(id);route.evidence.put(id,"Replant observed");dirty=true;}
        }
    }
    private static String birdStop(HarvestClockTool.BirdHouseSite site)
    {
        for(int i=0;i<HOUSE_POINTS.length;i++)if(site.point.getX()==HOUSE_POINTS[i][0]&&site.point.getY()==HOUSE_POINTS[i][1])return "birdhouse:"+i;
        return "";
    }
    private void observeBirdRefill(WorldPoint location)
    {
        Route route=route();net.runelite.api.WorldView view=client.getTopLevelWorldView();
        if(!"birdhouse".equals(saved.selected)||!route.active||view==null||view.isInstance()||view.getPlane()!=0||location.getPlane()!=0)return;
        long now=System.currentTimeMillis()/1000;
        Map<String,HarvestClockTool.BirdHouseReading> records=new HashMap<>();
        for(HarvestClockTool.BirdHouseReading bird:HarvestClockTool.readBirdHouses(config,client,profile,now))records.put(birdStop(bird.site),bird);
        for(HarvestClockTool.BirdHouseSite site:HarvestClockTool.BIRD_SITES)
        {
            String id=birdStop(site);if(!route.stops.contains(id)||route.completed.contains(id)||location.distanceTo(site.point)>8)continue;
            int state=client.getVarpValue(site.varp);if(state<0||state>27)continue;
            HarvestClockTool.BirdHouseReading record=records.get(id);
            if(state==0||state%3!=0)
            {birdUnseeded.add(id);birdBaseline.putIfAbsent(id,record==null?Math.max(0,route.startedAt/1000-1):record.observed);continue;}
            if(record!=null)birdBaseline.putIfAbsent(id,record.observed);
            if(!birdUnseeded.contains(id)||record==null||record.itemId()!=route.seedItemId||record.observed<route.startedAt/1000||record.observed<birdBaseline.getOrDefault(id,-1L))continue;
            route.completed.add(id);route.evidence.put(id,"Refill observed");birdUnseeded.remove(id);dirty=true;
        }
    }
    private static int compostRank(int id){return id==21483?3:id==6034?2:id==6032?1:0;}
    private static int compostRank(String name){return "Ultracompost".equalsIgnoreCase(name)?3:"Supercompost".equalsIgnoreCase(name)?2:"Compost".equalsIgnoreCase(name)?1:0;}
    private void updateAutomaticRoute()
    {
        if(!validProfile())return;Route route=route();if(!route.active)return;
        String next=nextStop(route);
        if(next==null){route.active=false;automaticRoute.stop();message="Run complete";dirty=true;return;}
        FarmPatchCatalog.Patch patch=patch(next);
        automaticRoute.update(next,destination(patch),patch==null?"Farm run":patch.label(),ticks);
    }
    private WorldPoint destination(FarmPatchCatalog.Patch patch)
    {
        if(patch==null)return null;
        if("BIRDHOUSE".equals(patch.type)){int i=Integer.parseInt(patch.id.substring(10));return new WorldPoint(HOUSE_POINTS[i][0],HOUSE_POINTS[i][1],0);}
        if(scene.matches(client.getTopLevelWorldView()))for(FarmPatchScene.Anchor anchor:scene.anchors)
            if(patch.id.equals(anchor.patchId)){WorldPoint point=anchor.object.getWorldLocation();if(point!=null&&patch.sentAt(point))return point;}
        return FarmPatchDestinations.forPatch(patch);
    }
    private RunType type(){return TYPES.stream().filter(t->t.id.equals(saved.selected)).findFirst().orElse(TYPES.get(0));}
    private static String normalize(String id)
    {
        if(id==null)return "herb";
        String word=id.toLowerCase(java.util.Locale.ROOT);
        if(word.contains("bird"))return "birdhouse";if(word.contains("hardwood"))return "hardwood";
        if(word.contains("fruit"))return "fruit";if(word.contains("tree"))return "tree";
        for(RunType type:TYPES)if(word.contains(type.id))return type.id;
        return "herb";
    }
    private List<FarmPatchCatalog.Patch> available(RunType type)
    {
        if("birdhouse".equals(type.id))return BIRDHOUSES;
        List<FarmPatchCatalog.Patch> result=new ArrayList<>();
        for(FarmPatchCatalog.Patch patch:catalog.forGuide(type.id))if(type.type.equals(patch.type))result.add(patch);
        return result;
    }
    private FarmPatchCatalog.Patch patch(String id)
    {if(id==null)return null;for(FarmPatchCatalog.Patch house:BIRDHOUSES)if(house.id.equals(id))return house;return catalog.patch(id);}
    private List<CropChoice> cropChoices(RunType type)
    {
        if("birdhouse".equals(type.id))return HOUSES;
        List<CropChoice> result=new ArrayList<>();for(FarmPatchCatalog.Seed seed:catalog.seeds(type.type))result.add(new CropChoice(seed.id,seed.name,seed.level,seed.quantity));
        return result;
    }
    private Route route()
    {
        Route route=saved.routes.get(saved.selected);if(route!=null)
        {if("birdhouse".equals(saved.selected)&&route.quantity!=1){route.quantity=1;dirty=true;}return route;}
        route=new Route();for(FarmPatchCatalog.Patch patch:available(type()))if(access.getOrDefault(patch.id,"Unlock not checked").isEmpty())route.stops.add(patch.id);
        List<CropChoice> seeds=cropChoices(type());if(!seeds.isEmpty()){route.seedItemId=seeds.get(0).id;route.quantity=seeds.get(0).quantity==null?0:seeds.get(0).quantity;}
        if("birdhouse".equals(saved.selected))route.compostItemId=0;
        saved.routes.put(saved.selected,route);dirty=true;return route;
    }
    private static String nextStop(Route route)
    {for(String id:route.stops)if(!route.completed.contains(id))return id;return null;}
    private void publish()
    {
        if(!validProfile())return;
        Integer precision=config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION,int.class);
        Integer offset=config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,TimeTrackingConfig.FARM_TICK_OFFSET,int.class);
        Set<net.runelite.api.WorldType> worlds=client.getWorldType();
        boolean leagues=worlds!=null&&worlds.contains(net.runelite.api.WorldType.SEASONAL)&&!worlds.contains(net.runelite.api.WorldType.DEADMAN);
        Map<String,PatchReading> observed=new LinkedHashMap<>();
        for(Map.Entry<String,Seen> entry:saved.patches.entrySet())
        {
            FarmPatchCatalog.Patch patch=catalog.patch(entry.getKey());Seen seen=entry.getValue();
            if(patch!=null)observed.put(patch.id,new PatchReading(patch,catalog.state(patch.type,seen.value),seen.at,seen.growthAt>0?seen.growthAt:seen.at,
                current.contains(patch.id),seen.compost,seen.compostAt,precision,offset,leagues));
        }
        readings=Collections.unmodifiableMap(observed);
        HudOptions options=new HudOptions();options.enabled=saved.hud.enabled;options.compost=saved.hud.compost;options.style=saved.hud.style;hudOptions=options;
        RunType type=type();choices=Collections.unmodifiableList(cropChoices(type));Route route=route();
        List<StopReading> stops=new ArrayList<>();Map<String,FarmPatchCatalog.Patch> remaining=new LinkedHashMap<>();
        for(FarmPatchCatalog.Patch patch:available(type))remaining.put(patch.id,patch);
        for(String id:route.stops){FarmPatchCatalog.Patch patch=remaining.remove(id);if(patch!=null)stops.add(stopReading(patch,route,true));}
        for(FarmPatchCatalog.Patch patch:remaining.values())stops.add(stopReading(patch,route,false));
        Map<Integer,Long> carried=new LinkedHashMap<>();
        for(InventoryID container:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT})
        {
            ItemContainer inventory=client.getItemContainer(container);if(inventory==null||inventory.getItems()==null)continue;
            for(Item item:inventory.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)carried.merge(item.getId(),(long)item.getQuantity(),Long::sum);
        }
        Map<Integer,Long> needed=new LinkedHashMap<>();List<String> preparation=new ArrayList<>();int count=route.stops.size()-route.completed.size();
        if(count>0)
        {
            if("birdhouse".equals(type.id))
            {
                long prebuilt=Math.min(count,carried.getOrDefault(route.seedItemId,0L));int log=HOUSE_LOGS.getOrDefault(route.seedItemId,1511);
                if(prebuilt>0)needed.put(route.seedItemId,prebuilt);
                if(prebuilt<count)
                {needed.put(log,count-prebuilt);needed.put(1755,1L);needed.put(carried.getOrDefault(25644,0L)>0?25644:2347,1L);}
                preparation.add("Bring "+count+" prebuilt houses, or one matching log per remaining stop with a hammer and chisel. Mixed supplies work.");
                preparation.add("Collection returns the clockwork. New empty spaces need a clockwork each; leave room for it and nests.");
            }
            else
            {
                if(route.seedItemId>0&&route.quantity>0)needed.put(route.seedItemId,(long)route.quantity*count);
                needed.put(952,1L);if(!type.type.contains("TREE"))needed.put(5343,1L);
                if(route.compostItemId>0)needed.put(route.compostItemId,(long)count);
                if("tree".equals(type.id)||"fruit".equals(type.id))
                {
                    preparation.add("Ready saplings: one per patch. No watering can needed for planting; check health and clear the old tree first.");
                    for(String payment:TREE_PROTECTION)
                    {
                        String[] parts=payment.split("\\|");if(Integer.parseInt(parts[0])!=route.seedItemId)continue;
                        int per=Integer.parseInt(parts[1]);preparation.add("Optional protection: "+per+" "+parts[2]+" per patch ("+(long)per*count+" for this run). Noted payments accepted; baskets must be full.");break;
                    }
                }
            }
        }
        List<SupplyReading> supplies=new ArrayList<>();
        needed.forEach((id,amount)->{String name="Item "+id;net.runelite.api.ItemComposition item=client.getItemDefinition(id);
            if(item!=null&&item.getName()!=null)name=item.getName();supplies.add(new SupplyReading(id,name,amount,carried.getOrDefault(id,0L)));});
        if(count>0&&"birdhouse".equals(type.id))
        {
            long units=0;int icon=5305;for(Map.Entry<Integer,Long> item:carried.entrySet())
            {int weight=birdSeedUnits(item.getKey());if(weight>0){if(units==0)icon=item.getKey();units+=item.getValue()*weight;}}
            supplies.add(new SupplyReading(icon,"Bird-house seed units",10L*count,units,"10 units per house: most accepted seeds = 1; wildblood and Ranarr–Torstol = 2."));
        }
        String next=nextStop(route);FarmPatchCatalog.Patch nextPatch=patch(next);String label=nextPatch==null?"":nextPatch.label();
        String advice=navigation==null?"":navigation.routeAdvice("farm").recommendation(profile,destination(nextPatch));
        String status=!message.isEmpty()?message:route.active?"Follow the next location":route.completed.size()>0&&next==null?"Run complete":"Ready to prepare";
        run=new RunReading(profile,epoch,type,route,next,label,status,advice,stops,supplies,preparation);
    }
    private StopReading stopReading(FarmPatchCatalog.Patch patch,Route route,boolean selected)
    {
        String status=route.evidence.get(patch.id);PatchReading observed=readings.get(patch.id);
        if(status==null)status=observed==null?"BIRDHOUSE".equals(patch.type)?"Check / skip after refilling":"Not observed":observed.status(System.currentTimeMillis()/1000);
        return new StopReading(patch.id,patch.label(),status,access.getOrDefault(patch.id,"Unlock not checked"),selected,route.completed.contains(patch.id),destination(patch));
    }
    private void save()
    {
        if(!dirty||profile==null||!profile.equals(config.getRSProfileKey()))return;
        config.setConfiguration(GROUP,profile,KEY,gson.toJson(saved));dirty=false;
    }
    private void load()
    {
        String raw=config.getConfiguration(GROUP,profile,KEY);
        if(raw==null||raw.isBlank())return;
        try
        {
            if(raw.length()>250_000)throw new IllegalArgumentException("Farm data too large");
            Saved value=gson.fromJson(raw,Saved.class);if(!validSaved(value))throw new IllegalArgumentException("Invalid farm data");
            saved=value;for(Route route:saved.routes.values())if(route.active){route.active=false;dirty=true;}
        }
        catch(RuntimeException invalid){saved=new Saved();message="Saved farm setup could not be read";}
    }
    private boolean validSaved(Saved value)
    {
        if(value==null||value.version!=1||value.selected==null||!normalize(value.selected).equals(value.selected)||value.hud==null||
            value.hud.style==null||!Set.of("Rings","Text","Hybrid").contains(value.hud.style)||value.patches==null||value.patches.size()>catalog.patches().size()||value.routes==null||value.routes.size()>TYPES.size())return false;
        for(Map.Entry<String,Seen> entry:value.patches.entrySet())
        {
            FarmPatchCatalog.Patch patch=catalog.patch(entry.getKey());Seen seen=entry.getValue();
            if(patch==null||seen==null||catalog.state(patch.type,seen.value)==null||seen.at<0||seen.growthAt<0||seen.compostAt<0||seen.compost!=null&&!FarmCompostEvidence.valid(seen.compost))return false;
        }
        for(Map.Entry<String,Route> entry:value.routes.entrySet())
        {
            RunType type=TYPES.stream().filter(t->t.id.equals(entry.getKey())).findFirst().orElse(null);Route route=entry.getValue();
            if(type==null||route==null||route.stops==null||route.stops.size()>40||route.completed==null||route.planted==null||route.evidence==null||
                route.quantity<0||route.quantity>100||!Set.of(0,6032,6034,21483).contains(route.compostItemId)||route.startedAt<0)return false;
            Set<String> ids=new LinkedHashSet<>();for(FarmPatchCatalog.Patch patch:available(type))ids.add(patch.id);
            if(!ids.containsAll(route.stops)||new LinkedHashSet<>(route.stops).size()!=route.stops.size()||!route.stops.containsAll(route.completed)||
                !route.stops.containsAll(route.planted)||!route.stops.containsAll(route.evidence.keySet()))return false;
            for(String evidence:route.evidence.values())if(!Set.of("Checked","Skipped","Replant observed","Replant observed · compost unchecked","Refill observed").contains(evidence))return false;
            if(route.seedItemId!=0&&cropChoices(type).stream().noneMatch(c->c.id==route.seedItemId))return false;
            if("birdhouse".equals(type.id)&&route.compostItemId!=0)return false;
        }
        return true;
    }
}
