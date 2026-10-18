/* SPDX-License-Identifier: BSD-2-Clause */
package net.runelite.client.plugins.timetracking.farming;

import com.google.gson.Gson;
import java.util.*;
/** Offline data generator, never compiled into the plugin. Uses the pinned client as the source of truth. */
public final class GenerateFarmingSnapshot {
  public static void main(String[] args) {
    Map<String,Object> out=new LinkedHashMap<>();out.put("version",1);out.put("runeLiteVersion","1.13.2");
    FarmingWorld world=new FarmingWorld();Map<String,Object> patches=new TreeMap<>();
    for (Set<FarmingPatch> tab:world.getTabs().values()) for (FarmingPatch p:tab) {
      Map<String,Object> row=new LinkedHashMap<>();
      String id=p.getRegion().getRegionID()+":"+p.getVarbit()+":"+p.getImplementation().name()+":"+p.getName();
      row.put("id",id);row.put("name",p.getName());row.put("region",p.getRegion().getName());row.put("regionId",p.getRegion().getRegionID());
      row.put("varbit",p.getVarbit());row.put("type",p.getImplementation().name());row.put("group",p.getImplementation().getTab().name());
      row.put("healthCheck",p.getImplementation().isHealthCheckRequired());row.put("farmer",p.getFarmer());patches.put(id,row);
    }
    Map<Integer,Set<Integer>> aliases=new TreeMap<>();
    for(int region=0;region<65536;region++) for(int xoff:new int[]{0,16,32,48,63}) for(int yoff:new int[]{0,16,32,48,63}) {
      int x=((region>>8)<<6)+xoff,y=((region&255)<<6)+yoff;
      for(FarmingRegion r:world.getRegionsForLocation(new net.runelite.api.coords.WorldPoint(x,y,0))) aliases.computeIfAbsent(r.getRegionID(),k->new TreeSet<>()).add(region);
    }
    for(Object patch:patches.values()) {Map<String,Object> row=(Map<String,Object>)patch;row.put("sentRegions",aliases.getOrDefault((Integer)row.get("regionId"),Collections.singleton((Integer)row.get("regionId"))));}
    out.put("patches",patches.values());Map<String,Object> allStates=new TreeMap<>();
    for (PatchImplementation implementation:PatchImplementation.values()) {
      List<Object> states=new ArrayList<>();
      for(int i=0;i<256;i++) {
        PatchState state=implementation.forVarbitValue(i);if(state==null){states.add(null);continue;}
        Map<String,Object> row=new LinkedHashMap<>();row.put("crop",state.getProduce().name());row.put("state",state.getCropState().name());row.put("stage",state.getStage());row.put("stages",state.getStages());row.put("tickMinutes",state.getTickRate());states.add(row);
      }
      allStates.put(implementation.name(),states);
    }
    out.put("states",allStates);Map<String,Object> crops=new TreeMap<>();
    for(Produce p:Produce.values()) {
      Map<String,Object> row=new LinkedHashMap<>();row.put("name",p.getName());row.put("itemId",p.getItemID());row.put("type",p.getPatchImplementation()==null?null:p.getPatchImplementation().name());crops.put(p.name(),row);
    }
    out.put("crops",crops);System.out.println(new Gson().toJson(out));
  }
}
