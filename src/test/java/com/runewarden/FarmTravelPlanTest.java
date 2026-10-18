/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmTravelPlanTest
{
    @Test public void reusableEquipmentIsPackedOnceWhileConsumablesAccumulate()
    {
        FarmTravelPlan.Stop a = stop(), b = stop(); a.carried.put(4251,1); b.carried.put(4251,1); a.consumed.put(563,2); b.consumed.put(563,3);
        FarmTravelPlan.Summary result = FarmTravelPlan.summarize(List.of(a,b)); assertEquals(Long.valueOf(1),result.required.get(4251)); assertEquals(Long.valueOf(5),result.required.get(563)); assertFalse(result.consumed.containsKey(4251));
    }
    @Test public void chargeBudgetIsSharedAcrossStopsAndConservativeAcrossStockEdits()
    {
        FarmTravelPlan.Stop a = stop(), b = stop(); a.chargePool = b.chargePool = "Digsite pendant"; a.chargesNeeded = b.chargesNeeded = 2; a.availableCharges=5; b.availableCharges=3;
        FarmTravelPlan.Summary result = FarmTravelPlan.summarize(List.of(a,b)); assertEquals(Integer.valueOf(4),result.charges.get("Digsite pendant")); assertEquals(1,result.chargeShortfalls); assertEquals(0,result.uncheckedCharges);
    }
    @Test public void unknownChargeBalanceDoesNotBecomeAnEmptyOrFullItem()
    { FarmTravelPlan.Stop a=stop(); a.chargePool="Xeric's talisman"; a.chargesNeeded=1; FarmTravelPlan.Summary result=FarmTravelPlan.summarize(List.of(a)); assertEquals(1,result.uncheckedCharges); assertEquals(0,result.chargeShortfalls); }
    @Test public void feesAreReconciledAsCoinsAndNotAsThePurchasePriceOfEquipment()
    { FarmTravelPlan.Stop a=stop(), b=stop(); a.coins=200; b.coins=200; a.carried.put(4251,1); FarmTravelPlan.Summary result=FarmTravelPlan.summarize(List.of(a,b)); assertEquals(400,result.coins); assertEquals(Long.valueOf(400),result.required.get(995)); assertTrue(result.consumed.isEmpty()); }
    @Test public void copiedTravelSettingsDoNotShareMutableItemLists()
    { FarmTravelPlan.Stop original=stop(); original.carried.put(4251,1); FarmTravelPlan.Stop copy=FarmTravelPlan.copy(original); copy.carried.clear(); assertFalse(original.carried.isEmpty()); }
    @Test public void oldFarmRoutesHaveEmptyTravelPlansAndNoInventedCharges()
    { FarmPatchPlanner.Route route=new Gson().fromJson("{\"stops\":[\"example\"]}",FarmPatchPlanner.Route.class); assertTrue(route.travel.isEmpty()); FarmTravelPlan.Stop stop=new Gson().fromJson("{}",FarmTravelPlan.Stop.class); assertTrue(FarmTravelPlan.valid(stop)); assertEquals(-1,stop.availableCharges); }
    @Test public void malformedChargeAndSupplyDataCannotBePersisted()
    { FarmTravelPlan.Stop a=stop(); a.chargesNeeded=1; assertFalse(FarmTravelPlan.valid(a)); a.chargesNeeded=0; a.consumed.put(563,-1); assertFalse(FarmTravelPlan.valid(a)); }
    @Test public void travelItemParserMergesDuplicateSuppliesAndRejectsNegativeQuantities()
    { assertEquals(Map.of(563,5),FarmPatchPlanner.parseTravelItems("563:2,563:3")); try { FarmPatchPlanner.parseTravelItems("563:-1"); fail(); }catch(IllegalArgumentException expected){} }
    @Test public void publishedPreparationMapsCannotBeMutatedByOtherModules()
    { FarmTravelPlan.Summary result=FarmTravelPlan.summarize(List.of(stop())); try { result.required.put(995,100L); fail(); }catch(UnsupportedOperationException expected){} }
    @Test public void farmRunPublishesTravelSuppliesAndFeesWithoutPricingReusableEquipment()
    {
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson()); OwnershipLedger ownership=new OwnershipLedger(null,null,new Gson(),System::currentTimeMillis);
        FarmPatchPlanner planner=new FarmPatchPlanner(null,null,null,null,catalog,ownership); FarmPatchPlanner.Route route=new FarmPatchPlanner.Route();
        String patch=catalog.forGuide("training:herb-runs").get(0).id; route.stops.add(patch); FarmTravelPlan.Stop travel=stop(); travel.carried.put(4251,1); travel.coins=200; route.travel.put(patch,travel);
        FarmPatchPlanner.RunReading reading=planner.reading("training:herb-runs",route); assertEquals(Long.valueOf(1),reading.requiredItems.get(4251)); assertEquals(Long.valueOf(200),reading.requiredItems.get(995)); assertEquals(Long.valueOf(200),reading.inputCost); assertEquals(200,reading.travelCoins);
        route.completed.add(patch); FarmPatchPlanner.RunReading done=planner.reading("training:herb-runs",route); assertTrue(done.requiredItems.isEmpty()); assertEquals(0,done.travelCoins);
    }
    private static FarmTravelPlan.Stop stop(){ return new FarmTravelPlan.Stop(); }
}
