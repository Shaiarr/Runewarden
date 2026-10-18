/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;

/** Guarded personal destinations and genuinely observed bank stops. */
final class TravelNotebook
{
    static final String KEY="travelPlansV1";
    static final int MAX_DESTINATIONS=60,MAX_BANKS=12;
    static final class StoredPoint
    {
        String id,name,guideId;int x,y,plane;long observedAt;List<Map<Integer,Integer>> requirements=new ArrayList<>();
        WorldPoint point(){return new WorldPoint(x,y,plane);}
    }
    static final class Saved {int version=1;List<StoredPoint> destinations=new ArrayList<>(),banks=new ArrayList<>();}
    static final class Entry
    {
        final String id,name,guideId;final WorldPoint point;final long observedAt;final List<Map<Integer,Integer>> requirements;
        Entry(StoredPoint value){id=value.id;name=value.name;guideId=value.guideId;point=value.point();observedAt=value.observedAt;requirements=copyRequirements(value.requirements);}
        @Override public String toString(){return name;}
    }
    private final ConfigManager config;private final RunewardenDataStore data;private String profile;private Saved saved=new Saved();
    TravelNotebook(ConfigManager config,RunewardenDataStore data){this.config=config;this.data=data;}
    void clear(){profile=null;saved=new Saved();}
    void bind(String next){if(Objects.equals(profile,next))return;clear();profile=next;if(next!=null)saved=data.read(RunewardenConfig.GROUP,next,KEY,1,Saved.class,TravelNotebook::valid,Saved::new);}
    List<Entry> destinations(){return reading(saved.destinations);}
    List<Entry> banks(){return reading(saved.banks);}
    private List<Entry> reading(List<StoredPoint> values){if(!current())return Collections.emptyList();List<Entry> result=new ArrayList<>();for(StoredPoint value:values)result.add(new Entry(value));return Collections.unmodifiableList(result);}
    boolean writable(){return current()&&!data.blocked(RunewardenConfig.GROUP,profile,KEY);}
    boolean put(String id,String name,WorldPoint point,String guideId,List<Map<Integer,Integer>> requirements)
    {
        if(!writable()||!validName(name)||!GuideNavigation.valid(point)||guideId!=null&&(guideId.length()>250||!guideId.contains(":")))return false;
        try{GuideNavigation.target(point,requirements);}catch(RuntimeException invalid){return false;}
        StoredPoint record=null;for(StoredPoint candidate:saved.destinations)if(candidate.id.equals(id))record=candidate;
        if(record==null){if(id!=null||saved.destinations.size()>=MAX_DESTINATIONS)return false;record=new StoredPoint();record.id=UUID.randomUUID().toString();saved.destinations.add(record);}
        record.name=name.trim();record.x=point.getX();record.y=point.getY();record.plane=point.getPlane();record.guideId=guideId;record.requirements=copyRequirements(requirements);return save();
    }
    boolean remove(String id){if(!writable())return false;boolean changed=saved.destinations.removeIf(value->value.id.equals(id));return changed&&save();}
    boolean forgetBank(String id){if(!writable())return false;boolean changed=saved.banks.removeIf(value->value.id.equals(id));return changed&&save();}
    boolean observeBank(WorldPoint point,long now)
    {
        if(!writable()||!GuideNavigation.valid(point)||now<1)return false;
        StoredPoint observed=null;for(StoredPoint bank:saved.banks)if(bank.point().getPlane()==point.getPlane()&&bank.point().distanceTo2D(point)<=8){observed=bank;break;}
        if(observed!=null&&now-observed.observedAt<60000)return false;
        if(observed!=null)saved.banks.remove(observed);else{observed=new StoredPoint();observed.id=UUID.randomUUID().toString();}
        observed.name="Observed bank · "+point.getX()+", "+point.getY();observed.x=point.getX();observed.y=point.getY();observed.plane=point.getPlane();observed.observedAt=now;saved.banks.add(0,observed);
        while(saved.banks.size()>MAX_BANKS)saved.banks.remove(saved.banks.size()-1);return save();
    }
    private boolean current(){return profile!=null&&profile.equals(config.getRSProfileKey());}
    private boolean save(){return current()&&data.write(RunewardenConfig.GROUP,profile,KEY,1,saved);}
    private static boolean validName(String name){return name!=null&&!name.trim().isEmpty()&&name.length()<=100&&name.chars().noneMatch(c->c<32||c=='<'||c=='>');}
    static boolean valid(Saved value)
    {
        if(value==null||value.version!=1||value.destinations==null||value.banks==null||value.destinations.size()>MAX_DESTINATIONS||value.banks.size()>MAX_BANKS)return false;
        Set<String> ids=new HashSet<>();for(List<StoredPoint> list:List.of(value.destinations,value.banks))for(StoredPoint point:list)
        {
            if(point==null||point.id==null||point.id.isEmpty()||point.id.length()>100||!ids.add(point.id)||!validName(point.name)||!GuideNavigation.valid(point.point())||point.observedAt<0||point.guideId!=null&&(point.guideId.length()>250||!point.guideId.contains(":"))||point.requirements==null)return false;
            try{GuideNavigation.target(point.point(),point.requirements);}catch(RuntimeException invalid){return false;}
        }return true;
    }
    static List<Map<Integer,Integer>> copyRequirements(List<Map<Integer,Integer>> source)
    {List<Map<Integer,Integer>> result=new ArrayList<>();if(source!=null)for(Map<Integer,Integer> group:source)result.add(Collections.unmodifiableMap(new LinkedHashMap<>(group)));return Collections.unmodifiableList(result);}
}
