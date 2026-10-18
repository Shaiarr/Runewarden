/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Account-scoped POH declarations and observations; native Shortest Path still solves the route. */
@Singleton
final class PohTravelCapabilities
{
    static final String KEY="pohTravelV1", LEASE_KEY="pohTravelAppliedV1", NATIVE="shortestpath";
    static final String[] CONFIG_KEYS={"usePoh","pohNexusPortals","pohJewelleryBoxTier","pohMountedItems","usePohFairyRing","usePohSpiritTree","usePohObelisk","useTeleportationPortalsPoh","usePohMountedItems"};
    static final Set<String> MOUNTED=Set.of("GLORY","XERICS_TALISMAN","DIGSITE_PENDANT","MYTHICAL_CAPE");
    private static final int[] POUCH={VarbitID.RUNE_POUCH_TYPE_1,VarbitID.RUNE_POUCH_TYPE_2,VarbitID.RUNE_POUCH_TYPE_3,VarbitID.RUNE_POUCH_TYPE_4,VarbitID.RUNE_POUCH_TYPE_5,VarbitID.RUNE_POUCH_TYPE_6,VarbitID.RUNE_POUCH_QUANTITY_1,VarbitID.RUNE_POUCH_QUANTITY_2,VarbitID.RUNE_POUCH_QUANTITY_3,VarbitID.RUNE_POUCH_QUANTITY_4,VarbitID.RUNE_POUCH_QUANTITY_5,VarbitID.RUNE_POUCH_QUANTITY_6};
    static final class Portal {String key,label;List<String> names;List<Integer> objectIds;}
    private static final class Catalog {int version;String source;List<Portal> portals;Set<Integer> revisionItemIds;}
    static final class Saved
    {
        int version=1;Set<String> portals=new TreeSet<>(),observed=new TreeSet<>(),mounted=new TreeSet<>();
        boolean confirmed,fairyRing,spiritTree,obelisk;String jewellery="NONE",nativeFingerprint;
    }
    static final class Applied {int version=1;String profile,fingerprint;}
    static final class Snapshot
    {
        final String profile;final Set<String> portals,observed,suggestions,mounted;final String jewellery;
        final boolean confirmed,usable,ownHouse,constructionCape,fairyRing,spiritTree,obelisk;
        Snapshot(String profile,Saved saved,Set<String> suggestions,boolean usable,boolean ownHouse,boolean cape)
        {this.profile=profile;portals=Set.copyOf(saved.portals);observed=Set.copyOf(saved.observed);this.suggestions=Set.copyOf(suggestions);mounted=Set.copyOf(saved.mounted);jewellery=saved.jewellery;confirmed=saved.confirmed;this.usable=usable;this.ownHouse=ownHouse;constructionCape=cape;fairyRing=saved.fairyRing;spiritTree=saved.spiritTree;obelisk=saved.obelisk;}
    }
    private final Client client;private final ClientThread clientThread;private final ConfigManager config;private final RunewardenDataStore data;private final Gson gson;
    private final Set<Integer> revisionItemIds;private final List<Portal> portals;private final Map<String,Portal> byKey=new LinkedHashMap<>();private final Map<Integer,String> byObject=new HashMap<>();private final Map<String,String> byName=new HashMap<>();
    private String profile;private Saved saved=new Saved();private Applied applied=new Applied();private volatile boolean running;private volatile long epoch,revision;private int tick,lastFingerprint;private volatile Snapshot snapshot;
    private JPanel panel;private GuideText status;private JButton scan;private final Map<String,JCheckBox> portalChecks=new LinkedHashMap<>(),mountedChecks=new LinkedHashMap<>();private JCheckBox fairy,spirit,obelisk;private JComboBox<String> jewellery;private String panelProfile;private long panelEpoch;

