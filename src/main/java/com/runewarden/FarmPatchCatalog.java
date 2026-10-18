/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.skillcalculator.skills.FarmingAction;

/** Immutable native patch states and a bounded seed index; no training or account planner. */
@Singleton
public final class FarmPatchCatalog
{
    static final class Data
    { int version; String runeLiteVersion; List<Patch> patches; Map<String,List<State>> states; Map<String,Crop> crops; }
    public static final class Patch
    {
        public String id, name, region, type, group;
        public int regionId, varbit, farmer;
        public boolean healthCheck;
        public List<Integer> sentRegions;
        public String label() { return region + (name == null || name.isEmpty() ? "" : " · " + name); }
        public boolean sentAt(WorldPoint point)
        {
            if (point == null || sentRegions == null || !sentRegions.contains(point.getRegionID())) return false;
            int x = point.getX(), y = point.getY();
            if ("Catherby".equals(region))
            {
                if ("FRUIT_TREE".equals(type)) return x >= 2840 || y < 3440 || point.getPlane() == 1;
                if (x >= 2816 && y < 3456) return x < 2840 && y >= 3440 && point.getPlane() == 0;
            }
            if ("Falador".equals(region) && !"TREE".equals(type) && y < 3272) return false;
            if ("Port Sarim".equals(region)) return y < 3272;
            if ("Fossil Island".equals(region) && "HARDWOOD_TREE".equals(type))
            {
                if (point.getPlane() != 0 || x == 3753 && y >= 3868 && y <= 3870) return false;
                if ((x == 3729 || x == 3728 || x == 3747 || x == 3746) && y <= 3832 && y >= 3830) return false;
            }
            return true;
        }
    }
    public static final class State
    { public String crop,state; public int stage,stages,tickMinutes; }
    public static final class Crop
    { public String name,type; public int itemId; }
    public static final class Seed
    {
        public final int id,growthMinutes;
        public final String name,type,cropKey;
        public final Integer level,quantity;
        Seed(int id,String key,Crop crop,int level,int quantity,int minutes)
        {
            this.id=id;cropKey=key;type=crop.type;this.level=level>0?level:null;
            this.quantity=quantity>0?quantity:null;growthMinutes=minutes;
            name="SEAWEED".equals(type)?"Seaweed spore":"MUSHROOM".equals(type)?"Mushroom spore":
                "Goutweed".equals(crop.name)?"Gout tuber":isTree(type)?crop.name.replace(" tree","")+" sapling":crop.name+" seed";
        }
        @Override public String toString(){return name+(level==null?"":" · "+level);}
    }
    // Exact IDs and verified quantities retained from the existing pinned native/Wiki snapshots.
    // Zero is an unknown level/quantity, never a guessed account requirement.
    private static final String[] SEED_FACTS = {
        "5496:APPLE:27:1",
        "5308:ASGARNIAN:0:4",
        "22881:ATTAS:0:1",
        "5298:AVANTOE:50:1",
        "5497:BANANA:33:1",
        "5305:BARLEY:0:4",
        "5281:BELLADONNA:0:1",
        "5324:CABBAGE:0:3",
        "5280:CACTUS:55:1",
        "5301:CADANTINE:67:1",
        "5102:CADAVABERRIES:22:1",
        "5503:CALQUAT:72:1",
        "31502:CAMPHOR:0:1",
        "22856:CELASTRUS:85:1",
        "31545:COTTON:0:0",
        "23659:CRYSTAL_TREE:0:1",
        "5499:CURRY:42:1",
        "22866:DRAGONFRUIT:81:1",
        "5303:DWARF_WEED:79:1",
        "5103:DWELLBERRIES:36:1",
        "31541:FLAX:0:0",
        "6311:GOUTWEED:0:1",
        "13657:GRAPE:0:1",
        "5291:GUAM:9:1",
        "5307:HAMMERSTONE:0:4",
        "5294:HARRALANDER:26:1",
        "31543:HEMP:0:0",
        "22875:HESPORI:0:1",
        "30088:HUASCA:65:1",
        "22883:IASOR:0:1",
        "5297:IRIT:44:1",
        "31505:IRONWOOD:0:1",
        "5104:JANGERBERRIES:48:1",
        "5306:JUTE:0:3",
        "5310:KRANDORIAN:0:4",
        "22885:KRONOS:0:1",
        "5299:KWUARM:56:1",
        "5302:LANTADYME:73:1",
        "5100:LIMPWURT:0:1",
        "5374:MAGIC:75:1",
        "21480:MAHOGANY:55:1",
        "5372:MAPLE:45:1",
        "5096:MARIGOLD:0:1",
        "5292:MARRENTILL:14:1",
        "5282:MUSHROOM:53:1",
        "5098:NASTURTIUM:0:1",
        "5370:OAK:15:1",
        "5319:ONION:0:3",
        "5498:ORANGE:39:1",
        "5502:PALM:68:1",
        "5501:PAPAYA:57:1",
        "5500:PINEAPPLE:51:1",
        "5106:POISON_IVY:70:1",
        "5318:POTATO:0:3",
        "22873:POTATO_CACTUS:64:1",
        "5295:RANARR:32:1",
        "5101:REDBERRIES:10:1",
        "22859:REDWOOD:90:1",
        "5097:ROSEMARY:0:1",
        "31508:ROSEWOOD:0:1",
        "21490:SEAWEED:0:1",
        "5300:SNAPDRAGON:62:1",
        "22879:SNAPE_GRASS:0:3",
        "5375:SPIRIT_TREE:83:1",
        "5323:STRAWBERRY:0:3",
        "5320:SWEETCORN:0:3",
        "5293:TARROMIN:19:1",
        "21477:TEAK:35:1",
        "5296:TOADFLAX:38:1",
        "5322:TOMATO:0:3",
        "5304:TORSTOL:85:1",
        "5321:WATERMELON:0:3",
        "5105:WHITEBERRIES:59:1",
        "22887:WHITE_LILY:0:1",
        "5311:WILDBLOOD:0:4",
        "5371:WILLOW:30:1",
        "5099:WOAD:0:1",
        "5309:YANILLIAN:16:4",
        "5373:YEW:60:1",
    };
    private final Map<String,Patch> patches;
    private final Map<String,List<State>> states;
    private final Map<String,Crop> crops;
    private final Map<String,List<Seed>> seeds=new LinkedHashMap<>();
    private final Map<Integer,Seed> seedIndex=new LinkedHashMap<>();

