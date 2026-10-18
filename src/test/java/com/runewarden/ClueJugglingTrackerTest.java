/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClueJugglingTrackerTest
{
    private final WorldPoint point=new WorldPoint(3200,3200,0);
    private long add(ClueJugglingTracker t,int remaining){return t.observe(2677,"Clue scroll (easy)",ClueJugglingTracker.Tier.EASY,point,301,1,true,remaining,-1);}
    @Test public void observesNativeDeadlineRatherThanAssumingAnHour()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,87);assertEquals(87,t.rows().get(0).ticks);assertEquals("53s",ClueJugglingTracker.time(t.rows().get(0),false));t.advance(20);assertEquals(67,t.rows().get(0).ticks);}
    @Test public void unknownDeadlineNeverReceivesAFabricatedExpiryOrAlert()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,-1);t.advance(6001);assertEquals(-1,t.rows().get(0).ticks);assertEquals("?",ClueJugglingTracker.time(t.rows().get(0),false));assertTrue(t.alerts(9999,x->true).isEmpty());}
    @Test public void sameIdAndTileCanHaveDistinctTimers()
    {ClueJugglingTracker t=new ClueJugglingTracker();long a=add(t,100),b=add(t,300);assertNotEquals(a,b);assertEquals(2,t.size());assertEquals(100,t.rows().get(0).ticks);t.remove(a);assertEquals(b,t.rows().get(0).key);}
    @Test public void sceneDetachAndNativeRespawnPreserveTheSameTimer()
    {ClueJugglingTracker t=new ClueJugglingTracker();long key=add(t,100);t.detach();t.advance(10);assertEquals(key,add(t,90));assertEquals(1,t.size());assertTrue(t.rows().get(0).present);}
    @Test public void reattachChoosesClosestDeadlineWhenSeveralCluesShareATile()
    {ClueJugglingTracker t=new ClueJugglingTracker();long early=add(t,100),late=add(t,300);t.detach();assertEquals(late,add(t,300));assertEquals(early,add(t,100));assertEquals(2,t.size());}
    @Test public void absentCluesKeepTickingAndExpireOffScene()
    {ClueJugglingTracker t=new ClueJugglingTracker();long key=add(t,100);t.absent(key);t.advance(99);assertFalse(t.rows().get(0).present);assertEquals(1,t.rows().get(0).ticks);t.advance(1);assertTrue(t.rows().isEmpty());}
    @Test public void notificationsAreOncePerNativeDropAndDisabledTiersDoNotConsumeThem()
    {ClueJugglingTracker t=new ClueJugglingTracker();long key=add(t,30);assertTrue(t.alerts(50,x->false).isEmpty());assertEquals(1,t.alerts(50,x->true).size());assertTrue(t.alerts(50,x->true).isEmpty());t.observe(2677,"Clue scroll (easy)",ClueJugglingTracker.Tier.EASY,point,301,1,true,6000,key);t.advance(5970);assertEquals(1,t.alerts(50,x->true).size());}
    @Test public void cleanLogoutRestoresAConservativeRememberedCountdown()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,6000);t.advance(100);ClueJugglingTracker.Saved s=t.save(true);ClueJugglingTracker restored=new ClueJugglingTracker();restored.restore(s);assertEquals(5900,restored.rows().get(0).ticks);assertEquals(ClueJugglingTracker.Certainty.REMEMBERED,restored.rows().get(0).certainty);assertEquals("~5900t",ClueJugglingTracker.time(restored.rows().get(0),true));assertTrue(restored.alerts(6000,x->true).isEmpty());assertEquals(1,restored.size());}
    @Test public void dirtyShutdownDoesNotRestartAStaleTimer()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,6000);ClueJugglingTracker restored=new ClueJugglingTracker();restored.restore(t.save(false));assertEquals(-1,restored.rows().get(0).ticks);restored.advance(60000);assertEquals(-1,restored.rows().get(0).ticks);assertTrue(restored.alerts(999999,x->true).isEmpty());}
    @Test public void aNativeSightingRevalidatesTheDirtyCheckpoint()
    {ClueJugglingTracker t=new ClueJugglingTracker();long key=add(t,6000);ClueJugglingTracker restored=new ClueJugglingTracker();restored.restore(t.save(false));assertEquals(key,add(restored,220));assertEquals(ClueJugglingTracker.Certainty.NATIVE,restored.rows().get(0).certainty);assertEquals(220,restored.rows().get(0).ticks);}
    @Test public void persistedWarningsDoNotRepeatAfterRelog()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,30);t.alerts(50,x->true);ClueJugglingTracker restored=new ClueJugglingTracker();restored.restore(t.save(true));add(restored,30);assertTrue(restored.alerts(50,x->true).isEmpty());}
    @Test public void publicAndExtraItemTimersAreNeverSavedAsPausedPrivateClues()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,100);t.observe(995,"Coins",ClueJugglingTracker.Tier.EXTRA,point,301,1,false,100,-1);t.observe(2674,"Clue scroll (hard)",ClueJugglingTracker.Tier.HARD,point,301,1,false,100,-1);assertEquals(1,t.save(true).entries.size());t.removeWorldBound(302);assertEquals(1,t.size());}
    @Test public void privateCluesCanReattachAfterAWorldHop()
    {ClueJugglingTracker t=new ClueJugglingTracker();long key=add(t,100);t.detach();assertEquals(key,t.observe(2677,"Clue scroll (easy)",ClueJugglingTracker.Tier.EASY,point,302,1,true,95,-1));assertEquals(302,t.rows().get(0).world);}
    @Test public void invalidOrFutureSavesDoNotCreateTimers()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,100);ClueJugglingTracker.Saved s=t.save(true);s.version=2;assertFalse(ClueJugglingTracker.valid(s));t.restore(s);assertTrue(t.rows().isEmpty());s.version=1;s.entries.get(0).key=Long.MAX_VALUE;assertFalse(ClueJugglingTracker.valid(s));}
    @Test public void duplicateSavedKeysAreRejected()
    {ClueJugglingTracker t=new ClueJugglingTracker();add(t,100);ClueJugglingTracker.Saved s=t.save(true);s.entries.add(s.entries.get(0));assertFalse(ClueJugglingTracker.valid(s));}
    @Test public void storageAndDisplayBoundsDoNotSilentlyEvictExistingClues()
    {ClueJugglingTracker t=new ClueJugglingTracker();for(int i=0;i<ClueJugglingTracker.MAX_ENTRIES;i++)assertTrue(add(t,100+i)>0);assertEquals(-1,add(t,300));assertEquals(ClueJugglingTracker.MAX_ENTRIES,t.rows().size());}
    @Test public void identifiesAllSixTiersAndTheEliteChallengeException()
    {for(ClueJugglingTracker.Tier tier:ClueJugglingTracker.Tier.values())if(tier!=ClueJugglingTracker.Tier.EXTRA)assertEquals(tier,ClueJugglingTracker.Tier.from("Clue scroll ("+tier.label.toLowerCase(java.util.Locale.ROOT)+")",false));assertEquals(ClueJugglingTracker.Tier.ELITE,ClueJugglingTracker.Tier.from("Challenge scroll",true));assertNull(ClueJugglingTracker.Tier.from("Reward casket (easy)",false));}
}