    @Inject PohTravelCapabilities(Client client,ClientThread clientThread,ConfigManager config,RunewardenDataStore data,Gson gson)
    {
        this.client=client;this.clientThread=clientThread;this.config=config;this.data=data;this.gson=gson;
        try(InputStreamReader reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/com/runewarden/poh-travel-portals.json")),StandardCharsets.UTF_8))
        {Catalog catalog=gson.fromJson(reader,Catalog.class);portals=List.copyOf(catalog.portals);revisionItemIds=Set.copyOf(catalog.revisionItemIds);}
        catch(java.io.IOException failure){throw new IllegalStateException("POH travel catalog",failure);}
        for(Portal portal:portals){byKey.put(portal.key,portal);byName.put(normalize(portal.key.replace('_',' ')),portal.key);byName.put(normalize(portal.label),portal.key);for(String name:portal.names)byName.put(normalize(name),portal.key);for(int id:portal.objectIds)byObject.put(id,portal.key);}
        alias("east ardougne","ARDOUGNE");alias("kourend castle","KOUREND");alias("great kourend","KOUREND");alias("ourania cave","OURANIA");alias("forgotten cemetery","CEMETERY");alias("frozen waste plateau","GHORROCK");alias("demonic ruins","ANNAKARL");alias("graveyard of shadows","CARRALLANGER");alias("canifis","KHARYRLL");alias("exam centre","SENNTISTEN");alias("exam center","SENNTISTEN");alias("digsite","SENNTISTEN");alias("edgeville dungeon","PADDEWWA");alias("ice mountain","LASSAR");alias("crazy archaeologist","DAREEYAK");alias("ruins west","DAREEYAK");alias("ape atoll","MARIM");alias("fenken castle","FENKENSTRAINS_CASTLE");
        snapshot=new Snapshot(null,saved,Set.of(),false,false,false);
    }
    void start(){running=true;epoch++;profile=null;tick=0;long ticket=epoch;clientThread.invokeLater(()->{if(running&&epoch==ticket)onGameTick();});}
    void stop(){running=false;epoch++;profile=null;saved=new Saved();snapshot=new Snapshot(null,saved,Set.of(),false,false,false);revision++;refreshPanel();}
    Snapshot snapshot(){return snapshot;}
    long revision(){return revision;}
    List<Portal> portals(){return portals;}

    /** Client-thread only; no foreign settings are mutated by routing. */
    Map<String,Object> routeOptions()
    {
        if(!running||client.getGameState()!=GameState.LOGGED_IN||!Objects.equals(profile,config.getRSProfileKey()))return Map.of("useTeleportationItems","Inventory","usePoh",false);
        return Map.of("useTeleportationItems","Inventory","usePoh",usable(nativeFingerprint()));
    }
    void onGameTick()
    {
        if(!running)return;
        String next=client.getGameState()==GameState.LOGGED_IN?config.getRSProfileKey():null;
        boolean changed=!Objects.equals(profile,next);
        if(changed)
        {
            profile=next;saved=profile==null?new Saved():data.read(RunewardenConfig.GROUP,profile,KEY,1,Saved.class,this::valid,Saved::new);
            applied=data.read(RunewardenConfig.GROUP,null,LEASE_KEY,1,Applied.class,a->a.version==1,Applied::new);
            if(profile!=null&&!saved.confirmed&&saved.portals.isEmpty()&&usable(nativeFingerprint()))seedDeclared();epoch++;revision++;
        }
        boolean own=ownHouse();boolean learned=false;
        if(profile!=null&&own&&++tick%10==0)learned=scanOwnHouse();
        String nativeState=nativeFingerprint();Map<Integer,Integer> held=heldItems();
        Map<Integer,Integer> relevant=new TreeMap<>(held);relevant.keySet().retainAll(revisionItemIds);
        int hash=Objects.hash(profile,relevant,nativeState,saved.portals,saved.mounted,saved.jewellery,saved.confirmed,saved.fairyRing,saved.spiritTree,saved.obelisk,client.getRealSkillLevel(Skill.CONSTRUCTION),client.getRealSkillLevel(Skill.MAGIC));
        for(int varbit:POUCH)hash=31*hash+client.getVarbitValue(varbit);
        hash=31*hash+client.getVarbitValue(4744);hash=31*hash+client.getVarbitValue(4070);
        if(hash!=lastFingerprint){lastFingerprint=hash;revision++;}
        boolean cape=client.getRealSkillLevel(Skill.CONSTRUCTION)>=99&&(held.containsKey(9789)||held.containsKey(9790));
        Snapshot before=snapshot;snapshot=new Snapshot(profile,saved,suggestions(),usable(nativeState),own,cape);
        if(changed||learned||before.usable!=snapshot.usable||before.ownHouse!=own||before.constructionCape!=cape)refreshPanel();
    }
    private boolean usable(String nativeState)
    {
        if(profile==null||data.blocked(RunewardenConfig.GROUP,null,LEASE_KEY)||!Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePoh")))return false;
        // Our explicit configuration is global upstream: never borrow another character's unchanged declaration.
        if(applied.profile!=null&&Objects.equals(applied.fingerprint,nativeState))return Objects.equals(profile,applied.profile);
        if(saved.confirmed&&Objects.equals(saved.nativeFingerprint,nativeState))return true;
        // Existing user-managed native declarations remain authoritative; no automatic rewrite.
        return applied.fingerprint==null||!Objects.equals(applied.fingerprint,nativeState);
    }
    private boolean ownHouse()
    {
        if(client.getGameState()!=GameState.LOGGED_IN||client.getVarbitValue(VarbitID.POH_BUILDING_MODE)!=1||client.getLocalPlayer()==null)return false;
        WorldView view=client.getTopLevelWorldView();if(view==null||!view.isInstance())return false;
        net.runelite.api.coords.LocalPoint local=client.getLocalPlayer().getLocalLocation();if(local==null||client.getWorldView(local.getWorldView())==null)return false;
        WorldPoint point=WorldPoint.fromLocalInstance(client,local);
        return point!=null&&point.getX()>=1856&&point.getX()<=2047&&point.getY()>=7040&&point.getY()<=7111;
    }
    /** Ownership requires building mode in the actual POH template, never merely a visited-house cache. */
    boolean scanOwnHouse()
    {
        if(profile==null||!Objects.equals(profile,config.getRSProfileKey())||!ownHouse())return false;
        Set<String> found=new TreeSet<>();WorldView view=client.getTopLevelWorldView();
        if(view.getScene()!=null&&view.getScene().getTiles()!=null)
        {
            Tile[][][] tiles=view.getScene().getTiles();int plane=view.getPlane();
            if(plane>=0&&plane<tiles.length&&tiles[plane]!=null)
            {int visited=0;Set<GameObject> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                for(Tile[] row:tiles[plane]){if(row==null)continue;for(Tile tile:row){if(++visited>16384)break;if(tile==null||tile.getGameObjects()==null)continue;for(GameObject object:tile.getGameObjects())if(object!=null&&seen.add(object)){String key=byObject.get(object.getId());if(key!=null)found.add(key);}}if(visited>16384)break;}
            }
        }
        Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());int[] budget={2048};
        for(int id:new int[]{InterfaceID.TelenexusTeleport.TEXT1,InterfaceID.TelenexusTeleport.EXTRAS,InterfaceID.Telenexus.SLOTTED_LIST,InterfaceID.Telenexus.ROWS2})collectWidgets(client.getWidget(id),found,seen,budget,0);
        boolean changed=saved.observed.addAll(found);if(!saved.confirmed)changed|=saved.portals.addAll(found);
        if(changed){data.write(RunewardenConfig.GROUP,profile,KEY,1,saved);revision++;}return changed;
    }
    private void collectWidgets(Widget widget,Set<String> found,Set<Widget> seen,int[] budget,int depth)
    {
        if(widget==null||widget.isHidden()||depth>8||budget[0]--<=0||!seen.add(widget))return;
        String key=keyForName(widget.getText());if(key!=null)found.add(key);
        for(Widget[] children:new Widget[][]{widget.getDynamicChildren(),widget.getStaticChildren(),widget.getNestedChildren()})if(children!=null)for(Widget child:children)collectWidgets(child,found,seen,budget,depth+1);
    }
    private Map<Integer,Integer> heldItems()
    {
        Map<Integer,Integer> result=new TreeMap<>();for(InventoryID id:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT})
        {ItemContainer container=client.getItemContainer(id);if(container!=null&&container.getItems()!=null)for(Item item:container.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)result.merge(item.getId(),item.getQuantity(),Integer::sum);}return result;
    }
    private Set<String> suggestions()
    {
        Set<String> result=new TreeSet<>();if(profile==null)return result;
        String raw=config.getConfiguration(NATIVE,profile,"portalNexusKeybinds");if(raw!=null&&raw.length()<12000&&raw.startsWith("2|"))for(String row:raw.substring(2).split("\\|")){int split=row.indexOf('=');if(split>0&&row.substring(split+1).matches("[1-9A-Z]|F(?:[1-9]|1[0-2])")){String key=keyForName(row.substring(0,split));if(key!=null)result.add(key);}}
        return result;
    }
    private void seedDeclared()
    {
        saved.portals.addAll(nativePortals());saved.mounted.addAll(nativeSet("pohMountedItems",MOUNTED));
        if(config.getConfiguration(NATIVE,"pohMountedItems")==null&&!"false".equals(config.getConfiguration(NATIVE,"usePohMountedItems")))saved.mounted.addAll(MOUNTED);
        String tier=config.getConfiguration(NATIVE,"pohJewelleryBoxTier");saved.jewellery=tier!=null&&Set.of("NONE","BASIC","FANCY","ORNATE").contains(tier)?tier:"ORNATE";
        saved.fairyRing=Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohFairyRing"));saved.spiritTree=Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohSpiritTree"));saved.obelisk=Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohObelisk"));
    }
    private String nativeFingerprint(){StringBuilder value=new StringBuilder();for(String key:CONFIG_KEYS)value.append(key).append('=').append(config.getConfiguration(NATIVE,key)).append(';');return value.toString();}
    private void alias(String name,String key){byName.put(normalize(name),key);}
    String keyForName(String raw){if(raw==null||raw.length()>400)return null;String name=raw.replaceAll("<[^>]*>","").trim().replaceFirst("(?i)^([1-9A-Z]|F(?:[1-9]|1[0-2]))\\s*:\\s*","");if(normalize(name).startsWith("respawn"))return "RESPAWN";return byName.get(normalize(name));}
    private static String normalize(String raw){return raw.toLowerCase(Locale.ROOT).replace("portal", " ").replace("teleport to"," ").replaceAll("\\([^)]*\\)"," ").replaceAll("[^a-z0-9]+"," ").trim().replaceAll(" +"," ");}
    private boolean valid(Saved value){return value!=null&&value.version==1&&value.portals!=null&&value.observed!=null&&value.mounted!=null&&value.portals.size()<=40&&value.observed.size()<=40&&byKey.keySet().containsAll(value.portals)&&byKey.keySet().containsAll(value.observed)&&MOUNTED.containsAll(value.mounted)&&Set.of("NONE","BASIC","FANCY","ORNATE").contains(value.jewellery)&&(value.nativeFingerprint==null||value.nativeFingerprint.length()<20000);}

    /** Explicit user action. String enum names serialize through the public ConfigManager Set contract. */
    boolean apply(String expectedProfile,Saved selection)
    {
        if(!running||profile==null||!Objects.equals(profile,expectedProfile)||!Objects.equals(profile,config.getRSProfileKey())||client.getGameState()!=GameState.LOGGED_IN||!valid(selection)||data.blocked(RunewardenConfig.GROUP,profile,KEY)||data.blocked(RunewardenConfig.GROUP,null,LEASE_KEY))return false;
        selection.observed=new TreeSet<>(saved.observed);selection.confirmed=true;
        // Write our protected record first; an incompatible saved version must never mutate upstream settings.
        if(!data.write(RunewardenConfig.GROUP,profile,KEY,1,selection))return false;
        config.setConfiguration(NATIVE,"pohNexusPortals",new TreeSet<>(selection.portals));
        config.setConfiguration(NATIVE,"pohMountedItems",new TreeSet<>(selection.mounted));
        config.setConfiguration(NATIVE,"pohJewelleryBoxTier",selection.jewellery);
        config.setConfiguration(NATIVE,"usePohFairyRing",selection.fairyRing);config.setConfiguration(NATIVE,"usePohSpiritTree",selection.spiritTree);config.setConfiguration(NATIVE,"usePohObelisk",selection.obelisk);
        config.setConfiguration(NATIVE,"usePoh",true);
        selection.nativeFingerprint=nativeFingerprint();saved=selection;data.write(RunewardenConfig.GROUP,profile,KEY,1,saved);
        applied=new Applied();applied.profile=profile;applied.fingerprint=selection.nativeFingerprint;data.write(RunewardenConfig.GROUP,null,LEASE_KEY,1,applied);revision++;onGameTick();refreshPanel();return true;
    }
    JPanel createPanel()
    {
        if(panel!=null)return panel;
        JPanel body=SidebarUi.column();status=SidebarUi.label("");body.add(status);
        scan=SidebarUi.button("Scan my house",()->{String owner=panelProfile;long ticket=panelEpoch;clientThread.invokeLater(()->{if(running&&epoch==ticket&&Objects.equals(owner,profile)){scanOwnHouse();onGameTick();refreshPanel();}});});body.add(scan);
        JPanel list=SidebarUi.column();list.add(SidebarUi.button("Select all portal destinations",()->portalChecks.values().forEach(box->box.setSelected(true))));list.add(SidebarUi.button("Clear portal selection",()->portalChecks.values().forEach(box->box.setSelected(false))));for(Portal portal:portals){JCheckBox choice=check(portal.label);portalChecks.put(portal.key,choice);list.add(choice);}body.add(SidebarUi.disclosure("Portals and nexus",list));
        fairy=check("Fairy ring");spirit=check("Spirit tree");obelisk=check("Wilderness obelisk");body.add(fairy);body.add(spirit);body.add(obelisk);
        jewellery=new JComboBox<>(new String[]{"None","Basic","Fancy","Ornate"});body.add(SidebarUi.field("Jewellery box",jewellery));
        JPanel mounts=SidebarUi.column();String[][] names={{"GLORY","Mounted glory"},{"XERICS_TALISMAN","Mounted Xeric's talisman"},{"DIGSITE_PENDANT","Mounted digsite pendant"},{"MYTHICAL_CAPE","Mounted mythical cape"}};for(String[] entry:names){JCheckBox box=check(entry[1]);mountedChecks.put(entry[0],box);mounts.add(box);}body.add(SidebarUi.disclosure("Mounted teleports",mounts));
        body.add(SidebarUi.wrappedButton("Use my POH for routing",()->{Saved selection=new Saved();portalChecks.forEach((key,box)->{if(box.isSelected())selection.portals.add(key);});mountedChecks.forEach((key,box)->{if(box.isSelected())selection.mounted.add(key);});selection.fairyRing=fairy.isSelected();selection.spiritTree=spirit.isSelected();selection.obelisk=obelisk.isSelected();selection.jewellery=((String)jewellery.getSelectedItem()).toUpperCase(Locale.ROOT);String owner=panelProfile;long ticket=panelEpoch;clientThread.invokeLater(()->{if(running&&epoch==ticket)apply(owner,selection);});}));
        panel=SidebarUi.disclosure("My POH",body);SidebarUi.install(panel);refreshPanel();return panel;
    }
    private static JCheckBox check(String label){JCheckBox box=new JCheckBox(label);box.setOpaque(false);box.setForeground(SidebarUi.TEXT);return box;}
    private void refreshPanel()
    {
        Snapshot state=snapshot;long ticket=epoch;SwingUtilities.invokeLater(()->{if(panel==null||ticket!=epoch)return;panelProfile=state.profile;panelEpoch=ticket;
            scan.setEnabled(state.ownHouse);scan.setToolTipText(state.ownHouse?"Read the portals in your house":"Enter your house in building mode to scan its portals");
            status.setText(state.profile==null?"Log in to manage your house.":(state.usable?"POH routing enabled":"POH routing needs your house selection")+" · "+state.observed.size()+" observed · "+state.suggestions.size()+" menu suggestions to confirm"+(state.ownHouse?" · Your house":""));
            Set<String> selected=new TreeSet<>(state.portals);
            portalChecks.forEach((key,box)->{box.setSelected(selected.contains(key));box.setToolTipText(state.observed.contains(key)?"Observed in your house":state.suggestions.contains(key)?"Seen in a nexus menu; confirm this is in your house":null);box.setEnabled(state.profile!=null);});
            mountedChecks.forEach((key,box)->box.setSelected(state.mounted.contains(key)));fairy.setSelected(state.fairyRing);spirit.setSelected(state.spiritTree);obelisk.setSelected(state.obelisk);jewellery.setSelectedItem(state.jewellery.substring(0,1)+state.jewellery.substring(1).toLowerCase(Locale.ROOT));panel.revalidate();panel.repaint();});
    }
    private Set<String> nativePortals(){Set<String> result=nativeSet("pohNexusPortals",byKey.keySet());return config.getConfiguration(NATIVE,"pohNexusPortals")==null&&"true".equals(config.getConfiguration(NATIVE,"useTeleportationPortalsPoh"))?byKey.keySet():result;}
    private Set<String> nativeSet(String key,Set<String> allowed){String raw=config.getConfiguration(NATIVE,key);if(raw==null||raw.length()>12000)return Set.of();try{Set<String> result=gson.fromJson(raw,new TypeToken<Set<String>>(){}.getType());return result!=null&&allowed.containsAll(result)?result:Set.of();}catch(RuntimeException invalid){return Set.of();}}
}