    @Inject public FarmPatchCatalog(Gson gson)
    {
        Data data=gson.fromJson(new InputStreamReader(FarmPatchCatalog.class.getResourceAsStream(
            "/com/runewarden/farming-native-snapshot.json"),StandardCharsets.UTF_8),Data.class);
        if(data==null||data.version!=1||!"1.13.2".equals(data.runeLiteVersion)||data.patches==null||data.patches.size()>250||data.states==null||data.crops==null)
            throw new IllegalStateException("Invalid native farming snapshot");
        Map<String,Patch> index=new LinkedHashMap<>();for(Patch value:data.patches)index.put(value.id,value);
        patches=Collections.unmodifiableMap(index);states=Collections.unmodifiableMap(data.states);crops=Collections.unmodifiableMap(data.crops);
        for(String row:SEED_FACTS)
        {
            String[] fields=row.split(":");Crop crop=crops.get(fields[1]);if(crop==null)continue;
            int level=Integer.parseInt(fields[2]),minutes=0;
            if(level==0)for(FarmingAction action:FarmingAction.values())if(action.getIcon()==crop.itemId){level=action.getLevel();break;}
            for(State state:states.getOrDefault(crop.type,List.of()))
                if(state!=null&&fields[1].equals(state.crop)&&"GROWING".equals(state.state)&&state.tickMinutes>0)
                    minutes=Math.max(minutes,(state.stages-1)*state.tickMinutes);
            Seed seed=new Seed(Integer.parseInt(fields[0]),fields[1],crop,level,Integer.parseInt(fields[3]),minutes);
            seedIndex.put(seed.id,seed);seeds.computeIfAbsent(seed.type,key->new ArrayList<>()).add(seed);
        }
        seeds.replaceAll((type,values)->{values.sort(java.util.Comparator.comparing(seed->seed.name));return Collections.unmodifiableList(values);});
    }
    private static boolean isTree(String type)
    {return type!=null&&(type.contains("TREE")||List.of("CALQUAT","CELASTRUS","REDWOOD").contains(type));}
    public List<Patch> patches(){return List.copyOf(patches.values());}
    public Patch patch(String id){return patches.get(id);}
    public Crop crop(String key){return crops.get(key);}
    public State state(String type,int value)
    {List<State> values=states.get(type);return values==null||value<0||value>=values.size()?null:values.get(value);}
    public List<Seed> seeds(String type){return seeds.getOrDefault(type,List.of());}
    public Seed seed(int id){return seedIndex.get(id);}
    int iconId(State state)
    {
        Crop crop=state==null?null:crops.get(state.crop);if(crop==null)return 0;
        if(isTree(crop.type))for(Seed seed:seeds(crop.type))if(seed.cropKey.equals(state.crop))return seed.id;
        return crop.itemId;
    }
    public List<Patch> forGuide(String id)
    {
        String type;
        switch(id==null?"":id)
        {
            case "herb":case "training:herb-runs":type="HERB";break;
            case "allotment":case "training:allotment-runs":type="ALLOTMENT,FLOWER";break;
            case "tree":case "training:tree-runs":type="TREE";break;
            case "fruit":case "training:fruit-tree-runs":type="FRUIT_TREE";break;
            case "hardwood":case "training:hardwood-runs":type="HARDWOOD_TREE";break;
            case "hops":type="HOPS";break;
            case "bush":type="BUSH";break;
            case "seaweed":case "training:giant-seaweed":type="SEAWEED";break;
            case "training:calquat-runs":type="CALQUAT";break;
            case "training:crystal-tree-runs":type="CRYSTAL_TREE";break;
            case "training:celastrus-runs":type="CELASTRUS";break;
            case "training:redwood-farming":type="REDWOOD";break;
            case "training:hespori":type="HESPORI";break;
            default:return List.of();
        }
        List<Patch> result=new ArrayList<>();
        for(Patch patch:patches.values())if((","+type+",").contains(","+patch.type+","))result.add(patch);
        result.sort(java.util.Comparator.comparing(Patch::label).thenComparing(value->value.type));return Collections.unmodifiableList(result);
    }
}
