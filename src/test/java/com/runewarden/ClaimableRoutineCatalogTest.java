/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.time.Instant;
import java.util.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClaimableRoutineCatalogTest
{
    @Test public void fullTierAllowancesUseClaimedRewardsRatherThanCompletedTasks()
    {
        Fixture f=new Fixture();f.bits.put(VarbitID.VARROCK_DIARY_ELITE_COMPLETE,1);
        ClaimableRoutineCatalog.Claim claim=f.get("battlestaves");assertEquals(ClaimableRoutineCatalog.Status.CLAIM_REWARD,claim.status);assertEquals(0,claim.remaining);
        f.claimed.put("varrock_easy",1);claim=f.get("battlestaves");assertEquals(15,claim.remaining);assertEquals(105000,claim.price);
        f.claimed.put("varrock_elite",1);claim=f.get("battlestaves");assertEquals(120,claim.remaining);assertEquals(840000,claim.price);assertTrue(claim.supplies.contains(995));
    }
    @Test public void dailyClaimBitsNeverBecomeAvailableBecauseClockPassesMidnight()
    {
        Fixture f=new Fixture();f.claimed.put("varrock_elite",1);f.bits.put(VarbitID.ZAFF_LAST_CLAIMED,1);
        f.time=Instant.parse("2026-10-16T23:59:59Z").toEpochMilli();assertEquals(ClaimableRoutineCatalog.Status.CLAIMED,f.get("battlestaves").status);
        f.time=Instant.parse("2026-10-17T00:00:01Z").toEpochMilli();assertEquals(ClaimableRoutineCatalog.Status.CLAIMED,f.get("battlestaves").status);
        f.bits.put(VarbitID.ZAFF_LAST_CLAIMED,0);assertEquals(ClaimableRoutineCatalog.Status.AVAILABLE,f.get("battlestaves").status);
    }
    @Test public void partialBonemealConversionTracksRemainingNativeCount()
    {
        Fixture f=new Fixture();f.claimed.put("morytania_hard",1);f.bits.put(VarbitID.MORYTANIA_SLIME_CLAIMED,20);
        ClaimableRoutineCatalog.Claim claim=f.get("bonemeal");assertEquals(26,claim.allowance);assertEquals(6,claim.remaining);assertTrue(claim.supplies.contains(526));
        f.bits.put(VarbitID.MORYTANIA_SLIME_CLAIMED,26);assertEquals(ClaimableRoutineCatalog.Status.CLAIMED,f.get("bonemeal").status);
    }
    @Test public void herbBoxesRespectIronmanEligibilityPointCostAndPurchasedCap()
    {
        Fixture f=new Fixture();f.points.put(VarPlayerID.NZONE_REWARDPOINTS,19000);f.bits.put(VarbitID.NZONE_HERBBOXES_PURCHASED,13);
        ClaimableRoutineCatalog.Claim claim=f.get("herb-boxes");assertEquals(2,claim.remaining);assertEquals(ClaimableRoutineCatalog.Status.AVAILABLE,claim.status);assertTrue(claim.detail.contains("2 affordable"));
        f.points.put(VarPlayerID.NZONE_REWARDPOINTS,9499);assertEquals(ClaimableRoutineCatalog.Status.NEEDS_PAYMENT,f.get("herb-boxes").status);
        f.bits.put(VarbitID.NZONE_HERBBOXES_PURCHASED,15);assertEquals(ClaimableRoutineCatalog.Status.CLAIMED,f.get("herb-boxes").status);
        f.bits.put(VarbitID.IRONMAN,4);assertEquals(ClaimableRoutineCatalog.Status.LOCKED,f.get("herb-boxes").status);
    }
    @Test public void sandHandlesQuestUltimateAndAutomaticDeliveryIncludingF2pLogin()
    {
        Fixture f=new Fixture();f.bits.put(VarbitID.HANDSAND_QUEST,159);assertEquals(ClaimableRoutineCatalog.Status.LOCKED,f.get("sand").status);
        f.bits.put(VarbitID.HANDSAND_QUEST,160);assertEquals(84,f.get("sand").remaining);
        f.bits.put(VarbitID.IRONMAN,2);assertEquals(ClaimableRoutineCatalog.Status.LOCKED,f.get("sand").status);
        f.bits.put(VarbitID.IRONMAN,1);f.claimed.put("ardougne_elite",1);f.member=false;assertEquals(ClaimableRoutineCatalog.Status.AUTOMATIC,f.get("sand").status);assertEquals(0,f.get("sand").remaining);
    }
    @Test public void sourcedAllowancesAndMinimumTiersCoverAllNineActivities()
    {
        Fixture f=new Fixture();for(String region:List.of("varrock","ardougne","wilderness","kandarin","western","morytania","kourend"))f.claimed.put(region+"_elite",1);
        assertEquals(9,f.all().size());assertEquals(250,f.get("essence").remaining);assertEquals(200,f.get("runes").remaining);assertEquals(250,f.get("flax").remaining);assertEquals(150,f.get("arrows").remaining);assertEquals(39,f.get("bonemeal").remaining);assertEquals(80,f.get("dynamite").remaining);
        f.claimed.clear();f.claimed.put("ardougne_easy",1);assertEquals(ClaimableRoutineCatalog.Status.LOCKED,f.get("essence").status);
    }
    @Test public void unknownAndInvalidNativeCountersStayUnknown()
    {
        Fixture f=new Fixture();f.bits.remove(VarbitID.ZAFF_LAST_CLAIMED);assertEquals(ClaimableRoutineCatalog.Status.UNKNOWN,f.get("battlestaves").status);
        f.bits.put(VarbitID.ZAFF_LAST_CLAIMED,-1);assertEquals(ClaimableRoutineCatalog.Status.UNKNOWN,f.get("battlestaves").status);
        f.bits.put(VarbitID.NZONE_HERBBOXES_PURCHASED,999);assertEquals(ClaimableRoutineCatalog.Status.UNKNOWN,f.get("herb-boxes").status);
    }
    @Test public void immutableFactsAndClaimListsCannotBeMutatedByConsumers()
    {
        Fixture f=new Fixture();ClaimableRoutineCatalog.Facts facts=f.facts();f.bits.put(VarbitID.HANDSAND_QUEST,0);assertEquals(Integer.valueOf(160),facts.bit(VarbitID.HANDSAND_QUEST));
        try{f.all().clear();fail();}catch(UnsupportedOperationException expected){}
        f.claimed.put("varrock_easy",1);try{f.get("battlestaves").supplies.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void dailyResetBoundaryUsesActualUtcDayAndHandlesLeapAndYearRollover()
    {
        assertEquals(Instant.parse("2026-10-18T00:00:00Z"),ClaimableRoutineCatalog.nextReset(Instant.parse("2026-10-17T00:00:00Z")));
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"),ClaimableRoutineCatalog.nextReset(Instant.parse("2026-12-31T23:59:59Z")));
        assertEquals(Instant.parse("2028-02-29T00:00:00Z"),ClaimableRoutineCatalog.nextReset(Instant.parse("2028-02-28T12:00:00Z")));
    }
    private static final class Fixture
    {
        final Map<Integer,Integer> bits=new LinkedHashMap<>(),points=new LinkedHashMap<>();final Map<String,Integer> claimed=new LinkedHashMap<>();boolean member=true;long time=1;
        Fixture(){for(int variable:ClaimableRoutineCatalog.variables())bits.put(variable,0);bits.put(VarbitID.HANDSAND_QUEST,160);points.put(VarPlayerID.NZONE_REWARDPOINTS,0);}
        ClaimableRoutineCatalog.Facts facts(){return new ClaimableRoutineCatalog.Facts(bits,points,claimed,member,time);}
        List<ClaimableRoutineCatalog.Claim> all(){return ClaimableRoutineCatalog.project(facts());}
        ClaimableRoutineCatalog.Claim get(String id){return all().stream().filter(claim->claim.id.equals(id)).findFirst().orElseThrow();}
    }
}
