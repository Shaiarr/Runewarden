/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class SkillProgressSessionTest
{
    @Test public void firstNativeValueAndBoostOnlyChangesNeverBecomeEarnedXp()
    {SkillProgressSession s=new SkillProgressSession();assertFalse(s.observe(100000,50,50,1,true));assertFalse(s.observe(100000,50,55,2,true));assertEquals(0,s.gained);assertEquals(55,s.boosted);}
    @Test public void measuredRateAndEtaUseTheActualObservedTickDenominator()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);s.observe(1120,10,10,2,true);for(int tick=2;tick<102;tick++)s.clock(tick,0,false);assertEquals(7200,s.rate());assertEquals(60,s.eta(120));assertEquals(0,s.eta(0));}
    @Test public void rateAndEtaWaitForSixSecondsOfObservedSamples()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);s.observe(1120,10,10,2,true);for(int tick=2;tick<11;tick++)s.clock(tick,0,false);assertEquals(0,s.rate());assertEquals(-1,s.eta(120));s.clock(11,0,false);assertTrue(s.rate()>0);}
    @Test public void idlePauseStopsTheClockAndDuplicateTickRefreshesDoNotAdvanceIt()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,0,true);s.observe(1120,10,10,1,true);for(int tick=1;tick<100;tick++){s.clock(tick,10,false);s.clock(tick,10,false);}assertEquals(11,s.ticks);assertTrue(s.idle(20,10));}
    @Test public void manuallyPausedGainsStillUpdateActualXpButDoNotEnterTheSession()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);s.observe(1200,10,10,2,false);s.clock(2,0,true);assertEquals(1200,s.xp);assertEquals(0,s.gained);assertEquals(0,s.ticks);s.observe(1300,10,10,3,true);assertEquals(100,s.gained);}
    @Test public void offlineGainsBecomeANewBaselineWhenThePlayerReturns()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);s.observe(1100,10,10,2,true);s.clock(2,0,false);s.suspend();s.observe(50000,42,42,100,true);assertEquals(100,s.gained);s.clock(100,0,false);assertEquals(1,s.ticks);s.observe(50100,42,42,101,true);assertEquals(200,s.gained);}
    @Test public void backwardsXpResetsTheMeasuredSessionRatherThanProducingNegativeRates()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);s.observe(1100,10,10,2,true);assertEquals(100,s.gained);assertFalse(s.observe(0,1,1,3,true));assertEquals(0,s.gained);assertEquals(0,s.rate());}
    @Test public void invalidNativeValuesCannotOverwriteTheLastGoodObservation()
    {SkillProgressSession s=new SkillProgressSession();s.observe(1000,10,10,1,true);assertFalse(s.observe(-1,10,10,2,true));assertFalse(s.observe(200000001,99,99,3,true));assertFalse(s.observe(10000,0,0,4,true));assertEquals(1000,s.xp);}
}
