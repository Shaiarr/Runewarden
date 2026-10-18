/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;

/** Public visible player appearance only. No hiscores, remote requests, inventory or hidden stats. */
@Singleton
final class EquipmentInspectorTool
{
    static final String GROUP="runewardenequipmentinspector",KEY="preferencesV1";
    enum Slot
    {
        HEAD("Head",KitType.HEAD,1),CAPE("Cape",KitType.CAPE,3),NECK("Neck",KitType.AMULET,4),AMMO("Ammo",null,5),
        WEAPON("Weapon",KitType.WEAPON,6),BODY("Body",KitType.TORSO,7),SHIELD("Shield",KitType.SHIELD,8),
        LEGS("Legs",KitType.LEGS,10),HANDS("Hands",KitType.HANDS,12),FEET("Feet",KitType.BOOTS,13),RING("Ring",null,14);
        final String label;final KitType kit;final int cell;
        Slot(String label,KitType kit,int cell){this.label=label;this.kit=kit;this.cell=cell;}
    }
    static final class Piece
    {
        final int id,canonicalId;final String name;final long price;final AsyncBufferedImage icon;
        Piece(int id,int canonical,String name,long price,AsyncBufferedImage icon){this.id=id;canonicalId=canonical;this.name=name;this.price=price;this.icon=icon;}
    }
    static final class Snapshot
    {
        static final Snapshot EMPTY=new Snapshot(null,"",0,Collections.emptyMap(),Collections.emptyMap(),0,0,false,-1,0);
        final String profile,name;final int combat,tick,unpriced;final long epoch,value;
        final boolean transformed;final Map<KitType,Piece> pieces;final Map<KitType,Boolean> appearance;
        Snapshot(String profile,String name,int combat,Map<KitType,Piece> pieces,Map<KitType,Boolean> appearance,long value,int unpriced,boolean transformed,long epoch,int tick)
        {this.profile=profile;this.name=name;this.combat=combat;this.pieces=Collections.unmodifiableMap(new EnumMapCopy<>(pieces));this.appearance=Collections.unmodifiableMap(new EnumMapCopy<>(appearance));this.value=value;this.unpriced=unpriced;this.transformed=transformed;this.epoch=epoch;this.tick=tick;}
        boolean available(){return profile!=null&&!name.isEmpty();}
        Piece piece(Slot slot){return slot.kit==null?null:pieces.get(slot.kit);}
    }
    /** EnumMap constructor requires a key type even for an empty source. */
    private static final class EnumMapCopy<V> extends EnumMap<KitType,V>
    {EnumMapCopy(Map<KitType,V> source){super(KitType.class);putAll(source);}}
    static final class Preferences
    {
        int version=1;boolean live=true,hud,values=true;
        Preferences copy(){Preferences p=new Preferences();p.live=live;p.hud=hud;p.values=values;return p;}
    }
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final ItemManager items;
    private final RunewardenDataStore store;private final EquipmentInspectorOverlay overlay;
    private final Map<Integer,Piece> definitions=new LinkedHashMap<Integer,Piece>(128,.75f,true)
    {@Override protected boolean removeEldestEntry(Map.Entry<Integer,Piece> eldest){return size()>128;}};
    private volatile Snapshot reading=Snapshot.EMPTY;private volatile Preferences preferences=new Preferences();
    private volatile boolean started;private volatile long epoch;private volatile String profile;
    private Player selected;private WorldView selectedView;private int selectedId=-1;private int[] lastEquipment;
    private volatile IntConsumer openItem=ignored->{};private volatile Runnable openModule=()->{};
    private JPanel panel,body;private JCheckBox live,hud,values;private boolean refreshing;

    @Inject EquipmentInspectorTool(Client client,ClientThread thread,ConfigManager config,ItemManager items,Gson gson,RunewardenDataStore store,RunewardenConfig hudConfig)
    {this.client=client;this.thread=thread;this.config=config;this.items=items;this.store=store;overlay=new EquipmentInspectorOverlay(this,hudConfig);reloadPreferences();}
    void start(){started=true;clear();reloadPreferences();}
    void stop(){started=false;clear();}
    void clear(){++epoch;profile=null;selected=null;selectedView=null;selectedId=-1;lastEquipment=null;definitions.clear();reading=Snapshot.EMPTY;refresh();}
    void setOpenItemAction(IntConsumer action){openItem=action==null?ignored->{}:action;}
    void setOpenModuleAction(Runnable action){openModule=action==null?()->{}:action;}
    void setHudLocked(boolean locked){overlay.setLocked(locked);}
    Overlay overlay(){return overlay;}
    Preferences preferences(){return preferences;}
    Snapshot reading(){Snapshot current=reading;return started&&current.profile!=null&&current.epoch==epoch&&current.profile.equals(config.getRSProfileKey())?current:Snapshot.EMPTY;}

