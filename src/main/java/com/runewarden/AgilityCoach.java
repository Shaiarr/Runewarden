/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.AgilityShortcut;

/** Native course observations and passive scene assistance; never sends a game action. */
@Singleton
public final class AgilityCoach
{
    static final String GROUP="runewardenagility", KEY="sessionsV1";
    private static final int MAX_OBJECTS=2048;
    private static final Map<Integer,List<AgilityShortcut>> SHORTCUTS=shortcuts();
    private final Client client;
    private final ClientThread clientThread;
    private final ConfigManager config;
    private final RunewardenDataStore store;
    private final AgilityHudOverlay hud;
    private final AgilitySceneOverlay scene;
    private final Map<TileObject,Obstacle> objects=new IdentityHashMap<>();
    private final Map<Tile,Boolean> marks=new IdentityHashMap<>();
    private final Map<Tile,Boolean> sticks=new IdentityHashMap<>();
    private final Collection<Obstacle> objectView=Collections.unmodifiableCollection(objects.values());
    private final Collection<Tile> markView=Collections.unmodifiableCollection(marks.keySet()),stickView=Collections.unmodifiableCollection(sticks.keySet());
    private Saved saved=new Saved();
    private volatile Snapshot snapshot=Snapshot.EMPTY;
    private volatile Preferences preferences=new Preferences();
    private volatile boolean started;
    private boolean seeded, dirty;
    private int epoch, lastXp=-1, lastMarkQuantity=-1, markEvidenceTick=-100, markEvidenceQuantity, basic=-1, advanced=-1;
    private int lastElapsedTick=-1;
    private int observedView=Integer.MIN_VALUE, observedBaseX, observedBaseY, observedPlane=-1;
    private String profile, context;
    private AgilityCourseCatalog wyrm;
    private WorldPoint arenaTarget;
    private int arenaChange=-1;
    private JPanel panel;
    private GuideText activityLabel, totalsLabel, lapLabel, goalLabel, shortcutsLabel;

    static final class Saved
    {
        int version=1;
        Map<String,AgilityTrainingSession> sessions=new LinkedHashMap<>();
        boolean valid()
        {
            if(version!=1||sessions==null||sessions.size()>32)return false;
            for(Map.Entry<String,AgilityTrainingSession> row:sessions.entrySet())
                if(!knownContext(row.getKey())||row.getValue()==null||!row.getValue().valid())return false;
            return true;
        }
    }
    static final class Preferences
    {
        boolean hud=true, obstacles=true, shortcuts=true, marks=true, traps=true, portals=true, sepulchre=true, skilling=true;
        int goal=99;
    }
    static final class Obstacle
    {
        final Tile tile;final TileObject object;final AgilityShortcut shortcut;
        Obstacle(Tile tile,TileObject object,AgilityShortcut shortcut){this.tile=tile;this.object=object;this.shortcut=shortcut;}
    }
    static final class Snapshot
    {
        static final Snapshot EMPTY=new Snapshot("", "",0,0,0,0,0,0,0,0,0,0,0,Collections.emptyList());
        final String key,title;
        final long xp;
        final int laps,xpHour,marks,groundMarks,lastLap,bestLap,lapsHour,lapsGoal,arenaTicks,progress;
        final List<String> shortcuts;
        Snapshot(String key,String title,long xp,int laps,int xpHour,int marks,int groundMarks,int lastLap,int bestLap,int lapsHour,int lapsGoal,int arenaTicks,int progress,List<String> shortcuts)
        {this.key=key;this.title=title;this.xp=xp;this.laps=laps;this.xpHour=xpHour;this.marks=marks;this.groundMarks=groundMarks;this.lastLap=lastLap;this.bestLap=bestLap;this.lapsHour=lapsHour;this.lapsGoal=lapsGoal;this.arenaTicks=arenaTicks;this.progress=progress;this.shortcuts=Collections.unmodifiableList(shortcuts);}
        boolean active(){return !key.isEmpty();}
    }

