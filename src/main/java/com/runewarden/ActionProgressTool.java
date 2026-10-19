/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.overlay.Overlay;

/** A local action display: sourced cast cycles, native animation progress and confirmed inventory batches. */
@Singleton
public final class ActionProgressTool
{
    public enum Display { BAR, RING, BOTH }
    private static final String GROUP="runewardenactionprogress";
    static final class Preferences
    {
        int version=1;boolean enabled=true,animation=true,batch=true,seconds=true;Display display=Display.BOTH;
        boolean valid(){return version==1&&display!=null;}
    }
    static final class Snapshot
    {
        private static final Snapshot EMPTY=new Snapshot(null,"","",0,0,0,false,null,0,0,-1);
        final String profile,title,basis,batchTitle;final long cycleStart,cycleEnd,observedAt,completed,remaining,batchTotal,batchEta;
        final boolean exactCycle;
        final int cycleItemId,cycleSpriteId,batchItemId;final Skill cycleSkill;final BufferedImage cycleIcon,batchIcon;
        Snapshot(String profile,String title,String basis,long start,long end,long at,boolean exact,String batchTitle,long done,long left,long eta)
        {this(profile,title,basis,start,end,at,exact,batchTitle,done,left,done+left,eta);}
        Snapshot(String profile,String title,String basis,long start,long end,long at,boolean exact,String batchTitle,long done,long left,long total,long eta)
        {this(profile,title,basis,start,end,at,exact,batchTitle,done,left,total,eta,0,0,null,null,0,null);}
        Snapshot(String profile,String title,String basis,long start,long end,long at,boolean exact,String batchTitle,long done,long left,long total,long eta,
            int cycleItemId,int cycleSpriteId,Skill cycleSkill,BufferedImage cycleIcon,int batchItemId,BufferedImage batchIcon)
        {this.profile=profile;this.title=title;this.basis=basis;cycleStart=start;cycleEnd=end;observedAt=at;exactCycle=exact;this.batchTitle=batchTitle;completed=done;remaining=left;batchTotal=total;batchEta=eta;
            this.cycleItemId=cycleItemId;this.cycleSpriteId=cycleSpriteId;this.cycleSkill=cycleSkill;this.cycleIcon=cycleIcon;this.batchItemId=batchItemId;this.batchIcon=batchIcon;}
        boolean cycle(){return cycleEnd>cycleStart;}
        boolean batch(){return batchTitle!=null&&(completed>0||remaining>0);}
        double batchFraction(){return batchTotal<=0?0:completed/(double)batchTotal;}
        static Snapshot empty(){return EMPTY;}
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final SharedActivitySession session;
    private final ActionProgressOverlay overlay;private final LongSupplier clock;
    private final ItemManager items;private final SpriteManager sprites;private final SkillIconManager skillIcons;
    private static final class ImageLookup { final BufferedImage image;final long retryAt;ImageLookup(BufferedImage image,long retryAt){this.image=image;this.retryAt=retryAt;} }
    private final Map<String,ImageLookup> images=boundedCache();
    private long imageRetryAt;
    private final ActionBatchProgress batch=new ActionBatchProgress();
    private final ActionRecipeDetector detector=new ActionRecipeDetector();
    private final Map<Skill,Integer> experience=new EnumMap<>(Skill.class);
    private final Map<Integer,ActionProgressRules.Timing> timings=boundedCache();
    private static <K,V> Map<K,V> boundedCache()
    {return new LinkedHashMap<K,V>(){protected boolean removeEldestEntry(Map.Entry<K,V> eldest){return size()>64;}};}
    private volatile Preferences preferences=new Preferences();private volatile Snapshot snapshot=Snapshot.empty();private volatile boolean started;
    private String profile,contextMethod,cycleTitle="",cycleBasis="";private int menuItemId,cycleItemId,cycleSpriteId;private Skill menuSkill,cycleSkill;private long menuAt;private ActionRecipeCatalog.Recipe menuRecipe,confirmedRecipe;private boolean cleanOperation;private long confirmedAt;private int animation=-1,lastFrame=-1;private long cycleStart,cycleEnd,lastActivity;private boolean exactCycle;
    public ActionProgressTool(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hud,SharedActivitySession session)
    {this(client,config,store,hud,session,System::currentTimeMillis);}
    @Inject public ActionProgressTool(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hud,SharedActivitySession session,ItemManager items,SpriteManager sprites,SkillIconManager skillIcons)
    {this(client,config,store,hud,session,System::currentTimeMillis,items,sprites,skillIcons);}
    ActionProgressTool(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hud,SharedActivitySession session,LongSupplier clock)
    {this(client,config,store,hud,session,clock,null,null,null);}
    ActionProgressTool(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hud,SharedActivitySession session,LongSupplier clock,ItemManager items,SpriteManager sprites,SkillIconManager skillIcons)
    {this.client=client;this.config=config;this.store=store;this.session=session;this.clock=clock;this.items=items;this.sprites=sprites;this.skillIcons=skillIcons;overlay=new ActionProgressOverlay(this,hud);}
    public void start(){preferences=store.read(GROUP,null,"preferencesV1",1,Preferences.class,Preferences::valid,Preferences::new);started=true;clear();}
    public void stop(){started=false;clear();}
    public void clear(){profile=contextMethod=null;animation=lastFrame=-1;cycleStart=cycleEnd=lastActivity=0;cycleTitle=cycleBasis="";exactCycle=false;batch.select(null);detector.clear();experience.clear();timings.clear();images.clear();imageRetryAt=0;menuItemId=cycleItemId=cycleSpriteId=0;menuSkill=cycleSkill=null;menuAt=confirmedAt=0;menuRecipe=confirmedRecipe=null;cleanOperation=false;snapshot=Snapshot.empty();}
    public Overlay overlay(){return overlay;}
    public void setHudLocked(boolean locked){overlay.setHudLocked(locked);}
    boolean enabled(){return started&&preferences.enabled;}
    Preferences preferences(){return preferences;}
    long now(){return clock.getAsLong();}
    Snapshot snapshot(){Snapshot value=snapshot;return enabled()&&value.profile!=null&&value.profile.equals(config.getRSProfileKey())?value:Snapshot.empty();}
    private boolean available(){return enabled()&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private boolean bankOpen(){Widget bank=client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);return bank!=null&&!bank.isHidden();}
    private boolean combat(){return client.getLocalPlayer().getInteracting() instanceof NPC&&((NPC)client.getLocalPlayer().getInteracting()).getCombatLevel()>0;}
    private void bind()
    {
        String current=config.getRSProfileKey();if(!Objects.equals(profile,current)){clear();profile=current;for(Skill skill:Skill.values())experience.put(skill,client.getSkillExperience(skill));}
        SharedActivitySession.Snapshot active=session==null?null:session.snapshot();String selected=active==null||active.context==null||active.state==SharedActivitySession.State.FINISHED?null:active.context.methodId;
        if(!Objects.equals(contextMethod,selected))
        {contextMethod=selected;detector.clear();}
    }
    private Map<Integer,Long> inventory()
    {
        ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);if(inventory==null)return Collections.emptyMap();Map<Integer,Long> items=new LinkedHashMap<>();
        for(Item item:inventory.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)items.merge(item.getId(),(long)item.getQuantity(),Long::sum);return items;
    }
    private java.util.Set<Integer> carriedIds()
    {
        java.util.Set<Integer> ids=new java.util.HashSet<>(inventory().keySet());ItemContainer worn=client.getItemContainer(InventoryID.EQUIPMENT);
        if(worn!=null)for(Item item:worn.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)ids.add(item.getId());return ids;
    }
    public void onGameTick()
    {
        if(!available()){if(profile!=null)clear();return;}bind();if(profile==null)return;
        long now=clock.getAsLong();boolean allowed=!bankOpen()&&!combat();
        if(!allowed){batch.inventory(Collections.emptyMap(),client.getTickCount(),false);detector.clear();if(contextMethod==null)batch.select(null);cycleStart=cycleEnd=0;publish(now);return;}
        Map<Integer,Long> items=inventory();batch.inventory(items,client.getTickCount(),true);detector.inventory(items,client.getTickCount(),now);
        if(batch.confirm(client.getTickCount(),now)>0)lastActivity=now;
        ActionRecipeDetector.Match match=detector.confirm(client.getTickCount());
        if(match!=null)
        {
            confirmedRecipe=match.recipe;confirmedAt=now;
            if(batch.recipe()==null||!batch.recipe().id.equals(match.recipe.id))batch.adopt(match,client.getTickCount(),now);
            lastActivity=now;
            if(client.getLocalPlayer().getAnimation()>=0)observeAnimation(client.getLocalPlayer(),now,false);
        }
        sampleAnimation(client.getLocalPlayer(),now);
        publish(now);
    }
    /** Sample native frame wraps at client frequency, including loops shorter than a game tick. */
    public void onClientTick()
    {
        if(!available())return;bind();if(profile==null)return;long now=clock.getAsLong();
        if(bankOpen()||combat()){cycleStart=cycleEnd=0;publish(now);return;}
        sampleAnimation(client.getLocalPlayer(),now);publish(now);
    }
    private void sampleAnimation(Player player,long now)
    {
        int current=player.getAnimation(),frame=player.getAnimationFrame();
        if(current>=0&&(current!=animation||frame<lastFrame&&("Animation".equals(cycleBasis)||cycleEnd<=now)))observeAnimation(player,now,false);
        else if(current<0){if("Animation".equals(cycleBasis))cycleStart=cycleEnd=0;animation=-1;lastFrame=-1;}
        if(current>=0)lastFrame=frame;
        if(cycleEnd<=now)cycleStart=cycleEnd=0;
    }
    public void onAnimationChanged(AnimationChanged event)
    {if(!available()||event==null||event.getActor()!=client.getLocalPlayer())return;bind();if(!bankOpen()&&!combat())observeAnimation(client.getLocalPlayer(),clock.getAsLong(),true);publish(clock.getAsLong());}
    private void observeAnimation(Player player,long now,boolean fresh)
    {
        int id=player.getAnimation();String label=ActionProgressRules.label(id);animation=id;lastFrame=player.getAnimationFrame();
        // Native idle events can arrive before a sourced action cooldown ends.
        // They end an animation bar, not an already observed cast/recipe cycle.
        if(id<0){if("Animation".equals(cycleBasis)||cycleEnd<=now)cycleStart=cycleEnd=0;return;}
        if(label==null){clearCycle();return;}
        ActionRecipeCatalog.Recipe actionRecipe=cycleRecipe(id,now);
        if(actionRecipe!=null)label=actionRecipe.name;
        // Walking, emotes and unidentified animation loops are not production actions.
        if(ActionProgressRules.animationItem(id)>0&&actionRecipe==null){clearCycle();return;}
        cycleSpriteId=ActionProgressRules.spellSprite(id);cycleSkill=actionRecipe==null?ActionProgressRules.skill(id):actionRecipe.skill;
        cycleItemId=actionRecipe==null?0:actionRecipe.icon;
        if(cycleSpriteId==0&&cycleItemId==0&&menuRecipe==null&&menuItemId>0&&cycleSkill==menuSkill&&now-menuAt<=120_000)cycleItemId=menuItemId;
        if(cycleSpriteId==0&&cycleItemId==0&&cycleSkill==null){cycleStart=cycleEnd=0;return;}
        if(actionRecipe!=null&&batch.recipe()!=null&&actionRecipe.id.equals(batch.recipe().id))batch.arm(now);
        long spellCycle=ActionProgressRules.spellCycle(id);
        if(spellCycle>0)
        {cycleStart=now;cycleEnd=now+spellCycle;cycleTitle=label;cycleBasis="Cast cycle";exactCycle=true;lastActivity=now;return;}
        exactCycle=false;
        long recipeMillis=ActionProgressRules.recipeCycle(actionRecipe,id,carriedIds(),actionRecipe==batch.recipe()?batch.firstAction():true);
        if(recipeMillis>0){cycleStart=now;cycleEnd=now+recipeMillis;cycleTitle=actionRecipe.name;cycleBasis="Standard cycle";lastActivity=now;return;}
        ActionProgressRules.Timing timing=timings.get(id);
        if(timing==null)
        {
            try{Animation definition=client.loadAnimation(id);timing=ActionProgressRules.timing(definition);if(timing!=null)timings.put(id,timing);}catch(RuntimeException ignored){timing=null;}
        }
        if(timing==null){cycleStart=cycleEnd=0;return;}
        cycleStart=fresh?now:now-timing.elapsedAtFrame(Math.max(0,lastFrame));cycleEnd=cycleStart+timing.durationMillis;cycleTitle=label;cycleBasis="Animation";lastActivity=now;
    }
    private void clearCycle() { cycleStart=cycleEnd=0;cycleTitle="";cycleItemId=cycleSpriteId=0;cycleSkill=null; }
    /** An animation name is only a hint: reused sequences never overrule a native item operation or transformation. */
    private ActionRecipeCatalog.Recipe cycleRecipe(int animation,long now)
    {
        int item=ActionProgressRules.animationItem(animation);Skill skill=ActionProgressRules.skill(animation);
        Map<Integer,Long> carried=inventory();
        boolean recentMenu=menuRecipe!=null&&now-menuAt<=120_000;
        // Cleaning shares hand-work sequences with other skills. The actual Clean item
        // operation and carried grimy input identify this action, not a sequence alias.
        if(recentMenu&&cleanOperation&&ActionProgressRules.handWork(animation)&&(now-menuAt<=3000||confirmedRecipe==menuRecipe&&now-confirmedAt<=3000)
            &&(ActionRecipeCatalog.availableActions(menuRecipe,carried)>0||batch.completed()>0))return menuRecipe;
        if(item>0)
        {
            for(ActionRecipeCatalog.Recipe recipe:ActionRecipeCatalog.recipes())if(recipe.icon==item&&(skill==null||recipe.skill==skill)
                &&(!ActionProgressRules.ambiguousItem(animation)||recentMenu&&menuRecipe==recipe||confirmedRecipe==recipe&&now-confirmedAt<=3000)
                &&(ActionRecipeCatalog.availableActions(recipe,carried)>0||confirmedRecipe==recipe&&now-confirmedAt<=3000))return recipe;
        }
        boolean compatible=recentMenu&&(skill==menuSkill||animation==AnimationID.HUMAN_FURNACE&&(menuSkill==Skill.CRAFTING||menuSkill==Skill.SMITHING));
        if(compatible&&(ActionRecipeCatalog.availableActions(menuRecipe,carried)>0||confirmedRecipe==menuRecipe&&now-confirmedAt<=3000))return menuRecipe;
        if(confirmedRecipe!=null&&now-confirmedAt<=3000&&(skill==confirmedRecipe.skill||ActionProgressRules.cleaning(confirmedRecipe)&&ActionProgressRules.handWork(animation)))return confirmedRecipe;
        return null;
    }
    public void onItemContainerChanged(ItemContainerChanged event)
    {if(!available()||event==null||event.getContainerId()!=InventoryID.INVENTORY.getId())return;bind();long now=clock.getAsLong();boolean allowed=!bankOpen()&&!combat();Map<Integer,Long> items=inventory();batch.inventory(items,client.getTickCount(),allowed);if(allowed)detector.inventory(items,client.getTickCount(),now);else detector.clear();publish(now);}
    public void onStatChanged(StatChanged event)
    {
        if(!available()||event==null)return;bind();Integer old=experience.put(event.getSkill(),event.getXp());
        if(old==null||event.getXp()<=old)return;
        boolean allowed=!bankOpen()&&!combat();long delta=(long)event.getXp()-old;
        if(allowed)detector.experience(event.getSkill(),delta,client.getTickCount(),clock.getAsLong());
        if(allowed&&ActionProgressRules.nonCombat(event.getSkill()))
        {
            if(menuRecipe!=null&&menuRecipe.skill!=event.getSkill()){menuRecipe=null;menuItemId=0;menuSkill=null;cleanOperation=false;}
            if(confirmedRecipe!=null&&confirmedRecipe.skill!=event.getSkill()){confirmedRecipe=null;confirmedAt=0;}
            if(cycleSkill!=null&&cycleSkill!=event.getSkill()){cycleStart=cycleEnd=0;cycleTitle="";cycleItemId=cycleSpriteId=0;cycleSkill=null;}
        }
        if(batch.recipe()!=null&&event.getSkill()==batch.recipe().skill)batch.experience(delta,client.getTickCount(),allowed);publish(clock.getAsLong());
    }
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(!available()||event==null||bankOpen()||combat())return;bind();
        String option=event.getMenuOption()==null?"":net.runelite.client.util.Text.removeTags(event.getMenuOption());
        String target=event.getMenuTarget()==null?"":net.runelite.client.util.Text.removeTags(event.getMenuTarget());
        Skill resourceSkill=ActionProgressRules.resourceSkill(option);int resource=ActionProgressRules.resourceItem(resourceSkill,target);
        if(resource>0){menuItemId=resource;menuSkill=resourceSkill;menuAt=clock.getAsLong();menuRecipe=null;cleanOperation=false;}
        else
        {
            int clicked=event.getItemId();if(clicked<=0&&event.getWidget()!=null)clicked=event.getWidget().getItemId();
            ActionRecipeCatalog.Recipe selected=ActionProgressRules.menuRecipe(option,clicked);
            if(selected!=null){menuRecipe=selected;cleanOperation="Clean".equalsIgnoreCase(option);menuItemId=selected.icon;menuSkill=selected.skill;menuAt=clock.getAsLong();
                if(batch.recipe()!=selected){batch.select(selected);batch.inventory(inventory(),client.getTickCount(),true);}}
            else if(!"Walk here".equals(option)){menuItemId=0;menuSkill=null;menuRecipe=null;cleanOperation=false;}
        }
        if(event.getMenuOption()!=null&&"Clean".equals(net.runelite.client.util.Text.removeTags(event.getMenuOption())))
        {
            int clickedItem=event.getItemId();if(clickedItem<=0&&event.getWidget()!=null)clickedItem=event.getWidget().getItemId();
            for(ActionRecipeCatalog.Recipe recipe:ActionRecipeCatalog.recipes())
                if(recipe.id.startsWith("herblore:")&&!recipe.id.contains("unfinished")&&recipe.ingredients.size()==1&&recipe.ingredients.get(0).itemId==clickedItem&&!recipe.products.isEmpty())
                {if(batch.recipe()==null||!recipe.id.equals(batch.recipe().id)){batch.select(recipe);batch.inventory(inventory(),client.getTickCount(),true);}batch.arm(clock.getAsLong());lastActivity=clock.getAsLong();break;}
        }
        if(client.getLocalPlayer().getAnimation()>=0)observeAnimation(client.getLocalPlayer(),clock.getAsLong(),false);
        publish(clock.getAsLong());
    }
    private void publish(long now)
    {
        boolean finished=batch.recipe()!=null&&batch.remaining()==0&&batch.completed()>0;
        if(finished&&cycleItemId==batch.recipe().icon&&cycleSkill==batch.recipe().skill)cycleStart=cycleEnd=0;
        boolean showBatch=preferences.batch&&batch.visible()&&batch.remaining()>0&&(now-lastActivity<=10_000||cycleEnd>now);
        int sprite=cycleSpriteId;Skill skill=cycleSkill;int item=cycleItemId;
        int batchItem=showBatch?batch.recipe().icon:0;
        long start=preferences.animation?cycleStart:0,end=preferences.animation?cycleEnd:0;
        String batchTitle=showBatch?batch.recipe().name:null;
        long done=showBatch?batch.completed():0,left=showBatch?batch.remaining():0,total=showBatch?batch.total():0,eta=showBatch?batch.estimatedMillis():-1;
        Snapshot previous=snapshot;
        boolean unchanged=Objects.equals(previous.profile,profile)&&Objects.equals(previous.title,cycleTitle)&&Objects.equals(previous.basis,cycleBasis)
            &&previous.cycleStart==start&&previous.cycleEnd==end&&previous.exactCycle==exactCycle
            &&Objects.equals(previous.batchTitle,batchTitle)&&previous.completed==done&&previous.remaining==left&&previous.batchTotal==total&&previous.batchEta==eta
            &&previous.cycleItemId==item&&previous.cycleSpriteId==sprite&&previous.cycleSkill==skill&&previous.batchItemId==batchItem;
        // Rendering advances from the clock; observedAt records a payload change, not every frame.
        if(unchanged&&now<imageRetryAt)return;
        imageRetryAt=Long.MAX_VALUE;
        BufferedImage cycleIcon=image(item,sprite,skill,now),batchIcon=showBatch?image(batchItem,0,null,now):null;
        if(unchanged&&previous.cycleIcon==cycleIcon&&previous.batchIcon==batchIcon)return;
        snapshot=new Snapshot(profile,cycleTitle,cycleBasis,start,end,now,exactCycle,batchTitle,done,left,total,eta,
            item,sprite,skill,cycleIcon,batchItem,batchIcon);
    }
    /** Called from native client events, never from render. Null sprite loads retry at most once per second. */
    private BufferedImage image(int item,int sprite,Skill skill,long now)
    {
        String key=item>0?"item:"+item:sprite>0?"sprite:"+sprite:skill!=null?"skill:"+skill.name():null;if(key==null)return null;
        ImageLookup cached=images.get(key);if(cached!=null&&(cached.image!=null||now<cached.retryAt))
        {if(cached.image==null)imageRetryAt=Math.min(imageRetryAt,cached.retryAt);return cached.image;}
        BufferedImage image=null;
        if(item>0&&items!=null)image=items.getImage(item);
        else if(sprite>0&&sprites!=null)image=sprites.getSprite(sprite,0);
        else if(item==0&&sprite==0&&skill!=null&&skillIcons!=null)image=skillIcons.getSkillImage(skill);
        long retryAt=now+1000;if(image==null)imageRetryAt=Math.min(imageRetryAt,retryAt);
        images.put(key,new ImageLookup(image,retryAt));return image;
    }
    public JPanel createPanel()
    {
        JPanel root=SidebarUi.column();root.add(SidebarUi.heading("ACTION PROGRESS"));
        option(root,"Show action HUD",preferences.enabled,value->preferences.enabled=value);
        option(root,"Animation / cast progress",preferences.animation,value->preferences.animation=value);
        option(root,"Inventory batch progress",preferences.batch,value->preferences.batch=value);
        option(root,"Show seconds",preferences.seconds,value->preferences.seconds=value);
        JComboBox<Display> display=new JComboBox<>(Display.values());display.setSelectedItem(preferences.display);display.addActionListener(event->{preferences.display=(Display)display.getSelectedItem();save();});
        root.add(SidebarUi.field("Display",display));return root;
    }
    private void option(JPanel root,String title,boolean selected,java.util.function.Consumer<Boolean> change)
    {JCheckBox box=new JCheckBox(title,selected);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.addActionListener(event->{change.accept(box.isSelected());save();});root.add(box);}
    private void save(){preferences=preferences;store.write(GROUP,null,"preferencesV1",1,preferences);if(!preferences.enabled)snapshot=Snapshot.empty();}
}
