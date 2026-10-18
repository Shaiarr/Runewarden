/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Skill;

/** Recognises an unselected recipe only after its exact transformation and XP agree. */
final class ActionRecipeDetector
{
    static final class Match
    {
        final BankedExperienceCalculator.Recipe recipe;final Map<Integer,Long> after;
        final long count,startedAt;
        Match(BankedExperienceCalculator.Recipe recipe,Map<Integer,Long> after,long count,long startedAt)
        {this.recipe=recipe;this.after=after;this.count=count;this.startedAt=startedAt;}
    }
    private Map<Integer,Long> previous,before,after;
    private final Map<Skill,Long> xp=new EnumMap<>(Skill.class);
    private int since=-1;private long startedAt;
    void clear(){previous=before=after=null;xp.clear();since=-1;startedAt=0;}
    void inventory(Map<Integer,Long> items,int tick,long now)
    {
        Map<Integer,Long> copy=Collections.unmodifiableMap(new LinkedHashMap<>(items));
        if(previous!=null&&!previous.equals(copy))
        {
            boolean consumed=false;for(Map.Entry<Integer,Long> item:previous.entrySet())if(copy.getOrDefault(item.getKey(),0L)<item.getValue()){consumed=true;break;}
            if(!consumed&&xp.isEmpty()){before=after=null;since=-1;}
            else{if(before==null)before=previous;after=copy;if(since<0){since=tick;startedAt=now;}}
        }
        previous=copy;
    }
    void experience(Skill skill,long delta,int tick,long now)
    {if(delta>0&&ActionProgressRules.nonCombat(skill)){xp.merge(skill,delta,Long::sum);if(since<0){since=tick;startedAt=now;}}}
    Match confirm(int tick)
    {
        if(since<0||tick-since<3)return null;Match match=null;
        if(before!=null&&after!=null)for(BankedExperienceCalculator.Recipe recipe:BankedExperienceCalculator.recipes())
        {
            long count=matches(recipe,before,after,xp.getOrDefault(recipe.skill,0L));
            if(count>0){if(match!=null){match=null;break;}match=new Match(recipe,after,count,startedAt);}
        }
        before=after=null;xp.clear();since=-1;return match;
    }
    static long matches(BankedExperienceCalculator.Recipe recipe,Map<Integer,Long> before,Map<Integer,Long> after,long xp)
    {
        if(recipe.xp<=0||recipe.products.isEmpty()||xp<=0)return 0;
        long count=Long.MAX_VALUE;Map<Integer,Long> inputs=amounts(recipe.ingredients),outputs=amounts(recipe.products);
        for(Map.Entry<Integer,Long> item:inputs.entrySet())count=Math.min(count,Math.max(0,before.getOrDefault(item.getKey(),0L)-after.getOrDefault(item.getKey(),0L))/item.getValue());
        for(Map.Entry<Integer,Long> item:outputs.entrySet())count=Math.min(count,Math.max(0,after.getOrDefault(item.getKey(),0L)-before.getOrDefault(item.getKey(),0L))/item.getValue());
        if(count<=0||count==Long.MAX_VALUE||Math.abs(xp-count*recipe.xp)>=1.000001)return 0;
        for(Map.Entry<Integer,Long> item:inputs.entrySet())if(before.getOrDefault(item.getKey(),0L)-after.getOrDefault(item.getKey(),0L)!=count*item.getValue())return 0;
        for(Map.Entry<Integer,Long> item:outputs.entrySet())if(after.getOrDefault(item.getKey(),0L)-before.getOrDefault(item.getKey(),0L)!=count*item.getValue())return 0;
        return count;
    }
    private static Map<Integer,Long> amounts(java.util.List<BankedExperienceCalculator.Ingredient> items)
    {Map<Integer,Long> result=new LinkedHashMap<>();for(BankedExperienceCalculator.Ingredient item:items)result.merge(item.itemId,(long)item.quantity,Long::sum);return result;}
}
