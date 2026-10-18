/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/** Measured per-skill XP session. Login/offline baselines never become earned XP. */
final class SkillProgressSession
{
    int xp,level,boosted,lastGainTick=-1,lastClockTick=-1;
    long gained,ticks;
    boolean initialized;
    boolean observe(int actualXp,int realLevel,int boostedLevel,int tick,boolean record)
    {
        if(actualXp<0||actualXp>net.runelite.api.Experience.MAX_SKILL_XP||realLevel<1)return false;
        int previous=xp;boolean before=initialized;xp=actualXp;level=realLevel;boosted=boostedLevel;initialized=true;
        if(before&&actualXp<previous){reset();return false;}
        if(before&&record&&actualXp>previous){gained+=actualXp-previous;lastGainTick=tick;return true;}
        return false;
    }
    void clock(int tick,int idleTicks,boolean paused)
    {
        if(tick==lastClockTick)return;lastClockTick=tick;
        if(!paused&&lastGainTick>=0&&gained>0&&(idleTicks==0||tick-lastGainTick<=idleTicks))ticks++;
    }
    void suspend(){initialized=false;lastGainTick=-1;lastClockTick=-1;}
    void reset(){gained=0;ticks=0;lastGainTick=-1;lastClockTick=-1;}
    boolean idle(int tick,int idleTicks){return lastGainTick<0||idleTicks>0&&tick-lastGainTick>idleTicks;}
    long rate(){return ticks<10||gained<=0?0:(long)Math.min(Long.MAX_VALUE,gained*6000d/ticks);}
    long eta(int remaining){long rate=rate();return remaining<=0?0:rate<=0?-1:(long)Math.ceil(remaining*3600d/rate);}
}
