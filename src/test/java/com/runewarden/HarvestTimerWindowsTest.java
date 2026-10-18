/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import net.runelite.client.plugins.timetracking.SummaryState;
import org.junit.Test;
import static org.junit.Assert.*;

public class HarvestTimerWindowsTest
{
    private static HarvestClockTool.Reading reading(SummaryState state, long deadline)
    {
        return new HarvestClockTool.Reading(true, false, "", Collections.singletonList(
            new HarvestClockTool.Harvest("herb", "Herb", state, deadline, true)));
    }
    @Test public void unchangedNativeDeadlineKeepsFirstObservedWindowAndNewDeadlineRestartsIt()
    {
        HarvestTimerWindows windows = new HarvestTimerWindows();
        HarvestClockTool.Harvest first = windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 100).harvests.get(0);
        assertEquals(100, first.windowStart); assertEquals(1, first.remainingFraction(100), 0);
        HarvestClockTool.Harvest later = windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 400).harvests.get(0);
        assertEquals(100, later.windowStart); assertEquals(2d / 3, later.remainingFraction(400), .00001);
        HarvestClockTool.Harvest replanted = windows.observe(reading(SummaryState.IN_PROGRESS, 2000), 400).harvests.get(0);
        assertEquals(400, replanted.windowStart); assertEquals(1, replanted.remainingFraction(400), 0);
    }
    @Test public void unknownObservationsAndAccountClearDiscardOldWindows()
    {
        HarvestTimerWindows windows = new HarvestTimerWindows();
        windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 100);
        assertFalse(windows.observe(reading(SummaryState.UNKNOWN, 1000), 200).harvests.get(0).hasWindow());
        assertEquals(300, windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 300).harvests.get(0).windowStart);
        windows.clear();
        assertEquals(500, windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 500).harvests.get(0).windowStart);
    }
    @Test public void nativeReadyStateDoesNotInventAPlantingTime()
    {
        HarvestTimerWindows windows = new HarvestTimerWindows();
        windows.observe(reading(SummaryState.IN_PROGRESS, 1000), 100);
        HarvestClockTool.Harvest ready = windows.observe(reading(SummaryState.COMPLETED, 0), 1100).harvests.get(0);
        assertTrue(ready.due(1100)); assertFalse(ready.hasWindow());
    }
}
