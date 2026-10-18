/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;

/** Patch-specific native observations, custom run order and reconciled seed/compost preparation. */
@Singleton
public final class FarmPatchPlanner
{
    public static final String KEY = "farmingPatchPlansV1";
    static final class Seen { int value; long at, growthAt; String compost; long compostAt; }
    static final class HudOptions { boolean enabled=true, compost=true; String style="Rings"; }
    static final class Route
    {
        List<String> stops = new ArrayList<>();
        Map<String, Integer> seeds = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        Set<String> protection = new LinkedHashSet<>();
        boolean compost = true;
        Map<String, Integer> patchSeeds = new LinkedHashMap<>();
        Map<String, Integer> patchCompost = new LinkedHashMap<>();
        Map<String, Boolean> patchProtection = new LinkedHashMap<>();
        Map<String, Integer> yield = new LinkedHashMap<>();
        Map<String, String> evidence = new LinkedHashMap<>();
        Map<String, int[]> destinations = new LinkedHashMap<>();
        Map<String, FarmTravelPlan.Stop> travel = new LinkedHashMap<>();
        long startedAt;
        boolean active, autoCheck = true;
    }
    static final class Saved
    { int version = 1; Map<String, Seen> patches = new LinkedHashMap<>(); Map<String, Route> routes = new LinkedHashMap<>(); HudOptions hud=new HudOptions(); }
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
    public static final class SupplyReading
    {
        public final int itemId;
        public final long required, carried, bank, seedVault, missing;
        public final boolean bankObserved, seedVaultObserved;
        SupplyReading(int id, long required, long carried, long bank, long vault, boolean bankObserved, boolean vaultObserved)
        { itemId = id; this.required = required; this.carried = carried; this.bank = bank; seedVault = vault;
            this.bankObserved = bankObserved; seedVaultObserved = vaultObserved; missing = Math.max(0, required - carried - bank - vault); }
    }
    /** Published data only: consumers never read native Client state or mutable saved routes. */
    public static final class RunReading
    {
        public final String profile, guideId, title, nextStopId, nextStopLabel;
        public final boolean active;
        public final long startedAt;
        public final int totalStops, completedStops, remainingStops, autoObservedStops, confirmedReady, estimatedDue, missingXpStops;
        public final Map<Integer, Long> requiredItems;
        public final List<SupplyReading> supplies;
        public final double knownBaseXp, scenarioHarvestXp;
        public final Long inputCost, harvestRevenue;
        public final int unpricedInputs, unratedHarvests;
        public final long travelCoins; public final int missingTravelCharges, uncheckedTravelCharges, inaccessibleStops;
        RunReading(String profile, String guide, Route route, String next, String nextLabel, int ready, int due, Map<Integer, Long> items,
            List<SupplyReading> supplies, double base, double harvest, int missingXp, Long cost, Long revenue, int unpriced, int unrated, FarmTravelPlan.Summary travel, int inaccessible)
        {
            this.profile = profile; guideId = guide; title = guide == null ? "Farm run" : guide.replace("training:", "").replace('-', ' ');
            active = route.active; startedAt = route.startedAt; totalStops = route.stops.size(); completedStops = route.completed.size(); remainingStops = totalStops - completedStops;
            autoObservedStops = (int) route.evidence.values().stream().filter("Native replant observed"::equals).count(); nextStopId = next; nextStopLabel = nextLabel;
            confirmedReady = ready; estimatedDue = due; requiredItems = Collections.unmodifiableMap(new LinkedHashMap<>(items)); this.supplies = Collections.unmodifiableList(supplies);
            knownBaseXp = base; scenarioHarvestXp = harvest; missingXpStops = missingXp; inputCost = cost; harvestRevenue = revenue; unpricedInputs = unpriced; unratedHarvests = unrated; travelCoins = travel.coins; missingTravelCharges = travel.chargeShortfalls; uncheckedTravelCharges = travel.uncheckedCharges; inaccessibleStops = inaccessible;
        }
    }
    private final Client client;
    private final ClientThread thread;
    private final ConfigManager config;
    private final RunewardenDataStore store;
    private final FarmPatchCatalog catalog;
    private final OwnershipLedger ledger;
    private volatile String profile, guideId;
    private volatile long epoch;
    private volatile boolean started;
    private volatile boolean activeSession;
    private Saved saved = new Saved();
    private final Set<String> current = new LinkedHashSet<>();
    private volatile Map<String, PatchReading> readings = Collections.emptyMap();
    private volatile Map<String, String> access = Collections.emptyMap();
    private volatile Map<String, Route> routes = Collections.emptyMap();
    private volatile WorldPoint observedLocation;
    private volatile Map<String,WorldPoint> observedScenePoints=Collections.emptyMap();
    private volatile FarmTravelPlan.RouteAdvice travelAdvice=FarmTravelPlan.RouteAdvice.EMPTY;
    private volatile Map<Integer, Integer> notedVariants = Collections.emptyMap();
    private volatile Set<String> diaryProtection = Collections.emptySet();
    private volatile int farmingLevel, boostedFarmingLevel;
    private int region = -1, settled, ticks;
    private String addType = "All patch types";
    private int addPage, routePage;
    private final Map<String, Boolean> expanded = new HashMap<>();
    private Set<String> localContext = Collections.emptySet();
    private boolean dirty;
    private JPanel panel;
    private final AtomicBoolean queued = new AtomicBoolean();
    private Consumer<Set<Integer>> bankFocus = ignored -> {};
    private Consumer<WorldPoint> routeAction = ignored -> {};
    private GuideNavigation navigation;
    private Runnable clearRouteAction=()->{};
    private final FarmRunNavigation automaticRoute=new FarmRunNavigation();
    private Consumer<String> guideAction = ignored -> {};
    private Consumer<RunReading> startAction = ignored -> {};
    private Runnable loadoutAction = () -> {};
    private volatile ItemManager itemManager;
    private volatile Map<Integer, Long> prices = Collections.emptyMap();
    private volatile String status = "";
    private volatile FarmPatchScene.Snapshot scene=FarmPatchScene.Snapshot.EMPTY;
    private volatile HudOptions hudOptions=new HudOptions();
    private PendingCompost pendingCompost;
    private static final class PendingCompost
    {
        String patch,profile; long epoch,at; FarmPatchScene.Snapshot scene; WorldPoint point; int width,height;
    }

