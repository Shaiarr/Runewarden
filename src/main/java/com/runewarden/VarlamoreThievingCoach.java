/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.HintArrowType;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.Text;

/** Native Varlamore thieving observations, bounded caches and account-scoped live HUD. */
@Singleton
final class VarlamoreThievingCoach
{
    static final String GROUP="runewardenthieving",KEY="preferencesV1";
    static final int KEYS=29325,VALUABLES=29332;
    static final Color READY=new Color(123,220,160),WARNING=new Color(255,119,100),BONUS=new Color(123,204,255);
    static final class Preferences
    {
        int version=1,scalePercent=100;
        boolean hud=true,radialTimers=true,labels=true,citizens=true,owners=true,doors=true,windows=true,furniture=true,bonus=true,compact;
        Preferences copy(){Preferences p=new Preferences();p.scalePercent=scalePercent;p.hud=hud;p.radialTimers=radialTimers;p.labels=labels;p.citizens=citizens;p.owners=owners;p.doors=doors;p.windows=windows;p.furniture=furniture;p.bonus=bonus;p.compact=compact;return p;}
    }
    static final class Target
    {
        final TileObject object;final NPC npc;final String label;final Color color;final VarlamoreThievingState.Window timer;
        Target(TileObject object,NPC npc,String label,Color color,VarlamoreThievingState.Window timer){this.object=object;this.npc=npc;this.label=label;this.color=color;this.timer=timer;}
    }
    static final class Snapshot
    {
        static final Snapshot EMPTY=new Snapshot(null,0,null,"","",List.of(),List.of(),List.of(),false,0,0,0);
        final String profile,title,objective;final int world,keys,valuables;final long tradeIn;
        final VarlamoreThievingState.House house;final List<String> facts;final List<VarlamoreThievingState.Window> timers;final List<Target> targets;final boolean active;
        Snapshot(String profile,int world,VarlamoreThievingState.House house,String title,String objective,List<String> facts,List<VarlamoreThievingState.Window> timers,List<Target> targets,boolean active,int keys,int valuables,long tradeIn)
        {this.profile=profile;this.world=world;this.house=house;this.title=title;this.objective=objective;this.facts=List.copyOf(facts);this.timers=List.copyOf(timers);this.targets=List.copyOf(targets);this.active=active;this.keys=keys;this.valuables=valuables;this.tradeIn=tradeIn;}
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final RunewardenConfig hudConfig;private final LongSupplier clock;
    private final VarlamoreThievingState state=new VarlamoreThievingState();
    private final Map<TileObject,WorldPoint> objects=new IdentityHashMap<>();
    private final Map<Integer,NPC> npcs=new LinkedHashMap<>();
    private final VarlamoreThievingHudOverlay hud;private final VarlamoreThievingSceneOverlay scene;
    private volatile Preferences preferences=new Preferences();private volatile Snapshot reading=Snapshot.EMPTY;private volatile boolean started;
    private String profile;private int world=-1,previousValuables=-1;
    private Scene seeded;private WorldView seededView;private int baseX,baseY,plane;private long epoch;
    private JPanel panel;private GuideText status,facts;private boolean refreshing;private final Map<String,JCheckBox> controls=new LinkedHashMap<>();private JSlider scale;
    private volatile Consumer<String> openGuide;
    @Inject VarlamoreThievingCoach(Client client,ConfigManager config,Gson gson,RunewardenDataStore store,RunewardenConfig hudConfig)
    {this(client,config,store,hudConfig,System::currentTimeMillis);}
    VarlamoreThievingCoach(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hudConfig,LongSupplier clock)
    {this.client=client;this.config=config;this.store=store;this.hudConfig=hudConfig;this.clock=clock;hud=new VarlamoreThievingHudOverlay(client,this,hudConfig);scene=new VarlamoreThievingSceneOverlay(client,this);}
    void start(){started=true;clear();reloadPreferences();}
    void reloadPreferences(){preferences=store.read(GROUP,null,KEY,1,Preferences.class,VarlamoreThievingCoach::validPreferences,Preferences::new);refreshLater();}
    void stop(){started=false;clear();}
    void clear(){epoch++;reading=Snapshot.EMPTY;state.clear();objects.clear();npcs.clear();seeded=null;seededView=null;profile=null;world=-1;previousValuables=-1;refreshLater();}
    void onSceneChanged(){clear();}
    void onGameStateChanged(GameStateChanged event){if(event==null||event.getGameState()!=GameState.LOGGED_IN)clear();}
    boolean started(){return started;}
    Preferences preferences(){return preferences;}
    Overlay hud(){return hud;}
    void setHudLocked(boolean locked){hud.setHudLocked(locked);}
    Overlay scene(){return scene;}
    long now(){return clock.getAsLong();}
    Snapshot reading()
    {Snapshot current=reading;return started&&current.profile!=null&&client.getGameState()==GameState.LOGGED_IN&&Objects.equals(current.profile,config.getRSProfileKey())&&current.world==client.getWorld()?current:Snapshot.EMPTY;}
    void setOpenGuideAction(Consumer<String> action){openGuide=action;}
    private boolean ready()
    {return started&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null&&VarlamoreThievingState.activityArea(client.getLocalPlayer().getWorldLocation());}
    private void scope()
    {
        String current=config.getRSProfileKey();int currentWorld=client.getWorld();
        if(!Objects.equals(profile,current)||world!=currentWorld)
        {clear();profile=current;world=currentWorld;}
    }
    void onGameTick()
    {
        if(!ready()){if(reading.active||seeded!=null)clear();return;}
        scope();seedScene();long now=now();WorldPoint player=client.getLocalPlayer().getWorldLocation();
        VarlamoreThievingState.House house=VarlamoreThievingState.House.at(player);state.enter(house);
        EnumMap<VarlamoreThievingState.House,VarlamoreThievingState.DoorState> doorStates=new EnumMap<>(VarlamoreThievingState.House.class);
        for(Map.Entry<TileObject,WorldPoint> entry:objects.entrySet())
        {
            VarlamoreThievingState.House door=VarlamoreThievingState.House.doorAt(entry.getValue());
            if(door==null)continue;int id=resolvedId(entry.getKey());
            if(id==51998)doorStates.merge(door,VarlamoreThievingState.DoorState.LOCKED,(a,b)->a==b?a:VarlamoreThievingState.DoorState.UNKNOWN);
            else if(id==51999)doorStates.merge(door,VarlamoreThievingState.DoorState.OPEN,(a,b)->a==b?a:VarlamoreThievingState.DoorState.UNKNOWN);
        }
        for(VarlamoreThievingState.House h:VarlamoreThievingState.House.values())state.observeDoor(h,doorStates.getOrDefault(h,VarlamoreThievingState.DoorState.UNKNOWN),now);
        refreshNpcs();int valuables=count(VALUABLES),keys=count(KEYS);if(previousValuables>=0)state.observeValuables(previousValuables,valuables,now);previousValuables=valuables;
        List<Target> targets=new ArrayList<>();List<String> lines=new ArrayList<>();List<VarlamoreThievingState.Window> timers=new ArrayList<>();
        boolean distracted=false,ownerNear=false,ownerSeen=false;int citizens=0;
        for(NPC npc:npcs.values())
        {
            WorldPoint at=npc.getWorldLocation();if(at==null||player.distanceTo(at)>48)continue;
            if(citizen(npc.getId()))
            {
                citizens++;boolean isDistracted=distracted(npc);distracted|=isDistracted;
                if(preferences.citizens)targets.add(new Target(null,npc,isDistracted?"DISTRACTED":"Wealthy citizen",isDistracted?READY:HudStyle.GOLD,isDistracted?state.distraction(now):null));
            }
            VarlamoreThievingState.House owner=VarlamoreThievingState.House.owner(npc.getId());
            if(owner!=null)
            {
                boolean close=at.distanceTo2D(owner.door)<5;
                if(owner==house){ownerSeen=true;ownerNear|=close;}
                if(preferences.owners)targets.add(new Target(null,npc,owner.name+(close?" · near entrance":""),close?WARNING:HudStyle.GOLD,null));
            }
        }
        WorldPoint nativeBonus=house!=null&&client.getHintArrowType()==HintArrowType.COORDINATE?client.getHintArrowPoint():null;
        boolean bonusFound=false,exitFound=false,anyFurniture=false;
        for(Map.Entry<TileObject,WorldPoint> entry:objects.entrySet())
        {
            TileObject object=entry.getKey();WorldPoint point=entry.getValue();if(player.distanceTo(point)>40)continue;
            int id=resolvedId(object);VarlamoreThievingState.House door=VarlamoreThievingState.House.doorAt(point);
            if(door!=null&&(id==51998||id==51999)&&preferences.doors)
                targets.add(new Target(object,null,id==51998?(keys>0?"Locked · key available":"Locked · need house key"):"Owner's entrance",id==51998?READY:WARNING,null));
            if(house!=null&&house.near(point,4)&&(id==52000||id==52001||object.getId()==52998))
            {exitFound=true;if(preferences.windows)targets.add(new Target(object,null,"Exit window",state.returnWarning()||ownerNear?WARNING:READY,null));}
            if(house!=null&&house.contains(point)&&furniture(id))
            {
                anyFurniture=true;boolean bonus=point.equals(nativeBonus);bonusFound|=bonus;
                if(bonus&&preferences.bonus)targets.add(new Target(object,null,"BONUS · "+furnitureName(id),BONUS,state.bonus(now)));
                else if(preferences.furniture)targets.add(new Target(object,null,furnitureName(id),HudStyle.GOLD,point.equals(state.looting())?state.container(now):null));
            }
        }
        long value=VarlamoreThievingState.tradeIn(valuables,client.getVarpValue(4130));
        String title=house==null?"Wealthy citizens":house.name+"'s house",objective;
        lines.add("Keys · "+VarlamoreThievingState.count(keys)+"     Valuables · "+VarlamoreThievingState.count(valuables));
        lines.add("Oriana trade-in · "+VarlamoreThievingState.count(value)+" gp");
        if(client.getRealSkillLevel(Skill.THIEVING)<50)objective="Requires 50 Thieving";
        else if(house!=null)
        {
            if(state.returnWarning()||ownerNear&&state.door(house)==VarlamoreThievingState.DoorState.LOCKED)objective="OWNER RETURNING · leave through the window";
            else if(bonusFound)objective="BONUS · search the highlighted furniture";
            else if(state.depleted())objective="Container empty · search another";
            else if(state.door(house)==VarlamoreThievingState.DoorState.OPEN)objective="Owner at home · wait for departure";
            else objective=anyFurniture?"Search the house's furniture":"Observe the house";
            VarlamoreThievingState.Window ownerTimer=state.ownerReturn(house,now);
            if(ownerTimer!=null){timers.add(ownerTimer);lines.add("Owner return · "+ownerTimer.remaining(now));}
            else lines.add("Owner · "+(ownerNear?"near entrance":state.door(house)==VarlamoreThievingState.DoorState.LOCKED?"away · departure not observed":ownerSeen?"observed nearby":"not observed"));
            if(bonusFound){VarlamoreThievingState.Window timer=state.bonus(now);if(timer!=null)timers.add(timer);lines.add(timer!=null?"Bonus · "+timer.remaining(now):"Bonus · native arrow visible");}
            VarlamoreThievingState.Window loot=state.container(now);if(loot!=null){timers.add(loot);lines.add("Container · "+loot.remaining(now));}
        }
        else
        {
            objective=distracted?"DISTRACTED · pickpocket the highlighted citizen":keys>0?"Pickpocket citizens or find a vacant house":"Collect house keys from wealthy citizens";
            VarlamoreThievingState.Window active=state.distraction(now),next=state.nextDistraction(now);
            if(distracted){if(active!=null)timers.add(active);lines.add(active==null?"Distraction · remaining unknown":"Distraction · "+active.remaining(now));}
            if(next!=null){timers.add(next);lines.add("Next distraction · "+next.remaining(now));}
        }
        boolean active=house!=null||citizens>0||!targets.isEmpty();
        reading=active?new Snapshot(profile,world,house,title,objective,lines,timers,targets,true,keys,valuables,value):Snapshot.EMPTY;
        hud.setHudLocked(hudConfig.hudLocked());if(panel!=null&&panel.isShowing())refreshLater();
    }
    static boolean citizen(int id){return id>=13302&&id<=13305;}
    static boolean distracted(NPC npc)
    {if(npc==null||!citizen(npc.getId()))return false;Actor actor=npc.getInteracting();return actor instanceof NPC&&((NPC)actor).getId()>=13306&&((NPC)actor).getId()<=13308;}
    private void refreshNpcs()
    {
        WorldView view=client.getTopLevelWorldView();if(view==null||view.npcs()==null){npcs.clear();return;}
        npcs.clear();int scanned=0;for(NPC npc:view.npcs())
        {if(++scanned>2048)break;if(npc!=null&&(citizen(npc.getId())||VarlamoreThievingState.House.owner(npc.getId())!=null)&&npcs.size()<64)npcs.put(npc.getIndex(),npc);}
    }
    private void seedScene()
    {
        WorldView view=client.getTopLevelWorldView();if(view==null||view.getScene()==null)return;Scene scene=view.getScene();
        if(view==seededView&&scene==seeded&&view.getBaseX()==baseX&&view.getBaseY()==baseY&&view.getPlane()==plane)return;
        if(seeded!=null){state.clear();previousValuables=-1;}
        objects.clear();seededView=view;seeded=scene;baseX=view.getBaseX();baseY=view.getBaseY();plane=view.getPlane();
        Tile[][][] tiles=scene.getTiles();if(tiles==null||plane<0||plane>=tiles.length||tiles[plane]==null)return;
        for(Tile[] row:tiles[plane])if(row!=null)for(Tile tile:row)if(tile!=null)
        {observe(tile.getWallObject());GameObject[] game=tile.getGameObjects();if(game!=null)for(GameObject object:game)observe(object);}
    }
    private void observe(TileObject object)
    {
        if(!ready()||object==null||objects.containsKey(object)||objects.size()>=192)return;WorldPoint point=object.getWorldLocation();
        if(point!=null&&VarlamoreThievingState.activityArea(point)&&(VarlamoreThievingState.House.doorAt(point)!=null||VarlamoreThievingState.House.at(point)!=null&&furniture(object.getId())||object.getId()==52998||object.getId()==52000||object.getId()==52001))objects.put(object,point);
    }
    private int resolvedId(TileObject object)
    {ObjectComposition definition=client.getObjectDefinition(object.getId());if(definition!=null&&definition.getImpostorIds()!=null){ObjectComposition resolved=definition.getImpostor();return resolved==null?-1:resolved.getId();}return object.getId();}
    static boolean furniture(int id){return id==52008||id==52010||id==52011;}
    static String furnitureName(int id){return id==52010?"Wardrobe":id==52011?"Jewellery case":"Chest";}
    private int count(int id)
    {ItemContainer container=client.getItemContainer(InventoryID.INV);if(container==null||container.getItems()==null)return 0;long total=0;for(Item item:container.getItems())if(item!=null&&item.getId()==id)total+=Math.max(0,item.getQuantity());return (int)Math.min(Integer.MAX_VALUE,total);}
    void onGameObjectSpawned(GameObjectSpawned event){if(ready()&&event!=null){scope();observe(event.getGameObject());}}
    void onGameObjectDespawned(GameObjectDespawned event){if(event!=null)objects.remove(event.getGameObject());}
    void onWallObjectSpawned(WallObjectSpawned event){if(ready()&&event!=null){scope();observe(event.getWallObject());}}
    void onWallObjectDespawned(WallObjectDespawned event){if(event!=null)objects.remove(event.getWallObject());}
    void onNpcSpawned(NpcSpawned event){} void onNpcChanged(NpcChanged event){} void onNpcDespawned(NpcDespawned event){if(event!=null&&event.getNpc()!=null)npcs.remove(event.getNpc().getIndex());}
    void onGraphicChanged(GraphicChanged event){}
    void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(!ready()||event==null||!furniture(event.getId()))return;
        MenuAction action=event.getMenuAction();if(action!=MenuAction.GAME_OBJECT_FIRST_OPTION&&action!=MenuAction.GAME_OBJECT_SECOND_OPTION&&action!=MenuAction.GAME_OBJECT_THIRD_OPTION&&action!=MenuAction.GAME_OBJECT_FOURTH_OPTION&&action!=MenuAction.GAME_OBJECT_FIFTH_OPTION)return;
        String option=event.getMenuOption()==null?"":Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
        if(!option.equals("search")&&!option.equals("steal-from")&&!option.equals("steal"))return;
        scope();state.enter(VarlamoreThievingState.House.at(client.getLocalPlayer().getWorldLocation()));WorldView view=client.getTopLevelWorldView();if(view==null)return;
        WorldPoint point=new WorldPoint(view.getBaseX()+event.getParam0(),view.getBaseY()+event.getParam1(),view.getPlane());
        if(objects.entrySet().stream().anyMatch(e->e.getKey().getId()==event.getId()&&e.getValue().equals(point)))state.requestSearch(point);
    }
    void onChatMessage(ChatMessage event)
    {
        if(!ready()||event==null||(event.getType()!=ChatMessageType.GAMEMESSAGE&&event.getType()!=ChatMessageType.SPAM))return;
        scope();state.enter(VarlamoreThievingState.House.at(client.getLocalPlayer().getWorldLocation()));
        String message=event.getMessage()==null?"":Text.removeTags(event.getMessage()).trim();long now=now();
        if(message.equals("You notice an urchin distract a wealthy citizen nearby."))state.noticeDistraction(now);
        else if(message.equals("You notice something shine somewhere else in the house."))state.noticeBonus(now);
        else if(message.equals("You hear someone outside spot the homeowner coming back!"))state.warnReturn();
        else if(message.startsWith("You can't spot anything else worth taking"))state.emptyContainer();
    }
    private static boolean validPreferences(Preferences p){return p!=null&&p.version==1&&p.scalePercent>=70&&p.scalePercent<=150;}
    private void update(Consumer<Preferences> action)
    {if(!started||refreshing)return;Preferences next=preferences.copy();action.accept(next);if(validPreferences(next)&&store.write(GROUP,null,KEY,1,next))preferences=next;refreshLater();}
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.add(SidebarUi.heading("VARLAMORE THIEVING"));
        status=SidebarUi.label("");facts=SidebarUi.label("");panel.add(status);panel.add(facts);
        panel.add(SidebarUi.button("Open current training guide",()->{Snapshot current=reading();Consumer<String> action=openGuide;if(started&&current.active&&action!=null)action.accept(current.house==null?"training:wealthy-citizens":"training:stealing-valuables");}));
        JPanel options=SidebarUi.section("DISPLAY");panel.add(options);
        choice(options,"hud","Show activity HUD",p->p.hud);choice(options,"radialTimers","Circular timers",p->p.radialTimers);choice(options,"labels","Scene labels",p->p.labels);
        choice(options,"citizens","Citizen highlights",p->p.citizens);choice(options,"owners","House owner highlights",p->p.owners);choice(options,"doors","House entrances",p->p.doors);choice(options,"windows","Escape windows",p->p.windows);choice(options,"furniture","Searchable furniture",p->p.furniture);choice(options,"bonus","Bonus furniture",p->p.bonus);choice(options,"compact","Compact HUD",p->p.compact);
        options.add(SidebarUi.label("HUD scale"));scale=new JSlider(70,150,100);scale.setName("varlamoreThievingScale");scale.setOpaque(false);scale.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));scale.addChangeListener(e->{if(!scale.getValueIsAdjusting())update(p->p.scalePercent=scale.getValue());});options.add(scale);
        refreshPanel();return panel;
    }
    private interface Flag{boolean value(Preferences p);}
    private void choice(JPanel parent,String key,String label,Flag flag)
    {
        JCheckBox box=new JCheckBox(label);box.setName("varlamoreThieving"+key);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);box.setBorder(javax.swing.BorderFactory.createEmptyBorder(4,0,4,0));
        box.addActionListener(e->update(p->{boolean value=box.isSelected();switch(key){case "hud":p.hud=value;break;case "radialTimers":p.radialTimers=value;break;case "labels":p.labels=value;break;case "citizens":p.citizens=value;break;case "owners":p.owners=value;break;case "doors":p.doors=value;break;case "windows":p.windows=value;break;case "furniture":p.furniture=value;break;case "bonus":p.bonus=value;break;case "compact":p.compact=value;break;default:break;}}));
        box.putClientProperty("flag",flag);controls.put(key,box);parent.add(box);
    }
    private void refreshLater(){if(panel==null)return;long expected=epoch;SwingUtilities.invokeLater(()->{if(expected==epoch)refreshPanel();});}
    private void refreshPanel()
    {
        if(panel==null)return;refreshing=true;try{Snapshot current=reading();status.setText(current.active?current.title+"\n"+current.objective:"No Varlamore thieving activity");facts.setText(String.join("\n",current.facts));
            for(JCheckBox control:controls.values()){control.setSelected(((Flag)control.getClientProperty("flag")).value(preferences));control.setEnabled(started);}scale.setValue(preferences.scalePercent);scale.setEnabled(started);panel.revalidate();panel.repaint();}finally{refreshing=false;}
    }
    int observedCount(){return objects.size();}
    /** Verified native identities, retained as diagnostics without inventing enum mappings. */
    int[] nativeActivityFields()
    {return ready()?new int[]{client.getVarbitValue(9928),client.getVarbitValue(9929),client.getVarbitValue(9930),client.getVarbitValue(9931),client.getVarbitValue(9932)}:new int[0];}
}
