/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

/** Account-local clue juggling, using native item deadlines instead of a fixed one-hour guess. */
@Singleton
final class ClueJugglingTool
{
    static final String GROUP="runewardenjuggling",PREFERENCES="preferencesV1",TIMERS="timersV1";
    static final class Preferences
    {
        int version=1,notifySeconds=30;boolean hud=true,combined,ticks,radial=true;int tiers=127;String extra="";
        Preferences copy(){Preferences p=new Preferences();p.notifySeconds=notifySeconds;p.hud=hud;p.combined=combined;p.ticks=ticks;p.radial=radial;p.tiers=tiers;p.extra=extra;return p;}
        boolean includes(ClueJugglingTracker.Tier tier){return (tiers&(1<<tier.ordinal()))!=0;}
        static boolean valid(Preferences p){return p!=null&&p.version==1&&p.notifySeconds>=0&&p.notifySeconds<=3600&&p.tiers>=0&&p.tiers<=127&&p.extra!=null&&p.extra.length()<=4096;}
    }
    static final class Snapshot
    {
        static final Snapshot EMPTY=new Snapshot(null,0,Collections.emptyList(),Collections.emptyMap());
        final String profile;final long epoch;final List<ClueJugglingTracker.Row> rows;final Map<Integer,AsyncBufferedImage> icons;
        Snapshot(String profile,long epoch,List<ClueJugglingTracker.Row> rows,Map<Integer,AsyncBufferedImage> icons)
        {this.profile=profile;this.epoch=epoch;this.rows=rows;this.icons=Collections.unmodifiableMap(new LinkedHashMap<>(icons));}
    }
    private static final class Definition
    {final String name;final ClueJugglingTracker.Tier tier;final AsyncBufferedImage icon;Definition(String name,ClueJugglingTracker.Tier tier,AsyncBufferedImage icon){this.name=name;this.tier=tier;this.icon=icon;}}
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final ItemManager items;
    private final RunewardenDataStore store;private final Notifier notifier;private final ClueJugglingOverlay overlay;
    private final ClueJugglingTracker tracker=new ClueJugglingTracker();
    private final IdentityHashMap<TileItem,Long> live=new IdentityHashMap<>();private final Set<Long> pending=new HashSet<>();
    private final Map<Integer,Definition> definitions=new LinkedHashMap<Integer,Definition>(256,.75f,true)
    {@Override protected boolean removeEldestEntry(Map.Entry<Integer,Definition> eldest){return size()>512;}};
    private volatile Snapshot snapshot=Snapshot.EMPTY;private volatile Preferences preferences=new Preferences();private volatile boolean started;
    private volatile String profile;private volatile long epoch;private int tick,world;private Scene scene;private int baseX,baseY,plane=-1,saveTicks;
    private boolean seeded;private volatile boolean active;private WorldView observedView;private volatile Consumer<WorldPoint> route=ignored->{};
    private JPanel panel,body;private JCheckBox hud,combine,radial;private final Map<ClueJugglingTracker.Tier,JCheckBox> tiers=new java.util.EnumMap<>(ClueJugglingTracker.Tier.class);
    private JComboBox<String> units;private JSpinner notification;private JTextField extra;private boolean refreshing;private int page;private volatile boolean dirty=true;private final java.util.concurrent.atomic.AtomicBoolean refreshQueued=new java.util.concurrent.atomic.AtomicBoolean();

