/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecipeActionEvidenceTest
{
    static BankedExperienceCalculator.Recipe recipe()
    {return BankedExperienceCalculator.recipes().stream().filter(r->r.xp>0&&r.products.size()==1&&r.ingredients.size()==2&&Math.floor(r.xp)==r.xp).findFirst().get();}
    static Map<Integer,Long> before(BankedExperienceCalculator.Recipe r){return Map.of(r.ingredients.get(0).itemId,10L*r.ingredients.get(0).quantity,r.ingredients.get(1).itemId,10L*r.ingredients.get(1).quantity);}
    static Map<Integer,Long> after(BankedExperienceCalculator.Recipe r,int n){return Map.of(r.ingredients.get(0).itemId,(10L-n)*r.ingredients.get(0).quantity,r.ingredients.get(1).itemId,(10L-n)*r.ingredients.get(1).quantity,r.products.get(0).itemId,(long)n*r.products.get(0).quantity);}
    @Test public void xpAndExactInputOutputBatchConfirmOnlyAfterRewardCorrelationWindow()
    {BankedExperienceCalculator.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,3),11,true);e.experience((long)(r.xp*3),11,true);assertEquals(0,e.confirm(13));assertEquals(3,e.confirm(14));assertEquals(0,e.confirm(15));}
    @Test public void animationOrXpAloneCannotConfirmActions()
    {BankedExperienceCalculator.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.experience((long)r.xp,11,true);assertEquals(0,e.confirm(14));}
    @Test public void bankDepositOrWrongXpCannotBecomeRecipeActions()
    {BankedExperienceCalculator.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,3),11,false);e.experience((long)(r.xp*3),11,true);assertEquals(0,e.confirm(14));e.inventory(before(r),20,true);e.inventory(after(r,2),21,true);e.experience((long)(r.xp*5),21,true);assertEquals(0,e.confirm(24));}
    @Test public void nativeTrainingObserverActuallyProducesConfirmedActionsAndUnifiedMetrics()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();BankedExperienceCalculator.Recipe r=recipe();
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,r.id,r.name,null,r.id,r.skill,10000));
        java.util.concurrent.atomic.AtomicInteger xp=new java.util.concurrent.atomic.AtomicInteger(1000);
        org.mockito.Mockito.when(f.client.getSkillExperience(r.skill)).thenAnswer(c->xp.get());f.animation.set(123);
        net.runelite.api.ItemContainer bag=org.mockito.Mockito.mock(net.runelite.api.ItemContainer.class);
        java.util.concurrent.atomic.AtomicReference<net.runelite.api.Item[]> inventory=new java.util.concurrent.atomic.AtomicReference<>(toItems(before(r)));
        org.mockito.Mockito.when(bag.getItems()).thenAnswer(c->inventory.get());org.mockito.Mockito.when(f.client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(bag);
        f.progress.start();f.tick(1);inventory.set(toItems(after(r,2)));f.progress.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(net.runelite.api.InventoryID.INVENTORY.getId(),bag));
        xp.set(1000+(int)(r.xp*2));f.progress.onStatChanged(new net.runelite.api.events.StatChanged(r.skill,xp.get(),99,99));f.tick(3);
        assertEquals(2,f.progress.reading().actions);assertEquals(2,f.session.currentRecord().actions);assertEquals((long)(r.xp*2),f.session.currentRecord().trainingXp);
    }
    private static net.runelite.api.Item[] toItems(Map<Integer,Long> values)
    {return values.entrySet().stream().filter(e->e.getValue()>0).map(e->new net.runelite.api.Item(e.getKey(),e.getValue().intValue())).toArray(net.runelite.api.Item[]::new);}
    @Test public void rewardSuppressionAndRecipeChangesDiscardPendingEvidence()
    {BankedExperienceCalculator.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,2),11,true);e.experience((long)(r.xp*2),11,true);e.discard();assertEquals(0,e.confirm(14));e.select(r);e.inventory(after(r,1),20,true);e.experience((long)r.xp,20,true);assertEquals(0,e.confirm(23));}
}
