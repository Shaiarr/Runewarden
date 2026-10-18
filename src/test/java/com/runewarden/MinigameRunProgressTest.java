/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameRunProgressTest
{
    @Test public void allTwentyActivitiesHaveOrderedDistinctUsableLessons()
    {
        assertEquals(20,MinigameLessonCatalog.all().size());int steps=0;
        for(MinigameLessonCatalog.Lesson lesson:MinigameLessonCatalog.all())
        {assertTrue(lesson.source.startsWith("https://oldschool.runescape.wiki/w/"));assertTrue(lesson.milestones.size()>=5);
            assertEquals(lesson.milestones.size(),lesson.milestones.stream().map(s->s.id).distinct().count());
            for(MinigameLessonCatalog.Milestone step:lesson.milestones){assertFalse(step.title.isBlank());assertFalse(step.instruction.isBlank());}steps+=lesson.milestones.size();}
        assertEquals(122,steps);
    }
    @Test public void learningCompletionAdvancesAndUndoReturnsToTheActualCompletedMilestone()
    {
        MinigameRunProgress.Learning learning=new MinigameRunProgress.Learning();learning.select(MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.WINTERTODT));
        assertEquals(0,learning.index());learning.completeAndAdvance();assertEquals(1,learning.index());assertEquals(1,learning.completed());
        learning.next();learning.completeAndAdvance();assertEquals(3,learning.index());learning.undo();assertEquals(2,learning.index());assertEquals(1,learning.completed());
        learning.undo();assertEquals(0,learning.index());assertEquals(0,learning.completed());
    }
    @Test public void observedPhaseFollowingDoesNotAssertThatUnobservedMilestonesWereCompleted()
    {
        MinigameRunProgress.Learning learning=new MinigameRunProgress.Learning();learning.follow(snapshot("Feed your kindling","325"));
        assertEquals("feed",learning.lesson().milestones.get(learning.index()).id);assertEquals(0,learning.completed());
        learning.clear();assertNull(learning.lesson());assertEquals(0,learning.completed());
    }
    @Test public void observationJournalCountsChangesOnceAndBoundsHistoryWhileScoreRemainsCurrentState()
    {
        MinigameRunProgress run=new MinigameRunProgress();run.observe(snapshot("Gather roots","10"));run.observe(snapshot("Gather roots","10"));
        assertEquals(0,run.view().phaseChanges);assertEquals(2,run.view().observedTicks);assertEquals(Integer.valueOf(10),run.view().scoreValue);
        long first=run.view().entrySequence;for(int i=0;i<20;i++)run.observe(snapshot("Step "+i,"20"));
        assertEquals(8,run.view().recentPhases.size());assertEquals(20,run.view().phaseChanges);assertEquals(Integer.valueOf(20),run.view().scoreValue);
        run.observe(MinigameActivityCatalog.Snapshot.EMPTY);assertFalse(run.view().active());run.observe(snapshot("Gather roots","1"));
        assertTrue(run.view().entrySequence>first);assertEquals(1,run.view().observedTicks);
    }
    @Test public void percentageAndCompositeScoreStringsAreUnknownAndCannotBecomeEarnedRewards()
    {MinigameRunProgress run=new MinigameRunProgress();run.observe(snapshot("Gather","10%"));assertNull(run.view().scoreValue);run.observe(snapshot("Gather","2/10"));assertNull(run.view().scoreValue);}
    private static MinigameActivityCatalog.Snapshot snapshot(String phase,String score)
    {return new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.WINTERTODT,"Wintertodt","training:wintertodt",List.of(new MinigameActivityCatalog.Fact("Points",score)),List.of(new MinigameActivityCatalog.Step(phase,"Instruction")),Set.of(),List.of(),true);}
}
