/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

/** Typed, revision-pinned facts from the official Hub Hunter Rumours project. */
@Singleton
final class HunterRumourCatalog
{
    static final String SOURCE="https://github.com/geel9/runelite-hunter-rumours/tree/91b833b05b30bcb18cae812be5f0686b324d41c2";
    static final class Master { int id;String name,tier;int level(){return "MASTER".equals(tier)?91:"EXPERT".equals(tier)?72:"ADEPT".equals(tier)?57:46;} @Override public String toString(){return name+" · "+tier.toLowerCase(Locale.ROOT);} }
    static final class Trap { String id,name;int itemId,pity,fullOutfitPity;int threshold(int pieces){return(int)Math.floor(pity-(pity-fullOutfitPity)/4d*Math.max(0,Math.min(4,pieces)));} }
    static final class Location { String name,ring;int x,y,plane;WorldPoint point(){return new WorldPoint(x,y,plane);} String label(){return name+(ring.isEmpty()?"":" · "+ring);} }
    static final class Rumour
    {
        String id,name,trap;int npcId,icon,rareItem,level;List<Integer> xpDrops;List<String> tiers;List<Location> locations;
        @Override public String toString(){return name+" · "+level;}
        Map<String,List<Location>> groups(){Map<String,List<Location>> result=new LinkedHashMap<>();for(Location location:locations)result.computeIfAbsent(location.label(),ignored->new ArrayList<>()).add(location);return result;}
    }
    private static final class Data {int version;String revision;List<Master>masters;List<Trap>traps;List<Rumour>rumours;}
    private final Map<Integer,Master> masters=new LinkedHashMap<>();private final Map<String,Trap>traps=new LinkedHashMap<>();private final Map<String,Rumour>rumours=new LinkedHashMap<>();
    @Inject HunterRumourCatalog(Gson gson)
    {
        java.io.InputStream stream=HunterRumourCatalog.class.getResourceAsStream("/com/runewarden/hunter-rumour-reference.json");if(stream==null)throw new IllegalStateException("Missing Hunter rumour reference");
        try(InputStreamReader reader=new InputStreamReader(stream,StandardCharsets.UTF_8))
        {
            Data data=gson.fromJson(reader,Data.class);if(data==null||data.version!=1||!"91b833b05b30bcb18cae812be5f0686b324d41c2".equals(data.revision)||data.masters==null||data.masters.size()!=6||data.traps==null||data.rumours==null)throw new IllegalStateException("Invalid Hunter rumour reference");
            for(Master master:data.masters){if(master.id<=0||master.name==null||master.tier==null||masters.put(master.id,master)!=null)throw new IllegalStateException("Invalid rumour master");}
            for(Trap trap:data.traps){if(trap.id==null||trap.itemId<=0||trap.pity<1||trap.fullOutfitPity<1||trap.fullOutfitPity>trap.pity||traps.put(trap.id,trap)!=null)throw new IllegalStateException("Invalid rumour trap");}
            for(Rumour rumour:data.rumours)
            {
                if(rumour.id==null||rumour.name==null||!traps.containsKey(rumour.trap)||rumour.level<1||rumour.level>99||rumour.rareItem<=0||rumour.npcId<0||rumour.locations==null||rumour.xpDrops==null||rumour.xpDrops.size()>600||rumour.tiers==null||rumours.put(rumour.id,rumour)!=null)throw new IllegalStateException("Invalid rumour creature");
                for(Location location:rumour.locations)if(location.name==null||location.ring==null||location.x<0||location.x>16383||location.y<0||location.y>16383||location.plane<0||location.plane>3)throw new IllegalStateException("Invalid rumour location");
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read Hunter rumour reference",failure);}
    }
    Master master(int id){return masters.get(id);}Rumour rumour(String id){return rumours.get(id);}Trap trap(String id){return traps.get(id);}
    List<Master>masters(){return Collections.unmodifiableList(new ArrayList<>(masters.values()));}List<Rumour>rumours(){return Collections.unmodifiableList(new ArrayList<>(rumours.values()));}
    Rumour referencedRumour(String message)
    {String lower=message.toLowerCase(Locale.ROOT);Rumour result=null;for(Rumour rumour:rumours.values())if(lower.contains(rumour.name.toLowerCase(Locale.ROOT))&&(result==null||rumour.name.length()>result.name.length()))result=rumour;return result;}
    Master referencedMaster(String message)
    {String lower=message.toLowerCase(Locale.ROOT);for(Master master:masters.values())if(java.util.regex.Pattern.compile("\\b"+java.util.regex.Pattern.quote(master.name.toLowerCase(Locale.ROOT))+"\\b").matcher(lower).find())return master;return null;}
    Set<Integer> supplies(Rumour rumour)
    {
        Set<Integer> ids=new LinkedHashSet<>();if(rumour==null)return ids;Trap trap=trap(rumour.trap);
        if("FALCONRY".equals(rumour.trap))ids.add(995);
        else if("NOOSE_HERBIBOAR".equals(rumour.trap))ids.addAll(GuideItemResolver.resolveName("Secateurs"));
        else ids.add(trap.itemId);
        if(List.of("DEADFALL","PIT").contains(rumour.trap))for(String name:List.of("Logs","Knife"))ids.addAll(GuideItemResolver.resolveName(name));
        if("NET_TRAP".equals(rumour.trap))ids.addAll(GuideItemResolver.resolveName("Rope"));
        if("BUTTERFLY".equals(rumour.trap))ids.addAll(GuideItemResolver.resolveName("Butterfly jar"));
        return Collections.unmodifiableSet(ids);
    }
}
