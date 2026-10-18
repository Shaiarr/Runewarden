/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;

/** One recoverable, account-bound clock shared by the HUD's current measurements. */
@Singleton
public final class SharedActivitySession
{
    // Retain the persisted context shape so previous current-session saves remain readable.
    public enum Kind { TRAINING, QUEST, MINIGAME, FARMING, BOSS, SLAYER, OTHER }
    public enum State { IDLE, RUNNING, PAUSED, SUSPENDED, FINISHED }
    public static final class Context
    {
        public final Kind kind;
        public final String id,title,guideId,methodId;
        public final Skill skill;
        public final long targetXp;
        public Context(Kind kind,String id,String title,String guideId,String methodId,Skill skill,long targetXp)
        {this.kind=kind;this.id=id;this.title=title;this.guideId=guideId;this.methodId=methodId;this.skill=skill;this.targetXp=targetXp;}
        boolean valid(){return kind!=null&&bounded(id,180,false)&&bounded(title,180,false)&&bounded(guideId,180,true)&&bounded(methodId,180,true)&&targetXp>=0&&targetXp<=200_000_000;}
    }
    public static final class Snapshot
    {
        public final String profile,runId;
        public final Context context,selected;
        public final State state;
        public final long elapsedMillis,activeMillis,revision;
        Snapshot(String profile,String runId,Context context,Context selected,State state,long elapsed,long active,long revision)
        {this.profile=profile;this.runId=runId;this.context=context;this.selected=selected;this.state=state;elapsedMillis=elapsed;activeMillis=active;this.revision=revision;}
        public boolean running(){return state==State.RUNNING;}
        public boolean current(){return profile!=null&&runId!=null;}
    }
    public static final class RunRecord
    {
        public final String runId;public final Context context;public final State state;
        public final long startedAt,finishedAt,elapsedMillis,activeMillis,workingMillis,actions,trainingXp,grossLoot,costs;
        public final int distinctLoot,unpricedItems;
        public final Map<Skill,Long> experience;
        public final Map<String,Long> lootSources;
        RunRecord(Snapshot s,Metrics m)
        {
            runId=s.runId;context=s.context;state=s.state;startedAt=m.startedAt;finishedAt=m.finishedAt;
            elapsedMillis=s.elapsedMillis;activeMillis=s.activeMillis;workingMillis=m.workingMillis;actions=m.actions;trainingXp=m.trainingXp;
            grossLoot=m.grossLoot;costs=m.costs;distinctLoot=m.distinctLoot;unpricedItems=m.unpricedItems;
            EnumMap<Skill,Long> xp=new EnumMap<>(Skill.class);xp.putAll(m.experience);experience=Collections.unmodifiableMap(xp);
            lootSources=Collections.unmodifiableMap(new LinkedHashMap<>(m.lootSources));
        }
        public long totalXp(){long total=0;for(long xp:experience.values())total=add(total,xp);return total;}
        public long netLoot(){return grossLoot-costs;}
    }
    private static final String GROUP="runewardenactivity",KEY="checkpointV1",METRICS="runMetricsV1";
    private static final class Saved
    {
        int version=1;String runId;Context context,selected;State state;long elapsedMillis,activeMillis;
        boolean valid(){return version==1&&bounded(runId,100,false)&&context!=null&&context.valid()&&(selected==null||selected.valid())&&state!=null&&elapsedMillis>=0&&elapsedMillis<=31_536_000_000L&&activeMillis>=0&&activeMillis<=elapsedMillis;}
    }
    private static final class Metrics
    {
        int version=1;String runId;long startedAt,finishedAt,workingMillis,actions,trainingXp,grossLoot,costs;
        int distinctLoot,unpricedItems;
        Map<Skill,Long> experience=new EnumMap<>(Skill.class);Map<String,Long> lootSources=new LinkedHashMap<>();
        boolean valid()
        {
            if(version!=1||!bounded(runId,100,false)||startedAt<0||finishedAt<0||workingMillis<0||actions<0||trainingXp<0||grossLoot<0||costs<0||distinctLoot<0||unpricedItems<0||experience==null||experience.size()>Skill.values().length||lootSources==null||lootSources.size()>64)return false;
            for(Map.Entry<Skill,Long> e:experience.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<0||e.getValue()>200_000_000)return false;
            for(Map.Entry<String,Long> e:lootSources.entrySet())if(!bounded(e.getKey(),250,false)||e.getValue()==null||e.getValue()<0)return false;
            return true;
        }
    }
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final LongSupplier clock;
    private boolean started,online,resumeAfterHop;private volatile boolean configBound;private volatile Long configProfileId;private String profile,runId;private Context context,selected;private State state=State.IDLE;
    private long elapsed,active,lastAt,revision;private int checkpointTicks;private Metrics metrics=new Metrics();
    private volatile Snapshot reading=empty(0);private volatile RunRecord record;
    @Inject public SharedActivitySession(Client client,ConfigManager config,RunewardenDataStore store){this(client,config,store,System::currentTimeMillis);}
    SharedActivitySession(Client client,ConfigManager config,RunewardenDataStore store,LongSupplier clock){this.client=client;this.config=config;this.store=store;this.clock=clock;}
    public synchronized void start(){started=true;clear();}
    public synchronized void stop(){checkpoint();started=false;unbind();}
    public synchronized void clear(){checkpoint();unbind();}
    public synchronized void onConfigProfileChanged(){unbind();}
    private void unbind(){configBound=false;configProfileId=null;online=false;profile=runId=null;context=selected=null;state=State.IDLE;elapsed=active=lastAt=0;checkpointTicks=0;metrics=new Metrics();revision++;publish();}
    public synchronized void onGameTick()
    {
        if(!started)return;if(!configCurrent())unbind();String next=config.getRSProfileKey();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||next==null||next.isEmpty()){suspend();return;}
        long now=clock.getAsLong();
        if(!next.equals(profile))
        {
            checkpoint();unbind();profile=next;configProfileId=nativeConfigProfileId();configBound=true;online=true;
            Saved saved=store.read(GROUP,profile,KEY,1,Saved.class,Saved::valid,()->null);
            if(saved!=null)
            {
                runId=saved.runId;context=saved.context;selected=saved.selected;elapsed=saved.elapsedMillis;active=saved.activeMillis;
                state=saved.state==State.FINISHED?State.FINISHED:State.PAUSED;
                Metrics restored=store.read(GROUP,profile,METRICS,1,Metrics.class,Metrics::valid,()->null);
                metrics=restored!=null&&runId.equals(restored.runId)?restored:new Metrics();metrics.runId=runId;
            }
            lastAt=now;revision++;publish();return;
        }
        online=true;if(state==State.SUSPENDED){state=resumeAfterHop?State.RUNNING:State.PAUSED;lastAt=now;revision++;}
        accrue(now);if(++checkpointTicks>=50){checkpointTicks=0;checkpoint();}publish();
    }
    /** Begin plain session tracking without a guide or activity selection. */
    public synchronized boolean begin(){return startActivity(new Context(Kind.OTHER,"session","Session loot",null,null,null,0));}
    public synchronized boolean resetCurrent(){return startActivity(context!=null?context:selected!=null?selected:new Context(Kind.OTHER,"session","Session loot",null,null,null,0));}
    public synchronized void select(Context next){if(next!=null&&next.valid()&&currentProfile()){selected=next;revision++;publish();}}
    public synchronized boolean startActivity(Context next)
    {
        if(!currentProfile()||!online||next==null||!next.valid())return false;
        selected=context=next;runId=UUID.randomUUID().toString();elapsed=active=0;state=State.RUNNING;lastAt=clock.getAsLong();
        metrics=new Metrics();metrics.runId=runId;metrics.startedAt=lastAt;revision++;checkpoint();publish();return true;
    }
    public synchronized boolean startSelected(){return startActivity(selected);}
    public synchronized void pause(){if(currentProfile()&&state==State.RUNNING){accrue(clock.getAsLong());state=State.PAUSED;revision++;checkpoint();publish();}}
    public synchronized void resume(){if(currentProfile()&&online&&state==State.PAUSED){lastAt=clock.getAsLong();state=State.RUNNING;revision++;checkpoint();publish();}}
    public synchronized void togglePause(){if(state==State.RUNNING)pause();else if(state==State.PAUSED)resume();}
    public synchronized void suspend()
    {
        if(profile!=null&&(state==State.RUNNING||state==State.PAUSED))
        {accrue(clock.getAsLong());resumeAfterHop=state==State.RUNNING;state=State.SUSPENDED;lastAt=0;revision++;checkpoint();publish();}
        online=false;
    }
    public synchronized void finish(){if(currentProfile()&&runId!=null&&state!=State.FINISHED){accrue(clock.getAsLong());state=State.FINISHED;metrics.finishedAt=clock.getAsLong();revision++;checkpoint();publish();}}
    public Snapshot snapshot(){Snapshot s=reading;return !configCurrent()||s.profile!=null&&!s.profile.equals(config.getRSProfileKey())?empty(s.revision):s;}
    public RunRecord currentRecord(){RunRecord r=record;Snapshot s=snapshot();return r!=null&&s.current()&&Objects.equals(r.runId,s.runId)?r:null;}
    public synchronized void observeXp(String expectedRun,Skill skill,long amount)
    {if(observing(expectedRun)&&skill!=null&&amount>0){metrics.experience.merge(skill,Math.min(200_000_000,amount),(a,b)->Math.min(200_000_000,add(a,b)));publish();}}
    synchronized void reportTraining(String expectedRun,long xp,long actions,long workingMillis)
    {if(observing(expectedRun)){metrics.trainingXp=Math.max(0,xp);metrics.actions=Math.max(0,actions);metrics.workingMillis=Math.max(0,workingMillis);publish();}}
    synchronized void reportLoot(String expectedRun,long gross,long cost,int distinct,int unpriced,Map<String,Long> sources)
    {
        if(!currentProfile()||!Objects.equals(runId,expectedRun)||runId==null||state==State.SUSPENDED)return;
        metrics.grossLoot=Math.max(0,gross);metrics.costs=Math.max(0,cost);metrics.distinctLoot=Math.max(0,distinct);metrics.unpricedItems=Math.max(0,unpriced);metrics.lootSources.clear();
        if(sources!=null)for(Map.Entry<String,Long> e:sources.entrySet())if(metrics.lootSources.size()<64&&bounded(e.getKey(),250,false)&&e.getValue()!=null&&e.getValue()>=0)metrics.lootSources.put(e.getKey(),e.getValue());publish();
    }
    private boolean currentProfile(){return started&&configCurrent()&&profile!=null&&profile.equals(config.getRSProfileKey());}
    private boolean observing(String expected){return currentProfile()&&online&&state==State.RUNNING&&runId!=null&&runId.equals(expected);}
    private void accrue(long now){if(lastAt>0&&online&&configCurrent()&&Objects.equals(profile,config.getRSProfileKey())&&runId!=null&&(state==State.RUNNING||state==State.PAUSED)){long delta=Math.max(0,Math.min(2000,now-lastAt));elapsed=add(elapsed,delta);if(state==State.RUNNING)active=add(active,delta);}lastAt=now;}
    private void checkpoint()
    {
        if(!configCurrent()||profile==null||runId==null||context==null)return;accrue(clock.getAsLong());Saved s=new Saved();s.runId=runId;s.context=context;s.selected=selected;s.state=state;s.elapsedMillis=elapsed;s.activeMillis=active;
        store.write(GROUP,profile,KEY,1,s);store.write(GROUP,profile,METRICS,1,metrics);
    }
    private Long nativeConfigProfileId(){ConfigProfile selected=config.getProfile();return selected==null?null:selected.getId();}
    private boolean configCurrent(){return !configBound||Objects.equals(configProfileId,nativeConfigProfileId());}
    private void publish(){reading=new Snapshot(profile,runId,context,selected,state,elapsed,active,revision);record=context==null?null:new RunRecord(reading,metrics);}
    private static Snapshot empty(long revision){return new Snapshot(null,null,null,null,State.IDLE,0,0,revision);}
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    private static boolean bounded(String text,int length,boolean nullable){return text==null?nullable:!text.isEmpty()&&text.length()<=length&&text.indexOf('\n')<0&&text.indexOf('\r')<0;}
}
