/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TrainingGuideProgressTest
{
    @Test public void openingWithAlreadyWithdrawnBraceletInputsStartsAtCraftingWithoutANewInventoryEvent()
    {
        Fixture f=new Fixture();f.inventory(new Item(1601,13),new Item(2357,13),new Item(ItemID.JEWL_BRACELET_MOULD,1));
        GuideLiveProgress.Reading reading=f.tool.read("training:diamond-bracelets");
        assertTrue(reading.known());assertEquals("method:2",reading.stepId);assertTrue(reading.confirmedStepIds.contains("method:1"));assertFalse(reading.terminal);
        assertFalse("Price checks cannot be manufactured from holding supplies",reading.confirmedStepIds.contains("prepare:2"));
    }
    @Test public void incompleteToolsOrBankOnlyResourcesDoNotPretendWithdrawalIsFinished()
    {
        Fixture f=new Fixture();f.inventory(new Item(1601,13),new Item(2357,13));assertFalse(f.tool.read("training:diamond-bracelets").known());
        f.inventory();ItemContainer bank=mock(ItemContainer.class);when(bank.getItems()).thenReturn(new Item[]{new Item(1601,13),new Item(2357,13),new Item(ItemID.JEWL_BRACELET_MOULD,1)});
        when(f.base.client.getItemContainer(InventoryID.BANK)).thenReturn(bank);assertFalse(f.tool.read("training:diamond-bracelets").known());
    }
    @Test public void openingWithFinishedOutputGoesToBankingButDoesNotEndAnOpenEndedTrainingGuide()
    {
        Fixture f=new Fixture();f.inventory(new Item(11092,13),new Item(ItemID.JEWL_BRACELET_MOULD,1));
        GuideLiveProgress.Reading reading=f.tool.read("training:diamond-bracelets");assertEquals("method:3",reading.stepId);assertFalse(reading.terminal);
        // The pinned item-name index normalizes names to lowercase; identity and phase matter here.
        assertTrue(reading.body,reading.body.toLowerCase(java.util.Locale.ROOT).contains("diamond bracelet"));assertFalse(reading.body.contains("Item 11092"));
        assertTrue("Holding output cannot prove 500 crafts or a sale",reading.confirmedStepIds.isEmpty());
    }
    @Test public void bowStringingAlreadyPreparedDoesNotGoBackToCuttingLogs()
    {
        Fixture f=new Fixture();f.inventory(new Item(70,14),new Item(1777,14));
        assertEquals("method:3",f.tool.read("training:magic-longbows").stepId);
        f.inventory(new Item(859,14));assertEquals("method:3",f.tool.read("training:magic-longbows").stepId);
    }
    @Test public void explicitMatchingXpGoalClosesOnlyItsOwnGuideAndOnlyWhenReached()
    {
        Fixture f=new Fixture();f.inventory(new Item(1601,13),new Item(2357,13),new Item(ItemID.JEWL_BRACELET_MOULD,1));
        f.base.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"training:diamond-bracelets","Bracelets","training:diamond-bracelets","training:diamond-bracelets",Skill.CRAFTING,5000));
        when(f.base.client.getSkillExperience(Skill.CRAFTING)).thenReturn(4999);assertFalse(f.tool.read("training:diamond-bracelets").terminal);
        when(f.base.client.getSkillExperience(Skill.CRAFTING)).thenReturn(5000);assertTrue(f.tool.read("training:diamond-bracelets").terminal);assertFalse(f.tool.read("training:ruby-bracelets").terminal);
        f.base.profile.set("rsprofile.bob");assertFalse(f.tool.read("training:diamond-bracelets").terminal);
    }
    @Test public void allMappedRecipePoliciesReferToRealAuthoredGuideSteps()
    {
        Fixture f=new Fixture();int covered=0;
        for(ActivityXpCatalog.Method method:f.methods.methods(null))
        {
            if(method.recipeId==null)continue;
            BankedExperienceCalculator.Recipe recipe=BankedExperienceCalculator.recipes().stream().filter(r->r.id.equals(method.recipeId)).findFirst().orElse(null);if(recipe==null)continue;
            java.util.List<Item> items=new java.util.ArrayList<>();for(BankedExperienceCalculator.Ingredient input:recipe.ingredients)items.add(new Item(input.itemId,input.quantity));
            for(int tool:new int[]{ItemID.JEWL_BRACELET_MOULD,ItemID.GLASSBLOWINGPIPE,ItemID.CHISEL,ItemID.NEEDLE,ItemID.THREAD,ItemID.KNIFE,ItemID.TINDERBOX})items.add(new Item(tool,1));
            f.inventory(items.toArray(new Item[0]));GuideLiveProgress.Reading reading=f.tool.read(method.guideId);if(!reading.known())continue;
            assertTrue(method.guideId+" "+reading.stepId,f.guides.entry(method.guideId).flattened().stream().anyMatch(step->reading.stepId.equals(step.step.id)));covered++;
        }
        assertTrue("Cover the authored recipe families rather than one example",covered>=50);
    }
    @Test public void nativeFarmingContractAlreadyAssignedOrHarvestedSkipsRequestingTheSameContract()
    {
        Fixture f=new Fixture();when(f.base.client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_TYPE)).thenReturn(12);
        assertEquals("method:2",f.tool.read("training:farming-contracts").stepId);
        when(f.base.client.getVarbitValue(VarbitID.FARMGUILD_CONTRACT_COMPLETE)).thenReturn(1);
        GuideLiveProgress.Reading claim=f.tool.read("training:farming-contracts");assertEquals("method:3",claim.stepId);assertFalse("Claiming the reward is still a real task",claim.terminal);
    }
    @Test public void openingAtFourAlreadyFilledBirdHousesShowsReturnPhaseAndAlreadyBuiltHouseShowsSeeding()
    {
        Fixture f=new Fixture();when(f.base.player.getWorldLocation()).thenReturn(new WorldPoint(3700,3835,0));long now=System.currentTimeMillis()/1000;
        for(int varp:new int[]{VarPlayerID.BIRDHOUSE_TRANSMIT_A,VarPlayerID.BIRDHOUSE_TRANSMIT_B,VarPlayerID.BIRDHOUSE_TRANSMIT_C,VarPlayerID.BIRDHOUSE_TRANSMIT_D})
        {when(f.base.client.getVarpValue(varp)).thenReturn(3);f.base.settings.put(ActivitySessionTest.key("timetracking","rsprofile.alice","birdhouse."+varp),"3:"+(now-120));}
        GuideLiveProgress.Reading filled=f.tool.read("training:bird-house-runs");assertEquals("method:3",filled.stepId);assertFalse(filled.terminal);
        when(f.base.client.getVarpValue(VarPlayerID.BIRDHOUSE_TRANSMIT_A)).thenReturn(2);assertEquals("method:2",f.tool.read("training:bird-house-runs").stepId);
        when(f.base.client.getVarpValue(VarPlayerID.BIRDHOUSE_TRANSMIT_A)).thenReturn(3);f.base.settings.put(ActivitySessionTest.key("timetracking","rsprofile.alice","birdhouse."+VarPlayerID.BIRDHOUSE_TRANSMIT_A),"3:"+(now-3600));
        assertEquals("method:1",f.tool.read("training:bird-house-runs").stepId);
        when(f.base.player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));assertFalse("Remote default-zero varps cannot become bird house progress",f.tool.read("training:bird-house-runs").known());
    }
    static final class Fixture
    {
        final ActivitySessionTest.Fixture base=new ActivitySessionTest.Fixture();final GuideCatalog guides=new GuideCatalog(new Gson());final ActivityXpCatalog methods=new ActivityXpCatalog(new Gson(),guides);
        final TrainingGuideProgress tool=new TrainingGuideProgress(base.client,base.config,guides,methods,base.session);
        void inventory(Item... items){ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(items);when(base.client.getItemContainer(InventoryID.INV)).thenReturn(inventory);}
    }
}
