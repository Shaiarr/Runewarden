/* SPDX-License-Identifier: BSD-2-Clause
 * Native mappings and timing windows adapted from pinned BSD Hub helpers.
 * Full notices: com/runewarden/licenses/minigame-field-events-BSD.txt.
 */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.Text;

/** Current native activity evidence plus explicitly estimated, witnessed event windows. */
final class MinigameFieldEvents
{
    static final String GROUP="runewardenminigameevents",KEY="preferencesV1";
    enum Mode { OFF, RADIAL, SECONDS, TICKS }
    static final class Preferences
    {
        int version=1;Mode doubleSpot=Mode.RADIAL,fire=Mode.RADIAL,wave=Mode.RADIAL,rift=Mode.RADIAL;
        boolean runeIcons=true,warnings=true;Preferences copy(){Preferences p=new Preferences();p.doubleSpot=doubleSpot;p.fire=fire;p.wave=wave;p.rift=rift;p.runeIcons=runeIcons;p.warnings=warnings;return p;}
    }
    static final class Rune
    {
        final String name;final int object,sprite,item,level;final boolean catalytic;
        Rune(String name,int object,int sprite,int item,int level,boolean catalytic){this.name=name;this.object=object;this.sprite=sprite;this.item=item;this.level=level;this.catalytic=catalytic;}
    }
    static final Rune[] RUNES={
        new Rune("Air",43701,4353,556,1,false),new Rune("Mind",43705,4354,558,2,true),new Rune("Water",43702,4355,555,5,false),new Rune("Earth",43703,4356,557,9,false),
        new Rune("Fire",43704,4357,554,14,false),new Rune("Body",43709,4358,559,20,true),new Rune("Cosmic",43710,4359,564,27,true),new Rune("Chaos",43706,4360,562,35,true),
        new Rune("Nature",43711,4361,561,44,true),new Rune("Law",43712,4362,563,54,true),new Rune("Death",43707,4363,560,65,true),new Rune("Blood",43708,4364,565,77,true)
    };
    static final Set<Integer> FIRE_IDS=Set.of(37582,41006,41007),TETHER_IDS=Set.of(41352,41353,41354,41355,40996,40997,41010,41011),BROKEN_IDS=Set.of(40996,40997,41010,41011);
    static final Color GOLD=new Color(224,190,113),GREEN=new Color(110,230,150),RED=new Color(255,110,100),BLUE=new Color(110,200,255);
    static final class Marker
    {
        final GameObject object;final NPC npc;final String label;final Color color;final Mode mode;final long start,duration;final int rune;
        Marker(GameObject object,NPC npc,String label,Color color,Mode mode,long start,long duration,int rune){this.object=object;this.npc=npc;this.label=label;this.color=color;this.mode=mode;this.start=start;this.duration=duration;this.rune=rune;}
        long remaining(long now){return start<0||now>=start+duration?-1:Math.min(duration,Math.max(0,start+duration-now));}
        String timer(long now){long left=remaining(now);return left<0?"?":left==0?"ending":mode==Mode.TICKS?"~"+((left+599)/600)+"t":String.format(java.util.Locale.ROOT,"~%.1fs",left/1000d);}
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final LongSupplier clock;
    private final Map<GameObject,Long> objects=new IdentityHashMap<>();private final Map<NPC,Long> doubles=new IdentityHashMap<>();
    private volatile List<Marker> markers=List.of();private volatile Preferences prefs=new Preferences();
    private String profile;private int world=-1;private MinigameActivityCatalog.Activity activity;
    private int previousElement=-1,previousCatalytic=-1;private long elementStart=-1,catalyticStart=-1,portalStart=-1,waveStart=-1;private boolean portalPreviouslyVisible,portalObserved;
    private JPanel panel;private boolean updating;private volatile boolean enabled=true;private final Map<String,JComboBox<Mode>> modes=new java.util.LinkedHashMap<>();private JCheckBox icons,warnings;
    MinigameFieldEvents(Client client,ConfigManager config,RunewardenDataStore store){this(client,config,store,System::currentTimeMillis);}
    MinigameFieldEvents(Client client,ConfigManager config,RunewardenDataStore store,LongSupplier clock){this.client=client;this.config=config;this.store=store;this.clock=clock;}
    void clear(){objects.clear();doubles.clear();markers=List.of();profile=null;world=-1;activity=null;previousElement=previousCatalytic=-1;elementStart=catalyticStart=portalStart=waveStart=-1;portalPreviouslyVisible=portalObserved=false;}
    private void scope(){String p=config.getRSProfileKey();int w=client.getWorld();if(!Objects.equals(p,profile)||world!=w){clear();profile=p;world=w;}}
    void reload(){prefs=store.read(GROUP,null,KEY,1,Preferences.class,MinigameFieldEvents::valid,Preferences::new);javax.swing.SwingUtilities.invokeLater(this::refresh);}
    Preferences preferences(){return prefs;}
    List<Marker> markers(){return enabled&&client.getGameState()==GameState.LOGGED_IN&&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&world==client.getWorld()?markers:List.of();}
    static Rune sprite(int sprite){for(Rune rune:RUNES)if(rune.sprite==sprite)return rune;return null;}
    static Rune object(int object){for(Rune rune:RUNES)if(rune.object==object)return rune;return null;}
    static boolean recognized(int id){return object(id)!=null||FIRE_IDS.contains(id)||TETHER_IDS.contains(id)||Set.of(43729,43732,43722,43723).contains(id);}
    void observe(GameObject object,boolean spawned)
    {
        if(object==null||!recognized(object.getId())||client.getGameState()!=GameState.LOGGED_IN||object.getWorldView()!=client.getTopLevelWorldView())return;scope();
        if(objects.size()>=192&&!objects.containsKey(object))return;boolean timed=FIRE_IDS.contains(object.getId())&&visible(437,2);
        objects.putIfAbsent(object,spawned&&timed?clock.getAsLong():-1L);
    }
    void remove(GameObject object){objects.remove(object);}
    void npcSpawned(NPC npc){if(npc==null||npc.getId()!=10569||npc.getWorldView()!=client.getTopLevelWorldView()||!visible(437,2))return;scope();if(doubles.size()<64)doubles.put(npc,clock.getAsLong());}
    void npcDespawned(NPC npc){doubles.remove(npc);}
    void message(ChatMessageType type,String raw)
    {
        if(type!=ChatMessageType.GAMEMESSAGE||!visible(437,2)||raw==null)return;scope();String text=Text.removeTags(raw).toLowerCase(java.util.Locale.ROOT);
        if(text.contains("a colossal wave closes in..."))waveStart=clock.getAsLong();else if(text.contains("as the wave washes over you")||text.contains("the wave slams into you"))waveStart=-1;
    }
    MinigameActivityCatalog.Snapshot enrich(MinigameActivityCatalog.Snapshot current)
    {
        scope();if(waveStart>=0&&clock.getAsLong()>=waveStart+7800)waveStart=-1;if(current.activity!=MinigameActivityCatalog.Activity.GOTR&&current.activity!=MinigameActivityCatalog.Activity.TEMPOROSS){clear();return current;}
        if(activity!=null&&activity!=current.activity){clear();scope();}activity=current.activity;
        List<Marker> next=new ArrayList<>();List<MinigameActivityCatalog.Fact> facts=new ArrayList<>(current.facts);Set<Integer> ids=new LinkedHashSet<>(current.highlightObjectIds),npcs=new LinkedHashSet<>(current.highlightNpcIds);List<MinigameActivityCatalog.Step> steps=current.steps;
        if(activity==MinigameActivityCatalog.Activity.GOTR)rift(next,facts,ids,npcs);
        else
        {
            tempoross(next,facts);if(waveStart>=0&&prefs.warnings&&client.getVarbitValue(11895)==0)
                steps=List.of(new MinigameActivityCatalog.Step("Tether for the incoming wave","Secure to an intact mast or totem. Repair a broken one if needed."));
        }
        markers=List.copyOf(next);MinigameActivityCatalog.Snapshot result=new MinigameActivityCatalog.Snapshot(current.activity,current.title,current.guideId,facts,steps,ids,current.referenceTiles,current.liveSupported,npcs).preserveGuideProgress(current);
        return steps!=current.steps&&!current.terminal?result.withGuideProgress("method:2","tempoross:tether",false,current.confirmedStepIds):result;
    }
    private void rift(List<Marker> next,List<MinigameActivityCatalog.Fact> facts,Set<Integer> ids,Set<Integer> npcs)
    {
        int elemental=spriteId(746,23),catalytic=spriteId(746,20);long now=clock.getAsLong();
        if(previousElement>=0&&elemental!=previousElement)elementStart=sprite(elemental)!=null?now:-1;if(previousCatalytic>=0&&catalytic!=previousCatalytic)catalyticStart=sprite(catalytic)!=null?now:-1;previousElement=elemental;previousCatalytic=catalytic;
        Rune e=sprite(elemental),c=sprite(catalytic);if(e!=null&&!e.catalytic)facts.add(new MinigameActivityCatalog.Fact("Elemental altar",runeFact(e,elementStart)));if(c!=null&&c.catalytic)facts.add(new MinigameActivityCatalog.Fact("Catalytic altar",runeFact(c,catalyticStart)));
        boolean portal=visible(746,28);if(portalObserved&&!portalPreviouslyVisible&&portal)portalStart=now;else if(!portal)portalStart=-1;portalObserved=true;portalPreviouslyVisible=portal;
        int cells=count(26882),essence=count(26879);boolean create=count(26886)>0&&count(1755)>0&&guardiansNeeded();if(cells==0)ids.add(43732);if(create){ids.add(43722);ids.add(43723);}if(count(26880,26881,26941,30887)>0)npcs.add(11403);
        for(GameObject object:objects.keySet())
        {
            Rune rune=object(object.getId());
            if(rune!=null&&((rune==e&&!rune.catalytic)||(rune==c&&rune.catalytic))&&active(object))
            {long start=rune.catalytic?catalyticStart:elementStart;Color color=client.getBoostedSkillLevel(Skill.RUNECRAFT)<rune.level?RED:rune.catalytic?BLUE:GOLD;next.add(new Marker(object,null,rune.name+" altar",color,prefs.rift,start,19800,prefs.runeIcons?rune.item:0));if(essence>0)ids.add(rune.object);}
            else if(object.getId()==43729&&portal)next.add(new Marker(object,null,"Huge remains",GOLD,prefs.rift,portalStart,25800,0));
        }
    }
    private String runeFact(Rune rune,long start){return rune.name+(client.getBoostedSkillLevel(Skill.RUNECRAFT)<rune.level?" · requires "+rune.level+" Runecraft":"")+(prefs.rift==Mode.OFF?"":start<0||nowExpired(start,19800)?" · remaining unknown":" · "+new Marker(null,null,"",GOLD,prefs.rift,start,19800,0).timer(clock.getAsLong()));}
    private boolean nowExpired(long start,long duration){return clock.getAsLong()>=start+duration;}
    private boolean guardiansNeeded(){Widget widget=client.getWidget(746,30);if(widget==null||widget.isHidden()||widget.getText()==null)return false;java.util.regex.Matcher m=java.util.regex.Pattern.compile("^\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*$").matcher(Text.removeTags(widget.getText()));return m.matches()&&Integer.parseInt(m.group(1))<Integer.parseInt(m.group(2));}
    private static boolean active(GameObject object){Renderable renderable=object.getRenderable();if(!(renderable instanceof DynamicObject))return false;Animation animation=((DynamicObject)renderable).getAnimation();return animation!=null&&animation.getId()==9363;}
    private void tempoross(List<Marker> next,List<MinigameActivityCatalog.Fact> facts)
    {
        WorldView view=client.getTopLevelWorldView();Set<NPC> present=java.util.Collections.newSetFromMap(new IdentityHashMap<>());if(view!=null&&view.npcs()!=null){int scanned=0;for(NPC npc:view.npcs()){if(++scanned>2048)break;if(npc!=null&&npc.getId()==10569&&present.size()<64){present.add(npc);doubles.putIfAbsent(npc,-1L);}}}doubles.keySet().retainAll(present);
        for(Map.Entry<NPC,Long> entry:doubles.entrySet())if(prefs.doubleSpot!=Mode.OFF)next.add(new Marker(null,entry.getKey(),"Double fish",BLUE,prefs.doubleSpot,entry.getValue(),24000,0));
        if(!doubles.isEmpty()&&prefs.warnings)facts.add(new MinigameActivityCatalog.Fact("Double spot","Available · estimated lifetime 20–27s"));
        long damage=(long)count(25564)*10L+(long)count(25565)*15L+(long)count(25566)*10L;facts.add(new MinigameActivityCatalog.Fact("Carried damage",Long.toString(damage)));
        boolean tethered=client.getVarbitValue(11895)>0;if(waveStart>=0){facts.add(new MinigameActivityCatalog.Fact("Wave safety",tethered?"Tethered":"UNTETHERED"));if(prefs.wave!=Mode.OFF)facts.add(new MinigameActivityCatalog.Fact("Wave impact",new Marker(null,null,"",GOLD,prefs.wave,waveStart,7800,0).timer(clock.getAsLong())));}
        for(Map.Entry<GameObject,Long> entry:objects.entrySet())
        {
            GameObject object=entry.getKey();int id=object.getId();
            if(FIRE_IDS.contains(id)&&prefs.fire!=Mode.OFF){long duration=id==37582?24000:id==41006?9600:1200;next.add(new Marker(object,null,id==37582?"Fire spread":id==41006?"Fire forming":"Fire spreading",RED,prefs.fire,entry.getValue(),duration,0));}
            else if(TETHER_IDS.contains(id)&&waveStart>=0&&prefs.wave!=Mode.OFF)next.add(new Marker(object,null,tethered?"TETHERED":BROKEN_IDS.contains(id)?"BROKEN · repair":"TETHER",tethered?GREEN:BROKEN_IDS.contains(id)?RED:GOLD,prefs.wave,waveStart,7800,0));
        }
    }
    private boolean visible(int group,int child){Widget widget=client.getWidget(group,child);return widget!=null&&!widget.isHidden();}
    private int spriteId(int group,int child){Widget widget=client.getWidget(group,child);return widget==null||widget.isHidden()?0:widget.getSpriteId();}
    private int count(int... ids){ItemContainer inv=client.getItemContainer(93);if(inv==null||inv.getItems()==null)return 0;long amount=0;for(Item item:inv.getItems())if(item!=null&&item.getQuantity()>0)for(int id:ids)if(item.getId()==id){amount+=item.getQuantity();break;}return (int)Math.min(Integer.MAX_VALUE,amount);}
    void render(Graphics2D graphics,boolean highlights,boolean labels,ItemManager items)
    {
        WorldView view=client.getTopLevelWorldView();long now=clock.getAsLong();for(Marker marker:markers())
        {
            if(marker.mode==Mode.OFF&&marker.rune==0)continue;if(view==null||marker.object!=null&&(marker.object.getWorldView()!=view||marker.object.getPlane()!=view.getPlane())||marker.npc!=null&&(marker.npc.getWorldView()!=view||marker.npc.getWorldLocation()==null||marker.npc.getWorldLocation().getPlane()!=view.getPlane()))continue;
            Polygon poly=marker.object!=null?marker.object.getCanvasTilePoly():marker.npc.getCanvasTilePoly();if(highlights&&poly!=null)OverlayUtil.renderPolygon(graphics,poly,marker.color);
            String text=marker.label+(marker.mode==Mode.OFF?"":" · "+marker.timer(now));Point location=marker.object!=null?marker.object.getCanvasTextLocation(graphics,text,80):marker.npc.getCanvasTextLocation(graphics,text,80);
            if(location==null)continue;if(labels)OverlayUtil.renderTextLocation(graphics,location,text,marker.color);
            if(labels&&marker.mode==Mode.RADIAL&&marker.start>=0&&marker.remaining(now)>0)RadialTimer.paint(graphics,location.getX(),location.getY()-24,14,RadialTimer.fraction(now,marker.start,marker.start+marker.duration),marker.color,"",true);
            if(labels&&marker.rune>0&&items!=null){BufferedImage image=items.getImage(marker.rune);if(image!=null)graphics.drawImage(image,location.getX()-image.getWidth()/2,location.getY()-56,null);}
        }
    }
    private static boolean valid(Preferences p){return p!=null&&p.version==1&&p.doubleSpot!=null&&p.fire!=null&&p.wave!=null&&p.rift!=null;}
    private void update(Consumer<Preferences> change){if(updating||!enabled)return;Preferences p=prefs.copy();change.accept(p);if(store.write(GROUP,null,KEY,1,p))prefs=p;refresh();}
    JPanel controls()
    {
        if(panel!=null)return panel;panel=SidebarUi.section("FIELD TIMERS");for(String[] option:new String[][]{{"doubleSpot","Double fish spot"},{"fire","Fire spread / forming"},{"wave","Wave and tether"},{"rift","GOTR altar / portal"}})
        {panel.add(SidebarUi.label(option[1]));JComboBox<Mode> box=new JComboBox<>(Mode.values());box.setName("minigameField"+option[0]);box.setMaximumSize(new Dimension(Integer.MAX_VALUE,30));box.setFont(GuideText.FONT);box.setBackground(SidebarUi.CARD);box.setForeground(SidebarUi.TEXT);box.addActionListener(e->update(p->mode(p,option[0],(Mode)box.getSelectedItem())));modes.put(option[0],box);panel.add(box);}
        icons=new JCheckBox("Native rune icons");icons.setName("minigameFieldIcons");icons.addActionListener(e->update(p->p.runeIcons=icons.isSelected()));warnings=new JCheckBox("Current event warnings");warnings.setName("minigameFieldWarnings");warnings.addActionListener(e->update(p->p.warnings=warnings.isSelected()));for(JCheckBox box:List.of(icons,warnings)){box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.setFont(GuideText.FONT);panel.add(box);}refresh();return panel;
    }
    private static Mode mode(Preferences p,String key,Mode value){switch(key){case "doubleSpot":if(value!=null)p.doubleSpot=value;return p.doubleSpot;case "fire":if(value!=null)p.fire=value;return p.fire;case "wave":if(value!=null)p.wave=value;return p.wave;default:if(value!=null)p.rift=value;return p.rift;}}
    void setEnabled(boolean value){enabled=value;javax.swing.SwingUtilities.invokeLater(this::refresh);}
    private void refresh(){if(panel==null)return;updating=true;try{for(Map.Entry<String,JComboBox<Mode>> entry:modes.entrySet()){entry.getValue().setSelectedItem(mode(prefs,entry.getKey(),null));entry.getValue().setEnabled(enabled);}icons.setEnabled(enabled);warnings.setEnabled(enabled);icons.setSelected(prefs.runeIcons);warnings.setSelected(prefs.warnings);}finally{updating=false;}}
}
