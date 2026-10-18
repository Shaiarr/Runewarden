/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.HashMap;
import java.util.Objects;

/** Manual, deterministic Wiki scenarios. Successful drains are inputs, never inferred from combat. */
final class DpsTargetScenario
{
    static final class Inputs
    {
        String phase;
        Integer phaseHp;
        int maul, warhammer, arclight, emberlight, tonalztic, bgs, seercull, ayak;
        boolean vulnerability, accursed;
        @Override public int hashCode(){return Objects.hash(phase,phaseHp,maul,warhammer,arclight,emberlight,tonalztic,bgs,seercull,ayak,vulnerability,accursed);}
    }
    static boolean valid(Inputs i)
    {
        if(i==null)return true;
        return (i.phase==null||i.phase.equals("Araxxor normal")||i.phase.equals("Araxxor enraged")||i.phase.equals("Vardorvis HP snapshot"))
            &&(i.phaseHp==null||i.phaseHp>=0&&i.phaseHp<=50000)&&i.maul>=0&&i.maul<=20&&i.warhammer>=0&&i.warhammer<=20
            &&i.arclight>=0&&i.arclight<=20&&i.emberlight>=0&&i.emberlight<=20&&i.tonalztic>=0&&i.tonalztic<=40
            &&i.bgs>=0&&i.bgs<=2000&&i.seercull>=0&&i.seercull<=2000&&i.ayak>=0&&i.ayak<=2000&&!(i.accursed&&i.vulnerability);
    }
    static DpsReferenceCatalog.Target apply(DpsReferenceCatalog.Target original,Inputs i)
    {
        if(original==null)return null;
        if(!valid(i))throw new IllegalArgumentException("Target scenario inputs are out of range");
        DpsReferenceCatalog.Target t=DpsReferenceCatalog.scenario(original,null,null,null);
        if(i==null)return t;
        if(i.phase!=null)
        {
            if(i.phase.startsWith("Araxxor")&&"Araxxor".equals(t.name)&&t.id==13668)
            {
                t.unsupportedReason=null;
                if(i.phase.equals("Araxxor enraged")){t.defenceLevel+=35;t.magicLevel+=28;}
                t.snapshotOnly=true;t.scenarioDescription="Manual " + i.phase + " snapshot";
            }
            else if(i.phase.equals("Vardorvis HP snapshot")&&"Vardorvis".equals(t.name))
            {
                int max=t.hitpoints==null?0:t.hitpoints;
                int start="Quest".equals(t.version)?180:"Awakened".equals(t.version)?268:215;
                int end="Quest".equals(t.version)?130:"Awakened".equals(t.version)?181:145;
                int hp=i.phaseHp==null?max:i.phaseHp;
                if(max<=0||hp>max||!("Quest".equals(t.version)||"Post-quest".equals(t.version)||"Awakened".equals(t.version)))
                    throw new IllegalArgumentException("Select a supported Vardorvis variant and HP within its maximum");
                t.defenceLevel=(int)(start+(double)(max-hp)*(end-start)/max);
                t.unsupportedReason=null;t.snapshotOnly=true;
                t.scenarioDescription="Manual Vardorvis snapshot at " + hp + "/" + max + " HP · Defence changes during the fight";
            }
            else throw new IllegalArgumentException("Selected phase does not belong to this target");
        }
        if(t.defenceLevel==null||t.magicLevel==null)return t;
        int baseDef=t.defenceLevel,def=baseDef,magic=t.magicLevel,floor=defenceFloor(t);
        if(i.accursed){def=retainFraction(def,17,20,floor);magic=retainFraction(magic,17,20,0);}
        else if(i.vulnerability)def=retainFraction(def,9,10,floor);
        def=drainCurrentFraction(def,i.maul,35,100,floor);
        def=drainCurrentFraction(def,i.warhammer,3,10,floor);
        int arclightDrain=baseDef*(t.has("demon")?2:1)/20+1;
        int emberlightDrain=baseDef*(t.has("demon")?3:1)/20+1;
        def=drainAmount(def,i.arclight*arclightDrain,floor);
        def=drainAmount(def,i.emberlight*emberlightDrain,floor);
        def=drainRepeatedAmount(def,i.tonalztic,magic/8,floor);
        magic=drainAmount(magic,i.seercull,0);
        if(i.bgs>def&&floor==0)
        {
            // Full BGS propagation needs Strength/Attack levels, absent from the compact target catalogue.
            t.unsupportedReason="BGS overflow needs full target stats; clear the BGS scenario and enter final Defence/Magic levels manually";
        }
        def=drainAmount(def,i.bgs,floor);
        t.defenceLevel=def;t.magicLevel=magic;
        if(i.ayak>0&&t.defenceBonuses!=null)
        {t.defenceBonuses=new HashMap<>(t.defenceBonuses);int bonus=t.defenceBonuses.getOrDefault("magic",0);if(bonus>0)t.defenceBonuses.put("magic",drainAmount(bonus,i.ayak,0));}
        if(i.maul+i.warhammer+i.arclight+i.emberlight+i.tonalztic+i.bgs+i.seercull+i.ayak>0||i.vulnerability||i.accursed)
            t.scenarioDescription=(t.scenarioDescription==null?"":t.scenarioDescription+"\n")+"Manual successful drains · Defence " + def + " (floor " + floor + ") · Magic " + magic;
        return t;
    }
    static int defenceFloor(DpsReferenceCatalog.Target t)
    {
        int low=0,high=DEFENCE_RULES.length-1;
        while(low<=high)
        {
            int middle=(low+high)>>>1;
            int[] rule=DEFENCE_RULES[middle];
            if(t.id<rule[0])high=middle-1;
            else if(t.id>rule[0])low=middle+1;
            else return rule[1]==DRAIN_IMMUNE?(t.defenceLevel==null?0:t.defenceLevel):rule[1];
        }
        return 0;
    }

