/* SPDX-License-Identifier: GPL-3.0-only */
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
        if(i.accursed){def=Math.max(floor,def*17/20);magic=magic*17/20;}
        else if(i.vulnerability)def=Math.max(floor,def*9/10);
        for(int n=0;n<i.maul;n++)def=Math.max(floor,def-def*35/100);
        for(int n=0;n<i.warhammer;n++)def=Math.max(floor,def-def*3/10);
        def=Math.max(floor,def-i.arclight*(baseDef*(t.has("demon")?2:1)/20+1));
        def=Math.max(floor,def-i.emberlight*(baseDef*(t.has("demon")?3:1)/20+1));
        for(int n=0;n<i.tonalztic;n++)def=Math.max(floor,def-magic/8);
        magic=Math.max(0,magic-i.seercull);
        if(i.bgs>def&&floor==0)
        {
            // Full BGS propagation needs Strength/Attack levels, absent from the compact target catalogue.
            t.unsupportedReason="BGS overflow needs full target stats; clear the BGS scenario and enter final Defence/Magic levels manually";
        }
        def=Math.max(floor,def-i.bgs);
        t.defenceLevel=def;t.magicLevel=magic;
        if(i.ayak>0&&t.defenceBonuses!=null)
        {t.defenceBonuses=new HashMap<>(t.defenceBonuses);int bonus=t.defenceBonuses.getOrDefault("magic",0);if(bonus>0)t.defenceBonuses.put("magic",Math.max(0,bonus-i.ayak));}
        if(i.maul+i.warhammer+i.arclight+i.emberlight+i.tonalztic+i.bgs+i.seercull+i.ayak>0||i.vulnerability||i.accursed)
            t.scenarioDescription=(t.scenarioDescription==null?"":t.scenarioDescription+"\n")+"Manual successful drains · Defence " + def + " (floor " + floor + ") · Magic " + magic;
        return t;
    }
    static int defenceFloor(DpsReferenceCatalog.Target t)
    {
        switch(t.id)
        {
            case 12223:case 12224:case 12228:case 12425:case 12426:case 13656:return t.defenceLevel==null?0:t.defenceLevel;
            case 10833:case 10834:case 10835:case 8372:case 8373:case 8374:case 10850:case 10851:case 10852:case 10830:case 10831:case 10832:case 8369:case 8370:case 8371:case 10847:case 10848:case 10849:return t.defenceLevel==null?0:t.defenceLevel;
            case 8387:case 8388:case 10867:case 10868:return 100;
            case 378:case 9425:case 9426:case 9427:case 9428:case 9429:case 9430:case 9431:case 9432:case 9433:case 9460:case 377:case 9423:case 9416:case 9417:case 9418:case 9419:case 9420:case 9421:case 9422:case 9424:case 11153:case 11154:case 11155:return 120;
            case 11789:case 11790:case 11791:case 11792:case 11793:case 11794:case 11795:case 11796:return 70;
            case 11778:case 11779:case 11780:return 60;
            case 11721:return 60;
            case 11719:return 60;
            case 11730:case 11732:case 11733:return 50;
            case 11761:case 11763:case 11762:case 11764:return 120;
            case 11751:case 11750:case 11752:return 60;
            case 11278:case 11279:case 11280:case 11281:case 11282:return 250;
            case 13668:return 90;
            case 14009:case 14010:case 14013:case 14017:case 14014:return 120;
            case 14176:return 145;
            default:return 0;
        }
    }
}
