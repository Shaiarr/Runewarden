/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/** Player-owned preparation and recovery acknowledgements, separate from observed game state. */
final class EncounterPreparation
{
    boolean[] ready=new boolean[4];
    String focus="";
    boolean reviewPending,recoveryPending;
    int readyCount(){int count=0;if(ready!=null)for(int i=0;i<Math.min(4,ready.length);i++)if(ready[i])count++;return count;}
    void finish(String outcome)
    {reviewPending=true;recoveryPending="Retry".equals(outcome);}
    void resetForNext(){ready=new boolean[4];reviewPending=false;recoveryPending=false;}
    static EncounterPreparation clean(EncounterPreparation saved)
    {
        EncounterPreparation next=new EncounterPreparation();if(saved==null)return next;
        if(saved.ready!=null)System.arraycopy(saved.ready,0,next.ready,0,Math.min(4,saved.ready.length));
        next.focus=EncounterPracticeSession.boundedFocus(saved.focus);next.reviewPending=saved.reviewPending;next.recoveryPending=saved.recoveryPending;return next;
    }
}
