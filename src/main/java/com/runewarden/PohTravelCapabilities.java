/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Observations and user-managed native house declarations. Never writes another plugin's configuration. */
@Singleton
final class PohTravelCapabilities
{
    static final String KEY="pohTravelV1",LEASE_KEY="pohTravelAppliedV1",NATIVE="shortestpath";
    static final String[] CONFIG_KEYS={"usePoh","pohNexusPortals","pohJewelleryBoxTier","pohMountedItems","usePohFairyRing","usePohSpiritTree","usePohObelisk","useTeleportationPortalsPoh","usePohMountedItems"};
    private static final int[] POUCH={VarbitID.RUNE_POUCH_TYPE_1,VarbitID.RUNE_POUCH_TYPE_2,VarbitID.RUNE_POUCH_TYPE_3,VarbitID.RUNE_POUCH_TYPE_4,VarbitID.RUNE_POUCH_TYPE_5,VarbitID.RUNE_POUCH_TYPE_6,VarbitID.RUNE_POUCH_QUANTITY_1,VarbitID.RUNE_POUCH_QUANTITY_2,VarbitID.RUNE_POUCH_QUANTITY_3,VarbitID.RUNE_POUCH_QUANTITY_4,VarbitID.RUNE_POUCH_QUANTITY_5,VarbitID.RUNE_POUCH_QUANTITY_6};
    static final class Portal{String key,label;List<String> names;List<Integer> objectIds;}
    private static final class Catalog{int version;List<Portal> portals;Set<Integer> revisionItemIds;}
    static final class Saved{int version=1;Set<String> observed=new TreeSet<>();}
    static final class Applied{int version=1;String profile,fingerprint;}
    static final class Snapshot
    {
        final String profile;final Set<String> observed,portals;final boolean usable,ownHouse,constructionCape;
        Snapshot(String profile,Set<String> observed,Set<String> portals,boolean usable,boolean ownHouse,boolean constructionCape)
        {this.profile=profile;this.observed=Set.copyOf(observed);this.portals=Set.copyOf(portals);this.usable=usable;this.ownHouse=ownHouse;this.constructionCape=constructionCape;}
    }
    private final Client client;private final ClientThread thread;private final ConfigManager config;private final RunewardenDataStore data;
    private final List<Portal> portals;private final Set<Integer> revisionItemIds;
    private final Map<String,String> names=new HashMap<>();private final Map<Integer,String> objects=new HashMap<>();private final Set<String> keys=new HashSet<>();
    private boolean running;private String profile;private long epoch,revision;private int ticks,lastFingerprint;
    private Saved saved=new Saved();private Applied applied=new Applied();
    private volatile Snapshot snapshot=new Snapshot(null,Set.of(),Set.of(),false,false,false);

