package com.runewarden;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoutinePlannerToolTest
{
    @Test public void dailyRolloverIsUtcAndOnceNeverBecomesDueAgain()
    {RoutinePlannerTool.Routine r=routine();r.completed=Instant.parse("2026-10-16T23:59:00Z").toEpochMilli();r.cadence=RoutinePlannerTool.Cadence.DAILY;assertFalse(RoutinePlannerTool.due(r,Instant.parse("2026-10-16T23:59:59Z")));assertTrue(RoutinePlannerTool.due(r,Instant.parse("2026-10-17T00:00:00Z")));r.cadence=RoutinePlannerTool.Cadence.ONCE;assertFalse(RoutinePlannerTool.due(r,Instant.parse("2030-01-01T00:00:00Z")));}
    @Test public void weeklyRepeatsSevenUtcDatesAfterUserCompletionWithoutClaimingGameReset()
    {RoutinePlannerTool.Routine r=routine();r.cadence=RoutinePlannerTool.Cadence.WEEKLY;r.completed=Instant.parse("2026-10-16T22:00:00Z").toEpochMilli();assertFalse(RoutinePlannerTool.due(r,Instant.parse("2026-10-22T23:00:00Z")));assertTrue(RoutinePlannerTool.due(r,Instant.parse("2026-10-23T00:00:00Z")));}
    @Test public void invalidAndDuplicateImportedRoutineEntriesAreRejected()
    {RoutinePlannerTool.Plan p=new RoutinePlannerTool.Plan();p.routines.add(routine());assertTrue(RoutinePlannerTool.valid(p));p.routines.add(routine());assertFalse(RoutinePlannerTool.valid(p));p.routines.clear();RoutinePlannerTool.Routine r=routine();r.completed=-1;p.routines.add(r);assertFalse(RoutinePlannerTool.valid(p));}
    private RoutinePlannerTool.Routine routine(){RoutinePlannerTool.Routine r=new RoutinePlannerTool.Routine();r.id="stable";r.label="My weekly run";r.module="Farm runs";r.cadence=RoutinePlannerTool.Cadence.ONCE;return r;}
}
