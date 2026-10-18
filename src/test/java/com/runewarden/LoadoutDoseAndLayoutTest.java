/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoadoutDoseAndLayoutTest
{
    private static LoadoutPlan.Requirement doses(long count){return new LoadoutPlan.Requirement(LoadoutItemVariants.potion(2434),count,LoadoutPlan.Location.INVENTORY,-1,LoadoutPlan.Unit.DOSES);}
    private static OwnershipLedger.View state(Map<Integer,OwnershipLedger.SlotItem> slots)
    {
        Map<OwnershipLedger.Storage,OwnershipLedger.Observation> stores=new EnumMap<>(OwnershipLedger.Storage.class);stores.put(OwnershipLedger.Storage.INVENTORY,new OwnershipLedger.Observation(slots,100,true));stores.put(OwnershipLedger.Storage.EQUIPMENT,new OwnershipLedger.Observation(Collections.emptyMap(),100,true));
        return new OwnershipLedger.View("profile",stores,0,Collections.emptyMap(),false,false,Collections.emptyMap());
    }
    private static LoadoutReadiness check(List<LoadoutPlan.Requirement> needs,OwnershipLedger.View state){return LoadoutReadiness.check(new LoadoutPlan("test","Test",needs,null),state);}
    @Test public void mixesRealPotionDosesWithoutCountingVialsOrUnrelatedPotions()
    {
        Map<Integer,OwnershipLedger.SlotItem> slots=new LinkedHashMap<>();slots.put(0,new OwnershipLedger.SlotItem(2434,1));slots.put(1,new OwnershipLedger.SlotItem(139,1));slots.put(2,new OwnershipLedger.SlotItem(229,100));slots.put(3,new OwnershipLedger.SlotItem(2440,100));
        LoadoutReadiness r=check(Collections.singletonList(doses(7)),state(slots));assertTrue(r.ready);assertEquals(7,r.lines.get(0).carried);
        assertEquals(1,check(Collections.singletonList(doses(8)),state(slots)).lines.get(0).missing);
    }
    @Test public void overlappingDoseRequirementsCannotReuseDosesOrWholeBottleRequirements()
    {
        OwnershipLedger.View state=state(Collections.singletonMap(0,new OwnershipLedger.SlotItem(2434,1)));
        LoadoutReadiness r=check(Arrays.asList(doses(2),doses(2),doses(1)),state);assertTrue(r.lines.get(0).ready());assertTrue(r.lines.get(1).ready());assertFalse(r.lines.get(2).ready());
        LoadoutPlan.Requirement bottle=new LoadoutPlan.Requirement(Collections.singleton(2434),1,LoadoutPlan.Location.INVENTORY,-1);
        assertFalse(check(Arrays.asList(doses(1),bottle),state).ready);
    }
    @Test public void inventoryLayoutSlotsAndDuplicatesRemainIndependent()
    {
        OwnershipLedger.View state=state(Collections.singletonMap(5,new OwnershipLedger.SlotItem(385,1)));
        LoadoutPlan.Requirement wrong=new LoadoutPlan.Requirement(Collections.singleton(385),1,LoadoutPlan.Location.INVENTORY,6),right=new LoadoutPlan.Requirement(Collections.singleton(385),1,LoadoutPlan.Location.INVENTORY,5);
        LoadoutReadiness r=check(Arrays.asList(wrong,right),state);assertFalse(r.lines.get(0).ready());assertTrue(r.lines.get(1).ready());
    }
    @Test public void potionStoreDosesAreSeparateFromBankedAndNeverCountAsCarried()
    {
        OwnershipLedger.View initial=state(Collections.emptyMap());Map<OwnershipLedger.Storage,OwnershipLedger.Observation> stores=new EnumMap<>(initial.storages);
        stores.put(OwnershipLedger.Storage.POTION_STORE,new OwnershipLedger.Observation(Collections.singletonMap(0,new OwnershipLedger.SlotItem(143,20)),50,false));
        OwnershipLedger.View state=new OwnershipLedger.View("profile",stores,0,Collections.emptyMap(),false,false,Collections.emptyMap());
        LoadoutReadiness r=check(Collections.singletonList(doses(8)),state);assertFalse(r.ready);assertEquals(8,r.lines.get(0).missing);assertEquals(20,r.lines.get(0).storedDoses);assertFalse(r.lines.get(0).bankKnown);
    }
    @Test public void previousSessionBankRemainsTimestampedAndCannotProduceReady()
    {
        OwnershipLedger.View initial=state(Collections.emptyMap());Map<OwnershipLedger.Storage,OwnershipLedger.Observation> stores=new EnumMap<>(initial.storages);stores.put(OwnershipLedger.Storage.BANK,new OwnershipLedger.Observation(Collections.singletonMap(0,new OwnershipLedger.SlotItem(2434,20)),50,false));
        LoadoutReadiness r=check(Collections.singletonList(doses(8)),new OwnershipLedger.View("profile",stores,0,Collections.emptyMap(),false,false,Collections.emptyMap()));assertFalse(r.ready);assertEquals(80,r.lines.get(0).banked);assertFalse(r.lines.get(0).bankCurrentSession);assertEquals(50,r.lines.get(0).bankObservedAt);
    }
    @Test public void alternativesAreReassignedToSatisfyExactRequirementsInsteadOfGreedilyFailing()
    {
        Map<Integer,OwnershipLedger.SlotItem> slots=new LinkedHashMap<>();slots.put(0,new OwnershipLedger.SlotItem(385,1));slots.put(1,new OwnershipLedger.SlotItem(379,1));
        LoadoutPlan.Requirement either=new LoadoutPlan.Requirement(new LinkedHashSet<>(Arrays.asList(385,379)),1,LoadoutPlan.Location.INVENTORY,-1),shark=new LoadoutPlan.Requirement(Collections.singleton(385),1,LoadoutPlan.Location.INVENTORY,-1);
        assertTrue(check(Arrays.asList(either,shark),state(slots)).ready);
    }
    @Test public void slotSpecificItemsCannotBeDoubleSpentByAnEarlierAnySlotLine()
    {
        Map<Integer,OwnershipLedger.SlotItem> slots=new LinkedHashMap<>();slots.put(0,new OwnershipLedger.SlotItem(385,1));slots.put(1,new OwnershipLedger.SlotItem(385,1));
        LoadoutPlan.Requirement any=new LoadoutPlan.Requirement(Collections.singleton(385),1,LoadoutPlan.Location.INVENTORY,-1),fixed=new LoadoutPlan.Requirement(Collections.singleton(385),1,LoadoutPlan.Location.INVENTORY,0);
        assertTrue(check(Arrays.asList(any,fixed),state(slots)).ready);
    }
    @Test public void arbitraryItemsAndEquipmentCannotBeDeclaredPotionDoses()
    {
        reject(()->new LoadoutPlan.Requirement(Collections.singleton(4151),2,LoadoutPlan.Location.INVENTORY,-1,LoadoutPlan.Unit.DOSES));reject(()->new LoadoutPlan.Requirement(Collections.singleton(2434),2,LoadoutPlan.Location.EQUIPMENT,3,LoadoutPlan.Unit.DOSES));
        assertEquals(LoadoutPlan.Unit.ITEMS,new com.google.gson.Gson().fromJson("{\"alternatives\":[385],\"quantity\":1,\"location\":\"INVENTORY\",\"slot\":-1}",LoadoutPlan.Requirement.class).checked().unit());
    }
    private static void reject(Runnable action){try{action.run();fail("Expected invalid dose requirement");}catch(IllegalArgumentException expected){}}
}
