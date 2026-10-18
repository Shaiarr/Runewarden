/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterPreparationTest
{
    @Test public void preparationResetsPerAttemptWhileRetainingThePlayersPracticeGoal()
    {
        EncounterPreparation p=new EncounterPreparation();p.focus="Keep the exit clear";Arrays.fill(p.ready,true);assertEquals(4,p.readyCount());
        p.finish("Retry");assertTrue(p.reviewPending);assertTrue(p.recoveryPending);p.resetForNext();
        assertEquals(0,p.readyCount());assertEquals("Keep the exit clear",p.focus);assertFalse(p.reviewPending);assertFalse(p.recoveryPending);
    }
    @Test public void oldOrMalformedPreparationDataIsBoundedAndCopied()
    {
        assertEquals(0,EncounterPreparation.clean(null).readyCount());EncounterPreparation p=new EncounterPreparation();p.ready=new boolean[]{true};p.focus="x".repeat(500);
        EncounterPreparation clean=EncounterPreparation.clean(p);assertEquals(4,clean.ready.length);assertEquals(1,clean.readyCount());assertEquals(120,clean.focus.length());
        p.ready[0]=false;assertTrue(clean.ready[0]);
    }
    @Test public void attemptStoresPreparationContextRatherThanChangingWithTheNextAttempt()
    {
        AtomicLong clock=new AtomicLong();EncounterPracticeSession session=new EncounterPracticeSession(clock::get);
        session.start(2,"Practice acid movement",3);clock.addAndGet(12000);EncounterPracticeSession.Attempt a=session.finish("Retry","Too late");
        assertEquals(2,a.phase);assertEquals(3,a.prepared);assertEquals("Practice acid movement",a.focus);assertEquals(12000,a.durationMillis);
        session.start(1,"Other goal",1);session.finish("Clear","");assertEquals("Practice acid movement",session.attempts().get(0).focus);
    }
    @Test public void historySnapshotCannotMutateTheSessionAndInvalidOutcomesDoNotFinishIt()
    {
        EncounterPracticeSession s=new EncounterPracticeSession(()->100);s.start();
        try{s.finish("Bogus","");fail();}catch(IllegalArgumentException expected){}assertTrue(s.active());
        s.finish("Clear","");s.attempts().get(0).note="changed";assertEquals("",s.attempts().get(0).note);
    }
    @Test public void reviewCountsStopsSeparatelyAndComputesBestAndMedianClears()
    {
        EncounterPracticeSummary s=EncounterPracticeSummary.of(List.of(a("Clear",10000),a("Retry",5000),a("Clear",20000),a("Stopped",500),a("Clear",15000),a("Clear",18000)));
        assertEquals(4,s.clears);assertEquals(5,s.completed);assertEquals(1,s.stopped);assertEquals(.8,s.clearRate,1e-12);
        assertEquals(Long.valueOf(10000),s.bestClearMillis);assertEquals(Long.valueOf(16500),s.medianClearMillis);
    }
    @Test public void emptyOrNoClearReviewDoesNotInventACompletionTime()
    {
        EncounterPracticeSummary s=EncounterPracticeSummary.of(List.of(a("Retry",5000),a("Stopped",300)));
        assertNull(s.bestClearMillis);assertNull(s.medianClearMillis);assertEquals(0,s.clearRate,0);
        s=EncounterPracticeSummary.of(null);assertEquals(0,s.completed);assertNull(s.medianClearMillis);
    }
    @Test public void legacyJournalRetainsAttemptsAndNewPreparationAcrossSerialization()
    {
        Gson gson=new Gson();GuideCatalog catalog=new GuideCatalog(gson);EncounterPracticeTool.Journal journal=new EncounterPracticeTool.Journal();
        EncounterPracticeTool.Record r=new EncounterPracticeTool.Record();r.preparation.focus="Practice exits";r.preparation.ready[2]=true;r.preparation.finish("Retry");r.attempts.add(a("Retry",12000));
        journal.encounters.put("encounter:vorkath",r);EncounterPracticeTool.Record restored=EncounterPracticeTool.loadJournal(gson.toJson(journal),gson,catalog).encounters.get("encounter:vorkath");
        assertEquals("Practice exits",restored.preparation.focus);assertEquals(1,restored.preparation.readyCount());assertTrue(restored.preparation.recoveryPending);assertEquals(1,restored.attempts.size());
        EncounterPracticeTool.Record legacy=EncounterPracticeTool.loadJournal("{\"encounters\":{\"encounter:vorkath\":{\"note\":\"old\"}}}",gson,catalog).encounters.get("encounter:vorkath");assertNotNull(legacy.preparation);assertEquals(0,legacy.preparation.readyCount());
    }
    @Test public void restoredPausedAttemptRetainsItsOriginalFocusAndReadiness()
    {
        AtomicLong clock=new AtomicLong();EncounterPracticeSession s=new EncounterPracticeSession(clock::get);
        s.restorePaused(5000,3,"Practice the exit",4);clock.addAndGet(900000);s.resume();clock.addAndGet(1000);
        EncounterPracticeSession.Attempt a=s.finish("Clear","");assertEquals(6000,a.durationMillis);assertEquals(3,a.phase);assertEquals(4,a.prepared);assertEquals("Practice the exit",a.focus);
    }
    @Test public void legacyAttemptWithoutContextDoesNotInventAPhaseOrReadiness()
    {
        EncounterPracticeSession.Attempt legacy=new Gson().fromJson("{\"outcome\":\"Clear\",\"durationMillis\":1000}",EncounterPracticeSession.Attempt.class);
        EncounterPracticeSession.Attempt clean=EncounterPracticeSession.copy(legacy);assertEquals(-1,clean.phase);assertEquals(0,clean.prepared);assertEquals("",clean.focus);
    }
    private static EncounterPracticeSession.Attempt a(String outcome,long time){return new EncounterPracticeSession.Attempt(outcome,time,"");}
}
