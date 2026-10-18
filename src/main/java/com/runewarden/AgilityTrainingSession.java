/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.List;

/** Account-local totals. Durations advance only on observed game ticks in the activity. */
final class AgilityTrainingSession
{
    long xp, ticks;
    int laps, marks;
    final List<Integer> lapTicks = new ArrayList<>();
    private transient long previousLap = -1;
    private transient int lastCompletionTick = -1;
    void tick() { if (xp > 0 || laps > 0) ticks++; }
    void gain(int amount) { if (amount > 0) xp += amount; }
    void interrupt() { previousLap = -1; lastCompletionTick = -1; }
    boolean complete(int gameTick)
    {
        if (gameTick == lastCompletionTick) return false;
        lastCompletionTick = gameTick;
        if (previousLap >= 0 && ticks > previousLap)
        {
            lapTicks.add((int)Math.min(Integer.MAX_VALUE, ticks - previousLap));
            if (lapTicks.size() > 30) lapTicks.remove(0);
        }
        previousLap = ticks; laps++; return true;
    }
    int xpPerHour() { return ticks == 0 ? 0 : (int)Math.min(Integer.MAX_VALUE, xp * 6000d / ticks); }
    int lastLap() { return lapTicks.isEmpty() ? 0 : lapTicks.get(lapTicks.size()-1); }
    int bestLap() { int best=0; for(int time:lapTicks)if(best==0||time<best)best=time;return best; }
    int lapsPerHour()
    { long total=0;for(int time:lapTicks)total+=time;return total==0?0:(int)(6000L*lapTicks.size()/total); }
    boolean valid()
    {
        if(xp<0||xp>2_000_000_000L||ticks<0||ticks>2_000_000_000L||laps<0||laps>10_000_000||marks<0||marks>10_000_000||lapTicks==null||lapTicks.size()>30)return false;
        for(Integer time:lapTicks)if(time==null||time<=0)return false;
        return true;
    }
}
