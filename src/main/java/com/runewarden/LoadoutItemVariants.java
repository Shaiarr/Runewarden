/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemVariationMapping;

/** Explicit user-selected item equivalence. Dose amounts come from RuneLite's verified charge catalog. */
final class LoadoutItemVariants
{
    private static final Map<Integer,Integer> DOSES = new LinkedHashMap<>();
    private static final Map<Integer,String> FAMILIES = new LinkedHashMap<>();
    private static final Map<String,Set<Integer>> POTIONS = new LinkedHashMap<>();
    static
    {
        Map<Integer,String> names=new LinkedHashMap<>();GuideItemResolver.nameIndex().forEach((name,ids)->ids.forEach(id->names.putIfAbsent(id,name)));
        try(InputStreamReader reader=new InputStreamReader(LoadoutItemVariants.class.getResourceAsStream("/com/runewarden/item-charge-reference.json"),StandardCharsets.UTF_8))
        {
            JsonArray items=new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("items");
            for(JsonElement element:items)
            {
                JsonObject item=element.getAsJsonObject();if(!"POTION".equals(item.get("type").getAsString()))continue;
                int id=item.get("itemId").getAsInt(),doses=item.get("charges").getAsInt();String name=names.get(id);
                if(name==null||!name.matches(".*\\([1-6]\\)$")||doses<1||doses>6)continue;
                String family=name.replaceFirst("\\([1-6]\\)$","").trim();DOSES.put(id,doses);FAMILIES.put(id,family);POTIONS.computeIfAbsent(family,ignored->new LinkedHashSet<>()).add(id);
            }
        }
        catch(java.io.IOException|RuntimeException error){throw new IllegalStateException("Invalid bundled potion variants",error);}
    }
    private LoadoutItemVariants() { }
    static int doses(int id){return DOSES.getOrDefault(id,0);}
    static Set<Integer> potion(int id){return Collections.unmodifiableSet(POTIONS.getOrDefault(FAMILIES.get(id),Collections.emptySet()));}
    static int oneDose(int id){for(Integer variant:potion(id))if(doses(variant)==1)return variant;return -1;}
    static Set<Integer> variations(int id)
    {
        Set<Integer> ids=new LinkedHashSet<>(ItemVariationMapping.getVariations(ItemVariationMapping.map(id)));ids.add(id);return ids;
    }
    static boolean validDoses(Set<Integer> ids){return !ids.isEmpty()&&ids.stream().allMatch(id->doses(id)>0);}
}