    @Inject ClueJugglingTool(Client client,ClientThread thread,ConfigManager config,ItemManager items,RunewardenDataStore store,Notifier notifier,RunewardenConfig hudConfig)
    {this.client=client;this.thread=thread;this.config=config;this.items=items;this.store=store;this.notifier=notifier;overlay=new ClueJugglingOverlay(this,hudConfig);reloadPreferences();}
    void start(){started=true;reset();reloadPreferences();}
    void stop(){if(active)save(false);started=false;reset();}
    void clear(){reset();}
    void setRouteAction(Consumer<WorldPoint> action){route=action==null?ignored->{}:action;}
    void setHudLocked(boolean locked){overlay.setLocked(locked);}
    Overlay overlay(){return overlay;}
    Preferences preferences(){return preferences;}
    Snapshot reading(){Snapshot current=snapshot;return started&&active&&current.epoch==epoch&&Objects.equals(current.profile,config.getRSProfileKey())?current:Snapshot.EMPTY;}
    private void reset()
    {++epoch;active=false;profile=null;tick=0;world=0;saveTicks=0;tracker.clear();clearScene();definitions.clear();snapshot=Snapshot.EMPTY;refresh();}
    private void clearScene(){tracker.dropTransient();tracker.detach();observedView=null;live.clear();pending.clear();seeded=false;scene=null;plane=-1;}
    private boolean bind()
    {
        if(!started||client.getGameState()!=GameState.LOGGED_IN)return false;
        String owner=config.getRSProfileKey();if(owner==null||!owner.startsWith("rsprofile"))return false;
        if(!owner.equals(profile))
        {
            // Never write the previous character after the profile has switched.
            reset();profile=owner;tracker.restore(store.read(GROUP,owner,TIMERS,1,ClueJugglingTracker.Saved.class,ClueJugglingTracker::valid,ClueJugglingTracker.Saved::new));
            active=true;tick=client.getTickCount();world=client.getWorld();save(false);
        }
        return active;
    }
    void onGameTick()
    {
        if(!started)return;
        if(client.getGameState()!=GameState.LOGGED_IN){if(active&&client.getGameState()!=GameState.LOADING)suspend(false);return;}
        if(!bind())return;
        int currentTick=client.getTickCount(),delta=currentTick-tick;
        if(delta>=0&&delta<=ClueJugglingTracker.MAX_TICKS)tracker.advance(delta);
        else{ClueJugglingTracker.Saved uncertain=tracker.save(false);tracker.restore(uncertain);clearScene();}
        tick=currentTick;
        int currentWorld=client.getWorld();if(currentWorld!=world){clearScene();tracker.removeWorldBound(currentWorld);world=currentWorld;}
        processDespawned();ensureSeeded();
        for(Map.Entry<TileItem,Long> observed:new ArrayList<>(live.entrySet()))refreshNative(observed.getKey(),observed.getValue());
        for(ClueJugglingTracker.Row row:tracker.alerts((int)Math.ceil(preferences.notifySeconds/.6d),preferences::includes))
            notifier.notify("Runewarden · "+row.name+" expires in "+ClueJugglingTracker.time(row,false)+" ("+row.point.getX()+", "+row.point.getY()+")");
        publish();if(++saveTicks>=10){saveTicks=0;save(false);}
    }
    void onGameStateChanged(GameState state)
    {
        if(!started)return;
        if(state==GameState.LOADING){clearScene();publish();return;}
        if(state==GameState.LOGGED_IN)return;
        // Only an observed normal logout or world hop may freeze a trustworthy checkpoint.
        suspend(state==GameState.LOGIN_SCREEN||state==GameState.HOPPING);
    }
    private void suspend(boolean clean)
    {
        if(active){save(clean);active=false;tracker.clear();live.clear();pending.clear();snapshot=Snapshot.EMPTY;++epoch;profile=null;seeded=false;scene=null;refresh();}
    }
    private void save(boolean clean)
    {if(profile!=null&&Objects.equals(profile,config.getRSProfileKey()))store.write(GROUP,profile,TIMERS,1,tracker.save(clean));}
    void onWorldViewUnloaded(WorldViewUnloaded event)
    {if(event!=null&&(event.getWorldView()==observedView||event.getWorldView()==client.getTopLevelWorldView())){clearScene();publish();}}
    void onItemSpawned(ItemSpawned event)
    {if(event!=null&&bind())observe(event.getTile(),event.getItem());}
    void onItemDespawned(ItemDespawned event)
    {
        if(event==null||!active)return;Long key=live.remove(event.getItem());if(key==null)return;
        tracker.absent(key);pending.add(key);
    }
    void onItemQuantityChanged(ItemQuantityChanged event)
    {if(event!=null&&bind()){Long key=live.get(event.getItem());if(event.getNewQuantity()<=0){if(key!=null){live.remove(event.getItem());tracker.absent(key);pending.add(key);}}else observe(event.getTile(),event.getItem(),event.getNewQuantity());}}
    private void processDespawned()
    {
        WorldPoint player=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldLocation();
        List<ClueJugglingTracker.Row> rows=tracker.rows();
        for(ClueJugglingTracker.Row row:rows)if(pending.contains(row.key)&&!row.present&&player!=null&&player.getPlane()==row.point.getPlane()&&player.distanceTo2D(row.point)<=14)
            tracker.remove(row.key);
        pending.clear();
    }
    private void observe(Tile tile,TileItem item){observe(tile,item,item==null?0:item.getQuantity());}
    private void observe(Tile tile,TileItem item,int quantity)
    {
        if(tile==null||item==null||quantity<=0||tile.getWorldLocation()==null)return;
        WorldView top=client.getTopLevelWorldView();
        // The account-persistent ground-item mechanic belongs to the top-level world, not temporary sub-views.
        if(tile.getItemLayer()!=null&&tile.getItemLayer().getWorldView()!=null&&tile.getItemLayer().getWorldView()!=top)return;
        int id=item.getId();Definition definition=definition(id);if(definition==null)return;
        ClueJugglingTracker.Tier tier=definition.tier;
        if(tier==null&&!extraMatches(preferences.extra,id,definition.name))return;if(tier==null)tier=ClueJugglingTracker.Tier.EXTRA;
        if(item.getOwnership()==TileItem.OWNERSHIP_OTHER||item.getOwnership()==TileItem.OWNERSHIP_GROUP)return;
        int deadline=item.getDespawnTime();if(deadline>0&&deadline<=client.getTickCount())return;
        Long known=live.get(item);long key=tracker.observe(id,definition.name,tier,tile.getWorldLocation(),client.getWorld(),quantity,item.isPrivate(),deadline>0?deadline-client.getTickCount():-1,known==null?-1:known);
        if(key>0){if(top!=null&&top.isInstance())tracker.transientEntry(key);live.put(item,key);pending.remove(key);}
    }
    private void refreshNative(TileItem item,long key)
    {
        ClueJugglingTracker.Row row=tracker.row(key);
        if(row==null){live.remove(item);return;}
        int deadline=item.getDespawnTime();if(deadline>0&&deadline<=tick){tracker.remove(key);live.remove(item);return;}
        tracker.observe(row.id,row.name,row.tier,row.point,world,Math.max(1,item.getQuantity()),item.isPrivate(),deadline>0?deadline-tick:-1,key);
    }
    private Definition definition(int id)
    {
        Definition cached=definitions.get(id);if(cached!=null)return cached;
        ItemComposition item=items.getItemComposition(id);if(item==null)return null;
        String name=Text.removeTags(item.getMembersName()==null?"":item.getMembersName()).replace('\n',' ').replace('\r',' ').trim();if(name.length()>100)name=name.substring(0,100);
        ClueJugglingTracker.Tier tier=ClueJugglingTracker.Tier.from(name,id==ItemID.CHALLENGE_SCROLL_ELITE);
        cached=new Definition(name,tier,tier!=null||extraMatches(preferences.extra,id,name)?items.getImage(id):null);definitions.put(id,cached);return cached;
    }
    private void ensureSeeded()
    {
        WorldView view=client.getTopLevelWorldView();if(view==null)return;
        Scene current=view.getScene();if(current==null)return;
        if(current!=scene||view.getBaseX()!=baseX||view.getBaseY()!=baseY||view.getPlane()!=plane)
        {clearScene();observedView=view;scene=current;baseX=view.getBaseX();baseY=view.getBaseY();plane=view.getPlane();}
        if(seeded)return;seeded=true;Tile[][][] tiles=current.getTiles();if(tiles==null||plane<0||plane>=tiles.length)return;
        Set<WorldPoint> visible=new HashSet<>();WorldPoint player=client.getLocalPlayer()==null?null:client.getLocalPlayer().getWorldLocation();
        Tile[][] floor=tiles[plane];if(floor==null)return;
        for(Tile[] column:floor)if(column!=null)for(Tile tile:column)if(tile!=null)
        {
            WorldPoint point=tile.getWorldLocation();if(point!=null&&player!=null&&point.distanceTo2D(player)<=14)visible.add(point);
            List<TileItem> ground=tile.getGroundItems();if(ground!=null)for(TileItem item:ground)observe(tile,item);
        }
        for(ClueJugglingTracker.Row row:tracker.rows())if(!row.present&&visible.contains(row.point))tracker.remove(row.key);
    }
    private void publish()
    {
        if(!active){snapshot=Snapshot.EMPTY;refresh();return;}
        List<ClueJugglingTracker.Row> visible=new ArrayList<>();Map<Integer,AsyncBufferedImage> icons=new LinkedHashMap<>();
        for(ClueJugglingTracker.Row row:tracker.rows())if(preferences.includes(row.tier))
        {visible.add(row);Definition definition=definitions.get(row.id);if(definition==null)definition=definition(row.id);if(definition!=null&&definition.icon!=null)icons.put(row.id,definition.icon);}
        snapshot=new Snapshot(profile,epoch,Collections.unmodifiableList(visible),icons);refresh();
    }
    private boolean owns(Snapshot owner)
    {Snapshot current=reading();return owner.profile!=null&&owner.epoch==current.epoch&&Objects.equals(owner.profile,current.profile);}
    void remove(long key){remove(reading(),key);}
    private void remove(Snapshot owner,long key)
    {
        if(!owns(owner))return;
        thread.invokeLater(()->{if(owns(owner)){tracker.remove(key);live.values().removeIf(value->value==key);pending.remove(key);save(false);publish();}});
    }
    void clearTimers(){clearTimers(reading());}
    private void clearTimers(Snapshot owner)
    {
        if(!owns(owner))return;
        thread.invokeLater(()->{if(owns(owner)){tracker.clear();live.clear();pending.clear();++epoch;save(false);publish();}});
    }
    private void route(Snapshot owner,ClueJugglingTracker.Row row)
    {Snapshot current=reading();if(owner.epoch!=current.epoch||!Objects.equals(owner.profile,current.profile))return;for(ClueJugglingTracker.Row candidate:current.rows)if(candidate.key==row.key&&candidate.id==row.id&&candidate.point.equals(row.point)){route.accept(candidate.point);return;}}
    void onConfigChanged(ConfigChanged event){if(event!=null&&GROUP.equals(event.getGroup())&&PREFERENCES.equals(event.getKey()))reloadPreferences();}
    void reloadPreferences()
    {preferences=store.read(GROUP,null,PREFERENCES,1,Preferences.class,Preferences::valid,Preferences::new);refresh();}
    private void preference(Consumer<Preferences> action)
    {Preferences next=preferences.copy();action.accept(next);if(!Preferences.valid(next))return;thread.invokeLater(()->{if(store.write(GROUP,null,PREFERENCES,1,next)){boolean rescan=!next.extra.equals(preferences.extra);preferences=next;if(rescan){clearScene();definitions.clear();}publish();}});}
    static boolean extraMatches(String rules,int id,String name)
    {
        if(rules==null||rules.length()>4096)return false;String text=name==null?"":name.toLowerCase(java.util.Locale.ROOT);int count=0;
        for(String rule:rules.split(","))
        {
            if(++count>64)break;rule=rule.trim().toLowerCase(java.util.Locale.ROOT);if(rule.isEmpty()||rule.length()>100)continue;
            if(rule.matches("[0-9]+")){try{if(Integer.parseInt(rule)==id)return true;}catch(NumberFormatException ignored){}continue;}
            if(wildcard(rule,text))return true;
        }
        return false;
    }
    private static boolean wildcard(String pattern,String text)
    {
        int p=0,t=0,star=-1,mark=0;
        while(t<text.length())
        {if(p<pattern.length()&&pattern.charAt(p)==text.charAt(t)){p++;t++;}else if(p<pattern.length()&&pattern.charAt(p)=='*'){star=p++;mark=t;}else if(star>=0){p=star+1;t=++mark;}else return false;}
        while(p<pattern.length()&&pattern.charAt(p)=='*')p++;return p==pattern.length();
    }
    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("runewarden.clue-juggling");panel.addHierarchyListener(event->{if((event.getChangeFlags()&java.awt.event.HierarchyEvent.SHOWING_CHANGED)!=0&&panel.isShowing()&&dirty)refresh();});panel.add(SidebarUi.heading("CLUE JUGGLING"));
        body=SidebarUi.column();body.setName("clueTimerRows");panel.add(body);
        JPanel options=SidebarUi.column();hud=choice(options,"Show clue timer HUD",p->p.hud=hud.isSelected());combine=choice(options,"Combine to earliest timer",p->p.combined=combine.isSelected());radial=choice(options,"Show countdown rings",p->p.radial=radial.isSelected());
        units=new JComboBox<>(new String[]{"Minutes / seconds","Game ticks"});units.addActionListener(event->{if(!refreshing)preference(p->p.ticks=units.getSelectedIndex()==1);});options.add(SidebarUi.field("Countdown units",units));
        notification=new JSpinner(new SpinnerNumberModel(30,0,3600,5));notification.addChangeListener(event->{if(!refreshing)preference(p->p.notifySeconds=((Number)notification.getValue()).intValue());});options.add(SidebarUi.field("Notify with seconds remaining (0 off)",notification));
        for(ClueJugglingTracker.Tier tier:ClueJugglingTracker.Tier.values())
        {JCheckBox box=new JCheckBox(tier.label+" timers");box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);box.addActionListener(event->{if(!refreshing)preference(p->{if(box.isSelected())p.tiers|=1<<tier.ordinal();else p.tiers&=~(1<<tier.ordinal());});});tiers.put(tier,box);options.add(box);}
        extra=new JTextField();options.add(SidebarUi.field("Extra item IDs / names / * wildcards",extra));options.add(SidebarUi.button("Save extra items",()->preference(p->p.extra=extra.getText().trim())));
        panel.add(SidebarUi.disclosure("Clue timer options",options));render();return panel;
    }
    private JCheckBox choice(JPanel parent,String label,Consumer<Preferences> action)
    {JCheckBox box=new JCheckBox(label);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);box.addActionListener(event->{if(!refreshing)preference(action);});parent.add(box);return box;}
    private void refresh()
    {
        dirty=true;
        if(panel!=null&&panel.isShowing()&&refreshQueued.compareAndSet(false,true))SwingUtilities.invokeLater(()->{refreshQueued.set(false);if(panel!=null&&panel.isShowing()&&dirty)render();});
    }
    private void render()
    {
        if(body==null)return;refreshing=true;dirty=false;
        try
        {
            hud.setSelected(preferences.hud);combine.setSelected(preferences.combined);radial.setSelected(preferences.radial);units.setSelectedIndex(preferences.ticks?1:0);notification.setValue(preferences.notifySeconds);
            if(!extra.isFocusOwner())extra.setText(preferences.extra);for(Map.Entry<ClueJugglingTracker.Tier,JCheckBox> item:tiers.entrySet())item.getValue().setSelected(preferences.includes(item.getKey()));
            body.removeAll();Snapshot current=reading();if(current.rows.isEmpty()){body.add(SidebarUi.label("No tracked ground clues"));return;}
            page=Math.max(0,Math.min(page,(current.rows.size()-1)/12));int first=page*12,last=Math.min(current.rows.size(),first+12);
            if(current.rows.size()>12)
            {
                body.add(SidebarUi.label((first+1)+"–"+last+" of "+current.rows.size()+" timers"));
                javax.swing.JButton previous=SidebarUi.button("Previous",()->{if(owns(current)){page=Math.max(0,page-1);render();}}),next=SidebarUi.button("Next",()->{if(owns(current)){page++;render();}});
                previous.setEnabled(first>0);next.setEnabled(last<current.rows.size());body.add(SidebarUi.row(previous,next));
            }
            for(ClueJugglingTracker.Row row:current.rows.subList(first,last))
            {
                JPanel card=SidebarUi.section(row.tier==ClueJugglingTracker.Tier.EXTRA?row.name:row.tier.label+" clue");
                card.add(SidebarUi.heading(ClueJugglingTracker.time(row,preferences.ticks)+(row.quantity>1?" ×"+row.quantity:"")));
                card.add(SidebarUi.label(location(row)+(row.present?" · in view":" · out of view")));
                if(row.certainty==ClueJugglingTracker.Certainty.UNKNOWN)card.add(SidebarUi.label("Revisit to verify expiry"));
                else if(row.certainty==ClueJugglingTracker.Certainty.REMEMBERED)card.add(SidebarUi.label("Saved timer · awaiting sighting"));
                card.add(SidebarUi.row(SidebarUi.button("Route",()->route(current,row)),SidebarUi.button("Remove",()->remove(current,row.key))));body.add(card);
            }
            body.add(SidebarUi.button("Clear tracked timers",()->clearTimers(current)));
        }
        finally{refreshing=false;body.revalidate();body.repaint();}
    }
    static String location(ClueJugglingTracker.Row row){return row.point.getX()+", "+row.point.getY()+" · P"+row.point.getPlane()+(row.privateItem?"":" · W"+row.world);}
}
