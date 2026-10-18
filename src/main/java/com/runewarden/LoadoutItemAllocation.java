/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Integral maximum-flow allocation prevents overlapping alternatives from consuming the same item twice. */
final class LoadoutItemAllocation
{
    final long[] carried;
    final Map<LoadoutPlan.Location,Map<Integer,Long>> remaining = new EnumMap<>(LoadoutPlan.Location.class);
    private static final class Edge { final int to, reverse; long capacity; Edge(int to,int reverse,long capacity){this.to=to;this.reverse=reverse;this.capacity=capacity;} }
    private static final class Pool { final int id,slot; final long count; Edge available; Pool(int id,int slot,long count){this.id=id;this.slot=slot;this.count=count;} }
    private LoadoutItemAllocation(int size){carried=new long[size];}
    static LoadoutItemAllocation allocate(LoadoutPlan plan,OwnershipLedger.View state)
    {
        LoadoutItemAllocation result=new LoadoutItemAllocation(plan.requirements.size());
        for(LoadoutPlan.Location location:LoadoutPlan.Location.values())
        {
            List<Pool> pools=new ArrayList<>();
            if(location==LoadoutPlan.Location.RUNE_POUCH)
            {if(state.runePouchObserved)state.runePouch.forEach((id,count)->pools.add(new Pool(id,-1,count)));}
            else
            {
                OwnershipLedger.Storage storage=location==LoadoutPlan.Location.EQUIPMENT?OwnershipLedger.Storage.EQUIPMENT:location==LoadoutPlan.Location.QUIVER?OwnershipLedger.Storage.QUIVER:OwnershipLedger.Storage.INVENTORY;
                OwnershipLedger.Observation observation=state.storages.get(storage);
                if(observation!=null&&observation.currentSession&&(location!=LoadoutPlan.Location.QUIVER||state.quiverAvailable))
                    observation.slots.forEach((slot,item)->pools.add(new Pool(item.id,slot,item.quantity)));
            }
            // Exact-ID variants share physical pools. Prefer lower dose bottles for item-count requirements.
            pools.sort(Comparator.comparingInt(pool->LoadoutItemVariants.doses(pool.id)));
            List<Integer> needs=new ArrayList<>();
            for(int i=0;i<plan.requirements.size();i++){LoadoutPlan.Requirement need=plan.requirements.get(i);if(need.location==location&&need.unit()==LoadoutPlan.Unit.ITEMS)needs.add(i);}
            int sink=1+pools.size()+needs.size();List<List<Edge>> graph=new ArrayList<>();for(int i=0;i<=sink;i++)graph.add(new ArrayList<>());
            for(int p=0;p<pools.size();p++)pools.get(p).available=edge(graph,0,1+p,pools.get(p).count);
            List<Edge> fulfilled=new ArrayList<>();
            for(int n=0;n<needs.size();n++)
            {
                LoadoutPlan.Requirement need=plan.requirements.get(needs.get(n));int node=1+pools.size()+n;
                fulfilled.add(edge(graph,node,sink,need.quantity));
                for(int p=0;p<pools.size();p++)
                {Pool pool=pools.get(p);if(need.alternatives.contains(pool.id)&&(need.slot<0||need.slot==pool.slot))edge(graph,1+p,node,Math.min(pool.count,need.quantity));}
            }
            while(true){boolean[] seen=new boolean[graph.size()];long sent=send(graph,0,sink,Long.MAX_VALUE,seen);if(sent==0)break;}
            for(int n=0;n<needs.size();n++)result.carried[needs.get(n)]=plan.requirements.get(needs.get(n)).quantity-fulfilled.get(n).capacity;
            Map<Integer,Long> remaining=new LinkedHashMap<>();for(Pool pool:pools)remaining.merge(pool.id,pool.available.capacity,Long::sum);result.remaining.put(location,remaining);
        }
        return result;
    }
    private static Edge edge(List<List<Edge>> graph,int from,int to,long amount)
    {Edge forward=new Edge(to,graph.get(to).size(),amount),reverse=new Edge(from,graph.get(from).size(),0);graph.get(from).add(forward);graph.get(to).add(reverse);return forward;}
    private static long send(List<List<Edge>> graph,int node,int sink,long limit,boolean[] seen)
    {
        if(node==sink)return limit;seen[node]=true;
        for(Edge edge:graph.get(node))if(edge.capacity>0&&!seen[edge.to])
        {long sent=send(graph,edge.to,sink,Math.min(limit,edge.capacity),seen);if(sent>0){edge.capacity-=sent;graph.get(edge.to).get(edge.reverse).capacity+=sent;return sent;}}
        return 0;
    }
}
