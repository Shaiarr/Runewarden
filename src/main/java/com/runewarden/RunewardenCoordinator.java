/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ServerNpcLoot;

/** Connects exact native activity identities to a run; reading another workflow never replaces it. */
@Singleton
final class RunewardenCoordinator
{
    private final Client client;private final ConfigManager config;private final SharedActivitySession session;
    private final Supplier<MinigameRunProgress.View> minigames;private final Supplier<SlayerCoachTool.ActivityObservation> slayer;
    private final Supplier<FarmPatchPlanner.RunReading> farms;private final GuideCatalog guides;private final ActivityXpCatalog methods;
    private final Supplier<AccountProgressTool.Snapshot> account;private final BiConsumer<String,Long> currency;
    private String profile,run,farmIdentity,miniIdentity;private SlayerCoachTool.ActivityObservation previousSlayer;
    private long replants;private int farmStops;private MinigameRunProgress.View previousMini;
    private Map<String,QuestState> previousQuests=Map.of();private final Set<String> phases=new HashSet<>();
    private String lootRun;private int lootTick=-1;private final Set<Object> seenLoot=Collections.newSetFromMap(new IdentityHashMap<>());
    @Inject RunewardenCoordinator(Client client,ConfigManager config,SharedActivitySession session,MinigameCoach minigames,
        SlayerCoachTool slayer,FarmRunsTool farms,GuideCatalog guides,ActivityXpCatalog methods,AccountProgressTool account,OwnershipLedger ownership)
    {this(client,config,session,minigames::sessionView,slayer::activityObservation,farms::currentRun,guides,methods,account::reading,ownership::observeCurrency);}
    /** Typed immutable readers keep integration fixtures independent of native final service implementations. */
    RunewardenCoordinator(Client client,ConfigManager config,SharedActivitySession session,Supplier<MinigameRunProgress.View> minigames,
        Supplier<SlayerCoachTool.ActivityObservation> slayer,Supplier<FarmPatchPlanner.RunReading> farms,GuideCatalog guides,ActivityXpCatalog methods,
        Supplier<AccountProgressTool.Snapshot> account,BiConsumer<String,Long> currency)
    {this.client=client;this.config=config;this.session=session;this.minigames=minigames;this.slayer=slayer;this.farms=farms;this.guides=guides;this.methods=methods;this.account=account;this.currency=currency;}
    void clear()
    {profile=run=farmIdentity=miniIdentity=lootRun=null;previousSlayer=null;previousMini=null;replants=0;farmStops=0;previousQuests=Map.of();phases.clear();seenLoot.clear();lootTick=-1;}
    void onGameTick()
    {
        SharedActivitySession.Snapshot s=session.snapshot();Player player=client.getLocalPlayer();
        if(client.getGameState()!=GameState.LOGGED_IN||player==null||s.profile==null||!s.profile.equals(config.getRSProfileKey())){clear();return;}
        if(!s.profile.equals(profile)){clear();profile=s.profile;}
        MinigameRunProgress.View mini=minigames.get();if(mini==null||mini.profile!=null&&!profile.equals(mini.profile))mini=MinigameRunProgress.View.EMPTY;
        SlayerCoachTool.ActivityObservation task=slayer.get();if(task==null||!profile.equals(task.profile))task=SlayerCoachTool.ActivityObservation.empty();
        FarmPatchPlanner.RunReading farm=farms.get();if(farm!=null&&!profile.equals(farm.profile))farm=null;
        SharedActivitySession.Context detected=null;
        if(!bankOpen())
        {
            if(mini.active())detected=minigameContext(mini);
            else
            {
                NPC npc=player.getInteracting() instanceof NPC?(NPC)player.getInteracting():null;
                if(npc!=null&&npc.getName()!=null)
                {
                    GuideCatalog.Entry boss=boss(npc.getName());
                    if(boss!=null)detected=new SharedActivitySession.Context(SharedActivitySession.Kind.BOSS,"boss:"+boss.id,npc.getName(),boss.id,null,null,0);
                    else if(task.remaining>0&&task.npcIds.contains(npc.getId()))detected=slayerContext(task);
                }
                if(detected==null&&farm!=null&&farm.active)detected=farmContext(farm);
            }
        }
        // One highest-priority actual context, or an explicit absence. A task in the sidebar alone is not activity.
        session.offerDetected(detected);s=session.snapshot();boolean changedRun=!Objects.equals(run,s.runId);
        if(changedRun){run=s.runId;phases.clear();}
        observeSlayer(s,task,changedRun);observeFarm(s,farm,changedRun);observeMinigame(s,mini,changedRun);observeAccount(s,changedRun);
    }
    private void observeSlayer(SharedActivitySession.Snapshot s,SlayerCoachTool.ActivityObservation task,boolean changedRun)
    {
        SlayerCoachTool.ActivityObservation previous=previousSlayer;
        boolean uninterrupted=previous!=null&&profile.equals(previous.profile)&&profile.equals(task.profile)&&!changedRun;
        boolean exactCurrent=matches(s,SharedActivitySession.Kind.SLAYER,task.id);
        boolean exactPrevious=previous!=null&&matches(s,SharedActivitySession.Kind.SLAYER,previous.id);
        boolean same=previous!=null&&previous.id.equals(task.id);
        // At completion the native observer deliberately changes identity to slayer:none:<new streak>.
        // Only a directly observed streak/completion transition from this run's previous assignment can close it.
        boolean finished=uninterrupted&&exactPrevious&&previous.remaining>0&&task.remaining==0
            &&task.streak>previous.streak&&task.completedTasks>previous.completedTasks;
        if(s.running()&&uninterrupted&&(exactCurrent&&same||finished))
        {
            add("slayer-credits","Assignment credits",task.observedCredits-previous.observedCredits);
            add("slayer-tasks","Tasks completed",task.completedTasks-previous.completedTasks);
            add("slayer-points","Slayer points gained",task.pointsGained-previous.pointsGained);
            if(finished)session.reachMilestone("slayer:"+previous.id+":"+task.streak,"Slayer assignment completed","Slayer");
        }
        previousSlayer=task.profile==null?null:task;
    }
    private void observeFarm(SharedActivitySession.Snapshot s,FarmPatchPlanner.RunReading farm,boolean changedRun)
    {
        String identity=farm==null?null:farmContext(farm).id;
        boolean continuous=farm!=null&&Objects.equals(farmIdentity,identity)&&!changedRun;
        if(s.running()&&farm!=null&&farm.active&&continuous&&matches(s,SharedActivitySession.Kind.FARMING,identity))
        {
            add("farm-stops","Stops checked",farm.completedStops-farmStops);add("farm-replants","Observed replants",farm.autoObservedStops-replants);
            if(farm.totalStops>0&&farm.completedStops==farm.totalStops&&farmStops<farm.totalStops)
                session.reachMilestone("farm:"+farm.guideId+":"+farm.startedAt,"Farm route checked","Farm runs");
        }
        farmIdentity=identity;farmStops=farm==null?0:farm.completedStops;replants=farm==null?0:farm.autoObservedStops;
    }
    private void observeMinigame(SharedActivitySession.Snapshot s,MinigameRunProgress.View mini,boolean changedRun)
    {
        String identity=mini.active()?minigameContext(mini).id:null;boolean sameEntry=identity!=null&&identity.equals(miniIdentity);
        if(!sameEntry||changedRun)phases.clear();
        boolean exact=matchesMinigame(s,mini);
        if(s.running()&&exact&&sameEntry&&!changedRun&&previousMini!=null)
        {
            add("minigame-phase-changes","Observed phase changes",mini.phaseChanges-previousMini.phaseChanges);
            if(mini.phaseChanges>previousMini.phaseChanges&&!mini.phaseId.isEmpty()&&!mini.phaseId.equals(previousMini.phaseId)&&phases.add(identity+":"+mini.phaseId))
            {
                session.reachMilestone("minigame:"+mini.activityId+":"+mini.entrySequence+":"+mini.phaseChanges,"Observed · "+mini.phaseId,"Minigames");
            }
            boolean sameRound=Objects.equals(mini.roundKey,previousMini.roundKey);
            if(sameRound&&mini.scoreValue!=null&&previousMini.scoreValue!=null&&Objects.equals(mini.scoreLabel,previousMini.scoreLabel))
                add("minigame-score-increases","Observed "+mini.scoreLabel.toLowerCase(Locale.ROOT)+" increases",(long)mini.scoreValue-previousMini.scoreValue);
            if(!sameRound&&!mini.roundKey.isEmpty()&&!previousMini.roundKey.isEmpty())
                session.reachMilestone("minigame-wave:"+mini.activityId+":"+mini.entrySequence+":"+mini.roundKey,"Observed wave · "+mini.roundKey,"Minigames");
        }
        // Initial phase and initial score are baselines, never historical wins, XP or reward claims.
        miniIdentity=identity;previousMini=mini.active()?mini:null;
    }
    private void observeAccount(SharedActivitySession.Snapshot s,boolean changedRun)
    {
        AccountProgressTool.Snapshot facts=account.get();if(facts==null||!facts.live||!profile.equals(facts.profile)){previousQuests=Map.of();return;}
        for(AccountProgressTool.Metric metric:AccountProgressTool.METRICS)if(metric.spendable){Long value=facts.values.get(metric.key);if(value!=null)currency.accept(metric.label,value);}
        if(s.running()&&!changedRun&&s.context!=null&&s.context.kind==SharedActivitySession.Kind.QUEST&&s.context.guideId!=null)
        {
            GuideCatalog.Entry quest=guides.entry(s.context.guideId);String q=quest==null||!"quest".equals(quest.category)?null:GoalCatalog.questKey(quest.title);
            if(q!=null&&previousQuests.containsKey(q)&&previousQuests.get(q)!=QuestState.FINISHED&&facts.quests.get(q)==QuestState.FINISHED)
            {session.observeOutcome("quest-completed","Quests completed",1);session.reachMilestone("quest:"+quest.id,quest.title+" completed","Guides");}
        }
        previousQuests=facts.quests;
    }
    private boolean bankOpen(){Widget bank=client.getWidget(InterfaceID.Bankmain.ITEMS);return bank!=null&&!bank.isHidden();}
    private static boolean matches(SharedActivitySession.Snapshot s,SharedActivitySession.Kind kind,String identity)
    {return s.context!=null&&s.context.kind==kind&&identity!=null&&identity.equals(s.context.id);}
    private boolean matchesMinigame(SharedActivitySession.Snapshot s,MinigameRunProgress.View mini)
    {
        if(s.context==null||!mini.active())return false;SharedActivitySession.Context c=s.context;
        if(c.kind!=SharedActivitySession.Kind.MINIGAME&&c.kind!=SharedActivitySession.Kind.TRAINING)return false;
        if(c.id.equals(minigameContext(mini).id))return true;
        // A whole-method run may span instances only when the actual method catalog links that method here.
        // Merely attaching an unrelated guide to a deterministic bank recipe does not establish that link.
        if(mini.guideId==null||!mini.guideId.equals(c.guideId))return false;
        ActivityXpCatalog.Method method=c.methodId==null?null:methods.find(c.methodId);
        if(method!=null&&mini.guideId.equals(method.guideId)&&method.skill==c.skill)return true;
        return c.kind==SharedActivitySession.Kind.MINIGAME
            &&(c.id.equals(mini.guideId)||c.id.equals("minigame:"+mini.activityId.toUpperCase(Locale.ROOT)));
    }
    private void add(String key,String label,long delta){if(delta>0)session.observeOutcome(key,label,delta);}
    static SharedActivitySession.Context minigameContext(MinigameRunProgress.View mini)
    {return new SharedActivitySession.Context(SharedActivitySession.Kind.MINIGAME,"minigame:"+mini.activityId+":"+mini.entrySequence,mini.title,emptyNull(mini.guideId),null,null,0);}
    static SharedActivitySession.Context farmContext(FarmPatchPlanner.RunReading farm)
    {return new SharedActivitySession.Context(SharedActivitySession.Kind.FARMING,"farm:"+farm.guideId+":"+farm.startedAt,farm.title,farm.guideId,null,null,0);}
    static SharedActivitySession.Context slayerContext(SlayerCoachTool.ActivityObservation task)
    {return new SharedActivitySession.Context(SharedActivitySession.Kind.SLAYER,task.id,task.title,emptyNull(task.guideId),null,Skill.SLAYER,0);}
    static SharedActivitySession.Context slayerContext(SlayerPlanningTool.Prepared prepared,SlayerCoachTool.ActivityObservation task)
    {
        if(prepared==null||task==null||task.remaining<=0||!prepared.profile.equals(task.profile)||!key(prepared.task).equals(key(task.title)))return null;
        String[] identity=task.id.split(":");if(identity.length<3||!"slayer".equals(identity[0])||!Integer.toString(prepared.taskId).equals(identity[1]))return null;
        if(prepared.area!=null&&task.location!=null&&!key(prepared.area).equals(key(task.location)))return null;
        SharedActivitySession.Context observed=slayerContext(task);
        return new SharedActivitySession.Context(observed.kind,observed.id,prepared.task,emptyNull(prepared.guideId),null,Skill.SLAYER,0);
    }
    void onServerNpcLoot(ServerNpcLoot event)
    {
        SharedActivitySession.Snapshot s=session.snapshot();
        if(event==null||event.getComposition()==null||client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||!s.running()||s.context==null
            ||s.context.kind!=SharedActivitySession.Kind.BOSS||s.profile==null||!s.profile.equals(config.getRSProfileKey()))return;
        GuideCatalog.Entry selected=s.context.guideId==null?null:guides.entry(s.context.guideId);
        if(selected==null||!"boss".equals(selected.category)||!key(selected.title).equals(key(s.context.title))||!key(selected.title).equals(key(event.getComposition().getName())))return;
        int tick=client.getTickCount();if(tick!=lootTick||!Objects.equals(lootRun,s.runId)){seenLoot.clear();lootTick=tick;lootRun=s.runId;}
        if(seenLoot.add(event))session.observeOutcome("boss-clears","Observed boss loot clears",1);
    }
    private GuideCatalog.Entry boss(String name)
    {for(GuideCatalog.Entry g:guides.search(name,""))if("boss".equals(g.category)&&key(g.title).equals(key(name)))return g;return null;}
    private static String emptyNull(String value){return value==null||value.isEmpty()?null:value;}
    private static String key(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
}
