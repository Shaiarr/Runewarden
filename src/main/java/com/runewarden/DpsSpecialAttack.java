/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Locale;

/** Explicit workbench attacks, matched against the pinned Wiki calculator. No live spec state is inferred. */
final class DpsSpecialAttack
{
    final String weapon, defenceStyle;
    final int energy, accuracyNumerator, accuracyDenominator, damageNumerator, damageDenominator, hits;
    final boolean godsword, guaranteed, conditionalSecond, halberd, fang;
    private DpsSpecialAttack(String weapon, String defenceStyle, int energy, int an, int ad, int dn, int dd,
        int hits, boolean godsword, boolean guaranteed, boolean conditionalSecond, boolean halberd, boolean fang)
    {
        this.weapon=weapon; this.defenceStyle=defenceStyle; this.energy=energy; accuracyNumerator=an; accuracyDenominator=ad;
        damageNumerator=dn; damageDenominator=dd; this.hits=hits; this.godsword=godsword; this.guaranteed=guaranteed;
        this.conditionalSecond=conditionalSecond; this.halberd=halberd; this.fang=fang;
    }
    static DpsSpecialAttack forWeapon(String raw)
    {
        String n=raw==null?"":raw.toLowerCase(Locale.ROOT).replace("(p++)","").replace("(p+)","").replace("(p)","").trim();
        if(n.startsWith("osmumten's fang"))return p(n,null,25,3,2,1,1,1,false,false,false,false,true);
        if(n.equals("voidwaker"))return p(n,"magic",50,1,1,1,1,1,false,true,false,false,false);
        if(n.equals("dragon dagger"))return p(n,"slash",25,23,20,23,20,2,false,false,false,false,false);
        if(n.startsWith("abyssal dagger"))return p(n,"slash",25,5,4,17,20,2,false,false,true,false,false);
        if(n.startsWith("dragon halberd")||n.startsWith("crystal halberd"))return p(n,"slash",30,1,1,11,10,1,false,false,false,true,false);
        if(n.equals("elder maul"))return p(n,null,50,5,4,1,1,1,false,false,false,false,false);
        if(n.equals("dragon warhammer"))return p(n,null,50,1,1,3,2,1,false,false,false,false,false);
        if(n.equals("dragon mace"))return p(n,"crush",25,5,4,3,2,1,false,false,false,false,false);
        if(n.equals("dragon sword"))return p(n,"stab",40,5,4,5,4,1,false,false,false,false,false);
        if(n.equals("dragon longsword"))return p(n,"slash",25,1,1,5,4,1,false,false,false,false,false);
        if(n.equals("dragon scimitar"))return p(n,"slash",55,5,4,1,1,1,false,false,false,false,false);
        if(n.equals("arclight")||n.equals("emberlight"))return p(n,"stab",50,1,1,1,1,1,false,false,false,false,false);
        if(n.startsWith("abyssal whip"))return p(n,null,50,5,4,1,1,1,false,false,false,false,false);
        if(n.startsWith("bandos godsword"))return p(n,"slash",50,2,1,11,10,1,true,false,false,false,false);
        if(n.startsWith("armadyl godsword"))return p(n,"slash",50,2,1,5,4,1,true,false,false,false,false);
        if(n.startsWith("saradomin godsword")||n.startsWith("zamorak godsword"))return p(n,"slash",50,2,1,1,1,1,true,false,false,false,false);
        if(n.equals("toxic blowpipe")||n.equals("blazing blowpipe"))return p(n,null,50,2,1,3,2,1,false,false,false,false,false);
        if(n.equals("rosewood blowpipe"))return p(n,null,25,2,1,3,2,2,false,false,false,false,false);
        if(n.equals("dragon knife"))return p(n,null,25,1,1,1,1,2,false,false,false,false,false);
        if(n.equals("heavy ballista")||n.equals("light ballista"))return p(n,null,65,5,4,5,4,1,false,false,false,false,false);
        return null;
    }
    private static DpsSpecialAttack p(String n,String style,int e,int an,int ad,int dn,int dd,int h,boolean g,boolean sure,boolean conditional,boolean halberd,boolean fang)
    {return new DpsSpecialAttack(n,style,e,an,ad,dn,dd,h,g,sure,conditional,halberd,fang);}
    long accuracyRoll(long normal){return normal*accuracyNumerator/accuracyDenominator;}
    int[] damageRange(int normalMin,int normalMax)
    {
        int min=normalMin,max=normalMax;
        if(fang)max+=normalMin;
        if(godsword)max=max*11/10;
        if(guaranteed){min=max/2;max+=min;}
        else max=max*damageNumerator/damageDenominator;
        return new int[]{Math.min(min,max),max};
    }
    double[] distribution(double accuracy,int min,int max,int armour,int size,long attack,long defence)
    {
        double[] first=DpsCalculator.hitDistribution(guaranteed?1:accuracy,min,max,armour);
        if(conditionalSecond)
        {
            // The second dagger hit has no accuracy roll, but only exists when the first accuracy roll passed.
            double[] both=DpsCalculator.convolve(DpsCalculator.hitDistribution(1,min,max,armour),DpsCalculator.hitDistribution(1,min,max,armour));
            for(int i=0;i<both.length;i++)both[i]*=accuracy;
            both[0]+=1-accuracy;return both;
        }
        if(halberd&&size>1)return DpsCalculator.convolve(first,DpsCalculator.hitDistribution(DpsCalculator.normalAccuracy(attack*3/4,defence),min,max,armour));
        double[] result=first;for(int i=1;i<hits;i++)result=DpsCalculator.convolve(result,first);return result;
    }
    String note()
    {
        if(weapon.contains("warhammer")||weapon.equals("elder maul")||weapon.equals("arclight")||weapon.equals("emberlight")||weapon.contains("bandos godsword"))
            return "Damage for one special · apply successful drains separately in Target scenario";
        if(weapon.contains("blowpipe")||weapon.contains("saradomin godsword"))return "Damage for one special · healing is separate";
        return "Damage for one special · energy regeneration and encounter downtime are separate";
    }
}
