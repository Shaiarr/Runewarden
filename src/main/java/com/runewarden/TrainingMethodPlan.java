/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/** Immutable account-aware preparation and level goalposts; no game inputs or material allocation. */
public final class TrainingMethodPlan
{
    public static final class Requirement
    {
        public final String label; public final int state;
        Requirement(String label,int state){this.label=label;this.state=state;}
        public String display(){return(state>0?"✓ ":state<0?"Needed · ":"Check · ")+label;}
    }
    public static final class Goalpost
    {
        public final int level;public final long remainingXp;public final List<String> unlocks;
        Goalpost(int level,long remaining,List<String> unlocks){this.level=level;remainingXp=remaining;this.unlocks=Collections.unmodifiableList(unlocks);}
    }
    private TrainingMethodPlan(){}
    public static List<Requirement> requirements(ActivityXpCatalog.Method method,Map<Skill,Integer> levels,Map<String,QuestState> quests,int combat)
    {
        Map<String,Requirement> result=new LinkedHashMap<>();
        if(method.skill!=null){Integer level=levels.get(method.skill);String label=method.level+" "+method.skill.getName();result.put(label.toLowerCase(java.util.Locale.ROOT),new Requirement(label,level==null?0:level>=method.level?1:-1));}
        for(ActivityXpCatalog.Unlock unlock:method.unlocks)
        {
            String label=unlock.label==null?unlock.key:unlock.label;if(label==null)continue;
            result.put(label.toLowerCase(java.util.Locale.ROOT),new Requirement(label,unlock.state(levels,quests,combat)));
        }
        for(String label:method.requirements)result.putIfAbsent(label.toLowerCase(java.util.Locale.ROOT),new Requirement(label,0));
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }
    public static boolean blocked(List<Requirement> requirements){return requirements.stream().anyMatch(requirement->requirement.state<0);}
    /** Five-level goals plus real method unlocks, clipped to the player's selected target. */
    public static List<Goalpost> goalposts(ActivityXpCatalog catalog,Skill skill,long currentXp,int targetLevel,String intensity)
    {
        if(skill==null||currentXp<0||currentXp>200_000_000L||targetLevel<1||targetLevel>99)throw new IllegalArgumentException("Invalid training goal.");
        Map<Integer,List<String>> levels=new java.util.TreeMap<>();int current=Math.min(99,Experience.getLevelForXp((int)currentXp));
        for(int goal=(current/5+1)*5;goal<targetLevel;goal+=5)levels.put(goal,new ArrayList<>());
        if(Experience.getXpForLevel(targetLevel)>currentXp)levels.put(targetLevel,new ArrayList<>());
        for(ActivityXpCatalog.Method method:catalog.plans(skill))
            if(method.level>current&&method.level<=targetLevel&&(intensity==null||intensity.equals(method.intensity)))
                levels.computeIfAbsent(method.level,ignored->new ArrayList<>()).add(method.name);
        List<Goalpost> result=new ArrayList<>();
        for(Map.Entry<Integer,List<String>> entry:levels.entrySet())result.add(new Goalpost(entry.getKey(),Math.max(0,Experience.getXpForLevel(entry.getKey())-currentXp),entry.getValue()));
        return Collections.unmodifiableList(result);
    }
}
