/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Native-deadline ledger. Scene disappearance is separate from a confirmed pickup. */
final class ClueJugglingTracker
{
    static final int MAX_ENTRIES=256, MAX_TICKS=1_008_000;
    enum Tier
    {
        BEGINNER("Beginner"), EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), ELITE("Elite"), MASTER("Master"), EXTRA("Extra");
        final String label;Tier(String label){this.label=label;}
        static Tier from(String name,boolean eliteChallenge)
        {
            if(eliteChallenge)return ELITE;
            String text=name==null?"":name.trim().toLowerCase(Locale.ROOT);
            for(Tier tier:values())if(tier!=EXTRA&&text.equals("clue scroll ("+tier.label.toLowerCase(Locale.ROOT)+")"))return tier;
            return null;
        }
    }
    enum Certainty { NATIVE, REMEMBERED, UNKNOWN }
    static final class Entry
    {
        final long key;final int id;final String name;final Tier tier;final WorldPoint point;
        int world,quantity,initialTicks;long deadline;boolean privateItem,present,notified,persistable=true;Certainty certainty;
        Entry(long key,int id,String name,Tier tier,WorldPoint point){this.key=key;this.id=id;this.name=name;this.tier=tier;this.point=point;}
    }
    static final class Row
    {
        final long key;final int id,quantity,world,ticks,initialTicks;final String name;final Tier tier;final WorldPoint point;
        final boolean present,privateItem;final Certainty certainty;
        Row(Entry e,long now){key=e.key;id=e.id;quantity=e.quantity;world=e.world;name=e.name;tier=e.tier;point=e.point;present=e.present;privateItem=e.privateItem;certainty=e.certainty;initialTicks=e.initialTicks;ticks=remaining(e,now);}
        boolean exact(){return certainty==Certainty.NATIVE;}
    }
    static final class SavedEntry
    {
        long key;int id,x,y,plane,world,quantity,remaining,initialTicks;String name,tier;boolean notified;
    }
    static final class Saved
    {
        int version=1;boolean clean;List<SavedEntry> entries=new ArrayList<>();
    }
    private final Map<Long,Entry> entries=new LinkedHashMap<>();private long now,nextKey=1;
    void clear(){entries.clear();now=0;nextKey=1;}
    int size(){return entries.size();}
    Row row(long key){Entry entry=entries.get(key);return entry==null?null:new Row(entry,now);}
    void advance(int ticks)
    {
        if(ticks<0||ticks>MAX_TICKS)return;now+=ticks;
        entries.values().removeIf(e->e.certainty!=Certainty.UNKNOWN&&remaining(e,now)<=0);
    }
    long observe(int id,String name,Tier tier,WorldPoint point,int world,int quantity,boolean privateItem,int nativeRemaining,long existing)
    {
        if(id<=0||tier==null||point==null||quantity<=0)return -1;
        Entry entry=entries.get(existing);
        if(entry==null)
        {
            // Reattach only an unseen native item. Separate simultaneous stacks keep their identity.
            long best=Long.MAX_VALUE;
            for(Entry candidate:entries.values())if(!candidate.present&&candidate.id==id&&candidate.point.equals(point)
                &&(candidate.world==world||candidate.privateItem&&privateItem))
            {
                long delta=nativeRemaining>0&&candidate.certainty!=Certainty.UNKNOWN?Math.abs((long)nativeRemaining-remaining(candidate,now)):MAX_TICKS;
                if(entry==null||delta<best){entry=candidate;best=delta;}
            }
        }
        if(entry==null)
        {
            if(entries.size()>=MAX_ENTRIES)return -1;
            entry=new Entry(nextKey++,id,name,tier,point);entries.put(entry.key,entry);entry.certainty=Certainty.UNKNOWN;
        }
        int before=remaining(entry,now);
        if(nativeRemaining>0&&nativeRemaining<=MAX_TICKS)
        {
            if(entry.certainty!=Certainty.UNKNOWN&&nativeRemaining>before+2)entry.notified=false;
            entry.deadline=now+nativeRemaining;entry.initialTicks=Math.max(entry.initialTicks,nativeRemaining);entry.certainty=Certainty.NATIVE;
        }
        else if(entry.certainty==null)entry.certainty=Certainty.UNKNOWN;
        entry.world=world;entry.quantity=quantity;entry.privateItem=privateItem;entry.present=true;
        return entry.key;
    }
    void absent(long key){Entry e=entries.get(key);if(e!=null)e.present=false;}
    void detach(){for(Entry e:entries.values())e.present=false;}
    void remove(long key){entries.remove(key);}
    void transientEntry(long key){Entry entry=entries.get(key);if(entry!=null)entry.persistable=false;}
    void dropTransient(){entries.values().removeIf(entry->!entry.persistable);}
    void removeWorldBound(int world){entries.values().removeIf(e->!e.privateItem&&e.world!=world);}
    List<Row> rows()
    {
        List<Row> result=new ArrayList<>();for(Entry e:entries.values())result.add(new Row(e,now));
        result.sort(Comparator.comparingInt((Row r)->r.ticks<0?Integer.MAX_VALUE:r.ticks).thenComparingLong(r->r.key));
        return Collections.unmodifiableList(result);
    }
    List<Row> alerts(int threshold,java.util.function.Predicate<Tier> included)
    {
        List<Row> result=new ArrayList<>();if(threshold<=0)return result;
        for(Entry e:entries.values())if(e.certainty==Certainty.NATIVE&&!e.notified&&remaining(e,now)>0&&remaining(e,now)<=threshold&&included.test(e.tier))
        {e.notified=true;result.add(new Row(e,now));}return result;
    }
    Saved save(boolean clean)
    {
        Saved saved=new Saved();saved.clean=clean;
        for(Entry e:entries.values())if(e.privateItem&&e.persistable&&e.tier!=Tier.EXTRA)
        {
            SavedEntry s=new SavedEntry();s.key=e.key;s.id=e.id;s.name=e.name;s.tier=e.tier.name();s.x=e.point.getX();s.y=e.point.getY();s.plane=e.point.getPlane();s.world=e.world;s.quantity=e.quantity;
            s.remaining=e.certainty==Certainty.UNKNOWN?-1:remaining(e,now);s.initialTicks=e.initialTicks;s.notified=e.notified;saved.entries.add(s);
        }
        return saved;
    }
    static boolean valid(Saved saved)
    {
        if(saved==null||saved.version!=1||saved.entries==null||saved.entries.size()>MAX_ENTRIES)return false;
        java.util.Set<Long> keys=new java.util.HashSet<>();
        for(SavedEntry e:saved.entries)
        {
            if(e==null||e.key<=0||e.key>Long.MAX_VALUE-MAX_ENTRIES||!keys.add(e.key)||e.id<=0||e.id>1_000_000||e.name==null||e.name.length()>100||e.quantity<1||e.quantity>Integer.MAX_VALUE
                ||e.x<0||e.x>65535||e.y<0||e.y>65535||e.plane<0||e.plane>3||e.world<0||e.world>65535||e.remaining< -1||e.remaining>MAX_TICKS||e.initialTicks<0||e.initialTicks>MAX_TICKS)return false;
            try{if(Tier.valueOf(e.tier)==Tier.EXTRA)return false;}catch(RuntimeException invalid){return false;}
        }
        return true;
    }
    void restore(Saved saved)
    {
        clear();if(!valid(saved))return;
        for(SavedEntry s:saved.entries)
        {
            if(saved.clean&&s.remaining==0)continue;
            Entry e=new Entry(s.key,s.id,s.name,Tier.valueOf(s.tier),new WorldPoint(s.x,s.y,s.plane));
            e.world=s.world;e.quantity=s.quantity;e.privateItem=true;e.initialTicks=s.initialTicks;e.notified=s.notified;
            e.certainty=saved.clean&&s.remaining>0?Certainty.REMEMBERED:Certainty.UNKNOWN;e.deadline=now+Math.max(0,s.remaining);
            entries.put(e.key,e);nextKey=Math.max(nextKey,e.key+1);
        }
    }
    private static int remaining(Entry e,long now){return e.certainty==null||e.certainty==Certainty.UNKNOWN?-1:(int)Math.max(0,Math.min(MAX_TICKS,e.deadline-now));}
    static String time(Row row,boolean ticks)
    {
        if(row.ticks<0)return "?";
        String prefix=row.certainty==Certainty.REMEMBERED?"~":"";
        if(ticks)return prefix+row.ticks+"t";
        int seconds=(int)Math.ceil(row.ticks*.6);return prefix+(seconds>=60?String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60):seconds+"s");
    }
}
