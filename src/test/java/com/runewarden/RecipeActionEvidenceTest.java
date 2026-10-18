/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecipeActionEvidenceTest
{
    static ActionRecipeCatalog.Recipe recipe()
    {return ActionRecipeCatalog.recipes().stream().filter(r->r.xp>0&&r.products.size()==1&&r.ingredients.size()==2&&Math.floor(r.xp)==r.xp).findFirst().get();}
    static Map<Integer,Long> before(ActionRecipeCatalog.Recipe r){return Map.of(r.ingredients.get(0).itemId,10L*r.ingredients.get(0).quantity,r.ingredients.get(1).itemId,10L*r.ingredients.get(1).quantity);}
    static Map<Integer,Long> after(ActionRecipeCatalog.Recipe r,int n){return Map.of(r.ingredients.get(0).itemId,(10L-n)*r.ingredients.get(0).quantity,r.ingredients.get(1).itemId,(10L-n)*r.ingredients.get(1).quantity,r.products.get(0).itemId,(long)n*r.products.get(0).quantity);}
    @Test public void xpAndExactInputOutputBatchConfirmOnlyAfterRewardCorrelationWindow()
    {ActionRecipeCatalog.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,3),11,true);e.experience((long)(r.xp*3),11,true);assertEquals(0,e.confirm(13));assertEquals(3,e.confirm(14));assertEquals(0,e.confirm(15));}
    @Test public void animationOrXpAloneCannotConfirmActions()
    {ActionRecipeCatalog.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.experience((long)r.xp,11,true);assertEquals(0,e.confirm(14));}
    @Test public void bankDepositOrWrongXpCannotBecomeRecipeActions()
    {ActionRecipeCatalog.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,3),11,false);e.experience((long)(r.xp*3),11,true);assertEquals(0,e.confirm(14));e.inventory(before(r),20,true);e.inventory(after(r,2),21,true);e.experience((long)(r.xp*5),21,true);assertEquals(0,e.confirm(24));}
    @Test public void rewardSuppressionAndRecipeChangesDiscardPendingEvidence()
    {ActionRecipeCatalog.Recipe r=recipe();RecipeActionEvidence e=new RecipeActionEvidence();e.select(r);e.inventory(before(r),10,true);e.inventory(after(r,2),11,true);e.experience((long)(r.xp*2),11,true);e.discard();assertEquals(0,e.confirm(14));e.select(r);e.inventory(after(r,1),20,true);e.experience((long)r.xp,20,true);assertEquals(0,e.confirm(23));}

    @Test public void everyRecipeRetainsExactInputOutputAndXpEvidenceForBothSelectionModes()
    {
        for(ActionRecipeCatalog.Recipe recipe:ActionRecipeCatalog.recipes())
        {
            if(recipe.xp<=0||recipe.products.isEmpty())continue;
            Map<Integer,Long> before=new LinkedHashMap<>(),after=new LinkedHashMap<>();
            for(ActionRecipeCatalog.Ingredient item:recipe.ingredients)
            {
                before.merge(item.itemId,10L*item.quantity,Long::sum);
                after.merge(item.itemId,7L*item.quantity,Long::sum);
            }
            for(ActionRecipeCatalog.Ingredient item:recipe.products)after.merge(item.itemId,3L*item.quantity,Long::sum);
            long xp=Math.round(3*recipe.xp);
            assertBoth(recipe,before,after,xp,3);
            assertBoth(recipe,before,after,0,0);
            assertBoth(recipe,before,after,xp+2,0);
            assertBoth(recipe,after,before,xp,0);
            Map<Integer,Long> extraConsumption=new LinkedHashMap<>(after);
            extraConsumption.merge(recipe.ingredients.get(0).itemId,-1L,Long::sum);
            assertBoth(recipe,before,extraConsumption,xp,0);
            Map<Integer,Long> extraOutput=new LinkedHashMap<>(after);
            extraOutput.merge(recipe.products.get(0).itemId,1L,Long::sum);
            assertBoth(recipe,before,extraOutput,xp,0);
        }
    }

    private static void assertBoth(ActionRecipeCatalog.Recipe recipe,Map<Integer,Long> before,Map<Integer,Long> after,long xp,long expected)
    {
        assertEquals(recipe.id+" inferred",expected,ActionRecipeDetector.matches(recipe,before,after,xp));
        RecipeActionEvidence evidence=new RecipeActionEvidence();evidence.select(recipe);
        evidence.inventory(before,1,true);evidence.inventory(after,2,true);evidence.experience(xp,2,true);
        assertEquals(recipe.id+" waits for correlation",0,evidence.confirm(4));
        assertEquals(recipe.id+" selected",expected,evidence.confirm(5));
        assertEquals(recipe.id+" consumed once",0,evidence.confirm(6));
    }
}
