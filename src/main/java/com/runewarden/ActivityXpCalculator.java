/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import net.runelite.api.Experience;

/** Pure, independent activity scenarios; does not spend the bank or allocate recipe outputs. */
public final class ActivityXpCalculator
{
    private ActivityXpCalculator() { }

    public static final class Estimate
    {
        public final long units;
        public final double xp;
        public final Map<String, Long> supplies;
        public final String basis;
        Estimate(long units, double xp, Map<String, Long> supplies, String basis)
        { this.units = units; this.xp = xp; this.supplies = Collections.unmodifiableMap(supplies); this.basis = basis; }
    }

    public static double hours(long currentXp, long targetXp, double xpPerHour)
    {
        if (!Double.isFinite(xpPerHour) || xpPerHour <= 0) throw new IllegalArgumentException("Enter a positive XP/hour.");
        return Math.max(0, targetXp - currentXp) / xpPerHour;
    }

    public static final class Segment
    {
        public final int level; public final long xp; public final double fastestHours, slowestHours;
        Segment(int level, long xp, double fast, double slow) { this.level=level; this.xp=xp; fastestHours=fast; slowestHours=slow; }
    }
    public static final class Projection
    {
        public final long remainingXp, unpricedXp; public final double fastestHours, slowestHours;
        public final boolean locked; public final List<Segment> segments;
        Projection(long xp,long unknown,double fast,double slow,boolean locked,List<Segment> segments)
        {remainingXp=xp;unpricedXp=unknown;fastestHours=fast;slowestHours=slow;this.locked=locked;this.segments=Collections.unmodifiableList(segments);}
        public boolean complete() { return !locked && unpricedXp==0; }
    }
    /** Integrates each level band separately. Missing source bands are not interpolated or extrapolated. */
    public static Projection project(ActivityXpCatalog.Method method,long currentXp,long targetXp,Double personalRate)
    {
        if(method==null||currentXp<0||targetXp<0||currentXp>200_000_000L||targetXp>200_000_000L)
            throw new IllegalArgumentException("Invalid training target.");
        if(personalRate!=null&&(!Double.isFinite(personalRate)||personalRate<=0))throw new IllegalArgumentException("Enter a positive XP/hour.");
        long remaining=Math.max(0,targetXp-currentXp);int first=Math.min(99,Experience.getLevelForXp((int)currentXp));
        boolean locked=first<method.level; List<Segment> segments=new ArrayList<>();long unknown=0;double fast=0,slow=0;
        if(locked||"oneoff".equals(method.kind))return new Projection(remaining,remaining,0,0,locked,segments);
        for(int level=first;level<=99&&currentXp<targetXp;level++)
        {
            long end=level==99?targetXp:Math.min(targetXp,Experience.getXpForLevel(level+1));
            long delta=Math.max(0,end-currentXp);if(delta==0)continue;
            ActivityXpCatalog.Rate rate=method.ratesAt(level);double low=personalRate!=null?personalRate:rate==null?0:rate.low(level);
            double high=personalRate!=null?personalRate:rate==null?0:rate.high(level);
            if(low<=0||high<=0){unknown+=delta;segments.add(new Segment(level,delta,Double.NaN,Double.NaN));}
            else{double f=delta/high,s=delta/low;fast+=f;slow+=s;segments.add(new Segment(level,delta,f,s));}
            currentXp=end;
        }
        return new Projection(remaining,unknown,fast,slow,false,segments);
    }
    public static long actionsFor(long remainingXp,double xpPerAction)
    {
        if(remainingXp<0||!Double.isFinite(xpPerAction)||xpPerAction<=0)throw new IllegalArgumentException("Invalid XP per action.");
        return (long)Math.ceil(remainingXp/xpPerAction);
    }

    public static long foundryHandInXp(int quality)
    {
        if (quality < 0 || quality > 199) throw new IllegalArgumentException("Quality must be 0–199.");
        return ((long) Math.floor((double) quality * quality / 73) + (long) Math.floor(1.5 * quality) + 1) * 30;
    }

    public static Map<String, Long> metalBars(List<ActivityXpCatalog.Metal> metals, Map<Integer, Long> bank)
    {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ActivityXpCatalog.Metal metal : metals)
        {
            long bars = 0;
            for (ActivityXpCatalog.Material material : metal.items)
                bars = Math.addExact(bars, Math.multiplyExact(Math.max(0, bank.getOrDefault(material.itemId, 0L)), material.bars));
            result.put(metal.name, bars);
        }
        return result;
    }

    public static Estimate foundry(ActivityXpCatalog.Method method, List<ActivityXpCatalog.Metal> metals,
        Map<Integer, Long> bank, int level, int quality)
    {
        Map<String, Long> bars = metalBars(metals, bank);
        long units = level < method.level ? 0 : Math.min(bars.getOrDefault(method.lowerMetal, 0L) / method.lowerBars,
            bars.getOrDefault(method.upperMetal, 0L) / method.upperBars);
        return new Estimate(units, units * (double) foundryHandInXp(quality), bars, "Hand-in XP only; forging XP additional");
    }

    public static Estimate homes(ActivityXpCatalog.Method method, Map<Integer, Long> bank, int level, boolean outfit)
    {
        long planks = Math.max(0, bank.getOrDefault(method.plankId, 0L));
        long bars = Math.max(0, bank.getOrDefault(2353, 0L));
        long units = level < method.level ? 0 : (long) Math.floor(Math.min(planks / method.planksPerUnit, bars / method.barsPerUnit));
        return new Estimate(units, units * method.xpPerUnit * (outfit ? 1.025 : 1),
            Map.of("planks", planks, "steel bars", bars), "Average contract XP; assignments vary about ±10%");
    }

    public static Estimate mixology(ActivityXpCatalog.Potion potion, List<ActivityXpCatalog.Herb> herbs,
        Map<Integer, Long> bank, int level)
    {
        Map<String, Long> paste = new LinkedHashMap<>();
        paste.put("mox", 0L); paste.put("aga", 0L); paste.put("lye", 0L);
        long refined = 0;
        for (ActivityXpCatalog.Herb herb : herbs) for (int itemId : herb.itemIds)
        {
            long quantity = Math.max(0, bank.getOrDefault(itemId, 0L));
            paste.merge(herb.paste, Math.multiplyExact(quantity, herb.amount), Math::addExact);
            refined = Math.addExact(refined, quantity);
        }
        long units = Long.MAX_VALUE;
        for (String kind : new String[]{"mox", "aga", "lye"})
        {
            char letter = kind.equals("mox") ? 'M' : kind.equals("aga") ? 'A' : 'L';
            long inputs = potion.code.chars().filter(c -> c == letter).count() * 10;
            if (inputs > 0) units = Math.min(units, paste.get(kind) / inputs);
        }
        if (units == Long.MAX_VALUE || level < Math.max(60, potion.level)) units = 0;
        paste.put("refining XP (separate)", level < 60 ? 0 : refined * 6);
        return new Estimate(units, units * potion.xp, paste, "Requested-potion XP; refining shown separately; no speedup/digweed XP");
    }

    public static Estimate rift(int level, long successfulRounds)
    {
        if (level < 1 || level > 99 || successfulRounds < 0) throw new IllegalArgumentException("Invalid rounds or level.");
        return new Estimate(level < 27 ? 0 : successfulRounds, level < 27 ? 0 : successfulRounds * 45.0 * level,
            Collections.emptyMap(), "Completion bonus only; at least 300 energy per round; crafting XP additional");
    }
}
