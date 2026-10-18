/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterPracticeSessionTest
{
    @Test public void pausedTimeDoesNotInflateTheRecordedAttempt()
    {
        AtomicLong clock = new AtomicLong(100);
        EncounterPracticeSession session = new EncounterPracticeSession(clock::get);
        session.start(); clock.set(1100); session.pause(); clock.set(11000);
        assertEquals(1000, session.elapsed());
        session.resume(); clock.set(12000);
        EncounterPracticeSession.Attempt result = session.finish("Clear", "Better movement");
        assertEquals(2000, result.durationMillis);
        assertEquals("Clear", result.outcome);
        assertFalse(session.active());
        assertNull(session.finish("Clear", "Duplicate button"));
        assertEquals(1, session.attempts().size());
    }

    @Test public void recentHistoryIsBoundedAndNotesCannotGrowWithoutLimit()
    {
        AtomicLong clock = new AtomicLong();
        EncounterPracticeSession session = new EncounterPracticeSession(clock::get);
        for (int i = 0; i < 25; i++) { session.start(); clock.incrementAndGet(); session.finish("Retry", "Attempt " + i); }
        assertEquals(20, session.attempts().size());
        assertEquals("Attempt 5", session.attempts().get(0).note);
        assertEquals(400, EncounterPracticeSession.boundedNote(String.join("", java.util.Collections.nCopies(500, "x"))).length());
    }

    @Test public void invalidSavedAttemptsAreRejectedAndRestoredClockIsInactive()
    {
        List<EncounterPracticeSession.Attempt> saved = new ArrayList<>();
        saved.add(null); saved.add(new EncounterPracticeSession.Attempt("Retry", -1, "Invalid"));
        saved.add(new EncounterPracticeSession.Attempt("Clear", 1500, null));
        EncounterPracticeSession session = new EncounterPracticeSession(() -> 5000);
        session.restore(saved);
        assertEquals(1, session.attempts().size());
        assertFalse(session.active());
        assertEquals(0, session.elapsed());
        assertEquals("", session.attempts().get(0).note);
    }

    @Test public void categoriesSeparatePhaseGuidesWavesAndEquipmentAndSearchRemainsLocal()
    {
        GuideCatalog catalog = new GuideCatalog(new Gson());
        List<GuideCatalog.Entry> bosses = EncounterPracticeTool.entries(catalog, "encounter", "");
        List<GuideCatalog.Entry> waves = EncounterPracticeTool.entries(catalog, "waves", "");
        List<GuideCatalog.Entry> equipment = EncounterPracticeTool.entries(catalog, "boss", "");
        assertTrue(bosses.size() >= 117); assertEquals(17, waves.size()); assertEquals(13, equipment.size());
        assertTrue(bosses.stream().allMatch(entry -> entry.id.startsWith("encounter:")));
        assertTrue(waves.stream().allMatch(entry -> entry.id.startsWith("waves:")));
        assertEquals(1, EncounterPracticeTool.entries(catalog, "encounter", "Abyssal Sire").size());
    }

    @Test public void restoredUnfinishedAttemptIsPausedAndRetainsOnlyActiveDuration()
    { AtomicLong clock=new AtomicLong(1000);EncounterPracticeSession session=new EncounterPracticeSession(clock::get);session.restorePaused(3000);clock.set(900000);assertTrue(session.active());assertTrue(session.paused());assertEquals(3000,session.elapsed());session.resume();clock.addAndGet(1000);assertEquals(4000,session.finish("Clear","").durationMillis); }
    @Test public void nativeLootMatchesOnlyTheExactSelectedEncounterRatherThanRaidMinibosses()
    { GuideCatalog guides=new GuideCatalog(new Gson());assertTrue(EncounterPracticeTool.matchesLoot(guides.entry("encounter:vorkath"),"Vorkath"));assertFalse(EncounterPracticeTool.matchesLoot(guides.entry("encounter:chambers-of-xeric"),"Great Olm"));assertFalse(EncounterPracticeTool.matchesLoot(guides.entry("encounter:vorkath"),"Zombified spawn"));assertFalse(EncounterPracticeTool.matchesLoot(guides.entry("boss:vorkath"),"Vorkath")); }
    @Test public void durationHasNoNegativeTimeOrHourWrap()
    {
        assertEquals("0:00", EncounterPracticeTool.duration(-1));
        assertEquals("1:05", EncounterPracticeTool.duration(65000));
        assertEquals("120:00", EncounterPracticeTool.duration(7200000));
    }
}