    @Inject public AgilityCoach(Client client,ClientThread clientThread,ConfigManager config,RunewardenDataStore store,RunewardenConfig hudConfig,net.runelite.client.ui.overlay.tooltip.TooltipManager tooltips)
    {this.client=client;this.clientThread=clientThread;this.config=config;this.store=store;hud=new AgilityHudOverlay(client,this,hudConfig);scene=new AgilitySceneOverlay(client,this,tooltips);}
    public void start(){started=true;int expected=++epoch;clearObserved();reloadPreferences();clientThread.invoke(()->{if(started&&expected==epoch)onGameTick();});}
    public void stop(){checkpoint();started=false;epoch++;clearObserved();snapshot=Snapshot.EMPTY;refreshPanel();}
    public void clear(){checkpoint();epoch++;profile=null;saved=new Saved();clearObserved();snapshot=Snapshot.EMPTY;refreshPanel();}
    public AgilityHudOverlay hud(){return hud;}
    public AgilitySceneOverlay scene(){return scene;}
    public void setHudLocked(boolean locked){hud.setHudLocked(locked);}
    Snapshot reading(){return started&&Objects.equals(profile,config.getRSProfileKey())?snapshot:Snapshot.EMPTY;}
    Preferences preferences(){return preferences;}
    boolean running(){return started&&Objects.equals(profile,config.getRSProfileKey());}
    Collection<Obstacle> obstacles(){return objectView;}
    Collection<Tile> groundMarks(){return markView;}
    Collection<Tile> groundSticks(){return stickView;}

