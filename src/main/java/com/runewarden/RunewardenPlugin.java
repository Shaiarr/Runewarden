package com.runewarden;

import com.google.inject.Provides;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Runewarden", description = "A local coach for training, farming and encounters",
    tags = {"guide", "training", "farming", "hud", "coach"})
public final class RunewardenPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private ConfigManager configManager;
    @Inject private RunewardenConfig config;
    @Inject private RunewardenGuides guides;
    @Inject private OverlayManager overlayManager;
    @Inject private RunewardenResourceOverlay resourceOverlay;
    @Inject private RunewardenSightTool sightTool;
    @Inject private WaveExportTool waveExport;
    @Inject private WaveEntryCoach waveEntryCoach;
    @Inject private WaveEntryOverlay waveEntryOverlay;
    @Inject private GuideBankFilter guideBankFilter;
    @Inject private BankedExperience bankedExperience;
    @Inject private SkillingCoach skillingCoach;
    @Inject private SkillingCoachOverlay skillingCoachOverlay;
    @Inject private NodeTimerOverlay nodeTimerOverlay;
    @Inject private HarvestClockTool harvestClock;
    @Inject private FarmRunsTool farmRuns;
    @Inject private FarmPatchOverlay farmPatchOverlay;
    @Inject private VarlamoreThievingCoach thieving;
    @Inject private AgilityCoach agility;
    @Inject private ActionProgressTool actionProgress;
    @Inject private EquipmentInspectorTool equipmentInspector;
    @Inject private HunterRumourCoach hunterRumours;
    @Inject private HunterRumourOverlay hunterHud;
    @Inject private HunterRumourSceneOverlay hunterScene;
    @Inject private SailingCoach sailing;
    @Inject private SkillProgressTool skillProgress;
    @Inject private BankMemoryTool bankMemory;
    @Inject private ClueJugglingTool clueTimers;
    @Inject private ContainerHudTool containers;
    @Inject private ContextActions contextActions;
    @Inject private CommandDashboardTool dashboard;
    @Inject private HudSettingsTool hudSettings;
    @Inject private RunLootTool runLoot;
    @Inject private TileMarkersTool tileMarkers;
    @Inject private ResourceTooltipOverlay resourceTooltips;
    @Inject private InventoryPreviewOverlay inventoryPreview;
    @Inject private HudSession hudSession;
    @Inject private ActivityXpTool activities;
    @Inject private GoalPlannerTool goals;
    @Inject private ItemKnowledgeTool itemKnowledge;
    @Inject private CombatCalculatorTool combatCalculator;
    @Inject private SlayerCoachTool slayer;
    @Inject private EncounterPracticeTool encounters;
    @Inject private HoverCardOverlay hoverCards;
    @Inject private RewardNotifications rewards;
    @Inject private RewardPopupOverlay rewardPopup;
    @Inject private RewardSettings rewardSettings;
    @Inject private RewardSettingsTool rewardSettingsTool;
    @Inject private MusicUnlockTool music;
    @Inject private CombatAchievementsTool combatAchievements;
    @Inject private MinigameCoach minigames;

    @Inject private OwnershipLedger ownership;
    @Inject private LoadoutReadinessTool loadouts;
    @Inject private FieldCoach field;
    @Inject private DeathRecoveryTool deaths;
    @Inject private GroundLootTool groundLoot;
    @Inject private AccountProgressTool progress;
    @Inject private DiaryPlannerTool diaries;
    @Inject private ClueCoachTool clues;
    @Inject private GuideNavigation travel;
    @Inject private PohTravelCapabilities pohTravel;
    @Inject private WorldHopperTool worldHopper;
    @Inject private WorldHopperPanel worldHopperPanel;
    @Inject private WorldHopperPingOverlay worldPingOverlay;
    @Inject private DpsComparisonTool dps;
    @Inject private SharedActivitySession activitySession;
    @Inject private TrainingProgressTool training;
    @Inject private TrainingGuideProgress trainingGuideProgress;
    @Inject private RunewardenDataStore data;
    @Inject private RecoveryTool recovery;
    @Inject private DiscoverTool discover;
    @Inject private RoutinePlannerTool routines;
    @Inject private ModuleRuntime modules;
    @Inject private ActivityHubTool activityHub;
    @Inject private WorkflowPlansTool plans;
    @Inject private OpportunityPlannerTool opportunities;
    @Inject private SupplyAwarenessTool supplies;
    @Inject private RunewardenCoordinator coordinator;
    @Inject private CommandCenterTool commandCenter;
    @Inject private GuideCatalog guideCatalog;
    @Inject private ActivityXpCatalog activityCatalog;
    @Inject private ItemKnowledgeCatalog itemCatalog;
    @Inject private MobKnowledgeCatalog mobCatalog;
    @Inject private CombatAchievementCatalog achievementCatalog;
    @Inject private MusicUnlockCatalog musicCatalog;
    @Inject private net.runelite.client.input.KeyManager keys;
    private final net.runelite.client.util.HotkeyListener pauseKey=new net.runelite.client.util.HotkeyListener(()->config.trainingPauseKey())
    { @Override public void hotkeyPressed(){if(running&&modules.enabled("Training session"))training.toggleTracking();} };
    private final net.runelite.client.util.HotkeyListener restartKey=new net.runelite.client.util.HotkeyListener(()->config.trainingRestartKey())
    { @Override public void hotkeyPressed(){if(running&&modules.enabled("Training session"))training.restartTracking();} };
    private final ActivitySelection activitySelection=new ActivitySelection();
    private volatile RunewardenPanel panel;
    private NavigationButton navigation;
    private volatile boolean running;
    private volatile long lifecycleEpoch;
    private volatile String activeAccount;

    @Provides
    RunewardenConfig provideConfig(ConfigManager manager) { return manager.getConfig(RunewardenConfig.class); }

    @Override
    protected void startUp()
    {
        lifecycleEpoch++;
        hudSettings.prepareForOverlayRegistration();
        running = true;keys.registerKeyListener(pauseKey);keys.registerKeyListener(restartKey);
        activeAccount = null;
        data.reload();modules.clear();activitySelection.reset();
        ownership.start();activitySession.start();
        activitySession.setFinishAction(this::onRunFinished);
        activitySession.setStartAction(this::onRunStarted);
        guides.setCompletionAction(id->{SharedActivitySession.Snapshot expected=activitySession.snapshot();onClient(()->{
            SharedActivitySession.Snapshot current=activitySession.snapshot();
            if(current.running()&&java.util.Objects.equals(expected.runId,current.runId)&&current.context!=null
                &&(id.equals(current.context.guideId)||id.equals(current.context.id)))activitySession.finish();
        });});
        guides.setActivitySupplier(minigames::reading);
        guides.setTrainingSupplier(training::reading);
        trainingGuideProgress.setActionSupplier(actionProgress::snapshot);
        guides.setProgressSupplier(id->{GuideLiveProgress.Reading farm=farmRuns.guideProgress(id);
            return farm.known()?farm:trainingGuideProgress.read(id);});
        guides.setDismissAction(()->{minigames.dismissGuide();onClient(guideBankFilter::onGuideDismissed);});
        guides.setExplicitOpenAction(()->{if(minigames.guidanceDismissed())minigames.resumeGuide();});
        guides.setRefreshProgressAction(minigames::refreshForGuide);
        minigames.setResumeGuideAction(guides::resumeAutomaticGuidance);
        minigames.setActivityExitAction(guides::closeActivityGuide);
        minigames.setCloseGuideAction(id->{GuideCatalog.Entry selected=guides.selectedGuide();
            if(selected!=null&&id.equals(selected.id))guides.closeGuide();});
        guides.setNativeCompletionAction(id->{SharedActivitySession.Snapshot expected=activitySession.snapshot();onClient(()->{
            SharedActivitySession.Snapshot current=activitySession.snapshot();
            if(current.running()&&java.util.Objects.equals(expected.runId,current.runId)&&current.context!=null
                &&(id.equals(current.context.guideId)||id.equals(current.context.id)))
                activitySession.reachMilestone("guide:"+id,"Guide completed","Guides");
        });});
        plans.start();opportunities.start();supplies.start();activityHub.start();coordinator.clear();commandCenter.start();
        loadouts.start();field.start();deaths.start();groundLoot.start();progress.start();diaries.start();clues.start();pohTravel.start();travel.start();worldHopper.start();dps.start();training.start();routines.start();
        for(net.runelite.client.ui.overlay.Overlay overlay:field.overlays())overlayManager.add(overlay);
        overlayManager.add(groundLoot.overlay());overlayManager.add(training.overlay());
        overlayManager.add(worldPingOverlay);
        thieving.start();agility.start();actionProgress.start();equipmentInspector.start();contextActions.start();
        hunterRumours.start();sailing.start();skillProgress.start();bankMemory.start();clueTimers.start();containers.start();
        overlayManager.add(farmPatchOverlay);overlayManager.add(thieving.hud());overlayManager.add(thieving.scene());
        overlayManager.add(agility.hud());overlayManager.add(agility.scene());overlayManager.add(actionProgress.overlay());
        overlayManager.add(equipmentInspector.overlay());
        overlayManager.add(hunterHud);overlayManager.add(hunterScene);overlayManager.add(sailing.hud());overlayManager.add(sailing.scene());overlayManager.add(skillProgress.hud());overlayManager.add(skillProgress.skillTab());overlayManager.add(clueTimers.overlay());overlayManager.add(containers.overlay());
        FieldStatusOverlay effects=(FieldStatusOverlay)field.overlays().get(0);
        hudSettings.registerWidget("field-effects",effects,effects::setHudLocked);
        hudSettings.registerWidget("training",training.overlay(),training::setHudLocked);
        hudSettings.registerWidget("world-ping",worldPingOverlay,worldPingOverlay::setHudLocked);
        hudSettings.registerWidget("thieving",thieving.hud(),thieving::setHudLocked);
        hudSettings.registerWidget("agility",agility.hud(),agility::setHudLocked);
        hudSettings.registerWidget("action-progress",actionProgress.overlay(),actionProgress::setHudLocked);
        hudSettings.registerWidget("equipment-inspector",equipmentInspector.overlay(),equipmentInspector::setHudLocked);
        hudSettings.registerWidget("hunter-rumours",hunterHud,hunterHud::setHudLocked);
        hudSettings.registerWidget("sailing",sailing.hud(),sailing::setHudLocked);
        hudSettings.registerWidget("skill-progress",skillProgress.hud(),skillProgress::setHudLocked);
        hudSettings.registerWidget("clue-timers",clueTimers.overlay(),clueTimers::setHudLocked);
        hudSettings.registerWidget("container-hud",containers.overlay(),containers::setHudLocked);
        rewards.start();
        overlayManager.add(rewardPopup);
        hudSettings.registerWidget("reward-popup",rewardPopup,rewardPopup::setHudLocked);
        music.start();
        combatAchievements.start();
        minigames.start();
        overlayManager.add(minigames.hud());
        overlayManager.add(minigames.scene());
        hudSettings.registerWidget("minigame",minigames.hud(),minigames::setHudLocked);
        overlayManager.add(hoverCards);
        hudSession.start();
        overlayManager.add(inventoryPreview);
        goals.start();
        itemKnowledge.start();
        combatCalculator.start();
        slayer.start();
        encounters.start();
        guides.start();
        tileMarkers.start();
        runLoot.start();
        overlayManager.add(resourceTooltips);
        overlayManager.add(resourceOverlay);
        sightTool.start();
        waveExport.start();
        waveEntryCoach.start();
        overlayManager.add(waveEntryOverlay);
        overlayManager.add(skillingCoachOverlay);
        overlayManager.add(nodeTimerOverlay);
        harvestClock.start();
        farmRuns.start();
        dashboard.start();
        hudSettings.start();
        panel = new RunewardenPanel(guides, sightTool, waveExport);
        RunewardenPanel createdPanel=panel;
        panel.setEndRunAction(()->{if(running&&panel==createdPanel)endRun();});
        JPanel bankPanel = new JPanel(new BorderLayout(0, 12));
        bankPanel.setOpaque(false);
        bankPanel.add(guideBankFilter.createPanel(), BorderLayout.NORTH);
        bankPanel.add(bankedExperience.createPanel(), BorderLayout.CENTER);
        panel.addModule("Command dashboard", commandDashboardPanel(), 0);
        panel.addModule("Farm runs", farmRuns.createPanel(), 1);
        panel.addModule("Run loot", runLoot.createPanel(), 2);
        panel.addModule("Tile markers", tileMarkers.createPanel(), 4);
        panel.addModule("Bank & XP", bankPanel);
        panel.addModule("Skilling coach", skillingCoach.createPanel());
        panel.addModule("Thieving coach",thieving.createPanel());panel.addModule("Agility coach",agility.createPanel());
        panel.addModule("Action progress",actionProgress.createPanel());panel.addModule("Equipment inspector",equipmentInspector.createPanel());
        panel.addModule("Hunter rumours",hunterRumours.createPanel());panel.addModule("Sailing",sailing.createPanel());panel.addModule("Skill progress",skillProgress.createPanel());
        panel.addModule("Bank memory",bankMemory.createPanel());panel.addModule("Clue timers",clueTimers.createPanel());panel.addModule("Containers",containers.createPanel());
        panel.addModule("Timers & harvest", withSettings(harvestClock.createPanel(),
            toggle("Show harvest HUD", "harvestHud", config.harvestHud())));
        panel.addModule("HUD", hudSettings.createPanel());
        panel.addModule("Goals", goals.createPanel(), 1);
        panel.addModule("Slayer", slayer.createPanel(), 2);
        panel.addModule("Boss academy", encounters.createPanel(), 3);
        panel.addModule("Item database", itemKnowledge.createPanel());
        panel.addModule("Max hit", combatCalculator.createPanel());
        panel.addModule("Rewards", rewardSettingsTool.createPanel());
        panel.addModule("Music unlocks", music.createPanel());
        panel.addModule("Combat achievements", combatAchievements.createPanel());
        panel.addModule("Minigames", minigames.createPanel(), 4);
        panel.addModule("Activity hub",activityHub.createPanel(),1);
        panel.addModule("Find a plan",opportunities.createPanel(),1);
        panel.addModule("Plans",plans.createPanel(),2);
        panel.addModule("Search",discover.createPanel(),0);
        panel.addModule("Loadouts",loadouts.createPanel());panel.addModule("Effects & supplies",effectsPanel());
        panel.addModule("Recovery",deaths.createPanel());panel.addModule("Ground loot",groundLoot.createPanel());
        panel.addModule("Account progress",progress.createPanel());panel.addModule("Achievement diaries",diaries.createPanel());
        panel.addModule("Clues & STASH",clues.createPanel());javax.swing.JPanel travelPanel=travel.createPanel();travelPanel.add(pohTravel.createPanel());panel.addModule("Travel",travelPanel);
        panel.addModule("World hopper",worldHopperPanel.createPanel());
        panel.addModule("DPS comparison",dps.createPanel());panel.addModule("Training session",training.createPanel());
        panel.addModule("My routines",routines.createPanel());panel.addModule("Backup & recovery",recovery.createPanel());
        panel.addModule("Modules & diagnostics",modules.createPanel());
        java.util.List<String> destinations=java.util.Arrays.asList("Command dashboard","Find a plan","Plans","Activity hub","Guides","Goals","Loadouts","Bank & XP","Training session","Action progress","Skilling coach","Thieving coach","Agility coach","Equipment inspector","Hunter rumours","Sailing","Skill progress","Bank memory","Clue timers","Containers","Slayer","Boss academy","Minigames","Farm runs","Timers & harvest","Ground loot","Recovery","Effects & supplies","Account progress","Achievement diaries","Combat achievements","Music unlocks","Clues & STASH","Travel","World hopper","Tile markers","Item database","Max hit","DPS comparison","HUD","Rewards","My routines","Backup & recovery","Modules & diagnostics","Visibility","Wave references","Search");
        discover.setActions(destinations,name->panel.selectModule(name),this::openGuide,id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        setupSearchProviders();
        thieving.setOpenGuideAction(this::openGuide);
        hunterRumours.setOpenItemAction(this::openItem);hunterRumours.setGuideAction(this::openGuide);hunterRumours.setBankFocusAction(ids->focusBank("hunter-rumours",ids));
        hunterRumours.setRouteAction(point->onClient(()->{travel.routeOwned("hunter-rumours",point,"Hunter rumour");selectModule("Travel");}));
        sailing.setRouteAction(point->onClient(()->{if(sailing.canRoute(point)){travel.routeOwned("sailing",point,"Port task");selectModule("Travel");}}));
        skillProgress.setOpenAction(()->selectModule("Skill progress"));
        bankMemory.setOpenItemAction(this::openItem);
        clueTimers.setRouteAction(point->onClient(()->{travel.routeOwned("clue-timers",point,"Dropped clue");selectModule("Travel");}));
        equipmentInspector.setOpenItemAction(this::openItem);
        equipmentInspector.setOpenModuleAction(()->selectModule("Equipment inspector"));
        contextActions.setActions(this::openItem,npc->{sightTool.select(npc);selectModule("Visibility");},
            player->{equipmentInspector.select(player);selectModule("Equipment inspector");},
            query->{discover.openQuery(query);selectModule("Search");},this::selectModule);
        activityHub.setOpenGuideAction(this::openGuide);activityHub.setOpenModuleAction(this::selectModule);
        activityHub.setOpenGoalAction(id->{goals.openGoal(id);selectModule("Goals");});
        plans.setActions(this::selectModule,this::openGuide,this::openItem,
            point->onClient(()->travel.routeOwned("plan",point,"Plan destination")),
            this::startPlan);
        plans.setModuleChoices(destinations);plans.setOpenSkillAction((skill,target)->{goals.openSkillTarget(skill,target);selectModule("Goals");});
        plans.setOpenMethodAction(id->{activities.openMethod(id);selectModule("Bank & XP");});
        plans.setCompletionAction((plan,step)->{SharedActivitySession.Snapshot expected=activitySession.snapshot();onClient(()->{SharedActivitySession.Snapshot current=activitySession.snapshot();if(current.running()&&java.util.Objects.equals(expected.runId,current.runId)&&current.context!=null&&("plan:"+plan.id).equals(current.context.id)){activitySession.reachMilestone("plan:"+plan.id+":"+step.id,step.label,"Plans");if(plan.completed()==plan.steps.size())activitySession.finish();}});});
        opportunities.setActions(this::selectModule,this::openGuide,id->{activities.openMethod(id);selectModule("Bank & XP");});
        commandCenter.setActions(this::selectModule,this::openOpportunity);progress.setOpenOpportunityAction(this::openOpportunity);
        travel.setDataStore(data);travel.setGuideContextSupplier(guides::selectedGuide);travel.setFactsSupplier(opportunities::facts);travel.setOwnershipSupplier(ownership::snapshot);
        travel.setArrivalOptions(farmRuns::travelDestinations);
        travel.setTransportOptionsSupplier(pohTravel::routeOptions);travel.setTransportRevisionSupplier(pohTravel::revision);
        travel.setBankFocusAction(ids->focusBank("travel",ids));travel.setOpenItemAction(this::openItem);travel.setItemNameLookup(id->{ItemKnowledgeCatalog.ItemRef item=itemCatalog.item(id);return item==null?"Item "+id:item.name;});
        deaths.setSelectedLoadoutSupplier(loadouts::selected);deaths.setActivityGuideSupplier(()->{SharedActivitySession.Snapshot s=activitySession.snapshot();return s.context==null?null:s.context.guideId;});
        deaths.setBankFocusAction(ids->focusBank("recovery",ids));deaths.setOpenLoadoutsAction(()->selectModule("Loadouts"));deaths.setResumeGuideAction(this::openGuide);
        supplies.setOpenItemAction(this::openItem);field.setAdditionalRows(supplies::hudRows);
        loadouts.setChargeFacts(supplies::chargeTotals,supplies::supportedItemCharges);loadouts.setActiveEffects(field::activeEffectKeys,field::knownEffectKeys);
        dps.setOpenLoadoutsAction(()->selectModule("Loadouts"));
        routines.setBankFocusAction(ids->focusBank("routine",ids));routines.setOpenDiaryAction(id->{diaries.openDiary(id);selectModule("Achievement diaries");});
        diaries.setBankFocusAction(ids->focusBank("diary",ids));music.setRouteAction(point->onClient(()->{travel.routeOwned("music",point,"Music unlock");selectModule("Travel");}));
        music.setGoalAction(goals::addGoal);music.setBankFocusAction(ids->focusBank("music",ids));combatAchievements.setGoalAction(goals::addGoal);
        goals.setOpenProgressAction(this::openProgressGoal);goals.setOpenItemAction(this::openItem);goals.setBankFocusAction(ids->focusBank("goal",ids));
        goals.setCompletionAction(goal->onClient(()->activitySession.reachMilestone("goal:"+goal.id,goal.label==null?goal.skill()==null?"Goal completed":goal.skill().getName()+" goal completed":goal.label,"Goals")));
        clues.setBankFocusAction(ids->onClient(()->{if(ids.isEmpty())guideBankFilter.clearExternalTargets("clue");else guideBankFilter.setExternalTargets("clue",ids);}));
        routines.setActions(destinations,name->panel.selectModule(name));
        loadouts.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        loadouts.setBankFocusAction(ids->{guideBankFilter.setExternalTargets(ids);panel.selectModule("Bank & XP");});
        deaths.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        deaths.setRouteAction(point->{travel.routeOwned("recovery",point,"Recovery");panel.selectModule("Travel");});
        groundLoot.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        groundLoot.setTargetItems(()->{java.util.Set<Integer> ids=new java.util.LinkedHashSet<>(guideBankFilter.requiredItemIds());ids.addAll(goals.goalItemTargets());ids.addAll(clues.targetItemIds());return java.util.Collections.unmodifiableSet(ids);});
        progress.setGoalAction(goals::addGoal);progress.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        diaries.setGoalAction(goals::addGoal);diaries.setOpenGuideAction(this::openGuide);
        diaries.setRouteAction(point->{travel.routeOwned("diary",point,"Diary task");panel.selectModule("Travel");});
        diaries.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        clues.setOpenGuideAction(this::openGuide);clues.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});clues.setModuleAction(name->panel.selectModule(name));
        travel.setOpenGuideAction(this::openGuide);dps.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        recovery.setApplyAction(action->clientThread.invokeLater(()->{try{data.pauseWrites(this::shutDown);action.run();data.reload();startUp();}catch(Exception error){throw new IllegalStateException("Could not reload Runewarden",error);}}));
        registerModules();panel.setRuntime(modules);modules.setChangeAction(()->{if(panel!=null)panel.refreshModules();});
        rewardSettingsTool.setDemoAction(rewards::preview);
        music.setUnlockAction(track -> rewards.unlock(new RewardNotification(RewardNotification.Kind.MUSIC_UNLOCK,
            "Music unlocked", track.name, -1, 1, RarityTier.WHITE, "New music track", track.location, "", "", true)));
        combatAchievements.setCompletionAction(task -> rewards.unlock(new RewardNotification(RewardNotification.Kind.COMBAT_ACHIEVEMENT,
            "Combat achievement", task.name, -1, 1, achievementTier(task.difficulty),
            task.difficulty + " · " + task.points + (task.points == 1 ? " point" : " points"), task.boss, "", "", true)));
        music.setOpenGuideAction(this::openGuide);
        combatAchievements.setOpenGuideAction(this::openGuide);
        combatAchievements.setOpenItemAction(id -> { itemKnowledge.openItem(id); panel.selectModule("Item database"); });
        minigames.setGuideActions(() -> guides.selectedGuide() == null ? null : guides.selectedGuide().id,
            id -> { if (id == null) guides.clearSelection(); else guides.openAutomaticGuide(id); });
        minigames.setOpenGuideAction(this::openGuide);
        minigames.setRouteAction(point -> onClient(() -> {
            if (!minigames.canRouteHome(point)) return;
            travel.routeOwned("minigame", point, "Mahogany Homes customer"); selectModule("Travel");
        }));
        activities.setOpenGuideAction(this::openGuide);
        goals.setOpenGuideAction(this::openGuide);
        goals.setOpenMethodAction(id -> { panel.selectModule("Bank & XP"); activities.openMethod(id); });
        itemKnowledge.setOpenGuideAction(this::openGuide);
        guides.setOpenItemAction(id -> { itemKnowledge.openItem(id); panel.selectModule("Item database"); });
        encounters.setOpenSectionAction((id, section) -> { guides.openSection(id, section); panel.selectModule("Guides"); });
        encounters.setPreparationActions(() -> selectModule("Loadouts"),
            () -> selectModule("DPS comparison"), () -> selectModule("Recovery"));
        encounters.setCombatTargetAction(dps::prepareTarget);
        encounters.setOpenItemAction(id -> { itemKnowledge.openItem(id); panel.selectModule("Item database"); });
        slayer.setOpenGuideAction(this::openGuide);
        slayer.setOpenItemAction(id -> { itemKnowledge.openItem(id); panel.selectModule("Item database"); });
        farmRuns.setOpenGuideAction(() -> panel.selectModule("Guides"));
        farmRuns.setBankFocusAction(guideBankFilter::setExternalTargets);
        farmRuns.setRouteAction(point->onClient(()->travel.routeOwned("farm",point,"Next farm stop")));
        farmRuns.setNavigation(travel);
        farmRuns.setClearRouteAction(()->onClient(()->travel.clearOwned("farm")));
        guides.setAutomaticTravelAllowed(()->{FarmPatchPlanner.RunReading run=farmRuns.currentRun();return run==null||!run.active;});
        farmRuns.setLoadoutAction(()->selectModule("Loadouts"));
        farmRuns.setFinishAction(activitySession::finish);
        farmRuns.setStartAction(run->{if(run.profile.equals(configManager.getRSProfileKey())){activityHub.startContext(RunewardenCoordinator.farmContext(run));}});
        bankedExperience.setOpenItemAction(id->{itemKnowledge.openItem(id);panel.selectModule("Item database");});
        slayer.setOpenLoadoutsAction(()->panel.selectModule("Loadouts"));
        slayer.setRouteAction(point->{travel.routeOwned("slayer",point,"Slayer destination");panel.selectModule("Travel");});
        slayer.setBankFocusAction(ids->focusBank("slayer",ids));
        slayer.setStartActivityAction(prepared->{if(prepared.profile.equals(configManager.getRSProfileKey()))activityHub.startContext(RunewardenCoordinator.slayerContext(prepared,slayer.activityObservation()));});
        encounters.setActivityActions(entry->{if(entry!=null)activityHub.startContext(new SharedActivitySession.Context(SharedActivitySession.Kind.BOSS,"boss:"+entry.id,entry.title,entry.id,null,null,0));},
            (entry,outcome)->{SharedActivitySession.Snapshot expected=activitySession.snapshot();onClient(()->{SharedActivitySession.Snapshot state=activitySession.snapshot();if(entry!=null&&state.running()&&java.util.Objects.equals(expected.runId,state.runId)&&state.context!=null&&state.context.kind==SharedActivitySession.Kind.BOSS&&entry.id.equals(state.context.guideId)){if(!outcome.startsWith("Observed"))activitySession.observeOutcome("practice:"+outcome,"Manual "+outcome,1);activitySession.reachMilestone("attempt:"+state.runId,entry.title+" · "+outcome,"Boss academy");activitySession.finish();}});});
        tileMarkers.setBackAction(() -> { if (panel != null) panel.selectModule("Command dashboard"); });
        tileMarkers.setRotationAction(rotation -> {
            if (!running) return;
            configManager.setConfiguration(RunewardenConfig.GROUP, "fightCavesTargetRotation", rotation);
            configManager.setConfiguration(RunewardenConfig.GROUP, "waveEntryCoach", true);
            waveEntryCoach.selectRotation(rotation);
        });
        panel.selectModule("Command dashboard");
        navigation = NavigationButton.builder().tooltip("Runewarden").icon(icon()).priority(7).panel(panel).build();
        toolbar.addNavigation(navigation);
        harvestClock.setOpenAction(() ->
        {
            if (!running || panel == null || navigation == null) return;
            panel.selectModule("Command dashboard");
            toolbar.openPanel(navigation);
        });
        harvestClock.setOpenRunAction(group ->
        {
            if (!running || panel == null || navigation == null) return;
            farmRuns.openGroup(group);
            panel.selectModule("Farm runs");
            toolbar.openPanel(navigation);
        });
    }

    @Override
    protected void shutDown()
    {
        lifecycleEpoch++;
        running = false;keys.unregisterKeyListener(pauseKey);keys.unregisterKeyListener(restartKey);pauseKey.focusLost();restartKey.focusLost();
        activityHub.stop();plans.stop();opportunities.stop();supplies.stop();coordinator.clear();commandCenter.stop();
        loadouts.stop();field.stop();deaths.stop();groundLoot.stop();progress.stop();diaries.stop();clues.stop();travel.stop();pohTravel.stop();worldHopper.stop();dps.stop();training.stop();routines.stop();
        for(net.runelite.client.ui.overlay.Overlay overlay:field.overlays())overlayManager.remove(overlay);
        overlayManager.remove(groundLoot.overlay());overlayManager.remove(training.overlay());
        overlayManager.remove(worldPingOverlay);
        contextActions.stop();thieving.stop();agility.stop();actionProgress.stop();equipmentInspector.stop();
        hunterRumours.stop();sailing.stop();skillProgress.stop();bankMemory.stop();clueTimers.stop();containers.stop();
        overlayManager.remove(farmPatchOverlay);overlayManager.remove(thieving.hud());overlayManager.remove(thieving.scene());
        overlayManager.remove(agility.hud());overlayManager.remove(agility.scene());overlayManager.remove(actionProgress.overlay());
        overlayManager.remove(equipmentInspector.overlay());
        overlayManager.remove(hunterHud);overlayManager.remove(hunterScene);overlayManager.remove(sailing.hud());overlayManager.remove(sailing.scene());overlayManager.remove(skillProgress.hud());overlayManager.remove(skillProgress.skillTab());overlayManager.remove(clueTimers.overlay());overlayManager.remove(containers.overlay());
        activitySession.stop();ownership.stop();modules.clear();
        minigames.stop();
        overlayManager.remove(minigames.hud());
        overlayManager.remove(minigames.scene());
        music.stop();
        combatAchievements.stop();
        rewards.stop();
        overlayManager.remove(rewardPopup);
        overlayManager.remove(hoverCards);
        hoverCards.clear();
        hudSettings.stop();
        overlayManager.remove(inventoryPreview);
        inventoryPreview.clear();
        hudSession.stop();
        goals.stop();
        itemKnowledge.stop();
        combatCalculator.stop();
        slayer.stop();
        encounters.stop();
        runLoot.stop();
        tileMarkers.stop();
        overlayManager.remove(resourceTooltips);
        guides.stop();
        overlayManager.remove(resourceOverlay);
        sightTool.stop();
        waveExport.stop();
        waveEntryCoach.stop();
        overlayManager.remove(waveEntryOverlay);
        clientThread.invokeLater(guideBankFilter::stop);
        bankedExperience.stop();
        clientThread.invokeLater(skillingCoach::shutDown);
        overlayManager.remove(skillingCoachOverlay);
        overlayManager.remove(nodeTimerOverlay);
        harvestClock.stop();
        farmRuns.stop();
        dashboard.stop();
        if (navigation != null) { toolbar.removeNavigation(navigation); }
        navigation = null;
        panel = null;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!running) return;
        checkAccount();
        ownership.onGameTick();activitySession.onGameTick();
        hudSession.onGameTick();
        waveEntryCoach.onGameTick();
        modules.tick("Inventory preview",inventoryPreview::onGameTick);
        modules.tick("Max hit",combatCalculator::onGameTick);
        modules.tick("Goals",goals::onGameTick);
        activities.updateAccountFacts(goals.facts());
        modules.tick("Item database",itemKnowledge::onGameTick);
        modules.tick("Slayer",slayer::onGameTick);
        modules.tick("Boss academy",encounters::onGameTick);
        modules.tick("Bank & XP",()->{guideBankFilter.onGameTick();bankedExperience.onGameTick();});
        modules.tick("Skilling coach",()->skillingCoach.onGameTick(event));
        modules.tick("Thieving coach",thieving::onGameTick);modules.tick("Agility coach",agility::onGameTick);
        modules.tick("Action progress",actionProgress::onGameTick);modules.tick("Equipment inspector",equipmentInspector::onGameTick);
        modules.tick("Hunter rumours",hunterRumours::onGameTick);modules.tick("Sailing",sailing::onGameTick);modules.tick("Skill progress",skillProgress::onGameTick);
        modules.tick("Bank memory",bankMemory::onGameTick);modules.tick("Clue timers",clueTimers::onGameTick);modules.tick("Containers",containers::onGameTick);
        modules.tick("Timers & harvest",harvestClock::onGameTick);
        modules.tick("Farm runs",farmRuns::onGameTick);
        modules.tick("Run loot",runLoot::onGameTick);
        modules.tick("Music unlocks",music::onGameTick);
        modules.tick("Combat achievements",combatAchievements::onGameTick);
        modules.tick("Minigames",minigames::onGameTick);
        modules.tick("Rewards",rewards::onGameTick);
        modules.tick("Effects & supplies",()->{supplies.onGameTick();field.onGameTick();});modules.tick("Loadouts",loadouts::onGameTick);
        modules.tick("Recovery",deaths::onGameTick);modules.tick("Ground loot",groundLoot::onGameTick);
        progress.onGameTick();modules.tick("Achievement diaries",diaries::onGameTick);modules.tick("Clues & STASH",clues::onGameTick);
        modules.tick("Travel",()->{pohTravel.onGameTick();travel.onGameTick();});modules.tick("DPS comparison",dps::onGameTick);
        modules.tick("World hopper",worldHopper::onGameTick);
        training.setTravelSuppressed(travel.snapshot().target!=null&&client.getLocalDestinationLocation()!=null);
        modules.tick("Training session",training::onGameTick);modules.tick("My routines",routines::onGameTick);
        // Follow this tick's native task and confirmed training evidence before publishing selection.
        modules.tick("Guides",()->{GuideCatalog.Entry selected=guides.selectedGuide();
            if(!modules.enabled("Minigames")&&selected!=null&&minigames.isGuide(selected.id))minigames.refreshForGuide();
            guides.onGameTick();});syncActivitySelection();
        modules.tick("Plans",plans::onGameTick);modules.tick("Find a plan",opportunities::onGameTick);coordinator.onGameTick();modules.tick("Activity hub",activityHub::onGameTick);modules.tick("Command dashboard",()->{dashboard.onGameTick();commandCenter.onGameTick();});
        SharedActivitySession.Snapshot completed=activitySession.snapshot();
        if(completed.running()&&completed.context!=null&&completed.context.skill!=null&&completed.context.targetXp>0
            &&client.getSkillExperience(completed.context.skill)>=completed.context.targetXp)activitySession.finish();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!running) return;
        checkAccount();
        ownership.onItemContainerChanged(event.getContainerId());
        if(modules.enabled("Loadouts"))loadouts.onItemContainerChanged(event.getContainerId());
        if(modules.enabled("Recovery"))deaths.onItemContainerChanged(event.getContainerId());
        progress.onItemContainerChanged(event);
        if(modules.enabled("Training session"))training.onItemContainerChanged(event);
        if(modules.enabled("Action progress"))actionProgress.onItemContainerChanged(event);
        if(modules.enabled("Containers"))containers.onItemContainerChanged(event);
        if(modules.enabled("Bank & XP"))bankedExperience.onItemContainerChanged(event.getContainerId());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!running) return;
        if(modules.enabled("World hopper"))worldHopper.onGameStateChanged(event);
        field.onGameStateChanged(event);deaths.onGameStateChanged(event);groundLoot.onGameStateChanged(event);
        activityHub.onGameStateChanged(event);
        encounters.onGameStateChanged(event);
        contextActions.clear();thieving.onGameStateChanged(event);agility.onGameStateChanged(event);actionProgress.clear();
        sailing.onGameStateChanged(event);skillProgress.onGameStateChanged(event);clueTimers.onGameStateChanged(event.getGameState());
        guides.onGameStateChanged(event.getGameState());
        waveEntryCoach.onGameStateChanged(event.getGameState());
        guideBankFilter.onGameStateChanged(event.getGameState());
        bankedExperience.onGameStateChanged(event.getGameState());
        if(modules.enabled("Skilling coach"))skillingCoach.onGameStateChanged(event);
        minigames.onSceneChanged();
        if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING
            || event.getGameState() == GameState.CONNECTION_LOST)
        {
            activitySession.suspend();training.onGameTick();
            runLoot.suspend();
            clearAccount();
        }
    }

    @Subscribe public void onServerNpcLoot(ServerNpcLoot event)
    {
        if (running) { checkAccount(); if(modules.enabled("Run loot"))runLoot.onServerNpcLoot(event); if(modules.enabled("Rewards"))rewards.onServerNpcLoot(event);coordinator.onServerNpcLoot(event);if(modules.enabled("Boss academy"))encounters.onServerNpcLoot(event); }
    }

    @Subscribe public void onPlayerLootReceived(PlayerLootReceived event)
    {
        if (running&&modules.enabled("Run loot")) { checkAccount(); runLoot.onPlayerLootReceived(event); }
    }

    @Subscribe public void onLootReceived(LootReceived event)
    {
        if (running) { checkAccount();if(modules.enabled("Run loot"))runLoot.onLootReceived(event);if(modules.enabled("Rewards"))rewards.onLootReceived(event); }
    }

    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if(running){field.onConfigChanged(event);deaths.onConfigChanged(event);groundLoot.onConfigChanged(event);equipmentInspector.onConfigChanged(event);clueTimers.onConfigChanged(event);}
        if(running&&ModuleRuntime.GROUP.equals(event.getGroup()))clientThread.invokeLater(modules::changed);
        if(running&&RunewardenDataStore.GROUP.equals(event.getGroup()))recovery.refresh();
        if (running && RunewardenConfig.GROUP.equals(event.getGroup()))
        { hudSettings.onConfigChanged(event.getKey()); itemKnowledge.onConfigChanged(event.getKey()); }
        if (running && RewardSettings.GROUP.equals(event.getGroup()))
        { rewardSettings.reload(); clientThread.invokeLater(rewards::preferencesChanged); }
        if (running && MinigameCoach.GROUP.equals(event.getGroup())) minigames.reloadPreferences();
        if (running && VarlamoreThievingCoach.GROUP.equals(event.getGroup())) thieving.reloadPreferences();
        if (running && SailingCoach.GROUP.equals(event.getGroup())) sailing.reloadPreferences();
    }

    private void checkAccount()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { return; }
        String name = PlayerIdentity.normalizedName(client.getLocalPlayer().getName());
        if (!name.equals(activeAccount))
        {
            if (activeAccount != null)
            {
                guides.onProfileChanged();
            }
            clearAccount();
            activeAccount = name;
        }
    }

    private void clearAccount()
    {
        lifecycleEpoch++;
        waveEntryCoach.onGameStateChanged(GameState.LOGIN_SCREEN);
        activeAccount = null;activitySelection.reset();recovery.refresh();discover.clearAccount();
        activityHub.clear();plans.clear();opportunities.clear();supplies.clear();coordinator.clear();commandCenter.clear();
        ownership.clear();loadouts.clear();field.clear();deaths.clear();groundLoot.clear();progress.clear();diaries.clear();clues.clear();travel.clear();dps.clear();training.clear();routines.clear();
        bankedExperience.reset();
        harvestClock.clear();
        farmRuns.clear();
        dashboard.clear();
        inventoryPreview.clear();
        goals.clear();
        itemKnowledge.clearProfile();
        combatCalculator.clear();
        slayer.clear();
        encounters.clear();
        hoverCards.clear();
        music.clear();
        combatAchievements.clear();
        minigames.clear();
        contextActions.clear();thieving.clear();agility.clear();actionProgress.clear();equipmentInspector.clear();
        hunterRumours.clear();sailing.clear();skillProgress.clear();bankMemory.clear();clueTimers.clear();containers.clear();
        activities.updateAccountFacts(null);
        if (panel != null) panel.resetNavigation();
        rewards.clear();
    }

    @Subscribe public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        if (!running) return;
        guides.onProfileChanged();
        worldHopper.onProfileChanged();
        activitySession.clear();
        runLoot.suspend();
        clearAccount();
    }

    @Subscribe(priority = -1) public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (running && modules.enabled("Bank & XP")) guideBankFilter.onScriptCallbackEvent(event);
    }

    @Subscribe public void onPostClientTick(PostClientTick event)
    {
        if (!running) return;
        sightTool.onClientTick();
        if (modules.enabled("Action progress")) actionProgress.onClientTick();
    }

    @Subscribe public void onScriptPreFired(ScriptPreFired event)
    { if (running) { if(modules.enabled("Skilling coach"))skillingCoach.onScriptPreFired(event);if(modules.enabled("Rewards"))rewards.onScriptPreFired(event);progress.onScriptPreFired(event); } }
    @Subscribe public void onStatChanged(StatChanged event) { if (running) { if(modules.enabled("Activity hub"))activityHub.onStatChanged(event);if(modules.enabled("Skilling coach"))skillingCoach.onStatChanged(event);if(modules.enabled("Training session"))training.onStatChanged(event);if(modules.enabled("Action progress"))actionProgress.onStatChanged(event);if(modules.enabled("Agility coach"))agility.onStatChanged(event);if(modules.enabled("Hunter rumours"))hunterRumours.onStatChanged(event);if(modules.enabled("Skill progress"))skillProgress.onStatChanged(event);if(modules.enabled("Containers"))containers.onStatChanged(event); } }
    @Subscribe public void onFocusChanged(net.runelite.api.events.FocusChanged event){if(running&&modules.enabled("Training session"))training.onFocusChanged(event);}
    @Subscribe public void onAnimationChanged(AnimationChanged event) { if(!running)return;if(modules.enabled("Skilling coach"))skillingCoach.onAnimationChanged(event);if(modules.enabled("Action progress"))actionProgress.onAnimationChanged(event); }
    @Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
    { if (running) { if(modules.enabled("World hopper"))worldHopper.onMenuOptionClicked(event);if(modules.enabled("Skilling coach"))skillingCoach.onMenuOptionClicked(event); if(modules.enabled("Max hit"))combatCalculator.onMenuOptionClicked(event);if(modules.enabled("Travel"))travel.onMenuOptionClicked(event);if(modules.enabled("Training session"))training.onMenuOptionClicked(event);if(modules.enabled("Farm runs"))farmRuns.onMenuOptionClicked(event);if(modules.enabled("Thieving coach"))thieving.onMenuOptionClicked(event);if(modules.enabled("Action progress"))actionProgress.onMenuOptionClicked(event);if(modules.enabled("Containers"))containers.onMenuOptionClicked(event); } }
    @Subscribe public void onMenuOpened(MenuOpened event){if(running)contextActions.onMenuOpened(event);}
    @Subscribe public void onMenuEntryAdded(MenuEntryAdded event){if(running){if(modules.enabled("Skill progress"))skillProgress.onMenuEntryAdded(event);if(modules.enabled("World hopper"))worldHopper.onMenuEntryAdded(event);}}
    @Subscribe public void onChatMessage(ChatMessage event)
    { if (running) { if(modules.enabled("World hopper"))worldHopper.onChatMessage(event);if(modules.enabled("Max hit"))combatCalculator.onChatMessage(event);if(modules.enabled("Rewards"))rewards.onChatMessage(event);if(modules.enabled("Minigames"))minigames.onChatMessage(event);if(modules.enabled("Farm runs"))farmRuns.onChatMessage(event);if(modules.enabled("Thieving coach"))thieving.onChatMessage(event);if(modules.enabled("Hunter rumours"))hunterRumours.onChatMessage(event);if(modules.enabled("Containers"))containers.onChatMessage(event);progress.onChatMessage(event); } }

    @Subscribe public void onWorldsFetch(net.runelite.client.events.WorldsFetch event)
    { if(running&&modules.enabled("World hopper"))worldHopper.onWorldsFetch(event); }
    @Subscribe public void onWorldListLoad(net.runelite.api.events.WorldListLoad event)
    { if(running&&modules.enabled("World hopper"))worldHopper.onWorldListLoad(event); }
    @Subscribe public void onCommandExecuted(net.runelite.api.events.CommandExecuted event)
    { if(running&&modules.enabled("World hopper"))worldHopper.onCommandExecuted(event); }
    @Subscribe public void onGameObjectDespawned(GameObjectDespawned event)
    { if (running) { if(modules.enabled("Skilling coach"))skillingCoach.onGameObjectDespawned(event);if(modules.enabled("Minigames"))minigames.onGameObjectDespawned(event.getGameObject());if(modules.enabled("Thieving coach"))thieving.onGameObjectDespawned(event);if(modules.enabled("Sailing"))sailing.onGameObjectDespawned(event);if(modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),event.getGameObject(),null); } }
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned event)
    { if (running) { if(modules.enabled("Skilling coach"))skillingCoach.onGameObjectSpawned(event);if(modules.enabled("Minigames"))minigames.onGameObjectSpawned(event.getGameObject());if(modules.enabled("Thieving coach"))thieving.onGameObjectSpawned(event);if(modules.enabled("Sailing"))sailing.onGameObjectSpawned(event);if(modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),null,event.getGameObject()); } }
    @Subscribe public void onWallObjectSpawned(WallObjectSpawned event){if(!running)return;if(modules.enabled("Thieving coach"))thieving.onWallObjectSpawned(event);if(modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),null,event.getWallObject());}
    @Subscribe public void onWallObjectDespawned(WallObjectDespawned event){if(!running)return;if(modules.enabled("Thieving coach"))thieving.onWallObjectDespawned(event);if(modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),event.getWallObject(),null);}
    @Subscribe public void onGroundObjectSpawned(GroundObjectSpawned event){if(running&&modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),null,event.getGroundObject());}
    @Subscribe public void onGroundObjectDespawned(GroundObjectDespawned event){if(running&&modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),event.getGroundObject(),null);}
    @Subscribe public void onDecorativeObjectSpawned(DecorativeObjectSpawned event){if(running&&modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),null,event.getDecorativeObject());}
    @Subscribe public void onDecorativeObjectDespawned(DecorativeObjectDespawned event){if(running&&modules.enabled("Agility coach"))agility.onTileObject(event.getTile(),event.getDecorativeObject(),null);}
    @Subscribe public void onActorDeath(ActorDeath event) { if (running) {guides.onActorDeath(event);if(modules.enabled("Skilling coach"))skillingCoach.onActorDeath(event);if(modules.enabled("Recovery"))deaths.onActorDeath(event);if(modules.enabled("Boss academy"))encounters.onActorDeath(event);} }
    @Subscribe public void onNpcSpawned(NpcSpawned event){if(running&&modules.enabled("Minigames"))minigames.onNpcSpawned(event);}
    @Subscribe public void onNpcDespawned(NpcDespawned event){if(running&&modules.enabled("Minigames"))minigames.onNpcDespawned(event);}
    @Subscribe public void onWidgetLoaded(WidgetLoaded event){if(!running)return;if(modules.enabled("Training session"))training.onWidgetLoaded(event);if(modules.enabled("Containers"))containers.onWidgetLoaded(event);}
    @Subscribe public void onVarbitChanged(VarbitChanged event){if(!running)return;if(modules.enabled("Effects & supplies"))field.onVarbitChanged(event);if(modules.enabled("Agility coach"))agility.onVarbitChanged(event);if(modules.enabled("Containers"))containers.onVarbitChanged(event);}
    @Subscribe public void onScriptPostFired(ScriptPostFired event){if(running)progress.onScriptPostFired(event);}
    // A disabled Travel module can still owe a receiver-confirmed clear of its previous route.
    @Subscribe public void onPluginMessage(PluginMessage event){if(running)travel.onPluginMessage(event);}
    @Subscribe public void onItemSpawned(ItemSpawned event){if(!running)return;if(modules.enabled("Ground loot"))groundLoot.onItemSpawned(event);if(modules.enabled("Agility coach"))agility.onItemSpawned(event);if(modules.enabled("Clue timers"))clueTimers.onItemSpawned(event);}
    @Subscribe public void onItemDespawned(ItemDespawned event){if(!running)return;if(modules.enabled("Ground loot"))groundLoot.onItemDespawned(event);if(modules.enabled("Agility coach"))agility.onItemDespawned(event);if(modules.enabled("Clue timers"))clueTimers.onItemDespawned(event);}
    @Subscribe public void onItemQuantityChanged(ItemQuantityChanged event){if(!running)return;if(modules.enabled("Ground loot"))groundLoot.onItemQuantityChanged(event);if(modules.enabled("Clue timers"))clueTimers.onItemQuantityChanged(event);}
    @Subscribe public void onWorldViewUnloaded(WorldViewUnloaded event){if(!running)return;groundLoot.onWorldViewUnloaded(event);sailing.onWorldViewUnloaded(event);clueTimers.onWorldViewUnloaded(event);}

    /** Optional companion uses Quest Helper's public API; our Hub plugin has no binary dependency on it. */
    public void setQuestHelperAction(java.util.function.Function<String, Boolean> action)
    { guides.setQuestHelperAction(action); }
    private void openGuide(String id)
    {
        if (panel != null) panel.selectModule("Guides");
        guides.openGuide(id);
    }
    private void endRun()
    {
        SharedActivitySession.Snapshot expected=activitySession.snapshot();
        // Dismiss immediately, including a manually browsed guide without a measured run.
        guides.clearSelection();minigames.dismissGuide();
        onClient(()->{
            SharedActivitySession.Snapshot current=activitySession.snapshot();
            if(java.util.Objects.equals(expected.profile,current.profile)&&java.util.Objects.equals(expected.runId,current.runId))
            { farmRuns.stopActiveRun(); activitySession.finish(); }
        });
    }
    private void onRunFinished(SharedActivitySession.Snapshot finished)
    {
        guides.clearSelection();minigames.dismissGuide();farmRuns.stopActiveRun();guideBankFilter.clearExternalTargets();
        if(modules.enabled("Training session"))training.onGameTick();
    }
    private void onRunStarted(SharedActivitySession.Snapshot started)
    {
        minigames.resumeGuide();long expected=lifecycleEpoch, guideGeneration=guides.selectionGeneration();
        if(modules.enabled("Guides")&&started.context!=null&&started.context.guideId!=null
            &&started.context.kind!=SharedActivitySession.Kind.MINIGAME&&!minigames.isGuide(started.context.guideId))javax.swing.SwingUtilities.invokeLater(()->{
            SharedActivitySession.Snapshot current=activitySession.snapshot();
            if(running&&expected==lifecycleEpoch&&guideGeneration==guides.selectionGeneration()&&current.state!=SharedActivitySession.State.FINISHED
                &&java.util.Objects.equals(started.profile,current.profile)&&java.util.Objects.equals(started.runId,current.runId))guides.openForRun(started.context.guideId);
        });
    }
    private void selectModule(String title){RunewardenPanel current=panel;if(current!=null)current.selectModule(title);}
    private void startPlan(WorkflowPlan plan)
    {
        if(plan==null||!plan.valid())return;WorkflowPlan.TrainingContext training=plan.trainingContext(activityCatalog);
        SharedActivitySession.Context context;
        if(training!=null)context=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"plan:"+plan.id,plan.name,training.guideId,training.methodId,training.skill,training.targetXp);
        else{String guide=plan.primaryGuideId();GuideCatalog.Entry entry=guide==null?null:guideCatalog.entry(guide);context=new SharedActivitySession.Context(entry!=null&&"quest".equals(entry.category)?SharedActivitySession.Kind.QUEST:SharedActivitySession.Kind.OTHER,"plan:"+plan.id,plan.name,guide,null,null,0);}
        activityHub.startContext(context);selectModule("Activity hub");
    }
    private void openItem(int id){itemKnowledge.openItem(id);selectModule("Item database");}
    private void onClient(Runnable action)
    {String owner=configManager.getRSProfileKey();long ticket=lifecycleEpoch;clientThread.invokeLater(()->{if(running&&ticket==lifecycleEpoch&&owner!=null&&owner.equals(configManager.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN)action.run();});}
    private void focusBank(String owner,java.util.Set<Integer> ids)
    {java.util.Set<Integer> copy=java.util.Set.copyOf(ids);onClient(()->{guideBankFilter.setExternalTargets(owner,copy);selectModule("Bank & XP");});}
    private void openProgressGoal(GoalPlan goal)
    {
        if(goal==null)return;
        try{switch(goal.kind){case "ca":combatAchievements.openTask(Integer.parseInt(goal.referenceKey));selectModule("Combat achievements");return;case "music":music.openTrack(Integer.parseInt(goal.referenceKey));selectModule("Music unlocks");return;case "diary":diaries.openDiary(goal.referenceKey);selectModule("Achievement diaries");return;default:break;}}catch(NumberFormatException ignored){}
        if(goal.itemIds!=null&&!goal.itemIds.isEmpty())openItem(goal.itemIds.get(0));else selectModule("Account progress");
    }
    private void openOpportunity(AccountProgressTool.Opportunity opportunity)
    {
        if(opportunity==null)return;
        try{if("Combat achievements".equals(opportunity.module))combatAchievements.openTask(Integer.parseInt(opportunity.reference));else if("Music unlocks".equals(opportunity.module))music.openTrack(Integer.parseInt(opportunity.reference));else if("Achievement diaries".equals(opportunity.module))diaries.openDiary(opportunity.reference);}catch(NumberFormatException ignored){}
        if("Account progress".equals(opportunity.module)&&!opportunity.itemTargets.isEmpty())openItem(opportunity.itemTargets.iterator().next());else selectModule(opportunity.module);
    }
    private void setupSearchProviders()
    {
        discover.clearProviders();
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();
            for(GoalPlan goal:goals.goalPlans()){String name=goal.label!=null&&!goal.label.isEmpty()?goal.label:goal.skill()!=null?goal.skill().getName()+" goal":goal.questGuideId==null?goal.kind:goal.questGuideId.substring(6);if(name.toLowerCase(java.util.Locale.ROOT).contains(query))found.add(new DiscoverTool.Hit("Goals",name,goal.kind,()->{goals.openGoal(goal.id);selectModule("Goals");}));}
            for(WorkflowPlan plan:plans.plans())if(plan.name.toLowerCase(java.util.Locale.ROOT).contains(query))found.add(new DiscoverTool.Hit("Plans",plan.name,plan.completed()+" / "+plan.steps.size()+" milestones",()->{plans.select(plan.id);selectModule("Plans");}));return found;});
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();for(CombatAchievementCatalog.Task task:achievementCatalog.tasks())if((task.name+" "+task.boss).toLowerCase(java.util.Locale.ROOT).contains(query)){found.add(new DiscoverTool.Hit("Combat achievements",task.name,task.difficulty+" · "+task.boss,()->{combatAchievements.openTask(task.id);selectModule("Combat achievements");}));if(found.size()>=60)break;}return found;});
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();for(MusicUnlockCatalog.Track track:musicCatalog.tracks())if(track.search.contains(query)){found.add(new DiscoverTool.Hit("Music unlocks",track.name,track.location,()->{music.openTrack(track.id);selectModule("Music unlocks");}));if(found.size()>=60)break;}return found;});
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();for(MobKnowledgeCatalog.Entry mob:mobCatalog.search(query)){found.add(new DiscoverTool.Hit("Monsters",mob.name+" · "+mob.version,"NPC #"+mob.id,()->onClient(()->{dps.selectTargetNow(mob.id,mob.version);selectModule("DPS comparison");})));if(found.size()>=60)break;}return found;});
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();OwnershipLedger.View state=ownership.snapshot();if(!state.active||!itemCatalog.loaded())return found;
            for(ItemKnowledgeCatalog.ItemRef item:itemCatalog.search(query)){java.util.List<String> locations=new java.util.ArrayList<>();for(java.util.Map.Entry<OwnershipLedger.Storage,OwnershipLedger.Observation> storage:state.storages.entrySet()){long quantity=storage.getValue().quantity(item.id);if(quantity>0)locations.add(storage.getKey().label+" "+quantity+(storage.getValue().currentSession?" · last seen":" · saved"));}
                if(!locations.isEmpty())found.add(new DiscoverTool.Hit("Your storage",item.name,String.join(" / ",locations),()->openItem(item.id)));if(found.size()>=60)break;}return found;});
        discover.addProvider(query->{java.util.List<DiscoverTool.Hit> found=new java.util.ArrayList<>();for(AccountProgressTool.Opportunity o:progress.opportunities())if((o.title+" "+o.detail).toLowerCase(java.util.Locale.ROOT).contains(query))found.add(new DiscoverTool.Hit("Unlocks & opportunities",o.title,o.detail,()->openOpportunity(o)));return found;});
    }
    private JPanel effectsPanel(){JPanel content=SidebarUi.column();content.add(field.createPanel());content.add(supplies.createPanel());return content;}
    private void syncActivitySelection()
    {
        activitySelection.update(minigames.reading(),guides.selectedGuide(),activitySession.snapshot().selected,activitySession::select);
    }
    private void registerModules()
    {
        modules.registerStarted("Guides",guides::start,guides::stop);
        modules.registerStarted("Command dashboard",()->{dashboard.start();commandCenter.start();},()->{dashboard.stop();commandCenter.stop();});
        modules.registerStarted("Activity hub",activityHub::start,activityHub::stop);
        modules.registerStarted("Plans",plans::start,plans::stop);modules.registerStarted("Find a plan",opportunities::start,opportunities::stop);
        modules.registerDeferred("Bank & XP",()->{guideBankFilter.start();bankedExperience.start();},()->{guideBankFilter.stop();bankedExperience.stop();});
        modules.registerStarted("Loadouts",loadouts::start,loadouts::stop);modules.registerStarted("Effects & supplies",()->{supplies.start();field.start();},()->{supplies.stop();field.stop();});
        modules.registerStarted("Recovery",deaths::start,deaths::stop);modules.registerStarted("Ground loot",groundLoot::start,groundLoot::stop);
        modules.registerStarted("Achievement diaries",diaries::start,diaries::stop);modules.registerStarted("Clues & STASH",clues::start,clues::stop);
        modules.registerStarted("Travel",()->{pohTravel.start();travel.start();},()->{travel.stop();pohTravel.stop();});modules.registerStarted("DPS comparison",dps::start,dps::stop);
        modules.registerStarted("World hopper",worldHopper::start,worldHopper::stop);
        modules.registerStarted("Training session",training::start,training::stop);modules.registerStarted("My routines",routines::start,routines::stop);
        modules.registerStarted("Goals",goals::start,goals::stop);modules.registerStarted("Slayer",slayer::start,slayer::stop);
        modules.registerStarted("Boss academy",encounters::start,encounters::stop);modules.registerStarted("Item database",itemKnowledge::start,itemKnowledge::stop);
        modules.registerStarted("Max hit",combatCalculator::start,combatCalculator::stop);modules.registerStarted("Run loot",runLoot::start,runLoot::stop);
        modules.registerStarted("Farm runs",farmRuns::start,farmRuns::stop);modules.registerStarted("Timers & harvest",harvestClock::start,harvestClock::stop);
        modules.registerStarted("Music unlocks",music::start,music::stop);modules.registerStarted("Combat achievements",combatAchievements::start,combatAchievements::stop);
        modules.registerStarted("Minigames",minigames::start,minigames::stop);modules.registerStarted("Rewards",rewards::start,rewards::stop);
        modules.registerDeferred("Skilling coach",skillingCoach::startUp,skillingCoach::shutDown);
        modules.registerStarted("Thieving coach",thieving::start,thieving::stop);modules.registerStarted("Agility coach",agility::start,agility::stop);
        modules.registerStarted("Action progress",actionProgress::start,actionProgress::stop);modules.registerStarted("Equipment inspector",equipmentInspector::start,equipmentInspector::stop);
        modules.registerStarted("Hunter rumours",hunterRumours::start,hunterRumours::stop);modules.registerStarted("Sailing",sailing::start,sailing::stop);modules.registerStarted("Skill progress",skillProgress::start,skillProgress::stop);
        modules.registerStarted("Bank memory",bankMemory::start,bankMemory::stop);modules.registerStarted("Clue timers",clueTimers::start,clueTimers::stop);modules.registerStarted("Containers",containers::start,containers::stop);
    }

    private JPanel commandDashboardPanel()
    {
        JPanel content = SidebarUi.column();content.add(commandCenter.createPanel());
        JPanel actions = new JPanel(new GridLayout(0, 2, 6, 6));
        actions.setOpaque(false);
        String[][] destinations = {{"Goals", "Goals"}, {"Slayer", "Slayer"}, {"Bosses", "Boss academy"},
            {"Guides", "Guides"}, {"Farm runs", "Farm runs"}, {"Timers", "Timers & harvest"},
            {"Run loot", "Run loot"}, {"Markers", "Tile markers"}, {"Items", "Item database"},
            {"Bank & XP", "Bank & XP"}, {"Max hit", "Max hit"}, {"HUD", "HUD"},
            {"Minigames", "Minigames"}, {"Music", "Music unlocks"},
            {"Combat CAs", "Combat achievements"}, {"Rewards", "Rewards"},
            {"Search", "Search"}, {"Loadouts", "Loadouts"}, {"Diaries", "Achievement diaries"},
            {"Clues", "Clues & STASH"}, {"Travel", "Travel"}, {"Training", "Training session"}};
        for (String[] destination : destinations)
        {
            javax.swing.JButton button = new javax.swing.JButton(destination[0]);
            button.setToolTipText(destination[1]);
            button.setBackground(new Color(35, 34, 30));
            button.setForeground(new Color(224, 190, 113));
            button.setFont(GuideText.FONT);
            button.setMargin(new java.awt.Insets(6, 4, 6, 4));
            button.addActionListener(event -> panel.selectModule(destination[1]));
            actions.add(button);
        }
        content.add(harvestClock.createDashboardPanel());
        content.add(SidebarUi.disclosure("All command shortcuts",actions));
        return content;
    }

    private static RarityTier achievementTier(String difficulty)
    {
        if ("Grandmaster".equals(difficulty) || "Master".equals(difficulty)) return RarityTier.GOLD;
        if ("Elite".equals(difficulty)) return RarityTier.PURPLE;
        if ("Hard".equals(difficulty)) return RarityTier.BLUE;
        if ("Medium".equals(difficulty)) return RarityTier.GREEN;
        return RarityTier.WHITE;
    }

    private JCheckBox toggle(String label, String key, boolean selected)
    {
        JCheckBox choice = new JCheckBox(label, selected);
        choice.setOpaque(false);
        choice.setForeground(new Color(233, 229, 219));
        choice.addActionListener(event -> configManager.setConfiguration(RunewardenConfig.GROUP, key, choice.isSelected()));
        return choice;
    }

    private static JPanel withSettings(JComponent content, JCheckBox... choices)
    {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setOpaque(false);
        JPanel settings = new JPanel(new GridLayout(0, 1, 0, 6));
        settings.setOpaque(false);
        for (JCheckBox choice : choices) settings.add(choice);
        wrapper.add(settings, BorderLayout.NORTH);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private static BufferedImage icon()
    {
        BufferedImage result = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(new Color(17, 17, 20));
        graphics.fillRoundRect(1, 1, 30, 30, 7, 7);
        graphics.setColor(new Color(224, 190, 113));
        graphics.drawRoundRect(1, 1, 29, 29, 7, 7);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        graphics.drawString("R", 9, 23);
        graphics.dispose();
        return result;
    }
}
