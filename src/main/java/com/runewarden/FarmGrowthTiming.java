/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/** Growth deadlines from an observed native stage and the account's observed farming clock. */
final class FarmGrowthTiming
{
    static final class Window
    {
        final long earliest, latest, cycleStart;
        final boolean clockKnown;
        Window(long earliest, long latest, long start, boolean known)
        { this.earliest=earliest; this.latest=latest; cycleStart=start; clockKnown=known; }
    }
    private FarmGrowthTiming() { }

    static Window predict(FarmPatchCatalog.State state, long observedMillis, long firstStageMillis,
        Integer precisionMinutes, Integer offsetMinutes, boolean leagues)
    {
        if(state==null || !"GROWING".equals(state.state) || state.tickMinutes<=0 || observedMillis<=0)
            return new Window(0,0,0,false);
        int tickMinutes=leagues?state.tickMinutes/5:state.tickMinutes;
        if(tickMinutes<=0)return new Window(0,0,0,false);
        long cycle=tickMinutes*60L,remaining=Math.max(0,state.stages-1-state.stage);
        boolean known=precisionMinutes!=null && offsetMinutes!=null && precisionMinutes>0 && offsetMinutes>=0
            && (precisionMinutes>=tickMinutes || precisionMinutes>=40);
        if(known)
        {
            // Same account-clock alignment as native FarmingTracker.getTickTime(). The saved
            // timestamp describes this stage, so visiting an old tree does not restart growth.
            long offset=(offsetMinutes%tickMinutes)*60L,shifted=observedMillis/1000+offset;
            long start=shifted-Math.floorMod(shifted,cycle)-offset,deadline=start+remaining*cycle;
            return new Window(deadline,deadline,start,true);
        }
        long observed=(firstStageMillis>0?Math.min(firstStageMillis,observedMillis):observedMillis)/1000;
        long latest=observed+remaining*cycle,earliest=Math.max(observed,latest-cycle);
        return new Window(earliest,latest,observed,false);
    }

    static String remaining(long seconds)
    {
        long minutes=Math.max(0,(seconds+59)/60),hours=minutes/60;
        return hours==0?minutes+"m":hours+"h"+(minutes%60==0?"":" "+minutes%60+"m");
    }
}
