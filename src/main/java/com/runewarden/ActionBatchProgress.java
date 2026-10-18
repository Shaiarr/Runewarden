/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Map;

/** Inventory transformations plus matching XP confirm a batch; animation duration never supplies its rate. */
final class ActionBatchProgress
{
    private final RecipeActionEvidence evidence=new RecipeActionEvidence();
    private ActionRecipeCatalog.Recipe recipe;
    private long completed,remaining,initialAvailable,firstAt=-1,lastAt=-1;private boolean armed;
    void select(ActionRecipeCatalog.Recipe next)
    {recipe=next;evidence.select(next);completed=remaining=initialAvailable=0;firstAt=lastAt=-1;armed=false;}
    ActionRecipeCatalog.Recipe recipe(){return recipe;}
    void adopt(ActionRecipeDetector.Match match,int tick,long now)
    {
        select(match.recipe);inventory(match.after,tick,true);armed=true;
        completed=match.count;initialAvailable=completed+remaining;firstAt=match.startedAt;lastAt=now;
    }
    void inventory(Map<Integer,Long> items,int tick,boolean allowed)
    {
        if(!allowed){evidence.suspend();armed=false;completed=remaining=initialAvailable=0;firstAt=lastAt=-1;return;}
        if(armed&&completed>0&&remaining==0&&recipe!=null&&ActionRecipeCatalog.availableActions(recipe,items)>0)select(recipe);
        evidence.inventory(items,tick,true);remaining=recipe==null?0:ActionRecipeCatalog.availableActions(recipe,items);if(!armed)initialAvailable=remaining;
    }
    void experience(long xp,int tick,boolean allowed){evidence.experience(xp,tick,allowed);}
    void arm(long now){if(recipe!=null&&!armed){armed=true;firstAt=now;}}
    long confirm(int tick,long now)
    {
        long count=evidence.confirm(tick);
        if(count>0){if(!armed)arm(now);completed=Math.min(1_000_000_000L,completed+count);lastAt=now;}
        return count;
    }
    boolean visible(){return armed&&recipe!=null&&!recipe.products.isEmpty()&&(completed>0||remaining>0);}
    boolean firstAction(){return completed==0&&remaining>=initialAvailable;}
    long completed(){return completed;}
    long remaining(){return remaining;}
    long total(){return Math.max(initialAvailable,completed+remaining);}
    double fraction(){return total()==0?0:completed/(double)total();}
    long estimatedMillis()
    {
        if(remaining==0)return completed>0&&completed>=total()?0:-1;
        long elapsed=lastAt-firstAt;
        if(completed<3||elapsed<1200)return -1;
        return (long)Math.ceil(remaining*(double)elapsed/completed);
    }
}