    private static int drainAmount(int level,int amount,int floor)
    {return Math.max(floor,level-amount);}

    private static int retainFraction(int level,int numerator,int denominator,int floor)
    {return Math.max(floor,level*numerator/denominator);}

    private static int drainRepeatedAmount(int level,int successes,int amount,int floor)
    {
        for(int remaining=successes;remaining>0;--remaining)level=drainAmount(level,amount,floor);
        return level;
    }

    private static int drainCurrentFraction(int level,int successes,int numerator,int denominator,int floor)
    {
        for(int remaining=successes;remaining>0;--remaining)
            level=drainAmount(level,level*numerator/denominator,floor);
        return level;
    }

    private static final int DRAIN_IMMUNE=-1;

    /** Gameplay facts, sorted by NPC ID. Immunity keeps the scenario's starting Defence. */
    private static final int[][] DEFENCE_RULES={
        {377,120},
        {378,120},
        {8369,DRAIN_IMMUNE},
        {8370,DRAIN_IMMUNE},
        {8371,DRAIN_IMMUNE},
        {8372,DRAIN_IMMUNE},
        {8373,DRAIN_IMMUNE},
        {8374,DRAIN_IMMUNE},
        {8387,100},
        {8388,100},
        {9416,120},
        {9417,120},
        {9418,120},
        {9419,120},
        {9420,120},
        {9421,120},
        {9422,120},
        {9423,120},
        {9424,120},
        {9425,120},
        {9426,120},
        {9427,120},
        {9428,120},
        {9429,120},
        {9430,120},
        {9431,120},
        {9432,120},
        {9433,120},
        {9460,120},
        {10830,DRAIN_IMMUNE},
        {10831,DRAIN_IMMUNE},
        {10832,DRAIN_IMMUNE},
        {10833,DRAIN_IMMUNE},
        {10834,DRAIN_IMMUNE},
        {10835,DRAIN_IMMUNE},
        {10847,DRAIN_IMMUNE},
        {10848,DRAIN_IMMUNE},
        {10849,DRAIN_IMMUNE},
        {10850,DRAIN_IMMUNE},
        {10851,DRAIN_IMMUNE},
        {10852,DRAIN_IMMUNE},
        {10867,100},
        {10868,100},
        {11153,120},
        {11154,120},
        {11155,120},
        {11278,250},
        {11279,250},
        {11280,250},
        {11281,250},
        {11282,250},
        {11719,60},
        {11721,60},
        {11730,50},
        {11732,50},
        {11733,50},
        {11750,60},
        {11751,60},
        {11752,60},
        {11761,120},
        {11762,120},
        {11763,120},
        {11764,120},
        {11778,60},
        {11779,60},
        {11780,60},
        {11789,70},
        {11790,70},
        {11791,70},
        {11792,70},
        {11793,70},
        {11794,70},
        {11795,70},
        {11796,70},
        {12223,DRAIN_IMMUNE},
        {12224,DRAIN_IMMUNE},
        {12228,DRAIN_IMMUNE},
        {12425,DRAIN_IMMUNE},
        {12426,DRAIN_IMMUNE},
        {13656,DRAIN_IMMUNE},
        {13668,90},
        {14009,120},
        {14010,120},
        {14013,120},
        {14014,120},
        {14017,120},
        {14176,145}
    };
}
