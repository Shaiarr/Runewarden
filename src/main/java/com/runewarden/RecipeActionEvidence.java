/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
/** A selected recipe, its exact item transformation and matching XP must agree before actions count. */
final class RecipeActionEvidence
{
    private ActionRecipeCatalog.Recipe recipe;private Map<Integer,Long> previous;
    private final Map<Integer,Long> changes=new LinkedHashMap<>();private long xp;private int since=-1;
    void select(ActionRecipeCatalog.Recipe selected){recipe=selected;previous=null;discard();}
    void suspend(){previous=null;discard();}
    void discard(){changes.clear();xp=0;since=-1;}
    void inventory(Map<Integer,Long> items,int tick,boolean allowed)
    {
        if(!allowed||recipe==null||recipe.products.isEmpty()||recipe.xp<=0){previous=items;discard();return;}
        if(previous!=null)
        {
            java.util.HashSet<Integer> ids=new java.util.HashSet<>(previous.keySet());ids.addAll(items.keySet());
            for(int id:ids){long delta=items.getOrDefault(id,0L)-previous.getOrDefault(id,0L);if(delta!=0){changes.merge(id,delta,Long::sum);if(since<0)since=tick;}}
        }
        previous=Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }
    void experience(long delta,int tick,boolean allowed){if(!allowed){discard();return;}if(delta>0&&recipe!=null&&!recipe.products.isEmpty()&&recipe.xp>0){xp+=delta;if(since<0)since=tick;}}
    long confirm(int tick)
    {
        if(since<0||tick-since<3)return 0;long units=Long.MAX_VALUE;
        if(recipe==null||xp<=0||recipe.products.isEmpty()){discard();return 0;}
        Map<Integer,Long> required=new LinkedHashMap<>(),products=new LinkedHashMap<>();
        for(ActionRecipeCatalog.Ingredient input:recipe.ingredients)required.merge(input.itemId,(long)input.quantity,Long::sum);
        for(ActionRecipeCatalog.Ingredient output:recipe.products)products.merge(output.itemId,(long)output.quantity,Long::sum);
        for(Map.Entry<Integer,Long> input:required.entrySet())units=Math.min(units,Math.max(0,-changes.getOrDefault(input.getKey(),0L))/input.getValue());
        for(Map.Entry<Integer,Long> output:products.entrySet())units=Math.min(units,Math.max(0,changes.getOrDefault(output.getKey(),0L))/output.getValue());
        boolean exact=units>0&&units!=Long.MAX_VALUE&&Math.abs(xp-units*recipe.xp)<1.000001;
        if(exact)for(Map.Entry<Integer,Long> input:required.entrySet())if(changes.getOrDefault(input.getKey(),0L)!=-units*input.getValue()){exact=false;break;}
        if(exact)for(Map.Entry<Integer,Long> output:products.entrySet())if(changes.getOrDefault(output.getKey(),0L)!=units*output.getValue()){exact=false;break;}
        discard();return exact?units:0;
    }
}
