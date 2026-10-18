/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
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

/** One account-bound run identity and journal for every Runewarden workflow. */
@Singleton
public final class SharedActivitySession
{
    public enum Kind { TRAINING, QUEST, MINIGAME, FARMING, BOSS, SLAYER, OTHER }
    public enum State { IDLE, RUNNING, PAUSED, SUSPENDED, FINISHED }
    public static final class Context
    {
        public final Kind kind;
        public final String id, title, guideId, methodId;
        public final Skill skill;
        public final long targetXp;
        public Context(Kind kind, String id, String title, String guideId, String methodId, Skill skill, long targetXp)
        { this.kind=kind;this.id=id;this.title=title;this.guideId=guideId;this.methodId=methodId;this.skill=skill;this.targetXp=targetXp; }
        public boolean valid()
        { return kind!=null&&bounded(id,180,false)&&bounded(title,180,false)&&bounded(guideId,180,true)
            &&bounded(methodId,180,true)&&targetXp>=0&&targetXp<=200_000_000; }
        Context withGuide(String guide) { return new Context(kind,id,title,guide,methodId,skill,targetXp); }
    }
    public static final class Snapshot
    {
        public final String profile,runId;
        public final Context context,selected;
        public final State state;
        public final long elapsedMillis,activeMillis,revision;
        Snapshot(String profile,String runId,Context context,Context selected,State state,long elapsed,long active,long revision)
        { this.profile=profile;this.runId=runId;this.context=context;this.selected=selected;this.state=state;elapsedMillis=elapsed;activeMillis=active;this.revision=revision; }
        public boolean running(){return state==State.RUNNING;}
        public boolean current(){return profile!=null&&runId!=null;}
    }
    public static final class Counter
    {
        public final String key,label; public final long value;
        Counter(String key,String label,long value){this.key=key;this.label=label;this.value=value;}
    }
    public static final class Milestone
    {
        public final String id,title,module;public final long atMillis;
        Milestone(String id,String title,String module,long at){this.id=id;this.title=title;this.module=module;atMillis=at;}
    }
    /** Immutable, standalone record: no live client, configuration or other-character references. */
    public static final class RunRecord
    {
        public final String runId;public final Context context;public final State state;
        public final long startedAt,finishedAt,elapsedMillis,activeMillis,workingMillis,actions,trainingXp,grossLoot,costs;
        public final int distinctLoot,unpricedItems;
        public final boolean detected,partial;
        public final Map<Skill,Long> experience;
        public final Map<String,Counter> outcomes;
        public final Map<String,Long> lootSources;
        public final List<Milestone> milestones;
        RunRecord(String id,Context context,State state,long start,long finish,long elapsed,long active,Metrics metrics)
        {
            runId=id;this.context=context;this.state=state;startedAt=start;finishedAt=finish;elapsedMillis=elapsed;activeMillis=active;
            workingMillis=metrics.workingMillis;actions=metrics.actions;trainingXp=metrics.trainingXp;grossLoot=metrics.grossLoot;costs=metrics.costs;
            distinctLoot=metrics.distinctLoot;unpricedItems=metrics.unpricedItems;detected=metrics.detected;partial=metrics.partial;
            EnumMap<Skill,Long> xp=new EnumMap<>(Skill.class);xp.putAll(metrics.experience);experience=Collections.unmodifiableMap(xp);
            outcomes=Collections.unmodifiableMap(new LinkedHashMap<>(metrics.outcomes));lootSources=Collections.unmodifiableMap(new LinkedHashMap<>(metrics.lootSources));
            milestones=Collections.unmodifiableList(new ArrayList<>(metrics.milestones));
        }
        public long totalXp(){long value=0;for(long xp:experience.values())value=add(value,xp);return value;}
        public long netLoot(){return grossLoot-costs;}
        public double xpPerHour(){return activeMillis<60_000?0:totalXp()*3_600_000d/activeMillis;}
        public long netPerHour(){return activeMillis<60_000?0:Math.round(netLoot()*3_600_000d/activeMillis);}
    }
    public static final class JournalView
    {
        public final Snapshot session;public final RunRecord run;public final List<RunRecord> history;
        JournalView(Snapshot session,RunRecord run,List<RunRecord> history){this.session=session;this.run=run;this.history=history;}
    }
    private static final String GROUP="runewardenactivity",KEY="checkpointV1",METRICS="runMetricsV1",HISTORY="runHistoryV1";
    private static final int HISTORY_LIMIT=100;
    private static final class Saved
    {
        int version=1;String runId;Context context,selected,dismissedDetection;State state;long elapsedMillis,activeMillis;
        boolean valid(){return version==1&&bounded(runId,100,false)&&context!=null&&context.valid()&&(selected==null||selected.valid())&&(dismissedDetection==null||dismissedDetection.valid())
            &&state!=null&&elapsedMillis>=0&&activeMillis>=0&&activeMillis<=elapsedMillis&&elapsedMillis<=31_536_000_000L;}
    }
    private static final class Metrics
    {
        int version=1;String runId;long startedAt,finishedAt,workingMillis,actions,trainingXp,grossLoot,costs;
        int distinctLoot,unpricedItems;boolean detected,partial;
        Map<Skill,Long> experience=new EnumMap<>(Skill.class);
        Map<String,Counter> outcomes=new LinkedHashMap<>();Map<String,Long> lootSources=new LinkedHashMap<>();List<Milestone> milestones=new ArrayList<>();
        boolean valid()
        {
            if(version!=1||!bounded(runId,100,false)||startedAt<0||finishedAt<0||workingMillis<0||actions<0||trainingXp<0||grossLoot<0||costs<0||distinctLoot<0||unpricedItems<0
                ||experience==null||experience.size()>Skill.values().length||outcomes==null||outcomes.size()>32||lootSources==null||lootSources.size()>64||milestones==null||milestones.size()>64)return false;
            for(Map.Entry<Skill,Long> e:experience.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<0||e.getValue()>200_000_000)return false;
            for(Map.Entry<String,Counter> e:outcomes.entrySet())if(!bounded(e.getKey(),80,false)||e.getValue()==null||!e.getKey().equals(e.getValue().key)||!bounded(e.getValue().label,120,false)||e.getValue().value<0)return false;
            for(Map.Entry<String,Long> e:lootSources.entrySet())if(!bounded(e.getKey(),250,false)||e.getValue()==null||e.getValue()<0)return false;
            java.util.HashSet<String> ids=new java.util.HashSet<>();for(Milestone m:milestones)if(m==null||!bounded(m.id,160,false)||!bounded(m.title,180,false)||!bounded(m.module,80,true)||m.atMillis<0||!ids.add(m.id))return false;
            return true;
        }
    }
    private static final class Archived
    {String id;Context context;long elapsed,active;Metrics metrics;
        boolean valid(){return bounded(id,100,false)&&context!=null&&context.valid()&&elapsed>=0&&elapsed<=31_536_000_000L&&active>=0&&active<=elapsed&&metrics!=null&&id.equals(metrics.runId)&&metrics.valid();}}
    private static final class Journal
    {int version=1;List<Archived> runs=new ArrayList<>();boolean valid(){if(version!=1||runs==null||runs.size()>HISTORY_LIMIT)return false;
        java.util.HashSet<String> ids=new java.util.HashSet<>();for(Archived run:runs)if(run==null||!run.valid()||!ids.add(run.id))return false;return true;}}
    private static final class Preferences{int version=1;boolean detect;boolean valid(){return version==1;}}
    private final Client client;private final ConfigManager config;private final RunewardenDataStore store;private final LongSupplier clock;
    private boolean started,suspendedWasRunning,online;private String profile,runId,lastDetection;private Context context,selected,lastDetectedContext,dismissedDetection;private State state=State.IDLE;
    private long elapsed,active,lastAt,revision;private int checkpointTicks;private Metrics metrics=new Metrics();private Journal journal=new Journal();
    private Preferences preferences=new Preferences();private volatile Snapshot reading=new Snapshot(null,null,null,null,State.IDLE,0,0,0);
    private volatile RunRecord currentRecord;private volatile List<RunRecord> history=Collections.emptyList();
    private volatile JournalView journalView=new JournalView(reading,null,Collections.emptyList());
    private java.util.function.Consumer<Snapshot> finishAction=ignored->{};
    private java.util.function.Consumer<Snapshot> startAction=ignored->{};
    public synchronized void setFinishAction(java.util.function.Consumer<Snapshot> action){finishAction=action==null?ignored->{}:action;}
    public synchronized void setStartAction(java.util.function.Consumer<Snapshot> action){startAction=action==null?ignored->{}:action;}
    @Inject public SharedActivitySession(Client client,ConfigManager config,RunewardenDataStore store){this(client,config,store,System::currentTimeMillis);}
    SharedActivitySession(Client client,ConfigManager config,RunewardenDataStore store,LongSupplier clock){this.client=client;this.config=config;this.store=store;this.clock=clock;}
    public synchronized void start(){preferences=store.read(GROUP,null,"preferencesV1",1,Preferences.class,Preferences::valid,Preferences::new);started=true;clear();}
    public synchronized void stop(){checkpoint();started=false;reset();}
    public synchronized void clear(){checkpoint();reset();}
    private void reset(){online=false;profile=runId=lastDetection=null;context=selected=lastDetectedContext=dismissedDetection=null;state=State.IDLE;elapsed=active=lastAt=0;checkpointTicks=0;metrics=new Metrics();journal=new Journal();history=Collections.emptyList();revision++;publish();}
    public synchronized void onGameTick()
    {
        if(!started)return;String next=config.getRSProfileKey();
        if(client.getGameState()!=GameState.LOGGED_IN||client.getLocalPlayer()==null||next==null||next.isEmpty()){suspend();return;}
        long now=clock.getAsLong();online=true;if(!next.equals(profile))
        {
            checkpoint();reset();online=true;profile=next;journal=store.read(GROUP,profile,HISTORY,1,Journal.class,Journal::valid,Journal::new);publishHistory();
            Saved saved=store.read(GROUP,profile,KEY,1,Saved.class,Saved::valid,()->null);
            if(saved!=null)
            {
                runId=saved.runId;context=saved.context;selected=saved.selected;elapsed=saved.elapsedMillis;active=saved.activeMillis;state=saved.state==State.FINISHED?State.FINISHED:State.PAUSED;
                dismissedDetection=saved.dismissedDetection!=null?saved.dismissedDetection:state==State.FINISHED?context:null;
                Metrics restored=store.read(GROUP,profile,METRICS,1,Metrics.class,Metrics::valid,()->null);
                if(restored!=null&&runId.equals(restored.runId))metrics=restored;
                else{metrics=new Metrics();metrics.runId=runId;metrics.startedAt=now;metrics.partial=true;}
            }
            lastAt=now;revision++;publish();return;
        }
        if(state==State.SUSPENDED){state=suspendedWasRunning?State.RUNNING:State.PAUSED;lastAt=now;revision++;}
        accrue(now);if(++checkpointTicks>=50){checkpointTicks=0;checkpoint();}publish();
    }
    /** Browsing and automatic context hints prepare the next run; they never replace this run. */
    public synchronized void select(Context next){if(next==null||!next.valid()||!sameProfileOrUnbound())return;selected=next;revision++;publish();}
    public synchronized void attachGuide(String guide){if(!bounded(guide,180,true)||!sameProfileOrUnbound())return;if(selected!=null)selected=selected.withGuide(guide);if(context!=null&&context.guideId==null)context=context.withGuide(guide);revision++;publish();}
    public synchronized boolean startActivity(Context next)
    {
        if(!currentProfile()||!online||next==null||!next.valid())return false;
        if(runId!=null&&state!=State.FINISHED){accrue(clock.getAsLong());archive();}
        dismissedDetection=null;selected=context=next;runId=UUID.randomUUID().toString();elapsed=active=0;state=State.RUNNING;lastAt=clock.getAsLong();
        metrics=new Metrics();metrics.runId=runId;metrics.startedAt=lastAt;revision++;checkpoint();publish();startAction.accept(reading);return true;
    }
    public synchronized boolean startSelected(){return startActivity(selected);}
    /** Native detectors may suggest a run; only the player's opted-in idle state can start it. */
    public synchronized boolean offerDetected(Context next)
    {
        if(next==null){lastDetection=null;lastDetectedContext=null;return false;}if(!next.valid()||!currentProfile())return false;
        lastDetectedContext=next;
        boolean fresh=!next.id.equals(lastDetection);lastDetection=next.id;
        if(state==State.IDLE||state==State.FINISHED)
        {
            if(selected==null||!selected.id.equals(next.id))select(next);
            // End run is an explicit dismissal. A temporary bank/scene gap must not make the
            // same activity a fresh automatic run and reopen its guide on the next observation.
            if(sameActivity(dismissedDetection,next))return false;
            dismissedDetection=null;
            if(fresh&&preferences.detect&&startActivity(next)){metrics.detected=true;checkpoint();publish();return true;}
        }
        return false;
    }
    private static boolean sameActivity(Context previous,Context next)
    {
        if(previous==null||next==null)return false;
        if(previous.kind==Kind.SLAYER||next.kind==Kind.SLAYER)return previous.kind==next.kind&&previous.id.equals(next.id);
        return previous.guideId!=null&&next.guideId!=null?previous.guideId.equals(next.guideId):previous.id.equals(next.id);
    }
    public synchronized boolean autoDetect(){return preferences.detect;}
    public synchronized void setAutoDetect(boolean enabled){if(enabled&&!preferences.detect)lastDetection=null;preferences.detect=enabled;store.write(GROUP,null,"preferencesV1",1,preferences);}
    public synchronized void pause(){if(currentProfile()&&state==State.RUNNING){accrue(clock.getAsLong());state=State.PAUSED;revision++;checkpoint();publish();}}
    public synchronized void resume(){if(currentProfile()&&state==State.PAUSED){lastAt=clock.getAsLong();state=State.RUNNING;revision++;checkpoint();publish();}}
    public synchronized void togglePause(){if(state==State.RUNNING)pause();else if(state==State.PAUSED)resume();}
    public synchronized void suspend(){online=false;if(profile!=null&&(state==State.RUNNING||state==State.PAUSED)){suspendedWasRunning=state==State.RUNNING;state=State.SUSPENDED;lastAt=0;revision++;checkpoint();publish();}}
    public synchronized void finish()
    {
        if(!currentProfile())return;
        dismissedDetection=lastDetectedContext!=null?lastDetectedContext:context!=null?context:selected;
        if(context!=null&&state!=State.FINISHED){accrue(clock.getAsLong());state=State.FINISHED;metrics.finishedAt=clock.getAsLong();archive();revision++;checkpoint();publish();}
        // End-run also dismisses a browsed guide when no measured run was started.
        finishAction.accept(reading);
    }
    public JournalView journalView(){JournalView v=journalView;return v.session.profile!=null&&!v.session.profile.equals(config.getRSProfileKey())?new JournalView(new Snapshot(null,null,null,null,State.IDLE,0,0,v.session.revision),null,Collections.emptyList()):v;}
    public Snapshot snapshot(){return journalView().session;}
    public RunRecord currentRecord(){return journalView().run;}
    public List<RunRecord> history(){return journalView().history;}
    public synchronized void observeXp(String expectedRun,Skill skill,long amount)
    {if(!observing(expectedRun)||skill==null||amount<=0)return;metrics.experience.merge(skill,Math.min(200_000_000,amount),(a,b)->Math.min(200_000_000,add(a,b)));publish();}
    public synchronized void observeOutcome(String key,String label,long delta)
    {if(!observing(runId)||!bounded(key,80,false)||!bounded(label,120,false)||delta<=0)return;Counter old=metrics.outcomes.get(key);if(old==null&&metrics.outcomes.size()>=32)return;metrics.outcomes.put(key,new Counter(key,label,add(old==null?0:old.value,delta)));revision++;publish();}
    public synchronized boolean reachMilestone(String id,String title,String module)
    {if(!observing(runId)||!bounded(id,160,false)||!bounded(title,180,false)||!bounded(module,80,true)||metrics.milestones.size()>=64)return false;
        for(Milestone m:metrics.milestones)if(m.id.equals(id))return false;metrics.milestones.add(new Milestone(id,title,module,clock.getAsLong()));revision++;checkpoint();publish();return true;}
    synchronized void reportTraining(String expectedRun,long xp,long actions,long workingMillis)
    {if(!observing(expectedRun))return;metrics.trainingXp=Math.max(0,xp);metrics.actions=Math.max(0,actions);metrics.workingMillis=Math.max(0,workingMillis);publish();}
    synchronized void reportLoot(String expectedRun,long gross,long cost,int distinct,int unpriced,Map<String,Long> sources)
    {if(!currentProfile()||!Objects.equals(runId,expectedRun)||runId==null||state==State.FINISHED||state==State.SUSPENDED)return;
        metrics.grossLoot=Math.max(0,gross);metrics.costs=Math.max(0,cost);metrics.distinctLoot=Math.max(0,distinct);metrics.unpricedItems=Math.max(0,unpriced);metrics.lootSources.clear();
        if(sources!=null)for(Map.Entry<String,Long> e:sources.entrySet())if(metrics.lootSources.size()<64&&bounded(e.getKey(),250,false)&&e.getValue()!=null&&e.getValue()>=0)metrics.lootSources.put(e.getKey(),e.getValue());publish();}
    public synchronized void removeHistory(String expectedProfile,String id)
    {if(!currentProfile()||!profile.equals(expectedProfile))return;if(journal.runs.removeIf(r->r.id.equals(id))){saveJournal();publishHistory();revision++;publish();}}
    private boolean currentProfile(){return started&&profile!=null&&profile.equals(config.getRSProfileKey());}
    private boolean sameProfileOrUnbound(){return profile==null||profile.equals(config.getRSProfileKey());}
    private boolean observing(String expected){return currentProfile()&&online&&state==State.RUNNING&&runId!=null&&runId.equals(expected);}
    private void accrue(long now){if(lastAt>0&&runId!=null&&(state==State.RUNNING||state==State.PAUSED)){long delta=Math.max(0,Math.min(2_000,now-lastAt));elapsed+=delta;if(state==State.RUNNING)active+=delta;}lastAt=now;}
    private void archive()
    {
        if(runId==null||context==null)return;Archived r=new Archived();r.id=runId;r.context=context;r.elapsed=elapsed;r.active=active;
        // The old mutable metrics object belongs only to this archive after a new run starts.
        r.metrics=metrics;if(r.metrics.finishedAt==0)r.metrics.finishedAt=clock.getAsLong();journal.runs.removeIf(a->a.id.equals(runId));journal.runs.add(0,r);
        while(journal.runs.size()>HISTORY_LIMIT||journal.runs.size()>1&&journalWeight()>900_000)journal.runs.remove(journal.runs.size()-1);saveJournal();publishHistory();
    }
    private long journalWeight()
    {
        long size=64;for(Archived r:journal.runs)
        {size+=1200+textWeight(r.id)+textWeight(r.context.id)+textWeight(r.context.title)+textWeight(r.context.guideId)+textWeight(r.context.methodId)+r.metrics.experience.size()*80L;
            for(Counter c:r.metrics.outcomes.values())size+=100+textWeight(c.key)+textWeight(c.label);
            for(Map.Entry<String,Long> e:r.metrics.lootSources.entrySet())size+=80+textWeight(e.getKey());
            for(Milestone m:r.metrics.milestones)size+=140+textWeight(m.id)+textWeight(m.title)+textWeight(m.module);}
        return size;
    }
    private static long textWeight(String value){return value==null?4:value.length()*6L+2;}
    private void saveJournal(){if(profile!=null)store.write(GROUP,profile,HISTORY,1,journal);}
    private void publishHistory(){List<RunRecord> copies=new ArrayList<>();for(Archived r:journal.runs)copies.add(new RunRecord(r.id,r.context,State.FINISHED,r.metrics.startedAt,r.metrics.finishedAt,r.elapsed,r.active,r.metrics));history=Collections.unmodifiableList(copies);}
    private void checkpoint()
    {if(profile==null||runId==null||context==null)return;Saved s=new Saved();s.runId=runId;s.context=context;s.selected=selected;s.dismissedDetection=dismissedDetection;s.state=state;s.elapsedMillis=elapsed;s.activeMillis=active;store.write(GROUP,profile,KEY,1,s);store.write(GROUP,profile,METRICS,1,metrics);}
    private void publish(){reading=new Snapshot(profile,runId,context,selected,state,elapsed,active,revision);currentRecord=context==null?null:new RunRecord(runId,context,state,metrics.startedAt,metrics.finishedAt,elapsed,active,metrics);journalView=new JournalView(reading,currentRecord,history);}
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    private static boolean bounded(String text,int length,boolean nullable){return text==null?nullable:!text.isEmpty()&&text.length()<=length&&text.indexOf('\n')<0&&text.indexOf('\r')<0;}
}
