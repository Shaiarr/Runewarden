/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exact known count or unknown; clicks alone cannot establish an amount. */
final class FishBarrelReading
{
    private int count=-1,checkUntil=-1,fillUntil=-1,pendingCaught,addedFish,lastCookingXp=-1;
    private boolean present,open,checkLoaded,cookingChanged;
    private Map<Integer,Long> inventory=Collections.emptyMap();
    int count(){return count;}
    boolean present(){return present;}
    void clear(){count=-1;checkUntil=fillUntil=-1;pendingCaught=addedFish=0;lastCookingXp=-1;present=open=checkLoaded=cookingChanged=false;inventory=Collections.emptyMap();}
    void cookingChanged(){cookingChanged=true;}
    void observe(boolean present,boolean open,Map<Integer,Long> items,int tick,int cookingXp)
    {
        if(!present){clear();return;}
        if(!this.present){inventory=new LinkedHashMap<>(items);lastCookingXp=cookingXp;}
        this.present=present;this.open=open;
        int added=0,removed=0,left=0;
        for(Map.Entry<Integer,Long> item:items.entrySet())if(ContainerHudRules.rawFish(item.getKey()))
        {left+=item.getValue();added+=Math.max(0,item.getValue()-inventory.getOrDefault(item.getKey(),0L));}
        for(Map.Entry<Integer,Long> item:inventory.entrySet())if(ContainerHudRules.rawFish(item.getKey()))removed+=Math.max(0,item.getValue()-items.getOrDefault(item.getKey(),0L));
        addedFish+=added;
        if(removed>0&&fillUntil>=tick)
        {
            if(count>=0){int next=count+removed;count=next<=ContainerHudRules.CAPACITY?next:-1;}
            else if(left>0)count=ContainerHudRules.CAPACITY;
            fillUntil=-1;
        }
        inventory=new LinkedHashMap<>(items);
    }
    void menu(String option,int item,int tick)
    {
        if(!present||!ContainerHudRules.BARRELS.contains(item))return;
        if("Check".equals(option)){checkUntil=tick+4;checkLoaded=false;}
        else if("Fill".equals(option))fillUntil=tick+3;
        else if("Empty".equals(option))count=-1;
    }
    void widgetLoaded(int group,int tick){if(group==ContainerHudRules.CHECK_GROUP&&checkUntil>=tick)checkLoaded=true;}
    boolean awaitingCheck(int tick){return present&&checkLoaded&&checkUntil>=tick;}
    private void authoritative(int value){count=value;pendingCaught=addedFish=0;}
    void check(String text,int tick)
    {if(!awaitingCheck(tick))return;int checked=ContainerHudRules.checkedFish(text);if(checked>=0){authoritative(checked);checkUntil=-1;checkLoaded=false;}}
    void chat(String message,boolean spam,boolean bankOpen)
    {
        if(!present||message==null)return;
        if("The barrel is full. It may be emptied at a bank.".equals(message)){authoritative(ContainerHudRules.CAPACITY);return;}
        if("Your bank could not hold your fish.".equals(message)){count=-1;return;}
        if(bankOpen&&("You empty all of your containers into the bank.".equals(message)||"Your containers are already empty.".equals(message))){authoritative(0);return;}
        if(spam&&open&&(ContainerHudRules.caughtFish(message)||ContainerHudRules.bonusCatch(message)))pendingCaught++;
    }
    void tick(int cookingXp)
    {
        if(pendingCaught>0)
        {
            if(!open||cookingChanged||lastCookingXp>=0&&cookingXp>lastCookingXp||pendingCaught<addedFish)count=-1;
            else if(count>=0)
            {
                int next=count+pendingCaught-addedFish;
                count=next<=ContainerHudRules.CAPACITY&&(addedFish==0||next==ContainerHudRules.CAPACITY)?next:-1;
            }
        }
        lastCookingXp=cookingXp;pendingCaught=addedFish=0;cookingChanged=false;
    }
}
