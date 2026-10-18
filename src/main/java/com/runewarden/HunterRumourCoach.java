/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.LinkBrowser;

/** Native dialogue/whistle observations and local preparation. Never requests or changes a rumour. */
@Singleton
final class HunterRumourCoach
{
    static final String KEY="hunterRumoursV1";
    static final class Record {String rumour="";int catches;boolean ready,manual;long at;}
    static final class Saved
    {int version=1,current,backToBack=-1;long completions;Map<Integer,Record>masters=new LinkedHashMap<>();Map<String,String>locations=new LinkedHashMap<>();boolean hud=true,highlights=true;}
    static final class State
    {
        final String profile;final HunterRumourCatalog.Master master;final HunterRumourCatalog.Rumour rumour;final Map<Integer,String>masters;final String masterAccess;
        final boolean verified,manual,ready,hud,highlights,rareCarried;final int catches,referenceThreshold,outfitPieces,hunterLevel,backToBack;final long completions;
        final Set<Integer>requiredItems;final List<HunterRumourCatalog.Location>locations;
        State(String profile,HunterRumourCatalog.Master master,HunterRumourCatalog.Rumour rumour,Map<Integer,String>masters,boolean verified,boolean manual,boolean ready,boolean hud,boolean highlights,boolean rareCarried,int catches,int threshold,int outfit,int level,int backToBack,long completions,Set<Integer>items,List<HunterRumourCatalog.Location>locations,String access)
        {this.profile=profile;this.master=master;this.rumour=rumour;this.masters=Collections.unmodifiableMap(new LinkedHashMap<>(masters));this.verified=verified;this.manual=manual;this.ready=ready;this.hud=hud;this.highlights=highlights;this.rareCarried=rareCarried;this.catches=catches;referenceThreshold=threshold;outfitPieces=outfit;hunterLevel=level;this.backToBack=backToBack;this.completions=completions;requiredItems=Set.copyOf(items);this.locations=List.copyOf(locations);masterAccess=access;}
        static State empty(){return new State(null,null,null,Map.of(),false,false,false,false,false,false,0,0,0,0,-1,0,Set.of(),List.of(),"");}
    }
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final RunewardenDataStore store;private final HunterRumourCatalog catalog;private final ItemManager items;
    private volatile String profile;private long generation;private boolean started,verified;private Saved saved=new Saved();private int previousXp=-1,ticks;
    private final Map<String,Integer>speakers=new LinkedHashMap<>();private final Set<String>confirmedRecords=new java.util.LinkedHashSet<>();private volatile State state=State.empty();private volatile List<NPC>sceneTargets=List.of();
    private WorldView targetView;private net.runelite.api.Scene targetScene;private int targetBaseX,targetBaseY,targetPlane;
    private JPanel panel;private volatile boolean renderQueued;private String draftProfile,draftRumour;private int draftMaster;private long draftEpoch=-1;private Consumer<WorldPoint>route=ignored->{};private Consumer<Set<Integer>>bank=ignored->{};private Consumer<String>guide=ignored->{};private java.util.function.IntConsumer openItem;
    @Inject HunterRumourCoach(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore store,HunterRumourCatalog catalog,ItemManager items)
    {this.client=client;this.thread=thread;this.config=config;this.store=store;this.catalog=catalog;this.items=items;}
    void setRouteAction(Consumer<WorldPoint>action){route=action==null?ignored->{}:action;}void setBankFocusAction(Consumer<Set<Integer>>action){bank=action==null?ignored->{}:action;}void setGuideAction(Consumer<String>action){guide=action==null?ignored->{}:action;}void setOpenItemAction(java.util.function.IntConsumer action){openItem=action;}
    void start(){started=true;clear();}void stop(){started=false;clear();}
    void clear(){profile=null;generation++;saved=new Saved();verified=false;previousXp=-1;ticks=0;speakers.clear();confirmedRecords.clear();state=State.empty();sceneTargets=List.of();targetView=null;targetScene=null;refresh();}
    State reading(){State current=state;return started&&current.profile!=null&&current.profile.equals(config.getRSProfileKey())?current:State.empty();}
    List<NPC>sceneTargets()
    {State current=reading();WorldView view=client.getTopLevelWorldView();return current.highlights&&current.verified&&!current.ready&&current.rumour!=null&&view==targetView&&view!=null&&view.getScene()==targetScene&&view.getBaseX()==targetBaseX&&view.getBaseY()==targetBaseY&&view.getPlane()==targetPlane?sceneTargets:List.of();}
    void onGameTick()
    {
        if(!started)return;String owner=config.getRSProfileKey();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||owner==null){if(profile!=null)clear();return;}
        if(!owner.equals(profile)){clear();profile=owner;saved=store.read(RunewardenConfig.GROUP,profile,KEY,1,Saved.class,this::valid,Saved::new);previousXp=client.getSkillExperience(Skill.HUNTER);}
        if(speakers.size()<6)for(HunterRumourCatalog.Master master:catalog.masters()){net.runelite.api.NPCComposition definition=client.getNpcDefinition(master.id);if(definition!=null&&definition.getName()!=null)speakers.put(definition.getName().toLowerCase(Locale.ROOT),master.id);}
        if(++ticks%5==1){publish();scanTargets();if(panel!=null&&panel.isShowing())refresh();}
    }
    void reloadPreferences(){if(!current())return;saved=store.read(RunewardenConfig.GROUP,profile,KEY,1,Saved.class,this::valid,Saved::new);verified=false;confirmedRecords.clear();previousXp=client.getSkillExperience(Skill.HUNTER);publish();scanTargets();refresh();}
    static boolean inBurrows(WorldPoint point){return point!=null&&point.getPlane()==0&&point.getX()>=1549&&point.getX()<=1565&&point.getY()>=9449&&point.getY()<=9464;}
    void onChatMessage(ChatMessage event)
    {
        if(!current()||event.getMessage()==null)return;String plain=event.getMessage().replaceAll("<[^>]*>","");if(plain.length()>3000)return;String lower=plain.toLowerCase(Locale.ROOT);
        if(event.getType()==ChatMessageType.GAMEMESSAGE)
        {
            if(lower.contains("your current rumour target is"))
            {HunterRumourCatalog.Rumour rumour=catalog.referencedRumour(lower);HunterRumourCatalog.Master master=catalog.referencedMaster(lower);if(rumour!=null&&master!=null)assign(master.id,rumour.id,false);return;}
            if(verified&&lower.contains("you find a rare piece of the creature! you should take it back to the hunter guild."))
            {Record record=saved.masters.get(saved.current);if(record!=null){record.ready=true;record.at=System.currentTimeMillis();save();publish();refresh();}return;}
        }
        if(event.getType()!=ChatMessageType.DIALOG||!inBurrows(client.getLocalPlayer().getWorldLocation()))return;
        if(lower.startsWith("guild scribe verity|would you like me to filter the rumour information a")){saved.backToBack=lower.contains("little less")?0:1;save();publish();refresh();return;}
        int pipe=lower.indexOf('|');if(pipe<0)return;Integer speaker=speakers.get(lower.substring(0,pipe));if(speaker==null)return;String text=lower.substring(pipe+1);
        if(text.contains("stopped off for a bit of hunting first"))return;
        if(text.contains("would you like another rumour?")||text.contains("here's your reward.")||text.contains("another one done?"))
        {
            Record old=saved.masters.get(speaker);if(old!=null&&!old.rumour.isEmpty()){saved.completions++;confirmedRecords.remove(speaker+":"+old.rumour);saved.masters.remove(speaker);if(saved.current==speaker)verified=false;save();publish();refresh();}return;
        }
        HunterRumourCatalog.Rumour rumour=catalog.referencedRumour(text);if(rumour==null)return;
        HunterRumourCatalog.Master referenced=catalog.referencedMaster(text);int master=referenced==null?speaker:referenced.id;
        if(text.contains("would you prefer that one, or a new one entirely"))
        {Record record=new Record();record.rumour=rumour.id;record.at=System.currentTimeMillis();saved.masters.put(master,record);save();publish();refresh();return;}
        assign(master,rumour.id,false);
    }
    void onStatChanged(StatChanged event)
    {
        if(event.getSkill()!=Skill.HUNTER||!current())return;int difference=previousXp<0?0:event.getXp()-previousXp;previousXp=event.getXp();
        Record record=saved.masters.get(saved.current);HunterRumourCatalog.Rumour rumour=record==null?null:catalog.rumour(record.rumour);
        if(!verified||record==null||record.ready||rumour==null||difference<=0||!rumour.xpDrops.contains(difference))return;
        WorldPoint player=client.getLocalPlayer().getWorldLocation();if(player==null||rumour.locations.stream().noneMatch(location->location.point().distanceTo(player)<=80))return;
        record.catches=Math.min(1_000_000,record.catches+1);record.at=System.currentTimeMillis();save();publish();refresh();
    }
    private void assign(int master,String rumour,boolean manual)
    {
        if(catalog.master(master)==null||catalog.rumour(rumour)==null)return;Record record=saved.masters.get(master);
        if(record==null||!record.rumour.equals(rumour)||record.manual!=manual){record=new Record();record.rumour=rumour;saved.masters.put(master,record);}
        String identity=master+":"+rumour;if(!manual&&!confirmedRecords.contains(identity)){record.catches=0;record.ready=false;confirmedRecords.add(identity);}if(manual)confirmedRecords.remove(identity);
        record.manual=manual;record.at=System.currentTimeMillis();saved.current=master;verified=!manual;previousXp=client.getSkillExperience(Skill.HUNTER);save();publish();scanTargets();refresh();
    }
    private void publish()
    {
        Record record=saved.masters.get(saved.current);HunterRumourCatalog.Rumour rumour=record==null?null:catalog.rumour(record.rumour);Map<Integer,String>held=new LinkedHashMap<>();saved.masters.forEach((id,value)->held.put(id,value.rumour));
        int outfit=0;boolean rare=false;ItemContainer worn=client.getItemContainer(InventoryID.WORN),inventory=client.getItemContainer(InventoryID.INV);
        Set<Integer>outfitIds=Set.of(net.runelite.api.ItemID.GUILD_HUNTER_HEADWEAR,net.runelite.api.ItemID.GUILD_HUNTER_TOP,net.runelite.api.ItemID.GUILD_HUNTER_LEGS,net.runelite.api.ItemID.GUILD_HUNTER_BOOTS);
        if(worn!=null)for(Item item:worn.getItems())if(item!=null&&outfitIds.contains(item.getId()))outfit++;
        if(inventory!=null&&rumour!=null)for(Item item:inventory.getItems())if(item!=null&&item.getId()==rumour.rareItem&&item.getQuantity()>0)rare=true;
        HunterRumourCatalog.Master master=catalog.master(saved.current);int level=client.getRealSkillLevel(Skill.HUNTER);String access="";
        if(master!=null&&!verified){if(level<master.level())access="Needs "+master.level()+" Hunter";else if("MASTER".equals(master.tier))try{net.runelite.api.QuestState quest=net.runelite.api.Quest.AT_FIRST_LIGHT.getState(client);if(quest!=net.runelite.api.QuestState.FINISHED)access=quest==null?"At First Light status unknown":"Needs At First Light";}catch(RuntimeException pending){access="At First Light status unknown";}}
        state=new State(profile,master,rumour,held,verified,record!=null&&record.manual,verified&&record!=null&&record.ready,saved.hud,saved.highlights,rare,record==null?0:record.catches,rumour==null?0:catalog.trap(rumour.trap).threshold(outfit),outfit,level,saved.backToBack,saved.completions,catalog.supplies(rumour),rumour==null?List.of():rumour.locations,access);
    }
    private void scanTargets()
    {
        State current=reading();WorldView view=client.getTopLevelWorldView();List<NPC>found=new ArrayList<>();
        if(view!=null&&current.verified&&current.highlights&&current.rumour!=null&&!current.ready&&current.rumour.npcId>0&&view.npcs()!=null)
        {int seen=0;for(NPC npc:view.npcs()){if(++seen>2048||found.size()>=24)break;if(npc!=null&&npc.getWorldView()==view&&npc.getId()==current.rumour.npcId)found.add(npc);}}
        targetView=view;targetScene=view==null?null:view.getScene();targetBaseX=view==null?0:view.getBaseX();targetBaseY=view==null?0:view.getBaseY();targetPlane=view==null?0:view.getPlane();sceneTargets=List.copyOf(found);
    }
    private boolean current(){return started&&profile!=null&&profile.equals(config.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private boolean valid(Saved data)
    {
        if(data==null||data.version!=1||data.masters==null||data.masters.size()>6||data.locations==null||data.locations.size()>30||data.current!=0&&catalog.master(data.current)==null||data.backToBack< -1||data.backToBack>1||data.completions<0||data.completions>10_000_000)return false;
        long now=System.currentTimeMillis();for(Map.Entry<Integer,Record>entry:data.masters.entrySet()){Record value=entry.getValue();if(catalog.master(entry.getKey())==null||value==null||catalog.rumour(value.rumour)==null||value.catches<0||value.catches>1_000_000||value.at<0||value.at>now)return false;}
        for(Map.Entry<String,String>entry:data.locations.entrySet())if(catalog.rumour(entry.getKey())==null||entry.getValue()==null||entry.getValue().length()>200)return false;return true;
    }
    private void save(){if(profile!=null&&profile.equals(config.getRSProfileKey()))store.write(RunewardenConfig.GROUP,profile,KEY,1,saved);}
    private void dispatch(State snapshot,long epoch,Runnable action)
    {thread.invokeLater(()->{if(current()&&epoch==generation&&snapshot.profile!=null&&snapshot.profile.equals(profile))action.run();});}
    private void edit(State snapshot,long epoch,Consumer<Saved>change)
    {dispatch(snapshot,epoch,()->{change.accept(saved);save();publish();scanTargets();refresh();});}
    JPanel createPanel(){if(panel==null){panel=SidebarUi.column();panel.setName("hunter-rumours");render();}return panel;}
    private void refresh(){if(panel==null||renderQueued)return;renderQueued=true;SwingUtilities.invokeLater(()->{renderQueued=false;if(javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length==0)render();});}
    private void render()
    {
        if(panel==null)return;panel.removeAll();State current=reading();long renderEpoch=generation;panel.add(SidebarUi.heading("HUNTER RUMOURS"));
        if(current.profile==null){panel.add(SidebarUi.label("Log in to view Hunter rumours"));finish();return;}
        JCheckBox hud=check("Rumour HUD",current.hud);hud.addActionListener(event->{boolean enabled=hud.isSelected();edit(current,renderEpoch,value->value.hud=enabled);});panel.add(hud);
        JCheckBox targets=check("Highlight rumour creatures",current.highlights);targets.addActionListener(event->{boolean enabled=targets.isSelected();edit(current,renderEpoch,value->value.highlights=enabled);});panel.add(targets);
        if(current.rumour==null)panel.add(SidebarUi.label("No current rumour observed"));
        else
        {
            JPanel active=SidebarUi.section(current.rumour.name);if(items!=null){javax.swing.JLabel icon=new javax.swing.JLabel();net.runelite.client.util.AsyncBufferedImage image=items.getImage(current.rumour.icon);if(image!=null)image.addTo(icon);active.add(icon);}active.add(SidebarUi.label((current.master==null?"":current.master.name+" · ")+(current.manual?"Manual reference":current.verified?"Observed this session":"Last seen")));
            active.add(SidebarUi.label(current.ready?"Rare piece found · return to the Guild":"Observed matching catches · "+current.catches));
            if(current.rareCarried)active.add(SidebarUi.label("Rare part carried"));active.add(SidebarUi.label("Hunter kit · "+current.outfitPieces+" / 4 worn\nReference bad-luck threshold · "+current.referenceThreshold));
            if(current.outfitPieces<4)active.add(SidebarUi.label("Wear available guild hunter pieces for the rumour perk"));
            if(current.hunterLevel<current.rumour.level)active.add(SidebarUi.label("Needs "+current.rumour.level+" Hunter"));
            if(!current.masterAccess.isEmpty())active.add(SidebarUi.label(current.masterAccess));
            active.add(SidebarUi.label(catalog.trap(current.rumour.trap).name));
            active.add(SidebarUi.button("Focus hunting supplies in bank",()->dispatch(current,renderEpoch,()->bank.accept(current.requiredItems))));
            active.add(SidebarUi.button("Open rumour guide",()->dispatch(current,renderEpoch,()->guide.accept("training:rumours-"+current.master.tier.toLowerCase(Locale.ROOT)))));
            if(openItem!=null)active.add(SidebarUi.button("Rare item sources",()->dispatch(current,renderEpoch,()->openItem.accept(current.rumour.rareItem))));
            active.add(SidebarUi.button("Creature reference",()->LinkBrowser.browse("https://oldschool.runescape.wiki/w/"+current.rumour.name.replace(' ','_'))));panel.add(active);
            JPanel locations=SidebarUi.section("Hunting locations");for(Map.Entry<String,List<HunterRumourCatalog.Location>>entry:current.rumour.groups().entrySet())
            {locations.add(SidebarUi.label(entry.getKey()+" · "+entry.getValue().size()+" mapped spawns"));locations.add(SidebarUi.button("Preview route",()->dispatch(current,renderEpoch,()->route.accept(entry.getValue().get(0).point()))));}panel.add(locations);
        }
        JPanel held=SidebarUi.section("All six guild hunters");for(HunterRumourCatalog.Master master:catalog.masters())
        {HunterRumourCatalog.Rumour rumour=catalog.rumour(current.masters.get(master.id));held.add(SidebarUi.label(master.name+" · "+master.tier.toLowerCase(Locale.ROOT)+(current.hunterLevel<master.level()?" · needs "+master.level():"")+"\n"+(rumour==null?"Not observed":rumour.name)));}held.add(SidebarUi.label("Back-to-back · "+(current.backToBack<0?"Unknown":current.backToBack==1?"Enabled":"Disabled")));panel.add(held);
        if(renderEpoch!=draftEpoch||!java.util.Objects.equals(current.profile,draftProfile))
        {draftProfile=current.profile;draftEpoch=renderEpoch;draftMaster=current.master==null?catalog.masters().get(0).id:current.master.id;draftRumour=current.rumour==null?catalog.rumours().get(0).id:current.rumour.id;}
        JPanel correction=SidebarUi.section("Manual reference");JComboBox<HunterRumourCatalog.Master>masterChoice=new JComboBox<>();masterChoice.setName("hunter-rumours.manual-master");catalog.masters().forEach(masterChoice::addItem);masterChoice.setSelectedItem(catalog.master(draftMaster));style(masterChoice);correction.add(masterChoice);
        masterChoice.addActionListener(event->{if(renderEpoch==generation&&current.profile.equals(reading().profile)){HunterRumourCatalog.Master selected=(HunterRumourCatalog.Master)masterChoice.getSelectedItem();if(selected!=null)draftMaster=selected.id;}});
        JComboBox<HunterRumourCatalog.Rumour>rumourChoice=new JComboBox<>();rumourChoice.setName("hunter-rumours.manual-rumour");catalog.rumours().forEach(rumourChoice::addItem);rumourChoice.setSelectedItem(catalog.rumour(draftRumour));style(rumourChoice);correction.add(rumourChoice);
        rumourChoice.addActionListener(event->{if(renderEpoch==generation&&current.profile.equals(reading().profile)){HunterRumourCatalog.Rumour selected=(HunterRumourCatalog.Rumour)rumourChoice.getSelectedItem();if(selected!=null)draftRumour=selected.id;}});
        correction.add(SidebarUi.button("Save manual reference",()->{HunterRumourCatalog.Master master=(HunterRumourCatalog.Master)masterChoice.getSelectedItem();HunterRumourCatalog.Rumour rumour=(HunterRumourCatalog.Rumour)rumourChoice.getSelectedItem();dispatch(current,renderEpoch,()->assign(master.id,rumour.id,true));}));
        correction.add(SidebarUi.button("Clear saved references",()->edit(current,renderEpoch,value->{value.masters.clear();value.current=0;verified=false;confirmedRecords.clear();})));panel.add(correction);finish();
    }
    private static JCheckBox check(String text,boolean selected){JCheckBox box=new JCheckBox(text,selected);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);return box;}
    private static void style(JComboBox<?>box){box.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,29));box.setFont(GuideText.FONT);box.setBackground(SidebarUi.CARD);box.setForeground(SidebarUi.TEXT);}
    private void finish(){panel.revalidate();panel.repaint();}
}