    /** Safe for a native context-menu callback or Swing; every Player/definition read is queued to the client thread. */
    void select(Player player)
    {
        long expected=epoch;String owner=config.getRSProfileKey();
        thread.invokeLater(()->{
            if(!started||expected!=epoch||owner==null||!owner.equals(config.getRSProfileKey())||!loggedIn())return;
            if(profile==null)profile=owner;
            if(!owner.equals(profile)||!loaded(player))return;
            selected=player;selectedView=player.getWorldView();selectedId=player.getId();lastEquipment=null;capture(true);
            Snapshot observed=reading;SwingUtilities.invokeLater(()->{if(reading()==observed)openModule.run();});
        });
    }
    void inspectSelf()
    {long expected=epoch;thread.invokeLater(()->{if(started&&expected==epoch&&loggedIn())select(client.getLocalPlayer());});}
    void onGameTick()
    {
        if(!started)return;
        if(!loggedIn()){if(profile!=null||selected!=null)clear();return;}
        String owner=config.getRSProfileKey();if(owner==null){clear();return;}
        if(!owner.equals(profile)){if(profile!=null)clear();profile=owner;}
        if(selected==null)return;
        if(selected.getWorldView()!=selectedView||selected.getId()!=selectedId||!loaded(selected)){selected=null;selectedView=null;lastEquipment=null;reading=Snapshot.EMPTY;refresh();return;}
        if(preferences.live)capture(false);
    }
    private boolean loggedIn(){return client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private boolean loaded(Player player)
    {
        if(player==null)return false;WorldView view=player.getWorldView();
        if(view==null||view!=client.getTopLevelWorldView()&&client.getWorldView(view.getId())!=view||view.players()==null)return false;
        return view.players().byIndex(player.getId())==player;
    }
    private void capture(boolean force)
    {
        PlayerComposition composition=selected==null?null:selected.getPlayerComposition();if(composition==null){reading=Snapshot.EMPTY;refresh();return;}
        int[] ids=new int[KitType.values().length+1];ids[ids.length-1]=composition.getTransformedNpcId();
        for(KitType kit:KitType.values())ids[kit.ordinal()]=composition.getEquipmentId(kit);
        String name=clean(selected.getName());int combat=Math.max(0,selected.getCombatLevel());
        if(!force&&Arrays.equals(ids,lastEquipment)&&reading.name.equals(name)&&reading.combat==combat)return;
        lastEquipment=ids;Map<KitType,Piece> worn=new EnumMap<>(KitType.class);Map<KitType,Boolean> appearance=new EnumMap<>(KitType.class);long total=0;int unpriced=0;
        boolean transformed=composition.getTransformedNpcId()>=0;
        if(!transformed)for(KitType kit:KitType.values())
        {
            int id=ids[kit.ordinal()];if(id<=0||id>1_000_000){appearance.put(kit,composition.getKitId(kit)>=0);continue;}
            Piece piece=definitions.get(id);
            if(piece==null)
            {
                ItemComposition definition=items.getItemComposition(id);if(definition==null)continue;
                int canonical=items.canonicalize(id);if(canonical<=0)canonical=id;
                piece=new Piece(id,canonical,clean(definition.getMembersName()),Math.max(0,items.getItemPrice(canonical)),items.getImage(id));definitions.put(id,piece);
            }
            worn.put(kit,piece);
        }
        for(Slot slot:Slot.values())if(slot.kit!=null&&worn.containsKey(slot.kit))
        {Piece item=worn.get(slot.kit);if(item.price>0)total=Long.MAX_VALUE-total<item.price?Long.MAX_VALUE:total+item.price;else unpriced++;}
        reading=new Snapshot(profile,name,combat,worn,appearance,total,unpriced,transformed,epoch,client.getTickCount());refresh();
    }
    void refreshSelected()
    {long expected=epoch;String owner=profile;thread.invokeLater(()->{if(started&&expected==epoch&&Objects.equals(owner,config.getRSProfileKey())&&loggedIn()&&loaded(selected)){definitions.clear();capture(true);}});}
    void onConfigChanged(ConfigChanged event){if(event!=null&&GROUP.equals(event.getGroup()))reloadPreferences();}
    private void reloadPreferences()
    {preferences=store.read(GROUP,null,KEY,1,Preferences.class,p->p!=null&&p.version==1,Preferences::new);refresh();}
    private void preference(java.util.function.Consumer<Preferences> change)
    {Preferences next=preferences.copy();change.accept(next);if(store.write(GROUP,null,KEY,1,next)){preferences=next;refresh();}}

    JPanel createPanel()
    {
        if(panel!=null)return panel;panel=SidebarUi.column();panel.setName("runewarden.equipment-inspector");panel.add(SidebarUi.heading("EQUIPMENT INSPECTOR"));
        panel.add(SidebarUi.row(SidebarUi.button("Inspect yourself",this::inspectSelf),SidebarUi.button("Refresh",this::refreshSelected)));
        body=SidebarUi.column();panel.add(body);JPanel options=SidebarUi.column();
        live=choice(options,"Update visible gear","equipmentInspectorLive",p->p.live=live.isSelected());
        hud=choice(options,"Show equipment HUD","equipmentInspectorHud",p->p.hud=hud.isSelected());
        values=choice(options,"Show visible GE value","equipmentInspectorValues",p->p.values=values.isSelected());panel.add(SidebarUi.disclosure("Inspector options",options));render();return panel;
    }
    private JCheckBox choice(JPanel parent,String title,String name,java.util.function.Consumer<Preferences> change)
    {JCheckBox control=new JCheckBox(title);control.setName(name);control.setOpaque(false);control.setForeground(SidebarUi.TEXT);control.setFont(GuideText.FONT);control.addActionListener(event->{if(!refreshing)preference(change);});parent.add(control);return control;}
    private void refresh(){if(panel==null)return;long expected=epoch;SwingUtilities.invokeLater(()->{if(expected==epoch&&panel!=null)render();});}
    private void render()
    {
        if(body==null)return;refreshing=true;
        try
        {
            if(live!=null){live.setSelected(preferences.live);hud.setSelected(preferences.hud);values.setSelected(preferences.values);}
            body.removeAll();Snapshot current=reading();
            if(!current.available()){body.add(SidebarUi.label("No player selected"));return;}
            body.add(SidebarUi.heading(current.name));body.add(SidebarUi.label("Combat "+current.combat));
            if(current.transformed){body.add(SidebarUi.label("Equipment unavailable while transformed"));return;}
            JPanel grid=new JPanel(new GridLayout(5,3,4,4));grid.setOpaque(false);grid.setName("equipmentInspectorGrid");
            for(int cell=0;cell<15;cell++)
            {
                Slot slot=null;for(Slot candidate:Slot.values())if(candidate.cell==cell)slot=candidate;
                if(slot==null){JPanel empty=new JPanel();empty.setOpaque(false);grid.add(empty);continue;}
                Piece item=current.piece(slot);JButton button=new JButton();button.setName("equipmentInspector."+slot.name());button.setPreferredSize(new Dimension(48,44));button.setMargin(new java.awt.Insets(1,1,1,1));button.setBackground(SidebarUi.CARD);button.setForeground(SidebarUi.MUTED);button.setBorder(BorderFactory.createLineBorder(new Color(84,74,51)));
                String detail=slot.kit==null?"Not visible":item==null?Boolean.TRUE.equals(current.appearance.get(slot.kit))?"Appearance":"Empty":item.name;
                button.setToolTipText(slot.label+" · "+detail);button.getAccessibleContext().setAccessibleName(slot.label+" · "+detail);
                if(item!=null){if(item.icon!=null)item.icon.addTo(button);else button.setText(slot.label);int id=item.id;button.addActionListener(event->openItem(current,id));}
                else{button.setText(slot.label);button.setEnabled(false);}grid.add(button);
            }
            body.add(grid);if(preferences.values){body.add(SidebarUi.label("Visible GE value · "+money(current.value)+" gp"));if(current.unpriced>0)body.add(SidebarUi.label(current.unpriced+" visible pieces unpriced"));}
            JPanel details=SidebarUi.column();for(KitType kit:KitType.values())
            {Piece item=current.pieces.get(kit);if(item==null)continue;details.add(SidebarUi.wrappedButton(label(kit)+" · "+item.name,()->openItem(current,item.id)));}
            body.add(SidebarUi.disclosure("Visible item list",details));
        }
        finally{refreshing=false;body.revalidate();body.repaint();panel.revalidate();panel.repaint();}
    }
    private void openItem(Snapshot owner,int id)
    {if(reading()!=owner)return;long expected=epoch;thread.invokeLater(()->{if(started&&epoch==expected&&reading()==owner&&loaded(selected))SwingUtilities.invokeLater(()->{if(reading()==owner)openItem.accept(id);});});}
    static String label(KitType kit){for(Slot slot:Slot.values())if(slot.kit==kit)return slot.label;return kit.name().substring(0,1)+kit.name().substring(1).toLowerCase(java.util.Locale.ROOT)+" appearance";}
    static String money(long amount){return String.format(java.util.Locale.ROOT,"%,d",amount);}
    private static String clean(String value){String text=value==null?"":Text.removeTags(value).replace('\n',' ').replace('\r',' ').trim();return text.substring(0,Math.min(100,text.length()));}
}
