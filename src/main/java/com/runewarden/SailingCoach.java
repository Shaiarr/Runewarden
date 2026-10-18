/* SPDX-License-Identifier: GPL-3.0-only
 * Native mappings/logic adapted from pinned BSD sources.
 * Full notices: com/runewarden/licenses/sailing-coach-BSD.txt.
 */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.vars.AccountType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.Text;

/** Native boat, courier-task and trial coaching; no game input or foreign settings. */
@Singleton
final class SailingCoach
{
    static final String GROUP="runewardensailing",KEY="preferencesV1";
    static final class Preferences
    {
        int version=1,scalePercent=100,repairThreshold=30,selectedSlot;
        boolean hud=true,labels=true,sails=true,repair=true,rapids=true,trialTargets=true,mapDestination=true,cargo=true,compact;
        Preferences copy(){Preferences p=new Preferences();p.scalePercent=scalePercent;p.repairThreshold=repairThreshold;p.selectedSlot=selectedSlot;p.hud=hud;p.labels=labels;p.sails=sails;p.repair=repair;p.rapids=rapids;p.trialTargets=trialTargets;p.mapDestination=mapDestination;p.cargo=cargo;p.compact=compact;return p;}
    }
    static final class Target
    {final GameObject object;final String label;final Color color;Target(GameObject object,String label,Color color){this.object=object;this.label=label;this.color=color;}}
    static final class Snapshot
    {
        static final Snapshot EMPTY=new Snapshot(null,0,false,"","",List.of(),List.of(),List.of(),null);
        final String profile,title,objective;final int world;final boolean active;final List<String> facts;final List<Target> targets;final List<SailingCoachTasks.Task> tasks;final SailingCoachTasks.Task selected;
        Snapshot(String profile,int world,boolean active,String title,String objective,List<String> facts,List<Target> targets,List<SailingCoachTasks.Task> tasks,SailingCoachTasks.Task selected)
        {this.profile=profile;this.world=world;this.active=active;this.title=title;this.objective=objective;this.facts=List.copyOf(facts);this.targets=List.copyOf(targets);this.tasks=List.copyOf(tasks);this.selected=selected;}
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final RunewardenConfig hudConfig;private final WorldMapPointManager map;
    private final SailingCoachTasks tasks=new SailingCoachTasks();private final Map<GameObject,Boolean> objects=new IdentityHashMap<>();
    private final SailingCoachHud hud;private final SailingCoachScene scene;
    private volatile Preferences preferences=new Preferences();private volatile Snapshot reading=Snapshot.EMPTY;private volatile boolean started;
    private String profile;private int world=-1,boatId=-1,lastFineX,lastFineY;private boolean havePosition;
    private static final class SceneStamp {final Scene scene;final int baseX,baseY,plane;SceneStamp(WorldView view){scene=view.getScene();baseX=view.getBaseX();baseY=view.getBaseY();plane=view.getPlane();}boolean same(WorldView view){return scene==view.getScene()&&baseX==view.getBaseX()&&baseY==view.getBaseY()&&plane==view.getPlane();}}
    private final Map<WorldView,SceneStamp> seeded=new IdentityHashMap<>();private WorldMapPoint mapPoint;private volatile Consumer<WorldPoint> routeAction;
    private int previousTrial=-1,previousStart,runStart=-1,lastProgress=-1,startKc;private String runningTrial;private final List<String> splits=new ArrayList<>();private String lastAttempt="";
    private JPanel panel;private GuideText status,facts;private JComboBox<SailingCoachTasks.Task> taskChoice;private boolean refreshing;private final Map<String,JCheckBox> controls=new LinkedHashMap<>();private JSlider scale,repairThreshold;private long epoch;
    @Inject SailingCoach(Client client,ConfigManager config,Gson gson,RunewardenDataStore store,RunewardenConfig hudConfig,WorldMapPointManager map)
    {this.client=client;this.config=config;this.store=store;this.hudConfig=hudConfig;this.map=map;hud=new SailingCoachHud(client,this,hudConfig);scene=new SailingCoachScene(client,this);}
    void start(){started=true;clear();reloadPreferences();}
    void stop(){started=false;clear();}
    void clear(){epoch++;reading=Snapshot.EMPTY;objects.clear();seeded.clear();tasks.clear();profile=null;world=boatId=-1;havePosition=false;previousTrial=-1;previousStart=0;runStart=-1;lastProgress=-1;runningTrial=null;splits.clear();lastAttempt="";clearMap();refreshLater();}
    void onGameStateChanged(GameStateChanged event){if(event==null||event.getGameState()!=GameState.LOGGED_IN)clear();}
    void onWorldViewUnloaded(WorldViewUnloaded event){if(event==null||event.getWorldView()==null)return;WorldView gone=event.getWorldView();seeded.remove(gone);objects.keySet().removeIf(o->o.getWorldView()==gone);if(gone.getId()==boatId){havePosition=false;boatId=-1;}}
    void reloadPreferences(){preferences=store.read(GROUP,null,KEY,1,Preferences.class,SailingCoach::valid,Preferences::new);refreshLater();}
    boolean started(){return started;} Preferences preferences(){return preferences;} Overlay hud(){return hud;} Overlay scene(){return scene;}
    void setHudLocked(boolean value){hud.setHudLocked(value);}
    void setRouteAction(Consumer<WorldPoint> action){routeAction=action;}
    private SailingCoachTasks.Task preferredTask(Snapshot current){return current.tasks.stream().filter(t->t.slot==preferences.selectedSlot).findFirst().orElse(current.selected);}
    boolean canRoute(WorldPoint point){Snapshot current=reading();SailingCoachTasks.Task task=preferredTask(current);return current.active&&task!=null&&point!=null&&point.equals(task.nextPort().point);}
    Snapshot reading(){Snapshot current=reading;return started&&current.profile!=null&&client.getGameState()==GameState.LOGGED_IN&&Objects.equals(current.profile,config.getRSProfileKey())&&current.world==client.getWorld()?current:Snapshot.EMPTY;}
    private void scope(){String next=config.getRSProfileKey();int nextWorld=client.getWorld();if(!Objects.equals(profile,next)||world!=nextWorld){clear();profile=next;world=nextWorld;}}
    void onGameTick()
    {
        if(!started)return;if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null){clear();return;}scope();tasks.observe(client);
        WorldView boatView=client.getLocalPlayer().getWorldView(),top=client.getTopLevelWorldView();WorldEntity boat=boat(top,boatView);
        List<String> lines=new ArrayList<>();List<Target> targets=new ArrayList<>();String title="Port tasks",objective="Select a current courier task";boolean sailing=boat!=null;
        if(sailing)
        {
            title="Sailing command";if(boatId!=boatView.getId()){if(boatId!=-1){objects.clear();seeded.clear();}boatId=boatView.getId();havePosition=false;}
            seed(boatView);seed(top);LocalPoint next=boat.getTargetLocation();
            if(next!=null)
            {
                int x=top.getBaseX()*128+next.getX(),y=top.getBaseY()*128+next.getY();String speed=havePosition?String.format(java.util.Locale.ROOT,"%.2f",SailingCoachRules.speed(lastFineX,lastFineY,x,y)):"Unknown";
                lines.add("Heading · "+SailingCoachRules.heading(boat.getTargetOrientation())+"     Speed · "+speed+" tiles/tick");lastFineX=x;lastFineY=y;havePosition=true;
            }
            int hp=client.getVarbitValue(19181),max=client.getVarbitValue(19177),wind=client.getVarbitValue(19178),maxWind=client.getVarbitValue(11629);
            boolean low=preferences.repair&&max>0&&hp>=0&&hp<=max&&(long)hp*100<=(long)max*preferences.repairThreshold;
            lines.add("Hull · "+(max>0&&hp>=0&&hp<=max?hp+" / "+max:"Unknown"));
            if(maxWind>0&&wind>=0&&wind<=maxWind)lines.add("Wind charges · "+wind+" / "+maxWind);
            objective=low?"LOW HULL · repair your boat":"Navigate to your next objective";boolean trim=false,capacityConflict=false;int capacity=-1;
            for(GameObject object:objects.keySet())
            {
                if(object.getWorldView()==boatView)
                {
                    if(preferences.sails&&SailingCoachRules.SAILS.contains(object.getId())&&object.isOpShown(0)){targets.add(new Target(object,"TRIM SAILS",new Color(123,220,160)));trim=true;}
                    if(preferences.repair&&low&&SailingCoachRules.HULLS.contains(object.getId()))targets.add(new Target(object,"REPAIR HULL",new Color(255,119,100)));
                    int candidate=SailingCoachRules.capacity(object.getId(),client.getAccountType()==AccountType.ULTIMATE_IRONMAN);if(candidate>0){if(capacity>0&&capacity!=candidate)capacityConflict=true;capacity=candidate;}
                }
                else if(object.getWorldView()==top&&preferences.rapids&&SailingCoachRules.RAPIDS.contains(object.getId()))targets.add(new Target(object,"Rapids",new Color(123,204,255)));
            }
            if(!low&&trim)objective="TRIM SAILS · native action available";
            if(preferences.cargo)lines.add(cargo(capacityConflict?-1:capacity));trial(lines,targets,top);
        }
        else{havePosition=false;boatId=-1;if(previousTrial>0)finishAttempt();previousTrial=0;previousStart=0;}
        List<SailingCoachTasks.Task> activeTasks=tasks.tasks();SailingCoachTasks.Task selected=activeTasks.stream().filter(t->t.slot==preferences.selectedSlot).findFirst().orElse(activeTasks.isEmpty()?null:activeTasks.get(0));
        if(selected!=null)
        {lines.add("Task · "+selected.from.name+" → "+selected.to.name);lines.add("Cargo · collected "+selected.taken+" / "+selected.amount+" · delivered "+selected.delivered+" / "+selected.amount);if(!sailing||objective.equals("Navigate to your next objective"))objective=selected.objective();}
        if(selected!=null)
        {
            seed(top);for(GameObject object:objects.keySet())if(object.getWorldView()==top&&object.getId()==selected.nextPort().ledger)
                targets.add(new Target(object,selected.taken<selected.amount?"COLLECT CARGO":"DELIVERY LEDGER",HudStyle.GOLD));
        }
        if(tasks.unsupported()>0)lines.add("Other native tasks · "+tasks.unsupported()+" unresolved");
        if(!lastAttempt.isEmpty()&&client.getVarbitValue(18410)==0)lines.add(lastAttempt);
        reading=sailing||!activeTasks.isEmpty()?new Snapshot(profile,world,true,title,objective,lines,targets,activeTasks,selected):Snapshot.EMPTY;
        updateMap(selected);setHudLocked(hudConfig.hudLocked());if(panel!=null&&panel.isShowing())refreshLater();
    }
    static WorldEntity boat(WorldView top,WorldView local)
    {
        if(top==null||local==null||local.isTopLevel()||top.worldEntities()==null)return null;WorldEntity entity=top.worldEntities().byIndex(local.getId());
        if(entity==null||entity.getWorldView()!=local||entity.getConfig()==null)return null;int type=entity.getConfig().getId();return type>=1&&type<=3?entity:null;
    }
    private String cargo(int capacity)
    {
        Widget root=client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE);if(root==null||root.isHidden())return "Cargo hold · "+(capacity>0?"capacity "+capacity:"not observed");
        int id=client.getVarpValue(5204);boolean known=id==InventoryID.SAILING_BOAT_1_CARGOHOLD||id==InventoryID.SAILING_BOAT_2_CARGOHOLD||id==InventoryID.SAILING_BOAT_3_CARGOHOLD||id==InventoryID.SAILING_BOAT_4_CARGOHOLD||id==InventoryID.SAILING_BOAT_5_CARGOHOLD;
        ItemContainer inv=known?client.getItemContainer(id):null;if(inv==null||inv.getItems()==null)return "Cargo hold · contents not observed";
        long used=0;int inspected=0;for(Item item:inv.getItems()){if(++inspected>512)return "Cargo hold · item capacity unknown";if(item==null||item.getId()<1||item.getQuantity()<1||tasks.tasks().stream().anyMatch(t->t.cargo==item.getId()))continue;ItemComposition def=client.getItemDefinition(item.getId());if(def==null)return "Cargo hold · item capacity unknown";used+=def.isStackable()?1:item.getQuantity();}
        return "Cargo hold · "+used+(capacity>0?" / "+capacity:"")+" slots";
    }
    private void trial(List<String> lines,List<Target> targets,WorldView top)
    {
        int inTrial=client.getVarbitValue(18410),start=client.getVarpValue(4987);String trial=SailingCoachRules.trial(client.getVarbitValue(18427),client.getVarbitValue(18432),client.getVarbitValue(18437));
        String difficulty=SailingCoachRules.difficulty(inTrial);
        if(inTrial>0&&trial!=null&&difficulty!=null)
        {
            boolean continuing=previousTrial>0&&trial.equals(runningTrial);
            if(!continuing){runStart=-1;splits.clear();lastProgress=-1;runningTrial=trial;startKc=client.getVarpValue(kcVar(trial));}
            if(continuing&&start!=previousStart&&start>0)runStart=client.getTickCount();
            lines.add(trial+" · "+difficulty);String nativeProgress=text(InterfaceID.SailingBtHud.BT_TRACKER_PROGRESS);if(!nativeProgress.isEmpty())lines.add("Trial progress · "+nativeProgress);
            int[] progress=SailingCoachRules.progress(nativeProgress);if(progress!=null&&progress[0]!=lastProgress)
            {if(lastProgress>=0&&progress[0]>lastProgress&&runStart>=0)splits.add(progress[0]+" · "+SailingCoachRules.elapsed(client.getTickCount()-runStart));lastProgress=progress[0];}
            if(runStart>=0)lines.add("Observed run · "+SailingCoachRules.elapsed(client.getTickCount()-runStart));
            if(!splits.isEmpty())lines.add("Latest split · "+splits.get(splits.size()-1));
            if(preferences.trialTargets)trialTargets(trial,targets,top);
        }
        else if(previousTrial>0&&runningTrial!=null)finishAttempt();
        previousTrial=inTrial;previousStart=start;
    }
    private void finishAttempt(){if(runningTrial!=null)lastAttempt=client.getVarpValue(kcVar(runningTrial))>startKc?runningTrial+" · completed":runningTrial+" · completion not observed";runStart=-1;runningTrial=null;}
    private static int kcVar(String trial){return trial.equals("Tempor Tantrum")?4998:trial.equals("Jubbly Jive")?4999:5000;}
    private void trialTargets(String trial,List<Target> targets,WorldView top)
    {
        Widget partial=client.getWidget(InterfaceID.SailingBtHud.BT_PARTIAL_GFX);boolean rum=partial!=null&&partial.getSpriteId()==7022;
        Widget middle=client.getWidget(InterfaceID.SailingBtHud.BT_MIDDLE_CONTENT);
        int[] progress=SailingCoachRules.progress(text(InterfaceID.SailingBtHud.BT_TRACKER_PROGRESS));int jubbly=-1;
        if(middle!=null&&progress!=null&&progress[1]-progress[0]==1)for(int i=0;i<8;i++){Widget child=middle.getChild(i);if(child!=null&&child.getSpriteId()==6998){jubbly=i;break;}}
        int[] pillars={ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_1_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_2_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_3_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_4_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_5_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_6_PARENT,ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_7_PARENT};
        for(GameObject object:objects.keySet())
        {
            if(object.getWorldView()!=top)continue;int id=object.getId();
            if(trial.equals("Tempor Tantrum")&&id==(rum?ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT:ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT))targets.add(new Target(object,rum?"DELIVER RUM":"COLLECT RUM",HudStyle.GOLD));
            if(SailingCoachRules.LOST_CARGO.contains(id)&&transformed(object)!=null)targets.add(new Target(object,"Trial supplies",HudStyle.GOLD));
            if(trial.equals("Jubbly Jive")&&middle!=null)for(int i=0;i<pillars.length;i++)if(id==pillars[i])
            {Widget child=middle.getChild(i+1);if(i+1>jubbly&&child!=null&&child.getSpriteId()!=6998&&child.getSpriteId()!=6999)targets.add(new Target(object,"Empty outcrop "+(i+1),HudStyle.GOLD));}
        }
    }
    private String text(int id){Widget widget=client.getWidget(id);if(widget==null||widget.isHidden()||widget.getText()==null)return "";String raw=Text.removeTags(widget.getText()).trim();return raw.substring(0,Math.min(120,raw.length()));}
    private ObjectComposition transformed(GameObject object){ObjectComposition def=client.getObjectDefinition(object.getId());return def!=null&&def.getImpostorIds()!=null?def.getImpostor():def;}
    private void seed(WorldView view)
    {
        if(view==null||view.getScene()==null||seeded.containsKey(view)&&seeded.get(view).same(view))return;Scene scene=view.getScene();seeded.put(view,new SceneStamp(view));objects.keySet().removeIf(o->o.getWorldView()==view);
        Tile[][][] tiles=scene.getTiles();int plane=view.getPlane();if(tiles==null||plane<0||plane>=tiles.length||tiles[plane]==null)return;
        for(Tile[] row:tiles[plane])if(row!=null)for(Tile tile:row)if(tile!=null&&tile.getGameObjects()!=null)for(GameObject object:tile.getGameObjects())observe(object);
    }
    private void observe(GameObject object)
    {
        if(!started||object==null||objects.size()>=384||object.getWorldView()==null)return;Player local=client.getLocalPlayer();if(object.getWorldView()!=client.getTopLevelWorldView()&&(local==null||object.getWorldView()!=local.getWorldView()))return;int id=object.getId();
        if(SailingCoachRules.SAILS.contains(id)||SailingCoachRules.HULLS.contains(id)||SailingCoachRules.RAPIDS.contains(id)||SailingCoachRules.capacity(id,false)>0||SailingCoachRules.LOST_CARGO.contains(id)||SailingCoachRules.ledger(id)||id==ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT||id==ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT||id>=ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_1_PARENT&&id<=ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_7_PARENT)objects.put(object,Boolean.TRUE);
    }
    void onGameObjectSpawned(GameObjectSpawned event){if(started&&event!=null&&client.getGameState()==GameState.LOGGED_IN){scope();observe(event.getGameObject());}}
    void onGameObjectDespawned(GameObjectDespawned event){if(event!=null)objects.remove(event.getGameObject());}
    private void clearMap(){if(mapPoint!=null){map.remove(mapPoint);mapPoint=null;}}
    private void updateMap(SailingCoachTasks.Task selected)
    {
        WorldPoint target=profile!=null&&preferences.mapDestination&&selected!=null?selected.nextPort().point:null;
        if(target==null){clearMap();return;}if(mapPoint!=null&&target.equals(mapPoint.getWorldPoint())){mapPoint.setTooltip(selected.objective());return;}clearMap();
        BufferedImage image=new BufferedImage(24,24,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setColor(new Color(12,14,18));g.fillOval(1,1,22,22);g.setColor(HudStyle.GOLD);g.drawOval(1,1,22,22);g.fillPolygon(new int[]{12,5,19},new int[]{4,18,18},3);}finally{g.dispose();}
        mapPoint=new WorldMapPoint(target,image);mapPoint.setName("Runewarden · "+selected.nextPort().name);mapPoint.setTooltip(selected.objective());mapPoint.setSnapToEdge(true);map.add(mapPoint);
    }
    private static boolean valid(Preferences p){return p!=null&&p.version==1&&p.scalePercent>=70&&p.scalePercent<=150&&p.repairThreshold>=5&&p.repairThreshold<=75&&p.selectedSlot>=0&&p.selectedSlot<5;}
    private void update(Consumer<Preferences> action){if(!started||refreshing)return;Preferences next=preferences.copy();action.accept(next);if(valid(next)&&store.write(GROUP,null,KEY,1,next))preferences=next;refreshLater();}
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.add(SidebarUi.heading("SAILING COMMAND"));status=SidebarUi.label("");facts=SidebarUi.label("");panel.add(status);panel.add(facts);
        panel.add(SidebarUi.heading("CURRENT PORT TASK"));taskChoice=new JComboBox<>();taskChoice.setName("sailingCoachTask");taskChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));taskChoice.setBackground(SidebarUi.CARD);taskChoice.setForeground(SidebarUi.TEXT);taskChoice.setFont(GuideText.FONT);taskChoice.addActionListener(e->{if(!refreshing&&taskChoice.getSelectedItem()!=null)update(p->p.selectedSlot=((SailingCoachTasks.Task)taskChoice.getSelectedItem()).slot);});panel.add(taskChoice);
        panel.add(SidebarUi.button("Route to selected port",()->{Snapshot current=reading();Consumer<WorldPoint> action=routeAction;SailingCoachTasks.Task task=preferredTask(current);if(task!=null&&action!=null&&canRoute(task.nextPort().point))action.accept(task.nextPort().point);}));
        JPanel display=SidebarUi.section("DISPLAY");panel.add(display);
        for(String[] item:new String[][]{{"hud","Show Sailing HUD"},{"labels","Scene labels"},{"sails","Trimmable sails"},{"repair","Repair reminder"},{"rapids","Rapid highlights"},{"trialTargets","Trial objectives"},{"mapDestination","Port map destination"},{"cargo","Cargo hold counts"},{"compact","Compact HUD"}})
        {JCheckBox box=new JCheckBox(item[1]);box.setName("sailingCoach"+item[0]);box.setFont(GuideText.FONT);box.setForeground(SidebarUi.TEXT);box.setOpaque(false);box.addActionListener(e->update(p->flag(p,item[0],box.isSelected())));controls.put(item[0],box);display.add(box);}
        display.add(SidebarUi.label("HUD scale"));scale=new JSlider(70,150,100);scale.setName("sailingCoachScale");scale.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));scale.setOpaque(false);scale.addChangeListener(e->{if(!scale.getValueIsAdjusting())update(p->p.scalePercent=scale.getValue());});display.add(scale);
        display.add(SidebarUi.label("Repair reminder · hull %"));repairThreshold=new JSlider(5,75,30);repairThreshold.setName("sailingCoachRepair");repairThreshold.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));repairThreshold.setOpaque(false);repairThreshold.addChangeListener(e->{if(!repairThreshold.getValueIsAdjusting())update(p->p.repairThreshold=repairThreshold.getValue());});display.add(repairThreshold);refreshPanel();return panel;
    }
    private static boolean flag(Preferences p,String key,Boolean set)
    {switch(key){case "hud":if(set!=null)p.hud=set;return p.hud;case "labels":if(set!=null)p.labels=set;return p.labels;case "sails":if(set!=null)p.sails=set;return p.sails;case "repair":if(set!=null)p.repair=set;return p.repair;case "rapids":if(set!=null)p.rapids=set;return p.rapids;case "trialTargets":if(set!=null)p.trialTargets=set;return p.trialTargets;case "mapDestination":if(set!=null)p.mapDestination=set;return p.mapDestination;case "cargo":if(set!=null)p.cargo=set;return p.cargo;case "compact":if(set!=null)p.compact=set;return p.compact;default:return false;}}
    private void refreshLater(){if(panel==null)return;long expected=epoch;SwingUtilities.invokeLater(()->{if(expected==epoch)refreshPanel();});}
    private void refreshPanel()
    {
        if(panel==null)return;refreshing=true;try{Snapshot current=reading();status.setText(current.active?current.title+"\n"+current.objective:"No current Sailing activity");facts.setText(String.join("\n",current.facts));taskChoice.removeAllItems();for(SailingCoachTasks.Task task:current.tasks){taskChoice.addItem(task);if(task.slot==preferences.selectedSlot||task==current.selected&&current.tasks.stream().noneMatch(t->t.slot==preferences.selectedSlot))taskChoice.setSelectedItem(task);}taskChoice.setEnabled(started&&!current.tasks.isEmpty());for(Map.Entry<String,JCheckBox> e:controls.entrySet()){e.getValue().setSelected(flag(preferences,e.getKey(),null));e.getValue().setEnabled(started);}scale.setValue(preferences.scalePercent);scale.setEnabled(started);repairThreshold.setValue(preferences.repairThreshold);repairThreshold.setEnabled(started);panel.revalidate();panel.repaint();}finally{refreshing=false;}
    }
}
