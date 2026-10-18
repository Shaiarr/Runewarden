/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.SoundEffectID;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;

/** Observed XP is distinct from attributed training XP; ambiguous rewards never inflate the rate. */
@Singleton
public final class TrainingProgressTool
{
    public enum StopCause { WORKING, BANKING, PAUSED, FINISHED, OFFLINE, INVENTORY_FULL, MATERIALS_MISSING, LOW_HEALTH, LOW_PRAYER, COMBAT, IDLE, READY, TRAVELLING }
    public static final class Reading
    {
        public final SharedActivitySession.Snapshot session;
        public final long observedXp, trainingXp, unattributedXp, xpDrops, actions, activeMillis;
        public final double activeXpPerHour, elapsedXpPerHour;
        public final long remainingXp, activeEtaMillis, elapsedEtaMillis;
        public final long currentXp,progressStartXp,progressTargetXp;
        public final StopCause cause;
        public final int iconItemId;public final BufferedImage icon;
        Reading(SharedActivitySession.Snapshot session,long observed,long training,long drops,long actions,long active,long current,StopCause cause)
        {this(session,observed,training,drops,actions,active,current,cause,0,null);}
        Reading(SharedActivitySession.Snapshot session,long observed,long training,long drops,long actions,long active,long current,StopCause cause,int iconItemId,BufferedImage icon)
        {
            this.iconItemId=iconItemId;this.icon=icon;
            this.session=session;observedXp=observed;trainingXp=training;unattributedXp=observed-training;xpDrops=drops;this.actions=actions;activeMillis=active;
            activeXpPerHour=active>=60_000?training*3_600_000d/active:0;
            elapsedXpPerHour=session.elapsedMillis>=60_000?training*3_600_000d/session.elapsedMillis:0;
            remainingXp=session.context==null||session.context.targetXp==0?0:Math.max(0,session.context.targetXp-current);
            currentXp=Math.max(0,current);
            boolean target=session.context!=null&&session.context.targetXp>0;
            int level=net.runelite.api.Experience.getLevelForXp((int)Math.min(200_000_000,currentXp));
            progressStartXp=target?Math.max(0,currentXp-observed):net.runelite.api.Experience.getXpForLevel(Math.min(126,level));
            progressTargetXp=target?session.context.targetXp:level>=126?200_000_000L:net.runelite.api.Experience.getXpForLevel(level+1);
            activeEtaMillis=eta(remainingXp,activeXpPerHour);elapsedEtaMillis=eta(remainingXp,elapsedXpPerHour);this.cause=cause;
        }
        double progressFraction(){return progressTargetXp<=progressStartXp?1:Math.max(0,Math.min(1,(currentXp-progressStartXp)/(double)(progressTargetXp-progressStartXp)));}
        private static long eta(long remaining,double rate)
        { return remaining==0?0:rate<=0?-1:(long)Math.min(Long.MAX_VALUE,Math.ceil(remaining*3_600_000d/rate)); }
    }
    private static final String GROUP="runewardentraining",KEY="checkpointV1";
    private static final class Preferences
    {
        int version=1;volatile int idleTicks=8,lowHealthPercent=25;volatile boolean showHud=true;
        volatile boolean notifications,sound,onlyUnfocused=true,notifyIdle=true,notifyFull=true,notifyMissing=true,notifyHealth=true,notifyPrayer=true;
        volatile int notificationDelayTicks=5;
        boolean valid(){return version==1&&idleTicks>=2&&idleTicks<=100&&lowHealthPercent>=5&&lowHealthPercent<=50&&notificationDelayTicks>=0&&notificationDelayTicks<=100;}
    }
    private volatile Preferences preferences=new Preferences();
    private static final class Checkpoint
    {
        int version=1;String runId;long observed,training,drops,actions,active;
        boolean valid(){return version==1&&runId!=null&&runId.length()<=100&&observed>=0&&training>=0&&training<=observed&&drops>=0&&actions>=0&&active>=0;}
    }
    private final Client client;
    private final ClientThread thread;
    private final ConfigManager config;
    private final RunewardenDataStore store;
    private final SharedActivitySession session;
    private final TrainingProgressOverlay overlay;
    private final Notifier notifier;
    private final ActivityXpCatalog trainingCatalog;
    private final ItemManager itemManager;private final SkillIconManager skillIcons;
    private int iconItemId;private BufferedImage activityIcon;
    private final RecipeActionEvidence actionEvidence=new RecipeActionEvidence();
    private boolean focused=true,travelSuppressed;private WorldPoint previousLocation;private int lastMovedTick=-100;
    private StopCause pendingAlert;private int alertSince=-1;private boolean alertSent;
    private volatile boolean started,visible,showHud=true;
    private String runId,profile;
    private Skill skill;
    private int previousXp=-1,lastActionTick=-100,ignoreUntil=-100,lastTick=-1,checkpointTicks;
    private long observed,training,drops,actions,workingMillis;
    private boolean prayerObservedPositive,genericWorkActivated;
    private long farmReplantBaseline;
    private volatile Map<Integer,Integer> inputs=Collections.emptyMap();
    private final AtomicBoolean renderQueued=new AtomicBoolean();
    private final ArrayDeque<XpDrop> recentDrops=new ArrayDeque<>();
    private static final class XpDrop { final int tick;final long xp;boolean attributed;
        XpDrop(int tick,long xp,boolean attributed){this.tick=tick;this.xp=xp;this.attributed=attributed;} }
    private volatile Reading reading;
    private JPanel panel;
    private GuideText title,stats,cause;
    private JButton toggle;
    public TrainingProgressTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,SharedActivitySession session,RunewardenConfig hudConfig)
    {this(client,thread,config,store,session,hudConfig,null);}
    public TrainingProgressTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,SharedActivitySession session,RunewardenConfig hudConfig,Notifier notifier)
    {this(client,thread,config,store,session,hudConfig,notifier,null);}
    public TrainingProgressTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,SharedActivitySession session,RunewardenConfig hudConfig,Notifier notifier,ActivityXpCatalog trainingCatalog)
    {this(client,thread,config,store,session,hudConfig,notifier,trainingCatalog,null,null);}
    @Inject public TrainingProgressTool(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,SharedActivitySession session,RunewardenConfig hudConfig,Notifier notifier,ActivityXpCatalog trainingCatalog,ItemManager itemManager,SkillIconManager skillIcons)
    {this.client=client;this.thread=thread;this.config=config;this.store=store;this.session=session;this.notifier=notifier;this.trainingCatalog=trainingCatalog;this.itemManager=itemManager;this.skillIcons=skillIcons;overlay=new TrainingProgressOverlay(client,this,hudConfig);publish(StopCause.READY,0);}
    public void start(){preferences=store.read(GROUP,null,"preferencesV1",1,Preferences.class,Preferences::valid,Preferences::new);showHud=preferences.showHud;started=true;clear();}
    public void stop(){checkpoint();started=false;clear();}
    public void clear()
    {checkpoint();profile=runId=null;skill=null;iconItemId=0;activityIcon=null;previousXp=-1;observed=training=drops=actions=workingMillis=0;lastActionTick=ignoreUntil=-100;lastTick=-1;inputs=Collections.emptyMap();recentDrops.clear();actionEvidence.select(null);previousLocation=null;lastMovedTick=-100;travelSuppressed=false;prayerObservedPositive=genericWorkActivated=false;farmReplantBaseline=0;resetAlert();publish(StopCause.OFFLINE,0);}
    public Overlay overlay(){return overlay;}
    public void setHudLocked(boolean locked){overlay.setHudLocked(locked);}
    public Reading reading(){Reading value=reading;return value.session.profile!=null&&!value.session.profile.equals(config.getRSProfileKey())?new Reading(session.snapshot(),0,0,0,0,0,0,StopCause.OFFLINE):value;}
    public boolean showHud(){return showHud&&started;}
    /** Root may bind F10 to pause/resume (or start the prepared activity), F11 to restart the prepared activity. */
    public void toggleTracking()
    {SharedActivitySession.Snapshot expected=session.snapshot();thread.invoke(()->{if(!sameControl(expected))return;SharedActivitySession.Snapshot s=session.snapshot();if(s.state==SharedActivitySession.State.IDLE||s.state==SharedActivitySession.State.FINISHED)session.startSelected();else session.togglePause();});}
    public void restartTracking()
    {SharedActivitySession.Snapshot expected=session.snapshot();thread.invoke(()->{if(sameControl(expected))session.startSelected();});}
    public void finishTracking()
    {SharedActivitySession.Snapshot expected=session.snapshot();thread.invoke(()->{if(sameControl(expected))session.finish();});}
    private boolean sameControl(SharedActivitySession.Snapshot expected)
    {SharedActivitySession.Snapshot now=session.snapshot();return started&&expected.profile!=null&&expected.profile.equals(config.getRSProfileKey())&&Objects.equals(expected.runId,now.runId);}
    public void startRecipe(BankedExperienceCalculator.Recipe recipe,long targetXp)
    {
        if(recipe==null)return;
        SharedActivitySession.Snapshot expected=session.snapshot();
        thread.invoke(()->{
            if(!sameControl(expected))return;
            session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,
                recipe.id,recipe.name,null,recipe.id,recipe.skill,Math.max(0,Math.min(200_000_000,targetXp))));
        });
    }
    /** Exact selected recipe inputs only. Absence of a mapping does not imply a missing supply. */
    public void setRequiredMaterials(Map<Integer,Integer> required)
    {
        Map<Integer,Integer> next=new LinkedHashMap<>();if(required!=null)for(Map.Entry<Integer,Integer> entry:required.entrySet())
            if(next.size()<32&&entry.getKey()!=null&&entry.getKey()>0&&entry.getValue()!=null&&entry.getValue()>0)next.put(entry.getKey(),entry.getValue());
        inputs=Collections.unmodifiableMap(next);
    }
    /** Call only for a verified activity/action event, not an animation or an arbitrary inventory change. */
    public void observeAction(int units)
    {if(current()&&session.snapshot().running()&&units>0){actions=Math.min(1_000_000_000L,actions+units);boolean banking=bankOpen();if(!banking&&!travelSuppressed){lastActionTick=client.getTickCount();genericWorkActivated=true;}publish(banking?StopCause.BANKING:travelSuppressed?StopCause.TRAVELLING:StopCause.WORKING,Math.max(0,previousXp));}}
    /** A source observer can exclude lamp/quest-reward windows before their StatChanged events. */
    public void suppressRewardXp()
    {
        int tick=client.getTickCount();ignoreUntil=tick+8;actionEvidence.discard();
        for(XpDrop drop:recentDrops)if(drop.attributed&&tick>=drop.tick&&tick-drop.tick<=2){training-=drop.xp;drop.attributed=false;}
        publish(reading.cause,Math.max(0,previousXp));
    }
    public void onWidgetLoaded(WidgetLoaded event)
    {if(started&&event!=null&&rewardInterface(event.getGroupId()))suppressRewardXp();}
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(!started||event==null)return;
        if(rewardInterface(event.getParam1()>>>16)){suppressRewardXp();return;}
        String option=event.getMenuOption();
        if(event.isItemOp()&&("Rub".equals(option)||"Read".equals(option)||"Use".equals(option)))
            for(int reward:REWARD_ITEMS)if(event.getItemId()==reward){suppressRewardXp();break;}
    }
    private static final int[] REWARD_ITEMS={net.runelite.api.ItemID.LAMP,net.runelite.api.ItemID.ANTIQUE_LAMP,
        net.runelite.api.ItemID.COMBAT_LAMP,net.runelite.api.ItemID.BLESSED_LAMP,net.runelite.api.ItemID.DREAMY_LAMP,
        net.runelite.api.ItemID.BOOK_OF_KNOWLEDGE,net.runelite.api.ItemID.BOOK_OF_ARCANE_KNOWLEDGE,
        net.runelite.api.ItemID.TOME_OF_EXPERIENCE,net.runelite.api.ItemID.TOME_OF_EXPERIENCE_1,
        net.runelite.api.ItemID.TOME_OF_EXPERIENCE_2,net.runelite.api.ItemID.TOME_OF_EXPERIENCE_3,
        net.runelite.api.gameval.ItemID.POG_STRENGTH_LAMP,net.runelite.api.gameval.ItemID.POG_SLAYER_LAMP,
        net.runelite.api.gameval.ItemID.POG_THIEVING_LAMP,net.runelite.api.gameval.ItemID.POG_MAGIC_LAMP};
    private static boolean rewardInterface(int group)
    {return group==InterfaceID.XPREWARD||group==InterfaceID.QUESTSCROLL||group==InterfaceID.QUESTSCROLL_SPEEDRUN;}
    public void onGameTick()
    {
        if(!started)return;SharedActivitySession.Snapshot s=session.snapshot();Player player=client.getLocalPlayer();
        if(client.getGameState()!=GameState.LOGGED_IN||player==null||s.profile==null)
        {previousXp=-1;lastActionTick=ignoreUntil=-100;lastTick=-1;recentDrops.clear();actionEvidence.suspend();previousLocation=null;genericWorkActivated=prayerObservedPositive=false;resetAlert();publish(StopCause.OFFLINE,0);return;}
        if(!Objects.equals(profile,s.profile)||!Objects.equals(runId,s.runId))bind(s);
        if(s.context==null){genericWorkActivated=false;resetAlert();publish(StopCause.READY,0);return;}
        int tick=client.getTickCount(),current=skill==null?0:client.getSkillExperience(skill);
        Widget reward=client.getWidget(InterfaceID.Xpreward.UNIVERSE);
        if(reward!=null&&!reward.isHidden())ignoreUntil=tick+8;
        if(skill!=null&&previousXp<0)previousXp=current;
        WorldPoint location=player.getWorldLocation();if(location!=null&&previousLocation!=null&&!location.equals(previousLocation))lastMovedTick=tick;previousLocation=location;
        if(client.getBoostedSkillLevel(Skill.PRAYER)>0)prayerObservedPositive=true;
        if(skill==null)
        {
            // A phase change, a full combat backpack or an arbitrary animation is not verified work.
            // The coordinator publishes this counter only after a matching route's native replant observation.
            long replants=farmReplants(s);if(s.running()&&s.context.kind==SharedActivitySession.Kind.FARMING&&replants>farmReplantBaseline)
            {genericWorkActivated=true;lastActionTick=tick;}farmReplantBaseline=replants;
        }
        else
        {
            boolean proofAllowed=s.running()&&!bankOpen()&&tick>ignoreUntil;
            actionEvidence.inventory(inventoryAmounts(),tick,proofAllowed);
            long confirmed=proofAllowed?actionEvidence.confirm(tick):0;if(confirmed>0)actions=Math.min(1_000_000_000L,actions+confirmed);
        }
        if((skill!=null||genericWorkActivated)&&player.getAnimation()!=-1)lastActionTick=tick;
        StopCause why=stopCause(s,player,tick);
        if(why==StopCause.BANKING||why==StopCause.TRAVELLING||why==StopCause.PAUSED||why==StopCause.FINISHED)
        {lastActionTick=-100;if(skill==null)genericWorkActivated=false;}
        if(why!=StopCause.BANKING&&why!=StopCause.TRAVELLING&&lastTick>=0&&tick>lastTick&&s.running()
            &&(skill!=null||genericWorkActivated)&&tick>=lastActionTick&&tick-lastActionTick<=preferences.idleTicks)
            workingMillis+=Math.min(2,tick-lastTick)*600L;
        lastTick=tick;updateAlert(why,tick);publish(why,current);
        if(++checkpointTicks>=50){checkpointTicks=0;checkpoint();}
    }
    private long farmReplants(SharedActivitySession.Snapshot s)
    {SharedActivitySession.RunRecord record=session.currentRecord();if(record==null||!Objects.equals(record.runId,s.runId))return 0;
        SharedActivitySession.Counter counter=record.outcomes.get("farm-replants");return counter==null?0:counter.value;}
    public void onStatChanged(StatChanged event)
    {
        if(!started||event==null||event.getSkill()!=skill||!current())return;
        int now=event.getXp(),old=previousXp;previousXp=now;
        if(old<0||now<=old||!session.snapshot().running())return;
        long delta=(long)now-old;observed+=delta;drops++;
        // Animation/activity correlation is evidence of a work window, not proof of a recipe action count.
        Player player=client.getLocalPlayer();int tick=client.getTickCount();
        if(player!=null&&player.getAnimation()!=-1)lastActionTick=tick;
        boolean attributed=!bankOpen()&&tick>ignoreUntil&&tick>=lastActionTick&&tick-lastActionTick<=preferences.idleTicks;if(attributed)training+=delta;
        actionEvidence.experience(delta,tick,attributed);
        recentDrops.addLast(new XpDrop(tick,delta,attributed));while(recentDrops.size()>32)recentDrops.removeFirst();
        publish(stopCause(session.snapshot(),player,tick),now);
    }
    private void bind(SharedActivitySession.Snapshot s)
    {
        checkpoint();profile=s.profile;runId=s.runId;skill=s.context==null?null:s.context.skill;previousXp=-1;lastTick=-1;
        observed=training=drops=actions=workingMillis=0;inputs=Collections.emptyMap();lastActionTick=-100;ignoreUntil=-100;recentDrops.clear();prayerObservedPositive=genericWorkActivated=false;farmReplantBaseline=farmReplants(s);actionEvidence.select(null);previousLocation=null;lastMovedTick=-100;resetAlert();
        iconItemId=0;activityIcon=skill==null||skillIcons==null?null:skillIcons.getSkillImage(skill);
        String recipeId=s.context==null?null:s.context.methodId;
        ActivityXpCatalog.Method method=trainingCatalog==null||recipeId==null?null:trainingCatalog.find(recipeId);
        if(method!=null&&method.recipeId!=null)recipeId=method.recipeId;
        if(recipeId!=null)for(BankedExperienceCalculator.Recipe recipe:BankedExperienceCalculator.recipes())
            if(recipeId.equals(recipe.id))
            {iconItemId=recipe.icon;activityIcon=itemManager==null?null:itemManager.getImage(recipe.icon);actionEvidence.select(recipe);Map<Integer,Integer> required=new LinkedHashMap<>();for(BankedExperienceCalculator.Ingredient item:recipe.ingredients)required.merge(item.itemId,item.quantity,Integer::sum);setRequiredMaterials(required);break;}
        if(profile!=null&&runId!=null)
        {
            Checkpoint saved=store.read(GROUP,profile,KEY,1,Checkpoint.class,Checkpoint::valid,()->null);
            if(saved!=null&&runId.equals(saved.runId)){observed=saved.observed;training=saved.training;drops=saved.drops;actions=saved.actions;workingMillis=saved.active;}
        }
    }
    private boolean current()
    {SharedActivitySession.Snapshot s=session.snapshot();return started&&profile!=null&&profile.equals(config.getRSProfileKey())&&Objects.equals(runId,s.runId)&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private StopCause stopCause(SharedActivitySession.Snapshot s,Player player,int tick)
    {
        if(player==null||s.state==SharedActivitySession.State.SUSPENDED)return StopCause.OFFLINE;
        if(s.state==SharedActivitySession.State.FINISHED)return StopCause.FINISHED;
        if(!s.running())return StopCause.PAUSED;
        if(bankOpen())return StopCause.BANKING;
        int hp=client.getBoostedSkillLevel(Skill.HITPOINTS),maxHp=client.getRealSkillLevel(Skill.HITPOINTS);
        if(maxHp>0&&hp>0&&hp*100<=maxHp*preferences.lowHealthPercent)return StopCause.LOW_HEALTH;
        if(prayerObservedPositive&&client.getBoostedSkillLevel(Skill.PRAYER)==0)return StopCause.LOW_PRAYER;
        if(player.getInteracting() instanceof NPC&&((NPC)player.getInteracting()).getCombatLevel()>0)return StopCause.COMBAT;
        if(travelSuppressed||tick>=lastMovedTick&&tick-lastMovedTick<=2)return StopCause.TRAVELLING;
        if((skill!=null||genericWorkActivated)&&tick>=lastActionTick&&tick-lastActionTick<=preferences.idleTicks)return StopCause.WORKING;
        ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);
        if(inventory!=null)
        {
            Map<Integer,Integer> amounts=new LinkedHashMap<>();int occupied=0;
            for(Item item:inventory.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0){occupied++;amounts.merge(item.getId(),item.getQuantity(),(a,b)->(int)Math.min(Integer.MAX_VALUE,(long)a+b));}
            for(Map.Entry<Integer,Integer> input:inputs.entrySet())if(amounts.getOrDefault(input.getKey(),0)<input.getValue())return StopCause.MATERIALS_MISSING;
            if(occupied>=28&&(skill!=null||genericWorkActivated&&s.context!=null
                &&(s.context.kind==SharedActivitySession.Kind.FARMING||s.context.kind==SharedActivitySession.Kind.MINIGAME)))return StopCause.INVENTORY_FULL;
        }
        return skill!=null||genericWorkActivated?StopCause.IDLE:StopCause.READY;
    }
    public void onFocusChanged(FocusChanged event){if(event!=null)focused=event.isFocused();}
    public void setTravelSuppressed(boolean intentionalTravel){travelSuppressed=intentionalTravel;if(intentionalTravel&&skill==null){genericWorkActivated=false;lastActionTick=-100;}}
    public void onItemContainerChanged(ItemContainerChanged event)
    {if(!started||event==null||event.getContainerId()!=InventoryID.INVENTORY.getId()||!current())return;
        actionEvidence.inventory(inventoryAmounts(),client.getTickCount(),session.snapshot().running()&&!bankOpen()&&client.getTickCount()>ignoreUntil);}
    private Map<Integer,Long> inventoryAmounts()
    {Map<Integer,Long> amounts=new LinkedHashMap<>();ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);
        if(inventory!=null)for(Item item:inventory.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)amounts.merge(item.getId(),(long)item.getQuantity(),Long::sum);return amounts;}
    private void resetAlert(){pendingAlert=null;alertSince=-1;alertSent=false;}
    private boolean enabledAlert(StopCause why)
    {switch(why){case IDLE:return preferences.notifyIdle;case INVENTORY_FULL:return preferences.notifyFull;case MATERIALS_MISSING:return preferences.notifyMissing;case LOW_HEALTH:return preferences.notifyHealth;case LOW_PRAYER:return preferences.notifyPrayer;default:return false;}}
    private void updateAlert(StopCause why,int tick)
    {
        if(!enabledAlert(why)||!session.snapshot().running()){resetAlert();return;}
        if(pendingAlert!=why){pendingAlert=why;alertSince=tick;alertSent=false;}
        if(alertSent||tick-alertSince<preferences.notificationDelayTicks||preferences.onlyUnfocused&&focused)return;
        if(!preferences.notifications&&!preferences.sound)return;alertSent=true;
        String activity=session.snapshot().context==null?"Training":session.snapshot().context.title;
        if(preferences.notifications&&notifier!=null)notifier.notify("Runewarden: "+activity+" · "+why.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '));
        if(preferences.sound)client.playSoundEffect(SoundEffectID.GE_INCREMENT_PLOP,25);
    }
    void setAlertPreferences(boolean notifications,boolean sound,boolean onlyUnfocused,int delay)
    {preferences.notifications=notifications;preferences.sound=sound;preferences.onlyUnfocused=onlyUnfocused;preferences.notificationDelayTicks=Math.max(0,Math.min(100,delay));savePreferences();resetAlert();}
    private boolean bankOpen()
    {Widget bank=client.getWidget(InterfaceID.Bankmain.ITEMS);return bank!=null&&!bank.isHidden();}
    private void checkpoint()
    {
        if(profile==null||runId==null)return;Checkpoint saved=new Checkpoint();saved.runId=runId;saved.observed=observed;saved.training=training;saved.drops=drops;saved.actions=actions;saved.active=workingMillis;
        store.write(GROUP,profile,KEY,1,saved);
    }
    private void publish(StopCause why,long xp)
    {reading=new Reading(session.snapshot(),observed,training,drops,actions,workingMillis,xp,why,iconItemId,activityIcon);
        session.reportTraining(runId,training,actions,workingMillis);
        if(visible&&renderQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{renderQueued.set(false);if(visible)render();});}
    public JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("training-progress");
        JPanel card=SidebarUi.section("Activity & XP");title=SidebarUi.heading("");stats=SidebarUi.label("");cause=SidebarUi.label("");card.add(title);card.add(stats);card.add(cause);panel.add(card);
        toggle=SidebarUi.button("Start / pause",this::toggleTracking);panel.add(toggle);panel.add(SidebarUi.button("Restart selected activity",this::restartTracking));
        panel.add(SidebarUi.button("Finish activity",this::finishTracking));
        JCheckBox hud=new JCheckBox("Show training HUD",showHud);hud.setOpaque(false);hud.setForeground(SidebarUi.TEXT);hud.addActionListener(e->{showHud=hud.isSelected();preferences.showHud=showHud;savePreferences();});panel.add(hud);
        JPanel afk=SidebarUi.column();GuideText idleCaption=SidebarUi.label("Idle after "+preferences.idleTicks+" ticks");afk.add(idleCaption);
        JSlider idle=new JSlider(2,100,preferences.idleTicks);idle.setOpaque(false);idle.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,32));afk.add(idle);
        idle.addChangeListener(e->{idleCaption.setText("Idle after "+idle.getValue()+" ticks");if(!idle.getValueIsAdjusting()){preferences.idleTicks=idle.getValue();savePreferences();}});
        GuideText healthCaption=SidebarUi.label("Low health at "+preferences.lowHealthPercent+"%");afk.add(healthCaption);
        JSlider health=new JSlider(5,50,preferences.lowHealthPercent);health.setOpaque(false);health.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,32));afk.add(health);
        health.addChangeListener(e->{healthCaption.setText("Low health at "+health.getValue()+"%");if(!health.getValueIsAdjusting()){preferences.lowHealthPercent=health.getValue();savePreferences();}});
        JCheckBox notifications=check("Desktop notification",preferences.notifications,selected->{preferences.notifications=selected;savePreferences();});afk.add(notifications);
        afk.add(check("Play alert sound",preferences.sound,selected->{preferences.sound=selected;savePreferences();}));
        afk.add(check("Alert only when unfocused",preferences.onlyUnfocused,selected->{preferences.onlyUnfocused=selected;savePreferences();}));
        GuideText delayCaption=SidebarUi.label("Alert delay "+preferences.notificationDelayTicks+" ticks");afk.add(delayCaption);
        JSlider delay=new JSlider(0,100,preferences.notificationDelayTicks);delay.setOpaque(false);delay.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,32));afk.add(delay);
        delay.addChangeListener(e->{delayCaption.setText("Alert delay "+delay.getValue()+" ticks");if(!delay.getValueIsAdjusting()){preferences.notificationDelayTicks=delay.getValue();savePreferences();}});
        afk.add(check("Idle alert",preferences.notifyIdle,selected->{preferences.notifyIdle=selected;savePreferences();}));
        afk.add(check("Inventory full alert",preferences.notifyFull,selected->{preferences.notifyFull=selected;savePreferences();}));
        afk.add(check("Missing materials alert",preferences.notifyMissing,selected->{preferences.notifyMissing=selected;savePreferences();}));
        afk.add(check("Low health alert",preferences.notifyHealth,selected->{preferences.notifyHealth=selected;savePreferences();}));
        afk.add(check("Low prayer alert",preferences.notifyPrayer,selected->{preferences.notifyPrayer=selected;savePreferences();}));
        panel.add(SidebarUi.disclosure("AFK checks & alerts",afk));
        panel.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){visible=panel.isShowing();if(visible)render();}});render();return panel;
    }
    private static JCheckBox check(String text,boolean selected,java.util.function.Consumer<Boolean> action)
    {JCheckBox box=new JCheckBox(text,selected);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);box.addActionListener(e->action.accept(box.isSelected()));return box;}
    private void savePreferences(){store.write(GROUP,null,"preferencesV1",1,preferences);}
    private void render()
    {
        if(stats==null)return;Reading r=reading();SharedActivitySession.Context c=r.session.context;
        title.setText(c==null?(r.session.selected==null?"Choose an activity":r.session.selected.title):c.title);
        if(c!=null&&c.skill==null)stats.setText("Active "+time(r.session.activeMillis)+" · elapsed "+time(r.session.elapsedMillis)
            +(r.actions>0?"\nConfirmed actions "+String.format(java.util.Locale.ROOT,"%,d",r.actions):""));
        else stats.setText(String.format(java.util.Locale.ROOT,"Training XP %,d\nXP drops %,d · confirmed actions %,d\nWorking %s · elapsed %s\nActive rate %s · elapsed rate %s\nRemaining %,d XP\nETA active %s · elapsed %s",r.trainingXp,r.xpDrops,r.actions,time(r.activeMillis),time(r.session.elapsedMillis),rate(r.activeXpPerHour),rate(r.elapsedXpPerHour),r.remainingXp,time(r.activeEtaMillis),time(r.elapsedEtaMillis))+(r.unattributedXp>0?"\nOther observed XP "+r.unattributedXp:""));
        cause.setText(r.cause.name().replace('_',' '));toggle.setText(r.session.running()?"Pause activity":r.session.state==SharedActivitySession.State.PAUSED?"Resume activity":"Start selected activity");panel.revalidate();panel.repaint();
    }
    static String time(long ms){if(ms<0)return "—";long sec=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60);}
    static String rate(double value){return value<=0?"—":String.format(java.util.Locale.ROOT,"%,.0f XP/h",value);}
}
