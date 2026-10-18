/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgilityTrainingSessionTest
{
    @Test public void rateUsesObservedTicksAndDoesNotStartBeforeTheFirstAction()
    {
        AgilityTrainingSession session=new AgilityTrainingSession();for(int i=0;i<100;i++)session.tick();assertEquals(0,session.ticks);
        session.gain(120);for(int i=0;i<100;i++)session.tick();assertEquals(7200,session.xpPerHour());
    }
    @Test public void sameTickCompletionIsDeduplicatedAndOnlyFullIntervalsBecomeLapTimes()
    {
        AgilityTrainingSession session=new AgilityTrainingSession();assertTrue(session.complete(10));assertFalse(session.complete(10));assertEquals(0,session.lastLap());
        for(int i=0;i<100;i++)session.tick();assertTrue(session.complete(110));assertEquals(100,session.lastLap());assertEquals(60,session.lapsPerHour());assertEquals("1:00",AgilityCoach.duration(session.lastLap()));
    }
    @Test public void leavingOrReloggingDoesNotTreatTheOfflineGapAsALap()
    {
        AgilityTrainingSession session=new AgilityTrainingSession();session.complete(10);for(int i=0;i<20;i++)session.tick();session.interrupt();session.complete(5000);assertEquals(0,session.lastLap());
        for(int i=0;i<90;i++)session.tick();session.complete(5090);assertEquals(90,session.bestLap());
    }
    @Test public void rollingThirtyLapSamplesDiscardAnAncientBest()
    {
        AgilityTrainingSession session=new AgilityTrainingSession();session.complete(0);int tick=0;
        for(int lap=0;lap<31;lap++){int length=lap==0?1:100;for(int i=0;i<length;i++)session.tick();tick+=length;session.complete(tick);}
        assertEquals(30,session.lapTicks.size());assertEquals(100,session.bestLap());assertEquals(32,session.laps);
    }
    @Test public void invalidRecoveredTotalsAreRejected()
    {AgilityTrainingSession session=new AgilityTrainingSession();assertTrue(session.valid());session.lapTicks.add(0);assertFalse(session.valid());session.lapTicks.clear();session.xp=-1;assertFalse(session.valid());}
}
