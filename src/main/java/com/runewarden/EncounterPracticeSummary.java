/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Recent practice metrics count only completed clears/retries; voluntarily stopped attempts remain separate. */
final class EncounterPracticeSummary
{
    final int clears,completed,stopped;
    final double clearRate;
    final Long bestClearMillis,medianClearMillis;
    private EncounterPracticeSummary(int clears,int completed,int stopped,Long best,Long median)
    {this.clears=clears;this.completed=completed;this.stopped=stopped;clearRate=completed==0?0:(double)clears/completed;bestClearMillis=best;medianClearMillis=median;}
    static EncounterPracticeSummary of(List<EncounterPracticeSession.Attempt> attempts)
    {
        List<Long> clearTimes=new ArrayList<>();int completed=0,stopped=0;
        if(attempts!=null)for(EncounterPracticeSession.Attempt a:attempts)
        {
            if(a==null||a.durationMillis<0||!EncounterPracticeSession.validOutcome(a.outcome))continue;
            if("Stopped".equals(a.outcome)){stopped++;continue;}completed++;
            if("Clear".equals(a.outcome))clearTimes.add(a.durationMillis);
        }
        Collections.sort(clearTimes);int n=clearTimes.size();
        Long median=null;if(n>0)median=n%2==1?clearTimes.get(n/2):clearTimes.get(n/2-1)+(clearTimes.get(n/2)-clearTimes.get(n/2-1))/2;
        return new EncounterPracticeSummary(n,completed,stopped,n==0?null:clearTimes.get(0),median);
    }
}
