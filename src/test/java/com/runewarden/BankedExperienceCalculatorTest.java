/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankedExperienceCalculatorTest
{
    private static BankedExperienceCalculator.Recipe recipe(String id)
    { return BankedExperienceCalculator.recipes().stream().filter(r -> r.id.equals(id)).findFirst().get(); }

    @Test public void bundledRecipesHaveUniqueIdsAndSupportedEightSkills()
    {
        List<BankedExperienceCalculator.Recipe> recipes = BankedExperienceCalculator.recipes();
        assertEquals(210, recipes.size());
        assertEquals(recipes.size(), recipes.stream().map(r -> r.id).distinct().count());
        assertEquals(8, recipes.stream().map(r -> r.skill).distinct().count());
        assertEquals(196,recipes.stream().filter(r -> r.xp>0).count());
        assertTrue(recipes.stream().allMatch(r -> r.source.startsWith("RuneLite 1.13.2") || r.source.startsWith("OSRS Wiki Module:")));
    }

    @Test public void diamondBraceletsRequireBothDiamondsAndGoldBars()
    {
        BankedExperienceCalculator.Recipe diamond = recipe("crafting:diamond_bracelet");
        Map<Integer, Long> bank = Map.of(1601, 1000L, 2357, 37L);
        BankedExperienceCalculator.Result result = BankedExperienceCalculator.allocate(Collections.singletonList(diamond), bank, 99);
        assertEquals(37, result.allocations.get(0).actions);
        assertEquals(3515, result.bankedXp, .001);
        assertEquals(Long.valueOf(963), result.remaining.get(1601));
        assertEquals(Long.valueOf(0), result.remaining.get(2357));
        assertEquals(Long.valueOf(1000), bank.get(1601));
    }

    @Test public void sharedGoldAndFeathersAreAllocatedOnceAndNoOutputIsReused()
    {
        BankedExperienceCalculator.Recipe diamond = recipe("crafting:diamond_bracelet");
        BankedExperienceCalculator.Recipe ruby = recipe("crafting:ruby_bracelet");
        BankedExperienceCalculator.Result craft = BankedExperienceCalculator.allocate(Arrays.asList(diamond, ruby),
            Map.of(1601, 100L, 1603, 100L, 2357, 120L), 99);
        assertEquals(100, craft.allocations.get(0).actions);
        assertEquals(20, craft.allocations.get(1).actions);
        BankedExperienceCalculator.Recipe headless = recipe("fletching:headless_arrow"), dart = recipe("fletching:mithril_dart");
        BankedExperienceCalculator.Result fletch = BankedExperienceCalculator.allocate(Arrays.asList(headless, dart),
            Map.of(52, 50L, 314, 60L, 822, 100L), 99);
        assertEquals(50, fletch.allocations.get(0).actions);
        assertEquals(10, fletch.allocations.get(1).actions);
        assertFalse(fletch.remaining.containsKey(53)); // Produced headless arrows aren't banked yet.
    }

    @Test public void ordinaryFurnaceRatiosAndMahoganyTableAreExact()
    {
        BankedExperienceCalculator.Recipe steel = recipe("smithing:steel_bar");
        BankedExperienceCalculator.Result result = BankedExperienceCalculator.allocate(Collections.singletonList(steel),
            Map.of(440, 100L, 453, 99L), 99);
        assertEquals(49, result.allocations.get(0).actions);
        assertEquals(857.5, result.bankedXp, .001);
        BankedExperienceCalculator.Recipe table = recipe("construction:mahogany_table");
        assertEquals(2, BankedExperienceCalculator.availableActions(table, Map.of(8782, 17L)));
        assertEquals(Map.of(8782, 1L), BankedExperienceCalculator.missing(table, 3, Map.of(8782, 17L)));
    }

    @Test public void inaccessibleRecipesDoNotConsumeMaterialsOrAddXp()
    {
        BankedExperienceCalculator.Recipe light = recipe("crafting:light_orb"), glass = recipe("crafting:beer_glass");
        BankedExperienceCalculator.Result result = BankedExperienceCalculator.allocate(Arrays.asList(light, glass), Map.of(1775, 100L), 10);
        assertTrue(result.allocations.get(0).locked);
        assertEquals(0, result.allocations.get(0).actions);
        assertEquals(100, result.allocations.get(1).actions);
        assertEquals(1750, result.bankedXp, .001);
    }

    @Test public void alternateBoneMethodsCannotCountTheSameBonesTwice()
    {
        BankedExperienceCalculator.Recipe altar = recipe("prayer:dragon_bones:gilded"), bury = recipe("prayer:dragon_bones");
        BankedExperienceCalculator.Result result = BankedExperienceCalculator.allocate(Arrays.asList(altar, bury), Map.of(536, 100L), 99);
        assertEquals(25200, result.bankedXp, .001);
        assertEquals(0, result.allocations.get(1).actions);
    }

    @Test public void largeStacksUseLongArithmeticAndMissingIngredientsRemainExact()
    {
        BankedExperienceCalculator.Recipe arrow = recipe("fletching:headless_arrow");
        Map<Integer, Long> bank = Map.of(52, 4_294_967_294L, 314, 4_294_967_294L);
        BankedExperienceCalculator.Result result = BankedExperienceCalculator.allocate(Collections.singletonList(arrow), bank, 99);
        assertEquals(4_294_967_294L, result.allocations.get(0).actions);
        assertEquals(99, result.projectedLevel(13_034_431));
        assertEquals(Map.of(52, 706L, 314, 706L), BankedExperienceCalculator.missing(arrow, 4_294_968_000L, bank));
    }
}