    @Inject public FarmPatchPlanner(Client client, ClientThread thread, ConfigManager config, RunewardenDataStore store,
        FarmPatchCatalog catalog, OwnershipLedger ledger)
    { this.client = client; this.thread = thread; this.config = config; this.store = store; this.catalog = catalog; this.ledger = ledger; }
    public void setBankFocusAction(Consumer<Set<Integer>> action) { bankFocus = action == null ? ignored -> {} : action; }
    public void setRouteAction(Consumer<WorldPoint> action)
    {
        routeAction=action==null?ignored->{}:action;
        if(navigation==null)automaticRoute.setPort(action==null?null:new FarmRunNavigation.Port(){
            public long manualRevision(){return 0;}
            public boolean route(WorldPoint point,String title,long revision){routeAction.accept(point);return true;}
            public void clear(){clearRouteAction.run();}
        });
    }
    public void setClearRouteAction(Runnable action){clearRouteAction=action==null?()->{}:action;}
    void setNavigation(GuideNavigation value)
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
    private Runnable finishAction=()->{};
    public void setFinishAction(Runnable action){finishAction=action==null?()->{}:action;}
    public void setGuideAction(Consumer<String> action) { guideAction = action == null ? ignored -> {} : action; }
    public void setStartAction(Consumer<RunReading> action) { startAction = action == null ? ignored -> {} : action; }
    public void setLoadoutAction(Runnable action) { loadoutAction = action == null ? () -> {} : action; }
    void setItemManager(ItemManager items) { itemManager = items; }
    public void start() { started = true; clear(); }
    public void stop() { save(); started = false; clear(); }
    public void clear()
    { automaticRoute.stop();save(); activeSession = false; profile = null; epoch++; saved = new Saved(); current.clear(); readings = Collections.emptyMap(); routes = Collections.emptyMap();observedLocation=null;observedScenePoints=Collections.emptyMap();travelAdvice=FarmTravelPlan.RouteAdvice.EMPTY; access = Collections.emptyMap(); notedVariants = Collections.emptyMap(); diaryProtection = Collections.emptySet(); prices = Collections.emptyMap(); farmingLevel = boostedFarmingLevel = 0; region = -1; settled = ticks = 0; localContext = Collections.emptySet(); dirty = false; scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;hudOptions=new HudOptions();refresh(); }
    /** Shared End run uses this too; old queued start/next-stop buttons lose their generation. */
    public void stopActiveRun()
    {
        automaticRoute.stop();epoch++;
        for(Route route:saved.routes.values())if(route.active){route.active=false;dirty=true;}
        publish();save();refresh();
    }
    public void selectGuide(String id) { if (!java.util.Objects.equals(id, guideId)) { addPage = routePage = 0; } guideId = id; refresh(); }
    public Map<String, PatchReading> readings() { return validProfile() ? readings : Collections.emptyMap(); }
    FarmPatchCatalog.Crop cropReference(String key){return catalog.crop(key);}
    int cropIconId(FarmPatchCatalog.State state){return catalog.iconId(state);}
    private boolean validProfile()
    { return started && activeSession && profile != null && profile.equals(config.getRSProfileKey()); }
    FarmPatchScene.Snapshot sceneReading() { return validProfile()?scene:FarmPatchScene.Snapshot.EMPTY; }
    HudOptions hudOptions() { return hudOptions; }
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
        seen.compost=compost;seen.compostAt=now;dirty=true;publish();save();refresh();
    }
    public void onGameTick()
    {
        if (!started || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { automaticRoute.stop();activeSession = false;observedLocation=null;observedScenePoints=Collections.emptyMap(); settled = 0; region = -1; scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;return; }
        String owner = config.getRSProfileKey(); if (owner == null) return;
        if (!owner.equals(profile)) { clear(); profile = owner; saved = store.read(RunewardenConfig.GROUP, profile, KEY, 1, Saved.class, this::validSaved, Saved::new); for (Route route : saved.routes.values()) if (route.active) { route.active = false; dirty = true; } publish(); }
        activeSession = true;
        WorldPoint location = client.getLocalPlayer().getWorldLocation();
        observedLocation=location;
        if (location == null || client.isInInstancedRegion()) { settled = 0;scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null;readTravelAdvice();if(panel!=null&&panel.isShowing())refresh(); return; }
        if (location.getRegionID() != region) { region = location.getRegionID(); settled = 0;scene=FarmPatchScene.Snapshot.EMPTY;pendingCompost=null; }
        List<FarmPatchCatalog.Patch> local = new ArrayList<>(); Set<String> context = new LinkedHashSet<>();
        for (FarmPatchCatalog.Patch patch : catalog.patches()) if (patch.sentAt(location)) { local.add(patch); context.add(patch.id); }
        if (!context.equals(localContext)) { localContext = context; settled = 0; }
        boolean modal = false;
        if (client.getComponentTable() != null)
            for (WidgetNode widget : client.getComponentTable()) if (widget.getModalMode() != WidgetModalMode.NON_MODAL) { modal = true; break; }
        if (modal) settled = 0;
        else if (++settled >= 3)
        {
            long now = System.currentTimeMillis();
            for (FarmPatchCatalog.Patch patch : local)
            {
                int value = client.getVarbitValue(patch.varbit); if (catalog.state(patch.type, value) == null) continue;
                Seen before = saved.patches.get(patch.id);
                if (before == null || before.value != value || !current.contains(patch.id) || now - before.at >= 300_000)
                { observeReplant(patch, before, value); Seen next = new Seen(); next.value = value; next.at = now;
                    next.growthAt = before != null && before.value == value && current.contains(patch.id) ? before.growthAt > 0 ? before.growthAt : before.at : now;
                    if(before!=null && current.contains(patch.id) && !clearsCompost(catalog.state(patch.type,before.value),catalog.state(patch.type,value),before.at,before.compostAt)) { next.compost=before.compost;next.compostAt=before.compostAt; }
                    saved.patches.put(patch.id, next); current.add(patch.id); dirty = true; }
            }
            observeDestinations(local);
        }
        if (++ticks % 5 == 1) { readAccess(); readPrices(); publish(); scene=settled>=3?FarmPatchScene.scan(client,client.getTopLevelWorldView(),location,local):FarmPatchScene.Snapshot.EMPTY;
            Map<String,WorldPoint> points=new LinkedHashMap<>();for(FarmPatchScene.Anchor anchor:scene.anchors){WorldPoint point=anchor.object.getWorldLocation();if(point!=null)points.put(anchor.patchId,point);}observedScenePoints=Collections.unmodifiableMap(points);
            updateAutomaticRoute(); if (panel != null && panel.isShowing()) refresh(); }
        if (ticks % 10 == 0) save();
    }
    private void readAccess()
    {
        Map<String, String> next = new LinkedHashMap<>(); Map<Quest, QuestState> quests = new HashMap<>();
        for (Quest quest : new Quest[]{Quest.BONE_VOYAGE, Quest.MY_ARMS_BIG_ADVENTURE, Quest.MAKING_FRIENDS_WITH_MY_ARM, Quest.SONG_OF_THE_ELVES, Quest.PRIEST_IN_PERIL, Quest.MOURNINGS_END_PART_I, Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE})
            try { quests.put(quest, quest.getState(client)); } catch (RuntimeException loading) { }
        int farming = client.getBoostedSkillLevel(Skill.FARMING);
        farmingLevel = client.getRealSkillLevel(Skill.FARMING); boostedFarmingLevel = farming;
        Set<String> free = new LinkedHashSet<>();
        boolean faladorElite = client.getVarbitValue(VarbitID.FALADOR_DIARY_ELITE_COMPLETE) != 0;
        boolean kourendEasy = client.getVarbitValue(VarbitID.KOUREND_DIARY_EASY_COMPLETE) != 0;
        for (FarmPatchCatalog.Patch patch : catalog.patches())
        {
            String gate = ""; Quest quest = null;
            if ("Fossil Island".equals(patch.region) || "Seaweed".equals(patch.region)) quest = Quest.BONE_VOYAGE;
            else if ("Troll Stronghold".equals(patch.region)) quest = Quest.MY_ARMS_BIG_ADVENTURE;
            else if ("Weiss".equals(patch.region)) quest = Quest.MAKING_FRIENDS_WITH_MY_ARM;
            else if ("Prifddinas".equals(patch.region)) quest = Quest.SONG_OF_THE_ELVES;
            else if ("Morytania".equals(patch.region)) quest = Quest.PRIEST_IN_PERIL;
            else if ("Avium Savannah".equals(patch.region)) quest = Quest.THE_RIBBITING_TALE_OF_A_LILY_PAD_LABOUR_DISPUTE;
            if (quest != null && quests.get(quest) != QuestState.FINISHED) gate = quests.containsKey(quest) ? "Needs " + quest.getName() : "Unlock not checked";
            if ("Farming Guild".equals(patch.region))
            {
                int level = "HERB".equals(patch.type) || "TREE".equals(patch.type) || "HESPORI".equals(patch.type) ? 65
                    : "FRUIT_TREE".equals(patch.type) || "CELASTRUS".equals(patch.type) || "REDWOOD".equals(patch.type) ? 85 : 45;
                if (farming < level) gate = level + " Farming required";
            }
            if (("Harmony".equals(patch.region) || "Harmony Island".equals(patch.region)) && "HERB".equals(patch.type) && client.getVarbitValue(VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE) == 0) gate = "Needs elite Morytania diary";
            if ("Lletya".equals(patch.region) && quests.get(Quest.MOURNINGS_END_PART_I) != QuestState.IN_PROGRESS && quests.get(Quest.MOURNINGS_END_PART_I) != QuestState.FINISHED) gate = "Start Mourning's End Part I";
            if (("Auburnvale".equals(patch.region) || "Kastori".equals(patch.region) || "Anglers' Retreat".equals(patch.region)) && !saved.patches.containsKey(patch.id)) gate = "Access not checked";
            next.put(patch.id, gate);
            if (faladorElite && "Falador".equals(patch.region) && "TREE".equals(patch.type)
                || kourendEasy && "Kourend".equals(patch.region) && ("ALLOTMENT".equals(patch.type) || "FLOWER".equals(patch.type) || "HERB".equals(patch.type))) free.add(patch.id);
        }
        access = Collections.unmodifiableMap(next);
        diaryProtection = Collections.unmodifiableSet(free);
        Map<Integer, Integer> notes = new LinkedHashMap<>(notedVariants);
        for (FarmPatchCatalog.Payment payment : catalog.payments()) for (FarmPatchCatalog.PaymentItem item : payment.items)
        {
            if (!item.notesAllowed || notes.containsKey(item.itemId)) continue;
            try
            {
                net.runelite.api.ItemComposition definition = client.getItemDefinition(item.itemId); if (definition == null) continue;
                int noted = definition.getLinkedNoteId(); net.runelite.api.ItemComposition counterpart = noted > 0 ? client.getItemDefinition(noted) : null;
                if (counterpart != null && counterpart.getNote() != -1 && counterpart.getLinkedNoteId() == item.itemId) notes.put(item.itemId, noted);
                else notes.put(item.itemId, -1);
            }
            catch (RuntimeException loading) { /* Native definitions can arrive after login. */ }
        }
        notedVariants = Collections.unmodifiableMap(notes);
    }
    private void publish()
    {
        Integer precision=profile==null?null:config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION,int.class);
        Integer offset=profile==null?null:config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,profile,TimeTrackingConfig.FARM_TICK_OFFSET,int.class);
        java.util.Set<net.runelite.api.WorldType> worlds=client.getWorldType();
        boolean leagues=worlds!=null && worlds.contains(net.runelite.api.WorldType.SEASONAL) && !worlds.contains(net.runelite.api.WorldType.DEADMAN);
        Map<String, PatchReading> next = new LinkedHashMap<>();
        for (Map.Entry<String, Seen> entry : saved.patches.entrySet())
        {
            FarmPatchCatalog.Patch patch = catalog.patch(entry.getKey()); Seen seen = entry.getValue();
            if (patch != null) next.put(patch.id, new PatchReading(patch, catalog.state(patch.type, seen.value), seen.at, seen.growthAt > 0 ? seen.growthAt : seen.at, current.contains(patch.id),seen.compost,seen.compostAt,precision,offset,leagues));
        }
        readings = Collections.unmodifiableMap(next);
        HudOptions options=new HudOptions();options.enabled=saved.hud.enabled;options.compost=saved.hud.compost;options.style=saved.hud.style;hudOptions=options;
        Map<String, Route> copy = new LinkedHashMap<>(); saved.routes.forEach((id, route) -> copy.put(id, copy(route))); routes = Collections.unmodifiableMap(copy);
        readTravelAdvice();
    }
    private void readTravelAdvice()
    {
        GuideNavigation nav=navigation;
        if(nav==null||!validProfile())travelAdvice=FarmTravelPlan.RouteAdvice.EMPTY;
        else
        {
            GuideNavigation.Plan plan=nav.snapshot();boolean owned=nav.ownsRoute("farm",plan.point)||nav.ownsRoute("guide",plan.point);
            travelAdvice=new FarmTravelPlan.RouteAdvice(profile,observedLocation,plan.point,owned,plan.answered,plan.reached,plan.transports);
        }
    }
    private static Route copy(Route route)
    {
        Route next = new Route(); next.stops = new ArrayList<>(route.stops); next.seeds = new LinkedHashMap<>(route.seeds); next.completed = new LinkedHashSet<>(route.completed); next.protection = new LinkedHashSet<>(route.protection); next.compost = route.compost;
        next.patchSeeds = new LinkedHashMap<>(route.patchSeeds); next.patchCompost = new LinkedHashMap<>(route.patchCompost); next.patchProtection = new LinkedHashMap<>(route.patchProtection);
        next.yield = new LinkedHashMap<>(route.yield); next.evidence = new LinkedHashMap<>(route.evidence); route.destinations.forEach((id, point) -> next.destinations.put(id, point.clone()));
        route.travel.forEach((id, stop) -> next.travel.put(id, FarmTravelPlan.copy(stop)));
        next.startedAt = route.startedAt; next.active = route.active; next.autoCheck = route.autoCheck; return next;
    }
    private Route route(String guide)
    {
        Route value = routes.get(guide); if (value != null) return copy(value);
        Route next = new Route(); for (FarmPatchCatalog.Patch patch : catalog.forGuide(guide)) if (access.getOrDefault(patch.id, "").isEmpty()) next.stops.add(patch.id); return next;
    }
    FarmPatchCatalog.Seed crop(Route route, FarmPatchCatalog.Patch patch)
    { Integer id = route.patchSeeds.containsKey(patch.id) ? route.patchSeeds.get(patch.id) : route.seeds.get(patch.type); return id == null || id == 0 ? null : catalog.seed(id); }
    int compost(Route route, FarmPatchCatalog.Patch patch) { return route.patchCompost.getOrDefault(patch.id, route.compost ? 21483 : 0); }
    boolean protectedCrop(Route route, FarmPatchCatalog.Patch patch) { return route.patchProtection.getOrDefault(patch.id, route.protection.contains(patch.type)); }
    private void observeReplant(FarmPatchCatalog.Patch patch, Seen before, int value)
    {
        if (before == null || !current.contains(patch.id)) return;
        FarmPatchCatalog.State previous = catalog.state(patch.type, before.value), next = catalog.state(patch.type, value);
        if (previous == null || next == null || !"GROWING".equals(next.state) || next.stage != 0 || "GROWING".equals(previous.state) && previous.crop.equals(next.crop) && previous.stage == 0) return;
        for (Route route : saved.routes.values())
        {
            FarmPatchCatalog.Seed seed = crop(route, patch);
            if (!route.active || !route.autoCheck || !route.stops.contains(patch.id) || route.completed.contains(patch.id) || seed == null || !seed.cropKey.equals(next.crop)) continue;
            // A planted seed proves neither compost nor a subsequent gardener payment.
            if (compost(route, patch) == 0 && (!protectedCrop(route, patch) || diaryProtection.contains(patch.id)))
            { route.completed.add(patch.id); route.evidence.put(patch.id, "Native replant observed"); dirty = true; }
            else { route.evidence.put(patch.id, "Replant observed · confirm compost / payment"); dirty = true; }
        }
    }
    private void observeDestinations(List<FarmPatchCatalog.Patch> local)
    {
        List<net.runelite.api.NPC> npcs = client.getNpcs(); if (npcs == null || npcs.isEmpty()) return;
        for (FarmPatchCatalog.Patch patch : local)
        {
            for (net.runelite.api.NPC npc : npcs)
                if (npc != null && (patch.farmer > 0 && npc.getId() == patch.farmer || "Tool leprechaun".equalsIgnoreCase(npc.getName())) && npc.getWorldLocation() != null && patch.sentAt(npc.getWorldLocation()))
                {
                    WorldPoint point = npc.getWorldLocation(); int[] position = {point.getX(), point.getY(), point.getPlane()};
                    for (Route route : saved.routes.values()) if (route.stops.contains(patch.id) && !route.destinations.containsKey(patch.id))
                    { route.destinations.put(patch.id, position); dirty = true; }
                    break;
                }
        }
    }
    private WorldPoint routeDestination(Route route,FarmPatchCatalog.Patch patch)
    {
        int[] pinned=route.destinations.get(patch.id);
        if(pinned!=null && pinned.length==3)return new WorldPoint(pinned[0],pinned[1],pinned[2]);
        FarmPatchScene.Snapshot visible=scene;
        if(visible.matches(client.getTopLevelWorldView()))for(FarmPatchScene.Anchor anchor:visible.anchors)
            if(patch.id.equals(anchor.patchId))
            {
                WorldPoint point=anchor.object.getWorldLocation();
                if(point!=null && patch.sentAt(point))return point;
            }
        return FarmPatchDestinations.forPatch(patch);
    }
    private void updateAutomaticRoute()
    {
        if(!validProfile() || !automaticRoute.active())return;
        for(Map.Entry<String,Route> entry:saved.routes.entrySet())
        {
            Route route=entry.getValue();if(!route.active)continue;
            for(String id:route.stops)if(!route.completed.contains(id))
            {
                FarmPatchCatalog.Patch patch=catalog.patch(id);if(patch==null)continue;
                automaticRoute.update(id,routeDestination(route,patch),patch.label(),ticks);return;
            }
            // Every configured stop is checked. End the shared run instead of retaining stale guidance.
            route.active=false;dirty=true;automaticRoute.stop();publish();save();refresh();finishAction.run();return;
        }
        automaticRoute.stop();
    }
    private void readPrices()
    {
        ItemManager items = itemManager; if (items == null) return;
        Set<Integer> ids = new LinkedHashSet<>();
        for (Route route : saved.routes.values())
        {
            ids.addAll(supplies(route).keySet());
            for (String id : route.stops) { FarmPatchCatalog.Patch patch = catalog.patch(id); FarmPatchCatalog.Seed seed = patch == null ? null : crop(route, patch); if (seed != null && seed.produceId > 0) ids.add(seed.produceId); }
        }
        Map<Integer, Long> next = new LinkedHashMap<>();
        for (int id : ids) try
        { net.runelite.api.ItemComposition definition = items.getItemComposition(id); long price = items.getItemPrice(id); if (definition != null && definition.isTradeable() && price > 0) next.put(id, price); }
        catch (RuntimeException loading) { /* Leave uncached prices unknown. */ }
        prices = Collections.unmodifiableMap(next);
    }
    public RunReading currentRun()
    {
        if (!validProfile()) return null;
        for (Map.Entry<String, Route> entry : routes.entrySet()) if (entry.getValue().active) return reading(entry.getKey(), copy(entry.getValue()));
        String guide = guideId; return catalog.forGuide(guide).isEmpty() ? null : reading(guide, route(guide));
    }
    /** Immutable published observations only: opening a guide does not start or mutate a farm run. */
    GuideLiveProgress.Reading guideProgress(String requestedGuide)
    {
        if(!validProfile())return GuideLiveProgress.Reading.UNKNOWN;
        List<FarmPatchCatalog.Patch> choices=catalog.forGuide(requestedGuide);if(choices.isEmpty())return GuideLiveProgress.Reading.UNKNOWN;
        Route route=routes.get(requestedGuide);PatchReading objective=null;WorldPoint location=observedLocation;
        if(route!=null && route.active)
        {
            for(String id:route.stops)if(!route.completed.contains(id))
            {PatchReading reading=readings.get(id);if(reading!=null&&reading.currentSession&&location!=null&&reading.patch.sentAt(location))objective=reading;break;}
        }
        else if(location!=null)
        {
            int distance=Integer.MAX_VALUE;
            for(FarmPatchCatalog.Patch patch:choices)
            {
                PatchReading reading=readings.get(patch.id);if(reading==null||!reading.currentSession||!patch.sentAt(location))continue;
                WorldPoint point=observedScenePoints.get(patch.id);if(point==null)point=FarmPatchDestinations.forPatch(patch);
                int candidate=point==null?Integer.MAX_VALUE-1:point.distanceTo(location);
                if(objective==null||candidate<distance){objective=reading;distance=candidate;}
            }
        }
        return FarmGuideProgress.read(profile,requestedGuide,route,objective,catalog);
    }
    /** Eligible solver alternatives. Null means not a patch guide; empty means wait for eligible facts. */
    List<GuideNavigation.Destination> travelDestinations(String requestedGuide)
    {
        List<FarmPatchCatalog.Patch> candidates=catalog.forGuide(requestedGuide);if(candidates.isEmpty())return null;
        if(!validProfile())return Collections.emptyList();Route route=routes.get(requestedGuide);
        if(route!=null)
        {
            candidates=new ArrayList<>();
            for(String id:route.stops)
            {
                if(route.startedAt>0&&route.completed.contains(id))continue;
                FarmPatchCatalog.Patch patch=catalog.patch(id);if(patch!=null)candidates.add(patch);
                if(route.active)break;
            }
        }
        List<GuideNavigation.Destination> options=new ArrayList<>();Set<WorldPoint> seen=new LinkedHashSet<>();
        for(FarmPatchCatalog.Patch patch:candidates)
        {
            if(route!=null&&!route.stops.contains(patch.id))continue;
            if(route!=null&&route.active&&route.completed.contains(patch.id))continue;
            String gate=access.get(patch.id);boolean confirmed=route!=null&&route.travel.containsKey(patch.id)&&route.travel.get(patch.id).accessConfirmed;
            if(gate==null||!gate.isEmpty()&&!(confirmed&&(gate.equals("Access not checked")||gate.equals("Unlock not checked"))))continue;
            WorldPoint point=publishedDestination(route,patch);if(point!=null&&seen.add(point))options.add(new GuideNavigation.Destination(patch.label(),requestedGuide,point));
            if(route!=null&&route.active)break;
        }
        return Collections.unmodifiableList(options);
    }
    boolean isPatchGuide(String requestedGuide){return !catalog.forGuide(requestedGuide).isEmpty();}
    private WorldPoint publishedDestination(Route route,FarmPatchCatalog.Patch patch)
    {
        int[] pin=route==null?null:route.destinations.get(patch.id);if(pin!=null&&pin.length==3)return new WorldPoint(pin[0],pin[1],pin[2]);
        WorldPoint observed=observedScenePoints.get(patch.id);return observed!=null?observed:FarmPatchDestinations.forPatch(patch);
    }
    private String transport(Route route,FarmPatchCatalog.Patch patch)
    {return validProfile()?travelAdvice.recommendation(profile,publishedDestination(route,patch)):"";}
    RunReading reading(String guide, Route route)
    {
        String next = null; int ready = 0, due = 0, missingXp = 0, unpriced = 0, unrated = 0; double baseXp = 0, harvestXp = 0; long cost = 0, revenue = 0;
        FarmTravelPlan.Summary travel = travelSummary(route);
        Map<Integer, Long> supplies = supplies(route); List<SupplyReading> supplyReadings = new ArrayList<>(); OwnershipLedger.View owned = ledger.snapshot(); Set<Integer> noteable = paymentItemIds(route);
        OwnershipLedger.Observation bank = owned.storages.get(OwnershipLedger.Storage.BANK), vault = owned.storages.get(OwnershipLedger.Storage.SEED_VAULT);
        for (Map.Entry<Integer, Long> item : supplies.entrySet())
        {
            int id = item.getKey(); supplyReadings.add(new SupplyReading(id, item.getValue(), carried(owned, id, noteable.contains(id)), bank == null ? 0 : bank.quantity(id), vault == null ? 0 : vault.quantity(id), bank != null, vault != null));
            long consumedCount = item.getValue() - travel.required.getOrDefault(id, 0L);
            if (id != 952 && id != 5343 && id != 995 && consumedCount > 0) { Long price = prices.get(id); if (price == null) unpriced++; else cost += price * consumedCount; }
        }
        for (Map.Entry<Integer, Long> item : travel.consumed.entrySet()) { Long price = prices.get(item.getKey()); if (price == null) unpriced++; else cost += price * item.getValue(); }
        long now = Instant.now().getEpochSecond();
        for (String id : route.stops)
        {
            if (route.completed.contains(id)) continue;
            if (next == null) next = id; PatchReading observed = readings.get(id);
            if (observed != null && observed.state != null)
            {
                if ("HARVESTABLE".equals(observed.state.state)) ready++;
                else if (observed.status(now).startsWith("Due to check")) due++;
            }
            FarmPatchCatalog.Patch patch = catalog.patch(id); FarmPatchCatalog.Seed seed = patch == null ? null : crop(route, patch);
            if (seed == null || seed.plantingXp == null) { missingXp++; unrated++; continue; }
            Integer yield = route.yield.get(id);
            if (patch.healthCheck && seed.healthXp == null) missingXp++;
            baseXp += (seed.plantingOnFirstHarvest && Integer.valueOf(0).equals(yield) ? 0 : seed.plantingXp) + (seed.healthXp == null ? 0 : seed.healthXp);
            if (Integer.valueOf(0).equals(yield)) continue;
            if (yield == null || seed.harvestXp == null || seed.produceId <= 0) { unrated++; continue; }
            harvestXp += yield * seed.harvestXp; Long price = prices.get(seed.produceId); if (price == null) unrated++; else revenue += yield * price;
        }
        return new RunReading(profile, guide, route, next, next == null ? "Run checked" : catalog.patch(next).label(), ready, due, supplies, supplyReadings, baseXp, harvestXp, missingXp,
            unpriced == 0 ? cost + travel.coins : null, unrated == 0 ? revenue : null, unpriced, unrated, travel, inaccessible(route));
    }
    /** Explicit player action; restoring or merely browsing a route does not start a run. */
    public void startSelectedRun()
    {
        startRun(profile, epoch, guideId);
    }
    private void startRun(String owner, long generation, String guide)
    {
        if (catalog.forGuide(guide).isEmpty()) return;
        edit(owner, generation, guide, value -> { for (Route previous : saved.routes.values()) previous.active = false; value.completed.clear(); value.evidence.clear(); value.active = true; value.startedAt = System.currentTimeMillis();automaticRoute.begin(); });
        thread.invokeLater(() -> { if (guard(owner, generation)) { RunReading run = currentRun(); if (run != null) startAction.accept(run); } });
    }
    public JPanel createPanel() { if (panel == null) { panel = SidebarUi.column(); panel.setName("farm-patch-planner"); refresh(); } return panel; }
    private void refresh()
    {
        if (panel == null || !queued.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> { try { if (javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length == 0) render(); } finally { queued.set(false); } });
    }
    private void render()
    {
        panel.removeAll(); String guide = guideId; long generation = epoch; String owner = profile;
        List<FarmPatchCatalog.Patch> available = catalog.forGuide(guide);
        if (available.isEmpty()) { panel.setVisible(false); return; }
        panel.setVisible(true);
        if (!validProfile()) { panel.add(SidebarUi.label("Log in to view patch observations")); finish(); return; }
        Route route = route(guide); JPanel heading = SidebarUi.section("PATCH ROUTE");
        JPanel hud=SidebarUi.column();JCheckBox showHud=new JCheckBox("Timers above farming patches",saved.hud.enabled);showHud.setOpaque(false);showHud.setForeground(SidebarUi.TEXT);showHud.setFont(GuideText.FONT);
        showHud.addActionListener(event->withCurrent(owner,generation,()->{saved.hud.enabled=showHud.isSelected();dirty=true;publish();save();}));hud.add(showHud);
        JCheckBox showCompost=new JCheckBox("Show observed compost",saved.hud.compost);showCompost.setOpaque(false);showCompost.setForeground(SidebarUi.TEXT);showCompost.setFont(GuideText.FONT);
        showCompost.addActionListener(event->withCurrent(owner,generation,()->{saved.hud.compost=showCompost.isSelected();dirty=true;publish();save();}));hud.add(showCompost);
        JComboBox<String> hudStyle=new JComboBox<>(new String[]{"Rings","Compact"});hudStyle.setSelectedItem(saved.hud.style);style(hudStyle);hudStyle.addActionListener(event->{String chosen=(String)hudStyle.getSelectedItem();withCurrent(owner,generation,()->{saved.hud.style=chosen;dirty=true;publish();save();});});hud.add(SidebarUi.field("Patch HUD style",hudStyle));panel.add(disclosure("hud","Patch HUD",hud));
        heading.add(SidebarUi.label(route.completed.size() + " / " + route.stops.size() + " stops checked"));
        heading.add(SidebarUi.button("New run", () -> startRun(owner, generation, guide)));
        if (route.startedAt > 0) heading.add(SidebarUi.button(route.active ? "Pause run" : "Resume run", () -> edit(owner, generation, guide, value -> {value.active=!value.active;if(value.active)automaticRoute.begin();else automaticRoute.stop();})));
        if (route.active) heading.add(SidebarUi.button("Finish run", () -> edit(owner, generation, guide, value -> {value.active=false;finishAction.run();})));
        JCheckBox auto = new JCheckBox("Auto-check replants", route.autoCheck); auto.setOpaque(false); auto.setForeground(SidebarUi.TEXT); auto.setFont(GuideText.FONT);
        auto.addActionListener(event -> { boolean selected = auto.isSelected(); edit(owner, generation, guide, value -> value.autoCheck = selected); }); heading.add(auto);
        RunReading run = reading(guide, route);
        if (run.inaccessibleStops > 0) heading.add(SidebarUi.label("! " + run.inaccessibleStops + " stops need an access check"));
        if (run.missingTravelCharges > 0) heading.add(SidebarUi.label("! Travel needs " + run.missingTravelCharges + " more charges"));
        if (run.uncheckedTravelCharges > 0) heading.add(SidebarUi.label(run.uncheckedTravelCharges + " travel charge pools unchecked"));
        heading.add(SidebarUi.label(run.confirmedReady + " harvestable last seen · " + run.estimatedDue + " estimated due"));
        heading.add(SidebarUi.button("Open run loadout", () -> withCurrent(owner, generation, loadoutAction)));
        heading.add(SidebarUi.button("Open run guide", () -> withCurrent(owner, generation, () -> guideAction.accept(guide))));
        if (!status.isEmpty()) heading.add(SidebarUi.label(status)); panel.add(heading);
        JPanel prep = SidebarUi.column(); Set<String> types = new LinkedHashSet<>();
        for (String id : route.stops) { FarmPatchCatalog.Patch patch = catalog.patch(id); if (patch != null) types.add(patch.type); }
        for (String type : types)
        {
            List<FarmPatchCatalog.Seed> seeds = catalog.seeds(type); if (seeds.isEmpty()) { prep.add(SidebarUi.label(type.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " · crop supplies unknown")); continue; }
            JComboBox<FarmPatchCatalog.Seed> choice = new JComboBox<>(); choice.setName("farm-patch.seed."+type); choice.addItem(null); for (FarmPatchCatalog.Seed seed : seeds) choice.addItem(seed);
            choice.setRenderer(new javax.swing.DefaultListCellRenderer()
            {
                @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
                { super.getListCellRendererComponent(list, value == null ? "Choose crop" : value, index, selected, focus); setFont(GuideText.FONT); setForeground(SidebarUi.TEXT); setBackground(selected ? new java.awt.Color(66, 57, 36) : SidebarUi.CARD); return this; }
            });
            for (FarmPatchCatalog.Seed seed : seeds) if (Integer.valueOf(seed.id).equals(route.seeds.get(type))) choice.setSelectedItem(seed);
            choice.addActionListener(event -> { FarmPatchCatalog.Seed seed = (FarmPatchCatalog.Seed) choice.getSelectedItem(); edit(owner, generation, guide, value -> { if (seed == null) value.seeds.remove(type); else value.seeds.put(type, seed.id); }); });
            prep.add(SidebarUi.field(type.replace('_', ' ').toLowerCase(java.util.Locale.ROOT), choice));
            Integer selectedSeed = route.seeds.get(type);
            if (selectedSeed != null)
            {
                FarmPatchCatalog.Seed seed = catalog.seed(selectedSeed);
                prep.add(SidebarUi.label(seed == null || seed.quantity == null ? "Seed quantity · unknown" : seed.quantity + " seed / sapling per patch"));
                prep.add(SidebarUi.label(seed == null || seed.level == null ? "Planting level · unknown"
                    : (boostedFarmingLevel >= seed.level ? "✓ " : "! ") + seed.level + " Farming · you " + farmingLevel
                        + (boostedFarmingLevel != farmingLevel ? " (" + boostedFarmingLevel + " boosted)" : "")));
                FarmPatchCatalog.Payment payment = catalog.payment(selectedSeed);
                if (payment == null) prep.add(SidebarUi.label("Gardener payment · unknown"));
                else if (!payment.available) prep.add(SidebarUi.label(payment.reason.isEmpty() ? "Gardener protection unavailable" : payment.reason));
                else
                {
                    JCheckBox protect = new JCheckBox("Pack gardener payment", route.protection.contains(type)); protect.setOpaque(false); protect.setForeground(SidebarUi.TEXT); protect.setFont(GuideText.FONT);
                    protect.addActionListener(event -> { boolean selected = protect.isSelected(); edit(owner, generation, guide, value -> { if (selected) value.protection.add(type); else value.protection.remove(type); }); }); prep.add(protect);
                    for (FarmPatchCatalog.PaymentItem item : payment.items) prep.add(SidebarUi.label(item.quantity + " × " + item.name + " / patch"));
                }
            }
        }
        JCheckBox compost = new JCheckBox("Pack ultracompost", route.compost); compost.setOpaque(false); compost.setForeground(SidebarUi.TEXT); compost.setFont(GuideText.FONT);
        compost.addActionListener(event -> { boolean selected = compost.isSelected(); edit(owner, generation, guide, value -> value.compost = selected); }); prep.add(compost);
        Map<Integer, Long> supplies = supplies(route); Set<Integer> noteable = paymentItemIds(route); OwnershipLedger.View owned = ledger.snapshot();
        for (Map.Entry<Integer, Long> supply : supplies.entrySet())
        {
            long carried = carried(owned, supply.getKey(), noteable.contains(supply.getKey()));
            OwnershipLedger.Observation bank = owned.storages.get(OwnershipLedger.Storage.BANK);
            OwnershipLedger.Observation vault = owned.storages.get(OwnershipLedger.Storage.SEED_VAULT);
            long ownedTotal = carried + (bank == null ? 0 : bank.quantity(supply.getKey())) + (vault == null ? 0 : vault.quantity(supply.getKey()));
            GuideText description = SidebarUi.label((carried >= supply.getValue() ? "✓ " : "! ") + LoadoutReadinessTool.itemName(supply.getKey()) + " · " + carried + " / " + supply.getValue()
                + (carried < supply.getValue() ? "\nBank " + (bank == null ? "unknown" : bank.quantity(supply.getKey()) + " last seen")
                    + (vault == null ? "" : " · vault " + vault.quantity(supply.getKey()) + " last seen")
                    + (ownedTotal < supply.getValue() ? "\nNeed " + (supply.getValue() - ownedTotal) + " beyond observed stock" : "\nOwned in observed storage") : ""));
            JPanel line = new JPanel(new java.awt.BorderLayout(5, 0)); line.setOpaque(false); line.add(description, java.awt.BorderLayout.CENTER);
            if (itemManager != null) { javax.swing.JLabel icon = new javax.swing.JLabel(); icon.setVerticalAlignment(javax.swing.SwingConstants.TOP); net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(supply.getKey()); if (image != null) image.addTo(icon); line.add(icon, java.awt.BorderLayout.WEST); } prep.add(line);
        }
        prep.add(SidebarUi.button("Focus run supplies in bank", () -> {
            Set<Integer> ids = new LinkedHashSet<>(supplies.keySet()); thread.invokeLater(() -> { if (guard(owner, generation)) bankFocus.accept(Collections.unmodifiableSet(ids)); });
        })); panel.add(disclosure("preparation", "Seeds & preparation", prep));
        JPanel estimates = SidebarUi.column();
        estimates.add(SidebarUi.label("Full-growth base XP · " + SkillingResourceCatalog.number(run.knownBaseXp)
            + (run.missingXpStops > 0 ? "\n" + run.missingXpStops + " stops without verified base XP" : "")));
        if (run.scenarioHarvestXp > 0) estimates.add(SidebarUi.label("Your yield scenario · +" + SkillingResourceCatalog.number(run.scenarioHarvestXp) + " harvest XP"));
        estimates.add(SidebarUi.label("Consumed supplies · " + (run.inputCost == null ? run.unpricedInputs + " unpriced items" : String.format(java.util.Locale.ROOT, "%,d gp", run.inputCost))));
        if (run.travelCoins > 0) estimates.add(SidebarUi.label(String.format(java.util.Locale.ROOT, "Included travel / clearing fees · %,d gp", run.travelCoins)));
        estimates.add(SidebarUi.label("Your harvest value · " + (run.harvestRevenue == null ? run.unratedHarvests + " stops need yield / price facts" : String.format(java.util.Locale.ROOT, "%,d gp", run.harvestRevenue))));
        if (run.harvestRevenue != null && run.inputCost != null) estimates.add(SidebarUi.label(String.format(java.util.Locale.ROOT, "Margin before fees · %+,d gp", run.harvestRevenue - run.inputCost)));
        panel.add(disclosure("estimates", "XP & yield scenario", estimates));
        FarmPatchCatalog.Patch next = null;
        for (String id : route.stops) if (!route.completed.contains(id)) { next = catalog.patch(id); if (next != null) break; }
        if (next != null)
        {
            JPanel objective = SidebarUi.section("NEXT STOP"); objective.add(SidebarUi.label(next.label()));
            String transport = transport(route,next); if (!transport.isEmpty()) objective.add(SidebarUi.label(transport));
            PatchReading reading = readings.get(next.id); FarmPatchCatalog.Seed selectedSeed = crop(route, next);
            objective.add(SidebarUi.label(selectedSeed != null && selectedSeed.level != null && boostedFarmingLevel < selectedSeed.level ? "Needs " + selectedSeed.level + " Farming to plant " + selectedSeed.name
                : selectedSeed != null ? action(reading) : "Inspect or harvest this patch."));
            final String nextId = next.id;
            objective.add(SidebarUi.button("Mark next stop checked", () -> edit(owner, generation, guide, value -> { value.completed.add(nextId); value.evidence.put(nextId, "Manually checked"); })));
            panel.add(objective);
        }
        routePage = Math.max(0, Math.min(routePage, Math.max(0, (route.stops.size() - 1) / 10)));
        if (route.stops.size() > 10) panel.add(SidebarUi.row(SidebarUi.button("‹ Stops", () -> { routePage = Math.max(0, routePage - 1); refresh(); }), SidebarUi.button("Stops ›", () -> { routePage++; refresh(); })));
        for (int i = routePage * 10; i < Math.min(route.stops.size(), (routePage + 1) * 10); i++)
        {
            final int index = i; FarmPatchCatalog.Patch patch = catalog.patch(route.stops.get(i)); if (patch == null) continue;
            JPanel card = SidebarUi.section((i + 1) + " · " + patch.label()); String gate = access.getOrDefault(patch.id, "");
            String transport = transport(route,patch); if (!transport.isEmpty()) card.add(SidebarUi.label(transport));
            if (!gate.isEmpty() && !((gate.equals("Access not checked") || gate.equals("Unlock not checked")) && route.travel.containsKey(patch.id) && route.travel.get(patch.id).accessConfirmed)) card.add(SidebarUi.label("! " + gate));
            if (diaryProtection.contains(patch.id)) card.add(SidebarUi.label("✓ Diary disease protection"));
            else if (protectedCrop(route, patch) && patch.farmer <= 0) card.add(SidebarUi.label("No gardener at this patch"));
            PatchReading reading = readings.get(patch.id); FarmPatchCatalog.Crop crop = reading == null || reading.state == null ? null : catalog.crop(reading.state.crop);
            card.add(SidebarUi.label((crop == null || reading != null && "WEEDS".equals(reading.state.crop) ? "" : crop.name + " · ") + (reading == null ? "Not observed" : reading.status(Instant.now().getEpochSecond()))));
            if (reading != null) card.add(SidebarUi.label("Seen " + DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(reading.observedAt))));
            card.add(SidebarUi.label(reading==null?"Compost unknown":FarmCompostEvidence.label(reading.observedCompost)+(reading.observedCompost==null?"":reading.currentSession?" · observed":" · last seen")));
            FarmPatchCatalog.Seed selected = crop(route, patch);
            if (selected != null) card.add(SidebarUi.label("Plant " + selected.name + (selected.growthMinutes > 0 ? " · ~" + HarvestClockTool.countdown(selected.growthMinutes * 60L) : "")
                + (selected.level == null ? "\nPlanting level unknown" : boostedFarmingLevel < selected.level ? "\n! Needs " + selected.level + " Farming" : "")));
            if (route.evidence.containsKey(patch.id)) card.add(SidebarUi.label(route.evidence.get(patch.id)));
            card.add(patchOptions(owner, generation, guide, route, patch));
            card.add(travelOptions(owner, generation, guide, route, patch));
            card.add(SidebarUi.button(route.completed.contains(patch.id) ? "✓ Checked · undo" : "Mark stop checked", () -> edit(owner, generation, guide, value -> { if (!value.completed.add(patch.id)) { value.completed.remove(patch.id); value.evidence.remove(patch.id); } else value.evidence.put(patch.id, "Manually checked"); })));
            card.add(SidebarUi.row(SidebarUi.button("↑ Up", () -> edit(owner, generation, guide, value -> move(value, index, -1))), SidebarUi.button("↓ Down", () -> edit(owner, generation, guide, value -> move(value, index, 1)))));
            card.add(SidebarUi.button("Remove stop", () -> edit(owner, generation, guide, value -> { value.stops.remove(patch.id); value.completed.remove(patch.id); value.patchSeeds.remove(patch.id); value.patchCompost.remove(patch.id); value.patchProtection.remove(patch.id); value.evidence.remove(patch.id); value.yield.remove(patch.id); value.destinations.remove(patch.id); value.travel.remove(patch.id); }))); panel.add(card);
        }
        JPanel options = SidebarUi.column();
        JComboBox<String> typeChoice = new JComboBox<>(); typeChoice.setName("farm-patch.add-type"); typeChoice.addItem("All patch types");
        Set<String> allTypes = new java.util.TreeSet<>(); catalog.patches().forEach(patch -> allTypes.add(patch.type)); for (String type : allTypes) typeChoice.addItem(type);
        typeChoice.setSelectedItem(addType); typeChoice.setFont(GuideText.FONT); typeChoice.setForeground(SidebarUi.TEXT); typeChoice.setBackground(SidebarUi.CARD);
        typeChoice.addActionListener(event -> { addType = (String) typeChoice.getSelectedItem(); addPage = 0; refresh(); }); options.add(typeChoice);
        List<FarmPatchCatalog.Patch> candidates = new ArrayList<>();
        for (FarmPatchCatalog.Patch patch : catalog.patches()) if (!route.stops.contains(patch.id) && ("All patch types".equals(addType) || patch.type.equals(addType))) candidates.add(patch);
        candidates.sort(java.util.Comparator.comparing(FarmPatchCatalog.Patch::label).thenComparing(patch -> patch.type));
        addPage = Math.max(0, Math.min(addPage, Math.max(0, (candidates.size() - 1) / 10)));
        options.add(SidebarUi.label(candidates.size() + " available patches · page " + (addPage + 1)));
        for (int i = addPage * 10; i < Math.min(candidates.size(), (addPage + 1) * 10); i++)
        {
            FarmPatchCatalog.Patch patch = candidates.get(i); String gate = access.getOrDefault(patch.id, ""); options.add(SidebarUi.label(patch.label() + " · " + patch.type.replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + (gate.isEmpty() ? "" : "\n" + gate)));
            options.add(SidebarUi.button("Add stop", () -> edit(owner, generation, guide, value -> { if (!value.stops.contains(patch.id)) value.stops.add(patch.id); })));
        }
        if (candidates.size() > 10) options.add(SidebarUi.row(SidebarUi.button("‹ Patches", () -> { addPage = Math.max(0, addPage - 1); refresh(); }), SidebarUi.button("Patches ›", () -> { addPage++; refresh(); })));
        panel.add(disclosure("patches", "Add other patches", options)); finish();
    }
    private JPanel patchOptions(String owner, long generation, String guide, Route route, FarmPatchCatalog.Patch patch)
    {
        JPanel options = SidebarUi.column();
        JComboBox<Object> seedChoice = new JComboBox<>(); seedChoice.setName("farm-patch.crop." + patch.id); seedChoice.addItem("Use run crop"); seedChoice.addItem("Do not replant");
        for (FarmPatchCatalog.Seed seed : catalog.seeds(patch.type)) seedChoice.addItem(seed);
        Integer selected = route.patchSeeds.get(patch.id);
        if (selected != null) { if (selected == 0) seedChoice.setSelectedIndex(1); else for (int i = 2; i < seedChoice.getItemCount(); i++) if (((FarmPatchCatalog.Seed) seedChoice.getItemAt(i)).id == selected) seedChoice.setSelectedIndex(i); }
        style(seedChoice); seedChoice.addActionListener(event -> { Object chosen = seedChoice.getSelectedItem(); int index = seedChoice.getSelectedIndex(); edit(owner, generation, guide, value -> { if (index == 0) value.patchSeeds.remove(patch.id); else value.patchSeeds.put(patch.id, chosen instanceof FarmPatchCatalog.Seed ? ((FarmPatchCatalog.Seed) chosen).id : 0); }); });
        options.add(SidebarUi.field("Crop for this patch", seedChoice));
        String[] compostNames = {"Use run compost", "No compost", "Compost", "Supercompost", "Ultracompost"}; int[] compostIds = {-1, 0, 6032, 6034, 21483};
        JComboBox<String> compostChoice = new JComboBox<>(compostNames); compostChoice.setName("farm-patch.compost." + patch.id);
        int compost = route.patchCompost.getOrDefault(patch.id, -1); for (int i = 0; i < compostIds.length; i++) if (compostIds[i] == compost) compostChoice.setSelectedIndex(i);
        style(compostChoice); compostChoice.addActionListener(event -> { int id = compostIds[compostChoice.getSelectedIndex()]; edit(owner, generation, guide, value -> { if (id < 0) value.patchCompost.remove(patch.id); else value.patchCompost.put(patch.id, id); }); }); options.add(SidebarUi.field("Compost", compostChoice));
        JComboBox<String> protectionChoice = new JComboBox<>(new String[]{"Use run protection", "No gardener payment", "Pack gardener payment"}); protectionChoice.setName("farm-patch.protection." + patch.id);
        Boolean protect = route.patchProtection.get(patch.id); protectionChoice.setSelectedIndex(protect == null ? 0 : protect ? 2 : 1); style(protectionChoice);
        protectionChoice.addActionListener(event -> { int index = protectionChoice.getSelectedIndex(); edit(owner, generation, guide, value -> { if (index == 0) value.patchProtection.remove(patch.id); else value.patchProtection.put(patch.id, index == 2); }); }); options.add(SidebarUi.field("Protection", protectionChoice));
        FarmPatchCatalog.Seed crop = crop(route, patch);
        if (crop != null)
        {
            FarmPatchCatalog.Payment payment = catalog.payment(crop.id);
            if (protectedCrop(route, patch) && !diaryProtection.contains(patch.id))
            {
                if (patch.farmer <= 0) options.add(SidebarUi.label("No gardener at this patch"));
                else if (payment == null) options.add(SidebarUi.label("Gardener payment unknown"));
                else if (!payment.available) options.add(SidebarUi.label(payment.reason.isEmpty() ? "Gardener protection unavailable" : payment.reason));
                else for (FarmPatchCatalog.PaymentItem item : payment.items) options.add(SidebarUi.label(item.quantity + " × " + item.name));
            }
            if (crop.harvestXp != null)
            {
                javax.swing.JTextField yield = new javax.swing.JTextField(route.yield.containsKey(patch.id) ? Integer.toString(route.yield.get(patch.id)) : "");
                yield.setName("farm-patch.yield." + patch.id); yield.setFont(GuideText.FONT); yield.setForeground(SidebarUi.TEXT); yield.setBackground(SidebarUi.CARD);
                options.add(SidebarUi.field("Your expected harvest (items)", yield));
                options.add(SidebarUi.button("Apply yield scenario", () -> { String input = yield.getText().trim(); try { Integer amount = input.isEmpty() ? null : Integer.valueOf(input); if (amount != null && (amount < 0 || amount > 10_000)) throw new NumberFormatException(); edit(owner, generation, guide, value -> { if (amount == null) value.yield.remove(patch.id); else value.yield.put(patch.id, amount); }); } catch (NumberFormatException invalid) { status = "Yield must be 0–10,000"; refresh(); } }));
            }
            options.add(SidebarUi.label((crop.plantingXp == null ? "Planting XP unknown" : (crop.plantingOnFirstHarvest ? "First harvest planting credit · " : "Planting · ") + SkillingResourceCatalog.number(crop.plantingXp) + " XP")
                + (crop.healthXp == null ? "" : "\nHealth check · " + SkillingResourceCatalog.number(crop.healthXp) + " XP")
                + (crop.harvestXp == null ? "" : "\nHarvest · " + SkillingResourceCatalog.number(crop.harvestXp) + " XP / item")));
        }
        options.add(SidebarUi.button("Use current tile as destination", () -> withCurrent(owner, generation, () -> {
            WorldPoint point = client.getLocalPlayer().getWorldLocation(); if (point == null || !patch.sentAt(point)) { status = "Visit this patch's region to pin a stop"; refresh(); return; }
            edit(owner, generation, guide, value -> value.destinations.put(patch.id, new int[]{point.getX(), point.getY(), point.getPlane()}));
        })));
        return disclosure("patch:" + patch.id, "Crop & stop settings", options);
    }
    private static void style(JComboBox<?> choice)
    { choice.setFont(GuideText.FONT); choice.setForeground(SidebarUi.TEXT); choice.setBackground(SidebarUi.CARD); choice.setMaximumRowCount(10); }
    private JPanel disclosure(String key, String title, JPanel content)
    {
        JPanel result = SidebarUi.column(); boolean open = expanded.getOrDefault(key, false); content.setVisible(open);
        javax.swing.JButton button = SidebarUi.button((open ? "▾ " : "▸ ") + title, () -> {});
        button.addActionListener(event -> { boolean show = !content.isVisible(); expanded.put(key, show); content.setVisible(show); button.setText((show ? "▾ " : "▸ ") + title); result.revalidate(); result.repaint(); });
        result.add(button); result.add(content); return result;
    }
    private static void move(Route route, int index, int direction)
    { int target = index + direction; if (index >= 0 && index < route.stops.size() && target >= 0 && target < route.stops.size()) Collections.swap(route.stops, index, target); }
    private FarmTravelPlan.Summary travelSummary(Route route)
    {
        List<FarmTravelPlan.Stop> stops = new ArrayList<>();
        for (String id : route.stops) if (!route.completed.contains(id) && route.travel.containsKey(id)) stops.add(route.travel.get(id));
        return FarmTravelPlan.summarize(stops);
    }
    private int inaccessible(Route route)
    {
        int count = 0;
        for (String id : route.stops)
        {
            if (route.completed.contains(id)) continue; String gate = access.getOrDefault(id, "Access not checked");
            boolean unknown = gate.equals("Access not checked") || gate.equals("Unlock not checked");
            if (!gate.isEmpty() && !(unknown && route.travel.containsKey(id) && route.travel.get(id).accessConfirmed)) count++;
        }
        return count;
    }
    private JPanel travelOptions(String owner, long generation, String guide, Route route, FarmPatchCatalog.Patch patch)
    {
        FarmTravelPlan.Stop saved = route.travel.getOrDefault(patch.id, new FarmTravelPlan.Stop()); JPanel options = SidebarUi.column();
        javax.swing.JTextField label = travelField(saved.label);
        javax.swing.JTextField reusable = travelField(travelItems(saved.carried)), consumed = travelField(travelItems(saved.consumed));
        javax.swing.JTextField pool = travelField(saved.chargePool), need = travelField(Integer.toString(saved.chargesNeeded));
        javax.swing.JTextField available = travelField(saved.availableCharges < 0 ? "" : Integer.toString(saved.availableCharges));
        javax.swing.JTextField coins = travelField(Long.toString(saved.coins));
        options.add(SidebarUi.field("My route / teleport", label));
        String recommendation = transport(route,patch); if (!recommendation.isEmpty()) options.add(SidebarUi.label(recommendation));
        options.add(SidebarUi.field("Reusable items · name:quantity", reusable));
        options.add(SidebarUi.field("Consumed items · name:quantity", consumed));
        options.add(SidebarUi.field("Shared charge pool", pool)); options.add(SidebarUi.field("Charges for this stop", need));
        options.add(SidebarUi.field("Charges available in pool", available)); options.add(SidebarUi.field("Travel / clearing fees in coins", coins));
        JCheckBox confirmed = new JCheckBox("I have access to this patch and route", saved.accessConfirmed); confirmed.setOpaque(false); confirmed.setForeground(SidebarUi.TEXT); confirmed.setFont(GuideText.FONT); options.add(confirmed);
        options.add(SidebarUi.button("Save travel preparation", () -> {
            try { FarmTravelPlan.Stop update = new FarmTravelPlan.Stop(); update.label = label.getText().trim(); update.carried = parseTravelItems(reusable.getText()); update.consumed = parseTravelItems(consumed.getText());
                update.chargePool = pool.getText().trim(); update.chargesNeeded = Integer.parseInt(need.getText().trim()); update.availableCharges = available.getText().isBlank() ? -1 : Integer.parseInt(available.getText().trim());
                update.coins = Long.parseLong(coins.getText().trim()); update.accessConfirmed = confirmed.isSelected(); if (!FarmTravelPlan.valid(update)) throw new IllegalArgumentException();
                edit(owner, generation, guide, value -> value.travel.put(patch.id, update));
            } catch (IllegalArgumentException invalid) { status = "Check travel quantities, item names and charge counts"; refresh(); }
        }));
        if (route.travel.containsKey(patch.id)) options.add(SidebarUi.button("Reset travel preparation", () -> edit(owner, generation, guide, value -> value.travel.remove(patch.id))));
        return disclosure("travel:" + patch.id, "Travel & charges", options);
    }
    private static javax.swing.JTextField travelField(String text)
    { javax.swing.JTextField field = new javax.swing.JTextField(text); field.setFont(GuideText.FONT); field.setForeground(SidebarUi.TEXT); field.setBackground(SidebarUi.CARD); field.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 29)); return field; }
    private static String travelItems(Map<Integer, Integer> items)
    { List<String> parts = new ArrayList<>(); items.forEach((id, quantity) -> parts.add(id + ":" + quantity)); return String.join(", ", parts); }
    static Map<Integer, Integer> parseTravelItems(String text)
    {
        Map<Integer, Integer> result = new LinkedHashMap<>(); if (text.isBlank()) return result;
        for (String part : text.split(","))
        {
            int colon = part.lastIndexOf(':'); String name = (colon < 0 ? part : part.substring(0, colon)).trim(); int quantity = colon < 0 ? 1 : Integer.parseInt(part.substring(colon + 1).trim());
            int id; try { id = Integer.parseInt(name); } catch (NumberFormatException named) { Set<Integer> ids = GuideItemResolver.resolveName(name); if (ids.size() != 1) throw new IllegalArgumentException("Choose one item variant"); id = ids.iterator().next(); }
            if (id <= 0 || id > 1_000_000 || quantity <= 0 || quantity > 100_000 || result.size() >= 12) throw new IllegalArgumentException();
            result.merge(id, quantity, Math::addExact);
        }
        return result;
    }
    Map<Integer, Long> supplies(Route route)
    {
        Map<Integer, Long> result = new LinkedHashMap<>();
        if (route.stops.isEmpty() || route.stops.stream().allMatch(route.completed::contains)) return result;
        result.put(952, 1L);
        for (String id : route.stops)
        {
            if (route.completed.contains(id)) continue;
            FarmPatchCatalog.Patch patch = catalog.patch(id); if (patch == null) continue; FarmPatchCatalog.Seed crop = crop(route, patch);
            if (crop != null)
            {
                if (crop.quantity != null) result.merge(crop.id, crop.quantity.longValue(), Long::sum);
                if (!patch.type.contains("TREE") && !"CALQUAT".equals(patch.type) && !"CELASTRUS".equals(patch.type) && !"REDWOOD".equals(patch.type)) result.put(5343, 1L);
                int compost = compost(route, patch);
                if (compost > 0 && (",HERB,ALLOTMENT,FLOWER,TREE,FRUIT_TREE,HARDWOOD_TREE,CALQUAT,CELASTRUS,REDWOOD,SEAWEED,BUSH,CACTUS,").contains("," + patch.type + ",")) result.merge(compost, 1L, Long::sum);
                FarmPatchCatalog.Payment payment = catalog.payment(crop.id);
                if (protectedCrop(route, patch) && payment != null && payment.available && patch.farmer > 0 && !diaryProtection.contains(patch.id))
                    for (FarmPatchCatalog.PaymentItem item : payment.items) result.merge(item.itemId, item.quantity, Long::sum);
            }
        }
        travelSummary(route).required.forEach((id, quantity) -> result.merge(id, quantity, Long::sum));
        return result;
    }
    private Set<Integer> paymentItemIds(Route route)
    {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String id : route.stops)
        {
            FarmPatchCatalog.Patch patch = catalog.patch(id); if (patch == null || route.completed.contains(id) || !protectedCrop(route, patch)) continue;
            FarmPatchCatalog.Seed seed = crop(route, patch); FarmPatchCatalog.Payment payment = seed == null ? null : catalog.payment(seed.id);
            if (payment != null && payment.available) for (FarmPatchCatalog.PaymentItem item : payment.items) if (item.notesAllowed) ids.add(item.itemId);
        }
        return ids;
    }
    long carried(OwnershipLedger.View view, int itemId, boolean notesAllowed)
    {
        OwnershipLedger.Observation inventory = view.storages.get(OwnershipLedger.Storage.INVENTORY), worn = view.storages.get(OwnershipLedger.Storage.EQUIPMENT);
        long count = inventory != null && inventory.currentSession ? inventory.quantity(itemId) : 0;
        if (worn != null && worn.currentSession) count += worn.quantity(itemId);
        Integer note = notedVariants.get(itemId);
        if (notesAllowed && note != null && note > 0 && inventory != null && inventory.currentSession) count += inventory.quantity(note);
        return count;
    }
    private static String action(PatchReading reading)
    {
        if (reading == null || reading.state == null) return "Visit and inspect this patch.";
        switch (reading.state.state)
        {
            case "DISEASED": return "Cure this crop before continuing.";
            case "DEAD": return "Clear the dead crop, compost and replant.";
            case "HARVESTABLE": return reading.patch.healthCheck ? "Check health, harvest if wanted, then clear and replant." : "Harvest, note produce with the leprechaun, compost and replant.";
            case "EMPTY": return "Compost and plant your selected crop.";
            default: return "WEEDS".equals(reading.state.crop) ? "Rake if needed, compost and plant." : "Inspect the growing crop, then continue to the next stop.";
        }
    }
    private boolean guard(String owner, long generation)
    { return generation == epoch && owner != null && owner.equals(profile) && validProfile() && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null; }
    private void withCurrent(String owner, long generation, Runnable action) { thread.invokeLater(() -> { if (guard(owner, generation)) action.run(); }); }
    private void edit(String owner, long generation, String guide, Consumer<Route> change)
    {
        thread.invokeLater(() -> {
            if (!guard(owner, generation)) return;
            Route route = route(guide); change.accept(route); saved.routes.put(guide, route); dirty = true; publish();updateAutomaticRoute(); save(); refresh();
        });
    }
    private boolean validSaved(Saved value)
    {
        if (value == null || value.version != 1 || value.patches == null || value.patches.size() > 250 || value.routes == null || value.routes.size() > 20 || value.hud==null || !List.of("Rings","Compact").contains(value.hud.style)) return false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Seen> entry : value.patches.entrySet())
        { FarmPatchCatalog.Patch patch = catalog.patch(entry.getKey()); Seen seen = entry.getValue(); if (patch == null || seen == null || seen.at <= 0 || seen.at > now || seen.growthAt < 0 || seen.growthAt > seen.at || catalog.state(patch.type, seen.value) == null || seen.compost!=null && (!FarmCompostEvidence.valid(seen.compost)||seen.compostAt<=0||seen.compostAt>now)) return false; }
        for (Map.Entry<String, Route> entry : value.routes.entrySet())
        {
            Route route = entry.getValue(); if (catalog.forGuide(entry.getKey()).isEmpty() || route == null || route.stops == null || route.stops.size() > 107 || route.seeds == null || route.seeds.size() > 23 || route.completed == null || route.completed.size() > 107 || !route.stops.containsAll(route.completed) || route.protection == null || route.protection.size() > 23) return false;
            Set<String> ids = new LinkedHashSet<>(); for (FarmPatchCatalog.Patch patch : catalog.patches()) ids.add(patch.id);
            if (!ids.containsAll(route.stops) || new LinkedHashSet<>(route.stops).size() != route.stops.size()) return false;
            for (Map.Entry<String, Integer> seed : route.seeds.entrySet()) if (catalog.seeds(seed.getKey()).stream().noneMatch(item -> Integer.valueOf(item.id).equals(seed.getValue()))) return false;
            for (String type : route.protection) if (catalog.seeds(type).isEmpty()) return false;
            if (route.patchSeeds == null || route.patchCompost == null || route.patchProtection == null || route.yield == null || route.evidence == null || route.destinations == null || route.travel == null || route.travel.size() > 107
                || route.patchSeeds.size() > 107 || route.patchCompost.size() > 107 || route.patchProtection.size() > 107 || route.yield.size() > 107 || route.evidence.size() > 107 || route.destinations.size() > 107
                || route.startedAt < 0 || route.startedAt > now || !route.stops.containsAll(route.patchSeeds.keySet()) || !route.stops.containsAll(route.patchCompost.keySet())
                || !route.stops.containsAll(route.patchProtection.keySet()) || !route.stops.containsAll(route.yield.keySet()) || !route.stops.containsAll(route.evidence.keySet()) || !route.stops.containsAll(route.destinations.keySet()) || !route.stops.containsAll(route.travel.keySet())) return false;
            for (Map.Entry<String, Integer> selected : route.patchSeeds.entrySet())
            { FarmPatchCatalog.Patch patch = catalog.patch(selected.getKey()); Integer seedId = selected.getValue(); if (seedId == null || seedId != 0 && catalog.seeds(patch.type).stream().noneMatch(seed -> seed.id == seedId)) return false; }
            for (Integer compost : route.patchCompost.values()) if (compost == null || compost != 0 && compost != 6032 && compost != 6034 && compost != 21483) return false;
            for (Boolean protection : route.patchProtection.values()) if (protection == null) return false;
            for (Integer yield : route.yield.values()) if (yield == null || yield < 0 || yield > 10_000) return false;
            for (String evidence : route.evidence.values()) if (!"Native replant observed".equals(evidence) && !"Manually checked".equals(evidence) && !"Replant observed · confirm compost / payment".equals(evidence)) return false;
            for (FarmTravelPlan.Stop stop : route.travel.values()) if (!FarmTravelPlan.valid(stop)) return false;
            for (Map.Entry<String, int[]> destination : route.destinations.entrySet())
            { int[] point = destination.getValue(); if (point == null || point.length != 3 || point[0] < 0 || point[0] > 16383 || point[1] < 0 || point[1] > 16383 || point[2] < 0 || point[2] > 3 || !catalog.patch(destination.getKey()).sentAt(new WorldPoint(point[0], point[1], point[2]))) return false; }
        }
        return true;
    }
    private void save()
    { if (dirty && profile != null && profile.equals(config.getRSProfileKey()) && store.write(RunewardenConfig.GROUP, profile, KEY, 1, saved)) { dirty = false; status = ""; } }
    private void finish() { panel.revalidate(); panel.repaint(); }
}
