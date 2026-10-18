/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameCountdownsTest
{
    @Test public void nativeClockDoesNotAdvanceWithoutNewNativeEvidence()
    {
        MinigameCountdowns clocks=new MinigameCountdowns();
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","2:00"));
        assertEquals(120,clocks.reading().get(0).seconds);assertEquals(1d,clocks.reading().get(0).remainingFraction(),0);
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","1:00"));
        assertEquals(.5d,clocks.reading().get(0).remainingFraction(),0);assertEquals("1:00",clocks.reading().get(0).text());
        for(int i=0;i<100;i++)assertEquals(60,clocks.reading().get(0).seconds);
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","1:00"));
        assertEquals(60,clocks.reading().get(0).seconds);
    }
    @Test public void restartExitAndActivityChangesDropTheOldWindow()
    {
        MinigameCountdowns clocks=new MinigameCountdowns();
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","2:00"));
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","0:30"));
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","1:00"));
        assertEquals(60,clocks.reading().get(0).observedWindow);
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.VOLCANIC_MINE,"Time","0:20"));
        assertEquals(20,clocks.reading().get(0).observedWindow);
        clocks.observe(MinigameActivityCatalog.Snapshot.EMPTY);assertTrue(clocks.reading().isEmpty());
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.PEST_CONTROL,"Time","0:05"));
        assertEquals(5,clocks.reading().get(0).observedWindow);clocks.clear();assertTrue(clocks.reading().isEmpty());
    }
    @Test public void progressCountersAndStatusTextAreNotInterpretedAsClocks()
    {
        MinigameCountdowns clocks=new MinigameCountdowns();
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.GOTR,"Portal","82%"));assertTrue(clocks.reading().isEmpty());
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.GOTR,"Energy","2:00"));assertTrue(clocks.reading().isEmpty());
        clocks.observe(snapshot(MinigameActivityCatalog.Activity.GOTR,"Round","Waiting"));assertTrue(clocks.reading().isEmpty());
    }
    private static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity,String label,String value)
    {return new MinigameActivityCatalog.Snapshot(activity,"Activity",null,List.of(new MinigameActivityCatalog.Fact(label,value)),List.of(),Set.of(),List.of(),true);}
}