    public void onGameTick()
    {
        if(!started)return;
        String current=config.getRSProfileKey();
        if(!Objects.equals(profile,current))
        {
            checkpoint();profile=current;saved=profile==null?new Saved():store.read(GROUP,profile,KEY,1,Saved.class,Saved::valid,Saved::new);
            for(AgilityTrainingSession session:saved.sessions.values())session.interrupt();
            clearObserved();reloadPreferences();
        }
        Player player=client.getLocalPlayer();
        if(profile==null||client.getGameState()!=GameState.LOGGED_IN||player==null||player.getWorldView()==null)
        {interrupt();snapshot=Snapshot.EMPTY;refreshPanel();return;}
        WorldView view=player.getWorldView();
        if(!seeded||view.getId()!=observedView||view.getBaseX()!=observedBaseX||view.getBaseY()!=observedBaseY||view.getPlane()!=observedPlane)seed(view);
        if(lastXp<0)lastXp=client.getSkillExperience(Skill.AGILITY);
        if(basic<0)basic=client.getVarbitValue(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS);
        if(advanced<0)advanced=client.getVarbitValue(VarbitID.VARLAMORE_WYRM_AGILITY_ADVANCED_PROGRESS);
        WorldPoint point=point(player);AgilityCourseCatalog course=course(point);
        String next=course==null?specialContext(point):course.name();
        if(!Objects.equals(context,next)){interrupt();context=next;}
        AgilityTrainingSession session=session();if(session!=null&&lastElapsedTick!=client.getTickCount()){session.tick();dirty=true;}
        lastElapsedTick=client.getTickCount();
        observeMarks(player,session);
        if("BRIMHAVEN".equals(context))
        {
            WorldPoint target=client.getHintArrowPoint();
            if(target!=null&&arenaTarget!=null&&(target.getX()!=arenaTarget.getX()||target.getY()!=arenaTarget.getY()))arenaChange=client.getTickCount();
            if(target!=null)arenaTarget=target;
        }
        else {arenaTarget=null;arenaChange=-1;}
        publish(course,player);
        if(client.getTickCount()%50==0)checkpoint();
    }
    public void onStatChanged(StatChanged event)
    {
        if(!ready()||event.getSkill()!=Skill.AGILITY)return;
        int previous=lastXp;lastXp=event.getXp();if(previous<0||lastXp<=previous)return;
        Player player=client.getLocalPlayer();if(player==null)return;
        WorldPoint point=point(player);AgilityCourseCatalog course=course(point);
        String next=course==null?specialContext(point):course.name();
        if(next==null)return;
        if(!Objects.equals(context,next)){interrupt();context=next;}
        AgilityTrainingSession session=session();session.gain(lastXp-previous);dirty=true;
        boolean completed=course!=null&&course.finish(point)&&session.complete(client.getTickCount());
        publish(course,player);if(completed)checkpoint();
    }
    public void onVarbitChanged(VarbitChanged event)
    {
        if(!ready())return;
        int id=event.getVarbitId(),value=event.getValue(),previous;
        AgilityCourseCatalog course;
        if(id==VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS){previous=basic;basic=value;course=AgilityCourseCatalog.COLOSSAL_WYRM_BASIC;}
        else if(id==VarbitID.VARLAMORE_WYRM_AGILITY_ADVANCED_PROGRESS){previous=advanced;advanced=value;course=AgilityCourseCatalog.COLOSSAL_WYRM_ADVANCED;}
        else return;
        if(!hasWyrm()||previous<0||value<0||value>6)return;
        wyrm=course;
        if(!Objects.equals(context,course.name())){interrupt();context=course.name();}
        if(value==6&&previous!=6){session().complete(client.getTickCount());dirty=true;checkpoint();}
        Player player=client.getLocalPlayer();if(player!=null)publish(course,player);
    }
    public void onGameStateChanged(GameStateChanged event)
    {
        if(!started)return;
        if(event.getGameState()==GameState.LOADING){objects.clear();marks.clear();sticks.clear();seeded=false;}
        else if(event.getGameState()==GameState.HOPPING||event.getGameState()==GameState.LOGIN_SCREEN||event.getGameState()==GameState.CONNECTION_LOST)
        {checkpoint();clearObserved();snapshot=Snapshot.EMPTY;refreshPanel();}
    }
    public void onTileObject(Tile tile,TileObject oldObject,TileObject newObject)
    {
        if(!started)return;
        if(oldObject!=null)objects.remove(oldObject);
        if(newObject==null||tile==null||objects.size()>=MAX_OBJECTS)return;
        Player player=client.getLocalPlayer();if(player==null||player.getWorldView()==null||newObject.getWorldView()!=player.getWorldView())return;
        int id=newObject.getId();AgilityShortcut shortcut=shortcut(client,newObject);
        if(shortcut!=null||AgilityObstacles.obstacle(id)||AgilityObstacles.trap(id,newObject.getWorldLocation().getRegionID()))
            objects.put(newObject,new Obstacle(tile,newObject,shortcut));
    }
    public void onItemSpawned(ItemSpawned event)
    {if(started&&event.getItem()!=null)addGround(event.getTile(),event.getItem());}
    public void onItemDespawned(ItemDespawned event)
    {
        if(!started||event.getItem()==null)return;
        if(event.getItem().getId()==ItemID.GRACE)
        {
            marks.remove(event.getTile());Player player=client.getLocalPlayer();
            if(player!=null&&event.getTile().getWorldLocation().distanceTo(player.getWorldLocation())<=2)
            {if(client.getTickCount()-markEvidenceTick>2)markEvidenceQuantity=0;markEvidenceTick=client.getTickCount();markEvidenceQuantity=Math.min(1000,markEvidenceQuantity+Math.max(1,event.getItem().getQuantity()));}
        }
        if(event.getItem().getId()==ItemID.WAA_STICK)sticks.remove(event.getTile());
    }
    private boolean ready(){return started&&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN;}
    private void clearObserved()
    {interrupt();objects.clear();marks.clear();sticks.clear();seeded=false;lastXp=-1;lastElapsedTick=-1;lastMarkQuantity=-1;basic=-1;advanced=-1;wyrm=null;arenaTarget=null;arenaChange=-1;markEvidenceQuantity=0;markEvidenceTick=-100;}
    private void interrupt(){AgilityTrainingSession session=sessionExisting();if(session!=null)session.interrupt();context=null;}
    private AgilityTrainingSession sessionExisting(){return context==null?null:saved.sessions.get(context);}
    private AgilityTrainingSession session(){return context==null?null:saved.sessions.computeIfAbsent(context,key->new AgilityTrainingSession());}
    private void checkpoint(){if(dirty&&profile!=null){store.write(GROUP,profile,KEY,1,saved);dirty=false;}}
    private void seed(WorldView view)
    {
        objects.clear();marks.clear();sticks.clear();Tile[][][] tiles=view.getScene()==null?null:view.getScene().getTiles();
        if(tiles!=null&&view.getPlane()>=0&&view.getPlane()<tiles.length)
            for(Tile[] row:tiles[view.getPlane()])if(row!=null)for(Tile tile:row)if(tile!=null)
            {
                GameObject[] games=tile.getGameObjects();if(games!=null)for(GameObject object:games)onTileObject(tile,null,object);
                onTileObject(tile,null,tile.getGroundObject());onTileObject(tile,null,tile.getWallObject());onTileObject(tile,null,tile.getDecorativeObject());
                if(tile.getGroundItems()!=null)for(TileItem item:tile.getGroundItems())addGround(tile,item);
            }
        observedView=view.getId();observedBaseX=view.getBaseX();observedBaseY=view.getBaseY();observedPlane=view.getPlane();seeded=true;
    }
    private void addGround(Tile tile,TileItem item)
    {if(tile==null)return;if(item.getId()==ItemID.GRACE&&marks.size()<256)marks.put(tile,true);else if(item.getId()==ItemID.WAA_STICK&&sticks.size()<32)sticks.put(tile,true);}
    private void observeMarks(Player player,AgilityTrainingSession session)
    {
        ItemContainer inventory=client.getItemContainer(InventoryID.INVENTORY);int quantity=0;
        if(inventory==null)return;
        for(Item item:inventory.getItems())if(item!=null&&item.getId()==ItemID.GRACE)quantity+=item.getQuantity();
        if(lastMarkQuantity>=0&&quantity>lastMarkQuantity&&session!=null&&client.getTickCount()-markEvidenceTick<=2)
        {session.marks+=Math.min(quantity-lastMarkQuantity,markEvidenceQuantity);markEvidenceQuantity=0;dirty=true;}
        if(client.getTickCount()-markEvidenceTick>2)markEvidenceQuantity=0;
        lastMarkQuantity=quantity;
    }
    private AgilityCourseCatalog course(WorldPoint point)
    {AgilityCourseCatalog nativeCourse=point==null?null:AgilityCourseCatalog.region(point.getRegionID());return nativeCourse!=null?nativeCourse:hasWyrm()?wyrm:null;}
    private String specialContext(WorldPoint point)
    {
        if(point!=null&&point.getRegionID()==11157)return "BRIMHAVEN";
        if(hasWyrm())return "COLOSSAL_WYRM";
        for(Obstacle obstacle:objects.values())if(AgilityObstacles.SEPULCHRE_OBSTACLE_IDS.contains(obstacle.object.getId()))return "SEPULCHRE";
        return null;
    }
    private boolean hasWyrm()
    {for(Obstacle obstacle:objects.values())if(wyrmObject(obstacle.object.getId()))return true;return false;}
    static boolean wyrmObject(int id)
    {return id==ObjectID.VARLAMORE_WYRM_AGILITY_START_LADDER_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_END_ZIPLINE_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_BALANCE_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_BALANCE_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_JUMP_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_ADVANCED_LADDER_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_BALANCE_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_LADDER_1_TRIGGER||id==ObjectID.VARLAMORE_WYRM_AGILITY_BASIC_MONKEYBARS_1_TRIGGER;}
    static WorldPoint point(Player player)
    {WorldView view=player.getWorldView();return view!=null&&view.getScene()!=null&&player.getLocalLocation()!=null?WorldPoint.fromLocalInstance(view.getScene(),player.getLocalLocation(),view.getPlane()):player.getWorldLocation();}
    private void publish(AgilityCourseCatalog course,Player player)
    {
        AgilityTrainingSession session=sessionExisting();
        List<String> nearby=new ArrayList<>();
        for(Obstacle obstacle:objects.values())if(obstacle.shortcut!=null&&obstacle.tile.getPlane()==player.getWorldView().getPlane()&&obstacle.object.getLocalLocation()!=null&&obstacle.object.getLocalLocation().distanceTo(player.getLocalLocation())<2350)
        {String label=obstacle.shortcut.getDescription()+" · Agility "+obstacle.shortcut.getLevel();if(!nearby.contains(label)&&nearby.size()<8)nearby.add(label);}
        int goal=0;if(course!=null){int remaining=Math.max(0,Experience.getXpForLevel(preferences.goal)-client.getSkillExperience(Skill.AGILITY));goal=(int)Math.ceil(remaining/course.xp(client));}
        int arena=arenaChange<0?0:Math.max(0,100-(client.getTickCount()-arenaChange));
        int progress=course==AgilityCourseCatalog.COLOSSAL_WYRM_BASIC?basic:course==AgilityCourseCatalog.COLOSSAL_WYRM_ADVANCED?advanced:-1;
        String title=course!=null?course.label():"BRIMHAVEN".equals(context)?"Brimhaven Agility Arena":"SEPULCHRE".equals(context)?"Hallowed Sepulchre":"COLOSSAL_WYRM".equals(context)?"Colossal Wyrm":"";
        snapshot=new Snapshot(context==null?"":context,title,session==null?0:session.xp,session==null?0:session.laps,session==null?0:session.xpPerHour(),session==null?0:session.marks,marks.size(),session==null?0:session.lastLap(),session==null?0:session.bestLap(),session==null?0:session.lapsPerHour(),goal,arena,progress,nearby);refreshPanel();
    }
    private static boolean knownContext(String key)
    {if(key==null)return false;try{AgilityCourseCatalog.valueOf(key);return true;}catch(IllegalArgumentException ignored){return key.equals("BRIMHAVEN")||key.equals("SEPULCHRE")||key.equals("COLOSSAL_WYRM");}}
    private static Map<Integer,List<AgilityShortcut>> shortcuts()
    {Map<Integer,List<AgilityShortcut>> map=new HashMap<>();for(AgilityShortcut shortcut:AgilityShortcut.values())for(int id:shortcut.getObstacleIds())map.computeIfAbsent(id,key->new ArrayList<>()).add(shortcut);return map;}
    static AgilityShortcut shortcut(Client client,TileObject object)
    {
        List<AgilityShortcut> choices=SHORTCUTS.get(object.getId());if(choices==null)return null;
        AgilityShortcut best=null;int distance=Integer.MAX_VALUE;
        for(AgilityShortcut choice:choices)if(choice.matches(client,object))
        {if(choice.getWorldLocation()==null)return choice;int next=choice.getWorldLocation().distanceTo2D(object.getWorldLocation());if(next<distance){distance=next;best=choice;}}
        return best;
    }
    private void reloadPreferences()
    {
        Preferences p=new Preferences();p.hud=setting("hud");p.obstacles=setting("obstacles");p.shortcuts=setting("shortcuts");p.marks=setting("marks");p.traps=setting("traps");p.portals=setting("portals");p.sepulchre=setting("sepulchre");p.skilling=setting("skilling");
        String raw=config.getConfiguration(GROUP,"goal");try{if(raw!=null)p.goal=Math.max(2,Math.min(99,Integer.parseInt(raw)));}catch(NumberFormatException ignored){}preferences=p;
    }
    private boolean setting(String key){return !"false".equals(config.getConfiguration(GROUP,key));}
    public JPanel createPanel()
    {
        panel=SidebarUi.column();JPanel status=SidebarUi.section("Agility coach");activityLabel=SidebarUi.heading("");totalsLabel=SidebarUi.label("");lapLabel=SidebarUi.label("");goalLabel=SidebarUi.label("");status.add(activityLabel);status.add(totalsLabel);status.add(lapLabel);status.add(goalLabel);
        status.add(SidebarUi.button("Reset this activity",()->queue(()->{if(context!=null){saved.sessions.put(context,new AgilityTrainingSession());dirty=true;checkpoint();onGameTick();}})));panel.add(status);
        JPanel goal=SidebarUi.section("Training goal");JSpinner level=new JSpinner(new SpinnerNumberModel(preferences.goal,2,99,1));level.addChangeListener(event->{int value=(Integer)level.getValue();config.setConfiguration(GROUP,"goal",value);queue(()->{reloadPreferences();onGameTick();});});goal.add(SidebarUi.field("Agility level",level));panel.add(goal);
        JPanel options=SidebarUi.column();addToggle(options,"Activity HUD","hud",preferences.hud);addToggle(options,"Obstacle clickboxes","obstacles",preferences.obstacles);addToggle(options,"Shortcuts","shortcuts",preferences.shortcuts);addToggle(options,"Marks and werewolf stick","marks",preferences.marks);addToggle(options,"Pyramid trap tiles","traps",preferences.traps);addToggle(options,"Prifddinas portals","portals",preferences.portals);addToggle(options,"Sepulchre floor links","sepulchre",preferences.sepulchre);addToggle(options,"Sepulchre skill objects","skilling",preferences.skilling);panel.add(SidebarUi.disclosure("Display options",options));
        JPanel shortcuts=SidebarUi.section("Nearby shortcuts");shortcutsLabel=SidebarUi.label("");shortcuts.add(shortcutsLabel);panel.add(shortcuts);SidebarUi.install(panel);refreshPanel();return panel;
    }
    private void addToggle(JPanel panel,String text,String key,boolean selected)
    {JCheckBox toggle=new JCheckBox(text,selected);toggle.setName("agility"+key);toggle.setFont(GuideText.FONT);toggle.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,44));toggle.setOpaque(false);toggle.setForeground(SidebarUi.TEXT);toggle.addActionListener(event->{config.setConfiguration(GROUP,key,toggle.isSelected());queue(this::reloadPreferences);});panel.add(toggle);}
    private void queue(Runnable action)
    {int expected=epoch;String expectedProfile=profile;clientThread.invoke(()->{if(started&&epoch==expected&&Objects.equals(expectedProfile,config.getRSProfileKey()))action.run();});}
    private void refreshPanel()
    {
        if(panel==null)return;Snapshot value=reading();int expected=epoch;JPanel target=panel;
        SwingUtilities.invokeLater(()->{if(panel!=target||expected!=epoch)return;activityLabel.setText(value.active()?value.title:"Enter an agility course");totalsLabel.setText(String.format(java.util.Locale.ROOT,"%,d XP · %,d XP/hr\n%d laps · %d marks collected\n%d marks on the ground",value.xp,value.xpHour,value.laps,value.marks,value.groundMarks));lapLabel.setText("Last "+duration(value.lastLap)+" · Best "+duration(value.bestLap)+"\n"+value.lapsHour+" laps/hr");goalLabel.setText(value.lapsGoal>0?value.lapsGoal+" laps to level "+preferences.goal:"");shortcutsLabel.setText(value.shortcuts.isEmpty()?"No nearby shortcuts":String.join("\n",value.shortcuts));});
    }
    static String duration(int ticks){if(ticks<=0)return "—";long seconds=Math.round(ticks*.6);return seconds/60+":"+String.format(java.util.Locale.ROOT,"%02d",seconds%60);}
}
