/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A bounded account-local observation journal. Phase changes and checklist clicks are separate evidence. */
final class MinigameRunProgress
{
    static final class View
    {
        static final View EMPTY=new View("","","","",0,0,0,"",null,"",List.of(),null);
        final String activityId,title,guideId,phaseId,scoreLabel,roundKey,profile;
        final long entrySequence,observedTicks;
        final int phaseChanges;
        final Integer scoreValue;
        final List<String> recentPhases;
        View(String activityId,String title,String guideId,String phaseId,long entrySequence,long observedTicks,int phaseChanges,
            String scoreLabel,Integer scoreValue,String roundKey,List<String> recentPhases)
        {this(activityId,title,guideId,phaseId,entrySequence,observedTicks,phaseChanges,scoreLabel,scoreValue,roundKey,recentPhases,null);}
        View(String activityId,String title,String guideId,String phaseId,long entrySequence,long observedTicks,int phaseChanges,
            String scoreLabel,Integer scoreValue,String roundKey,List<String> recentPhases,String profile)
        {
            this.activityId=activityId;this.title=title;this.guideId=guideId;this.phaseId=phaseId;this.entrySequence=entrySequence;
            this.observedTicks=observedTicks;this.phaseChanges=phaseChanges;this.scoreLabel=scoreLabel;this.scoreValue=scoreValue;
            this.profile=profile;this.roundKey=roundKey;this.recentPhases=Collections.unmodifiableList(new ArrayList<>(recentPhases));
        }
        boolean active(){return !activityId.isEmpty();}
    }
    private volatile View view=View.EMPTY;
    private long sequence;
    void clear(){view=View.EMPTY;}
    View view(){return view;}
    void observe(MinigameActivityCatalog.Snapshot snapshot){observe(snapshot,null);}
    void observe(MinigameActivityCatalog.Snapshot snapshot,String profile)
    {
        if(snapshot==null||snapshot.activity==null){clear();return;}
        View before=view;String activity=snapshot.activity.name().toLowerCase(java.util.Locale.ROOT);
        boolean entered=!activity.equals(before.activityId)||!java.util.Objects.equals(profile,before.profile);long ticks=entered?1:before.observedTicks+1;
        String phase=snapshot.steps.isEmpty()?"":snapshot.steps.get(0).title;
        List<String> recent=entered?new ArrayList<>():new ArrayList<>(before.recentPhases);
        boolean changed=!phase.equals(entered?"":before.phaseId);if(changed&&!phase.isEmpty()){recent.add(phase);if(recent.size()>8)recent.remove(0);}
        String scoreLabel="",round="";Integer score=null;
        for(MinigameActivityCatalog.Fact fact:snapshot.facts)
        {
            if(fact.label.equals("Wave"))round=fact.value;
            if(score==null&&(fact.label.equals("Points")||fact.label.equals("Round score")||fact.label.equals("Dream points")))
            {
                String text=fact.value.replace(",","").trim();
                // A clean displayed integer is a current score. Composite values or percentages stay unknown.
                if(text.matches("[0-9]{1,9}")){score=Integer.valueOf(text);scoreLabel=fact.label;}
            }
        }
        view=new View(activity,snapshot.title,snapshot.guideId,phase,entered?++sequence:before.entrySequence,ticks,
            entered?0:before.phaseChanges+(changed?1:0),scoreLabel,score,round,recent,profile);
    }

    static final class Learning
    {
        private MinigameLessonCatalog.Lesson lesson;
        private int index;
        private final Set<String> completed=new LinkedHashSet<>();
        private final List<Integer> undo=new ArrayList<>();
        synchronized void select(MinigameLessonCatalog.Lesson next)
        {if(lesson!=next){lesson=next;index=0;completed.clear();undo.clear();}}
        synchronized void clear(){lesson=null;index=0;completed.clear();undo.clear();}
        synchronized void follow(MinigameActivityCatalog.Snapshot snapshot)
        {
            MinigameLessonCatalog.Lesson next=MinigameLessonCatalog.get(snapshot.activity);if(next==null)return;select(next);
            int observed=MinigameLessonCatalog.observedIndex(next,snapshot);if(observed>=0)index=observed;
        }
        synchronized boolean completeAndAdvance()
        {
            if(lesson==null||index>=lesson.milestones.size())return false;
            undo.add(index);if(undo.size()>32)undo.remove(0);
            completed.add(lesson.milestones.get(index).id);index=Math.min(lesson.milestones.size(),index+1);return true;
        }
        synchronized void back(){if(lesson!=null)index=Math.max(0,index-1);}
        synchronized void next(){if(lesson!=null)index=Math.min(lesson.milestones.size(),index+1);}
        synchronized void undo()
        {if(lesson!=null&&!undo.isEmpty()){index=undo.remove(undo.size()-1);completed.remove(lesson.milestones.get(index).id);}}
        synchronized MinigameLessonCatalog.Lesson lesson(){return lesson;}
        synchronized int index(){return index;}
        synchronized int completed(){return completed.size();}
        synchronized boolean done(){return lesson!=null&&index>=lesson.milestones.size();}
        synchronized boolean canUndo(){return !undo.isEmpty();}
        synchronized String key(){return (lesson==null?"":lesson.activity)+"|"+index+"|"+completed.size();}
    }
}
