/* SPDX-License-Identifier: BSD-2-Clause
 * Native mappings/logic adapted from pinned BSD sources.
 * Full notices: com/runewarden/licenses/sailing-coach-BSD.txt.
 */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/** Reads current native task rows, rather than embedding a release-specific task list. */
final class SailingCoachTasks
{
    static final class Task
    {
        final int slot,id,amount,taken,delivered,cargo;final SailingCoachRules.Port from,to;final String name;
        Task(int slot,int id,int amount,int taken,int delivered,int cargo,SailingCoachRules.Port from,SailingCoachRules.Port to,String name)
        {this.slot=slot;this.id=id;this.amount=amount;this.taken=taken;this.delivered=delivered;this.cargo=cargo;this.from=from;this.to=to;this.name=name;}
        SailingCoachRules.Port nextPort(){return taken<amount?from:to;}
        String objective(){return taken<amount?"Collect "+(amount-taken)+" cargo at "+from.name:delivered<amount?"Deliver "+(amount-delivered)+" cargo to "+to.name:"Finish at "+to.name+"'s ledger";}
        @Override public String toString(){return (slot+1)+" · "+from.name+" → "+to.name;}
    }
    private int retryTick;private int[] signature;private List<Task> tasks=List.of();private int unsupported;
    List<Task> tasks(){return tasks;}
    int unsupported(){return unsupported;}
    void clear(){signature=null;tasks=List.of();unsupported=0;}
    void observe(Client client)
    {
        int[] current=new int[15];for(int i=0;i<15;i++)current[i]=client.getVarbitValue(19574+i);
        if(Arrays.equals(current,signature)&&(unsupported==0||client.getTickCount()<retryTick))return;signature=current;retryTick=client.getTickCount()+10;List<Task> next=new ArrayList<>();unsupported=0;
        for(int slot=0;slot<5;slot++)
        {
            int id=current[slot*3];if(id<=0)continue;
            Task task=decode(client,slot,id,current[slot*3+1],current[slot*3+2]);if(task!=null)next.add(task);else unsupported++;
        }
        tasks=List.copyOf(next);
    }
    static Task decode(Client client,int slot,int id,int taken,int delivered)
    {
        try
        {
            List<Integer> rows=client.getDBRowsByValue(DBTableID.PortTask.ID,DBTableID.PortTask.COL_TASK_ID,0,id);if(rows==null||rows.size()!=1)return null;int row=rows.get(0);
            if(integer(client,row,DBTableID.PortTask.COL_TASK_TYPE,0)!=0)return null;
            SailingCoachRules.Port from=SailingCoachRules.port(integer(client,row,DBTableID.PortTask.COL_CARGO_PORT,0)),to=SailingCoachRules.port(integer(client,row,DBTableID.PortTask.COL_ENDING_PORT,0));
            int amount=integer(client,row,DBTableID.PortTask.COL_CARGO,1),cargo=integer(client,row,DBTableID.PortTask.COL_CARGO,0);
            if(from==null||to==null||amount<1||amount>10000||cargo<1||taken<0||taken>amount||delivered<0||delivered>taken)return null;
            Object[] names=client.getDBTableField(row,DBTableID.PortTask.COL_NAME,0);String name=names!=null&&names.length>0&&names[0] instanceof String?(String)names[0]:"Courier task";
            return new Task(slot,id,amount,taken,delivered,cargo,from,to,name);
        }
        catch(IllegalArgumentException|IndexOutOfBoundsException unavailable){return null;}
    }
    private static int integer(Client client,int row,int column,int index)
    {Object[] values=client.getDBTableField(row,column,index);return values!=null&&values.length>0&&values[0] instanceof Number?((Number)values[0]).intValue():-1;}
}