    @Inject PohTravelCapabilities(Client client,ClientThread thread,ConfigManager config,RunewardenDataStore data,Gson gson)
    {
        this.client=client;this.thread=thread;this.config=config;this.data=data;
        try(InputStreamReader reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/com/runewarden/poh-travel-portals.json")),StandardCharsets.UTF_8))
        {Catalog catalog=gson.fromJson(reader,Catalog.class);portals=List.copyOf(catalog.portals);revisionItemIds=Set.copyOf(catalog.revisionItemIds);}
        catch(java.io.IOException failure){throw new IllegalStateException("POH travel catalog",failure);}
        for(Portal portal:portals)
        {
            keys.add(portal.key);names.put(normalize(portal.label),portal.key);names.put(normalize(portal.key.replace('_',' ')),portal.key);
            for(String name:portal.names)names.put(normalize(name),portal.key);
            for(int id:portal.objectIds)objects.put(id,portal.key);
        }
        names.put("exam centre","SENNTISTEN");names.put("exam center","SENNTISTEN");
    }
    void start()
    {
        running=true;profile=null;ticks=0;long ticket=++epoch;
        Runnable update=()->{if(running&&epoch==ticket)onGameTick();};if(thread==null)update.run();else thread.invokeLater(update);
    }
    void stop(){running=false;epoch++;profile=null;saved=new Saved();snapshot=new Snapshot(null,Set.of(),Set.of(),false,false,false);revision++;}
    Snapshot snapshot(){return snapshot;}
    long revision(){return revision;}
    List<Portal> portals(){return portals;}

    Map<String,Object> routeOptions()
    {
        boolean valid=running&&profile!=null&&Objects.equals(profile,config.getRSProfileKey())&&client.getGameState()==GameState.LOGGED_IN;
        return Map.of("useTeleportationItems","Inventory","usePoh",valid&&usable(nativeFingerprint()));
    }
    void onGameTick()
    {
        if(!running)return;
        String account=client.getGameState()==GameState.LOGGED_IN?config.getRSProfileKey():null;
        if(!Objects.equals(profile,account))
        {
            profile=account;epoch++;revision++;
            saved=profile==null?new Saved():data.read(RunewardenConfig.GROUP,profile,KEY,1,Saved.class,s->s.version==1&&s.observed!=null&&s.observed.size()<=40&&keys.containsAll(s.observed),Saved::new);
            applied=data.read(RunewardenConfig.GROUP,null,LEASE_KEY,1,Applied.class,a->a.version==1,Applied::new);
        }
        if(profile==null){snapshot=new Snapshot(null,Set.of(),Set.of(),false,false,false);return;}
        boolean own=ownHouseOrigin()!=null;
        if(own&&++ticks%10==0)scanOwnHouse();
        Map<Integer,Integer> held=heldItems();Map<Integer,Integer> relevant=new TreeMap<>(held);relevant.keySet().retainAll(revisionItemIds);
        String nativeState=nativeFingerprint();int fingerprint=Objects.hash(profile,nativeState,relevant,saved.observed,client.getRealSkillLevel(Skill.CONSTRUCTION),client.getRealSkillLevel(Skill.MAGIC));
        for(int varbit:POUCH)fingerprint=31*fingerprint+client.getVarbitValue(varbit);
        fingerprint=31*fingerprint+client.getVarbitValue(4744);fingerprint=31*fingerprint+client.getVarbitValue(4070);
        if(fingerprint!=lastFingerprint){lastFingerprint=fingerprint;revision++;}
        boolean cape=client.getRealSkillLevel(Skill.CONSTRUCTION)>=99&&(held.containsKey(9789)||held.containsKey(9790));
        snapshot=new Snapshot(profile,saved.observed,nativePortals(),usable(nativeState),own,cape);
    }
    private boolean usable(String fingerprint)
    {
        if(profile==null||!Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePoh"))||data.blocked(RunewardenConfig.GROUP,null,LEASE_KEY))return false;
        // A legacy declaration written by this project belongs only to the character that confirmed it.
        if(applied.profile!=null&&Objects.equals(applied.fingerprint,fingerprint)&&!Objects.equals(applied.profile,profile))return false;
        // The native plugin owns these declarations. A cape alone or an unconfirmed menu cache proves no facility.
        return !nativePortals().isEmpty()||explicitSet("pohMountedItems")||explicitTier()
            ||Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohFairyRing"))
            ||Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohSpiritTree"))
            ||Boolean.parseBoolean(config.getConfiguration(NATIVE,"usePohObelisk"));
    }
    private boolean explicitTier(){String tier=config.getConfiguration(NATIVE,"pohJewelleryBoxTier");return "BASIC".equals(tier)||"FANCY".equals(tier)||"ORNATE".equals(tier);}
    private boolean explicitSet(String key)
    {String raw=config.getConfiguration(NATIVE,key);if(raw==null||raw.length()>1000)return false;try{JsonElement value=new JsonParser().parse(raw);return value.isJsonArray()&&value.getAsJsonArray().size()>0;}catch(RuntimeException invalid){return false;}}
    private Set<String> nativePortals()
    {
        Set<String> result=new TreeSet<>();String raw=config.getConfiguration(NATIVE,"pohNexusPortals");if(raw==null||raw.length()>4000)return result;
        try{JsonElement value=new JsonParser().parse(raw);if(value.isJsonArray())for(JsonElement item:value.getAsJsonArray())if(item.isJsonPrimitive()&&keys.contains(item.getAsString()))result.add(item.getAsString());}catch(RuntimeException invalid){return Set.of();}return result;
    }
    /** Native Shortest Path models an owned house at this virtual landing, independent of room rotation. */
    WorldPoint ownHouseOrigin()
    {
        if(client.getGameState()!=GameState.LOGGED_IN||client.getVarbitValue(VarbitID.POH_BUILDING_MODE)!=1||client.getLocalPlayer()==null)return null;
        Player player=client.getLocalPlayer();if(player.getLocalLocation()==null)return null;
        WorldView view=client.getTopLevelWorldView();if(view==null||!view.isInstance()||view.getInstanceTemplateChunks()==null||client.getWorldView(player.getLocalLocation().getWorldView())!=view)return null;
        WorldPoint point=WorldPoint.fromLocalInstance(client,player.getLocalLocation());
        return point!=null&&point.getX()>=1856&&point.getX()<=2047&&point.getY()>=7040&&point.getY()<=7111?new WorldPoint(1858,7051,0):null;
    }
    boolean scanOwnHouse()
    {
        if(!running||profile==null||!Objects.equals(profile,config.getRSProfileKey())||ownHouseOrigin()==null)return false;
        Set<String> found=new TreeSet<>();WorldView view=client.getTopLevelWorldView();
        if(view.getScene()!=null&&view.getScene().getTiles()!=null)
        {
            Tile[][][] tiles=view.getScene().getTiles();int plane=view.getPlane(),budget=16384;
            if(plane>=0&&plane<tiles.length&&tiles[plane]!=null)for(Tile[] row:tiles[plane])
            {if(row==null)continue;for(Tile tile:row){if(--budget<0)break;if(tile==null||tile.getGameObjects()==null)continue;for(GameObject object:tile.getGameObjects())if(object!=null){String key=objects.get(object.getId());if(key!=null)found.add(key);}}if(budget<0)break;}
        }
        Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());int[] budget={2048};
        for(int id:new int[]{InterfaceID.TelenexusTeleport.TEXT1,InterfaceID.TelenexusTeleport.EXTRAS,InterfaceID.Telenexus.SLOTTED_LIST,InterfaceID.Telenexus.ROWS2})collect(client.getWidget(id),found,seen,budget,0);
        if(!saved.observed.addAll(found))return false;
        data.write(RunewardenConfig.GROUP,profile,KEY,1,saved);revision++;return true;
    }
    private void collect(Widget widget,Set<String> found,Set<Widget> seen,int[] budget,int depth)
    {
        if(widget==null||widget.isHidden()||depth>8||budget[0]--<=0||!seen.add(widget))return;
        String key=keyForName(widget.getText());if(key!=null)found.add(key);
        for(Widget[] children:new Widget[][]{widget.getDynamicChildren(),widget.getStaticChildren(),widget.getNestedChildren()})if(children!=null)for(Widget child:children)collect(child,found,seen,budget,depth+1);
    }
    String keyForName(String raw)
    {if(raw==null||raw.length()>400)return null;return names.get(normalize(raw.replaceAll("<[^>]*>","").replaceFirst("(?i)^\\s*([1-9A-Z]|F(?:[1-9]|1[0-2]))\\s*:\\s*","")));}
    private static String normalize(String raw)
    {return raw.toLowerCase(Locale.ROOT).replace("portal"," ").replace("teleport to"," ").replaceAll("\\([^)]*\\)"," ").replaceAll("[^a-z0-9]+"," ").trim().replaceAll(" +"," ");}
    private String nativeFingerprint(){StringBuilder value=new StringBuilder();for(String key:CONFIG_KEYS)value.append(key).append('=').append(config.getConfiguration(NATIVE,key)).append(';');return value.toString();}
    private Map<Integer,Integer> heldItems()
    {
        Map<Integer,Integer> held=new TreeMap<>();for(InventoryID id:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT})
        {ItemContainer container=client.getItemContainer(id);if(container!=null&&container.getItems()!=null)for(Item item:container.getItems())if(item!=null&&item.getId()>0&&item.getQuantity()>0)held.merge(item.getId(),item.getQuantity(),Integer::sum);}return held;
    }
}
