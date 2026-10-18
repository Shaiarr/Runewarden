/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;

/** Carried/banked container readings from public native counts and confirmed barrel observations. */
@Singleton
public final class ContainerHudTool
{
    private static final String GROUP="runewardencontainerhud";
    static final class Preferences
    {
        int version=1;boolean enabled=true,fish=true,planks=true,breakdown=true,bank=true;
        boolean valid(){return version==1;}
    }
    enum Location { INVENTORY("Inventory"),EQUIPPED("Equipped"),BANK("Bank"),UNKNOWN("Not carried");
        final String title;Location(String title){this.title=title;}}
    static final class Content
    {final String name;final int amount;final BufferedImage image;Content(String name,int amount,BufferedImage image){this.name=name;this.amount=amount;this.image=image;}}
    static final class Container
    {
        final String name;final int count;final Location location;final BufferedImage image;final List<Content> contents;
        Container(String name,int count,Location location,BufferedImage image,List<Content> contents)
        {this.name=name;this.count=count;this.location=location;this.image=image;this.contents=Collections.unmodifiableList(new ArrayList<>(contents));}
        boolean known(){return count>=0;}
    }
    static final class Snapshot
    {
        final String profile;final List<Container> containers;
        Snapshot(String profile,List<Container> containers){this.profile=profile;this.containers=Collections.unmodifiableList(new ArrayList<>(containers));}
        static Snapshot empty(){return new Snapshot(null,Collections.emptyList());}
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final ItemManager items;
    private final ContainerHudOverlay overlay;private final FishBarrelReading fish=new FishBarrelReading();
    private final Map<Integer,BufferedImage> images=new LinkedHashMap<>();private volatile Preferences preferences=new Preferences();
    private volatile Snapshot snapshot=Snapshot.empty();private boolean started;private String profile;private int cookingXp;
    @Inject public ContainerHudTool(Client client,ConfigManager config,RunewardenDataStore store,RunewardenConfig hud,ItemManager items)
    {this.client=client;this.config=config;this.store=store;this.items=items;overlay=new ContainerHudOverlay(this,hud);}
    public void start(){preferences=store.read(GROUP,null,"preferencesV1",1,Preferences.class,Preferences::valid,Preferences::new);started=true;clear();}
    public void stop(){started=false;clear();}
    public void clear(){profile=null;fish.clear();images.clear();cookingXp=0;snapshot=Snapshot.empty();}
    public Overlay overlay(){return overlay;}
    public void setHudLocked(boolean locked){overlay.setHudLocked(locked);}
    boolean enabled(){return started&&preferences.enabled;}
    Preferences preferences(){return preferences;}
    Snapshot snapshot(){Snapshot value=snapshot;return enabled()&&value.profile!=null&&value.profile.equals(config.getRSProfileKey())?value:Snapshot.empty();}
    private boolean available(){return enabled()&&client.getGameState()==GameState.LOGGED_IN&&client.getLocalPlayer()!=null;}
    private boolean bankOpen(){Widget bank=client.getWidget(InterfaceID.Bankmain.ITEMS_CONTAINER);return bank!=null&&!bank.isHidden();}
    private boolean bind()
    {
        if(!available()){if(profile!=null)clear();return false;}
        String current=config.getRSProfileKey();if(!Objects.equals(profile,current)){clear();profile=current;cookingXp=client.getSkillExperience(Skill.COOKING);}
        return profile!=null;
    }
    public void onGameTick()
    {
        if(!bind())return;refresh();
        if(fish.awaitingCheck(client.getTickCount()))
        {Widget response=client.getWidget(ContainerHudRules.CHECK_GROUP,ContainerHudRules.CHECK_COMPONENT);if(response!=null&&!response.isHidden())fish.check(response.getText(),client.getTickCount());}
        fish.tick(client.getSkillExperience(Skill.COOKING));cookingXp=client.getSkillExperience(Skill.COOKING);publish();
    }
    public void onItemContainerChanged(ItemContainerChanged event)
    {if(event==null||!bind())return;int id=event.getContainerId();if(id==InventoryID.INVENTORY.getId()||id==InventoryID.EQUIPMENT.getId()||id==InventoryID.BANK.getId()){refresh();publish();}}
    public void onVarbitChanged(VarbitChanged event)
    {
        if(event==null)return;int id=event.getVarbitId();
        for(ContainerHudRules.Plank plank:ContainerHudRules.Plank.values())if(plank.varbit==id){if(bind())publish();return;}
    }
    public void onWidgetLoaded(WidgetLoaded event){if(event!=null&&bind())fish.widgetLoaded(event.getGroupId(),client.getTickCount());}
    public void onStatChanged(StatChanged event)
    {if(event!=null&&event.getSkill()==Skill.COOKING&&bind()){if(event.getXp()>cookingXp)fish.cookingChanged();cookingXp=event.getXp();}}
    public void onChatMessage(ChatMessage event)
    {
        if(event==null||!bind()||event.getType()!=ChatMessageType.GAMEMESSAGE&&event.getType()!=ChatMessageType.SPAM)return;
        refresh();fish.chat(event.getMessage(),event.getType()==ChatMessageType.SPAM,bankOpen());publish();
    }
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if(event==null||!bind())return;int id=event.getItemId();
        if(!ContainerHudRules.BARRELS.contains(id)&&event.getWidget()!=null)id=event.getWidget().getItemId();
        if(ContainerHudRules.BARRELS.contains(id)){refresh();fish.menu(event.getMenuOption(),id,client.getTickCount());publish();}
    }
    private Map<Integer,Long> inventory(InventoryID id)
    {
        ItemContainer container=client.getItemContainer(id);if(container==null)return Collections.emptyMap();Map<Integer,Long> result=new LinkedHashMap<>();
        for(Item item:container.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)result.merge(item.getId(),(long)item.getQuantity(),Long::sum);return result;
    }
    private void refresh()
    {
        Map<Integer,Long> inventory=inventory(InventoryID.INVENTORY),worn=inventory(InventoryID.EQUIPMENT);
        boolean present=inventory.keySet().stream().anyMatch(ContainerHudRules.BARRELS::contains)||worn.keySet().stream().anyMatch(ContainerHudRules.BARRELS::contains);
        boolean open=inventory.keySet().stream().anyMatch(ContainerHudRules.OPEN_BARRELS::contains)||worn.keySet().stream().anyMatch(ContainerHudRules.OPEN_BARRELS::contains);
        fish.observe(present,open,inventory,client.getTickCount(),client.getSkillExperience(Skill.COOKING));
    }
    private BufferedImage image(int id)
    {if(!images.containsKey(id))images.put(id,items==null?null:items.getImage(id));return images.get(id);}
    private Location location(java.util.Set<Integer> ids,Map<Integer,Long> inventory,Map<Integer,Long> worn,Map<Integer,Long> bank)
    {
        if(inventory.keySet().stream().anyMatch(ids::contains))return Location.INVENTORY;
        if(worn.keySet().stream().anyMatch(ids::contains))return Location.EQUIPPED;
        return bankOpen()&&bank.keySet().stream().anyMatch(ids::contains)?Location.BANK:Location.UNKNOWN;
    }
    private void publish()
    {
        Map<Integer,Long> inventory=inventory(InventoryID.INVENTORY),worn=inventory(InventoryID.EQUIPMENT),bank=bankOpen()?inventory(InventoryID.BANK):Collections.emptyMap();
        List<Container> rows=new ArrayList<>();Location barrel=location(ContainerHudRules.BARRELS,inventory,worn,bank);
        if(preferences.fish&&barrel!=Location.UNKNOWN&&(barrel!=Location.BANK||preferences.bank))
        {
            int icon=ContainerHudRules.BARRELS.stream().filter(id->inventory.containsKey(id)||worn.containsKey(id)||bank.containsKey(id)).findFirst().orElse(ItemID.FISH_BARREL_CLOSED);
            rows.add(new Container("Fish barrel",barrel==Location.BANK?-1:fish.count(),barrel,image(icon),Collections.emptyList()));
        }
        Location sack=location(Collections.singleton(ItemID.PLANK_SACK),inventory,worn,bank);
        if(preferences.planks&&sack!=Location.UNKNOWN&&(sack!=Location.BANK||preferences.bank))
        {
            int[] counts=new int[ContainerHudRules.Plank.values().length];List<Content> contents=new ArrayList<>();int index=0;
            for(ContainerHudRules.Plank plank:ContainerHudRules.Plank.values())
            {int amount=client.getVarbitValue(plank.varbit);counts[index++]=amount;if(amount>0&&amount<=ContainerHudRules.CAPACITY)contents.add(new Content(plank.title,amount,image(plank.item)));}
            int total=ContainerHudRules.totalPlanks(counts);if(total<0)contents.clear();
            rows.add(new Container("Plank sack",total,sack,image(ItemID.PLANK_SACK),preferences.breakdown?contents:Collections.emptyList()));
        }
        snapshot=new Snapshot(profile,rows);
    }
    public JPanel createPanel()
    {
        JPanel root=SidebarUi.column();root.add(SidebarUi.heading("CONTAINER HUD"));
        option(root,"Show container HUD",preferences.enabled,value->preferences.enabled=value);
        option(root,"Fish barrel",preferences.fish,value->preferences.fish=value);option(root,"Plank sack",preferences.planks,value->preferences.planks=value);
        option(root,"Plank type breakdown",preferences.breakdown,value->preferences.breakdown=value);option(root,"Show banked containers",preferences.bank,value->preferences.bank=value);
        return root;
    }
    private void option(JPanel root,String title,boolean selected,java.util.function.Consumer<Boolean> change)
    {JCheckBox box=new JCheckBox(title,selected);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);box.addActionListener(event->{change.accept(box.isSelected());preferences=preferences;store.write(GROUP,null,"preferencesV1",1,preferences);if(!preferences.enabled)snapshot=Snapshot.empty();});root.add(box);}
}
