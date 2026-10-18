/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.*;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductionChainTest
{
    private static BankedExperienceCalculator.Recipe recipe(String id)
    { return BankedExperienceCalculator.recipes().stream().filter(r -> r.id.equals(id)).findFirst().get(); }
    private static Map<Skill,Integer> maxed()
    { Map<Skill,Integer> levels=new EnumMap<>(Skill.class); for (Skill skill : Skill.values()) levels.put(skill,99); return levels; }
    private static long actions(BankedExperienceCalculator.Result result,String id)
    { return result.allocations.stream().filter(a -> a.recipe.id.equals(id)).findFirst().get().actions; }

    @Test public void uncutDiamondsFlowToBraceletsEvenWhenTheConsumerWasChosenFirst()
    {
        BankedExperienceCalculator.Recipe cut=recipe("crafting:diamond"),bracelet=recipe("crafting:diamond_bracelet");
        Map<Integer,Long> bank=Map.of(1617,100L,2357,40L);
        BankedExperienceCalculator.Result result=BankedExperienceCalculator.allocateChained(Arrays.asList(bracelet,cut),bank,maxed());
        assertEquals("crafting:diamond",result.allocations.get(0).recipe.id); assertEquals(100,actions(result,cut.id)); assertEquals(40,actions(result,bracelet.id));
        assertEquals(14550,result.xp(Skill.CRAFTING),.001); assertEquals(Long.valueOf(60),result.remaining.get(1601));
        assertEquals(Long.valueOf(40),result.remaining.get(11092)); assertEquals(Long.valueOf(100),bank.get(1617));
    }

    @Test public void sharedFeathersAreConsumedOnceAcrossHeadlessArrowsAndDarts()
    {
        List<BankedExperienceCalculator.Recipe> selected=Arrays.asList(recipe("fletching:rune_arrow"),recipe("fletching:headless_arrow"),recipe("fletching:mithril_dart"));
        BankedExperienceCalculator.Result result=BankedExperienceCalculator.allocateChained(selected,Map.of(52,50L,314,60L,44,100L,822,100L),maxed());
        assertEquals(50,actions(result,"fletching:headless_arrow")); assertEquals(50,actions(result,"fletching:rune_arrow")); assertEquals(10,actions(result,"fletching:mithril_dart"));
        assertEquals(Long.valueOf(0),result.remaining.get(314)); assertEquals(Long.valueOf(50),result.remaining.get(892));
    }

    @Test public void grimyHerbUnfinishedAndPotionStepsDoNotInventPreparationXp()
    {
        List<BankedExperienceCalculator.Recipe> selected=Arrays.asList(recipe("herblore:prayer_potion_3"),recipe("herblore:unfinished:99"),recipe("herblore:ranarr_weed"));
        BankedExperienceCalculator.Result result=BankedExperienceCalculator.allocateChained(selected,Map.of(207,20L,227,20L,231,12L),maxed());
        assertEquals(20,actions(result,"herblore:ranarr_weed")); assertEquals(20,actions(result,"herblore:unfinished:99")); assertEquals(12,actions(result,"herblore:prayer_potion_3"));
        assertEquals(1200,result.xp(Skill.HERBLORE),.001); assertEquals(Long.valueOf(8),result.remaining.get(99));
    }

    @Test public void shoppingUsesOneSharedStockAndBuysRawSelectedInputs()
    {
        List<BankedExperienceCalculator.Recipe> selected=Arrays.asList(recipe("herblore:ranarr_weed"),recipe("herblore:unfinished:99"));
        BankedExperienceCalculator.ShoppingPlan plan=BankedExperienceCalculator.shopping(recipe("herblore:prayer_potion_3"),20,Map.of(207,10L,257,5L,227,12L,231,3L),selected,maxed());
        assertEquals(Map.of(207,5L,227,8L,231,17L),plan.purchases); assertEquals(3,plan.steps.size());
        assertEquals(1862.5,plan.xp(Skill.HERBLORE),.001); assertEquals(Long.valueOf(20),plan.remaining.get(139));
    }

    @Test public void targetShoppingCountsUpstreamExperienceAndChoosesMinimalFinalActions()
    {
        List<BankedExperienceCalculator.Recipe> selected=Collections.singletonList(recipe("crafting:diamond"));
        BankedExperienceCalculator.ShoppingPlan plan=BankedExperienceCalculator.shoppingForTarget(recipe("crafting:diamond_bracelet"),1000,Collections.emptyMap(),selected,maxed());
        assertEquals(5,plan.steps.get(plan.steps.size()-1).actions); assertEquals(1012.5,plan.xp(Skill.CRAFTING),.001);
        assertEquals(Map.of(1617,5L,2357,5L),plan.purchases);
    }

    @Test public void unrelatedSkillXpDoesNotCountTowardsTheChosenGoal()
    {
        BankedExperienceCalculator.ShoppingPlan plan=BankedExperienceCalculator.shoppingForTarget(recipe("crafting:diamond_bracelet"),950,Collections.emptyMap(),
            Collections.singletonList(recipe("smithing:gold_bar")),maxed());
        assertEquals(950,plan.xp(Skill.CRAFTING),.001); assertEquals(225,plan.xp(Skill.SMITHING),.001);
        assertEquals(Map.of(1601,10L,444,10L),plan.purchases);
    }

    @Test public void lockedUpstreamMethodIsBoughtAsIntermediateAndLockedFinalMethodIsRejected()
    {
        Map<Skill,Integer> levels=maxed(); levels.put(Skill.SMITHING,1);
        BankedExperienceCalculator.ShoppingPlan plan=BankedExperienceCalculator.shopping(recipe("crafting:diamond_bracelet"),2,Collections.emptyMap(),Collections.singletonList(recipe("smithing:gold_bar")),levels);
        assertEquals(Map.of(1601,2L,2357,2L),plan.purchases);
        levels.put(Skill.CRAFTING,1);
        try { BankedExperienceCalculator.shopping(recipe("crafting:diamond_bracelet"),2,Collections.emptyMap(),Collections.emptyList(),levels); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void noBurnFreeCookingOutputOrReusablePrayerInputIsInferred()
    {
        assertTrue(recipe("cooking:shark").products.isEmpty()); assertTrue(recipe("prayer:dragon_bones").products.isEmpty());
        assertTrue(recipe("firemaking:yew_logs").products.isEmpty());
    }

    @Test public void overflowFailsWithoutCorruptingOriginalStock()
    {
        Map<Integer,Long> bank=Map.of(1617,Long.MAX_VALUE,1601,Long.MAX_VALUE);
        try { BankedExperienceCalculator.allocateChained(Collections.singletonList(recipe("crafting:diamond")),bank,maxed()); fail(); }
        catch (ArithmeticException expected) { }
        assertEquals(Long.valueOf(Long.MAX_VALUE),bank.get(1601));
    }
}
