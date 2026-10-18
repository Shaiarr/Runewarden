/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;

/** Pure projection of native daily-claim state. Time never changes a claim bit. */
final class ClaimableRoutineCatalog
{
    enum Status { AVAILABLE, NEEDS_PAYMENT, CLAIMED, LOCKED, CLAIM_REWARD, AUTOMATIC, UNKNOWN }
    static final class Claim
    {
        final String id, title, destination, detail, source, diaryId;
        final Status status; final int allowance, remaining; final long price; final Set<Integer> supplies;
        Claim(String id, String title, String destination, String detail, String source, String diaryId,
            Status status, int allowance, int remaining, long price, Set<Integer> supplies)
        {
            this.id=id;this.title=title;this.destination=destination;this.detail=detail;this.source=source;this.diaryId=diaryId;
            this.status=status;this.allowance=allowance;this.remaining=remaining;this.price=price;
            this.supplies=Collections.unmodifiableSet(new LinkedHashSet<>(supplies));
        }
        String statusText() { return status==Status.AVAILABLE?"Ready to collect":status==Status.NEEDS_PAYMENT?"Payment needed":status==Status.CLAIMED?"Collected today":status==Status.CLAIM_REWARD?"Claim diary reward":status==Status.AUTOMATIC?"Automatic delivery":status==Status.LOCKED?"Unlock needed":"Not observed"; }
    }
    static final class Facts
    {
        final Map<Integer,Integer> bits, varps; final Map<String,Integer> claimed;
        final long observedAt; final boolean member;
        Facts(Map<Integer,Integer> bits, Map<Integer,Integer> varps, Map<String,Integer> claimed, boolean member, long time)
        {this.bits=Collections.unmodifiableMap(new LinkedHashMap<>(bits));this.varps=Collections.unmodifiableMap(new LinkedHashMap<>(varps));this.claimed=Collections.unmodifiableMap(new LinkedHashMap<>(claimed));this.member=member;this.observedAt=time;}
        Integer bit(int id) {return bits.get(id);}
    }
    static final int[][] TIER_BITS = {
        {VarbitID.VARROCK_DIARY_EASY_COMPLETE,VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE,VarbitID.VARROCK_DIARY_HARD_COMPLETE,VarbitID.VARROCK_DIARY_ELITE_COMPLETE},
        {VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE,VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE,VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE,VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE},
        {VarbitID.WILDERNESS_DIARY_EASY_COMPLETE,VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE,VarbitID.WILDERNESS_DIARY_HARD_COMPLETE,VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE},
        {VarbitID.KANDARIN_DIARY_EASY_COMPLETE,VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE,VarbitID.KANDARIN_DIARY_HARD_COMPLETE,VarbitID.KANDARIN_DIARY_ELITE_COMPLETE},
        {VarbitID.WESTERN_DIARY_EASY_COMPLETE,VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE,VarbitID.WESTERN_DIARY_HARD_COMPLETE,VarbitID.WESTERN_DIARY_ELITE_COMPLETE},
        {VarbitID.MORYTANIA_DIARY_EASY_COMPLETE,VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE,VarbitID.MORYTANIA_DIARY_HARD_COMPLETE,VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE},
        {VarbitID.KOUREND_DIARY_EASY_COMPLETE,VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE,VarbitID.KOUREND_DIARY_HARD_COMPLETE,VarbitID.KOUREND_DIARY_ELITE_COMPLETE}
    };
    static final int[] CLAIM_BITS={VarbitID.ZAFF_LAST_CLAIMED,VarbitID.ARDOUGNE_FREE_ESSENCE,VarbitID.LUNDAIL_LAST_CLAIMED,
        VarbitID.SEERS_FREE_FLAX,VarbitID.WESTERN_RANTZ_ARROWS,VarbitID.MORYTANIA_SLIME_CLAIMED,VarbitID.KOUREND_FREE_DYNAMITE,
        VarbitID.YANILLE_SAND_CLAIMED,VarbitID.NZONE_HERBBOXES_PURCHASED,VarbitID.IRONMAN,VarbitID.HANDSAND_QUEST};
    static Set<Integer> variables()
    {Set<Integer> result=new LinkedHashSet<>();for(int[] tier:TIER_BITS)for(int bit:tier)result.add(bit);for(int bit:CLAIM_BITS)result.add(bit);return Collections.unmodifiableSet(result);}
    private static final String[] TIER_NAMES={"easy","medium","hard","elite"};
    static List<Claim> project(Facts facts)
    {
        List<Claim> result=new ArrayList<>();
        result.add(diary(facts,"battlestaves","Discount battlestaves","Zaff's barrel · Varrock","Zaff", "varrock",0,1,new int[]{15,30,60,120},VarbitID.ZAFF_LAST_CLAIMED,7000,"Coins"));
        result.add(diary(facts,"essence","Pure essence","Wizard Cromperty · East Ardougne","Wizard_Cromperty","ardougne",1,2,new int[]{0,100,150,250},VarbitID.ARDOUGNE_FREE_ESSENCE,0,null));
        result.add(diary(facts,"runes","Random runes","Lundail · Mage Arena bank","Lundail","wilderness",2,1,new int[]{40,80,120,200},VarbitID.LUNDAIL_LAST_CLAIMED,0,null));
        result.add(diary(facts,"flax","Flax to bow strings","Flax keeper · Seers' Village","Flax_keeper","kandarin",3,1,new int[]{30,60,120,250},VarbitID.SEERS_FREE_FLAX,0,"Flax"));
        result.add(diary(facts,"arrows","Ogre arrows","Rantz · Feldip Hills","Rantz","western",4,1,new int[]{25,50,100,150},VarbitID.WESTERN_RANTZ_ARROWS,0,null));
        result.add(diary(facts,"bonemeal","Bonemeal and slime","Robin · Port Phasmatys","Robin","morytania",5,2,new int[]{0,13,26,39},VarbitID.MORYTANIA_SLIME_CLAIMED,0,"Bones"));
        result.add(diary(facts,"dynamite","Free dynamite","Thirus · Lovakengj","Thirus","kourend",6,2,new int[]{0,20,40,80},VarbitID.KOUREND_FREE_DYNAMITE,0,null));
        result.add(sand(facts));result.add(herbs(facts));return Collections.unmodifiableList(result);
    }
    static int highestClaimed(Facts facts,String region)
    {for(int tier=3;tier>=0;tier--)if(Integer.valueOf(1).equals(facts.claimed.get(region+"_"+TIER_NAMES[tier])))return tier;return -1;}
    private static Claim diary(Facts f,String id,String title,String destination,String page,String region,int regionIndex,int minimum,int[] amounts,int claimBit,int unitCost,String supply)
    {
        String diaryId=region+"_"+TIER_NAMES[minimum-1];int tier=highestClaimed(f,region);int allowance=tier>=minimum-1?amounts[tier]:0;
        Integer claimed=f.bit(claimBit);Status status;String detail;int remaining=0;long price=0;
        if(!f.member){status=Status.LOCKED;detail="Members activity";}
        else if(claimed==null||claimed<0){status=Status.UNKNOWN;detail="Waiting for native account state";}
        else if(allowance==0)
        {boolean complete=false;for(int i=minimum-1;i<4;i++)complete|=Integer.valueOf(1).equals(f.bit(TIER_BITS[regionIndex][i]));status=complete?Status.CLAIM_REWARD:Status.LOCKED;detail=region.replace('_',' ')+" "+TIER_NAMES[minimum-1]+" diary";}
        else
        {
            remaining="bonemeal".equals(id)?Math.max(0,allowance-claimed):(claimed==0?allowance:0);
            price=(long)remaining*unitCost;status=remaining>0?Status.AVAILABLE:Status.CLAIMED;
            detail=remaining+" / "+allowance+" remaining"+(price>0?" · "+String.format(java.util.Locale.ROOT,"%,d",price)+" coins":"");
            diaryId=region+"_"+TIER_NAMES[tier];
            if(supply!=null&&remaining>0&&unitCost==0)detail+=" · bring "+remaining+" "+supply.toLowerCase(java.util.Locale.ROOT);
        }
        return new Claim(id,title,destination,detail,"https://oldschool.runescape.wiki/w/"+page,diaryId,status,allowance,remaining,price,resolve(supply));
    }
    private static Claim sand(Facts f)
    {
        Integer quest=f.bit(VarbitID.HANDSAND_QUEST),iron=f.bit(VarbitID.IRONMAN),claimed=f.bit(VarbitID.YANILLE_SAND_CLAIMED);
        Status status;String detail;int remaining=0;
        boolean automatic=Integer.valueOf(1).equals(f.claimed.get("ardougne_elite"));
        if(quest==null||iron==null||claimed==null||claimed<0||iron<0||quest<0){status=Status.UNKNOWN;detail="Waiting for native account state";}
        else if(iron==2){status=Status.LOCKED;detail="Unavailable to Ultimate Ironmen";}
        else if(quest<160){status=Status.LOCKED;detail="The Hand in the Sand";}
        else if(automatic){status=Status.AUTOMATIC;detail="84 buckets on login · Ardougne elite";}
        else if(!f.member){status=Status.LOCKED;detail="Members activity";}
        else{remaining=claimed==0?84:0;status=remaining>0?Status.AVAILABLE:Status.CLAIMED;detail=remaining+" / 84 buckets remaining";}
        return new Claim("sand","Buckets of sand","Bert · Yanille",detail,"https://oldschool.runescape.wiki/w/Bert",automatic?"ardougne_elite":null,status,84,remaining,0,Set.of());
    }
    private static Claim herbs(Facts f)
    {
        Integer iron=f.bit(VarbitID.IRONMAN),bought=f.bit(VarbitID.NZONE_HERBBOXES_PURCHASED),points=f.varps.get(VarPlayerID.NZONE_REWARDPOINTS);
        Status status;String detail;int remaining=0;
        if(iron==null||bought==null||points==null||iron<0||bought<0||bought>15||points<0){status=Status.UNKNOWN;detail="Waiting for native account state";}
        else if(!f.member||iron!=0){status=Status.LOCKED;detail=iron!=0?"Unavailable to Ironmen":"Members activity";}
        else {remaining=Math.max(0,15-bought);int affordable=Math.min(remaining,points/9500);status=remaining==0?Status.CLAIMED:affordable>0?Status.AVAILABLE:Status.NEEDS_PAYMENT;detail=remaining+" / 15 boxes remaining · "+affordable+" affordable · 9,500 points each";}
        return new Claim("herb-boxes","NMZ herb boxes","Rewards chest · Nightmare Zone",detail,"https://oldschool.runescape.wiki/w/Herb_box",null,status,15,remaining,9500,Set.of());
    }
    private static Set<Integer> resolve(String name)
    {return name==null?Set.of():GuideItemResolver.resolveName(name);}
    static Instant nextReset(Instant now){return now.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();}
}
