/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import java.util.Map;
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
}
