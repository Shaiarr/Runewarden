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
        if(since<0||tick-since<3)return 0;
        long units=recipe==null?0:ActionRecipeDetector.matches(recipe,id->changes.getOrDefault(id,0L),xp);
        discard();return units;
    }
}
